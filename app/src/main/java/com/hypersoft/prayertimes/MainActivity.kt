package com.hypersoft.prayertimes

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.Priority
import com.hypersoft.offlineprayertimes.managers.sharedPref.SharedPrefManager
import com.hypersoft.offlineprayertimes.managers.threads.PrayerTimeCoroutine
import com.hypersoft.prayertimes.adapters.PrayersAdapter
import com.hypersoft.prayertimes.databinding.ActivityMainBinding
import com.hypersoft.prayertimes.extensions.isGpsNetworkProviderEnabled
import com.hypersoft.prayertimes.extensions.isLocationFineCoarseApproved
import com.hypersoft.prayertimes.interfaces.PrayerItemClickInterface
import com.hypersoft.prayertimes.models.PrayerModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel

class MainActivity : AppCompatActivity(), PrayerItemClickInterface {

    private var prayerTimeCoroutine: Job? = null
    private var sharedPrefManager: SharedPrefManager? = null
    private val prayerAdapter by lazy { PrayersAdapter(this) }
    private var prayersList: ArrayList<PrayerModel>? = null
    private lateinit var prayerRecyclerView: RecyclerView
    private lateinit var binding: ActivityMainBinding

    private lateinit var locationManager: LocationManager
    private var isAlreadyRequestingLocation = false
    private var isPermissionDontAskCheck = false
    private var currentLocation: Location? = null
    private var fusedLocationProviderClient: FusedLocationProviderClient? = null
    private val interval: Long = 5000 // 5seconds
    private val fastestInterval: Long = 1000 // 1seconds
    private lateinit var mLocationRequest: LocationRequest

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        initPreferences()
        initLocationPermission()
        initRecyclerView()
        setupClicks()
        initBackPress()

    }

    private fun initBackPress() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        })
    }

    private fun setupClicks() {
        binding.permissionButton.setOnClickListener {
            checkLocationGetterPermissions()
        }
    }

    private fun initPreferences() {
        sharedPrefManager = SharedPrefManager(
            getSharedPreferences(
                "prayer_preferences",
                MODE_PRIVATE
            )
        )
    }

    private fun initLocationPermission() {
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        fusedLocationProviderClient =
            LocationServices.getFusedLocationProviderClient(this)
        mLocationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY, // Priority of the request
            interval // Interval in milliseconds (e.g., 10 seconds)
        ).apply {
            setMinUpdateIntervalMillis(fastestInterval) // Minimum time between location updates
        }.build()

        val builder = LocationSettingsRequest.Builder()
        builder.addLocationRequest(mLocationRequest)
        val locationSettingsRequest = builder.build()
        val settingsClient = LocationServices.getSettingsClient(this)
        settingsClient.checkLocationSettings(locationSettingsRequest)

        fusedLocationProviderClient =
            LocationServices.getFusedLocationProviderClient(this)
    }

    private fun initRecyclerView() {
        prayersList = ArrayList()
        prayerRecyclerView = binding.prayerRecyclerView
        prayerRecyclerView.layoutManager = LinearLayoutManager(this)
        prayerRecyclerView.adapter = prayerAdapter
    }

    private fun getTodayPrayerTimes() {

        prayerTimeCoroutine = object : PrayerTimeCoroutine(
            sharedPrefManager!!
        ) {

            override fun onPreExecute() {
                super.onPreExecute()
            }

            override fun onPostExecute(
                prayerTimeList: ArrayList<com.hypersoft.offlineprayertimes.models.PrayerTimeModel>?,
                nextDayFajrTime: String?
            ) {
                super.onPostExecute(prayerTimeList, nextDayFajrTime)

                if (!prayerTimeList.isNullOrEmpty() && prayerTimeList.size == 7 && nextDayFajrTime?.isNotEmpty() == true) {
                    if (!prayersList.isNullOrEmpty()) {
                        prayersList?.clear()
                    }
                    for (item in prayerTimeList) {
                        val prayerModel = PrayerModel()
                        prayerModel.prayerTime = item.prayerTime
                        prayerModel.prayerName = item.prayerName
                        prayersList?.add(prayerModel)
                    }
                    prayersList?.let { prayList ->
                        prayerAdapter.submitList(prayList)
                    }
                }

                clearPrayerResources()
                cancelCoroutine()
            }

        }.executeCoroutine()

    }

    override fun onResume() {
        super.onResume()
        isAlreadyRequestingLocation = false
        if (isLocationFineCoarseApproved() && isGpsNetworkProviderEnabled(
                locationManager
            )
        ) {
            binding.permissionButton.isEnabled = false
            binding.permissionButton.text = getString(R.string.permission_granted_text)
            Log.d("prayersData", "onResume: Inside Fun StartGetter")
            startLocationGetter()
        }
    }

    private fun startLocationGetter() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        isAlreadyRequestingLocation = true
        fusedLocationProviderClient?.requestLocationUpdates(
            mLocationRequest,
            mLocationCallback,
            Looper.myLooper()
        )
    }

    private val mLocationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {

            Log.d("prayersData", "onLocationChanged: Inside Location Changed Listener")
            currentLocation = if (locationResult.locations.isNotEmpty()) {
                locationResult.locations.last()
            } else {
                if (locationResult.lastLocation != null) {
                    locationResult.lastLocation
                } else {
                    null
                }
            }
            if (currentLocation != null) {

                sharedPrefManager?.prayerLatitude =
                    currentLocation?.latitude?.toBits() ?: 0L
                sharedPrefManager?.prayerLongitude =
                    currentLocation?.longitude?.toBits() ?: 0L
                if (!isFinishing) {
                    getTodayPrayerTimes()
                }
                pauseLocation()

            }

        }
    }

    private fun cancelCoroutine() {
        if (prayerTimeCoroutine != null && prayerTimeCoroutine?.isActive == true) {
            prayerTimeCoroutine?.cancel("coroutineCanceled", null)
        }
        prayerTimeCoroutine = null
    }

    override fun onPrayerItemClick(
        prayerModel: PrayerModel,
        position: Int
    ) {
        Toast.makeText(
            applicationContext,
            "Clicked Prayer = ${prayerModel.prayerName}",
            Toast.LENGTH_SHORT
        ).show()
    }

    override fun onDestroy() {
        isAlreadyRequestingLocation = false
        if (::prayerRecyclerView.isInitialized) {
            prayerRecyclerView.adapter = null
        }
        Log.d(
            "prayerTimes", "onDestroy MainActivity"
        )
        cancelCoroutine()
        if (prayersList != null && prayersList?.isNotEmpty() == true) {
            prayersList?.clear()
        }
        prayersList = null
        super.onDestroy()
    }

    override fun onPause() {
        super.onPause()
        pauseLocation()
    }

    private fun pauseLocation() {
        if (isLocationFineCoarseApproved()) {
            if (::locationManager.isInitialized && fusedLocationProviderClient != null) {
                isAlreadyRequestingLocation = false
//                isAlreadyRequestingGPS = false
                fusedLocationProviderClient?.removeLocationUpdates(mLocationCallback)
            }
        }
    }

    private fun checkLocationGetterPermissions() {

        if (isLocationFineCoarseApproved()) {
            if (isGpsNetworkProviderEnabled(locationManager)) {
                if (sharedPrefManager?.prayerLatitude == 0L || sharedPrefManager?.prayerLongitude == 0L
                ) {
                    if (isAlreadyRequestingLocation) {
                        pauseLocation()
                    }
                    startLocationGetter()
                }
            } else {
                val settingsIntent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                startActivity(settingsIntent)
            }
        } else {
            if (isPermissionDontAskCheck) {

                val settingsIntent =
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                val uri = Uri.fromParts("package", packageName, null)
                settingsIntent.setData(uri)
                startActivity(settingsIntent)

            } else {
                locationPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        }

    }

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->

        val isFineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val isCoarseLocationGranted =
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

        if (isFineLocationGranted || isCoarseLocationGranted) {

        } else {
            if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) ||
                shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION)
            ) {

            } else {
                if (!this.isFinishing) {
                    isPermissionDontAskCheck = true
                    val settingsIntent =
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    val uri = Uri.fromParts("package", packageName, null)
                    settingsIntent.setData(uri)
                    startActivity(settingsIntent)
                }
            }
        }

    }

}