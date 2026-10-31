package io.github.marouane4142.maros;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** User configuration, deliberately independent from credentials and connection tokens. */
public final class LauncherPrefs {
    public static final String ACTION_CHANGED = "io.github.marouane4142.maros.CONFIG_CHANGED";
    private final Context context;
    private final SharedPreferences prefs;
    public LauncherPrefs(Context c) { context = c.getApplicationContext(); prefs = context.getSharedPreferences("tvhome_v2", Context.MODE_PRIVATE); }
    public SharedPreferences prefs() { return prefs; }
    public String theme() { String s=getString("theme", "nature"); return Arrays.asList("nature","cinema","minimal","aurora","dune").contains(s)?s:"nature"; }
    public int getInt(String key,int fallback) { try { return prefs.getInt(key,fallback); } catch(ClassCastException e) { return fallback; } }
    public boolean getBool(String key,boolean fallback) { try { return prefs.getBoolean(key,fallback); } catch(ClassCastException e) { return fallback; } }
    public String getString(String key,String fallback) { try { return prefs.getString(key,fallback); } catch(ClassCastException e) { return fallback; } }
    public void putString(String key,String value) { prefs.edit().putString(key,value).apply(); changed(); }
    public void putInt(String key,int value) { prefs.edit().putInt(key,value).apply(); changed(); }
    public void putBool(String key,boolean value) { prefs.edit().putBoolean(key,value).apply(); changed(); }
    public void changed() { prefs.edit().putInt("ui_revision",getInt("ui_revision",0)+1).apply(); context.sendBroadcast(new Intent(ACTION_CHANGED).setPackage(context.getPackageName())); }
    public List<String> dock() {
        String json=getString("dock", ""); ArrayList<String> result=new ArrayList<>();
        if(json.isEmpty()) return new ArrayList<>(Arrays.asList(context.getResources().getStringArray(R.array.dock_apps)));
        try { JSONArray a=new JSONArray(json); for(int i=0;i<a.length();i++) { String p=a.optString(i); if(validPackage(p)&&!result.contains(p)) result.add(p); } } catch(Exception e) { return new ArrayList<>(Arrays.asList(context.getResources().getStringArray(R.array.dock_apps))); }
        return result;
    }
    public void setDock(List<String> packages) { LinkedHashSet<String> clean=new LinkedHashSet<>(); for(String p:packages) if(validPackage(p)) clean.add(p); putString("dock",new JSONArray(clean).toString()); }
    public Set<String> hidden() { try { return new HashSet<>(prefs.getStringSet("hidden",new HashSet<>(Arrays.asList(context.getResources().getStringArray(R.array.hidden_apps))))); } catch(ClassCastException e) { return new HashSet<>(); } }
    public void setHidden(Set<String> packages) { HashSet<String> clean=new HashSet<>(); for(String p:packages) if(validPackage(p)) clean.add(p); prefs.edit().putStringSet("hidden",clean).apply(); changed(); }
    public String category(String pkg) {
        String own=getString("category_"+pkg,""); if(Arrays.asList("regarder","musique","jeux","outils").contains(own)) return own;
        String p=pkg.toLowerCase(java.util.Locale.ROOT);
        if(p.contains("spotify")||p.contains("music")||p.contains("deezer")||p.contains("tidal")) return "musique";
        if(p.contains("limelight")||p.contains("parsec")||p.contains("steam")||p.contains("game")||p.contains("retroarch")||p.contains("nvidia")) return "jeux";
        if(p.contains("netflix")||p.contains("crunchyroll")||p.contains("youtube")||p.contains("amazonvideo")||p.contains("orange")||p.contains("tvplayer")||p.contains("vlc")||p.contains("kodi")||p.contains("jellyfin")||p.contains("plex")||p.contains("mediaexplorer")||p.contains("disney")) return "regarder";
        return "outils";
    }
    public void setCategory(String pkg,String key) { if(validPackage(pkg)&&Arrays.asList("regarder","musique","jeux","outils").contains(key)) putString("category_"+pkg,key); }
    public static boolean validPackage(String p) { return p!=null&&p.length()<180&&p.matches("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+"); }

