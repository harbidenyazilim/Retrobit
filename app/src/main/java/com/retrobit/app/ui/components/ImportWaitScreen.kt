package com.retrobit.app.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.retrobit.app.R
import com.retrobit.app.data.ImportProgress
import kotlinx.coroutines.delay

@Composable
fun ImportProgressDetails(progress: ImportProgress?, accent: Color, modifier: Modifier = Modifier) {
    val scanning = progress == null || progress.scanning
    val fraction = progress?.fraction?.takeUnless { scanning }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            stringResource(if (scanning) R.string.import_stage_prepare else R.string.import_stage_games),
            style = MaterialTheme.typography.labelLarge,
            color = accent
        )
        if (fraction == null) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = accent)
        } else {
            LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth(), color = accent)
        }
        Text(
            if (scanning) stringResource(R.string.import_prepare_detail)
            else stringResource(R.string.import_progress, progress?.current ?: 0, progress?.total ?: 0),
            style = MaterialTheme.typography.bodySmall,
            color = retroOnSurface(0.78f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        val current = progress?.note?.let { stringResource(R.string.import_opening, it) }
            ?: progress?.fileName.orEmpty()
        if (current.isNotBlank()) {
            Text(current, style = MaterialTheme.typography.labelSmall, color = retroOnSurface(0.65f),
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun ImportWaitScreen(progress: ImportProgress?, onArcade: () -> Unit, onBack: () -> Unit) {
    val focus = remember { FocusRequester() }
    val accent = Color(0xFFF5C518)
    BackHandler(onBack = onBack)
    LaunchedEffect(Unit) {
        delay(80)
        focus.requestFocus()
    }
    RequestFocusOnWindowFocus(focus)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        RetroBackdrop(null, scanlines = false, dim = 0.65f)
        Column(
            Modifier.padding(LocalSafeArea.current).widthIn(max = 540.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(stringResource(R.string.import_library_title), style = MaterialTheme.typography.headlineSmall,
                color = retroOnSurface())
            Text(stringResource(R.string.import_library_locked), style = MaterialTheme.typography.bodyMedium,
                color = retroOnSurface(0.78f))
            ImportProgressDetails(progress, accent, Modifier.fillMaxWidth())
            Spacer(Modifier.height(4.dp))
            RetroNavButton(Icons.Rounded.SportsEsports, stringResource(R.string.wait_play), accent, onArcade,
                modifier = Modifier.focusRequester(focus))
            RetroNavButton(Icons.Rounded.ArrowBack, stringResource(R.string.hint_back), accent, onBack)
        }
    }
}
