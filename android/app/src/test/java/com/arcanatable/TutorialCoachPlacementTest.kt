package com.arcanatable

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Build 137 issue #7: instruction cards never occupy the highlighted tap target. */
class TutorialCoachPlacementTest {
    private fun assertClear(region: TutorialCoachRegion, target: TutorialCoachTarget) {
        val intersects = region.x < target.right && region.x + region.width > target.left &&
            region.y < target.bottom && region.y + region.height > target.top
        assertFalse("Coach " + region.dock + " region " + region + " covers highlighted target " + target, intersects)
        assertTrue(region.width > 0.dp && region.height > 0.dp)
    }

    @Test fun foldedPortraitNearBottomDocksAbove() {
        val target = TutorialCoachTarget(16.dp, 780.dp, 391.dp, 860.dp)
        val region = tutorialCoachRegion(411.dp, 891.dp, target)
        assertEquals("above", region.dock)
        assertTrue(region.bottomAligned)
        assertClear(region, target)
    }

    @Test fun foldedPortraitNearTopDocksBelow() {
        val target = TutorialCoachTarget(16.dp, 20.dp, 390.dp, 85.dp)
        val region = tutorialCoachRegion(411.dp, 891.dp, target)
        assertEquals("below", region.dock)
        assertClear(region, target)
    }

    @Test fun middleControlOnShortFoldedWindowLeavesClearScrollableArea() {
        val target = TutorialCoachTarget(16.dp, 180.dp, 395.dp, 275.dp)
        val region = tutorialCoachRegion(411.dp, 460.dp, target)
        assertTrue(region.dock == "above" || region.dock == "below")
        assertClear(region, target)
        assertTrue("Long lesson text should be constrained to the free region", region.height < 180.dp)
    }

    @Test fun unfoldedLandscapeUsesSideRailWhenVerticalSpaceIsShort() {
        val target = TutorialCoachTarget(310.dp, 130.dp, 490.dp, 280.dp)
        val region = tutorialCoachRegion(841.dp, 410.dp, target)
        assertEquals("right", region.dock)
        assertClear(region, target)
    }

    @Test fun leftSideRailWorksWhenRightSideIsTooSmall() {
        val target = TutorialCoachTarget(490.dp, 130.dp, 680.dp, 280.dp)
        val region = tutorialCoachRegion(841.dp, 410.dp, target)
        assertEquals("left", region.dock)
        assertClear(region, target)
    }

    @Test fun textOnlyStepHasAnOnscreenCoach() {
        val region = tutorialCoachRegion(411.dp, 891.dp, null)
        assertEquals("top", region.dock)
        assertEquals(12.dp, region.x)
        assertEquals(12.dp, region.y)
        assertTrue(region.height > 100.dp)
    }
}
