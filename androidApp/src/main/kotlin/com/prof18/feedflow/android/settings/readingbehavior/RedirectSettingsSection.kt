package com.prof18.feedflow.android.settings.readingbehavior

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.prof18.feedflow.android.ArticleRedirectSettings
import com.prof18.feedflow.android.R
import com.prof18.feedflow.android.isValidRedirectPrefix
import com.prof18.feedflow.android.ui.settings.CompactSettingDropdownRow
import com.prof18.feedflow.android.ui.settings.SettingDropdownOption
import com.prof18.feedflow.android.ui.settings.SettingSwitchItem
import kotlinx.collections.immutable.toImmutableList

@Composable
internal fun RedirectSettingsSection() {
    val context = LocalContext.current
    val settings = remember(context) { ArticleRedirectSettings(context) }
    var prefixes by remember { mutableStateOf(settings.prefixes()) }
    var defaultPrefix by remember { mutableStateOf(settings.defaultPrefix().orEmpty()) }
    var defaultDraft by remember { mutableStateOf(defaultPrefix) }
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    val none = stringResource(R.string.redirect_none)
    Column {
        val trimmedDefault = defaultDraft.trim()
        val validDefault = trimmedDefault.isEmpty() || isValidRedirectPrefix(trimmedDefault)
        OutlinedTextField(
            value = defaultDraft,
            onValueChange = { defaultDraft = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.redirect_default_url)) },
            singleLine = true,
            isError = !validDefault,
            supportingText = {
                if (!validDefault) Text(stringResource(R.string.redirect_invalid))
            },
        )
        TextButton(
            enabled = validDefault && trimmedDefault != defaultPrefix,
            onClick = {
                if (trimmedDefault.isNotEmpty() && trimmedDefault !in prefixes) {
                    prefixes = prefixes + trimmedDefault
                    settings.savePrefixes(prefixes)
                }
                settings.setDefaultPrefix(trimmedDefault.ifEmpty { null })
                defaultPrefix = trimmedDefault
                defaultDraft = trimmedDefault
            },
        ) {
            Text(stringResource(R.string.redirect_save_default))
        }
        TextButton(onClick = {
            draft = prefixes.joinToString("\n")
            editing = true
        }) {
            Text(stringResource(R.string.redirect_edit))
        }
        CompactSettingDropdownRow(
            title = stringResource(R.string.redirect_default),
            currentValue = defaultPrefix,
            options = (
                listOf(SettingDropdownOption("", none)) +
                    prefixes.map { SettingDropdownOption(it, it) }
                ).toImmutableList(),
            onOptionSelected = {
                defaultPrefix = it
                defaultDraft = it
                settings.setDefaultPrefix(it.ifEmpty { null })
            },
        )
    }
    if (editing) {
        val values = draft.lines().map(String::trim).filter(String::isNotEmpty).distinct()
        val valid = values.all(::isValidRedirectPrefix)
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text(stringResource(R.string.redirect_edit)) },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.redirect_one_per_line)) },
                    isError = !valid,
                    supportingText = { if (!valid) Text(stringResource(R.string.redirect_invalid)) },
                )
            },
            confirmButton = {
                TextButton(enabled = valid, onClick = {
                    settings.savePrefixes(values)
                    prefixes = values
                    defaultPrefix = settings.defaultPrefix().orEmpty()
                    defaultDraft = defaultPrefix
                    editing = false
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { editing = false }) { Text(stringResource(android.R.string.cancel)) }
            },
        )
    }
}

@Composable
internal fun FeedRedirectSelector(feedId: String?) {
    if (feedId == null) return
    val context = LocalContext.current
    val settings = remember(context) { ArticleRedirectSettings(context) }
    val prefixes = remember(feedId) { settings.prefixes() }
    var selected by remember(feedId) { mutableStateOf(settings.feedOverride(feedId).orEmpty()) }
    val useDefault = stringResource(R.string.redirect_use_default)
    var autoRedirect by remember(feedId) { mutableStateOf(settings.redirectsOrdinaryTaps(feedId)) }
    CompactSettingDropdownRow(
        title = stringResource(R.string.redirect_feed),
        currentValue = selected,
        options = (
            listOf(SettingDropdownOption("", useDefault)) +
                prefixes.map { SettingDropdownOption(it, it) }
            ).toImmutableList(),
        onOptionSelected = {
            selected = it
            settings.setFeedPrefix(feedId, it.ifEmpty { null })
        },
    )
    SettingSwitchItem(
        title = stringResource(R.string.redirect_ordinary_taps),
        isChecked = autoRedirect,
        onCheckedChange = {
            autoRedirect = it
            settings.setRedirectsOrdinaryTaps(feedId, it)
        },
    )
}
