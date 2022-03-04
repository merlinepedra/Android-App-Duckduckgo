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

package com.duckduckgo.feature.toggles.store.remote

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

interface RemoteFeatureTogglesRepository {
    fun get(
        featureName: String,
        defaultValue: Boolean
    ): Boolean?

    fun getFeature(
        featureName: String
    ): RemoteFeatureToggle?

    fun getAllFeatures(): List<RemoteFeatureToggle>

    fun insert(toggle: RemoteFeatureToggle)

    fun configVersion(): Int

    fun updateConfigVersion(version: Int)
}

data class RemoteFeatureToggle(
    val featureName: String,
    val rolloutToPercentage: Int,
    val isEnabled: Boolean
)

class RemoteFeatureTogglesRepositoryImpl constructor(
    private val context: Context,
    database: RemoteFeatureToggleDatabase
) : RemoteFeatureTogglesRepository {

    companion object {
        const val FILENAME = "com.duckduckgo.feature.toggles.store.remote"
        const val KEY_REMOTE_FEATURE_TOGGLE_VERSION = "key.remote.feature.toggle.version"
    }

    private val preferences: SharedPreferences
        get() = context.getSharedPreferences(FILENAME, Context.MODE_PRIVATE)

    private val dao = database.remoteFeatureToggleDao()

    override fun get(
        featureName: String,
        defaultValue: Boolean
    ): Boolean? = dao.findFeature(featureName)?.run {
        isEnabled && isActive
    }

    override fun getFeature(featureName: String): RemoteFeatureToggle? = dao.findFeature(featureName)?.toToggle()

    override fun getAllFeatures(): List<RemoteFeatureToggle> = dao.getAll().map {
        RemoteFeatureToggle(it.featureName, it.rolloutToPercentage, it.isEnabled)
    }

    override fun insert(toggle: RemoteFeatureToggle) {
        dao.insert(toggle.toEntity())
    }

    override fun configVersion(): Int = preferences.getInt(KEY_REMOTE_FEATURE_TOGGLE_VERSION, 0)

    override fun updateConfigVersion(version: Int) {
        preferences.edit { putInt(KEY_REMOTE_FEATURE_TOGGLE_VERSION, version) }
    }

    private fun RemoteFeatureToggleEntity.toToggle() = RemoteFeatureToggle(
        featureName,
        rolloutToPercentage,
        isEnabled
    )

    private fun RemoteFeatureToggle.toEntity() = RemoteFeatureToggleEntity(
        featureName,
        rolloutToPercentage,
        isEnabled,
        rolloutToPercentage > 0
    )
}
