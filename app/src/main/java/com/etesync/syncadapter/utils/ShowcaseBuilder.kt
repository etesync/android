package com.etesync.syncadapter.utils

import android.app.Activity
import tourguide.tourguide.Overlay
import tourguide.tourguide.Pointer
import tourguide.tourguide.TourGuide

object ShowcaseBuilder {
    fun getBuilder(activity: Activity): TourGuide {
        val ret = TourGuide.init(activity).with(TourGuide.Technique.Click)
                .setPointer(Pointer())
        ret.setOverlay(Overlay().setOnClickListener { ret.cleanUp() })

        return ret
    }
}
