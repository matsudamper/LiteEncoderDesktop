package net.matsudamper.liteencoder.ui

import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.FileKitDialogParent
import io.github.vinceglb.filekit.dialogs.FileKitDialogSettings
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.openFilePicker
import io.github.vinceglb.filekit.dialogs.openFileSaver
import java.awt.Window
import java.io.File

interface FileDialogs {
    suspend fun pickVideo(): File?
    suspend fun pickExportDestination(suggestedName: String, extension: String, directory: File?): File?
}

class FileKitDialogs(window: Window) : FileDialogs {
    private val dialogSettings = FileKitDialogSettings(parent = FileKitDialogParent.awt(window))

    override suspend fun pickVideo(): File? {
        return FileKit.openFilePicker(type = FileKitType.Video, dialogSettings = dialogSettings)?.file
    }

    override suspend fun pickExportDestination(suggestedName: String, extension: String, directory: File?): File? {
        return FileKit.openFileSaver(
            suggestedName = suggestedName,
            extension = extension,
            directory = if (directory != null) PlatformFile(directory) else null,
            dialogSettings = dialogSettings,
        )?.file
    }
}
