package dev.harlow.feature.settings

import android.net.Uri
import android.text.format.DateUtils
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.harlow.core.common.StreamQuality
import dev.harlow.core.data.SyncProgress
import dev.harlow.core.designsystem.component.HarlowDialog
import dev.harlow.core.designsystem.component.HarlowRadioOption
import dev.harlow.core.designsystem.icon.PhosphorIcons
import dev.harlow.core.designsystem.theme.HarlowSpacing
import dev.harlow.core.designsystem.theme.HarlowTheme
import dev.harlow.core.network.ConnectionState

/** Touch targets in the car: at least 76 dp (car app quality). */
private val MIN_ROW_HEIGHT = 88.dp

private const val SYNC_MANUAL = 0

private val SYNC_INTERVALS = listOf(SYNC_MANUAL, 1, 6, 12, 24)

/**
 * Harlow's settings, opened from the car's media center. Parked-only; sized for arm's length (rows ≥ 76 dp) and with
 * its own back button, since AAOS has no guaranteed system back.
 *
 * Downloads have no settings here: the media center can't start one, so there is nothing to configure yet.
 */
@Composable
fun SettingsScreen(
    appVersion: String,
    serverUrl: String,
    connectionState: ConnectionState,
    lastSyncTimestamp: Long,
    isSyncing: Boolean,
    syncProgress: SyncProgress?,
    isCleaningUp: Boolean,
    isForceOffline: Boolean,
    autoSyncIntervalHours: Int,
    streamQuality: StreamQuality,
    autoResume: Boolean,
    listView: Boolean,
    onBack: () -> Unit,
    onSyncNow: () -> Unit,
    onCleanup: () -> Unit,
    onForceOfflineChange: (Boolean) -> Unit,
    onAutoSyncIntervalChange: (Int) -> Unit,
    onStreamQualityChange: (StreamQuality) -> Unit,
    onAutoResumeChange: (Boolean) -> Unit,
    onListViewChange: (Boolean) -> Unit,
    onLicensesClick: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showSyncIntervalDialog by rememberSaveable { mutableStateOf(false) }
    var showStreamQualityDialog by rememberSaveable { mutableStateOf(false) }
    var showLogoutDialog by rememberSaveable { mutableStateOf(false) }
    val canReachServer = connectionState == ConnectionState.Connected && !isForceOffline

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(HarlowTheme.colors.background),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = HarlowSpacing.Sp4, vertical = HarlowSpacing.Sp3),
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(76.dp)) {
                Icon(
                    PhosphorIcons.ArrowLeft,
                    contentDescription = stringResource(R.string.back),
                    tint = HarlowTheme.colors.foreground,
                    modifier = Modifier.size(36.dp),
                )
            }
            Spacer(Modifier.width(HarlowSpacing.Sp3))
            Text(
                stringResource(R.string.settings_screen_title),
                style = MaterialTheme.typography.headlineLarge.copy(fontSize = 32.sp),
                color = HarlowTheme.colors.foreground,
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = HarlowSpacing.Sp6),
        ) {
            section(R.string.settings_section_server) {
                item(key = "server") {
                    SettingsRow(
                        icon = PhosphorIcons.HardDrives,
                        title = Uri.parse(serverUrl).host ?: serverUrl,
                        value = connectionLabel(connectionState, isForceOffline),
                    )
                }
            }

            section(R.string.settings_section_playback) {
                item(key = "stream-quality") {
                    SettingsRow(
                        icon = PhosphorIcons.MusicNotes,
                        title = stringResource(R.string.settings_stream_quality),
                        value = streamQualityLabel(streamQuality),
                        onClick = { showStreamQualityDialog = true },
                    )
                }
                item(key = "auto-resume") {
                    SettingsSwitchRow(
                        icon = PhosphorIcons.PlayCircle,
                        title = stringResource(R.string.settings_auto_resume),
                        value = stringResource(R.string.settings_auto_resume_summary),
                        checked = autoResume,
                        onCheckedChange = onAutoResumeChange,
                    )
                }
            }

            section(R.string.settings_section_library) {
                item(key = "sync-now") {
                    SettingsRow(
                        icon = PhosphorIcons.ArrowsClockwise,
                        title = stringResource(R.string.settings_sync_now),
                        value = syncLabel(isSyncing, syncProgress, lastSyncTimestamp, canReachServer),
                        enabled = canReachServer && !isSyncing,
                        busy = isSyncing,
                        onClick = onSyncNow,
                    )
                }
                item(key = "auto-sync") {
                    SettingsRow(
                        icon = PhosphorIcons.Clock,
                        title = stringResource(R.string.settings_auto_sync),
                        value = syncIntervalLabel(autoSyncIntervalHours),
                        onClick = { showSyncIntervalDialog = true },
                    )
                }
                item(key = "offline") {
                    SettingsSwitchRow(
                        icon = PhosphorIcons.CloudSlash,
                        title = stringResource(R.string.settings_work_offline),
                        value = stringResource(R.string.settings_work_offline_summary),
                        checked = isForceOffline,
                        onCheckedChange = onForceOfflineChange,
                    )
                }
                item(key = "cleanup") {
                    SettingsRow(
                        icon = PhosphorIcons.Broom,
                        title = stringResource(R.string.settings_clean_up),
                        value = stringResource(
                            if (isCleaningUp) R.string.settings_cleaning_up else R.string.settings_clean_up_summary,
                        ),
                        enabled = canReachServer && !isCleaningUp,
                        busy = isCleaningUp,
                        onClick = onCleanup,
                    )
                }
                item(key = "list-view") {
                    SettingsSwitchRow(
                        icon = PhosphorIcons.List,
                        title = stringResource(R.string.settings_list_view),
                        value = stringResource(R.string.settings_list_view_summary),
                        checked = listView,
                        onCheckedChange = onListViewChange,
                    )
                }
            }

            section(R.string.settings_section_about) {
                item(key = "version") {
                    SettingsRow(
                        icon = PhosphorIcons.Info,
                        title = stringResource(R.string.settings_version),
                        value = appVersion,
                    )
                }
                item(key = "licenses") {
                    SettingsRow(
                        icon = PhosphorIcons.TextAa,
                        title = stringResource(R.string.licenses_title),
                        value = null,
                        onClick = onLicensesClick,
                    )
                }
            }

            section(R.string.settings_section_account) {
                item(key = "logout") {
                    SettingsRow(
                        icon = PhosphorIcons.SignOut,
                        title = stringResource(R.string.settings_sign_out),
                        value = null,
                        titleColor = HarlowTheme.colors.error,
                        onClick = { showLogoutDialog = true },
                    )
                }
            }
        }
    }

    if (showSyncIntervalDialog) {
        HarlowDialog(
            onDismissRequest = { showSyncIntervalDialog = false },
            title = stringResource(R.string.settings_auto_sync),
        ) {
            Column {
                SYNC_INTERVALS.forEach { hours ->
                    HarlowRadioOption(
                        label = syncIntervalLabel(hours),
                        selected = hours == autoSyncIntervalHours,
                        onClick = {
                            onAutoSyncIntervalChange(hours)
                            showSyncIntervalDialog = false
                        },
                    )
                }
            }
        }
    }

    if (showStreamQualityDialog) {
        HarlowDialog(
            onDismissRequest = { showStreamQualityDialog = false },
            title = stringResource(R.string.settings_stream_quality),
        ) {
            Column {
                StreamQuality.entries.forEach { quality ->
                    HarlowRadioOption(
                        label = streamQualityLabel(quality),
                        description = if (quality == StreamQuality.ORIGINAL) {
                            stringResource(R.string.settings_stream_quality_original_note)
                        } else {
                            null
                        },
                        selected = quality == streamQuality,
                        onClick = {
                            onStreamQualityChange(quality)
                            showStreamQualityDialog = false
                        },
                    )
                }
            }
        }
    }

    if (showLogoutDialog) {
        HarlowDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = stringResource(R.string.settings_sign_out),
            description = stringResource(R.string.settings_sign_out_description),
            confirmLabel = stringResource(R.string.settings_sign_out),
            confirmColor = HarlowTheme.colors.error,
            confirmBackground = Color.Transparent,
            onConfirm = {
                showLogoutDialog = false
                onLogout()
            },
        )
    }
}

