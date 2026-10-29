package io.github.marouane4142.maros;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.content.ComponentName;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.media.MediaMetadata;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import java.util.*;

/** The system binding authorizes MediaSessionManager only; no notifications are read or stored. */
public final class MediaListener extends NotificationListenerService {
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Map<MediaController,MediaController.Callback> callbacks=new HashMap<>();
    private final Map<String,Long> starts=new HashMap<>();private MediaSessionManager manager;
    private final MediaSessionManager.OnActiveSessionsChangedListener sessions=this::bind;
    private final Runnable poll=new Runnable(){public void run(){for(MediaController c:new ArrayList<>(callbacks.keySet()))sample(c);handler.postDelayed(this,30000);}};
    @Override public void onListenerConnected(){manager=(MediaSessionManager)getSystemService(MEDIA_SESSION_SERVICE);if(manager==null)return;try{manager.addOnActiveSessionsChangedListener(sessions,new ComponentName(this,MediaListener.class),handler);bind(manager.getActiveSessions(new ComponentName(this,MediaListener.class)));handler.removeCallbacks(poll);handler.post(poll);}catch(SecurityException ignored){}}
    private void bind(List<MediaController> list){for(Map.Entry<MediaController,MediaController.Callback> e:callbacks.entrySet())e.getKey().unregisterCallback(e.getValue());callbacks.clear();if(list==null)return;for(MediaController c:list){if(!PlaybackHistory.supported(c.getPackageName()))continue;MediaController.Callback cb=new MediaController.Callback(){public void onMetadataChanged(MediaMetadata m){starts.remove(c.getPackageName());sample(c);}public void onPlaybackStateChanged(PlaybackState s){sample(c);}};callbacks.put(c,cb);c.registerCallback(cb,handler);sample(c);}}
    private void sample(MediaController c){try{PlaybackState s=c.getPlaybackState();String pkg=c.getPackageName();if(s!=null&&s.getState()==PlaybackState.STATE_PLAYING&&!starts.containsKey(pkg))starts.put(pkg,SystemClock.elapsedRealtime());PlaybackHistory.record(this,c,starts.containsKey(pkg)?starts.get(pkg):0);if(s==null||s.getState()!=PlaybackState.STATE_PLAYING)starts.remove(pkg);}catch(RuntimeException ignored){}}
    @Override public void onListenerDisconnected(){cleanup();}
    @Override public void onDestroy(){cleanup();super.onDestroy();}
    private void cleanup(){handler.removeCallbacksAndMessages(null);if(manager!=null)try{manager.removeOnActiveSessionsChangedListener(sessions);}catch(Exception ignored){}for(Map.Entry<MediaController,MediaController.Callback> e:callbacks.entrySet())try{e.getKey().unregisterCallback(e.getValue());}catch(Exception ignored){}callbacks.clear();starts.clear();}
    @Override public void onNotificationPosted(StatusBarNotification ignored) { }
    @Override public void onNotificationRemoved(StatusBarNotification ignored) { }
}
