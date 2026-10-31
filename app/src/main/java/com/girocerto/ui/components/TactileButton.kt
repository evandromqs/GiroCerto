package com.girocerto.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.girocerto.app.theme.AppSpacing
import com.girocerto.app.theme.NeobrutalBlack

/**
 * Standardized Neobrutalist Button conforming directly to M1 <-> M2 interface contract.
 * Features mechanical diagonal press translation (+2dp), solid border, hard shadow,
 * standardized rounded corners (10dp), and 48dp minimum touch target.
 */
@Composable
fun NeobrutalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
    borderColor: Color = MaterialTheme.colorScheme.outline,
    borderWidth: Dp = AppSpacing.BorderAction, // 2.0.dp
    shadowOffset: Dp = AppSpacing.ShadowOffset, // 4.dp
    shadowColor: Color = NeobrutalBlack,
    shape: Shape = RoundedCornerShape(AppSpacing.CornerButton), // 10.dp standardized rounded corners
    text: String,
    leadingIcon: (@Composable () -> Unit)? = null,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressTranslation by animateDpAsState(
        targetValue = if (isPressed && enabled) 2.dp else 0.dp,
        animationSpec = tween(durationMillis = 60),
        label = "ButtonPressTranslation"
    )

    Box(
        modifier = modifier
            .padding(end = shadowOffset, bottom = shadowOffset)
            .alpha(if (enabled) 1f else 0.5f),
        propagateMinConstraints = true
    ) {
        // Hard drop shadow layer
        if (shadowOffset > 0.dp) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .offset(x = shadowOffset, y = shadowOffset)
                    .background(color = shadowColor, shape = shape)
            )
        }

        // Foreground interactive surface translating upon press
        Surface(
            modifier = Modifier
                .offset(x = pressTranslation, y = pressTranslation)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onClick
                ),
            shape = shape,
            color = backgroundColor,
            border = BorderStroke(borderWidth, borderColor)
        ) {
            Row(
                modifier = Modifier
                    .heightIn(min = AppSpacing.MinTouchTarget) // 48dp min touch target
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
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}

/**
 * TactileButton: Backwards-compatible mechanical button delegating press physics
 * and neobrutalist styling with standardized rounded corners.
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
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressTranslation by animateDpAsState(
        targetValue = if (isPressed && enabled) 2.dp else 0.dp,
        animationSpec = tween(durationMillis = 60),
        label = "TactilePressTranslation"
    )
    val shadowOffset = AppSpacing.ShadowOffset
    val borderWidth = border?.width ?: AppSpacing.BorderAction
    val borderColor = MaterialTheme.colorScheme.outline
    val containerBg = if (enabled) colors.containerColor else colors.disabledContainerColor
    val contentFg = if (enabled) colors.contentColor else colors.disabledContentColor

    Box(
        modifier = modifier
            .padding(end = shadowOffset, bottom = shadowOffset)
            .alpha(if (enabled) 1f else 0.5f),
        propagateMinConstraints = true
    ) {
        if (shadowOffset > 0.dp) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .offset(x = shadowOffset, y = shadowOffset)
                    .background(color = NeobrutalBlack, shape = shape)
            )
        }

        Surface(
            modifier = Modifier
                .offset(x = pressTranslation, y = pressTranslation)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onClick
                ),
            shape = shape,
            color = containerBg,
            contentColor = contentFg,
            border = BorderStroke(borderWidth, borderColor)
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
}
