package com.harshal.scrollblocker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * BroadcastReceiver that listens for device reboot events to ensure
 * Scroll Blocker's preferences and background state are cleanly initialized.
 */
public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "ScrollBlockerBoot";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent != null && (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())
                || "android.intent.action.QUICKBOOT_POWERON".equals(intent.getAction()))) {
            Log.d(TAG, "Device rebooted. Initializing Scroll Blocker state.");
            
            // Initialize AppPrefs to trigger any startup checks (like midnight reset if needed)
            AppPrefs prefs = new AppPrefs(context);
            // Accessing a getter/setter triggers any lazy initialization or date alignment
            prefs.getBlockCount();
        }
    }
}