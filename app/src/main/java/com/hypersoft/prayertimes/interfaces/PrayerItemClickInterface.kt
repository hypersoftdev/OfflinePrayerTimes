package com.hypersoft.prayertimes.interfaces

import com.hypersoft.prayertimes.models.PrayerModel

interface PrayerItemClickInterface {
    fun onPrayerItemClick(
        prayerModel: PrayerModel,
        position: Int
    )
}