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
package com.github.panpf.sketch.test.datasource

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.panpf.sketch.datasource.AssetDataSource
import com.github.panpf.sketch.datasource.DataFrom
import com.github.panpf.sketch.datasource.FileDataSource
import com.github.panpf.sketch.fetch.newAssetUri
import com.github.panpf.sketch.fetch.newFileUri
import com.github.panpf.sketch.request.LoadRequest
import com.github.panpf.sketch.test.utils.getTestContextAndNewSketch
import com.github.panpf.tools4j.test.ktx.assertThrow
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileNotFoundException

@RunWith(AndroidJUnit4::class)
class FileDataSourceTest {

    @Test
    fun testConstructor() {
        val (context, sketch) = getTestContextAndNewSketch()
        val file = AssetDataSource(
            sketch = sketch,
            request = LoadRequest(context, newAssetUri("sample.jpeg")),
            assetFileName = "sample.jpeg"
        ).getFile()
        val request = LoadRequest(context, newFileUri(file.path))
        FileDataSource(
            sketch = sketch,
            request = request,
            file = file
        ).apply {
            Assert.assertTrue(sketch === this.sketch)
            Assert.assertTrue(request === this.request)
            Assert.assertTrue(file === this.getFile())
            Assert.assertEquals(DataFrom.LOCAL, this.dataFrom)
        }
    }

    @Test
    fun testNewInputStream() {
        val (context, sketch) = getTestContextAndNewSketch()
        val file = AssetDataSource(
            sketch = sketch,
            request = LoadRequest(context, newAssetUri("sample.jpeg")),
            assetFileName = "sample.jpeg"
        ).getFile()
        FileDataSource(
            sketch = sketch,
            request = LoadRequest(context, newFileUri(file.path)),
            file = file
        ).apply {
            newInputStream().close()
        }

        assertThrow(FileNotFoundException::class) {
            FileDataSource(
                sketch = sketch,
                request = LoadRequest(context, newFileUri("/sdcard/not_found.jpeg")),
                file = File("/sdcard/not_found.jpeg")
            ).apply {
                newInputStream()
            }
        }
    }

    @Test
    fun testFile() {
        val (context, sketch) = getTestContextAndNewSketch()
        val file = AssetDataSource(
            sketch = sketch,
            request = LoadRequest(context, newAssetUri("sample.jpeg")),
            assetFileName = "sample.jpeg"
        ).getFile()
        FileDataSource(
            sketch = sketch,
            request = LoadRequest(context, newFileUri(file.path)),
            file = file,
        ).apply {
            val file1 = getFile()
            Assert.assertEquals(file, file1)
        }
    }

    @Test
    fun testToString() {
        val (context, sketch) = getTestContextAndNewSketch()
        val file = AssetDataSource(
            sketch = sketch,
            request = LoadRequest(context, newAssetUri("sample.jpeg")),
            assetFileName = "sample.jpeg"
        ).getFile()
        FileDataSource(
            sketch = sketch,
            request = LoadRequest(context, newFileUri(file.path)),
            file = file
        ).apply {
            Assert.assertEquals(
                "FileDataSource('${file.path}')",
                toString()
            )
        }

        FileDataSource(
            sketch = sketch,
            request = LoadRequest(context, newFileUri("/sdcard/not_found.jpeg")),
            file = File("/sdcard/not_found.jpeg")
        ).apply {
            Assert.assertEquals("FileDataSource('/sdcard/not_found.jpeg')", toString())
        }
    }
}