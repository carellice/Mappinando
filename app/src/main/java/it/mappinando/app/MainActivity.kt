package it.mappinando.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import it.mappinando.app.updates.AppUpdateViewModel
import it.mappinando.app.ui.MappinandoApp
import it.mappinando.app.ui.MappinandoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val updates = ViewModelProvider(this)[AppUpdateViewModel::class.java]
        setContent { MappinandoTheme { MappinandoApp(updateManager = updates) } }
    }
}
