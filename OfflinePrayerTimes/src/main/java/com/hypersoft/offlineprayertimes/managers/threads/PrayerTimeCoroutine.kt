package com.hypersoft.offlineprayertimes.managers.threads

import android.util.Log
import com.hypersoft.offlineprayertimes.managers.sharedPref.SharedPrefManager
import com.hypersoft.offlineprayertimes.models.PrayerTimeModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.Objects
import java.util.TimeZone
import kotlin.coroutines.CoroutineContext
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

abstract class PrayerTimeCoroutine() : CoroutineScope {

    override val coroutineContext: CoroutineContext get() = Dispatchers.Main

    // Calculation Methods
    private val MAKKAH: Int = 4 // Umm al-Qura, MAKKAH
    private val EGYPT: Int = 5 // Egyptian General Authority of Survey
    private val CUSTOM: Int = 7 // CUSTOM Setting
    private val TEHRAN: Int = 6 // Institute of Geophysics, University of TEHRAN
    private val JAFARI: Int = 0 // Ithna Ashari
    private val KARACHI: Int = 1 // University of Islamic Sciences, KARACHI
    private val ISNA: Int = 2 // Islamic Society of North America (ISNA)
    private val MWL: Int = 3 // Muslim World League (MWL)

    // Adjusting Methods for Higher Latitudes
    private val NONE: Int = 0 // No adjustment
    private val MID_NIGHT: Int = 1 // middle of night
    private val ONE_SEVENTH: Int = 2 // 1/7th of night
    private val ANGLE_BASED: Int = 3 // angle/60th of night

    // Time Formats
    private val TIME_24: Int = 0 // 24-hour format
    private val TIME_12: Int = 1 // 12-hour format
    private val TIME_12_NS: Int = 2 // 12-hour format with no suffix
    private val FLOATING: Int = 3 // floating point number

    // Juristic Methods
    private val SHAFII: Int = 0 // SHAFII
    private val HANAFI: Int = 1 // HANAFI (standard)

    // ---------------------- Global Variables --------------------
    private var calcMethod = 0 // caculation method
    private var asrJuristic = 0 // Juristic method for Asr
    private var dhuhrMinutes = 0 // minutes after mid-day for Dhuhr
    private var adjustHighLats = 0 // adjusting method for higher latitudes
    private var timeFormat = 0 // time format
    private var lat = 0.0 // latitude
    private var lng = 0.0 // longitude
    private var timeZone = 0.0 // time-zone
    private var JDate = 0.0 // Julian date

    // Time Names
    private var timeNames: ArrayList<String>? = null
    private var InvalidTime: String? = null // The string used for invalid times

    // --------------------- Technical Settings --------------------
    private var numIterations = 0 // number of iterations needed to compute times

    // ------------------- Calc Method Parameters --------------------
    private var methodParams: HashMap<Int, DoubleArray>? = null
    private var prayerTimeList: ArrayList<PrayerTimeModel>? = ArrayList()

    /*
     * this.methodParams[methodNum] = new Array(fa, ms, mv, is, iv);
     *
     * fa : fajr angle ms : maghrib selector (0 = angle; 1 = minutes after
     * sunset) mv : maghrib parameter value (in angle or minutes) is : isha
     * selector (0 = angle; 1 = minutes after maghrib) iv : isha parameter value
     * (in angle or minutes)
     */
    //    private double[] prayerTimesCurrent;
    private var offsets: IntArray? = null

    //    Calendar calendarAlarm;
    private var timeFormatter: SimpleDateFormat? = null

    //    String[] numberList;
    private var nextDayFajrTime: String = ""
    private var mainSharedPref: SharedPrefManager? = null

    constructor(
        mainSharedPref: SharedPrefManager
    ) : this() {
        this.mainSharedPref = mainSharedPref
    }

    open fun onPostExecute(prayerTimeList: ArrayList<PrayerTimeModel>?, nextDayFajrTime: String?) {}
    open fun onPreExecute() {}

    fun executeCoroutine(): Job {
        return launch {
            onPreExecute()
            onPrayerDataInit()
            doInBackground() // runs in background thread without blocking the Main Thread
            val newPrayList = prayerTimeList?.let { ArrayList(it) }
            if (newPrayList?.size == 7 &&
                (nextDayFajrTime.isNotEmpty())
            ) {
                onPostExecute(newPrayList, nextDayFajrTime)
            } else {
                onPostExecute(null, null)
            }
        }
    }

