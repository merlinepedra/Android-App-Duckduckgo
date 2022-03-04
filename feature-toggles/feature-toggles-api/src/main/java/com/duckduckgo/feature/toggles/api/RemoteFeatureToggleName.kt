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

package com.duckduckgo.feature.toggles.api

sealed class RemoteFeatureToggleName(override val value: String) : FeatureName {
    data class FeatureFullEnabled(override val value: String = "feature_full_enabled") : RemoteFeatureToggleName(value)
    data class FeaturePartlyEnabled(override val value: String = "feature_partly_enabled") : RemoteFeatureToggleName(value)
    data class FeatureNotEnabled(override val value: String = "feature_not_enabled") : RemoteFeatureToggleName(value)
}
