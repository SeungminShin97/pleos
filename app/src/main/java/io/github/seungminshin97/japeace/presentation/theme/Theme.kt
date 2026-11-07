package io.github.seungminshin97.japeace.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Colors = darkColorScheme(
    primary = Color(0xFF9BE6CD),
    onPrimary = Color(0xFF07382C),
    background = Color(0xFF101C20),
    onBackground = Color(0xFFE5EFEA),
    surface = Color(0xFF1B2B30),
    onSurface = Color(0xFFE5EFEA),
    surfaceVariant = Color(0xFF283B40),
    onSurfaceVariant = Color(0xFFB9CCC6),
)

@Composable
fun JapeaceTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, content = content)
}