    private fun onPrayerDataInit() {

        try {
            // Initialize vars
//        this.setCalcMethod(0);
//        this.setAsrJuristic(0);
//        this.setDhuhrMinutes(0);
//        this.setAdjustHighLats(1);
//        this.setTimeFormat(0);

            // Time Names

            timeNames = ArrayList()
            timeNames!!.add("Fajr")
            timeNames!!.add("Sunrise")
            timeNames!!.add("Dhuhr")
            timeNames!!.add("Asr")
            timeNames!!.add("Sunset")
            timeNames!!.add("Maghrib")
            timeNames!!.add("Isha")

            InvalidTime = "-----" // The string used for invalid times

            // --------------------- Technical Settings --------------------
            this.setNumIterations(1) // number of iterations needed to compute

            // times

            // ------------------- Calc Method Parameters --------------------

            // Tuning offsets {fajr, sunrise, dhuhr, asr, sunset, maghrib, isha}
            offsets = IntArray(7)
            offsets!![0] = 0
            offsets!![1] = 0
            offsets!![2] = 0
            offsets!![3] = 0
            offsets!![4] = 0
            offsets!![5] = 0
            offsets!![6] = 0
            tune(offsets!!)
            /*
             * fa : fajr angle ms : maghrib selector (0 = angle; 1 = minutes after
             * sunset) mv : maghrib parameter value (in angle or minutes) is : isha
             * selector (0 = angle; 1 = minutes after maghrib) iv : isha parameter
             * value (in angle or minutes)
             */
            methodParams = HashMap()

            // JAFARI
            val Jvalues = doubleArrayOf(16.0, 0.0, 4.0, 0.0, 14.0)
            methodParams!![JAFARI] = Jvalues

            // KARACHI
            val Kvalues = doubleArrayOf(18.0, 1.0, 0.0, 0.0, 18.0)
            methodParams!![KARACHI] = Kvalues

            // ISNA
            val Ivalues = doubleArrayOf(15.0, 1.0, 0.0, 0.0, 15.0)
            methodParams!![ISNA] = Ivalues

            // MWL
            val MWvalues = doubleArrayOf(18.0, 1.0, 0.0, 0.0, 17.0)
            methodParams!![MWL] = MWvalues

            // MAKKAH
            val MKvalues = doubleArrayOf(18.5, 1.0, 0.0, 1.0, 90.0)
            methodParams!![MAKKAH] = MKvalues

            // EGYPT
            val Evalues = doubleArrayOf(19.5, 1.0, 0.0, 0.0, 17.5)
            methodParams!![EGYPT] = Evalues

            // TEHRAN
            val Tvalues = doubleArrayOf(17.7, 0.0, 4.5, 0.0, 14.0)
            methodParams!![TEHRAN] = Tvalues

            // CUSTOM
            val Cvalues = doubleArrayOf(18.0, 1.0, 0.0, 0.0, 17.0)
            methodParams!![CUSTOM] = Cvalues

            Log.i("PrayerTime", "main: Inside Cons New = 222")
        } catch (e: Exception) {
            Log.i("PrayerTime", "main: Inside Exception = $e")
            e.printStackTrace()
        }

        prayerTimeList?.clear()

    }

    private suspend fun doInBackground() =
        withContext(Dispatchers.IO) {
            // to run code in Background Thread

            Log.d("prayersData", "doInBackground: All Prayer = $isActive")

            if (isActive) {
                if (mainSharedPref != null) {
                    Log.d("prayersData", "doInBackground: All 2 Prayer = $isActive")
                    refreshPrayerTimes(mainSharedPref!!)
                }
            }
            return@withContext

        }

    // ---------------------- Trigonometric Functions -----------------------
    // range reduce angle in degrees.
    private fun fixangle(a: Double): Double {
        var a = a
        a = a - (360 * (floor(a / 360.0)))
        a = if (a < 0) (a + 360) else a
        return a
    }

    // range reduce hours to 0..23
    private fun fixhour(a: Double): Double {
        var a = a
        a = a - 24.0 * floor(a / 24.0)
        a = if (a < 0) (a + 24) else a
        return a
    }

    // radian to degree
    private fun radiansToDegrees(alpha: Double): Double {
        return ((alpha * 180.0) / Math.PI)
    }

    // deree to radian
    private fun DegreesToRadians(alpha: Double): Double {
        return ((alpha * Math.PI) / 180.0)
    }

    // degree sin
    private fun dsin(d: Double): Double {
        return (sin(DegreesToRadians(d)))
    }

    // degree cos
    private fun dcos(d: Double): Double {
        return (cos(DegreesToRadians(d)))
    }

    // degree tan
    private fun dtan(d: Double): Double {
        return (tan(DegreesToRadians(d)))
    }

    // degree arcsin
    private fun darcsin(x: Double): Double {
        val `val` = asin(x)
        return radiansToDegrees(`val`)
    }

