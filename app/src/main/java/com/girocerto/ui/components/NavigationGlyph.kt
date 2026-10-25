package com.girocerto.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.girocerto.app.theme.PrimaryBlue
import com.girocerto.app.theme.TextSecondary
import com.girocerto.navigation.Screen

/** Consistent monochrome instrument icons, independent of emoji fonts. */
@Composable
fun NavigationGlyph(screen: Screen, selected: Boolean) {
    val color = if (selected) PrimaryBlue else TextSecondary
    Canvas(Modifier.size(24.dp)) {
        val u = size.width / 24f
        val stroke = Stroke(width = 1.7f * u, cap = StrokeCap.Round)
        fun line(x: Float, y: Float, x2: Float, y2: Float) = drawLine(color, Offset(x*u,y*u), Offset(x2*u,y2*u), 1.7f*u, StrokeCap.Round)
        when (screen) {
            Screen.HOME -> {
                val path = Path().apply {
                    moveTo(3*u, 11*u); lineTo(12*u, 3*u); lineTo(21*u, 11*u)
                    moveTo(5*u, 10*u); lineTo(5*u,21*u); lineTo(10*u,21*u); lineTo(10*u,15*u)
                    lineTo(14*u,15*u); lineTo(14*u,21*u); lineTo(19*u,21*u); lineTo(19*u,10*u)
                }
                drawPath(path,color,style=stroke)
            }
            Screen.DIARY -> {
                line(5f,20f,5f,13f); line(12f,20f,12f,8f); line(19f,20f,19f,3f)
            }
            Screen.GOALS -> {
                drawCircle(color,9*u,Offset(12*u,12*u),style=stroke)
                drawCircle(color,5*u,Offset(12*u,12*u),style=stroke)
                drawCircle(color,1.5f*u,Offset(12*u,12*u))
            }
            Screen.HISTORY -> {
                drawCircle(color,9*u,Offset(12*u,12*u),style=stroke)
                line(12f,6f,12f,12f); line(12f,12f,16f,14f)
            }
            Screen.PROFILE -> {
                drawCircle(color,4*u,Offset(12*u,7*u),style=stroke)
                drawArc(color,180f,180f,false,Offset(4*u,13*u),Size(16*u,14*u),style=stroke)
            }
            Screen.SETTINGS, Screen.COPILOT, Screen.ADVANCED -> {
                line(4f,6f,20f,6f); line(4f,12f,20f,12f); line(4f,18f,20f,18f)
                drawCircle(color,2*u,Offset(9*u,6*u))
                drawCircle(color,2*u,Offset(16*u,12*u))
                drawCircle(color,2*u,Offset(7*u,18*u))
            }
        }
    }
}


