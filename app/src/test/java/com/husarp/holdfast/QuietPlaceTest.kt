package com.husarp.holdfast
import com.husarp.holdfast.data.*
import org.junit.Assert.*
import org.junit.Test
class QuietPlaceTest {
    private val place = QuietPlace(1, "Home", 50.0, 20.0, 200f, "app\nother", true)
    @Test fun insideAndSelected() { assertTrue(placeQuiets(listOf(place), "app", true)); assertFalse(placeQuiets(listOf(place), "unknown", true)) }
    @Test fun outsideAndDenied() { assertFalse(placeQuiets(listOf(place.copy(inside = false)), "app", true)); assertFalse(placeQuiets(listOf(place), "app", false)); assertFalse(placeQuiets(emptyList(), "app", true)) }
    @Test(expected = IllegalArgumentException::class) fun invalidRadius() { place.copy(radius = -1f) }
}
