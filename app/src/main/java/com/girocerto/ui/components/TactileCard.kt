package com.girocerto.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
            shadowElevation = shadowOffset * 2,
            color = backgroundColor,
            border = if (borderWidth > 0.dp) BorderStroke(borderWidth, borderColor) else null
        ) {
            Column(modifier = Modifier.background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.White.copy(alpha = .09f), Color.Transparent, Color.Black.copy(alpha = .025f)))) , content = content)
        }
    }
}

/**
 * Backwards-compatible wrapper delegating to [NeobrutalCard].
 */
@Composable
fun TactileCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(AppSpacing.CornerCard),
    colors: CardColors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border: BorderStroke? = BorderStroke(AppSpacing.BorderThick, MaterialTheme.colorScheme.outline),
    content: @Composable ColumnScope.() -> Unit
) {
    val borderWidth = if (border != null) {
        if (border.width <= 1.dp) AppSpacing.BorderThick else border.width
    } else 0.dp
    val borderColor = MaterialTheme.colorScheme.outline

    NeobrutalCard(
        modifier = modifier,
        backgroundColor = colors.containerColor,
        borderColor = borderColor,
        borderWidth = borderWidth,
        shadowOffset = AppSpacing.ShadowOffset,
        shadowColor = NeobrutalBlack,
        shape = shape,
        content = content
    )
}

