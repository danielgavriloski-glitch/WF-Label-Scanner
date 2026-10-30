package mk.workfashion.personal;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
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
import java.io.BufferedInputStream;

public class MainActivity extends Activity {
    private static final int CREATE_FILE=71, OPEN_BACKUP=72, ROUTE_PERMISSION=73, OPEN_ROUTE_ARCHIVE=74, MAX_BYTES=16*1024*1024;
    WebView webView;
    private AtomicFile stateFile;
    private byte[] pendingBytes;
    private String pendingName;
    private boolean ready=false;
    private String pendingRouteTripId="";
    private static final int PICK_DOCUMENT=81,CAPTURE_DOCUMENT=82,SCAN_DOCUMENT=83,CREATE_ARCHIVE=84;
    private String documentVisit="",scanOutput="",archiveState="",archiveGps="",replaceDocumentId="";
    private File archiveStage(){return new File(getCacheDir(),"wfag-restore-stage");}
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        stateFile=new AtomicFile(new File(getFilesDir(), "workfashion-state.json"));
        documentVisit=getPreferences(0).getString("documentVisit","");
        scanOutput=getPreferences(0).getString("scanOutput","");
        replaceDocumentId=getPreferences(0).getString("replaceDocumentId","");
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
                if("https".equals(request.getUrl().getScheme())&&"wfag.local".equals(request.getUrl().getHost())&&request.getUrl().getPath()!=null&&request.getUrl().getPath().startsWith("/documents/")){
                    try{File f=Documents.file(MainActivity.this,request.getUrl().getLastPathSegment());return new WebResourceResponse(Documents.mime(f.getName()),null,new java.io.FileInputStream(f));}catch(Exception ignored){return new WebResourceResponse("text/plain","UTF-8",new ByteArrayInputStream(new byte[0]));}
                }
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

