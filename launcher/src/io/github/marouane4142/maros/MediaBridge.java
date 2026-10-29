package io.github.marouane4142.maros;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.provider.Settings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Real active media sessions supplied by compatible players. */
public final class MediaBridge {
    private final Context context;
    public MediaBridge(Context c){context=c.getApplicationContext();}
    public static final class NowPlaying {
        public String title="",subtitle="",pkg="";
        public Bitmap artwork;
        public MediaController controller;
        public boolean playing;
        public long position,duration;
    }
    public boolean isAuthorized(){
        String value=Settings.Secure.getString(context.getContentResolver(),"enabled_notification_listeners");
        if(value==null)return false;
        ComponentName ours=new ComponentName(context,MediaListener.class);
        for(String part:value.split(":"))if(ours.equals(ComponentName.unflattenFromString(part)))return true;
        return false;
    }
    public List<NowPlaying> sessions(){
        List<NowPlaying> result=new ArrayList<>();
        if(!isAuthorized())return result;
        MediaSessionManager manager=(MediaSessionManager)context.getSystemService(Context.MEDIA_SESSION_SERVICE);
        if(manager==null)return result;
        try{
            for(MediaController controller:manager.getActiveSessions(new ComponentName(context,MediaListener.class))){
                MediaMetadata metadata=controller.getMetadata();PlaybackState state=controller.getPlaybackState();
                if(metadata==null)continue;
                NowPlaying item=new NowPlaying();item.controller=controller;item.pkg=controller.getPackageName();
                item.title=first(metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE),metadata.getString(MediaMetadata.METADATA_KEY_TITLE));
                item.subtitle=first(metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE),metadata.getString(MediaMetadata.METADATA_KEY_ARTIST),metadata.getString(MediaMetadata.METADATA_KEY_ALBUM));
                item.artwork=metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
                if(item.artwork==null)item.artwork=metadata.getBitmap(MediaMetadata.METADATA_KEY_ART);
                if(item.artwork==null)item.artwork=metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
                item.duration=metadata.getLong(MediaMetadata.METADATA_KEY_DURATION);
                if(state!=null){item.position=state.getPosition();item.playing=state.getState()==PlaybackState.STATE_PLAYING||state.getState()==PlaybackState.STATE_BUFFERING;}
                if(!item.title.isEmpty())result.add(item);
            }
            Collections.sort(result,(a,b)->Boolean.compare(b.playing,a.playing));
        }catch(SecurityException ignored){ }catch(RuntimeException ignored){ }
        return result;
    }
    private String first(String...values){for(String value:values)if(value!=null&&!value.trim().isEmpty())return value;return "";}
    public void playPause(NowPlaying p){
        if(p==null||p.controller==null)return;
        try{
            PlaybackState s=p.controller.getPlaybackState();
            if(s!=null&&(s.getState()==PlaybackState.STATE_PLAYING||s.getState()==PlaybackState.STATE_BUFFERING))p.controller.getTransportControls().pause();
            else p.controller.getTransportControls().play();
        }catch(RuntimeException ignored){ }
    }
    public void next(NowPlaying p){if(p!=null&&p.controller!=null)try{p.controller.getTransportControls().skipToNext();}catch(RuntimeException ignored){}}
    public void previous(NowPlaying p){if(p!=null&&p.controller!=null)try{p.controller.getTransportControls().skipToPrevious();}catch(RuntimeException ignored){}}
    public boolean open(String pkg){
        Intent launch=context.getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
        if(launch==null)launch=context.getPackageManager().getLaunchIntentForPackage(pkg);
        if(launch==null)return false;
        try{context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));ContentBridge.recordLaunch(context,pkg);return true;}catch(RuntimeException ignored){return false;}
    }
}
