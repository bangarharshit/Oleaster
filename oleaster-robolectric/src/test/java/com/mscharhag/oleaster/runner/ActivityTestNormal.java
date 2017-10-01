package com.mscharhag.oleaster.runner;

import android.app.Activity;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 21)
public class ActivityTestNormal {
    private Activity activity;

    @Before
    public void setup() {
        activity = Robolectric.setupActivity(Activity.class);
    }

    @Test
    public void test() {
        Assert.assertFalse(activity.isFinishing());
    }
}
