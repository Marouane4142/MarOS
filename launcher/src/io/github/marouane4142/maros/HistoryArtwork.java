package io.github.marouane4142.maros;

import android.content.Context;
import android.graphics.*;
import android.media.MediaMetadata;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;

/** Retains artwork explicitly shared by players; never captures video frames or DRM surfaces. */
public final class HistoryArtwork {
    private static final long QUOTA=12L*1024*1024;
    private static final Set<String> pending=new HashSet<>();
    private static int generation;
    private static final ThreadPoolExecutor writer=new ThreadPoolExecutor(1,1,20,TimeUnit.SECONDS,
        new ArrayBlockingQueue<>(8),r->{Thread t=new Thread(r,"MarOS-EpisodeArt");t.setDaemon(true);return t;});
    static{writer.allowCoreThreadTimeOut(true);}
    private static File folder(Context c){return new File(c.getFilesDir(),"episode-art");}
    private static String key(String pkg,String id,String title,String episode){
        try{byte[] bytes=MessageDigest.getInstance("SHA-256").digest((pkg+"|"+id+"|"+title+"|"+episode).getBytes(StandardCharsets.UTF_8));StringBuilder s=new StringBuilder();for(byte b:bytes)s.append(String.format(Locale.ROOT,"%02x",b&255));return s.toString();}
        catch(Exception e){throw new IllegalStateException(e);}
    }
    public static String find(Context c,String pkg,String id,String title,String episode){String key=key(pkg,id,title,episode);return new File(folder(c),key+".jpg").isFile()?"maros-art://"+key:"";}
    public static File resolve(Context c,String uri){
        if(uri==null||!uri.matches("maros-art://[a-f0-9]{64}"))return null;
        File file=new File(folder(c),uri.substring("maros-art://".length())+".jpg");return file.isFile()?file:null;
    }
    public static void remember(Context context,String pkg,String id,String title,String episode,MediaMetadata metadata){
        if(metadata==null)return;
        Bitmap shared=metadata.getBitmap(MediaMetadata.METADATA_KEY_ART);
        if(shared==null)shared=metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        if(shared==null)shared=metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
        if(shared==null||shared.isRecycled())return;
        String key=key(pkg,id,title,episode);Context c=context.getApplicationContext();File target=new File(folder(c),key+".jpg");
        final int started;
        synchronized(pending){if(target.isFile()||!pending.add(key))return;started=generation;}
        Bitmap bitmap=shared;
        try{writer.execute(()->{
            File temporary=null;Bitmap scaled=null;
            try{
                if(!folder(c).isDirectory()&&!folder(c).mkdirs())return;
                if(new android.os.StatFs(c.getFilesDir().getPath()).getAvailableBytes()<64L*1024*1024)return;
                int width=Math.min(640,bitmap.getWidth()),height=Math.max(1,(int)((long)bitmap.getHeight()*width/bitmap.getWidth()));
                if(height>640){width=Math.max(1,width*640/height);height=640;}
                scaled=Bitmap.createScaledBitmap(bitmap,width,height,true);
                temporary=File.createTempFile("episode-",".tmp",folder(c));
                try(OutputStream out=new FileOutputStream(temporary)){if(!scaled.compress(Bitmap.CompressFormat.JPEG,82,out))return;}
                synchronized(pending){if(started!=generation||!temporary.renameTo(target))return;}
                File[] files=folder(c).listFiles((d,n)->n.endsWith(".jpg"));
                if(files!=null){Arrays.sort(files,Comparator.comparingLong(File::lastModified));long bytes=0;for(File f:files)bytes+=f.length();for(int i=0;i<files.length;i++)if(bytes>QUOTA||files.length-i>64||System.currentTimeMillis()-files[i].lastModified()>RecentPolicy.RETENTION){long size=files[i].length();if(files[i].delete())bytes-=size;}}
                new LauncherPrefs(c).changed();
            }catch(Exception|OutOfMemoryError ignored){}
            finally{if(scaled!=null&&scaled!=bitmap)scaled.recycle();if(temporary!=null)temporary.delete();synchronized(pending){pending.remove(key);}}
        });}catch(RejectedExecutionException ignored){synchronized(pending){pending.remove(key);}}
    }
    public static void clear(Context c){synchronized(pending){generation++;File[] files=folder(c).listFiles();if(files!=null)for(File f:files)if(f.isFile())f.delete();}}
}
