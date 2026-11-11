package mk.workfashion.personal;

import android.Manifest;
import android.app.Service;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.util.AtomicFile;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Optional foreground location monitor; never runs until workday GPS mode is selected. */
public class WorkdayArrivalService extends Service {
    private static final String CHANNEL="wfag_workday_arrival";
    private static final int NOTIFICATION=2837;
    private LocationManager locationManager;
    private LocationListener listener;
    private boolean finished=false;

    @Override public IBinder onBind(Intent intent){return null;}

    private void notification(){
        NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL,"WFAG · почеток на работа",NotificationManager.IMPORTANCE_LOW));
        Intent launch=new Intent(this,MainActivity.class);
        PendingIntent open=PendingIntent.getActivity(this,0,launch,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification note=new Notification.Builder(this,CHANNEL)
            .setSmallIcon(R.drawable.wf_icon)
            .setContentTitle("WFAG · GPS работна локација")
            .setContentText("Работниот ден ќе започне кога ќе пристигнеш.")
            .setContentIntent(open)
            .setOngoing(true)
            .build();
        if(Build.VERSION.SDK_INT>=29)
            startForeground(NOTIFICATION,note,ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
        else startForeground(NOTIFICATION,note);
    }

    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){
            stopSelf();return START_NOT_STICKY;
        }
        try{
            notification();
            if(!monitorRequired()){stopSelf();return START_NOT_STICKY;}
            if(locationManager==null){
                locationManager=(LocationManager)getSystemService(LOCATION_SERVICE);
                if(locationManager==null||!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)){
                    stopSelf();return START_NOT_STICKY;
                }
                listener=this::checkArrival;
                // No live map or UI polling; GPS requests are limited to 60 seconds / 25 metres.
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,60000L,25f,listener,Looper.getMainLooper());
            }
            return START_STICKY;
        }catch(Exception failure){stopSelf();return START_NOT_STICKY;}
    }

    private AtomicFile dataFile(){return new AtomicFile(new File(getFilesDir(),"workfashion-state.json"));}
    private JSONObject load()throws Exception{
        AtomicFile file=dataFile();
        if(!file.getBaseFile().exists())return null;
        return new JSONObject(new String(file.readFully(),StandardCharsets.UTF_8));
    }
    private static String today(){
        SimpleDateFormat sdf=new SimpleDateFormat("yyyy-MM-dd",Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("Europe/Skopje"));
        return sdf.format(new Date());
    }
    private boolean monitorRequired(){
        try{
            JSONObject state=load();if(state==null)return false;
            JSONObject cfg=state.optJSONObject("workdaySettings");
            if(cfg==null||!"geofence".equals(cfg.optString("mode"))||cfg.optJSONObject("workplace")==null)return false;
            String date=today();
            if(date.compareTo(cfg.optString("activatedOn","9999-12-31"))<0)return false;
            JSONObject sessions=state.optJSONObject("workdaySessions");
            JSONObject day=sessions==null?null:sessions.optJSONObject(date);
            return day==null||day.optLong("startAt",0)<=0;
        }catch(Exception e){return false;}
    }
    private void checkArrival(Location fix){
        if(finished||fix==null||!fix.hasAccuracy())return;
        long age=SystemClock.elapsedRealtimeNanos()-fix.getElapsedRealtimeNanos();
        if(age<0||age>30000000000L||(Build.VERSION.SDK_INT>=18&&fix.isFromMockProvider()))return;
        try{
            AtomicFile file=dataFile();
            JSONObject state=load();if(state==null)return;
            JSONObject cfg=state.optJSONObject("workdaySettings");
            if(cfg==null||!"geofence".equals(cfg.optString("mode"))) {stopSelf();return;}
            String date=today();
            if(date.compareTo(cfg.optString("activatedOn","9999-12-31"))<0)return;
            JSONObject workplace=cfg.optJSONObject("workplace");
            if(workplace==null)return;
            double radius=cfg.optDouble("radiusM",100),lat=workplace.optDouble("lat",Double.NaN),lon=workplace.optDouble("lon",Double.NaN);
            if(!Double.isFinite(lat)||!Double.isFinite(lon)||radius<50||radius>1000||
               fix.getAccuracy()>Math.min(radius,150))return;
            float[] metres=new float[1];Location.distanceBetween(lat,lon,fix.getLatitude(),fix.getLongitude(),metres);
            if(metres[0]>radius)return;
            JSONObject sessions=state.optJSONObject("workdaySessions");
            if(sessions==null){sessions=new JSONObject();state.put("workdaySessions",sessions);}
            JSONObject old=sessions.optJSONObject(date);
            if(old!=null&&old.optLong("startAt",0)>0){finished=true;stopSelf();return;}
            JSONObject day=new JSONObject();
            day.put("date",date);day.put("startAt",System.currentTimeMillis());day.put("endAt",JSONObject.NULL);
            day.put("travel",new JSONArray());day.put("status","working");day.put("source","geofence");
            sessions.put(date,day);
            JSONArray audit=state.optJSONArray("audit");
            if(audit!=null){
                JSONObject event=new JSONObject();event.put("at",new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX",Locale.US).format(new Date()));
                event.put("text","Автоматски GPS почеток на работен ден");
                audit.put(event);
            }
            FileOutputStream out=null;
            try{
                out=file.startWrite();
                out.write(state.toString().getBytes(StandardCharsets.UTF_8));
                file.finishWrite(out);
                finished=true;stopSelf();
            }catch(Exception e){if(out!=null)file.failWrite(out);}
        }catch(Exception ignored){}
    }
    @Override public void onDestroy(){
        if(locationManager!=null&&listener!=null){
            try{locationManager.removeUpdates(listener);}catch(Exception ignored){}
        }
        super.onDestroy();
    }
}
