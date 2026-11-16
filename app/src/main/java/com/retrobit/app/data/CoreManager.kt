package com.retrobit.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

data class LibretroCore(
    val consoleId: String,
    val coreName: String,
    val displayName: String,
    val author: String,
    /** ROM extensions routed to this core when a console card spans several cores. */
    val extensions: Set<String> = emptySet(),
    /** Core options exactly as the core declares them (key to value). */
    val variables: Map<String, String> = emptyMap()
)

/**
 * Cores ship inside the app package as native libraries (jniLibs) and are extracted by the
 * installer into [android.content.pm.ApplicationInfo.nativeLibraryDir]; nothing is downloaded.
 */
class CoreManager(context: Context) {

    private val assets = context.assets
    private val nativeDirectory = File(context.applicationInfo.nativeLibraryDir)
    // Libraries fetched by builds before 1.18.0. Never loaded again; only measured and removable.
    private val legacyDownloads = File(context.filesDir, "cores")
    val systemDirectory: File = File(context.filesDir, "system").apply { mkdirs() }
    val savesDirectory: File = File(context.filesDir, "saves").apply { mkdirs() }

    fun coreFor(consoleId: String?, romName: String? = null): LibretroCore? =
        Companion.coreFor(consoleId, romName)

    fun coreFile(core: LibretroCore): File =
        File(nativeDirectory, "lib${core.coreName}_libretro_android.so")

    fun isAvailable(core: LibretroCore): Boolean = coreFile(core).isFile

    /** A console is ready only when every core behind its card is present in the package. */
    fun installedConsoleIds(): Set<String> =
        CORES.groupBy { it.consoleId }.filterValues { cores -> cores.all(::isAvailable) }.keys

    fun bundledSize(): Long =
        CORES.map(::coreFile).distinct().filter { it.isFile }.sumOf { it.length() }

    fun legacyDownloadsSize(): Long =
        legacyDownloads.listFiles()?.filter { it.isFile }?.sumOf { it.length() } ?: 0L

    suspend fun deleteLegacyDownloads() = withContext(Dispatchers.IO) {
        legacyDownloads.listFiles()?.forEach { runCatching { it.delete() } }
    }

    suspend fun prepareSystemFiles(core: LibretroCore) = withContext(Dispatchers.IO) {
        if (core.coreName != "ppsspp") return@withContext
        val destination = File(systemDirectory, "PPSSPP")
        val marker = File(destination, ".retrobit-$PPSSPP_ASSET_REVISION")
        if (marker.isFile) return@withContext
        copyAssetTree("ppsspp", destination)
        marker.writeText(PPSSPP_ASSET_REVISION)
    }

    private fun copyAssetTree(assetPath: String, destination: File) {
        val children = assets.list(assetPath).orEmpty()
        if (children.isNotEmpty()) {
            destination.mkdirs()
            children.forEach { child ->
                copyAssetTree("$assetPath/$child", File(destination, child))
            }
            return
        }
        if (destination.exists()) return
        destination.parentFile?.mkdirs()
        val staged = File(destination.parentFile, ".${destination.name}.tmp")
        assets.open(assetPath).use { input ->
            staged.outputStream().use { output -> input.copyTo(output) }
        }
        check(staged.renameTo(destination))
    }

    companion object {
        private const val PPSSPP_ASSET_REVISION = "65181eea1deb7361beb3be6062ea98e8427f2786"
        val CORES: List<LibretroCore> = listOf(
            LibretroCore("nes", "fceumm", "FCEUmm", "Libretro"),
            LibretroCore(
                "snes", "bsnes_mercury_performance", "bsnes-mercury Performance", "byuu, bsnes-mercury Team",
                // DSP/Cx4/ST010 games run without copyrighted coprocessor firmware.
                variables = mapOf("bsnes_violate_accuracy" to "enabled", "bsnes_chip_hle" to "HLE")
            ),
            LibretroCore("n64", "mupen64plus_next_gles3", "Mupen64Plus-Next", "Mupen64Plus"),
            LibretroCore("gb", "gambatte", "Gambatte", "Gambatte Team"),
            LibretroCore("gbc", "gambatte", "Gambatte", "Gambatte Team"),
            LibretroCore("gba", "mgba", "mGBA", "mGBA Team"),
            LibretroCore("nds", "melonds", "melonDS", "melonDS Team"),
            LibretroCore("genesis", "picodrive", "PicoDrive", "PicoDrive Team"),
            LibretroCore("genesis", "gearsystem", "Gearsystem", "Ignacio Sanchez", extensions = setOf("sms", "gg")),
            LibretroCore("ps1", "pcsx_rearmed", "PCSX-ReARMed", "PCSX-ReARMed Team"),
            LibretroCore("psp", "ppsspp", "PPSSPP", "PPSSPP Team"),
            LibretroCore("atari2600", "stella", "Stella", "Stella Team")
        )

        /** An extension-specific core wins; otherwise the console's general core runs the ROM. */
        fun coreFor(consoleId: String?, romName: String? = null): LibretroCore? {
            val candidates = CORES.filter { it.consoleId == consoleId }
            val extension = romName?.substringAfterLast('.', "")?.lowercase(Locale.ROOT).orEmpty()
            return candidates.firstOrNull { extension.isNotEmpty() && extension in it.extensions }
                ?: candidates.firstOrNull { it.extensions.isEmpty() }
        }
    }
}
