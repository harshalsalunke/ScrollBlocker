package com.harshal.scrollblocker;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

public class MainActivity extends AppCompatActivity {

    private View activeStateContainer;
    private View inactiveStateContainer;
    private TextView btnAccessibility;
    private LinearLayout appsContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        activeStateContainer = findViewById(R.id.activeStateContainer);
        inactiveStateContainer = findViewById(R.id.inactiveStateContainer);
        appsContainer = findViewById(R.id.appsContainer);
        
        btnAccessibility = findViewById(R.id.btnAccessibility);
        btnAccessibility.setOnClickListener(v -> {
            new AlertDialog.Builder(this, R.style.ThemeOverlay_ScrollBlocker_AlertDialog)
                .setTitle("Accessibility Permission Required")
                .setMessage("Scroll Blocker needs Accessibility Service access solely to detect when you open YouTube Shorts, Instagram Reels, or Facebook Reels so it can apply your mindful delays.\n\nWe never read your personal messages or private data.")
                .setPositiveButton("Continue", (dialog, which) -> {
                    startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                })
                .setNegativeButton("Cancel", null)
                .show();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateUI();
    }

    private void updateUI() {
        boolean isEnabled = isAccessibilityServiceEnabled(this, ScrollBlockerService.class);
        
        if (isEnabled) {
            activeStateContainer.setVisibility(View.VISIBLE);
            inactiveStateContainer.setVisibility(View.GONE);
            
            checkBatteryOptimization();

            // Populate statistics
            AppPrefs prefs = new AppPrefs(this);
            int blockCount = prefs.getBlockCount();
            int minutesSaved = blockCount * 3; // Estimate ~3 minutes saved per blocked attempt
            
            TextView tvBlocksCount = findViewById(R.id.tvBlocksCount);
            TextView tvTimeSaved = findViewById(R.id.tvTimeSaved);
            if (tvBlocksCount != null) tvBlocksCount.setText(String.valueOf(blockCount));
            if (tvTimeSaved != null) tvTimeSaved.setText(minutesSaved + " mins");

            populateAppsList();
        } else {
            activeStateContainer.setVisibility(View.GONE);
            inactiveStateContainer.setVisibility(View.VISIBLE);
        }
    }

    private void populateAppsList() {
        appsContainer.removeAllViews();
        addAppCard("com.google.android.youtube", "YouTube", "Shorts");
        addAppCard("com.instagram.android", "Instagram", "Reels");
        addAppCard("com.facebook.katana", "Facebook", "Reels");
    }

    private void addAppCard(String pkg, String appName, String contentType) {
        View card = LayoutInflater.from(this).inflate(R.layout.item_app_card, appsContainer, false);
        
        TextView tvAppName = card.findViewById(R.id.tvAppName);
        TextView tvBlockTitle = card.findViewById(R.id.tvBlockTitle);
        TextView tvBlockDesc = card.findViewById(R.id.tvBlockDesc);
        SwitchCompat switchBlock = card.findViewById(R.id.switchBlock);
        
        View allowanceClickArea = card.findViewById(R.id.allowanceClickArea);
        TextView tvAllowanceDesc = card.findViewById(R.id.tvAllowanceDesc);
        ProgressBar progressAllowance = card.findViewById(R.id.progressAllowance);
        
        View delayClickArea = card.findViewById(R.id.delayClickArea);
        TextView tvDelayDesc = card.findViewById(R.id.tvDelayDesc);
        SwitchCompat switchDelay = card.findViewById(R.id.switchDelay);

        tvAppName.setText(appName);
        tvBlockTitle.setText("Block " + contentType);
        tvBlockDesc.setText("Turn off to allow " + contentType + " freely");

        AppPrefs appPrefs = new AppPrefs(this);
        
        switchBlock.setChecked(appPrefs.isBlockEnabled(pkg));
        switchBlock.setOnCheckedChangeListener((btn, isChecked) -> {
            appPrefs.setBlockEnabled(pkg, isChecked);
            // Wait, we don't dynamically hide/show the row based on block state here, 
            // but the service respects the block state.
        });

        // Setup Allowance row
        updateAllowanceDesc(tvAllowanceDesc, progressAllowance, pkg, appPrefs.getDailyAllowance(pkg), appPrefs.getShortsTime(pkg));
        allowanceClickArea.setOnClickListener(v -> 
            showAllowancePicker(pkg, contentType, tvAllowanceDesc, progressAllowance)
        );

        switchDelay.setChecked(appPrefs.isDelayEnabled(pkg));
        switchDelay.setOnCheckedChangeListener((btn, isChecked) -> {
            appPrefs.setDelayEnabled(pkg, isChecked);
            updateDelayDesc(tvDelayDesc, delayClickArea, appName, appPrefs.getDelaySeconds(pkg), isChecked);
        });

        // Initialize description and content description
        updateDelayDesc(tvDelayDesc, delayClickArea, appName, appPrefs.getDelaySeconds(pkg), switchDelay.isChecked());

        // Handle tapping the delay row to show picker
        delayClickArea.setOnClickListener(v -> 
            showDelayPicker(pkg, appName, tvDelayDesc, delayClickArea, switchDelay)
        );

        appsContainer.addView(card);
    }
    
    private void updateAllowanceDesc(TextView tvDesc, ProgressBar progress, String pkg, int allowance, long timeSpent) {
        if (allowance == 0) {
            tvDesc.setText("None, always blocked");
            progress.setVisibility(View.GONE);
        } else {
            long allowanceMs = allowance * 60 * 1000L;
            long clampedTimeSpent = Math.min(timeSpent, allowanceMs);
            progress.setVisibility(View.VISIBLE);
            progress.setMax((int) allowanceMs);
            progress.setProgress((int) clampedTimeSpent);
            
            long seconds = clampedTimeSpent / 1000;
            String timeStr = String.format("%d:%02d", seconds / 60, seconds % 60);
            String fullText = timeStr + " of " + allowance + " mins used today";
            
            android.text.SpannableString span = new android.text.SpannableString(fullText);
            span.setSpan(new android.text.style.ForegroundColorSpan(androidx.core.content.ContextCompat.getColor(this, R.color.accent)), 0, timeStr.length(), android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            tvDesc.setText(span);
        }
    }

    private void showAllowancePicker(String pkg, String contentType, TextView tvDesc, ProgressBar progress) {
        AppPrefs appPrefs = new AppPrefs(this);
        int current = appPrefs.getDailyAllowance(pkg);
        int[] options = {0, 1, 5, 10, 15};
        String[] items = {"None (always blocked)", "1 minute", "5 minutes", "10 minutes", "15 minutes"};
        
        int checkedItem = 0;
        for (int i = 0; i < options.length; i++) {
            if (options[i] == current) checkedItem = i;
        }

        new AlertDialog.Builder(this, R.style.ThemeOverlay_ScrollBlocker_AlertDialog)
            .setTitle("Daily allowance for " + contentType)
            .setSingleChoiceItems(items, checkedItem, (dialog, which) -> {
                int selected = options[which];
                appPrefs.setDailyAllowance(pkg, selected);
                updateAllowanceDesc(tvDesc, progress, pkg, selected, appPrefs.getShortsTime(pkg));
                dialog.dismiss();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }
    
    private void updateDelayDesc(TextView tvDesc, View clickArea, String appName, int seconds, boolean enabled) {
        if (enabled) {
            String timeText = seconds + " seconds";
            String fullText = timeText + ", then confirm · tap to change";
            
            // Create a SpannableString to color only the "X seconds" part orange
            android.text.SpannableString spannable = new android.text.SpannableString(fullText);
            int colorOrange = androidx.core.content.ContextCompat.getColor(this, R.color.accent);
            
            spannable.setSpan(
                new android.text.style.ForegroundColorSpan(colorOrange),
                0, 
                timeText.length(), 
                android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            );
            
            tvDesc.setText(spannable);
        } else {
            tvDesc.setText("Off · tap to change");
        }
        
        clickArea.setContentDescription("Delay for " + appName + ": " + seconds + " seconds. Tap to change.");
    }

    private void showDelayPicker(String pkg, String appName, TextView tvDelayDesc, View clickArea, SwitchCompat switchDelay) {
        AppPrefs appPrefs = new AppPrefs(this);
        int current = appPrefs.getDelaySeconds(pkg);
        int[] options = {5, 10, 15, 20};
        String[] items = {"5 seconds", "10 seconds", "15 seconds", "20 seconds"};
        
        int checkedItem = 1; // Default to 10 seconds (index 1)
        for (int i = 0; i < options.length; i++) {
            if (options[i] == current) checkedItem = i;
        }

        new AlertDialog.Builder(this, R.style.ThemeOverlay_ScrollBlocker_AlertDialog)
            .setTitle("Wait before opening " + appName)
            .setSingleChoiceItems(items, checkedItem, (dialog, which) -> {
                int selected = options[which];
                appPrefs.setDelaySeconds(pkg, selected);
                // Update the UI string instantly to reflect choice
                updateDelayDesc(tvDelayDesc, clickArea, appName, selected, switchDelay.isChecked());
                dialog.dismiss();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void checkBatteryOptimization() {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
            AppPrefs prefs = new AppPrefs(this);
            if (!prefs.hasPromptedBatteryOptimization()) {
                new AlertDialog.Builder(this, R.style.ThemeOverlay_ScrollBlocker_AlertDialog)
                    .setTitle("Ensure 100% Reliable Delays")
                    .setMessage("Android's battery saver can put Scroll Blocker to sleep. To guarantee your delay screens always trigger on time, allow Scroll Blocker to run without battery optimization.")
                    .setPositiveButton("Allow", (dialog, which) -> {
                        prefs.setPromptedBatteryOptimization(true);
                        try {
                            Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                            intent.setData(Uri.parse("package:" + getPackageName()));
                            startActivity(intent);
                        } catch (Exception e) {
                            Intent intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                            startActivity(intent);
                        }
                    })
                    .setNegativeButton("Later", (dialog, which) -> {
                        prefs.setPromptedBatteryOptimization(true);
                    })
                    .show();
            }
        }
    }

    public static boolean isAccessibilityServiceEnabled(Context context, Class<?> accessibilityService) {
        int accessibilityEnabled = 0;
        final String service = context.getPackageName() + "/" + accessibilityService.getCanonicalName();
        try {
            accessibilityEnabled = Settings.Secure.getInt(
                    context.getApplicationContext().getContentResolver(),
                    android.provider.Settings.Secure.ACCESSIBILITY_ENABLED);
        } catch (Settings.SettingNotFoundException e) {
            // Ignored
        }
        TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(':');

        if (accessibilityEnabled == 1) {
            String settingValue = Settings.Secure.getString(
                    context.getApplicationContext().getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (settingValue != null) {
                splitter.setString(settingValue);
                while (splitter.hasNext()) {
                    if (splitter.next().equalsIgnoreCase(service)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}