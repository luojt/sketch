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
package com.github.panpf.sketch.test.request

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.panpf.sketch.datasource.DataFrom.MEMORY
import com.github.panpf.sketch.request.DownloadData
import com.github.panpf.sketch.request.DownloadRequest
import com.github.panpf.sketch.request.DownloadResult
import com.github.panpf.sketch.test.utils.getTestContext
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DownloadResultTest {

    @Test
    fun test() {
        val context = getTestContext()
        val request1 = DownloadRequest(context, "http://sample.com/sample.jpeg")

        DownloadResult.Success(request1, DownloadData(byteArrayOf(), MEMORY)).apply {
            Assert.assertSame(request1, request)
            Assert.assertNotNull(data)
            Assert.assertEquals(MEMORY, dataFrom)
        }

        DownloadResult.Error(request1, Exception("")).apply {
            Assert.assertSame(request1, request)
            Assert.assertTrue(throwable is Exception)
        }
    }
}