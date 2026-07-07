package com.example.fitness.data.repository

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.LatLng

class fitnessRepository(private val context: Context) {
    private val locationClient = LocationServices.getFusedLocationProviderClient(context)
    private var lastValidLocation: Location? = null
    private var isAcctuallyMoving = false
    private var totalDuration : Long = 0
    private var totalStartTime: Long = 0
    private val movement_Threshold = 1.0f

    //Live Data
    private val routePoints = MutableLiveData<List<LatLng>>(emptyList())
    val RoutePoint: LiveData<List<LatLng>> = routePoints

    private val totalDistance = MutableLiveData<Float>(0f)
    val TotalDistance: LiveData<Float> = totalDistance

    private val isTracking = MutableLiveData<Boolean>(false)
    val moving: LiveData<Boolean> = isTracking

    private val currentLocation = MutableLiveData<LatLng>()
    val location: LiveData<LatLng> = currentLocation

    //time variables
    private var startTime: Long = 0
    private var activeMovementTime: Long = 0
    private var lastMovementTime: Long = 0
    private var lastLocationUpdateTime: Long = 0

    // Location Tracking
    private val locationCallBack = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            val location = locationResult.lastLocation
            location?.let {newlocation ->
                Log.d("FitnessRepository", "Location received: ${newlocation.latitude}, ${newlocation.longitude}")
                val curentTime = System.currentTimeMillis()
                currentLocation.postValue(LatLng(newlocation.latitude, newlocation.longitude))

                // accuracy check
                if( newlocation.accuracy >20f){
                    Log.d("FitnessRepository", "Location accuracy is too poor: ${newlocation.accuracy}")
                    isAcctuallyMoving = false
                    return
                }

                lastValidLocation?.let { lastLocation ->
                    val distance = newlocation.distanceTo(lastLocation)
                    val timeGaph = curentTime - lastLocationUpdateTime

                    val speed = if (timeGaph > 0) (distance * 1000 / timeGaph) else 0f

                    isAcctuallyMoving = distance > movement_Threshold &&
                            speed < 8f &&  // max speed ~29 km/h
                            speed > 0.3f   // min speed ~1 km/h


                    if (isAcctuallyMoving) {
                        updateLocationData(newlocation)
                        updateMovementTime(curentTime)

                        Log.d("FitnessRepository", "Valid Moment = $distance meter , $speed m/s")
                    } else {
                        Log.d("FitnessRepository", "Invalid Moment = $distance meter , $speed m/s")
                    }
                }
                        lastValidLocation = newlocation
                    lastLocationUpdateTime = curentTime
                }
            }

        }

        fun updateMovementTime(curentTime: Long) {
            if (lastMovementTime > 0){
                activeMovementTime += curentTime - lastMovementTime

            }
            lastMovementTime = curentTime
        }

        fun getCurrentTime (): Long{
            return if(isTracking.value == true){
                System.currentTimeMillis() - totalStartTime
            }else{
                totalDuration
            }
        }

        fun updateLocationData(newlocation: Location) {
            if (!isAcctuallyMoving){
                Log.d("FitnessRepository", "Location is not moving")
                return
            }

            val lating = LatLng(newlocation.latitude, newlocation.longitude)
            val currentRoutePoints = routePoints.value?.toMutableList()?: mutableListOf()

            if(currentRoutePoints.isEmpty()){
                currentRoutePoints .add(lating)
                routePoints.postValue(currentRoutePoints)
                Log.d("FitnessRepository", "First Location Added")
                return
            }

            // Only add points if we've moved enough
            val distanceFromLast = calculateDistance( currentRoutePoints.last() , lating)
            if (distanceFromLast > movement_Threshold / 1000f){
                currentRoutePoints.add(lating)
                routePoints.postValue(currentRoutePoints)

                //update total distance
                val currentTotal = totalDistance.value ?: 0f
                val newTotal = currentTotal + distanceFromLast
                totalDistance.postValue(newTotal)
                Log.d("FitnessRepository","Added point to route.Distance: $distanceFromLast km, Total: $newTotal km")
            }

            lastValidLocation = newlocation
        }




        private fun requestLocationUpdates() {
            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000)
//                priority = LocationRequest.PRIORITY_HIGH_ACCURACY
//                interval = 1000 // updates every second
//                fastestInterval = 500 //fastest possible updates
//                smallestDisplacement = 1f //min distance 1 meter
                .setMinUpdateIntervalMillis(500)
                .setMinUpdateDistanceMeters(1f)
                .build()



            if (checkLocationPermission()){
                try {
                    locationClient.requestLocationUpdates(
                        locationRequest,
                        locationCallBack,
                        context.mainLooper
                    )
                    Log.d("FitnessRepository", "Location updates requested")

                }catch (e: Exception){
                    Log.e("FitnessRepository", "Error requesting location updates", e)
                }

            }
        }


        fun startTracking(){
            if (checkLocationPermission()){
                initializeTracking()
                requestLocationUpdates()
            }
        }

        private fun initializeTracking() {
            startTime = System.currentTimeMillis()
            totalStartTime = System.currentTimeMillis()
            isTracking.postValue(true)
        }


        fun stopTracking(){
            isTracking.postValue(false)
            locationClient.removeLocationUpdates(locationCallBack)
            totalDuration = System.currentTimeMillis() - totalStartTime
            resetTimer()
        }

        private fun resetTimer() {
            lastMovementTime = 0
            activeMovementTime = 0
            totalDuration = 0
            totalStartTime = 0
        }

        fun clearTracking(){
            routePoints.postValue(emptyList())
            totalDistance.postValue(0f)
            totalDuration = 0
            resetTimer()
            lastValidLocation = null
            isAcctuallyMoving = false
        }



        private fun checkLocationPermission(): Boolean {
            return ActivityCompat.checkSelfPermission(context,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(context,
                        Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        }

        private fun calculateDistance(Point1: LatLng, Point2: LatLng): Float {
            val results = FloatArray(1)
            Location.distanceBetween(
                Point1.latitude, Point1.longitude,
                Point2.latitude, Point2.longitude,
                results
            )
            return results[0] / 1000f // Convert to kilometers
        }

        fun calculateCalory(weight: Float, distance: Float, duration: Long): Float {

            if (duration<1000){
                return 0f
            }
            val hours = duration/(1000.0 * 60.0 * 60.0)

            if (hours < 0) return 0f

            val speed = if (hours > 0 ) distance/hours else 0.0

            val met = when{
                speed <= 4.0 -> 2.0 //walking
                speed <= 8.0 -> 7.0 //slow jogging
                speed <= 11.0 -> 8.5 //medium pace
                else -> 10.0 // running
            }

            val calories = (met * weight * hours).toFloat()
            Log.d("FitnessRepository", "Calories: $calories (MET : $met, SPEED: $speed km/h)")
            return calories
        }

        fun calculatePace(distance: Float, duration: Long): Double {
            if (distance <= 0 || duration <=0) return 0.0

            val hours = duration/ (1000.0 * 60.0 * 60.0)

            return distance/hours

        }

    }
