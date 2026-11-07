package com.girocerto.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.girocerto.app.theme.AppSpacing
import com.girocerto.app.theme.BorderDark
import com.girocerto.app.theme.NeobrutalBlack
import com.girocerto.app.theme.SurfaceDark

/**
 * Tactile surface:
 * Soft elevation, illuminated top edge and subtle shaded base.
 */
@Composable
fun NeobrutalCard(
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    borderColor: Color = MaterialTheme.colorScheme.outline,
    borderWidth: Dp = AppSpacing.BorderThick, // 2.5.dp
    shadowOffset: Dp = AppSpacing.ShadowOffset, // 4.dp
    shadowColor: Color = NeobrutalBlack,
    shape: Shape = RoundedCornerShape(AppSpacing.CornerCard), // 12.dp standardized rounded corners
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier.padding(end = shadowOffset, bottom = shadowOffset),
        propagateMinConstraints = true
    ) {
        Surface(
            shape = shape,
            shadowElevation = 8.dp,
            color = backgroundColor,
            border = if (borderWidth > 0.dp) BorderStroke(borderWidth, borderColor) else null
        ) {
            Column(
                modifier = Modifier.background(
                    Brush.verticalGradient(
                        0f to Color.White.copy(alpha = .13f),
                        .22f to Color.Transparent,
                        1f to Color.Black.copy(alpha = .055f),
                    )
                ),
                content = content,
            )
        }
    }
}

