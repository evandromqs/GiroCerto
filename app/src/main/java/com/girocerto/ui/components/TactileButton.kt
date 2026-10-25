package com.girocerto.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.Button as MaterialButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.unit.dp

@Composable
fun TactileButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(16.dp), colors: ButtonColors = ButtonDefaults.buttonColors(),
    elevation: ButtonElevation? = ButtonDefaults.buttonElevation(defaultElevation = 5.dp, pressedElevation = 1.dp),
    border: BorderStroke? = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .35f)),
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
    content: @Composable RowScope.() -> Unit
) {
    MaterialButton(onClick = onClick, modifier = modifier.heightIn(min = 48.dp).drawWithContent {
        drawContent()
        drawLine(Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = .3f), Color.Transparent)),
            Offset(18.dp.toPx(), 1.dp.toPx()), Offset(size.width - 18.dp.toPx(), 1.dp.toPx()), 1.dp.toPx())
    }, enabled = enabled, shape = shape, colors = colors, elevation = elevation, border = border,
        contentPadding = contentPadding, content = content)
}
