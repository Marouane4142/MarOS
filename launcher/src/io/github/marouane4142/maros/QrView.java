package io.github.marouane4142.maros;
import android.content.Context;
import android.graphics.*;
import android.view.View;
import io.nayuki.qrcodegen.QrCode;
/** Integer module edges and a four-module quiet zone keep the code scannable from a sofa. */
public final class QrView extends View {
    private final QrCode qr;private final Paint paint=new Paint();
    public QrView(Context c,String value){super(c);qr=QrCode.encodeText(value,QrCode.Ecc.MEDIUM);setContentDescription("QR code de connexion au téléphone");}
    @Override protected void onDraw(Canvas c){int n=qr.size+8,unit=Math.max(1,Math.min(getWidth(),getHeight())/n),left=(getWidth()-n*unit)/2,top=(getHeight()-n*unit)/2;c.drawColor(Color.WHITE);paint.setColor(Color.BLACK);for(int y=0;y<qr.size;y++)for(int x=0;x<qr.size;x++)if(qr.getModule(x,y))c.drawRect(left+(x+4)*unit,top+(y+4)*unit,left+(x+5)*unit,top+(y+5)*unit,paint);}
}
