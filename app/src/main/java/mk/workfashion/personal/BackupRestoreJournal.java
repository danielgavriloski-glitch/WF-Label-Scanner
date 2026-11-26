package mk.workfashion.personal;

import android.content.Context;
import android.util.AtomicFile;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;

/** A restart-safe rollback journal for state, native metadata and added attachments. */
final class BackupRestoreJournal {
    private static final String[] NATIVE_FILES={"workfashion-state.json","wfag-routes.json","wfag-notifications.json"};
    private static final String[] CONTENT_DIRS={"documents","wfag-backup-extras"};
    private static File directory(Context c){return new File(c.getFilesDir(),"wfag-restore-transaction");}
    private static void sync(File file,byte[] bytes)throws Exception{
        try(FileOutputStream out=new FileOutputStream(file)){out.write(bytes);out.getFD().sync();}
    }
    private static void list(File root,String relative,JSONArray out){
        File f=new File(root,relative);File[] children=f.listFiles();if(children==null)return;
        for(File child:children){String path=relative+"/"+child.getName();if(child.isDirectory())list(root,path,out);else out.put(path);}
    }
    static synchronized void begin(Context c)throws Exception{
        recover(c);File journal=directory(c);if(!journal.mkdirs())throw new Exception("Не може да се подготви безбедното враќање.");
        JSONObject manifest=new JSONObject(),nativeFiles=new JSONObject();JSONArray paths=new JSONArray();
        for(String name:NATIVE_FILES){
            AtomicFile source=new AtomicFile(new File(c.getFilesDir(),name));
            boolean exists=source.getBaseFile().exists()||new File(source.getBaseFile()+".bak").exists();nativeFiles.put(name,exists);
            if(exists)sync(new File(journal,name),source.readFully());
        }
        for(String name:CONTENT_DIRS)list(c.getFilesDir(),name,paths);
        manifest.put("nativeFiles",nativeFiles).put("contentPaths",paths);
        sync(new File(journal,"manifest.json"),manifest.toString().getBytes(StandardCharsets.UTF_8));
        sync(new File(journal,"pending"),new byte[]{1});
    }
    static synchronized void commit(Context c)throws Exception{
        File journal=directory(c);sync(new File(journal,"committed"),new byte[]{1});
        File pending=new File(journal,"pending");if(!pending.exists()||pending.delete())Documents.removeTree(journal);
    }
    static synchronized void recover(Context c)throws Exception{
        File journal=directory(c);if(!journal.exists())return;
        if(new File(journal,"pending").isFile()&&!new File(journal,"committed").isFile()){
            JSONObject manifest=new JSONObject(new String(Files.readAllBytes(new File(journal,"manifest.json").toPath()),StandardCharsets.UTF_8));
            JSONObject nativeFiles=manifest.getJSONObject("nativeFiles");
            for(String name:NATIVE_FILES){
                AtomicFile target=new AtomicFile(new File(c.getFilesDir(),name));
                if(nativeFiles.optBoolean(name)){
                    byte[] previous=Files.readAllBytes(new File(journal,name).toPath());FileOutputStream out=null;
                    try{out=target.startWrite();out.write(previous);target.finishWrite(out);}catch(Exception e){if(out!=null)target.failWrite(out);throw e;}
                }else target.delete();
            }
            Set<String> previous=new HashSet<>();JSONArray paths=manifest.getJSONArray("contentPaths");for(int i=0;i<paths.length();i++)previous.add(paths.getString(i));
            JSONArray current=new JSONArray();for(String name:CONTENT_DIRS)list(c.getFilesDir(),name,current);
            for(int i=0;i<current.length();i++){String path=current.getString(i);if(!previous.contains(path)){File extra=new File(c.getFilesDir(),path);if(extra.exists()&&!extra.delete())throw new Exception("Не може да се заврши враќањето на претходната состојба.");}}
        }
        File pending=new File(journal,"pending");if(!pending.exists()||pending.delete())Documents.removeTree(journal);
    }
}
