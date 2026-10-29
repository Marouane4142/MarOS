package io.github.marouane4142.maros;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.database.Cursor;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** A real DPAD-friendly settings surface. All changes are local and immediately reversible. */
public final class SettingsActivity extends Activity {
    private static final int BG=MarOSUI.BG, PANEL=MarOSUI.PANEL, TEXT=MarOSUI.TEXT, MUTED=MarOSUI.MUTED;
    private static final int PICK_PHOTOS=80,PICK_BANNER=81,EXPORT=82,IMPORT=83;
    private LauncherPrefs prefs;
    private PhotoStore photos;
    private LinearLayout content, navigation;
    private ScrollView pageScroll;
    private TextView heading, description;
    private String section="overview", bannerPackage="";
    private ExecutorService worker=Executors.newSingleThreadExecutor();
    private volatile int pageGeneration=0;
    private final Map<String,String> appNames=new HashMap<>();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state); prefs=new LauncherPrefs(this); photos=new PhotoStore(this);
        if(state!=null) { section=state.getString("section","overview"); bannerPackage=state.getString("bannerPackage",""); }
        else if(getIntent().hasExtra("section")) section=getIntent().getStringExtra("section");
        buildFrame(); showSection(section);
        String pkg=getIntent().getStringExtra("app_package"); if(LauncherPrefs.validPackage(pkg)) content.post(()->appActions(pkg));
    }
    @Override protected void onSaveInstanceState(Bundle state) { super.onSaveInstanceState(state); state.putString("section",section); state.putString("bannerPackage",bannerPackage); }
    @Override protected void onDestroy() { worker.shutdownNow(); super.onDestroy(); /* Views own decoded thumbnails until Android releases the activity. */ }
    private int accent(){return MarOSUI.accent(this);}
    private void buildFrame() {
        getWindow().getDecorView().setSystemUiVisibility(5894);
        LinearLayout root=new LinearLayout(this);root.setOrientation(0);root.setBackground(MarOSUI.background());root.setPadding(dp(28),dp(22),dp(30),dp(22));
        navigation=new LinearLayout(this);navigation.setOrientation(1);navigation.setPadding(dp(12),dp(8),dp(12),dp(12));navigation.setBackground(MarOSUI.border(this,0xB9111B2C,0x1C6783AC,20));root.addView(navigation,new LinearLayout.LayoutParams(dp(216),-1));
        navigation.addView(MarOSUI.brand(this,true),new LinearLayout.LayoutParams(-1,dp(70)));
        nav("À votre façon","overview");nav("Applications","apps");nav("Photos & ambiance","photos");nav("Mon téléphone","phone");nav("Avancé","advanced");
        View spacer=new View(this);navigation.addView(spacer,new LinearLayout.LayoutParams(1,0,1));Button back=button("Retour à l’accueil",()->finish());navigation.addView(back,new LinearLayout.LayoutParams(-1,dp(44)));
        LinearLayout right=new LinearLayout(this);right.setOrientation(1);right.setPadding(dp(30),dp(7),0,0);root.addView(right,new LinearLayout.LayoutParams(0,-1,1));
        heading=text("Réglages",28,TEXT);heading.setTypeface(MarOSUI.type(this,true));LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,-2);hp.topMargin=dp(12);right.addView(heading,hp);description=text("",11,MUTED);description.setPadding(0,dp(7),0,dp(20));right.addView(description);
        ScrollView scroll=new ScrollView(this);pageScroll=scroll;scroll.setVerticalScrollBarEnabled(false);scroll.setClipToPadding(false);scroll.setPadding(0,dp(3),dp(5),dp(18));right.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));content=new LinearLayout(this);content.setOrientation(1);scroll.addView(content,new ScrollView.LayoutParams(-1,-2));setContentView(root);
    }
    private void nav(String label,String id){Button b=button(label,()->showSection(id));b.setTag(id);b.setTextSize(12);b.setPadding(dp(12),0,dp(10),0);b.setCompoundDrawables(new MarOSUI.Icon(MarOSUI.iconFor(label),accent(),dp(18)),null,null,null);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(45));lp.setMargins(0,dp(3),0,dp(3));navigation.addView(b,lp);b.setOnFocusChangeListener((v,has)->{MarOSUI.navState(b,section.equals(id));if(has)b.setBackground(MarOSUI.border(this,0xFF2B405E,accent(),12));});}
    private void showSection(String id) {
        Object restoreTag=getCurrentFocus()==null?null:getCurrentFocus().getTag();String target=id==null?"overview":id;boolean different=!target.equals(section);section=target;pageGeneration++; content.removeAllViews();
        for(int i=0;i<navigation.getChildCount();i++) { View child=navigation.getChildAt(i); if(child instanceof Button) MarOSUI.navState((Button)child,section.equals(child.getTag())); }
        switch(section) { case "overview":overviewPage();break;case "advanced":advancedPage();break;case "phone":title("Votre téléphone, votre télé.","Le clavier et les photos passent par votre téléphone.");action("Connecter mon téléphone",()->HubActivity.open(this,"phone"));break;case "apps":appsPage();break;case "photos":photosPage();break;case "widgets":widgetsPage();break;case "connections":connectionsPage();break;case "backup":backupPage();break;case "appearance":appearancePage();break;default:overviewPage();break; }
        if(different&&pageScroll!=null)pageScroll.post(()->pageScroll.scrollTo(0,0));else{View restore=MarOSUI.tagged(content,restoreTag);if(restore!=null)restore.requestFocus();}
    }
    private void title(String h,String d) { heading.setText(h); description.setText(d); }
    private void overviewPage(){
        title("Composez votre ambiance.","Un choix, et tout s’accorde. Les réglages fins restent dans Avancé.");
        LinearLayout row=new LinearLayout(this);String[] keys={"nature","cinema","minimal"},names={"Nature\nLumière & paysages","Cinéma\nUne soirée immersive","Minimal\nDe l’espace pour l’essentiel"};int[] colors={0xFF204A49,0xFF293953,0xFF333741};
        for(int i=0;i<keys.length;i++){final String key=keys[i];Button b=button(names[i]+(prefs.theme().equals(key)?" · Actuel":""),()->{prefs.prefs().edit().putString("theme",key).putString("accent",key.equals("nature")?"sage":"ice").putInt("contrast",key.equals("cinema")?60:key.equals("minimal")?65:45).putString("tile_size","normal").apply();prefs.changed();showSection("overview");toast("Ambiance "+key+" appliquée");});b.setSelected(prefs.theme().equals(key));b.setGravity(Gravity.BOTTOM|Gravity.LEFT);b.setPadding(dp(16),dp(12),dp(10),dp(18));b.setCompoundDrawables(new MarOSUI.Icon(key.equals("nature")?"photo":key.equals("cinema")?"play":"spark",TEXT,dp(26)),null,null,null);GradientDrawable bg=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{colors[i],0xFF121B29});bg.setCornerRadius(dp(18));bg.setStroke(dp(prefs.theme().equals(key)?2:0),accent());b.setBackground(bg);b.setOnFocusChangeListener((v,has)->{bg.setStroke(dp(has||prefs.theme().equals(key)?2:0),has?TEXT:accent());});LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(120),1);p.rightMargin=dp(i==2?0:12);row.addView(b,p);}content.addView(row);
        label("VOS ESSENTIELS");action("Organiser mes applications",()->showSection("apps"));action("Choisir mes photos",()->showSection("photos"));action("Connecter mon téléphone",()->HubActivity.open(this,"phone"));action("Personnaliser mon message d’accueil",this::editHomeTitle);
    }
    private void advancedPage(){title("Les détails, si vous le souhaitez.","Votre configuration est déjà prête. Ces réglages restent facultatifs.");action("À propos et licences",this::aboutLicenses);action("Style, couleurs et lisibilité",()->showSection("appearance"));action("Informations et widgets",()->showSection("widgets"));action("Connexions et appareils",()->showSection("connections"));action("Sauvegarde et restauration",()->showSection("backup"));action("Boutons de la télécommande",()->startActivity(new Intent(this,RemoteBridgeActivity.class)));action("Réglages Android",()->safeStart(new Intent(Settings.ACTION_SETTINGS)));}
    private void aboutLicenses(){String[] labels={"MarOS · licence MIT","tv-home · notice d’origine","Lucide · ISC / MIT","Manrope · SIL OFL 1.1","QR Code · Nayuki MIT","Paysages · auteurs et licences"};String[] files={"MarOS-MIT.txt","upstream-tv-home.txt","lucide.txt","Manrope-OFL.txt","qrcode-generator.txt","wallpaper-credits.txt"};MarOSUI.choose(this,"MarOS 4.0.0-beta.1","Dérivé de tv-home par Mihir Gupta · contributions MarOS par Marouane4142.",labels,index->{try(java.io.InputStream in=getAssets().open("licenses/"+files[index]);java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream()){byte[] buffer=new byte[4096];int n;while((n=in.read(buffer))!=-1)bytes.write(buffer,0,n);TextView body=text(new String(bytes.toByteArray(),java.nio.charset.StandardCharsets.UTF_8),12,TEXT);body.setPadding(dp(22),dp(12),dp(22),dp(12));ScrollView scroll=new ScrollView(this);scroll.addView(body);new AlertDialog.Builder(this).setTitle(labels[index]).setView(scroll).setPositiveButton("Fermer",null).show();}catch(Exception e){error(e);}});}
    private void appearancePage() {
        title("Un espace à votre image.","Choisissez l’atmosphère, les couleurs et les détails qui font la différence.");
        LinearLayout previews=new LinearLayout(this);String[] themes={"nature","cinema","minimal"},names={"Nature","Cinéma","Minimal"};int[] colors={0xFF285D69,0xFF343050,0xFF2D3B50};
        for(int i=0;i<themes.length;i++){final String key=themes[i];Button b=button(names[i]+(prefs.theme().equals(key)?"  ·  Actuel":""),()->{prefs.putString("theme",key);showSection("appearance");});b.setCompoundDrawables(null,null,null,null);b.setGravity(Gravity.BOTTOM|Gravity.LEFT);GradientDrawable preview=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{colors[i],0xFF101A2C});preview.setCornerRadius(dp(14));preview.setStroke(dp(1),prefs.theme().equals(key)?accent():0x334E6584);b.setBackground(preview);b.setOnFocusChangeListener((v,has)->{preview.setStroke(dp(has?2:1),has?accent():prefs.theme().equals(key)?accent():0x334E6584);});LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(0,dp(68),1);pp.setMargins(0,0,dp(i==2?0:10),dp(16));previews.addView(b,pp);}content.addView(previews);
        choice("Couleur de signature","accent",new String[]{"ice","sage","rose","amber"},new String[]{"Bleu glacier","Sauge","Rose poudré","Ambre"},"ice");
        action("Message d’accueil personnalisé",this::editHomeTitle);
        choice("Thème visuel","theme",new String[]{"nature","cinema","minimal"},new String[]{"Nature · lumineux et apaisant","Cinéma · profond et discret","Minimal · l’essentiel"},"nature");
        choice("Taille des vignettes","tile_size",new String[]{"small","normal","large"},new String[]{"Petite · plus d’applications","Normale · équilibrée","Grande · confort de lecture"},"normal");
        number("Ombre derrière les informations","contrast",new int[]{20,30,45,60,75,80}," %",45);
        number("Taille des textes","text_scale",new int[]{85,100,110,120,130,140}," %",100);
        toggle("Animations réduites","Des transitions immédiates et plus sobres.","reduced_animations",false);
        toggle("Mémoriser la navigation","Retrouver le dernier favori et votre position.","remember_navigation",true);
        toggle("Collections d’applications","Regarder, Musique, Jeux et Outils.","show_collections",true);
        toggle("Mot de bienvenue","Un accueil qui suit le moment de la journée.","show_greeting",true);
        note("Conseil fluidité","Les photos sont locales. Les widgets se rafraîchissent à intervalles espacés, et les images sont redimensionnées avant stockage.");
    }
    private void widgetsPage() {
        title("Juste les informations utiles","Chaque information reste facultative.");
        toggle("Horloge","Afficher l’heure sur l’accueil.","show_clock",true);
        toggle("Horloge sur 24 heures","Utiliser le format français.","clock_24h",true);
        toggle("Météo","Données Open-Meteo · une requête espacée, sans compte.","show_weather",true);
        toggle("Espace de stockage","Suivre l’espace disponible sur la box.","show_storage",true);
        toggle("Minuteur","Accès au minuteur depuis les informations.","show_timer",true);
        toggle("Rendez-vous","Calendrier personnel configuré dans Connexions.","show_calendar",true);
        toggle("Bandeau d’informations","Météo, stockage et raccourcis personnels.","show_info",true);
        toggle("Dernières lectures","Lectures réelles datées, classées dans l’ordre chronologique.","show_continue",true);
        toggle("Musique en cours","Pochette et commandes des lecteurs compatibles.","show_music",true);
        toggle("Espace Jeux","Vos applications de jeu et le réveil du PC quand il sera configuré.","show_games",true);
        action("Ouvrir les intégrations et le minuteur",()->openConnected());
    }
    private void appsPage() {
        title("Applications & favoris","Sélectionnez une application pour la déplacer, la masquer ou changer sa bannière.");
        action("Ajouter des apps · Play Store",()->launchApp("com.android.vending"));
        List<String> dock=prefs.dock(); List<App> installed=installedApps(); Map<String,App> byPackage=new HashMap<>(); for(App a:installed) byPackage.put(a.pkg,a);
        label("FAVORIS · DANS VOTRE ORDRE"); int n=0;
        for(String pkg:dock) { App a=byPackage.get(pkg); if(a!=null) { n++; appRow(a,String.format(Locale.FRANCE,"%02d",n)+"   "+a.title+"    ·    "+categoryLabel(prefs.category(pkg))); } }
        if(n==0) note("Vos favoris","Choisissez une application ci-dessous puis « Ajouter aux favoris ».");
        label("TOUTES LES APPLICATIONS · "+installed.size());
        Set<String> hidden=prefs.hidden(); for(App a:installed) appRow(a,a.title+(hidden.contains(a.pkg)?"    ·    masquée":"")+(dock.contains(a.pkg)?"    ★":""));
        note("Masquer reste réversible","Une application masquée est seulement retirée de l’accueil. Elle reste installée et peut être réaffichée ici.");
    }
    private void appRow(App a,String title){Button b=button(title,()->appActions(a.pkg));try{android.graphics.drawable.Drawable icon=getPackageManager().getApplicationIcon(a.pkg);icon.setBounds(0,0,dp(26),dp(26));b.setCompoundDrawables(icon,null,new MarOSUI.Icon("chevron",MUTED,dp(12)),null);}catch(Exception ignored){}LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(51));p.bottomMargin=dp(7);content.addView(b,p);}
    public void appActions(String pkg) {
        if(!LauncherPrefs.validPackage(pkg)) return; String title=appTitle(pkg); boolean dock=prefs.dock().contains(pkg), hidden=prefs.hidden().contains(pkg);
        List<String> actions=new ArrayList<>(); actions.add(dock?"Retirer des favoris":"Ajouter aux favoris"); actions.add("Déplacer dans la barre des favoris"); actions.add(hidden?"Réafficher sur l’accueil":"Masquer sur l’accueil"); actions.add("Collection : "+categoryLabel(prefs.category(pkg))); actions.add("Importer une bannière"); if(photos.hasBanner(pkg)) actions.add("Retrouver la bannière d’origine"); actions.add("Ouvrir l’application"); actions.add("Informations Android de l’application");
        MarOSUI.choose(this,title,"Organisez vos essentiels, sans rien désinstaller.",actions.toArray(new String[0]),w->{
            String chosen=actions.get(w);
            if(w==0) { List<String> list=prefs.dock(); if(dock) list.remove(pkg); else { list.add(pkg); Set<String> set=prefs.hidden(); set.remove(pkg); prefs.setHidden(set); } prefs.setDock(list); showSection("apps"); }
            else if(w==1) moveApp(pkg);
            else if(w==2) { Set<String> set=prefs.hidden(); if(hidden) set.remove(pkg); else set.add(pkg); prefs.setHidden(set); showSection("apps"); }
            else if(w==3) new AlertDialog.Builder(this).setTitle("Collection de "+title).setItems(new String[]{"Regarder","Musique","Jeux","Outils"},(d2,index)->{ prefs.setCategory(pkg,new String[]{"regarder","musique","jeux","outils"}[index]); showSection("apps"); }).setNegativeButton("Annuler",null).show();
            else if(w==4) { bannerPackage=pkg; pickImage(PICK_BANNER,false); }
            else if(chosen.equals("Retrouver la bannière d’origine")) { photos.removeBanner(pkg); toast("Bannière d’origine restaurée"); }
            else if(chosen.equals("Ouvrir l’application")) launchApp(pkg);
            else { try { startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+pkg))); } catch(Exception e) { toast("Les réglages de cette application ne sont pas disponibles."); } }
        });
    }
    private void moveApp(String pkg) {
        List<String> source=prefs.dock(); source.remove(pkg); List<String> labels=new ArrayList<>(); for(int i=0;i<source.size();i++) labels.add((i+1)+" · Avant "+appTitle(source.get(i))); labels.add((source.size()+1)+" · À la fin");
        new AlertDialog.Builder(this).setTitle("Placer "+appTitle(pkg)).setItems(labels.toArray(new String[0]),(d,where)->{ source.add(where,pkg); prefs.setDock(source); Set<String> h=prefs.hidden();h.remove(pkg);prefs.setHidden(h);showSection("apps");toast("Favori déplacé"); }).setNegativeButton("Annuler",null).show();
    }
    private void photosPage() {
        title("Photos & ambiance","Des paysages intégrés et vos propres souvenirs, conservés sur la box.");
        action("Envoyer des photos depuis mon téléphone",()->HubActivity.open(this,"phone"));
        action("Importer depuis un fichier Android",()->pickImage(PICK_PHOTOS,true));
        action("Afficher la photo suivante",()->{ prefs.putString("next_photo_id","next:"+System.currentTimeMillis()); toast("La prochaine photo vous attend à l’accueil"); });
        choice("Album du diaporama","photo_mode",new String[]{"all","favorites"},new String[]{"Toutes les photos incluses","Mes photos favorites"},"all");
        number("Changer de paysage toutes les","wallpaper_minutes",new int[]{1,2,5,10,15,30,60}," min",10);
        number("Mode ambiant après","ambient_minutes",new int[]{0,1,2,5,10,15,30}," min",5);
        toggle("Horloge en mode ambiant","Une heure discrète sur les photos plein écran.","ambient_clock",true);
        toggle("Afficher le lieu et les crédits","Les photographes et les licences des paysages intégrés.","show_photo_credit",true);
        action("Ouvrir le mode ambiant maintenant",()->openAmbient());
        label("VOTRE ALBUM · "+photos.all().size()+" PHOTOS");
        final int generation=pageGeneration; int count=0; LinearLayout grid=null;
        for(PhotoStore.Photo p:photos.all()) {
            if(count%3==0) { grid=new LinearLayout(this); grid.setOrientation(LinearLayout.HORIZONTAL); grid.setPadding(0,dp(5),0,dp(6)); content.addView(grid); }
            LinearLayout card=new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(7),dp(7),dp(7),dp(9)); card.setFocusable(true); card.setClickable(true); card.setBackground(shape(PANEL,12));
            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,dp(145),1); cp.setMargins(0,0,dp(10),0); grid.addView(card,cp);
            ImageView image=new ImageView(this); image.setScaleType(ImageView.ScaleType.CENTER_CROP); card.addView(image,new LinearLayout.LayoutParams(-1,dp(86)));
            TextView name=text((p.favorite?"★ ":"")+p.title,12,p.excluded?MUTED:TEXT); name.setMaxLines(1);name.setEllipsize(android.text.TextUtils.TruncateAt.END); name.setPadding(dp(2),dp(5),0,0);card.addView(name);
            TextView state=text(p.excluded?"Masquée · sélectionner pour gérer":(p.builtin?"Paysage intégré":"Album personnel"),10,MUTED);state.setMaxLines(1);card.addView(state);
            focus(card); card.setOnClickListener(v->photoActions(p));
            card.setTag(p.id);
            worker.execute(()->{ if(generation!=pageGeneration||Thread.currentThread().isInterrupted()) return;Bitmap bm=photos.load(p,400,230); if(bm!=null) runOnUiThread(()->{ if(isFinishing()||isDestroyed()||generation!=pageGeneration) {bm.recycle();return;} image.setImageBitmap(bm); }); }); count++;
        }
        if(grid!=null) for(int i=count%3;i>0&&i<3;i++) grid.addView(new View(this),new LinearLayout.LayoutParams(0,1,1));
        note("Importer depuis votre téléphone","Ouvrez Mon téléphone. Vous pouvez y envoyer une photo depuis votre navigateur. L’import depuis un fournisseur de fichiers Android est aussi disponible ci-dessus.");
        note("Mode ambiant","0 minute désactive le lancement automatique. Le mode ambiant démarre seulement lorsque vous restez sur l’accueil ; il n’interrompt pas Netflix ou la musique dans une autre application.");
    }
    private void photoActions(PhotoStore.Photo p) {
        ArrayList<String> choices=new ArrayList<>(); choices.add("Afficher sur l’accueil maintenant"); choices.add(p.favorite?"Retirer de mes photos favorites":"Ajouter à mes photos favorites"); choices.add(p.excluded?"Inclure dans le diaporama":"Masquer dans le diaporama"); choices.add("Renommer"); choices.add("Lieu, auteur et licence"); if(!p.builtin) choices.add("Retirer cette photo personnelle");
        new AlertDialog.Builder(this).setTitle(p.title).setItems(choices.toArray(new String[0]),(d,w)->{
            if(w==0) { photos.setExcluded(p.id,false);prefs.putString("next_photo_id",p.id);toast("Photo choisie pour l’accueil"); }
            else if(w==1) { photos.setFavorite(p.id,!p.favorite);refresh(p.id); }
            else if(w==2) { int included=0;for(PhotoStore.Photo existing:photos.all())if(!existing.excluded)included++;if(!p.excluded&&included<=1) { toast("Conservez au moins une photo dans votre diaporama.");return;} photos.setExcluded(p.id,!p.excluded);refresh(p.id); }
            else if(w==3) { EditText input=new EditText(this); input.setSingleLine(true);input.setText(p.title);new AlertDialog.Builder(this).setTitle("Nom de la photo").setView(input).setPositiveButton("Enregistrer",(x,y)->{photos.rename(p.id,input.getText().toString());showSection("photos");}).setNegativeButton("Annuler",null).show(); }
            else if(w==4) { AlertDialog.Builder credits=new AlertDialog.Builder(this).setTitle(p.title).setMessage(p.credit+(p.builtin?"\n\nSource : Wikimedia Commons. Photo redimensionnée et recadrée pour l’écran. Les conditions de la licence et le fichier original sont disponibles à la source.":"\n\nCette image reste dans le stockage privé de votre box.")).setPositiveButton("Fermer",null);String source=photos.source(p);if(!source.isEmpty())credits.setNeutralButton("Voir la source et la licence",(x,y)->safeStart(new Intent(Intent.ACTION_VIEW,Uri.parse(source))));credits.show(); }
            else new AlertDialog.Builder(this).setTitle("Retirer cette photo ?").setMessage("Seule la copie importée sur la box sera supprimée. Votre fichier original reste intact.").setPositiveButton("Retirer la copie",(x,y)->{try{photos.deleteImported(p.id);showSection("photos");}catch(Exception e){error(e);}}).setNegativeButton("Annuler",null).show();
        }).setNegativeButton("Fermer",null).show();
    }
    private void connectionsPage() {
        title("Votre télé, connectée à votre vie","Les fonctions avancées s’activent lorsque vous configurez leur source.");
        action("Ouvrir le centre des connexions",()->openConnected());
        note("Téléphone compagnon","Depuis votre téléphone sur le même Wi-Fi : envoyer une photo, du texte ou un lien avec un code d’appairage. Le service reste sous votre contrôle.");
        note("PC, musique et bibliothèque","Le réveil du PC et Jellyfin attendent vos futurs équipements. Les commandes de musique utilisent les lecteurs Android compatibles.");
        action("Paramètres réseau de la box",()->safeStart(new Intent(Settings.ACTION_WIFI_SETTINGS)));
        action("Réglages Android",()->safeStart(new Intent(Settings.ACTION_SETTINGS)));
    }
    private void backupPage() {
        title("Vos réglages vous appartiennent","Une sauvegarde portable des préférences, sans mot de passe ni jeton de connexion.");
        action("Exporter mes réglages dans un fichier",()->{ Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.setType("application/json");i.addCategory(Intent.CATEGORY_OPENABLE);i.putExtra(Intent.EXTRA_TITLE,"MarOS-reglages.json");pick(i,EXPORT); });
        action("Importer des réglages sauvegardés",()->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("*/*");i.addCategory(Intent.CATEGORY_OPENABLE);pick(i,IMPORT);});
        note("Ce qui est inclus","Thème, favoris, applications masquées, collections, taille des vignettes, photos et widgets. L’import fusionne les préférences du fichier avec vos réglages actuels.");
        note("Ce qui reste sur cette box","Les photos et bannières personnelles, les mots de passe, les jetons, les adresses des services, les événements privés et les autorisations Android ne sont pas exportés.");
        action("Réinitialiser seulement le style visuel",()->new AlertDialog.Builder(this).setTitle("Retrouver le style Nature ?").setMessage("Les favoris, photos, connexions et applications masquées sont conservés.").setPositiveButton("Réinitialiser le style",(d,w)->{prefs.prefs().edit().remove("theme").remove("accent").remove("home_title").remove("tile_size").remove("contrast").remove("text_scale").remove("reduced_animations").apply();prefs.changed();showSection("appearance");}).setNegativeButton("Annuler",null).show());
        note("Restauration de la TV","Les outils ADB du dépôt restaurent les opérations journalisées avec votre sauvegarde. Consultez le guide d’installation et de retour arrière. Rien ici ne désinstalle d’application.");
    }
    private void pickImage(int request,boolean multiple) { Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType("image/*");intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,multiple);intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);pick(intent,request); }
    private void pick(Intent intent,int request) { try { startActivityForResult(intent,request); } catch(Exception e) { new AlertDialog.Builder(this).setTitle("Sélecteur de fichiers indisponible").setMessage("Cette box ne propose pas de fournisseur de fichiers Android pour cette action. Pour les photos et les sauvegardes, utilisez le Téléphone compagnon dans Connexions.").setPositiveButton("Connexions",(d,w)->showSection("connections")).setNegativeButton("Fermer",null).show(); } }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data); if(result!=RESULT_OK||data==null) return;
        if(request==PICK_PHOTOS) { List<Uri> uris=new ArrayList<>();ClipData clip=data.getClipData();if(clip!=null) for(int i=0;i<clip.getItemCount()&&i<100;i++) uris.add(clip.getItemAt(i).getUri());else if(data.getData()!=null) uris.add(data.getData()); toast("Import des photos en cours…");
            worker.execute(()->{int done=0;String lastError="";for(Uri uri:uris){try{photos.importImage(uri,fileName(uri));done++;}catch(Exception e){lastError=e.getMessage();}}final int imported=done;final String issue=lastError;runOnUiThread(()->{if(isFinishing())return;showSection("photos");toast(imported+" photo(s) importée(s)"+(issue.isEmpty()?"":" · "+issue));});});
        } else if(request==PICK_BANNER&&data.getData()!=null) { final Uri uri=data.getData();final String pkg=bannerPackage;worker.execute(()->{try{photos.importBanner(pkg,uri);runOnUiThread(()->toast("Bannière personnalisée enregistrée"));}catch(Exception e){runOnUiThread(()->error(e));}});
        } else if(request==EXPORT&&data.getData()!=null) { final Uri uri=data.getData();worker.execute(()->{try{byte[] bytes=prefs.exportConfiguration().getBytes(StandardCharsets.UTF_8);try(OutputStream out=getContentResolver().openOutputStream(uri,"wt")){if(out==null)throw new java.io.IOException("Impossible d’ouvrir le fichier.");out.write(bytes);}runOnUiThread(()->toast("Réglages exportés · aucun secret inclus"));}catch(Exception e){runOnUiThread(()->error(e));}});
        } else if(request==IMPORT&&data.getData()!=null) {final Uri uri=data.getData();worker.execute(()->{try{ByteArrayOutputStream b=new ByteArrayOutputStream();try(InputStream in=getContentResolver().openInputStream(uri)){if(in==null)throw new java.io.IOException("Fichier inaccessible.");byte[] buf=new byte[4096];int n;while((n=in.read(buf))!=-1){if(b.size()+n>262144)throw new java.io.IOException("Fichier trop volumineux.");b.write(buf,0,n);}}String config=new String(b.toByteArray(),StandardCharsets.UTF_8);runOnUiThread(()->new AlertDialog.Builder(this).setTitle("Importer ces réglages ?").setMessage("Vos préférences actuelles seront modifiées. Les connexions et les photos restent conservées.").setPositiveButton("Importer",(d,w)->{try{int count=prefs.importConfiguration(config);toast(count+" réglages importés");showSection(section);}catch(Exception e){error(e);}}).setNegativeButton("Annuler",null).show());}catch(Exception e){runOnUiThread(()->error(e));}}); }
    }
    private String fileName(Uri uri) {try(Cursor c=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst())return c.getString(0).replaceFirst("\\.[^.]+$","");}catch(Exception ignored){}return "Photo personnelle";}
    private void choice(String title,String key,String[] values,String[] labels,String fallback) {String value=prefs.getString(key,fallback);int selected=0;for(int i=0;i<values.length;i++)if(values[i].equals(value))selected=i;final int initial=selected;Button b=action(title+"\n"+labels[selected],()->new AlertDialog.Builder(this).setTitle(title).setSingleChoiceItems(labels,initial,(d,w)->{prefs.putString(key,values[w]);d.dismiss();refresh(key);}).setNegativeButton("Annuler",null).show());b.setTag(key);}
    private void number(String title,String key,int[] values,String suffix,int fallback) {int value=prefs.getInt(key,fallback);String[] labels=new String[values.length];int selected=-1;for(int i=0;i<values.length;i++){labels[i]=values[i]==0?"Désactivé":values[i]+suffix;if(values[i]==value)selected=i;}final int initial=selected;Button b=action(title+"\n"+(value==0?"Désactivé":value+suffix),()->new AlertDialog.Builder(this).setTitle(title).setSingleChoiceItems(labels,initial,(d,w)->{prefs.putInt(key,values[w]);d.dismiss();refresh(key);}).setNegativeButton("Annuler",null).show());b.setTag(key);}
    private void toggle(String title,String help,String key,boolean fallback) {boolean value=prefs.getBool(key,fallback);Button b=button((value?"✓  ":"○  ")+title+"    ·    "+(value?"Activé":"Désactivé"),()->{prefs.putBool(key,!value);refresh(key);});b.setTag(key);content.addView(b,new LinearLayout.LayoutParams(-1,dp(52)));TextView hint=text(help,11,MUTED);hint.setPadding(dp(14),0,0,dp(10));content.addView(hint);}
    private Button action(String title,Runnable click) {Button b=button(title,click);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(50));p.setMargins(0,0,0,dp(6));content.addView(b,p);return b;}
    private void refresh(String tag) {showSection(section);content.post(()->{View v=content.findViewWithTag(tag);if(v!=null)v.requestFocus();});}
    private void note(String title,String message) {LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(16),dp(13),dp(16),dp(14));box.setBackground(shape(0xff15212c,12));TextView t=text(title,14,TEXT);t.setTypeface(MarOSUI.type(this,true));box.addView(t);TextView body=text(message,12,MUTED);body.setPadding(0,dp(5),0,0);body.setLineSpacing(dp(2),1);box.addView(body);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(7),0,dp(10));content.addView(box,p);}
    private void label(String value) {TextView t=text(value,11,accent());t.setLetterSpacing(.1f);t.setPadding(dp(10),dp(20),0,dp(10));content.addView(t);}
    private Button button(String title,Runnable action){return MarOSUI.button(this,title,MarOSUI.iconFor(title),action);}
    private void focus(View v){MarOSUI.focus(v);}
    private void editHomeTitle(){EditText input=new EditText(this);input.setText(prefs.getString("home_title",""));input.setHint("Votre soirée, à votre rythme.");input.setTextColor(TEXT);input.setSingleLine(true);input.setPadding(dp(20),dp(14),dp(20),dp(14));new AlertDialog.Builder(this).setTitle("Votre message d’accueil").setView(input).setPositiveButton("Enregistrer",(d,w)->{String value=input.getText().toString().trim();prefs.putString("home_title",value.substring(0,Math.min(70,value.length())));}).setNeutralButton("Message par défaut",(d,w)->prefs.putString("home_title","")).setNegativeButton("Annuler",null).show();}
    private TextView text(String value,int sp,int color) {TextView t=MarOSUI.text(this,value,sp,color);t.setTextColor(color);t.setTextSize(sp);return t;}
    private GradientDrawable shape(int color,int radius) {GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private int dp(float value) {return Math.round(value*getResources().getDisplayMetrics().density);}
    private void toast(String value) {if(!isFinishing())Toast.makeText(this,value,Toast.LENGTH_LONG).show();}
    private void error(Exception e) {if(!isFinishing())new AlertDialog.Builder(this).setTitle("Action non terminée").setMessage(e.getMessage()==null?"Une erreur est survenue. Vos réglages précédents sont conservés.":e.getMessage()).setPositiveButton("Fermer",null).show();}
    private void safeStart(Intent i) {try{startActivity(i);}catch(Exception e){toast("Cette fonction n’est pas disponible sur cette box.");}}
    private void openConnected() {safeStart(new Intent().setClassName(getPackageName(),getPackageName()+".HubActivity"));}
    private void openAmbient() {safeStart(new Intent().setClassName(getPackageName(),getPackageName()+".AmbientActivity"));}
    private void launchApp(String pkg) {Intent i=getPackageManager().getLeanbackLaunchIntentForPackage(pkg);if(i==null)i=getPackageManager().getLaunchIntentForPackage(pkg);if(i!=null)safeStart(i);else toast("Cette application n’a pas d’écran accessible.");}
    private String appTitle(String pkg) {if(appNames.containsKey(pkg))return appNames.get(pkg);try{return getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(pkg,0)).toString();}catch(Exception e){return pkg;}}
    private List<App> installedApps() {LinkedHashMap<String,App> items=new LinkedHashMap<>();PackageManager pm=getPackageManager();for(String category:new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER,Intent.CATEGORY_LAUNCHER}){Intent i=new Intent(Intent.ACTION_MAIN).addCategory(category);for(ResolveInfo r:pm.queryIntentActivities(i,0)){String pkg=r.activityInfo.packageName;if(pkg.equals(getPackageName()))continue;String title=r.loadLabel(pm).toString();appNames.put(pkg,title);items.put(pkg,new App(pkg,title));}}ArrayList<App> out=new ArrayList<>(items.values());Collections.sort(out,(a,b)->a.title.compareToIgnoreCase(b.title));return out;}
    private static String categoryLabel(String value) {switch(value){case "regarder":return "Regarder";case "musique":return "Musique";case "jeux":return "Jeux";default:return "Outils";}}
    private static final class App {final String pkg,title;App(String p,String t){pkg=p;title=t;}}
}
