package com.cortextransl.translateonscreen.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cortextransl.translateonscreen.R
import com.cortextransl.translateonscreen.data.model.TranslationModes
import com.cortextransl.translateonscreen.data.preferences.UserPreferences
import com.cortextransl.translateonscreen.data.translation.LanguageCatalog
import com.cortextransl.translateonscreen.data.translation.TranslationEngines
import com.cortextransl.translateonscreen.ui.components.LanguagePickerSheet
import com.cortextransl.translateonscreen.ui.components.TintedIcon
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    isServiceRunning: Boolean,
    hasOverlayPermission: Boolean,
    onStartTranslator: (singleApp: Boolean) -> Unit,
    onStopTranslator: () -> Unit,
    onRequestOverlayPermission: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val source by viewModel.sourceLanguage.collectAsStateWithLifecycle()
    val target by viewModel.targetLanguage.collectAsStateWithLifecycle()
    val mode by viewModel.translationMode.collectAsStateWithLifecycle()
    val engine by viewModel.translationEngine.collectAsStateWithLifecycle()
    val deeplKey by viewModel.deeplApiKey.collectAsStateWithLifecycle()
    val singleApp by viewModel.singleAppCapture.collectAsStateWithLifecycle()
    val downloaded by viewModel.downloadedLanguages.collectAsStateWithLifecycle()
    val downloading by viewModel.downloading.collectAsStateWithLifecycle()
    val downloadError by viewModel.downloadError.collectAsStateWithLifecycle()

    var pickingSource by remember { mutableStateOf(false) }
    var pickingTarget by remember { mutableStateOf(false) }
    var pickingEngine by remember { mutableStateOf(false) }
    var pickingMode by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val downloadFailedText = stringResource(R.string.language_download_failed)
    val cannotSwapText = stringResource(R.string.cannot_swap_auto_language)
    val deeplNeedsKeyText = stringResource(R.string.deepl_needs_key)

    LaunchedEffect(downloadError) {
        if (downloadError) {
            snackbar.showSnackbar(downloadFailedText)
            viewModel.clearDownloadError()
        }
    }

    val pairReady = if (TranslationEngines.usesOnDevicePacks(engine)) {
        (source == UserPreferences.AUTO_LANGUAGE || source in downloaded) &&
            target in downloaded
    } else {
        true
    }
    val sourceName = if (source == UserPreferences.AUTO_LANGUAGE) {
        stringResource(R.string.language_auto)
    } else {
        LanguageCatalog.displayName(source)
    }
    val targetName = LanguageCatalog.displayName(target)
    val modeLabel = stringResource(TranslationModes.labelRes(mode))
    val engineLabel = stringResource(TranslationEngines.labelRes(engine))

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            if (!hasOverlayPermission) {
                PermissionBanner(onRequestOverlayPermission)
                Spacer(modifier = Modifier.height(12.dp))
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(2.dp, RoundedCornerShape(22.dp), clip = false)
                    .clip(RoundedCornerShape(22.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 16.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LanguageChip(
                        modifier = Modifier.weight(1f),
                        name = sourceName,
                        onClick = { pickingSource = true }
                    )
                    IconButton(onClick = {
                        if (source == UserPreferences.AUTO_LANGUAGE) {
                            scope.launch { snackbar.showSnackbar(cannotSwapText) }
                        } else {
                            viewModel.swapLanguages()
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Default.SwapHoriz,
                            contentDescription = stringResource(R.string.swap_languages),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    LanguageChip(
                        modifier = Modifier.weight(1f),
                        name = targetName,
                        onClick = { pickingTarget = true }
                    )
                }

                Spacer(modifier = Modifier.height(22.dp))

                PowerButton(
                    active = isServiceRunning,
                    enabled = true,
                    onClick = {
                        if (isServiceRunning) onStopTranslator()
                        else onStartTranslator(singleApp)
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                StatusPill(
                    active = isServiceRunning,
                    text = stringResource(
                        if (isServiceRunning) R.string.translator_active_short else R.string.start_translator
                    ),
                    onClick = {
                        if (!hasOverlayPermission) onRequestOverlayPermission()
                        else if (isServiceRunning) onStopTranslator()
                        else onStartTranslator(singleApp)
                    }
                )

                Spacer(modifier = Modifier.height(20.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))

                ConfigRow(
                    icon = Icons.Outlined.Translate,
                    iconTint = Color(0xFF1A73E8),
                    iconBackground = Color(0xFFDCEBFF),
                    label = stringResource(R.string.translation_engine),
                    value = engineLabel,
                    onClick = { pickingEngine = true }
                )
                HorizontalDivider(
                    modifier = Modifier.padding(start = 50.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
                )
                ConfigRow(
                    icon = Icons.Outlined.Layers,
                    iconTint = Color(0xFF7B61FF),
                    iconBackground = Color(0xFFE8E2FF),
                    label = stringResource(R.string.translation_mode),
                    value = modeLabel,
                    onClick = { pickingMode = true }
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (!pairReady && TranslationEngines.usesOnDevicePacks(engine)) {
                CompactDownloadRow(
                    downloading = downloading,
                    onDownload = viewModel::downloadRequiredLanguages
                )
            }
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        )
    }

    if (pickingSource) {
        LanguagePickerSheet(
            includeAuto = true,
            selectedCode = source,
            downloaded = downloaded,
            onSelect = {
                viewModel.setSourceLanguage(it)
                pickingSource = false
            },
            onDismiss = { pickingSource = false }
        )
    }
    if (pickingTarget) {
        LanguagePickerSheet(
            includeAuto = false,
            selectedCode = target,
            downloaded = downloaded,
            onSelect = {
                viewModel.setTargetLanguage(it)
                pickingTarget = false
            },
            onDismiss = { pickingTarget = false }
        )
    }
    if (pickingEngine) {
        val engineOptions = TranslationEngines.all.map { id ->
            id to stringResource(TranslationEngines.labelRes(id))
        }
        OptionSheet(
            title = stringResource(R.string.translation_engine),
            options = engineOptions.map { it.second },
            selected = engineLabel,
            onSelect = { label ->
                val selected = engineOptions.firstOrNull { it.second == label }?.first
                    ?: UserPreferences.ENGINE_MLKIT
                if (selected == UserPreferences.ENGINE_DEEPL && deeplKey.isBlank()) {
                    scope.launch { snackbar.showSnackbar(deeplNeedsKeyText) }
                } else {
                    viewModel.setTranslationEngine(selected)
                }
                pickingEngine = false
            },
            onDismiss = { pickingEngine = false }
        )
    }
    if (pickingMode) {
        val modeOptions = TranslationModes.all.associateWith { stringResource(TranslationModes.labelRes(it)) }
        OptionSheet(
            title = stringResource(R.string.translation_mode),
            options = modeOptions.values.toList(),
            selected = modeLabel,
            onSelect = { label ->
                val selectedMode = modeOptions.entries.firstOrNull { it.value == label }?.key
                    ?: TranslationModes.FULLSCREEN
                viewModel.setTranslationMode(selectedMode)
                pickingMode = false
            },
            onDismiss = { pickingMode = false }
        )
    }
}

@Composable
private fun LanguageChip(
    modifier: Modifier,
    name: String,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun PowerButton(
    active: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val ring = if (active) MaterialTheme.colorScheme.primary else Color(0xFFB8BCC0)
    Box(
        modifier = Modifier
            .size(92.dp)
            .clip(CircleShape)
            .background(ring)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(7.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.PowerSettingsNew,
            contentDescription = stringResource(
                if (active) R.string.stop_translator else R.string.start_translator
            ),
            tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(40.dp)
        )
    }
}

@Composable
private fun StatusPill(
    active: Boolean,
    text: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(
                    if (active) Color(0xFF2E7D32) else Color(0xFF9AA0A6)
                )
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun ConfigRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    iconBackground: Color,
    label: String,
    value: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TintedIcon(icon = icon, tint = iconTint, background = iconBackground)
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = Icons.Outlined.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline
        )
    }
}

@Composable
private fun CompactDownloadRow(
    downloading: Boolean,
    onDownload: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(enabled = !downloading, onClick = onDownload)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.offline_download_needed),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        if (downloading) {
            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
        } else {
            TextButton(onClick = onDownload) {
                Text(stringResource(R.string.download_pair))
            }
        }
    }
}

@Composable
private fun PermissionBanner(onRestore: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Outlined.Warning, contentDescription = null)
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = stringResource(R.string.overlay_missing_banner),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onRestore) {
            Text(stringResource(R.string.restore_overlay))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OptionSheet(
    title: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            options.forEach { option ->
                val isSelected = option == selected
                Text(
                    text = option,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSelect(option) }
                        .padding(vertical = 14.dp, horizontal = 8.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
