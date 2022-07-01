/*
 * Copyright (c) 2019 DuckDuckGo
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

package com.duckduckgo.app.browser

import android.animation.Animator
import android.animation.Animator.AnimatorListener
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.animation.addListener
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.children
import androidx.core.widget.TextViewCompat
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat
import com.airbnb.lottie.LottieAnimationView
import com.duckduckgo.app.browser.TrackerLogo.ImageLogo
import com.duckduckgo.app.browser.TrackerLogo.LetterLogo
import com.duckduckgo.app.browser.TrackerLogo.StackedLogo
import com.duckduckgo.app.cta.ui.Cta
import com.duckduckgo.app.cta.ui.DaxDialogCta
import com.duckduckgo.app.privacy.renderer.TrackersRenderer
import com.duckduckgo.app.trackerdetection.model.Entity
import com.duckduckgo.mobile.android.R as CommonR
import com.duckduckgo.mobile.android.ui.store.AppTheme
import com.duckduckgo.mobile.android.ui.view.getColorFromAttr
import com.duckduckgo.mobile.android.ui.view.toPx
import timber.log.Timber
import java.util.*

interface TrackersAnimatorListener {
    fun onAnimationFinished()
}

class BrowserTrackersAnimatorHelper {

    private var trackersAnimation: AnimatorSet = AnimatorSet()
    private var pulseAnimation: AnimatorSet = AnimatorSet()
    private var listener: TrackersAnimatorListener? = null

    fun startTrackersAnimation(
        cta: Cta?,
        activity: Activity,
        container: ConstraintLayout,
        omnibarViews: List<View>,
        entities: List<Entity>?
    ) {
        if (entities.isNullOrEmpty()) { // no badge nor tracker animations
            listener?.onAnimationFinished()
            return
        }

        val logoViews: List<View> = getLogosViewListInContainer(activity, container, entities)
        if (logoViews.isEmpty()) {
            listener?.onAnimationFinished()
            return
        }

        if (!trackersAnimation.isRunning) {
            trackersAnimation = if (cta is DaxDialogCta.DaxTrackersBlockedCta) {
                createPartialTrackersAnimation(container, omnibarViews, logoViews).apply {
                    start()
                }
            } else {
                createFullTrackersAnimation(container, omnibarViews, logoViews).apply {
                    start()
                }
            }
        }
    }

    fun startPulseAnimation(view: ImageButton) {
        if (!pulseAnimation.isRunning) {
            val scaleDown = ObjectAnimator.ofPropertyValuesHolder(
                view,
                PropertyValuesHolder.ofFloat("scaleX", 1f, 0.9f, 1f),
                PropertyValuesHolder.ofFloat("scaleY", 1f, 0.9f, 1f)
            )
            scaleDown.repeatCount = ObjectAnimator.INFINITE
            scaleDown.duration = PULSE_ANIMATION_DURATION

            pulseAnimation = AnimatorSet().apply {
                play(scaleDown)
                start()
            }
        }
    }

    fun stopPulseAnimation() {
        if (pulseAnimation.isRunning) {
            pulseAnimation.end()
        }
    }

    fun removeListener() {
        listener = null
    }

    fun setListener(animatorListener: TrackersAnimatorListener) {
        listener = animatorListener
    }

    fun cancelAnimations(
        omnibarViews: List<View>,
        container: ViewGroup
    ) {
        stopTrackersAnimation()
        stopPulseAnimation()
        listener?.onAnimationFinished()
        omnibarViews.forEach { it.alpha = 1f }
        container.alpha = 0f
    }

    fun finishTrackerAnimation(
        omnibarViews: List<View>,
        container: ViewGroup
    ) {
        trackersAnimation = AnimatorSet().apply {
            play(animateLogosSlideOut(container.children.toList()))
                .before(animateOmnibarIn(omnibarViews))
                .before(animateFadeOut(container))
            start()
            addListener(onEnd = { listener?.onAnimationFinished() })
        }
    }

    private fun getLogosViewListInContainer(
        activity: Activity,
        container: ViewGroup,
        entities: List<Entity>
    ): List<View> {
        container.removeAllViews()
        container.alpha = 0f
        val logos = createTrackerLogoList(activity, entities)
        return createLogosViewList(activity, container, logos)
    }

    private fun createLogosViewList(
        activity: Activity,
        container: ViewGroup,
        resourcesId: List<TrackerLogo>
    ): List<View> {
        return resourcesId.map {
            val frameLayout = when (it) {
                is TrackerLogo.ImageLogo -> createTrackerImageLogo(activity, it)
                is LetterLogo -> createTrackerTextLogo(activity, it)
                is TrackerLogo.StackedLogo -> createTrackerStackedLogo(activity, it)
            }
            container.addView(frameLayout)
            return@map frameLayout
        }
    }

    private fun getParams(): FrameLayout.LayoutParams {
        val params = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT)
        params.gravity = Gravity.CENTER
        return params
    }

    private fun createAnimatedDrawable(context: Context): AnimatedVectorDrawableCompat? {
        return AnimatedVectorDrawableCompat.create(context, R.drawable.network_cross_anim)
    }

    private fun createFrameLayoutContainer(context: Context): FrameLayout {
        val frameLayout = FrameLayout(context)
        frameLayout.alpha = 0f
        frameLayout.id = View.generateViewId()
        frameLayout.layoutParams = getParams()
        frameLayout.setBackgroundResource(R.drawable.background_tracker_logo)
        return frameLayout
    }

    private fun createImageView(
        context: Context,
        resId: Int
    ): ImageView {
        val imageView = ImageView(context)
        imageView.scaleType = ImageView.ScaleType.CENTER_CROP
        imageView.setBackgroundResource(resId)
        imageView.id = View.generateViewId()
        imageView.layoutParams = getParams()
        return imageView
    }

    private fun createTextView(context: Context): AppCompatTextView {
        val textView = AppCompatTextView(context)
        textView.gravity = Gravity.CENTER
        TextViewCompat.setTextAppearance(textView, CommonR.style.UnknownTrackerText)
        textView.layoutParams = getParams()

        return textView
    }

    private fun createTrackerTextLogo(
        activity: Activity,
        trackerLogo: LetterLogo
    ): FrameLayout {
        val animatedDrawable = createAnimatedDrawable(activity)

        val animationView = ImageView(activity)
        animationView.setImageDrawable(animatedDrawable)
        animationView.layoutParams = getParams()

        val textView = createTextView(activity)
        textView.setBackgroundResource(trackerLogo.resId)
        textView.text = trackerLogo.trackerLetter

        val frameLayout = createFrameLayoutContainer(activity)
        frameLayout.addView(textView)
        frameLayout.addView(animationView)

        return frameLayout
    }

    private fun createTrackerStackedLogo(
        activity: Activity,
        trackerLogo: TrackerLogo.StackedLogo
    ): FrameLayout {
        val imageView = createImageView(activity, trackerLogo.resId)
        val frameLayout = createFrameLayoutContainer(activity)

        frameLayout.addView(imageView)

        return frameLayout
    }

    private fun createTrackerImageLogo(
        activity: Activity,
        trackerLogo: TrackerLogo.ImageLogo
    ): FrameLayout {
        val imageView = createImageView(activity, trackerLogo.resId)
        val animatedDrawable = createAnimatedDrawable(activity)
        imageView.setImageDrawable(animatedDrawable)

        val frameLayout = createFrameLayoutContainer(activity)
        frameLayout.addView(imageView)

        return frameLayout
    }

    private fun createTrackerLogoList(
        activity: Activity,
        entities: List<Entity>
    ): List<TrackerLogo> {
        if (activity.packageName == null) return emptyList()
        val trackerLogoList = entities
            .asSequence()
            .distinct()
            .take(MAX_LOGOS_SHOWN + 1)
            .sortedWithDisplayNamesStartingWithVowelsToTheEnd()
            .map {
                val resId = TrackersRenderer().networkLogoIcon(activity, it.name)
                if (resId == null) {
                    LetterLogo(it.displayName.take(1))
                } else {
                    TrackerLogo.ImageLogo(resId)
                }
            }
            .toMutableList()

        return if (trackerLogoList.size <= MAX_LOGOS_SHOWN) {
            trackerLogoList
        } else {
            trackerLogoList.take(MAX_LOGOS_SHOWN)
                .toMutableList()
                .apply { add(TrackerLogo.StackedLogo()) }
        }
    }

    private fun animateLogosBlocked(views: List<View>) {
        views.map {
            if (it is FrameLayout) {
                val view: ImageView? = it.children.filter { child -> child is ImageView }.firstOrNull() as ImageView?
                view?.let {
                    val animatedVectorDrawableCompat = view.drawable as? AnimatedVectorDrawableCompat
                    animatedVectorDrawableCompat?.start()
                }
            }
        }
    }

    private fun createFullTrackersAnimation(
        container: ConstraintLayout,
        omnibarViews: List<View>,
        logoViews: List<View>
    ): AnimatorSet {
        val finalAnimation = AnimatorSet().apply {
            play(animateLogosSlideOut(logoViews))
                .after(TRACKER_LOGOS_DELAY_ON_SCREEN)
                .before(animateFadeOut(container))
                .before(animateOmnibarIn(omnibarViews))
        }

        return AnimatorSet().apply {
            playSequentially(
                createPartialTrackersAnimation(container, omnibarViews, logoViews),
                finalAnimation
            )
            start()
            addListener(onEnd = { listener?.onAnimationFinished() })
        }
    }

    private fun createPartialTrackersAnimation(
        container: ConstraintLayout,
        omnibarViews: List<View>,
        logoViews: List<View>
    ): AnimatorSet {
        applyConstraintSet(container, logoViews)
        container.alpha = 1f
        animateLogosBlocked(logoViews)

        return AnimatorSet().apply {
            play(animateLogosSlideIn(logoViews)).after(animateOmnibarOut(omnibarViews))
        }
    }

    private fun applyConstraintSet(
        container: ConstraintLayout,
        views: List<View>
    ) {
        val constraints = ConstraintSet()
        constraints.clone(container)

        views.mapIndexed { index, view ->
            constraints.connect(view.id, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
            constraints.connect(view.id, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
            if (index == 0) {
                constraints.connect(view.id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START, FIRST_LOGO_MARGIN_IN_DP.toPx())
            } else {
                constraints.setTranslationX(view.id, (NORMAL_LOGO_MARGIN_IN_DP.toPx() * index))
                constraints.connect(view.id, ConstraintSet.START, views[index - 1].id, ConstraintSet.END, 0)
            }
            if (index == views.size - 1) {
                if (views.size == MAX_LOGOS_SHOWN + 1) {
                    constraints.setTranslationX(view.id, (STACKED_LOGO_MARGIN_IN_DP.toPx() * index))
                }
                constraints.connect(view.id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
            }
        }

        views.reversed().map { it.bringToFront() }

        val viewIds = views.map { it.id }.toIntArray()

        if (viewIds.size > 1) {
            constraints.createHorizontalChain(
                ConstraintSet.PARENT_ID,
                ConstraintSet.LEFT,
                ConstraintSet.PARENT_ID,
                ConstraintSet.RIGHT,
                viewIds,
                null,
                ConstraintSet.CHAIN_SPREAD
            )
        }

        constraints.applyTo(container)
    }

    private fun calculateMarginInPx(position: Int): Int = START_MARGIN_IN_DP.toPx() + (position * LOGO_SIZE_IN_DP.toPx())

    private fun stopTrackersAnimation() {
        if (trackersAnimation.isRunning) {
            trackersAnimation.end()
        }
    }

    private fun animateOmnibarOut(views: List<View>): AnimatorSet {
        val animators = views.map {
            animateFadeOut(it)
        }
        return AnimatorSet().apply {
            playTogether(animators)
        }
    }

    private fun animateOmnibarIn(views: List<View>): AnimatorSet {
        val animators = views.map {
            animateFadeIn(it)
        }
        return AnimatorSet().apply {
            playTogether(animators)
        }
    }

    private fun animateLogosSlideIn(views: List<View>): AnimatorSet {
        val initialMargin = (views.first().layoutParams as ConstraintLayout.LayoutParams).marginStart.toFloat()
        val slideInAnimators = views.mapIndexed { index, it ->
            val margin = calculateMarginInPx(index) + initialMargin
            animateSlideIn(it, margin)
        }
        val fadeInAnimators = views.map {
            animateFadeIn(it)
        }
        return AnimatorSet().apply {
            playTogether(slideInAnimators + fadeInAnimators)
        }
    }

    private fun animateLogosSlideOut(views: List<View>): AnimatorSet {
        val slideOutAnimators = views.map {
            animateSlideOut(it)
        }
        val fadeOutAnimators = views.map {
            animateFadeOut(it)
        }
        return AnimatorSet().apply {
            playTogether(slideOutAnimators + fadeOutAnimators)
        }
    }

    private fun animateSlideIn(
        view: View,
        margin: Float
    ): ObjectAnimator {
        return ObjectAnimator.ofFloat(view, "x", 0f, margin + view.translationX).apply {
            duration = DEFAULT_ANIMATION_DURATION
        }
    }

    private fun animateSlideOut(view: View): ObjectAnimator {
        return ObjectAnimator.ofFloat(view, "x", 0f).apply {
            duration = DEFAULT_ANIMATION_DURATION
        }
    }

    private fun animateFadeOut(
        view: View,
        durationInMs: Long = DEFAULT_ANIMATION_DURATION
    ): ObjectAnimator {
        return ObjectAnimator.ofFloat(view, "alpha", 1f, 0f).apply {
            duration = durationInMs
        }
    }

    private fun animateFadeIn(view: View): ObjectAnimator {
        return ObjectAnimator.ofFloat(view, "alpha", 0f, 1f).apply {
            duration = DEFAULT_ANIMATION_DURATION
        }
    }

    private fun Sequence<Entity>.sortedWithDisplayNamesStartingWithVowelsToTheEnd(): Sequence<Entity> {
        return sortedWith(compareBy { "AEIOU".contains(it.displayName.take(1)) })
    }

    companion object {
        private const val TRACKER_LOGOS_DELAY_ON_SCREEN = 2400L
        private const val DEFAULT_ANIMATION_DURATION = 150L
        private const val PULSE_ANIMATION_DURATION = 1500L
        private const val MAX_LOGOS_SHOWN = 3
        private const val LOGO_SIZE_IN_DP = 26
        private const val START_MARGIN_IN_DP = 10
        private const val STACKED_LOGO_MARGIN_IN_DP = -11.5f
        private const val NORMAL_LOGO_MARGIN_IN_DP = -7f
        private const val FIRST_LOGO_MARGIN_IN_DP = 25
    }
}

class BrowserLottieTrackersAnimatorHelper {

    private var listener: TrackersAnimatorListener? = null
    private lateinit var trackersAnimation: LottieAnimationView
    private lateinit var shieldAnimation: LottieAnimationView
    private var currentCta: Cta? = null
    private val isCtaVisible
        get() = currentCta is DaxDialogCta.DaxTrackersBlockedCta

    private var partialAnimation: Boolean = false

    fun startTrackersAnimation(
        cta: Cta?,
        activity: Activity,
        shieldAnimationView: LottieAnimationView,
        trackersAnimationView: LottieAnimationView,
        omnibarViews: List<View>,
        entities: List<Entity>?,
        theme: AppTheme
    ) {
        this.trackersAnimation = trackersAnimationView
        this.shieldAnimation = shieldAnimationView
        this.currentCta = cta

        Timber.i("Lottie: isAnimating ${trackersAnimationView.isAnimating}")
        if (trackersAnimationView.isAnimating) return

        if (entities.isNullOrEmpty()) { // no badge nor tracker animations
            Timber.i("Lottie: entities.isNullOrEmpty()")
            listener?.onAnimationFinished()
            return
        }

        entities.forEach {
            Timber.i("Lottie: entities ${it.name}")
        }
        val logos = getLogos(activity, entities)
        /*if (logos.isEmpty()) {
            Timber.i("Lottie: logos empty")
            listener?.onAnimationFinished()
            return
        }*/

        val animationRawRes = getAnimationRawRes(logos, theme)

        with(trackersAnimationView) {
            this.setCacheComposition(false) // ensure assets are not cached
            this.setAnimation(animationRawRes)
            this.maintainOriginalImageBounds = true
            this.setImageAssetDelegate { asset ->
                Timber.i("Lottie: ${asset?.id} ${asset?.fileName}")
                when (asset?.id) {
                    "image_0" -> {
                        kotlin.runCatching { logos[0].asDrawable(activity) }
                            .getOrDefault(
                                ContextCompat.getDrawable(activity, R.drawable.network_logo_blank)!!.toBitmap()
                            )
                    }
                    "image_1" -> {
                        kotlin.runCatching { logos[1].asDrawable(activity) }
                            .getOrDefault(
                                ContextCompat.getDrawable(activity, R.drawable.network_logo_blank)!!.toBitmap()
                            )
                    }
                    "image_2" -> {
                        kotlin.runCatching { logos[2].asDrawable(activity) }
                            .getOrDefault(
                                ContextCompat.getDrawable(activity, R.drawable.network_logo_blank)!!.toBitmap()
                            )
                    }
                    "image_3" ->
                        kotlin.runCatching { logos[3].asDrawable(activity) }
                            .getOrNull()
                    else -> TODO()
                }
            }
            this.addAnimatorListener(object : AnimatorListener {
                override fun onAnimationStart(animation: Animator?) {
                    Timber.i("Lottie: onAnimationStart")
                    if (partialAnimation) return
                    animateOmnibarOut(omnibarViews).start()
                }

                override fun onAnimationEnd(animation: Animator?) {
                    Timber.i("Lottie: onAnimationEnd")
                    if (!isCtaVisible) {
                        animateOmnibarIn(omnibarViews).start()
                        partialAnimation = false
                    }
                }

                override fun onAnimationCancel(animation: Animator?) {
                }

                override fun onAnimationRepeat(animation: Animator?) {
                }
            })

            Timber.i("Lottie: isCtaVisible $isCtaVisible")
            if (isCtaVisible) {
                this.setMaxProgress(0.5f)
                shieldAnimationView.setMaxProgress(0.5f)
            } else {
                this.setMaxProgress(1f)
                shieldAnimationView.setMaxProgress(1f)
            }
            shieldAnimationView.playAnimation()
            this.playAnimation()
        }
    }

    private fun getAnimationRawRes(
        logos: List<TrackerLogo>,
        theme: AppTheme
    ): Int {
        val trackers = logos.size
        return when {
            trackers == 1 -> if (theme.isLightModeEnabled()) R.raw.light_trackers_1 else R.raw.dark_trackers_1
            trackers == 2 -> if (theme.isLightModeEnabled()) R.raw.light_trackers_2 else R.raw.dark_trackers_2
            trackers >= 3 -> if (theme.isLightModeEnabled()) R.raw.light_trackers else R.raw.dark_trackers
            else -> TODO()
        }
    }

    private fun TrackerLogo.asDrawable(activity: Activity): Bitmap {
        return kotlin.runCatching {
            when (this) {
                is ImageLogo -> ContextCompat.getDrawable(activity, resId)!!.toBitmap()
                is LetterLogo -> generateDefaultDrawable(activity, this.trackerLetter).toBitmap(24.toPx(), 24.toPx())
                is StackedLogo -> ContextCompat.getDrawable(activity, R.drawable.network_logo_blank)!!.toBitmap()
            }
        }.getOrThrow()
    }

    private fun getLogos(
        activity: Activity,
        entities: List<Entity>
    ): List<TrackerLogo> {
        if (activity.packageName == null) return emptyList()
        val trackerLogoList = entities
            .asSequence()
            .distinct()
            .take(MAX_LOGOS_SHOWN + 1)
            .sortedWithDisplayNamesStartingWithVowelsToTheEnd()
            .map {
                val resId = TrackersRenderer().networkLogoIcon(activity, it.name)
                if (resId == null) {
                    LetterLogo(it.displayName.take(1))
                } else {
                    ImageLogo(resId)
                }
            }.toMutableList()

        return if (trackerLogoList.size <= MAX_LOGOS_SHOWN) {
            trackerLogoList
        } else {
            trackerLogoList.take(MAX_LOGOS_SHOWN)
                .toMutableList()
                .apply { add(StackedLogo()) }
        }
    }

    fun generateDefaultDrawable(
        context: Context,
        letter: String
    ): Drawable {
        Timber.i("Lottie: will create logo for $letter")
        return object : Drawable() {

            private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = context.getColorFromAttr(com.duckduckgo.mobile.android.R.attr.toolbarIconColor)
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
                Timber.i("Lottie: will create logo for $letter")
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

    fun removeListener() {
        listener = null
    }

    fun setListener(animatorListener: TrackersAnimatorListener) {
        listener = animatorListener
    }

    fun cancelAnimations(
        omnibarViews: List<View>,
        container: ViewGroup
    ) {
        Timber.i("Lottie: cancelAnimations")
        stopTrackersAnimation()
        listener?.onAnimationFinished()
        omnibarViews.forEach { it.alpha = 1f }
        container.alpha = 0f
    }

    fun finishTrackerAnimation(
        omnibarViews: List<View>,
        container: ViewGroup
    ) {
        currentCta = null
        partialAnimation = true
        Timber.i("Lottie: finishTrackerAnimation")
        this.trackersAnimation.setMinAndMaxProgress(0.5f, 1f)
        this.shieldAnimation.setMinAndMaxProgress(0.5f, 1f)
        this.trackersAnimation.playAnimation()
        this.shieldAnimation.playAnimation()
    }

    private fun stopTrackersAnimation() {
        Timber.i("Lottie: stopTrackersAnimation")
        if (!::trackersAnimation.isInitialized || !::shieldAnimation.isInitialized) return

        Timber.i("Lottie: stopTrackersAnimation real")
        trackersAnimation.cancelAnimation()
        trackersAnimation.progress = 1f
        if (shieldAnimation.isAnimating) {
            shieldAnimation.cancelAnimation()
            shieldAnimation.progress = 0f
        }
    }

    private fun animateOmnibarOut(views: List<View>): AnimatorSet {
        Timber.i("Lottie: animateOmnibarOut")
        val animators = views.map {
            animateFadeOut(it)
        }
        return AnimatorSet().apply {
            playTogether(animators)
        }
    }

    private fun animateOmnibarIn(views: List<View>): AnimatorSet {
        Timber.i("Lottie: animateOmnibarIn")
        val animators = views.map {
            animateFadeIn(it)
        }
        return AnimatorSet().apply {
            playTogether(animators)
        }
    }

    private fun animateFadeOut(
        view: View,
        durationInMs: Long = DEFAULT_ANIMATION_DURATION
    ): ObjectAnimator {
        Timber.i("Lottie: animateFadeOut")
        return ObjectAnimator.ofFloat(view, "alpha", 1f, 0f).apply {
            duration = durationInMs
        }
    }

    private fun animateFadeIn(view: View): ObjectAnimator {
        Timber.i("Lottie: animateFadeIn")
        if (view.alpha == 1f) {
            return ObjectAnimator.ofFloat(view, "alpha", 1f, 1f).apply {
                duration = DEFAULT_ANIMATION_DURATION
            }
        }

        return ObjectAnimator.ofFloat(view, "alpha", 0f, 1f).apply {
            duration = DEFAULT_ANIMATION_DURATION
        }
    }

    private fun Sequence<Entity>.sortedWithDisplayNamesStartingWithVowelsToTheEnd(): Sequence<Entity> {
        return sortedWith(compareBy { "AEIOU".contains(it.displayName.take(1)) })
    }

    companion object {
        private const val DEFAULT_ANIMATION_DURATION = 150L
        private const val MAX_LOGOS_SHOWN = 3
    }
}

sealed class TrackerLogo(val resId: Int) {
    class ImageLogo(resId: Int) : TrackerLogo(resId)
    class LetterLogo(
        val trackerLetter: String = "",
        resId: Int = R.drawable.other_tracker_bg
    ) : TrackerLogo(resId)

    class StackedLogo(resId: Int = R.drawable.other_tracker_bg) : TrackerLogo(resId)
}
