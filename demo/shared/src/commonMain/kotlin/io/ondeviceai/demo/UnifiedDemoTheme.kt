package io.ondeviceai.demo

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun UnifiedDemoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            // Green accent stays exactly as before.
            primary = Color(0xFF287653), onPrimary = Color.White,
            primaryContainer = Color(0xFFE1F2E5), onPrimaryContainer = Color(0xFF163B29),
            secondary = Color(0xFF5C7563), onSecondary = Color.White,
            secondaryContainer = Color(0xFFE5ECE4), onSecondaryContainer = Color(0xFF1D3A28),
            tertiary = Color(0xFF5C7563), onTertiary = Color.White,
            tertiaryContainer = Color(0xFFE5ECE4), onTertiaryContainer = Color(0xFF1D3A28),

            // Every neutral / container token used by Material3 surfaces is pinned to the
            // same warm off-white palette so Cards can never fall back to a purple tint.
            background = Color(0xFFF7F8F3), onBackground = Color(0xFF1D3025),
            surface = Color(0xFFF7F8F3), onSurface = Color(0xFF1D3025),
            surfaceVariant = Color(0xFFEDF2E9), onSurfaceVariant = Color(0xFF5B6D60),
            surfaceTint = Color(0xFF287653),
            surfaceDim = Color(0xFFE4E8DE), surfaceBright = Color(0xFFFFFFFF),
            surfaceContainerLowest = Color(0xFFFFFFFF),
            surfaceContainerLow = Color(0xFFFFFFFF),
            surfaceContainer = Color(0xFFEDF2E9),
            surfaceContainerHigh = Color(0xFFE7EEE3),
            // Filled Card container color token: white, not the pale-green off-white.
            surfaceContainerHighest = Color(0xFFFFFFFF),
            inverseSurface = Color(0xFF1D3025), inverseOnSurface = Color(0xFFF7F8F3),

            outline = Color(0xFFB6C6B7), outlineVariant = Color(0xFFDCE6DB),
        ),
        shapes = Shapes(
            small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(18.dp),
            large = RoundedCornerShape(24.dp),
        ),
        content = content,
    )
}
