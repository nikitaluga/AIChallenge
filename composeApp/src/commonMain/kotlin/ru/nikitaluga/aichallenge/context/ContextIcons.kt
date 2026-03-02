package ru.nikitaluga.aichallenge.context

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

internal val CtxIconSend: ImageVector by lazy {
    ImageVector.Builder(
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(2.01f, 21f)
            lineTo(23f, 12f)
            lineTo(2.01f, 3f)
            lineTo(2f, 10f)
            lineToRelative(15f, 2f)
            lineToRelative(-15f, 2f)
            close()
        }
    }.build()
}

internal val CtxIconDelete: ImageVector by lazy {
    ImageVector.Builder(
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(6f, 19f)
            curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
            horizontalLineToRelative(8f)
            curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
            verticalLineTo(7f)
            horizontalLineTo(6f)
            verticalLineToRelative(12f)
            close()
            moveTo(19f, 4f)
            horizontalLineToRelative(-3.5f)
            lineToRelative(-1f, -1f)
            horizontalLineToRelative(-5f)
            lineToRelative(-1f, 1f)
            horizontalLineTo(5f)
            verticalLineToRelative(2f)
            horizontalLineToRelative(14f)
            verticalLineTo(4f)
            close()
        }
    }.build()
}

internal val CtxIconExpandMore: ImageVector by lazy {
    ImageVector.Builder(
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(16.59f, 8.59f)
            lineTo(12f, 13.17f)
            lineTo(7.41f, 8.59f)
            lineTo(6f, 10f)
            lineTo(12f, 16f)
            lineTo(18f, 10f)
            close()
        }
    }.build()
}

internal val CtxIconExpandLess: ImageVector by lazy {
    ImageVector.Builder(
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(12f, 8f)
            lineTo(6f, 14f)
            lineTo(7.41f, 15.41f)
            lineTo(12f, 10.83f)
            lineTo(16.59f, 15.41f)
            lineTo(18f, 14f)
            close()
        }
    }.build()
}

internal val CtxIconClose: ImageVector by lazy {
    ImageVector.Builder(
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(19f, 6.41f)
            lineTo(17.59f, 5f)
            lineTo(12f, 10.59f)
            lineTo(6.41f, 5f)
            lineTo(5f, 6.41f)
            lineTo(10.59f, 12f)
            lineTo(5f, 17.59f)
            lineTo(6.41f, 19f)
            lineTo(12f, 13.41f)
            lineTo(17.59f, 19f)
            lineTo(19f, 17.59f)
            lineTo(13.41f, 12f)
            close()
        }
    }.build()
}

internal val CtxIconBranch: ImageVector by lazy {
    ImageVector.Builder(
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            // Git-branch like icon: two circles connected by a path with a fork
            moveTo(6f, 3f)
            curveToRelative(-1.66f, 0f, -3f, 1.34f, -3f, 3f)
            curveToRelative(0f, 1.31f, 0.84f, 2.41f, 2f, 2.83f)
            verticalLineTo(15f)
            curveToRelative(-1.16f, 0.42f, -2f, 1.52f, -2f, 2.83f)
            curveToRelative(0f, 1.66f, 1.34f, 3f, 3f, 3f)
            curveToRelative(1.66f, 0f, 3f, -1.34f, 3f, -3f)
            curveToRelative(0f, -1.31f, -0.84f, -2.41f, -2f, -2.83f)
            verticalLineTo(9.41f)
            lineToRelative(5f, -5f)
            verticalLineTo(8.83f)
            curveToRelative(-1.16f, 0.42f, -2f, 1.52f, -2f, 2.83f)
            curveToRelative(0f, 1.66f, 1.34f, 3f, 3f, 3f)
            curveToRelative(1.66f, 0f, 3f, -1.34f, 3f, -3f)
            curveToRelative(0f, -1.31f, -0.84f, -2.41f, -2f, -2.83f)
            verticalLineTo(3.83f)
            curveToRelative(1.16f, -0.42f, 2f, -1.52f, 2f, -2.83f)
            curveTo(18f, -0.66f, 16.66f, -2f, 15f, -2f)
            close()
        }
    }.build()
}
