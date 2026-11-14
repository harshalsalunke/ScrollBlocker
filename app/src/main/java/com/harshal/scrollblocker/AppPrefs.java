package com.harshal.scrollblocker;

import android.content.Context;
import android.content.SharedPreferences;

public class AppPrefs {
    private static final String PREFS_NAME = "ScrollBlockerPrefs";
    private final SharedPreferences prefs;

    public AppPrefs(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public boolean isDelayEnabled(String pkg) {
        return prefs.getBoolean("delay_enabled_" + pkg, true);
    }

    public void setDelayEnabled(String pkg, boolean enabled) {
        prefs.edit().putBoolean("delay_enabled_" + pkg, enabled).apply();
    }

    public int getDelaySeconds(String pkg) {
        int val = prefs.getInt("delay_seconds_" + pkg, -1);
        if (isValidDelay(val)) return val;
        
        // Fall back to legacy global delay if it is valid
        int legacyVal = prefs.getInt("delay_seconds", -1);
        if (isValidDelay(legacyVal)) return legacyVal;
        
        // Per-app defaults
        if ("com.google.android.youtube".equals(pkg)) return 5;
        if ("com.instagram.android".equals(pkg)) return 10;
        return 10;
    }

    public void setDelaySeconds(String pkg, int seconds) {
        if (isValidDelay(seconds)) {
            prefs.edit().putInt("delay_seconds_" + pkg, seconds).apply();
        }
    }

    private boolean isValidDelay(int seconds) {
        return seconds == 5 || seconds == 10 || seconds == 15 || seconds == 20;
    }

    public int getDailyAllowance(String pkg) {
        int def = "com.google.android.youtube".equals(pkg) ? 5 : 0;
        return prefs.getInt("allowance_" + pkg, def);
    }
    
    public void setDailyAllowance(String pkg, int minutes) {
        prefs.edit().putInt("allowance_" + pkg, minutes).apply();
    }

    public boolean isBlockEnabled(String pkg) {
        // Map to legacy keys for compatibility with older installs
        if ("com.google.android.youtube".equals(pkg)) {
            return prefs.getBoolean("block_youtube", true);
        } else if ("com.instagram.android".equals(pkg)) {
            return prefs.getBoolean("block_instagram", true);
        }
        return prefs.getBoolean("block_" + pkg, true);
    }

    public void setBlockEnabled(String pkg, boolean enabled) {
        if ("com.google.android.youtube".equals(pkg)) {
            prefs.edit().putBoolean("block_youtube", enabled).apply();
        } else if ("com.instagram.android".equals(pkg)) {
            prefs.edit().putBoolean("block_instagram", enabled).apply();
        } else {
            prefs.edit().putBoolean("block_" + pkg, enabled).apply();
        }
    }

    public int getGraceMinutes() {
        return prefs.getInt("grace_minutes", 1);
    }

    public void setGraceMinutes(int minutes) {
        prefs.edit().putInt("grace_minutes", minutes).apply();
    }

    public int getBlockCount() {
        return prefs.getInt("block_count", 0);
    }

    public void incrementBlockCount() {
        prefs.edit().putInt("block_count", getBlockCount() + 1).apply();
    }

    // --- DAILY TIME LIMIT LOGIC ---

    public long getShortsTime(String pkg) {
        checkMidnightReset();
        long time = prefs.getLong("shorts_time_" + pkg, 0);
        long allowanceMinutes = getDailyAllowance(pkg);
        long clampedTime = (allowanceMinutes > 0) ? Math.min(time, allowanceMinutes * 60 * 1000L) : time;
        android.util.Log.d("AppPrefsDebug", "getShortsTime for " + pkg + " = " + clampedTime);
        return clampedTime;
    }

    public void setShortsTime(String pkg, long timeMillis) {
        long allowanceMinutes = getDailyAllowance(pkg);
        long clampedTimeMillis = (allowanceMinutes > 0) ? Math.min(timeMillis, allowanceMinutes * 60 * 1000L) : timeMillis;
        android.util.Log.d("AppPrefsDebug", "setShortsTime for " + pkg + " = " + clampedTimeMillis);
        prefs.edit().putLong("shorts_time_" + pkg, clampedTimeMillis).commit(); // Synchronous commit to prevent rollbacks
    }

    public boolean hasPromptedBatteryOptimization() {
        return prefs.getBoolean("prompted_battery_opt", false);
    }

    public void setPromptedBatteryOptimization(boolean prompted) {
        prefs.edit().putBoolean("prompted_battery_opt", prompted).apply();
    }

    private void checkMidnightReset() {
        String today = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                .format(new java.util.Date());
        String savedDate = prefs.getString("last_active_date", "");
        android.util.Log.d("AppPrefsDebug", "checkMidnightReset: today=" + today + ", savedDate=" + savedDate);

        if (!today.equals(savedDate)) {
            android.util.Log.d("AppPrefsDebug", "MIDNIGHT RESET TRIGGERED! Resetting times to 0.");
            // It is a new day! Reset daily metrics
            android.content.SharedPreferences.Editor editor = prefs.edit()
                    .putString("last_active_date", today)
                    .putInt("block_count", 0); // Reset "Blocked Today" counter

            // Dynamically reset all accumulated time counters for any monitored app
            for (String key : prefs.getAll().keySet()) {
                if (key.startsWith("shorts_time_")) {
                    editor.putLong(key, 0);
                }
            }
            editor.commit(); // Synchronous commit to prevent asynchronous race conditions on restart
        }
    }
}