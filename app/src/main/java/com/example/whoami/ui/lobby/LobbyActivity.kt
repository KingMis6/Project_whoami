package com.example.whoami.ui.lobby

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.whoami.data.model.GameRoom
import com.example.whoami.data.model.RoomStatus
import com.example.whoami.databinding.ActivityLobbyBinding
import com.example.whoami.ui.Extras
import com.example.whoami.ui.confirmLeave
import com.example.whoami.ui.game.GameActivity
import com.example.whoami.ui.room.RoomViewModel
import com.example.whoami.ui.room.RoomViewModelFactory
import com.example.whoami.ui.submit.SubmitCharacterActivity
import kotlinx.coroutines.launch

class LobbyActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLobbyBinding
    private val roomCode by lazy { intent.getStringExtra(Extras.ROOM_CODE)!! }
    private val vm: RoomViewModel by viewModels { RoomViewModelFactory(roomCode) }
    private val adapter = PlayerAdapter()
    private var navigated = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLobbyBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.tvRoomCode.text = roomCode
        binding.rvPlayers.layoutManager = LinearLayoutManager(this)
        binding.rvPlayers.adapter = adapter

        binding.btnShare.setOnClickListener { shareCode() }
        binding.btnStart.setOnClickListener { vm.startSubmissionPhase() }
        binding.btnLeave.setOnClickListener { askLeave() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = askLeave()
        })

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { vm.room.collect { room -> if (room != null) render(room) } }
                launch { vm.events.collect { handle(it) } }
                launch { vm.busy.collect { busy -> binding.btnStart.alpha = if (busy) 0.6f else 1f } }
            }
        }
    }

    private fun render(room: GameRoom) {
        when (room.statusEnum()) {
            RoomStatus.LOBBY -> Unit
            RoomStatus.SUBMITTING -> { goTo(SubmitCharacterActivity::class.java); return }
            else -> { goTo(GameActivity::class.java); return }
        }

        val me = vm.myUid
        val isHost = room.hostUid == me
        val enoughPlayers = room.playerCount() >= room.minPlayers

        adapter.submitList(
            room.orderedPlayers().mapIndexed { index, p ->
                PlayerAdapter.Row(p.uid, p.name, index, p.uid == room.hostUid, p.uid == me)
            }
        )
        binding.tvCount.text = "${room.playerCount()}/${room.maxPlayers}"

        binding.btnStart.isVisible = isHost
        binding.btnStart.isEnabled = enoughPlayers
        binding.tvHint.text = when {
            !isHost -> "Waiting for the host to start…"
            !enoughPlayers -> "Need at least ${room.minPlayers} players to start"
            else -> "Everyone here? Tap start!"
        }
    }

    private fun goTo(target: Class<*>) {
        if (navigated) return
        navigated = true
        startActivity(Intent(this, target).putExtra(Extras.ROOM_CODE, roomCode))
        finish()
    }

    private fun shareCode() {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "Join my Who Am I? game! Room code: $roomCode")
        }
        startActivity(Intent.createChooser(send, "Share room code"))
    }

    private fun askLeave() {
        val isHost = vm.room.value?.hostUid == vm.myUid
        confirmLeave(isHost) { vm.leave() }
    }

    private fun handle(event: RoomViewModel.Event) {
        when (event) {
            is RoomViewModel.Event.Message ->
                Toast.makeText(this, event.text, Toast.LENGTH_SHORT).show()
            RoomViewModel.Event.RoomClosed -> {
                Toast.makeText(this, "The host closed this room.", Toast.LENGTH_LONG).show()
                finish()
            }
            RoomViewModel.Event.Left -> finish()
        }
    }
}
