package io.github.marouane4142.maros;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import java.util.List;
import java.util.Locale;

/** Real media-session controls with live artwork, elapsed time and DPAD seeking. */
public final class MusicPanelView extends FrameLayout {
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final MediaBridge bridge;private final boolean expanded;
    private final ImageView cover;private final TextView name,artist,source,elapsed,total,play,previous,next;
    private final SeekBar seek;private MediaBridge.NowPlaying current;private MediaController controller;
    private boolean attached,dragging;private int ticks;private long duration;private PlaybackState state;
    private final Runnable tick=new Runnable(){public void run(){if(!attached)return;if(++ticks%5==0||controller==null)select();position();handler.postDelayed(this,1000);}};
    private final MediaController.Callback changed=new MediaController.Callback(){
        @Override public void onMetadataChanged(MediaMetadata metadata){metadata(metadata);updateControls();}
        @Override public void onPlaybackStateChanged(PlaybackState value){state=value;updateControls();position();}
        @Override public void onAudioInfoChanged(MediaController.PlaybackInfo value){updateControls();}
        @Override public void onSessionDestroyed(){unbind();select();}
    };
    public MusicPanelView(Context c,boolean expanded){
        super(c);this.expanded=expanded;bridge=new MediaBridge(c);setBackground(MarOSUI.border(c,0xED131C29,0x22527386,22));setClipToOutline(true);
        LinearLayout row=new LinearLayout(c);row.setGravity(Gravity.TOP);row.setPadding(d(18),d(16),d(18),d(14));addView(row,new LayoutParams(-1,-1));
        cover=new ImageView(c);cover.setScaleType(ImageView.ScaleType.CENTER_CROP);cover.setBackground(MarOSUI.surface(c,0xFF25364F,12));cover.setClipToOutline(true);int art=expanded?132:88;LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(d(art),d(art));cp.rightMargin=d(16);cp.gravity=Gravity.CENTER_VERTICAL;row.addView(cover,cp);cover.setContentDescription("Ouvrir l’application en lecture");cover.setFocusable(true);cover.setOnClickListener(v->{if(current!=null)bridge.open(current.pkg);});MarOSUI.focus(cover);
        LinearLayout details=new LinearLayout(c);details.setOrientation(1);row.addView(details,new LinearLayout.LayoutParams(0,-1,1));
        source=txt("Votre musique",8,MarOSUI.accent(c));source.setLetterSpacing(.035f);details.addView(source);
        name=txt("Une place pour votre musique",expanded?23:16,MarOSUI.TEXT);name.setTypeface(MarOSUI.type(c,true));name.setSingleLine(true);name.setEllipsize(TextUtils.TruncateAt.END);details.addView(name,lp(-1,-2,0,expanded?12:7,0,0));
        artist=txt("Lancez Spotify ou YouTube Music",11,MarOSUI.MUTED);artist.setSingleLine(true);artist.setEllipsize(TextUtils.TruncateAt.END);details.addView(artist,lp(-1,-2,0,4,0,0));
        LinearLayout times=new LinearLayout(c);times.setGravity(Gravity.CENTER_VERTICAL);elapsed=txt("0:00",8,MarOSUI.MUTED);total=txt("0:00",8,MarOSUI.MUTED);seek=new SeekBar(c);seek.setMax(1000);seek.setPadding(d(8),0,d(8),0);seek.setProgressTintList(ColorStateList.valueOf(MarOSUI.accent(c)));seek.setProgressBackgroundTintList(ColorStateList.valueOf(0xFF283851));seek.setThumbTintList(ColorStateList.valueOf(MarOSUI.accent(c)));seek.setContentDescription("Position de lecture, flèches gauche et droite");seek.setKeyProgressIncrement(50);
        times.addView(elapsed);times.addView(seek,new LinearLayout.LayoutParams(0,d(23),1));times.addView(total);details.addView(times,lp(-1,d(25),0,expanded?14:6,0,0));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar b,int value,boolean user){if(user&&controller!=null&&duration>0){controller.getTransportControls().seekTo(duration*value/1000);elapsed.setText(time(duration*value/1000));}}public void onStartTrackingTouch(SeekBar b){dragging=true;}public void onStopTrackingTouch(SeekBar b){dragging=false;}});
        LinearLayout buttons=new LinearLayout(c);buttons.setGravity(Gravity.CENTER_VERTICAL);previous=control("previous","Titre précédent",()->{if(current!=null)bridge.previous(current);});play=control("play","Lecture ou pause",()->{if(current!=null){if(remotePlayback())bridge.open(current.pkg);else bridge.playPause(current);}else openMusic();});next=control("next","Titre suivant",()->{if(current!=null)bridge.next(current);});
        buttons.addView(previous,lp(d(36),d(36),0,0,8,0));buttons.addView(play,lp(d(44),d(44),0,0,8,0));buttons.addView(next,lp(d(36),d(36),0,0,8,0));TextView volume=control("volume","Afficher le volume",()->{AudioManager a=(AudioManager)c.getSystemService(Context.AUDIO_SERVICE);if(a!=null)a.adjustVolume(AudioManager.ADJUST_SAME,AudioManager.FLAG_SHOW_UI);});buttons.addView(volume,lp(d(32),d(32),0,0,0,0));View gap=new View(c);buttons.addView(gap,new LinearLayout.LayoutParams(0,1,1));TextView open=control("chevron","Ouvrir le lecteur",()->{if(current!=null)bridge.open(current.pkg);else openMusic();});buttons.addView(open,lp(d(30),d(32),0,0,0,0));details.addView(buttons,lp(-1,d(46),0,expanded?8:4,0,0));
        setContentDescription("Lecteur multimédia MarOS");cover.setImageDrawable(new MarOSUI.Icon("music",MarOSUI.accent(c),d(42)));updateControls();
    }
    private void openMusic(){if(!bridge.open("com.spotify.tv.android"))bridge.open("com.google.android.youtube.tvmusic");}
    private TextView control(String kind,String description,Runnable run){TextView v=MarOSUI.iconButton(getContext(),kind,description,run);v.setPadding(d(7),d(6),d(7),d(6));v.setCompoundDrawables(new MarOSUI.Icon(kind,MarOSUI.TEXT,d(19)),null,null,null);return v;}
    private void select(){List<MediaBridge.NowPlaying> sessions=bridge.sessions();MediaBridge.NowPlaying candidate=null;for(MediaBridge.NowPlaying s:sessions)if(new LauncherPrefs(getContext()).category(s.pkg).equals("musique")){candidate=s;break;}if(candidate==null&&!sessions.isEmpty())candidate=sessions.get(0);if(candidate==null){if(controller!=null){unbind();name.setText("Une place pour votre musique");artist.setText("Lancez Spotify ou YouTube Music");source.setText("Votre musique");duration=0;elapsed.setText("0:00");total.setText("0:00");seek.setProgress(0);cover.setImageDrawable(new MarOSUI.Icon("music",MarOSUI.accent(getContext()),d(42)));updateControls();}return;}if(controller!=null&&current!=null&&current.pkg.equals(candidate.pkg)&&controller.getSessionToken().equals(candidate.controller.getSessionToken()))return;unbind();current=candidate;controller=candidate.controller;try{controller.registerCallback(changed,handler);}catch(RuntimeException ignored){}metadata(controller.getMetadata());state=controller.getPlaybackState();updateControls();position();}
    private void metadata(MediaMetadata m){if(m==null)return;name.setText(first(m.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE),m.getString(MediaMetadata.METADATA_KEY_TITLE),"En lecture"));artist.setText(first(m.getString(MediaMetadata.METADATA_KEY_ARTIST),m.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE),m.getString(MediaMetadata.METADATA_KEY_ALBUM)));duration=m.getLong(MediaMetadata.METADATA_KEY_DURATION);Bitmap b=m.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);if(b==null)b=m.getBitmap(MediaMetadata.METADATA_KEY_ART);if(b==null)b=m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);if(b==null)cover.setImageDrawable(new MarOSUI.Icon("music",MarOSUI.accent(getContext()),d(42)));else cover.setImageBitmap(b);total.setText(time(duration));}
    private boolean remotePlayback(){MediaController.PlaybackInfo info=controller==null?null:controller.getPlaybackInfo();return info!=null&&info.getPlaybackType()==MediaController.PlaybackInfo.PLAYBACK_TYPE_REMOTE;}
    private void updateControls(){play.setBackground(MarOSUI.surface(getContext(),MarOSUI.accent(getContext()),99));play.setOnFocusChangeListener((v,has)->v.setBackground(MarOSUI.border(getContext(),MarOSUI.accent(getContext()),has?MarOSUI.TEXT:android.graphics.Color.TRANSPARENT,99)));boolean remote=remotePlayback();boolean playing=state!=null&&(state.getState()==3||state.getState()==6);play.setCompoundDrawables(new MarOSUI.Icon(remote?"phone":playing?"pause":"play",0xFF102330,d(22)),null,null,null);play.setContentDescription(remote?"Ouvrir le lecteur : lecture sur un autre appareil":playing?"Mettre en pause":"Reprendre la lecture");if(current!=null)try{source.setText(getContext().getPackageManager().getApplicationLabel(getContext().getPackageManager().getApplicationInfo(current.pkg,0)).toString()+(remote?" · autre appareil":playing?" · en lecture":" · en pause"));}catch(Exception ignored){}long actions=state==null||remote?0:state.getActions();enable(previous,(actions&PlaybackState.ACTION_SKIP_TO_PREVIOUS)!=0);enable(next,(actions&PlaybackState.ACTION_SKIP_TO_NEXT)!=0);enable(seek,duration>0&&(actions&PlaybackState.ACTION_SEEK_TO)!=0);}
    private void enable(View v,boolean available){v.setEnabled(available);v.setAlpha(available?1f:.3f);v.setFocusable(available);}
    private void position(){if(state==null||dragging)return;long p=Math.max(0,state.getPosition());if(state.getState()==3)p+=(long)((SystemClock.elapsedRealtime()-state.getLastPositionUpdateTime())*state.getPlaybackSpeed());if(duration>0)p=Math.min(duration,p);elapsed.setText(time(p));seek.setProgress(duration>0?(int)(p*1000/duration):0);}
    private static String time(long ms){long seconds=Math.max(0,ms/1000);return String.format(Locale.ROOT,"%d:%02d",seconds/60,seconds%60);}
    private static String first(String...s){for(String x:s)if(x!=null&&!x.trim().isEmpty())return x;return "";}
    private void unbind(){if(controller!=null)try{controller.unregisterCallback(changed);}catch(Exception ignored){}controller=null;current=null;state=null;}
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();attached=true;select();handler.post(tick);}
    @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);if(cover==null||!isAttachedToWindow())return;handler.removeCallbacksAndMessages(null);if(visibility==VISIBLE){attached=true;select();handler.post(tick);}else{attached=false;unbind();}}
    @Override protected void onDetachedFromWindow(){attached=false;handler.removeCallbacksAndMessages(null);unbind();cover.setImageDrawable(null);super.onDetachedFromWindow();}
    private TextView txt(String value,int size,int color){return MarOSUI.text(getContext(),value,size,color);}private int d(float n){return MarOSUI.dp(getContext(),n);}
    private LinearLayout.LayoutParams lp(int w,int h,int l,int t,int r,int b){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(w,h);p.setMargins(d(l),d(t),d(r),d(b));return p;}
}
