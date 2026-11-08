package dev.harlow.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.harlow.core.designsystem.R
import dev.harlow.core.designsystem.theme.HarlowPalette
import dev.harlow.core.designsystem.theme.HarlowShapes
import dev.harlow.core.designsystem.theme.HarlowTheme

/**
 * Dialog sized for the car: large text and buttons of at least 76 dp. Title, description and [content] scroll when
 * the head unit is too short for them; the buttons stay visible.
 */
@Composable
fun HarlowDialog(
    onDismissRequest: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    dismissLabel: String = stringResource(R.string.dialog_cancel),
    confirmLabel: String? = null,
    confirmColor: Color = HarlowPalette.Stone950,
    confirmBackground: Color = HarlowPalette.Stone200,
    confirmEnabled: Boolean = true,
    onConfirm: (() -> Unit)? = null,
    content: (@Composable () -> Unit)? = null,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = modifier
                .padding(32.dp)
                .widthIn(max = 720.dp)
                .fillMaxWidth()
                .clip(HarlowShapes.ExtraLarge)
                .background(HarlowTheme.colors.surfaceElevated)
                .border(1.dp, Color.White.copy(alpha = 0.04f), HarlowShapes.ExtraLarge),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 32.dp, end = 32.dp, top = 32.dp, bottom = 8.dp),
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.headlineSmall.copy(fontSize = 28.sp),
                    color = HarlowTheme.colors.foreground,
                )
                if (description != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        description,
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 20.sp, lineHeight = 28.sp),
                        color = HarlowTheme.colors.muted,
                    )
                }
                if (content != null) {
                    Spacer(Modifier.height(16.dp))
                    content()
                }
            }

            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, bottom = 24.dp, top = 8.dp),
            ) {
                DialogButton(
                    label = dismissLabel,
                    color = HarlowTheme.colors.muted,
                    background = Color.Transparent,
                    onClick = onDismissRequest,
                )
                if (onConfirm != null && confirmLabel != null) {
                    Spacer(Modifier.width(12.dp))
                    DialogButton(
                        label = confirmLabel,
                        color = if (confirmEnabled) confirmColor else HarlowPalette.Stone500,
                        background = if (confirmEnabled) confirmBackground else HarlowPalette.Stone700,
                        onClick = onConfirm.takeIf { confirmEnabled },
                    )
                }
            }
        }
    }
}

@Composable
private fun DialogButton(label: String, color: Color, background: Color, onClick: (() -> Unit)?) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .heightIn(min = 76.dp)
            .widthIn(min = 120.dp)
            .clip(HarlowShapes.Full)
            .background(background)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 28.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge.copy(fontSize = 22.sp), color = color)
    }
}
