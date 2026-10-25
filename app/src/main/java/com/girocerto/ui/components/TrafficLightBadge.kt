package com.girocerto.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.girocerto.app.theme.GreenPositive
import com.girocerto.app.theme.RedAlert
import com.girocerto.app.theme.TextSecondary
import com.girocerto.app.theme.YellowWarning
import com.girocerto.domain.model.TrafficLightStatus

@Composable
fun TrafficLightBadge(
    status: TrafficLightStatus,
    modifier: Modifier = Modifier,
    large: Boolean = false
) {
    val (bgColor, textColor, label) = when (status) {
        TrafficLightStatus.GREEN -> Triple(
            GreenPositive.copy(alpha = 0.2f),
            GreenPositive,
            "Compensa"
        )
        TrafficLightStatus.YELLOW -> Triple(
            YellowWarning.copy(alpha = 0.2f),
            YellowWarning,
            "Atenção"
        )
        TrafficLightStatus.RED -> Triple(
            RedAlert.copy(alpha = 0.2f),
            RedAlert,
            "Não compensa"
        )
        TrafficLightStatus.INVALID -> Triple(
            TextSecondary.copy(alpha = 0.2f),
            TextSecondary,
            "Incompleto"
        )
    }

    val dotColor = when (status) {
        TrafficLightStatus.GREEN -> GreenPositive
        TrafficLightStatus.YELLOW -> YellowWarning
        TrafficLightStatus.RED -> RedAlert
        TrafficLightStatus.INVALID -> TextSecondary
    }

    Box(
        modifier = modifier
            .background(
                color = bgColor,
                shape = RoundedCornerShape(if (large) 12.dp else 8.dp)
            )
            .padding(
                horizontal = if (large) 14.dp else 10.dp,
                vertical = if (large) 8.dp else 4.dp
            )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(if (large) 10.dp else 8.dp)
                    .background(dotColor, CircleShape)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                color = textColor,
                fontSize = if (large) 13.sp else 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}



