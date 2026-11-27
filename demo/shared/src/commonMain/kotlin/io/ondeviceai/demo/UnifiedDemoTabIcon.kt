package io.ondeviceai.demo

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp

@Composable
internal fun UnifiedDemoTabIcon(tab: DemoTab) {
    val tint = LocalContentColor.current

    Canvas(modifier = Modifier.size(24.dp)) {
        val scale = size.minDimension / 24f
        withTransform({
            scale(scaleX = scale, scaleY = scale, pivot = Offset.Zero)
        }) {
            val stroke = Stroke(
                width = 1.8f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            )

            when (tab) {
                DemoTab.EXPLORE -> {
                    drawCircle(
                        color = tint,
                        center = Offset(12f, 12f),
                        radius = 9f,
                        style = stroke,
                    )
                    drawPath(
                        path = Path().apply {
                            moveTo(16f, 8f)
                            lineTo(13.5f, 13.5f)
                            lineTo(8f, 16f)
                            lineTo(10.5f, 10.5f)
                            close()
                        },
                        color = tint,
                        style = stroke,
                    )
                }

                DemoTab.MODELS -> {
                    drawPath(
                        path = Path().apply {
                            moveTo(3f, 8f)
                            lineTo(12f, 3.5f)
                            lineTo(21f, 8f)
                            lineTo(12f, 12.5f)
                            close()
                        },
                        color = tint,
                        style = stroke,
                    )
                    drawPath(
                        path = Path().apply {
                            moveTo(3f, 12f)
                            lineTo(12f, 16.5f)
                            lineTo(21f, 12f)
                        },
                        color = tint,
                        style = stroke,
                    )
                    drawPath(
                        path = Path().apply {
                            moveTo(3f, 16f)
                            lineTo(12f, 20.5f)
                            lineTo(21f, 16f)
                        },
                        color = tint,
                        style = stroke,
                    )
                }

                DemoTab.RECORDS -> {
                    drawArc(
                        color = tint,
                        startAngle = 194f,
                        sweepAngle = 317f,
                        useCenter = false,
                        topLeft = Offset(3.4f, 3.4f),
                        size = Size(17.2f, 17.2f),
                        style = stroke,
                    )
                    drawPath(
                        path = Path().apply {
                            moveTo(3.8f, 5.8f)
                            lineTo(3.8f, 10.5f)
                            lineTo(8.5f, 10.5f)
                        },
                        color = tint,
                        style = stroke,
                    )
                    drawPath(
                        path = Path().apply {
                            moveTo(12f, 7.2f)
                            lineTo(12f, 12f)
                            lineTo(15.6f, 14f)
                        },
                        color = tint,
                        style = stroke,
                    )
                }
            }
        }
    }
}
