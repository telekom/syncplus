/**
 * This file is part of SyncPlus.
 *
 * Copyright (C) 2020  Deutsche Telekom AG
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package de.telekom.syncplus.ui.main.intro

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Parcelable
import android.view.View
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentStatePagerAdapter
import androidx.viewpager.widget.ViewPager
import de.telekom.dtagsyncpluskit.ui.BaseActivity
import de.telekom.syncplus.R
import de.telekom.syncplus.databinding.IntroActivityBinding
import de.telekom.syncplus.ui.main.login.LoginActivity
import de.telekom.syncplus.util.viewbinding.viewBinding
import kotlinx.parcelize.Parcelize

class IntroActivity : BaseActivity(R.layout.intro_activity) {
    companion object {
        private const val NUM_PAGES = 4
    }

    // TODO Refactor in favor of using the Fragment Result API
    @Parcelize
    open class OnCancelListener : Parcelable {
        open fun onCancel() {}
    }

    private var mBottomPadding: Int = 0
    private val binding by viewBinding(R.id.root) { IntroActivityBinding.bind(it) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Setup view
        binding.bottomLayout.measure(0, 0)
        mBottomPadding = binding.bottomLayout.measuredHeight

        // The pager adapter, which provides the pages to the view pager widget.
        val pagerAdapter = IntroPagerAdapter(this, supportFragmentManager)
        binding.viewPager.adapter = pagerAdapter

        binding.viewPager.addOnPageChangeListener(
            object : ViewPager.OnPageChangeListener {
                override fun onPageScrollStateChanged(state: Int) {}

                override fun onPageScrolled(
                    position: Int,
                    positionOffset: Float,
                    positionOffsetPixels: Int,
                ) {
                }

                override fun onPageSelected(position: Int) {
                    updatePaginationForPage(position)
                    updateBackgroundForPage(position)
                    updateTextForPage(position)
                    if (position != 0) {
                        binding.nextButton.setStrokeColorResource(R.color.colorPrimary)
                    } else {
                        binding.nextButton.setStrokeColorResource(android.R.color.white)
                    }
                }
            },
        )

        // Initial page
        updatePaginationForPage(0)
        updateBackgroundForPage(0)
        updateTextForPage(0)

        @Suppress("DEPRECATION")
        binding.nextButton.setBackgroundColor(resources.getColor(R.color.colorPrimary))
        binding.nextButton.setStrokeColorResource(android.R.color.white)
        binding.nextButton.setOnClickListener {
            if (binding.viewPager.currentItem >= 3) {
                startActivity(LoginActivity.newIntent(this))
            } else {
                binding.viewPager.currentItem = binding.viewPager.currentItem + 1
            }
        }
    }

    private fun updatePaginationForPage(position: Int) {
        val dots =
            arrayOf(
                binding.paginationDot1,
                binding.paginationDot2,
                binding.paginationDot3,
                binding.paginationDot4,
            )

        if (position >= 1) {
            val dotNormal = ContextCompat.getDrawable(applicationContext, R.drawable.ic_dot)
            val dotPrimary =
                ContextCompat.getDrawable(applicationContext, R.drawable.ic_dot_primary)
            dots.forEach { it.background = dotNormal }
            dots[position].background = dotPrimary
        } else if (position == 0) {
            val dotLight = ContextCompat.getDrawable(applicationContext, R.drawable.ic_dot_light)
            val dotWhite = ContextCompat.getDrawable(applicationContext, R.drawable.ic_dot_white)
            dots.forEach { it.background = dotLight }
            dots[0].background = dotWhite
        }
    }

    private fun updateBackgroundForPage(position: Int) {
        val mainView = findViewById<View>(android.R.id.content).rootView
        val light = Color.parseColor("#ffffff")
        val dark = ContextCompat.getColor(applicationContext, R.color.colorPrimary)

        var newColor = dark
        if (position > 0) {
            newColor = light
        }
        var currentColor = light
        if (mainView.background is ColorDrawable) {
            currentColor = (mainView.background as ColorDrawable).color
        }

        val anim = ValueAnimator()
        anim.setIntValues(currentColor, newColor)
        anim.setEvaluator(ArgbEvaluator())
        anim.addUpdateListener { valueAnimator -> mainView.setBackgroundColor(valueAnimator.animatedValue as Int) }
        anim.duration = 250
        anim.start()
    }

    private fun updateTextForPage(position: Int) {
        if (position >= 3) {
            binding.nextButton.text = getString(R.string.button_setup)
        } else {
            binding.nextButton.text = getString(R.string.button_next)
        }
    }

    /**
     * A simple pager adapter that represents 5 ScreenSlidePageFragment objects, in
     * sequence.
     */
    private inner class IntroPagerAdapter(val context: Context, fm: FragmentManager) :
        FragmentStatePagerAdapter(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {
        override fun getCount(): Int = NUM_PAGES

        override fun getItem(position: Int): Fragment {
            val l =
                object : OnCancelListener() {
                    override fun onCancel() {
                        startActivity(LoginActivity.newIntent(context))
                    }
                }

            return when (position) {
                0 -> {
                    IntroCloudFragment.newInstance(l, mBottomPadding)
                }
                1 -> {
                    IntroContactsFragment.newInstance(l, mBottomPadding)
                }
                2 -> {
                    IntroCalendarFragment.newInstance(l, mBottomPadding)
                }
                3 -> {
                    IntroMailFragment.newInstance(l, mBottomPadding)
                }
                else -> throw Exception("Too many pages in ViewPager")
            }
        }
    }
}
