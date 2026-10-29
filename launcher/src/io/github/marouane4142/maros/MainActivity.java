package io.github.marouane4142.maros;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.provider.Settings;
import android.util.Log;
import android.util.LruCache;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Native, local-first home. Connected features are configured separately and cached. */
public final class MainActivity extends Activity {
    private static final int WHITE=0xFFF7F8FC,MUTED=0xFFBAC5D8;
    private static final String[] TABS={"accueil","regarder","musique","jeux","outils","apps"};
    private static final String[] NAMES={"Accueil","Regarder","Musique","Jeux","Outils","Toutes les applis"};
    private LauncherPrefs prefs;private PhotoStore photos;private HubData hub;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ExecutorService images=Executors.newFixedThreadPool(2),photoWorker=Executors.newSingleThreadExecutor();
    private final LruCache<String,Bitmap> cache=new LruCache<String,Bitmap>(12288){protected int sizeOf(String k,Bitmap b){return Math.max(1,b.getByteCount()/1024);}};
    private final Map<String,App> apps=new LinkedHashMap<>();private final Map<String,View> appViews=new LinkedHashMap<>(),gridViews=new LinkedHashMap<>();
    private HorizontalScrollView dockScroll;private boolean selectedInDock=true,restoringFocus;private int savedY,savedX;
    private final List<View> tabs=new ArrayList<>();
    private FrameLayout root,hero;private ImageView front,back;private StableScroll scroll;private LinearLayout content,dynamicRows;
    private TextView clock,date,weather,info,place,title,networkButton;
    private String tab="accueil",selected="",photoId="";private boolean visible,registered,configChanged=true,firstResume=true;private int generation;
    private final List<Runnable> mediaCleanup=new ArrayList<>();
    private final Runnable rotate=()->showPhoto(false);
    private final Runnable ambient=()->{if(visible&&prefs.getInt("ambient_minutes",5)>0)startActivity(new Intent(this,AmbientActivity.class));};
    private long lastWeatherCheck;private int lastRevision=-1;
    private final Runnable pulse=new Runnable(){public void run(){if(visible){updateHeader();checkPhone();if(System.currentTimeMillis()-lastWeatherCheck>1800000){lastWeatherCheck=System.currentTimeMillis();hub.refresh(()->{if(visible)updateHeader();});}handler.postDelayed(this,15000);}}};
    private final Runnable persistNavigation=()->saveNav();
    private final Runnable redraw=()->{if(visible)build();};
    private final BroadcastReceiver changed=new BroadcastReceiver(){public void onReceive(Context c,Intent i){configChanged=LauncherPrefs.ACTION_CHANGED.equals(i.getAction());if(visible){handler.removeCallbacks(redraw);handler.postDelayed(redraw,180);}}};
    private static final class App {final String pkg,label;final ResolveInfo resolve;App(String p,String l,ResolveInfo r){pkg=p;label=l;resolve=r;}}

