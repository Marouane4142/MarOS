package io.github.marouane4142.maros;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;

/** Filters only the four dedicated application buttons. No window content is requested. */
public final class RemoteButtonService extends AccessibilityService {
    public static final String[] TARGETS = {"youtube", "netflix", "apps", "prime"};
    public static final String[] LABELS = {"YouTube", "Netflix", "Applications", "Prime Video"};
    private static final int[] DEFAULT_KEYS = {190, 193, 284, 194};
    private final SparseBooleanArray consumed = new SparseBooleanArray();

    public static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences("tvhome_v2", Context.MODE_PRIVATE);
    }

    public static int key(Context context, String target) {
        for (int i = 0; i < TARGETS.length; i++) {
            if (TARGETS[i].equals(target)) return prefs(context).getInt("remote_key_" + target, DEFAULT_KEYS[i]);
        }
        return -1;
    }

    public static boolean isEnabled(Context context) {
        String enabled = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabled == null) return false;
        String our = new android.content.ComponentName(context, RemoteButtonService.class).flattenToString();
        for (String item : enabled.split(":")) {
            android.content.ComponentName component = android.content.ComponentName.unflattenFromString(item);
            if (component != null && our.equals(component.flattenToString())) return true;
        }
        return false;
    }

    /** Learning never consumes navigation, volume, power, voice, letters or digit keys. */
    public static boolean learnable(int code) {
        return (code >= KeyEvent.KEYCODE_BUTTON_1 && code <= KeyEvent.KEYCODE_BUTTON_16)
                || (code >= KeyEvent.KEYCODE_F1 && code <= KeyEvent.KEYCODE_F12)
                || code == 284 || code >= 288;
    }

    public static boolean learn(Context context, KeyEvent event) {
        SharedPreferences p = prefs(context);
        String target = p.getString("remote_learning_target", "");
        long until = p.getLong("remote_learning_until", 0);
        if (target.isEmpty() || until < System.currentTimeMillis() || !learnable(event.getKeyCode())) return false;
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
            // Explicit short calibration only. Never log keys during ordinary use.
            Log.i("TvHomeRemote", "Calibration " + target + " keyCode=" + event.getKeyCode() + " scanCode=" + event.getScanCode());
            SharedPreferences.Editor edit = p.edit();
            for (String t : TARGETS) {
                if (!t.equals(target) && key(context, t) == event.getKeyCode()) edit.putInt("remote_key_" + t, -1);
            }
            edit.putInt("remote_key_" + target, event.getKeyCode())
                    .putString("remote_learning_target", "").putLong("remote_learning_until", 0)
                    .putString("remote_learning_result", target + " : " + KeyEvent.keyCodeToString(event.getKeyCode())).apply();
        }
        return true;
    }

    @Override protected boolean onKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        if (event.getAction() == KeyEvent.ACTION_UP && consumed.get(code)) {
            consumed.delete(code);
            return true;
        }
        if (learn(this, event)) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) consumed.put(code, true);
            return true;
        }
        if (!prefs(this).getBoolean("remote_enabled", true) || !learnable(code)) return false;
        for (String target : TARGETS) {
            if (code == key(this, target)) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    consumed.put(code, true);
                    if (event.getRepeatCount() == 0) launch(this, target);
                }
                return true;
            }
        }
        return false;
    }

    public static boolean launch(Context context, String target) {
        Intent intent = null;
        String pkg = null;
        if ("apps".equals(target)) {
            intent = new Intent(context, MainActivity.class).setAction("io.github.marouane4142.maros.SHOW_APPS").putExtra("open_apps", true);
        } else {
            if ("youtube".equals(target)) pkg = "com.google.android.youtube.tv";
            if ("netflix".equals(target)) pkg = "com.netflix.ninja";
            if ("prime".equals(target)) pkg = "com.amazon.amazonvideo.livingroom";
            if (pkg != null) {
                intent = context.getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
                if (intent == null) intent = context.getPackageManager().getLaunchIntentForPackage(pkg);
            }
        }
        if (intent == null) {
            Toast.makeText(context, "Application indisponible", Toast.LENGTH_SHORT).show();
            return false;
        }
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
            Log.i("AuroraRemote", "Launch " + target);
            if (pkg != null) ContentBridge.recordLaunch(context, pkg);
            return true;
        } catch (RuntimeException error) {
            Toast.makeText(context, "Impossible d’ouvrir cette application", Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) { /* No content collection. */ }
    @Override public void onInterrupt() { consumed.clear(); }
}
