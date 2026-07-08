package com.example.fitness.Ui

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.fitness.data.models.Users
import com.example.fitness.data.models.Worksession
import com.example.fitness.data.repository.fitnessRepository
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.jvm.java
import kotlin.time.Duration

class FitnessViewmodel( private val repository: fitnessRepository, private val context: Context) : ViewModel() {

    val currentLocation: LiveData<LatLng> = repository.location

    // exhasting live data diclaration remain the same

    private val Distance = MutableLiveData<Float>(0f)
    val distance: LiveData<Float> = Distance

    private val Calories = MutableLiveData<Float>(0f)
    val calories: LiveData<Float> = Calories

    private val isTracking = MutableLiveData<Boolean>(false)
    val tracking: LiveData<Boolean> = isTracking

    private val Duration = MutableLiveData<Long>(0L)
    val duration: LiveData<Long> = Duration

    private val Pace = MutableLiveData<Double>(0.0)
    val pace: LiveData<Double> = Pace

    val routePoints: LiveData<List<LatLng>> = repository.RoutePoint

    private val CurrentUser = MutableLiveData<Users>()
    val currentUsers: LiveData<Users> = CurrentUser

    private var durationUpdateJob: Job? = null
    private var activeTime: Long = 0
    private var lastUpdateTime: Long = 0

    init {
        repository.TotalDistance.observeForever { newDistance ->
            Distance.value = newDistance
            UpdateCalories()
            UpdatePace()
        }

        // Observe repository tracking status
        repository.moving.observeForever { isMoving ->
            isTracking.value = isMoving
            if (!isMoving) {
                lastUpdateTime = 0
            }
        }
        lodeUserData()

    }

    private fun lodeUserData() {
        val currentLocation: LiveData<LatLng> = repository.location

        viewModelScope.launch {
            // Get SharedPreferences instance
            val sharedPref = context.getSharedPreferences("user_pref", Context.MODE_PRIVATE)

            // Load user data with default values if not found
            val user = Users(
                id = sharedPref.getString("user_id", UUID.randomUUID().toString()) ?: "",
                weight = sharedPref.getFloat("user_weight", 70f),  // Default 70kg
                height = sharedPref.getFloat("user_height", 170f),  // Default 170cm
                age = sharedPref.getInt("user_age", 25),  // Default 25 years old
            )
            CurrentUser.value = user

        }
    }

    private fun startDurationUpdate() {
        durationUpdateJob?.cancel()
        durationUpdateJob = viewModelScope.launch {
            while (isActive && isTracking.value == true) {
                Duration.value = repository.getCurrentTime()
                UpdateCalories()
                delay(1000)
            }
        }
    }

    fun startWorkout() {
        viewModelScope.launch {
            Duration.value = 0
            Calories.value = 0f
            Pace.value = 0.0
            Distance.value = 0f
            activeTime = 0
            lastUpdateTime = System.currentTimeMillis()
            isTracking.value = true
            repository.startTracking()
            startDurationUpdate()
        }
    }

    private fun UpdateCalories() {
        val weight = CurrentUser.value?.weight ?: 70f
        val distance = Distance.value ?: 0f
        val duration = Duration.value ?: 0L

        Log.d(
            "FitnessViewModel",
            "Updating calories with weight: $weight, distance: $distance, duration: $Duration"
        )

        val newCaloties = repository.calculateCalory(
            weight = weight,
            distance = distance,
            duration = duration
        )
        Log.d("FitnessViewModel", "New Calories value: $newCaloties")
        Calories.value = newCaloties
    }

    private fun UpdatePace() {
        val distance = Distance.value ?: 0f
        val duration = Duration.value ?: 0L
        if (duration > 0) {
            Pace.value = repository.calculatePace(distance, duration)
        }
    }

    fun stopWorkout() {
        isTracking.value = false
        repository.stopTracking()
        stopDurationUpdate()
        lastUpdateTime = 0
        activeTime = 0
        saveWorkoutsession()
    }

    fun pauseWorkout() {
        isTracking.value = false
        stopDurationUpdate()
        repository.stopTracking()
        lastUpdateTime = 0
    }

    fun resumeWorkout() {
        viewModelScope.launch {
            lastUpdateTime = System.currentTimeMillis()
            repository.startTracking()
            startDurationUpdate()
            isTracking.value = true
        }
    }

    fun clearWorkout() {
        Duration.value = 0L
        Distance.value = 0f
        Calories.value = 0f
        Pace.value = 0.0
        activeTime = 0
        lastUpdateTime = 0
        repository.clearTracking()
    }

    // Keep existing formatting functions
    fun formateDuration(duration: Long): String {
        val second = (duration / 1000) % 60
        val minute = (duration / (1000 * 60)) % 60
        val hours = duration / (1000 * 60 * 60)
        return String.format("%02d:%02d:%02d", hours, minute, second)

    }

    fun formateDistance(distance: Float): String {
        return String.format("%2f km", distance)
    }

    fun formatePace(pace: Double): String {
        return String.format("%2f min/km", pace)
    }

    fun formateCalories(calories: Float): String {
        return String.format("%0f kcal", calories)
    }


    private fun saveWorkoutsession() {
        viewModelScope.launch {
            val session = Worksession(
                id = UUID.randomUUID().toString(),
                duration = Duration.value ?: 0L,
                distance = Distance.value ?: 0f,
                caloriesBurned = Calories.value ?: 0f,
                timestape = System.currentTimeMillis(),
                avaragePace = Pace.value ?: 0.0,
                routePoints = routePoints.value ?: emptyList()

            )
            // Implement saving logic here
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopDurationUpdate()
        repository.stopTracking()
    }

    private fun stopDurationUpdate() {
        durationUpdateJob?.cancel()
        durationUpdateJob = null
        Duration.value = repository.getCurrentTime()

    }

    class Factory(
        private val repository: fitnessRepository,
        private val context: Context  // Add context parameter
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(FitnessViewmodel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return FitnessViewmodel(repository, context) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }





}



