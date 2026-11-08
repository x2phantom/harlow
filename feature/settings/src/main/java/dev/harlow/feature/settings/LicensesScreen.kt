package dev.harlow.feature.settings

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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.aboutlibraries.entity.Library
import dev.harlow.core.designsystem.component.HarlowDialog
import dev.harlow.core.designsystem.icon.PhosphorIcons
import dev.harlow.core.designsystem.theme.HarlowSpacing
import dev.harlow.core.designsystem.theme.HarlowTheme

/**
 * Open-source licenses. Tapping a library shows its license text in the app: head units usually have no browser, so
 * links can't be opened.
 */
@Composable
fun LicensesScreen(
    communityLibraries: List<Library>,
    platformLibraries: List<Library>,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
) {
    var shownLibrary by remember { mutableStateOf<Library?>(null) }

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
                stringResource(R.string.licenses_title),
                style = MaterialTheme.typography.headlineLarge.copy(fontSize = 32.sp),
                color = HarlowTheme.colors.foreground,
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = HarlowSpacing.Sp6),
        ) {
            items(communityLibraries, key = { it.uniqueId }) { library ->
                LibraryRow(library, onClick = { shownLibrary = library })
                HorizontalDivider(color = HarlowTheme.colors.border)
            }
            item(key = "section-platform") {
                SectionHeader(stringResource(R.string.licenses_section_platform))
            }
            items(platformLibraries, key = { "platform-" + it.uniqueId }) { library ->
                LibraryRow(library, onClick = { shownLibrary = library })
                HorizontalDivider(color = HarlowTheme.colors.border)
            }
        }
    }

    shownLibrary?.let { library ->
        HarlowDialog(
            onDismissRequest = { shownLibrary = null },
            title = library.name,
            dismissLabel = stringResource(R.string.licenses_close),
            description = licenseText(library).ifBlank { stringResource(R.string.licenses_none) },
        )
    }
}

private fun licenseText(library: Library): String {
    val texts = library.licenses.map { license ->
        license.licenseContent?.takeIf { it.isNotBlank() }
            ?: listOfNotNull(license.name, license.url).joinToString("\n")
    }
    return texts.joinToString("\n\n")
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelLarge.copy(fontSize = 18.sp),
        color = HarlowTheme.colors.muted,
        modifier = Modifier.padding(start = HarlowSpacing.Sp4, top = HarlowSpacing.Sp6, bottom = HarlowSpacing.Sp2),
    )
}

@Composable
private fun LibraryRow(library: Library, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 88.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = HarlowSpacing.Sp4, vertical = HarlowSpacing.Sp3),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    library.name,
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 24.sp),
                    color = HarlowTheme.colors.foreground,
                )
                val version = library.artifactVersion
                if (!version.isNullOrBlank()) {
                    Spacer(Modifier.width(HarlowSpacing.Sp3))
                    Text(
                        version,
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 19.sp),
                        color = HarlowTheme.colors.muted,
                    )
                }
            }
            val licenseName = library.licenses.firstOrNull()?.name
            if (!licenseName.isNullOrBlank()) {
                Text(
                    licenseName,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 19.sp),
                    color = HarlowTheme.colors.muted,
                )
            }
        }
        Icon(
            PhosphorIcons.CaretRight,
            contentDescription = null,
            tint = HarlowTheme.colors.muted,
            modifier = Modifier.size(28.dp),
        )
    }
}
