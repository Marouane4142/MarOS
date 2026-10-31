package io.github.marouane4142.maros.tv

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/** Short, interruptible transitions. Frame values are read in the graphics/draw phases. */
@Composable
fun focusProgress(focused: Boolean): State<Float> = animateFloatAsState(
    if (focused) 1f else 0f,
    if (LocalReducedMotion.current) snap() else tween(150, easing = FastOutSlowInEasing), label = "tv-focus"
)

@Composable
fun MotionReveal(target: Any, modifier: Modifier = Modifier, animateInitial: Boolean = true, content: @Composable () -> Unit) {
    val progress = remember { Animatable(1f) }
    val reduced = LocalReducedMotion.current
    val distance = with(LocalDensity.current) { 14.dp.toPx() }
    var initial by remember {mutableStateOf(true)}
    LaunchedEffect(target, reduced) {
        if (reduced || initial && !animateInitial) progress.snapTo(1f) else {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(190, easing = FastOutSlowInEasing))
        }
        initial=false
    }
    Box(modifier.graphicsLayer {
        alpha = .35f + .65f * progress.value
        translationY = distance * (1f - progress.value)
    }) { content() }
}
