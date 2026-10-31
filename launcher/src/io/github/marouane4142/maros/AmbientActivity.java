package io.github.marouane4142.maros;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Fullscreen local album, no wake lock and no background work when it is hidden. */
public final class AmbientActivity extends Activity {
    private Scene scene;
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        getWindow().getDecorView().setSystemUiVisibility(5894);
        scene=new Scene(this);setContentView(scene);
    }
    @Override protected void onResume(){super.onResume();scene.start();}
    @Override protected void onPause(){scene.stop();super.onPause();}
    @Override protected void onDestroy(){scene.release();super.onDestroy();}
    @Override public boolean dispatchKeyEvent(KeyEvent event){
        int code=event.getKeyCode();
        if(code==KeyEvent.KEYCODE_HOME||code==KeyEvent.KEYCODE_POWER||code==KeyEvent.KEYCODE_VOLUME_UP||code==KeyEvent.KEYCODE_VOLUME_DOWN||code==KeyEvent.KEYCODE_VOLUME_MUTE)return super.dispatchKeyEvent(event);
        if(event.getAction()==KeyEvent.ACTION_DOWN)finish();
        return true; // The wake-up press never opens the previously selected application.
    }
    @Override public boolean onTouchEvent(MotionEvent event){if(event.getAction()==MotionEvent.ACTION_DOWN)finish();return true;}

    /** Reused by Android's real DreamService. Images are decoded once, off the UI thread. */
    public static final class Scene extends FrameLayout {
        private final PhotoStore photos;
        private final SharedPreferences prefs;
        private final Handler handler=new Handler(Looper.getMainLooper());
        private final ExecutorService loader=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"TvHome-AmbientPhoto");t.setDaemon(true);return t;});
        private final ImageView image;
        private final TextView clock,date,caption;
        private final FrameLayout timeBox;
        private List<PhotoStore.Photo> album=new ArrayList<>();
        private boolean running,released;
        private int generation,index;
        private Bitmap displayed;
        private final SharedPreferences.OnSharedPreferenceChangeListener privacy=(store,key)->{
            if("guest_mode".equals(key))handler.post(this::refreshPrivacy);
        };
        private void refreshPrivacy(){
            if(!running||released)return;
            generation++;album=photos.list();index=0;
            image.setImageDrawable(null);displayed=null;caption.setText("");show(index);
        }
        private final Runnable rotate=new Runnable(){public void run(){if(!running)return;next();handler.postDelayed(this,rotationMillis());}};
        private final Runnable tick=new Runnable(){public void run(){if(!running)return;updateTime();handler.postDelayed(this,60000-System.currentTimeMillis()%60000);}};

        public Scene(Context context){
            super(context);photos=new PhotoStore(context);prefs=context.getSharedPreferences("tvhome_v2",Context.MODE_PRIVATE);setBackgroundColor(Color.BLACK);
            image=new ImageView(context);image.setScaleType(ImageView.ScaleType.CENTER_CROP);addView(image,new LayoutParams(-1,-1));
            View shade=new View(context);shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0x25000000,0x08000000,0x68000000}));addView(shade,new LayoutParams(-1,-1));
            timeBox=new FrameLayout(context);
            LinearTime time=new LinearTime(context);
            clock=time.clock;date=time.date;timeBox.addView(time,new LayoutParams(-2,-2));
            LayoutParams tp=new LayoutParams(-2,-2,Gravity.TOP|Gravity.LEFT);tp.leftMargin=dp(62);tp.topMargin=dp(42);addView(timeBox,tp);
            caption=new TextView(context);caption.setTextColor(0xDDFFFFFF);caption.setTextSize(12);caption.setShadowLayer(dp(3),0,dp(1),Color.BLACK);caption.setMaxLines(3);
            LayoutParams cp=new LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.LEFT);cp.leftMargin=dp(38);cp.bottomMargin=dp(24);cp.rightMargin=dp(38);addView(caption,cp);
        }
        private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
        private long rotationMillis(){return Math.max(1,Math.min(120,prefs.getInt("wallpaper_minutes",10)))*60000L;}
        public void start(){
            if(running||released)return;running=true;album=photos.list();
            prefs.registerOnSharedPreferenceChangeListener(privacy);
            if(RoomData.guest(getContext())){image.setImageDrawable(null);displayed=null;caption.setText("");}
            timeBox.setVisibility(prefs.getBoolean("ambient_clock",true)?VISIBLE:GONE);
            String last=prefs.getString("ambient_photo_id","");index=0;
            for(int i=0;i<album.size();i++)if(album.get(i).id.equals(last)){index=i;break;}
            handler.post(tick);show(index);handler.postDelayed(rotate,rotationMillis());
        }
        public void stop(){
            running=false;generation++;prefs.unregisterOnSharedPreferenceChangeListener(privacy);handler.removeCallbacksAndMessages(null);
        }
        public void release(){
            stop();released=true;loader.shutdownNow();image.setImageDrawable(null);displayed=null;
        }
        public void next(){if(album.isEmpty())return;index=(index+1)%album.size();show(index);}
        private void show(int position){
            if(album.isEmpty()){caption.setText("Ajoutez des photos dans Réglages → Album.");return;}
            PhotoStore.Photo photo=album.get(position);int token=++generation;
            int width=Math.max(1,getResources().getDisplayMetrics().widthPixels),height=Math.max(1,getResources().getDisplayMetrics().heightPixels);
            try{loader.execute(()->{
                Bitmap bitmap=photos.load(photo,Math.min(1920,width),Math.min(1080,height));
                handler.post(()->{
                    if(!running||released||token!=generation){if(bitmap!=null&&!bitmap.isRecycled())bitmap.recycle();return;}
                    if(bitmap==null){
                        if(displayed==null)caption.setText("Photo indisponible. Choisissez un autre paysage dans votre album.");
                        return;
                    }
                    image.setImageBitmap(bitmap);displayed=bitmap;
                    // View removal releases the previous image naturally; do not recycle GPU-owned bitmaps.
                    caption.setText(photo.title+(photo.credit.isEmpty()?"":"\n"+photo.credit));
                    prefs.edit().putString("ambient_photo_id",photo.id).apply();
                });
            });}catch(java.util.concurrent.RejectedExecutionException ignored){ }
        }
        private void updateTime(){
            Date now=new Date();clock.setText(new SimpleDateFormat("HH:mm",Locale.FRANCE).format(now));date.setText(new SimpleDateFormat("EEEE d MMMM",Locale.FRANCE).format(now));
            // Move once per minute to distribute the static clock over the screen without animation.
            post(()->{
                if(!running)return;
                long minute=System.currentTimeMillis()/60000;
                int boxW=Math.max(dp(230),timeBox.getWidth()),boxH=Math.max(dp(130),timeBox.getHeight());
                int areaW=Math.max(0,getWidth()-boxW-dp(100)),areaH=Math.max(0,getHeight()-boxH-dp(130));
                LayoutParams p=(LayoutParams)timeBox.getLayoutParams();p.leftMargin=dp(46)+(int)((minute*79)%Math.max(1,areaW));p.topMargin=dp(35)+(int)((minute*43)%Math.max(1,areaH));timeBox.setLayoutParams(p);
            });
        }
        private static final class LinearTime extends android.widget.LinearLayout{
            final TextView clock,date;
            LinearTime(Context c){super(c);setOrientation(VERTICAL);
                clock=new TextView(c);clock.setTextColor(Color.WHITE);clock.setTextSize(58);clock.setTypeface(Typeface.create("sans-serif-light",Typeface.NORMAL));clock.setShadowLayer(5,0,2,0xAA000000);addView(clock,new android.widget.LinearLayout.LayoutParams(-2,-2));
                date=new TextView(c);date.setTextColor(0xE6FFFFFF);date.setTextSize(18);date.setShadowLayer(4,0,1,0xAA000000);addView(date,new android.widget.LinearLayout.LayoutParams(-2,-2));
            }
        }
    }
}