    // degree arccos
    private fun darccos(x: Double): Double {
        val `val` = acos(x)
        return radiansToDegrees(`val`)
    }

    // degree arctan
    private fun darctan(x: Double): Double {
        val `val` = atan(x)
        return radiansToDegrees(`val`)
    }

    // degree arctan2
    private fun darctan2(y: Double, x: Double): Double {
        val `val` = atan2(y, x)
        return radiansToDegrees(`val`)
    }

    // degree arccot
    private fun darccot(x: Double): Double {
        val `val` = atan2(1.0, x)
        return radiansToDegrees(`val`)
    }

    // ---------------------- Time-Zone Functions -----------------------
    // compute local time-zone for a specific date
    private fun getTimeZone1(): Double {
        val timez = TimeZone.getDefault()
        return (timez.rawOffset / 1000.0) / 3600
    }

    // compute base time-zone of the system
    private fun getBaseTimeZone(): Double {
        val timez = TimeZone.getDefault()
        return (timez.rawOffset / 1000.0) / 3600
    }

    // detect daylight saving in a given date
    private fun detectDaylightSaving(): Double {
        val timez = TimeZone.getDefault()
        return timez.dstSavings.toDouble()
    }

    // ---------------------- Julian Date Functions -----------------------
    // calculate julian date from a calendar date
    private fun julianDate(year: Int, month: Int, day: Int): Double {
        var year = year
        var month = month
        var B = 0.0
        try {
            if (month <= 2) {
                year -= 1
                month += 12
            }
            val A = floor(year / 100.0)

            B = 2 - A + floor(A / 4.0)
        } catch (e: Exception) {
            Log.i("PrayerTime", "main: Inside Exception = $e")
            e.printStackTrace()
        }

        return floor(365.25 * (year + 4716)) + floor(30.6001 * (month + 1)) + day + B - 1524.5
    }

    // convert a calendar date to julian date (second method)
    private fun calcJD(year: Int, month: Int, day: Int): Double {
        var J1970 = 0.0
        var days = 0.0
        try {
            J1970 = 2440588.0
            val date = Date(year, month - 1, day)

            val ms = date.time.toDouble() // # of milliseconds since midnight Jan 1,
            // 1970
            days = floor(ms / (1000.0 * 60.0 * 60.0 * 24.0))
        } catch (e: Exception) {
            Log.i("PrayerTime", "main: Inside Exception = $e")
            e.printStackTrace()
        }

        return J1970 + days - 0.5
    }

    // ---------------------- Calculation Functions -----------------------
    // References:
    // compute declination angle of sun and equation of time
    private fun sunPosition(jd: Double): DoubleArray {
        var sPosition = DoubleArray(0)
        try {
            val D = jd - 2451545
            val g = fixangle(357.529 + 0.98560028 * D)
            val q = fixangle(280.459 + 0.98564736 * D)
            val L = fixangle(q + (1.915 * dsin(g)) + (0.020 * dsin(2 * g)))

            // double R = 1.00014 - 0.01671 * [self dcos:g] - 0.00014 * [self dcos:
            // (2*g)];
            val e = 23.439 - (0.00000036 * D)
            val d = darcsin(dsin(e) * dsin(L))
            var RA = (darctan2((dcos(e) * dsin(L)), (dcos(L)))) / 15.0
            RA = fixhour(RA)
            val EqT = q / 15.0 - RA
            sPosition = DoubleArray(2)
            sPosition[0] = d
            sPosition[1] = EqT
        } catch (exception: Exception) {
            Log.i("PrayerTime", "main: Inside Exception = $exception")
            exception.printStackTrace()
        }

        return sPosition
    }

    // compute equation of time
    private fun equationOfTime(jd: Double): Double {
        return sunPosition(jd)[1]
    }

    // compute declination angle of sun
    private fun sunDeclination(jd: Double): Double {
        return sunPosition(jd)[0]
    }

    // compute mid-day (Dhuhr, Zawal) time
    private fun computeMidDay(t: Double): Double {
        var T = 0.0
        try {
            T = equationOfTime(this.getJDate() + t)
        } catch (e: Exception) {
            Log.i("PrayerTime", "main: Inside Exception = $e")
            e.printStackTrace()
        }
        return fixhour(12 - T)
    }

    // compute time for a given angle G
    private fun computeTime(G: Double, t: Double): Double {
        var Z = 0.0
        var V = 0.0
        try {
            val D = sunDeclination(this.getJDate() + t)
            Z = computeMidDay(t)
            val Beg = -dsin(G) - dsin(D) * dsin(this.getLat())
            val Mid = dcos(D) * dcos(this.getLat())
            V = darccos(Beg / Mid) / 15.0
        } catch (e: Exception) {
            Log.i("PrayerTime", "main: Inside Exception = $e")
            e.printStackTrace()
        }

        return Z + (if (G > 90) -V else V)
    }

