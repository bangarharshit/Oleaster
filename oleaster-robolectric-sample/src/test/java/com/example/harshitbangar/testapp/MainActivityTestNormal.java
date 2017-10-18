package com.example.harshitbangar.testapp;

import android.content.Intent;
import android.widget.Button;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowActivity;
import org.robolectric.shadows.ShadowIntent;

@RunWith(RobolectricTestRunner.class)
@Config(constants = BuildConfig.class, sdk = 21)
public class MainActivityTestNormal {

    private MainActivity mainActivity;
    private Button button;

    @Before
    public void setup() {
        mainActivity = Robolectric.buildActivity(MainActivity.class).create().get();
        button = (Button) mainActivity.findViewById(R.id.next_activity_click);
    }

    @Test
    public void buttonClick_startNextActivity() {
        button.performClick();
        ShadowActivity shadowActivity = Shadows.shadowOf(mainActivity);
        Intent intent = shadowActivity.getNextStartedActivity();
        ShadowIntent shadowIntent = Shadows.shadowOf(intent);
        Assert.assertEquals(SecondActivity.class, shadowIntent.getIntentClass());
    }
}
