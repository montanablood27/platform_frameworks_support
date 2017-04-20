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

package android.support.dynamicanimation.tests;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;

import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.floatThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.support.animation.DynamicAnimation;
import android.support.animation.FlingAnimation;
import android.support.animation.FloatPropertyCompat;
import android.support.dynamicanimation.test.R;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.MediumTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.view.View;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.internal.matchers.GreaterThan;
import org.mockito.internal.matchers.LessThan;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class FlingTests {
    @Rule
    public final ActivityTestRule<AnimationActivity> mActivityTestRule;
    public View mView1;
    public View mView2;

    @Rule
    public ExpectedException mExpectedException = ExpectedException.none();

    public FlingTests() {
        mActivityTestRule = new ActivityTestRule<>(AnimationActivity.class);
    }

    @Before
    public void setup() throws Exception {
        mView1 = mActivityTestRule.getActivity().findViewById(R.id.anim_view);
        mView2 = mActivityTestRule.getActivity().findViewById(R.id.anim_another_view);
    }

    /**
     * Test that custom properties are supported.
     */
    @Test
    public void testCustomProperties() {
        final Object animObj = new Object();
        FloatPropertyCompat property = new FloatPropertyCompat("") {
            private float mValue = 0f;
            @Override
            public float getValue(Object object) {
                assertEquals(animObj, object);
                return mValue;
            }

            @Override
            public void setValue(Object object, float value) {
                assertEquals(animObj, object);
                assertTrue(value > mValue);
                assertTrue(value >= 100);
                mValue = value;
            }
        };
        final FlingAnimation anim = new FlingAnimation(animObj, property);
        DynamicAnimation.OnAnimationEndListener listener = mock(
                DynamicAnimation.OnAnimationEndListener.class);
        anim.addEndListener(listener);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                anim.setStartValue(100).setStartVelocity(2000).start();
            }
        });
        verify(listener, timeout(1000)).onAnimationEnd(eq(anim), eq(false), floatThat(
                new GreaterThan(110f)), eq(0f));
    }

    /**
     * Test that spring animation can work with a single property without an object.
     */
    @Test
    public void testPropertyWithoutObject() {
        FloatPropertyCompat propertyNoObject = new FloatPropertyCompat("") {
            private float mValue = 0f;
            @Override
            public float getValue(Object object) {
                return mValue;
            }

            @Override
            public void setValue(Object object, float value) {
                // New value <= value from last frame
                assertTrue(mValue >= value);
                mValue = value;
            }
        };
        final FlingAnimation anim = new FlingAnimation(propertyNoObject);

        DynamicAnimation.OnAnimationEndListener listener = mock(
                DynamicAnimation.OnAnimationEndListener.class);
        anim.addEndListener(listener);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                anim.setStartVelocity(-2500).start();
            }
        });
        verify(listener, timeout(1000)).onAnimationEnd(eq(anim), eq(false), floatThat(
                new LessThan(-50f)), eq(0f));
    }


    /**
     * Test that friction does affect how fast the slow down happens. Fling animation with
     * higher friction should finish first.
     */
    @Test
    public void testFriction() {
        FloatPropertyCompat propertyNoObject = mock(FloatPropertyCompat.class);
        float lowFriction = 0.5f;
        float highFriction = 2f;
        final FlingAnimation animLowFriction = new FlingAnimation(propertyNoObject);
        final FlingAnimation animHighFriction = new FlingAnimation(propertyNoObject);

        animHighFriction.setFriction(highFriction);
        animLowFriction.setFriction(lowFriction);

        DynamicAnimation.OnAnimationEndListener listener = mock(
                DynamicAnimation.OnAnimationEndListener.class);
        animHighFriction.addEndListener(listener);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                animHighFriction.setStartVelocity(5000).setStartValue(0).start();
                animLowFriction.setStartVelocity(5000).setStartValue(0).start();
            }
        });

        verify(listener, timeout(1000)).onAnimationEnd(eq(animHighFriction), eq(false), floatThat(
                new GreaterThan(200f)), eq(0f));
        // By the time high scalar animation finishes, the lower friction animation should still be
        // running.
        assertTrue(animLowFriction.isRunning());
        InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                animLowFriction.cancel();
            }
        });

        assertEquals(lowFriction, animLowFriction.getFriction(), 0f);
        assertEquals(highFriction, animHighFriction.getFriction(), 0f);

    }

    /**
     * Test that velocity threshold does affect how early fling animation ends. An animation with
     * higher velocity threshold should finish first.
     */
    @Test
    public void testVelocityThreshold() {
        FloatPropertyCompat propertyNoObject = mock(FloatPropertyCompat.class);
        float lowThreshold = 50f;
        final float highThreshold = 1000f;
        final FlingAnimation animLowThreshold = new FlingAnimation(propertyNoObject);
        final FlingAnimation animHighThreshold = new FlingAnimation(propertyNoObject);

        animHighThreshold.setVelocityThreshold(highThreshold);
        animHighThreshold.addUpdateListener(new DynamicAnimation.OnAnimationUpdateListener() {
            @Override
            public void onAnimationUpdate(DynamicAnimation animation, float value, float velocity) {
                if (velocity != 0f) {
                    // Other than last frame, velocity should always be above threshold
                    assertTrue(velocity >= highThreshold);
                }
            }
        });
        animLowThreshold.setVelocityThreshold(lowThreshold);

        DynamicAnimation.OnAnimationEndListener listener = mock(
                DynamicAnimation.OnAnimationEndListener.class);
        animHighThreshold.addEndListener(listener);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                animHighThreshold.setStartVelocity(2000).setStartValue(0).start();
                animLowThreshold.setStartVelocity(2000).setStartValue(0).start();
            }
        });

        verify(listener, timeout(1000)).onAnimationEnd(eq(animHighThreshold), eq(false), floatThat(
                new GreaterThan(200f)), eq(0f));
        // By the time high scalar animation finishes, the lower friction animation should still be
        // running.
        assertTrue(animLowThreshold.isRunning());
        InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                animLowThreshold.cancel();
            }
        });

        assertEquals(lowThreshold, animLowThreshold.getVelocityThreshold(), 0f);
        assertEquals(highThreshold, animHighThreshold.getVelocityThreshold(), 0f);

    }
}