    // compute the time of Asr
    // SHAFII: step=1, HANAFI: step=2
    private fun computeAsr(step: Double, t: Double): Double {
        var G = 0.0
        try {
            val D = sunDeclination(this.getJDate() + t)
            G = -darccot(step + dtan(abs(this.getLat() - D)))
        } catch (e: Exception) {
            Log.i("PrayerTime", "main: Inside Exception = $e")
            e.printStackTrace()
        }
        return computeTime(G, t)
    }

    // ---------------------- Misc Functions -----------------------
    // compute the difference between two times
    private fun timeDiff(time1: Double, time2: Double): Double {
        return fixhour(time2 - time1)
    }

    // -------------------- Interface Functions --------------------
    // return prayer times for a given date
    private fun getDatePrayerTimes(
        year: Int, month: Int, day: Int,
        latitude: Double, longitude: Double, tZone: Double
    ): ArrayList<String?> {
        this.setLat(latitude)
        this.setLng(longitude)
        this.setTimeZone(tZone)
        this.setJDate(julianDate(year, month, day))
        val lonDiff = longitude / (15.0 * 24.0)
        this.setJDate(this.getJDate() - lonDiff)
        return computeDayTimes()
    }

    // return prayer times for a given date
    private fun getPrayerTimes(
        date: Calendar, latitude: Double,
        longitude: Double, tZone: Double
    ): ArrayList<String?> {
        var year = 0
        var month = 0
        var day = 0
        try {
            year = date[Calendar.YEAR]
            month = date[Calendar.MONTH]
            day = date[Calendar.DATE]
        } catch (e: Exception) {
            Log.i("PrayerTime", "main: Inside Exception = $e")
            e.printStackTrace()
        }

        return getDatePrayerTimes(year, month + 1, day, latitude, longitude, tZone)
    }

    // set custom values for calculation parameters
    private fun setCustomParams(params: DoubleArray) {
        try {
            for (i in 0..4) {
                if (params[i] == -1.0) {
                    params[i] = Objects.requireNonNull(methodParams!![getCalcMethod()])!![i]
                    methodParams!![CUSTOM] = params
                } else {
                    Objects.requireNonNull(methodParams!![CUSTOM])!![i] = params[i]
                }
            }
            setCalcMethod(CUSTOM)
        } catch (e: Exception) {
            Log.i("PrayerTime", "main: Inside Exception = $e")
            e.printStackTrace()
        }
    }

    // set the angle for calculating Fajr
    private fun setFajrAngle(angle: Double) {
        try {
            val params = doubleArrayOf(angle, -1.0, -1.0, -1.0, -1.0)
            setCustomParams(params)
        } catch (e: Exception) {
            Log.i("PrayerTime", "main: Inside Exception = $e")
            e.printStackTrace()
        }
    }

    // set the angle for calculating Maghrib
    private fun setMaghribAngle(angle: Double) {
        try {
            val params = doubleArrayOf(-1.0, 0.0, angle, -1.0, -1.0)
            setCustomParams(params)
        } catch (e: Exception) {
            Log.i("PrayerTime", "main: Inside Exception = $e")
            e.printStackTrace()
        }
    }

    // set the angle for calculating Isha
    private fun setIshaAngle(angle: Double) {
        try {
            val params = doubleArrayOf(-1.0, -1.0, -1.0, 0.0, angle)
            setCustomParams(params)
        } catch (e: Exception) {
            Log.i("PrayerTime", "main: Inside Exception = $e")
            e.printStackTrace()
        }
    }

    // set the minutes after Sunset for calculating Maghrib
    private fun setMaghribMinutes(minutes: Double) {
        try {
            val params = doubleArrayOf(-1.0, 1.0, minutes, -1.0, -1.0)
            setCustomParams(params)
        } catch (e: Exception) {
            Log.i("PrayerTime", "main: Inside Exception = $e")
            e.printStackTrace()
        }
    }

    // set the minutes after Maghrib for calculating Isha
    private fun setIshaMinutes(minutes: Double) {
        try {
            val params = doubleArrayOf(-1.0, -1.0, -1.0, 1.0, minutes)
            setCustomParams(params)
        } catch (e: Exception) {
            Log.i("PrayerTime", "main: Inside Exception = $e")
            e.printStackTrace()
        }
    }

