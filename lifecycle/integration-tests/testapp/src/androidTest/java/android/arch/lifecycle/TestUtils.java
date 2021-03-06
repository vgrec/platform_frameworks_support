/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.arch.lifecycle;

import static android.arch.lifecycle.Lifecycle.State.RESUMED;
import static android.arch.lifecycle.Lifecycle.State.STARTED;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.Instrumentation.ActivityMonitor;
import android.support.test.InstrumentationRegistry;
import android.support.test.rule.ActivityTestRule;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

class TestUtils {

    private static final long TIMEOUT_MS = 2000;

    @SuppressWarnings("unchecked")
    static <T extends Activity> T recreateActivity(final T activity, ActivityTestRule rule)
            throws Throwable {
        ActivityMonitor monitor = new ActivityMonitor(
                activity.getClass().getCanonicalName(), null, false);
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        instrumentation.addMonitor(monitor);
        rule.runOnUiThread(activity::recreate);
        T result;

        // this guarantee that we will reinstall monitor between notifications about onDestroy
        // and onCreate
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (monitor) {
            do {
                // the documetation says "Block until an Activity is created
                // that matches this monitor." This statement is true, but there are some other
                // true statements like: "Block until an Activity is destoyed" or
                // "Block until an Activity is resumed"...

                // this call will release synchronization monitor's monitor
                result = (T) monitor.waitForActivityWithTimeout(TIMEOUT_MS);
                if (result == null) {
                    throw new RuntimeException("Timeout. Failed to recreate an activity");
                }
            } while (result == activity);
        }
        return result;
    }

    static void waitTillResumed(final LifecycleOwner owner, ActivityTestRule<?> activityRule)
            throws Throwable {
        waitTillState(owner, activityRule, RESUMED);
    }

    static void waitTillStarted(final LifecycleOwner owner, ActivityTestRule<?> activityRule)
            throws Throwable {
        waitTillState(owner, activityRule, STARTED);
    }

    private static void waitTillState(final LifecycleOwner owner, ActivityTestRule<?> activityRule,
            Lifecycle.State state)
            throws Throwable {
        final CountDownLatch latch = new CountDownLatch(1);
        activityRule.runOnUiThread(() -> {
            if (owner.getLifecycle().getCurrentState() == state) {
                latch.countDown();
            } else {
                owner.getLifecycle().addObserver(new LifecycleObserver() {
                    @OnLifecycleEvent(Lifecycle.Event.ON_ANY)
                    public void onStateChanged(LifecycleOwner provider) {
                        if (provider.getLifecycle().getCurrentState() == state) {
                            latch.countDown();
                            provider.getLifecycle().removeObserver(this);
                        }
                    }
                });
            }
        });
        latch.await(1, TimeUnit.SECONDS);
    }

}
