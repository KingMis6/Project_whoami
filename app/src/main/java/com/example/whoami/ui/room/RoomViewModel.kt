package com.example.whoami.ui.room

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.whoami.data.model.GameRoom
import com.example.whoami.data.model.RoomStatus
import com.example.whoami.data.repository.GameRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * One ViewModel shared in spirit by Lobby, Submit and Game: it watches a single
 * room live and exposes the actions a player can take.
 */
class RoomViewModel(
    val roomCode: String,
    private val repo: GameRepository
) : ViewModel() {

    sealed class Event {
        data class Message(val text: String) : Event()
        object RoomClosed : Event()
        object Left : Event()
    }

    private val _room = MutableStateFlow<GameRoom?>(null)
    val room: StateFlow<GameRoom?> = _room.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _events = Channel<Event>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    val myUid: String? get() = repo.currentUid()

    private var hasSeenRoom = false
    private var leaving = false
    private var dealing = false

    init {
        viewModelScope.launch {
            repo.observeRoom(roomCode)
                .catch { _events.send(Event.Message("Connection problem: ${it.message}")) }
                .collect { room ->
                    if (room == null) {
                        if (hasSeenRoom && !leaving) _events.send(Event.RoomClosed)
                    } else {
                        hasSeenRoom = true
                        _room.value = room
                        dealIfReady(room)
                    }
                }
        }
    }

    /** Host device automatically deals the characters once everyone has submitted. */
    private fun dealIfReady(room: GameRoom) {
        val uid = myUid ?: return
        if (dealing) return
        val ready = room.hostUid == uid &&
            room.statusEnum() == RoomStatus.SUBMITTING &&
            room.allSubmitted() &&
            room.playerCount() >= room.minPlayers
        if (!ready) return

        dealing = true
        viewModelScope.launch {
            try {
                repo.assignCharactersAndStart(roomCode)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                dealing = false
                _events.send(Event.Message("Couldn’t deal the characters: ${e.message}"))
            }
        }
    }

    fun startSubmissionPhase() = act { repo.startSubmissionPhase(roomCode) }

    fun submitCharacter(name: String) = act { repo.submitCharacter(roomCode, name) }

    fun startPlaying() = act { repo.startPlaying(roomCode) }

    fun passTurn() = act {
        val uid = myUid ?: return@act
        repo.passTurn(roomCode, uid)
    }

    /** Correct guess solves it. A wrong guess ends your turn (classic rules). */
    fun guess(text: String) = act {
        val uid = myUid ?: return@act
        if (repo.submitGuess(roomCode, uid, text)) {
            _events.send(Event.Message("Correct! 🎉 Nicely done."))
        } else {
            repo.passTurn(roomCode, uid)
            _events.send(Event.Message("Not quite. The turn passes on."))
        }
    }

    fun leave() {
        leaving = true
        viewModelScope.launch {
            try {
                repo.leaveRoom(roomCode)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Even if cleanup fails we still let the player walk away.
            }
            _events.send(Event.Left)
        }
    }

    private fun act(block: suspend () -> Unit) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.send(Event.Message(e.message ?: "Something went wrong"))
            } finally {
                _busy.value = false
            }
        }
    }
}

class RoomViewModelFactory(private val roomCode: String) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        RoomViewModel(roomCode, GameRepository()) as T
}
