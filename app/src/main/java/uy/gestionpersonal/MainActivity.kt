package uy.gestionpersonal

import android.os.Bundle
import android.content.Context
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import uy.gestionpersonal.data.MemoryRepository
import uy.gestionpersonal.ui.GestionApp
import uy.gestionpersonal.ui.GestionTheme
import uy.gestionpersonal.ui.ThemeMode
import java.util.Locale

class SessionViewModel : ViewModel() {
    val repository = MemoryRepository()
}

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        val configuration = Configuration(newBase.resources.configuration)
        configuration.setLocale(Locale.forLanguageTag("es-UY"))
        super.attachBaseContext(newBase.createConfigurationContext(configuration))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val preferences = getSharedPreferences("appearance", Context.MODE_PRIVATE)
        val initialMode = ThemeMode.fromStored(preferences.getString("theme_mode", null))
        val systemDark = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        if (!initialMode.isDark(systemDark)) setTheme(R.style.AppThemeLight)
        super.onCreate(savedInstanceState)
        val dark = initialMode.isDark(systemDark)
        enableEdgeToEdge(
            statusBarStyle = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = if (dark) SystemBarStyle.dark(android.graphics.Color.BLACK)
                else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        )
        setContent {
            val session: SessionViewModel = viewModel()
            var modeName by rememberSaveable { mutableStateOf(initialMode.name) }
            GestionTheme(mode = ThemeMode.fromStored(modeName), onModeChange = { mode ->
                modeName = mode.name
                preferences.edit().putString("theme_mode", mode.name).apply()
            }) { GestionApp(session.repository) }
        }
    }
}
