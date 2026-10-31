package io.github.marouane4142.maros.tv

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import io.github.marouane4142.maros.R

val Ink = Color(0xFFF1F6FB)
val Muted = Color(0xFFA7B7CC)
val Panel = Color(0xDF142132)
val Night = Color(0xFF090F1A)
val Manrope = FontFamily(
    Font(R.font.manrope, weight = FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(450))),
    Font(R.font.manrope, weight = FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(650)))
)
val LocalReducedMotion = staticCompositionLocalOf { false }
val LocalHomePalette = staticCompositionLocalOf { HomePalette(Night, Panel, Color(0xFF29425A)) }
data class HomePalette(val background: Color, val surface: Color, val selection: Color)
fun homePalette(theme: String) = when (theme) {
    "cinema" -> HomePalette(Color(0xFF050609), Color(0xF0121318), Color(0xFF34303C))
    "aurora" -> HomePalette(Color(0xFF071918), Color(0xED102D2B), Color(0xFF28544A))
    "dune" -> HomePalette(Color(0xFF191310), Color(0xED2B211C), Color(0xFF584333))
    "minimal" -> HomePalette(Color(0xFF0D1014), Color(0xF01C222A), Color(0xFF35414D))
    else -> HomePalette(Night, Panel, Color(0xFF29425A))
}

@Composable
fun MarosTheme(accentName: String = "ice", reduced: Boolean = false, textScale: Int = 100, theme: String = "nature", content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val accent = when (accentName) {
        "sage" -> Color(0xFFADE2CE); "amber" -> Color(0xFFF1CEA1)
        "rose" -> Color(0xFFF0B6C4); else -> Color(0xFF87DCF5)
    }
    val palette = homePalette(theme)
    CompositionLocalProvider(LocalReducedMotion provides reduced, LocalContentColor provides Ink, LocalHomePalette provides palette,
        LocalDensity provides Density(density.density, density.fontScale * textScale.coerceIn(85, 140) / 100f)) {
        MaterialTheme(
            colorScheme = darkColorScheme(primary = accent, onPrimary = Night, background = palette.background,
                onBackground = Ink, surface = palette.surface, onSurface = Ink),
            typography = Typography(
                displaySmall = TextStyle(fontFamily = Manrope, fontSize = 36.sp, fontWeight = FontWeight.Normal, lineHeight = 42.sp),
                headlineMedium = TextStyle(fontFamily = Manrope, fontSize = 24.sp, fontWeight = FontWeight.Medium),
                titleLarge = TextStyle(fontFamily = Manrope, fontSize = 19.sp, fontWeight = FontWeight.SemiBold),
                titleMedium = TextStyle(fontFamily = Manrope, fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                bodyLarge = TextStyle(fontFamily = Manrope, fontSize = 14.sp),
                bodyMedium = TextStyle(fontFamily = Manrope, fontSize = 12.sp, lineHeight = 18.sp),
                bodySmall = TextStyle(fontFamily = Manrope, fontSize = 10.sp),
                labelLarge = TextStyle(fontFamily = Manrope, fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                labelSmall = TextStyle(fontFamily = Manrope, fontSize = 9.sp, letterSpacing = .5.sp)
            ), content = content
        )
    }
}

@Composable
fun Glyph(id: Int, modifier: Modifier = Modifier.size(18.dp), tint: Color = MaterialTheme.colorScheme.primary) {
    Icon(painterResource(id), contentDescription = null, modifier = modifier, tint = tint)
}

@Composable
fun Action(label: String, icon: Int? = null, modifier: Modifier = Modifier, selected: Boolean = false, focusedScale: Float = 1.025f, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val motion = focusProgress(focused)
    Button(onClick = onClick, modifier = modifier.onFocusChanged { focused = it.isFocused }.graphicsLayer {
        val scale = 1f + (focusedScale - 1f) * motion.value
        scaleX = scale; scaleY = scale
    }.then(if (label.isBlank()) Modifier.size(42.dp) else Modifier.heightIn(min = 42.dp)).semantics {
        if (label.isBlank()) contentDescription = when(icon) {
            R.drawable.ui_search -> "Rechercher"; R.drawable.ui_wifi, R.drawable.ui_offline, R.drawable.ui_ethernet -> "Réseau et Wi-Fi"
            R.drawable.ui_settings -> "Paramètres Android TV"
            R.drawable.ui_cast -> "Chromecast"
            R.drawable.ui_spark -> "Mon salon"
            R.drawable.ui_sliders -> "Personnaliser"
            else -> "Action MarOS"
        }
    }, shape = ButtonDefaults.shape(RoundedCornerShape(12.dp)),
        colors = ButtonDefaults.colors(containerColor = if (selected) LocalHomePalette.current.selection else LocalHomePalette.current.surface,
            contentColor = Ink, focusedContainerColor = MaterialTheme.colorScheme.primary, focusedContentColor = Night),
        scale = ButtonDefaults.scale(focusedScale = 1f),
        contentPadding = if (label.isBlank()) PaddingValues(0.dp) else PaddingValues(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            if (icon != null) Glyph(icon, tint = LocalContentColor.current)
            if (label.isNotBlank()) {
                if (icon != null) Spacer(Modifier.width(8.dp))
                Text(label, maxLines = 1)
            }
        }
    }
}

/** Expressive motion is independent of mobile MaterialTheme; TV focus semantics stay native. */
@Composable
fun Modifier.expressiveFocus(onFocused: () -> Unit = {}): Modifier {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.045f else 1f,
        if (LocalReducedMotion.current) snap() else MotionScheme.expressive().fastSpatialSpec(), label = "focus")
    return this.scale(scale).onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocused() }
}
