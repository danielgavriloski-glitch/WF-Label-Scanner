package mk.workfashion.personal;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Bundle;
import android.util.AtomicFile;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private static final int CREATE_FILE=71, OPEN_BACKUP=72, MAX_BYTES=16*1024*1024;
    WebView webView;
    private AtomicFile stateFile;
    private byte[] pendingBytes;
    private String pendingName;
    private boolean ready=false;
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        stateFile=new AtomicFile(new File(getFilesDir(), "workfashion-state.json"));
        FrameLayout root=new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(16,45,53));
        root.setOnApplyWindowInsetsListener((v,insets)->{
            if(android.os.Build.VERSION.SDK_INT>=30){
                android.graphics.Insets i=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
                root.setPadding(i.left,i.top,i.right,i.bottom);
            }else root.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());
            return insets;
        });
        webView=new WebView(this);
        webView.setBackgroundColor(Color.rgb(243,246,247));
        root.addView(webView,new FrameLayout.LayoutParams(-1,-1));
        setContentView(root);
        WebSettings s=webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setAllowFileAccessFromFileURLs(false);
        s.setAllowUniversalAccessFromFileURLs(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setBlockNetworkLoads(true);
        webView.addJavascriptInterface(new LocalBridge(),"Android");
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){return !request.getUrl().toString().startsWith("file:///android_asset/");}
            @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){
                if(!request.getUrl().toString().startsWith("file:///android_asset/"))return new WebResourceResponse("text/plain","UTF-8",new ByteArrayInputStream(new byte[0]));
                return null;
            }
            @Override public void onPageFinished(WebView view,String url){ready=true;}
        });
        webView.loadUrl("file:///android_asset/index.html");
    }
    private void error(String message){runOnUiThread(()->webView.evaluateJavascript("window.onNativeError&&onNativeError("+JSONObject.quote(message)+")",null));}
    private synchronized String readState(){
        if(!stateFile.getBaseFile().exists())return "";
        try{return new String(stateFile.readFully(),StandardCharsets.UTF_8);}catch(Exception e){return "{\"readError\":true}";}
    }
    private synchronized boolean writeState(String text){
        FileOutputStream out=null;
        try{
            if(text.length()>MAX_BYTES)return false;
            JSONObject obj=new JSONObject(text);
            if(!"workfashion-personal-time".equals(obj.optString("appId"))||obj.optInt("schemaVersion")!=1)return false;
            out=stateFile.startWrite();out.write(text.getBytes(StandardCharsets.UTF_8));stateFile.finishWrite(out);return true;
        }catch(Exception e){if(out!=null)stateFile.failWrite(out);return false;}
    }
    public class LocalBridge {
        @JavascriptInterface public String loadState(){return readState();}
        @JavascriptInterface public boolean saveState(String text){return writeState(text);}
        @JavascriptInterface public void exportText(String name,String text,String mime){requestSave(name,text.getBytes(StandardCharsets.UTF_8),mime);}
        @JavascriptInterface public void exportBytes(String name,String base64,String mime){try{requestSave(name,Base64.decode(base64,Base64.DEFAULT),mime);}catch(Exception e){error("Фајлот не се подготви.");}}
        @JavascriptInterface public void exportPdf(String name,String json){try{requestSave(name,PdfReport.create(new JSONObject(json)),"application/pdf");}catch(Exception e){error("PDF извештајот не се подготви: "+e.getMessage());}}
        @JavascriptInterface public void openBackup(){runOnUiThread(()->{try{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("*/*");i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"application/json","text/plain","application/octet-stream"});i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,OPEN_BACKUP);}catch(Exception e){error("Не се отвори изборот за бекап.");}});}
        @JavascriptInterface public void keepBeforeRestore(String text){try{if(text.length()<=MAX_BYTES){FileOutputStream out=new FileOutputStream(new File(getFilesDir(),"before-restore.json"));out.write(text.getBytes(StandardCharsets.UTF_8));out.close();}}catch(Exception ignored){}}
    }
    private void requestSave(String name,byte[] bytes,String mime){runOnUiThread(()->{
        if(pendingBytes!=null){error("Прво заврши го претходното зачувување.");return;}
        if(bytes.length>MAX_BYTES){error("Фајлот е предолг. Избери пократок период.");return;}
        if(!name.matches("[A-Za-z0-9_.-]+")){error("Невалидно име на фајл.");return;}
        pendingBytes=bytes;pendingName=name;
        try{Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType(mime);i.putExtra(Intent.EXTRA_TITLE,name);startActivityForResult(i,CREATE_FILE);}catch(Exception e){pendingBytes=null;pendingName=null;error("Не се отвори прозорецот за зачувување.");}
    });}
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==CREATE_FILE){
            byte[] bytes=pendingBytes;String name=pendingName;pendingBytes=null;pendingName=null;
            if(result==RESULT_OK&&data!=null&&data.getData()!=null&&bytes!=null){
                new Thread(()->{try(OutputStream out=getContentResolver().openOutputStream(data.getData(),"wt")){
                    if(out==null)throw new Exception("Нема пристап до фајлот.");out.write(bytes);out.flush();
                    runOnUiThread(()->webView.evaluateJavascript("window.onExportComplete&&onExportComplete("+JSONObject.quote(name)+")",null));
                }catch(Exception e){error("Фајлот не се зачува. Провери го просторот и избраната папка.");}},"wf-save").start();
            }
        }else if(request==OPEN_BACKUP&&result==RESULT_OK&&data!=null&&data.getData()!=null){
            Uri uri=data.getData();new Thread(()->{
                try(InputStream in=getContentResolver().openInputStream(uri);ByteArrayOutputStream b=new ByteArrayOutputStream()){
                    if(in==null)throw new Exception();byte[] buffer=new byte[8192];int n;
                    while((n=in.read(buffer))!=-1){b.write(buffer,0,n);if(b.size()>MAX_BYTES)throw new Exception();}
                    String text=new String(b.toByteArray(),StandardCharsets.UTF_8);
                    runOnUiThread(()->webView.evaluateJavascript("window.handleImport&&handleImport("+JSONObject.quote(text)+")",null));
                }catch(Exception e){error("Бекапот не се прочита. Избери валиден JSON бекап.");}
            },"wf-import").start();
        }
    }
    @Override public void onBackPressed(){
        if(!ready){super.onBackPressed();return;}
        webView.evaluateJavascript("window.onNativeBack?onNativeBack():false",value->{if(!"true".equals(value))super.onBackPressed();});
    }
    @Override protected void onResume(){super.onResume();if(ready&&webView!=null)webView.evaluateJavascript("if(typeof render==='function'&&document.getElementById('modal').hidden)render()",null);}
    @Override protected void onDestroy(){if(webView!=null){webView.removeJavascriptInterface("Android");webView.destroy();}super.onDestroy();}

    static class PdfReport {
        static final int WIDTH=595,HEIGHT=842,TEAL=Color.rgb(0,125,120),INK=Color.rgb(21,46,53),GRAY=Color.rgb(102,120,127);
        static Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        static void text(Canvas c,String s,float x,float y,int size,int color,boolean bold){paint.setTextSize(size);paint.setColor(color);paint.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));c.drawText(s,x,y,paint);}
        static void rect(Canvas c,float x,float y,float w,float h,int color){paint.setColor(color);c.drawRect(x,y,x+w,y+h,paint);}
        static String fit(String s,int size,int max){paint.setTextSize(size);paint.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));if(paint.measureText(s)<=max)return s;while(s.length()>0&&paint.measureText(s+"…")>max)s=s.substring(0,s.length()-1);return s+"…";}
        static java.util.List<String> wrap(String s,int size,int width){java.util.List<String> lines=new java.util.ArrayList<>();paint.setTextSize(size);paint.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));for(String paragraph:s.replace("\r","").split("\n",-1)){String line="";for(String word:paragraph.split(" ")){String next=line.isEmpty()?word:line+" "+word;if(paint.measureText(next)>width&&!line.isEmpty()){lines.add(line);line=word;}else line=next;while(paint.measureText(line)>width&&line.length()>1){int i=line.length();while(i>1&&paint.measureText(line.substring(0,i))>width)i--;lines.add(line.substring(0,i));line=line.substring(i);}}lines.add(line);}return lines;}
        static final int[] TRIP_WIDTHS={85,42,106,57,237};
        static java.util.List<java.util.List<String>> tripCells(JSONArray row){
            java.util.List<java.util.List<String>> cells=new java.util.ArrayList<>();
            for(int i=0;i<TRIP_WIDTHS.length;i++)cells.add(wrap(row.optString(i,""),8,TRIP_WIDTHS[i]-8));
            return cells;
        }
        static int tripHeight(java.util.List<java.util.List<String>> cells){int lines=1;for(java.util.List<String> cell:cells)lines=Math.max(lines,cell.size());return lines*12+8;}
        static byte[] create(JSONObject report)throws Exception {
            PdfDocument doc=new PdfDocument();ByteArrayOutputStream out=new ByteArrayOutputStream();
            try{
                JSONArray rows=report.getJSONArray("rows"),headers=report.getJSONArray("headers"),summary=report.getJSONArray("summary"),details=report.getJSONArray("details"),notes=report.getJSONArray("notes");
                JSONArray trips=report.optJSONArray("tripRows"),tripHeaders=report.optJSONArray("tripHeaders");
                if(trips==null)trips=new JSONArray();
                if(tripHeaders==null)tripHeaders=new JSONArray("[\"Датум на поаѓање\",\"Час\",\"Пристигнување\",\"На пат\",\"Белешка\"]");
                if(rows.length()+trips.length()>10000)throw new Exception("Периодот е предолг.");
                int row=0,tripRow=0,pageNo=0,lineIndex=0;java.util.List<String> noteLines=new java.util.ArrayList<>();
                for(int i=0;i<notes.length();i++)noteLines.addAll(wrap(notes.getString(i),8,527));
                boolean first=true;
                do{
                    pageNo++;PdfDocument.Page page=doc.startPage(new PdfDocument.PageInfo.Builder(WIDTH,HEIGHT,pageNo).create());Canvas c=page.getCanvas();
                    rect(c,0,0,WIDTH,7,TEAL);text(c,fit(report.optString("company","Workfashion").toUpperCase(),20,510),34,45,20,INK,true);
                    text(c,report.optString("title"),34,67,12,TEAL,true);
                    text(c,"Лична евиденција"+(report.optString("name").isEmpty()?"":" · "+report.optString("name")),34,87,10,GRAY,false);
                    text(c,"Период: "+report.optString("from")+" – "+report.optString("to"),34,104,9,GRAY,false);
                    int y=122;
                    if(first){
                        for(int i=0;i<Math.min(summary.length(),4);i++){JSONArray a=summary.getJSONArray(i);int x=34+i*134;rect(c,x,y,124,48,Color.rgb(235,246,243));text(c,a.getString(0),x+10,y+15,8,GRAY,false);text(c,a.getString(1),x+10,y+37,17,TEAL,true);}y+=65;
                        for(int i=0;i<details.length();i++){for(String line:wrap(details.getString(i),8,527)){text(c,line,34,y,8,GRAY,false);y+=13;}}y+=14;
                    }
                    if(row<rows.length()||first){
                        int[] widths={43,124,80,56,56,56,48,64};int x=34;
                        rect(c,34,y,527,23,TEAL);for(int i=0;i<8;i++){text(c,headers.getString(i),x+4,y+15,8,Color.WHITE,true);x+=widths[i];}y+=23;
                        while(row<rows.length()&&y+19<775){JSONArray a=rows.getJSONArray(row);if(row%2==0)rect(c,34,y,527,17,Color.rgb(245,248,249));x=34;for(int i=0;i<8;i++){text(c,fit(a.optString(i,""),8,widths[i]-8),x+4,y+12,8,INK,false);x+=widths[i];}row++;y+=17;}
                        if(rows.length()==0&&first){text(c,"Нема записи во овој период.",34,y+20,10,GRAY,false);y+=32;}
                    }
                    if(row==rows.length()&&tripRow<trips.length()&&y+47+tripHeight(tripCells(trips.getJSONArray(tripRow)))<775){
                        y+=20;text(c,tripRow==0?"Патувања":"Патувања · продолжение",34,y,10,TEAL,true);y+=10;
                        rect(c,34,y,527,23,TEAL);int x=34;for(int i=0;i<TRIP_WIDTHS.length;i++){text(c,fit(tripHeaders.optString(i,""),8,TRIP_WIDTHS[i]-8),x+4,y+15,8,Color.WHITE,true);x+=TRIP_WIDTHS[i];}y+=23;
                        while(tripRow<trips.length()){
                            java.util.List<java.util.List<String>> cells=tripCells(trips.getJSONArray(tripRow));int height=tripHeight(cells);if(y+height>=775)break;
                            if(tripRow%2==0)rect(c,34,y,527,height,Color.rgb(235,246,243));x=34;
                            for(int i=0;i<cells.size();i++){int lineY=y+13;for(String line:cells.get(i)){text(c,line,x+4,lineY,8,INK,false);lineY+=12;}x+=TRIP_WIDTHS[i];}
                            tripRow++;y+=height;
                        }
                    }
                    if(row==rows.length()&&tripRow==trips.length()&&lineIndex<noteLines.size()&&y+40<775){y+=20;text(c,"Белешки",34,y,9,TEAL,true);y+=16;while(lineIndex<noteLines.size()&&y+12<775){text(c,noteLines.get(lineIndex++),34,y,8,GRAY,false);y+=12;}}
                    rect(c,34,793,527,1,Color.rgb(218,228,231));text(c,"Извезено: "+report.optString("generated"),34,812,8,GRAY,false);text(c,"Workfashion · Мои часови  |  "+pageNo,390,812,8,GRAY,false);
                    doc.finishPage(page);first=false;
                }while(row<rows.length()||tripRow<trips.length()||lineIndex<noteLines.size());
                doc.writeTo(out);return out.toByteArray();
            }finally{doc.close();out.close();}
        }
    }
}
