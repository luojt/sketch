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
package com.github.panpf.sketch.test.decode

import android.graphics.Bitmap
import android.graphics.Bitmap.Config.RGB_565
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.panpf.sketch.datasource.DataFrom.LOCAL
import com.github.panpf.sketch.datasource.DataFrom.MEMORY
import com.github.panpf.sketch.decode.BitmapDecodeResult
import com.github.panpf.sketch.decode.ImageInfo
import com.github.panpf.sketch.decode.internal.createInSampledTransformed
import com.github.panpf.sketch.resize.Scale.FILL
import com.github.panpf.sketch.transform.createCircleCropTransformed
import com.github.panpf.sketch.transform.createRotateTransformed
import com.github.panpf.sketch.util.toInfoString
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BitmapDecodeResultTest {

    @Test
    fun testConstructor() {
        val newBitmap = Bitmap.createBitmap(100, 100, RGB_565)
        val imageInfo = ImageInfo(3000, 500, "image/png", 0)
        val transformedList = listOf(createInSampledTransformed(4), createRotateTransformed(45))
        BitmapDecodeResult(
            newBitmap,
            imageInfo,
            LOCAL,
            transformedList,
            mapOf("age" to "16")
        ).apply {
            Assert.assertTrue(newBitmap === bitmap)
            Assert.assertEquals(
                "ImageInfo(width=3000, height=500, mimeType='image/png', exifOrientation=UNDEFINED)",
                imageInfo.toString()
            )
            Assert.assertEquals(LOCAL, dataFrom)
            Assert.assertEquals(
                "InSampledTransformed(4), RotateTransformed(45)",
                this.transformedList?.joinToString()
            )
            Assert.assertEquals(
                mapOf("age" to "16"),
                this.extras
            )
        }
    }

    @Test
    fun testToString() {
        BitmapDecodeResult(
            bitmap = Bitmap.createBitmap(100, 100, RGB_565),
            imageInfo = ImageInfo(3000, 500, "image/png", 0),
            dataFrom = LOCAL,
            transformedList = listOf(createInSampledTransformed(4), createRotateTransformed(45)),
            extras = mapOf("age" to "16"),
        ).apply {
            Assert.assertEquals(
                "BitmapDecodeResult(bitmap=${bitmap.toInfoString()}, " +
                        "imageInfo=$imageInfo, " +
                        "dataFrom=$dataFrom, " +
                        "transformedList=$transformedList, " +
                        "extras={age=16})",
                toString()
            )
        }
    }

    @Test
    fun testNewResult() {
        val bitmap1 = Bitmap.createBitmap(100, 100, RGB_565)
        val bitmap2 = Bitmap.createBitmap(200, 200, RGB_565)

        val result = BitmapDecodeResult(
            bitmap = bitmap1,
            imageInfo = ImageInfo(3000, 500, "image/png", 0),
            dataFrom = LOCAL,
            transformedList = listOf(createInSampledTransformed(4), createRotateTransformed(45)),
            extras = mapOf("age" to "16"),
        ).apply {
            Assert.assertEquals(bitmap1, bitmap)
            Assert.assertEquals(ImageInfo(3000, 500, "image/png", 0), imageInfo)
            Assert.assertEquals(LOCAL, dataFrom)
            Assert.assertEquals(
                listOf(createInSampledTransformed(4), createRotateTransformed(45)),
                transformedList
            )
            Assert.assertEquals(
                mapOf("age" to "16"),
                this.extras
            )
        }

        result.newResult().apply {
            Assert.assertNotSame(result, this)
            Assert.assertEquals(result, this)
            Assert.assertEquals(bitmap1, bitmap)
            Assert.assertEquals(ImageInfo(3000, 500, "image/png", 0), imageInfo)
            Assert.assertEquals(LOCAL, dataFrom)
            Assert.assertEquals(
                listOf(createInSampledTransformed(4), createRotateTransformed(45)),
                transformedList
            )
            Assert.assertEquals(
                mapOf("age" to "16"),
                this.extras
            )
        }

        result.newResult(bitmap = bitmap2).apply {
            Assert.assertNotSame(result, this)
            Assert.assertNotEquals(result, this)
            Assert.assertEquals(bitmap2, bitmap)
            Assert.assertEquals(ImageInfo(3000, 500, "image/png", 0), imageInfo)
            Assert.assertEquals(LOCAL, dataFrom)
            Assert.assertEquals(
                listOf(createInSampledTransformed(4), createRotateTransformed(45)),
                transformedList
            )
            Assert.assertEquals(
                mapOf("age" to "16"),
                this.extras
            )
        }

        result.newResult(imageInfo = result.imageInfo.newImageInfo(width = 200, height = 200))
            .apply {
                Assert.assertNotSame(result, this)
                Assert.assertNotEquals(result, this)
                Assert.assertEquals(bitmap1, bitmap)
                Assert.assertEquals(ImageInfo(200, 200, "image/png", 0), imageInfo)
                Assert.assertEquals(LOCAL, dataFrom)
                Assert.assertEquals(
                    listOf(createInSampledTransformed(4), createRotateTransformed(45)),
                    transformedList
                )
                Assert.assertEquals(
                    mapOf("age" to "16"),
                    this.extras
                )
            }

        result.newResult(dataFrom = MEMORY).apply {
            Assert.assertNotSame(result, this)
            Assert.assertNotEquals(result, this)
            Assert.assertEquals(bitmap1, bitmap)
            Assert.assertEquals(ImageInfo(3000, 500, "image/png", 0), imageInfo)
            Assert.assertEquals(MEMORY, dataFrom)
            Assert.assertEquals(
                listOf(createInSampledTransformed(4), createRotateTransformed(45)),
                transformedList
            )
            Assert.assertEquals(
                mapOf("age" to "16"),
                this.extras
            )
        }

        result.newResult {
            addTransformed(createCircleCropTransformed(FILL))
        }.apply {
            Assert.assertNotSame(result, this)
            Assert.assertNotEquals(result, this)
            Assert.assertEquals(bitmap1, bitmap)
            Assert.assertEquals(ImageInfo(3000, 500, "image/png", 0), imageInfo)
            Assert.assertEquals(LOCAL, dataFrom)
            Assert.assertEquals(
                listOf(
                    createInSampledTransformed(4),
                    createRotateTransformed(45),
                    createCircleCropTransformed(FILL)
                ),
                transformedList
            )
            Assert.assertEquals(
                mapOf("age" to "16"),
                this.extras
            )
        }

        result.newResult {
            addExtras("sex", "male")
        }.apply {
            Assert.assertNotSame(result, this)
            Assert.assertNotEquals(result, this)
            Assert.assertEquals(bitmap1, bitmap)
            Assert.assertEquals(ImageInfo(3000, 500, "image/png", 0), imageInfo)
            Assert.assertEquals(LOCAL, dataFrom)
            Assert.assertEquals(
                listOf(createInSampledTransformed(4), createRotateTransformed(45)),
                transformedList
            )
            Assert.assertEquals(
                mapOf("age" to "16", "sex" to "male"),
                this.extras
            )
        }
    }
}