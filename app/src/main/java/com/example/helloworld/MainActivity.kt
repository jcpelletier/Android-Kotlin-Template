package com.example.helloworld

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.isActive
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sqrt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    GameScreen()
                }
            }
        }
    }
}

@Composable
private fun GameScreen() {
    val density = LocalDensity.current
    val triangleSide = 80.dp
    val triangleSidePx = with(density) { triangleSide.toPx() }
    val halfBase = triangleSidePx / 2f
    val triangleHeight = triangleSidePx * sqrt(3f) / 2f
    val topOffset = triangleHeight * 2f / 3f
    val bottomOffset = triangleHeight / 3f

    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var triangleCenter by remember { mutableStateOf(Offset.Zero) }
    var joystickInput by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(containerSize) {
        if (containerSize != IntSize.Zero) {
            triangleCenter = Offset(containerSize.width / 2f, containerSize.height / 2f)
        }
    }

    LaunchedEffect(containerSize) {
        if (containerSize == IntSize.Zero) return@LaunchedEffect
        var lastTimestamp = 0L
        while (isActive) {
            withFrameNanos { timestamp ->
                if (lastTimestamp != 0L) {
                    val deltaSeconds = (timestamp - lastTimestamp) / 1_000_000_000f
                    val speed = 500f
                    val input = joystickInput
                    val delta = Offset(input.x * speed * deltaSeconds, input.y * speed * deltaSeconds)
                    val proposedCenter = triangleCenter + delta
                    triangleCenter = proposedCenter.coerceWithin(
                        minX = halfBase,
                        maxX = containerSize.width - halfBase,
                        minY = topOffset,
                        maxY = containerSize.height - bottomOffset
                    )
                }
                lastTimestamp = timestamp
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF101010))
            .onGloballyPositioned { layoutCoordinates ->
                containerSize = layoutCoordinates.size
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (containerSize != IntSize.Zero && triangleCenter != Offset.Zero) {
                drawTriangle(center = triangleCenter, side = triangleSidePx, color = Color.Red)
            }
        }

        Joystick(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 24.dp, bottom = 24.dp),
            onOffsetChanged = { joystickInput = it }
        )
    }
}

@Composable
private fun Joystick(
    modifier: Modifier = Modifier,
    radius: Dp = 80.dp,
    onOffsetChanged: (Offset) -> Unit
) {
    val density = LocalDensity.current
    val radiusPx = with(density) { radius.toPx() }
    val knobRadiusDp = radius * 0.5f
    var knobPosition by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            .size(radius * 2)
            .pointerInput(radiusPx) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val relative = offset - Offset(radiusPx, radiusPx)
                        knobPosition = relative.coerceInCircle(radiusPx)
                        onOffsetChanged(knobPosition.normalized(radiusPx))
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val newPosition = knobPosition + Offset(dragAmount.x, dragAmount.y)
                        knobPosition = newPosition.coerceInCircle(radiusPx)
                        onOffsetChanged(knobPosition.normalized(radiusPx))
                    },
                    onDragEnd = {
                        knobPosition = Offset.Zero
                        onOffsetChanged(Offset.Zero)
                    },
                    onDragCancel = {
                        knobPosition = Offset.Zero
                        onOffsetChanged(Offset.Zero)
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(color = Color.White.copy(alpha = 0.2f), radius = radiusPx)
        }

        Box(
            modifier = Modifier
                .size(knobRadiusDp * 2)
                .align(Alignment.Center)
                .offset { IntOffset(knobPosition.x.roundToInt(), knobPosition.y.roundToInt()) }
                .background(Color.White.copy(alpha = 0.5f), CircleShape)
        )
    }
}

private fun DrawScope.drawTriangle(center: Offset, side: Float, color: Color) {
    val halfBase = side / 2f
    val height = side * sqrt(3f) / 2f
    val path = Path().apply {
        val top = Offset(center.x, center.y - (2f * height / 3f))
        val bottomLeft = Offset(center.x - halfBase, center.y + height / 3f)
        val bottomRight = Offset(center.x + halfBase, center.y + height / 3f)
        moveTo(top.x, top.y)
        lineTo(bottomLeft.x, bottomLeft.y)
        lineTo(bottomRight.x, bottomRight.y)
        close()
    }
    drawPath(path = path, color = color)
}

private fun Offset.coerceInCircle(radius: Float): Offset {
    val distance = hypot(x, y)
    return if (distance > radius) {
        val scale = radius / distance
        Offset(x * scale, y * scale)
    } else {
        this
    }
}

private fun Offset.coerceWithin(minX: Float, maxX: Float, minY: Float, maxY: Float): Offset {
    val clampedX = x.coerceIn(minX, maxX)
    val clampedY = y.coerceIn(minY, maxY)
    return Offset(clampedX, clampedY)
}

private fun Offset.normalized(radius: Float): Offset {
    if (radius == 0f) return Offset.Zero
    return Offset(x / radius, y / radius)
}

