package com.retrobit.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.retrobit.app.AppViewModel
import com.retrobit.app.R
import com.retrobit.app.data.ConsoleCatalog
import com.retrobit.app.data.LibraryGame
import com.retrobit.app.ui.components.ConsoleGlyph
import com.retrobit.app.ui.components.EmptyState
import com.retrobit.app.ui.components.GameCover
import com.retrobit.app.ui.components.LocalIsTv
import com.retrobit.app.ui.components.ScreenHeader
import com.retrobit.app.ui.components.consoleColor
import com.retrobit.app.ui.components.tvFocus

@Composable
fun StatsScreen(
    vm: AppViewModel,
    isTr: Boolean,
    onBack: () -> Unit
) {
    val library by vm.library.collectAsStateWithLifecycle()
    val covers by vm.coverUrls.collectAsStateWithLifecycle()

    // Cover downloads update a separate map on every image; the aggregate only follows the library.
    val summary = remember(library) {
        val played = library.filter { it.playSeconds > 0 }.sortedByDescending { it.playSeconds }
        StatsSummary(
            totalSeconds = library.sumOf { it.playSeconds },
            played = played,
            byConsole = played.groupBy { it.consoleId }
                .mapValues { entry -> entry.value.sumOf { it.playSeconds } }
                .toList()
                .sortedByDescending { it.second }
        )
    }
    val totalSeconds = summary.totalSeconds
    val played = summary.played
    val byConsole = summary.byConsole

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 36.dp)
    ) {
        item {
            ScreenHeader(
                title = stringResource(R.string.stats_title),
                subtitle = stringResource(R.string.stats_note),
                onBack = onBack
            )
        }

        if (played.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Rounded.BarChart,
                    title = stringResource(R.string.stats_title),
                    description = stringResource(R.string.stats_empty)
                )
            }
        } else {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Text(
                            stringResource(R.string.stats_total_time),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            playTimeText(totalSeconds),
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StatPill("${played.size} ${stringResource(R.string.stats_games)}")
                            Spacer(Modifier.width(8.dp))
                            StatPill("${byConsole.size} ${stringResource(R.string.stats_consoles)}")
                        }
                    }
                }
            }

            item {
                Text(
                    stringResource(R.string.stats_most_played).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 20.dp, top = 18.dp, bottom = 8.dp)
                )
            }

            itemsIndexed(played.take(8)) { index, game ->
                MostPlayedRow(
                    rank = index + 1,
                    game = game,
                    coverUrl = covers["lib:${game.id}"] ?: game.coverUrl,
                    isTr = isTr,
                    share = game.playSeconds.toFloat() / played.first().playSeconds.coerceAtLeast(1L)
                )
            }

            item {
                Text(
                    stringResource(R.string.stats_console_split).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 8.dp)
                )
            }

            items(byConsole) { (consoleId, seconds) ->
                ConsoleStatRow(
                    consoleId = consoleId,
                    seconds = seconds,
                    share = seconds.toFloat() / totalSeconds.coerceAtLeast(1L),
                    isTr = isTr
                )
            }

            item {
                Text(
                    stringResource(R.string.stats_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)
                )
            }
        }
    }
}

@Composable
private fun StatPill(text: String) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun MostPlayedRow(
    rank: Int,
    game: LibraryGame,
    coverUrl: String?,
    isTr: Boolean,
    share: Float
) {
    val console = ConsoleCatalog.get(game.consoleId)
    Row(
        Modifier.fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .tvFocus(RoundedCornerShape(12.dp), consoleColor(console))
            .focusable(enabled = LocalIsTv.current)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "$rank",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.width(24.dp)
        )
        GameCover(
            url = coverUrl,
            title = game.title,
            console = console,
            modifier = Modifier.size(46.dp),
            corner = 11.dp,
            showMonogram = false
        )
        Column(Modifier.weight(1f).padding(horizontal = 11.dp)) {
            Text(
                game.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                console?.localizedName(isTr) ?: game.consoleId,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Spacer(Modifier.height(5.dp))
            LinearProgressIndicator(
                progress = { share.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                color = consoleColor(console),
                trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            )
        }
        Text(
            playTimeText(game.playSeconds),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ConsoleStatRow(consoleId: String, seconds: Long, share: Float, isTr: Boolean) {
    val console = ConsoleCatalog.get(consoleId)
    val accent = consoleColor(console)
    Row(
        Modifier.fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .tvFocus(RoundedCornerShape(12.dp), accent)
            .focusable(enabled = LocalIsTv.current)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            ConsoleGlyph(console?.glyph, Modifier.size(18.dp), tint = accent)
        }
        Column(Modifier.weight(1f).padding(horizontal = 11.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    console?.localizedName(isTr) ?: consoleId,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    playTimeText(seconds),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { share.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = accent,
                trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            )
        }
    }
}

@Composable
private fun playTimeText(seconds: Long): String {
    val totalMinutes = seconds / 60
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 -> stringResource(R.string.stats_time_hm, hours.toInt(), minutes.toInt())
        minutes > 0 -> stringResource(R.string.stats_time_m, minutes.toInt())
        else -> stringResource(R.string.stats_time_lt)
    }
}

private data class StatsSummary(
    val totalSeconds: Long,
    val played: List<LibraryGame>,
    val byConsole: List<Pair<String, Long>>
)
