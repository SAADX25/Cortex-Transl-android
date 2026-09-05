package com.cortextransl.translateonscreen.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.ViewHeadline
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cortextransl.translateonscreen.R
import com.cortextransl.translateonscreen.data.model.BubbleActions
import com.cortextransl.translateonscreen.data.model.TranslationModes

data class BubbleMenuUiState(
    val mode: String,
    val doubleTapAction: String,
    val sourceName: String,
    val targetName: String,
    val engineLabel: String
)

private val SheetBg = Color(0xFF2C2C2E)
private val FieldBg = Color(0xFF3A3A3C)
private val DimScrim = Color(0x99000000)
private val LabelGrey = Color(0xFFB0B3B8)
private val CheckGreen = Color(0xFF4CAF50)

@Composable
fun BubbleOptionsSheet(
    state: BubbleMenuUiState,
    extraBottomPx: Int,
    onSelectMode: (String) -> Unit,
    onDoubleTapAction: (String) -> Unit,
    onSwapLanguages: () -> Unit,
    onOpenApp: () -> Unit,
    onDismiss: () -> Unit
) {
    var expandedAction by remember { mutableStateOf<String?>(null) }
    val bottomPad = with(LocalDensity.current) {
        extraBottomPx.toDp().coerceAtLeast(12.dp)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DimScrim)
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(SheetBg)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) {}
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp, bottom = bottomPad + 16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.default_translation_mode),
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cancel), tint = Color.White)
                }
            }

            ModeRow(
                icon = Icons.Filled.ViewHeadline,
                iconTint = Color(0xFF4A90E2),
                iconBg = Color(0x334A90E2),
                title = stringResource(R.string.mode_fullscreen),
                selected = TranslationModes.normalize(state.mode) == TranslationModes.FULLSCREEN,
                onClick = { onSelectMode(TranslationModes.FULLSCREEN) }
            )
            ModeRow(
                icon = Icons.Filled.CropFree,
                iconTint = Color(0xFF9B7BFF),
                iconBg = Color(0x339B7BFF),
                title = stringResource(R.string.mode_region),
                selected = TranslationModes.normalize(state.mode) == TranslationModes.REGION,
                onClick = { onSelectMode(TranslationModes.REGION) }
            )
            ModeRow(
                icon = Icons.Filled.Lock,
                iconTint = Color(0xFF3DDC84),
                iconBg = Color(0x333DDC84),
                title = stringResource(R.string.mode_fixed_region),
                selected = TranslationModes.normalize(state.mode) == TranslationModes.FIXED_REGION,
                onClick = { onSelectMode(TranslationModes.FIXED_REGION) }
            )
            ModeRow(
                icon = Icons.Filled.Sync,
                iconTint = Color(0xFFF5C542),
                iconBg = Color(0x33F5C542),
                title = stringResource(R.string.mode_auto_region),
                selected = TranslationModes.normalize(state.mode) == TranslationModes.AUTO_REGION,
                onClick = { onSelectMode(TranslationModes.AUTO_REGION) }
            )
            ModeRow(
                icon = Icons.Filled.Fullscreen,
                iconTint = Color(0xFFE85D5D),
                iconBg = Color(0x33E85D5D),
                title = stringResource(R.string.mode_auto_fullscreen),
                selected = TranslationModes.normalize(state.mode) == TranslationModes.AUTO_FULLSCREEN,
                onClick = { onSelectMode(TranslationModes.AUTO_FULLSCREEN) }
            )

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.bubble_gestures_hint),
                color = LabelGrey,
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.floating_icon_actions),
                color = LabelGrey,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(10.dp))

            ActionDropdown(
                label = stringResource(R.string.double_tap_action),
                selected = state.doubleTapAction,
                expanded = expandedAction == "double",
                onToggle = { expandedAction = if (expandedAction == "double") null else "double" },
                onSelect = {
                    onDoubleTapAction(it)
                    expandedAction = null
                }
            )

            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = stringResource(R.string.languages_and_engine),
                color = LabelGrey,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(FieldBg)
                        .clickable(onClick = onSwapLanguages)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        state.sourceName,
                        color = Color.White,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Icon(Icons.Filled.SwapHoriz, contentDescription = stringResource(R.string.swap_languages), tint = Color.White)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        state.targetName,
                        color = Color.White,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(FieldBg)
                        .clickable(onClick = onOpenApp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.PhoneAndroid, contentDescription = stringResource(R.string.app_name), tint = Color.White)
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(FieldBg)
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = state.engineLabel,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ModeRow(
    icon: ImageVector,
    iconTint: Color,
    iconBg: Color,
    title: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = title,
            color = Color.White,
            modifier = Modifier.weight(1f),
            fontSize = 16.sp
        )
        if (selected) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = CheckGreen)
        }
    }
}

@Composable
private fun ActionDropdown(
    label: String,
    selected: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    onSelect: (String) -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(FieldBg)
                .clickable(onClick = onToggle)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(label, color = LabelGrey, fontSize = 12.sp)
                Text(
                    text = stringResource(BubbleActions.labelRes(selected)),
                    color = Color.White,
                    fontWeight = FontWeight.Medium
                )
            }
            Icon(Icons.Outlined.ExpandMore, contentDescription = null, tint = LabelGrey)
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(tween(170)) + fadeIn(tween(150)),
            exit = shrinkVertically(tween(150)) + fadeOut(tween(120))
        ) {
            Column(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(FieldBg)
            ) {
                BubbleActions.all.forEachIndexed { index, action ->
                    Text(
                        text = stringResource(BubbleActions.labelRes(action)),
                        color = if (action == selected) CheckGreen else Color.White,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(action) }
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    )
                    if (index != BubbleActions.all.lastIndex) {
                        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                    }
                }
            }
        }
    }
}
