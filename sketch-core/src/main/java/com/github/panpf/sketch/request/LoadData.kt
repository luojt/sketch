package com.github.panpf.sketch.request

import android.graphics.Bitmap
import com.github.panpf.sketch.decode.ImageInfo
import com.github.panpf.sketch.request.internal.ImageData

data class LoadData constructor(
    val bitmap: Bitmap,
    val info: ImageInfo,
    val from: DataFrom
): ImageData