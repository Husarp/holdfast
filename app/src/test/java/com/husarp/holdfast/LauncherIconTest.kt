package com.husarp.holdfast

import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import androidx.core.graphics.drawable.toBitmap
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LauncherIconTest {
    private val context = ApplicationProvider.getApplicationContext<HoldfastApplication>()

    @Test fun launcherUsesPinnedCardColorsAndTransparentCutouts() {
        val icon = context.getDrawable(R.mipmap.ic_launcher) as AdaptiveIconDrawable
        assertEquals(Color.rgb(0, 105, 92), icon.background.toBitmap(108, 108).getPixel(54, 54))
        val foreground = icon.foreground.toBitmap(108, 108)
        assertEquals(Color.rgb(184, 242, 223), foreground.getPixel(40, 42))
        val mono = requireNotNull(icon.monochrome).toBitmap(108, 108)
        assertEquals(Color.WHITE, mono.getPixel(40, 42))
        listOf(45 to 49, 45 to 58, 66 to 54, 66 to 35, 10 to 10).forEach { (x, y) ->
            assertEquals("Transparent cutout at $x,$y", 0, Color.alpha(mono.getPixel(x, y)))
        }
        assertEquals(Color.WHITE, mono.getPixel(66, 29))
    }

    @Test @Config(sdk = [26]) fun minimumSdkLoadsLauncherAndWhiteNotificationSilhouette() {
        val info = context.packageManager.getApplicationInfo(context.packageName, 0)
        assertTrue(context.getDrawable(info.icon) is AdaptiveIconDrawable)
        val bitmap = requireNotNull(context.getDrawable(R.drawable.ic_holdfast)).toBitmap(24, 24)
        var visiblePixels = 0
        for (y in 0 until 24) for (x in 0 until 24) {
            val pixel = bitmap.getPixel(x, y)
            if (Color.alpha(pixel) > 0) {
                visiblePixels++
                assertEquals(0xFFFFFF, pixel and 0xFFFFFF)
            }
        }
        assertTrue(visiblePixels > 100)
        assertEquals(0, Color.alpha(bitmap.getPixel(0, 0)))
    }
}
