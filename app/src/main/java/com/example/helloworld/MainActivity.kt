package com.example.helloworld

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.awaitPointerEvent
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.layout.onSizeChanged
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    GameScreen()
                }
            }
        }
    }
}

@Composable
private fun GameScreen() {
    val density = LocalDensity.current
    val playerSpeed = with(density) { 240.dp.toPx() }
    val playerCollisionRadius = with(density) { 18.dp.toPx() }
    val playerTriangleHeight = with(density) { 32.dp.toPx() }
    val playerTriangleWidth = playerTriangleHeight * 0.9f
    val enemyRadius = with(density) { 24.dp.toPx() }
    val playerBulletRadius = with(density) { 5.dp.toPx() }
    val enemyBulletRadius = with(density) { 4.dp.toPx() }
    val playerBulletSpeed = with(density) { 320.dp.toPx() }
    val enemyBulletSpeed = with(density) { 150.dp.toPx() }

    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var ready by remember { mutableStateOf(true) }
    var running by remember { mutableStateOf(false) }
    var playerPosition by remember { mutableStateOf(Offset.Zero) }
    var joystickVector by remember { mutableStateOf(Offset.Zero) }
    var firing by remember { mutableStateOf(false) }
    var shotCooldown by remember { mutableFloatStateOf(1f) }
    val enemies = remember { mutableStateListOf<EnemyState>() }
    val playerBullets = remember { mutableStateListOf<BulletState>() }
    val enemyBullets = remember { mutableStateListOf<BulletState>() }
    var nextEnemyId by remember { mutableIntStateOf(0) }
    var nextBulletId by remember { mutableIntStateOf(0) }

    fun resetPlayerPosition() {
        if (canvasSize != IntSize.Zero) {
            playerPosition = Offset(
                canvasSize.width / 2f,
                canvasSize.height * 0.8f
            )
        }
    }

    fun resetGame() {
        running = false
        ready = true
        enemies.clear()
        playerBullets.clear()
        enemyBullets.clear()
        shotCooldown = 1f
        firing = false
        joystickVector = Offset.Zero
        resetPlayerPosition()
    }

    fun startGame() {
        if (canvasSize == IntSize.Zero) return
        ready = false
        running = true
        enemies.clear()
        playerBullets.clear()
        enemyBullets.clear()
        shotCooldown = 1f
        firing = false
        joystickVector = Offset.Zero
        resetPlayerPosition()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0D47A1))
                .pointerInput(ready) {
                    if (ready) {
                        awaitEachGesture {
                            awaitFirstDown()
                            startGame()
                        }
                    }
                }
                .onSizeChanged {
                    if (canvasSize != it) {
                        canvasSize = it
                        resetPlayerPosition()
                    }
                }
        ) {
            drawRect(color = Color(0xFF0D47A1), size = size)

            if (playerPosition != Offset.Zero) {
                val path = Path().apply {
                    moveTo(playerPosition.x, playerPosition.y - playerTriangleHeight / 2f)
                    lineTo(
                        playerPosition.x - playerTriangleWidth / 2f,
                        playerPosition.y + playerTriangleHeight / 2f
                    )
                    lineTo(
                        playerPosition.x + playerTriangleWidth / 2f,
                        playerPosition.y + playerTriangleHeight / 2f
                    )
                    close()
                }
                drawPath(path, Color.Red)
            }

            enemies.forEach { enemy ->
                drawRegularPolygon(
                    center = enemy.position,
                    radius = enemyRadius,
                    sides = 6,
                    color = Color(0xFFFFEB3B)
                )
            }

            playerBullets.forEach { bullet ->
                drawCircle(color = Color.Red, radius = bullet.radius, center = bullet.position)
            }

            enemyBullets.forEach { bullet ->
                drawCircle(color = Color(0xFFFFEB3B), radius = bullet.radius, center = bullet.position)
            }
        }

        if (ready) {
            ReadyOverlay()
        }

        VirtualJoystick(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(24.dp)
        ) { vector ->
            joystickVector = vector
        }

        FireButton(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
        ) { pressed ->
            firing = pressed
        }

        LaunchedEffect(running, canvasSize) {
            if (!running || canvasSize == IntSize.Zero) return@LaunchedEffect
            var previousTime = withFrameNanos { it }
            while (running && canvasSize != IntSize.Zero) {
                val frameTime = withFrameNanos { it }
                val deltaSeconds = ((frameTime - previousTime).coerceAtLeast(0L)) / 1_000_000_000f
                previousTime = frameTime

                // Move player
                val velocity = Offset(joystickVector.x * playerSpeed, joystickVector.y * playerSpeed)
                val nextPlayer = playerPosition + velocity * deltaSeconds
                val clampedX = nextPlayer.x.coerceIn(playerCollisionRadius, canvasSize.width - playerCollisionRadius)
                val clampedY = nextPlayer.y.coerceIn(playerCollisionRadius, canvasSize.height - playerCollisionRadius)
                playerPosition = Offset(clampedX, clampedY)

                // Player firing
                shotCooldown += deltaSeconds
                if (firing && shotCooldown >= 1f) {
                    val bullet = BulletState(
                        id = nextBulletId++,
                        position = Offset(playerPosition.x, playerPosition.y - playerCollisionRadius),
                        velocity = Offset(0f, -playerBulletSpeed),
                        radius = playerBulletRadius,
                        fromEnemy = false
                    )
                    playerBullets.add(bullet)
                    shotCooldown = 0f
                }

                // Update enemies
                val enemiesToRemove = mutableListOf<EnemyState>()
                enemies.forEach { enemy ->
                    enemy.noiseTime += deltaSeconds * 0.5f
                    val noise = fractalNoise(enemy.noiseTime, enemy.noiseSeed)
                    val horizontal = noise * 80f
                    val downward = 50f + enemy.noiseTime * 10f
                    val nextPosition = enemy.position + Offset(horizontal, downward) * deltaSeconds
                    val enemyX = nextPosition.x.coerceIn(enemyRadius, canvasSize.width - enemyRadius)
                    enemy.position = Offset(enemyX, nextPosition.y)

                    if (enemy.position.y - enemyRadius > canvasSize.height) {
                        enemiesToRemove.add(enemy)
                    }

                    if (enemy.burstRemaining > 0) {
                        enemy.burstTimer -= deltaSeconds
                        if (enemy.burstTimer <= 0f) {
                            val bullet = BulletState(
                                id = nextBulletId++,
                                position = enemy.position,
                                velocity = enemy.burstDirection * enemyBulletSpeed,
                                radius = enemyBulletRadius,
                                fromEnemy = true
                            )
                            enemyBullets.add(bullet)
                            enemy.burstRemaining -= 1
                            enemy.burstTimer = 0.12f
                        }
                    } else {
                        enemy.timeSinceLastShot += deltaSeconds
                        if (enemy.timeSinceLastShot >= enemy.nextShotDelay) {
                            enemy.timeSinceLastShot = 0f
                            enemy.nextShotDelay = Random.nextFloat() * 3f + 3f
                            val baseAngle = atan2(
                                playerPosition.y - enemy.position.y,
                                playerPosition.x - enemy.position.x
                            )
                            val offset = Random.nextFloat() * (PI.toFloat() / 2f) - (PI.toFloat() / 4f)
                            val angle = baseAngle + offset
                            val direction = Offset(cos(angle), sin(angle)).normalize()
                            enemy.burstDirection = direction
                            enemy.burstRemaining = Random.nextInt(3, 11)
                            enemy.burstTimer = 0f
                        }
                    }
                }
                if (enemiesToRemove.isNotEmpty()) {
                    enemies.removeAll(enemiesToRemove)
                }

                // Update enemy bullets
                for (index in enemyBullets.indices.reversed()) {
                    val bullet = enemyBullets[index]
                    bullet.position += bullet.velocity * deltaSeconds
                    val outOfBounds = bullet.position.x + bullet.radius < 0f ||
                        bullet.position.x - bullet.radius > canvasSize.width ||
                        bullet.position.y + bullet.radius < 0f ||
                        bullet.position.y - bullet.radius > canvasSize.height
                    val hitPlayer = (bullet.position - playerPosition).magnitude() <= bullet.radius + playerCollisionRadius
                    if (outOfBounds || hitPlayer) {
                        enemyBullets.removeAt(index)
                    }
                    if (hitPlayer) {
                        resetGame()
                        break
                    }
                }
                if (!running) break

                // Update player bullets
                val defeatedEnemies = mutableSetOf<EnemyState>()
                for (index in playerBullets.indices.reversed()) {
                    val bullet = playerBullets[index]
                    bullet.position += bullet.velocity * deltaSeconds
                    if (bullet.position.y + bullet.radius < 0f) {
                        playerBullets.removeAt(index)
                        continue
                    }
                    var hitEnemy: EnemyState? = null
                    enemies.forEach { enemy ->
                        if ((bullet.position - enemy.position).magnitude() <= bullet.radius + enemyRadius) {
                            hitEnemy = enemy
                        }
                    }
                    if (hitEnemy != null) {
                        playerBullets.removeAt(index)
                        hitEnemy!!.hp -= 1
                        if (hitEnemy!!.hp <= 0) {
                            defeatedEnemies.add(hitEnemy!!)
                        }
                    }
                }
                if (defeatedEnemies.isNotEmpty()) {
                    enemies.removeAll(defeatedEnemies)
                }
            }
        }

        LaunchedEffect(running, canvasSize) {
            if (!running || canvasSize == IntSize.Zero) return@LaunchedEffect
            coroutineScope {
                repeat(3) {
                    launch {
                        while (isActive && running) {
                            delay(Random.nextLong(3000, 10001))
                            if (!running || canvasSize == IntSize.Zero) continue
                            val x = Random.nextFloat() * (canvasSize.width - 2 * enemyRadius) + enemyRadius
                            val y = -enemyRadius - Random.nextFloat() * enemyRadius
                            enemies.add(
                                EnemyState(
                                    id = nextEnemyId++,
                                    position = Offset(x, y),
                                    noiseSeed = Random.nextFloat() * 1000f
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BoxScope.ReadyOverlay() {
    Box(modifier = Modifier.align(Alignment.Center)) {
        Text(
            text = "READY!",
            fontSize = 48.sp,
            fontWeight = FontWeight.Black,
            color = Color.White,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun VirtualJoystick(
    modifier: Modifier = Modifier,
    onVectorChange: (Offset) -> Unit
) {
    val density = LocalDensity.current
    val outerRadius = with(density) { 60.dp.toPx() }
    val innerRadius = with(density) { 22.dp.toPx() }
    var knobOffset by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            .size(140.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val center = Offset(size.width / 2f, size.height / 2f)
                    var current = down
                    do {
                        val offset = current.position - center
                        val clamped = clampOffset(offset, outerRadius)
                        knobOffset = clamped
                        onVectorChange(clamped / outerRadius)
                        val event = awaitPointerEvent()
                        current = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (current.changedToUp()) break
                    } while (current.pressed)
                    knobOffset = Offset.Zero
                    onVectorChange(Offset.Zero)
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(color = Color(0x33FFFFFF), radius = outerRadius, center = center)
            drawCircle(
                color = Color(0x55FFFFFF),
                radius = innerRadius,
                center = center + knobOffset
            )
        }
    }
}

@Composable
private fun FireButton(
    modifier: Modifier = Modifier,
    onPressedChange: (Boolean) -> Unit
) {
    Box(
        modifier = modifier
            .size(96.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    onPressedChange(true)
                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change == null || change.changedToUp()) {
                            break
                        }
                    } while (true)
                    onPressedChange(false)
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(color = Color(0x88FFFFFF), center = center, radius = size.minDimension / 2f)
            drawCircle(color = Color(0xFFFF1744), center = center, radius = size.minDimension / 2.6f)
        }
    }
}

private fun DrawScope.drawRegularPolygon(center: Offset, radius: Float, sides: Int, color: Color) {
    val path = Path()
    for (i in 0 until sides) {
        val angle = (2 * PI * i / sides - PI / 2).toFloat()
        val point = Offset(
            center.x + radius * cos(angle),
            center.y + radius * sin(angle)
        )
        if (i == 0) {
            path.moveTo(point.x, point.y)
        } else {
            path.lineTo(point.x, point.y)
        }
    }
    path.close()
    drawPath(path, color)
}

private fun fractalNoise(time: Float, seed: Float): Float {
    var total = 0f
    var amplitude = 1f
    var frequency = 1f
    repeat(3) { octave ->
        val value = seed * (octave + 1) * 0.15f + time * frequency
        total += amplitude * combinedNoise(value)
        amplitude *= 0.5f
        frequency *= 2f
    }
    return total.coerceIn(-1f, 1f)
}

private fun combinedNoise(value: Float): Float {
    return (sin(value) + cos(value * 1.3f) + sin(value * 0.7f + 1.1f)) / 3f
}

private fun clampOffset(offset: Offset, maxDistance: Float): Offset {
    val distance = sqrt(offset.x * offset.x + offset.y * offset.y)
    return if (distance <= maxDistance || distance == 0f) {
        offset
    } else {
        val scale = maxDistance / distance
        Offset(offset.x * scale, offset.y * scale)
    }
}

private operator fun Offset.plus(other: Offset): Offset = Offset(x + other.x, y + other.y)

private operator fun Offset.times(scale: Float): Offset = Offset(x * scale, y * scale)

private fun Offset.magnitude(): Float = sqrt(x.pow(2) + y.pow(2))

private fun Offset.normalize(): Offset {
    val length = magnitude()
    if (length == 0f) return Offset.Zero
    return Offset(x / length, y / length)
}

private class EnemyState(
    val id: Int,
    position: Offset,
    val noiseSeed: Float
) {
    var position by mutableStateOf(position)
    var hp by mutableStateOf(3)
    var timeSinceLastShot: Float = 0f
    var nextShotDelay: Float = Random.nextFloat() * 3f + 3f
    var burstRemaining: Int = 0
    var burstTimer: Float = 0f
    var burstDirection: Offset = Offset.Zero
    var noiseTime: Float = Random.nextFloat() * 10f
}

private class BulletState(
    val id: Int,
    position: Offset,
    val velocity: Offset,
    val radius: Float,
    val fromEnemy: Boolean
) {
    var position by mutableStateOf(position)
}

