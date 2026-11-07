package com.harshal.scrollblocker;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.List;

import com.harshal.scrollblocker.managers.OverlayManager;
import com.harshal.scrollblocker.managers.TimeManager;

public class ScrollBlockerService extends AccessibilityService {

    private static final String TAG = "ScrollBlocker";
    private static final String YOUTUBE_PKG = "com.google.android.youtube";
    private static final String INSTAGRAM_PKG = "com.instagram.android";
    private static final String FACEBOOK_PKG = "com.facebook.katana";

    private static final String[] YOUTUBE_SHORTS_IDS = {
            "com.google.android.youtube:id/reel_watch_fragment_root",
            "com.google.android.youtube:id/reel_recycler"
    };
    private static final String INSTAGRAM_REELS_TAB = "com.instagram.android:id/clips_tab";

    // private static final long BLOCK_COOLDOWN_MS = 2500;

    private final Handler handler = new Handler(Looper.getMainLooper());
    // private long lastBlockTime = 0;
    private boolean delayShownForCurrentSession = false;
    private long lastYouTubeEventTime = 0;
    private boolean isInShortsOrReels = false;
    private boolean hasTriggeredBlock = false;
    
    private Runnable limitReachedRunnable = null;

    private void scheduleLimitCheck(long allowanceMs) {
        if (limitReachedRunnable != null) {
            handler.removeCallbacks(limitReachedRunnable);
        }
        
        long totalSpent = timeManager.calculateTotalTimeSpent();
        long remainingMs = allowanceMs - totalSpent;
        
        if (remainingMs <= 0) {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            String pkg = timeManager.getTrackingPackage();
            triggerBlockAndExit(allowanceMs, root, YOUTUBE_PKG.equals(pkg));
            return;
        }
        
        limitReachedRunnable = () -> {
            String pkg = timeManager.getTrackingPackage();
            if (pkg != null) {
                long spent = timeManager.calculateTotalTimeSpent();
                if (spent >= allowanceMs) {
                    AccessibilityNodeInfo root = getRootInActiveWindow();
                    triggerBlockAndExit(allowanceMs, root, YOUTUBE_PKG.equals(pkg));
                } else {
                    scheduleLimitCheck(allowanceMs);
                }
            }
        };
        
        handler.postDelayed(limitReachedRunnable, remainingMs);
    }

    private void triggerBlockAndExit(long allowanceMs, AccessibilityNodeInfo root, boolean isYoutube) {
        long now = System.currentTimeMillis();
        // if (now - lastBlockTime < BLOCK_COOLDOWN_MS) {
        //     return;
        // }
        hasTriggeredBlock = true;
        if (limitReachedRunnable != null) {
            handler.removeCallbacks(limitReachedRunnable);
            limitReachedRunnable = null;
        }
        if (timeManager != null) {
            timeManager.saveAndStopTracking();
        }
        
        // lastBlockTime = now;
        appPrefs.incrementBlockCount();
        
        int calculatedMins = Math.max(1, (int)(allowanceMs / 60000));
        
        overlayManager.showBlockedMessage("Time's up for today! 🎉", 
            "You crushed your " + calculatedMins + "-min limit. Time to get back to the real world. Resets at midnight.");
        exitShortsOrReels(root, isYoutube);
    }

    private void stopTrackingHelper() {
        if (limitReachedRunnable != null) {
            handler.removeCallbacks(limitReachedRunnable);
            limitReachedRunnable = null;
        }
        if (timeManager != null) {
            timeManager.saveAndStopTracking();
        }
    }
    private String currentForegroundPackage = "";

    private OverlayManager overlayManager;
    private TimeManager timeManager;
    private AppPrefs appPrefs;

    @Override
    public void onCreate() {
        super.onCreate();
        overlayManager = new OverlayManager(this);
        timeManager = new TimeManager(this);
        appPrefs = new AppPrefs(this);
    }

