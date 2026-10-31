package io.github.marouane4142.maros.tv

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.*
import androidx.tv.material3.*
import io.github.marouane4142.maros.UserAccounts
import kotlinx.coroutines.*

@Composable
fun UserAvatar(profile: String, name: String, revision: Int, size: Dp = 42.dp, guest: Boolean = false) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(null, profile, revision, guest) {
        value = if (guest) null else withContext(Dispatchers.IO) { UserAccounts.avatar(context, profile) }
    }
    val image = remember(bitmap) { bitmap?.asImageBitmap() }
    Box(Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = .20f)), contentAlignment = Alignment.Center) {
        if (image != null) Image(image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Text(if (guest && name == "Ajouter") "+" else name.trim().take(1).uppercase().ifBlank { "M" }, color = MaterialTheme.colorScheme.primary, fontSize = (size.value * .4f).sp)
    }
}
