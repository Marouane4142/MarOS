package io.github.marouane4142.maros;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import java.util.Locale;

/** Shared native design system. Icons are paths, never font glyphs. */
public final class MarOSUI {
    public static final int BG=0xFF090D15,PANEL=0xFF161D2A,SURFACE=0xFF202B3B,TEXT=0xFFF5F8FC,MUTED=0xFFA1AFBF;
    private static android.content.res.Resources resources;
    private static Typeface regular,medium;
    public static Typeface type(Context c,boolean bold){prepare(c);return bold?medium:regular;}
    private static void prepare(Context c){if(resources==null)resources=c.getApplicationContext().getResources();if(regular==null){try{regular=new Typeface.Builder(c.getAssets(),"fonts/Manrope.ttf").setFontVariationSettings("'wght' 450").build();medium=new Typeface.Builder(c.getAssets(),"fonts/Manrope.ttf").setFontVariationSettings("'wght' 650").build();}catch(Exception ignored){regular=Typeface.DEFAULT;medium=Typeface.DEFAULT_BOLD;}}}
    public static int dp(Context c,float n){prepare(c);return Math.round(n*c.getResources().getDisplayMetrics().density);}
    public static int accent(Context c){String key=new LauncherPrefs(c).getString("accent","ice");return key.equals("sage")?0xFFADE2CE:key.equals("rose")?0xFFF0B6C4:key.equals("amber")?0xFFF1CEA1:0xFF87DCF5;}
    public static GradientDrawable surface(Context c,int color,float radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(c,radius));return d;}
    public static GradientDrawable border(Context c,int fill,int stroke,float radius){GradientDrawable d=surface(c,fill,radius);d.setStroke(dp(c,1),stroke);return d;}
    public static GradientDrawable background(){return new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xFF172333,0xFF0D131F,0xFF090D15});}
    public static TextView text(Context c,String value,float size,int color){TextView t=new TextView(c);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setTypeface(type(c,false));t.setIncludeFontPadding(false);return t;}
    public static LinearLayout brand(Context c,boolean subtitle){LinearLayout row=new LinearLayout(c);row.setGravity(Gravity.CENTER_VERTICAL);ImageView logo=new ImageView(c);logo.setImageDrawable(new Icon("mark",accent(c),dp(c,36)));row.addView(logo,new LinearLayout.LayoutParams(dp(c,36),dp(c,36)));LinearLayout words=new LinearLayout(c);words.setOrientation(1);words.setPadding(dp(c,9),0,0,0);TextView name=text(c,"MarOS",23,TEXT);name.setTypeface(type(c,true));name.setLetterSpacing(-.03f);name.setSingleLine(true);words.addView(name,new LinearLayout.LayoutParams(-1,-2));if(subtitle){TextView sub=text(c,"Chez vous.",8,MUTED);sub.setLetterSpacing(.02f);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,-2);p.topMargin=dp(c,4);words.addView(sub,p);}row.addView(words,new LinearLayout.LayoutParams(0,-2,1));return row;}
    public static String iconFor(String label){String s=label.toLowerCase(Locale.ROOT);if(s.contains("play store"))return "store";if(s.contains("plus")||s.contains("avancé"))return "more";if(s.contains("wifi")||s.contains("wi-fi")||s.contains("réseau"))return "wifi";if(s.contains("cherch")||s.contains("recherch")||s.contains("dicter"))return "search";if(s.contains("ambiant")||s.contains("caméra")||s.contains("photo")||s.contains("bannière")||s.contains("album"))return "photo";if(s.contains("couleur")||s.contains("taille")||s.contains("ombre")||s.contains("animation")||s.contains("personnal")||s.contains("style")||s.contains("thème")||s.contains("apparence"))return "sliders";if(s.contains("application")||s.contains("appli")||s.contains("favori"))return "apps";if(s.contains("musique")||s.contains("spotify"))return "music";if(s.contains("jeu")||s.contains("moonlight")||s.contains("pc"))return "game";if(s.contains("agenda")||s.contains("rendez"))return "calendar";if(s.contains("minute")||s.contains("durée"))return "timer";if(s.contains("téléphone")||s.contains("compagnon")||s.contains("appair"))return "phone";if(s.contains("télécommande"))return "remote";if(s.contains("météo")||s.contains("prévision"))return "sun";if(s.contains("sauvegard")||s.contains("export")||s.contains("import"))return "save";if(s.contains("retour")||s.contains("accueil"))return "home";if(s.contains("raccourci")||s.contains("connex")||s.contains("maison"))return "link";if(s.contains("historique")||s.contains("récent")||s.contains("reprendre"))return "history";if(s.contains("regarder")||s.contains("bibliothèque"))return "play";if(s.contains("envie"))return "spark";if(s.contains("horloge")||s.contains("heure"))return "clock";if(s.contains("masquer"))return "eye";if(s.contains("supprim")||s.contains("effacer"))return "trash";if(s.contains("ajout")||s.contains("nouveau"))return "plus";return "settings";}
    public static String cleanLabel(String s){return s.replaceAll("^[＋+‹⌕▧◷◈⌨⚙✧▣◉⌘☀★○✓]+\\s*","").trim();}
    public static Button button(Context c,String label,String icon,Runnable action){Button b=new Button(c);String clean=cleanLabel(label);android.text.SpannableString styled=new android.text.SpannableString(clean);int split=clean.indexOf("\n");if(split>=0){styled.setSpan(new android.text.style.RelativeSizeSpan(.83f),split+1,clean.length(),33);styled.setSpan(new android.text.style.ForegroundColorSpan(MUTED),split+1,clean.length(),33);}b.setText(styled);b.setTag(clean.split("\n",2)[0]);b.setAllCaps(false);b.setTextSize(13);b.setTypeface(type(c,true));b.setTextColor(TEXT);b.setGravity(Gravity.CENTER_VERTICAL|Gravity.LEFT);b.setPadding(dp(c,16),dp(c,9),dp(c,16),dp(c,9));b.setMinHeight(dp(c,48));b.setMinimumHeight(dp(c,48));b.setCompoundDrawablePadding(dp(c,12));b.setCompoundDrawables(new Icon(icon,accent(c),dp(c,20)),null,null,null);b.setBackground(border(c,PANEL,Color.TRANSPARENT,14));b.setFocusable(true);b.setOnClickListener(v->action.run());focus(b);return b;}
    public static void focus(View view){view.setOnFocusChangeListener((v,has)->{Context c=v.getContext();v.animate().cancel();v.setBackground(border(c,has?0xFF2A3C50:PANEL,has?accent(c):Color.TRANSPARENT,v instanceof IconButton?99:14));v.setElevation(dp(c,has?4:0));if(v instanceof TextView)((TextView)v).setTextColor(TEXT);});}
    public static void navState(Button b,boolean selected){Context c=b.getContext();b.setBackground(surface(c,selected?0xFF233447:Color.TRANSPARENT,14));b.setTextColor(selected?TEXT:MUTED);}
    public static TextView iconButton(Context c,String type,String description,Runnable click){IconButton v=new IconButton(c);v.setCompoundDrawables(new Icon(type,TEXT,dp(c,20)),null,null,null);v.setFocusable(true);v.setContentDescription(description);v.setTooltipText(description);v.setBackground(surface(c,0xB0161D2A,99));v.setOnClickListener(w->click.run());focus(v);return v;}
    /** Compound drawables otherwise align to the left even when an empty TextView is centered. */
    public static final class IconButton extends TextView {
        public IconButton(Context c){super(c);prepare(c);setGravity(Gravity.CENTER);setIncludeFontPadding(false);}
        @Override protected void onDraw(Canvas canvas){Drawable d=getCompoundDrawables()[0];if(d==null)return;int w=d.getBounds().width(),h=d.getBounds().height();int save=canvas.save();canvas.translate((getWidth()-w)/2f,(getHeight()-h)/2f);d.draw(canvas);canvas.restoreToCount(save);}
        @Override public CharSequence getAccessibilityClassName(){return "android.widget.Button";}
    }
    public static void choose(Context c,String title,String subtitle,String[] labels,java.util.function.IntConsumer selected){
        android.app.Dialog dialog=new android.app.Dialog(c);LinearLayout panel=new LinearLayout(c);panel.setOrientation(1);panel.setPadding(dp(c,24),dp(c,24),dp(c,24),dp(c,18));panel.setBackground(border(c,0xFF111B2C,0x486883AA,22));
        panel.addView(brand(c,false));TextView heading=text(c,title,24,TEXT);heading.setTypeface(type(c,true));LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,-2);hp.topMargin=dp(c,14);panel.addView(heading,hp);
        if(subtitle!=null&&!subtitle.isEmpty()){TextView sub=text(c,subtitle,11,MUTED);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,-2);sp.setMargins(0,dp(c,8),0,dp(c,14));panel.addView(sub,sp);}
        ScrollView scroll=new ScrollView(c);scroll.setVerticalScrollBarEnabled(false);LinearLayout choices=new LinearLayout(c);choices.setOrientation(1);scroll.addView(choices);panel.addView(scroll,new LinearLayout.LayoutParams(-1,dp(c,Math.min(320,labels.length*54))));Button first=null;
        for(int i=0;i<labels.length;i++){final int at=i;Button b=button(c,labels[i],iconFor(labels[i]),()->{dialog.dismiss();selected.accept(at);});LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(c,48));lp.setMargins(0,dp(c,3),0,dp(c,3));choices.addView(b,lp);if(first==null)first=b;}
        Button close=button(c,"Fermer","back",dialog::dismiss);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,dp(c,42));cp.topMargin=dp(c,12);panel.addView(close,cp);dialog.setContentView(panel);android.view.Window window=dialog.getWindow();if(window!=null){window.setBackgroundDrawableResource(android.R.color.transparent);window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);WindowManager.LayoutParams a=window.getAttributes();a.dimAmount=.62f;window.setAttributes(a);}dialog.show();if(window!=null)window.setLayout(Math.min(dp(c,520),c.getResources().getDisplayMetrics().widthPixels-dp(c,80)),-2);if(first!=null)first.requestFocus();
    }

    public static View tagged(View root,Object tag){if(root==null||tag==null)return null;if(tag.equals(root.getTag())&&root.isFocusable())return root;if(root instanceof ViewGroup){ViewGroup group=(ViewGroup)root;for(int i=0;i<group.getChildCount();i++){View found=tagged(group.getChildAt(i),tag);if(found!=null)return found;}}return null;}

    public static final class Icon extends Drawable {
        private final Drawable vector;private final int size;
        public Icon(String kind,int color,int size){this.size=size;if(resources==null)throw new IllegalStateException("Initialize MarOSUI with a context");vector=resources.getDrawable(IconRegistry.resource(kind),null).mutate();vector.setTint(color);setBounds(0,0,size,size);}
        @Override protected void onBoundsChange(Rect bounds){vector.setBounds(bounds);}
        @Override public void draw(Canvas c){vector.draw(c);}
        @Override public void setAlpha(int a){vector.setAlpha(a);invalidateSelf();}
        @Override public void setColorFilter(ColorFilter filter){vector.setColorFilter(filter);invalidateSelf();}
        @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
        @Override public int getIntrinsicWidth(){return size;}@Override public int getIntrinsicHeight(){return size;}
    }
}
