package com.example.whoami.ui.submit

import android.content.Intent
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.whoami.data.model.GameRoom
import com.example.whoami.data.model.RoomStatus
import com.example.whoami.databinding.ActivitySubmitBinding
import com.example.whoami.ui.Extras
import com.example.whoami.ui.confirmLeave
import com.example.whoami.ui.game.GameActivity
import com.example.whoami.ui.room.RoomViewModel
import com.example.whoami.ui.room.RoomViewModelFactory
import kotlinx.coroutines.launch

class SubmitCharacterActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySubmitBinding
    private val roomCode by lazy { intent.getStringExtra(Extras.ROOM_CODE)!! }
    private val vm: RoomViewModel by viewModels { RoomViewModelFactory(roomCode) }
    private var navigated = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySubmitBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnSubmit.setOnClickListener { submit() }
        binding.etCharacter.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submit()
                true
            } else {
                false
            }
        }
        binding.btnLeave.setOnClickListener { askLeave() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = askLeave()
        })

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { vm.room.collect { room -> if (room != null) render(room) } }
                launch { vm.events.collect { handle(it) } }
                launch {
                    vm.busy.collect { busy ->
                        binding.progress.isVisible = busy
                        binding.btnSubmit.isEnabled = !busy
                    }
                }
            }
        }
    }

    private fun submit() {
        val name = binding.etCharacter.text?.toString().orEmpty().trim()
        if (name.isEmpty()) {
            binding.etCharacterLayout.error = "Type a character first"
            return
        }
        binding.etCharacterLayout.error = null
        getSystemService(InputMethodManager::class.java)
            ?.hideSoftInputFromWindow(binding.root.windowToken, 0)
        vm.submitCharacter(name)
    }

    private fun render(room: GameRoom) {
        when (room.statusEnum()) {
            RoomStatus.ASSIGNED, RoomStatus.PLAYING, RoomStatus.FINISHED -> {
                goToGame()
                return
            }
            else -> Unit
        }

        val me = room.players[vm.myUid]
        val locked = me?.hasSubmitted == true
        val ready = room.players.values.count { it.hasSubmitted }
        val total = room.playerCount()

        binding.groupInput.isVisible = !locked
        binding.groupWaiting.isVisible = locked
        binding.tvWaiting.text = "$ready of $total friends are ready"
        binding.progressWaiting.max = total.coerceAtLeast(1)
        binding.progressWaiting.setProgressCompat(ready, true)
    }

    private fun goToGame() {
        if (navigated) return
        navigated = true
        startActivity(Intent(this, GameActivity::class.java).putExtra(Extras.ROOM_CODE, roomCode))
        finish()
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
