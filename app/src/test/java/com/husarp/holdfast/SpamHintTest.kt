package com.husarp.holdfast
import com.husarp.holdfast.data.shouldSuggestSpam
import org.junit.Assert.*
import org.junit.Test
class SpamHintTest {
    @Test fun threshold() { assertFalse(shouldSuggestSpam(19, false, false)); assertTrue(shouldSuggestSpam(20, false, false)) }
    @Test fun opensAndDecisionsPreventHints() { assertFalse(shouldSuggestSpam(30, true, false)); assertFalse(shouldSuggestSpam(30, false, true)) }
}
