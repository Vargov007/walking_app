package com.example.fitness

import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.preference.PreferenceManager
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.fitness.Ui.FitnessViewmodel
import com.example.fitness.data.repository.fitnessRepository
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.LatLng
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.api.IMapController
import org.osmdroid.util.GeoPoint

import kotlin.getValue

class MainActivity : AppCompatActivity() {

    private lateinit var map: MapView
    private lateinit var currentLocationMarker: Marker
    private lateinit var startStopButton: Button
    private lateinit var distanceValue: TextView
    private lateinit var caloriesValue: TextView
    private lateinit var pathPolyline: Polyline
    private var firstLocationUpdate = true
    private lateinit var durationValue: TextView
    private val viewModel: FitnessViewmodel by viewModels(){
        FitnessViewmodel.Factory(fitnessRepository(this), this)
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            startTracking()
        }
    }

    fun startTracking() {
        requestPermissionLauncher.launch(
            arrayOf(
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Initialize OpenStreetMap
        org.osmdroid.config.Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this))
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)


        // Initialize views
        map = findViewById(R.id.map)
        startStopButton = findViewById(R.id.startStopButton)
        distanceValue = findViewById(R.id.distanceValue)
        caloriesValue = findViewById(R.id.caloriesValue)
        durationValue = findViewById(R.id.DurationValue)

        // Setup map - this will initialize markers and start location updates
        setupMap()

        // Setup click listeners
        startStopButton.setOnClickListener {
            if (viewModel.tracking.value == true) {
                stopTracking()
            } else {
                checkPermissionsAndStartTracking()
            }
        }

        // Setup observers
        setupObservers()
    }


    private fun setupMap() {
        map.setTileSource(org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        val mapController = map.controller
        mapController.setZoom(18.0)

        //Initialize the marker
        currentLocationMarker = Marker(map).apply {
            setAnchor(Marker.ANCHOR_CENTER , Marker.ANCHOR_BOTTOM)
            icon = ContextCompat.getDrawable(this@MainActivity, R.drawable.baseline_location_on_24)
            title = "Current Location"
        }

        map.overlays.add(currentLocationMarker)

        //Initialize path polyline
        pathPolyline = Polyline(map).apply {
            outlinePaint.color = Color.BLUE
            outlinePaint.strokeWidth = 10f
        }
        map.overlays.add(pathPolyline)

        // for start location updates
        startLocationUpdates()


    }

    private fun startLocationUpdates() {
        if(!checkLocationPermission()) return

        val locationRequest = LocationRequest.create().apply {
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
            interval = 1000
            fastestInterval = 500
        }

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: com.google.android.gms.location.LocationResult) {
                val location = locationResult.lastLocation
                location?.let { location ->
                    val lating = LatLng(location.latitude, location.longitude)
                    updateLocationMarker(lating)
                    Log.d("MainActivity", "Location received: ${location.latitude}, ${location.longitude}")

                }
        }
    }

        try {
            LocationServices.getFusedLocationProviderClient(this)
                .requestLocationUpdates(locationRequest, locationCallback, mainLooper)
        }catch (e: SecurityException){
            Log.d("MainActivity", "Error requesting location update", e)
        }
    }

    private fun updateLocationMarker(lating: LatLng) {
        Log.d("MainActivity", "Updating location marker: ${lating.latitude}, ${lating.longitude}")
        val geoPoint = GeoPoint(lating.latitude, lating.longitude)

        //update marker update
        currentLocationMarker.position = geoPoint

        // Center map on first location update
        if(firstLocationUpdate){
            map.controller.setZoom(18.0)
            map.controller.setCenter(geoPoint)
            firstLocationUpdate = false
            Log.d("MainActivity", "First location update - centered map")
        }else if( viewModel.tracking.value == true){
            map.controller.animateTo(geoPoint)

        }

        map.invalidate()

    }

    private fun checkLocationPermission(): Boolean {
        return (ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED)
    }

    private fun requestCurrentLocation(){
        if(checkLocationPermission()){
            val locationClient = LocationServices.getFusedLocationProviderClient(this)
            locationClient.lastLocation.addOnSuccessListener {location ->
                location?.let {
                    val lating = LatLng(location.latitude, location.longitude)
                    updateLocationMarker(lating)
                    Log.d("MainActivity", "Current location: ${location.latitude}, ${location.longitude}")
                }
            }
        }
    }


    fun checkPermissionsAndStartTracking() {
        if (!checkLocationPermission()) {
            startTracking()
        }else{
            requestPermissionLauncher.launch(
                arrayOf(
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }

    }

    fun stopTracking() {
        viewModel.stopWorkout()    }


    private fun setupObservers() {
        viewModel.tracking.observe(this){ isTracking ->
            Log.d("MainActivity", "Tracking status changed: $isTracking")
            startStopButton.text = if (isTracking) "Stop" else "Start"
        }
        viewModel.duration.observe(this){duration ->
            durationValue.text = viewModel.formateDuration(duration)
            Log.d("MainActivity", "Duration updated: ${viewModel.formateDuration(duration)}")

        }
        viewModel.routePoints.observe(this){points ->
            Log.d("MainActivity", "Received ${points.size} route points")
            if (points.isNotEmpty()){
                updateRouteOnMap(points)
            }
        }
        viewModel.currentLocation.observe(this){location ->
            updateLocationMarker(location)
            Log.d("MainActivity", "Marker updated to: ${location.latitude}, ${location.longitude}")
        }
        viewModel.calories.observe(this){ calories ->
            caloriesValue.text = viewModel.formateCalories(calories)
            Log.d("MainActivity", "Calories updated: $calories")
        }
        viewModel.distance.observe(this){distance ->
            Log.d("MainActivity", "Distance updated: $distance")
            distanceValue.text = viewModel.formateDistance(distance)
        }
    }

    private fun enableMyLocation(){
        if(checkLocationPermission()){
            getCurrentLocation()
        }
    }

    private fun getCurrentLocation() {
        if (checkLocationPermission()){
            val locationClient = LocationServices.getFusedLocationProviderClient(this)
            locationClient.lastLocation.addOnSuccessListener { location ->
                location?.let {
                    val lating = LatLng(location.latitude, location.longitude)
                    Log.d("MainActivity", "Initial location: ${location.latitude}, ${location.longitude}")
                    updateLocationMarker(lating)
                }?.run {
                    // If last location is null, request a fresh location
                    requestFreshLocation()
                }
            }
        }
    }

    private fun requestFreshLocation() {
        if (checkLocationPermission()){
            val locationRequest = LocationRequest.create().apply {
                priority = LocationRequest.PRIORITY_HIGH_ACCURACY
                interval = 1000
                numUpdates = 1
            }
            val locationCallback = object: LocationCallback(){
                override fun onLocationResult(result: com.google.android.gms.location.LocationResult) {
                    result.lastLocation?.let {location ->
                        val lating = LatLng(location.latitude, location.longitude)
                        Log.d("MainActivity", "Fresh location: ${location.latitude}, ${location.longitude}")
                        updateLocationMarker(lating)
                    }

                    // Remove updates after getting location
                    LocationServices.getFusedLocationProviderClient(this@MainActivity)
                        .removeLocationUpdates(this)
                }
            }

            LocationServices.getFusedLocationProviderClient(this)
                .requestLocationUpdates(locationRequest, locationCallback, mainLooper)
        }
    }


    fun updateRouteOnMap(points: List<LatLng>) {
        if (points.isEmpty()) return

        try {
            val geoPoints = points.map { GeoPoint(it.latitude, it.longitude) }
            val currentLocation = geoPoints.last()

            // Update marker and path
            // currentLocationMarker.position = currentLocation

            updateLocationMarker(LatLng(currentLocation.longitude, currentLocation.latitude))
            pathPolyline.setPoints(geoPoints)

            // Keep map centered on current location
            if (viewModel.tracking.value == true){
                map.controller.animateTo(currentLocation)
            }
            map.invalidate()

            Log.d("MainActivity", "Updated location: ${currentLocation.latitude}, ${currentLocation.longitude}")
        }catch (e: Exception){
            Log.e("MainActivity", "Error updating route on map", e)
        }
    }

    override fun onPause() {
        super.onPause()
        map.onPause()
    }

    override fun onResume() {
        super.onResume()
        map.onResume()
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.stopWorkout()
    }
}
