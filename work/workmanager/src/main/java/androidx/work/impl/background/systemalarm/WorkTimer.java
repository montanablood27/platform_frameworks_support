/*
 * Copyright 2018 The Android Open Source Project
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

package androidx.work.impl.background.systemalarm;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;
import androidx.annotation.VisibleForTesting;
import androidx.work.Logger;
import androidx.work.WorkRequest;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * Manages timers to enforce a time limit for processing {@link WorkRequest}.
 * Notifies a {@link TimeLimitExceededListener} when the time limit
 * is exceeded.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
class WorkTimer {

    private static final String TAG = Logger.tagWithPrefix("WorkTimer");

    private final ThreadFactory mBackgroundThreadFactory = new ThreadFactory() {

        private int mThreadsCreated = 0;

        @Override
        public Thread newThread(@NonNull Runnable r) {
            // Delegate to the default factory, but keep track of the current thread being used.
            Thread thread = Executors.defaultThreadFactory().newThread(r);
            thread.setName("WorkManager-WorkTimer-thread-" + mThreadsCreated);
            mThreadsCreated++;
            return thread;
        }
    };

    private final ScheduledExecutorService mExecutorService;
    final Map<String, WorkTimerRunnable> mTimerMap;
    final Map<String, TimeLimitExceededListener> mListeners;
    final Object mLock;

    WorkTimer() {
        mTimerMap = new HashMap<>();
        mListeners = new HashMap<>();
        mLock = new Object();
        mExecutorService = Executors.newSingleThreadScheduledExecutor(mBackgroundThreadFactory);
    }

    @SuppressWarnings("FutureReturnValueIgnored")
    void startTimer(@NonNull final String workSpecId,
            long processingTimeMillis,
            @NonNull TimeLimitExceededListener listener) {

        synchronized (mLock) {
            Logger.get().debug(TAG, String.format("Starting timer for %s", workSpecId));
            // clear existing timer's first
            stopTimer(workSpecId);
            WorkTimerRunnable runnable = new WorkTimerRunnable(this, workSpecId);
            mTimerMap.put(workSpecId, runnable);
            mListeners.put(workSpecId, listener);
            mExecutorService.schedule(runnable, processingTimeMillis, TimeUnit.MILLISECONDS);
        }
    }

    void stopTimer(@NonNull final String workSpecId) {
        synchronized (mLock) {
            WorkTimerRunnable removed = mTimerMap.remove(workSpecId);
            if (removed != null) {
                Logger.get().debug(TAG, String.format("Stopping timer for %s", workSpecId));
                mListeners.remove(workSpecId);
            }
        }
    }

    void onDestroy() {
        // Calling shutdown() waits for pending scheduled WorkTimerRunnable's which is not
        // something we care about. Hence call shutdownNow().
        mExecutorService.shutdownNow();
    }

    @VisibleForTesting
    synchronized Map<String, WorkTimerRunnable> getTimerMap() {
        return mTimerMap;
    }

    @VisibleForTesting
    synchronized Map<String, TimeLimitExceededListener> getListeners() {
        return mListeners;
    }

    @VisibleForTesting
    ScheduledExecutorService getExecutorService() {
        return mExecutorService;
    }

    /**
     * The actual runnable scheduled on the scheduled executor.
     */
    static class WorkTimerRunnable implements Runnable {
        static final String TAG = "WrkTimerRunnable";

        private final WorkTimer mWorkTimer;
        private final String mWorkSpecId;

        WorkTimerRunnable(@NonNull WorkTimer workTimer, @NonNull String workSpecId) {
            mWorkTimer = workTimer;
            mWorkSpecId = workSpecId;
        }

        @Override
        public void run() {
            synchronized (mWorkTimer.mLock) {
                WorkTimerRunnable removed = mWorkTimer.mTimerMap.remove(mWorkSpecId);
                if (removed != null) {
                    // notify time limit exceeded.
                    TimeLimitExceededListener listener = mWorkTimer.mListeners.remove(mWorkSpecId);
                    if (listener != null) {
                        listener.onTimeLimitExceeded(mWorkSpecId);
                    }
                } else {
                    Logger.get().debug(TAG, String.format(
                            "Timer with %s is already marked as complete.", mWorkSpecId));
                }
            }
        }
    }

    interface TimeLimitExceededListener {
        void onTimeLimitExceeded(@NonNull String workSpecId);
    }
}
