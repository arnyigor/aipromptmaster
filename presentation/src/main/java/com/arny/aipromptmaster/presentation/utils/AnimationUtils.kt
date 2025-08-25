package com.arny.aipromptmaster.presentation.utils

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.TextView
import androidx.core.view.isVisible

object AnimationUtils {

    private const val ANIMATION_DURATION = 300L
    private const val FADE_DURATION = 150L

    /**
     * Показывает View с анимацией slide down + fade in
     */
    fun showWithSlideDown(view: View, onAnimationEnd: (() -> Unit)? = null) {
        if (view.isVisible) {
            onAnimationEnd?.invoke()
            return
        }

        // Подготавливаем view для анимации
        view.alpha = 0f
        view.translationY = -view.height.toFloat()
        view.isVisible = true

        // Создаем аниматоры
        val fadeIn = ObjectAnimator.ofFloat(view, "alpha", 0f, 1f)
        val slideDown = ObjectAnimator.ofFloat(view, "translationY", -view.height.toFloat(), 0f)

        fadeIn.duration = ANIMATION_DURATION
        slideDown.duration = ANIMATION_DURATION

        fadeIn.interpolator = AccelerateDecelerateInterpolator()
        slideDown.interpolator = AccelerateDecelerateInterpolator()

        slideDown.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                onAnimationEnd?.invoke()
            }
        })

        fadeIn.start()
        slideDown.start()
    }

    /**
     * Скрывает View с анимацией slide up + fade out
     */
    fun hideWithSlideUp(view: View, onAnimationEnd: (() -> Unit)? = null) {
        if (!view.isVisible) {
            onAnimationEnd?.invoke()
            return
        }

        val fadeOut = ObjectAnimator.ofFloat(view, "alpha", 1f, 0f)
        val slideUp = ObjectAnimator.ofFloat(view, "translationY", 0f, -view.height.toFloat())

        fadeOut.duration = ANIMATION_DURATION
        slideUp.duration = ANIMATION_DURATION

        fadeOut.interpolator = AccelerateDecelerateInterpolator()
        slideUp.interpolator = AccelerateDecelerateInterpolator()

        slideUp.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                view.isVisible = false
                view.alpha = 1f
                view.translationY = 0f
                onAnimationEnd?.invoke()
            }
        })

        fadeOut.start()
        slideUp.start()
    }

    /**
     * Обновить текст с fade эффектом
     */
    fun updateTextWithFade(
        textView: TextView,
        newText: String,
        onAnimationEnd: (() -> Unit)? = null
    ) {
        val fadeOut = ObjectAnimator.ofFloat(textView, "alpha", 1f, 0f)
        fadeOut.duration = FADE_DURATION

        fadeOut.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                textView.text = newText
                val fadeIn = ObjectAnimator.ofFloat(textView, "alpha", 0f, 1f)
                fadeIn.duration = FADE_DURATION
                fadeIn.addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        onAnimationEnd?.invoke()
                    }
                })
                fadeIn.start()
            }
        })

        fadeOut.start()
    }

    /**
     * Показать с пульсацией для привлечения внимания
     */
    fun showWithPulse(view: View, pulseCount: Int = 2) {
        showWithSlideDown(view) {
            val scaleX = ObjectAnimator.ofFloat(view, "scaleX", 1f, 1.05f, 1f)
            val scaleY = ObjectAnimator.ofFloat(view, "scaleY", 1f, 1.05f, 1f)

            scaleX.duration = 300
            scaleY.duration = 300
            scaleX.repeatCount = (pulseCount * 2) - 1
            scaleY.repeatCount = (pulseCount * 2) - 1

            scaleX.start()
            scaleY.start()
        }
    }

    /**
     * Альтернативная анимация: Expand/Collapse с изменением высоты
     */
    fun expandView(view: View, onAnimationEnd: (() -> Unit)? = null) {
        if (view.isVisible) {
            onAnimationEnd?.invoke()
            return
        }

        // Измеряем желаемую высоту
        view.measure(
            View.MeasureSpec.makeMeasureSpec(view.width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val targetHeight = view.measuredHeight

        view.layoutParams.height = 0
        view.isVisible = true

        val animator = ValueAnimator.ofInt(0, targetHeight)
        animator.duration = ANIMATION_DURATION
        animator.interpolator = AccelerateDecelerateInterpolator()

        animator.addUpdateListener { animation ->
            view.layoutParams.height = animation.animatedValue as Int
            view.requestLayout()
        }

        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                view.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
                onAnimationEnd?.invoke()
            }
        })

        animator.start()
    }

    fun collapseView(view: View, onAnimationEnd: (() -> Unit)? = null) {
        if (!view.isVisible) {
            onAnimationEnd?.invoke()
            return
        }

        val initialHeight = view.height

        val animator = ValueAnimator.ofInt(initialHeight, 0)
        animator.duration = ANIMATION_DURATION
        animator.interpolator = AccelerateDecelerateInterpolator()

        animator.addUpdateListener { animation ->
            view.layoutParams.height = animation.animatedValue as Int
            view.requestLayout()
        }

        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                view.isVisible = false
                view.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
                onAnimationEnd?.invoke()
            }
        })

        animator.start()
    }
}
