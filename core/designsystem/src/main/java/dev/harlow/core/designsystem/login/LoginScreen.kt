package dev.harlow.core.designsystem.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.LocalAutofillHighlightColor
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.harlow.core.designsystem.R
import dev.harlow.core.designsystem.icon.PhosphorIcons
import dev.harlow.core.designsystem.theme.HarlowPalette
import dev.harlow.core.designsystem.theme.HarlowShapes
import dev.harlow.core.designsystem.theme.HarlowSpacing
import dev.harlow.core.designsystem.theme.HarlowTheme

/** From this width (landscape head units) the app's name and the form sit side by side. */
private val TWO_COLUMN_MIN_WIDTH = 900.dp

/** Below this height the form gets tighter spacing. */
private val COMPACT_MAX_HEIGHT = 600.dp

/** Share of the launcher foreground's canvas that the glyph covers. */
private const val LOGO_GLYPH_FRACTION = 0.55f

/**
 * Sign-in, parked-only. Sized for the car: fields and buttons at least 76 dp, text readable at arm's length. Wide
 * landscape screens get two columns (logo and name left, form right); narrow or portrait ones a single column. The
 * form scrolls; the surrounding [dev.harlow.core.designsystem.component.HarlowWindow] makes room for the on-screen
 * keyboard (its insets include the IME), so the focused field stays visible.
 */
@Composable
fun LoginScreen(
    modifier: Modifier = Modifier,
    logo: Painter? = null,
    onSignIn: (serverUrl: String, username: String, password: String) -> Unit = { _, _, _ -> },
    isLoading: Boolean = false,
    error: String? = null,
    onClose: (() -> Unit)? = null,
) {
    var serverUrl by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    // Only HTTPS: the address is normalized and http:// rejected when signing in (normalizeServerUrl).
    val signIn = { onSignIn(serverUrl, username, password) }

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = HarlowPalette.Stone500,
        unfocusedBorderColor = HarlowTheme.colors.border,
        focusedContainerColor = HarlowTheme.colors.surface,
        unfocusedContainerColor = HarlowTheme.colors.surface,
        focusedTextColor = HarlowTheme.colors.foreground,
        unfocusedTextColor = HarlowTheme.colors.foreground,
        cursorColor = HarlowTheme.colors.foreground,
        focusedLabelColor = HarlowTheme.colors.muted,
        unfocusedLabelColor = HarlowTheme.colors.muted,
    )

    val form: @Composable (compact: Boolean) -> Unit = { compact ->
        LoginForm(
            serverUrl = serverUrl,
            onServerUrlChange = { serverUrl = it },
            username = username,
            onUsernameChange = { username = it },
            password = password,
            onPasswordChange = { password = it },
            isLoading = isLoading,
            error = error,
            textFieldColors = textFieldColors,
            onSignIn = signIn,
            compact = compact,
        )
    }

    @Suppress("DEPRECATION")
    CompositionLocalProvider(LocalAutofillHighlightColor provides Color.Transparent) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(HarlowTheme.colors.background),
        contentAlignment = Alignment.Center,
    ) {
        val twoColumns = maxWidth >= TWO_COLUMN_MIN_WIDTH && maxWidth > maxHeight
        val screenHeight = maxHeight
        // Short head units (e.g. 1920×720 at 240 dpi = 480 dp): tighter form, so "Sign in" stays on screen.
        val compact = maxHeight < COMPACT_MAX_HEIGHT
        if (twoColumns) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 112.dp, end = HarlowSpacing.Sp16),
            ) {
                Branding(
                    logo = logo,
                    logoSize = 160.dp,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = HarlowSpacing.Sp12),
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .widthIn(max = 640.dp)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                ) {
                    // At least screen high, so the form is centered until the keyboard needs the room.
                    Column(
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .heightIn(min = screenHeight)
                            .padding(vertical = if (compact) HarlowSpacing.Sp2 else HarlowSpacing.Sp8),
                    ) {
                        form(compact)
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .heightIn(min = screenHeight)
                        .padding(horizontal = HarlowSpacing.Sp8, vertical = 112.dp),
                ) {
                    Branding(logo = logo, logoSize = 112.dp)
                    Spacer(Modifier.height(HarlowSpacing.Sp12))
                    form(compact)
                }
            }
        }

        // AAOS has no system back button: a screen opened from outside the app needs its own way out.
        if (onClose != null) {
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(HarlowSpacing.Sp4)
                    .size(76.dp),
            ) {
                Icon(
                    PhosphorIcons.ArrowLeft,
                    contentDescription = stringResource(R.string.login_close),
                    tint = HarlowTheme.colors.foreground,
                    modifier = Modifier.size(36.dp),
                )
            }
        }
    }
    }
}

