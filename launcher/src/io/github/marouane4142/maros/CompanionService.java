package io.github.marouane4142.maros;
import android.app.*;
import android.content.*;
import android.os.*;

/** Explicit, time-limited local sharing session. No boot receiver or wake lock. */
public final class CompanionService extends Service {
    private static volatile long deadline;
    private static final String CLOSE="io.github.marouane4142.maros.CLOSE_COMPANION";
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable expire=()->stopSelf();
    public static boolean active(){return deadline>SystemClock.elapsedRealtime();}
    public static long remainingMinutes(){return Math.max(0,(deadline-SystemClock.elapsedRealtime()+59999)/60000);}
    public static void begin(Context c){c.startForegroundService(new Intent(c,CompanionService.class));}
    public static void end(Context c){c.stopService(new Intent(c,CompanionService.class));}
    @Override public int onStartCommand(Intent intent,int flags,int id){
        if(intent!=null&&CLOSE.equals(intent.getAction())){stopSelf();return START_NOT_STICKY;}
        NotificationManager manager=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        manager.createNotificationChannel(new NotificationChannel("maros_companion","Téléphone MarOS",NotificationManager.IMPORTANCE_LOW));
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,HubActivity.class).putExtra("section","phone"),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        PendingIntent close=PendingIntent.getService(this,0,new Intent(this,CompanionService.class).setAction(CLOSE),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification n=new Notification.Builder(this,"maros_companion").setSmallIcon(R.drawable.ui_phone).setContentTitle("Téléphone connecté à MarOS").setContentText("Partage local actif pendant 1 heure · toucher pour afficher le QR code").setContentIntent(open).setOngoing(true).addAction(new Notification.Action.Builder(null,"Fermer la connexion",close).build()).build();
        startForeground(8765,n);
        CompanionServer server=CompanionServer.get(this);server.start();if(!server.isRunning()){stopSelf();return START_NOT_STICKY;}
        deadline=SystemClock.elapsedRealtime()+3600000;handler.removeCallbacks(expire);handler.postDelayed(expire,3600000);
        new LauncherPrefs(this).prefs().edit().putBoolean("companion_enabled",true).apply();return START_NOT_STICKY;
    }
    @Override public void onDestroy(){handler.removeCallbacksAndMessages(null);deadline=0;CompanionServer.get(this).stop();new LauncherPrefs(this).prefs().edit().putBoolean("companion_enabled",false).apply();stopForeground(true);super.onDestroy();}
    @Override public IBinder onBind(Intent i){return null;}
}
