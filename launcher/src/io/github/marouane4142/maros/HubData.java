package io.github.marouane4142.maros;

import android.app.Activity;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Optional integrations. All I/O runs on a bounded worker, all UI callbacks on main. */
public final class HubData {
    public interface Callback { void onChanged(); }
    public static final class Item {
        public final String title,subtitle,imageUrl,uri,pkg;
        public Item(String a,String b,String c,String d,String e) { title=a; subtitle=b; imageUrl=c; uri=d; pkg=e; }
    }
    private static final ExecutorService WORK=Executors.newFixedThreadPool(2);
    private final Context context;
    private final LauncherPrefs config;
    private final Secrets secrets;
    private static volatile boolean refreshing;
    public HubData(Context c) { context=c.getApplicationContext(); config=new LauncherPrefs(context); secrets=new Secrets(context); }
    public void refresh(Callback callback) {
        if(refreshing) { new Handler(Looper.getMainLooper()).post(callback::onChanged); return; }
        refreshing=true;
        WORK.execute(()-> { try { refreshWeather(false); if(!config.getString("jellyfin_url","").isEmpty()&&System.currentTimeMillis()-config.prefs().getLong("library_updated",0)>300000) refreshLibrary(); } finally { refreshing=false; new Handler(Looper.getMainLooper()).post(callback::onChanged); } });
    }
    public interface Job { String run() throws Exception; }
    public interface Result { void done(String result,boolean success); }
    public static void work(Job job,Result result) { WORK.execute(()-> { String message; boolean success=true; try {message=job.run();} catch(Exception e) {success=false; message=error(e);} final String m=message; final boolean ok=success; new Handler(Looper.getMainLooper()).post(()->result.done(m,ok)); }); }
    private static String error(Exception e) { if(e instanceof SocketTimeoutException) return "Délai dépassé. Vérifie le réseau et l’adresse du serveur."; String m=e.getMessage(); return m==null?"Connexion impossible.":m; }
    public String weatherSummary() {
        if(config.getString("weather_city", "").trim().isEmpty())return "Choisir ma ville · météo";
        String s=config.getString("weather_summary", "");if(s.isEmpty())return "Météo · à actualiser";
        long age=System.currentTimeMillis()-config.prefs().getLong("weather_updated",0);return s+(age>3600000?" · ancienne mesure":"");
    }
    public String weatherDetails(){return config.getString("weather_details", "Choisissez une ville pour consulter les prévisions. Aucune localisation automatique.")+"\n"+config.getString("weather_error", "");}
    public String refreshWeather(boolean force){
        String query=config.getString("weather_city", "").trim();if(query.isEmpty()||!config.getBool("show_weather",true))return weatherSummary();
        long now=System.currentTimeMillis();if(!force&&now-config.prefs().getLong("weather_last_attempt",0)<1800000)return weatherSummary();config.prefs().edit().putLong("weather_last_attempt",now).apply();
        try{
            if(!query.equals(config.getString("weather_resolved_query", ""))){
                JSONObject geo=new JSONObject(request("https://geocoding-api.open-meteo.com/v1/search?name="+Uri.encode(query)+"&count=1&language=fr&format=json", "GET",null,null));JSONArray matches=geo.optJSONArray("results");if(matches==null||matches.length()==0)throw new IOException("Ville introuvable. Essayez un nom de ville ou un code postal plus précis.");JSONObject first=matches.getJSONObject(0);double lat=first.getDouble("latitude"),lon=first.getDouble("longitude");if(!Double.isFinite(lat)||!Double.isFinite(lon)||Math.abs(lat)>90||Math.abs(lon)>180)throw new IOException("Coordonnées invalides.");String city=first.getString("name"),area=first.optString("admin1"),country=first.optString("country");String location=city+(area.isEmpty()?"":" · "+area)+(country.isEmpty()?"":" · "+country);
                config.prefs().edit().putString("weather_latitude",Double.toString(lat)).putString("weather_longitude",Double.toString(lon)).putString("weather_resolved_query",query).putString("weather_location_name",location).putString("weather_location_city",city).apply();
            }
            String latitude=config.getString("weather_latitude",""),longitude=config.getString("weather_longitude","");double lat=Double.parseDouble(latitude),lon=Double.parseDouble(longitude);if(!Double.isFinite(lat)||!Double.isFinite(lon)||Math.abs(lat)>90||Math.abs(lon)>180)throw new IOException("Coordonnées invalides.");
            JSONObject root=new JSONObject(request("https://api.open-meteo.com/v1/forecast?latitude="+Uri.encode(latitude)+"&longitude="+Uri.encode(longitude)+"&current=temperature_2m,apparent_temperature,weather_code,wind_speed_10m&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max&forecast_days=3&timezone=auto","GET",null,null));JSONObject current=root.getJSONObject("current"),daily=root.getJSONObject("daily");String summary=config.getString("weather_location_city",query)+"  "+Math.round(current.getDouble("temperature_2m"))+"°  ·  "+weatherCode(current.optInt("weather_code"));
            StringBuilder details=new StringBuilder(config.getString("weather_location_name",query)+"\nRessenti "+Math.round(current.optDouble("apparent_temperature"))+"°  ·  Vent "+Math.round(current.optDouble("wind_speed_10m"))+" km/h\n");JSONArray times=daily.getJSONArray("time");for(int i=0;i<times.length();i++)details.append(times.optString(i)).append("   ").append(Math.round(daily.getJSONArray("temperature_2m_min").optDouble(i))).append("° / ").append(Math.round(daily.getJSONArray("temperature_2m_max").optDouble(i))).append("°   Pluie ").append(daily.getJSONArray("precipitation_probability_max").optInt(i)).append(" %\n");details.append("Mesure : ").append(current.optString("time").replace('T',' ')).append(" · ").append(root.optString("timezone")).append("\nOpen-Meteo · données météo · GeoNames · localisation");config.prefs().edit().putString("weather_summary",summary).putString("weather_details",details.toString()).putString("weather_error","").putLong("weather_updated",now).apply();
        }catch(Exception e){config.prefs().edit().putString("weather_error","Mise à jour impossible : "+error(e)).apply();}return weatherSummary();
    }
    private static String weatherCode(int code) { if(code==0) return "Ciel dégagé"; if(code<=3) return "Nuageux"; if(code<=48) return "Brouillard"; if(code<=57) return "Bruine"; if(code<=67) return "Pluie"; if(code<=77) return "Neige"; if(code<=82) return "Averses"; if(code<=86) return "Averses de neige"; return "Orages"; }
    public JSONArray events() {return array("agenda_events");}
    public String nextEventSummary() { JSONObject next=null; long now=System.currentTimeMillis(); JSONArray items=events(); for(int i=0;i<items.length();i++) {JSONObject o=items.optJSONObject(i); if(o!=null&&o.optLong("time")>=now&&(next==null||o.optLong("time")<next.optLong("time"))) next=o;} return next==null?"":next.optString("title")+" · "+new SimpleDateFormat("EEE d MMM HH:mm",Locale.FRENCH).format(new Date(next.optLong("time"))); }
    public JSONArray shortcuts() {return array("hub_shortcuts");}
    public JSONArray array(String key) {try {return new JSONArray(config.getString(key,"[]"));}catch(Exception e){return new JSONArray();}}
    public void saveArray(String key,JSONArray data) {config.putString(key,data.toString());}
    public void runShortcut(Activity activity,JSONObject item) {open(activity,item.optString("uri"),item.optString("package"));}
    public static boolean safeUri(String value) {
        if(value==null||value.length()>4096||value.matches("(?s).*[\\x00-\\x20\\x7f].*"))return false;
        try{URI uri=new URI(value);String scheme=uri.getScheme();if(scheme==null||uri.getRawUserInfo()!=null)return false;scheme=scheme.toLowerCase(Locale.ROOT);
            if("spotify".equals(scheme))return !uri.getSchemeSpecificPart().isEmpty();
            if(!Arrays.asList("http","https","rtsp").contains(scheme))return false;
            // Reject malformed escapes and invalid ports before constructing any Android intent.
            return uri.getHost()!=null&&!uri.getHost().isEmpty()&&uri.getPort()>=-1&&uri.getPort()<=65535;
        }catch(Exception e){return false;}
    }
    public static void open(Activity activity,String uri,String pkg) {
        try {
            Intent intent=null;
            if(!uri.isEmpty()) {if(!safeUri(uri))throw new IllegalArgumentException("Lien incompatible : http, https, rtsp ou spotify attendu.");intent=new Intent(Intent.ACTION_VIEW,Uri.parse(uri));if(LauncherPrefs.validPackage(pkg))intent.setPackage(pkg);}
            else if(LauncherPrefs.validPackage(pkg)) {intent=activity.getPackageManager().getLeanbackLaunchIntentForPackage(pkg);if(intent==null)intent=activity.getPackageManager().getLaunchIntentForPackage(pkg);}
            if(intent==null)throw new IllegalArgumentException("Application absente ou aucun raccourci configuré.");
            try {activity.startActivity(intent);}catch(android.content.ActivityNotFoundException e){if(!pkg.isEmpty()&&!uri.isEmpty()){intent.setPackage(null);activity.startActivity(intent);}else throw e;}
        }catch(Exception e){Toast.makeText(activity,error(e),Toast.LENGTH_LONG).show();}
    }
    public static void search(Activity activity,String query,String pkg) {
        query=query.trim();if(query.isEmpty())return;
        if("com.google.android.youtube.tv".equals(pkg)) {open(activity,"https://www.youtube.com/results?search_query="+Uri.encode(query),pkg);return;}
        if("com.spotify.tv.android".equals(pkg)) {open(activity,"https://open.spotify.com/search/"+Uri.encode(query),pkg);return;}
        Intent intent=new Intent(Intent.ACTION_SEARCH).putExtra(SearchManager.QUERY,query).setPackage(pkg);
        try {if(intent.resolveActivity(activity.getPackageManager())!=null){activity.startActivity(intent);return;}}catch(Exception ignored){}
        try {((android.content.ClipboardManager)activity.getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(android.content.ClipData.newPlainText("Recherche",query));}catch(Exception ignored){}
        Toast.makeText(activity,"Recherche directe non prise en charge. Texte copié ; ouvre la recherche de l’application.",Toast.LENGTH_LONG).show();open(activity,"",pkg);
    }
    public List<Item> libraryItems() {
        ArrayList<Item> list=new ArrayList<>();JSONArray a=array("library_items");for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o!=null)list.add(new Item(o.optString("title"),o.optString("subtitle"),o.optString("imageUrl"),o.optString("uri"),o.optString("package")));}return list;
    }
    public String statusSummary() {return config.getString("library_status","Bibliothèque personnelle · à connecter");}
    public static String normalizeServer(String input) throws Exception {
        URI uri=new URI(input.trim());String s=uri.getScheme();if(!"http".equalsIgnoreCase(s)&&!"https".equalsIgnoreCase(s))throw new IllegalArgumentException("Adresse http:// ou https:// attendue.");
        if(uri.getHost()==null||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null)throw new IllegalArgumentException("Adresse du serveur invalide.");String value=uri.toASCIIString();while(value.endsWith("/"))value=value.substring(0,value.length()-1);return value;
    }
    public String refreshLibrary() {
        try {
            String base=normalizeServer(config.getString("jellyfin_url","")), token=secrets.get("jellyfin_token"), user=config.getString("jellyfin_user","");
            if(token.isEmpty())throw new IllegalArgumentException("Ajoute un jeton Jellyfin dans Connexion.");
            String auth="MediaBrowser Client=\"TV Home\", Device=\"Android TV\", DeviceId=\"tvhome-v2\", Version=\"2.0\", Token=\""+token.replace("\"","")+"\"";
            if(user.isEmpty()){JSONObject me=new JSONObject(request(base+"/Users/Me","GET",auth,null));user=me.getString("Id");config.prefs().edit().putString("jellyfin_user",user).apply();}
            String userQuery="userId="+Uri.encode(user), legacy="/Users/"+Uri.encode(user);
            String resume;
            try{resume=request(base+"/Items/Resume?"+userQuery+"&Limit=12&MediaTypes=Video&Fields=PrimaryImageAspectRatio","GET",auth,null);}catch(Exception e){resume=request(base+legacy+"/Items/Resume?Limit=12&MediaTypes=Video","GET",auth,null);}
            JSONArray raw=new JSONObject(resume).optJSONArray("Items"), result=new JSONArray();appendLibrary(result,raw,base,"À reprendre");
            String latest;try{latest=request(base+"/Items/Latest?"+userQuery+"&Limit=12&IncludeItemTypes=Movie,Episode","GET",auth,null);}catch(Exception e){latest=request(base+legacy+"/Items/Latest?Limit=12&IncludeItemTypes=Movie,Episode","GET",auth,null);}appendLibrary(result,new JSONArray(latest),base,"Ajout récent");
            String status=result.length()+" contenus Jellyfin · actualisés à "+new SimpleDateFormat("HH:mm",Locale.FRANCE).format(new Date());
            config.prefs().edit().putString("library_items",result.toString()).putString("library_status",status).putLong("library_updated",System.currentTimeMillis()).apply();return status;
        }catch(Exception e){String status="Jellyfin : "+error(e);config.prefs().edit().putString("library_status",status).putLong("library_updated",System.currentTimeMillis()).apply();return status;}
    }
    private void appendLibrary(JSONArray result,JSONArray raw,String base,String category)throws Exception {if(raw==null)return;HashSet<String> seen=new HashSet<>();for(int i=0;i<result.length();i++)seen.add(result.getJSONObject(i).optString("id"));for(int i=0;i<raw.length()&&result.length()<24;i++){JSONObject o=raw.optJSONObject(i);if(o==null||!seen.add(o.optString("Id")))continue;String id=o.optString("Id"),name=o.optString("Name"),series=o.optString("SeriesName");JSONObject userdata=o.optJSONObject("UserData");String subtitle=category+(series.isEmpty()?"":" · "+series);if(userdata!=null&&userdata.optDouble("PlayedPercentage",0)>0)subtitle+=" · "+Math.round(userdata.optDouble("PlayedPercentage"))+" %";result.put(new JSONObject().put("id",id).put("title",name).put("subtitle",subtitle).put("imageUrl",base+"/Items/"+Uri.encode(id)+"/Images/Primary?maxWidth=420&quality=85").put("uri",base+"/web/index.html#!/details?id="+Uri.encode(id)).put("package",""));}}
    public String testHomeAssistant()throws Exception {String base=normalizeServer(config.getString("ha_url",""));JSONObject status=new JSONObject(request(base+"/api/","GET","Bearer "+secrets.get("ha_token"),null));return status.optString("message","Home Assistant joignable");}
    public String homeStates()throws Exception {String base=normalizeServer(config.getString("ha_url",""));JSONArray values=new JSONArray(request(base+"/api/states","GET","Bearer "+secrets.get("ha_token"),null));StringBuilder b=new StringBuilder();for(int i=0;i<values.length()&&i<80;i++){JSONObject o=values.getJSONObject(i);b.append(o.optString("entity_id")).append("  ·  ").append(o.optString("state")).append('\n');}return b.length()==0?"Aucune entité.":b.toString();}
    public String homeAction(String domain,String service,String entity)throws Exception {
        if(!domain.matches("[a-z_]{2,40}")||!service.matches("[a-z_]{2,60}")||!entity.matches("[a-z_]+\\.[a-z0-9_]+"))throw new IllegalArgumentException("Domaine, action ou identifiant d’entité invalide.");
        String base=normalizeServer(config.getString("ha_url",""));request(base+"/api/services/"+domain+"/"+service,"POST","Bearer "+secrets.get("ha_token"),new JSONObject().put("entity_id",entity).toString());return "Commande envoyée à Home Assistant.";
    }
    public String wakePc()throws Exception {
        String mac=config.getString("pc_mac","").replace(":","").replace("-","");if(!mac.matches("[A-Fa-f0-9]{12}"))throw new IllegalArgumentException("Renseigne les six groupes de l’adresse MAC du PC.");
        String destination=config.getString("pc_broadcast","").trim();if(destination.isEmpty())throw new IllegalArgumentException("Renseignez l’adresse de diffusion de votre réseau.");InetAddress address=InetAddress.getByName(destination);if(!address.isSiteLocalAddress())throw new IllegalArgumentException("Adresse de diffusion locale attendue.");
        byte[] packet=new byte[102];Arrays.fill(packet,0,6,(byte)255);for(int i=0;i<96;i++)packet[6+i]=(byte)Integer.parseInt(mac.substring((i%6)*2,(i%6)*2+2),16);
        try(DatagramSocket socket=new DatagramSocket()){socket.setBroadcast(true);for(int i=0;i<3;i++)socket.send(new DatagramPacket(packet,packet.length,address,9));}return "Signal de réveil envoyé. Le PC doit prendre en charge Wake-on-LAN.";
    }
    /** Suggestions from the user's configured model, never a claim of streaming availability. */
    public String recommend(String criteria)throws Exception {
        String base=normalizeServer(config.getString("assistant_url","")),model=config.getString("assistant_model","").trim();
        if(model.isEmpty()||model.length()>120||!model.matches("[A-Za-z0-9._:/-]+"))throw new IllegalArgumentException("Renseigne le nom exact d’un modèle disponible sur ton serveur.");
        if(criteria==null||criteria.length()>1500)throw new IllegalArgumentException("Demande trop longue.");
        JSONArray messages=new JSONArray().put(new JSONObject().put("role","system").put("content","Réponds en français. Propose exactement trois films ou séries existants correspondant à la demande. Pour chaque proposition donne le titre, l’année si connue, et une phrase expliquant ton choix. Respecte la durée demandée lorsque tu la connais et signale les incertitudes. N’invente jamais un titre, un prix, une disponibilité ou un lien de streaming. Ne présume pas de disponibilité sur les abonnements ; invite à vérifier dans les applications. N’ajoute aucun lien et reste sous 220 mots." )).put(new JSONObject().put("role","user").put("content",criteria));
        JSONObject body=new JSONObject().put("model",model).put("messages",messages).put("stream",false).put("options",new JSONObject().put("num_predict",450));
        String token=secrets.get("assistant_token"),auth=token.isEmpty()?null:"Bearer "+token;
        JSONObject response=new JSONObject(request(base+"/api/chat","POST",auth,body.toString(),45000));JSONObject message=response.optJSONObject("message");String text=message==null?"":message.optString("content").trim();
        if(text.isEmpty())throw new java.io.IOException("Le modèle n’a renvoyé aucune proposition.");if(text.length()>10000)text=text.substring(0,10000);
        config.prefs().edit().putString("recommendation_result",text).putString("recommendation_criteria",criteria).putLong("recommendation_updated",System.currentTimeMillis()).apply();
        return text+"\n\nPropositions générées par ton assistant. Disponibilité et informations à vérifier dans tes applications.";
    }
    static String request(String address,String method,String auth,String body)throws Exception {
        return request(address,method,auth,body,8000);
    }
    private static String request(String address,String method,String auth,String body,int readTimeout)throws Exception {
        HttpURLConnection connection=(HttpURLConnection)new URL(address).openConnection();connection.setConnectTimeout(7000);connection.setReadTimeout(readTimeout);connection.setInstanceFollowRedirects(false);connection.setRequestMethod(method);connection.setRequestProperty("Accept","application/json");connection.setRequestProperty("User-Agent","MarOS/"+BuildConfig.VERSION_NAME);if(auth!=null&&!auth.isEmpty())connection.setRequestProperty("Authorization",auth);
        try {if(body!=null){connection.setDoOutput(true);connection.setRequestProperty("Content-Type","application/json");try(OutputStream out=connection.getOutputStream()){out.write(body.getBytes(StandardCharsets.UTF_8));}}int status=connection.getResponseCode();if(status<200||status>=300)throw new IOException(status==401||status==403?"Accès refusé : vérifie le jeton.":"Le serveur répond HTTP "+status+".");try(InputStream in=connection.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buffer=new byte[8192];int n,total=0;while((n=in.read(buffer))!=-1){total+=n;if(total>2097152)throw new IOException("Réponse du serveur trop volumineuse.");out.write(buffer,0,n);}return new String(out.toByteArray(),StandardCharsets.UTF_8);}}
        finally{connection.disconnect();}
    }
}
