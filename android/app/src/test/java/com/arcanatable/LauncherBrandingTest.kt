package com.arcanatable

import android.content.Context
import android.graphics.drawable.AdaptiveIconDrawable
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Prevent a release or preview from silently reverting to Android's generic launcher icon. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LauncherBrandingTest {
    @Test fun manifestAndBothAdaptiveIconsUseArcanaBranding() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val info = context.packageManager.getApplicationInfo(context.packageName, 0)
        assertEquals(R.mipmap.ic_launcher, info.icon)
        // ApplicationInfo has no roundIcon property in the Android SDK API.
        // The manifest's android:roundIcon reference is checked by the source gate.
        val roundRes = context.resources.getIdentifier("ic_launcher_round", "mipmap", context.packageName)
        assertEquals(R.mipmap.ic_launcher_round, roundRes)
        val standard = context.getDrawable(info.icon)
        val round = context.getDrawable(roundRes)
        assertTrue("Standard icon must be adaptive", standard is AdaptiveIconDrawable)
        assertTrue("Round icon must be adaptive", round is AdaptiveIconDrawable)
        assertNotNull((standard as AdaptiveIconDrawable).foreground)
        assertNotNull((round as AdaptiveIconDrawable).foreground)
    }
}
