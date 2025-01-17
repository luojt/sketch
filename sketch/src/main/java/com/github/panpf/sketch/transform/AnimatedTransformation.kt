/*
 * Copyright (C) 2022 panpf <panpfpanpf@outlook.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.panpf.sketch.transform

import android.graphics.Canvas
import android.graphics.PixelFormat
import android.graphics.PostProcessor
import androidx.annotation.RequiresApi

/**
 * An interface for making transformations to an animated image's pixel data.
 */
fun interface AnimatedTransformation {

    /**
     * Apply the transformation to the [canvas].
     *
     * @param canvas The [Canvas] to draw on.
     * @return The opacity of the image after drawing.
     */
    fun transform(canvas: Canvas): PixelOpacity
}

@RequiresApi(28)
internal fun AnimatedTransformation.asPostProcessor() =
    PostProcessor { canvas -> transform(canvas).flag }

/**
 * Represents the opacity of an image's pixels after applying an [AnimatedTransformation].
 */
enum class PixelOpacity {

    /**
     * Indicates that the [AnimatedTransformation] did not change the image's opacity.
     *
     * Return this unless you add transparent pixels to the image or remove all transparent
     * pixels in the image.
     */
    UNCHANGED,

    /**
     * Indicates that the [AnimatedTransformation] added transparent pixels to the image.
     */
    TRANSLUCENT,

    /**
     * Indicates that the [AnimatedTransformation] removed all transparent pixels in the image.
     */
    OPAQUE
}

internal val PixelOpacity.flag: Int
    get() = when (this) {
        PixelOpacity.UNCHANGED -> PixelFormat.UNKNOWN
        PixelOpacity.TRANSLUCENT -> PixelFormat.TRANSLUCENT
        PixelOpacity.OPAQUE -> PixelFormat.OPAQUE
    }
