package com.girocerto.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.girocerto.app.theme.AppSpacing

/**
 * Primary action button: flat filled surface, hairline border, ripple feedback.
 * No offset translation or hard shadow; the fill color carries the hierarchy.
 */
@Composable
fun NeobrutalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
    borderColor: Color = MaterialTheme.colorScheme.outline,
    borderWidth: Dp = AppSpacing.BorderAction,
    shadowOffset: Dp = 0.dp,
    shadowColor: Color = Color.Transparent,
    shape: Shape = RoundedCornerShape(AppSpacing.CornerButton),
    text: String,
    leadingIcon: (@Composable () -> Unit)? = null,
    enabled: Boolean = true
) {
    Surface(
        onClick = onClick,
        modifier = modifier.alpha(if (enabled) 1f else 0.5f),
        enabled = enabled,
        shape = shape,
        color = backgroundColor,
        contentColor = contentColor,
        border = BorderStroke(borderWidth, borderColor),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = AppSpacing.MinTouchTarget)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leadingIcon != null) {
                leadingIcon()
                Spacer(modifier = Modifier.width(8.dp))
            }
            AppText(
                text = text,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
            )
        }
    }
}

/**
 * Backwards-compatible mechanical button delegating to the same flat surface styling.
 */
@Composable
fun TactileButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(AppSpacing.CornerButton),
    colors: ButtonColors = ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary
    ),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = BorderStroke(AppSpacing.BorderAction, MaterialTheme.colorScheme.outline),
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
    content: @Composable RowScope.() -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier.alpha(if (enabled) 1f else 0.5f),
        enabled = enabled,
        shape = shape,
        color = if (enabled) colors.containerColor else colors.disabledContainerColor,
        contentColor = if (enabled) colors.contentColor else colors.disabledContentColor,
        border = border,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = AppSpacing.MinTouchTarget)
                .padding(contentPadding),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}
