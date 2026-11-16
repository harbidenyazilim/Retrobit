package com.retrobit.app.data

import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

object AtomicFiles {
    @Synchronized
    fun write(target: File, writer: (OutputStream) -> Unit) {
        val parent = requireNotNull(target.absoluteFile.parentFile)
        check(parent.isDirectory || parent.mkdirs()) { "Cannot create output directory" }
        val temporary = File.createTempFile(".write-", ".tmp", parent)
        try {
            FileOutputStream(temporary).use { output ->
                writer(output)
                output.flush()
                output.fd.sync()
            }
            Files.move(
                temporary.toPath(), target.toPath(),
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING
            )
        } finally {
            temporary.delete()
        }
    }
}
