# RoboOleaster
[![Build Status](https://travis-ci.org/OleasterFramework/Oleaster.svg?branch=master)](https://travis-ci.org/OleasterFramework/Oleaster) 


RoboOleaster is a BDD testing framework for Java and Android. It is a fork of [Oleaster](https://github.com/mscharhag/oleaster) 
and is inspired heavily by [Jasmine](https://github.com/jasmine/jasmine).

An Oleaster JUnit test looks like this:

```java
@RunWith(OleasterRunner.class)
public class OleasterIntroductionTest {{
	describe("A suite", () -> {
		it("contains a spec with an expectation", () -> {
			expect(40 + 2).toEqual(42);
		});
	});
}}
```

Oleaster consists out of two independent libraries:

The [Oleaster JUnit Runner](https://github.com/mscharhag/oleaster/tree/master/oleaster-runner) gives you the option
 to write JUnit tests in the format shown above. Java 8 Lambda expressions are used to structure a test in suites
 and specifications.
 
[Oleaster-Matcher](https://github.com/mscharhag/oleaster/tree/master/oleaster-matcher)
 provides Jasmine-like Matchers (`expect(..).toEqual(..)`) to validate test results. These Matchers can be used
 as a replacement (or extension) for standard JUnit assertions.
 
 
 ## Documentation and examples

[Oleaster JUnit Runner Documentation](https://github.com/mscharhag/oleaster/blob/master/oleaster-runner/README.md)

[Oleaster Matcher Documentation](https://github.com/mscharhag/oleaster/blob/master/oleaster-matcher/README.md)

[Source of the AudioPlayer example](https://github.com/mscharhag/oleaster/blob/master/oleaster-examples/src/test/java/com/mscharhag/oleaster/examples/AudioPlayerExampleTest.java) from the Oleaster Runner documentation.

Oleaster tests are (mostly) written with Oleaster (see: [Oleaster JUnit Runner Tests](https://github.com/mscharhag/oleaster/tree/master/oleaster-runner/src/test/java/com/mscharhag/oleaster/runner) and [Oleaster Matcher Tests](https://github.com/mscharhag/oleaster/tree/master/oleaster-matcher/src/test/java/com/mscharhag/oleaster/matcher/matchers)).

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
// Main runner
testCompile 'com.github.OleasterFramework.Oleaster:oleaster-runner:v0.3.4'
// Robolectric support for Android
testCompile 'com.github.OleasterFramework.Oleaster:oleaster-robolectric:v0.3.4'
// For matchers
testCompile 'com.github.OleasterFramework.Oleaster:oleaster-matcher:v0.3.4'
```

## Android Example
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

### Contributing to Oleaster
Check the issue tracker and send a PR. Looks for issues marked with good first issue label.

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
