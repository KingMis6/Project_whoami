package com.example.whoami.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.whoami.data.repository.GameRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class HomeViewModel : ViewModel() {

    sealed class Event {
        data class OpenLobby(val roomCode: String) : Event()
        data class Error(val message: String) : Event()
    }

    private val repo = GameRepository()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _events = Channel<Event>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun createRoom(name: String) = launchBusy { Event.OpenLobby(repo.createRoom(name)) }

    fun joinRoom(code: String, name: String) = launchBusy { Event.OpenLobby(repo.joinRoom(code, name)) }

    private fun launchBusy(block: suspend () -> Event) {
        if (_loading.value) return
        viewModelScope.launch {
            _loading.value = true
            try {
                _events.send(block())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.send(Event.Error(e.message ?: "Something went wrong"))
            } finally {
                _loading.value = false
            }
        }
    }
}