    // convert double hours to 24h format
    private fun floatToTime24(time: Double): String? {
        var time = time
        var result = ""

        try {
            if (java.lang.Double.isNaN(time)) {
                return InvalidTime
            }

            time = fixhour(time + 0.5 / 60.0) // add 0.5 minutes to round
            val hours = floor(time).toInt()
            val minutes = floor((time - hours) * 60.0)

            result = if ((hours >= 0 && hours <= 9) && (minutes >= 0 && minutes <= 9)) {
                hours.toString() + ":0" + Math.round(minutes)
            } else if ((hours >= 0 && hours <= 9)) {
                hours.toString() + ":" + Math.round(minutes)
            } else if ((minutes >= 0 && minutes <= 9)) {
                hours.toString() + ":0" + Math.round(minutes)
            } else {
                hours.toString() + ":" + Math.round(minutes)
            }
        } catch (e: Exception) {
            Log.i("PrayerTime", "main: Inside Exception = $e")
            e.printStackTrace()
        }

        return result
    }

    // convert double hours to 12h format
    private fun floatToTime12(time: Double, noSuffix: Boolean): String? {
        var time = time
        if (java.lang.Double.isNaN(time)) {
            return InvalidTime
        }

        val suffix: String
        var result = ""
        try {
            time = fixhour(time + 0.5 / 60) // add 0.5 minutes to round
            var hours = floor(time).toInt()
            val minutes = floor((time - hours) * 60)
            suffix = if (hours >= 12) {
                "pm"
            } else {
                "am"
            }
            hours = ((((hours + 12) - 1) % (12)) + 1)

            /*hours = (hours + 12) - 1;
        int hrs = (int) hours % 12;
        hrs += 1;*/
            result = if ((hours >= 0 && hours <= 9) && (minutes >= 0 && minutes <= 9)) {
                hours.toString() + ":0" + Math.round(minutes)
            } else if ((hours >= 0 && hours <= 9)) {
                hours.toString() + ":" + Math.round(minutes)
            } else if ((minutes >= 0 && minutes <= 9)) {
                hours.toString() + ":0" + Math.round(minutes)
            } else {
                hours.toString() + ":" + Math.round(minutes)
            }

            if (!noSuffix) {
                result += " $suffix"
            }
        } catch (e: Exception) {
            Log.i("PrayerTime", "main: Inside Exception = $e")
            e.printStackTrace()
        }

        return result
    }

    // convert double hours to 12h format with no suffix
    private fun floatToTime12NS(time: Double): String? {
        return floatToTime12(time, true)
    }

    // ---------------------- Compute Prayer Times -----------------------
    // compute prayer times at given julian date
    private fun computeTimes(times: DoubleArray): DoubleArray {
        var Fajr = 0.0
        var Sunrise = 0.0
        var Dhuhr = 0.0
        var Asr = 0.0
        var Sunset = 0.0
        var Maghrib = 0.0
        var Isha = 0.0
        try {
            val t = dayPortion(times)

            Fajr = this.computeTime(
                180 - Objects.requireNonNull(methodParams!![getCalcMethod()])!![0],
                t[0]
            )

            Sunrise = this.computeTime(180 - 0.833, t[1])

            Dhuhr = this.computeMidDay(t[2])
            Asr = this.computeAsr(
                (1 + this.getAsrJuristic()).toDouble(),
                t[3]
            )
            Sunset = this.computeTime(0.833, t[4])

            Maghrib = this.computeTime(
                Objects.requireNonNull(methodParams!![getCalcMethod()])!![2],
                t[5]
            )
            Isha = this.computeTime(
                Objects.requireNonNull(methodParams!![getCalcMethod()])!![4],
                t[6]
            )
        } catch (e: Exception) {
            Log.i("PrayerTime", "main: Inside Exception = $e")
            e.printStackTrace()
        }

        return doubleArrayOf(Fajr, Sunrise, Dhuhr, Asr, Sunset, Maghrib, Isha)
    }

    // compute prayer times at given julian date
    private fun computeDayTimes(): ArrayList<String?> {
        var times = DoubleArray(0) // default times
        try {
            times = doubleArrayOf(5.0, 6.0, 12.0, 13.0, 18.0, 18.0, 18.0)

            for (i in 1..this.getNumIterations()) {
                times = computeTimes(times)
            }
            times = adjustTimes(times)
            times = tuneTimes(times)
        } catch (e: Exception) {
            Log.i("PrayerTime", "main: Inside Exception = $e")
            e.printStackTrace()
        }

        return adjustTimesFormat(times)
    }

