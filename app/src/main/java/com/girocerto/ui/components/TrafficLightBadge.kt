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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.girocerto.app.theme.AppSpacing
import com.girocerto.app.theme.NeobrutalBlack
import com.girocerto.app.theme.StatusGreenSolid
import com.girocerto.app.theme.StatusInvalidSolid
import com.girocerto.app.theme.StatusRedSolid
import com.girocerto.app.theme.StatusYellowSolid
import com.girocerto.domain.model.TrafficLightStatus

/**
 * Neobrutalist TrafficLightBadge:
 * High-contrast decision indicator with solid color fill, solid black border,
 * standardized rounded corners, framed status dot, and bold uppercase label.
 */
@Composable
fun TrafficLightBadge(
    status: TrafficLightStatus,
    modifier: Modifier = Modifier,
    large: Boolean = false
) {
    val (bgColor, label) = when (status) {
        TrafficLightStatus.GREEN -> StatusGreenSolid to "COMPENSA"
        TrafficLightStatus.YELLOW -> StatusYellowSolid to "ATENÇÃO"
        TrafficLightStatus.RED -> StatusRedSolid to "NÃO COMPENSA"
        TrafficLightStatus.INVALID -> StatusInvalidSolid to "INCOMPLETO"
    }

    val shape = RoundedCornerShape(if (large) AppSpacing.CornerCard else AppSpacing.CornerSmall)

    Box(
        modifier = modifier
            .background(color = bgColor, shape = shape)
            .border(BorderStroke(AppSpacing.BorderThin, NeobrutalBlack), shape = shape)
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
                    .background(NeobrutalBlack, CircleShape)
            )
            Spacer(modifier = Modifier.width(6.dp))
            AppText(
                text = label,
                color = NeobrutalBlack,
                fontSize = if (large) 12.sp else 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }
    }
}



