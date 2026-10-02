package com.litefiles.app.data

import com.litefiles.app.R

/** Home-screen categories. Backed by the MediaStore index, not by walking the disk. */
enum class Category(val labelRes: Int, val kind: Kind) {
    IMAGES(R.string.cat_images, Kind.IMAGE),
    VIDEOS(R.string.cat_videos, Kind.VIDEO),
    AUDIO(R.string.cat_audio, Kind.AUDIO),
    DOCUMENTS(R.string.cat_documents, Kind.DOC),
    APKS(R.string.cat_apks, Kind.APK),
}
