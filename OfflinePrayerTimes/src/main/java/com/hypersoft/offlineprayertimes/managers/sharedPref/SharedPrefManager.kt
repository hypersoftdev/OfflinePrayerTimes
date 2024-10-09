package com.hypersoft.offlineprayertimes.managers.sharedPref

import android.content.SharedPreferences

//Prayer Start
private const val PRAYER_LATITUDE_MAIN_KEY = "prayer_main_latitude_key"
private const val PRAYER_LONGITUDE_MAIN_KEY = "prayer_main_longitude_key"
private const val PRAYER_TIME_FORMAT_KEY = "prayer_time_format_key"
private const val PRAYER_CALCULATION_METHOD_KEY = "prayer_calculation_method_key"
private const val PRAYER_ASR_JURISTIC_KEY = "prayer_asr_juristic_key"
private const val PRAYER_ADJUST_HIGH_LATS_KEY = "prayer_adjust_high_lats_key"
//Prayer End

class SharedPrefManager(private val sharedPreferences: SharedPreferences) {

    //Prayer SharedPref Start
    var prayerLatitude: Long
        get() = sharedPreferences.getLong(PRAYER_LATITUDE_MAIN_KEY, 0L)
        set(value) {
            sharedPreferences.edit().apply {
                putLong(PRAYER_LATITUDE_MAIN_KEY, value)
                apply()
            }
        }

    var prayerLongitude: Long
        get() = sharedPreferences.getLong(PRAYER_LONGITUDE_MAIN_KEY, 0L)
        set(value) {
            sharedPreferences.edit().apply {
                putLong(PRAYER_LONGITUDE_MAIN_KEY, value)
                apply()
            }
        }

    var prayerTimeFormat: Int
        get() = sharedPreferences.getInt(PRAYER_TIME_FORMAT_KEY, 1)
        set(value) {
            sharedPreferences.edit().apply {
                putInt(PRAYER_TIME_FORMAT_KEY, value)
                apply()
            }
        }

    var prayerCalMethod: Int
        get() = sharedPreferences.getInt(PRAYER_CALCULATION_METHOD_KEY, 1)
        set(value) {
            sharedPreferences.edit().apply {
                putInt(PRAYER_CALCULATION_METHOD_KEY, value)
                apply()
            }
        }

    var asrJuristic: Int
        get() = sharedPreferences.getInt(PRAYER_ASR_JURISTIC_KEY, 1)
        set(value) {
            sharedPreferences.edit().apply {
                putInt(PRAYER_ASR_JURISTIC_KEY, value)
                apply()
            }
        }
    var prayerHighLats: Int
        get() = sharedPreferences.getInt(PRAYER_ADJUST_HIGH_LATS_KEY, 3)
        set(value) {
            sharedPreferences.edit().apply {
                putInt(PRAYER_ADJUST_HIGH_LATS_KEY, value)
                apply()
            }
        }


}