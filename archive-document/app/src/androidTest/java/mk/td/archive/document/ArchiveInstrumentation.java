package mk.td.archive.document;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;

/** End-to-end checks on a real Android runtime, without test framework dependencies. */
public class ArchiveInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    void check(boolean ok,String why) throws Exception {if(!ok)throw new Exception(why);}
    Button find(View view,String label){if(view instanceof Button&&((Button)view).getText().toString().equals(label))return(Button)view;if(view instanceof ViewGroup){ViewGroup p=(ViewGroup)view;for(int i=0;i<p.getChildCount();i++){Button b=find(p.getChildAt(i),label);if(b!=null)return b;}}return null;}
    MainActivity launch(){return(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}
    String zipText(File file,String name) throws Exception {try(ZipFile z=new ZipFile(file)){ZipEntry e=z.getEntry(name);check(e!=null,"Missing ZIP part: "+name);try(InputStream in=z.getInputStream(e);ByteArrayOutputStream out=new ByteArrayOutputStream()){Store.copy(in,out,10000000);return out.toString("UTF-8");}}}
    byte[] bytes(File f) throws Exception {try(InputStream in=new FileInputStream(f);ByteArrayOutputStream out=new ByteArrayOutputStream()){Store.copy(in,out,10000000);return out.toByteArray();}}
    void exportFixture(File f,String name) throws Exception {File dest=new File(getTargetContext().getExternalFilesDir(null),name);try(InputStream in=new FileInputStream(f);OutputStream out=new FileOutputStream(dest)){Store.copy(in,out,10000000);}}
    @Override public void onStart(){
        Bundle result=new Bundle();
        try{
            MainActivity first=launch();waitForIdleSync();
            runOnMainSync(()->{find(first.root,"+ Нов запис").performClick();first.title.setText("Тест Архива & документ");first.notes.setText("Информации за сликите: свадба во Охрид.\nПродолжување подоцна.");first.reference.setText("AR-001");find(first.root,"Зачувај за подоцна").performClick();});
            Store db=new Store(getTargetContext());List<Store.Record> saved=db.records("AR-001","");check(saved.size()==1,"Draft not saved");
            Store.Record r=saved.get(0);String rid=r.id;check(r.status.equals("draft")&&r.notes.contains("Охрид"),"Draft fields missing");
            runOnMainSync(()->first.finish());waitForIdleSync();
            MainActivity a=launch();waitForIdleSync();
            check(a.page.equals("home"),"Home not restored");
            runOnMainSync(()->find(a.root,"Продолжи").performClick());check(a.current.id.equals(rid)&&a.page.equals("files"),"Resume wrong record");

            final boolean[] captured={false};
            ActivityMonitor camera=new ActivityMonitor(){@Override public ActivityResult onStartActivity(Intent i){
                if(MediaStore.ACTION_IMAGE_CAPTURE.equals(i.getAction())){
                    try{Uri uri=i.getParcelableExtra(MediaStore.EXTRA_OUTPUT);if(uri==null)throw new IOException("No full-size output URI");
                        if((i.getFlags()&Intent.FLAG_GRANT_WRITE_URI_PERMISSION)==0)throw new IOException("No camera write grant");
                        Bitmap b=Bitmap.createBitmap(1200,900,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(b);c.drawColor(Color.rgb(55,100,125));Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(Color.WHITE);p.setTextSize(72);c.drawText("Фотографија",80,420,p);
                        try(OutputStream out=getTargetContext().getContentResolver().openOutputStream(uri)){b.compress(Bitmap.CompressFormat.JPEG,90,out);}b.recycle();captured[0]=true;
                        return new ActivityResult(Activity.RESULT_OK,null);
                    }catch(Exception e){throw new RuntimeException(e);}
                }return null;
            }};
            addMonitor(camera);runOnMainSync(()->a.takePhoto());
            for(int i=0;i<40&&db.assets(rid).isEmpty();i++){Thread.sleep(100);waitForIdleSync();}
            removeMonitor(camera);check(captured[0]&&db.assets(rid).size()==1,"Full-size camera callback failed");
            sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);waitForIdleSync();
            Store.Asset photo=db.assets(rid).get(0);db.caption(photo,"Слика 1 – опис на фотографијата");check(photo.kind.equals("photo")&&db.file(photo).length()>2000,"Camera file missing");
            // Camera cancellation must not insert an empty asset.
            ActivityMonitor canceled=new ActivityMonitor(new IntentFilter(MediaStore.ACTION_IMAGE_CAPTURE),new ActivityResult(Activity.RESULT_CANCELED,null),true);
            addMonitor(canceled);runOnMainSync(()->a.takePhoto());Thread.sleep(250);waitForIdleSync();removeMonitor(canceled);check(db.assets(rid).size()==1,"Canceled camera inserted a file");

            for(String ext:new String[]{"pdf","docx","xlsx"}){
                File f=Exports.create(db,db.get(rid),ext);check(f.length()>300,"Empty export: "+ext);
                if(ext.equals("docx")){String xml=zipText(f,"word/document.xml");check(xml.contains("Охрид")&&xml.contains("photo1"),"DOCX lost Cyrillic/photos");}
                if(ext.equals("xlsx")){String xml=zipText(f,"xl/worksheets/sheet1.xml");check(xml.contains("Охрид")&&xml.contains("inlineStr"),"XLSX lost literal text");}
                if(ext.equals("pdf")){try(ParcelFileDescriptor fd=ParcelFileDescriptor.open(f,ParcelFileDescriptor.MODE_READ_ONLY);PdfRenderer renderer=new PdfRenderer(fd)){check(renderer.getPageCount()>=2,"PDF photos missing");}}
                exportFixture(f,"sample."+ext);
                byte[] original=bytes(f);Store.Asset attached;try(InputStream in=new FileInputStream(f)){attached=db.importStream(rid,in,"Документ."+ext,Exports.mime(ext));}f.delete();check(Arrays.equals(original,bytes(db.file(attached))),"Imported attachment is not an owned copy: "+ext);
            }
            check(db.assets(rid).size()==4,"Document attachments missing");
            String[] denied={"../archive.db","media/../../archive.db","media/../archive.db"};for(String path:denied){boolean failed=false;try{Store.safeFile(getTargetContext(),path);}catch(IOException e){failed=true;}check(failed,"Path traversal accepted");}
            // A fresh DB helper must see the exact same durable IDs and descriptions.
            db.close();db=new Store(getTargetContext());check(db.get(rid)!=null&&db.assets(rid).get(0).caption.contains("опис"),"Reopening DB lost record/caption");

            File backup=ArchiveBackup.create(db);exportFixture(backup,"sample-backup.zip");
            List<Store.Asset> before=db.assets(rid);byte[] photoBytes=bytes(db.file(before.get(0)));
            db.deleteRecord(rid);check(db.get(rid)==null,"Delete fixture failed");
            try(InputStream in=new FileInputStream(backup)){check(ArchiveBackup.restore(db,in)==1,"Backup restore failed");}
            check(db.get(rid)!=null&&db.assets(rid).size()==4,"Restore missing records/documents");check(Arrays.equals(photoBytes,bytes(db.file(db.assets(rid).get(0)))),"Photo bytes changed during restore");
            try(InputStream in=new FileInputStream(backup)){check(ArchiveBackup.restore(db,in)==0,"Restore duplicated existing record");}
            check(db.records("","").size()==1,"Duplicate record after reimport");
            ByteArrayOutputStream unsafe=new ByteArrayOutputStream();try(ZipOutputStream z=new ZipOutputStream(unsafe)){z.putNextEntry(new ZipEntry("../outside.txt"));z.write("unsafe".getBytes(StandardCharsets.UTF_8));z.closeEntry();}
            boolean rejected=false;try{ArchiveBackup.restore(db,new ByteArrayInputStream(unsafe.toByteArray()));}catch(Exception e){rejected=true;}check(rejected&&db.records("","").size()==1,"Unsafe backup accepted or damaged DB");
            runOnMainSync(()->{a.current=a.store.get(rid);a.showEdit();a.notes.append("\nДополнето по продолжување.");find(a.root,"Продолжи →").performClick();});
            check(db.get(rid).notes.contains("Дополнето"),"Continue did not save edits");
            runOnMainSync(()->find(a.root,"Заврши и зачувај запис").performClick());check(db.get(rid).status.equals("done"),"Final save did not finish record");
            runOnMainSync(()->{a.current=a.store.get(rid);a.showFiles();});waitForIdleSync();
            Thread.sleep(700);Bitmap screen=getUiAutomation().takeScreenshot();try(OutputStream out=new FileOutputStream(new File(getTargetContext().getExternalFilesDir(null),"archive-files.png"))){screen.compress(Bitmap.CompressFormat.PNG,100,out);}screen.recycle();
            // Leave a saved session for an external force-stop/restart check.
            check(!a.prefs().getString("activeRecord","").isEmpty(),"Active record not durable");
            result.putString("stream","ARHIVA_SMOKE_OK: draft, resume, camera success/cancel, captions, PDF, DOCX, XLSX, copied attachments, backup restore, duplicate prevention, unsafe ZIP rejection, finish.\n");
            finish(Activity.RESULT_OK,result);
        }catch(Throwable e){result.putString("stream","ARHIVA_SMOKE_FAILED: "+e+"\n"+android.util.Log.getStackTraceString(e));finish(Activity.RESULT_CANCELED,result);}
    }
}
