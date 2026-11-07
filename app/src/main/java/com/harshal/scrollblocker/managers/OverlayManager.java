package com.harshal.scrollblocker.managers;

import android.content.Context;
import android.graphics.PixelFormat;
import android.os.CountDownTimer;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.ProgressBar;
import android.widget.TextView;
import com.harshal.scrollblocker.R;

/**
 * Handles the creation and destruction of TYPE_ACCESSIBILITY_OVERLAY windows.
 */
public class OverlayManager {

    private final Context context;
    private final WindowManager windowManager;
    
    private View delayOverlayView;
    private CountDownTimer delayTimer;
    private boolean isShowingDelayOverlay = false;
    
    private View blockedOverlayView;

    public OverlayManager(Context context) {
        this.context = context;
        this.windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
    }

    public boolean isShowingDelayOverlay() {
        return isShowingDelayOverlay;
    }

    /**
     * Shows the "Wait before opening" screen.
     */
    public void showDelayOverlay(String appName, int delaySeconds, Runnable onNotNow, Runnable onOpenAnyway) {
        if (isShowingDelayOverlay) return;
        isShowingDelayOverlay = true;

        ContextThemeWrapper ctx = new ContextThemeWrapper(context, R.style.Theme_ScrollBlocker);
        LayoutInflater inflater = LayoutInflater.from(ctx);
        delayOverlayView = inflater.inflate(R.layout.overlay_delay, null);

        TextView tvOpeningApp = delayOverlayView.findViewById(R.id.tvOpeningApp);
        TextView tvBody = delayOverlayView.findViewById(R.id.tvBody);
        TextView tvSeconds = delayOverlayView.findViewById(R.id.tvSeconds);
        ProgressBar progressBar = delayOverlayView.findViewById(R.id.progressBar);
        TextView btnNotNow = delayOverlayView.findViewById(R.id.btnNotNow);
        TextView btnOpenAnyway = delayOverlayView.findViewById(R.id.btnOpenAnyway);

        tvOpeningApp.setText("Opening " + appName);
        tvBody.setText("Do you still want to open " + appName + " right now?");

        long totalMillis = delaySeconds * 1000L;
        progressBar.setMax((int) totalMillis);
        progressBar.setProgress((int) totalMillis);
        tvSeconds.setText(String.valueOf(delaySeconds));

        btnNotNow.setOnClickListener(v -> onNotNow.run());
        btnOpenAnyway.setOnClickListener(v -> onOpenAnyway.run());

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);

        delayOverlayView.setOnTouchListener((v, event) -> event.getY() >= 120);

        try {
            windowManager.addView(delayOverlayView, lp);
            android.util.Log.d("OverlayManager", "Successfully added delay overlay view for " + appName);
        } catch (Exception e) {
            android.util.Log.e("OverlayManager", "Failed to add delay overlay view", e);
            isShowingDelayOverlay = false;
            return;
        }

        delayTimer = new CountDownTimer(totalMillis, 50) {
            @Override
            public void onTick(long millisUntilFinished) {
                int secondsLeft = (int) Math.ceil(millisUntilFinished / 1000.0);
                tvSeconds.setText(String.valueOf(secondsLeft));
                progressBar.setProgress((int) millisUntilFinished);
                btnOpenAnyway.setText("Open anyway in " + secondsLeft + "s");
            }

            @Override
            public void onFinish() {
                tvSeconds.setText("0");
                progressBar.setProgress(0);
                btnOpenAnyway.setText("Open anyway");
                btnOpenAnyway.setEnabled(true);
                // We use the application context's resources to get the color, so it's immune to theme issues
                btnOpenAnyway.setTextColor(context.getResources().getColor(R.color.text_primary, null));
            }
        }.start();
    }

    public void hideDelayOverlay() {
        if (!isShowingDelayOverlay) return;
        
        if (delayTimer != null) {
            delayTimer.cancel();
            delayTimer = null;
        }
        
        if (delayOverlayView != null) {
            try { windowManager.removeView(delayOverlayView); } catch (Exception ignored) {}
            delayOverlayView = null;
        }
        
        isShowingDelayOverlay = false;
    }

    /**
     * Shows the "Shorts not allowed" screen and auto-dismisses it.
     */
    public void showBlockedMessage(String title, String body) {
        if (blockedOverlayView != null) {
            try { windowManager.removeView(blockedOverlayView); } catch (Exception ignored) { }
            blockedOverlayView = null;
        }

        LayoutInflater inflater = LayoutInflater.from(new ContextThemeWrapper(context, R.style.Theme_ScrollBlocker));
        blockedOverlayView = inflater.inflate(R.layout.overlay_blocked, null);
        
        TextView tvTitle = blockedOverlayView.findViewById(R.id.tvBlockTitle);
        TextView tvBody = blockedOverlayView.findViewById(R.id.tvBlockBody);
        
        tvTitle.setText(title);
        if (body != null && !body.isEmpty()) {
            tvBody.setText(body);
            tvBody.setVisibility(View.VISIBLE);
        } else {
            tvBody.setVisibility(View.GONE);
        }

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.CENTER;

        // Add standard fade animation
        lp.windowAnimations = android.R.style.Animation_Toast;

        try {
            windowManager.addView(blockedOverlayView, lp);
            
            // Auto dismiss the visual overlay quickly (1 second) so it doesn't linger 
            // over other screens if the back button kicks them out fast
            blockedOverlayView.postDelayed(() -> {
                if (blockedOverlayView != null) {
                    try { windowManager.removeView(blockedOverlayView); } catch (Exception ignored) { }
                    blockedOverlayView = null;
                }
            }, 1200);
            
        } catch (Exception ignored) {}
    }
}