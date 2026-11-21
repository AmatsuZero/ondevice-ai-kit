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
            primary = Color(0xFF287653), onPrimary = Color.White,
            primaryContainer = Color(0xFFE1F2E5), onPrimaryContainer = Color(0xFF163B29),
            secondary = Color(0xFF5C7563), secondaryContainer = Color(0xFFE5ECE4),
            background = Color(0xFFF7F8F3), surface = Color(0xFFF7F8F3),
            surfaceContainer = Color(0xFFEDF2E9), surfaceContainerLow = Color.White,
            onSurface = Color(0xFF1D3025), onSurfaceVariant = Color(0xFF5B6D60),
            outline = Color(0xFFB6C6B7), outlineVariant = Color(0xFFDCE6DB),
        ),
        shapes = Shapes(
            small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(18.dp),
            large = RoundedCornerShape(24.dp),
        ),
        content = content,
    )
}
