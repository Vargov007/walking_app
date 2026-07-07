package com.example.fitness.data.models

import com.google.android.gms.maps.model.LatLng

data class Worksession(
    val id: String,
    val distance: Float, // in kilometers
    val duration: Long, // in milliseconds
    val caloriesBurned: Float,
    val timestape: Long,
    val avaragePace: Double, // in minutes per kilometer
    val routePoints: List<LatLng>,  // List of LatLng points representing the route
)