    /** An explicit allowlist ensures secrets from integrations never enter a portable backup. */
    private static final Set<String> PORTABLE = new HashSet<>(Arrays.asList("theme","accent","home_title","ui_scale","tile_size","contrast","wallpaper_minutes","reduced_animations","ambient_minutes","show_clock","text_scale","photo_mode","show_photo_credit","show_weather","weather_city","show_storage","show_timer","show_calendar","clock_24h","show_greeting","remember_navigation","ambient_clock","show_collections","show_continue","show_music","show_info","show_games","dock","hidden","home_rows","hidden_rows","show_recent_apps"));
    private static final Set<String> NUMBERS = new HashSet<>(Arrays.asList("contrast","wallpaper_minutes","ambient_minutes","text_scale","ui_scale"));
    private static final Set<String> STRINGS = new HashSet<>(Arrays.asList("theme","accent","home_title","tile_size","photo_mode","weather_city","home_rows"));
    public String exportConfiguration() throws Exception {
        JSONObject data=new JSONObject();
        // A portable backup describes the effective configuration, including
        // defaults that Android has never needed to write to SharedPreferences.
        data.put("accent","ice").put("home_title","");
        data.put("home_rows","[]");
        data.put("theme","nature").put("tile_size","normal").put("photo_mode","all").put("weather_city","");
        data.put("contrast",45).put("wallpaper_minutes",10).put("ambient_minutes",5).put("text_scale",100).put("ui_scale",85);
        for(String key:PORTABLE) {
            if(!NUMBERS.contains(key)&&!STRINGS.contains(key)&&!key.equals("dock")&&!key.equals("hidden")&&!key.equals("hidden_rows"))
                data.put(key,!key.equals("reduced_animations"));
        }
        for(Map.Entry<String,?> item:prefs.getAll().entrySet()) {
            String k=item.getKey(); Object value=item.getValue();
            if(k.startsWith("category_")&&validPackage(k.substring(9))) {
                if(value instanceof String&&Arrays.asList("regarder","musique","jeux","outils").contains(value)) data.put(k,value);
                continue;
            }
            if(!PORTABLE.contains(k)||k.equals("dock")||k.equals("hidden")||k.equals("hidden_rows")) continue;
            Object fallback=data.opt(k);
            if(fallback instanceof Boolean&&value instanceof Boolean) data.put(k,value);
            else if(fallback instanceof Integer&&value instanceof Integer) {
                int n=(Integer)value;
                if(k.equals("ui_scale")) n=Math.max(75,Math.min(110,n));
                else if(k.equals("contrast")) n=Math.max(0,Math.min(80,n));
                else if(k.equals("text_scale")) n=Math.max(85,Math.min(140,n));
                else if(k.equals("wallpaper_minutes")) n=Math.max(1,Math.min(1440,n));
                else if(k.equals("ambient_minutes")) n=Math.max(0,Math.min(120,n));
                data.put(k,n);
            } else if(fallback instanceof String&&value instanceof String&&((String)value).length()<500) {
                String s=(String)value;
                if(k.equals("theme")&&!Arrays.asList("nature","cinema","minimal","aurora","dune").contains(s)) continue;
                if(k.equals("accent")&&!Arrays.asList("ice","sage","rose","amber").contains(s)) continue;
                if(k.equals("home_title")&&s.length()>70) continue;
                if(k.equals("tile_size")&&!Arrays.asList("small","normal","large").contains(s)) continue;
                if(k.equals("photo_mode")&&!Arrays.asList("all","favorites").contains(s)) continue;
                data.put(k,s);
            }
        }
        data.put("theme",theme());
        List<String> effectiveDock=dock();
        data.put("dock",new JSONArray(effectiveDock.subList(0,Math.min(250,effectiveDock.size()))));
        List<String> effectiveHidden=new ArrayList<>();
        for(String pkg:hidden()) if(validPackage(pkg)) effectiveHidden.add(pkg);
        Collections.sort(effectiveHidden);
        data.put("hidden",new JSONArray(effectiveHidden.subList(0,Math.min(250,effectiveHidden.size()))));
        data.put("hidden_rows",new JSONArray(prefs.getStringSet("hidden_rows",new HashSet<>())));
        return new JSONObject().put("format","tvhome-settings").put("version",2).put("settings",data).toString(2);
    }
    public int importConfiguration(String json) throws Exception {
        JSONObject root=new JSONObject(json); if(!"tvhome-settings".equals(root.optString("format"))||root.optInt("version")!=2) throw new IllegalArgumentException("Ce fichier n’est pas une sauvegarde TV Home V2.");
        JSONObject values=root.getJSONObject("settings"); SharedPreferences.Editor e=prefs.edit(); int count=0;
        java.util.Iterator<String> keys=values.keys(); while(keys.hasNext()) {
            String key=keys.next(); if(!PORTABLE.contains(key)&&!(key.startsWith("category_")&&validPackage(key.substring(9)))) continue;
            Object value=values.get(key);
            if(key.equals("dock")) { JSONArray a=value instanceof JSONArray?(JSONArray)value:new JSONArray(String.valueOf(value)); if(a.length()>250) throw new IllegalArgumentException("Trop de favoris."); ArrayList<String> clean=new ArrayList<>(); for(int i=0;i<a.length();i++) if(validPackage(a.optString(i))) clean.add(a.optString(i)); e.putString(key,new JSONArray(clean).toString()); }
            else if(key.equals("hidden_rows")) { JSONArray a=(JSONArray)value; Set<String> set=new HashSet<>(); for(int i=0;i<a.length();i++) if(Arrays.asList("favorites","continue","recent","library","shortcuts").contains(a.optString(i)))set.add(a.optString(i));e.putStringSet(key,set); }
            else if(key.equals("hidden")) { JSONArray a=(JSONArray)value; Set<String> set=new HashSet<>(); for(int i=0;i<a.length()&&i<250;i++) if(validPackage(a.optString(i))) set.add(a.optString(i)); e.putStringSet(key,set); }
            else if(value instanceof Boolean&&!NUMBERS.contains(key)&&!STRINGS.contains(key)&&!key.startsWith("category_")) e.putBoolean(key,(Boolean)value);
            else if(value instanceof Number&&NUMBERS.contains(key)) { long raw=((Number)value).longValue(); int n=(int)Math.max(0,Math.min(1440,raw)); if(key.equals("ui_scale")) n=Math.max(75,Math.min(110,n)); else if(key.equals("contrast")) n=Math.max(0,Math.min(80,n)); else if(key.equals("text_scale")) n=Math.max(85,Math.min(140,n)); else if(key.equals("wallpaper_minutes")) n=Math.max(1,Math.min(1440,n)); else if(key.equals("ambient_minutes")) n=Math.max(0,Math.min(120,n)); e.putInt(key,n); }
            else if(value instanceof String && ((String)value).length()<500&&(STRINGS.contains(key)||key.startsWith("category_"))) {
                String s=(String)value;
                if(key.equals("theme")&&!Arrays.asList("nature","cinema","minimal","aurora","dune").contains(s)) continue;
                if(key.equals("accent")&&!Arrays.asList("ice","sage","rose","amber").contains(s)) continue;
                if(key.equals("home_title")&&s.length()>70) continue;
                if(key.equals("tile_size")&&!Arrays.asList("small","normal","large").contains(s)) continue;
                if(key.equals("photo_mode")&&!Arrays.asList("all","favorites").contains(s)) continue;
                if(key.startsWith("category_")&&!Arrays.asList("regarder","musique","jeux","outils").contains(s)) continue;
                e.putString(key,s);
            }
            else continue;
            count++;
        }
        if(count==0) throw new IllegalArgumentException("Aucun réglage compatible dans le fichier.");
        e.apply(); changed(); return count;
    }
    /** Structural validation for staged archives, before any images or settings are written. */
    public static void validatePortable(String json) throws Exception {
        JSONObject root=new JSONObject(json);
        if(!"tvhome-settings".equals(root.optString("format"))||root.optInt("version")!=2)throw new IllegalArgumentException("Format de réglages invalide");
        JSONObject settings=root.getJSONObject("settings");if(settings.length()>500)throw new IllegalArgumentException("Trop de réglages");
        java.util.Iterator<String> names=settings.keys();while(names.hasNext()){
            String key=names.next();Object value=settings.get(key);
            if(key.startsWith("category_")){if(!validPackage(key.substring(9))||!(value instanceof String)||!Arrays.asList("regarder","musique","jeux","outils").contains(value))throw new IllegalArgumentException("Collection invalide");continue;}
            if(!PORTABLE.contains(key))continue;
            if(key.equals("dock")||key.equals("hidden")||key.equals("hidden_rows")){
                if(!(value instanceof JSONArray))throw new IllegalArgumentException("Liste attendue pour "+key);JSONArray a=(JSONArray)value;if(a.length()>250)throw new IllegalArgumentException("Liste trop longue");
                for(int i=0;i<a.length();i++){if(!(a.get(i) instanceof String))throw new IllegalArgumentException("Nom attendu");if(key.equals("hidden_rows")){if(!Arrays.asList("favorites","continue","recent","library","shortcuts").contains(a.getString(i)))throw new IllegalArgumentException("Section inconnue");}else if(!validPackage(a.getString(i)))throw new IllegalArgumentException("Application invalide");}
            }else if(NUMBERS.contains(key)){if(!(value instanceof Number))throw new IllegalArgumentException("Nombre attendu pour "+key);}
            else if(STRINGS.contains(key)){if(!(value instanceof String)||((String)value).length()>=500)throw new IllegalArgumentException("Texte invalide pour "+key);}
            else if(!(value instanceof Boolean))throw new IllegalArgumentException("Valeur oui/non attendue pour "+key);
        }
    }
}
