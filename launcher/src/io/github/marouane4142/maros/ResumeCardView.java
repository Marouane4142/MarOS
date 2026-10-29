package io.github.marouane4142.maros;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.LruCache;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Native content card. Suggested caller dimensions: 200 dp by 155 dp.
 * Artwork is decoded off the UI thread to exactly 400 x 225; the shared cache is capped at 4 MiB.
 * This view never synthesizes content or progress. Its click action belongs to the caller.
 */
public final class ResumeCardView extends FrameLayout {
    private static final int ART_W=400, ART_H=225, MAX_BODY=4*1024*1024;
    private static final LruCache<String,Bitmap> ART=new LruCache<String,Bitmap>(4*1024*1024) {
        @Override protected int sizeOf(String key,Bitmap bitmap){return Math.max(1,bitmap.getAllocationByteCount());}
    };
    private static final ThreadPoolExecutor LOADERS=new ThreadPoolExecutor(2,2,30,TimeUnit.SECONDS,
            new ArrayBlockingQueue<Runnable>(24),r->{Thread t=new Thread(r,"Aurora-ResumeArt");t.setDaemon(true);return t;});
    static { LOADERS.allowCoreThreadTimeOut(true); }
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ImageView artwork;
    private final View imageShade;
    private final TextView fallback;
    private final String imageUri;
    private boolean attached;
    private int generation;
    private Future<?> pending;
    private ArtworkRequest request;

