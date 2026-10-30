package com.husarp.holdfast

import com.husarp.holdfast.update.*
import org.junit.Assert.*
import org.junit.Test

class UpdateTest {
    @Test fun comparesNumericVersions() {
        assertTrue(UpdateLogic.isNewer("3.10.0", "3.9.0"))
        assertEquals(0, UpdateLogic.compare("v0.1.0", "0.1"))
        assertFalse(UpdateLogic.isNewer("0.1.0", "0.2.0"))
    }
    @Test fun throttleBoundaryAndClockReversal() {
        assertTrue(UpdateLogic.mayCheck(1, 0))
        assertFalse(UpdateLogic.mayCheck(300_000, 1))
        assertTrue(UpdateLogic.mayCheck(300_001, 1))
        assertTrue(UpdateLogic.mayCheck(1, 10))
    }
    @Test fun bannerDismissalSurvivesResumeUntilFreshStart() {
        val gate = UpdateLogic.BannerGate()
        assertTrue(gate.visible("0.2.0", "0.1.0"))
        gate.dismiss()
        assertFalse(gate.visible("0.2.0", "0.1.0"))
        assertFalse(gate.visible("0.3.0", "0.1.0"))
        gate.onAppStart()
        assertTrue(gate.visible("0.2.0", "0.1.0"))
    }
    @Test fun newestStableApkAcrossRecentReleases() {
        val releases = listOf(
            UpdateLogic.Release("new", emptyList()),
            UpdateLogic.Release("older", listOf(UpdateLogic.Asset("Anything-0.10.0.apk", "url", 20))),
            UpdateLogic.Release("preview", listOf(UpdateLogic.Asset("0.11.0.apk", "url", 20)), prerelease = true))
        assertEquals("0.10.0", UpdateLogic.newestApk(releases)!!.third)
    }
    @Test fun parseSortAndFilterBundledChangelog() {
        val entries = WhatsNew.parse("# ignored\n## 0.1.0\n- First\n## 0.10.0\n- **New** @ Settings\n### Fixes\n- Fixed\n## 0.2.0\n- Second")
        assertEquals(listOf("0.10.0", "0.2.0", "0.1.0"), entries.map { it.version })
        assertEquals("New", entries.first().items.single().text)
        assertEquals("Settings", entries.first().items.single().path)
        assertEquals(1, entries.first().fixes.size)
        assertEquals(2, WhatsNew.upTo(entries, "0.2.0").size)
    }
    @Test fun installedCardOnlyAfterUpdate() {
        assertEquals(WhatsNew.Action.MARK_SEEN, WhatsNew.decide(null, "0.1.0", false))
        assertEquals(WhatsNew.Action.SHOW, WhatsNew.decide(null, "0.1.0", true))
        assertEquals(WhatsNew.Action.NONE, WhatsNew.decide("0.1.0", "0.1.0", true))
        assertEquals(WhatsNew.Action.SHOW, WhatsNew.decide("0.1.0", "0.2.0", true))
    }
}
