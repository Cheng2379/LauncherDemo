package com.example.launcherdemo.fragment

import android.app.Dialog
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.core.graphics.drawable.toDrawable

/**
 *
 * @author cheng
 * @since 2025/4/12
 */
class LauncherDialogFragment(
    private val layoutId: Int,
    private val onView: (view: View, dialog: Dialog) -> Unit
) : DialogFragment() {
    private var width: Int? = null
    private var height: Int? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(layoutId, container, false)
        dialog?.apply {
            window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
            onView(view, this)
        }
        return view
    }

    override fun onStart() {
        super.onStart()
        initDialog()
    }

    private fun initDialog() {
        dialog?.window?.apply {
            val displayMetrics = getDisplayMetrics()
            width = width ?: (displayMetrics.widthPixels * 0.7).toInt()
            height = height ?: ViewGroup.LayoutParams.WRAP_CONTENT
            setLayout(width!!, height!!)
            attributes?.gravity = Gravity.CENTER
        }
    }

    private fun getDisplayMetrics(): DisplayMetrics {
        val windowManager = activity?.windowManager
        return DisplayMetrics().apply {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                    windowManager?.currentWindowMetrics?.let {
                        widthPixels = it.bounds.width()
                        heightPixels = it.bounds.height()
                    }
                }

                else -> windowManager?.defaultDisplay?.getMetrics(this)
            }
        }
    }

}