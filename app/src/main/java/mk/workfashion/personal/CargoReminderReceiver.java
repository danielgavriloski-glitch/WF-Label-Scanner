package mk.workfashion.personal;
import android.Manifest;
import android.app.AlarmManager;
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
import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;

public class CargoReminderReceiver extends BroadcastReceiver {
 private static final String CHANNEL="wfag_cargo_daily";
 public static void schedule(Context ctx){
  AlarmManager manager=(AlarmManager)ctx.getSystemService(Context.ALARM_SERVICE);
  Intent intent=new Intent(ctx,CargoReminderReceiver.class);
  PendingIntent pi=PendingIntent.getBroadcast(ctx,501,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
  Calendar c=Calendar.getInstance();c.set(Calendar.HOUR_OF_DAY,8);c.set(Calendar.MINUTE,0);c.set(Calendar.SECOND,0);c.set(Calendar.MILLISECOND,0);
  if(!c.after(Calendar.getInstance()))c.add(Calendar.DAY_OF_YEAR,1);
  manager.setInexactRepeating(AlarmManager.RTC_WAKEUP,c.getTimeInMillis(),AlarmManager.INTERVAL_DAY,pi);
 }
 @Override public void onReceive(Context ctx,Intent intent){
  if("android.intent.action.BOOT_COMPLETED".equals(intent.getAction())){schedule(ctx);return;}
  try{
   if(Build.VERSION.SDK_INT>=33&&ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return;
   File f=new File(ctx.getFilesDir(),"workfashion-state.json");if(!f.isFile()||f.length()>16*1024*1024)return;
   byte[] bytes=new byte[(int)f.length()];try(FileInputStream in=new FileInputStream(f)){int n=0;while(n<bytes.length){int r=in.read(bytes,n,bytes.length-n);if(r<0)return;n+=r;}}
   JSONObject root=new JSONObject(new String(bytes,StandardCharsets.UTF_8));
   JSONArray list=root.optJSONObject("cargoLog")==null?null:root.getJSONObject("cargoLog").optJSONArray("shipments");if(list==null)return;
   Calendar now=Calendar.getInstance();String today=String.format(java.util.Locale.US,"%04d-%02d-%02d",now.get(Calendar.YEAR),now.get(Calendar.MONTH)+1,now.get(Calendar.DAY_OF_MONTH));
   int count=0;String first="";
   for(int i=0;i<list.length();i++){JSONObject s=list.optJSONObject(i);if(s==null)continue;String status=s.optString("status");String due=s.optString("dueDate");if(!("pending".equals(status)||"partial".equals(status))||(!due.isEmpty()&&due.compareTo(today)>0))continue;count++;if(first.isEmpty())first=s.optString("company")+" · "+s.optString("location");}
   if(count==0)return;
   NotificationManager nm=(NotificationManager)ctx.getSystemService(Context.NOTIFICATION_SERVICE);
   if(Build.VERSION.SDK_INT>=26)nm.createNotificationChannel(new NotificationChannel(CHANNEL,"WFAG неподигната роба",NotificationManager.IMPORTANCE_DEFAULT));
   PendingIntent open=PendingIntent.getActivity(ctx,502,new Intent(ctx,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
   android.app.Notification.Builder b=Build.VERSION.SDK_INT>=26?new android.app.Notification.Builder(ctx,CHANNEL):new android.app.Notification.Builder(ctx);
   b.setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("WFAG: "+count+" товари за подигање").setContentText(first).setStyle(new android.app.Notification.BigTextStyle().bigText(first+" · Отвори WFAG за сите неподигнати товари.")).setAutoCancel(true).setContentIntent(open);
   nm.notify(501,b.build());
  }catch(Exception ignored){}
 }
}
