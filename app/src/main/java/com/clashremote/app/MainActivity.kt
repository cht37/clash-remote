package com.clashremote.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clashremote.app.ui.ClashRemoteApp
import com.clashremote.app.ui.ClashRemoteTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm = viewModel<RemoteViewModel>()
            val appearance by vm.appearance.collectAsStateWithLifecycle()
            ClashRemoteTheme(appearance.palette) { ClashRemoteApp(vm) }
        }
    }
}
