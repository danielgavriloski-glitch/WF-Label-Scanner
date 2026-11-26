package mk.workfashion.personal;

import android.content.Context;
import android.util.AtomicFile;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.TimeZone;

/** Delivered local notifications, independent of WebView lifetime and read state. */
final class NotificationHistory {
    static final String EXTRA_ID="wfag_notification_id";
    private static final Object LOCK=new Object();
    private static final int MAX_BYTES=16*1024*1024;
    interface Listener { void changed(); }
    private static java.lang.ref.WeakReference<Listener> listener=new java.lang.ref.WeakReference<>(null);
    static void listen(Listener value){listener=new java.lang.ref.WeakReference<>(value);}
    static void forget(Listener value){if(listener.get()==value)listener.clear();}
    static void changed(){Listener value=listener.get();if(value!=null)value.changed();}
    static AtomicFile file(Context c){return new AtomicFile(new File(c.getFilesDir(),"wfag-notifications.json"));}
    static JSONObject empty()throws Exception{return new JSONObject().put("version",1).put("notifications",new JSONArray());}
    static JSONObject read(Context c)throws Exception{
        AtomicFile f=file(c);if(!f.getBaseFile().exists()&&!new File(f.getBaseFile()+".bak").exists())return empty();
        byte[] data=f.readFully();if(data.length>MAX_BYTES)throw new Exception("Историјата на известувања е преголема.");
        return normalize(new JSONObject(new String(data,StandardCharsets.UTF_8)));
    }
    static String json(Context c){synchronized(LOCK){try{return read(c).toString();}catch(Exception e){return "{\"version\":1,\"notifications\":[],\"error\":\"Историјата не се прочита.\"}";}}}
    static JSONObject normalize(Object input)throws Exception{
        JSONObject result=input instanceof JSONObject?new JSONObject(input.toString()):empty();
        JSONArray source=input instanceof JSONArray?(JSONArray)input:result.optJSONArray("notifications");
        if(source==null)source=result.optJSONArray("items");
        if(source==null)throw new Exception("Невалидна историја на известувања.");
        if(source.length()>50000)throw new Exception("Историјата на известувања е преголема.");
        JSONArray items=new JSONArray();java.util.HashSet<String> ids=new java.util.HashSet<>();
        for(int i=0;i<source.length();i++){
            JSONObject item=source.optJSONObject(i);if(item==null)throw new Exception("Невалидно известување.");
            JSONObject n=new JSONObject(item.toString());String id=n.optString("id");
            if(id.isEmpty()||id.length()>160||!ids.add(id)||n.optString("title").length()>500||n.optString("body").length()>12000)throw new Exception("Невалидно известување.");
            long at=n.optLong("createdAt",0);if(at<=0)throw new Exception("Невалиден датум на известување.");
            n.put("type",n.optString("type",n.optString("kind","info")));
            n.put("targetId",n.optString("targetId",n.optString("recordId","")));
            n.put("read",n.optBoolean("read",false)||n.optLong("readAt",0)>0);
            items.put(n);
        }
        result.put("version",1);result.put("notifications",items);result.remove("items");return result;
    }
    private static void write(Context c,JSONObject history)throws Exception{
        byte[] bytes=history.toString().getBytes(StandardCharsets.UTF_8);if(bytes.length>MAX_BYTES)throw new Exception("Историјата на известувања е преголема.");
        AtomicFile f=file(c);FileOutputStream out=null;
        try{out=f.startWrite();out.write(bytes);f.finishWrite(out);}catch(Exception e){if(out!=null)f.failWrite(out);throw e;}
    }
    static String id(String type,String title,String body,JSONArray recordIds,long now){
        try{
            SimpleDateFormat day=new SimpleDateFormat("yyyy-MM-dd",Locale.ROOT);day.setTimeZone(TimeZone.getTimeZone("Europe/Skopje"));
            byte[] hash=MessageDigest.getInstance("SHA-256").digest((day.format(new Date(now))+"|"+type+"|"+title+"|"+body+"|"+recordIds).getBytes(StandardCharsets.UTF_8));
            StringBuilder value=new StringBuilder("notice_");for(int i=0;i<16;i++)value.append(String.format(Locale.ROOT,"%02x",hash[i]&255));return value.toString();
        }catch(Exception impossible){throw new IllegalStateException(impossible);}
    }
    static void delivered(Context c,String id,String type,String title,String body,String targetId,JSONArray recordIds,long now){
        try{synchronized(LOCK){
            JSONObject history=read(c);JSONArray items=history.getJSONArray("notifications");
            for(int i=0;i<items.length();i++)if(id.equals(items.getJSONObject(i).optString("id")))return;
            items.put(new JSONObject().put("id",id).put("type",type).put("title",title).put("body",body).put("createdAt",now).put("read",false).put("targetId",targetId).put("recordIds",recordIds));
            write(c,history);
        }changed();}catch(Exception e){android.util.Log.w("WFAGNotifications","Could not retain delivered notification",e);}
    }
    static boolean markRead(Context c,String id){
        try{synchronized(LOCK){
            JSONObject history=read(c);JSONArray items=history.getJSONArray("notifications");boolean updated=false,found=id==null||id.isEmpty();
            for(int i=0;i<items.length();i++){JSONObject item=items.getJSONObject(i);if(id==null||id.isEmpty()||id.equals(item.optString("id"))){found=true;if(!item.optBoolean("read")){item.put("read",true);item.put("readAt",System.currentTimeMillis());updated=true;}}}
            if(updated)write(c,history);if(!found)return false;
        }changed();return true;}catch(Exception e){return false;}
    }
    static void merge(Context c,Object input)throws Exception{
        JSONObject incoming=normalize(input);
        synchronized(LOCK){
            JSONObject current=read(c);LinkedHashMap<String,JSONObject> byId=new LinkedHashMap<>();
            JSONArray old=current.getJSONArray("notifications"),added=incoming.getJSONArray("notifications");
            for(int i=0;i<old.length();i++){JSONObject item=old.getJSONObject(i);byId.put(item.getString("id"),item);}
            boolean updated=false;
            for(int i=0;i<added.length();i++){JSONObject item=added.getJSONObject(i),prior=byId.get(item.getString("id"));
                if(prior==null){byId.put(item.getString("id"),item);updated=true;}
                else if(item.optBoolean("read")&&!prior.optBoolean("read")){prior.put("read",true);prior.put("readAt",item.optLong("readAt",System.currentTimeMillis()));updated=true;}
            }
            if(updated){JSONArray merged=new JSONArray();for(JSONObject n:byId.values())merged.put(n);current.put("notifications",merged);write(c,current);}
        }changed();
    }
    static JSONObject forBackup(Context c)throws Exception{synchronized(LOCK){return read(c);}}
}
