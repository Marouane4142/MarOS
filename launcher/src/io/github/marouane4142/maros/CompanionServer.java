package io.github.marouane4142.maros;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;
import org.json.JSONObject;
import org.json.JSONArray;

/** Time-limited LAN companion, kept alive by CompanionService. Owner-paired semantic controls and bounded backup transfer; no shell or credentials endpoint. */
public final class CompanionServer {
    private static CompanionServer instance;
    private final Context context;
    private final LauncherPrefs prefs;
    private volatile ServerSocket server;
    private volatile long expiresAt;
    private volatile Socket activeClient;
    private volatile String url="",code="",token="",lastError="";
    private final HashMap<String,long[]> attempts=new HashMap<>();
    private final SecureRandom random=new SecureRandom();
    private CompanionServer(Context c){context=c.getApplicationContext();prefs=new LauncherPrefs(context);}
    public static synchronized CompanionServer get(Context c){if(instance==null)instance=new CompanionServer(c);return instance;}
    public synchronized void start(){
        if(isRunning())return;
        try{
            InetAddress local=localAddress();if(local==null)throw new IOException("La TV doit être connectée au réseau local.");
            server=new ServerSocket();server.setReuseAddress(true);server.bind(new InetSocketAddress("0.0.0.0",8765),8);server.setSoTimeout(1000);
            byte[] secret=new byte[32];random.nextBytes(secret);token=hex(secret);code=String.format(Locale.ROOT,"%06d",random.nextInt(1000000));url="http://"+local.getHostAddress()+":8765";expiresAt=android.os.SystemClock.elapsedRealtime()+3600000;lastError="";attempts.clear();
            final ServerSocket listener=server;
            Thread thread=new Thread(()->{while(server==listener&&!listener.isClosed())try{Socket socket=listener.accept();if(expired()){socket.close();break;}synchronized(this){if(server!=listener){socket.close();continue;}activeClient=socket;}socket.setSoTimeout(5000);if(!socket.getInetAddress().isSiteLocalAddress()&&!socket.getInetAddress().isLoopbackAddress()){socket.close();if(activeClient==socket)activeClient=null;continue;}handle(socket);if(activeClient==socket)activeClient=null;}catch(SocketTimeoutException ignored){if(expired())break;}catch(Exception e){if(!listener.isClosed())lastError="Connexion interrompue.";}},"TVHomeCompanion");thread.setDaemon(true);thread.start();
        }catch(Exception e){lastError=e.getMessage()==null?"Le compagnon ne peut pas démarrer.":e.getMessage();stop();}
    }
    public synchronized void stop(){ServerSocket current=server;Socket client=activeClient;server=null;activeClient=null;url="";code="";token="";if(current!=null)try{current.close();}catch(Exception ignored){}if(client!=null)try{client.close();}catch(Exception ignored){} }
    private boolean expired(){if(expiresAt>0&&android.os.SystemClock.elapsedRealtime()>=expiresAt){stop();context.stopService(new Intent(context,CompanionService.class));return true;}return false;}
    public boolean isRunning(){return server!=null&&!server.isClosed();}
    public String getUrl(){return url;}
    public String getPairingCode(){return code;}
    public String getLastError(){return lastError;}
    private InetAddress localAddress()throws Exception{
        InetAddress fallback=null;Enumeration<NetworkInterface> all=NetworkInterface.getNetworkInterfaces();while(all.hasMoreElements()){NetworkInterface network=all.nextElement();if(!network.isUp()||network.isLoopback()||network.isVirtual())continue;Enumeration<InetAddress> addresses=network.getInetAddresses();while(addresses.hasMoreElements()){InetAddress a=addresses.nextElement();if(a instanceof Inet4Address&&a.isSiteLocalAddress()&&!a.isLoopbackAddress()){String name=network.getName().toLowerCase(Locale.ROOT);if(name.startsWith("wlan")||name.startsWith("eth"))return a;if(fallback==null)fallback=a;}}}return fallback;
    }
    private static String hex(byte[] value){StringBuilder b=new StringBuilder();for(byte a:value)b.append(String.format(Locale.ROOT,"%02x",a&255));return b.toString();}
    private static boolean equal(String a,String b){return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8),b.getBytes(StandardCharsets.UTF_8));}
    private synchronized boolean rateLimited(String ip){long now=System.currentTimeMillis();long[] hit=attempts.get(ip);if(hit==null||now-hit[0]>60000){if(attempts.size()>256)attempts.clear();attempts.put(ip,new long[]{now,1});return false;}return ++hit[1]>6;}
    private void handle(Socket socket){Socket client=socket;java.util.Timer deadline=new java.util.Timer(true);deadline.schedule(new java.util.TimerTask(){public void run(){try{client.close();}catch(IOException ignored){}}},20000);try{
        InputStream input=new BufferedInputStream(client.getInputStream());String request=line(input,4096);if(request==null)return;String[] parts=request.split(" ");if(parts.length!=3){respond(client,400,"text/plain","Requête invalide");return;}String method=parts[0],path=parts[1];
        HashMap<String,String> headers=new HashMap<>();int total=0;String entry;while((entry=line(input,4096))!=null&&!entry.isEmpty()){total+=entry.length();if(total>16384)throw new IOException("En-tête trop grand");int colon=entry.indexOf(':');if(colon>0)headers.put(entry.substring(0,colon).toLowerCase(Locale.ROOT),entry.substring(colon+1).trim());}
        String host=headers.get("host");String expectedHost=url.replace("http://","");if(host==null||!host.equals(expectedHost)){respond(client,403,"text/plain","Hôte refusé");return;}
        if("GET".equals(method)&&"/".equals(path)){respond(client,200,"text/html; charset=utf-8",page());return;}
        if(!"POST".equals(method)){respond(client,404,"text/plain","Introuvable");return;}
        String origin=headers.get("origin");
        if(headers.containsKey("transfer-encoding")){respond(client,400,"text/plain","Taille requise");return;}
        int length;try{length=Integer.parseInt(headers.get("content-length"));}catch(Exception e){respond(client,411,"text/plain","Taille requise");return;}
        int maximum=("/restore".equals(path)||"/restore/validate".equals(path))?PersonalBackup.MAX_ARCHIVE:("/photo".equals(path)||"/banner".equals(path))?10*1024*1024:("/import".equals(path)?262144:16384);if(length<0||length>maximum){respond(client,413,"text/plain","Fichier trop volumineux");return;}
        if(origin!=null&&!origin.equals(url)){discard(input,length);respond(client,403,"text/plain","Origine refusée");return;}
        String contentType=headers.get("content-type");if(contentType==null)contentType="";
        if("/pair".equals(path)){
            if(rateLimited(client.getInetAddress().getHostAddress())){discard(input,length);respond(client,429,"text/plain","Attends une minute avant de réessayer.");return;}
            if(!contentType.startsWith("application/json")){discard(input,length);respond(client,415,"text/plain","JSON attendu");return;}
            JSONObject body=new JSONObject(new String(read(input,length),StandardCharsets.UTF_8));if(!equal(body.optString("code").replace(" ","").replace("-","").toUpperCase(Locale.ROOT),code)||code.isEmpty()){respond(client,403,"text/plain","Code incorrect ou expiré");return;}respond(client,200,"application/json",new JSONObject().put("token",token).toString());return;
        }
        String supplied=headers.get("x-tvhome-token");if(supplied==null||token.isEmpty()||!equal(supplied,token)){discard(input,length);respond(client,401,"text/plain","Appairage requis");return;}
        if("/backup".equals(path)||"/backup/previous".equals(path)){
            if(RoomData.guest(context))throw new IOException("Quittez le mode invités pour exporter vos données personnelles");
            discard(input,length);java.io.File file="/backup".equals(path)?PersonalBackup.create(context):new java.io.File(context.getFilesDir(),"last-restore-backup.zip");
            try{if(!file.isFile())throw new IOException("Aucune restauration précédente");
                OutputStream out=client.getOutputStream();String head="HTTP/1.1 200 OK\r\nContent-Type: application/zip\r\nContent-Disposition: attachment; filename=\"maros-backup.zip\"\r\nContent-Length: "+file.length()+"\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n";out.write(head.getBytes(StandardCharsets.US_ASCII));
                try(InputStream fileIn=new java.io.FileInputStream(file)){byte[] buffer=new byte[32768];int count;while((count=fileIn.read(buffer))!=-1)out.write(buffer,0,count);}out.flush();
            }finally{if("/backup".equals(path))file.delete();}return;
        }
        if("/restore".equals(path)||"/restore/validate".equals(path)){
            if(!contentType.startsWith("application/zip"))throw new IOException("Archive ZIP attendue");
            if(new android.os.StatFs(context.getCacheDir().getPath()).getAvailableBytes()<2L*length+64L*1024*1024)throw new IOException("Espace insuffisant pour vérifier cette archive");
            java.io.File file=java.io.File.createTempFile("maros-restore-",".zip",context.getCacheDir());
            try{try(OutputStream archive=new java.io.FileOutputStream(file)){byte[] buffer=new byte[32768];int remaining=length;while(remaining>0){int count=input.read(buffer,0,Math.min(remaining,buffer.length));if(count<0)throw new IOException("Archive interrompue");archive.write(buffer,0,count);remaining-=count;}}
                respond(client,200,"application/json",PersonalBackup.restore(context,file,"/restore".equals(path)).toString());
            }finally{file.delete();}return;
        }
        if("/photo".equals(path)){
            if(!contentType.startsWith("image/")){discard(input,length);respond(client,415,"text/plain","Une photo JPEG, PNG ou WebP est attendue");return;}
            byte[] body=read(input,length);new PhotoStore(context).importImage(new ByteArrayInputStream(body),"Photo du téléphone");respond(client,200,"text/plain","Photo ajoutée à ton album.");return;
        }
        if("/banner".equals(path)){
            if(!contentType.startsWith("image/")){discard(input,length);respond(client,415,"text/plain","Une image JPEG, PNG ou WebP est attendue");return;}
            String pkg=headers.get("x-tvhome-package");
            if(!LauncherPrefs.validPackage(pkg)){discard(input,length);respond(client,400,"text/plain","Application non valide");return;}
            try{context.getPackageManager().getApplicationInfo(pkg,0);}catch(android.content.pm.PackageManager.NameNotFoundException e){discard(input,length);respond(client,404,"text/plain","Cette application n’est pas installée sur la TV");return;}
            new PhotoStore(context).importBanner(pkg,new ByteArrayInputStream(read(input,length)));
            respond(client,200,"text/plain","Nouvelle bannière enregistrée. Reviens à l’accueil pour la voir.");return;
        }
        if(!contentType.startsWith("application/json")){discard(input,length);respond(client,415,"text/plain","JSON attendu");return;}
        JSONObject body=new JSONObject(new String(read(input,length),StandardCharsets.UTF_8));
        if("/google/status".equals(path)){respond(client,200,"application/json",GoogleLink.status(context).toString());return;}
        if("/calendar".equals(path)){respond(client,200,"application/json",new JSONObject().put("events",CalendarAccess.INSTANCE.events(context)).put("guest",RoomData.guest(context)).toString());return;}
        if("/status".equals(path)){respond(client,200,"application/json",RoomData.status(context).toString());return;}
        if("/control".equals(path)){respond(client,200,"application/json",RoomData.control(context,body).toString());return;}
        if("/tasks".equals(path)){respond(client,200,"application/json",RoomTasks.start(context,body).toString());return;}
        if("/task".equals(path)){respond(client,200,"application/json",RoomTasks.status(body.getString("id")).toString());return;}
        if("/watchlist".equals(path)){
            Watchlist list=new Watchlist(context);String action=body.optString("action","list");
            if("search".equals(action)){respond(client,200,"application/json",list.search(body.getString("query")).toString());return;}
            if("add".equals(action))list.add(body.getString("title"),body.optString("package"),body.optInt("show_id"));
            else if("remove".equals(action)||"next".equals(action)||"season".equals(action))list.change(body.getString("id"),action);
            else if("service".equals(action))list.setService(body.getString("id"),body.getString("package"));
            else if(!"list".equals(action))throw new IOException("Action inconnue");
            respond(client,200,"application/json",list.items().toString());return;
        }
        if("/close".equals(path)){
            respond(client,200,"text/plain","Connexion fermée.");
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(()->CompanionService.end(context),150);return;
        }
        if("/profiles".equals(path)){
            io.github.marouane4142.maros.tv.Profiles profiles=new io.github.marouane4142.maros.tv.Profiles(context);
            respond(client,200,"application/json",new JSONObject().put("names",new JSONArray(profiles.names())).put("current",profiles.getCurrent()).toString());return;
        }
        if("/profile".equals(path)){
            io.github.marouane4142.maros.tv.Profiles profiles=new io.github.marouane4142.maros.tv.Profiles(context);
            String action=body.getString("action"),name=body.getString("name");
            if("create".equals(action))profiles.create(name);else if("switch".equals(action))profiles.select(name);else if("delete".equals(action))profiles.delete(name);else throw new IOException("Action inconnue");
            respond(client,200,"application/json",new JSONObject().put("names",new JSONArray(profiles.names())).put("current",profiles.getCurrent()).toString());return;
        }
        if("/preview".equals(path)){
            String pkg=body.getString("package");if(!LauncherPrefs.validPackage(pkg))throw new IOException("Application invalide");
            context.getPackageManager().getApplicationInfo(pkg,0);
            android.graphics.Bitmap bitmap=RoomData.guest(context)?null:new PhotoStore(context).getBanner(pkg,400,225);
            if(bitmap==null){android.graphics.drawable.Drawable drawable=context.getPackageManager().getApplicationBanner(pkg);if(drawable==null)drawable=context.getPackageManager().getApplicationIcon(pkg);bitmap=android.graphics.Bitmap.createBitmap(400,225,android.graphics.Bitmap.Config.RGB_565);android.graphics.Canvas canvas=new android.graphics.Canvas(bitmap);canvas.drawColor(0xFF17243A);drawable.setBounds(0,0,400,225);drawable.draw(canvas);}
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG,80,bytes);bitmap.recycle();
            respond(client,200,"application/json",new JSONObject().put("image","data:image/jpeg;base64,"+android.util.Base64.encodeToString(bytes.toByteArray(),android.util.Base64.NO_WRAP)).toString());return;
        }
        if("/apps".equals(path)){
            List<ResolveInfo> apps=AppCatalog.query(context);
            Collections.sort(apps,(a,b)->String.valueOf(a.loadLabel(context.getPackageManager())).compareToIgnoreCase(String.valueOf(b.loadLabel(context.getPackageManager()))));
            JSONArray list=new JSONArray();Set<String> seen=new HashSet<>();
            for(ResolveInfo app:apps){String pkg=app.activityInfo.packageName;if(pkg.equals(context.getPackageName())||!seen.add(pkg))continue;list.put(new JSONObject().put("label",String.valueOf(app.loadLabel(context.getPackageManager()))).put("package",pkg).put("favorite",prefs.dock().contains(pkg)).put("category",prefs.category(pkg)).put("order",prefs.dock().indexOf(pkg)));}
            respond(client,200,"application/json",list.toString());return;
        }
        if("/favorite".equals(path)){String pkg=body.optString("package");if(!LauncherPrefs.validPackage(pkg))throw new IOException("Application invalide");context.getPackageManager().getApplicationInfo(pkg,0);List<String> dock=prefs.dock();boolean add=body.optBoolean("favorite");if(add&&!dock.contains(pkg))dock.add(pkg);else if(!add)dock.remove(pkg);if(add){Set<String> hidden=prefs.hidden();hidden.remove(pkg);prefs.setHidden(hidden);}prefs.setDock(dock);respond(client,200,"application/json",new JSONObject().put("favorite",add).toString());return;}
        if("/history".equals(path)){respond(client,200,"application/json",RoomData.guest(context)?"{\"items\":[],\"guest\":true}":new ContentBridge(context).diagnostics().toString());return;}
        if("/history/import".equals(path)){
            int count=PlaybackHistory.importObservations(context,body.getJSONArray("items"));
            respond(client,200,"application/json",new JSONObject().put("imported",count).toString());return;
        }
        if("/arrange".equals(path)){
            JSONArray order=body.getJSONArray("order");if(order.length()>250)throw new IOException("Trop d’applications");
            List<String> clean=new ArrayList<>();for(int i=0;i<order.length();i++){String pkg=order.getString(i);if(!LauncherPrefs.validPackage(pkg))throw new IOException("Application invalide");try{context.getPackageManager().getApplicationInfo(pkg,0);}catch(android.content.pm.PackageManager.NameNotFoundException absent){if(!prefs.dock().contains(pkg))throw new IOException("Application inconnue");}if(!clean.contains(pkg))clean.add(pkg);}
            prefs.setDock(clean);respond(client,200,"text/plain","Ordre des favoris enregistré.");return;
        }
        if("/collection".equals(path)){
            String pkg=body.getString("package"),category=body.getString("category");
            if(!LauncherPrefs.validPackage(pkg)||!Arrays.asList("regarder","musique","jeux","outils").contains(category))throw new IOException("Collection invalide");
            context.getPackageManager().getApplicationInfo(pkg,0);prefs.setCategory(pkg,category);respond(client,200,"text/plain","Collection enregistrée.");return;
        }
        if("/layout".equals(path)){
            JSONArray order=body.getJSONArray("order");List<String> clean=new ArrayList<>();
            for(int i=0;i<order.length();i++){String row=order.getString(i);if(Arrays.asList("favorites","continue","recent","library","shortcuts").contains(row)&&!clean.contains(row))clean.add(row);}
            for(String row:Arrays.asList("favorites","continue","recent","library","shortcuts"))if(!clean.contains(row))clean.add(row);
            prefs.putString("home_rows",new JSONArray(clean).toString());respond(client,200,"text/plain","Accueil réorganisé.");return;
        }
        if("/export".equals(path)){respond(client,200,"application/json",prefs.exportConfiguration());return;}
        if("/import".equals(path)){int count=prefs.importConfiguration(body.toString());respond(client,200,"text/plain",count+" réglages restaurés. Photos et jetons restent propres à cette TV.");return;}
        if("/search".equals(path)){String query=body.optString("text").trim();if(query.isEmpty()||query.length()>500)throw new IOException("Texte limité à 500 caractères");prefs.putString("phone_pending_search",query);context.sendBroadcast(new Intent(RoomData.ACTION_CONTROL).setPackage(context.getPackageName()).putExtra("command","phone_search"));respond(client,200,"text/plain","Recherche envoyée à l’accueil de la TV.");return;}
        if("/bookmark".equals(path)){String link=body.optString("uri").trim(),label=body.optString("label","Lien du téléphone").trim();if(!HubData.safeUri(link)||label.isEmpty()||label.length()>100)throw new IOException("Lien ou nom invalide");HubData data=new HubData(context);JSONArray shortcuts=data.shortcuts();if(shortcuts.length()>=50)throw new IOException("50 raccourcis maximum");shortcuts.put(new JSONObject().put("label",label).put("uri",link).put("package",""));data.saveArray("hub_shortcuts",shortcuts);respond(client,200,"text/plain","Raccourci ajouté. Ouvre Raccourcis sur la TV.");return;}
        respond(client,404,"text/plain","Introuvable");
    }catch(Exception error){try{
        String message="Envoi refusé. Vérifie le format et la taille du fichier.";
        if(error instanceof IllegalArgumentException||error instanceof IllegalStateException){String detail=error.getMessage();if(detail!=null&&!detail.contains("\n")&&detail.length()<=300&&!"Failed requirement.".equals(detail))message=detail;}
        respond(socket,400,"text/plain",message);
    }catch(Exception ignored){}}finally{deadline.cancel();try{socket.close();}catch(Exception ignored){}}}
    private static void discard(InputStream in,int length)throws IOException{byte[] buffer=new byte[4096];int remaining=length;while(remaining>0){int n=in.read(buffer,0,Math.min(buffer.length,remaining));if(n<0)throw new EOFException();remaining-=n;}}
    private static byte[] read(InputStream in,int length)throws Exception{byte[] value=new byte[length];int offset=0,count;while(offset<length&&(count=in.read(value,offset,length-offset))>0)offset+=count;if(offset!=length)throw new EOFException();return value;}
    private static String line(InputStream input,int max)throws Exception{ByteArrayOutputStream buffer=new ByteArrayOutputStream();int c;while((c=input.read())!=-1){if(c=='\n')break;if(c!='\r')buffer.write(c);if(buffer.size()>max)throw new IOException("Ligne trop longue");}return c==-1&&buffer.size()==0?null:new String(buffer.toByteArray(),StandardCharsets.US_ASCII);}
    private static void respond(Socket client,int status,String type,String body)throws Exception{byte[] bytes=body.getBytes(StandardCharsets.UTF_8);OutputStream output=client.getOutputStream();String header="HTTP/1.1 "+status+" Response\r\nContent-Type: "+type+"\r\nContent-Length: "+bytes.length+"\r\nCache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\nContent-Security-Policy: default-src 'self'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; connect-src 'self'; img-src 'self' data:; frame-ancestors 'none'\r\nConnection: close\r\n\r\n";output.write(header.getBytes(StandardCharsets.US_ASCII));output.write(bytes);output.flush();}
    private String page()throws IOException{try(InputStream in=context.getAssets().open("companion.html");ByteArrayOutputStream bytes=new ByteArrayOutputStream()){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)bytes.write(b,0,n);return new String(bytes.toByteArray(),StandardCharsets.UTF_8);}}
}
