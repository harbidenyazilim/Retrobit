package com.retrobit.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.retrobit.app.AppViewModel
import com.retrobit.app.R
import com.retrobit.app.data.SettingsData
import com.retrobit.app.ui.components.LocalIsTv
import com.retrobit.app.ui.components.LocalSafeArea
import com.retrobit.app.ui.components.RequestFocusOnWindowFocus
import com.retrobit.app.ui.components.RomZipImportCard
import com.retrobit.app.ui.components.rememberImportLauncher
import com.retrobit.app.ui.components.rememberRomZipLauncher
import com.retrobit.app.ui.components.tvFocus

@Composable
fun SetupScreen(vm: AppViewModel, isTr: Boolean) {
    val isTv = LocalIsTv.current
    val importing by vm.importing.collectAsStateWithLifecycle()
    val pickZip = rememberRomZipLauncher(vm)
    val pickFiles = rememberImportLauncher(vm) { vm.completeOnboarding() }
    val zipFocusRequester = remember { FocusRequester() }
    RequestFocusOnWindowFocus(zipFocusRequester)

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(LocalSafeArea.current)
        ) {
            val landscape = maxWidth > maxHeight
            val compact = landscape &&
                (maxHeight < 600.dp || LocalDensity.current.fontScale > 1.15f)

            if (landscape) {
                val actionsScroll = rememberScrollState()
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(if (isTv) 24.dp else 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(0.42f).fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(
                            if (compact) 12.dp else 20.dp,
                            Alignment.CenterVertically
                        )
                    ) {
                        SetupIntroduction(landscape = true, compact = compact, isTv = isTv)
                        SetupPreferences(vm = vm, isTr = isTr, landscape = true)
                    }
                    SetupImportActions(
                        pickZip = pickZip,
                        pickFiles = pickFiles,
                        onSkip = { vm.completeOnboarding() },
                        importing = importing,
                        zipFocusRequester = zipFocusRequester,
                        modifier = Modifier
                            .weight(0.58f)
                            .fillMaxHeight()
                            .verticalScroll(
                                state = actionsScroll,
                                enabled = actionsScroll.maxValue > 0
                            )
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Column(
                        modifier = Modifier.widthIn(max = 440.dp).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        SetupIntroduction(landscape = false, compact = false, isTv = isTv)
                        SetupImportActions(
                            pickZip = pickZip,
                            pickFiles = pickFiles,
                            onSkip = { vm.completeOnboarding() },
                            importing = importing,
                            zipFocusRequester = zipFocusRequester
                        )
                        SetupPreferences(vm = vm, isTr = isTr, landscape = false)
                    }
                }
            }
        }
    }
}

@Composable
private fun SetupIntroduction(landscape: Boolean, compact: Boolean, isTv: Boolean) {
    val alignment = if (landscape) Alignment.Start else Alignment.CenterHorizontally
    val textAlign = if (landscape) TextAlign.Start else TextAlign.Center
    val logoSize = if (compact) 40.dp else if (isTv) 56.dp else 64.dp
    val brandStyle = if (landscape) MaterialTheme.typography.titleMedium
        else MaterialTheme.typography.headlineSmall

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment,
        verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)
    ) {
        if (landscape) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.size(logoSize)
                )
                Text(
                    text = stringResource(R.string.app_name).uppercase(),
                    style = brandStyle,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 3.sp,
                    modifier = Modifier.weight(1f)
                )
            }
        } else {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.size(logoSize)
            )
            Text(
                text = stringResource(R.string.app_name).uppercase(),
                style = brandStyle,
                fontWeight = FontWeight.Bold,
                letterSpacing = 3.sp,
                textAlign = textAlign
            )
        }
        Text(
            text = stringResource(R.string.setup_title),
            style = if (compact) MaterialTheme.typography.titleLarge
                else MaterialTheme.typography.headlineMedium,
            textAlign = textAlign
        )
        Text(
            text = stringResource(R.string.setup_saf_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = textAlign
        )
    }
}

@Composable
private fun SetupPreferences(vm: AppViewModel, isTr: Boolean, landscape: Boolean) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (landscape) Alignment.Start else Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = stringResource(R.string.setup_privacy),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = if (landscape) TextAlign.Start else TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = isTr,
                onClick = { vm.setLanguage(SettingsData.LANGUAGE_TR) },
                modifier = Modifier.tvFocus(RoundedCornerShape(8.dp)),
                label = { Text(stringResource(R.string.language_tr)) }
            )
            FilterChip(
                selected = !isTr,
                onClick = { vm.setLanguage(SettingsData.LANGUAGE_EN) },
                modifier = Modifier.tvFocus(RoundedCornerShape(8.dp)),
                label = { Text(stringResource(R.string.language_en)) }
            )
        }
    }
}

@Composable
private fun SetupImportActions(
    pickZip: () -> Unit,
    pickFiles: () -> Unit,
    onSkip: () -> Unit,
    importing: Boolean,
    zipFocusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    val buttonShape = RoundedCornerShape(12.dp)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)
    ) {
        RomZipImportCard(
            onPick = pickZip,
            importing = importing,
            buttonModifier = Modifier.focusRequester(zipFocusRequester)
        )
        OutlinedButton(
            onClick = pickFiles,
            enabled = !importing,
            shape = buttonShape,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            modifier = Modifier.fillMaxWidth().tvFocus(buttonShape)
        ) {
            Icon(Icons.Rounded.VideoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(9.dp))
            Text(
                text = stringResource(R.string.setup_pick_files),
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
        }
        OutlinedButton(
            onClick = onSkip,
            enabled = !importing,
            shape = buttonShape,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            modifier = Modifier.fillMaxWidth().tvFocus(buttonShape)
        ) {
            Text(
                text = stringResource(R.string.setup_skip_roms),
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center
            )
        }
    }
}
