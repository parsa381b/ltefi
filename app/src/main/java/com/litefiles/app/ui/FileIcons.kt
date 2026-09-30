package com.litefiles.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.litefiles.app.data.Kind

fun kindIcon(kind: Kind): ImageVector = when (kind) {
    Kind.FOLDER -> Icons.Filled.Folder
    Kind.IMAGE -> Icons.Filled.Image
    Kind.VIDEO -> Icons.Filled.Movie
    Kind.AUDIO -> Icons.Filled.MusicNote
    Kind.DOC -> Icons.Filled.Description
    Kind.ARCHIVE -> Icons.Filled.FolderZip
    Kind.APK -> Icons.Filled.Android
    Kind.OTHER -> Icons.Filled.InsertDriveFile
}

fun kindColor(kind: Kind): Color = when (kind) {
    Kind.FOLDER -> Color(0xFFF5A623)
    Kind.IMAGE -> Color(0xFF2FA866)
    Kind.VIDEO -> Color(0xFFE5534B)
    Kind.AUDIO -> Color(0xFF8E5BD9)
    Kind.DOC -> Color(0xFF3E91FF)
    Kind.ARCHIVE -> Color(0xFF8D6E63)
    Kind.APK -> Color(0xFF3DDC84)
    Kind.OTHER -> Color(0xFF8A8A8A)
}

fun shortcutIcon(folder: String): ImageVector = when (folder) {
    "Download" -> Icons.Filled.Download
    "DCIM" -> Icons.Filled.PhotoCamera
    "Pictures" -> Icons.Filled.Image
    "Documents" -> Icons.Filled.Description
    "Music" -> Icons.Filled.MusicNote
    "Movies" -> Icons.Filled.Movie
    else -> Icons.Filled.Folder
}
