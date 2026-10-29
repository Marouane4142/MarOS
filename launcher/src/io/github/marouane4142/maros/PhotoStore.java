package io.github.marouane4142.maros;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** Local, bounded image library. No external storage permission or network access is needed. */
public final class PhotoStore {
    private static final Object LOCK = new Object();
    private static final long MAX_SOURCE_BYTES = 20L * 1024 * 1024;
    private final Context context;
    private final SharedPreferences meta;
    private final File photos, banners;
    public static final class Photo {
        public final String id, title, credit;
        public final boolean favorite, excluded, builtin;
        Photo(String i,String t,String c,boolean f,boolean e,boolean b) { id=i; title=t; credit=c; favorite=f; excluded=e; builtin=b; }
    }
    private static final String[] TITLES={"Lac Louise · Canada","Lofoten · Norvège","Baleia Franca · Brésil","Lac du Cuniã · Amazonie","Spullersee · Autriche","Paczków · Pologne","Stromboli · Italie","Flatirons · Colorado","Étretat · Normandie","Kjeungskjær · Norvège","Eilean Donan · Écosse","Vallée de Dzukou · Inde"};
    private static final String[] CREDITS={"Chensiyuan · CC BY-SA 4.0","Ximonic / Simo Räsänen · CC BY-SA 3.0","Ctseelig · CC BY-SA 4.0","RodrigoErse · CC BY-SA 4.0","Feel free to use my photos, but please m · CC BY-SA 3.0 AT","Jacek Halicki · CC BY-SA 3.0","Carsten Steger · CC BY-SA 4.0","Tyler Cipriani · CC BY-SA 4.0","Jörg Braukmann · CC BY-SA 4.0","Gordon Leggett · CC BY-SA 4.0","Diliff · CC BY-SA 3.0","Samudra Bikash Hazarika · CC BY-SA 4.0"};
    private static final String[] SOURCES={"1 lake louise pano 2019.jpg","Cirrus front over Austnesfjorden, Austvågøya, Lofoten, Norway, 2015 April.jpg","Área de Proteção Ambiental da Baleia Franca Claudio Seelig (01).jpg","Aurora amazônica no Lago do Cuniã.jpg","2015 Spullersee im Lechquellengebirge.jpg","2014 Paczków, staw.jpg","Aerial image of Stromboli (view from the northeast).jpg","Flatirons Sunrise.jpg","Manneporte en soir.jpg","2016-11-13 01 Kjeungskjær Lighthouse, Sør-Trøndelag, Norway.jpg","Eilean Donan Castle, Scotland - Jan 2011.jpg","Breathtaking beauty of Dzukou Valley in Manipur-Nagaland border (edit).jpg"};
    public PhotoStore(Context c) {
        context=c.getApplicationContext(); meta=context.getSharedPreferences("photo_meta_v2",Context.MODE_PRIVATE);
        photos=new File(context.getFilesDir(),"photos"); banners=new File(context.getFilesDir(),"banners");
    }
    public List<Photo> all() {
        synchronized(LOCK) {
            ArrayList<Photo> out=new ArrayList<>(); JSONArray stored=records();
            try {
                String[] assets=context.getAssets().list("wallpapers"); if(assets!=null) { Arrays.sort(assets);
                    for(int i=0;i<assets.length;i++) { String name=assets[i]; if(!name.matches("[A-Za-z0-9_.-]+\\.(?i:jpg|jpeg|png|webp)")) continue;
                        JSONObject r=find(stored,"asset:"+name); out.add(photo(r,"asset:"+name,i<TITLES.length?TITLES[i]:name,i<CREDITS.length?CREDITS[i]:"Photo intégrée",true)); }
                }
            } catch(IOException ignored) { }
            for(int i=0;i<stored.length();i++) { JSONObject r=stored.optJSONObject(i); if(r==null) continue; String id=r.optString("id");
                if(validLocalId(id)&&fileFor(id).isFile()) out.add(photo(r,id,"Photo personnelle","Album personnel",false)); }
            return out;
        }
    }
    public List<Photo> list() {
        List<Photo> included=new ArrayList<>(), favorites=new ArrayList<>();
        for(Photo p:all()) if(!p.excluded) { included.add(p); if(p.favorite) favorites.add(p); }
        // Never leave the user with an empty album merely because no favorite was selected yet.
        return "favorites".equals(new LauncherPrefs(context).getString("photo_mode","all"))&&!favorites.isEmpty()?favorites:included;
    }
    public Bitmap load(Photo photo,int width,int height) {
        if(photo==null) return null;
        try { BitmapFactory.Options opts=new BitmapFactory.Options(); opts.inJustDecodeBounds=true;
            try(InputStream stream=open(photo.id)) { BitmapFactory.decodeStream(stream,null,opts); }
            if(opts.outWidth<=0||opts.outHeight<=0) return null;
            opts.inSampleSize=sample(opts.outWidth,opts.outHeight,Math.max(1,width),Math.max(1,height)); opts.inJustDecodeBounds=false; opts.inPreferredConfig=Bitmap.Config.RGB_565;
            try(InputStream stream=open(photo.id)) { return BitmapFactory.decodeStream(stream,null,opts); }
        } catch(IOException|RuntimeException|OutOfMemoryError e) { return null; }
    }
    public Bitmap getBanner(String pkg,int width,int height) {
        if(!LauncherPrefs.validPackage(pkg)) return null;
        File f=new File(banners,pkg+".jpg"); if(!f.isFile()) return null;
        try { BitmapFactory.Options o=new BitmapFactory.Options(); o.inJustDecodeBounds=true; BitmapFactory.decodeFile(f.getAbsolutePath(),o);
            o.inSampleSize=sample(o.outWidth,o.outHeight,Math.max(1,width),Math.max(1,height)); o.inJustDecodeBounds=false; o.inPreferredConfig=Bitmap.Config.RGB_565;
            return BitmapFactory.decodeFile(f.getAbsolutePath(),o);
        } catch(RuntimeException|OutOfMemoryError e) { return null; }
    }
    public boolean hasBanner(String pkg) { return LauncherPrefs.validPackage(pkg)&&new File(banners,pkg+".jpg").isFile(); }
    public String source(Photo p) { if(p==null||!p.builtin) return ""; try { int n=Integer.parseInt(p.id.substring(6,8))-1; if(n>=0&&n<SOURCES.length) return "https://commons.wikimedia.org/wiki/File:"+Uri.encode(SOURCES[n]); } catch(Exception ignored) { } return ""; }
    public void removeBanner(String pkg) { if(LauncherPrefs.validPackage(pkg)) { new File(banners,pkg+".jpg").delete(); changed(); } }
    public void setFavorite(String id,boolean value) { update(id,"favorite",value); }
    public void setExcluded(String id,boolean value) { update(id,"excluded",value); }
    public void rename(String id,String title) { update(id,"title",cleanTitle(title)); }
    private void update(String id,String key,Object value) {
        synchronized(LOCK) {
            boolean exists=false; for(Photo p:all()) if(p.id.equals(id)) { exists=true; break; } if(!exists) return;
            JSONArray a=records(); JSONObject r=find(a,id); if(r==null) { r=new JSONObject(); a.put(r); }
            try { r.put("id",id).put(key,value); meta.edit().putString("records",a.toString()).apply(); } catch(Exception ignored) { }
        } changed();
    }
    public Photo importImage(Uri uri,String title) throws IOException { try(InputStream in=context.getContentResolver().openInputStream(uri)) { if(in==null) throw new IOException("Cette photo est inaccessible."); return importImage(in,title); } }
    public Photo importImage(InputStream input,String title) throws IOException {
        synchronized(LOCK) {
            int localCount=0; for(Photo p:all()) if(!p.builtin) localCount++; if(localCount>=100) throw new IOException("L’album contient déjà 100 photos personnelles. Retirez-en une avant d’en ajouter.");
            ensureDir(photos); String name=UUID.randomUUID()+".jpg"; File output=new File(photos,name); transcode(input,output,1920,1080);
            String id="local:"+name; JSONArray a=records();
            try { JSONObject r=new JSONObject().put("id",id).put("title",cleanTitle(title)).put("credit","Album personnel").put("favorite",false).put("excluded",false); a.put(r); if(!meta.edit().putString("records",a.toString()).commit()) { output.delete(); throw new IOException("Impossible d’enregistrer l’album."); }
                changed(); return photo(r,id,"Photo personnelle","Album personnel",false);
            } catch(org.json.JSONException e) { output.delete(); throw new IOException("Impossible d’enregistrer la photo.",e); }
        }
    }
    public void importBanner(String pkg,Uri uri) throws IOException { try(InputStream in=context.getContentResolver().openInputStream(uri)) { if(in==null) throw new IOException("Cette image est inaccessible."); importBanner(pkg,in); } }
    public void importBanner(String pkg,InputStream in) throws IOException { if(!LauncherPrefs.validPackage(pkg)) throw new IOException("Application non valide."); synchronized(LOCK) { ensureDir(banners); transcode(in,new File(banners,pkg+".jpg"),960,540); } changed(); }
    /** Only user-imported files can be removed. Builtin photos are reversible exclusions. */
    public void deleteImported(String id) throws IOException { if(!validLocalId(id)) throw new IOException("Une photo intégrée peut être masquée, pas supprimée."); synchronized(LOCK) { File f=fileFor(id); if(f.exists()&&!f.delete()) throw new IOException("Impossible de retirer cette photo."); JSONArray out=new JSONArray(), source=records(); for(int i=0;i<source.length();i++) { JSONObject r=source.optJSONObject(i); if(r!=null&&!id.equals(r.optString("id"))) out.put(r); } meta.edit().putString("records",out.toString()).apply(); } changed(); }
    private void transcode(InputStream in,File output,int maxW,int maxH) throws IOException {
        File temp=File.createTempFile("photo-source-",".tmp",context.getCacheDir()); File encoded=File.createTempFile("photo-result-",".jpg",output.getParentFile()); Bitmap bm=null;
        try {
            try(FileOutputStream raw=new FileOutputStream(temp)) { byte[] buf=new byte[32768]; int n; long total=0; while((n=in.read(buf))!=-1) { total+=n; if(total>MAX_SOURCE_BYTES) throw new IOException("Image trop volumineuse : 20 Mo maximum."); raw.write(buf,0,n); } }
            BitmapFactory.Options o=new BitmapFactory.Options(); o.inJustDecodeBounds=true; BitmapFactory.decodeFile(temp.getAbsolutePath(),o);
            if(o.outWidth<32||o.outHeight<32||o.outWidth>50000||o.outHeight>50000||(long)o.outWidth*o.outHeight>250000000L) throw new IOException("Choisissez une image JPEG, PNG ou WebP valide.");
            o.inSampleSize=sample(o.outWidth,o.outHeight,maxW,maxH); o.inJustDecodeBounds=false; o.inPreferredConfig=Bitmap.Config.ARGB_8888;
            bm=BitmapFactory.decodeFile(temp.getAbsolutePath(),o); if(bm==null) throw new IOException("Ce format d’image n’est pas pris en charge.");
            try { int orientation=new ExifInterface(temp.getAbsolutePath()).getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL); Matrix m=new Matrix();
                switch(orientation) { case 2:m.setScale(-1,1);break;case 3:m.setRotate(180);break;case 4:m.setScale(1,-1);break;case 5:m.setRotate(90);m.postScale(-1,1);break;case 6:m.setRotate(90);break;case 7:m.setRotate(270);m.postScale(-1,1);break;case 8:m.setRotate(270);break;default:break; }
                if(!m.isIdentity()) { Bitmap rotated=Bitmap.createBitmap(bm,0,0,bm.getWidth(),bm.getHeight(),m,true); if(rotated!=bm) { bm.recycle(); bm=rotated; } }
            } catch(IOException ignored) { }
            float ratio=Math.min(1f,Math.min((float)maxW/bm.getWidth(),(float)maxH/bm.getHeight())); if(ratio<1f) { Bitmap scaled=Bitmap.createScaledBitmap(bm,Math.max(1,Math.round(bm.getWidth()*ratio)),Math.max(1,Math.round(bm.getHeight()*ratio)),true); if(scaled!=bm) { bm.recycle();bm=scaled; } }
            try(FileOutputStream out=new FileOutputStream(encoded)) { if(!bm.compress(Bitmap.CompressFormat.JPEG,88,out)) throw new IOException("Impossible de convertir l’image."); out.getFD().sync(); }
            // Same-filesystem rename atomically replaces a previous banner on Android/Linux.
            if(!encoded.renameTo(output)) throw new IOException("Impossible d’enregistrer l’image.");
        } catch(OutOfMemoryError e) { throw new IOException("Image trop complexe pour la mémoire disponible."); }
        finally { if(bm!=null&&!bm.isRecycled()) bm.recycle(); temp.delete(); encoded.delete(); }
    }
    private static int sample(int w,int h,int tw,int th) { int n=1; while(n<128&&(w/(n*2)>tw||h/(n*2)>th)) n*=2; return n; }
    private InputStream open(String id) throws IOException { if(id.startsWith("asset:")&&id.substring(6).matches("[A-Za-z0-9_.-]+")) return context.getAssets().open("wallpapers/"+id.substring(6)); if(validLocalId(id)) return new FileInputStream(fileFor(id)); throw new IOException("Image invalide."); }
    private static boolean validLocalId(String id) { return id!=null&&id.matches("local:[a-f0-9-]{36}\\.jpg"); }
    private File fileFor(String id) { return new File(photos,id.substring(6)); }
    private JSONArray records() { try { return new JSONArray(meta.getString("records","[]")); } catch(Exception e) { return new JSONArray(); } }
    private static JSONObject find(JSONArray a,String id) { for(int i=0;i<a.length();i++) { JSONObject r=a.optJSONObject(i); if(r!=null&&id.equals(r.optString("id"))) return r; } return null; }
    private static Photo photo(JSONObject r,String id,String title,String credit,boolean builtin) { return new Photo(id,r==null?title:r.optString("title",title),r==null?credit:r.optString("credit",credit),r!=null&&r.optBoolean("favorite"),r!=null&&r.optBoolean("excluded"),builtin); }
    private static String cleanTitle(String s) { if(s==null||s.trim().isEmpty()) return "Photo personnelle"; s=s.replaceAll("[\\p{Cntrl}]"," ").trim(); return s.substring(0,Math.min(100,s.length())); }
    private static void ensureDir(File f) throws IOException { if(!f.isDirectory()&&!f.mkdirs()) throw new IOException("Stockage interne inaccessible."); }
    private void changed() { new LauncherPrefs(context).changed(); }
}