    private void startRouteService(String tripId){
        Intent i=new Intent(this,RouteTrackingService.class);
        i.setAction(RouteTrackingService.ACTION_START);
        i.putExtra("tripId",tripId==null?"":tripId);
        if(android.os.Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);
    }
    private void requestRouteStart(String tripId){
        runOnUiThread(()->{
            boolean noLocation=checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED&&
               checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED;
            boolean noNotifications=android.os.Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED;
            if(noLocation||noNotifications){
                pendingRouteTripId=tripId==null?"":tripId;
                if(android.os.Build.VERSION.SDK_INT>=33)requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.POST_NOTIFICATIONS},ROUTE_PERMISSION);
                else requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},ROUTE_PERMISSION);
            }else startRouteService(tripId);
        });
    }
    private void stopRouteService(){
        runOnUiThread(()->{
            RouteTrackingService.finishSession(this);
            stopService(new Intent(this,RouteTrackingService.class));
            if(ready&&webView!=null)webView.evaluateJavascript("window.onRouteNativeChanged&&onRouteNativeChanged()",null);
        });
    }
    public class LocalBridge {
        @JavascriptInterface public boolean deleteUnreferencedDocument(String fileId){
            try{
                File target=Documents.file(MainActivity.this,fileId);
                JSONObject current=new JSONObject(readState());
                JSONArray docs=Documents.documentList(current);
                for(int i=0;i<docs.length();i++)if(fileId.equals(docs.getJSONObject(i).optString("fileId")))return false;
                return !target.exists()||target.delete();
            }catch(Exception e){return false;}
        }
        @JavascriptInterface public void attachDocument(String visitId,String mode){runOnUiThread(()->chooseDocument(visitId,mode));}
        @JavascriptInterface public void openDocument(String fileId){runOnUiThread(()->{try{File f=Documents.file(MainActivity.this,fileId);if(!f.isFile())throw new Exception();Uri uri=Uri.parse("content://"+getPackageName()+".documents/documents/"+fileId);Intent i=new Intent(Intent.ACTION_VIEW);i.setDataAndType(uri,Documents.mime(fileId));i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(i);}catch(Exception e){error("Документот не се отвори. За PDF, Word или Excel избери соодветна апликација.");}});}
        @JavascriptInterface public void exportFullBackup(){runOnUiThread(()->{if(!archiveState.isEmpty()){error("Прво заврши го претходниот бекап.");return;}archiveState=readState();archiveGps=RouteTrackingService.stateJson(MainActivity.this);try{Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/zip");i.putExtra(Intent.EXTRA_TITLE,"WFAG_full_backup_"+new java.text.SimpleDateFormat("yyyy-MM-dd",java.util.Locale.ROOT).format(new java.util.Date())+".zip");startActivityForResult(i,CREATE_ARCHIVE);}catch(Exception e){archiveState="";archiveGps="";error("Бекапот не се отвори.");}});}
        @JavascriptInterface public boolean documentsAvailable(String log){try{return Documents.available(MainActivity.this,new JSONObject(log),archiveStage());}catch(Exception e){return false;}}
        @JavascriptInterface public boolean hasStagedArchive(){return new File(archiveStage(),"state.json").isFile();}
        @JavascriptInterface public void discardArchive(){Documents.removeTree(archiveStage());}
        @JavascriptInterface public boolean finishArchiveRestore(){try{Documents.installArchive(MainActivity.this,archiveStage());Documents.removeTree(archiveStage());return true;}catch(Exception e){error("Враќањето не заврши: "+e.getMessage());return false;}}
        @JavascriptInterface public String loadState(){return readState();}
        @JavascriptInterface public boolean saveState(String text){return writeState(text);}
        @JavascriptInterface public void exportText(String name,String text,String mime){requestSave(name,text.getBytes(StandardCharsets.UTF_8),mime);}
        @JavascriptInterface public void exportBytes(String name,String base64,String mime){try{requestSave(name,Base64.decode(base64,Base64.DEFAULT),mime);}catch(Exception e){error("Фајлот не се подготви.");}}
        @JavascriptInterface public void exportPdf(String name,String json){try{requestSave(name,PdfReport.create(new JSONObject(json)),"application/pdf");}catch(Exception e){error("PDF извештајот не се подготви: "+e.getMessage());}}
        @JavascriptInterface public void openBackup(){runOnUiThread(()->{try{Documents.removeTree(archiveStage());Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("*/*");i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"application/json","text/plain","application/octet-stream","application/zip","application/x-zip-compressed"});i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,OPEN_BACKUP);}catch(Exception e){error("Не се отвори изборот за бекап.");}});}
        @JavascriptInterface public void keepBeforeRestore(String text){try{if(text.length()<=MAX_BYTES){FileOutputStream out=new FileOutputStream(new File(getFilesDir(),"before-restore.json"));out.write(text.getBytes(StandardCharsets.UTF_8));out.close();}}catch(Exception ignored){}}
        @JavascriptInterface public String getRouteLog(){return RouteTrackingService.stateJson(MainActivity.this);}
        @JavascriptInterface public void openRouteArchive(){runOnUiThread(()->{try{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("application/json");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,OPEN_ROUTE_ARCHIVE);}catch(Exception e){error("Изборот на GPS архивата не се отвори.");}});}
        @JavascriptInterface public void startRouteTracking(String tripId){requestRouteStart(tripId);}
        @JavascriptInterface public void stopRouteTracking(){stopRouteService();}
        @JavascriptInterface public boolean setRouteStop(String stopId,String name,boolean remember){return RouteTrackingService.resolveStop(MainActivity.this,stopId,name,remember);}
        @JavascriptInterface public boolean renameSavedPlace(String placeId,String name){return RouteTrackingService.renameSavedPlace(MainActivity.this,placeId,name);}
        @JavascriptInterface public void openRouteMap(double lat,double lon){runOnUiThread(()->{try{Intent i=new Intent(MainActivity.this,RouteMapActivity.class);i.putExtra(RouteMapActivity.EXTRA_LAT,lat);i.putExtra(RouteMapActivity.EXTRA_LON,lon);startActivity(i);}catch(Exception e){error("Офлајн мапата не се отвори.");}});}
        @JavascriptInterface public void openRouteHistoryMap(String from,String to){runOnUiThread(()->{try{Intent i=new Intent(MainActivity.this,RouteMapActivity.class);i.putExtra(RouteMapActivity.EXTRA_FROM,from==null?"":from);i.putExtra(RouteMapActivity.EXTRA_TO,to==null?"":to);startActivity(i);}catch(Exception e){error("Офлајн мапата не се отвори.");}});}
        @JavascriptInterface public void openRouteSessionMap(String sessionId){runOnUiThread(()->{try{Intent i=new Intent(MainActivity.this,RouteMapActivity.class);i.putExtra(RouteMapActivity.EXTRA_SESSION_ID,sessionId==null?"":sessionId);startActivity(i);}catch(Exception e){error("GPS рутата не се отвори.");}});}
    }
    private void documentsChanged(String visit,String message){runOnUiThread(()->{if(webView!=null)webView.evaluateJavascript("window.onDocumentsChanged&&onDocumentsChanged("+JSONObject.quote(visit)+","+JSONObject.quote(message)+")",null);});}
    private void rememberDocumentRequest(){getPreferences(0).edit().putString("documentVisit",documentVisit).putString("scanOutput",scanOutput).putString("replaceDocumentId",replaceDocumentId).commit();}
    private void chooseDocument(String visitId,String mode){
        if(!documentVisit.isEmpty()){error("Прво заврши го претходното прикачување.");return;}
        try{JSONObject s=new JSONObject(readState());JSONArray visits=s.getJSONObject("warehouseLog").getJSONArray("visits");boolean found=false;for(int n=0;n<visits.length();n++)if(visitId.equals(visits.getJSONObject(n).optString("id")))found=true;if(!found)throw new Exception("Записот не е зачуван.");replaceDocumentId=mode!=null&&mode.startsWith("replace:")?mode.substring(8):"";if(!replaceDocumentId.isEmpty()){boolean exists=false;for(int n=0;n<visits.length();n++){JSONObject v=visits.getJSONObject(n);if(!visitId.equals(v.optString("id")))continue;JSONArray docs=v.getJSONArray("documents");for(int j=0;j<docs.length();j++)if(replaceDocumentId.equals(docs.getJSONObject(j).optString("id")))exists=true;}if(!exists)throw new Exception("Документот не е пронајден.");}documentVisit=visitId;rememberDocumentRequest();
            if("camera".equals(mode)){Intent i=new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);Uri uri=Uri.parse("content://"+getPackageName()+".documents/capture/camera.jpg");i.putExtra(android.provider.MediaStore.EXTRA_OUTPUT,uri);i.setClipData(android.content.ClipData.newRawUri("Скен",uri));i.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivityForResult(i,CAPTURE_DOCUMENT);}
            else{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("*/*");i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"image/*","application/pdf","application/msword","application/vnd.ms-excel","application/vnd.openxmlformats-officedocument.wordprocessingml.document","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet","text/plain"});i.addCategory(Intent.CATEGORY_OPENABLE);i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,replaceDocumentId.isEmpty());startActivityForResult(i,PICK_DOCUMENT);}
        }catch(Exception e){documentVisit="";replaceDocumentId="";rememberDocumentRequest();error("Прикачувањето не се отвори. Провери дали имаш камера или избери постоечки документ.");}
    }
    synchronized boolean appendDocuments(String visitId,JSONArray incoming)throws Exception{
        JSONObject s=new JSONObject(readState());JSONArray visits=s.getJSONObject("warehouseLog").getJSONArray("visits");for(int n=0;n<visits.length();n++){JSONObject v=visits.getJSONObject(n);if(!visitId.equals(v.optString("id")))continue;JSONArray docs=v.getJSONArray("documents");if(docs.length()+incoming.length()>100)throw new Exception("Најмногу 100 документи во еден запис.");for(int j=0;j<incoming.length();j++)docs.put(incoming.getJSONObject(j));v.put("updatedAt",new java.util.Date().toInstant().toString());if(!writeState(s.toString()))throw new Exception("Записот не се зачува.");return true;}throw new Exception("Записот не е пронајден.");
    }
    synchronized void replaceDocument(String visitId,String documentId,JSONObject incoming)throws Exception{
        JSONObject state=new JSONObject(readState());JSONArray visits=state.getJSONObject("warehouseLog").getJSONArray("visits");
        for(int i=0;i<visits.length();i++){
            JSONObject visit=visits.getJSONObject(i);if(!visitId.equals(visit.optString("id")))continue;
            JSONArray docs=visit.getJSONArray("documents");for(int j=0;j<docs.length();j++){
                JSONObject old=docs.getJSONObject(j);if(!documentId.equals(old.optString("id")))continue;
                String oldFile=old.getString("fileId");incoming.put("id",documentId);docs.put(j,incoming);
                visit.put("updatedAt",new java.util.Date().toInstant().toString());
                if(!writeState(state.toString()))throw new Exception("Замената не се зачува.");
                boolean used=false;JSONArray all=Documents.documentList(state);for(int k=0;k<all.length();k++)if(oldFile.equals(all.getJSONObject(k).optString("fileId")))used=true;
                if(!used)try{Documents.file(this,oldFile).delete();}catch(Exception ignored){}return;
            }
        }
        throw new Exception("Документот за замена не е пронајден.");
    }
    private boolean documentResult(int request,int result,Intent data){
        if(request==CREATE_ARCHIVE){String savedState=archiveState.isEmpty()?readState():archiveState,savedGps=archiveGps.isEmpty()?RouteTrackingService.stateJson(this):archiveGps;archiveState="";archiveGps="";if(result==RESULT_OK&&data!=null&&data.getData()!=null&&!savedState.isEmpty()){Uri uri=data.getData();new Thread(()->{try(OutputStream out=getContentResolver().openOutputStream(uri,"wt")){if(out==null)throw new Exception();Documents.exportArchive(this,out,savedState,savedGps);error("Целосниот бекап со документи е зачуван.");}catch(Exception e){error("Бекапот НЕ е завршен: "+e.getMessage());}},"wfag-full-backup").start();}return true;}
        if(request!=PICK_DOCUMENT&&request!=CAPTURE_DOCUMENT&&request!=SCAN_DOCUMENT)return false;
        final String visit=documentVisit,replacing=replaceDocumentId;
        if(result!=RESULT_OK){if(!scanOutput.isEmpty())try{Documents.file(this,scanOutput).delete();}catch(Exception ignored){}documentVisit="";scanOutput="";replaceDocumentId="";rememberDocumentRequest();documentsChanged(visit,"Прикачувањето е откажано. Записот остана зачуван.");return true;}
        if(request==CAPTURE_DOCUMENT){try{scanOutput=Documents.unique("jpg");rememberDocumentRequest();Intent i=new Intent(this,ScanActivity.class);i.putExtra("input",Uri.parse("content://"+getPackageName()+".documents/capture/camera.jpg").toString());i.putExtra("output",Documents.file(this,scanOutput).getAbsolutePath());startActivityForResult(i,SCAN_DOCUMENT);}catch(Exception e){documentVisit="";scanOutput="";replaceDocumentId="";rememberDocumentRequest();error("Скенерот не се отвори.");}return true;}
        final String scanned=scanOutput;
        new Thread(()->{JSONArray added=new JSONArray();try{if(request==SCAN_DOCUMENT){File f=Documents.file(this,scanned);added.put(Documents.metadata(f,"Скен_"+new java.text.SimpleDateFormat("yyyy-MM-dd_HHmmss",java.util.Locale.ROOT).format(new java.util.Date())+".jpg"));}else{if(data==null)throw new Exception("Нема избран документ.");android.content.ClipData clip=data.getClipData();int count=clip==null?1:clip.getItemCount();if(count>30)throw new Exception("Избери најмногу 30 документи одеднаш.");for(int n=0;n<count;n++){Uri uri=clip==null?data.getData():clip.getItemAt(n).getUri();if(uri==null)throw new Exception("Нема пристап.");added.put(Documents.importFile(this,uri));}}if(!replacing.isEmpty()){if(added.length()!=1)throw new Exception("Избери само еден документ за замена.");replaceDocument(visit,replacing,added.getJSONObject(0));documentsChanged(visit,"Документот е заменет.");}else{appendDocuments(visit,added);documentsChanged(visit,"Прикачени документи: "+added.length());}}catch(Exception e){for(int n=0;n<added.length();n++)try{Documents.file(this,added.getJSONObject(n).getString("fileId")).delete();}catch(Exception ignored){}documentsChanged(visit,"Документите не се прикачија: "+e.getMessage());}finally{documentVisit="";scanOutput="";replaceDocumentId="";rememberDocumentRequest();new File(getCacheDir(),"wfag-camera.jpg").delete();}},"wfag-documents").start();return true;
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
        if(documentResult(request,result,data))return;
        if(request==CREATE_FILE){
            byte[] bytes=pendingBytes;String name=pendingName;pendingBytes=null;pendingName=null;
            if(result==RESULT_OK&&data!=null&&data.getData()!=null&&bytes!=null){
                new Thread(()->{try(OutputStream out=getContentResolver().openOutputStream(data.getData(),"wt")){
                    if(out==null)throw new Exception("Нема пристап до фајлот.");out.write(bytes);out.flush();
                    runOnUiThread(()->webView.evaluateJavascript("window.onExportComplete&&onExportComplete("+JSONObject.quote(name)+")",null));
                }catch(Exception e){error("Фајлот не се зачува. Провери го просторот и избраната папка.");}},"wf-save").start();
            }
        }else if(request==OPEN_ROUTE_ARCHIVE&&result==RESULT_OK&&data!=null&&data.getData()!=null){
            Uri uri=data.getData();
            new Thread(()->{
                try(InputStream in=getContentResolver().openInputStream(uri);ByteArrayOutputStream out=new ByteArrayOutputStream()){
                    if(in==null)throw new Exception("Нема пристап.");
                    byte[] buffer=new byte[8192];int n;
                    while((n=in.read(buffer))!=-1){out.write(buffer,0,n);if(out.size()>MAX_BYTES)throw new Exception("Архивата е преголема.");}
                    int count=RouteTrackingService.importArchive(MainActivity.this,new String(out.toByteArray(),StandardCharsets.UTF_8));
                    runOnUiThread(()->{
                        webView.evaluateJavascript("window.onRouteNativeChanged&&onRouteNativeChanged()",null);
                        webView.evaluateJavascript("window.onNativeError&&onNativeError("+JSONObject.quote("Увезени "+count+" GPS патувања од архива.")+")",null);
                    });
                }catch(Exception e){error("GPS архивата не се увезе: "+e.getMessage());}
            },"wfag-import-gps").start();
        }else if(request==OPEN_BACKUP&&result==RESULT_OK&&data!=null&&data.getData()!=null){
            Uri uri=data.getData();new Thread(()->{
                try(BufferedInputStream in=new BufferedInputStream(getContentResolver().openInputStream(uri));ByteArrayOutputStream b=new ByteArrayOutputStream()){
                    if(in==null)throw new Exception();byte[] buffer=new byte[8192];int n;
                    in.mark(4);int first=in.read(),second=in.read();in.reset();
                    if(first=='P'&&second=='K'){String text=Documents.stageArchive(this,in,archiveStage());runOnUiThread(()->webView.evaluateJavascript("window.handleImport&&handleImport("+JSONObject.quote(text)+")",null));return;}
                    while((n=in.read(buffer))!=-1){b.write(buffer,0,n);if(b.size()>MAX_BYTES)throw new Exception();}
                    String text=new String(b.toByteArray(),StandardCharsets.UTF_8);
                    runOnUiThread(()->webView.evaluateJavascript("window.handleImport&&handleImport("+JSONObject.quote(text)+")",null));
                }catch(Exception e){Documents.removeTree(archiveStage());error("Бекапот не се прочита: "+e.getMessage());}
            },"wf-import").start();
        }
    }
    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grantResults){
        super.onRequestPermissionsResult(requestCode,permissions,grantResults);
        if(requestCode==ROUTE_PERMISSION){
            boolean locationGranted=checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED||
                checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED;
            if(locationGranted){String trip=pendingRouteTripId;pendingRouteTripId="";startRouteService(trip);}
            else{pendingRouteTripId="";error("За автоматско следење на рутата дозволи Location / Локација.");}
        }
    }
    @Override public void onBackPressed(){
        if(!ready){super.onBackPressed();return;}
        webView.evaluateJavascript("window.onNativeBack?onNativeBack():false",value->{if(!"true".equals(value))super.onBackPressed();});
    }
    @Override protected void onResume(){super.onResume();if(ready&&webView!=null)webView.evaluateJavascript("if(typeof render==='function'&&document.getElementById('modal').hidden)render();window.onRouteNativeChanged&&onRouteNativeChanged()",null);}
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
                final boolean overtimeTable=headers.length()==5;
                if(!overtimeTable&&headers.length()!=8)throw new Exception("Невалидни колони во извештајот.");
                final int[] widths=overtimeTable?new int[]{120,72,50,56,229}:new int[]{43,124,80,56,56,56,48,64};
                int row=0,rowLine=0,tripRow=0,pageNo=0,lineIndex=0;java.util.List<String> noteLines=new java.util.ArrayList<>();
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
                        for(int i=0;i<summary.length();i++){JSONArray a=summary.getJSONArray(i);int x=34+(i%4)*134,sy=y+(i/4)*60;rect(c,x,sy,124,48,Color.rgb(235,246,243));text(c,fit(a.getString(0),8,104),x+10,sy+15,8,GRAY,false);text(c,fit(a.getString(1),17,104),x+10,sy+37,17,TEAL,true);}y+=Math.max(1,(summary.length()+3)/4)*60+5;
                        for(int i=0;i<details.length();i++){for(String line:wrap(details.getString(i),8,527)){text(c,line,34,y,8,GRAY,false);y+=13;}}y+=14;
                    }
                    if(row<rows.length()||first){
                        int x=34;
                        rect(c,34,y,527,23,TEAL);for(int i=0;i<widths.length;i++){text(c,fit(headers.getString(i),8,widths[i]-8),x+4,y+15,8,Color.WHITE,true);x+=widths[i];}y+=23;
                        while(row<rows.length()&&y+20<775){
                            JSONArray a=rows.getJSONArray(row);x=34;
                            if(overtimeTable){
                                java.util.List<java.util.List<String>> cells=new java.util.ArrayList<>();int lines=1;
                                for(int i=0;i<widths.length;i++){java.util.List<String> cell=wrap(a.optString(i,""),8,widths[i]-8);cells.add(cell);lines=Math.max(lines,cell.size());}
                                int visible=Math.min(lines-rowLine,(775-y-8)/12),height=visible*12+8;
                                if(visible<=0)break;
                                if(row%2==0)rect(c,34,y,527,height,Color.rgb(245,248,249));
                                for(int i=0;i<cells.size();i++){java.util.List<String> cell=cells.get(i);for(int n=0;n<visible&&rowLine+n<cell.size();n++)text(c,cell.get(rowLine+n),x+4,y+13+n*12,8,INK,false);x+=widths[i];}
                                rowLine+=visible;y+=height;
                                if(rowLine>=lines){row++;rowLine=0;}
                            }else{
                                if(row%2==0)rect(c,34,y,527,17,Color.rgb(245,248,249));
                                for(int i=0;i<widths.length;i++){text(c,fit(a.optString(i,""),8,widths[i]-8),x+4,y+12,8,INK,false);x+=widths[i];}row++;y+=17;
                            }
                        }
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