@Composable
private fun streamQualityLabel(quality: StreamQuality): String = when (val bitrate = quality.bitrate) {
    null -> stringResource(R.string.settings_stream_quality_original)
    else -> stringResource(R.string.settings_stream_quality_kbps, bitrate / 1000)
}

@Composable
private fun syncIntervalLabel(hours: Int): String =
    if (hours == SYNC_MANUAL) {
        stringResource(R.string.settings_auto_sync_manual)
    } else {
        pluralStringResource(R.plurals.settings_auto_sync_interval, hours, hours)
    }

@Composable
private fun connectionLabel(state: ConnectionState, isForceOffline: Boolean): String = stringResource(
    when {
        isForceOffline -> R.string.settings_connection_offline_by_choice
        state == ConnectionState.Connected -> R.string.settings_connection_connected
        state == ConnectionState.ServerUnreachable -> R.string.settings_connection_server_unreachable
        else -> R.string.settings_connection_no_network
    },
)

@Composable
private fun syncLabel(
    isSyncing: Boolean,
    progress: SyncProgress?,
    lastSyncTimestamp: Long,
    canReachServer: Boolean,
): String = when {
    isSyncing && progress != null && progress.total > 0 ->
        stringResource(
            R.string.settings_syncing_progress,
            progress.phaseLabel(LocalContext.current),
            progress.current,
            progress.total,
        )
    isSyncing -> stringResource(R.string.settings_syncing)
    !canReachServer -> stringResource(R.string.settings_sync_needs_connection)
    lastSyncTimestamp <= 0L -> stringResource(R.string.settings_not_synced)
    System.currentTimeMillis() - lastSyncTimestamp < DateUtils.MINUTE_IN_MILLIS ->
        stringResource(R.string.settings_synced_just_now)
    else -> stringResource(
        R.string.settings_synced_at,
        DateUtils.getRelativeTimeSpanString(
            lastSyncTimestamp,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS,
        ),
    )
}

