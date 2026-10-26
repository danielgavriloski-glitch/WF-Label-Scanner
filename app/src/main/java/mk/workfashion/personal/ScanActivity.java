package mk.workfashion.personal;
import android.app.Activity;
import android.content.Intent;
import android.graphics.*;
import android.net.Uri;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.io.File;

/** Offline four-corner crop with perspective correction; keeps text in a readable JPEG. */
public class ScanActivity extends Activity {
 Bitmap bitmap;CropView crop;Button save;String output;
 @Override public void onCreate(Bundle state){super.onCreate(state);output=getIntent().getStringExtra("output");try{bitmap=Documents.image(this,Uri.parse(getIntent().getStringExtra("input")));}catch(Exception e){Toast.makeText(this,"Сликата не се отвори.",Toast.LENGTH_LONG).show();finish();return;}
  LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Color.rgb(16,45,53));root.setPadding(12,16,12,18);root.setOnApplyWindowInsetsListener((v,insets)->{root.setPadding(12,insets.getSystemWindowInsetTop()+12,12,insets.getSystemWindowInsetBottom()+12);return insets;});TextView title=new TextView(this);title.setText("Скен на документ\nНамести ги четирите агли на страницата.");title.setTextColor(Color.WHITE);title.setTextSize(17);title.setPadding(8,8,8,14);root.addView(title);crop=new CropView();root.addView(crop,new LinearLayout.LayoutParams(-1,0,1));LinearLayout tools=new LinearLayout(this);Button rotate=new Button(this);rotate.setText("Заврти ↻");tools.addView(rotate,new LinearLayout.LayoutParams(0,-2,1));Button cancel=new Button(this);cancel.setText("Откажи");tools.addView(cancel,new LinearLayout.LayoutParams(0,-2,1));root.addView(tools);save=new Button(this);save.setText("Зачувај читлив скен");root.addView(save);setContentView(root);
  rotate.setOnClickListener(v->{Matrix m=new Matrix();m.postRotate(90);Bitmap next=Bitmap.createBitmap(bitmap,0,0,bitmap.getWidth(),bitmap.getHeight(),m,true);if(next!=bitmap)bitmap.recycle();bitmap=next;crop.reset();});cancel.setOnClickListener(v->finish());save.setOnClickListener(v->save());
 }
 void save(){save.setEnabled(false);new Thread(()->{try{float[] src=crop.corners.clone();float top=distance(src,0,2),bottom=distance(src,6,4),left=distance(src,0,6),right=distance(src,2,4);int w=Math.max(1,Math.round(Math.max(top,bottom))),h=Math.max(1,Math.round(Math.max(left,right)));if(w<80||h<80)throw new Exception("Избери ја целата страница.");float scale=Math.min(1f,2600f/Math.max(w,h));w=Math.max(1,Math.round(w*scale));h=Math.max(1,Math.round(h*scale));Matrix matrix=new Matrix();if(!matrix.setPolyToPoly(src,0,new float[]{0,0,w,0,w,h,0,h},0,4))throw new Exception("Провери ги аглите.");Bitmap b=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(b);c.drawColor(Color.WHITE);c.drawBitmap(bitmap,matrix,new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG));try{Documents.writeJpeg(b,new File(output));}finally{b.recycle();}runOnUiThread(()->{setResult(RESULT_OK,new Intent().putExtra("output",output));finish();});}catch(Exception e){runOnUiThread(()->{save.setEnabled(true);Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();});}},"wfag-scan").start();}
 static float distance(float[] p,int a,int b){return (float)Math.hypot(p[a]-p[b],p[a+1]-p[b+1]);}
 class CropView extends View {
  float[] corners=new float[8];float scale=1,dx,dy;int active=-1;Paint paint=new Paint(3);
  CropView(){super(ScanActivity.this);reset();setContentDescription("Четири агли на скенираниот документ");}
  void reset(){float w=bitmap.getWidth(),h=bitmap.getHeight();corners=new float[]{w*.04f,h*.04f,w*.96f,h*.04f,w*.96f,h*.96f,w*.04f,h*.96f};invalidate();}
  @Override protected void onDraw(Canvas c){super.onDraw(c);scale=Math.min(getWidth()/(float)bitmap.getWidth(),getHeight()/(float)bitmap.getHeight());dx=(getWidth()-bitmap.getWidth()*scale)/2;dy=(getHeight()-bitmap.getHeight()*scale)/2;Matrix m=new Matrix();m.postScale(scale,scale);m.postTranslate(dx,dy);c.drawBitmap(bitmap,m,paint);Path p=new Path();p.moveTo(dx+corners[0]*scale,dy+corners[1]*scale);for(int i=2;i<8;i+=2)p.lineTo(dx+corners[i]*scale,dy+corners[i+1]*scale);p.close();paint.setColor(Color.rgb(35,222,196));paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(4);c.drawPath(p,paint);paint.setStyle(Paint.Style.FILL);for(int i=0;i<8;i+=2)c.drawCircle(dx+corners[i]*scale,dy+corners[i+1]*scale,13,paint);paint.setColor(Color.WHITE);}
  @Override public boolean onTouchEvent(MotionEvent e){float x=(e.getX()-dx)/scale,y=(e.getY()-dy)/scale;if(e.getAction()==MotionEvent.ACTION_DOWN){float best=80/scale;for(int i=0;i<8;i+=2){float d=(float)Math.hypot(x-corners[i],y-corners[i+1]);if(d<best){best=d;active=i;}}return true;}if(e.getAction()==MotionEvent.ACTION_MOVE&&active>=0){float[] proposed=corners.clone();proposed[active]=Math.max(0,Math.min(bitmap.getWidth(),x));proposed[active+1]=Math.max(0,Math.min(bitmap.getHeight(),y));if(convex(proposed))corners=proposed;invalidate();return true;}if(e.getAction()==MotionEvent.ACTION_UP){active=-1;performClick();return true;}return true;}
  @Override public boolean performClick(){super.performClick();return true;}
  boolean convex(float[] p){float sign=0;for(int i=0;i<4;i++){int a=i*2,b=((i+1)%4)*2,d=((i+2)%4)*2;float cross=(p[b]-p[a])*(p[d+1]-p[b+1])-(p[b+1]-p[a+1])*(p[d]-p[b]);if(Math.abs(cross)<100)return false;if(sign==0)sign=cross;else if(cross*sign<0)return false;}return sign>0;}
 }
}
