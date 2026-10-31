package io.github.marouane4142.maros.tv

import android.graphics.Bitmap
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.tv.material3.*
import io.github.marouane4142.maros.*
import io.github.marouane4142.maros.R
import kotlin.math.ceil

/** The image owns the card; metadata remains outside it and no preview is invented. */
@Composable
fun EpisodeCard(model: HomeViewModel, state: HomeState, item: ContentBridge.Item) {
    val context = LocalContext.current
    val app = state.apps.firstOrNull { it.pkg == item.packageName }
    val label = app?.label ?: item.packageName.substringAfterLast('.')
    val title = item.episode.ifBlank { item.title }
    val remaining = if (item.duration > item.position) ceil((item.duration - item.position) / 60000.0).toInt() else 0
    val detail = when {
        item.completed -> "Terminé"
        remaining > 0 -> "$remaining min restantes"
        else -> ContentBridge.`when`(item.lastSeen)
    }
    var focused by remember { mutableStateOf(false) }
    val motion = focusProgress(focused)
    Column(Modifier.width(200.dp)) {
        Card(onClick = {
            if (!ContentBridge(context).open(item)) android.widget.Toast.makeText(context, "Ouvrez ce programme dans son application", android.widget.Toast.LENGTH_SHORT).show()
        }, onLongClick = {
            MarOSUI.choose(context, title, "Votre fil de lectures", arrayOf("Masquer cette lecture")) {
                ContentBridge(context).hide(item); model.refresh()
            }
        }, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
            .graphicsLayer { scaleX=1f+.035f*motion.value;scaleY=scaleX }
            .onFocusChanged { if (it.isFocused) model.focused("continue:${item.id}") }
            .then(Modifier.semantics { contentDescription = "$title, $label, $detail" })
            .then(Modifier.onFocusChanged { focused = it.isFocused }),
            shape = CardDefaults.shape(RoundedCornerShape(12.dp)), scale = CardDefaults.scale(focusedScale = 1f),
            border = CardDefaults.border(focusedBorder = Border(BorderStroke(2.dp, MaterialTheme.colorScheme.primary)))) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Glyph(R.drawable.ui_photo, Modifier.size(22.dp), Muted.copy(alpha = .65f))
                    Text("Image non partagée", style = MaterialTheme.typography.bodySmall, color = Muted)
                }
                key(item.imageUri) {
                    AndroidView(factory = { ResumeCardView(it, item, null, null, true) }, modifier = Modifier.fillMaxSize())
                }
                Text(label, Modifier.align(Alignment.TopStart).padding(8.dp).background(Color(0xCC0C131D), RoundedCornerShape(5.dp)).padding(horizontal = 7.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodySmall, color = Ink, maxLines = 1)
                if (focused) Box(Modifier.align(Alignment.BottomEnd).padding(10.dp).size(30.dp).background(Ink, RoundedCornerShape(50)), contentAlignment = Alignment.Center) {
                    Glyph(R.drawable.ui_play, Modifier.size(17.dp), Night)
                }
                if (item.duration > 0 && item.position > 0) {
                    Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(Color(0x775E6C7D)))
                    Box(Modifier.align(Alignment.BottomStart).fillMaxWidth((item.position.toFloat() / item.duration).coerceIn(.001f, 1f)).height(3.dp).background(MaterialTheme.colorScheme.primary))
                }
            }
        }
        Text(title, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text((if (item.episode.isNotBlank()) "${item.title} · " else "") + detail, Modifier.padding(top = 2.dp),
            style = MaterialTheme.typography.bodySmall, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
