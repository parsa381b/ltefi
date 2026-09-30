package com.litefiles.app.data

/** Home-screen categories. Backed by the MediaStore index, not by walking the disk. */
enum class Category(val label: String, val kind: Kind) {
    IMAGES("Images", Kind.IMAGE),
    VIDEOS("Videos", Kind.VIDEO),
    AUDIO("Audio", Kind.AUDIO),
    DOCUMENTS("Documents", Kind.DOC),
    APKS("APKs", Kind.APK),
}
