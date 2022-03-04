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

package com.duckduckgo.feature.toggles.impl.di

import android.content.Context
import androidx.room.Room
import com.duckduckgo.app.global.AppUrl
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.impl.remote.RemoteFeatureToggleConfigService
import com.duckduckgo.feature.toggles.store.remote.RemoteFeatureToggleDatabase
import com.duckduckgo.feature.toggles.store.remote.RemoteFeatureTogglesRepository
import com.duckduckgo.feature.toggles.store.remote.RemoteFeatureTogglesRepositoryImpl
import com.squareup.anvil.annotations.ContributesTo
import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides
import dagger.SingleInstanceIn
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import javax.inject.Named

@Module
@ContributesTo(AppScope::class)
class RemoteFeatureToggleConfigModule {
    @Provides
    @SingleInstanceIn(AppScope::class)
    fun provideRemoteFeatureTogglesRepository(
        context: Context,
        database: RemoteFeatureToggleDatabase
    ): RemoteFeatureTogglesRepository = RemoteFeatureTogglesRepositoryImpl(
        context,
        database
    )
}

@Module
@ContributesTo(AppScope::class)
class NetworkModule {

    @Provides
    @SingleInstanceIn(AppScope::class)
    fun apiRetrofit(@Named("api") okHttpClient: OkHttpClient): RemoteFeatureToggleConfigService {
        val moshi = Moshi.Builder().build()
        val retrofit = Retrofit.Builder()
            .baseUrl(AppUrl.Url.API)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

        return retrofit.create(RemoteFeatureToggleConfigService::class.java)
    }
}

@Module
@ContributesTo(AppScope::class)
class DatabaseModule {
    @Provides
    @SingleInstanceIn(AppScope::class)
    fun providesRemoteFeatureToggleDatabase(context: Context): RemoteFeatureToggleDatabase {
        return Room.databaseBuilder(context, RemoteFeatureToggleDatabase::class.java, "remote_feature_toggle.db")
            .enableMultiInstanceInvalidation()
            .fallbackToDestructiveMigration()
            .build()
    }
}
