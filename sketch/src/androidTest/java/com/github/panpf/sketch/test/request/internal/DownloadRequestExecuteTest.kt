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
import com.github.panpf.sketch.request.Depth.LOCAL
import com.github.panpf.sketch.request.Depth.MEMORY
import com.github.panpf.sketch.request.Depth.NETWORK
import com.github.panpf.sketch.request.DepthException
import com.github.panpf.sketch.request.DownloadRequest
import com.github.panpf.sketch.request.DownloadResult
import com.github.panpf.sketch.request.GlobalLifecycle
import com.github.panpf.sketch.request.get
import com.github.panpf.sketch.test.utils.DownloadListenerSupervisor
import com.github.panpf.sketch.test.utils.DownloadProgressListenerSupervisor
import com.github.panpf.sketch.test.utils.TestDownloadTarget
import com.github.panpf.sketch.test.utils.TestErrorBitmapDecoder.Factory
import com.github.panpf.sketch.test.utils.TestErrorDrawableDecoder
import com.github.panpf.sketch.test.utils.TestHttpFetcherFactory
import com.github.panpf.sketch.test.utils.TestHttpStack
import com.github.panpf.sketch.test.utils.TestRequestInterceptor
import com.github.panpf.sketch.test.utils.getTestContext
import com.github.panpf.sketch.test.utils.getTestContextAndNewSketch
import com.github.panpf.sketch.test.utils.newSketch
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
class DownloadRequestExecuteTest {

    @Test
    fun testDepth() {
        val context = getTestContext()
        val sketch = newSketch {
            httpStack(TestHttpStack(context))
        }
        val testImage = TestHttpStack.testImages.first()
        val imageUri = testImage.uriString

        // default
        sketch.downloadCache.clear()
        sketch.memoryCache.clear()
        DownloadRequest(context, imageUri) {
            resultCachePolicy(DISABLED)
        }.let {
            runBlocking { sketch.execute(it) }
        }.asOrNull<DownloadResult.Success>()!!.apply {
            Assert.assertEquals(DataFrom.NETWORK, dataFrom)
        }

        // NETWORK
        sketch.downloadCache.clear()
        sketch.memoryCache.clear()
        DownloadRequest(context, imageUri) {
            resultCachePolicy(DISABLED)
            depth(NETWORK)
        }.let {
            runBlocking { sketch.execute(it) }
        }.asOrNull<DownloadResult.Success>()!!.apply {
            Assert.assertEquals(DataFrom.NETWORK, dataFrom)
        }

        // LOCAL
        sketch.downloadCache.clear()
        sketch.memoryCache.clear()
        runBlocking {
            sketch.execute(DownloadRequest(context, imageUri) {
                resultCachePolicy(DISABLED)
            })
        }
        sketch.memoryCache.clear()
        Assert.assertTrue(sketch.downloadCache.exist(imageUri))
        DownloadRequest(context, imageUri) {
            resultCachePolicy(DISABLED)
            depth(LOCAL)
        }.let {
            runBlocking { sketch.execute(it) }
        }.asOrNull<DownloadResult.Success>()!!.apply {
            Assert.assertEquals(DataFrom.DOWNLOAD_CACHE, dataFrom)
        }

        sketch.downloadCache.clear()
        sketch.memoryCache.clear()
        DownloadRequest(context, imageUri) {
            resultCachePolicy(DISABLED)
            depth(LOCAL)
        }.let {
            runBlocking { sketch.execute(it) }
        }.asOrNull<DownloadResult.Error>()!!.apply {
            Assert.assertTrue(throwable is DepthException)
        }

        // MEMORY
        sketch.memoryCache.clear()
        runBlocking {
            sketch.execute(DownloadRequest(context, imageUri) {
                resultCachePolicy(DISABLED)
            })
        }
        DownloadRequest(context, imageUri) {
            resultCachePolicy(DISABLED)
            depth(MEMORY)
        }.let {
            runBlocking { sketch.execute(it) }
        }.asOrNull<DownloadResult.Success>()!!.apply {
            Assert.assertEquals(DataFrom.DOWNLOAD_CACHE, dataFrom)
        }

        sketch.memoryCache.clear()
        DownloadRequest(context, imageUri) {
            resultCachePolicy(DISABLED)
            depth(MEMORY)
        }.let {
            runBlocking { sketch.execute(it) }
        }.asOrNull<DownloadResult.Error>().apply {
            Assert.assertNull(this)
        }
    }