    public ResumeCardView(Context context,ContentBridge.Item item,Runnable open,View.OnFocusChangeListener externalFocus){
        super(context);
        if(item==null)throw new IllegalArgumentException("A real content item is required");
        imageUri=item.imageUri==null?"":item.imageUri.trim();
        setFocusable(true);setClickable(true);setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        setClipToOutline(true);setElevation(dp(2));
        GradientDrawable surface=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xFF294761,0xFF0C192B});
        surface.setCornerRadius(dp(13));setBackground(surface);

        fallback=new TextView(context);fallback.setGravity(Gravity.CENTER);fallback.setPadding(dp(82),dp(36),dp(82),dp(36));fallback.setCompoundDrawables(new MarOSUI.Icon("play",MarOSUI.accent(context),dp(32)),null,null,null);
        addView(fallback,new LayoutParams(-1,dp(112),Gravity.TOP));
        artwork=new ImageView(context);artwork.setScaleType(ImageView.ScaleType.CENTER_CROP);artwork.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(artwork,new LayoutParams(-1,dp(112),Gravity.TOP));
        imageShade=new View(context);imageShade.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        imageShade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0x08091522,0x33091522,0xEF0C192B}));
        addView(imageShade,new LayoutParams(-1,dp(112),Gravity.TOP));

        String appLabel=applicationLabel(context,item.packageName);
        TextView badge=label(context,8,0xFFF2F7FF,"sans-serif-medium");badge.setText(appLabel);
        badge.setSingleLine(true);badge.setEllipsize(TextUtils.TruncateAt.END);badge.setPadding(dp(7),dp(4),dp(7),dp(4));
        GradientDrawable tag=new GradientDrawable();tag.setColor(0xB818273B);tag.setCornerRadius(dp(6));badge.setBackground(tag);
        LayoutParams bp=new LayoutParams(-2,-2,Gravity.TOP|Gravity.LEFT);bp.setMargins(dp(10),dp(9),dp(10),0);addView(badge,bp);

        boolean progress=item.position>0&&item.duration>0;
        LinearLayout labels=new LinearLayout(context);labels.setOrientation(LinearLayout.VERTICAL);
        TextView title=label(context,12,0xFFF8FAFE,"sans-serif-medium");title.setText(clean(item.title,300));title.setMaxLines(2);title.setEllipsize(TextUtils.TruncateAt.END);
        title.setShadowLayer(dp(3),0,dp(1),0x99000000);labels.addView(title,new LinearLayout.LayoutParams(-1,-2));
        String sub=clean(item.subtitle,200);
        if(!sub.isEmpty()){
            TextView subtitle=label(context,9,0xFFBDCADB,"sans-serif");subtitle.setText(sub);subtitle.setSingleLine(true);subtitle.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,-2);sp.topMargin=dp(3);labels.addView(subtitle,sp);
        }
        LayoutParams lp=new LayoutParams(-1,-2,Gravity.BOTTOM);lp.setMargins(dp(12),0,dp(12),dp(progress?22:12));addView(labels,lp);
        String description=clean(item.title,300)+", "+appLabel;
        if(progress){
            int part=(int)Math.round(1000.0*Math.max(0,Math.min(item.position,item.duration))/item.duration);
            ProgressBar bar=new ProgressBar(context,null,android.R.attr.progressBarStyleHorizontal);bar.setMax(1000);bar.setProgress(part);
            bar.setProgressTintList(ColorStateList.valueOf(MarOSUI.accent(context)));bar.setProgressBackgroundTintList(ColorStateList.valueOf(0x425D7892));
            bar.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            LayoutParams pp=new LayoutParams(-1,dp(4),Gravity.BOTTOM);pp.setMargins(dp(12),0,dp(12),dp(10));addView(bar,pp);
            description+=", progression "+Math.round(part/10f)+" %";
        }
        setContentDescription(description);
        setOnClickListener(v->{if(open!=null)open.run();});
        setOnFocusChangeListener((view,focused)->{
            animate().cancel();boolean reduced=new LauncherPrefs(context).getBool("reduced_animations",false);
            if(reduced){setScaleX(1);setScaleY(1);}else animate().scaleX(focused?1.035f:1).scaleY(focused?1.035f:1).setDuration(140).setInterpolator(new DecelerateInterpolator()).start();
            setElevation(dp(focused?8:2));
            if(focused){GradientDrawable border=new GradientDrawable();border.setColor(Color.TRANSPARENT);border.setCornerRadius(dp(13));border.setStroke(dp(2),MarOSUI.accent(context));setForeground(border);}else setForeground(null);
            if(externalFocus!=null)externalFocus.onFocusChange(view,focused);
        });
    }
    private int dp(float value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private static TextView label(Context context,int size,int color,String font){TextView view=new TextView(context);view.setTextSize(size);view.setTextColor(color);view.setTypeface(Typeface.create(font,Typeface.NORMAL));view.setIncludeFontPadding(false);return view;}
    private static String clean(String value,int max){if(value==null)return "";value=value.replaceAll("[\\p{Cntrl}]"," ").trim();return value.substring(0,Math.min(max,value.length()));}
    private static String applicationLabel(Context context,String pkg){
        if(pkg!=null)try{return clean(String.valueOf(context.getPackageManager().getApplicationLabel(context.getPackageManager().getApplicationInfo(pkg,0))),80);}catch(Exception ignored){ }
        return "À regarder";
    }
    @Override protected void onSizeChanged(int width,int height,int oldWidth,int oldHeight){
        super.onSizeChanged(width,height,oldWidth,oldHeight);int thumbHeight=Math.min(height,Math.round(width*9f/16f));
        for(View view:new View[]{artwork,imageShade,fallback}){LayoutParams p=(LayoutParams)view.getLayoutParams();if(p.height!=thumbHeight){p.height=thumbHeight;view.setLayoutParams(p);}}
    }
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();attached=true;loadArtwork();}
    @Override protected void onDetachedFromWindow(){
        attached=false;generation++;handler.removeCallbacksAndMessages(null);animate().cancel();
        if(request!=null){request.cancel();request=null;}
        if(pending!=null){pending.cancel(true);if(pending instanceof Runnable)LOADERS.remove((Runnable)pending);pending=null;}
        artwork.setImageDrawable(null);fallback.setVisibility(VISIBLE);super.onDetachedFromWindow();
    }
    private void loadArtwork(){
        if(imageUri.isEmpty()||imageUri.length()>8192)return;
        Bitmap cached=ART.get(imageUri);if(cached!=null&&!cached.isRecycled()){artwork.setImageBitmap(cached);fallback.setVisibility(GONE);return;}
        int version=++generation;
        request=new ArtworkRequest(getContext().getApplicationContext(),imageUri,version,this);
        try{pending=LOADERS.submit(request);}catch(java.util.concurrent.RejectedExecutionException ignored){request=null;}
    }

    private static final class ArtworkRequest implements Runnable {
        private final Context context;
        private final String uri;
        private final int generation;
        private final WeakReference<ResumeCardView> target;
        private final CancellationSignal cancellation=new CancellationSignal();
        private volatile boolean cancelled;
        private volatile HttpURLConnection connection;
        ArtworkRequest(Context context,String uri,int generation,ResumeCardView view){this.context=context;this.uri=uri;this.generation=generation;target=new WeakReference<>(view);}
        void cancel(){
            cancelled=true;
            try{cancellation.cancel();}catch(RuntimeException ignored){ }
            HttpURLConnection c=connection;if(c!=null)try{c.disconnect();}catch(RuntimeException ignored){ }
        }
        @Override public void run(){
            File body=null;Bitmap bitmap=null;
            try{
                if(cancelled)return;
                Bitmap cached=ART.get(uri);if(cached!=null&&!cached.isRecycled())bitmap=cached;
                else{
                    Uri parsed=Uri.parse(uri);String scheme=parsed.getScheme();if(scheme==null)return;scheme=scheme.toLowerCase(Locale.ROOT);
                    if(!scheme.equals("https")&&!scheme.equals("http")&&!scheme.equals("content"))return;
                    body=File.createTempFile("resume-art-",".tmp",context.getCacheDir());
                    if(scheme.equals("content")){
                        try(AssetFileDescriptor descriptor=context.getContentResolver().openAssetFileDescriptor(parsed,"r",cancellation)){
                            if(descriptor==null||descriptor.getDeclaredLength()>MAX_BODY)return;
                            try(InputStream input=descriptor.createInputStream()){copy(input,body);}
                        }
                    }else readHttp(parsed,body);
                    if(cancelled)return;
                    bitmap=decode(body);
                    if(bitmap==null)return;
                    if(cancelled){bitmap.recycle();return;}
                    ART.put(uri,bitmap);
                }
                if(cancelled||bitmap==null)return;
                final Bitmap result=bitmap;
                ResumeCardView owner=target.get();
                if(owner!=null)owner.handler.post(()->{
                    ResumeCardView view=target.get();
                    if(view==null||!view.attached||view.generation!=generation||cancelled)return;
                    view.artwork.setImageBitmap(result);view.fallback.setVisibility(GONE);view.pending=null;view.request=null;
                });
            }catch(Exception|OutOfMemoryError ignored){ /* The native fallback remains usable. */ }
            finally{HttpURLConnection c=connection;connection=null;if(c!=null)c.disconnect();if(body!=null)body.delete();}
        }
        private void readHttp(Uri source,File body)throws IOException{
            URL address=new URL(source.toString());
            for(int redirects=0;redirects<=3;redirects++){
                if(cancelled)throw new IOException("Cancelled");
                String protocol=address.getProtocol();if(!"https".equals(protocol)&&!"http".equals(protocol))throw new IOException("Unsupported image address");
                HttpURLConnection current=(HttpURLConnection)address.openConnection();connection=current;
                current.setConnectTimeout(3000);current.setReadTimeout(5000);current.setInstanceFollowRedirects(false);current.setUseCaches(false);
                current.setRequestProperty("Accept","image/*");current.setRequestProperty("Connection","close");
                int code=current.getResponseCode();
                if(code==301||code==302||code==303||code==307||code==308){
                    String location=current.getHeaderField("Location");current.disconnect();connection=null;
                    if(location==null||redirects==3)throw new IOException("Invalid image redirect");address=new URL(address,location);continue;
                }
                if(code<200||code>=300||current.getContentLengthLong()>MAX_BODY)throw new IOException("Image unavailable");
                try(InputStream input=current.getInputStream()){copy(input,body);}return;
            }
            throw new IOException("Too many redirects");
        }
        private void copy(InputStream input,File output)throws IOException{
            try(FileOutputStream out=new FileOutputStream(output)){
                byte[] buffer=new byte[16384];long count=0;int n;
                while((n=input.read(buffer))!=-1){
                    if(cancelled||Thread.currentThread().isInterrupted())throw new IOException("Cancelled");
                    count+=n;if(count>MAX_BODY)throw new IOException("Image exceeds 4 MiB");out.write(buffer,0,n);
                }
            }
        }
        private Bitmap decode(File file){
            BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;BitmapFactory.decodeFile(file.getAbsolutePath(),options);
            if(options.outWidth<1||options.outHeight<1||options.outWidth>50000||options.outHeight>50000)return null;
            options.inSampleSize=1;while(options.inSampleSize<1024&&(options.outWidth/options.inSampleSize>800||options.outHeight/options.inSampleSize>450))options.inSampleSize*=2;
            options.inJustDecodeBounds=false;options.inPreferredConfig=Bitmap.Config.RGB_565;
            Bitmap source=BitmapFactory.decodeFile(file.getAbsolutePath(),options);if(source==null)return null;
            try{
                Bitmap thumbnail=Bitmap.createBitmap(ART_W,ART_H,Bitmap.Config.RGB_565);Canvas canvas=new Canvas(thumbnail);
                float scale=Math.max((float)ART_W/source.getWidth(),(float)ART_H/source.getHeight());
                float width=source.getWidth()*scale,height=source.getHeight()*scale,left=(ART_W-width)/2,top=(ART_H-height)/2;
                Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);canvas.drawBitmap(source,null,new RectF(left,top,left+width,top+height),paint);return thumbnail;
            }finally{source.recycle();}
        }
    }
}
