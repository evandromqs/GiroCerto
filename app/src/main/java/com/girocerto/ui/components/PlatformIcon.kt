package com.girocerto.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.girocerto.app.R
import com.girocerto.domain.model.WorkPlatform

/** Original publisher artwork from the official Google Play listings; no tint. */
@Composable
fun PlatformIcon(platform: WorkPlatform, modifier: Modifier = Modifier) {
    val image = when (platform) {
        WorkPlatform.UBER, WorkPlatform.UBER_EATS -> R.drawable.platform_uber
        WorkPlatform.NINETY_NINE, WorkPlatform.FOOD_99 -> R.drawable.platform_ninety_nine
        WorkPlatform.IFOOD -> R.drawable.platform_ifood
        WorkPlatform.KEETA -> R.drawable.platform_keeta
    }
    Image(painterResource(image), contentDescription = platform.label, modifier = modifier.size(36.dp))
}
