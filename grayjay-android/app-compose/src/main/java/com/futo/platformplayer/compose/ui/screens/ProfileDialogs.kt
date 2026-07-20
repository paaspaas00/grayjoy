package com.futo.platformplayer.compose.ui.screens

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.futo.platformplayer.compose.R
import com.futo.platformplayer.compose.ui.ProfileProtection
import com.futo.platformplayer.compose.ui.ProfileUiModel

@Composable
fun ProfileSwitcherDialogs(
    profiles: List<ProfileUiModel>,
    activeProfileId: String,
    visible: Boolean,
    onDismiss: () -> Unit,
    onSwitch: (String) -> Unit,
    onCreate: (String, String) -> Unit,
    onVerifyPin: (String, String) -> Boolean,
) {
    if (!visible) return
    val context = LocalContext.current
    val deviceAuthUnavailableWindow = stringResource(R.string.device_auth_unavailable_window)
    val deviceAuthEnrollFirst = stringResource(R.string.device_auth_enroll_first)
    val deviceAuthNoHardware = stringResource(R.string.device_auth_no_hardware)
    val deviceAuthUnavailable = stringResource(R.string.device_auth_unavailable)
    val deviceAuthNotRecognized = stringResource(R.string.device_auth_not_recognized)
    val profileUnlockFailed = stringResource(R.string.profile_unlock_failed)
    val deviceAuthSubtitle = stringResource(R.string.device_auth_subtitle)
    val deviceAuthStartFailed = stringResource(R.string.device_auth_start_failed)
    val incorrectPin = stringResource(R.string.incorrect_pin)
    val unlockTitles = profiles.associate { profile ->
        profile.id to stringResource(R.string.unlock_profile, profile.name)
    }
    var pinProfileId by rememberSaveable { mutableStateOf<String?>(null) }
    var pin by rememberSaveable { mutableStateOf("") }
    var pinError by rememberSaveable { mutableStateOf<String?>(null) }
    var showCreate by rememberSaveable { mutableStateOf(false) }
    var unlockError by rememberSaveable { mutableStateOf<String?>(null) }

    fun switchAfterUnlock(profileId: String) {
        onSwitch(profileId)
        onDismiss()
    }

    fun authenticateDevice(profile: ProfileUiModel) {
        unlockError = null
        val activity = context.findFragmentActivity()
        if (activity == null) {
            unlockError = deviceAuthUnavailableWindow
            return
        }
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_WEAK or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        when (BiometricManager.from(context).canAuthenticate(authenticators)) {
            BiometricManager.BIOMETRIC_SUCCESS -> Unit
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                unlockError = deviceAuthEnrollFirst
                return
            }
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> {
                unlockError = deviceAuthNoHardware
                return
            }
            else -> {
                unlockError = deviceAuthUnavailable
                return
            }
        }

        runCatching {
            val prompt = BiometricPrompt(
                activity,
                ContextCompat.getMainExecutor(context),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        switchAfterUnlock(profile.id)
                    }

                    override fun onAuthenticationFailed() {
                        unlockError = deviceAuthNotRecognized
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        if (
                            errorCode != BiometricPrompt.ERROR_CANCELED &&
                            errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                            errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON
                        ) {
                            unlockError = errString.toString().ifBlank {
                                profileUnlockFailed
                            }
                        }
                    }
                },
            )
            prompt.authenticate(
                BiometricPrompt.PromptInfo.Builder()
                    .setTitle(unlockTitles[profile.id] ?: profile.name)
                    .setSubtitle(deviceAuthSubtitle)
                    .setAllowedAuthenticators(authenticators)
                    .build(),
            )
        }.onFailure { error ->
            unlockError = error.localizedMessage
                ?.takeIf(String::isNotBlank)
                ?: deviceAuthStartFailed
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.profiles)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                profiles.forEach { profile ->
                    ListItem(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                when {
                                    profile.id == activeProfileId -> onDismiss()
                                    profile.protection == ProfileProtection.None -> switchAfterUnlock(profile.id)
                                    profile.protection == ProfileProtection.DeviceCredential -> authenticateDevice(profile)
                                    else -> {
                                        pinProfileId = profile.id
                                        pin = ""
                                        pinError = null
                                    }
                                }
                            }
                            .testTag("profile-${profile.id}"),
                        headlineContent = { Text(profile.name) },
                        supportingContent = {
                            Text(
                                when (profile.protection) {
                                    ProfileProtection.None -> stringResource(R.string.standard_profile)
                                    ProfileProtection.DeviceCredential -> stringResource(R.string.fingerprint_or_device_lock)
                                    ProfileProtection.Pin -> stringResource(R.string.profile_pin)
                                },
                            )
                        },
                        leadingContent = {
                            Icon(
                                if (profile.protection == ProfileProtection.None) {
                                    Icons.Outlined.Person
                                } else {
                                    Icons.Outlined.Lock
                                },
                                contentDescription = null,
                            )
                        },
                        trailingContent = {
                            RadioButton(
                                selected = profile.id == activeProfileId,
                                onClick = null,
                            )
                        },
                    )
                }
                unlockError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = { showCreate = true }) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Text(stringResource(R.string.add_profile))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )

    pinProfileId?.let { profileId ->
        val profile = profiles.firstOrNull { it.id == profileId }
        AlertDialog(
            onDismissRequest = { pinProfileId = null },
            title = {
                Text(
                    stringResource(
                        R.string.unlock_profile,
                        profile?.name ?: stringResource(R.string.unlock_profile_fallback),
                    ),
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { pin = it.filter(Char::isDigit).take(12) },
                        label = { Text(stringResource(R.string.pin)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                    )
                    pinError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (onVerifyPin(profileId, pin)) {
                            pinProfileId = null
                            switchAfterUnlock(profileId)
                        } else {
                            pinError = incorrectPin
                        }
                    },
                    enabled = pin.length >= 4,
                ) { Text(stringResource(R.string.unlock)) }
            },
            dismissButton = {
                TextButton(onClick = { pinProfileId = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    if (showCreate) {
        CreateProfileDialog(
            onDismiss = { showCreate = false },
            onCreate = { name, newPin ->
                showCreate = false
                onCreate(name, newPin)
                onDismiss()
            },
        )
    }
}

private tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}

@Composable
private fun CreateProfileDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var pin by rememberSaveable { mutableStateOf("") }
    var confirmation by rememberSaveable { mutableStateOf("") }
    var protectWithPin by rememberSaveable { mutableStateOf(false) }
    val valid = name.isNotBlank() && (
        !protectWithPin || (pin.length >= 4 && pin == confirmation)
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_profile)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.profile_isolation_description))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    label = { Text(stringResource(R.string.profile_name)) },
                    singleLine = true,
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.protect_with_pin)) },
                    supportingContent = { Text(stringResource(R.string.protect_with_pin_description)) },
                    trailingContent = {
                        Switch(
                            checked = protectWithPin,
                            onCheckedChange = { protectWithPin = it },
                        )
                    },
                    modifier = Modifier.clickable { protectWithPin = !protectWithPin },
                )
                if (protectWithPin) {
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { pin = it.filter(Char::isDigit).take(12) },
                        label = { Text(stringResource(R.string.pin)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = confirmation,
                        onValueChange = { confirmation = it.filter(Char::isDigit).take(12) },
                        label = { Text(stringResource(R.string.confirm_pin)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        isError = confirmation.isNotEmpty() && confirmation != pin,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(name, pin.takeIf { protectWithPin }.orEmpty()) },
                enabled = valid,
            ) { Text(stringResource(R.string.create)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
