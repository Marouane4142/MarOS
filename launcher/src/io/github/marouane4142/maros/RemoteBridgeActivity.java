package io.github.marouane4142.maros;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Optional calibration for other revisions of Xiaomi remotes. */
public final class RemoteBridgeActivity extends Activity {
    private final Handler handler = new Handler();
    private TextView state;
    private final TextView[] keys = new TextView[4];
    private int consumedKey = -1;
    private final Runnable refresh = new Runnable() {
        public void run() { update(); handler.postDelayed(this, 500); }
    };

    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().getDecorView().setSystemUiVisibility(5894);
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(48), dp(26), dp(48), dp(24));
        column.setBackground(MarOSUI.background());column.addView(MarOSUI.brand(this,false),new LinearLayout.LayoutParams(-1,dp(52)));
        TextView title = text("Votre télécommande", 26); title.setTypeface(null, Typeface.BOLD); column.addView(title);
        column.addView(text("Les quatre touches dédiées ouvrent vos applications depuis tout écran.\nLa navigation, le volume, la voix et le bouton Accueil gardent leur fonctionnement.", 11));
        state = text("", 11); state.setPadding(0, dp(10), 0, dp(10)); column.addView(state);
        column.addView(button("Ouvrir les réglages d’accessibilité", () -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))));
        for (int i=0; i<4; i++) {
            final String target = RemoteButtonService.TARGETS[i];
            final String label = RemoteButtonService.LABELS[i];
            keys[i] = button(label, () -> {
                RemoteButtonService.prefs(this).edit().putString("remote_learning_target", target)
                    .putLong("remote_learning_until", System.currentTimeMillis()+30000)
                    .putString("remote_learning_result", "").apply(); update();
            });
            column.addView(keys[i]);
        }
        column.addView(button("Rétablir les quatre touches Xiaomi", () -> {
            android.content.SharedPreferences.Editor edit = RemoteButtonService.prefs(this).edit();
            for(String target:RemoteButtonService.TARGETS) edit.remove("remote_key_"+target);
            edit.putString("remote_learning_target", "").putString("remote_learning_result", "Touches Xiaomi rétablies").apply(); update();
        }));
        column.addView(button("Retour à l’accueil", this::finish));
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.addView(column); setContentView(scroll);
    }
    private TextView text(String s, int size) {
        TextView t=new TextView(this);t.setText(s);t.setTextColor(Color.WHITE);t.setTextSize(size);t.setPadding(0,dp(5),0,dp(5));return t;
    }
    private TextView button(String s, Runnable action) {
        android.widget.Button button=MarOSUI.button(this,s,MarOSUI.iconFor(s),action);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(46));p.topMargin=dp(5);button.setLayoutParams(p);return button;
    }
    private void update() {
        android.content.SharedPreferences p=RemoteButtonService.prefs(this);
        String target=p.getString("remote_learning_target", "");long until=p.getLong("remote_learning_until",0);
        String message=RemoteButtonService.isEnabled(this)?"Contrôle global activé":"Activez « Boutons de la télécommande MarOS » pour agir aussi dans les autres applis.";
        if(!target.isEmpty() && until>System.currentTimeMillis()) message="Appuyez sur la touche « "+target+" » — "+((until-System.currentTimeMillis()+999)/1000)+" s. Retour annule.";
        else if(!target.isEmpty()) {p.edit().putString("remote_learning_target", "").apply();message="Aucune touche détectée. Vous pouvez réessayer.";}
        else if(!p.getString("remote_learning_result", "").isEmpty()) message += "\n"+p.getString("remote_learning_result", "");
        state.setText(message);
        for(int i=0;i<4;i++)keys[i].setText(RemoteButtonService.LABELS[i]+"  ·  Modifier la touche");
    }
    @Override protected void onResume(){super.onResume();handler.post(refresh);}
    @Override protected void onPause(){
        handler.removeCallbacksAndMessages(null);
        RemoteButtonService.prefs(this).edit().putString("remote_learning_target", "").putLong("remote_learning_until",0).apply();
        super.onPause();
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event){
        if(event.getKeyCode()==KeyEvent.KEYCODE_BACK && !RemoteButtonService.prefs(this).getString("remote_learning_target", "").isEmpty()){
            if(event.getAction()==KeyEvent.ACTION_DOWN){
                consumedKey=KeyEvent.KEYCODE_BACK;
                RemoteButtonService.prefs(this).edit().putString("remote_learning_target", "").putLong("remote_learning_until",0).apply();
                update();
            }
            return true;
        }
        if(event.getAction()==KeyEvent.ACTION_UP && event.getKeyCode()==consumedKey){consumedKey=-1;return true;}
        if(RemoteButtonService.learn(this,event)){consumedKey=event.getKeyCode();update();return true;}
        return super.dispatchKeyEvent(event);
    }
}
