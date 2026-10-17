package com.l0gic0l.casiocalc.ui.effects

import android.animation.ValueAnimator
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.flow.first
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Soft glowing dots that drift and twinkle. Touches push nearby dots away and scatter a few
 * sparks; [burst] adds a short-lived shower at a point (used when a result appears).
 * All positions are in pixels of the field.
 */
@Stable
class ParticleState(private val seed: Int = 7) {
    internal val dust = ArrayList<Particle>()
    internal val sparks = ArrayList<Particle>()
    internal var width = 0f
    internal var height = 0f
    private val random = Random(seed)

    /** Bumped every frame so the canvas redraws without recomposing anything. */
    internal var frame by mutableLongStateOf(0L)

    /** Bumped by [burst], to wake a field that has nothing left to animate. */
    internal var bursts by mutableIntStateOf(0)

    internal fun ensureDust(count: Int, w: Float, h: Float, density: Float) {
        if (w == width && h == height && dust.size == count) return
        width = w
        height = h
        dust.clear()
        repeat(count) {
            dust += Particle(
                x = random.nextFloat() * w,
                y = random.nextFloat() * h,
                vx = (random.nextFloat() - 0.5f) * 14f * density,
                vy = (random.nextFloat() - 0.5f) * 14f * density - 4f * density,
                radius = (0.8f + random.nextFloat() * 1.8f) * density,
                phase = random.nextFloat() * 2f * PI.toFloat(),
                colour = random.nextInt(3),
            )
        }
    }

    /** Scatters [count] sparks from ([x], [y]). */
    fun burst(x: Float, y: Float, count: Int = 24, density: Float = 2.6f, spread: Float = 1f) {
        repeat(count) {
            val angle = random.nextFloat() * 2f * PI.toFloat()
            val speed = (30f + random.nextFloat() * 90f) * density * spread
            sparks += Particle(
                x = x, y = y,
                vx = cos(angle) * speed,
                vy = sin(angle) * speed - 20f * density,
                radius = (1f + random.nextFloat() * 2f) * density,
                phase = 0f,
                colour = random.nextInt(3),
                life = 0.6f + random.nextFloat() * 0.6f,
            )
        }
        if (sparks.size > 300) sparks.subList(0, sparks.size - 300).clear()
        bursts++
    }

    /** Pushes dust away from a touch at ([x], [y]). */
    internal fun push(x: Float, y: Float, density: Float) {
        val reach = 90f * density
        for (p in dust) {
            val dx = p.x - x
            val dy = p.y - y
            val d = sqrt(dx * dx + dy * dy)
            if (d < reach && d > 0.1f) {
                val f = (1f - d / reach) * 160f * density
                p.vx += dx / d * f
                p.vy += dy / d * f
            }
        }
    }

    internal fun step(dt: Float, density: Float) {
        val drift = 7f * density
        for (p in dust) {
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.phase += dt * 1.6f
            // Pushed dots slow back down to a gentle drift.
            val speed = sqrt(p.vx * p.vx + p.vy * p.vy)
            if (speed > drift) {
                val k = 1f - (1f - drift / speed) * minOf(1f, dt * 2.5f)
                p.vx *= k
                p.vy *= k
            }
            if (p.x < -10f) p.x += width + 20f
            if (p.x > width + 10f) p.x -= width + 20f
            if (p.y < -10f) p.y += height + 20f
            if (p.y > height + 10f) p.y -= height + 20f
        }
        val it = sparks.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.age += dt
            if (p.age >= p.life) {
                it.remove()
                continue
            }
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.vx *= 1f - minOf(1f, dt * 2.2f)
            p.vy = p.vy * (1f - minOf(1f, dt * 2.2f)) + 30f * density * dt
        }
    }

    internal class Particle(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        val radius: Float,
        var phase: Float,
        val colour: Int,
        val life: Float = 0f,
        var age: Float = 0f,
    )
}

/**
 * Draws [state]'s particles in [colours] (three theme colours); [count] dots drift across the
 * field. Nothing is drawn while [running] is false or the system's "Remove animations" is on,
 * so the field never shows frozen dots. Touches come from [particleTouch] on a parent.
 */
@Composable
fun ParticleField(
    state: ParticleState,
    colours: List<Color>,
    modifier: Modifier = Modifier,
    count: Int = 70,
    running: Boolean = true,
) {
    val density = LocalDensity.current.density
    val palette by rememberUpdatedState(colours)
    if (!running || !rememberAnimatorsEnabled()) return
    LaunchedEffect(state, count) {
        var last = 0L
        while (true) {
            // A field with no drifting dust sleeps until the next burst instead of redrawing every frame.
            if (count == 0 && state.sparks.isEmpty()) {
                val seen = state.bursts
                snapshotFlow { state.bursts }.first { it != seen }
                last = 0L
            }
            withInfiniteAnimationFrameNanos { now ->
                val dt = if (last == 0L) 0f else ((now - last) / 1e9f).coerceAtMost(0.05f)
                last = now
                state.step(dt, density)
                state.frame = now
            }
        }
    }
    Canvas(modifier) {
        state.frame // read so each frame redraws
        state.ensureDust(count, size.width, size.height, density)
        for (p in state.dust) {
            val twinkle = 0.35f + 0.35f * (1f + sin(p.phase)) / 2f
            val c = palette[p.colour % palette.size]
            drawCircle(c.copy(alpha = twinkle * 0.35f), p.radius * 3f, Offset(p.x, p.y))
            drawCircle(c.copy(alpha = twinkle), p.radius, Offset(p.x, p.y))
        }
        for (p in state.sparks) {
            val fade = 1f - p.age / p.life
            val c = palette[p.colour % palette.size]
            drawCircle(c.copy(alpha = fade * 0.3f), p.radius * 3f, Offset(p.x, p.y))
            drawCircle(c.copy(alpha = fade), p.radius, Offset(p.x, p.y))
        }
    }
}

/**
 * Touches anywhere in this element push [state]'s dust aside and scatter a few sparks. Put it on
 * a parent of both the field and the buttons over it: it watches the touch on its way down to the
 * buttons (the initial pass) and never consumes it, so taps and scrolls work as usual.
 */
@Composable
fun Modifier.particleTouch(state: ParticleState, enabled: Boolean = true): Modifier {
    val density = LocalDensity.current.density
    if (!enabled || !rememberAnimatorsEnabled()) return this
    return pointerInput(state) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                for (c in event.changes) {
                    if (!c.pressed) continue
                    state.push(c.position.x, c.position.y, density)
                    if (!c.previousPressed) state.burst(c.position.x, c.position.y, 14, density, 0.6f)
                }
            }
        }
    }
}

/** False when "Remove animations" is on in the system accessibility settings. */
@Composable
private fun rememberAnimatorsEnabled(): Boolean = remember { ValueAnimator.areAnimatorsEnabled() }

/** A [ParticleState] that lives as long as the composable that remembers it. */
@Composable
fun rememberParticleState(seed: Int = 7): ParticleState = remember { ParticleState(seed) }
