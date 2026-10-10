package com.example.whoami.data.repository

import com.example.whoami.data.model.Character
import com.example.whoami.data.model.GameRoom
import com.example.whoami.data.model.Player
import com.example.whoami.data.model.RoomStatus
import com.example.whoami.util.GuessMatcher
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Single source of truth for all networked game state.
 *
 * Database layout:
 *   /rooms/{code}                 -> GameRoom (players, status, turn, assignments)
 *   /submissions/{code}/{uid}     -> Character typed by that player (host-readable only)
 */
class GameRepository {

    private val db: DatabaseReference = FirebaseDatabase.getInstance().reference
    private val auth = FirebaseAuth.getInstance()
    private val imageLookup = ImageLookupService()

    private fun roomRef(code: String) = db.child("rooms").child(code)
    private fun submissionsRef(code: String) = db.child("submissions").child(code)

    // ---------- auth ----------

    /** Anonymous sign-in so every device has a stable uid without a login screen. */
    suspend fun ensureSignedIn(): String {
        auth.currentUser?.let { return it.uid }
        val result = auth.signInAnonymously().await()
        return result.user!!.uid
    }

    fun currentUid(): String? = auth.currentUser?.uid

    // ---------- lobby ----------

    /** Creates a fresh room with a short random code and returns that code. */
    suspend fun createRoom(hostName: String): String {
        val uid = ensureSignedIn()
        for (attempt in 1..5) {
            val code = generateRoomCode()
            if (roomRef(code).get().await().exists()) continue // extremely unlikely collision

            val host = Player(uid = uid, name = hostName.trim(), joinedAt = System.currentTimeMillis())
            val room = GameRoom(
                roomCode = code,
                hostUid = uid,
                status = RoomStatus.LOBBY.name,
                players = mapOf(uid to host)
            )
            roomRef(code).setValue(room).await()
            return code
        }
        throw IllegalStateException("Couldn't create a room, please try again")
    }

    /** Joins an existing room. Throws a friendly error if missing/full/already started. */
    suspend fun joinRoom(rawCode: String, name: String): String {
        val uid = ensureSignedIn()
        val code = rawCode.trim().uppercase()
        val ref = roomRef(code)
        val room = ref.get().await().getValue(GameRoom::class.java)
            ?: throw IllegalStateException("No room found with that code")

        if (room.players.containsKey(uid)) return code // already in this room (rejoin)
        if (room.statusEnum() != RoomStatus.LOBBY) throw IllegalStateException("That game has already started")
        if (room.playerCount() >= room.maxPlayers) throw IllegalStateException("That room is full")

        val player = Player(uid = uid, name = name.trim(), joinedAt = System.currentTimeMillis())
        ref.child("players").child(uid).setValue(player).await()
        return code
    }

