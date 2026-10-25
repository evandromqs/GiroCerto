package com.girocerto.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Card as MaterialCard
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.girocerto.app.theme.BorderDark
import com.girocerto.app.theme.SurfaceDark

/** A restrained raised panel: a soft shadow and a thin reflected top edge. */
@Composable
fun TactileCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    colors: CardColors = CardDefaults.cardColors(containerColor = SurfaceDark),
    border: BorderStroke? = BorderStroke(1.dp, BorderDark),
    content: @Composable ColumnScope.() -> Unit
) {
    MaterialCard(
        modifier = modifier.drawWithContent {
            drawContent()
            drawLine(
                brush = Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = .17f), Color.Transparent)),
                start = Offset(20.dp.toPx(), 1.dp.toPx()),
                end = Offset(size.width - 20.dp.toPx(), 1.dp.toPx()),
                strokeWidth = 1.dp.toPx()
            )
        },
        shape = shape,
        colors = colors,
        border = border,
        elevation = CardDefaults.cardElevation(defaultElevation = 5.dp),
        content = content
    )
}

