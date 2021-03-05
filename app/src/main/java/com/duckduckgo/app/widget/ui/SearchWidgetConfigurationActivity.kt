/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.app.widget.ui

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import androidx.core.content.edit
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.global.DuckDuckGoActivity
import kotlinx.android.synthetic.main.content_search_widget_configuration.*
import timber.log.Timber

class SearchWidgetConfigurationActivity : DuckDuckGoActivity() {

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_search_widget_configuration)

        widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        Timber.i("Configuring widget $widgetId")

        configureButtonListeners()
    }

    private fun configureButtonListeners() {

        themeSelectionDark.setOnClickListener { exitConfiguration("dark") }
        themeSelectionLight.setOnClickListener { exitConfiguration("light") }
        themeSelectionWild.setOnClickListener { exitConfiguration("wild") }
        themeSelectionTranslucent.setOnClickListener { exitConfiguration("translucent") }

    }

    private fun exitConfiguration(theme: String) {
        Timber.i("Ending widget configuration. Theme: $theme for widget $widgetId")

        updateWidgetThemePreference(this, widgetId, theme)

        val widgetUpdateIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
        val widgetsToUpdate = IntArray(1).also { it[0] = widgetId }
        widgetUpdateIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, widgetsToUpdate)
        sendBroadcast(widgetUpdateIntent)

        val returnIntent = Intent()
        returnIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, IntArray(1).also { it[0] = widgetId })
        setResult(RESULT_OK, returnIntent)
        finish()
    }

    companion object {

        private const val SHARED_PREFS_FILENAME = "SearchWidgetPrefs"
        const val SHARED_PREFS_LIGHT_THEME_KEY = "SearchWidgetLightTheme"


        fun intent(context: Context): Intent {
            return Intent(context, SearchWidgetConfigurationActivity::class.java)
        }

        fun widgetPreferences(context: Context): SharedPreferences {
            return context.getSharedPreferences(SHARED_PREFS_FILENAME, Context.MODE_PRIVATE)
        }

        fun widgetThemePreference(context: Context, widgetId: Int): String? {
            val prefs = widgetPreferences(context)
            val key = keyForWidgetTheme(widgetId)
            if (!prefs.contains(key)) return null
            return prefs.getString(key, "dark")
        }

        fun updateWidgetThemePreference(context: Context, widgetId: Int, theme: String) {
            widgetPreferences(context).edit {
                val key = keyForWidgetTheme(widgetId)
                Timber.i("Updating widget preference. $key=$theme")
                putString(key, theme)
            }
        }

        private fun keyForWidgetTheme(widgetId: Int): String {
            return "$SHARED_PREFS_LIGHT_THEME_KEY-$widgetId"
        }
    }

}