    // adjust times in a prayer time array
    private fun adjustTimes(times: DoubleArray): DoubleArray {
        var times = times
        try {
            for (i in times.indices) {
                times[i] += this.getTimeZone() - this.getLng() / 15
            }

            times[2] += getDhuhrMinutes().toDouble() / 60 // Dhuhr
            if (Objects.requireNonNull(methodParams!![getCalcMethod()])!![1] == 1.0) // Maghrib
            {
                times[5] = times[4] + Objects.requireNonNull(
                    methodParams!![getCalcMethod()]
                )!![2] / 60
            }
            if (Objects.requireNonNull(methodParams!![getCalcMethod()])!![3] == 1.0) // Isha
            {
                times[6] = times[5] + Objects.requireNonNull(
                    methodParams!![getCalcMethod()]
                )!![4] / 60
            }

            if (this.getAdjustHighLats() != NONE) {
                times = adjustHighLatTimes(times)
            }
        } catch (e: Exception) {
            Log.i("PrayerTime", "main: Inside Exception = $e")
            e.printStackTrace()
        }

        return times
    }

    // convert times array to given time format
    private fun adjustTimesFormat(times: DoubleArray): ArrayList<String?> {
        val result = ArrayList<String?>()

        try {
            if (this.getTimeFormat() == FLOATING) {
                for (time in times) {
                    result.add(time.toString())
                }
                return result
            }
            for (i in 0..6) {
                if (this.getTimeFormat() == TIME_12) {
                    result.add(floatToTime12(times[i], false))
                } else if (this.getTimeFormat() == TIME_12_NS) {
                    result.add(floatToTime12(times[i], true))
                } else {
                    result.add(floatToTime24(times[i]))
                }
            }
            return result
        } catch (e: Exception) {
            Log.i("PrayerTime", "main: Inside Exception = $e")
            e.printStackTrace()
        }
        return result
    }

    // adjust Fajr, Isha and Maghrib for locations in higher latitudes
    private fun adjustHighLatTimes(times: DoubleArray): DoubleArray {
        try {
            val nightTime = timeDiff(times[4], times[1]) // sunset to sunrise

            // Adjust Fajr
            val FajrDiff = nightPortion(
                Objects.requireNonNull(
                    methodParams!![getCalcMethod()]
                )!![0]
            ) * nightTime

            if (java.lang.Double.isNaN(times[0]) || timeDiff(times[0], times[1]) > FajrDiff) {
                times[0] = times[1] - FajrDiff
            }

            // Adjust Isha
            val IshaAngle = if ((Objects.requireNonNull(
                    methodParams!![getCalcMethod()]
                )!![3] == 0.0)
            ) Objects.requireNonNull(methodParams!![getCalcMethod()])!![4] else 18.0
            val IshaDiff = this.nightPortion(IshaAngle) * nightTime
            if (java.lang.Double.isNaN(times[6]) || this.timeDiff(times[4], times[6]) > IshaDiff) {
                times[6] = times[4] + IshaDiff
            }

            // Adjust Maghrib
            val MaghribAngle = if ((Objects.requireNonNull(
                    methodParams!![getCalcMethod()]
                )!![1] == 0.0)
            ) Objects.requireNonNull(
                methodParams!![getCalcMethod()]
            )!![2] else 4.0
            val MaghribDiff = nightPortion(MaghribAngle) * nightTime
            if (java.lang.Double.isNaN(times[5]) || this.timeDiff(
                    times[4],
                    times[5]
                ) > MaghribDiff
            ) {
                times[5] = times[4] + MaghribDiff
            }
        } catch (e: Exception) {
            Log.i("PrayerTime", "main: Inside Exception = $e")
            e.printStackTrace()
        }

        return times
    }

    // the night portion used for adjusting times in higher latitudes
    private fun nightPortion(angle: Double): Double {
        var calc = 0.0

        try {
            if (adjustHighLats == ANGLE_BASED) calc = (angle) / 60.0
            else if (adjustHighLats == MID_NIGHT) calc = 0.5
            else if (adjustHighLats == ONE_SEVENTH) calc = 0.14286
        } catch (e: Exception) {
            Log.i("PrayerTime", "main: Inside Exception = $e")
            e.printStackTrace()
        }

        return calc
    }

    // convert hours to day portions
    private fun dayPortion(times: DoubleArray): DoubleArray {
        try {
            for (i in 0..6) {
                times[i] /= 24.0
            }
        } catch (e: Exception) {
            Log.i("PrayerTime", "main: Inside Exception = $e")
            e.printStackTrace()
        }
        return times
    }

