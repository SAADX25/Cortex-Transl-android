package com.cortextransl.translateonscreen.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cortextransl.translateonscreen.R
import com.cortextransl.translateonscreen.data.preferences.UserPreferences
import com.cortextransl.translateonscreen.data.translation.LanguageCatalog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguagePickerSheet(
    includeAuto: Boolean,
    selectedCode: String,
    downloaded: Set<String>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    val languages = remember { LanguageCatalog.all() }
    val filtered = remember(query, languages) {
        val q = query.trim()
        if (q.isEmpty()) languages
        else languages.filter {
            it.displayName.contains(q, ignoreCase = true) ||
                it.nativeName.contains(q, ignoreCase = true) ||
                it.code.contains(q, ignoreCase = true)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Text(
                text = stringResource(R.string.select_language),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text(stringResource(R.string.search_languages)) },
                shape = RoundedCornerShape(16.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        val autoLabel = stringResource(R.string.language_auto)
        val showAuto = includeAuto && (query.isBlank() || autoLabel.contains(query, ignoreCase = true))
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            if (showAuto) {
                item {
                    LanguageRow(
                        title = autoLabel,
                        subtitle = UserPreferences.AUTO_LANGUAGE.uppercase(),
                        selected = selectedCode == UserPreferences.AUTO_LANGUAGE,
                        downloaded = true,
                        onClick = { onSelect(UserPreferences.AUTO_LANGUAGE) }
                    )
                    HorizontalDivider()
                }
            }
            items(filtered, key = { it.code }) { language ->
                LanguageRow(
                    title = language.displayName,
                    subtitle = "${language.nativeName} · ${language.code.uppercase()}",
                    selected = selectedCode == language.code,
                    downloaded = language.code in downloaded,
                    onClick = { onSelect(language.code) }
                )
            }
            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun LanguageRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    downloaded: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            imageVector = if (downloaded) Icons.Filled.CheckCircle else Icons.Outlined.CloudDownload,
            contentDescription = null,
            tint = if (downloaded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
