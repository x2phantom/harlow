package dev.harlow.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.harlow.core.designsystem.theme.HarlowShapes
import dev.harlow.core.designsystem.theme.HarlowTheme

/** A choice in a picker dialog, at least 76 dp high for the car; [description] adds a note below the label. */
@Composable
fun HarlowRadioOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 76.dp)
            .clip(HarlowShapes.Small)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = if (description != null) 12.dp else 0.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(32.dp)
                .border(
                    width = 3.dp,
                    color = if (selected) HarlowTheme.colors.controlSelected else HarlowTheme.colors.controlUnselected,
                    shape = CircleShape,
                ),
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(HarlowTheme.colors.controlSelected),
                )
            }
        }
        Spacer(Modifier.width(20.dp))
        Column {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 22.sp),
                color = HarlowTheme.colors.foreground,
            )
            if (description != null) {
                Text(
                    description,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp, lineHeight = 24.sp),
                    color = HarlowTheme.colors.muted,
                )
            }
        }
    }
}
