package io.github.marouane4142.maros;

import android.service.dreams.DreamService;

/** Android screensaver lifecycle: user input is handled by the system and ends this dream. */
public final class AmbientDreamService extends DreamService {
    private AmbientActivity.Scene scene;
    @Override public void onAttachedToWindow(){
        super.onAttachedToWindow();setInteractive(false);setFullscreen(true);setScreenBright(false);
        scene=new AmbientActivity.Scene(this);setContentView(scene);
    }
    @Override public void onDreamingStarted(){super.onDreamingStarted();if(scene!=null)scene.start();}
    @Override public void onDreamingStopped(){if(scene!=null)scene.stop();super.onDreamingStopped();}
    @Override public void onDetachedFromWindow(){if(scene!=null){scene.release();scene=null;}super.onDetachedFromWindow();}
}
