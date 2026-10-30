package com.husarp.holdfast.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class QuietPlace(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radius: Float,
    val packages: String,
    val inside: Boolean = false,
) {
    init {
        require(name.isNotBlank())
        require(latitude.isFinite() && latitude in -90.0..90.0)
        require(longitude.isFinite() && longitude in -180.0..180.0)
        require(radius.isFinite() && radius > 0)
    }
}
fun placeQuiets(places: List<QuietPlace>, packageName: String, permitted: Boolean) =
    permitted && places.any { it.inside && packageName in it.packages.lines() }
