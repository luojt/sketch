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
package com.github.panpf.sketch.test

import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup.LayoutParams
import android.widget.ImageView
import androidx.fragment.app.FragmentActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.panpf.sketch.cache.CachePolicy.DISABLED
import com.github.panpf.sketch.displayAssetImage
import com.github.panpf.sketch.displayImage
import com.github.panpf.sketch.displayResourceImage
import com.github.panpf.sketch.displayResult
import com.github.panpf.sketch.disposeDisplay
import com.github.panpf.sketch.request.DisplayResult
import com.github.panpf.sketch.test.utils.DelayTransformation
import com.github.panpf.sketch.test.utils.ExifOrientationTestFileHelper
import com.github.panpf.sketch.test.utils.TestAssets
import com.github.panpf.sketch.test.utils.getTestContext
import com.github.panpf.tools4a.test.ktx.getActivitySync
import com.github.panpf.tools4a.test.ktx.launchActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ImageViewExtensionsTest {

    @Test
    fun testDisplayImage() {
        val context = getTestContext()

        val activity = TestActivity::class.launchActivity().getActivitySync()
        val imageView = activity.imageView

        Assert.assertNull(imageView.drawable)
        runBlocking {
            imageView.displayImage(TestAssets.SAMPLE_JPEG_URI).job.join()
        }
        Assert.assertNotNull(imageView.drawable)

        runBlocking(Dispatchers.Main) {
            imageView.setImageDrawable(null)
        }
        Assert.assertNull(imageView.drawable)
        runBlocking {
            imageView.displayImage(Uri.parse(TestAssets.SAMPLE_PNG_URI)).job.join()
        }
        Assert.assertNotNull(imageView.drawable)
        runBlocking(Dispatchers.Main) {
            imageView.setImageDrawable(null)
        }
        Assert.assertNull(imageView.drawable)
        runBlocking {
            imageView.displayImage(null as Uri?).job.join()
        }
        Assert.assertNull(imageView.drawable)

        runBlocking(Dispatchers.Main) {
            imageView.setImageDrawable(null)
        }
        Assert.assertNull(imageView.drawable)
        runBlocking {
            imageView.displayImage(R.drawable.ic_launcher).job.join()
        }
        Assert.assertNotNull(imageView.drawable)
        runBlocking(Dispatchers.Main) {
            imageView.setImageDrawable(null)
        }
        Assert.assertNull(imageView.drawable)
        runBlocking {
            imageView.displayImage(null as Int?).job.join()
        }
        Assert.assertNull(imageView.drawable)

        runBlocking(Dispatchers.Main) {
            imageView.setImageDrawable(null)
        }
        Assert.assertNull(imageView.drawable)
        runBlocking {
            imageView.displayResourceImage(R.drawable.test).job.join()
        }
        Assert.assertNotNull(imageView.drawable)
        runBlocking(Dispatchers.Main) {
            imageView.setImageDrawable(null)
        }
        Assert.assertNull(imageView.drawable)
        runBlocking {
            imageView.displayResourceImage(null).job.join()
        }
        Assert.assertNull(imageView.drawable)

        runBlocking(Dispatchers.Main) {
            imageView.setImageDrawable(null)
        }
        Assert.assertNull(imageView.drawable)
        runBlocking {
            imageView.displayResourceImage(context.packageName, R.drawable.test).job.join()
        }
        Assert.assertNotNull(imageView.drawable)

        runBlocking(Dispatchers.Main) {
            imageView.setImageDrawable(null)
        }
        Assert.assertNull(imageView.drawable)
        runBlocking {
            imageView.displayAssetImage("sample_anim.gif").job.join()
        }
        Assert.assertNotNull(imageView.drawable)
        runBlocking(Dispatchers.Main) {
            imageView.setImageDrawable(null)
        }
        Assert.assertNull(imageView.drawable)
        runBlocking {
            imageView.displayAssetImage(null).job.join()
        }
        Assert.assertNull(imageView.drawable)

        runBlocking(Dispatchers.Main) {
            imageView.setImageDrawable(null)
        }
        Assert.assertNull(imageView.drawable)
        val file = ExifOrientationTestFileHelper(context, "exif_origin_clock_hor.jpeg", 2).files()
            .first().file
        runBlocking {
            imageView.displayImage(file).job.join()
        }
        Assert.assertNotNull(imageView.drawable)
        runBlocking(Dispatchers.Main) {
            imageView.setImageDrawable(null)
        }
        Assert.assertNull(imageView.drawable)
        runBlocking {
            imageView.displayImage(null as File?).job.join()
        }
        Assert.assertNull(imageView.drawable)

        runBlocking(Dispatchers.Main) {
            imageView.setImageDrawable(null)
        }
        Assert.assertNull(imageView.drawable)
        runBlocking {
            imageView.displayImage(null as String?).job.join()
        }
        Assert.assertNull(imageView.drawable)
    }

    @Test
    fun testDisposeDisplay() {
        val activity = TestActivity::class.launchActivity().getActivitySync()
        val imageView = activity.imageView

        Assert.assertNull(imageView.drawable)
        runBlocking {
            imageView.displayImage(TestAssets.SAMPLE_JPEG_URI).job.join()
        }
        Assert.assertNotNull(imageView.drawable)

        runBlocking(Dispatchers.Main) {
            imageView.setImageDrawable(null)
        }
        Assert.assertNull(imageView.drawable)
        runBlocking {
            imageView.displayImage(TestAssets.SAMPLE_PNG_URI) {
                resultCachePolicy(DISABLED)
                memoryCachePolicy(DISABLED)
                addTransformations(DelayTransformation {
                    imageView.disposeDisplay()
                })
            }.job.join()
        }
        Assert.assertNull(imageView.drawable)
    }

    @Test
    fun testDisplayResult() {
        val activity = TestActivity::class.launchActivity().getActivitySync()
        val imageView = activity.imageView

        Assert.assertNull(imageView.displayResult)

        runBlocking {
            imageView.displayImage(TestAssets.SAMPLE_JPEG_URI).job.join()
        }
        Assert.assertTrue(imageView.displayResult is DisplayResult.Success)

        runBlocking {
            imageView.displayImage("asset://fake.jpeg").job.join()
        }
        Assert.assertTrue(imageView.displayResult is DisplayResult.Error)

        runBlocking {
            imageView.displayImage(TestAssets.SAMPLE_PNG_URI) {
                resultCachePolicy(DISABLED)
                memoryCachePolicy(DISABLED)
                addTransformations(DelayTransformation {
                    imageView.disposeDisplay()
                })
            }.job.join()
        }
        Assert.assertNull(imageView.displayResult)
    }

    class TestActivity : FragmentActivity() {

        lateinit var imageView: ImageView

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            imageView = ImageView(this)
            setContentView(imageView, LayoutParams(500, 500))
        }
    }
}