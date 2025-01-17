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
package com.github.panpf.sketch.sample.widget

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.github.panpf.sketch.sample.prefsService
import com.github.panpf.sketch.sample.util.lifecycleOwner
import com.github.panpf.sketch.util.PauseLoadWhenScrollingMixedScrollListener
import kotlinx.coroutines.launch

@SuppressLint("NotifyDataSetChanged")
class MyRecyclerView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : RecyclerView(context, attrs) {

    private val scrollListener = PauseLoadWhenScrollingMixedScrollListener()

    override fun onAttachedToWindow() {
        /* Must be in onAttachedToWindow */
        /* Must be before super.onAttachedToWindow() */
        lifecycleOwner.lifecycleScope.launch {
            prefsService.pauseLoadWhenScrollInList.stateFlow.collect {
                setEnabledPauseLoadWhenScrolling(it)
            }
        }
        super.onAttachedToWindow()
    }

    private fun setEnabledPauseLoadWhenScrolling(enabled: Boolean) {
        if (enabled) {
            addOnScrollListener(scrollListener)
        } else {
            removeOnScrollListener(scrollListener)
        }
    }
}