package io.github.demiterhugo.debuglens.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.demiterhugo.debuglens.R

@Composable
internal fun AnalysisHero(
    importing: Boolean,
    enabled: Boolean,
    onImport: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(Brush.horizontalGradient(listOf(Color(0xFF102429), Color(0xFF143D3A)))),
    ) {
        TraceArtwork(Modifier.matchParentSize())
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.home_input_title),
                modifier = Modifier.widthIn(max = 245.dp).semantics { heading() },
                style = MaterialTheme.typography.headlineMedium,
                color = Color(0xFFF3FAF7),
            )
            Button(
                onClick = onImport,
                enabled = enabled,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFA6EBD7),
                    contentColor = Color(0xFF10362F),
                    disabledContainerColor = Color(0xFF355651),
                    disabledContentColor = Color(0xFFD3E7E1),
                ),
                modifier = Modifier.testTag("import_logcat"),
            ) {
                Icon(painterResource(R.drawable.ic_import_log), contentDescription = null, Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(stringResource(if (importing) R.string.action_importing_logcat else R.string.action_import_logcat))
            }
        }
    }
}

/** Decorative signal stays behind the heading and the import action. */
@Composable
private fun TraceArtwork(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val left = size.width * 0.53f
        val width = size.width - left
        val step = 15.dp.toPx()
        val gridColor = Color(0xFF8DD0BC).copy(alpha = 0.15f)
        var x = left
        while (x < size.width) {
            drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.dp.toPx())
            x += step
        }
        var y = step
        while (y < size.height) {
            drawLine(gridColor, Offset(left, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
            y += step
        }
        val peak = Offset(left + width * 0.49f, size.height * 0.42f)
        val trace = Path().apply {
            moveTo(left, size.height * 0.68f)
            lineTo(left + width * 0.24f, size.height * 0.68f)
            lineTo(left + width * 0.33f, size.height * 0.57f)
            lineTo(left + width * 0.39f, size.height * 0.76f)
            lineTo(peak.x, peak.y)
            lineTo(left + width * 0.64f, size.height * 0.79f)
            lineTo(left + width * 0.74f, size.height * 0.63f)
            lineTo(size.width, size.height * 0.63f)
        }
        drawPath(
            path = trace,
            brush = Brush.horizontalGradient(
                listOf(Color.Transparent, Color(0xFF83DEBE).copy(alpha = 0.78f)),
                startX = left,
                endX = size.width,
            ),
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        drawCircle(Color(0xFFFFBE90).copy(alpha = 0.17f), radius = 8.dp.toPx(), center = peak)
        drawCircle(Color(0xFFFFBE90), radius = 2.5.dp.toPx(), center = peak)
    }
}