    /** Live updates for a room. Emits null if the room doesn't exist (or was deleted). */
    fun observeRoom(roomCode: String): Flow<GameRoom?> = callbackFlow {
        val ref = roomRef(roomCode)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                try {
                    trySend(snapshot.getValue(GameRoom::class.java))
                } catch (e: Exception) {
                    close(e)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    /**
     * Leaving: the host leaving an unfinished game closes the room for everyone.
     * A regular player leaving is removed (and their turn skipped if it was theirs).
     * The last person out turns off the lights.
     */
    suspend fun leaveRoom(roomCode: String) {
        val uid = currentUid() ?: return
        val ref = roomRef(roomCode)
        val room = ref.get().await().getValue(GameRoom::class.java) ?: return
        val status = room.statusEnum()

        if (room.hostUid == uid && status != RoomStatus.FINISHED) {
            deleteRoom(roomCode)
            return
        }
        if (status == RoomStatus.PLAYING && room.currentTurnUid == uid) {
            advanceTurn(roomCode, room, uid, justSolved = false, leavingUid = uid)
        }
        if (room.playerCount() <= 1) {
            deleteRoom(roomCode)
            return
        }
        ref.child("players").child(uid).removeValue().await()
    }

    private suspend fun deleteRoom(roomCode: String) {
        // Submissions first: its rules look at the room's hostUid, so the room must still exist.
        runCatching { submissionsRef(roomCode).removeValue().await() }
        roomRef(roomCode).removeValue().await()
    }

    // ---------- submitting characters ----------

    /** Host only: moves the room from LOBBY into SUBMITTING. */
    suspend fun startSubmissionPhase(roomCode: String) {
        roomRef(roomCode).child("status").setValue(RoomStatus.SUBMITTING.name).await()
    }

    /**
     * Each player privately submits the character they thought up. We look up a
     * picture automatically (best effort) so nobody has to hunt for one.
     */
    suspend fun submitCharacter(roomCode: String, characterName: String) {
        val uid = ensureSignedIn()
        val name = characterName.trim()
        val character = Character(name = name, imageUrl = imageLookup.findThumbnailUrl(name))

        submissionsRef(roomCode).child(uid).setValue(character).await()
        roomRef(roomCode).child("players").child(uid).child("hasSubmitted").setValue(true).await()
    }

    /**
     * Host only: once everyone has submitted, deal each player a character that
     * someone ELSE wrote, pick the first turn, and move to ASSIGNED.
     */
    suspend fun assignCharactersAndStart(roomCode: String) {
        val ref = roomRef(roomCode)
        val room = ref.get().await().getValue(GameRoom::class.java) ?: return

        val submissions: Map<String, Character> = submissionsRef(roomCode).get().await()
            .children
            .mapNotNull { snap ->
                val key = snap.key ?: return@mapNotNull null
                val character = snap.getValue(Character::class.java) ?: return@mapNotNull null
                key to character
            }
            .toMap()

        val order = room.orderedPlayers().map { it.uid }
        val assignments = deriveDerangement(order) { fromUid ->
            submissions[fromUid] ?: Character(name = "Mystery guest")
        }

        // Write everything first, flip the status last, so clients never see a half-dealt game.
        ref.child("assignments").setValue(assignments).await()
        ref.child("currentTurnUid").setValue(order.first()).await()
        ref.child("status").setValue(RoomStatus.ASSIGNED.name).await()
        runCatching { submissionsRef(roomCode).removeValue().await() }
    }

    /** Host only: moves from the "look at everyone's cards" moment into turns. */
    suspend fun startPlaying(roomCode: String) {
        roomRef(roomCode).child("status").setValue(RoomStatus.PLAYING.name).await()
    }

    // ---------- playing ----------

    /**
     * Checks a guess against the player's true character. If correct, marks them
     * solved and moves the turn on. Returns whether it was correct.
     */
    suspend fun submitGuess(roomCode: String, uid: String, guess: String): Boolean {
        val ref = roomRef(roomCode)
        val room = ref.get().await().getValue(GameRoom::class.java) ?: return false
        val truth = room.assignments[uid] ?: return false

        val correct = GuessMatcher.isMatch(guess, truth.name)
        if (correct) {
            ref.child("players").child(uid).child("hasGuessedCorrectly").setValue(true).await()
            advanceTurn(roomCode, room, uid, justSolved = true)
        }
        return correct
    }

    /** Ends the current player's turn without solving. */
    suspend fun passTurn(roomCode: String, uid: String) {
        val room = roomRef(roomCode).get().await().getValue(GameRoom::class.java) ?: return
        if (room.currentTurnUid != uid) return
        advanceTurn(roomCode, room, uid, justSolved = false)
    }

    /**
     * Moves the turn to the next player who hasn't solved yet, or finishes the
     * game when nobody is left. [room] is the snapshot from BEFORE this change,
     * so we account for [justSolved] / [leavingUid] explicitly.
     */
    private suspend fun advanceTurn(
        roomCode: String,
        room: GameRoom,
        playedUid: String,
        justSolved: Boolean,
        leavingUid: String? = null
    ) {
        val order = room.orderedPlayers().map { it.uid }
        val solved = order.filter { uid ->
            (uid == playedUid && justSolved) || room.players[uid]?.hasGuessedCorrectly == true
        }.toSet()
        val remaining = order.filter { it !in solved && it != leavingUid }

        val ref = roomRef(roomCode)
        if (remaining.isEmpty()) {
            ref.child("status").setValue(RoomStatus.FINISHED.name).await()
            return
        }

        val startIndex = order.indexOf(playedUid).coerceAtLeast(0)
        val next = (1..order.size)
            .map { step -> order[(startIndex + step) % order.size] }
            .first { it in remaining }
        ref.child("currentTurnUid").setValue(next).await()
    }

    // ---------- helpers ----------

    private fun generateRoomCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // no 0/O/1/I to avoid mix-ups
        return (1..5).map { chars.random() }.joinToString("")
    }

    /**
     * Random shuffle where nobody is handed their own submission. Falls back to
     * rotating by one, which is always valid for 2+ players.
     */
    private fun deriveDerangement(
        uids: List<String>,
        submissionFor: (String) -> Character
    ): Map<String, Character> {
        val n = uids.size
        if (n < 2) return emptyMap()
        repeat(50) {
            val shuffled = uids.shuffled()
            if (uids.indices.all { i -> shuffled[i] != uids[i] }) {
                return uids.indices.associate { i -> uids[i] to submissionFor(shuffled[i]) }
            }
        }
        return uids.indices.associate { i -> uids[i] to submissionFor(uids[(i + 1) % n]) }
    }
}