    // Tune timings for adjustments
    // Set time offsets
    private fun tune(offsetTimes: IntArray) {
        try {
//            for (int i = 0; i < offsetTimes.length; i++) { // offsetTimes length
//                // should be 7 in order
//                // of Fajr, Sunrise,
//                // Dhuhr, Asr, Sunset,
//                // Maghrib, Isha
//                this.offsets[i] = offsetTimes[i];
//            }
            for (i in offsetTimes.indices) {
                // offsetTimes length
                // should be 7 in order
                // of Fajr, Sunrise,
                // Dhuhr, Asr, Sunset,
                // Maghrib, Isha
                offsets!![i] = offsetTimes[i]
            }
        } catch (e: Exception) {
            Log.i("PrayerTime", "main: Inside Exception = $e")
            e.printStackTrace()
        }
    }

    private fun tuneTimes(times: DoubleArray): DoubleArray {
        try {
            for (i in times.indices) {
                times[i] = times[i] + offsets!![i] / 60.0
            }
        } catch (e: Exception) {
            Log.i("PrayerTime", "main: Inside Exception = $e")
            e.printStackTrace()
        }
        return times
    }

    fun clearPrayerResources() {
        if (timeNames != null && timeNames?.isNotEmpty() == true) {
            timeNames?.clear()
            timeNames = null
        }
        if (prayerTimeList != null && prayerTimeList?.isNotEmpty() == true) {
            prayerTimeList?.clear()
            prayerTimeList = null
        }
        if (methodParams != null && methodParams?.isNotEmpty() == true) {
            methodParams?.clear()
            methodParams = null
        }
    }

    private fun refreshPrayerTimes(mainSharedPref: SharedPrefManager) {

        try {

            val defaultTz = TimeZone.getDefault()

            //Get NY calendar object with current date/time
            val defaultCalc = Calendar.getInstance(defaultTz)

            //Get offset from UTC, accounting for DST
            val defaultTzOffsetMs =
                defaultCalc[Calendar.ZONE_OFFSET] + defaultCalc[Calendar.DST_OFFSET]
            val timezone = defaultTzOffsetMs.toDouble() / (1000 * 60 * 60)

            setTimeFormat(mainSharedPref.prayerTimeFormat)
            setCalcMethod(mainSharedPref.prayerCalMethod)
            setAsrJuristic(mainSharedPref.asrJuristic)
            setAdjustHighLats(mainSharedPref.prayerHighLats)

            //            int[] offsets = {0, 0, 0, 0, 0, 0, 0}; // {Fajr,Sunrise,Dhuhr,Asr,Sunset,Maghrib,Isha}
//            tune(offsets);
            val now = Date()
            val cal = Calendar.getInstance()
            cal.time = now

//            SimpleDateFormat timeFormatter;
            var timeFormat = ""

            timeFormat = if (mainSharedPref.prayerTimeFormat == 1) {
                "hh:mm aa"
            } else {
                "hh:mm";
            }
            timeFormatter = SimpleDateFormat(timeFormat, Locale.US)

            val prayerTimes = getPrayerTimes(
                cal,
                java.lang.Double.longBitsToDouble(mainSharedPref.prayerLatitude),
                java.lang.Double.longBitsToDouble(mainSharedPref.prayerLongitude), timezone
            )
            val prayerNames = getTimeNames()
            for (i in prayerTimes.indices) {
                if (isActive) {
                    val prayerTimeModel = PrayerTimeModel()
                    prayerTimeModel.prayerName = prayerNames!![i]

                    val indexNamaz = timeFormatter!!.parse(prayerTimes[i]!!)
                    var updateTime = prayerTimes[i]

                    if (i != 1 && i != 4) {
                        var firstID = 1
                        var nextNamazTime: String? = ""
                        if (i == 0) {
                            if (indexNamaz != null) {
                                indexNamaz.minutes = indexNamaz.minutes
                                updateTime = timeFormatter!!.format(indexNamaz)
                                prayerTimeModel.prayerTime = updateTime
                            }
                            val indexNext = timeFormatter!!.parse(prayerTimes[2]!!)
                            if (indexNext != null) {
                                indexNext.minutes = indexNext.minutes
                                nextNamazTime = timeFormatter!!.format(indexNext)
                            }
                        } else if (i == 2) {
                            if (indexNamaz != null) {
                                indexNamaz.minutes = indexNamaz.minutes
                                updateTime = timeFormatter!!.format(indexNamaz)
                                prayerTimeModel.prayerTime = updateTime
                            }
                            firstID = 2
                            val indexNext = timeFormatter!!.parse(prayerTimes[3]!!)
                            if (indexNext != null) {
                                indexNext.minutes = indexNext.minutes
                                nextNamazTime = timeFormatter!!.format(indexNext)
                            }
                        } else if (i == 3) {
                            if (indexNamaz != null) {
                                indexNamaz.minutes = indexNamaz.minutes
                                updateTime = timeFormatter!!.format(indexNamaz)
                                prayerTimeModel.prayerTime = updateTime
                            }
                            firstID = 3
                            val indexNext = timeFormatter!!.parse(prayerTimes[5]!!)
                            if (indexNext != null) {
                                indexNext.minutes = indexNext.minutes
                                nextNamazTime = timeFormatter!!.format(indexNext)
                            }
                        } else if (i == 5) {
                            if (indexNamaz != null) {
                                indexNamaz.minutes = indexNamaz.minutes
                                updateTime = timeFormatter!!.format(indexNamaz)
                                prayerTimeModel.prayerTime = updateTime
                            }
                            firstID = 4
                            val indexNext = timeFormatter!!.parse(prayerTimes[6]!!)
                            if (indexNext != null) {
                                indexNext.minutes = indexNext.minutes
                                nextNamazTime = timeFormatter!!.format(indexNext)
                            }
                        } else if (i == 6) {
                            if (indexNamaz != null) {
                                indexNamaz.minutes = indexNamaz.minutes
                                updateTime = timeFormatter!!.format(indexNamaz)
                                prayerTimeModel.prayerTime = updateTime
                            }
                            firstID = 5
                            val indexNext = timeFormatter!!.parse(prayerTimes[0]!!)
                            if (indexNext != null) {
                                indexNext.minutes = indexNext.minutes
                                nextNamazTime = timeFormatter!!.format(indexNext)
                            }

                            val nowFajr = Date()
                            val calFajr = Calendar.getInstance()
                            calFajr.time = nowFajr
                            calFajr.add(Calendar.DATE, 1)
                            val prayerTimeForFajr = getPrayerTimes(
                                calFajr,
                                java.lang.Double.longBitsToDouble(mainSharedPref.prayerLatitude),
                                java.lang.Double.longBitsToDouble(mainSharedPref.prayerLongitude),
                                timezone
                            )
                            if (prayerTimeForFajr.size > 0) {
                                val indexNextFajr = timeFormatter!!.parse(prayerTimeForFajr[0]!!)
                                if (indexNextFajr != null) {
                                    indexNextFajr.minutes = indexNextFajr.minutes
                                    nextDayFajrTime = timeFormatter!!.format(indexNextFajr)
                                }
                            }
                        }

                    }
                    if (i == 1) {
                        if (indexNamaz != null) {
                            updateTime = timeFormatter!!.format(indexNamaz)
                            prayerTimeModel.prayerTime = updateTime
                        }

                    } else if (i == 4) {
                        if (indexNamaz != null) {
                            indexNamaz.minutes = indexNamaz.minutes
                            updateTime = timeFormatter!!.format(indexNamaz)
                            prayerTimeModel.prayerTime = updateTime
                        }

                    }
                    prayerTimeList!!.add(prayerTimeModel)
                } else {
                    break
                }
            }
        } catch (e: Exception) {
            Log.i("prayersData", "main: Inside Exception = $e")
            e.printStackTrace()
        }

    }

