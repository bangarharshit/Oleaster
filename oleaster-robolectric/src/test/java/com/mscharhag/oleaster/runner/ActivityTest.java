package com.mscharhag.oleaster.runner;


import android.app.Activity;

import org.junit.Assert;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.Shadows;
import org.robolectric.internal.RoboOleaster;
import org.robolectric.shadows.ShadowActivity;

import static com.mscharhag.oleaster.runner.StaticRunnerSupport.beforeEach;
import static com.mscharhag.oleaster.runner.StaticRunnerSupport.describe;
import static com.mscharhag.oleaster.runner.StaticRunnerSupport.it;

@RunWith(RoboOleaster.class)
public class ActivityTest {

    private Activity activity;
    {
        describe("Activity create", () -> {
            beforeEach(() -> {
                activity = Robolectric.buildActivity(Activity.class).create().get();
            });
            it("the activity is created by setup", () -> {
                ShadowActivity shadowActivity = Shadows.shadowOf(activity);

                Assert.assertFalse("Activity is just created", shadowActivity.isFinishing());
            });
            it("the activity is destroyed when finish is called", () -> {
                ShadowActivity shadowActivity = Shadows.shadowOf(activity);
                activity.finish();
                Assert.assertTrue("Activity is finished", shadowActivity.isFinishing());
            });
        });
    }
}