    @Test
    fun testDownloadCachePolicy() {
        val context = getTestContext()
        val sketch = newSketch {
            httpStack(TestHttpStack(context))
        }
        val testImage = TestHttpStack.testImages.first()
        val imageUri = testImage.uriString
        val diskCache = sketch.downloadCache

        /* ENABLED */
        diskCache.clear()
        Assert.assertFalse(diskCache.exist(imageUri))
        DownloadRequest(context, imageUri) {
            memoryCachePolicy(DISABLED)
            resultCachePolicy(DISABLED)
            downloadCachePolicy(ENABLED)
        }.let {
            runBlocking { sketch.execute(it) }
        }.asOrNull<DownloadResult.Success>()!!.apply {
            Assert.assertEquals(DataFrom.NETWORK, dataFrom)
        }

        Assert.assertTrue(diskCache.exist(imageUri))
        DownloadRequest(context, imageUri) {
            memoryCachePolicy(DISABLED)
            resultCachePolicy(DISABLED)
            downloadCachePolicy(ENABLED)
        }.let {
            runBlocking { sketch.execute(it) }
        }.asOrNull<DownloadResult.Success>()!!.apply {
            Assert.assertEquals(DataFrom.DOWNLOAD_CACHE, dataFrom)
        }

        /* DISABLED */
        diskCache.clear()
        Assert.assertFalse(diskCache.exist(imageUri))
        DownloadRequest(context, imageUri) {
            memoryCachePolicy(DISABLED)
            resultCachePolicy(DISABLED)
            downloadCachePolicy(DISABLED)
        }.let {
            runBlocking { sketch.execute(it) }
        }.asOrNull<DownloadResult.Success>()!!.apply {
            Assert.assertEquals(DataFrom.NETWORK, dataFrom)
        }

        Assert.assertFalse(diskCache.exist(imageUri))
        DownloadRequest(context, imageUri) {
            memoryCachePolicy(DISABLED)
            resultCachePolicy(DISABLED)
            downloadCachePolicy(DISABLED)
        }.let {
            runBlocking { sketch.execute(it) }
        }.asOrNull<DownloadResult.Success>()!!.apply {
            Assert.assertEquals(DataFrom.NETWORK, dataFrom)
        }

        /* READ_ONLY */
        diskCache.clear()
        Assert.assertFalse(diskCache.exist(imageUri))
        DownloadRequest(context, imageUri) {
            memoryCachePolicy(DISABLED)
            resultCachePolicy(DISABLED)
            downloadCachePolicy(READ_ONLY)
        }.let {
            runBlocking { sketch.execute(it) }
        }.asOrNull<DownloadResult.Success>()!!.apply {
            Assert.assertEquals(DataFrom.NETWORK, dataFrom)
        }

        Assert.assertFalse(diskCache.exist(imageUri))
        DownloadRequest(context, imageUri) {
            memoryCachePolicy(DISABLED)
            resultCachePolicy(DISABLED)
            downloadCachePolicy(READ_ONLY)
        }.let {
            runBlocking { sketch.execute(it) }
        }.asOrNull<DownloadResult.Success>()!!.apply {
            Assert.assertEquals(DataFrom.NETWORK, dataFrom)
        }

        Assert.assertFalse(diskCache.exist(imageUri))
        DownloadRequest(context, imageUri) {
            memoryCachePolicy(DISABLED)
            resultCachePolicy(DISABLED)
            downloadCachePolicy(ENABLED)
        }.let {
            runBlocking { sketch.execute(it) }
        }
        Assert.assertTrue(diskCache.exist(imageUri))
        DownloadRequest(context, imageUri) {
            memoryCachePolicy(DISABLED)
            resultCachePolicy(DISABLED)
            downloadCachePolicy(READ_ONLY)
        }.let {
            runBlocking { sketch.execute(it) }
        }.asOrNull<DownloadResult.Success>()!!.apply {
            Assert.assertEquals(DataFrom.DOWNLOAD_CACHE, dataFrom)
        }

        /* WRITE_ONLY */
        diskCache.clear()
        Assert.assertFalse(diskCache.exist(imageUri))
        DownloadRequest(context, imageUri) {
            memoryCachePolicy(DISABLED)
            resultCachePolicy(DISABLED)
            downloadCachePolicy(WRITE_ONLY)
        }.let {
            runBlocking { sketch.execute(it) }
        }.asOrNull<DownloadResult.Success>()!!.apply {
            Assert.assertEquals(DataFrom.NETWORK, dataFrom)
        }

        Assert.assertTrue(diskCache.exist(imageUri))
        DownloadRequest(context, imageUri) {
            memoryCachePolicy(DISABLED)
            resultCachePolicy(DISABLED)
            downloadCachePolicy(WRITE_ONLY)
        }.let {
            runBlocking { sketch.execute(it) }
        }.asOrNull<DownloadResult.Success>()!!.apply {
            Assert.assertEquals(DataFrom.NETWORK, dataFrom)
        }
    }

