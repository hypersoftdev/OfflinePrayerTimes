package com.hypersoft.prayertimes.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.hypersoft.prayertimes.interfaces.PrayerItemClickInterface
import com.hypersoft.prayertimes.databinding.ItemPrayerTimesBinding
import com.hypersoft.prayertimes.models.PrayerModel

class PrayersAdapter(val prayerItemClickInterface: PrayerItemClickInterface?) :
    ListAdapter<PrayerModel, PrayersAdapter.CustomViewHolder>(diffUtilParagraphInfoItem) {

    private var amText: String = "AM"
    private var pmText: String = "PM"

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): CustomViewHolder {

        val binding =
            ItemPrayerTimesBinding.inflate(LayoutInflater.from(parent.context), parent, false)

        return CustomViewHolder(binding)

    }

    override fun onBindViewHolder(holder: CustomViewHolder, position: Int) {

        val currentItem = getItem(position)
        if (currentItem != null) {
            holder.binding.prayItemTopConstraint.visibility = View.VISIBLE
            populateItemRows(currentItem, holder.binding, position)
        }

    }

    private fun populateItemRows(
        currentItem: PrayerModel,
        binding: ItemPrayerTimesBinding,
        position: Int
    ) {

        if (position == currentList.size - 1) {
            binding.prayerListLine.visibility = View.GONE
        } else {
            binding.prayerListLine.visibility = View.VISIBLE
        }

        binding.prayerName.text = currentItem.prayerName
        binding.prayerTime.text = currentItem.prayerTime.substringBefore("am")
            .substringBefore("AM")
            .substringBefore("pm")
            .substringBefore("PM").trim()
        if (currentItem.prayerTime.contains("am", true)) {
            binding.prayerAMPM.text = amText
        } else if (currentItem.prayerTime.contains("pm", true)) {
            binding.prayerAMPM.text = pmText
        }

    }

    inner class CustomViewHolder(val binding: ItemPrayerTimesBinding) :
        RecyclerView.ViewHolder(binding.root) {
        init {
            if (adapterPosition != -1) {
                prayerItemClickInterface?.onPrayerItemClick(
                    getItem(adapterPosition),
                    adapterPosition
                )
            }
        }
    }

    companion object {

        val diffUtilParagraphInfoItem = object : DiffUtil.ItemCallback<PrayerModel>() {
            override fun areItemsTheSame(
                oldItem: PrayerModel,
                newItem: PrayerModel
            ): Boolean {
                return oldItem == newItem
            }

            override fun areContentsTheSame(
                oldItem: PrayerModel,
                newItem: PrayerModel
            ): Boolean {
                return oldItem.prayerName == newItem.prayerName
            }

        }

    }

}