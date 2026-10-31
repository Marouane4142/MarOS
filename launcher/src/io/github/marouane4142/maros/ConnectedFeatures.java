package io.github.marouane4142.maros;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.net.*;
import java.io.*;
import java.time.ZoneId;
import io.github.marouane4142.maros.tv.CalendarEntry;
import io.github.marouane4142.maros.tv.CalendarFeed;

/** User-triggered connectors. No polling, discovery, credentials in links or automatic installs. */
public final class ConnectedFeatures {
    private ConnectedFeatures() { }
    public static void saveSource(Context context, String key, String input) throws Exception {
        if (!key.equals("remote_album_url") && !key.equals("calendar_url")) throw new IllegalArgumentException("Source inconnue.");
        String url = input.trim();
        if (!url.isEmpty()) validate(url);
        new LauncherPrefs(context).putString(key, url);
    }
    private static URL validate(String source) throws Exception {
        URI uri = new URI(source);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null || source.length() > 4096)
            throw new IllegalArgumentException("Une adresse HTTPS sans identifiant intégré est attendue.");
        return uri.toURL();
    }
    public static String importAlbum(Context context) throws Exception {
        String address = new LauncherPrefs(context).getString("remote_album_url", "");
        validate(address);
        JSONArray items = new JSONArray(HubData.request(address, "GET", null, null));
        if (items.length() > 30) throw new IOException("30 photos maximum par liste.");
        PhotoStore photos = new PhotoStore(context);
        int count = 0;
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.getJSONObject(i);
            HttpURLConnection connection = (HttpURLConnection)validate(item.getString("url")).openConnection();
            connection.setConnectTimeout(7000); connection.setReadTimeout(8000); connection.setInstanceFollowRedirects(false);
            try {
                int status = connection.getResponseCode();
                if (status != 200) throw new IOException("Image HTTP " + status + ". " + count + " photo(s) déjà importée(s).");
                try (InputStream in = connection.getInputStream()) { photos.importImage(in, item.optString("title", "Album distant")); count++; }
            } finally { connection.disconnect(); }
        }
        return count + " photo(s) importée(s) localement. Les doublons ne sont pas synchronisés automatiquement.";
    }
    public static String importImmich(Context context) throws Exception {
        LauncherPrefs prefs = new LauncherPrefs(context);
        String base = HubData.normalizeServer(prefs.getString("immich_url", ""));
        validate(base);
        if(!base.endsWith("/api"))base += "/api";
        String album=prefs.getString("immich_album", ""), token=new Secrets(context).get("immich_token");
        if(!album.matches("[A-Fa-f0-9]{8}(?:-[A-Fa-f0-9]{4}){3}-[A-Fa-f0-9]{12}")||token.isEmpty())throw new IOException("Configurez le serveur, l’album et la clé API Immich.");
        android.content.SharedPreferences imported=context.getSharedPreferences("immich_imports",Context.MODE_PRIVATE);
        JSONObject mapping=new JSONObject(imported.getString("assets","{}"));
        java.util.Set<String> local=new java.util.HashSet<>();PhotoStore photos=new PhotoStore(context);
        for(PhotoStore.Photo photo:photos.all())local.add(photo.id);
        int count=0, skipped=0;
        // Metadata search works with the documented v2 fields retained by v3.
        for(int page=1;page<=5&&count<30;page++){
            JSONObject query=new JSONObject().put("albumIds",new JSONArray().put(album)).put("type","IMAGE").put("size",30).put("page",page).put("withExif",false);
            HttpURLConnection request=immichConnection(base+"/search/metadata",token);
            JSONObject response;
            try{
                request.setRequestMethod("POST");request.setDoOutput(true);request.setRequestProperty("Content-Type","application/json");
                try(OutputStream out=request.getOutputStream()){out.write(query.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));}
                if(request.getResponseCode()!=200)throw new IOException("Recherche Immich HTTP "+request.getResponseCode()+". Vérifiez la clé et ses droits de lecture.");
                try(InputStream in=request.getInputStream();ByteArrayOutputStream bytes=new ByteArrayOutputStream()){
                    byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){if(bytes.size()+n>2097152)throw new IOException("Réponse Immich trop volumineuse.");bytes.write(buffer,0,n);}response=new JSONObject(bytes.toString("UTF-8"));
                }
            }finally{request.disconnect();}
            JSONObject assets=response.getJSONObject("assets");JSONArray items=assets.getJSONArray("items");
            for(int i=0;i<items.length()&&count<30;i++){
                JSONObject item=items.getJSONObject(i);String id=item.getString("id");
                if(!id.matches("[A-Fa-f0-9-]{36}")||!"IMAGE".equals(item.optString("type")))continue;
                String key=base+"|"+id;if(local.contains(mapping.optString(key))){skipped++;continue;}
                HttpURLConnection image=immichConnection(base+"/assets/"+id+"/thumbnail?size=preview",token);
                try{
                    if(image.getResponseCode()!=200)throw new IOException("Aperçu Immich HTTP "+image.getResponseCode()+". "+count+" photo(s) déjà importée(s).");
                    try(InputStream in=image.getInputStream()){
                        PhotoStore.Photo photo=photos.importImage(in,item.optString("originalFileName","Album Immich"));
                        mapping.put(key,photo.id);local.add(photo.id);count++;imported.edit().putString("assets",mapping.toString()).apply();
                    }
                }finally{image.disconnect();}
            }
            if(items.length()==0||assets.isNull("nextPage")&&assets.isNull("nextCursor"))break;
        }
        return count+" nouvelle(s) photo(s) Immich importée(s), "+skipped+" déjà présente(s). Import manuel, 30 nouvelles photos maximum.";
    }
    private static HttpURLConnection immichConnection(String address,String token)throws Exception{
        HttpURLConnection connection=(HttpURLConnection)validate(address).openConnection();
        connection.setConnectTimeout(7000);connection.setReadTimeout(8000);connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("x-api-key",token);connection.setRequestProperty("Accept","application/json,image/*");
        return connection;
    }
    public static String importCalendar(Context context) throws Exception {
        LauncherPrefs prefs = new LauncherPrefs(context);
        String address = prefs.getString("calendar_url", ""); validate(address);
        String text = HubData.request(address, "GET", null, null);
        JSONArray events = new HubData(context).events(), result = new JSONArray();
        // Preserve manually entered events, replace only the previous imported feed.
        for (int i = 0; i < events.length(); i++) { JSONObject event = events.optJSONObject(i); if (event != null && !"ics".equals(event.optString("source"))) result.put(event); }
        int count = 0;
        for (CalendarEntry event : CalendarFeed.INSTANCE.parse(text, ZoneId.systemDefault())) {
            if (result.length() >= 100) break;
            result.put(new JSONObject().put("id", "ics:" + event.getId()).put("title", event.getTitle()).put("time", event.getTime()).put("source", "ics")); count++;
        }
        new HubData(context).saveArray("agenda_events", result);
        return count + " événement(s) importé(s). Événements récurrents exclus ; actualisation manuelle.";
    }
    public static String wakeAndWait(Context context) throws Exception {
        LauncherPrefs prefs = new LauncherPrefs(context);
        String host = prefs.getString("pc_host", "").trim();
        if (host.isEmpty()) throw new IOException("Renseignez d’abord l’adresse IP du PC dans Scènes.");
        InetAddress ip = InetAddress.getByName(host);
        if (!ip.isSiteLocalAddress()) throw new IOException("Une adresse de PC sur le réseau local est attendue.");
        new HubData(context).wakePc();
        long deadline = android.os.SystemClock.elapsedRealtime() + 60000;
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(ip, 47989), 1200);
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> new MediaBridge(context).open("com.limelight"));
                return "Le service GameStream/Sunshine répond. Ouverture de Moonlight.";
            } catch (IOException ignored) { Thread.sleep(2000); }
        }
        return "Signal de réveil envoyé, mais le service du PC ne répond pas après une minute. Vérifiez Sunshine et le réseau.";
    }
    public static String checkUpdate(Context context) throws Exception {
        JSONArray releases = new JSONArray(HubData.request("https://api.github.com/repos/Marouane4142/MarOS/releases?per_page=10", "GET", null, null));
        if(releases.length()==0)return "Version installée : "+BuildConfig.VERSION_NAME+"\nAucune publication publique disponible pour le moment.";
        JSONObject release = releases.getJSONObject(0);
        String tag = release.optString("tag_name")+(release.optBoolean("prerelease")?" · bêta":" · stable");
        new LauncherPrefs(context).putString("available_release", tag);
        return "Version installée : " + BuildConfig.VERSION_NAME + "\nDernière publication publique : " + tag + "\nConsultez github.com/Marouane4142/MarOS/releases pour les APK et leurs signatures.";
    }
}
