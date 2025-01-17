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

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleObserver
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.panpf.sketch.request.GlobalLifecycle
import com.github.panpf.sketch.request.isSketchGlobalLifecycle
import com.github.panpf.tools4j.test.ktx.assertThrow
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GlobalLifecycleTest {

    @Test
    fun test() {
        GlobalLifecycle.apply {
            Assert.assertEquals(Lifecycle.State.RESUMED, currentState)
            Assert.assertEquals("GlobalLifecycle", toString())

            val observer = LifecycleEventObserver { owner, _ ->
                Assert.assertSame(GlobalLifecycle, owner.lifecycle)
            }
            addObserver(observer)
            removeObserver(observer)

            assertThrow(IllegalArgumentException::class) {
                addObserver(object : LifecycleObserver {})
            }

            Assert.assertTrue(isSketchGlobalLifecycle())
        }
    }
}