    @Override
    protected void onServiceConnected() {
        Log.d(TAG, "Service connected");
        if (overlayManager == null) {
            overlayManager = new OverlayManager(this);
            timeManager = new TimeManager(this);
            appPrefs = new AppPrefs(this);
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null) return;
        String pkg = event.getPackageName().toString();

        // If delay overlay is showing, any systemui event (like locking the phone) or non-monitored app must hide the overlay immediately
        if (overlayManager.isShowingDelayOverlay()) {
            if (pkg.equals("com.android.systemui") || (!YOUTUBE_PKG.equals(pkg) && !INSTAGRAM_PKG.equals(pkg) && !FACEBOOK_PKG.equals(pkg) && !pkg.equals(getPackageName()))) {
                overlayManager.hideDelayOverlay();
            }
        }

        // 1. App Open Delay Feature (Window State Tracking)
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            String className = event.getClassName() != null ? event.getClassName().toString() : "";
            
            // CRITICAL FIX: When we draw our delay overlay, Android fires a window state change 
            // for our own package (ScrollBlocker). We MUST ignore this, otherwise our own 
            // overlay triggers the "un-monitored app" logic and instantly hides itself!
            if (pkg.equals(getPackageName()) && !className.contains("MainActivity")) {
                return;
            }

            // Ignore System UI and keyboards changing states
            if (!pkg.equals("com.android.systemui") 
                    && !pkg.contains("inputmethod")
                    && !pkg.equals("com.google.android.permissioncontroller")) {
                handleWindowStateChanged(pkg);
            }
        }

        // 2. Existing Reels/Shorts Blocking
        boolean isYoutube = YOUTUBE_PKG.equals(pkg);
        boolean isInstagram = INSTAGRAM_PKG.equals(pkg);
        boolean isFacebook = FACEBOOK_PKG.equals(pkg);
        
        if (isYoutube || isInstagram || isFacebook) {
            long now = System.currentTimeMillis();
            if (now - lastYouTubeEventTime > 5000) {
                delayShownForCurrentSession = false;
            }
            lastYouTubeEventTime = now;
        }

        if (!isYoutube && !isInstagram && !isFacebook) {
             stopTrackingHelper();
            return;
        }
        
