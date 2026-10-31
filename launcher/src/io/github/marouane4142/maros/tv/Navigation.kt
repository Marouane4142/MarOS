@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package io.github.marouane4142.maros.tv

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import kotlinx.coroutines.delay

@Composable
fun HomeNavigation(entries: List<Pair<String, String>>, model: HomeViewModel, selected: String, homeRequest: Int) {
    val bounds = remember { mutableStateMapOf<String, Rect>() }
    var origin by remember { mutableStateOf(Offset.Zero) }
    var focused by remember { mutableStateOf("") }
    var active by remember { mutableStateOf(false) }
    val indicator = remember { Animatable(Rect.Zero, Rect.VectorConverter) }
    val reduced = LocalReducedMotion.current
    val target = bounds[focused]
    val list = rememberLazyListState()
    val primary = MaterialTheme.colorScheme.primary
    val visibility = focusProgress(active)
    LaunchedEffect(target, reduced, list.isScrollInProgress) {
        if (target != null) {
            if (reduced || list.isScrollInProgress || indicator.value == Rect.Zero) indicator.snapTo(target)
            else indicator.animateTo(target, tween(170, easing = FastOutSlowInEasing))
        }
    }
    Box(Modifier.fillMaxWidth().padding(bottom = 8.dp)
        .onGloballyPositioned { origin = it.positionInRoot() }
        .onFocusChanged { active = it.hasFocus }.focusGroup()
        .drawBehind {
            val rect = indicator.value
            if (rect.width > 0 && visibility.value > .001f) {
                drawRoundRect(primary.copy(alpha = .20f * visibility.value), rect.topLeft, rect.size, CornerRadius(13.dp.toPx()))
                drawRoundRect(primary.copy(alpha = .9f * visibility.value), rect.topLeft, rect.size, CornerRadius(13.dp.toPx()), style = Stroke(1.5.dp.toPx()))
            }
        }) {
        CompositionLocalProvider(LocalBringIntoViewSpec provides EdgeScrollSpec) {
        LazyRow(Modifier.fillMaxWidth(), state=list, contentPadding = PaddingValues(horizontal = 48.dp, vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(entries, key = { it.first }) { (id, label) ->
                val requester = remember { FocusRequester() }
                LaunchedEffect(homeRequest) {
                    if (model.focusId == "nav:$id") { delay(100); runCatching { requester.requestFocus() } }
                }
                Box(Modifier.focusRequester(requester)
                    .onGloballyPositioned { bounds[id] = it.boundsInRoot().translate(-origin) }
                    .onFocusChanged { if (it.isFocused) { focused = id; model.focused("nav:$id") } }
                    .background(if (selected == id) LocalHomePalette.current.selection.copy(alpha = .4f) else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(13.dp))
                    .combinedClickable(onClick = { model.selectTab(id) }, interactionSource = null, indication = null)
                    .semantics { role = Role.Tab; this.selected = selected == id }
                    .padding(horizontal = 18.dp, vertical = 12.dp)) {
                    Text(label, color = if (selected == id) primary else Ink, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        }
    }
}
