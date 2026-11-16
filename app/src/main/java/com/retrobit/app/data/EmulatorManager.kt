package com.retrobit.app.data

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

object EmulatorManager {

    enum class LaunchResult { STARTED, NO_TARGET, MISSING_FILE, FAILED }

    private data class KnownEmulator(
        val packageName: String,
        val label: String,
        val consoles: Set<String>
    )

    private val allConsoles: Set<String> = ConsoleCatalog.consoles.map { it.id }.toSet()

    private val known = listOf(
        KnownEmulator("com.retroarch", "RetroArch", allConsoles),
        KnownEmulator("com.retroarch.aarch64", "RetroArch (AArch64)", allConsoles),
        KnownEmulator("org.ppsspp.ppsspp", "PPSSPP", setOf("psp")),
        KnownEmulator("org.ppsspp.ppssppgold", "PPSSPP Gold", setOf("psp")),
        KnownEmulator("com.dsemu.drastic", "DraStic", setOf("nds")),
        KnownEmulator("me.magnum.melonds", "melonDS", setOf("nds")),
        KnownEmulator("org.mupen64plusae.v3.fz", "M64Plus FZ", setOf("n64")),
        KnownEmulator("com.github.stenzek.duckstation", "DuckStation", setOf("ps1")),
        KnownEmulator("com.explusalpha.Snes9x", "Snes9x EX+", setOf("snes")),
        KnownEmulator("com.explusalpha.GbaEmu", "GBA.emu", setOf("gba")),
        KnownEmulator("com.explusalpha.NesEmu", "NES.emu", setOf("nes")),
        KnownEmulator("com.explusalpha.MdEmu", "MD.emu", setOf("genesis")),
        KnownEmulator("com.fastemulator.gba", "My Boy!", setOf("gba")),
        KnownEmulator("com.fastemulator.gbc", "My OldBoy!", setOf("gb", "gbc")),
        KnownEmulator("com.johnemulators.johngbac", "John GBA Lite", setOf("gba")),
        KnownEmulator("com.johnemulators.johnnes", "John NES Lite", setOf("nes")),
        KnownEmulator("com.johnemulators.johnsnes", "John SNES Lite", setOf("snes")),
        KnownEmulator("com.aether.emulator", "AetherSX2", setOf("psp")),
        KnownEmulator("com.pcsx2.pcsx2", "PCSX2", setOf("ps1")),
        KnownEmulator("com.zzn.inputbridge", "Input Bridge", allConsoles),
        KnownEmulator("com.magicdroidx.tekkenring", "Retro Emulator", allConsoles)
    )

    fun installedEmulators(context: Context): List<EmulatorInfo> {
        val pm = context.packageManager
        return known.mapNotNull { item ->
            runCatching { pm.getPackageInfo(item.packageName, 0) }.getOrNull()?.let {
                val label = runCatching {
                    pm.getApplicationLabel(pm.getApplicationInfo(item.packageName, 0)).toString()
                }.getOrDefault(item.label)
                EmulatorInfo(item.packageName, label)
            }
        }
    }

    fun supportsConsole(packageName: String, consoleId: String): Boolean =
        known.firstOrNull { it.packageName == packageName }?.consoles?.contains(consoleId) ?: true

    fun launchFile(
        context: Context,
        file: File,
        consoleId: String,
        preferredPackage: String? = null
    ): LaunchResult {
        if (!file.exists() || !file.canRead()) return LaunchResult.MISSING_FILE
        val uri = runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        }.getOrNull() ?: Uri.fromFile(file)
        return launch(context, uri, mimeFor(file.extension), preferredPackage, file.absolutePath)
    }

    fun openUri(
        context: Context,
        uri: Uri,
        mime: String,
        preferredPackage: String? = null,
        filePath: String? = null
    ): Boolean = launch(context, uri, mime, preferredPackage, filePath) == LaunchResult.STARTED

    private fun launch(
        context: Context,
        uri: Uri,
        mime: String,
        preferredPackage: String?,
        filePath: String?
    ): LaunchResult {
        val pm = context.packageManager
        val target = preferredPackage?.takeIf { isInstalled(context, it) }

        fun buildIntent(type: String): Intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, type)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (target != null) setPackage(target)
            if (target != null && (target == "com.retroarch" || target == "com.retroarch.aarch64")) {
                filePath?.let { putExtra("ROM", it) }
                uri.toString().let { putExtra("rom", it) }
            }
        }

        val candidates = listOf(mime, "*/*").distinct()
        for (type in candidates) {
            if (type == "*/*" && target == null && installedEmulators(context).isEmpty()) continue
            val intent = buildIntent(type)
            val matches = if (target != null) {
                if (pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null) 1 else 0
            } else {
                pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
                    .count { it.activityInfo.packageName != context.packageName }
            }
            if (matches == 0) continue
            return runCatching {
                if (target != null) {
                    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } else {
                    val chooser = Intent.createChooser(intent, null)
                        .putExtra(
                            Intent.EXTRA_EXCLUDE_COMPONENTS,
                            arrayOf(ComponentName(context.packageName, "${context.packageName}.MainActivity"))
                        )
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    context.startActivity(chooser)
                }
                LaunchResult.STARTED
            }.getOrElse { error ->
                if (error is ActivityNotFoundException) LaunchResult.NO_TARGET else LaunchResult.FAILED
            }
        }
        return LaunchResult.NO_TARGET
    }

    private fun isInstalled(context: Context, packageName: String): Boolean =
        runCatching { context.packageManager.getPackageInfo(packageName, 0) }.isSuccess

    fun mimeFor(extension: String): String = when (extension.lowercase()) {
        "nes", "unf", "unif", "fds" -> "application/x-nes-rom"
        "sfc", "smc", "fig", "swc" -> "application/x-snes-rom"
        "n64", "z64", "v64" -> "application/x-n64-rom"
        "gb" -> "application/x-gameboy-rom"
        "gbc" -> "application/x-gameboy-color-rom"
        "gba" -> "application/x-gba-rom"
        "nds" -> "application/x-nintendo-ds-rom"
        "md", "gen", "smd", "32x" -> "application/x-genesis-rom"
        "iso", "cue", "chd", "pbp", "cso" -> "application/x-psx-rom"
        "pce", "sgx" -> "application/x-pc-engine-rom"
        "ws", "wsc" -> "application/x-wonderswan-rom"
        "zip" -> "application/zip"
        "exe", "com" -> "application/octet-stream"
        else -> "*/*"
    }
}
