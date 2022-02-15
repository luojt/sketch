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
package com.github.panpf.sketch.zoom

import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.annotation.DrawableRes
import androidx.appcompat.widget.AppCompatImageView
import com.github.panpf.sketch.Sketch
import com.github.panpf.sketch.sketch
import com.github.panpf.sketch.zoom.internal.ImageZoomer

// todo extends AbsAbilityImageView
// todo The redesign
open class SketchZoomImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : AppCompatImageView(context, attrs, defStyle) {

    private val zoomer: ImageZoomer by lazy {
        ImageZoomer(this)
    }

    val sketch: Sketch
        get() = context.sketch

    override fun setScaleType(scaleType: ScaleType) {
        if (zoomer.isWorking && scaleType != ScaleType.MATRIX) {
            zoomer.scaleType = scaleType
        } else {
            super.setScaleType(scaleType)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        zoomer.reset("onAttachedToWindow")
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        zoomer.onDraw(canvas)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return zoomer.onTouchEvent(event)
    }

    override fun setImageURI(uri: Uri?) {
        val oldDrawable = drawable
        super.setImageURI(uri)
        val newDrawable = drawable
        onDrawableChanged("setImageURI", oldDrawable, newDrawable)
    }

    override fun setImageResource(@DrawableRes resId: Int) {
        val oldDrawable = drawable
        super.setImageResource(resId)
        val newDrawable = drawable
        onDrawableChanged("setImageResource", oldDrawable, newDrawable)
    }

    override fun setImageDrawable(drawable: Drawable?) {
        val oldDrawable = getDrawable()
        super.setImageDrawable(drawable)
        val newDrawable = getDrawable()
        onDrawableChanged("setImageDrawable", oldDrawable, newDrawable)
    }

    private fun onDrawableChanged(
        callPosition: String, oldDrawable: Drawable?, newDrawable: Drawable?
    ): Boolean {
        zoomer.reset("onDrawableChanged")
        return false
    }

    override fun onSizeChanged(left: Int, top: Int, right: Int, bottom: Int) {
        super.onSizeChanged(left, top, right, bottom)
        // todo 当被频繁修改尺寸时，这里会频繁触发，最后会触发 InitHandler 的 init，最后会频繁创建 BitmapRegionDecoder 消耗大量内存
        // todo 当尺寸修改时，没必要再次初始化 BitmapRegionDecoder
        zoomer.reset("onSizeChanged")
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        zoomer.recycle("onDetachedFromWindow")
    }

    /**
     * 根据高度计算是否可以使用阅读模式
     */
    open fun canUseReadModeByHeight(imageWidth: Int, imageHeight: Int): Boolean {
        return imageHeight > imageWidth * 2
    }

    /**
     * 根据宽度度计算是否可以使用阅读模式
     */
    open fun canUseReadModeByWidth(imageWidth: Int, imageHeight: Int): Boolean {
        return imageWidth > imageHeight * 3
    }
}