package com.guestvault

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.guestvault.guest.GuestHomeActivity
import com.guestvault.ui.AppViewModel
import com.guestvault.ui.navigation.GuestVaultNavHost
import com.guestvault.ui.theme.GuestVaultTheme
import com.guestvault.ui.theme.HeroGradient
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val appViewModel: AppViewModel = hiltViewModel()
            val themeMode by appViewModel.themeMode.collectAsStateWithLifecycle()
            val sessionActive by appViewModel.sessionActive.collectAsStateWithLifecycle()

            // A guest session may be running when the app is (re)opened. In that
            // case the owner dashboard must never be rendered: hand straight
            // over to the restricted guest home instead.
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner, sessionActive) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME && sessionActive) {
                        openGuestMode()
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            GuestVaultTheme(themeMode = themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (sessionActive) {
                        // Neutral holding frame; the activity is being replaced.
                        Box(modifier = Modifier.fillMaxSize().background(HeroGradient))
                    } else {
                        GuestVaultNavHost()
                    }
                }
            }
        }
    }

    private fun openGuestMode() {
        val intent = Intent(this, GuestHomeActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        startActivity(intent)
        if (!isFinishing) finish()
    }
}
