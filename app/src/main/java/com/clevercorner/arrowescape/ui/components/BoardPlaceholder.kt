package com.clevercorner.arrowescape.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Stands in for the board while the next level is being built.
 *
 * Without it the screen kept drawing the *previous* level's arrows until
 * generation finished - so a level change looked like a freeze followed by a
 * pop, and sometimes like an empty board that filled itself in later. An
 * honest spinner for 80ms beats a lying board for 80ms.
 *
 * Hand-rolled rather than Material3's `CircularProgressIndicator` on
 * purpose: that composable's indeterminate mode drives its rotation through
 * a `keyframes` spec, and on this project's resolved dependency graph that
 * path throws `NoSuchMethodError` on `KeyframesSpecConfig.at(...)` - a
 * version skew between `material3` and `animation-core` that crashed both
 * the curved and daily screens the moment this file first rendered. A plain
 * `drawArc` driven by `animateFloat` needs none of that machinery, and it is
 * the same technique `DailyHud`'s shuffle ring already uses successfully.
 */
@Composable
fun BoardPlaceholder(modifier: Modifier = Modifier) {
    val spin = rememberInfiniteTransition(label = "boardPlaceholderSpin")
    val angle by spin.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
        label = "spinAngle"
    )
    val color = MaterialTheme.colorScheme.primary

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(36.dp)) {
            val stroke = size.minDimension * 0.09f
            drawArc(
                color = color,
                startAngle = angle,
                sweepAngle = 270f,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }
    }
}
