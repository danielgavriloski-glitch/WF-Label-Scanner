package mk.workfashion.personal;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.util.AtomicFile;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Inexact Android alarm; records configured shift start without requiring the UI to be open. */
public class WorkdayFixedReceiver extends BroadcastReceiver {
    private static final int REQUEST=8371;
    private static final TimeZone ZONE=TimeZone.getTimeZone("Europe/Skopje");
    private static PendingIntent alarm(Context ctx){
        Intent intent=new Intent(ctx,WorkdayFixedReceiver.class);
        return PendingIntent.getBroadcast(ctx,REQUEST,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    private static AtomicFile dataFile(Context ctx){return new AtomicFile(new File(ctx.getFilesDir(),"workfashion-state.json"));}
    private static JSONObject load(Context ctx)throws Exception{
        AtomicFile file=dataFile(ctx);
        if(!file.getBaseFile().exists())return null;
        return new JSONObject(new String(file.readFully(),StandardCharsets.UTF_8));
    }
    private static String date(Calendar c){
        return String.format(Locale.US,"%04d-%02d-%02d",
            c.get(Calendar.YEAR),c.get(Calendar.MONTH)+1,c.get(Calendar.DAY_OF_MONTH));
    }
    private static Calendar at(String day,String hhmm)throws Exception{
        SimpleDateFormat fmt=new SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.US);
        fmt.setLenient(false);fmt.setTimeZone(ZONE);
        Date parsed=fmt.parse(day+" "+hhmm);
        if(parsed==null)throw new IllegalArgumentException("Invalid schedule");
        Calendar c=Calendar.getInstance(ZONE);
        c.setTime(parsed);return c;
    }
    private static boolean scheduled(JSONArray days,Calendar c){
        if(days==null)return false;
        int dow=c.get(Calendar.DAY_OF_WEEK)-1;
        for(int i=0;i<days.length();i++)if(days.optInt(i,-1)==dow)return true;
        return false;
    }
    public static void schedule(Context ctx){
        AlarmManager manager=(AlarmManager)ctx.getSystemService(Context.ALARM_SERVICE);
        if(manager==null)return;
        PendingIntent pi=alarm(ctx);
        manager.cancel(pi);
        try{
            JSONObject root=load(ctx);
            if(root==null)return;
            JSONObject config=root.optJSONObject("workdaySettings");
            if(config==null||!"fixed".equals(config.optString("mode"))||
               config.optString("activatedOn","").isEmpty())return;
            JSONObject settings=root.optJSONObject("settings");
            JSONArray days=settings==null?null:settings.optJSONArray("workdays");
            Calendar now=Calendar.getInstance(ZONE);
            for(int offset=0;offset<=8;offset++){
                Calendar current=(Calendar)now.clone();
                current.add(Calendar.DAY_OF_MONTH,offset);
                String day=date(current);
                if(day.compareTo(config.optString("activatedOn"))<0||!scheduled(days,current))continue;
                Calendar when=at(day,config.optString("start","08:00"));
                if(when.getTimeInMillis()<=now.getTimeInMillis())continue;
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when.getTimeInMillis(),pi);
                break;
            }
        }catch(Exception ignored){}
    }
    @Override public void onReceive(Context ctx,Intent intent){
        try{
            JSONObject root=load(ctx);
            if(root==null)return;
            JSONObject config=root.optJSONObject("workdaySettings");
            JSONObject settings=root.optJSONObject("settings");
            if(config==null||settings==null||!"fixed".equals(config.optString("mode")))return;
            Calendar now=Calendar.getInstance(ZONE);
            String day=date(now);
            if(day.compareTo(config.optString("activatedOn","9999-12-31"))<0||
               !scheduled(settings.optJSONArray("workdays"),now))return;
            Calendar scheduledStart=at(day,config.optString("start","08:00"));
            if(System.currentTimeMillis()<scheduledStart.getTimeInMillis())return;
            JSONObject sessions=root.optJSONObject("workdaySessions");
            if(sessions==null){sessions=new JSONObject();root.put("workdaySessions",sessions);}
            JSONObject current=sessions.optJSONObject(day);
            if(current!=null&&current.optLong("startAt",0)>0)return;
            JSONObject entry=new JSONObject();
            entry.put("date",day);entry.put("startAt",scheduledStart.getTimeInMillis());
            entry.put("endAt",JSONObject.NULL);entry.put("travel",new JSONArray());
            entry.put("status","working");entry.put("source","fixed");
            sessions.put(day,entry);
            JSONArray audit=root.optJSONArray("audit");
            if(audit!=null){
                JSONObject event=new JSONObject();
                SimpleDateFormat fmt=new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX",Locale.US);
                fmt.setTimeZone(ZONE);
                event.put("at",fmt.format(new Date()));
                event.put("text","Автоматски фиксно започнат работен ден");
                audit.put(event);
            }
            AtomicFile f=dataFile(ctx);FileOutputStream output=null;
            try{
                output=f.startWrite();
                output.write(root.toString().getBytes(StandardCharsets.UTF_8));
                f.finishWrite(output);
            }catch(Exception e){if(output!=null)f.failWrite(output);}
        }catch(Exception ignored){}
        finally{schedule(ctx);}
    }
}
