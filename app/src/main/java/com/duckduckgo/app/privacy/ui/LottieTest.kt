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

package com.duckduckgo.app.privacy.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.ActivityLottieTestBinding
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.app.global.baseHost
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.mobile.android.ui.view.getColorFromAttr
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding
import timber.log.Timber
import java.util.*

@InjectWith(ActivityScope::class)
class LottieTest : DuckDuckGoActivity() {

    private val binding: ActivityLottieTestBinding by viewBinding()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        lifecycleScope.launchWhenCreated {
            val animation = binding.badgeIcon.animation
            binding.trackersBlocked.setAnimation(R.raw.dark_trackers)
            /*binding.trackersBlocked.addLottieOnCompositionLoadedListener {
                val im0 = it.images.get("image_0")
                val im1 = it.images.get("image_1")
                val im2 = it.images.get("image_2")
                im0?.bitmap = ContextCompat.getDrawable(this@LottieTest, R.drawable.network_logo_amazon_technologies_inc)!!.toBitmap()
                im1?.bitmap = ContextCompat.getDrawable(this@LottieTest, R.drawable.network_logo_amazon_technologies_inc)!!.toBitmap()
                im2?.bitmap = ContextCompat.getDrawable(this@LottieTest, R.drawable.network_logo_amazon_technologies_inc)!!.toBitmap()
                it.images["image_0"] = im0
                it.images["image_1"] = im1
                it.images["image_2"] = im2

            }*/
            binding.trackersBlocked.setImageAssetDelegate { asset ->
                Timber.i("Lottie: ${asset?.id} ${asset?.fileName}")
                val generateDefaultDrawable = generateDefaultDrawable(this@LottieTest, "amazon.com")
                // ContextCompat.getDrawable(this@LottieTest, R.drawable.network_logo_amazon)!!.toBitmap(24, 24)
                generateDefaultDrawable.toBitmap(24, 24)
            }
            binding.badgeIcon.setAnimation(R.raw.shield)
            binding.badgeIcon.playAnimation()
            binding.trackersBlocked.playAnimation()

            val toBitmap = ContextCompat.getDrawable(this@LottieTest, R.drawable.network_logo_amazon_technologies_inc)!!.toBitmap(24, 24)
            binding.demoImg.setImageBitmap(toBitmap)
        }
    }

    fun generateDefaultDrawable(
        context: Context,
        domain: String
    ): Drawable {
        return object : Drawable() {
            private val baseHost: String = domain.toUri().baseHost ?: ""

            private val letter
                get() = baseHost.firstOrNull()?.toString()?.toUpperCase(Locale.getDefault()) ?: ""

            private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = context.getColorFromAttr(com.duckduckgo.mobile.android.R.attr.toolbarIconColor)
                // color = ContextCompat.getColor(context, com.duckduckgo.mobile.android.R.color.red50)
            }

            private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = context.getColorFromAttr(com.duckduckgo.mobile.android.R.attr.omnibarRoundedFieldBackgroundColor)
                typeface = Typeface.SANS_SERIF
            }

            override fun draw(canvas: Canvas) {
                val centerX = bounds.width() * 0.5f
                val centerY = bounds.height() * 0.5f
                textPaint.textSize = (bounds.width() / 2).toFloat()
                val textWidth: Float = textPaint.measureText(letter) * 0.5f
                val textBaseLineHeight = textPaint.fontMetrics.ascent * -0.4f
                canvas.drawCircle(centerX, centerY, centerX, backgroundPaint)
                canvas.drawText(letter, centerX - textWidth, centerY + textBaseLineHeight, textPaint)
            }

            override fun setAlpha(alpha: Int) {
            }

            override fun setColorFilter(colorFilter: ColorFilter?) {
            }

            override fun getOpacity(): Int {
                return PixelFormat.TRANSPARENT
            }
        }
    }
}
