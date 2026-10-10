package com.eko.constellation

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf

sealed class Launch {
    data class Capture(val text: String) : Launch()
    data object Voice : Launch()
    data object Today : Launch()
}

class MainActivity : ComponentActivity() {
    private val pending = mutableStateOf<Launch?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Repo.init(this)
        Reminders.ensureChannel(this)
        Reminders.schedule(this)
        if (savedInstanceState == null) handle(intent)
        setContent { ConstellationTheme { App(pending) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    override fun onResume() {
        super.onResume()
        Repo.ensureQueue()
    }

    private fun handle(i: Intent?) {
        val launch = when (i?.action) {
            Intent.ACTION_SEND -> Launch.Capture(
                listOfNotNull(i.getStringExtra(Intent.EXTRA_SUBJECT), i.getStringExtra(Intent.EXTRA_TEXT))
                    .filter { it.isNotBlank() }.distinct().joinToString("\n")
            )
            Intent.ACTION_PROCESS_TEXT -> Launch.Capture(i.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString() ?: "")
            ACTION_CAPTURE -> Launch.Capture("")
            ACTION_VOICE -> Launch.Voice
            Reminders.ACTION_OPEN_TODAY -> Launch.Today
            else -> null
        }
        if (launch != null) pending.value = launch
    }

    companion object {
        const val ACTION_CAPTURE = "com.eko.constellation.CAPTURE"
        const val ACTION_VOICE = "com.eko.constellation.VOICE"
    }
}
