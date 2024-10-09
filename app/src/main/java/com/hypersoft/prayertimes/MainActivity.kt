package com.hypersoft.prayertimes

import android.os.Bundle
import android.util.Log
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.hypersoft.offlineprayertimes.managers.sharedPref.SharedPrefManager
import com.hypersoft.offlineprayertimes.managers.threads.PrayerTimeCoroutine
import com.hypersoft.offlineprayertimes.models.PrayerTimeModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel

class MainActivity : AppCompatActivity() {

    private var prayerTimeCoroutine: Job? = null
    private var sharedPrefManager: SharedPrefManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setPrayerAttributes()
        getTodayPrayerTimes()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        })

    }

    private fun setPrayerAttributes() {

        sharedPrefManager = SharedPrefManager(
            getSharedPreferences(
                "prayer_preferences",
                MODE_PRIVATE
            )
        )

        sharedPrefManager?.prayerLatitude = 33.601921.toBits()
        sharedPrefManager?.prayerLongitude = 73.038078.toBits()

    }

    private fun getTodayPrayerTimes() {

        prayerTimeCoroutine = object : PrayerTimeCoroutine(
            sharedPrefManager!!
        ) {

            override fun onPreExecute() {
                super.onPreExecute()
            }

            override fun onPostExecute(
                prayerTimeList: ArrayList<PrayerTimeModel>?,
                nextDayFajrTime: String?
            ) {
                super.onPostExecute(prayerTimeList, nextDayFajrTime)

                if (!prayerTimeList.isNullOrEmpty() && nextDayFajrTime?.isNotEmpty() == true) {
                    Log.d(
                        "prayerTimes", "onPostExecute: Size = ${prayerTimeList?.size} = " +
                                "Prayer = ${prayerTimeList!![5].prayerName} = ${prayerTimeList!![5].prayerTime}"
                    )
                }

                clearPrayerResources()
                cancelCoroutine()
            }

        }.executeCoroutine()

    }

    override fun onResume() {
        super.onResume()
        Log.d(
            "prayerTimes", "onResume MainActivity"
        )
    }

    private fun cancelCoroutine() {
        if (prayerTimeCoroutine != null && prayerTimeCoroutine?.isActive == true) {
            prayerTimeCoroutine?.cancel("coroutineCanceled", null)
        }
        prayerTimeCoroutine = null
    }

    override fun onDestroy() {
        Log.d(
            "prayerTimes", "onDestroy MainActivity"
        )
        cancelCoroutine()
        super.onDestroy()
    }

}