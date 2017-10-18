package com.example.harshitbangar.testapp;

import android.content.Intent;
import android.widget.Button;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.internal.RoboOleaster;
import org.robolectric.shadows.ShadowActivity;
import org.robolectric.shadows.ShadowIntent;

import static com.mscharhag.oleaster.runner.StaticRunnerSupport.beforeEach;
import static com.mscharhag.oleaster.runner.StaticRunnerSupport.describe;
import static com.mscharhag.oleaster.runner.StaticRunnerSupport.it;

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
@RunWith(RoboOleaster.class)
@Config(constants = BuildConfig.class, sdk = 23)
public class MainActivityTest {

    private MainActivity mainActivity;
    private Button nextActivityButton;

    {
        describe("Activity create", () -> {
            beforeEach(() -> {
                mainActivity = Robolectric.buildActivity(MainActivity.class).create().get();
                nextActivityButton = (Button) mainActivity.findViewById(R.id.next_activity_click);
            });
            it("the activity is created by setup", () -> {
                ShadowActivity shadowActivity = Shadows.shadowOf(mainActivity);

                Assert.assertFalse("Activity is just created", shadowActivity.isFinishing());
            });
            it("the activity is destroyed when finish is called", () -> {
                ShadowActivity shadowActivity = Shadows.shadowOf(mainActivity);
                mainActivity.finish();
                Assert.assertTrue("Activity is finished", shadowActivity.isFinishing());
            });
            it("the next activity is started when the next button is clicked", () -> {
                nextActivityButton.performClick();
                ShadowActivity shadowActivity = Shadows.shadowOf(mainActivity);
                Intent intent = shadowActivity.getNextStartedActivity();
                ShadowIntent shadowIntent = Shadows.shadowOf(intent);
                Assert.assertEquals(SecondActivity.class, shadowIntent.getIntentClass());
            });
        });
    }
}
