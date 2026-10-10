package com.example.whoami.data.model

/**
 * Full state of one game room, mirrored live to every connected device.
 * Stored at /rooms/{roomCode}
 *
 * - assignments: uid -> Character ASSIGNED to that player (i.e. someone
 *   else's submission). Every client reads this (everyone else must see it),
 *   and the UI hides the row matching the local user's own uid.
 *
 * Typed-in submissions are NOT stored here. They live at
 * /submissions/{roomCode}/{uid}, which only the host can read (see README),
 * so players can't peek at who wrote what.
 */
data class GameRoom(
    val roomCode: String = "",
    val hostUid: String = "",
    val status: String = RoomStatus.LOBBY.name,
    val currentTurnUid: String = "",
    val minPlayers: Int = 2,
    val maxPlayers: Int = 6,
    val players: Map<String, Player> = emptyMap(),
    val assignments: Map<String, Character> = emptyMap()
) {
    /** Players in join order - used everywhere for stable turn order + colors. */
    fun orderedPlayers(): List<Player> = players.values.sortedBy { it.joinedAt }

    fun playerCount() = players.size

    fun allSubmitted() = players.isNotEmpty() && players.values.all { it.hasSubmitted }

    fun statusEnum(): RoomStatus =
        runCatching { RoomStatus.valueOf(status) }.getOrDefault(RoomStatus.LOBBY)
}
