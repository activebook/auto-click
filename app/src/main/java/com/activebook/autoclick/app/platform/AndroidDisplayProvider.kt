package com.activebook.autoclick.app.platform

import android.content.Context
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowInsets
import android.view.WindowManager
import com.activebook.autoclick.app.core.DisplayProvider
import com.activebook.autoclick.app.domain.geometry.ScreenRect

/**
 * Reports the area a tap target may live in: the full display minus system bars and cutout.
 *
 * The grid is defined over exactly this area, which is also what the in-app preview and the
 * on-screen picker show — so "cell 5" means the same thing everywhere, in any orientation.
 */
class AndroidDisplayProvider(private val context: Context) : DisplayProvider {

    override fun usableArea(): ScreenRect {
        val windowManager = context.getSystemService(WindowManager::class.java)
            ?: return ScreenRect(0f, 0f, 0f, 0f)

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = windowManager.maximumWindowMetrics
            val bounds = metrics.bounds
            val insets = metrics.windowInsets.getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout(),
            )
            ScreenRect(
                left = (bounds.left + insets.left).toFloat(),
                top = (bounds.top + insets.top).toFloat(),
                right = (bounds.right - insets.right).toFloat(),
                bottom = (bounds.bottom - insets.bottom).toFloat(),
            )
        } else {
            legacyArea(windowManager)
        }
    }

    /**
     * API 26-29 fallback. Estimated from framework dimensions: accurate on the overwhelming
     * majority of devices that still run those releases.
     */
    @Suppress("DEPRECATION")
    private fun legacyArea(windowManager: WindowManager): ScreenRect {
        val displayMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getRealMetrics(displayMetrics)

        val statusBar = frameworkDimension("status_bar_height")
        val navigationBar = frameworkDimension("navigation_bar_height")

        return ScreenRect(
            left = 0f,
            top = statusBar.toFloat(),
            right = displayMetrics.widthPixels.toFloat(),
            bottom = (displayMetrics.heightPixels - navigationBar).toFloat(),
        )
    }

    // status_bar_height / navigation_bar_height are internal framework resources (not in the
    // public SDK R), so name lookup is the only way to read them on API 26-29. Discouraged for
    // app resources, but unavoidable here; the modern path above never calls this.
    @Suppress("DiscouragedApi")
    private fun frameworkDimension(name: String): Int {
        val id = context.resources.getIdentifier(name, "dimen", "android")
        return if (id > 0) context.resources.getDimensionPixelSize(id) else 0
    }
}