/** A section header followed by its rows. [content] adds list items; it isn't composable. */
private fun LazyListScope.section(@StringRes title: Int, content: LazyListScope.() -> Unit) {
    item(key = "section-$title") { SettingsSection(stringResource(title)) }
    content()
}

@Composable
private fun SettingsSection(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelLarge.copy(fontSize = 18.sp),
        color = HarlowTheme.colors.muted,
        modifier = Modifier.padding(start = HarlowSpacing.Sp4, top = HarlowSpacing.Sp6, bottom = HarlowSpacing.Sp2),
    )
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    value: String?,
    enabled: Boolean = true,
    busy: Boolean = false,
    titleColor: Color = HarlowTheme.colors.foreground,
    onClick: (() -> Unit)? = null,
) {
    SettingsRowLayout(
        icon = icon,
        title = title,
        value = value,
        enabled = enabled,
        titleColor = titleColor,
        modifier = if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier,
    ) {
        when {
            busy -> CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 3.dp)
            onClick != null -> Icon(
                PhosphorIcons.CaretRight,
                contentDescription = null,
                tint = HarlowTheme.colors.muted,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    icon: ImageVector,
    title: String,
    value: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    SettingsRowLayout(
        icon = icon,
        title = title,
        value = value,
        enabled = true,
        titleColor = HarlowTheme.colors.foreground,
        modifier = Modifier.clickable { onCheckedChange(!checked) },
    ) {
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsRowLayout(
    icon: ImageVector,
    title: String,
    value: String?,
    enabled: Boolean,
    titleColor: Color,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit,
) {
    val alpha = if (enabled) 1f else 0.5f
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MIN_ROW_HEIGHT)
            .then(modifier)
            .padding(horizontal = HarlowSpacing.Sp4, vertical = HarlowSpacing.Sp3),
    ) {
        Icon(icon, null, tint = HarlowTheme.colors.muted.copy(alpha = alpha), modifier = Modifier.size(32.dp))
        Spacer(Modifier.width(HarlowSpacing.Sp5))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 24.sp),
                color = titleColor.copy(alpha = alpha),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (value != null) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 19.sp),
                    color = HarlowTheme.colors.muted.copy(alpha = alpha),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(HarlowSpacing.Sp4))
        trailing()
    }
}
