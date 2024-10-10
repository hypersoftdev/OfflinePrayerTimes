[![](https://jitpack.io/v/hypersoftdev/OfflinePrayerTimes.svg)](https://jitpack.io/#hypersoftdev/OfflinePrayerTimes)

# OfflinePrayerTimes

**OfflinePrayerTimes** is a powerful, flexible and customizable Offline Prayer Times library used to get current day Prayer Timings.

## Gradle Integration

### Step A: Add Maven Repository

In your project-level **build.gradle** or **settings.gradle** file, add the JitPack repository:
```
repositories {
    google()
    mavenCentral()
    maven { url "https://jitpack.io" }
}
```  

### Step B: Add Dependencies

Next, include the library in your app-level **build.gradle** file. Replace x.x.x with the latest version [![](https://jitpack.io/v/hypersoftdev/OfflinePrayerTimes.svg)](https://jitpack.io/#hypersoftdev/OfflinePrayerTimes)
```
implementation com.github.hypersoftdev:OfflinePrayerTimes:x.x.x'
```

## Implementation

### Requirements:

- **Things app must have before starting implementation**
  - App must have latitude and longitude value in order to pass these in below code.

### Kotlin Example
```
class MainActivity : AppCompatActivity() {

    private var prayerTimeCoroutine: Job? = null
    private var sharedPrefManager: SharedPrefManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        
        //Use Below Functions
        setPrayerAttributes()
        getTodayPrayerTimes()
		
    }

     private fun setPrayerAttributes() {

        sharedPrefManager = SharedPrefManager(
            getSharedPreferences(
                "prayer_preferences",
                MODE_PRIVATE
            )
        )

        sharedPrefManager?.prayerLatitude = 33.601921.toBits() //Put your own Latitude value here
        sharedPrefManager?.prayerLongitude = 73.038078.toBits() //Put your own Longitude value here

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
                    //Write your own logic here
                }

                clearPrayerResources() //Optional
                cancelCoroutine()      //Optional
            }

        }.executeCoroutine()

    }
    
    private fun cancelCoroutine() {
        if (prayerTimeCoroutine != null && prayerTimeCoroutine?.isActive == true) {
            prayerTimeCoroutine?.cancel("coroutineCanceled", null)
        }
        prayerTimeCoroutine = null
    }
    
    //Optional
    override fun onDestroy() {
        cancelCoroutine()
        super.onDestroy()
    }
	
}
```

## Attribute Summary
<table>
  <tr><th>Attribute</th><th>Format</th><th>Description</th></tr>
  <tr><td>prayerLatitude</td><td>long</td><td>Set latitude (e.g, 33.601921.toBits())</td></tr>
  <tr><td>prayerLongitude</td><td>long</td><td>Set longitude (e.g, 73.038078.toBits())</td></tr>
  <tr><td>prayerTimeFormat</td><td>Int</td><td>Set Prayer Time Format.<br>1 = 12Hour (Default)<br>0 = 24Hour</td></tr>
  <tr><td>prayerCalMethod</td><td>Int</td><td>Set Prayer Calculation Method<br>0 = JAFARI<br>1 =	University of Islamic Sciences, KARACHI (Default)<br>2 = Islamic Society of North America (ISNA)<br>3 = Muslim World League (MWL)<br>4 = Umm al-Qura, MAKKAH<br>5 = Egyptian General Authority of Survey<br>6 = Institute of Geophysics, University of TEHRAN</td></tr>
  <tr><td>asrJuristic</td><td>Int</td><td>Set Asr Juristic.<br>1 = Hanfi (Default)<br>0 = Shafi</td></tr>
  <tr><td>prayerHighLats</td><td>Int</td><td>Adjustment for Higher Altitudes areas.<br>0 = No Adjustment<br>1 = Middle of Night<br>2 = 1/7th of Night<br>3 = Angle/60th of Night (Default)</td></tr>
</table>

## Features

- **Offline Prayer Times**
  - Get All Prayer Timings (Online/Offline).
  - Customizable attributes so user can get accurate prayer times based on their location.
  - Get next day Fajr prayer time in advance to show it to user for better user experience.

## Screen Demo

![Demo](https://github.com/hypersoftdev/OfflinePrayerTimes/blob/master/screens/screen1.jpg?raw=true)

# Acknowledgements

This work would not have been possible without the invaluable contributions of **M. Ali Khan**. His expertise, dedication, and unwavering support have been instrumental in bringing this project to fruition.

![screenshot](https://github.com/hypersoftdev/OfflinePrayerTimes/blob/master/screens/profile_image.jpg?raw=true)

We are deeply grateful for **M. Ali Khan's** involvement and his belief in the importance of this work. His contributions have made a significant impact, and we are honored to have had the opportunity to collaborate with him.

# LICENSE

Copyright 2023 Hypersoft Inc

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
