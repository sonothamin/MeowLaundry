package com.sonothamin.meowlaundry.ui.theme

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * A scalloped "cookie" shape in the spirit of Material 3 Expressive's shape library
 * (the 9-sided cookie). The radius wobbles sinusoidally [sides] times around the circle, and the
 * outline always stays inside the bounds it is given, so it can be used as a clip for photos.
 */
class CookieShape(
    private val sides: Int = 9,
    private val amplitude: Float = 0.08f,
    private val steps: Int = 360,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val radius = min(cx, cy)
        val path = Path()
        for (i in 0..steps) {
            val theta = 2.0 * PI * i / steps
            val r = radius * (1f + amplitude * cos(sides * theta).toFloat()) / (1f + amplitude)
            val x = cx + (r * cos(theta)).toFloat()
            val y = cy + (r * sin(theta)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return Outline.Generic(path)
    }
}
