package io.github.marouane4142.maros;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.PlaybackState;
import android.os.SystemClock;
import org.json.*;
import java.util.*;

/** Local actual playback observations. Launching an app never creates a watched item. */
public final class PlaybackHistory {
    private static final Object LOCK=new Object();
    private static final Set<String> VIDEO=new HashSet<>(Arrays.asList("com.crunchyroll.crunchyroid","com.netflix.ninja","com.amazon.amazonvideo.livingroom","ar.tvplayer.tv","com.orange.owtv.tv","com.disney.disneyplus","com.canal.android.canal","org.videolan.vlc","com.xiaomi.mitv.mediaexplorer","com.google.android.youtube.tv"));
    public static boolean supported(String pkg){return VIDEO.contains(pkg);}
    private static SharedPreferences prefs(Context c){return c.getSharedPreferences("maros_playback_history",Context.MODE_PRIVATE);}
    public static void record(Context context,MediaController controller,long playingSince){
        if(controller==null||!supported(controller.getPackageName()))return;
        try{MediaMetadata m=controller.getMetadata();PlaybackState state=controller.getPlaybackState();if(m==null||state==null||state.getState()!=PlaybackState.STATE_PLAYING&&state.getState()!=PlaybackState.STATE_PAUSED)return;
            String title=first(m.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE),m.getString(MediaMetadata.METADATA_KEY_TITLE));
            String clean=RecentPolicy.normal(title);if(clean.length()<2||Arrays.asList("netflix","primevideo","crunchyroll","tivimate","youtube","lecture","enlecture").contains(clean)||title.toLowerCase(Locale.ROOT).matches(".*(trailer|bande.annonce).*"))return;
            try{if(clean.equals(RecentPolicy.normal(context.getPackageManager().getApplicationLabel(context.getPackageManager().getApplicationInfo(controller.getPackageName(),0)).toString())))return;}catch(Exception ignored){}
            boolean playing=state.getState()==PlaybackState.STATE_PLAYING;long elapsed=SystemClock.elapsedRealtime(),position=Math.max(0,state.getPosition());if(playing&&state.getLastPositionUpdateTime()>0)position+=(long)((elapsed-state.getLastPositionUpdateTime())*state.getPlaybackSpeed());
            if(position<60000&&(playingSince<=0||elapsed-playingSince<30000))return;
            long when=System.currentTimeMillis();if(!playing){long update=state.getLastPositionUpdateTime();if(update<=0)return;when-=Math.max(0,elapsed-update);}
            if(!RecentPolicy.recent(when,System.currentTimeMillis()))return;
            String id=first(m.getString(MediaMetadata.METADATA_KEY_MEDIA_ID),title);String pkg=controller.getPackageName();
            JSONObject item=new JSONObject().put("package",pkg).put("title",title).put("id",id).put("when",when).put("position",position).put("duration",Math.max(0,m.getLong(MediaMetadata.METADATA_KEY_DURATION))).put("image",first(m.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI),m.getString(MediaMetadata.METADATA_KEY_ART_URI),m.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI))).put("uri",first(m.getString(MediaMetadata.METADATA_KEY_MEDIA_URI),"")).put("subtitle",first(m.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE),""));
            synchronized(LOCK){JSONArray prior=array(prefs(context).getString("records","[]")),out=new JSONArray();out.put(item);for(int i=0;i<prior.length()&&out.length()<64;i++){JSONObject old=prior.optJSONObject(i);if(old==null||old.optString("package").equals(pkg)&&old.optString("id").equals(id))continue;if(RecentPolicy.recent(old.optLong("when"),System.currentTimeMillis()))out.put(old);}prefs(context).edit().putString("records",out.toString()).apply();}
        }catch(RuntimeException|JSONException ignored){}
    }
    public static List<ContentBridge.Item> items(Context c){List<ContentBridge.Item> out=new ArrayList<>();JSONArray a=array(prefs(c).getString("records","[]"));for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o==null)continue;ContentBridge.Item r=new ContentBridge.Item();r.packageName=o.optString("package");r.title=o.optString("title");r.id=o.optString("id");r.lastSeen=o.optLong("when");r.position=o.optLong("position");r.duration=o.optLong("duration");r.imageUri=o.optString("image");r.intentUri="";r.episode=o.optString("subtitle");r.observed=true;r.source="session";r.watchNext=false;r.completed=RecentPolicy.completed(r.position,r.duration);out.add(r);}return out;}
    public static void clear(Context c){prefs(c).edit().remove("records").apply();}
    private static JSONArray array(String raw){try{return new JSONArray(raw);}catch(JSONException e){return new JSONArray();}}
    private static String first(String...s){for(String x:s)if(x!=null&&!x.trim().isEmpty())return x;return "";}
}
