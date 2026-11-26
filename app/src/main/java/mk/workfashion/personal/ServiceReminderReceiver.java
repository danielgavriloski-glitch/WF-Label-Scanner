package mk.workfashion.personal;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.TimeZone;

/** Daily date/mileage reminders for the existing vehicleLog.services records. */
public class ServiceReminderReceiver extends BroadcastReceiver {
    static final String CHANNEL="wfag_service_daily",ACTION_REMIND="mk.workfashion.personal.SERVICE_REMINDER";
    static final int ID=642;
    static String today(long now){SimpleDateFormat f=new SimpleDateFormat("yyyy-MM-dd",Locale.ROOT);f.setTimeZone(TimeZone.getTimeZone("Europe/Skopje"));return f.format(new Date(now));}
    static JSONArray currentServices(JSONObject state){
        JSONObject log=state.optJSONObject("vehicleLog");JSONArray source=log==null?null:log.optJSONArray("services");
        LinkedHashMap<String,JSONObject> latest=new LinkedHashMap<>();
        for(int i=0;source!=null&&i<source.length();i++){
            JSONObject s=source.optJSONObject(i);if(s==null||s.optBoolean("deleted")||s.optBoolean("remindersDisabled"))continue;
            String title=s.optString("title").trim().toLowerCase(Locale.ROOT);if(title.isEmpty())continue;
            JSONObject previous=latest.get(title);
            if(previous==null||s.optString("at").compareTo(previous.optString("at"))>=0)latest.put(title,s);
        }
        JSONArray result=new JSONArray();for(JSONObject s:latest.values())result.put(s);return result;
    }
    static double odometer(JSONObject state){
        JSONObject log=state.optJSONObject("vehicleLog");if(log==null)return Double.NaN;
        String at="";double value=Double.NaN;
        for(String key:new String[]{"readings","fills","tireChanges","trips"}){
            JSONArray rows=log.optJSONArray(key);
            for(int i=0;rows!=null&&i<rows.length();i++){
                JSONObject r=rows.optJSONObject(i);if(r==null||r.optBoolean("deleted"))continue;
                String stamp=r.optString("trips".equals(key)?"endAt":"at");
                double n=r.optDouble("trips".equals(key)?"endOdo":"odo",Double.NaN);
                if(Double.isNaN(n)||n<0||stamp.compareTo(at)<0)continue;
                at=stamp;value=n;
            }
        }return value;
    }
    static JSONArray due(JSONObject state,long now){
        JSONArray services=currentServices(state),result=new JSONArray();String date=today(now);double odo=odometer(state);
        for(int i=0;i<services.length();i++){
            JSONObject s=services.optJSONObject(i);String next=s.optString("nextDate","");if("null".equals(next))next="";
            double nextOdo=s.optDouble("nextOdo",Double.NaN);
            if((!next.isEmpty()&&next.compareTo(date)<=0)||(!Double.isNaN(nextOdo)&&!Double.isNaN(odo)&&odo>=nextOdo))result.put(s);
        }return result;
    }
    static void ensureChannel(Context c){c.getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel(CHANNEL,"Сервис за комбето",NotificationManager.IMPORTANCE_DEFAULT));}
    static boolean enabled(Context c){
        ensureChannel(c);if(Build.VERSION.SDK_INT>=33&&c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return false;
        NotificationManager n=c.getSystemService(NotificationManager.class);return n.areNotificationsEnabled()&&n.getNotificationChannel(CHANNEL).getImportance()!=NotificationManager.IMPORTANCE_NONE;
    }
    static PendingIntent alarm(Context c){return PendingIntent.getBroadcast(c,ID,new Intent(c,ServiceReminderReceiver.class).setAction(ACTION_REMIND),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
    static void sync(Context c,JSONObject state){
        try{
            JSONArray services=currentServices(state);boolean hasReminder=false;
            for(int i=0;i<services.length();i++){JSONObject s=services.optJSONObject(i);if(!s.isNull("nextDate")&&!s.optString("nextDate").isEmpty()||!s.isNull("nextOdo")&&s.has("nextOdo")){hasReminder=true;break;}}
            AlarmManager manager=c.getSystemService(AlarmManager.class);
            if(hasReminder)manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,PickupReminderReceiver.nextReminder(System.currentTimeMillis()),alarm(c));else manager.cancel(alarm(c));
            JSONArray due=due(state,System.currentTimeMillis());
            if(due.length()==0)c.getSystemService(NotificationManager.class).cancel(ID);
            else for(android.service.notification.StatusBarNotification n:c.getSystemService(NotificationManager.class).getActiveNotifications())if(n.getId()==ID){show(c,due);break;}
        }catch(Exception e){android.util.Log.w("WFAGService","Could not schedule service reminder",e);}
    }
    static void refresh(Context c){try{sync(c,PickupReminderReceiver.readState(c));}catch(Exception e){android.util.Log.w("WFAGService","Could not refresh service reminders",e);}}
    static void show(Context c,JSONArray items){
        if(items.length()==0||!enabled(c))return;
        String title="Доспеан сервис · "+items.length();StringBuilder text=new StringBuilder();JSONArray ids=new JSONArray();
        for(int i=0;i<items.length();i++){
            JSONObject item=items.optJSONObject(i);ids.put(item.optString("id"));
            if(i<8){if(text.length()>0)text.append('\n');text.append(item.optString("title"));
                if(!item.isNull("nextDate")&&!item.optString("nextDate").isEmpty())text.append(" · ").append(item.optString("nextDate"));
                if(!item.isNull("nextOdo")&&item.has("nextOdo"))text.append(" · ").append(item.optLong("nextOdo")).append(" km");}
        }
        String body=text.toString();long now=System.currentTimeMillis();String id=NotificationHistory.id("service",title,body,ids,now);
        Intent target=new Intent(c,MainActivity.class).putExtra(NotificationHistory.EXTRA_ID,id).putExtra("wfag_open_services",true).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent open=PendingIntent.getActivity(c,ID,target,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification notification=new Notification.Builder(c,CHANNEL).setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(title).setContentText(body).setStyle(new Notification.BigTextStyle().bigText(body)).setContentIntent(open).setAutoCancel(true).setOnlyAlertOnce(true).setVisibility(Notification.VISIBILITY_PRIVATE).build();
        c.getSystemService(NotificationManager.class).notify(ID,notification);
        NotificationHistory.delivered(c,id,"service",title,body,items.length()==1?items.optJSONObject(0).optString("id"):"",ids,now);
    }
    @Override public void onReceive(Context c,Intent intent){
        try{JSONObject state=PickupReminderReceiver.readState(c);sync(c,state);if(ACTION_REMIND.equals(intent.getAction()))show(c,due(state,System.currentTimeMillis()));}
        catch(Exception e){android.util.Log.w("WFAGService","Service reminder failed",e);}
    }
}
