package com.girocerto.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.girocerto.app.theme.AppSpacing
import com.girocerto.app.theme.GreenPositive
import com.girocerto.app.theme.RedAlert
import com.girocerto.app.theme.StatusGreenSolid
import com.girocerto.app.theme.StatusInvalidSolid
import com.girocerto.app.theme.StatusRedSolid
import com.girocerto.app.theme.StatusYellowSolid
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
    val (tint, solid, label) = when (status) {
        TrafficLightStatus.GREEN -> Triple(GreenPositive, StatusGreenSolid, "COMPENSA")
        TrafficLightStatus.YELLOW -> Triple(YellowWarning, StatusYellowSolid, "ATENÇÃO")
        TrafficLightStatus.RED -> Triple(RedAlert, StatusRedSolid, "NÃO COMPENSA")
        TrafficLightStatus.INVALID -> Triple(
            MaterialTheme.colorScheme.onSurfaceVariant,
            StatusInvalidSolid,
            "INCOMPLETO"
        )
    }

    val shape = RoundedCornerShape(if (large) AppSpacing.CornerCard else AppSpacing.CornerSmall)

    Box(
        modifier = modifier
            .background(color = tint.copy(alpha = 0.12f), shape = shape)
            .border(BorderStroke(AppSpacing.BorderThin, tint.copy(alpha = 0.32f)), shape = shape)
            .padding(
                horizontal = if (large) 14.dp else 10.dp,
                vertical = if (large) 8.dp else 5.dp
            )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(if (large) 10.dp else 8.dp)
                    .background(solid, CircleShape)
            )
            Spacer(modifier = Modifier.width(6.dp))
            AppText(
                text = label,
                color = tint,
                fontSize = if (large) 12.sp else 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }
    }
}
