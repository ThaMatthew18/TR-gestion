package uy.gestionpersonal.ui

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp

enum class ThemeMode(val label: String) {
    AMOLED("AMOLED"), LIGHT("Claro"), SYSTEM("Sistema");

    fun isDark(systemDark: Boolean): Boolean = this == AMOLED || (this == SYSTEM && systemDark)

    companion object {
        fun fromStored(value: String?): ThemeMode = entries.find { it.name == value } ?: AMOLED
    }
}

private data class Appearance(val mode: ThemeMode, val dark: Boolean, val onChange: (ThemeMode) -> Unit)
private val LocalAppearance = staticCompositionLocalOf { Appearance(ThemeMode.AMOLED, true, {}) }

val IsAmoled: Boolean @Composable get() = LocalAppearance.current.dark
val Navy: Color @Composable get() = MaterialTheme.colorScheme.onSurface
val Teal: Color @Composable get() = MaterialTheme.colorScheme.primary
val Muted: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
val Canvas: Color @Composable get() = MaterialTheme.colorScheme.background
val BrandPanel: Color @Composable get() = if (IsAmoled) Color.Black else Color(0xFF172E45)

private val AmoledColors = darkColorScheme(
    primary = Color(0xFF6CE5CE), onPrimary = Color(0xFF002B23),
    primaryContainer = Color(0xFF073A31), onPrimaryContainer = Color(0xFFB0F7E6),
    secondary = Color(0xFFA6C9F4), onSecondary = Color(0xFF0B2541),
    secondaryContainer = Color(0xFF192D2A), onSecondaryContainer = Color(0xFFD0F6EB),
    tertiary = Color(0xFFF0C574), onTertiary = Color(0xFF382500),
    tertiaryContainer = Color(0xFF291F0D), onTertiaryContainer = Color(0xFFF5D496),
    background = Color.Black, onBackground = Color(0xFFECF3EF),
    surface = Color(0xFF0B0F0E), onSurface = Color(0xFFECF3EF),
    surfaceVariant = Color(0xFF19211E), onSurfaceVariant = Color(0xFFAAB9B2),
    surfaceContainerLowest = Color.Black, surfaceContainerLow = Color(0xFF0B0F0E),
    surfaceContainer = Color(0xFF111714), surfaceContainerHigh = Color(0xFF19211E),
    surfaceContainerHighest = Color(0xFF222C27), surfaceTint = Color.Transparent,
    outline = Color(0xFF56655D), outlineVariant = Color(0xFF2C3932),
    error = Color(0xFFFFB0B5), onError = Color(0xFF570F1B),
    errorContainer = Color(0xFF43131C), onErrorContainer = Color(0xFFFFD9DC),
    inverseSurface = Color(0xFFE0E9E3), inverseOnSurface = Color(0xFF17221B),
    inversePrimary = Color(0xFF006B58), scrim = Color.Black
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF047F80), onPrimary = Color.White,
    primaryContainer = Color(0xFFD8F2ED), onPrimaryContainer = Color(0xFF063D34),
    secondary = Color(0xFF3568AD), onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCEEE9), onSecondaryContainer = Color(0xFF172E45),
    tertiary = Color(0xFF795A16), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFF4D9), onTertiaryContainer = Color(0xFF795A16),
    background = Color(0xFFF4F6F8), onBackground = Color(0xFF172E45),
    surface = Color.White, onSurface = Color(0xFF172E45),
    surfaceVariant = Color(0xFFE9EFF3), onSurfaceVariant = Color(0xFF617185),
    outline = Color(0xFFB7C4CE), outlineVariant = Color(0xFFE0E7EC),
    error = Color(0xFFBA353A)
)

@Composable
fun GestionTheme(
    mode: ThemeMode = ThemeMode.AMOLED,
    onModeChange: (ThemeMode) -> Unit = {},
    content: @Composable () -> Unit
) {
    val dark = mode.isDark(isSystemInDarkTheme())
    val view = LocalView.current
    if (!view.isInEditMode) SideEffect {
        view.context.activity()?.enableEdgeToEdge(
            statusBarStyle = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = if (dark) SystemBarStyle.dark(android.graphics.Color.BLACK)
                else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        )
    }
    CompositionLocalProvider(LocalAppearance provides Appearance(mode, dark, onModeChange)) {
        MaterialTheme(
            colorScheme = if (dark) AmoledColors else LightColors,
            shapes = Shapes(small = RoundedCornerShape(10.dp), medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(24.dp)),
            content = content
        )
    }
}

@Composable
fun ThemeSelector() {
    val appearance = LocalAppearance.current
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                if (appearance.dark) Icons.Outlined.DarkMode else Icons.Outlined.LightMode,
                contentDescription = "Cambiar tema",
                tint = MaterialTheme.colorScheme.primary
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Text("Apariencia", modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.labelMedium)
            ThemeMode.entries.forEach { mode ->
                DropdownMenuItem(
                    modifier = Modifier.semantics { selected = appearance.mode == mode },
                    text = { Text(mode.label) },
                    onClick = { appearance.onChange(mode); expanded = false },
                    trailingIcon = { if (appearance.mode == mode) Icon(Icons.Outlined.Check, null) }
                )
            }
        }
    }
}

private fun Context.activity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}