    fun getCalcMethod(): Int {
        return calcMethod
    }

    fun setCalcMethod(calcMethod: Int) {
        this.calcMethod = calcMethod
    }

    fun getAsrJuristic(): Int {
        return asrJuristic
    }

    fun setAsrJuristic(asrJuristic: Int) {
        this.asrJuristic = asrJuristic
    }

    fun getDhuhrMinutes(): Int {
        return dhuhrMinutes
    }

    fun setDhuhrMinutes(dhuhrMinutes: Int) {
        this.dhuhrMinutes = dhuhrMinutes
    }

    fun getAdjustHighLats(): Int {
        return adjustHighLats
    }

    fun setAdjustHighLats(adjustHighLats: Int) {
        this.adjustHighLats = adjustHighLats
    }

    fun getTimeFormat(): Int {
        return timeFormat
    }

    fun setTimeFormat(timeFormat: Int) {
        this.timeFormat = timeFormat
    }

    fun getLat(): Double {
        return lat
    }

    fun setLat(lat: Double) {
        this.lat = lat
    }

    fun getLng(): Double {
        return lng
    }

    fun setLng(lng: Double) {
        this.lng = lng
    }

    fun getTimeZone(): Double {
        return timeZone
    }

    fun setTimeZone(timeZone: Double) {
        this.timeZone = timeZone
    }

    fun getJDate(): Double {
        return JDate
    }

    fun setJDate(jDate: Double) {
        JDate = jDate
    }

    private fun getNumIterations(): Int {
        return numIterations
    }

    private fun setNumIterations(numIterations: Int) {
        this.numIterations = numIterations
    }

    fun getTimeNames(): ArrayList<String>? {
        return timeNames
    }

}