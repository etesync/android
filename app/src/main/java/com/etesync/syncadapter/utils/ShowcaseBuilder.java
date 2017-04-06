package com.etesync.syncadapter.utils;

import android.app.Activity;
import android.view.View;

import tourguide.tourguide.Overlay;
import tourguide.tourguide.Pointer;
import tourguide.tourguide.TourGuide;

public class ShowcaseBuilder {
    public static TourGuide getBuilder(Activity activity) {
        final TourGuide ret = TourGuide.init(activity).with(TourGuide.Technique.Click)
                .setPointer(new Pointer());
        ret.setOverlay(new Overlay().setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ret.cleanUp();
            }
        }));

        return ret;
    }
}
