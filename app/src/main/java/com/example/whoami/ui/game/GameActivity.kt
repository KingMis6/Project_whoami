package com.example.whoami.ui.game

import android.os.Bundle
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import com.example.whoami.R
import com.example.whoami.data.local.NotesManager
import com.example.whoami.data.model.GameRoom
import com.example.whoami.data.model.RoomStatus
import com.example.whoami.databinding.ActivityGameBinding
import com.example.whoami.databinding.DialogGuessBinding
import com.example.whoami.ui.Extras
import com.example.whoami.ui.confirmLeave
import com.example.whoami.ui.room.RoomViewModel
import com.example.whoami.ui.room.RoomViewModelFactory
import com.example.whoami.util.PlayerColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class GameActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGameBinding
    private val roomCode by lazy { intent.getStringExtra(Extras.ROOM_CODE)!! }
    private val vm: RoomViewModel by viewModels { RoomViewModelFactory(roomCode) }
    private val notes by lazy { NotesManager(this) }
    private val adapter = CharacterCardAdapter()

    /** What the big banner button does right now (start playing / back to home). */
    private var bannerAction: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGameBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.rvCards.layoutManager = GridLayoutManager(this, 2)
        binding.rvCards.adapter = adapter

        setupNotes()

        binding.btnBannerAction.setOnClickListener { bannerAction?.invoke() }
        binding.btnGuess.setOnClickListener { showGuessDialog() }
        binding.btnPass.setOnClickListener { vm.passTurn() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = askLeave()
        })

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { vm.room.collect { room -> if (room != null) render(room) } }
                launch { vm.events.collect { handle(it) } }
                launch {
                    vm.busy.collect { busy ->
                        binding.btnGuess.isEnabled = !busy
                        binding.btnPass.isEnabled = !busy
                        binding.btnBannerAction.isEnabled = !busy
                    }
                }
            }
        }
    }

    // ---------- rendering ----------

    private fun render(room: GameRoom) {
        val me = vm.myUid
        val status = room.statusEnum()
        val players = room.orderedPlayers()
        val isHost = room.hostUid == me

        val myPlayer = players.firstOrNull { it.uid == me }
        val iAmSolved = myPlayer?.hasGuessedCorrectly == true
        val myTurn = status == RoomStatus.PLAYING && room.currentTurnUid == me && !iAmSolved

        // Cards: everyone's character is visible EXCEPT your own (until you solve it).
        adapter.submitList(
            players.mapIndexed { index, p ->
                val isMe = p.uid == me
                val character = room.assignments[p.uid]
                val reveal = !isMe || p.hasGuessedCorrectly
                CharacterCardAdapter.CardUi(
                    uid = p.uid,
                    ownerName = p.name,
                    colorIndex = index,
                    characterName = if (reveal) (character?.name ?: "…") else null,
                    imageUrl = if (reveal) character?.imageUrl else null,
                    isMe = isMe,
                    isSolved = p.hasGuessedCorrectly,
                    isCurrentTurn = status == RoomStatus.PLAYING &&
                        room.currentTurnUid == p.uid && !p.hasGuessedCorrectly
                )
            }
        )

        // Banner
        val currentIndex = players.indexOfFirst { it.uid == room.currentTurnUid }
        val currentName = players.getOrNull(currentIndex)?.name ?: "Someone"
        var bannerColor = ContextCompat.getColor(this, R.color.lavender)
        val title: String
        val sub: String
        bannerAction = null
        binding.btnBannerAction.isVisible = false

        when (status) {
            RoomStatus.ASSIGNED -> {
                title = "Characters are dealt!"
                sub = if (isHost) {
                    "Look at your friends’ cards (yours stays hidden). Start when everyone’s ready."
                } else {
                    "Look at your friends’ cards (yours stays hidden). Waiting for the host to start."
                }
                if (isHost) {
                    binding.btnBannerAction.text = "Everyone’s ready, start!"
                    binding.btnBannerAction.isVisible = true
                    bannerAction = { vm.startPlaying() }
                }
            }
            RoomStatus.PLAYING -> {
                if (currentIndex >= 0) bannerColor = PlayerColors.color(this, currentIndex)
                if (myTurn) {
                    title = "Your turn!"
                    sub = "Ask yes/no questions out loud, then take a guess when you’re ready."
                } else {
                    title = "$currentName’s turn"
                    sub = if (iAmSolved) "You’ve solved yours. Help your friends out!"
                    else "Answer their questions honestly 🙂"
                }
            }
            RoomStatus.FINISHED -> {
                title = "That’s a wrap! 🎉"
                sub = "Everyone found their character."
                binding.btnBannerAction.text = "Back to home"
                binding.btnBannerAction.isVisible = true
                bannerAction = { vm.leave() }
            }
            else -> {
                title = "Getting things ready…"
                sub = ""
            }
        }

        binding.bannerCard.setCardBackgroundColor(bannerColor)
        binding.tvBannerTitle.text = title
        binding.tvBannerSub.text = sub
        binding.tvBannerSub.isVisible = sub.isNotEmpty()

        binding.actionRow.isVisible = myTurn
    }

    // ---------- guessing ----------

    private fun showGuessDialog() {
        val dialogBinding = DialogGuessBinding.inflate(layoutInflater)
        MaterialAlertDialogBuilder(this)
            .setTitle("Who do you think you are?")
            .setMessage("A wrong guess ends your turn.")
            .setView(dialogBinding.root)
            .setPositiveButton("Guess") { _, _ ->
                val guess = dialogBinding.etGuess.text?.toString().orEmpty().trim()
                if (guess.isNotEmpty()) vm.guess(guess)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ---------- notes (private, saved on this device only) ----------

    private fun setupNotes() {
        binding.etNotes.setText(notes.getNotes(roomCode))
        binding.etNotes.doAfterTextChanged { notes.saveNotes(roomCode, it?.toString().orEmpty()) }
        binding.notesHeader.setOnClickListener {
            setNotesExpanded(!binding.etNotes.isVisible)
        }
    }

    private fun setNotesExpanded(expanded: Boolean) {
        binding.etNotes.isVisible = expanded
        binding.tvNotesHint.isVisible = expanded
        binding.tvNotesToggle.text = if (expanded) "Hide" else "Show"
    }

    // ---------- leaving / events ----------

    private fun askLeave() {
        val room = vm.room.value
        if (room?.statusEnum() == RoomStatus.FINISHED) {
            vm.leave()
        } else {
            confirmLeave(room?.hostUid == vm.myUid) { vm.leave() }
        }
    }

    private fun handle(event: RoomViewModel.Event) {
        when (event) {
            is RoomViewModel.Event.Message ->
                Toast.makeText(this, event.text, Toast.LENGTH_SHORT).show()
            RoomViewModel.Event.RoomClosed -> {
                Toast.makeText(this, "The host ended the game.", Toast.LENGTH_LONG).show()
                notes.clearNotes(roomCode)
                finish()
            }
            RoomViewModel.Event.Left -> {
                notes.clearNotes(roomCode)
                finish()
            }
        }
    }
}
