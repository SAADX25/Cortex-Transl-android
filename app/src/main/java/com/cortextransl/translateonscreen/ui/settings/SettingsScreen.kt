package com.cortextransl.translateonscreen.ui.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.AddBox
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cortextransl.translateonscreen.BuildConfig
import com.cortextransl.translateonscreen.R
import com.cortextransl.translateonscreen.overlay.BubbleStyle
import com.cortextransl.translateonscreen.data.preferences.UserPreferences
import com.cortextransl.translateonscreen.ui.components.SettingsListCard
import com.cortextransl.translateonscreen.ui.components.SettingsNavRow
import com.cortextransl.translateonscreen.ui.components.SettingsStatusRow
import com.cortextransl.translateonscreen.ui.components.SettingsToggleRow
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    onOpenLanguages: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val darkMode by viewModel.darkMode.collectAsStateWithLifecycle()
    val singleApp by viewModel.singleAppCapture.collectAsStateWithLifecycle()
    val deeplKey by viewModel.deeplApiKey.collectAsStateWithLifecycle()
    val libreUrl by viewModel.libreBaseUrl.collectAsStateWithLifecycle()
    val libreKey by viewModel.libreApiKey.collectAsStateWithLifecycle()
    val bubbleSize by viewModel.bubbleSizeDp.collectAsStateWithLifecycle()
    val bubbleColor by viewModel.bubbleColor.collectAsStateWithLifecycle()
    val bubbleOpacity by viewModel.bubbleOpacity.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var dialog by remember { mutableStateOf<MoreDialog?>(null) }
    var showDeeplKey by remember { mutableStateOf(false) }
    var showLibre by remember { mutableStateOf(false) }
    val darkOn = darkMode ?: isSystemInDarkTheme()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        SectionLabel(stringResource(R.string.settings_floating_button))
        Spacer(modifier = Modifier.height(8.dp))
        SettingsListCard {
            FloatingButtonEditor(
                sizeDp = bubbleSize,
                color = bubbleColor,
                opacity = bubbleOpacity,
                onSizeChange = viewModel::setBubbleSizeDp,
                onColorChange = viewModel::setBubbleColor,
                onOpacityChange = viewModel::setBubbleOpacity
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
        SettingsListCard {
            SettingsNavRow(
                icon = Icons.Outlined.Language,
                iconTint = Color(0xFF1A73E8),
                iconBackground = Color(0xFFDCEBFF),
                title = stringResource(R.string.settings_language),
                onClick = onOpenLanguages
            )
            SettingsNavRow(
                icon = Icons.Outlined.Key,
                iconTint = Color(0xFF0B5394),
                iconBackground = Color(0xFFD6E6F5),
                title = stringResource(R.string.settings_deepl_api) + " · " +
                    stringResource(
                        if (deeplKey == UserPreferences.DEFAULT_DEEPL_API_KEY) {
                            R.string.deepl_key_builtin
                        } else {
                            R.string.deepl_key_saved
                        }
                    ),
                onClick = { showDeeplKey = true }
            )
            SettingsNavRow(
                icon = Icons.Outlined.Cloud,
                iconTint = Color(0xFF1565C0),
                iconBackground = Color(0xFFD6E4F5),
                title = stringResource(R.string.settings_libre_api) + " · " +
                    if (libreUrl == UserPreferences.DEFAULT_LIBRE_URL) {
                        stringResource(R.string.libre_url_default)
                    } else {
                        libreUrl.removePrefix("https://").removePrefix("http://")
                    },
                onClick = { showLibre = true }
            )
            SettingsStatusRow(
                icon = Icons.Outlined.AddBox,
                iconTint = Color(0xFF2E7D32),
                iconBackground = Color(0xFFDFF3E3),
                title = stringResource(R.string.settings_quick_access),
                status = stringResource(R.string.settings_quick_access_on)
            )
            SettingsToggleRow(
                icon = Icons.Outlined.DarkMode,
                iconTint = Color(0xFF7B61FF),
                iconBackground = Color(0xFFE8E2FF),
                title = stringResource(R.string.settings_dark_mode),
                checked = darkOn,
                onCheckedChange = { enabled ->
                    viewModel.setDarkMode(enabled)
                }
            )
            SettingsToggleRow(
                icon = Icons.Outlined.PhoneAndroid,
                iconTint = Color(0xFFE65100),
                iconBackground = Color(0xFFFFE4C7),
                title = stringResource(R.string.settings_single_app_capture),
                checked = singleApp,
                onCheckedChange = viewModel::setSingleAppCapture,
                showDivider = true
            )
            SettingsNavRow(
                icon = Icons.AutoMirrored.Outlined.VolumeUp,
                iconTint = Color(0xFF00838F),
                iconBackground = Color(0xFFD4F3F6),
                title = stringResource(R.string.settings_voice),
                onClick = { dialog = MoreDialog.Voice },
                showDivider = false
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
        SectionLabel(stringResource(R.string.support_title))
        Spacer(modifier = Modifier.height(8.dp))

        SettingsListCard {
            SettingsNavRow(
                icon = Icons.AutoMirrored.Outlined.MenuBook,
                iconTint = Color(0xFF7B61FF),
                iconBackground = Color(0xFFE8E2FF),
                title = stringResource(R.string.support_tutorial),
                onClick = { dialog = MoreDialog.Tutorial }
            )
            SettingsNavRow(
                icon = Icons.Outlined.Info,
                iconTint = Color(0xFFC62828),
                iconBackground = Color(0xFFFFE1E1),
                title = stringResource(R.string.support_battery_optimization),
                onClick = {
                    context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                }
            )
            SettingsNavRow(
                icon = Icons.Outlined.ChatBubbleOutline,
                iconTint = Color(0xFF00838F),
                iconBackground = Color(0xFFD4F3F6),
                title = stringResource(R.string.support_help_feedback),
                onClick = { dialog = MoreDialog.Help }
            )
            SettingsNavRow(
                icon = Icons.Outlined.Share,
                iconTint = Color(0xFF2E7D32),
                iconBackground = Color(0xFFDFF3E3),
                title = stringResource(R.string.support_share),
                onClick = {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, context.getString(R.string.share_text))
                    }
                    context.startActivity(Intent.createChooser(send, context.getString(R.string.support_share)))
                },
                showDivider = false
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
        SectionLabel(stringResource(R.string.legal_title))
        Spacer(modifier = Modifier.height(8.dp))

        SettingsListCard {
            SettingsNavRow(
                icon = Icons.Outlined.Description,
                iconTint = Color(0xFF1A73E8),
                iconBackground = Color(0xFFDCEBFF),
                title = stringResource(R.string.legal_terms),
                onClick = { dialog = MoreDialog.Terms },
                showDivider = false
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
    }

    val dialogData = when (dialog) {
        MoreDialog.Tutorial -> stringResource(R.string.support_tutorial) to stringResource(R.string.tutorial_body)
        MoreDialog.Help -> stringResource(R.string.help_title) to stringResource(R.string.how_it_works_body)
        MoreDialog.Voice -> stringResource(R.string.settings_voice) to stringResource(R.string.voice_coming_soon)
        MoreDialog.Terms -> stringResource(R.string.legal_terms) to stringResource(R.string.terms_body)
        null -> null
    }
    if (dialogData != null) {
        AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(dialogData.first) },
            text = { Text(dialogData.second) },
            confirmButton = {
                TextButton(onClick = { dialog = null }) {
                    Text(stringResource(R.string.ok))
                }
            }
        )
    }

    if (showDeeplKey) {
        var draft by remember(deeplKey) { mutableStateOf(deeplKey) }
        AlertDialog(
            onDismissRequest = { showDeeplKey = false },
            title = { Text(stringResource(R.string.settings_deepl_api)) },
            text = {
                Column {
                    Text(
                        text = stringResource(R.string.settings_deepl_api_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.deepl_key_hint)) },
                        visualTransformation = PasswordVisualTransformation()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.setDeeplApiKey(draft)
                        showDeeplKey = false
                    }
                ) {
                    Text(stringResource(R.string.deepl_key_save))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        viewModel.restoreDefaultDeeplApiKey()
                        showDeeplKey = false
                    }
                ) {
                    Text(stringResource(R.string.deepl_key_restore))
                }
            }
        )
    }

    if (showLibre) {
        var urlDraft by remember(libreUrl) { mutableStateOf(libreUrl) }
        var keyDraft by remember(libreKey) { mutableStateOf(libreKey) }
        AlertDialog(
            onDismissRequest = { showLibre = false },
            title = { Text(stringResource(R.string.settings_libre_api)) },
            text = {
                Column {
                    Text(
                        text = stringResource(R.string.settings_libre_api_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = urlDraft,
                        onValueChange = { urlDraft = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text(UserPreferences.DEFAULT_LIBRE_URL) }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = keyDraft,
                        onValueChange = { keyDraft = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.libre_key_hint)) },
                        visualTransformation = PasswordVisualTransformation()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.setLibreBaseUrl(urlDraft)
                        viewModel.setLibreApiKey(keyDraft)
                        showLibre = false
                    }
                ) {
                    Text(stringResource(R.string.deepl_key_save))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        viewModel.setLibreBaseUrl("")
                        viewModel.setLibreApiKey("")
                        showLibre = false
                    }
                ) {
                    Text(stringResource(R.string.libre_url_restore))
                }
            }
        )
    }
}

