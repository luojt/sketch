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
package com.github.panpf.sketch.request.internal

import com.github.panpf.sketch.request.ImageRequest
import com.github.panpf.sketch.request.ImageResult
import com.github.panpf.sketch.request.Listener

class CombinedListener<REQUEST : ImageRequest, SUCCESS_RESULT : ImageResult.Success, ERROR_RESULT : ImageResult.Error>(
    val fromProviderListener: Listener<REQUEST, SUCCESS_RESULT, ERROR_RESULT>,
    val fromBuilderListener: Listener<REQUEST, SUCCESS_RESULT, ERROR_RESULT>?,
) : Listener<REQUEST, SUCCESS_RESULT, ERROR_RESULT> {

    override fun onStart(request: REQUEST) {
        fromProviderListener.onStart(request)
        fromBuilderListener?.onStart(request)
    }

    override fun onCancel(request: REQUEST) {
        fromProviderListener.onCancel(request)
        fromBuilderListener?.onCancel(request)
    }

    override fun onError(request: REQUEST, result: ERROR_RESULT) {
        fromProviderListener.onError(request, result)
        fromBuilderListener?.onError(request, result)
    }

    override fun onSuccess(request: REQUEST, result: SUCCESS_RESULT) {
        fromProviderListener.onSuccess(request, result)
        fromBuilderListener?.onSuccess(request, result)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as CombinedListener<*, *, *>
        if (fromProviderListener != other.fromProviderListener) return false
        if (fromBuilderListener != other.fromBuilderListener) return false
        return true
    }

    override fun hashCode(): Int {
        var result = fromProviderListener.hashCode()
        result = 31 * result + (fromBuilderListener?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String {
        return "CombinedListener(fromProvider=$fromProviderListener, fromBuilder=$fromBuilderListener)"
    }
}