        if (overlayManager.isShowingDelayOverlay()) {
            stopTrackingHelper();
            return;
        }

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            // Can't check the screen, so pause tracking just in case they left the video player
            stopTrackingHelper();
            return; 
        }

        String reason = isYoutube ? detectYoutubeShorts(root) : (isInstagram ? detectInstagramReels(root) : detectFacebookReels(root));
        long now = System.currentTimeMillis();

        if (reason == null) {
            hasTriggeredBlock = false;
        } else if (hasTriggeredBlock) {
            return; // Ignore further events until user leaves shorts
        }
        
        if (isYoutube) {
            List<AccessibilityNodeInfo> watchPlayer = root.findAccessibilityNodeInfosByViewId("com.google.android.youtube:id/watch_player");
            boolean isNormalVideo = watchPlayer != null && !watchPlayer.isEmpty() && watchPlayer.get(0).isVisibleToUser();
            
            if (reason != null) {
                isInShortsOrReels = true;
            } else if (isNormalVideo) {
                isInShortsOrReels = false;
            }
        } else if (isInstagram) {
            if (reason != null) {
                isInShortsOrReels = true;
            } else {
                List<AccessibilityNodeInfo> feedTabs = root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/feed_tab");
                if (feedTabs != null && !feedTabs.isEmpty() && feedTabs.get(0).isSelected()) {
                    isInShortsOrReels = false;
                }
            }
        } else {
            if (reason != null) {
                isInShortsOrReels = true;
            }
        }

        if (isInShortsOrReels) {
            boolean isHardBlocked = appPrefs.isBlockEnabled(pkg);
            
            if (isHardBlocked) {
                long allowanceMs = appPrefs.getDailyAllowance(pkg) * 60 * 1000L;
                
                if (allowanceMs == 0) {
                    // if (now - lastBlockTime < BLOCK_COOLDOWN_MS) {
                    //     return;
                    // }
                    hasTriggeredBlock = true;
                    stopTrackingHelper();
                    isInShortsOrReels = false;
                    
                    // lastBlockTime = now;
                    appPrefs.incrementBlockCount();
                    String blockTitle = isYoutube ? "Shorts skipped ✨" : "Reels skipped ✨";
                    overlayManager.showBlockedMessage(blockTitle, "Protecting your focus.");
                    exitShortsOrReels(root, isYoutube);
                } else {
                    if (!timeManager.isTracking(pkg)) {
                         timeManager.startTrackingSession(pkg);
                         scheduleLimitCheck(allowanceMs);
                    }
                    
                    long totalTimeSpent = timeManager.updateAndGetCurrentTime(pkg);
                    
                    if (totalTimeSpent >= allowanceMs) {
                        triggerBlockAndExit(allowanceMs, root, isYoutube);
                        isInShortsOrReels = false;
                    }
                }
            } else {
                stopTrackingHelper();
                isInShortsOrReels = false;
            }
        } else {
            stopTrackingHelper();
        }
    }

    private void exitShortsOrReels(AccessibilityNodeInfo root, boolean isYoutube) {
        if (isYoutube && root != null) {
            String[] backButtonIds = {
                "com.google.android.youtube:id/back_button",
                "com.google.android.youtube:id/reel_back_button",
                "com.google.android.youtube:id/menu_button",
                "com.google.android.youtube:id/fullscreen_back_button"
            };
            
            for (String id : backButtonIds) {
                List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(id);
                if (nodes != null) {
                    for (AccessibilityNodeInfo node : nodes) {
                        if (node.isVisibleToUser() && node.isClickable()) {
                            if (node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                                Log.d(TAG, "Successfully clicked YouTube Shorts back button ID: " + id);
                                return;
                            }
                        }
                    }
                }
            }
            
            // Try clicking Home tab in bottom navigation to switch away from Shorts
            List<AccessibilityNodeInfo> homeIdNodes = root.findAccessibilityNodeInfosByViewId("com.google.android.youtube:id/menu_item_home");
            if (homeIdNodes != null) {
                for (AccessibilityNodeInfo node : homeIdNodes) {
                    if (node.isVisibleToUser() && node.isClickable()) {
                        if (node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                            Log.d(TAG, "Successfully clicked YouTube Home tab ID");
                            return;
                        }
                    }
                }
            }
            
            if (clickNodeByContentDescription(root, "back")) return;
            if (clickNodeByContentDescription(root, "navigate up")) return;
            if (clickNodeByContentDescription(root, "close")) return;
            
            List<AccessibilityNodeInfo> homeTextNodes = root.findAccessibilityNodeInfosByText("Home");
            if (homeTextNodes != null) {
                for (AccessibilityNodeInfo node : homeTextNodes) {
                    if (node.isVisibleToUser()) {
                        AccessibilityNodeInfo parentNode = node;
                        while (parentNode != null) {
                            if (parentNode.isClickable()) {
                                if (parentNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                                    Log.d(TAG, "Successfully clicked Home tab by text");
                                    return;
                                }
                            }
                            parentNode = parentNode.getParent();
                        }
                    }
                }
            }
            
            performGlobalAction(GLOBAL_ACTION_BACK);
        } else {
            performGlobalAction(GLOBAL_ACTION_BACK);
        }
    }

    private boolean clickNodeByContentDescription(AccessibilityNodeInfo node, String query) {
        if (node == null) return false;
        CharSequence desc = node.getContentDescription();
        if (desc != null && desc.toString().toLowerCase().contains(query.toLowerCase()) && node.isVisibleToUser()) {
            if (node.isClickable()) {
                return node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            } else {
                AccessibilityNodeInfo parent = node.getParent();
                while (parent != null) {
                    if (parent.isClickable()) {
                        return parent.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    }
                    parent = parent.getParent();
                }
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (clickNodeByContentDescription(node.getChild(i), query)) {
                return true;
            }
        }
        return false;
    }

    // --- NEW DELAY LOGIC ---

    private void handleWindowStateChanged(String newPkg) {
        currentForegroundPackage = newPkg;
        boolean isMonitored = YOUTUBE_PKG.equals(newPkg) || INSTAGRAM_PKG.equals(newPkg) || FACEBOOK_PKG.equals(newPkg);

        if (!isMonitored) {
            if (delayShownForCurrentSession) {
                delayShownForCurrentSession = false;
                Log.d(TAG, "Left monitored app. Reset delayShownForCurrentSession to false.");
            }
            if (!newPkg.equals(getPackageName())
                    && !newPkg.equals("com.android.systemui")
                    && !newPkg.contains("inputmethod")
                    && !newPkg.equals("com.google.android.permissioncontroller")) {
                timeManager.clearGracePeriods();
            }
        } else {
            if (!delayShownForCurrentSession) {
                delayShownForCurrentSession = true;
                checkAndShowDelayOverlay(newPkg);
            } else {
                Log.d(TAG, "IGNORED delay check - session already active for " + newPkg);
            }
        }
    }

    private void checkAndShowDelayOverlay(String pkg) {
        if (!appPrefs.isDelayEnabled(pkg)) return;
        
        Log.d(TAG, "Checking delay for " + pkg);

        if (timeManager.isInsideGracePeriod(pkg)) {
            Log.d(TAG, "Inside grace period. Bypassing delay.");
            return; // Currently inside the grace period
        }

        final String appName = YOUTUBE_PKG.equals(pkg) ? "YouTube" : (INSTAGRAM_PKG.equals(pkg) ? "Instagram" : "Facebook");

        Log.d(TAG, "SHOWING delay overlay for " + appName);
        overlayManager.showDelayOverlay(appName, appPrefs.getDelaySeconds(pkg), 
            () -> { // onNotNow
                Log.d(TAG, "User clicked 'Not now' on " + appName + " - performing GLOBAL_ACTION_BACK with 500ms debounce");
                overlayManager.hideDelayOverlay();
                timeManager.clearGracePeriods();
                performGlobalAction(GLOBAL_ACTION_BACK);
                
                handler.postDelayed(() -> {
                    delayShownForCurrentSession = false;
                    Log.d(TAG, "500ms exit debounce finished. Reset delayShownForCurrentSession to false.");
                }, 500);
            },
            () -> { // onOpenAnyway
                Log.d(TAG, "User clicked 'Open anyway' on " + appName + " - granting grace period");
                timeManager.grantGracePeriod(pkg);
                overlayManager.hideDelayOverlay();
            }
        );
    }

    // --- END DELAY LOGIC ---

    private String detectYoutubeShorts(AccessibilityNodeInfo root) {
        String id = findAnyId(root, YOUTUBE_SHORTS_IDS);
        if (id != null) return id;

        List<AccessibilityNodeInfo> matches = root.findAccessibilityNodeInfosByText("Shorts");
        if (matches != null) {
            for (AccessibilityNodeInfo n : matches) {
                CharSequence cls = n.getClassName();
                if (n.isSelected() && n.isVisibleToUser() && cls != null
                        && "android.widget.Button".contentEquals(cls)) {
                    return "selected Shorts tab";
                }
            }
        }
        return null;
    }

    private String detectInstagramReels(AccessibilityNodeInfo root) {
        if (root == null) return null;

        // 1. Check if Reels tab is explicitly selected
        List<AccessibilityNodeInfo> tabs = root.findAccessibilityNodeInfosByViewId(INSTAGRAM_REELS_TAB);
        if (tabs != null) {
            for (AccessibilityNodeInfo n : tabs) {
                if (n.isVisibleToUser() && n.isSelected()) {
                    return "selected Reels tab";
                }
            }
        }

        // 2. Check if Story markers are visible (segmented progress bar or reply box)
        List<AccessibilityNodeInfo> progressBar = root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/segmented_progress_bar");
        if (progressBar != null) {
            for (AccessibilityNodeInfo n : progressBar) {
                if (n.isVisibleToUser()) return null; // Story
            }
        }

        List<AccessibilityNodeInfo> composer = root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/toolbar_composer_container");
        if (composer != null) {
            for (AccessibilityNodeInfo n : composer) {
                if (n.isVisibleToUser()) return null; // Story
            }
        }

        // 3. Check if Search, Profile, or Inbox tabs are selected
        String[] nonReelTabs = {
            "com.instagram.android:id/search_tab",
            "com.instagram.android:id/explore_tab",
            "com.instagram.android:id/profile_tab",
            "com.instagram.android:id/direct_tab",
            "com.instagram.android:id/inbox_tab"
        };
        for (String tabId : nonReelTabs) {
            List<AccessibilityNodeInfo> tabNodes = root.findAccessibilityNodeInfosByViewId(tabId);
            if (tabNodes != null) {
                for (AccessibilityNodeInfo n : tabNodes) {
                    if (n.isVisibleToUser() && n.isSelected()) {
                        return null; // On Search, Profile, or Inbox
                    }
                }
            }
        }

        // 4. Check for visible clips video layout
        List<AccessibilityNodeInfo> videoLayouts = root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/clips_viewer_video_layout");
        if (videoLayouts != null && !videoLayouts.isEmpty()) {
            boolean hasVisibleVideo = false;
            for (AccessibilityNodeInfo vl : videoLayouts) {
                if (vl.isVisibleToUser()) {
                    hasVisibleVideo = true;
                    break;
                }
            }
            if (hasVisibleVideo) {
                // Ensure not home feed
                List<AccessibilityNodeInfo> actionBars = root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/action_bar_container");
                if (actionBars != null) {
                    for (AccessibilityNodeInfo ab : actionBars) {
                        if (ab.isVisibleToUser()) {
                            return null; // Home feed video
                        }
                    }
                }
                return "fullscreen Reel viewer";
            }
        }

        return null;
    }

    private String findAnyId(AccessibilityNodeInfo root, String[] ids) {
        for (String id : ids) {
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(id);
            if (nodes != null && !nodes.isEmpty()) return id;
        }
        return null;
    }

    private String detectFacebookReels(AccessibilityNodeInfo root) {
        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText("Reels");
        if (nodes != null) {
            for (AccessibilityNodeInfo n : nodes) {
                if (n.isVisibleToUser()) {
                    CharSequence desc = n.getContentDescription();
                    if (desc != null && desc.toString().toLowerCase().contains("tab")) {
                        if (n.isSelected()) {
                            Log.d(TAG, "FB Detect: Found Reels tab selected");
                            return "selected Facebook Reels tab";
                        }
                    }
                }
            }
        }

        // Check for Reels-specific UI markers in the fullscreen viewer
        if (hasNodeWithContentDescription(root, "Navigate to your Reels profile") ||
            hasNodeWithContentDescription(root, "reels profile")) {
            Log.d(TAG, "FB Detect: Found Reels profile navigation - inside Reels viewer");
            return "Facebook Reels fullscreen viewer";
        }

        return null;
    }

    private boolean hasNodeWithContentDescription(AccessibilityNodeInfo node, String query) {
        if (node == null) return false;
        CharSequence desc = node.getContentDescription();
        if (desc != null && desc.toString().toLowerCase().contains(query.toLowerCase()) && node.isVisibleToUser()) {
            return true;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (hasNodeWithContentDescription(node.getChild(i), query)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void onInterrupt() {
        if (overlayManager != null) overlayManager.hideDelayOverlay();
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        if (overlayManager != null) overlayManager.hideDelayOverlay();
        stopTrackingHelper();
    }
}