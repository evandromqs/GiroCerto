package com.girocerto.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.girocerto.app.theme.AppSpacing
import com.girocerto.app.theme.GreenPositive
import com.girocerto.app.theme.RedAlert
import com.girocerto.app.theme.YellowWarning
import com.girocerto.domain.model.TrafficLightStatus

/**
 * Decision indicator: soft tinted pill with a status dot and bold label.
 * The semantic status color is preserved (green/yellow/red) inside the
 * otherwise single-accent palette.
 */
@Composable
fun TrafficLightBadge(
    status: TrafficLightStatus,
    modifier: Modifier = Modifier,
    large: Boolean = false
) {
    val (tint, label, textColor) = when (status) {
        TrafficLightStatus.GREEN -> Triple(GreenPositive, "COMPENSA", if (androidx.compose.material3.MaterialTheme.colorScheme.background.luminance() < 0.5f) com.girocerto.app.theme.BrandRoyalBlue else GreenPositive)
        TrafficLightStatus.YELLOW -> Triple(YellowWarning, "ATENÇÃO", YellowWarning)
        TrafficLightStatus.RED -> Triple(RedAlert, "NÃO COMPENSA", RedAlert)
        TrafficLightStatus.INVALID -> Triple(MaterialTheme.colorScheme.onSurfaceVariant, "INCOMPLETO", MaterialTheme.colorScheme.onSurfaceVariant)
    }

    val shape = RoundedCornerShape(if (large) AppSpacing.CornerCard else AppSpacing.CornerSmall)

    Box(
        modifier = modifier
            .background(color = if (status == TrafficLightStatus.GREEN) tint else tint.copy(alpha = 0.12f), shape = shape)
            .border(BorderStroke(AppSpacing.BorderThin, if (status == TrafficLightStatus.GREEN) Color.White.copy(alpha = 0.6f) else tint.copy(alpha = 0.32f)), shape = shape)
            .padding(
                horizontal = if (large) 14.dp else 10.dp,
                vertical = if (large) 8.dp else 5.dp
            )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(when (status) {
                    TrafficLightStatus.GREEN -> com.girocerto.app.R.drawable.ride_quality_great
                    TrafficLightStatus.YELLOW -> com.girocerto.app.R.drawable.ride_quality_good
                    TrafficLightStatus.RED -> com.girocerto.app.R.drawable.ride_quality_bad
                    TrafficLightStatus.INVALID -> com.girocerto.app.R.drawable.ride_quality_good
                }),
                contentDescription = null,
                modifier = Modifier.size(if (large) 42.dp else 26.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            AppText(
                text = label,
                color = textColor,
                fontSize = if (large) 12.sp else 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }
    }
}
