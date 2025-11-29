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
import androidx.compose.runtime.ExperimentalComposeApi
import androidx.compose.runtime.*
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.awaitPointerEventScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInParent
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

@OptIn(ExperimentalComposeApi::class)
@Composable
private fun GameScreen() {
    val density = LocalDensity.current
    val triangleSide = 40.dp
    val triangleSidePx = with(density) { triangleSide.toPx() }
    val halfBase = triangleSidePx / 2f
    val triangleHeight = triangleSidePx * sqrt(3f) / 2f
    val topOffset = triangleHeight * 2f / 3f
    val bottomOffset = triangleHeight / 3f
    val projectileSpeed = 750f * 4f
    val projectileSize = with(density) { Offset(12.dp.toPx(), 20.dp.toPx()) }

    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var triangleCenter by remember { mutableStateOf(Offset.Zero) }
    var joystickInput by remember { mutableStateOf(Offset.Zero) }
    val projectiles = remember { mutableStateListOf<Projectile>() }
    var joystickBounds by remember { mutableStateOf<Rect?>(null) }
    var lastShotTimeMillis by remember { mutableStateOf(0L) }

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
                    val speed = 750f
                    val input = joystickInput
                    val delta = Offset(input.x * speed * deltaSeconds, input.y * speed * deltaSeconds)
                    val proposedCenter = triangleCenter + delta
                    triangleCenter = proposedCenter.coerceWithin(
                        minX = halfBase,
                        maxX = containerSize.width - halfBase,
                        minY = topOffset,
                        maxY = containerSize.height - bottomOffset
                    )

                    val updatedProjectiles = projectiles.mapNotNull { projectile ->
                        val nextCenter = projectile.center + Offset(0f, -projectileSpeed * deltaSeconds)
                        val newProjectile = projectile.copy(center = nextCenter)
                        val isOutOfBounds = nextCenter.y + projectileSize.y / 2f < 0f
                        if (isOutOfBounds) null else newProjectile
                    }
                    projectiles.clear()
                    projectiles.addAll(updatedProjectiles)
                }
                lastTimestamp = timestamp
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF101010))
            .pointerInput(containerSize, joystickBounds, triangleCenter) {
                awaitPointerEventScope {
                    val pointerStartsInJoystick = mutableMapOf<PointerId, Boolean>()
                    while (true) {
                        val event = awaitPointerEvent()
                        event.changes.forEach { change ->
                            if (change.changedToDownIgnoreConsumed()) {
                                val startedInJoystick = joystickBounds?.contains(change.position) == true
                                pointerStartsInJoystick[change.id] = startedInJoystick
                            }
                            if (change.changedToUpIgnoreConsumed()) {
                                val startedInJoystick = pointerStartsInJoystick.remove(change.id) ?: false
                                if (!startedInJoystick && triangleCenter != Offset.Zero) {
                                    val shotIntervalMillis = 333L
                                    if (change.uptimeMillis - lastShotTimeMillis >= shotIntervalMillis) {
                                        projectiles.add(Projectile(center = triangleCenter, size = projectileSize))
                                        lastShotTimeMillis = change.uptimeMillis
                                    }
                                }
                            }
                            if (change.previousPressed && !change.pressed) {
                                pointerStartsInJoystick.remove(change.id)
                            }
                        }
                    }
                }
            }
            .onGloballyPositioned { layoutCoordinates ->
                containerSize = layoutCoordinates.size
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (containerSize != IntSize.Zero && triangleCenter != Offset.Zero) {
                drawTriangle(center = triangleCenter, side = triangleSidePx, color = Color.Red)
                projectiles.forEach { projectile ->
                    drawOval(
                        color = Color.Yellow,
                        topLeft = projectile.center - Offset(projectile.size.x / 2f, projectile.size.y / 2f),
                        size = Size(projectile.size.x, projectile.size.y)
                    )
                }
            }
        }

        Joystick(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 24.dp, bottom = 24.dp),
            onOffsetChanged = { joystickInput = it },
            onBoundsChanged = { joystickBounds = it }
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun Joystick(
    modifier: Modifier = Modifier,
    radius: Dp = 80.dp,
    onOffsetChanged: (Offset) -> Unit,
    onBoundsChanged: (Rect) -> Unit
) {
    val density = LocalDensity.current
    val radiusPx = with(density) { radius.toPx() }
    val knobRadiusDp = radius * 0.5f
    var knobPosition by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            .size(radius * 2)
            .onGloballyPositioned { coordinates ->
                onBoundsChanged(coordinates.boundsInParent())
            }
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

private data class Projectile(
    val center: Offset,
    val size: Offset
)

private fun Offset.normalized(radius: Float): Offset {
    if (radius == 0f) return Offset.Zero
    return Offset(x / radius, y / radius)
}

