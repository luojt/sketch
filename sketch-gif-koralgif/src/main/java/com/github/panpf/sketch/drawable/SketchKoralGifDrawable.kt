package com.github.panpf.sketch.drawable

/*
 * Copyright (C) 2019 panpf <panpfpanpf@outlook.com>
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

import android.annotation.SuppressLint
import androidx.appcompat.graphics.drawable.DrawableWrapper
import androidx.vectordrawable.graphics.drawable.Animatable2Compat
import com.github.panpf.sketch.decode.ImageInfo
import com.github.panpf.sketch.decode.Transformed
import com.github.panpf.sketch.decode.internal.exifOrientationName
import com.github.panpf.sketch.request.DataFrom
import com.github.panpf.sketch.util.BitmapInfo
import com.github.panpf.sketch.util.byteCountCompat
import pl.droidsonroids.gif.GifDrawable
import pl.droidsonroids.gif.bitmapCompat

@SuppressLint("RestrictedApi")
class SketchKoralGifDrawable constructor(
    override val requestKey: String,
    override val requestUri: String,
    override val imageInfo: ImageInfo,
    override val imageExifOrientation: Int,
    override val imageDataFrom: DataFrom,
    private val gifDrawable: GifDrawable,
) : DrawableWrapper(gifDrawable), SketchAnimatableDrawable {

    private val callbacks = mutableListOf<Animatable2Compat.AnimationCallback>()

    override val bitmapInfo: BitmapInfo by lazy {
        gifDrawable.bitmapCompat.let {
            BitmapInfo(it.width, it.height, it.byteCountCompat, it.config)
        }
    }

    override val transformedList: List<Transformed>? = null

    override fun start() {
        val isRunning = isRunning
        if (!isRunning) {
            callbacks.forEach { it.onAnimationStart(this) }
        }
    }

    override fun stop() {
        val isRunning = isRunning
        if (isRunning) {
            callbacks.forEach { it.onAnimationEnd(this) }
        }
    }

    override fun registerAnimationCallback(callback: Animatable2Compat.AnimationCallback) {
        callbacks.add(callback)
    }

    override fun unregisterAnimationCallback(callback: Animatable2Compat.AnimationCallback): Boolean {
        return callbacks.remove(callback)
    }

    override fun clearAnimationCallbacks() = callbacks.clear()

    override fun isRunning(): Boolean = gifDrawable.isRunning

    override fun toString(): String =
        "SketchKoralGifDrawable(${imageInfo.toShortString()},${exifOrientationName(imageExifOrientation)},$imageDataFrom,${bitmapInfo.toShortString()},${transformedList},$requestKey)"
}