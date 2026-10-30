package com.husarp.holdfast

import com.husarp.holdfast.data.*
import org.junit.Assert.*
import org.junit.Test

class MuteRuleTest {
    private val row = CapturedNotification(packageName = "app", appLabel = "App", sourceKey = "key", channelId = "Offers", title = "Weekly SALE", text = "A Special offer", postedAt = 1, updatedAt = 1)
    @Test fun channelIsExactAndScopedToApp() {
        assertTrue(MuteRule(packageName = "app", kind = "Channel", value = "Offers").matches(row))
        assertFalse(MuteRule(packageName = "app", kind = "Channel", value = "offers").matches(row))
        assertFalse(MuteRule(packageName = "other", kind = "Channel", value = "Offers").matches(row))
        assertFalse(MuteRule(packageName = "app", kind = "Channel", value = "Offers").matches(row.copy(channelId = null)))
    }
    @Test fun textMatchesIgnoringCaseInChosenFieldOnly() {
        assertTrue(MuteRule(packageName = "app", kind = "Title", value = "sale").matches(row))
        assertTrue(MuteRule(packageName = "app", kind = "Text", value = "SPECIAL").matches(row))
        assertFalse(MuteRule(packageName = "app", kind = "Text", value = "sale").matches(row))
        assertFalse(MuteRule(packageName = "other", kind = "Title", value = "sale").matches(row))
    }
    @Test(expected = IllegalArgumentException::class) fun blankRulesAreRejected() {
        MuteRule(packageName = "app", kind = "Text", value = " ")
    }
}
