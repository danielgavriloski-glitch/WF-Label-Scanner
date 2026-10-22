package mk.td.archive.document;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;

public final class ArchiveBackup {
    static final long MAX_TOTAL=4L*1024*1024*1024;
    public static File create(Store store) throws Exception {
        synchronized(store){
            File dir=new File(store.context.getFilesDir(),"exports");if(!dir.exists()&&!dir.mkdirs())throw new IOException("Не може да се зачува.");
            File[] previous=dir.listFiles();if(previous!=null)for(File f:previous)if(f.getName().endsWith(".zip"))f.delete();
            File file=new File(dir,UUID.randomUUID()+".zip");
            try(ZipOutputStream z=new ZipOutputStream(new FileOutputStream(file))){
                JSONObject root=new JSONObject();root.put("format","mk.td.archive.document");root.put("version",2);JSONArray rows=new JSONArray(),assets=new JSONArray();
                for(Store.Record r:store.records("","")){
                    JSONObject row=new JSONObject();row.put("id",r.id);row.put("title",r.title);row.put("reference",r.reference);row.put("category",r.category);row.put("notes",r.notes);row.put("date",r.date);row.put("status",r.status);row.put("created",r.created);row.put("updated",r.updated);row.put("fields",new JSONArray(r.fields));rows.put(row);
                    for(Store.Asset a:store.assets(r.id)){
                        File source=store.file(a);if(!source.isFile())throw new IOException("Недостига датотека: "+a.name);
                        JSONObject item=new JSONObject();item.put("id",a.id);item.put("recordId",r.id);item.put("path",a.path);item.put("name",a.name);item.put("mime",a.mime);item.put("caption",a.caption);item.put("kind",a.kind);item.put("created",a.created);item.put("fields",new JSONObject(a.fields));assets.put(item);
                        z.putNextEntry(new ZipEntry(a.path));try(InputStream in=new FileInputStream(source)){Store.copy(in,z,Long.MAX_VALUE);}z.closeEntry();
                    }
                }
                root.put("records",rows);root.put("assets",assets);Exports.entry(z,"records.json",root.toString());
            }catch(Exception e){file.delete();throw e;}return file;
        }
    }
    public static int restore(Store store,InputStream source) throws Exception {
        if(source==null)throw new IOException("Не може да се отвори копијата.");
        File stage=new File(store.context.getCacheDir(),"restore-"+UUID.randomUUID());if(!stage.mkdirs())throw new IOException("Не може да се подготви враќањето.");
        List<File> installed=new ArrayList<>();boolean success=false;
        try{
            Set<String> entries=new HashSet<>();String manifest=null;long total=0;
            try(ZipInputStream z=new ZipInputStream(source)){
                ZipEntry e;int count=0;
                while((e=z.getNextEntry())!=null){
                    if(++count>20000)throw new IOException("Премногу датотеки во копијата.");
                    String name=e.getName();if(e.isDirectory()&&(name.equals("media/")||name.matches("media/[a-zA-Z0-9-]{1,80}/")))continue;
                    if(!name.equals("records.json")&&!name.matches("media/(?:[a-zA-Z0-9-]{1,80}/)?[a-zA-Z0-9-]+\\.[a-zA-Z0-9]{1,10}"))throw new IOException("Невалидна патека во копијата.");
                    if(!entries.add(name))throw new IOException("Дупликат во копијата.");
                    File f=new File(stage,name);if(!f.getParentFile().isDirectory()&&!f.getParentFile().mkdirs())throw new IOException("Не може да се подготви папката.");
                    long limit=name.equals("records.json")?8L*1024*1024:256L*1024*1024;
                    try(FileOutputStream out=new FileOutputStream(f)){Store.copy(z,out,limit);}
                    total+=f.length();if(total>MAX_TOTAL)throw new IOException("Копијата е поголема од 4 GB.");
                    if(name.equals("records.json")){ByteArrayOutputStream b=new ByteArrayOutputStream();try(InputStream in=new FileInputStream(f)){Store.copy(in,b,8L*1024*1024);}manifest=b.toString("UTF-8");}
                }
            }
            if(manifest==null)throw new IOException("Ова не е резервна копија од Архива.");
            JSONObject root=new JSONObject(manifest);if(!root.optString("format").equals("mk.td.archive.document")||(root.optInt("version")!=1&&root.optInt("version")!=2))throw new IOException("Неподдржана резервна копија.");
            JSONArray rows=root.getJSONArray("records"),assets=root.getJSONArray("assets");int added=0;
            synchronized(store){
                SQLiteDatabase db=store.getWritableDatabase();db.beginTransaction();
                try{
                    Set<String> newRecords=new HashSet<>();
                    for(int i=0;i<rows.length();i++){
                        JSONObject x=rows.getJSONObject(i);String id=x.getString("id");if(!id.matches("[a-zA-Z0-9-]{1,80}"))throw new IOException("Невалиден запис.");if(store.get(id)!=null)continue;
                        Store.Record r=new Store.Record();r.id=id;r.title=x.getString("title");r.reference=x.getString("reference");r.category=x.getString("category");r.notes=x.getString("notes");r.date=x.getString("date");r.status=x.getString("status");
                        if(!r.status.equals("draft")&&!r.status.equals("done"))throw new IOException("Невалиден статус.");
                        r.fields=x.has("fields")?x.getJSONArray("fields").toString():"[]";CardFields.list(r);r.created=x.getLong("created");r.updated=x.getLong("updated");db.insertOrThrow("records",null,Store.values(r));newRecords.add(id);added++;
                    }
                    for(int i=0;i<assets.length();i++){
                        JSONObject x=assets.getJSONObject(i);String rid=x.getString("recordId");if(!newRecords.contains(rid)){if(store.get(rid)==null)throw new IOException("Прилог без запис.");continue;}
                        String path=x.getString("path");if(!entries.contains(path)||!path.matches("media/(?:[a-zA-Z0-9-]{1,80}/)?[a-zA-Z0-9-]+\\.[a-zA-Z0-9]{1,10}"))throw new IOException("Недостига прилог во копијата.");
                        File staged=new File(stage,path);File dest=store.mediaFile(rid,path.substring(path.lastIndexOf('.')+1));
                        if(!staged.renameTo(dest))throw new IOException("Не може да се врати прилогот.");installed.add(dest);
                        ContentValues v=new ContentValues();v.put("id",x.getString("id"));v.put("record_id",rid);v.put("path",store.relative(dest));v.put("name",x.getString("name"));v.put("mime",x.getString("mime"));v.put("caption",x.getString("caption"));v.put("kind",x.getString("kind"));v.put("created",x.getLong("created"));v.put("fields",x.has("fields")?x.getJSONObject("fields").toString():"{}");db.insertOrThrow("assets",null,v);
                    }
                    db.setTransactionSuccessful();
                }finally{db.endTransaction();}
            }
            success=true;return added;
        }finally{if(!success)for(File f:installed)f.delete();removeStage(stage);}
    }
    static void removeStage(File f){File[] children=f.listFiles();if(children!=null)for(File child:children)removeStage(child);f.delete();}
}
