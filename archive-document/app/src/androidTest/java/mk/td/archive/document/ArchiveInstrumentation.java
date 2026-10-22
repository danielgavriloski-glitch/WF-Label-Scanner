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
import org.json.*;
import android.database.sqlite.SQLiteDatabase;

/** End-to-end checks on a real Android runtime, without test framework dependencies. */
public class ArchiveInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    void check(boolean ok,String why) throws Exception {if(!ok)throw new Exception(why);}
    Button find(View view,String label){if(view instanceof Button&&((Button)view).getText().toString().equals(label))return(Button)view;if(view instanceof ViewGroup){ViewGroup p=(ViewGroup)view;for(int i=0;i<p.getChildCount();i++){Button b=find(p.getChildAt(i),label);if(b!=null)return b;}}return null;}
    MainActivity launch(){return(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}
    String zipText(File file,String name) throws Exception {try(ZipFile z=new ZipFile(file)){ZipEntry e=z.getEntry(name);check(e!=null,"Missing ZIP part: "+name);try(InputStream in=z.getInputStream(e);ByteArrayOutputStream out=new ByteArrayOutputStream()){Store.copy(in,out,10000000);return out.toString("UTF-8");}}}
    byte[] bytes(File f) throws Exception {try(InputStream in=new FileInputStream(f);ByteArrayOutputStream out=new ByteArrayOutputStream()){Store.copy(in,out,10000000);return out.toByteArray();}}
    void exportFixture(File f,String name) throws Exception {File dest=new File(getTargetContext().getExternalFilesDir(null),name);try(InputStream in=new FileInputStream(f);OutputStream out=new FileOutputStream(dest)){Store.copy(in,out,10000000);}}
    void migrationFixture() throws Exception {
        Context context=getTargetContext();File path=context.getDatabasePath("archive.db");path.getParentFile().mkdirs();context.deleteDatabase("archive.db");
        String record=UUID.randomUUID().toString(),asset=UUID.randomUUID().toString();File media=new File(context.getFilesDir(),"media");media.mkdirs();File photo=new File(media,UUID.randomUUID()+".jpg");
        Bitmap b=Bitmap.createBitmap(80,60,Bitmap.Config.ARGB_8888);b.eraseColor(Color.GREEN);try(OutputStream out=new FileOutputStream(photo)){b.compress(Bitmap.CompressFormat.JPEG,90,out);}b.recycle();byte[] original=bytes(photo);
        try(SQLiteDatabase old=SQLiteDatabase.openOrCreateDatabase(path,null)){
            old.execSQL("CREATE TABLE records(id TEXT PRIMARY KEY,title TEXT NOT NULL,reference TEXT NOT NULL,category TEXT NOT NULL,notes TEXT NOT NULL,date TEXT NOT NULL,status TEXT NOT NULL,created INTEGER NOT NULL,updated INTEGER NOT NULL)");
            old.execSQL("CREATE TABLE assets(id TEXT PRIMARY KEY,record_id TEXT NOT NULL REFERENCES records(id) ON DELETE CASCADE,path TEXT NOT NULL UNIQUE,name TEXT NOT NULL,mime TEXT NOT NULL,caption TEXT NOT NULL,kind TEXT NOT NULL,created INTEGER NOT NULL)");
            old.execSQL("INSERT INTO records VALUES(?,?,?,?,?,?,?,?,?)",new Object[]{record,"Стар запис","LEGACY","","Стар опис","2026-10-22","draft",1,1});
            old.execSQL("INSERT INTO assets VALUES(?,?,?,?,?,?,?,?)",new Object[]{asset,record,"media/"+photo.getName(),"OLD_CAMERA.jpg","image/jpeg","Зачуван опис","photo",1});old.setVersion(1);
        }
        try(Store upgraded=new Store(context)){
            check(upgraded.get(record).fields.equals("[]"),"Version 1 record not migrated");Store.Asset a=upgraded.assets(record).get(0);File owned=upgraded.file(a);
            check(a.path.startsWith("media/"+record+"/")&&!photo.exists(),"Legacy photo not moved into record folder");check(Arrays.equals(original,bytes(owned))&&a.caption.equals("Зачуван опис"),"Migration changed photo or description");
            Uri uri=ArchiveProvider.uri(context,owned);check(uri.getPathSegments().size()==3,"Record-folder provider URI missing");
            try(InputStream in=context.getContentResolver().openInputStream(uri)){check(in!=null&&in.read()>=0,"Provider cannot open migrated photo");}
            upgraded.deleteRecord(record);
        }
    }
    void legacyBackup(Store db,Store.Record record,Store.Asset photo) throws Exception {
        JSONObject root=new JSONObject();root.put("format","mk.td.archive.document");root.put("version",1);String id=UUID.randomUUID().toString();
        JSONObject row=new JSONObject();row.put("id",id);row.put("title","Стара копија");row.put("reference","");row.put("category","");row.put("notes","");row.put("date",record.date);row.put("status","draft");row.put("created",1);row.put("updated",1);root.put("records",new JSONArray().put(row));
        String path="media/"+UUID.randomUUID()+".jpg";JSONObject a=new JSONObject();a.put("id",UUID.randomUUID().toString());a.put("recordId",id);a.put("path",path);a.put("name","legacy.jpg");a.put("mime","image/jpeg");a.put("caption","Стар опис");a.put("kind","photo");a.put("created",1);root.put("assets",new JSONArray().put(a));
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(ZipOutputStream zip=new ZipOutputStream(bytes)){Exports.entry(zip,"records.json",root.toString());zip.putNextEntry(new ZipEntry(path));zip.write(bytes(db.file(photo)));zip.closeEntry();}
        check(ArchiveBackup.restore(db,new ByteArrayInputStream(bytes.toByteArray()))==1,"Version 1 backup not supported");Store.Asset restored=db.assets(id).get(0);
        check(db.get(id).fields.equals("[]")&&restored.fields.equals("{}")&&restored.path.startsWith("media/"+id+"/"),"Version 1 backup fields/folder incorrect");db.deleteRecord(id);
    }
    @Override public void onStart(){
        Bundle result=new Bundle();
        try{
            migrationFixture();
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
            check(photo.path.startsWith("media/"+rid+"/"),"Camera not in its record folder");
            CardFields.add(db,r,"Шифра");CardFields.add(db,r,"Материјал-бр.");CardFields.add(db,r,"Назив");CardFields.add(db,r,"Големина");
            List<CardFields.Field> defs=CardFields.list(r);JSONObject values=new JSONObject();values.put(defs.get(0).id,"STAR 3-A");values.put(defs.get(1).id,"");values.put(defs.get(2).id,"Federose");values.put(defs.get(3).id,"10.5 mm");db.describe(photo,photo.caption,values.toString());
            CardFields.rename(db,r,defs.get(0),"Ознака");check(CardFields.value(photo,CardFields.list(r).get(0)).equals("STAR 3-A"),"Rename lost field value");check(CardFields.value(photo,defs.get(1)).isEmpty(),"Blank field fabricated a value");
            legacyBackup(db,r,photo);runOnMainSync(()->{a.current=a.store.get(rid);a.showFiles();});
            // Camera cancellation must not insert an empty asset.
            ActivityMonitor canceled=new ActivityMonitor(new IntentFilter(MediaStore.ACTION_IMAGE_CAPTURE),new ActivityResult(Activity.RESULT_CANCELED,null),true);
            addMonitor(canceled);runOnMainSync(()->a.takePhoto());Thread.sleep(250);waitForIdleSync();removeMonitor(canceled);check(db.assets(rid).size()==1,"Canceled camera inserted a file");

            for(String ext:new String[]{"pdf","docx","xlsx","card"}){
                File f=Exports.create(db,db.get(rid),ext);check(f.length()>300,"Empty export: "+ext);
                if(ext.equals("docx")){String xml=zipText(f,"word/document.xml");check(xml.contains("Охрид")&&xml.contains("photo1")&&xml.contains("STAR 3-A")&&xml.contains("Материјал-бр.")&&!xml.contains(photo.name),"DOCX lost fields or exposed photo filename");}
                if(ext.equals("xlsx")){String xml=zipText(f,"xl/worksheets/sheet1.xml");check(xml.contains("Охрид")&&xml.contains("inlineStr")&&xml.contains("STAR 3-A")&&xml.contains("Материјал-бр.")&&!xml.contains(photo.name),"XLSX lost fields or exposed photo filename");}
                if(ext.equals("pdf")||ext.equals("card")){try(ParcelFileDescriptor fd=ParcelFileDescriptor.open(f,ParcelFileDescriptor.MODE_READ_ONLY);PdfRenderer renderer=new PdfRenderer(fd)){check(renderer.getPageCount()>=(ext.equals("card")?1:2),"PDF photos missing");}}
                exportFixture(f,ext.equals("card")?"sample-card.pdf":"sample."+ext);
                byte[] original=bytes(f);Store.Asset attached;try(InputStream in=new FileInputStream(f)){attached=db.importStream(rid,in,"Документ."+(ext.equals("card")?"pdf":ext),Exports.mime(ext));}f.delete();check(Arrays.equals(original,bytes(db.file(attached))),"Imported attachment is not an owned copy: "+ext);check(attached.path.startsWith("media/"+rid+"/"),"Import not in record folder");
            }
            check(db.assets(rid).size()==5,"Document attachments missing");
            String[] denied={"../archive.db","media/../../archive.db","media/../archive.db","media/id/../../archive.db","media/id/../photo.jpg","media/id/nested/photo.jpg"};for(String path:denied){boolean failed=false;try{Store.safeFile(getTargetContext(),path);}catch(IOException e){failed=true;}check(failed,"Path traversal accepted");}
            // A fresh DB helper must see the exact same durable IDs and descriptions.
            db.close();db=new Store(getTargetContext());check(db.get(rid)!=null&&db.assets(rid).get(0).caption.contains("опис"),"Reopening DB lost record/caption");

            File backup=ArchiveBackup.create(db);exportFixture(backup,"sample-backup.zip");
            List<Store.Asset> before=db.assets(rid);byte[] photoBytes=bytes(db.file(before.get(0)));
            db.deleteRecord(rid);check(db.get(rid)==null,"Delete fixture failed");
            try(InputStream in=new FileInputStream(backup)){check(ArchiveBackup.restore(db,in)==1,"Backup restore failed");}
            check(db.get(rid)!=null&&db.assets(rid).size()==5,"Restore missing records/documents");check(Arrays.equals(photoBytes,bytes(db.file(db.assets(rid).get(0)))),"Photo bytes changed during restore");
            try(InputStream in=new FileInputStream(backup)){check(ArchiveBackup.restore(db,in)==0,"Restore duplicated existing record");}
            check(db.records("","").size()==1,"Duplicate record after reimport");
            Store.Record restoredRecord=db.get(rid);Store.Asset restoredPhoto=db.assets(rid).get(0);
            check(CardFields.list(restoredRecord).size()==4&&CardFields.value(restoredPhoto,CardFields.list(restoredRecord).get(0)).equals("STAR 3-A"),"Backup lost custom fields");
            // Multiple photos, more than three columns, and very long values must paginate.
            Store.Record many=db.create();many.title="Проверка на картичка";many.fields=restoredRecord.fields;db.save(many);
            for(int i=0;i<7;i++){Store.Asset copy;try(InputStream in=new FileInputStream(db.file(restoredPhoto))){copy=db.importStream(many.id,in,"DO_NOT_PRINT_"+i+".jpg","image/jpeg");}db.caption(copy,i==6?"ПОСЛЕДНА_СЛИКА":"Опис "+(i+1));
                if(i==0){JSONObject longValues=new JSONObject();StringBuilder longText=new StringBuilder();for(int j=0;j<200;j++)longText.append("Податок за алатот ");longText.append("КРАЈ_ПОЛЕ");longValues.put(CardFields.list(many).get(0).id,longText.toString());db.describe(copy,copy.caption,longValues.toString());}}
            File large=Exports.create(db,many,"card");try(ParcelFileDescriptor fd=ParcelFileDescriptor.open(large,ParcelFileDescriptor.MODE_READ_ONLY);PdfRenderer renderer=new PdfRenderer(fd)){check(renderer.getPageCount()>=3,"Long card data did not paginate");}exportFixture(large,"sample-card-long.pdf");
            List<File> cards=ArchiveCards.images(db,many,large);check(!cards.isEmpty(),"No JPG card images");
            for(File jpg:cards){BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeFile(jpg.getPath(),bounds);check(bounds.outWidth==1785&&bounds.outHeight==2526,"JPG card dimensions wrong");db.attachCardImage(many.id,jpg,"card.jpg","Архивска картичка");}
            int originalPhotos=0;for(Store.Asset asset:db.assets(many.id))if(asset.kind.equals("photo"))originalPhotos++;check(originalPhotos==7,"Generated JPG cards became source photographs");
            large.delete();db.deleteRecord(many.id);
            ByteArrayOutputStream unsafe=new ByteArrayOutputStream();try(ZipOutputStream z=new ZipOutputStream(unsafe)){z.putNextEntry(new ZipEntry("../outside.txt"));z.write("unsafe".getBytes(StandardCharsets.UTF_8));z.closeEntry();}
            boolean rejected=false;try{ArchiveBackup.restore(db,new ByteArrayInputStream(unsafe.toByteArray()));}catch(Exception e){rejected=true;}check(rejected&&db.records("","").size()==1,"Unsafe backup accepted or damaged DB");
            runOnMainSync(()->{a.current=a.store.get(rid);a.showEdit();a.notes.append("\nДополнето по продолжување.");find(a.root,"Продолжи →").performClick();});
            check(db.get(rid).notes.contains("Дополнето"),"Continue did not save edits");
            runOnMainSync(()->find(a.root,"Заврши и зачувај запис").performClick());check(db.get(rid).status.equals("done"),"Final save did not finish record");
            runOnMainSync(()->{a.current=a.store.get(rid);a.showFiles();});waitForIdleSync();
            Thread.sleep(700);Bitmap screen=getUiAutomation().takeScreenshot();try(OutputStream out=new FileOutputStream(new File(getTargetContext().getExternalFilesDir(null),"archive-files.png"))){screen.compress(Bitmap.CompressFormat.PNG,100,out);}screen.recycle();
            // Leave a saved session for an external force-stop/restart check.
            check(!a.prefs().getString("activeRecord","").isEmpty(),"Active record not durable");
            result.putString("stream","ARHIVA_SMOKE_OK: draft, resume, camera success/cancel, captions, custom/blank fields, field rename, card PDF/JPG pagination, no report filenames, per-record folders, v1 DB migration, v1/v2 backup restore, PDF, DOCX, XLSX, copied attachments, duplicate prevention, unsafe ZIP rejection, finish.\n");
            finish(Activity.RESULT_OK,result);
        }catch(Throwable e){result.putString("stream","ARHIVA_SMOKE_FAILED: "+e+"\n"+android.util.Log.getStackTraceString(e));finish(Activity.RESULT_CANCELED,result);}
    }
}
