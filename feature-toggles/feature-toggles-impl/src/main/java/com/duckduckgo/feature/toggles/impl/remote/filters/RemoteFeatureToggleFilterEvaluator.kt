/*
 * Copyright (c) 2022 DuckDuckGo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.duckduckgo.feature.toggles.impl.remote.filters

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.impl.remote.models.Filters
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface RemoteFeatureToggleFilterEvaluator {
    fun evaluate(filters: List<Filters>?): Boolean
}

@ContributesBinding(AppScope::class)
class DefaultRemoteFeatureToggleFilterEvaluator @Inject constructor(
    private val filterEvaluators: Map<String, @JvmSuppressWildcards RemoteFeatureToggleFilter>
) : RemoteFeatureToggleFilterEvaluator {

    override fun evaluate(filters: List<Filters>?): Boolean {
        if (filters.isNullOrEmpty()) return true
        filters.forEach {
            if (filterEvaluators.containsKey(it.key)) {
                val passesFilter = filterEvaluators[it.key]!!.handle(it)
                if (!passesFilter) return passesFilter
            }
            // else it mean we don't have a handling for it then we ignore
        }
        return true
    }
}
