package io.github.marouane4142.maros;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.media.tv.TvContract;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Reads only the TV provider's permitted rows. App history is separate from video progress. */
public final class ContentBridge {
    private final Context context;
    public String status="";
    public ContentBridge(Context c){context=c.getApplicationContext();}
    public static final class Item extends RecentPolicy.Record { }
    public int scanned,excluded;private final java.util.Map<String,Integer> sourceCounts=new java.util.LinkedHashMap<>();
    public List<Item> load(){return load("all",false);}
    public List<Item> load(String service,boolean unfinished){
        if(RoomData.guest(context)){status="Lectures masquées en mode invités";return new ArrayList<>();}
        List<Item> records=new ArrayList<>();long now=System.currentTimeMillis();scanned=0;excluded=0;sourceCounts.clear();
        readWatchNext(records,now);List<Item> observations=PlaybackHistory.items(context);
        for(Item item:observations){
            for(Item source:records)if(item.packageName.equals(source.packageName)&&!item.id.isEmpty()&&item.id.equals(source.id)){item.intentUri=source.intentUri;if(item.imageUri.isEmpty())item.imageUri=source.imageUri;item.seriesId=source.seriesId;break;}
            records.add(item);
        }
        java.util.Set<String> hidden=context.getSharedPreferences("maros_playback_history",Context.MODE_PRIVATE).getStringSet("hidden",new java.util.HashSet<>());
        java.util.Set<String> hiddenApps=new LauncherPrefs(context).hidden();List<Item> visible=new ArrayList<>();
        for(Item item:records){if(hiddenApps.contains(item.packageName)||hidden.contains(RecentPolicy.seriesKey(item))||unfinished&&item.completed||!service.equals("all")&&!service.equals(item.packageName))continue;
            try{android.content.pm.ApplicationInfo ai=context.getPackageManager().getApplicationInfo(item.packageName,0);if(!ai.enabled)continue;}catch(Exception e){continue;}
            item.subtitle=when(item.lastSeen)+(item.episode.isEmpty()?"":"  ·  "+item.episode)+(item.completed?"  ·  Terminé":"");visible.add(item);
        }
        List<Item> out=RecentPolicy.chronological(visible,now,12,12);
        enrichArtwork(out);
        for(Item item:out)sourceCounts.put(item.packageName,sourceCounts.containsKey(item.packageName)?sourceCounts.get(item.packageName)+1:1);
        status=out.isEmpty()?"Aucune lecture récente datée partagée pour ce service.":"Dernière lecture réelle · du plus récent au plus ancien";return out;
    }
    /** Artwork-only join: suggestions never become watched records or change viewing dates. */
    private void enrichArtwork(List<Item> items){
        java.util.Map<Item,Set<String>> matches=new java.util.HashMap<>();
        for(Item item:items)if(item.imageUri.isEmpty())matches.put(item,new HashSet<>());
        if(matches.isEmpty())return;
        for(Uri table:new Uri[]{TvContract.WatchNextPrograms.CONTENT_URI,TvContract.PreviewPrograms.CONTENT_URI}){
            try(Cursor c=context.getContentResolver().query(table,null,null,null,null)){
                if(c==null)continue;int read=0;
                while(c.moveToNext()&&read++<2000){
                    String image=first(value(c,"thumbnail_uri"),value(c,"poster_art_uri"));if(image.isEmpty())continue;
                    String pkg=value(c,"package_name"),title=RecentPolicy.normal(value(c,"title")),episode=RecentPolicy.normal(value(c,"episode_title"));
                    String id=first(value(c,"content_id"),value(c,"internal_provider_id"));
                    for(java.util.Map.Entry<Item,Set<String>> entry:matches.entrySet()){
                        Item item=entry.getKey();if(!pkg.equals(item.packageName))continue;
                        String name=RecentPolicy.normal(item.title);
                        boolean same=!id.isEmpty()&&id.equals(item.id)||!episode.isEmpty()&&name.equals(episode)
                            ||!title.isEmpty()&&name.equals(title)&&(item.episode.isEmpty()||RecentPolicy.normal(item.episode).equals(episode));
                        if(same)entry.getValue().add(image);
                    }
                }
            }catch(SecurityException ignored){}catch(RuntimeException ignored){}
        }
        // Ambiguous episode matches remain explicitly without artwork.
        for(java.util.Map.Entry<Item,Set<String>> entry:matches.entrySet())if(entry.getValue().size()==1)entry.getKey().imageUri=entry.getValue().iterator().next();
    }
    private void readWatchNext(List<Item> out,long now){
        try(Cursor cursor=context.getContentResolver().query(TvContract.WatchNextPrograms.CONTENT_URI,null,null,null,null)){
            if(cursor==null)return;while(cursor.moveToNext()&&scanned<2000){scanned++;Item item=new Item();item.watchNext=true;item.nextType=(int)number(cursor,"watch_next_type",-1);item.lastSeen=RecentPolicy.timestamp(number(cursor,"last_engagement_time_utc_millis",0));item.position=number(cursor,"last_playback_position_millis",0);item.duration=number(cursor,"duration_millis",0);
                if(number(cursor,"browsable",1)==0||!RecentPolicy.continuation(item.nextType,item.position,item.duration,item.lastSeen,now)){excluded++;continue;}
                item.title=value(cursor,"title");item.episode=first(value(cursor,"episode_title"),episode(cursor));item.imageUri=first(value(cursor,"thumbnail_uri"),value(cursor,"poster_art_uri"));item.intentUri=value(cursor,"intent_uri");item.packageName=value(cursor,"package_name");item.id=first(value(cursor,"content_id"),value(cursor,"internal_provider_id"),value(cursor,"_id"));item.seriesId=value(cursor,"series_id");
                if(item.title.isEmpty()||item.intentUri.isEmpty()||!PlaybackHistory.supported(item.packageName)){excluded++;continue;}out.add(item);
            }
        }catch(SecurityException ignored){status="Accès aux lectures partagées indisponible.";}catch(RuntimeException ignored){status="Ce fournisseur ne partage pas d’historique exploitable.";}
    }
    private static String episode(Cursor c){String season=value(c,"season_display_number"),episode=value(c,"episode_display_number");return episode.isEmpty()?"":(season.isEmpty()?"Épisode ":"S"+season+" · E")+episode;}
    public static String when(long date){java.text.SimpleDateFormat f=new java.text.SimpleDateFormat("d MMM · HH:mm",java.util.Locale.FRENCH);long ago=System.currentTimeMillis()-date;if(ago>=0&&ago<3600000)return ago<60000?"À l’instant":"Il y a "+(ago/60000)+" min";return f.format(new java.util.Date(date));}
    public void hide(Item item){android.content.SharedPreferences p=context.getSharedPreferences("maros_playback_history",Context.MODE_PRIVATE);java.util.Set<String> hidden=new java.util.HashSet<>(p.getStringSet("hidden",new java.util.HashSet<>()));hidden.add(RecentPolicy.seriesKey(item));p.edit().putStringSet("hidden",hidden).apply();}
    public void resetHidden(){context.getSharedPreferences("maros_playback_history",Context.MODE_PRIVATE).edit().remove("hidden").apply();}
    public JSONObject diagnostics(){JSONObject object=new JSONObject();JSONArray list=new JSONArray();try{for(Item i:load()){list.put(new JSONObject().put("title",i.title).put("package",i.packageName).put("when",i.lastSeen).put("position",i.position).put("duration",i.duration).put("source",i.source).put("watch_next_type",i.nextType).put("completed",i.completed).put("has_image",!i.imageUri.isEmpty()));}object.put("items",list).put("scanned",scanned).put("excluded",excluded).put("order","lastSeen DESC").put("retention_days",30);}catch(JSONException ignored){}return object;}
    private static String value(Cursor c,String column){int i=c.getColumnIndex(column);return i<0||c.isNull(i)?"":c.getString(i);}
    private static long number(Cursor c,String column,long fallback){int i=c.getColumnIndex(column);return i<0||c.isNull(i)?fallback:c.getLong(i);}
    private static String first(String...values){for(String v:values)if(v!=null&&!v.trim().isEmpty())return v;return "";}
    public boolean open(Item item){
        if(item==null)return false;
        try{
            if(item.intentUri.isEmpty()&&item.observed)return new MediaBridge(context).open(item.packageName);
            Intent i=Intent.parseUri(item.intentUri,Intent.URI_INTENT_SCHEME);
            if(i.getComponent()!=null&&!item.packageName.equals(i.getComponent().getPackageName()))return false;
            i.setSelector(null);
            i.setClipData(null);
            int grants=Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    |Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION|Intent.FLAG_GRANT_PREFIX_URI_PERMISSION;
            i.setFlags(i.getFlags()&~grants);
            i.setPackage(item.packageName);i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(i);recordLaunch(context,item.packageName);return true;
        }catch(Exception ignored){return false;}
    }
    /** Opening an app does not create or retain a viewing observation. */
    public static void recordLaunch(Context context,String pkg){ /* App-launch history removed. */ }
    public static List<String> recentPackages(Context context){return new ArrayList<>();}
}
