package mk.td.archive.document;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.io.*;
import java.util.*;

/** One durable ID per record and asset. All attachments are owned copies. */
public class Store extends SQLiteOpenHelper {
    public static class Record {
        public String id, title="", reference="", category="", notes="", date="", status="draft";
        public long created, updated;
    }
    public static class Asset {
        public String id, recordId, path, name, mime, caption="", kind;
        public long created;
    }
    final Context context;
    public Store(Context c) { super(c, "archive.db", null, 1); context=c.getApplicationContext(); }
    @Override public void onConfigure(SQLiteDatabase db) { db.setForeignKeyConstraintsEnabled(true); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE records (id TEXT PRIMARY KEY,title TEXT NOT NULL,reference TEXT NOT NULL,category TEXT NOT NULL,notes TEXT NOT NULL,date TEXT NOT NULL,status TEXT NOT NULL,created INTEGER NOT NULL,updated INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE assets (id TEXT PRIMARY KEY,record_id TEXT NOT NULL REFERENCES records(id) ON DELETE CASCADE,path TEXT NOT NULL UNIQUE,name TEXT NOT NULL,mime TEXT NOT NULL,caption TEXT NOT NULL,kind TEXT NOT NULL,created INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX assets_record ON assets(record_id,created,id)");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int old,int next) { throw new IllegalStateException("Unsupported migration"); }
    public synchronized Record create() {
        Record r=new Record(); r.id=UUID.randomUUID().toString();
        r.created=r.updated=System.currentTimeMillis();
        r.date=new java.text.SimpleDateFormat("yyyy-MM-dd",Locale.ROOT).format(new Date());
        save(r); return r;
    }
    static ContentValues values(Record r) {
        ContentValues v=new ContentValues();v.put("id",r.id);v.put("title",r.title);v.put("reference",r.reference);
        v.put("category",r.category);v.put("notes",r.notes);v.put("date",r.date);v.put("status",r.status);
        v.put("created",r.created);v.put("updated",r.updated);return v;
    }
    public synchronized void save(Record r) {
        r.updated=System.currentTimeMillis(); SQLiteDatabase db=getWritableDatabase();
        ContentValues v=values(r);
        if(db.update("records",v,"id=?",new String[]{r.id})==0) db.insertOrThrow("records",null,v);
    }
    static Record record(Cursor c) {
        Record r=new Record();r.id=s(c,"id");r.title=s(c,"title");r.reference=s(c,"reference");r.category=s(c,"category");
        r.notes=s(c,"notes");r.date=s(c,"date");r.status=s(c,"status");r.created=l(c,"created");r.updated=l(c,"updated");return r;
    }
    static String s(Cursor c,String k) { return c.getString(c.getColumnIndexOrThrow(k)); }
    static long l(Cursor c,String k) { return c.getLong(c.getColumnIndexOrThrow(k)); }
    public synchronized Record get(String id) {
        try(Cursor c=getReadableDatabase().query("records",null,"id=?",new String[]{id},null,null,null)) { return c.moveToFirst()?record(c):null; }
    }
    public synchronized List<Record> records(String query,String status) {
        String q=query.toLowerCase(Locale.ROOT);
        String where=status.isEmpty()?null:"status=?";
        String[] args=status.isEmpty()?null:new String[]{status};
        List<Record> list=new ArrayList<>();
        try(Cursor c=getReadableDatabase().query("records",null,where,args,null,null,"updated DESC")) {
            while(c.moveToNext()){
                Record r=record(c);
                if((r.title+"\n"+r.reference+"\n"+r.notes+"\n"+r.category).toLowerCase(Locale.ROOT).contains(q))list.add(r);
            }
        }
        return list;
    }
    static Asset asset(Cursor c) {
        Asset a=new Asset();a.id=s(c,"id");a.recordId=s(c,"record_id");a.path=s(c,"path");a.name=s(c,"name");
        a.mime=s(c,"mime");a.caption=s(c,"caption");a.kind=s(c,"kind");a.created=l(c,"created");return a;
    }
    public synchronized List<Asset> assets(String recordId) {
        List<Asset> list=new ArrayList<>();
        try(Cursor c=getReadableDatabase().query("assets",null,"record_id=?",new String[]{recordId},null,null,"created,id")) {while(c.moveToNext()) list.add(asset(c));}
        return list;
    }
    public File mediaFile(String extension) throws IOException {
        File dir=new File(context.getFilesDir(),"media");
        if(!dir.exists()&&!dir.mkdirs())throw new IOException("Не може да се создаде папка.");
        String ext=extension.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]","");
        if(ext.isEmpty()||ext.length()>10)ext="bin";
        return new File(dir,UUID.randomUUID()+"."+ext);
    }
    public File file(Asset a) throws IOException { return safeFile(context,a.path); }
    static File safeFile(Context c,String path) throws IOException {
        File base=c.getFilesDir().getCanonicalFile();File media=new File(base,"media").getCanonicalFile();File f=new File(base,path).getCanonicalFile();
        if(!path.matches("media/[a-zA-Z0-9-]+\\.[a-zA-Z0-9]{1,10}")||!media.equals(f.getParentFile()))throw new IOException("Недозволена патека.");
        return f;
    }
    public synchronized Asset attach(String recordId,File f,String name,String mime,String caption) throws IOException {
        if(get(recordId)==null)throw new IOException("Записот не постои.");
        if(!f.isFile()||f.length()==0)throw new IOException("Датотеката е празна.");
        String path="media/"+f.getName();
        if(!safeFile(context,path).equals(f.getCanonicalFile()))throw new IOException("Недозволена датотека.");
        Asset a=new Asset();a.id=UUID.randomUUID().toString();a.recordId=recordId;a.path=path;a.name=name;
        a.mime=mime==null?"application/octet-stream":mime;a.caption=caption==null?"":caption;
        a.kind=a.mime.startsWith("image/")?"photo":"document";a.created=System.currentTimeMillis();
        ContentValues v=new ContentValues();v.put("id",a.id);v.put("record_id",a.recordId);v.put("path",a.path);
        v.put("name",a.name);v.put("mime",a.mime);v.put("caption",a.caption);v.put("kind",a.kind);v.put("created",a.created);
        getWritableDatabase().insertOrThrow("assets",null,v);
        Record r=get(recordId);save(r);return a;
    }
    public Asset importStream(String recordId,InputStream in,String name,String mime) throws IOException {
        String ext=name.contains(".")?name.substring(name.lastIndexOf('.')+1):"bin";
        File dest=mediaFile(ext), part=new File(dest.getPath()+".part");
        try {
            try(FileOutputStream out=new FileOutputStream(part)) {copy(in,out,256L*1024*1024);out.getFD().sync();}
            if(!part.renameTo(dest))throw new IOException("Не може да се зачува датотеката.");
            return attach(recordId,dest,name,mime,"");
        } catch(Exception e){part.delete();dest.delete();if(e instanceof IOException)throw(IOException)e;throw new IOException(e);}
    }
    static void copy(InputStream in,OutputStream out,long max) throws IOException {
        byte[] b=new byte[32768];long total=0;int n;
        while((n=in.read(b))!=-1){total+=n;if(total>max)throw new IOException("Датотеката е поголема од 256 MB.");out.write(b,0,n);}
        if(total==0)throw new IOException("Датотеката е празна.");
    }
    public synchronized void caption(Asset a,String caption){ContentValues v=new ContentValues();v.put("caption",caption);getWritableDatabase().update("assets",v,"id=?",new String[]{a.id});a.caption=caption;Record r=get(a.recordId);if(r!=null)save(r);}
    public synchronized void deleteAsset(Asset a) throws IOException {getWritableDatabase().delete("assets","id=?",new String[]{a.id});file(a).delete();}
    public synchronized void deleteRecord(String id) throws IOException {List<Asset> a=assets(id);getWritableDatabase().delete("records","id=?",new String[]{id});for(Asset item:a)file(item).delete();}
}
