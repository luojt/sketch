@file:Suppress("DEPRECATION")

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
package com.github.panpf.sketch.test.request.internal

import android.graphics.Bitmap
import android.graphics.Bitmap.Config.ALPHA_8
import android.graphics.Bitmap.Config.ARGB_4444
import android.graphics.Bitmap.Config.ARGB_8888
import android.graphics.Bitmap.Config.HARDWARE
import android.graphics.Bitmap.Config.RGBA_F16
import android.graphics.Bitmap.Config.RGB_565
import android.graphics.Color
import android.graphics.ColorSpace
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.StateListDrawable
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Lifecycle.State.CREATED
import androidx.lifecycle.Lifecycle.State.STARTED
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.panpf.sketch.cache.CachePolicy.DISABLED
import com.github.panpf.sketch.cache.CachePolicy.ENABLED
import com.github.panpf.sketch.cache.CachePolicy.READ_ONLY
import com.github.panpf.sketch.cache.CachePolicy.WRITE_ONLY
import com.github.panpf.sketch.datasource.DataFrom
import com.github.panpf.sketch.decode.BitmapConfig
import com.github.panpf.sketch.decode.GifAnimatedDrawableDecoder
import com.github.panpf.sketch.decode.internal.exifOrientationName
import com.github.panpf.sketch.decode.internal.resultCacheDataKey
import com.github.panpf.sketch.drawable.SketchAnimatableDrawable
import com.github.panpf.sketch.drawable.SketchDrawable
import com.github.panpf.sketch.drawable.internal.CrossfadeDrawable
import com.github.panpf.sketch.drawable.internal.ResizeDrawable
import com.github.panpf.sketch.fetch.newAssetUri
import com.github.panpf.sketch.request.Depth.LOCAL
import com.github.panpf.sketch.request.Depth.MEMORY
import com.github.panpf.sketch.request.Depth.NETWORK
import com.github.panpf.sketch.request.DepthException
import com.github.panpf.sketch.request.DisplayRequest
import com.github.panpf.sketch.request.DisplayResult
import com.github.panpf.sketch.request.GlobalLifecycle
import com.github.panpf.sketch.request.get
import com.github.panpf.sketch.request.internal.memoryCacheKey
import com.github.panpf.sketch.resize.Precision.EXACTLY
import com.github.panpf.sketch.resize.Precision.LESS_PIXELS
import com.github.panpf.sketch.resize.Precision.SAME_ASPECT_RATIO
import com.github.panpf.sketch.resize.Scale.CENTER_CROP
import com.github.panpf.sketch.resize.Scale.END_CROP
import com.github.panpf.sketch.resize.Scale.FILL
import com.github.panpf.sketch.resize.Scale.START_CROP
import com.github.panpf.sketch.stateimage.internal.SketchStateNormalDrawable
import com.github.panpf.sketch.target.DisplayTarget
import com.github.panpf.sketch.test.utils.DisplayListenerSupervisor
import com.github.panpf.sketch.test.utils.DisplayProgressListenerSupervisor
import com.github.panpf.sketch.test.utils.ExifOrientationTestFileHelper
import com.github.panpf.sketch.test.utils.TestAssetFetcherFactory
import com.github.panpf.sketch.test.utils.TestAssets
import com.github.panpf.sketch.test.utils.TestBitmapDecodeInterceptor
import com.github.panpf.sketch.test.utils.TestDisplayCountDisplayTarget
import com.github.panpf.sketch.test.utils.TestDisplayTarget
import com.github.panpf.sketch.test.utils.TestDrawableDecodeInterceptor
import com.github.panpf.sketch.test.utils.TestErrorBitmapDecoder
import com.github.panpf.sketch.test.utils.TestErrorDrawableDecoder
import com.github.panpf.sketch.test.utils.TestHttpStack
import com.github.panpf.sketch.test.utils.TestRequestInterceptor
import com.github.panpf.sketch.test.utils.TestTransitionDisplayTarget
import com.github.panpf.sketch.test.utils.corners
import com.github.panpf.sketch.test.utils.getTestContext
import com.github.panpf.sketch.test.utils.getTestContextAndNewSketch
import com.github.panpf.sketch.test.utils.intrinsicSize
import com.github.panpf.sketch.test.utils.newSketch
import com.github.panpf.sketch.test.utils.ratio
import com.github.panpf.sketch.test.utils.samplingByTarget
import com.github.panpf.sketch.test.utils.size
import com.github.panpf.sketch.test.utils.toRequestContext
import com.github.panpf.sketch.transform.CircleCropTransformation
import com.github.panpf.sketch.transform.RotateTransformation
import com.github.panpf.sketch.transform.RoundedCornersTransformation
import com.github.panpf.sketch.transform.getCircleCropTransformed
import com.github.panpf.sketch.transform.getRotateTransformed
import com.github.panpf.sketch.transform.getRoundedCornersTransformed
import com.github.panpf.sketch.transition.CrossfadeTransition
import com.github.panpf.sketch.util.Size
import com.github.panpf.sketch.util.asOrNull
import com.github.panpf.sketch.util.asOrThrow
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DisplayRequestExecuteTest {

    @Test
    fun testDepth() {
        val context = getTestContext()
        val sketch = newSketch {
            httpStack(TestHttpStack(context))
        }
        val imageUri = TestHttpStack.testImages.first().uriString

        // default
        sketch.downloadCache.clear()
        sketch.memoryCache.clear()
        DisplayRequest(context, imageUri) {
            resultCachePolicy(DISABLED)
            target(TestDisplayCountDisplayTarget())
        }.let {
            runBlocking { sketch.execute(it) }
        }.asOrNull<DisplayResult.Success>()!!.apply {
            Assert.assertEquals(DataFrom.NETWORK, dataFrom)
        }

        // NETWORK
        sketch.downloadCache.clear()
        sketch.memoryCache.clear()
        DisplayRequest(context, imageUri) {
            resultCachePolicy(DISABLED)
            depth(NETWORK)
            target(TestDisplayCountDisplayTarget())
        }.let {
            runBlocking { sketch.execute(it) }
        }.asOrNull<DisplayResult.Success>()!!.apply {
            Assert.assertEquals(DataFrom.NETWORK, dataFrom)
        }

        // LOCAL
        sketch.downloadCache.clear()
        sketch.memoryCache.clear()
        runBlocking {
            sketch.execute(DisplayRequest(context, imageUri) {
                resultCachePolicy(DISABLED)
                target(TestDisplayCountDisplayTarget())
            })
        }
        sketch.memoryCache.clear()
        Assert.assertTrue(sketch.downloadCache.exist(imageUri))
        DisplayRequest(context, imageUri) {
            resultCachePolicy(DISABLED)
            depth(LOCAL)
            target(TestDisplayCountDisplayTarget())
        }.let {
            runBlocking { sketch.execute(it) }
        }.asOrNull<DisplayResult.Success>()!!.apply {
            Assert.assertEquals(DataFrom.DOWNLOAD_CACHE, dataFrom)
        }

        sketch.downloadCache.clear()
        sketch.memoryCache.clear()
        DisplayRequest(context, imageUri) {
            resultCachePolicy(DISABLED)
            depth(LOCAL)
            target(TestDisplayCountDisplayTarget())
        }.let {
            runBlocking { sketch.execute(it) }
        }.asOrNull<DisplayResult.Error>()!!.apply {
            Assert.assertTrue(throwable is DepthException)
        }

        // MEMORY
        sketch.memoryCache.clear()
        runBlocking {
            sketch.execute(DisplayRequest(context, imageUri) {
                resultCachePolicy(DISABLED)
                target(TestDisplayCountDisplayTarget())
            })
        }
        DisplayRequest(context, imageUri) {
            resultCachePolicy(DISABLED)
            depth(MEMORY)
            target(TestDisplayCountDisplayTarget())
        }.let {
            runBlocking { sketch.execute(it) }
        }.asOrNull<DisplayResult.Success>()!!.apply {
            Assert.assertEquals(DataFrom.MEMORY_CACHE, dataFrom)
        }

        sketch.memoryCache.clear()
        DisplayRequest(context, imageUri) {
            resultCachePolicy(DISABLED)
            depth(MEMORY)
            target(TestDisplayCountDisplayTarget())
        }.let {
            runBlocking { sketch.execute(it) }
        }.asOrNull<DisplayResult.Error>()!!.apply {
            Assert.assertTrue(throwable is DepthException)
        }
    }

    @Test
    fun testDownloadCachePolicy() {
        val context = getTestContext()
        val sketch = newSketch {
            httpStack(TestHttpStack(context))
        }
        val diskCache = sketch.downloadCache
        val imageUri = TestHttpStack.testImages.first().uriString

        /* ENABLED */
        diskCache.clear()
        Assert.assertFalse(diskCache.exist(imageUri))
        DisplayRequest(context, imageUri) {
            memoryCachePolicy(DISABLED)
            resultCachePolicy(DISABLED)
            downloadCachePolicy(ENABLED)
        }.let {
            runBlocking { sketch.execute(it) }
        }.asOrNull<DisplayResult.Success>()!!.apply {
            Assert.assertEquals(DataFrom.NETWORK, dataFrom)
        }

        Assert.assertTrue(diskCache.exist(imageUri))
        DisplayRequest(context, imageUri) {
            memoryCachePolicy(DISABLED)
            resultCachePolicy(DISABLED)
            downloadCachePolicy(ENABLED)
        }.let {
            runBlocking { sketch.execute(it) }
        }.asOrNull<DisplayResult.Success>()!!.apply {
            Assert.assertEquals(DataFrom.DOWNLOAD_CACHE, dataFrom)
        }

        /* DISABLED */
        diskCache.clear()
        Assert.assertFalse(diskCache.exist(imageUri))
        DisplayRequest(context, imageUri) {
            memoryCachePolicy(DISABLED)
            resultCachePolicy(DISABLED)
            downloadCachePolicy(DISABLED)
        }.let {
            runBlocking { sketch.execute(it) }
        }.asOrNull<DisplayResult.Success>()!!.apply {
            Assert.assertEquals(DataFrom.NETWORK, dataFrom)
        }

        Assert.assertFalse(diskCache.exist(imageUri))
        DisplayRequest(context, imageUri) {
            memoryCachePolicy(DISABLED)
            resultCachePolicy(DISABLED)
            downloadCachePolicy(DISABLED)
        }.let {
            runBlocking { sketch.execute(it) }
        }.asOrNull<DisplayResult.Success>()!!.apply {
            Assert.assertEquals(DataFrom.NETWORK, dataFrom)
        }

        /* READ_ONLY */
        diskCache.clear()
        Assert.assertFalse(diskCache.exist(imageUri))
        DisplayRequest(context, imageUri) {
            memoryCachePolicy(DISABLED)
            resultCachePolicy(DISABLED)
            downloadCachePolicy(READ_ONLY)
        }.let {
            runBlocking { sketch.execute(it) }
        }.asOrNull<DisplayResult.Success>()!!.apply {
            Assert.assertEquals(DataFrom.NETWORK, dataFrom)
        }

        Assert.assertFalse(diskCache.exist(imageUri))
        DisplayRequest(context, imageUri) {
            memoryCachePolicy(DISABLED)
            resultCachePolicy(DISABLED)
            downloadCachePolicy(READ_ONLY)
        }.let {
            runBlocking { sketch.execute(it) }
        }.asOrNull<DisplayResult.Success>()!!.apply {
            Assert.assertEquals(DataFrom.NETWORK, dataFrom)
        }

        Assert.assertFalse(diskCache.exist(imageUri))
        DisplayRequest(context, imageUri) {
            memoryCachePolicy(DISABLED)
            resultCachePolicy(DISABLED)
            downloadCachePolicy(ENABLED)
        }.let {
            runBlocking { sketch.execute(it) }
        }
        Assert.assertTrue(diskCache.exist(imageUri))
        DisplayRequest(context, imageUri) {
            memoryCachePolicy(DISABLED)
            resultCachePolicy(DISABLED)
            downloadCachePolicy(READ_ONLY)
        }.let {
            runBlocking { sketch.execute(it) }
        }.asOrNull<DisplayResult.Success>()!!.apply {
            Assert.assertEquals(DataFrom.DOWNLOAD_CACHE, dataFrom)
        }

        /* WRITE_ONLY */
        diskCache.clear()
        Assert.assertFalse(diskCache.exist(imageUri))
        DisplayRequest(context, imageUri) {
            memoryCachePolicy(DISABLED)
            resultCachePolicy(DISABLED)
            downloadCachePolicy(WRITE_ONLY)
        }.let {
            runBlocking { sketch.execute(it) }
        }.asOrNull<DisplayResult.Success>()!!.apply {
            Assert.assertEquals(DataFrom.NETWORK, dataFrom)
        }

        Assert.assertTrue(diskCache.exist(imageUri))
        DisplayRequest(context, imageUri) {
            memoryCachePolicy(DISABLED)
            resultCachePolicy(DISABLED)
            downloadCachePolicy(WRITE_ONLY)
        }.let {
            runBlocking { sketch.execute(it) }
        }.asOrNull<DisplayResult.Success>()!!.apply {
            Assert.assertEquals(DataFrom.NETWORK, dataFrom)
        }
    }

    @Test
    fun testBitmapConfig() {
        val context = getTestContext()
        val sketch = newSketch()

        DisplayRequest(context, TestAssets.SAMPLE_JPEG_URI) {
            resultCachePolicy(DISABLED)
            memoryCachePolicy(DISABLED)
        }.let { runBlocking { sketch.execute(it) } }
            .asOrNull<DisplayResult.Success>()!!
            .drawable.asOrNull<BitmapDrawable>()!!
            .apply {
                Assert.assertEquals(ARGB_8888, bitmap.config)
            }

        DisplayRequest(context, TestAssets.SAMPLE_JPEG_URI) {
            resultCachePolicy(DISABLED)
            memoryCachePolicy(DISABLED)
            bitmapConfig(ARGB_8888)
        }.let { runBlocking { sketch.execute(it) } }
            .asOrNull<DisplayResult.Success>()!!
            .drawable.asOrNull<BitmapDrawable>()!!
            .apply {
                Assert.assertEquals(ARGB_8888, bitmap.config)
            }

        DisplayRequest(context, TestAssets.SAMPLE_JPEG_URI) {
            resultCachePolicy(DISABLED)
            memoryCachePolicy(DISABLED)
            @Suppress("DEPRECATION")
            bitmapConfig(ARGB_4444)
        }.let { runBlocking { sketch.execute(it) } }
            .asOrNull<DisplayResult.Success>()!!
            .drawable.asOrNull<BitmapDrawable>()!!
            .apply {
                if (VERSION.SDK_INT > VERSION_CODES.M) {
                    Assert.assertEquals(ARGB_8888, bitmap.config)
                } else {
                    @Suppress("DEPRECATION")
                    Assert.assertEquals(ARGB_4444, bitmap.config)
                }
            }

        DisplayRequest(context, TestAssets.SAMPLE_JPEG_URI) {
            resultCachePolicy(DISABLED)
            memoryCachePolicy(DISABLED)
            bitmapConfig(ALPHA_8)
        }.let { runBlocking { sketch.execute(it) } }
            .asOrNull<DisplayResult.Success>()!!
            .drawable.asOrNull<BitmapDrawable>()!!
            .apply {
                Assert.assertEquals(ARGB_8888, bitmap.config)
            }

        DisplayRequest(context, TestAssets.SAMPLE_JPEG_URI) {
            resultCachePolicy(DISABLED)
            memoryCachePolicy(DISABLED)
            bitmapConfig(RGB_565)
        }.let { runBlocking { sketch.execute(it) } }
            .asOrNull<DisplayResult.Success>()!!
            .drawable.asOrNull<BitmapDrawable>()!!
            .apply {
                Assert.assertEquals(RGB_565, bitmap.config)
            }

        if (VERSION.SDK_INT >= VERSION_CODES.O) {
            DisplayRequest(context, TestAssets.SAMPLE_JPEG_URI) {
                resultCachePolicy(DISABLED)
                memoryCachePolicy(DISABLED)
                bitmapConfig(RGBA_F16)
            }.let { runBlocking { sketch.execute(it) } }
                .asOrNull<DisplayResult.Success>()!!
                .drawable.asOrNull<BitmapDrawable>()!!
                .apply {
                    Assert.assertEquals(RGBA_F16, bitmap.config)
                }
        }

        if (VERSION.SDK_INT >= VERSION_CODES.O) {
            DisplayRequest(context, TestAssets.SAMPLE_JPEG_URI) {
                resultCachePolicy(DISABLED)
                memoryCachePolicy(DISABLED)
                bitmapConfig(HARDWARE)
            }.let { runBlocking { sketch.execute(it) } }
                .asOrNull<DisplayResult.Success>()!!
                .drawable.asOrNull<BitmapDrawable>()!!
                .apply {
                    Assert.assertEquals(HARDWARE, bitmap.config)
                }
        }

        DisplayRequest(context, TestAssets.SAMPLE_JPEG_URI) {
            resultCachePolicy(DISABLED)
            memoryCachePolicy(DISABLED)
            bitmapConfig(BitmapConfig.LowQuality)
        }.let { runBlocking { sketch.execute(it) } }
            .asOrNull<DisplayResult.Success>()!!
            .drawable.asOrNull<BitmapDrawable>()!!
            .apply {
                Assert.assertEquals(RGB_565, bitmap.config)
            }
        DisplayRequest(context, TestAssets.SAMPLE_PNG_URI) {
            resultCachePolicy(DISABLED)
            memoryCachePolicy(DISABLED)
            bitmapConfig(BitmapConfig.LowQuality)
        }.let { runBlocking { sketch.execute(it) } }
            .asOrNull<DisplayResult.Success>()!!
            .drawable.asOrNull<BitmapDrawable>()!!
            .apply {
                if (VERSION.SDK_INT >= VERSION_CODES.KITKAT) {
                    Assert.assertEquals(ARGB_8888, bitmap.config)
                } else {
                    @Suppress("DEPRECATION")
                    Assert.assertEquals(ARGB_4444, bitmap.config)
                }
            }

        DisplayRequest(context, TestAssets.SAMPLE_JPEG_URI) {
            resultCachePolicy(DISABLED)
            memoryCachePolicy(DISABLED)
            bitmapConfig(BitmapConfig.HighQuality)
        }.let { runBlocking { sketch.execute(it) } }
            .asOrNull<DisplayResult.Success>()!!
            .drawable.asOrNull<BitmapDrawable>()!!
            .apply {
                if (VERSION.SDK_INT >= VERSION_CODES.O) {
                    Assert.assertEquals(RGBA_F16, bitmap.config)
                } else {
                    Assert.assertEquals(ARGB_8888, bitmap.config)
                }
            }
        DisplayRequest(context, TestAssets.SAMPLE_PNG_URI) {
            resultCachePolicy(DISABLED)
            memoryCachePolicy(DISABLED)
            bitmapConfig(BitmapConfig.HighQuality)
        }.let { runBlocking { sketch.execute(it) } }
            .asOrNull<DisplayResult.Success>()!!
            .drawable.asOrNull<BitmapDrawable>()!!
            .apply {
                if (VERSION.SDK_INT >= VERSION_CODES.O) {
                    Assert.assertEquals(RGBA_F16, bitmap.config)
                } else {
                    Assert.assertEquals(ARGB_8888, bitmap.config)
                }
            }
    }

    @Test
    fun testColorSpace() {
        if (VERSION.SDK_INT < VERSION_CODES.O) return

        val context = getTestContext()
        val sketch = newSketch()

        DisplayRequest(context, TestAssets.SAMPLE_JPEG_URI) {
            resultCachePolicy(DISABLED)
            memoryCachePolicy(DISABLED)
        }.let { runBlocking { sketch.execute(it) } }
            .asOrNull<DisplayResult.Success>()!!
            .drawable.asOrNull<BitmapDrawable>()!!
            .apply {
                Assert.assertEquals(
                    ColorSpace.get(ColorSpace.Named.SRGB).name,
                    bitmap.colorSpace!!.name
                )
            }

        DisplayRequest(context, TestAssets.SAMPLE_JPEG_URI) {
            resultCachePolicy(DISABLED)
            memoryCachePolicy(DISABLED)
            colorSpace(ColorSpace.get(ColorSpace.Named.ADOBE_RGB))
        }.let { runBlocking { sketch.execute(it) } }
            .asOrNull<DisplayResult.Success>()!!
            .drawable.asOrNull<BitmapDrawable>()!!
            .apply {
                Assert.assertEquals(
                    ColorSpace.get(ColorSpace.Named.ADOBE_RGB).name,
                    bitmap.colorSpace!!.name
                )
            }

        DisplayRequest(context, TestAssets.SAMPLE_JPEG_URI) {
            resultCachePolicy(DISABLED)
            memoryCachePolicy(DISABLED)
            colorSpace(ColorSpace.get(ColorSpace.Named.DISPLAY_P3))
        }.let { runBlocking { sketch.execute(it) } }
            .asOrNull<DisplayResult.Success>()!!
            .drawable.asOrNull<BitmapDrawable>()!!
            .apply {
                Assert.assertEquals(
                    ColorSpace.get(ColorSpace.Named.DISPLAY_P3).name,
                    bitmap.colorSpace!!.name
                )
            }
    }

    @Test
    fun testPreferQualityOverSpeed() {
        val context = getTestContext()
        val sketch = newSketch()

        DisplayRequest(context, TestAssets.SAMPLE_JPEG_URI) {
            resultCachePolicy(DISABLED)
            memoryCachePolicy(DISABLED)
        }.let { runBlocking { sketch.execute(it) } }.apply {
            Assert.assertTrue(this is DisplayResult.Success)
        }

        DisplayRequest(context, TestAssets.SAMPLE_JPEG_URI) {
            resultCachePolicy(DISABLED)
            memoryCachePolicy(DISABLED)
            preferQualityOverSpeed(true)
        }.let { runBlocking { sketch.execute(it) } }.apply {
            Assert.assertTrue(this is DisplayResult.Success)
        }

        DisplayRequest(context, TestAssets.SAMPLE_JPEG_URI) {
            resultCachePolicy(DISABLED)
            memoryCachePolicy(DISABLED)
            preferQualityOverSpeed(false)
        }.let { runBlocking { sketch.execute(it) } }.apply {
            Assert.assertTrue(this is DisplayResult.Success)
        }
    }

    @Test
    fun testResize() {
        val (context, sketch) = getTestContextAndNewSketch()
        val imageUri = TestAssets.SAMPLE_JPEG_URI
        val imageSize = Size(1291, 1936)
        val displaySize = context.resources.displayMetrics.let {
            Size(it.widthPixels, it.heightPixels)
        }

        // default
        DisplayRequest(context, imageUri) {
            resultCachePolicy(DISABLED)
            memoryCachePolicy(DISABLED)
        }
            .let { runBlocking { sketch.execute(it) } }
            .asOrNull<DisplayResult.Success>()!!.apply {
                Assert.assertEquals(imageSize, imageInfo.size)
                Assert.assertEquals(
                    samplingByTarget(imageSize, displaySize),
                    drawable.intrinsicSize
                )
                Assert.assertEquals(imageInfo.size.ratio, drawable.intrinsicSize.ratio)
            }

        // size: small, precision=LESS_PIXELS/SAME_ASPECT_RATIO/EXACTLY
        val smallSize1 = Size(600, 500)
        DisplayRequest(context, imageUri) {
            resizeSize(smallSize1)
            resizePrecision(LESS_PIXELS)
        }.let { runBlocking { sketch.execute(it) } }
            .asOrNull<DisplayResult.Success>()!!.apply {
                Assert.assertEquals(imageSize, imageInfo.size)
                Assert.assertEquals(Size(323, 484), drawable.intrinsicSize)
                Assert.assertEquals(imageInfo.size.ratio, drawable.intrinsicSize.ratio)
            }
        DisplayRequest(context, imageUri) {
            resizeSize(smallSize1)
            resizePrecision(SAME_ASPECT_RATIO)
        }.let { runBlocking { sketch.execute(it) } }
            .asOrNull<DisplayResult.Success>()!!.apply {
                Assert.assertEquals(imageSize, imageInfo.size)
                Assert.assertEquals(Size(322, 268), drawable.intrinsicSize)
                Assert.assertEquals(smallSize1.ratio, drawable.intrinsicSize.ratio)
            }
        DisplayRequest(context, imageUri) {
            resizeSize(smallSize1)
            resizePrecision(EXACTLY)
        }.let { runBlocking { sketch.execute(it) } }
            .asOrNull<DisplayResult.Success>()!!.apply {
                Assert.assertEquals(imageSize, imageInfo.size)
                Assert.assertEquals(smallSize1, drawable.intrinsicSize)
            }

        val smallSize2 = Size(500, 600)
        DisplayRequest(context, imageUri) {
            resizeSize(smallSize2)
            resizePrecision(LESS_PIXELS)
        }.let { runBlocking { sketch.execute(it) } }
            .asOrNull<DisplayResult.Success>()!!.apply {
                Assert.assertEquals(imageSize, imageInfo.size)
                Assert.assertEquals(Size(323, 484), drawable.intrinsicSize)
                Assert.assertEquals(imageInfo.size.ratio, drawable.intrinsicSize.ratio)
            }
        DisplayRequest(context, imageUri) {
            resizeSize(smallSize2)
            resizePrecision(SAME_ASPECT_RATIO)
        }.let { runBlocking { sketch.execute(it) } }
            .asOrNull<DisplayResult.Success>()!!.apply {
                Assert.assertEquals(imageSize, imageInfo.size)
                Assert.assertEquals(Size(322, 387), drawable.intrinsicSize)
                Assert.assertEquals(smallSize2.ratio, drawable.intrinsicSize.ratio)
            }
        DisplayRequest(context, imageUri) {
            resizeSize(smallSize2)
            resizePrecision(EXACTLY)
        }.let { runBlocking { sketch.execute(it) } }
            .asOrNull<DisplayResult.Success>()!!.apply {
                Assert.assertEquals(imageSize, imageInfo.size)
                Assert.assertEquals(smallSize2, drawable.intrinsicSize)
            }

        // size: same, precision=LESS_PIXELS/SAME_ASPECT_RATIO/EXACTLY
        val sameSize = Size(imageSize.width, imageSize.height)
        DisplayRequest(context, imageUri) {
            resizeSize(sameSize)
            resizePrecision(LESS_PIXELS)
        }.let { runBlocking { sketch.execute(it) } }
            .asOrNull<DisplayResult.Success>()!!.apply {
                Assert.assertEquals(imageSize, imageInfo.size)
                Assert.assertEquals(sameSize, drawable.intrinsicSize)
            }
        DisplayRequest(context, imageUri) {
            resizeSize(sameSize)
            resizePrecision(SAME_ASPECT_RATIO)
        }.let { runBlocking { sketch.execute(it) } }
            .asOrNull<DisplayResult.Success>()!!.apply {
                Assert.assertEquals(imageSize, imageInfo.size)
                Assert.assertEquals(sameSize, drawable.intrinsicSize)
            }
        DisplayRequest(context, imageUri) {
            resizeSize(sameSize)
            resizePrecision(EXACTLY)
        }.let { runBlocking { sketch.execute(it) } }
            .asOrNull<DisplayResult.Success>()!!.apply {
                Assert.assertEquals(imageSize, imageInfo.size)
                Assert.assertEquals(sameSize, drawable.intrinsicSize)
            }

        // size: big, precision=LESS_PIXELS/SAME_ASPECT_RATIO/EXACTLY
        val bigSize1 = Size(2500, 2100)
        DisplayRequest(context, imageUri) {
            resizeSize(bigSize1)
            resizePrecision(LESS_PIXELS)
        }.let { runBlocking { sketch.execute(it) } }
            .asOrNull<DisplayResult.Success>()!!.apply {
                Assert.assertEquals(imageSize, imageInfo.size)
                Assert.assertEquals(imageSize, drawable.intrinsicSize)
                Assert.assertEquals(imageInfo.size.ratio, drawable.intrinsicSize.ratio)
            }
        DisplayRequest(context, imageUri) {
            resizeSize(bigSize1)
            resizePrecision(SAME_ASPECT_RATIO)
        }.let { runBlocking { sketch.execute(it) } }
            .asOrNull<DisplayResult.Success>()!!.apply {
                Assert.assertEquals(imageSize, imageInfo.size)
                Assert.assertEquals(Size(1291, 1084), drawable.intrinsicSize)
                Assert.assertEquals(bigSize1.ratio, drawable.intrinsicSize.ratio)
            }
        DisplayRequest(context, imageUri) {
            resizeSize(bigSize1)
            resizePrecision(EXACTLY)
        }.let { runBlocking { sketch.execute(it) } }
            .asOrNull<DisplayResult.Success>()!!.apply {
                Assert.assertEquals(imageSize, imageInfo.size)
                Assert.assertEquals(bigSize1, drawable.intrinsicSize)
            }

        val bigSize2 = Size(2100, 2500)
        DisplayRequest(context, imageUri) {
            resizeSize(bigSize2)
            resizePrecision(LESS_PIXELS)
        }.let { runBlocking { sketch.execute(it) } }
            .asOrNull<DisplayResult.Success>()!!.apply {
                Assert.assertEquals(imageSize, imageInfo.size)
                Assert.assertEquals(imageSize, drawable.intrinsicSize)
                Assert.assertEquals(imageInfo.size.ratio, drawable.intrinsicSize.ratio)
            }
        DisplayRequest(context, imageUri) {
            resizeSize(bigSize2)
            resizePrecision(SAME_ASPECT_RATIO)
        }.let { runBlocking { sketch.execute(it) } }
            .asOrNull<DisplayResult.Success>()!!.apply {
                Assert.assertEquals(imageSize, imageInfo.size)
                Assert.assertEquals(Size(1291, 1537), drawable.intrinsicSize)
                Assert.assertEquals(bigSize2.ratio, drawable.intrinsicSize.ratio)
            }
        DisplayRequest(context, imageUri) {
            resizeSize(bigSize2)
            resizePrecision(EXACTLY)
        }.let { runBlocking { sketch.execute(it) } }
            .asOrNull<DisplayResult.Success>()!!.apply {
                Assert.assertEquals(imageSize, imageInfo.size)
                Assert.assertEquals(bigSize2, drawable.intrinsicSize)
            }

        val bigSize3 = Size(800, 2500)
        DisplayRequest(context, imageUri) {
            resizeSize(bigSize3)
            resizePrecision(LESS_PIXELS)
        }.let { runBlocking { sketch.execute(it) } }
            .asOrNull<DisplayResult.Success>()!!.apply {
                Assert.assertEquals(imageSize, imageInfo.size)
                Assert.assertEquals(Size(646, 968), drawable.intrinsicSize)
                Assert.assertEquals(imageInfo.size.ratio, drawable.intrinsicSize.ratio)
            }
        DisplayRequest(context, imageUri) {
            resizeSize(bigSize3)
            resizePrecision(SAME_ASPECT_RATIO)
        }.let { runBlocking { sketch.execute(it) } }
            .asOrNull<DisplayResult.Success>()!!.apply {
                Assert.assertEquals(imageSize, imageInfo.size)
                Assert.assertEquals(Size(620, 1936), drawable.intrinsicSize)
                Assert.assertEquals(bigSize3.ratio, drawable.intrinsicSize.ratio)
            }
        DisplayRequest(context, imageUri) {
            resizeSize(bigSize3)
            resizePrecision(EXACTLY)
        }.let { runBlocking { sketch.execute(it) } }
            .asOrNull<DisplayResult.Success>()!!.apply {
                Assert.assertEquals(imageSize, imageInfo.size)
                Assert.assertEquals(bigSize3, drawable.intrinsicSize)
            }

        val bigSize4 = Size(2500, 800)
        DisplayRequest(context, imageUri) {
            resizeSize(bigSize4)
            resizePrecision(LESS_PIXELS)
        }.let { runBlocking { sketch.execute(it) } }
            .asOrNull<DisplayResult.Success>()!!.apply {
                Assert.assertEquals(imageSize, imageInfo.size)
                Assert.assertEquals(Size(646, 968), drawable.intrinsicSize)
                Assert.assertEquals(imageInfo.size.ratio, drawable.intrinsicSize.ratio)
            }
        DisplayRequest(context, imageUri) {
            resizeSize(bigSize4)
            resizePrecision(SAME_ASPECT_RATIO)
        }.let { runBlocking { sketch.execute(it) } }
            .asOrNull<DisplayResult.Success>()!!.apply {
                Assert.assertEquals(imageSize, imageInfo.size)
                Assert.assertEquals(Size(1291, 413), drawable.intrinsicSize)
                Assert.assertEquals(bigSize4.ratio, drawable.intrinsicSize.ratio)
            }
        DisplayRequest(context, imageUri) {
            resizeSize(bigSize4)
            resizePrecision(EXACTLY)
        }.let { runBlocking { sketch.execute(it) } }
            .asOrNull<DisplayResult.Success>()!!.apply {
                Assert.assertEquals(imageSize, imageInfo.size)
                Assert.assertEquals(bigSize4, drawable.intrinsicSize)
            }

        /* scale */
        val size = Size(600, 500)
        var sarStartCropBitmap: Bitmap?
        var sarCenterCropBitmap: Bitmap?
        var sarEndCropBitmap: Bitmap?
        var sarFillCropBitmap: Bitmap?
        DisplayRequest(context, imageUri) {
            resizeSize(size)
            resizePrecision(SAME_ASPECT_RATIO)
            resizeScale(START_CROP)
        }.let { runBlocking { sketch.execute(it) } }
            .asOrNull<DisplayResult.Success>()!!.apply {
                sarStartCropBitmap = drawable.asOrNull<BitmapDrawable>()!!.bitmap
                Assert.assertEquals(imageSize, imageInfo.size)
                Assert.assertEquals(Size(322, 268), drawable.intrinsicSize)
                Assert.assertEquals(size.ratio, drawable.intrinsicSize.ratio)
            }
        DisplayRequest(context, imageUri) {
            resizeSize(size)
            resizePrecision(SAME_ASPECT_RATIO)
            resizeScale(CENTER_CROP)
        }.let { runBlocking { sketch.execute(it) } }
            .asOrNull<DisplayResult.Success>()!!.apply {
                sarCenterCropBitmap = drawable.asOrNull<BitmapDrawable>()!!.bitmap
                Assert.assertEquals(imageSize, imageInfo.size)
                Assert.assertEquals(Size(322, 268), drawable.intrinsicSize)
                Assert.assertEquals(size.ratio, drawable.intrinsicSize.ratio)
            }
        DisplayRequest(context, imageUri) {
            resizeSize(size)
            resizePrecision(SAME_ASPECT_RATIO)
            resizeScale(END_CROP)
        }.let { runBlocking { sketch.execute(it) } }
            .asOrNull<DisplayResult.Success>()!!.apply {
                sarEndCropBitmap = drawable.asOrNull<BitmapDrawable>()!!.bitmap
                Assert.assertEquals(imageSize, imageInfo.size)
                Assert.assertEquals(Size(322, 268), drawable.intrinsicSize)
                Assert.assertEquals(size.ratio, drawable.intrinsicSize.ratio)
            }
        DisplayRequest(context, imageUri) {
            resizeSize(size)
            resizePrecision(SAME_ASPECT_RATIO)
            resizeScale(FILL)
        }.let { runBlocking { sketch.execute(it) } }
            .asOrNull<DisplayResult.Success>()!!.apply {
                sarFillCropBitmap = drawable.asOrNull<BitmapDrawable>()!!.bitmap
                Assert.assertEquals(imageSize, imageInfo.size)
                Assert.assertEquals(
                    if (VERSION.SDK_INT >= 24)
                        Size(323, 269) else Size(322, 268),
                    drawable.intrinsicSize
                )
                Assert.assertEquals(size.ratio, drawable.intrinsicSize.ratio)
            }
        Assert.assertNotEquals(sarStartCropBitmap!!.corners(), sarCenterCropBitmap!!.corners())
        Assert.assertNotEquals(sarStartCropBitmap!!.corners(), sarEndCropBitmap!!.corners())
        Assert.assertNotEquals(sarStartCropBitmap!!.corners(), sarFillCropBitmap!!.corners())
        Assert.assertNotEquals(sarCenterCropBitmap!!.corners(), sarEndCropBitmap!!.corners())
        Assert.assertNotEquals(sarCenterCropBitmap!!.corners(), sarFillCropBitmap!!.corners())
        Assert.assertNotEquals(sarEndCropBitmap!!.corners(), sarFillCropBitmap!!.corners())

        var exactlyStartCropBitmap: Bitmap?
        var exactlyCenterCropBitmap: Bitmap?
        var exactlyEndCropBitmap: Bitmap?
        var exactlyFillCropBitmap: Bitmap?
        DisplayRequest(context, imageUri) {
            resizeSize(size)
            resizePrecision(EXACTLY)
            resizeScale(START_CROP)
        }.let { runBlocking { sketch.execute(it) } }
            .asOrNull<DisplayResult.Success>()!!.apply {
                exactlyStartCropBitmap = drawable.asOrNull<BitmapDrawable>()!!.bitmap
                Assert.assertEquals(imageSize, imageInfo.size)
                Assert.assertEquals(size, drawable.intrinsicSize)
            }
        DisplayRequest(context, imageUri) {
            resizeSize(size)
            resizePrecision(EXACTLY)
            resizeScale(CENTER_CROP)
        }.let { runBlocking { sketch.execute(it) } }
            .asOrNull<DisplayResult.Success>()!!.apply {
                exactlyCenterCropBitmap = drawable.asOrNull<BitmapDrawable>()!!.bitmap
                Assert.assertEquals(imageSize, imageInfo.size)
                Assert.assertEquals(size, drawable.intrinsicSize)
            }
        DisplayRequest(context, imageUri) {
            resizeSize(size)
            resizePrecision(EXACTLY)
            resizeScale(END_CROP)
        }.let { runBlocking { sketch.execute(it) } }
            .asOrNull<DisplayResult.Success>()!!.apply {
                exactlyEndCropBitmap = drawable.asOrNull<BitmapDrawable>()!!.bitmap
                Assert.assertEquals(imageSize, imageInfo.size)
                Assert.assertEquals(size, drawable.intrinsicSize)
            }
        DisplayRequest(context, imageUri) {
            resizeSize(size)
            resizePrecision(EXACTLY)
            resizeScale(FILL)
        }.let { runBlocking { sketch.execute(it) } }
            .asOrNull<DisplayResult.Success>()!!.apply {
                exactlyFillCropBitmap = drawable.asOrNull<BitmapDrawable>()!!.bitmap
                Assert.assertEquals(imageSize, imageInfo.size)
                Assert.assertEquals(size, drawable.intrinsicSize)
            }
        Assert.assertNotEquals(
            exactlyStartCropBitmap!!.corners(),
            exactlyCenterCropBitmap!!.corners()
        )
        Assert.assertNotEquals(exactlyStartCropBitmap!!.corners(), exactlyEndCropBitmap!!.corners())
        Assert.assertNotEquals(
            exactlyStartCropBitmap!!.corners(),
            exactlyFillCropBitmap!!.corners()
        )
        Assert.assertNotEquals(
            exactlyCenterCropBitmap!!.corners(),
            exactlyEndCropBitmap!!.corners()
        )
        Assert.assertNotEquals(
            exactlyCenterCropBitmap!!.corners(),
            exactlyFillCropBitmap!!.corners()
        )
        Assert.assertNotEquals(exactlyEndCropBitmap!!.corners(), exactlyFillCropBitmap!!.corners())
    }

    @Test
    fun testTransformations() {
        val context = getTestContext()
        val sketch = newSketch()
        val imageUri = TestAssets.SAMPLE_JPEG_URI
        val request = DisplayRequest(context, imageUri) {
            memoryCachePolicy(DISABLED)
            resultCachePolicy(DISABLED)
        }

        request.let { runBlocking { sketch.execute(it) } }
            .asOrNull<DisplayResult.Success>()!!
            .drawable.apply {
                Assert.assertTrue(this.asOrNull<SketchDrawable>()!!.transformedList?.all {
                    it.startsWith("ResizeTransformed") || it.startsWith("InSampledTransformed")
                } != false)
            }

        request.let { runBlocking { sketch.execute(it) } }
            .asOrNull<DisplayResult.Success>()!!
            .drawable.apply {
                Assert.assertNotEquals(
                    listOf(0, 0, 0, 0),
                    this.asOrNull<BitmapDrawable>()!!.bitmap.corners()
                )
                Assert.assertNull(
                    this.asOrNull<SketchDrawable>()!!.transformedList?.getRoundedCornersTransformed()
                )
            }
        request.newDisplayRequest {
            addTransformations(RoundedCornersTransformation(30f))
        }.let { runBlocking { sketch.execute(it) } }
            .asOrNull<DisplayResult.Success>()!!
            .drawable.apply {
                Assert.assertEquals(
                    listOf(0, 0, 0, 0),
                    this.asOrNull<BitmapDrawable>()!!.bitmap.corners()
                )
                Assert.assertNotNull(
                    this.asOrNull<SketchDrawable>()!!.transformedList?.getRoundedCornersTransformed()
                )
            }

        request.newDisplayRequest {
            resizeSize(500, 500)
            resizePrecision(LESS_PIXELS)
        }.let { runBlocking { sketch.execute(it) } }
            .asOrNull<DisplayResult.Success>()!!
            .drawable.apply {
                Assert.assertEquals(Size(323, 484), intrinsicSize)
                Assert.assertNull(
                    this.asOrNull<SketchDrawable>()!!.transformedList?.getRotateTransformed()
                )
            }
        request.newDisplayRequest {
            resizeSize(500, 500)
            resizePrecision(LESS_PIXELS)
            addTransformations(RotateTransformation(90))
        }.let { runBlocking { sketch.execute(it) } }
            .asOrNull<DisplayResult.Success>()!!
            .drawable.apply {
                Assert.assertEquals(Size(484, 323), intrinsicSize)
                Assert.assertNotNull(
                    this.asOrNull<SketchDrawable>()!!.transformedList?.getRotateTransformed()
                )
            }

        request.newDisplayRequest {
            resizeSize(500, 500)
            resizePrecision(LESS_PIXELS)
        }.let { runBlocking { sketch.execute(it) } }
            .asOrNull<DisplayResult.Success>()!!
            .drawable.apply {
                Assert.assertEquals(Size(323, 484), intrinsicSize)
                Assert.assertNotEquals(
                    listOf(0, 0, 0, 0),
                    this.asOrNull<BitmapDrawable>()!!.bitmap.corners()
                )
                Assert.assertNull(
                    this.asOrNull<SketchDrawable>()!!.transformedList?.getCircleCropTransformed()
                )
            }
        request.newDisplayRequest {
            resizeSize(500, 500)
            resizePrecision(LESS_PIXELS)
            addTransformations(CircleCropTransformation())
        }.let { runBlocking { sketch.execute(it) } }
            .asOrNull<DisplayResult.Success>()!!
            .drawable.apply {
                Assert.assertEquals(Size(323, 323), intrinsicSize)
                Assert.assertEquals(
                    listOf(0, 0, 0, 0),
                    this.asOrNull<BitmapDrawable>()!!.bitmap.corners()
                )
                Assert.assertNotNull(
                    this.asOrNull<SketchDrawable>()!!.transformedList?.getCircleCropTransformed()
                )
            }
    }

    @Test
    fun testDisallowReuseBitmap() {
        val context = getTestContext()
        val sketch = newSketch()
        val bitmapPool = sketch.bitmapPool
        val imageUri = TestAssets.SAMPLE_JPEG_URI
        val request = DisplayRequest(context, imageUri) {
            memoryCachePolicy(DISABLED)
            resultCachePolicy(DISABLED)
            resizeSize(500, 500)
            resizePrecision(LESS_PIXELS)
        }

        bitmapPool.put(Bitmap.createBitmap(323, 484, ARGB_8888))
        Assert.assertTrue(bitmapPool.exist(323, 484, ARGB_8888))

        request.newDisplayRequest {
            disallowReuseBitmap(true)
        }.let { runBlocking { sketch.execute(it) } }
        Assert.assertTrue(bitmapPool.exist(323, 484, ARGB_8888))

        request.newDisplayRequest {
            disallowReuseBitmap(false)
        }.let { runBlocking { sketch.execute(it) } }
        if (VERSION.SDK_INT >= VERSION_CODES.KITKAT) {
            Assert.assertFalse(bitmapPool.exist(323, 484, ARGB_8888))
        } else {
            Assert.assertTrue(bitmapPool.exist(323, 484, ARGB_8888))
        }

        bitmapPool.clear()
        bitmapPool.put(Bitmap.createBitmap(323, 484, ARGB_8888))
        request.newDisplayRequest {
            disallowReuseBitmap(null)
        }.let { runBlocking { sketch.execute(it) } }
        if (VERSION.SDK_INT >= VERSION_CODES.KITKAT) {
            Assert.assertFalse(bitmapPool.exist(323, 484, ARGB_8888))
        } else {
            Assert.assertTrue(bitmapPool.exist(323, 484, ARGB_8888))
        }

        bitmapPool.clear()
        Assert.assertTrue(bitmapPool.size == 0L)
        request.newDisplayRequest {
            disallowReuseBitmap(false)
            transformations(
                RoundedCornersTransformation(30f),
                CircleCropTransformation(),
                RotateTransformation(90),
            )
        }.let { runBlocking { sketch.execute(it) } }
        Assert.assertTrue(bitmapPool.size > 0L)

        bitmapPool.clear()
        Assert.assertTrue(bitmapPool.size == 0L)
        request.newDisplayRequest {
            disallowReuseBitmap(true)
            transformations(
                RoundedCornersTransformation(30f),
                CircleCropTransformation(),
                RotateTransformation(90),
            )
        }.let { runBlocking { sketch.execute(it) } }
        Assert.assertTrue(bitmapPool.size == 0L)
    }

    @Test
    fun testIgnoreExifOrientation() {
        val context = getTestContext()
        val sketch = newSketch()
        ExifOrientationTestFileHelper(context, "exif_origin_clock_hor.jpeg").files().forEach {
            Assert.assertNotEquals(ExifInterface.ORIENTATION_UNDEFINED, it.exifOrientation)

            DisplayRequest(context, it.file.path)
                .let { runBlocking { sketch.execute(it) } }
                .asOrNull<DisplayResult.Success>()!!
                .drawable.asOrNull<SketchDrawable>()!!
                .apply {
                    Assert.assertEquals(it.exifOrientation, imageInfo.exifOrientation)
                    Assert.assertEquals(Size(1500, 750), imageInfo.size)
                }

            DisplayRequest(context, it.file.path) {
                ignoreExifOrientation(true)
            }.let { runBlocking { sketch.execute(it) } }
                .asOrNull<DisplayResult.Success>()!!
                .drawable.asOrNull<SketchDrawable>()!!
                .apply {
                    Assert.assertEquals(
                        ExifInterface.ORIENTATION_UNDEFINED,
                        imageInfo.exifOrientation
                    )
                    if (it.exifOrientation == ExifInterface.ORIENTATION_ROTATE_90
                        || it.exifOrientation == ExifInterface.ORIENTATION_ROTATE_270
                        || it.exifOrientation == ExifInterface.ORIENTATION_TRANSVERSE
                        || it.exifOrientation == ExifInterface.ORIENTATION_TRANSPOSE
                    ) {
                        Assert.assertEquals(
                            exifOrientationName(it.exifOrientation),
                            Size(750, 1500),
                            imageInfo.size
                        )
                    } else {
                        Assert.assertEquals(
                            exifOrientationName(it.exifOrientation),
                            Size(1500, 750),
                            imageInfo.size
                        )
                    }
                }
        }

        DisplayRequest(context, TestAssets.SAMPLE_JPEG_URI)
            .let { runBlocking { sketch.execute(it) } }
            .asOrNull<DisplayResult.Success>()!!
            .drawable.asOrNull<SketchDrawable>()!!
            .apply {
                Assert.assertEquals(ExifInterface.ORIENTATION_NORMAL, imageInfo.exifOrientation)
                Assert.assertEquals(Size(1291, 1936), imageInfo.size)
            }
    }

    @Test
    fun testResultCachePolicy() {
        val context = getTestContext()
        val sketch = newSketch()
        val diskCache = sketch.resultCache
        val imageUri = TestAssets.SAMPLE_JPEG_URI
        val request = DisplayRequest(context, imageUri) {
            memoryCachePolicy(DISABLED)
            resizeSize(500, 500)
        }
        val resultCacheDataKey = request.toRequestContext().resultCacheDataKey

        /* ENABLED */
        diskCache.clear()
        Assert.assertFalse(diskCache.exist(resultCacheDataKey))
        request.newDisplayRequest {
            resultCachePolicy(ENABLED)
        }.let {
            runBlocking { sketch.execute(it) }
        }.asOrNull<DisplayResult.Success>()!!.apply {
            Assert.assertEquals(DataFrom.LOCAL, dataFrom)
        }

        Assert.assertTrue(diskCache.exist(resultCacheDataKey))
        request.newDisplayRequest {
            resultCachePolicy(ENABLED)
        }.let {
            runBlocking { sketch.execute(it) }
        }.asOrNull<DisplayResult.Success>()!!.apply {
            Assert.assertEquals(DataFrom.RESULT_CACHE, dataFrom)
        }

        /* DISABLED */
        diskCache.clear()
        Assert.assertFalse(diskCache.exist(resultCacheDataKey))
        request.newDisplayRequest {
            resultCachePolicy(DISABLED)
        }.let {
            runBlocking { sketch.execute(it) }
        }.asOrNull<DisplayResult.Success>()!!.apply {
            Assert.assertEquals(DataFrom.LOCAL, dataFrom)
        }

        Assert.assertFalse(diskCache.exist(resultCacheDataKey))
        request.newDisplayRequest {
            resultCachePolicy(DISABLED)
        }.let {
            runBlocking { sketch.execute(it) }
        }.asOrNull<DisplayResult.Success>()!!.apply {
            Assert.assertEquals(DataFrom.LOCAL, dataFrom)
        }

        /* READ_ONLY */
        diskCache.clear()
        Assert.assertFalse(diskCache.exist(resultCacheDataKey))
        request.newDisplayRequest {
            resultCachePolicy(READ_ONLY)
        }.let {
            runBlocking { sketch.execute(it) }
        }.asOrNull<DisplayResult.Success>()!!.apply {
            Assert.assertEquals(DataFrom.LOCAL, dataFrom)
        }

        Assert.assertFalse(diskCache.exist(resultCacheDataKey))
        request.newDisplayRequest {
            resultCachePolicy(READ_ONLY)
        }.let {
            runBlocking { sketch.execute(it) }
        }.asOrNull<DisplayResult.Success>()!!.apply {
            Assert.assertEquals(DataFrom.LOCAL, dataFrom)
        }

        Assert.assertFalse(diskCache.exist(resultCacheDataKey))
        request.newDisplayRequest {
            resultCachePolicy(ENABLED)
        }.let {
            runBlocking { sketch.execute(it) }
        }
        Assert.assertTrue(diskCache.exist(resultCacheDataKey))
        request.newDisplayRequest {
            resultCachePolicy(READ_ONLY)
        }.let {
            runBlocking { sketch.execute(it) }
        }.asOrNull<DisplayResult.Success>()!!.apply {
            Assert.assertEquals(DataFrom.RESULT_CACHE, dataFrom)
        }

        /* WRITE_ONLY */
        diskCache.clear()
        Assert.assertFalse(diskCache.exist(resultCacheDataKey))
        request.newDisplayRequest {
            resultCachePolicy(WRITE_ONLY)
        }.let {
            runBlocking { sketch.execute(it) }
        }.asOrNull<DisplayResult.Success>()!!.apply {
            Assert.assertEquals(DataFrom.LOCAL, dataFrom)
        }

        Assert.assertTrue(diskCache.exist(resultCacheDataKey))
        request.newDisplayRequest {
            resultCachePolicy(WRITE_ONLY)
        }.let {
            runBlocking { sketch.execute(it) }
        }.asOrNull<DisplayResult.Success>()!!.apply {
            Assert.assertEquals(DataFrom.LOCAL, dataFrom)
        }
    }

    @Test
    fun testPlaceholder() {
        val context = getTestContext()
        val sketch = newSketch()
        val imageUri = TestAssets.SAMPLE_JPEG_URI
        var onStartDrawable: Drawable?
        val request = DisplayRequest(context, imageUri) {
            resizeSize(500, 500)
            target(object : DisplayTarget {
                override val supportDisplayCount: Boolean = true
                override fun onStart(placeholder: Drawable?) {
                    super.onStart(placeholder)
                    onStartDrawable = placeholder
                }
            })
        }
        val memoryCacheKey = request.toRequestContext().memoryCacheKey
        val memoryCache = sketch.memoryCache
        val colorDrawable = ColorDrawable(Color.BLUE)

        memoryCache.clear()
        onStartDrawable = null
        Assert.assertFalse(memoryCache.exist(memoryCacheKey))
        request.newDisplayRequest()
            .let { runBlocking { sketch.execute(it) } }
        Assert.assertNull(onStartDrawable)

        onStartDrawable = null
        Assert.assertTrue(memoryCache.exist(memoryCacheKey))
        request.newDisplayRequest {
            placeholder(colorDrawable)
        }.let { runBlocking { sketch.execute(it) } }
        Assert.assertNull(onStartDrawable)
        Assert.assertNotNull(onStartDrawable === colorDrawable)

        onStartDrawable = null
        Assert.assertTrue(memoryCache.exist(memoryCacheKey))
        request.newDisplayRequest {
            memoryCachePolicy(DISABLED)
            placeholder(colorDrawable)
        }.let { runBlocking { sketch.execute(it) } }
        Assert.assertNotNull(onStartDrawable)
        Assert.assertNotNull(onStartDrawable === colorDrawable)
    }

    @Test
    fun testError() {
        val context = getTestContext()
        val sketch = newSketch()
        val imageUri = TestAssets.SAMPLE_JPEG_URI
        var onErrorDrawable: Drawable?
        val request = DisplayRequest(context, imageUri) {
            resizeSize(500, 500)
            target(
                onError = {
                    onErrorDrawable = it
                }
            )
        }
        val errorRequest = DisplayRequest(context, newAssetUri("error.jpeg")) {
            resizeSize(500, 500)
            target(
                onError = {
                    onErrorDrawable = it
                }
            )
        }
        val colorDrawable = ColorDrawable(Color.BLUE)

        onErrorDrawable = null
        request.newDisplayRequest()
            .let { runBlocking { sketch.execute(it) } }
        Assert.assertNull(onErrorDrawable)

        onErrorDrawable = null
        request.newDisplayRequest {
            error(colorDrawable)
        }.let { runBlocking { sketch.execute(it) } }
        Assert.assertNull(onErrorDrawable)

        onErrorDrawable = null
        errorRequest.newDisplayRequest()
            .let { runBlocking { sketch.execute(it) } }
        Assert.assertNull(onErrorDrawable)

        onErrorDrawable = null
        errorRequest.newDisplayRequest {
            error(colorDrawable)
        }.let { runBlocking { sketch.execute(it) } }
        Assert.assertNotNull(onErrorDrawable)
        Assert.assertNotNull(onErrorDrawable === colorDrawable)

        onErrorDrawable = null
        errorRequest.newDisplayRequest {
            placeholder(colorDrawable)
        }.let { runBlocking { sketch.execute(it) } }
        Assert.assertNotNull(onErrorDrawable)
        Assert.assertNotNull(onErrorDrawable === colorDrawable)
    }

    @Test
    fun testTransition() {
        val context = getTestContext()
        val sketch = newSketch()
        val imageUri = TestAssets.SAMPLE_JPEG_URI
        val testTarget = TestTransitionDisplayTarget()
        val request = DisplayRequest(context, imageUri) {
            resizeSize(500, 500)
            target(testTarget)
        }
        val memoryCache = sketch.memoryCache
        val memoryCacheKey = request.toRequestContext().memoryCacheKey

        memoryCache.clear()
        Assert.assertFalse(memoryCache.exist(memoryCacheKey))
        request.newDisplayRequest()
            .let { runBlocking { sketch.enqueue(it).job.join() } }
        Assert.assertFalse(testTarget.drawable!! is CrossfadeDrawable)

        Assert.assertTrue(memoryCache.exist(memoryCacheKey))
        request.newDisplayRequest {
            transitionFactory(CrossfadeTransition.Factory())
        }.let { runBlocking { sketch.enqueue(it).job.join() } }
        Assert.assertFalse(testTarget.drawable!! is CrossfadeDrawable)

        memoryCache.clear()
        Assert.assertFalse(memoryCache.exist(memoryCacheKey))
        request.newDisplayRequest {
            transitionFactory(CrossfadeTransition.Factory())
        }.let { runBlocking { sketch.enqueue(it).job.join() } }
        Assert.assertTrue(testTarget.drawable!! is CrossfadeDrawable)
    }

    @Test
    fun testDisallowAnimatedImage() {
        if (VERSION.SDK_INT < VERSION_CODES.P) return

        val context = getTestContext()
        val sketch = newSketch {
            components {
                addDrawableDecoder(GifAnimatedDrawableDecoder.Factory())
            }
            httpStack(TestHttpStack(context))
        }
        val imageUri = TestAssets.SAMPLE_ANIM_GIF_URI
        val request = DisplayRequest(context, imageUri)

        request.let { runBlocking { sketch.execute(it) } }
            .asOrNull<DisplayResult.Success>()!!.apply {
                Assert.assertTrue(drawable is SketchAnimatableDrawable)
            }

        request.newDisplayRequest {
            disallowAnimatedImage(false)
        }.let { runBlocking { sketch.execute(it) } }
            .asOrNull<DisplayResult.Success>()!!.apply {
                Assert.assertTrue(drawable is SketchAnimatableDrawable)
            }

        request.newDisplayRequest {
            disallowAnimatedImage(null)
        }.let { runBlocking { sketch.execute(it) } }
            .asOrNull<DisplayResult.Success>()!!.apply {
                Assert.assertTrue(drawable is SketchAnimatableDrawable)
            }

        request.newDisplayRequest {
            disallowAnimatedImage(true)
        }.let { runBlocking { sketch.execute(it) } }
            .asOrNull<DisplayResult.Success>()!!.apply {
                Assert.assertFalse(drawable is SketchAnimatableDrawable)
            }
    }

    @Test
    fun testResizeApplyToDrawable() {
        val context = getTestContext()
        val sketch = newSketch()
        val imageUri = TestAssets.SAMPLE_JPEG_URI
        val request = DisplayRequest(context, imageUri) {
            resizeSize(500, 500)
        }

        request.let { runBlocking { sketch.execute(it) } }
            .asOrNull<DisplayResult.Success>()!!.apply {
                Assert.assertFalse(drawable is ResizeDrawable)
            }

        request.newDisplayRequest {
            resizeApplyToDrawable(false)
        }.let { runBlocking { sketch.execute(it) } }
            .asOrNull<DisplayResult.Success>()!!.apply {
                Assert.assertFalse(drawable is ResizeDrawable)
            }

        request.newDisplayRequest {
            resizeApplyToDrawable(null)
        }.let { runBlocking { sketch.execute(it) } }
            .asOrNull<DisplayResult.Success>()!!.apply {
                Assert.assertFalse(drawable is ResizeDrawable)
            }

        request.newDisplayRequest {
            resizeApplyToDrawable(true)
        }.let { runBlocking { sketch.execute(it) } }
            .asOrNull<DisplayResult.Success>()!!.apply {
                Assert.assertTrue(drawable is ResizeDrawable)
            }
    }

    @Test
    fun testMemoryCachePolicy() {
        val context = getTestContext()
        val sketch = newSketch()
        val memoryCache = sketch.memoryCache
        val imageUri = TestAssets.SAMPLE_JPEG_URI
        val request = DisplayRequest(context, imageUri) {
            resultCachePolicy(DISABLED)
            resizeSize(500, 500)
            target(TestDisplayCountDisplayTarget())
        }
        val memoryCacheKey = request.toRequestContext().memoryCacheKey

        /* ENABLED */
        memoryCache.clear()
        Assert.assertFalse(memoryCache.exist(memoryCacheKey))
        request.newDisplayRequest {
            memoryCachePolicy(ENABLED)
        }.let {
            runBlocking { sketch.execute(it) }
        }.asOrNull<DisplayResult.Success>()!!.apply {
            Assert.assertEquals(DataFrom.LOCAL, dataFrom)
        }

        Assert.assertTrue(memoryCache.exist(memoryCacheKey))
        request.newDisplayRequest {
            memoryCachePolicy(ENABLED)
        }.let {
            runBlocking { sketch.execute(it) }
        }.asOrNull<DisplayResult.Success>()!!.apply {
            Assert.assertEquals(DataFrom.MEMORY_CACHE, dataFrom)
        }

        /* DISABLED */
        memoryCache.clear()
        Assert.assertFalse(memoryCache.exist(memoryCacheKey))
        request.newDisplayRequest {
            memoryCachePolicy(DISABLED)
        }.let {
            runBlocking { sketch.execute(it) }
        }.asOrNull<DisplayResult.Success>()!!.apply {
            Assert.assertEquals(DataFrom.LOCAL, dataFrom)
        }

        Assert.assertFalse(memoryCache.exist(memoryCacheKey))
        request.newDisplayRequest {
            memoryCachePolicy(DISABLED)
        }.let {
            runBlocking { sketch.execute(it) }
        }.asOrNull<DisplayResult.Success>()!!.apply {
            Assert.assertEquals(DataFrom.LOCAL, dataFrom)
        }

        /* READ_ONLY */
        memoryCache.clear()
        Assert.assertFalse(memoryCache.exist(memoryCacheKey))
        request.newDisplayRequest {
            memoryCachePolicy(READ_ONLY)
        }.let {
            runBlocking { sketch.execute(it) }
        }.asOrNull<DisplayResult.Success>()!!.apply {
            Assert.assertEquals(DataFrom.LOCAL, dataFrom)
        }

        Assert.assertFalse(memoryCache.exist(memoryCacheKey))
        request.newDisplayRequest {
            memoryCachePolicy(READ_ONLY)
        }.let {
            runBlocking { sketch.execute(it) }
        }.asOrNull<DisplayResult.Success>()!!.apply {
            Assert.assertEquals(DataFrom.LOCAL, dataFrom)
        }

        Assert.assertFalse(memoryCache.exist(memoryCacheKey))
        request.newDisplayRequest {
            memoryCachePolicy(ENABLED)
        }.let {
            runBlocking { sketch.execute(it) }
        }
        Assert.assertTrue(memoryCache.exist(memoryCacheKey))
        request.newDisplayRequest {
            memoryCachePolicy(READ_ONLY)
        }.let {
            runBlocking { sketch.execute(it) }
        }.asOrNull<DisplayResult.Success>()!!.apply {
            Assert.assertEquals(DataFrom.MEMORY_CACHE, dataFrom)
        }

        /* WRITE_ONLY */
        memoryCache.clear()
        Assert.assertFalse(memoryCache.exist(memoryCacheKey))
        request.newDisplayRequest {
            memoryCachePolicy(WRITE_ONLY)
        }.let {
            runBlocking { sketch.execute(it) }
        }.asOrNull<DisplayResult.Success>()!!.apply {
            Assert.assertEquals(DataFrom.LOCAL, dataFrom)
        }

        Assert.assertTrue(memoryCache.exist(memoryCacheKey))
        request.newDisplayRequest {
            memoryCachePolicy(WRITE_ONLY)
        }.let {
            runBlocking { sketch.execute(it) }
        }.asOrNull<DisplayResult.Success>()!!.apply {
            Assert.assertEquals(DataFrom.LOCAL, dataFrom)
        }
    }

    @Test
    fun testListener() {
        val context = getTestContext()
        val sketch = newSketch()
        val imageUri = TestAssets.SAMPLE_JPEG_URI
        val errorImageUri = TestAssets.SAMPLE_JPEG_URI + ".fake"

        DisplayListenerSupervisor().let { listenerSupervisor ->
            Assert.assertEquals(listOf<String>(), listenerSupervisor.callbackActionList)

            DisplayRequest(context, imageUri) {
                listener(listenerSupervisor)
            }.let { request ->
                runBlocking { sketch.execute(request) }
            }
            Assert.assertEquals(
                listOf("onStart", "onSuccess"),
                listenerSupervisor.callbackActionList
            )
        }

        DisplayListenerSupervisor().let { listenerSupervisor ->
            Assert.assertEquals(listOf<String>(), listenerSupervisor.callbackActionList)

            DisplayRequest(context, errorImageUri) {
                listener(listenerSupervisor)
            }.let { request ->
                runBlocking { sketch.execute(request) }
            }
            Assert.assertEquals(listOf("onStart", "onError"), listenerSupervisor.callbackActionList)
        }

        var deferred: Deferred<DisplayResult>? = null
        val listenerSupervisor = DisplayListenerSupervisor {
            deferred?.cancel()
        }
        DisplayRequest(context, TestAssets.SAMPLE_JPEG_URI) {
            memoryCachePolicy(DISABLED)
            resultCachePolicy(DISABLED)
            listener(listenerSupervisor)
        }.let { request ->
            runBlocking {
                deferred = async {
                    sketch.execute(request)
                }
                deferred?.join()
            }
        }
        Assert.assertEquals(listOf("onStart", "onCancel"), listenerSupervisor.callbackActionList)
    }

    @Test
    fun testProgressListener() {
        val context = getTestContext()
        val sketch = newSketch {
            httpStack(TestHttpStack(context, 20))
        }
        val testImage = TestHttpStack.testImages.first()

        DisplayProgressListenerSupervisor().let { listenerSupervisor ->
            Assert.assertEquals(listOf<String>(), listenerSupervisor.callbackActionList)

            DisplayRequest(context, testImage.uriString) {
                memoryCachePolicy(DISABLED)
                resultCachePolicy(DISABLED)
                downloadCachePolicy(DISABLED)
                progressListener(listenerSupervisor)
            }.let { request ->
                runBlocking { sketch.execute(request) }
            }

            Assert.assertTrue(listenerSupervisor.callbackActionList.size > 1)
            listenerSupervisor.callbackActionList.forEachIndexed { index, _ ->
                if (index > 0) {
                    Assert.assertTrue(listenerSupervisor.callbackActionList[index - 1].toLong() < listenerSupervisor.callbackActionList[index].toLong())
                }
            }
            Assert.assertEquals(
                testImage.contentLength,
                listenerSupervisor.callbackActionList.last().toLong()
            )
        }
    }

    @Test
    fun testComponents() {
        val context = getTestContext()

        DisplayRequest(context, TestAssets.SAMPLE_JPEG_URI)
            .let { runBlocking { it.execute() } }.asOrThrow<DisplayResult.Success>().apply {
                Assert.assertNull(request.parameters?.get("TestRequestInterceptor"))
            }
        DisplayRequest(context, TestAssets.SAMPLE_JPEG_URI) {
            components {
                addRequestInterceptor(TestRequestInterceptor())
            }
        }.let { runBlocking { it.execute() } }.asOrThrow<DisplayResult.Success>().apply {
            Assert.assertEquals("true", request.parameters?.get("TestRequestInterceptor"))
        }

        DisplayRequest(context, TestAssets.SAMPLE_JPEG_URI) {
            resultCachePolicy(DISABLED)
            memoryCachePolicy(DISABLED)
        }.let { runBlocking { it.execute() } }.asOrThrow<DisplayResult.Success>().apply {
            Assert.assertFalse(transformedList?.contains("TestBitmapDecodeInterceptor") == true)
        }
        DisplayRequest(context, TestAssets.SAMPLE_JPEG_URI) {
            resultCachePolicy(DISABLED)
            memoryCachePolicy(DISABLED)
            components {
                addBitmapDecodeInterceptor(TestBitmapDecodeInterceptor())
            }
        }.let { runBlocking { it.execute() } }.asOrThrow<DisplayResult.Success>().apply {
            Assert.assertTrue(transformedList?.contains("TestBitmapDecodeInterceptor") == true)
        }

        DisplayRequest(context, TestAssets.SAMPLE_JPEG_URI) {
            resultCachePolicy(DISABLED)
            memoryCachePolicy(DISABLED)
        }.let { runBlocking { it.execute() } }.asOrThrow<DisplayResult.Success>().apply {
            Assert.assertFalse(transformedList?.contains("TestDrawableDecodeInterceptor") == true)
        }
        DisplayRequest(context, TestAssets.SAMPLE_JPEG_URI) {
            resultCachePolicy(DISABLED)
            memoryCachePolicy(DISABLED)
            components {
                addDrawableDecodeInterceptor(TestDrawableDecodeInterceptor())
            }
        }.let { runBlocking { it.execute() } }.asOrThrow<DisplayResult.Success>().apply {
            Assert.assertTrue(transformedList?.contains("TestDrawableDecodeInterceptor") == true)
        }

        DisplayRequest(context, TestAssets.SAMPLE_JPEG_URI.replace("asset", "test")) {
            memoryCachePolicy(DISABLED)
            resultCachePolicy(DISABLED)
        }.let { runBlocking { it.execute() } }.apply {
            Assert.assertTrue(this is DisplayResult.Error)
        }
        DisplayRequest(context, TestAssets.SAMPLE_JPEG_URI.replace("asset", "test")) {
            memoryCachePolicy(DISABLED)
            resultCachePolicy(DISABLED)
            components {
                addFetcher(TestAssetFetcherFactory())
            }
        }.let { runBlocking { it.execute() } }.apply {
            Assert.assertTrue(this is DisplayResult.Success)
        }

        DisplayRequest(context, TestAssets.SAMPLE_JPEG_URI) {
            memoryCachePolicy(DISABLED)
            resultCachePolicy(DISABLED)
        }.let { runBlocking { it.execute() } }.apply {
            Assert.assertTrue(this is DisplayResult.Success)
        }
        DisplayRequest(context, TestAssets.SAMPLE_JPEG_URI) {
            memoryCachePolicy(DISABLED)
            resultCachePolicy(DISABLED)
            components {
                addBitmapDecoder(TestErrorBitmapDecoder.Factory())
            }
        }.let { runBlocking { it.execute() } }.apply {
            Assert.assertTrue(this is DisplayResult.Error)
        }

        DisplayRequest(context, TestAssets.SAMPLE_JPEG_URI) {
            memoryCachePolicy(DISABLED)
            resultCachePolicy(DISABLED)
        }.let { runBlocking { it.execute() } }.apply {
            Assert.assertTrue(this is DisplayResult.Success)
        }
        DisplayRequest(context, TestAssets.SAMPLE_JPEG_URI) {
            memoryCachePolicy(DISABLED)
            resultCachePolicy(DISABLED)
            components {
                addDrawableDecoder(TestErrorDrawableDecoder.Factory())
            }
        }.let { runBlocking { it.execute() } }.apply {
            Assert.assertTrue(this is DisplayResult.Error)
        }
    }

    @Test
    fun testTarget() {
        val context = getTestContext()
        val sketch = newSketch()

        TestDisplayTarget().let { testTarget ->
            Assert.assertNull(testTarget.startDrawable)
            Assert.assertNull(testTarget.successDrawable)
            Assert.assertNull(testTarget.errorDrawable)
        }

        TestDisplayTarget().let { testTarget ->
            DisplayRequest(context, TestAssets.SAMPLE_JPEG_URI) {
                target(testTarget)
            }.let { request ->
                runBlocking { sketch.execute(request) }
            }
            Assert.assertNull(testTarget.startDrawable)
            Assert.assertNotNull(testTarget.successDrawable)
            Assert.assertNull(testTarget.errorDrawable)
        }

        TestDisplayTarget().let { testTarget ->
            DisplayRequest(context, TestAssets.SAMPLE_JPEG_URI + ".fake") {
                placeholder(ColorDrawable(Color.BLUE))
                error(android.R.drawable.btn_radio)
                target(testTarget)
            }.let { request ->
                runBlocking { sketch.execute(request) }
            }
            Assert.assertNotNull(testTarget.startDrawable)
            Assert.assertTrue(testTarget.startDrawable!!.asOrThrow<SketchStateNormalDrawable>().drawable is ColorDrawable)
            Assert.assertNull(testTarget.successDrawable)
            Assert.assertNotNull(testTarget.errorDrawable)
            Assert.assertTrue(testTarget.errorDrawable!!.asOrThrow<SketchStateNormalDrawable>().drawable is StateListDrawable)
        }

        TestDisplayTarget().let { testTarget ->
            var deferred: Deferred<DisplayResult>? = null
            val listenerSupervisor = DisplayListenerSupervisor {
                deferred?.cancel()
            }
            DisplayRequest(context, TestAssets.SAMPLE_JPEG_URI) {
                memoryCachePolicy(DISABLED)
                resultCachePolicy(DISABLED)
                listener(listenerSupervisor)
                target(testTarget)
            }.let { request ->
                runBlocking {
                    deferred = async {
                        sketch.execute(request)
                    }
                    deferred?.join()
                }
            }
            Assert.assertNull(testTarget.startDrawable)
            Assert.assertNull(testTarget.successDrawable)
            Assert.assertNull(testTarget.errorDrawable)
        }

        TestDisplayTarget().let { testTarget ->
            var deferred: Deferred<DisplayResult>? = null
            val listenerSupervisor = DisplayListenerSupervisor {
                deferred?.cancel()
            }
            DisplayRequest(context, TestAssets.SAMPLE_JPEG_URI + ".fake") {
                memoryCachePolicy(DISABLED)
                resultCachePolicy(DISABLED)
                listener(listenerSupervisor)
                error(android.R.drawable.btn_radio)
                target(testTarget)
            }.let { request ->
                runBlocking {
                    deferred = async {
                        sketch.execute(request)
                    }
                    deferred?.join()
                }
            }
            Assert.assertNull(testTarget.startDrawable)
            Assert.assertNull(testTarget.successDrawable)
            Assert.assertNull(testTarget.errorDrawable)
        }
    }

    @Test
    fun testLifecycle() {
        val context = getTestContext()
        val sketch = newSketch()
        val lifecycleOwner = object : LifecycleOwner {
            private var lifecycle: Lifecycle? = null
            override fun getLifecycle(): Lifecycle {
                return lifecycle ?: LifecycleRegistry(this).apply {
                    lifecycle = this
                }
            }
        }
        val myLifecycle = lifecycleOwner.lifecycle as LifecycleRegistry
        runBlocking(Dispatchers.Main) {
            myLifecycle.currentState = CREATED
        }

        DisplayRequest(context, TestAssets.SAMPLE_JPEG_URI).let { request ->
            Assert.assertSame(GlobalLifecycle, request.lifecycle)
            runBlocking {
                sketch.execute(request)
            }
        }.apply {
            Assert.assertTrue(this is DisplayResult.Success)
        }

        DisplayRequest(context, TestAssets.SAMPLE_JPEG_URI) {
            lifecycle(myLifecycle)
        }.let { request ->
            Assert.assertSame(myLifecycle, request.lifecycle)
            runBlocking {
                val deferred = async {
                    sketch.execute(request)
                }
                delay(2000)
                if (!deferred.isCompleted) {
                    withContext(Dispatchers.Main) {
                        myLifecycle.currentState = STARTED
                    }
                }
                delay(2000)
                deferred.await()
            }
        }.apply {
            Assert.assertTrue(this is DisplayResult.Success)
        }
    }

    @Test
    fun testExecuteAndEnqueue() {
        val context = getTestContext()

        DisplayRequest(context, TestAssets.SAMPLE_JPEG_URI).let { request ->
            runBlocking { request.execute() }
        }.apply {
            Assert.assertTrue(this is DisplayResult.Success)
        }

        DisplayRequest(context, TestAssets.SAMPLE_JPEG_URI).let { request ->
            runBlocking { request.enqueue().job.await() }
        }.apply {
            Assert.assertTrue(this is DisplayResult.Success)
        }
    }
}