    @Override public void onCreate(Bundle b){
        super.onCreate(b);prefs=new LauncherPrefs(this);photos=new PhotoStore(this);hub=new HubData(this);
        if(prefs.getBool("remember_navigation",true)){tab=cleanTab(prefs.getString("nav_tab","accueil"));selected=prefs.getString("nav_package","");selectedInDock=prefs.getBool("nav_in_dock",true);savedY=prefs.getInt("nav_scroll_y",0);savedX=prefs.getInt("nav_scroll_x",0);}
        handle(getIntent());getWindow().getDecorView().setSystemUiVisibility(5894);build();
    }
    @Override protected void onResume(){
        super.onResume();visible=true;
        IntentFilter packages=new IntentFilter();packages.addAction(Intent.ACTION_PACKAGE_ADDED);packages.addAction(Intent.ACTION_PACKAGE_REMOVED);packages.addAction(Intent.ACTION_PACKAGE_CHANGED);packages.addDataScheme("package");
        registerReceiver(changed,packages);registerReceiver(changed,new IntentFilter(LauncherPrefs.ACTION_CHANGED));registered=true;
        if(!firstResume)build();firstResume=false;handler.post(pulse);resetAmbient();schedulePhoto();

        hub.refresh(()->{if(visible)updateHeader();});checkPhone();
    }
    @Override protected void onPause(){visible=false;generation++;clearMediaCallbacks();handler.removeCallbacks(rotate);handler.removeCallbacks(ambient);handler.removeCallbacks(pulse);handler.removeCallbacks(redraw);handler.removeCallbacks(persistNavigation);if(registered){unregisterReceiver(changed);registered=false;}saveNav();super.onPause();}
    @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);images.shutdownNow();photoWorker.shutdownNow();cache.evictAll();super.onDestroy();}
    @Override protected void onNewIntent(Intent i){super.onNewIntent(i);setIntent(i);handle(i);build();resetAmbient();}
    private void handle(Intent i){if(i==null)return;if(i.getBooleanExtra("open_apps",false)||"io.github.marouane4142.maros.SHOW_APPS".equals(i.getAction())){tab="apps";resetNavigation();}else if(i.hasExtra("collection")){tab=cleanTab(i.getStringExtra("collection"));resetNavigation();}}
    private String cleanTab(String value){return Arrays.asList(TABS).contains(value)?value:"accueil";}
    private void resetNavigation(){selected="";savedY=0;savedX=0;selectedInDock=true;}
    private void saveNav(){if(prefs!=null&&!restoringFocus){if(scroll!=null)savedY=scroll.getScrollY();if(dockScroll!=null)savedX=dockScroll.getScrollX();prefs.prefs().edit().putString("nav_tab",tab).putString("nav_package",selected).putBoolean("nav_in_dock",selectedInDock).putInt("nav_scroll_y",savedY).putInt("nav_scroll_x",savedX).apply();}}
    @Override public void onUserInteraction(){super.onUserInteraction();resetAmbient();}
    @Override public void onBackPressed(){if(!tab.equals("accueil")){tab="accueil";resetNavigation();build();}else{savedY=0;selectedInDock=true;focusSaved();}}
    @Override public boolean dispatchKeyEvent(KeyEvent e){
        if(e.getAction()==KeyEvent.ACTION_UP){if(e.getKeyCode()==KeyEvent.KEYCODE_MENU){homeMenu();return true;}if(e.getKeyCode()==KeyEvent.KEYCODE_SEARCH){HubActivity.open(this,"search");return true;}if(e.getKeyCode()==KeyEvent.KEYCODE_ALL_APPS){tab="apps";resetNavigation();build();return true;}}
        return super.dispatchKeyEvent(e);
    }

    private void readApps(){
        apps.clear();PackageManager pm=getPackageManager();List<App> sorted=new ArrayList<>();Set<String> seen=new HashSet<>();
        for(ResolveInfo r:pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER),0)){
            String p=r.activityInfo.packageName;if(!p.equals(getPackageName())&&seen.add(p))sorted.add(new App(p,String.valueOf(r.loadLabel(pm)),r));
        }
        Collections.sort(sorted,(a,b)->a.label.compareToIgnoreCase(b.label));for(App a:sorted)apps.put(a.pkg,a);
    }
    private void build(){
        if(isFinishing())return;clearMediaCallbacks();generation++;weather=null;place=null;title=null;
        int revision=prefs.getInt("ui_revision",0);if(revision!=lastRevision){configChanged=true;lastRevision=revision;}
        readApps();if(!prefs.getBool("store_added_v4",false)){if(apps.containsKey("com.android.vending")){List<String> dock=prefs.dock();if(!dock.contains("com.android.vending")){dock.add("com.android.vending");prefs.setDock(dock);}Set<String> hidden=prefs.hidden();hidden.remove("com.android.vending");prefs.setHidden(hidden);}prefs.prefs().edit().putBoolean("store_added_v4",true).apply();}appViews.clear();gridViews.clear();dockScroll=null;tabs.clear();if(configChanged)cache.evictAll();configChanged=false;
        root=new FrameLayout(this);root.setBackgroundColor(MarOSUI.BG);back=wall();front=wall();root.addView(back,new FrameLayout.LayoutParams(-1,-1));root.addView(front,new FrameLayout.LayoutParams(-1,-1));photoId="";
        float dim=Math.max(.2f,Math.min(.8f,prefs.getInt("contrast",45)/100f));View sideShade=new View(this);sideShade.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,new int[]{Color.argb(Math.min(250,(int)(180+dim*130)),8,13,23),Color.argb((int)(80+dim*224),8,13,23),Color.argb((int)(dim*213),8,13,23)}));root.addView(sideShade,new FrameLayout.LayoutParams(-1,-1));
        View shade=new View(this);int darkness=prefs.theme().equals("cinema")?100:prefs.theme().equals("minimal")?180:35;shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{Color.argb(55+darkness/2,8,13,23),Color.argb(darkness,8,13,23),0xF9080D17}));root.addView(shade,new FrameLayout.LayoutParams(-1,-1));
        scroll=new StableScroll(this);scroll.setFillViewport(true);scroll.setVerticalScrollBarEnabled(false);root.addView(scroll,new FrameLayout.LayoutParams(-1,-1));content=column();content.setClipChildren(false);content.setClipToPadding(false);scroll.addView(content,new ScrollView.LayoutParams(-1,-2));
        hero=new FrameLayout(this);hero.setClipChildren(false);boolean home=tab.equals("accueil");content.addView(hero,new LinearLayout.LayoutParams(-1,home?dp(484):dp(178)));
        buildHeader();buildTabs();dynamicRows=null;
        if(home){buildHero();dynamicRows=column();content.addView(dynamicRows);if(prefs.getBool("show_continue",true))loadDynamic(generation);quickActions();}
        else{
            TextView name=text(27,WHITE,"sans-serif-medium");name.setText(NAMES[Arrays.asList(TABS).indexOf(tab)]);hero.addView(name,frame(-1,-2,Gravity.TOP,44,137,44,0));
            if(tab.equals("apps")){actionRow("De nouvelles envies ?",new String[]{"Ajouter des apps · Play Store","Organiser mes applications"},new Runnable[]{()->new MediaBridge(this).open("com.android.vending"),()->startActivity(new Intent(this,SettingsActivity.class).putExtra("section","apps"))});}
            if(tab.equals("musique")){MusicPanelView music=new MusicPanelView(this,true);content.addView(music,lp(-1,dp(218),44,14,44,22));}
            List<App> list=filtered(tab.equals("apps")?null:tab);if(list.isEmpty())empty();else appGrid(null,list,prefs.getString("tile_size","normal").equals("large")?4:5);
            if(tab.equals("jeux"))actionRow("Le salon devient votre terrain de jeu",new String[]{"Réveiller mon PC","Ouvrir Moonlight","Ouvrir Parsec"},new Runnable[]{()->HubActivity.open(this,"pc"),()->new MediaBridge(this).open("com.limelight"),()->new MediaBridge(this).open("tv.parsec.client")});
            if(tab.equals("outils"))quickActions();
        }
        LinearLayout end=row();end.setGravity(Gravity.CENTER_VERTICAL);TextView footer=text(9,0xFF7B8DA9,"sans-serif-medium");footer.setText("MarOS · Chez vous.");end.addView(footer,new LinearLayout.LayoutParams(0,-2,1));TextView customize=pill("Personnaliser",()->startActivity(new Intent(this,SettingsActivity.class)));end.addView(customize,lp(-2,dp(36),0,0,0,0));content.addView(end,lp(-1,-2,44,28,44,24));
        setContentView(root);updateHeader();showPhoto(false);root.post(this::focusSaved);
    }
    private void buildHeader(){
        LinearLayout brand=MarOSUI.brand(this,false);brand.setFocusable(true);brand.setContentDescription("Menu MarOS");brand.setOnClickListener(v->homeMenu());brand.setOnFocusChangeListener((v,has)->v.setBackground(has?MarOSUI.border(this,0x77243650,MarOSUI.accent(this),12):null));hero.addView(brand,frame(dp(180),dp(38),Gravity.TOP|Gravity.LEFT,44,23,0,0));
        place=text(8,0xFFADBCD2,"sans-serif");place.setSingleLine(true);hero.addView(place,frame(dp(400),-2,Gravity.TOP|Gravity.LEFT,45,65,0,0));
        LinearLayout times=column();times.setGravity(Gravity.RIGHT);clock=text(21,WHITE,"sans-serif-light");clock.setGravity(Gravity.RIGHT);date=text(8,MUTED,"sans-serif");date.setGravity(Gravity.RIGHT);times.addView(clock);times.addView(date,lp(-2,-2,0,4,0,0));hero.addView(times,frame(dp(170),-2,Gravity.TOP|Gravity.RIGHT,0,23,196,0));
        LinearLayout buttons=row();buttons.setGravity(Gravity.CENTER_VERTICAL);buttons.addView(MarOSUI.iconButton(this,"search","Rechercher",()->HubActivity.open(this,"search")),lp(dp(38),dp(36),0,0,9,0));
        String network="offline";try{android.net.ConnectivityManager cm=(android.net.ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);android.net.NetworkCapabilities caps=cm==null?null:cm.getNetworkCapabilities(cm.getActiveNetwork());if(caps!=null)network=caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET)?"ethernet":"wifi";}catch(Exception ignored){}
        networkButton=MarOSUI.iconButton(this,network,"Réseau et Wi-Fi",()->safe(new Intent(Settings.ACTION_WIFI_SETTINGS)));buttons.addView(networkButton,lp(dp(38),dp(36),0,0,9,0));buttons.addView(MarOSUI.iconButton(this,"settings","Réglages MarOS",()->startActivity(new Intent(this,SettingsActivity.class))),lp(dp(38),dp(36),0,0,0,0));hero.addView(buttons,frame(-2,-2,Gravity.TOP|Gravity.RIGHT,0,24,44,0));
        info=text(8,0xFF9CADC5,"sans-serif");info.setGravity(Gravity.RIGHT);info.setSingleLine(true);hero.addView(info,frame(dp(420),-2,Gravity.TOP|Gravity.RIGHT,0,72,44,0));
    }
    private void buildTabs(){
        LinearLayout line=row();for(int i=0;i<TABS.length;i++){
            final String key=TABS[i];if(!prefs.getBool("show_collections",true)&&i>0&&i<5||i==3&&!prefs.getBool("show_games",true))continue;
            TextView button=text(11,WHITE,"sans-serif-medium");button.setText(NAMES[i]);button.setPadding(dp(16),dp(8),dp(16),dp(8));button.setGravity(Gravity.CENTER);button.setFocusable(true);button.setId(View.generateViewId());setTabStyle(button,key,false);
            button.setOnClickListener(v->{tab=key;resetNavigation();build();});button.setOnFocusChangeListener((v,has)->{setTabStyle(button,key,has);if(has)scroll.smoothScrollTo(0,0);});line.addView(button,lp(-2,dp(34),0,0,8,0));tabs.add(button);
        }hero.addView(strip(line),frame(-1,dp(40),Gravity.TOP,44,88,40,0));
    }
    private void setTabStyle(TextView button,String key,boolean focused){boolean active=tab.equals(key);button.setBackground(MarOSUI.border(this,focused?0xFF314664:active?0xB223344E:0x16080D17,focused?MarOSUI.accent(this):active?0x5C91AFDC:0x10000000,10));button.setTextColor(active||focused?WHITE:0xFFB5C1D3);}
    private void buildHero(){
        LinearLayout intro=column();TextView eyebrow=text(8,MarOSUI.accent(this),"sans-serif-medium");eyebrow.setText(greeting());eyebrow.setLetterSpacing(.03f);eyebrow.setVisibility(prefs.getBool("show_greeting",true)?View.VISIBLE:View.GONE);intro.addView(eyebrow);
        TextView headline=text(prefs.theme().equals("minimal")?30:32,WHITE,"sans-serif-light");String custom=prefs.getString("home_title","").trim();headline.setText(custom.isEmpty()?(prefs.theme().equals("minimal")?"L’essentiel, pour vous.":prefs.theme().equals("cinema")?"Votre séance\ncommence ici.":"Votre soirée,\nà votre rythme."):custom);headline.setMaxLines(2);headline.setEllipsize(android.text.TextUtils.TruncateAt.END);headline.setTextSize(Math.min(36,headline.getTextSize()/getResources().getDisplayMetrics().scaledDensity));headline.setLineSpacing(0,1.03f);headline.setShadowLayer(dp(8),0,dp(1),0x77000000);intro.addView(headline,lp(-1,-2,0,10,0,0));
        TextView sub=text(10,0xFFB0BFD5,"sans-serif");sub.setText("Vos films, vos sons, vos moments.");if(prefs.theme().equals("minimal"))sub.setVisibility(View.GONE);intro.addView(sub,lp(-1,-2,0,8,0,0));hero.addView(intro,frame(dp(470),-2,Gravity.TOP,44,145,0,0));
        weather=pill("Choisir ma ville · météo",()->HubActivity.open(this,"weather"));weather.setCompoundDrawables(new MarOSUI.Icon("sun",MarOSUI.accent(this),dp(16)),null,null,null);weather.setCompoundDrawablePadding(dp(8));hero.addView(weather,frame(dp(236),dp(32),Gravity.TOP,44,282,0,0));
        if(prefs.getBool("show_music",true)){MusicPanelView player=new MusicPanelView(this,false);hero.addView(player,frame(dp(384),dp(174),Gravity.TOP|Gravity.RIGHT,0,142,44,0));}
        LinearLayout bottom=column();bottom.setClipChildren(false);bottom.setClipToPadding(false);LinearLayout label=row();label.setGravity(Gravity.CENTER_VERTICAL);title=text(15,WHITE,"sans-serif-medium");title.setText("Vos essentiels");label.addView(title,new LinearLayout.LayoutParams(0,-2,1));TextView hint=text(8,0xFF8B9EBB,"sans-serif");hint.setText("Maintenir OK pour organiser");hint.setLetterSpacing(0);label.addView(hint);bottom.addView(label,lp(-1,-2,0,0,0,12));
        LinearLayout dock=row();dock.setClipChildren(false);dock.setClipToPadding(false);dock.setPadding(dp(7),dp(7),dp(7),dp(5));
        Set<String> hidden=prefs.hidden();int width=tileWidth();for(String pkg:prefs.dock())if(apps.containsKey(pkg)&&!hidden.contains(pkg))dock.addView(tile(apps.get(pkg),width,true),lp(width,-2,0,0,12,0));
        if(dock.getChildCount()==0)dock.addView(pill("Ajouter des favoris",()->startActivity(new Intent(this,SettingsActivity.class).putExtra("section","apps"))));
        dockScroll=strip(dock);bottom.addView(dockScroll,new LinearLayout.LayoutParams(-1,-2));hero.addView(bottom,frame(-1,-2,Gravity.BOTTOM,37,0,37,16));
    }
    private int tileWidth(){String s=prefs.getString("tile_size","normal");return dp(s.equals("small")?112:s.equals("large")?154:132);}
    private List<App> filtered(String category){List<App> out=new ArrayList<>();Set<String> hidden=prefs.hidden();for(App a:apps.values())if(!hidden.contains(a.pkg)&&(category==null||category.equals(prefs.category(a.pkg))))out.add(a);return out;}
    private void appGrid(String name,List<App> list,int columns){
        if(name!=null)heading(content,name,"Appui long pour personnaliser");LinearLayout grid=column();grid.setClipChildren(false);grid.setClipToPadding(false);content.addView(grid,lp(-1,-2,48,name==null?12:0,48,8));int width=(getResources().getDisplayMetrics().widthPixels-dp(96)-dp(18)*(columns-1))/columns;
        for(int i=0;i<list.size();i+=columns){LinearLayout line=row();line.setClipChildren(false);line.setClipToPadding(false);grid.addView(line,lp(-1,-2,0,0,0,18));for(int j=0;j<columns&&i+j<list.size();j++)line.addView(tile(list.get(i+j),width,false),lp(width,-2,0,0,j==columns-1?0:18,0));}
    }
    private View tile(App app,int width,boolean dock){
        LinearLayout box=column();box.setClipChildren(false);box.setClipToPadding(false);FrameLayout card=new FrameLayout(this);card.setId(View.generateViewId());card.setContentDescription(app.label);card.setFocusable(true);card.setBackground(bg(0xFF17243A,14));card.setClipToOutline(true);card.setElevation(dp(2));
        ImageView image=new ImageView(this);image.setScaleType(ImageView.ScaleType.CENTER_CROP);card.addView(image,new FrameLayout.LayoutParams(-1,-1));TextView fallback=text(14,WHITE,"sans-serif-medium");fallback.setText(app.label);fallback.setGravity(Gravity.CENTER);fallback.setPadding(dp(8),dp(8),dp(8),dp(8));card.addView(fallback,new FrameLayout.LayoutParams(-1,-1));box.addView(card,new LinearLayout.LayoutParams(width,width*9/16));
        TextView label=text(10,0xFFC2CEE0,"sans-serif-medium");label.setText(app.label);label.setSingleLine(true);label.setEllipsize(android.text.TextUtils.TruncateAt.END);label.setPadding(dp(2),dp(7),0,0);box.addView(label,new LinearLayout.LayoutParams(width,dp(28)));
        card.setOnClickListener(v->open(app));card.setOnLongClickListener(v->{startActivity(new Intent(this,SettingsActivity.class).putExtra("app_package",app.pkg));return true;});
        card.setOnFocusChangeListener((v,has)->{v.animate().cancel();v.animate().scaleX(has?1.055f:1f).scaleY(has?1.055f:1f).setDuration(anim(135)).setInterpolator(new DecelerateInterpolator()).start();card.setForeground(has?outline():null);label.setTextColor(has?WHITE:0xFFC2CEE0);if(has){selected=app.pkg;selectedInDock=dock;if(dock){if(!restoringFocus)scroll.smoothScrollTo(0,0);}else if(!restoringFocus)reveal(box);saveNav();if(!restoringFocus){handler.removeCallbacks(persistNavigation);handler.postDelayed(persistNavigation,300);}}});
        if(!dock&&!gridViews.containsKey(app.pkg))gridViews.put(app.pkg,card);if(!appViews.containsKey(app.pkg)||dock)appViews.put(app.pkg,card);if(dock&&!tabs.isEmpty())card.setNextFocusUpId(tabs.get(0).getId());
        Bitmap bitmap=cache.get(app.pkg);if(bitmap!=null){image.setImageBitmap(bitmap);fallback.setVisibility(View.GONE);}else{int version=generation;images.execute(()->{Bitmap art=art(app,Math.max(dp(160),width));if(art!=null)cache.put(app.pkg,art);handler.post(()->{if(!isDestroyed()&&version==generation&&art!=null){image.setImageBitmap(art);fallback.setVisibility(View.GONE);}});});}
        return box;
    }
    private Bitmap art(App app,int width){
        Bitmap custom=photos.getBanner(app.pkg,width,width*9/16);if(custom!=null)return custom;
        try{Drawable d=app.resolve.activityInfo.loadBanner(getPackageManager());if(d==null)d=app.resolve.activityInfo.applicationInfo.loadBanner(getPackageManager());boolean banner=d!=null;if(d==null)d=app.resolve.loadIcon(getPackageManager());Bitmap b=Bitmap.createBitmap(width,width*9/16,Bitmap.Config.RGB_565);Canvas c=new Canvas(b);c.drawColor(0xFF17243A);if(d!=null){if(banner)d.setBounds(0,0,b.getWidth(),b.getHeight());else{int side=(int)(b.getHeight()*.72),x=(b.getWidth()-side)/2,y=(b.getHeight()-side)/2;d.setBounds(x,y,x+side,y+side);}d.draw(c);}return b;}catch(RuntimeException|OutOfMemoryError e){return null;}
    }
    private void open(App app){try{selected=app.pkg;saveNav();ContentBridge.recordLaunch(this,app.pkg);startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER).setComponent(new ComponentName(app.pkg,app.resolve.activityInfo.name)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}catch(RuntimeException e){toast("Cette application est momentanément indisponible.");}}
    private void focusSaved(){
        boolean remember=prefs.getBool("remember_navigation",true);View target=remember?(selectedInDock?appViews:gridViews).get(selected):null;
        if(target==null&&!appViews.isEmpty())target=appViews.values().iterator().next();
        if(target==null&&!tabs.isEmpty())target=tabs.get(0);
        int y=remember?savedY:0,x=remember?savedX:0;restoringFocus=true;
        if(target!=null)target.requestFocus();scroll.scrollTo(0,Math.max(0,y));if(dockScroll!=null)dockScroll.scrollTo(Math.max(0,x),0);restoringFocus=false;
    }
    private void reveal(View view){if(scroll==null||view.getParent()==null)return;Rect r=new Rect();view.getDrawingRect(r);scroll.offsetDescendantRectToMyCoords(view,r);if(r.bottom>scroll.getScrollY()+scroll.getHeight()-dp(20))scroll.smoothScrollTo(0,Math.max(0,r.top-dp(90)));else if(r.top<scroll.getScrollY()+dp(20))scroll.smoothScrollTo(0,Math.max(0,r.top-dp(35)));}

    private void quickActions(){if(!prefs.getBool("show_info",true))return;
        actionRow("À portée de main",new String[]{"Mon téléphone","Mes photos","Minuteur","Plus"},new Runnable[]{()->HubActivity.open(this,"phone"),()->startActivity(new Intent(this,SettingsActivity.class).putExtra("section","photos")),()->HubActivity.open(this,"timer"),()->HubActivity.open(this,"more")});
        JSONArray shortcuts=hub.shortcuts();if(shortcuts.length()>0){heading(content,"Vos raccourcis",null);LinearLayout line=row();for(int i=0;i<shortcuts.length();i++){JSONObject o=shortcuts.optJSONObject(i);if(o!=null)line.addView(pill(o.optString("label","Raccourci"),()->hub.runShortcut(this,o)),lp(-2,dp(43),0,0,12,0));}addStrip(content,line);}

    }
    private void actionRow(String name,String[] labels,Runnable[] actions){heading(content,name,null);LinearLayout line=row();for(int i=0;i<labels.length;i++)line.addView(pill(labels[i],actions[i]),lp(-2,dp(43),0,0,12,0));addStrip(content,line);}
    private void addStrip(LinearLayout parent,LinearLayout line){parent.addView(strip(line),lp(-1,-2,48,0,0,20));}
    private HorizontalScrollView strip(LinearLayout line){HorizontalScrollView strip=new HorizontalScrollView(this);strip.setFocusable(false);strip.setHorizontalScrollBarEnabled(false);strip.setOverScrollMode(View.OVER_SCROLL_NEVER);strip.addView(line);return strip;}
    private void loadDynamic(int version){
        if(dynamicRows==null)return;final LinearLayout parent=dynamicRows;images.execute(()->{
            ContentBridge bridge=new ContentBridge(this);List<ContentBridge.Item> shared=bridge.load();
            handler.post(()->{if(isDestroyed()||generation!=version)return;parent.removeAllViews();
                LinearLayout head=row();head.setGravity(Gravity.CENTER_VERTICAL);TextView h=text(19,WHITE,"sans-serif-medium");h.setText("Vos dernières lectures");head.addView(h,new LinearLayout.LayoutParams(0,-2,1));head.addView(pill("Tout l’historique",()->HubActivity.open(this,"history")),lp(-2,dp(34),0,0,0,0));parent.addView(head,lp(-1,-2,44,14,44,6));
                TextView detail=text(9,MUTED,"sans-serif");detail.setText("Du plus récent au plus ancien · Lectures des 30 derniers jours");parent.addView(detail,lp(-1,-2,44,0,44,15));
                if(!shared.isEmpty()){LinearLayout line=row();line.setClipChildren(false);line.setClipToPadding(false);line.setPadding(dp(4),dp(5),0,dp(7));for(ContentBridge.Item i:shared){ResumeCardView card=new ResumeCardView(this,i,()->{if(!bridge.open(i))toast("Ouvrez ce contenu depuis son application.");},(v,has)->{if(has)reveal(v);});card.setOnLongClickListener(v->{new AlertDialog.Builder(this).setTitle(i.title).setItems(new String[]{"Masquer cette série dans MarOS","Voir l’historique et ses sources"},(d,n)->{if(n==0){bridge.hide(i);loadDynamic(generation);}else HubActivity.open(this,"history");}).setNegativeButton("Annuler",null).show();return true;});line.addView(card,lp(dp(210),dp(160),0,0,14,0));}addStrip(parent,line);}
                else{LinearLayout empty=column();empty.setPadding(dp(24),dp(20),dp(24),dp(20));empty.setBackground(MarOSUI.border(this,0xBF111B2C,0x284A648B,16));TextView t=text(16,WHITE,"sans-serif-medium");t.setText("Votre prochain épisode commence ici.");empty.addView(t);TextView note=text(11,MUTED,"sans-serif");note.setText("Les lectures datées apparaîtront ici à mesure que vos services les partageront.");empty.addView(note,lp(-1,-2,0,8,0,0));parent.addView(empty,lp(-1,-2,44,0,44,20));}
            });
        });
    }
    private void clearMediaCallbacks(){for(Runnable cleanup:mediaCleanup)cleanup.run();mediaCleanup.clear();}
    private void watchMedia(MediaBridge.NowPlaying p,TextView name,TextView artist,TextView playback,ImageView cover){
        if(p.controller==null)return;
        android.media.session.MediaController.Callback callback=new android.media.session.MediaController.Callback(){
            @Override public void onMetadataChanged(android.media.MediaMetadata m){
                if(m==null)return;String t=m.getString(android.media.MediaMetadata.METADATA_KEY_DISPLAY_TITLE);if(t==null)t=m.getString(android.media.MediaMetadata.METADATA_KEY_TITLE);if(t!=null)name.setText(t);
                String s=m.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST);if(s==null)s=m.getString(android.media.MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE);artist.setText(s==null?"":s);
                Bitmap b=m.getBitmap(android.media.MediaMetadata.METADATA_KEY_DISPLAY_ICON);if(b==null)b=m.getBitmap(android.media.MediaMetadata.METADATA_KEY_ART);if(b==null)b=m.getBitmap(android.media.MediaMetadata.METADATA_KEY_ALBUM_ART);cover.setImageBitmap(b);cover.setVisibility(b==null?View.GONE:View.VISIBLE);
            }
            @Override public void onPlaybackStateChanged(android.media.session.PlaybackState state){if(state!=null)playback.setText(state.getState()==android.media.session.PlaybackState.STATE_PLAYING||state.getState()==android.media.session.PlaybackState.STATE_BUFFERING?"Pause":"Lecture");}
        };
        try{p.controller.registerCallback(callback,handler);mediaCleanup.add(()->{try{p.controller.unregisterCallback(callback);}catch(RuntimeException ignored){}});}catch(RuntimeException ignored){}
    }
    private void heading(LinearLayout parent,String name,String detail){LinearLayout row=row();row.setGravity(Gravity.CENTER_VERTICAL);TextView t=text(16,WHITE,"sans-serif-medium");t.setText(name);row.addView(t,new LinearLayout.LayoutParams(0,-2,1));if(detail!=null){TextView d=text(10,0xFFA3B0C7,"sans-serif");d.setText(detail);row.addView(d);}parent.addView(row,lp(-1,-2,48,24,48,16));}
    private void empty(){LinearLayout box=column();TextView t=text(23,WHITE,"sans-serif-light");t.setText("Votre collection est prête");box.addView(t);TextView sub=text(13,MUTED,"sans-serif");sub.setText("Un appui long sur une application permet de changer sa collection.");box.addView(sub,lp(-1,-2,0,12,0,0));box.addView(pill("Organiser mes applis",()->startActivity(new Intent(this,SettingsActivity.class).putExtra("section","apps"))),lp(-2,dp(42),0,22,0,0));content.addView(box,lp(-1,-2,48,30,48,40));}
    private String greeting(){int h=Calendar.getInstance().get(Calendar.HOUR_OF_DAY);return h<5?"BONNE NUIT":h<12?"BONJOUR":h<18?"BON APRÈS-MIDI":"BONSOIR";}
    private void updateHeader(){if(clock==null)return;updateNetwork();java.util.Date now=new java.util.Date();clock.setText(new SimpleDateFormat(prefs.getBool("clock_24h",true)?"HH:mm":"h:mm a",Locale.FRANCE).format(now));clock.setVisibility(prefs.getBool("show_clock",true)?View.VISIBLE:View.GONE);date.setText(new SimpleDateFormat("EEEE d MMMM",Locale.FRANCE).format(now));if(weather!=null){weather.setText(hub.weatherSummary());weather.setVisibility(prefs.getBool("show_weather",true)?View.VISIBLE:View.GONE);}List<String> bits=new ArrayList<>();if(prefs.getBool("show_storage",true))bits.add(String.format(Locale.FRANCE,"%.1f Go libres",new StatFs(getFilesDir().getAbsolutePath()).getAvailableBytes()/1e9));if(prefs.getBool("show_calendar",true)){String event=hub.nextEventSummary();if(event!=null&&!event.isEmpty())bits.add(event);}if(prefs.getBool("show_timer",true)){HubActivity.timerSummary(this);long remaining=prefs.prefs().getLong("timer_deadline",0)-System.currentTimeMillis();if(remaining>0)bits.add("Minuteur "+((remaining+59999)/60000)+" min");}info.setText(android.text.TextUtils.join("   ·   ",bits));info.setVisibility(prefs.getBool("show_info",true)?View.VISIBLE:View.GONE);}

    private void updateNetwork(){if(networkButton==null)return;String kind="offline";try{android.net.ConnectivityManager cm=(android.net.ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);android.net.NetworkCapabilities caps=cm==null?null:cm.getNetworkCapabilities(cm.getActiveNetwork());if(caps!=null)kind=caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET)?"ethernet":"wifi";}catch(RuntimeException ignored){}networkButton.setCompoundDrawables(new MarOSUI.Icon(kind,MarOSUI.TEXT,dp(20)),null,null,null);networkButton.setContentDescription("Réseau et Wi-Fi · "+(kind.equals("wifi")?"Wi-Fi connecté":kind.equals("ethernet")?"Ethernet connecté":"Déconnecté"));}

    private ImageView wall(){ImageView v=new ImageView(this);v.setScaleType(ImageView.ScaleType.CENTER_CROP);return v;}
    private long photoInterval(){return Math.max(1,prefs.getInt("wallpaper_minutes",10))*60000L;}
    private void showPhoto(boolean force){
        handler.removeCallbacks(rotate);List<PhotoStore.Photo> album=photos.list();if(album.isEmpty())return;String requested=prefs.getString("next_photo_id","");String last=prefs.getString("last_photo_id","");long then=prefs.prefs().getLong("last_photo_time",0);int index=0;
        for(int i=0;i<album.size();i++)if(album.get(i).id.equals(last)){index=i;break;}
        boolean next=requested.startsWith("next:");if(next||force||requested.isEmpty()&&!last.isEmpty()&&System.currentTimeMillis()-then>=photoInterval())index=(index+1)%album.size();else if(!requested.isEmpty())for(int i=0;i<album.size();i++)if(album.get(i).id.equals(requested)){index=i;break;}
        if(!requested.isEmpty())prefs.prefs().edit().remove("next_photo_id").apply();final PhotoStore.Photo photo=album.get(index);final int version=generation;
        if(!photo.id.equals(photoId))photoWorker.execute(()->{Bitmap b=photos.load(photo,1920,1080);handler.post(()->{if(isDestroyed()||version!=generation||b==null)return;ImageView old=front;front=back;back=old;FrameLayout parent=(FrameLayout)front.getParent();parent.removeView(front);parent.addView(front,1,new FrameLayout.LayoutParams(-1,-1));front.setImageBitmap(b);front.setAlpha(photoId.isEmpty()?1f:0f);ImageView previous=back;if(front.getAlpha()<1f)front.animate().alpha(1f).setDuration(anim(1400)).withEndAction(()->previous.setImageDrawable(null)).start();else previous.setImageDrawable(null);photoId=photo.id;if(place!=null){place.setText(photo.title);place.setVisibility(prefs.getBool("show_photo_credit",true)?View.VISIBLE:View.GONE);}Log.i("MarOS","Wallpaper "+photo.id+" "+b.getWidth()+"x"+b.getHeight());});});
        if(force||!requested.isEmpty()||!photo.id.equals(last))prefs.prefs().edit().putString("last_photo_id",photo.id).putLong("last_photo_time",System.currentTimeMillis()).apply();schedulePhoto();
    }
    private void schedulePhoto(){handler.removeCallbacks(rotate);if(visible){long since=Math.max(0,System.currentTimeMillis()-prefs.prefs().getLong("last_photo_time",System.currentTimeMillis()));handler.postDelayed(rotate,Math.max(1000,photoInterval()-since));}}
    private void resetAmbient(){handler.removeCallbacks(ambient);if(visible&&prefs!=null&&prefs.getInt("ambient_minutes",5)>0)handler.postDelayed(ambient,prefs.getInt("ambient_minutes",5)*60000L);}
    private void homeMenu(){MarOSUI.choose(this,"Votre espace MarOS","Les raccourcis qui simplifient votre quotidien.",new String[]{"Photo suivante","Choisir mes photos","Personnaliser l’accueil","Mode ambiant","Régler la télécommande","Réglages Android"},n->{if(n==0)showPhoto(true);else if(n==1)startActivity(new Intent(this,SettingsActivity.class).putExtra("section","photos"));else if(n==2)startActivity(new Intent(this,SettingsActivity.class));else if(n==3)startActivity(new Intent(this,AmbientActivity.class));else if(n==4)startActivity(new Intent(this,RemoteBridgeActivity.class));else safe(new Intent(Settings.ACTION_SETTINGS));});}
    private void checkPhone(){long fired=prefs.prefs().getLong("timer_last_fired",0);if(fired>prefs.prefs().getLong("timer_last_ack",0)){prefs.prefs().edit().putLong("timer_last_ack",fired).apply();new AlertDialog.Builder(this).setTitle("Votre minuteur est terminé").setMessage("Le rappel que vous avez choisi vient de se terminer.").setPositiveButton("Fermer",null).show();return;}String query=prefs.getString("phone_pending_search","");String link=prefs.getString("phone_pending_link","");if(!query.isEmpty()){prefs.prefs().edit().remove("phone_pending_search").apply();startActivity(new Intent(this,HubActivity.class).putExtra("section","search").putExtra("query",query));}else if(!link.isEmpty()){prefs.prefs().edit().remove("phone_pending_link").apply();new AlertDialog.Builder(this).setTitle("Lien envoyé depuis votre téléphone").setMessage(link).setPositiveButton("Ouvrir",(d,w)->{android.net.Uri u=android.net.Uri.parse(link);if("http".equals(u.getScheme())||"https".equals(u.getScheme()))safe(new Intent(Intent.ACTION_VIEW,u));}).setNegativeButton("Annuler",null).show();}}
    private void safe(Intent i){try{startActivity(i);}catch(RuntimeException e){toast("Cette action n’est pas disponible sur cette box.");}}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    private TextView pill(String label,Runnable action){TextView v=text(10,WHITE,"sans-serif-medium");v.setText(MarOSUI.cleanLabel(label));v.setGravity(Gravity.CENTER_VERTICAL);v.setPadding(dp(13),dp(7),dp(13),dp(7));v.setCompoundDrawables(new MarOSUI.Icon(MarOSUI.iconFor(label),MarOSUI.accent(this),dp(16)),null,null,null);v.setCompoundDrawablePadding(dp(8));v.setFocusable(true);v.setId(View.generateViewId());v.setBackground(MarOSUI.border(this,0xA0111B2C,0x285A7195,11));v.setOnFocusChangeListener((w,has)->{v.setBackground(MarOSUI.border(this,has?0xFF2A405D:0xA0111B2C,has?MarOSUI.accent(this):0x285A7195,11));v.setTextColor(WHITE);if(has)reveal(v);});v.setOnClickListener(w->action.run());return v;}
    private TextView text(float size,int color,String font){TextView v=new TextView(this);v.setTextSize(size*Math.max(85,Math.min(140,prefs==null?100:prefs.getInt("text_scale",100)))/100f);v.setTextColor(color);v.setTypeface(MarOSUI.type(this,font.contains("medium")));return v;}
    private int anim(int millis){return prefs.getBool("reduced_animations",false)?0:millis;}
    private GradientDrawable bg(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private GradientDrawable outline(){GradientDrawable d=bg(Color.TRANSPARENT,12);d.setStroke(dp(2),MarOSUI.accent(this));return d;}
    private LinearLayout row(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.HORIZONTAL);return v;}
    private LinearLayout column(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private LinearLayout.LayoutParams lp(int width,int height,int l,int t,int r,int b){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(width,height);p.setMargins(dp(l),dp(t),dp(r),dp(b));return p;}
    private FrameLayout.LayoutParams frame(int width,int height,int gravity,int l,int t,int r,int b){FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(width,height,gravity);p.setMargins(dp(l),dp(t),dp(r),dp(b));return p;}
    private static final class StableScroll extends ScrollView{StableScroll(Context c){super(c);setOverScrollMode(View.OVER_SCROLL_NEVER);}@Override protected int computeScrollDeltaToGetChildRectOnScreen(Rect r){return 0;}}
}
