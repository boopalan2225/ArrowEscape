package com.example.arrowescape.ui.home

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.arrowescape.data.GamePreferences
import com.example.arrowescape.ui.theme.ArrowEscapeTheme

@Composable
fun HomeScreen(
    prefs: GamePreferences.Prefs,
    onPlayClick: () -> Unit,
    onJourneyClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val gameColors = ArrowEscapeTheme.gameColors

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.weight(0.5f))

            // Animated logo area
            AnimatedLogo()

            Spacer(modifier = Modifier.height(24.dp))

            // Title with glow effect
            Text(
                text = "ARROW",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Black,
                color = Color(0xFF4D88FF),
                letterSpacing = 6.sp,
                modifier = Modifier.shadow(
                    elevation = 20.dp,
                    ambientColor = Color(0xFF4D88FF).copy(alpha = 0.6f),
                    spotColor = Color(0xFF4D88FF).copy(alpha = 0.6f)
                )
            )
            Text(
                text = "ESCAPE",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Black,
                color = Color(0xFFEEF2FF),
                letterSpacing = 6.sp
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Subtitle
            Text(
                text = "TAP  ·  VANISH  ·  ESCAPE",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF3A4A6C),
                letterSpacing = 3.sp
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Level info card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.shadow(4.dp, RoundedCornerShape(16.dp))
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "LEVEL",
                        style = MaterialTheme.typography.labelMedium,
                        color = gameColors.textSecondary,
                        letterSpacing = 2.sp
                    )
                    Text(
                        text = "${prefs.currentLevel}",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    if (prefs.highestLevel > prefs.currentLevel) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Best: ${prefs.highestLevel}",
                            style = MaterialTheme.typography.bodySmall,
                            color = gameColors.textSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(0.5f))

            // Play button with pulse animation
            PulsatingPlayButton(onClick = onPlayClick)

            Spacer(modifier = Modifier.height(16.dp))

            // Journey / milestone map button
            OutlinedButton(
                onClick = onJourneyClick,
                modifier = Modifier
                    .width(200.dp)
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(text = "\uD83D\uDDFA", fontSize = 18.sp)
                    Text(
                        text = "Journey Map",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Settings button
            TextButton(
                onClick = onSettingsClick,
                modifier = Modifier.padding(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "\u2699",
                        fontSize = 20.sp,
                        color = gameColors.textSecondary
                    )
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.bodyLarge,
                        color = gameColors.textSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Stats summary
            if (prefs.totalLevelsCompleted > 0) {
                Text(
                    text = "${prefs.totalLevelsCompleted} levels completed",
                    style = MaterialTheme.typography.bodySmall,
                    color = gameColors.textSecondary.copy(alpha = 0.7f)
                )
            }

            Spacer(modifier = Modifier.weight(0.3f))
        }
    }
}

