package com.pietrondo.aquariummanager.presentation

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The pixel art icons of the app: decorative, so they carry no content description. */
@Composable
fun PixelIcon(resId: Int, size: Dp = 24.dp) {
    Image(
        painter = painterResource(resId),
        contentDescription = null,
        modifier = Modifier.size(size),
    )
}
