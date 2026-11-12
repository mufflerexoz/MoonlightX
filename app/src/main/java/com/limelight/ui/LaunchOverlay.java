package com.limelight.ui;

import android.app.Activity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.limelight.R;

/**
 * Full-screen "Initializing Moonlight… / Starting 'App'" screen shown while a stream
 * connects. It replaces the old "Establishing Connection" popup and keeps its behavior:
 * Back (or B on a controller) cancels and closes the stream.
 */
public class LaunchOverlay {
    private final Activity activity;
    private final View root;
    private final TextView stageView;
    private final View focusAfterDismiss;

    private LaunchOverlay(Activity activity, String appName, View focusAfterDismiss) {
        this.activity = activity;
        this.focusAfterDismiss = focusAfterDismiss;

        root = LayoutInflater.from(activity).inflate(R.layout.xbox_launch_overlay, null);
        stageView = root.findViewById(R.id.xboxLaunchStage);

        TextView appView = root.findViewById(R.id.xboxLaunchApp);
        if (appName != null && !appName.isEmpty()) {
            appView.setText(activity.getString(R.string.xbox_launch_app, appName));
        }
        else {
            appView.setVisibility(View.GONE);
        }

        // Use the background chosen in the MoonlightX background picker
        if (XboxUi.isDashboardBackground(activity)) {
            root.setBackgroundResource(R.drawable.xbox_background);
        }

        root.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_BUTTON_B) {
                    if (event.getAction() == KeyEvent.ACTION_UP) {
                        LaunchOverlay.this.activity.finish();
                    }
                    return true;
                }

                // Let volume keys through; swallow everything else so no input reaches
                // the stream before it is connected (as the old popup did)
                return keyCode != KeyEvent.KEYCODE_VOLUME_UP &&
                        keyCode != KeyEvent.KEYCODE_VOLUME_DOWN &&
                        keyCode != KeyEvent.KEYCODE_VOLUME_MUTE;
            }
        });
        root.setOnGenericMotionListener(new View.OnGenericMotionListener() {
            @Override
            public boolean onGenericMotion(View v, MotionEvent event) {
                return true;
            }
        });
    }

    public static LaunchOverlay show(Activity activity, String appName, View focusAfterDismiss) {
        LaunchOverlay overlay = new LaunchOverlay(activity, appName, focusAfterDismiss);
        activity.addContentView(overlay.root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        overlay.root.requestFocus();
        return overlay;
    }

    public void setMessage(final String message) {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                stageView.setText(message);
            }
        });
    }

    public void dismiss() {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ViewGroup parent = (ViewGroup) root.getParent();
                if (parent != null) {
                    parent.removeView(root);
                }
                if (focusAfterDismiss != null) {
                    focusAfterDismiss.requestFocus();
                }
            }
        });
    }
}
