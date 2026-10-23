package com.clevercorner.arrowescape.ui.milestone

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clevercorner.arrowescape.game.level.LevelShape
import com.clevercorner.arrowescape.ui.theme.ArrowEscapeTheme

/**
 * Renders a LevelShape as a small filled icon using its cell mask.
 * Gives each milestone a recognizable silhouette (heart, star, circle...).
 */
@Composable
fun ShapeIcon(
    shape: LevelShape,
    color: Color,
    modifier: Modifier = Modifier,
    resolution: Int = 11
) {
    Canvas(modifier = modifier) {
        val cells = shape.validCells(resolution, resolution)
        if (cells.isEmpty()) return@Canvas

        val cell = minOf(size.width, size.height) / resolution
        val gridSize = cell * resolution
        val offX = (size.width - gridSize) / 2f
        val offY = (size.height - gridSize) / 2f
        val dot = cell * 0.42f

        cells.forEach { (r, c) ->
            val cx = offX + c * cell + cell / 2
            val cy = offY + r * cell + cell / 2
            drawCircle(color = color, radius = dot, center = Offset(cx, cy))
        }
    }
}

/**
 * Draws a filled 5-point star (used for milestone rewards / ratings).
 */
fun DrawScope.drawStar(center: Offset, radius: Float, color: Color) {
    val path = Path()
    val points = 5
    val innerRadius = radius * 0.42f
    for (i in 0 until points * 2) {
        val r = if (i % 2 == 0) radius else innerRadius
        val angle = Math.PI / points * i - Math.PI / 2
        val x = center.x + (r * kotlin.math.cos(angle)).toFloat()
        val y = center.y + (r * kotlin.math.sin(angle)).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, color, style = Fill)
}

// ======================= PREVIEWS =======================

/**
 * Preview showing ALL LevelShapes in a grid - essential for debugging shape masks.
 */
@Preview(showBackground = true, name = "All Shapes Grid", widthDp = 400, heightDp = 800)
@Composable
private fun AllShapesPreview() {
    ArrowEscapeTheme(darkTheme = false) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "All LevelShapes (${LevelShape.entries.size} total)",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(LevelShape.entries) { shape ->
                        ShapePreviewItem(shape)
                    }
                }
            }
        }
    }
}

@Composable
private fun ShapePreviewItem(shape: LevelShape) {
    val isHard = shape in listOf(
        LevelShape.SPIRAL, LevelShape.LIGHTNING, LevelShape.CROWN, 
        LevelShape.RING, LevelShape.BUTTERFLY
    )
    val color = if (isHard) Color(0xFFE91E63) else Color(0xFF4285F4)
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .background(
                if (isHard) Color(0xFFFFE0E0) else Color(0xFFE3F2FD),
                RoundedCornerShape(8.dp)
            )
            .padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .background(Color.White, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            ShapeIcon(
                shape = shape,
                color = color,
                modifier = Modifier.size(50.dp),
                resolution = 12
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = shape.displayName,
            fontSize = 10.sp,
            fontWeight = if (isHard) FontWeight.Bold else FontWeight.Normal,
            color = color,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

@Preview(showBackground = true, name = "Hard Shapes Only", widthDp = 360)
@Composable
private fun HardShapesPreview() {
    val hardShapes = listOf(
        LevelShape.SPIRAL, LevelShape.LIGHTNING, LevelShape.CROWN,
        LevelShape.RING, LevelShape.BUTTERFLY
    )
    
    ArrowEscapeTheme(darkTheme = true) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "HARD SHAPES",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFE91E63),
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    hardShapes.forEach { shape ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .background(Color(0xFF2A2A2A), RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                ShapeIcon(
                                    shape = shape,
                                    color = Color(0xFFFF6B6B),
                                    modifier = Modifier.size(48.dp),
                                    resolution = 14 // Higher res for detail
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = shape.displayName,
                                fontSize = 9.sp,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, name = "Single Shape Large")
@Composable
private fun SingleShapeLargePreview() {
    ArrowEscapeTheme {
        Surface(modifier = Modifier.size(200.dp)) {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Change this to debug a specific shape!
                val debugShape = LevelShape.SPIRAL
                
                ShapeIcon(
                    shape = debugShape,
                    color = Color(0xFF4285F4),
                    modifier = Modifier.size(120.dp),
                    resolution = 16 // High resolution for debugging
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = debugShape.displayName,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Preview(showBackground = true, name = "Shape Scaling - Crown at Different Levels", widthDp = 400)
@Composable
private fun ShapeScalingPreview() {
    // Demonstrates how the same shape looks at different difficulty levels
    val levels = listOf(112 to 18, 130 to 20, 148 to 24) // level to grid size
    
    ArrowEscapeTheme(darkTheme = true) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "CROWN - Scaling by Level",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFFD700),
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    levels.forEach { (level, gridSize) ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "Lvl $level",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White
                            )
                            Text(
                                "${gridSize}x$gridSize",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Gray
                            )
                            Spacer(Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .size(100.dp)
                                    .background(Color(0xFF1A1A2E), RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                ShapeIcon(
                                    shape = LevelShape.CROWN,
                                    color = Color(0xFFFFD700),
                                    modifier = Modifier.size(80.dp),
                                    resolution = gridSize
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
