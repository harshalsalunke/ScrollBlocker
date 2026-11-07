package com.harshal.scrollblocker.managers;

import android.content.Context;
import android.os.SystemClock;
import com.harshal.scrollblocker.AppPrefs;
import java.util.HashMap;
import java.util.Map;

/**
 * Handles all time-based logic including the daily allowance stopwatch and grace periods.
 * Uses SystemClock.elapsedRealtime() to prevent users from cheating by changing the device time.
 */
public class TimeManager {

    private final AppPrefs prefs;
    private final Map<String, Long> gracePeriods = new HashMap<>();
    
    // Stopwatch state
    private String trackingPackage = null;
    private long currentSessionStartTimeRealtime = 0;
    private long timeAlreadySpentBeforeSession = 0;

    public TimeManager(Context context) {
        this.prefs = new AppPrefs(context);
    }

    // --- GRACE PERIOD LOGIC ---

    public void grantGracePeriod(String pkg) {
        long graceMillis = prefs.getGraceMinutes() * 60 * 1000L;
        // Use elapsedRealtime so it's immune to clock changes
        gracePeriods.put(pkg, SystemClock.elapsedRealtime() + graceMillis);
    }

    public boolean isInsideGracePeriod(String pkg) {
        long graceEnd = gracePeriods.getOrDefault(pkg, 0L);
        return SystemClock.elapsedRealtime() < graceEnd;
    }

    public void clearGracePeriods() {
        gracePeriods.clear();
    }

    // --- STOPWATCH LOGIC ---

    public boolean isTracking(String pkg) {
        return trackingPackage != null && trackingPackage.equals(pkg);
    }
    
    public String getTrackingPackage() {
        return trackingPackage;
    }

    public void startTrackingSession(String pkg) {
        trackingPackage = pkg;
        timeAlreadySpentBeforeSession = prefs.getShortsTime(pkg);
        currentSessionStartTimeRealtime = SystemClock.elapsedRealtime();
    }

    public void pauseSession() {
        if (trackingPackage != null) {
            long now = SystemClock.elapsedRealtime();
            long timeSpentThisSession = now - currentSessionStartTimeRealtime;
            timeAlreadySpentBeforeSession += timeSpentThisSession;
            currentSessionStartTimeRealtime = now;
        }
    }

    public long calculateTotalTimeSpent() {
        if (trackingPackage == null) return 0;
        long now = SystemClock.elapsedRealtime();
        long timeSpentThisSession = now - currentSessionStartTimeRealtime;
        long total = timeAlreadySpentBeforeSession + timeSpentThisSession;
        long allowanceMinutes = prefs.getDailyAllowance(trackingPackage);
        if (allowanceMinutes > 0) {
            long allowanceMs = allowanceMinutes * 60 * 1000L;
            if (total > allowanceMs) {
                total = allowanceMs;
            }
        }
        return total;
    }

    public void saveAndStopTracking() {
        if (trackingPackage != null) {
            long totalTimeSpent = calculateTotalTimeSpent();
            long allowanceMinutes = prefs.getDailyAllowance(trackingPackage);
            if (allowanceMinutes > 0) {
                long allowanceMs = allowanceMinutes * 60 * 1000L;
                if (totalTimeSpent > allowanceMs) {
                    totalTimeSpent = allowanceMs;
                }
            }
            long previousTime = prefs.getShortsTime(trackingPackage);
            // Safeguard: Never overwrite a valid recorded time with 0 or a lower value
            if (totalTimeSpent > 0 && totalTimeSpent >= previousTime) {
                prefs.setShortsTime(trackingPackage, totalTimeSpent);
            }
        }
        trackingPackage = null;
    }

    public long updateAndGetCurrentTime(String pkg) {
        if (trackingPackage == null) {
            long stored = prefs.getShortsTime(pkg);
            long allowanceMinutes = prefs.getDailyAllowance(pkg);
            if (allowanceMinutes > 0) {
                long allowanceMs = allowanceMinutes * 60 * 1000L;
                if (stored > allowanceMs) {
                    return allowanceMs;
                }
            }
            return stored;
        }
        long totalTimeSpent = calculateTotalTimeSpent();
        long allowanceMinutes = prefs.getDailyAllowance(pkg);
        if (allowanceMinutes > 0) {
            long allowanceMs = allowanceMinutes * 60 * 1000L;
            if (totalTimeSpent > allowanceMs) {
                totalTimeSpent = allowanceMs;
            }
        }
        long previousTime = prefs.getShortsTime(pkg);
        if (totalTimeSpent > 0 && totalTimeSpent >= previousTime) {
            prefs.setShortsTime(pkg, totalTimeSpent);
        }
        return totalTimeSpent;
    }
}