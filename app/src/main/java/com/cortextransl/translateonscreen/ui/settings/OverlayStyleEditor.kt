package com.cortextransl.translateonscreen.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cortextransl.translateonscreen.R
import com.cortextransl.translateonscreen.overlay.OverlayStyle
import kotlin.math.roundToInt

/**
 * Full appearance editor for the translated text cards with a live preview.
 */
@Composable
fun OverlayStyleEditor(
    style: OverlayStyle,
    onChange: ((OverlayStyle) -> OverlayStyle) -> Unit,
    onReset: () -> Unit
) {
    var scaleDraft by remember { mutableFloatStateOf(style.textScale.toFloat()) }
    var opacityDraft by remember { mutableFloatStateOf(style.backgroundOpacity.toFloat()) }
    LaunchedEffect(style.textScale) { scaleDraft = style.textScale.toFloat() }
    LaunchedEffect(style.backgroundOpacity) { opacityDraft = style.backgroundOpacity.toFloat() }

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
        // Live preview -----------------------------------------------------
        val previewStyle = style.copy(
            textScale = scaleDraft.roundToInt(),
            backgroundOpacity = opacityDraft.roundToInt()
        )
        OverlayPreview(previewStyle)

        Spacer(modifier = Modifier.height(14.dp))

        // Text size --------------------------------------------------------
        LabeledValue(
            label = stringResource(R.string.overlay_text_size),
            value = "${scaleDraft.roundToInt()}%"
        )
        Slider(
            value = scaleDraft,
            onValueChange = { scaleDraft = it },
            onValueChangeFinished = {
                val v = scaleDraft.roundToInt()
                onChange { it.copy(textScale = v) }
            },
            valueRange = OverlayStyle.MIN_TEXT_SCALE.toFloat()..OverlayStyle.MAX_TEXT_SCALE.toFloat(),
            steps = (OverlayStyle.MAX_TEXT_SCALE - OverlayStyle.MIN_TEXT_SCALE) / 5 - 1
        )

        // Font family ------------------------------------------------------
        Text(
            text = stringResource(R.string.overlay_font),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OverlayStyle.FONT_KEYS.forEach { key ->
                FilterChip(
                    selected = style.fontKey == key,
                    onClick = { onChange { it.copy(fontKey = key) } },
                    label = {
                        Text(
                            text = fontLabel(key),
                            fontFamily = composeFamily(key)
                        )
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        ToggleRow(
            label = stringResource(R.string.overlay_bold),
            checked = style.bold,
            onChecked = { v -> onChange { it.copy(bold = v) } }
        )
        ToggleRow(
            label = stringResource(R.string.overlay_outline),
            checked = style.outline,
            onChecked = { v -> onChange { it.copy(outline = v) } }
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Text colour ------------------------------------------------------
        Text(
            text = stringResource(R.string.overlay_text_color),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(10.dp))
        Swatches(
            colors = OverlayStyle.textColorPresets,
            selected = style.textColor,
            onSelect = { c -> onChange { it.copy(textColor = c) } }
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Background colour + opacity --------------------------------------
        Text(
            text = stringResource(R.string.overlay_bg_color),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(10.dp))
        Swatches(
            colors = OverlayStyle.backgroundPresets,
            selected = style.backgroundColor,
            onSelect = { c -> onChange { it.copy(backgroundColor = c) } }
        )
        Spacer(modifier = Modifier.height(8.dp))
        LabeledValue(
            label = stringResource(R.string.overlay_bg_opacity),
            value = "${opacityDraft.roundToInt()}%"
        )
        Slider(
            value = opacityDraft,
            onValueChange = { opacityDraft = it },
            onValueChangeFinished = {
                val v = opacityDraft.roundToInt()
                onChange { it.copy(backgroundOpacity = v) }
            },
            valueRange = OverlayStyle.MIN_BG_OPACITY.toFloat()..OverlayStyle.MAX_BG_OPACITY.toFloat()
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onReset) {
                Text(stringResource(R.string.overlay_reset))
            }
        }
    }
}

@Composable
private fun OverlayPreview(style: OverlayStyle) {
    val bg = Color(style.backgroundArgb())
    val fg = Color(style.textColor)
    val baseSp = 15f * style.scale
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                androidx.compose.ui.graphics.Brush.linearGradient(
                    listOf(Color(0xFF4A6CF7), Color(0xFF8E44AD), Color(0xFFF39C12))
                )
            )
            .padding(18.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(bg)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = stringResource(R.string.overlay_preview_title),
                    color = fg,
                    fontSize = (baseSp * 1.25f).sp,
                    fontFamily = composeFamily(style.fontKey),
                    fontWeight = if (style.bold) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(bg)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = stringResource(R.string.overlay_preview_body),
                    color = fg,
                    fontSize = baseSp.sp,
                    lineHeight = (baseSp * 1.4f).sp,
                    fontFamily = composeFamily(style.fontKey),
                    fontWeight = if (style.bold) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun LabeledValue(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun Swatches(colors: List<Int>, selected: Int, onSelect: (Int) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        colors.forEach { swatch ->
            val isSelected = (selected and 0x00FFFFFF) == (swatch and 0x00FFFFFF)
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Color(swatch))
                    .border(
                        width = if (isSelected) 2.5.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                        shape = CircleShape
                    )
                    .clickable { onSelect(swatch) }
            )
        }
        Spacer(modifier = Modifier.width(0.dp))
    }
}

@Composable
private fun fontLabel(key: String): String = when (key) {
    OverlayStyle.FONT_SYSTEM -> stringResource(R.string.font_system)
    OverlayStyle.FONT_LIGHT -> stringResource(R.string.font_light)
    OverlayStyle.FONT_CONDENSED -> stringResource(R.string.font_condensed)
    OverlayStyle.FONT_SERIF -> stringResource(R.string.font_serif)
    OverlayStyle.FONT_MONO -> stringResource(R.string.font_mono)
    OverlayStyle.FONT_ROUNDED -> stringResource(R.string.font_rounded)
    else -> key
}

private fun composeFamily(key: String): FontFamily = when (key) {
    OverlayStyle.FONT_SERIF -> FontFamily.Serif
    OverlayStyle.FONT_MONO -> FontFamily.Monospace
    OverlayStyle.FONT_ROUNDED -> FontFamily.Cursive
    else -> FontFamily.SansSerif
}
