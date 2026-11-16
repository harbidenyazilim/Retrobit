package com.retrobit.app.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.retrobit.app.AppViewModel
import com.retrobit.app.R
import com.retrobit.app.data.ConsoleCatalog
import com.retrobit.app.data.LibraryGame
import com.retrobit.app.ui.components.EmptyState
import com.retrobit.app.ui.components.ScreenHeader
import com.retrobit.app.ui.components.consoleColor
import com.retrobit.app.ui.components.tvFocus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.Locale
import kotlin.math.min

@Composable
fun ShowcaseScreen(
    vm: AppViewModel,
    onBack: () -> Unit
) {
    val library by vm.library.collectAsStateWithLifecycle()
    val covers by vm.coverUrls.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var building by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var output by remember { mutableStateOf<File?>(null) }

    fun create() {
        if (building) return
        building = true
        failed = false
        val games = library.take(12)
        scope.launch {
            val missing = games.filter { !vm.coverUrls.value.containsKey("lib:${it.id}") }
            missing.forEach { vm.ensureCover(it) }
            if (missing.isNotEmpty()) {
                withTimeoutOrNull(10_000) {
                    vm.coverUrls.first { map -> missing.all { map.containsKey("lib:${it.id}") } }
                }
            }
            val file = runCatching {
                buildCollage(context, games, vm.coverUrls.value)
            }.getOrNull()
            building = false
            if (file == null) failed = true else output = file
        }
    }

    fun share(file: File) {
        runCatching {
            val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(
                Intent.createChooser(send, context.getString(R.string.showcase_share_title))
            )
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 36.dp)
    ) {
        item {
            ScreenHeader(
                title = stringResource(R.string.showcase),
                onBack = onBack
            )
        }
        item {
            Text(
                stringResource(R.string.showcase_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
            )
        }

        if (library.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Rounded.Collections,
                    title = stringResource(R.string.showcase),
                    description = stringResource(R.string.showcase_none)
                )
            }
        } else {
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Button(
                        onClick = ::create,
                        enabled = !building,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().tvFocus(RoundedCornerShape(14.dp))
                    ) {
                        if (building) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.showcase_building))
                        } else {
                            Icon(Icons.Rounded.Collections, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.showcase_create))
                        }
                    }
                    if (failed) {
                        Text(
                            stringResource(R.string.showcase_failed),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    output?.let { file ->
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            AsyncImage(
                                model = file,
                                contentDescription = stringResource(R.string.showcase),
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            )
                        }
                        Button(
                            onClick = { share(file) },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                                .tvFocus(RoundedCornerShape(14.dp))
                        ) {
                            Icon(Icons.Rounded.Share, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.showcase_share))
                        }
                    }
                    Text(
                        stringResource(R.string.showcase_desc),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 14.dp)
                    )
                }
            }
        }
        item {
            Spacer(Modifier.height(20.dp))
        }
    }
}

private const val COLLAGE_WIDTH = 1080
private const val COLLAGE_HEIGHT = 1350

private suspend fun buildCollage(
    context: Context,
    games: List<LibraryGame>,
    covers: Map<String, String?>
): File? = withContext(Dispatchers.IO) {
    runCatching {
        val bitmap = Bitmap.createBitmap(COLLAGE_WIDTH, COLLAGE_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.parseColor("#0D0D12"))

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F2F2F6")
            textSize = 62f
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            letterSpacing = 0.08f
        }
        val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#5B4BDB")
            textSize = 28f
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            letterSpacing = 0.22f
            textAlign = Paint.Align.CENTER
        }

        val margin = 72f
        canvas.drawText(
            context.getString(R.string.showcase_title),
            margin,
            126f,
            titlePaint
        )

        val columns = 4
        val rows = 3
        val gap = 20f
        val cellWidth = (COLLAGE_WIDTH - 2 * margin - gap * (columns - 1)) / columns
        val cellHeight = cellWidth / 0.72f
        val top = 206f
        val loader = context.imageLoader
        val coverPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

        games.take(columns * rows).forEachIndexed { index, game ->
            val column = index % columns
            val row = index / columns
            val left = margin + column * (cellWidth + gap)
            val cellTop = top + row * (cellHeight + gap)
            val rect = RectF(left, cellTop, left + cellWidth, cellTop + cellHeight)
            val path = Path().apply {
                addRoundRect(rect, 22f, 22f, Path.Direction.CW)
            }
            canvas.save()
            canvas.clipPath(path)

            val model = covers["lib:${game.id}"] ?: game.coverUrl
            val cover = loadCover(context, loader, model, cellWidth.toInt(), cellHeight.toInt())
            if (cover != null) {
                canvas.drawBitmap(cover, null, rect, coverPaint)
            } else {
                val console = ConsoleCatalog.get(game.consoleId)
                val accent = consoleColor(console)
                val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb(46, (accent.red * 255).toInt(), (accent.green * 255).toInt(), (accent.blue * 255).toInt())
                }
                canvas.drawRect(rect, fill)
                val monogram = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = accent.copy(alpha = 0.7f).toArgb()
                    textSize = 72f
                    typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
                    textAlign = Paint.Align.CENTER
                }
                canvas.drawText(
                    game.title.take(2).uppercase(Locale.ROOT),
                    rect.centerX(),
                    rect.centerY() + 24f,
                    monogram
                )
            }
            canvas.restore()
        }

        canvas.drawText(
            context.getString(R.string.app_name).uppercase(Locale.ROOT),
            COLLAGE_WIDTH / 2f,
            COLLAGE_HEIGHT - 58f,
            brandPaint
        )

        val directory = File(context.cacheDir, "showcase").apply { mkdirs() }
        val file = File(directory, "showcase.png")
        file.outputStream().use { stream -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream) }
        bitmap.recycle()
        file
    }.getOrNull()
}

private suspend fun loadCover(
    context: Context,
    loader: coil.ImageLoader,
    model: String?,
    width: Int,
    height: Int
): Bitmap? {
    if (model.isNullOrBlank()) return null
    val data: Any = if (model.startsWith("/")) File(model) else model
    val request = ImageRequest.Builder(context)
        .data(data)
        .size(width.coerceAtLeast(1), height.coerceAtLeast(1))
        .allowHardware(false)
        .build()
    val result = runCatching { loader.execute(request) }.getOrNull()
    if (result !is SuccessResult) return null
    return runCatching { result.drawable.toBitmap(width, height) }.getOrNull()
}
