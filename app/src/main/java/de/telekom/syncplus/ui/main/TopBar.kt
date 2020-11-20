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

package de.telekom.syncplus.ui.main

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import de.telekom.dtagsyncpluskit.px
import de.telekom.syncplus.R
import kotlinx.android.synthetic.main.layout_topbar.view.*

@Suppress("unused")
class TopBar : LinearLayout {

    var hasBackButton: Boolean = true
        set(value) {
            field = value
            backButton.visibility = if (value) View.VISIBLE else View.GONE
        }

    var hasHelpButton: Boolean = true
        set(value) {
            field = value
            helpButton.visibility = if (value) View.VISIBLE else View.GONE
        }

    var currentStep: Int = 0
        set(value) {
            field = value
            stepTextView.text = context.getString(R.string.topbar_steps, currentStep, maxSteps)
            if (maxSteps > 0) {
                progress = currentStep.toFloat() / maxSteps.toFloat()
            }
        }

    var maxSteps: Int = 0
        set(value) {
            field = value
            stepTextView.text = context.getString(R.string.topbar_steps, currentStep, maxSteps)
            if (maxSteps > 0) {
                progress = currentStep.toFloat() / maxSteps.toFloat()
            }
        }

    var description: String? = null
        set(value) {
            field = value
            descriptionText.text = description
            descriptionText.visibility = if (value == null) View.GONE else View.VISIBLE
        }

    var progressVisible: Boolean = true
        set(value) {
            field = value
            progress_indicator.visibility = if (value) View.VISIBLE else View.GONE
        }

    var progress: Float = 0.0f
        set(value) {
            field = value
            progressBar.layoutParams = LayoutParams(0.px, 4.px, progress)
            progressBarFiller.layoutParams = LayoutParams(0, 0, 1.0f - progress)
        }

    var large: Boolean = false
        set(value) {
            field = value
            extraSectionWrapper.visibility = if (large) View.VISIBLE else View.GONE
        }

    var extraDrawable: Int = 0
        set(value) {
            field = value
            if (value != 0) extraSectionGraphic.setImageResource(value)
            extraSectionGraphic.visibility = if (value == 0) View.GONE else View.VISIBLE
        }

    var extraDrawableSmall: Int = 0
        set(value) {
            field = value
            if (value != 0) extraSectionGraphicSmall.setImageResource(value)
            extraSectionGraphicSmall.visibility = if (value == 0) View.GONE else View.VISIBLE
        }

    var extraTitle: String? = null
        set(value) {
            field = value
            extraSectionTitle.text = value
            extraSectionTitle.visibility = if (value == null) View.GONE else View.VISIBLE
        }

    var extraDescription: String? = null
        set(value) {
            field = value
            extraSectionDescription.text = value
            extraSectionDescription.visibility = if (value == null) View.GONE else View.VISIBLE
        }

    var extraSectionButtonTitle: String? = null
        set(value) {
            field = value
            extraSectionLinkText.text = value
            extraSectionLinkWrapper.visibility = if (value == null) View.GONE else View.VISIBLE
        }

    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    )

    fun setOnHelpClickListener(l: (v: View) -> Unit) {
        helpButton.setOnClickListener(l)
    }

    fun setOnBackClickListener(l: (v: View) -> Unit) {
        backButton.setOnClickListener(l)
    }

    fun setOnHelpClickListener(l: OnClickListener?) {
        helpButton.setOnClickListener(l)
    }

    fun setOnBackClickListener(l: OnClickListener?) {
        backButton.setOnClickListener(l)
    }

    fun setOnLinkClickListener(l: OnClickListener?) {
        extraSectionLinkWrapper.visibility = if (l == null) View.GONE else View.VISIBLE
        extraSectionLinkWrapper.setOnClickListener(l)
    }

    fun setOnLinkClickListener(l: (View) -> Unit) {
        extraSectionLinkWrapper.visibility = View.VISIBLE
        extraSectionLinkWrapper.setOnClickListener(l)
    }

    fun updateProgress() {
        progress = progress
    }

    override fun onFinishInflate() {
        super.onFinishInflate()

        description = null
        progress = 0.0f
        maxSteps = 0
        currentStep = 0
        large = false
        extraDrawable = 0
        extraDrawableSmall = 0
        extraTitle = null
        extraDescription = null
        extraSectionButtonTitle = null
    }
}