    @Test
    fun testListener() {
        val context = getTestContext()
        val sketch = newSketch {
            httpStack(TestHttpStack(context))
        }
        val testImage = TestHttpStack.testImages.first()
        val imageUri = testImage.uriString

        DownloadListenerSupervisor().let { listenerSupervisor ->
            Assert.assertEquals(listOf<String>(), listenerSupervisor.callbackActionList)

            DownloadRequest(context, imageUri) {
                listener(listenerSupervisor)
            }.let { request ->
                runBlocking { sketch.execute(request) }
            }
            Assert.assertEquals(
                listOf("onStart", "onSuccess"),
                listenerSupervisor.callbackActionList
            )
        }

        DownloadListenerSupervisor().let { listenerSupervisor ->
            Assert.assertEquals(listOf<String>(), listenerSupervisor.callbackActionList)

            DownloadRequest(context, "$imageUri.fake") {
                listener(listenerSupervisor)
            }.let { request ->
                runBlocking { sketch.execute(request) }
            }
            Assert.assertEquals(listOf("onStart", "onError"), listenerSupervisor.callbackActionList)
        }

        var deferred: Deferred<DownloadResult>? = null
        val listenerSupervisor = DownloadListenerSupervisor {
            deferred?.cancel()
        }
        DownloadRequest(context, imageUri) {
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

        DownloadProgressListenerSupervisor().let { listenerSupervisor ->
            Assert.assertEquals(listOf<String>(), listenerSupervisor.callbackActionList)

            DownloadRequest(context, testImage.uriString) {
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
        val (context, sketch) = getTestContextAndNewSketch {
            httpStack(TestHttpStack(it))
        }

        DownloadRequest(context, TestHttpStack.testImages.first().uriString)
            .let { runBlocking { it.execute(sketch) } }.asOrThrow<DownloadResult.Success>().apply {
                Assert.assertNull(request.parameters?.get("TestRequestInterceptor"))
            }
        DownloadRequest(context, TestHttpStack.testImages.first().uriString) {
            components {
                addRequestInterceptor(TestRequestInterceptor())
            }
        }.let { runBlocking { it.execute(sketch) } }.asOrThrow<DownloadResult.Success>().apply {
            Assert.assertEquals("true", request.parameters?.get("TestRequestInterceptor"))
        }

//        DownloadRequest(context, TestHttpStack.testImages.first().uriString) {
//            downloadCachePolicy(DISABLED)
//        }.let { runBlocking { it.execute(sketch) } }.asOrThrow<DownloadResult.Success>().apply {
//            Assert.assertFalse(transformedList?.contains("TestBitmapDecodeInterceptor") == true)
//        }
//        DownloadRequest(context, TestHttpStack.testImages.first().uriString) {
//            downloadCachePolicy(DISABLED)
//            components {
//                addBitmapDecodeInterceptor(TestBitmapDecodeInterceptor())
//            }
//        }.let { runBlocking { it.execute(sketch) } }.asOrThrow<DownloadResult.Success>().apply {
//            Assert.assertTrue(transformedList?.contains("TestBitmapDecodeInterceptor") == true)
//        }
//
//        DownloadRequest(context, TestHttpStack.testImages.first().uriString) {
//            downloadCachePolicy(DISABLED)
//        }.let { runBlocking { it.execute(sketch) } }.asOrThrow<DownloadResult.Success>().apply {
//            Assert.assertFalse(transformedList?.contains("TestDrawableDecodeInterceptor") == true)
//        }
//        DownloadRequest(context, TestHttpStack.testImages.first().uriString) {
//            downloadCachePolicy(DISABLED)
//            components {
//                addDrawableDecodeInterceptor(TestDrawableDecodeInterceptor())
//            }
//        }.let { runBlocking { it.execute(sketch) } }.asOrThrow<DownloadResult.Success>().apply {
//            Assert.assertFalse(transformedList?.contains("TestDrawableDecodeInterceptor") == true)
//        }

        DownloadRequest(
            context,
            TestHttpStack.testImages.first().uriString.replace("http://", "test://")
        ) {
            downloadCachePolicy(DISABLED)
        }.let { runBlocking { it.execute(sketch) } }.apply {
            Assert.assertTrue(this is DownloadResult.Error)
        }
        DownloadRequest(
            context,
            TestHttpStack.testImages.first().uriString.replace("http://", "test://")
        ) {
            downloadCachePolicy(DISABLED)
            components {
                addFetcher(TestHttpFetcherFactory())
            }
        }.let { runBlocking { it.execute(sketch) } }.apply {
            Assert.assertTrue(this is DownloadResult.Success)
        }

        DownloadRequest(context, TestHttpStack.testImages.first().uriString) {
            downloadCachePolicy(DISABLED)
        }.let { runBlocking { it.execute(sketch) } }.apply {
            Assert.assertTrue(this is DownloadResult.Success)
        }
        DownloadRequest(context, TestHttpStack.testImages.first().uriString) {
            downloadCachePolicy(DISABLED)
            components {
                addBitmapDecoder(Factory())
            }
        }.let { runBlocking { it.execute(sketch) } }.apply {
            Assert.assertTrue(this is DownloadResult.Success)
        }

        DownloadRequest(context, TestHttpStack.testImages.first().uriString) {
            downloadCachePolicy(DISABLED)
        }.let { runBlocking { it.execute(sketch) } }.apply {
            Assert.assertTrue(this is DownloadResult.Success)
        }
        DownloadRequest(context, TestHttpStack.testImages.first().uriString) {
            downloadCachePolicy(DISABLED)
            components {
                addDrawableDecoder(TestErrorDrawableDecoder.Factory())
            }
        }.let { runBlocking { it.execute(sketch) } }.apply {
            Assert.assertTrue(this is DownloadResult.Success)
        }
    }

    @Test
    fun testTarget() {
        val context = getTestContext()
        val sketch = newSketch {
            httpStack(TestHttpStack(context))
        }
        val testImage = TestHttpStack.testImages.first()
        val imageUri = testImage.uriString


        TestDownloadTarget().let { testTarget ->
            Assert.assertNull(testTarget.start)
            Assert.assertNull(testTarget.downloadData)
            Assert.assertNull(testTarget.throwable)
        }

        TestDownloadTarget().let { testTarget ->
            DownloadRequest(context, imageUri) {
                target(testTarget)
            }.let { request ->
                runBlocking { sketch.execute(request) }
            }
            Assert.assertNotNull(testTarget.start)
            Assert.assertNotNull(testTarget.downloadData)
            Assert.assertNull(testTarget.throwable)
        }

        TestDownloadTarget().let { testTarget ->
            DownloadRequest(context, "$imageUri.fake") {
                target(testTarget)
            }.let { request ->
                runBlocking { sketch.execute(request) }
            }
            Assert.assertNotNull(testTarget.start)
            Assert.assertNull(testTarget.downloadData)
            Assert.assertNotNull(testTarget.throwable)
        }

        TestDownloadTarget().let { testTarget ->
            var deferred: Deferred<DownloadResult>? = null
            val listenerSupervisor = DownloadListenerSupervisor {
                deferred?.cancel()
            }
            DownloadRequest(context, imageUri) {
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
            Assert.assertNotNull(testTarget.start)
            Assert.assertNull(testTarget.downloadData)
            Assert.assertNull(testTarget.throwable)
        }

        TestDownloadTarget().let { testTarget ->
            var deferred: Deferred<DownloadResult>? = null
            val listenerSupervisor = DownloadListenerSupervisor {
                deferred?.cancel()
            }
            DownloadRequest(context, "$imageUri.fake") {
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
            Assert.assertNotNull(testTarget.start)
            Assert.assertNull(testTarget.downloadData)
            Assert.assertNull(testTarget.throwable)
        }
    }

    @Test
    fun testLifecycle() {
        val context = getTestContext()
        val sketch = newSketch {
            httpStack(TestHttpStack(context))
        }
        val testImage = TestHttpStack.testImages.first()
        val imageUri = testImage.uriString

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

        DownloadRequest(context, imageUri).let { request ->
            Assert.assertSame(GlobalLifecycle, request.lifecycle)
            runBlocking {
                sketch.execute(request)
            }
        }.apply {
            Assert.assertTrue(this is DownloadResult.Success)
        }

        DownloadRequest(context, imageUri) {
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
            Assert.assertTrue(this is DownloadResult.Success)
        }
    }

    @Test
    fun testExecuteAndEnqueue() {
        val context = getTestContext()
        val sketch = newSketch {
            httpStack(TestHttpStack(context))
        }

        DownloadRequest(context, TestHttpStack.testImages.first().uriString).let { request ->
            runBlocking { request.execute(sketch) }
        }.apply {
            Assert.assertTrue(this is DownloadResult.Success)
        }

        DownloadRequest(context, TestHttpStack.testImages.first().uriString).let { request ->
            runBlocking { request.enqueue(sketch).job.await() }
        }.apply {
            Assert.assertTrue(this is DownloadResult.Success)
        }
    }
}