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

    }
}
