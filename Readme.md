# RoboOleaster
[![Build Status](https://travis-ci.org/bangarharshit/RoboOleaster.svg?branch=master)](https://travis-ci.org/bangarharshit/RoboOleaster) 


RoboOleaster is a BDD testing framework for Android. It is written on top of [Oleaster](https://github.com/mscharhag/oleaster) 
and is inspired heavily by [Jasmine](https://github.com/jasmine/jasmine).

## Using RoboOleaster in your application

Add this in your root build.gradle
```groovy
allprojects {
		repositories {
			...
			maven { url 'https://jitpack.io' }
		}
	}
```
Add this in your project build.gradle
```groovy
testCompile 'com.github.bangarharshit.RoboOleaster:oleaster-robolectric:0.3.3'
```

## Example
```java
@RunWith(RoboOleaster.class)
@Config(constants = BuildConfig.class, sdk = 21)
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
```

Sample project can be found at [RoboOleaster-Sample](https://github.com/bangarharshit/RoboOleaster-Sample)

## Limitations
1. No method level config support. We are exploring alternatives and is currently tracked [here](https://github.com/bangarharshit/RoboOleaster/issues/1).
2. Multiple API level support for a test. Check this [issue](https://github.com/bangarharshit/RoboOleaster/issues/3).

### Contributing to RoboOleaster
Check the issue tracker and send a PR.

### License
```
   Copyright (C) 2017 Harshit Bangar
   Copyright (C) 2017 Michael Scharhag
   Copyright (c) 2010 Xtreme Labs, Pivotal Labs and Google Inc.

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
```