@Composable
private fun Branding(logo: Painter?, logoSize: Dp, modifier: Modifier = Modifier) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        if (logo != null) {
            // The launcher icon's foreground: the glyph fills only the middle of its canvas (adaptive-icon safe zone),
            // so draw it larger and let the transparent margin overflow. Tinted, so it shows in the light theme too.
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(logoSize)) {
                Icon(
                    painter = logo,
                    contentDescription = null,
                    tint = HarlowTheme.colors.foreground,
                    modifier = Modifier.requiredSize(logoSize / LOGO_GLYPH_FRACTION),
                )
            }
            Spacer(Modifier.height(HarlowSpacing.Sp4))
        }
        Text(
            text = "Harlow",
            style = MaterialTheme.typography.displayLarge.copy(fontSize = 64.sp),
            color = HarlowTheme.colors.foreground,
        )
        Text(
            text = stringResource(R.string.login_subtitle),
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 24.sp),
            color = HarlowTheme.colors.muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = HarlowSpacing.Sp3),
        )
    }
}

@Composable
private fun LoginForm(
    serverUrl: String,
    onServerUrlChange: (String) -> Unit,
    username: String,
    onUsernameChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    isLoading: Boolean,
    error: String?,
    textFieldColors: androidx.compose.material3.TextFieldColors,
    onSignIn: () -> Unit,
    compact: Boolean,
) {
    val gap = if (compact) HarlowSpacing.Sp2 else HarlowSpacing.Sp5
    val canSignIn = !isLoading && serverUrl.isNotBlank() && username.isNotBlank()

    FieldLabel(stringResource(R.string.login_server_address), compact)
    OutlinedTextField(
        value = serverUrl,
        onValueChange = onServerUrlChange,
        placeholder = { Text("jellyfin.example.com", style = FIELD_TEXT, color = HarlowPalette.Stone600) },
        textStyle = FIELD_TEXT,
        shape = HarlowShapes.Medium,
        colors = textFieldColors,
        singleLine = true,
        enabled = !isLoading,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
        modifier = FIELD_MODIFIER,
    )
    if (!compact) {
        Text(
            stringResource(R.string.login_server_address_hint),
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp),
            color = HarlowTheme.colors.muted,
            modifier = Modifier.padding(top = HarlowSpacing.Sp2),
        )
    }

    Spacer(Modifier.height(gap))

    FieldLabel(stringResource(R.string.login_username), compact)
    OutlinedTextField(
        value = username,
        onValueChange = onUsernameChange,
        placeholder = {
            Text(stringResource(R.string.login_username), style = FIELD_TEXT, color = HarlowPalette.Stone600)
        },
        textStyle = FIELD_TEXT,
        shape = HarlowShapes.Medium,
        colors = textFieldColors,
        singleLine = true,
        enabled = !isLoading,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        modifier = FIELD_MODIFIER,
    )

    Spacer(Modifier.height(gap))

    FieldLabel(stringResource(R.string.login_password), compact)
    OutlinedTextField(
        value = password,
        onValueChange = onPasswordChange,
        placeholder = {
            Text(stringResource(R.string.login_password), style = FIELD_TEXT, color = HarlowPalette.Stone600)
        },
        textStyle = FIELD_TEXT,
        shape = HarlowShapes.Medium,
        colors = textFieldColors,
        singleLine = true,
        enabled = !isLoading,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { if (canSignIn) onSignIn() }),
        modifier = FIELD_MODIFIER,
    )

    Spacer(Modifier.height(if (compact) HarlowSpacing.Sp3 else HarlowSpacing.Sp6))

    if (error != null) {
        Text(
            text = error,
            color = HarlowTheme.colors.error,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 20.sp),
            modifier = Modifier.padding(bottom = if (compact) HarlowSpacing.Sp2 else HarlowSpacing.Sp4),
        )
    }

    Button(
        onClick = onSignIn,
        enabled = canSignIn,
        shape = HarlowShapes.Medium,
        colors = ButtonDefaults.buttonColors(
            containerColor = HarlowPalette.Stone200,
            contentColor = HarlowPalette.Stone950,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp),
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                color = HarlowPalette.Stone950,
                strokeWidth = 3.dp,
                modifier = Modifier.size(32.dp),
            )
        } else {
            Text(
                stringResource(R.string.login_sign_in),
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 24.sp),
            )
        }
    }
}

private val FIELD_TEXT = TextStyle(fontSize = 24.sp)

private val FIELD_MODIFIER = Modifier
    .fillMaxWidth()
    .heightIn(min = 76.dp)

@Composable
private fun FieldLabel(text: String, compact: Boolean) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge.copy(fontSize = 20.sp),
        color = HarlowTheme.colors.muted,
        modifier = Modifier.padding(bottom = if (compact) HarlowSpacing.Sp1 else HarlowSpacing.Sp2),
    )
}