@Composable
private fun AnimatedLogo(onClick: (() -> Unit)? = null) {
    // Touch interaction state
    var isPressed by remember { mutableStateOf(false) }
    
    // Entry animation
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    
    val entryScale by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessLow),
        label = "entryScale"
    )
    
    // Press animation - 3D push effect
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
        label = "pressScale"
    )
    val pressDepth by animateFloatAsState(
        targetValue = if (isPressed) 2f else 8f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
        label = "pressDepth"
    )
    
    // Continuous 3D rotation animation
    val infiniteTransition = rememberInfiniteTransition(label = "3d")
    val rotationY by infiniteTransition.animateFloat(
        initialValue = -8f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rotateY"
    )
    val rotationX by infiniteTransition.animateFloat(
        initialValue = 5f,
        targetValue = -5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rotateX"
    )
    
    // Glow pulse
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    Box(
        modifier = Modifier
            .scale(entryScale * pressScale)
            .graphicsLayer {
                this.rotationY = rotationY
                this.rotationX = rotationX
                cameraDistance = 12f * density
            },
        contentAlignment = Alignment.Center
    ) {
        // Outer glow ring
        Box(
            modifier = Modifier
                .size(110.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF3B82F6).copy(alpha = glowAlpha),
                            Color.Transparent
                        )
                    ),
                    CircleShape
                )
        )
        
        // 3D Shadow (offset for depth)
        Box(
            modifier = Modifier
                .size(88.dp)
                .offset(x = pressDepth.dp * 0.3f, y = pressDepth.dp * 0.5f)
                .background(Color(0xFF0F172A), CircleShape)
        )
        
        // Main circular logo
        Box(
            modifier = Modifier
                .size(88.dp)
                .shadow(elevation = pressDepth.dp, shape = CircleShape)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF1E40AF),
                            Color(0xFF3B82F6),
                            Color(0xFF60A5FA)
                        ),
                        start = androidx.compose.ui.geometry.Offset.Zero,
                        end = androidx.compose.ui.geometry.Offset.Infinite
                    )
                )
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            isPressed = true
                            tryAwaitRelease()
                            isPressed = false
                        },
                        onTap = { onClick?.invoke() }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            // Inner highlight (3D sphere effect)
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.25f),
                                Color.Transparent
                            ),
                            center = androidx.compose.ui.geometry.Offset(30f, 25f),
                            radius = 80f
                        ),
                        CircleShape
                    )
            )
            
            // Escaping arrow with 3D effect
            Canvas(modifier = Modifier.size(46.dp)) {
                // Arrow shadow
                drawEscapeArrow(
                    color = Color(0xFF1E3A8A).copy(alpha = 0.5f),
                    offsetX = 2.dp.toPx(),
                    offsetY = 2.dp.toPx()
                )
                // Main arrow
                drawEscapeArrow(
                    color = Color.White,
                    offsetX = 0f,
                    offsetY = 0f
                )
            }
        }
    }
}

// Extension function to draw the escape arrow
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawEscapeArrow(
    color: Color,
    offsetX: Float,
    offsetY: Float
) {
    val strokeWidth = 4.5f.dp.toPx()
    val centerY = size.height / 2 + offsetY
    val startX = 10.dp.toPx() + offsetX
    val endX = size.width - 10.dp.toPx() + offsetX
    val arrowHeadSize = 10.dp.toPx()
    
    // Arrow shaft
    drawLine(
        color = color,
        start = androidx.compose.ui.geometry.Offset(startX, centerY),
        end = androidx.compose.ui.geometry.Offset(endX - arrowHeadSize * 0.3f, centerY),
        strokeWidth = strokeWidth,
        cap = androidx.compose.ui.graphics.StrokeCap.Round
    )
    
    // Arrow head (chevron)
    val headPath = androidx.compose.ui.graphics.Path().apply {
        moveTo(endX - arrowHeadSize, centerY - arrowHeadSize * 0.6f)
        lineTo(endX, centerY)
        lineTo(endX - arrowHeadSize, centerY + arrowHeadSize * 0.6f)
    }
    drawPath(
        path = headPath,
        color = color,
        style = androidx.compose.ui.graphics.drawscope.Stroke(
            width = strokeWidth,
            cap = androidx.compose.ui.graphics.StrokeCap.Round,
            join = androidx.compose.ui.graphics.StrokeJoin.Round
        )
    )
}

@Composable
private fun PulsatingPlayButton(onClick: () -> Unit) {
    // Pulse animation
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    Box(contentAlignment = Alignment.Center) {
        // Glow effect
        Box(
            modifier = Modifier
                .size(180.dp)
                .scale(scale)
                .clip(RoundedCornerShape(28.dp))
                .background(
                    MaterialTheme.colorScheme.primary.copy(alpha = glowAlpha * 0.3f)
                )
        )

        // Main button
        Button(
            onClick = onClick,
            modifier = Modifier
                .width(160.dp)
                .height(56.dp)
                .scale(scale),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            ),
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = 8.dp,
                pressedElevation = 12.dp
            )
        ) {
            Text(
                text = "PLAY",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
        }
    }
}

private val EaseInOutCubic = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)
