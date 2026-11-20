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
import android.service.notification.StatusBarNotification;
import android.util.AtomicFile;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;

/** Daily local reminder, independent of GPS, network access and the WebView. */
public class PickupReminderReceiver extends BroadcastReceiver {
    static final String CHANNEL="wfag_pickups_daily";
    static final String ACTION_REMIND="mk.workfashion.personal.PICKUP_REMINDER";
    static final String EXTRA_OPEN="wfag_open_pickups";
    static final int ID=641;

    static JSONArray pending(JSONObject state){
        JSONArray result=new JSONArray();
        JSONObject log=state.optJSONObject("pickupLog");
        JSONArray items=log==null?null:log.optJSONArray("items");
        if(items!=null)for(int i=0;i<items.length();i++){
            JSONObject item=items.optJSONObject(i);
            if(item!=null&&"pending".equals(item.optString("status")))result.put(item);
        }
        return result;
    }
    static JSONObject readState(Context context)throws Exception{
        File file=new File(context.getFilesDir(),"workfashion-state.json");
        if(!file.exists())return new JSONObject();
        if(file.length()>16*1024*1024)throw new IllegalStateException("State too large");
        return new JSONObject(new String(new AtomicFile(file).readFully(),StandardCharsets.UTF_8));
    }
    static long nextReminder(long now){
        Calendar next=Calendar.getInstance();next.setTimeInMillis(now);
        next.set(Calendar.HOUR_OF_DAY,8);next.set(Calendar.MINUTE,0);
        next.set(Calendar.SECOND,0);next.set(Calendar.MILLISECOND,0);
        if(next.getTimeInMillis()<=now)next.add(Calendar.DAY_OF_YEAR,1);
        return next.getTimeInMillis();
    }
    static PendingIntent alarmIntent(Context context){
        Intent intent=new Intent(context,PickupReminderReceiver.class).setAction(ACTION_REMIND);
        return PendingIntent.getBroadcast(context,ID,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    static void ensureChannel(Context context){
        NotificationManager manager=context.getSystemService(NotificationManager.class);
        NotificationChannel channel=new NotificationChannel(CHANNEL,"Роба за подигање",NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription("Дневен потсетник до означување на робата како подигната.");
        manager.createNotificationChannel(channel);
    }
    static boolean notificationsEnabled(Context context){
        ensureChannel(context);
        if(Build.VERSION.SDK_INT>=33&&context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return false;
        NotificationManager manager=context.getSystemService(NotificationManager.class);
        return manager.areNotificationsEnabled()&&manager.getNotificationChannel(CHANNEL).getImportance()!=NotificationManager.IMPORTANCE_NONE;
    }
    static boolean visible(Context context){
        for(StatusBarNotification notification:context.getSystemService(NotificationManager.class).getActiveNotifications())if(notification.getId()==ID)return true;
        return false;
    }
    static void sync(Context context,JSONObject state){
        try{
            JSONArray items=pending(state);
            AlarmManager alarms=context.getSystemService(AlarmManager.class);
            PendingIntent alarm=alarmIntent(context);
            if(items.length()==0){
                alarms.cancel(alarm);
                context.getSystemService(NotificationManager.class).cancel(ID);
                return;
            }
            // A new local-calendar alarm each day also follows DST and time-zone changes.
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,nextReminder(System.currentTimeMillis()),alarm);
            if(visible(context))show(context,items);
        }catch(Exception e){android.util.Log.w("WFAGPickups","Could not schedule reminder",e);}
    }
    static void refresh(Context context){
        try{sync(context,readState(context));}catch(Exception e){android.util.Log.w("WFAGPickups","Could not read reminders",e);}
    }
    static void show(Context context,JSONArray items){
        if(items.length()==0||!notificationsEnabled(context))return;
        StringBuilder details=new StringBuilder();
        for(int i=0;i<Math.min(items.length(),8);i++){
            JSONObject item=items.optJSONObject(i);
            if(i>0)details.append('\n');
            details.append(item.optString("from")).append(": ").append(item.optString("goods"));
            if(details.length()>3000){details.setLength(3000);break;}
        }
        Intent intent=new Intent(context,MainActivity.class).putExtra(EXTRA_OPEN,true)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent open=PendingIntent.getActivity(context,ID,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification notification=new Notification.Builder(context,CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Роба за подигање · "+items.length())
            .setContentText(details.toString()).setStyle(new Notification.BigTextStyle().bigText(details.toString()))
            .setContentIntent(open).setAutoCancel(true).setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PRIVATE).build();
        context.getSystemService(NotificationManager.class).notify(ID,notification);
    }
    @Override public void onReceive(Context context,Intent intent){
        try{
            JSONObject state=readState(context);
            sync(context,state);
            if(ACTION_REMIND.equals(intent.getAction()))show(context,pending(state));
        }catch(Exception e){android.util.Log.w("WFAGPickups","Reminder failed",e);}
    }
}