@Composable
private fun FloatingButtonEditor(
    sizeDp: Int,
    color: Int,
    opacity: Int,
    onSizeChange: (Int) -> Unit,
    onColorChange: (Int) -> Unit,
    onOpacityChange: (Int) -> Unit
) {
    var sizeDraft by remember { mutableFloatStateOf(sizeDp.toFloat()) }
    var opacityDraft by remember { mutableFloatStateOf(opacity.toFloat()) }
    LaunchedEffect(sizeDp) {
        sizeDraft = sizeDp.toFloat()
    }
    LaunchedEffect(opacity) {
        opacityDraft = opacity.toFloat()
    }
    val previewSize by animateDpAsState(
        targetValue = sizeDraft.roundToInt().dp,
        animationSpec = tween(90),
        label = "bubblePreviewSize"
    )
    val previewOpacity by animateFloatAsState(
        targetValue = opacityDraft / 100f,
        animationSpec = tween(90),
        label = "bubblePreviewOpacity"
    )
    val sizeLabel = when {
        sizeDraft < 52f -> stringResource(R.string.bubble_size_small)
        sizeDraft > 62f -> stringResource(R.string.bubble_size_large)
        else -> stringResource(R.string.bubble_size_medium)
    }

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp),
            contentAlignment = Alignment.Center
        ) {
            BubblePreview(
                sizeDp = previewSize.value.roundToInt(),
                color = color,
                opacity = previewOpacity
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.settings_bubble_size),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = sizeLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Slider(
            value = sizeDraft,
            onValueChange = {
                sizeDraft = it
                onSizeChange(it.roundToInt())
            },
            onValueChangeFinished = { onSizeChange(sizeDraft.roundToInt()) },
            valueRange = BubbleStyle.MIN_DP.toFloat()..BubbleStyle.MAX_DP.toFloat()
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.settings_bubble_opacity),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = stringResource(R.string.bubble_opacity_value, opacityDraft.roundToInt()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Slider(
            value = opacityDraft,
            onValueChange = {
                opacityDraft = it
                onOpacityChange(it.roundToInt())
            },
            onValueChangeFinished = { onOpacityChange(opacityDraft.roundToInt()) },
            valueRange = BubbleStyle.MIN_OPACITY.toFloat()..BubbleStyle.MAX_OPACITY.toFloat()
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.settings_bubble_color),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            BubbleStyle.presets.forEach { swatch ->
                val selected = color == swatch
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(swatch))
                        .border(
                            width = if (selected) 2.5.dp else 1.dp,
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                            },
                            shape = CircleShape
                        )
                        .clickable { onColorChange(swatch) }
                )
            }
        }
    }
}

@Composable
private fun BubblePreview(sizeDp: Int, color: Int, opacity: Float) {
    val light = Color(ColorUtils.blendARGB(color, android.graphics.Color.WHITE, 0.20f))
    val mid = Color(color)
    val dark = Color(ColorUtils.blendARGB(color, android.graphics.Color.BLACK, 0.28f))
    val glyph = Color(BubbleStyle.glyphColor(color))
    Box(
        modifier = Modifier
            .size(sizeDp.dp)
            .alpha(opacity)
            .shadow(14.dp, CircleShape, clip = false)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(light, mid, dark)))
            .border(1.5.dp, Color.White.copy(alpha = 0.42f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_bubble_c),
            contentDescription = null,
            tint = glyph,
            modifier = Modifier
                .fillMaxSize()
                .padding((sizeDp * 0.18f).dp)
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(horizontal = 4.dp)
    )
}

private enum class MoreDialog { Tutorial, Help, Voice, Terms }
