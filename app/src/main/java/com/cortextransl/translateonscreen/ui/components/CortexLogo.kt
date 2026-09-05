package com.cortextransl.translateonscreen.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cortextransl.translateonscreen.R

@Composable
fun CortexLogo(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    elevated: Boolean = true
) {
    Image(
        painter = painterResource(R.drawable.ic_cortex_logo),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .size(size)
            .then(
                if (elevated) Modifier.shadow(8.dp, CircleShape, clip = false) else Modifier
            )
            .clip(CircleShape)
    )
}
