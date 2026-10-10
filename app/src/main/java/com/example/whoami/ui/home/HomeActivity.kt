package com.example.whoami.ui.home

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.whoami.databinding.ActivityHomeBinding
import com.example.whoami.ui.Extras
import com.example.whoami.ui.lobby.LobbyActivity
import kotlinx.coroutines.launch

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private val vm: HomeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnCreate.setOnClickListener {
            val name = readName() ?: return@setOnClickListener
            vm.createRoom(name)
        }

        binding.btnJoin.setOnClickListener {
            val name = readName() ?: return@setOnClickListener
            val code = binding.etCode.text?.toString().orEmpty().trim().uppercase()
            if (code.isEmpty()) {
                binding.etCodeLayout.error = "Enter the code your friend shared"
                return@setOnClickListener
            }
            binding.etCodeLayout.error = null
            vm.joinRoom(code, name)
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { vm.loading.collect { setLoading(it) } }
                launch { vm.events.collect { handle(it) } }
            }
        }
    }

    private fun readName(): String? {
        val name = binding.etName.text?.toString().orEmpty().trim()
        return if (name.isEmpty()) {
            binding.etNameLayout.error = "Please enter your name"
            null
        } else {
            binding.etNameLayout.error = null
            name
        }
    }

    private fun setLoading(loading: Boolean) {
        binding.progress.isVisible = loading
        binding.btnCreate.isEnabled = !loading
        binding.btnJoin.isEnabled = !loading
    }

    private fun handle(event: HomeViewModel.Event) {
        when (event) {
            is HomeViewModel.Event.OpenLobby -> {
                startActivity(
                    Intent(this, LobbyActivity::class.java)
                        .putExtra(Extras.ROOM_CODE, event.roomCode)
                )
            }
            is HomeViewModel.Event.Error ->
                Toast.makeText(this, event.message, Toast.LENGTH_LONG).show()
        }
    }
}
