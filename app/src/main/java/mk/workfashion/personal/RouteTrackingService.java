package mk.workfashion.personal;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.IBinder;
import android.util.AtomicFile;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

public class RouteTrackingService extends Service implements LocationListener {
    public static final String ACTION_START="mk.workfashion.personal.ROUTE_START";
    private static final String CHANNEL_ID="wfag_route_tracking";
    private static final int NOTIFICATION_ID=1401;
    private static final long STOP_MS=5*60*1000L;
    private static final float STOP_RADIUS_M=80f;
    private static final float PLACE_RADIUS_M=120f;
    private static final Object LOCK=new Object();
    private LocationManager locationManager;

    @Override public void onCreate(){
        super.onCreate();
        NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        NotificationChannel ch=new NotificationChannel(CHANNEL_ID,"WFAG · Патувања",NotificationManager.IMPORTANCE_LOW);
        ch.setDescription("Следење рута додека трае службен пат");
        nm.createNotificationChannel(ch);
    }

    @Override public int onStartCommand(Intent intent,int flags,int startId){
        startForeground(NOTIFICATION_ID,notification("Патувањето се следи","GPS следењето е активно."));
        String action=intent==null?null:intent.getAction();
        if(intent!=null&&ACTION_START.equals(action))beginSession(this,intent.getStringExtra("tripId"));
        JSONObject state=readState(this);
        if(!state.optBoolean("tracking",false)){stopSelf();return START_NOT_STICKY;}
        startUpdates();
        return START_STICKY;
    }

    private void startUpdates(){
        locationManager=(LocationManager)getSystemService(LOCATION_SERVICE);
        try{
            if(locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER))
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,30000L,20f,this);
            if(locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER))
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,45000L,30f,this);
        }catch(SecurityException e){
            finishSession(this);stopSelf();
        }
    }

    @Override public void onDestroy(){
        if(locationManager!=null)try{locationManager.removeUpdates(this);}catch(Exception ignored){}
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent){return null;}
    @Override public void onProviderEnabled(String provider){}
    @Override public void onProviderDisabled(String provider){}
    @Override public void onStatusChanged(String provider,int status,Bundle extras){}

    @Override public void onLocationChanged(Location loc){
        if(loc==null||loc.getAccuracy()>150f)return;
        synchronized(LOCK){
            try{
                JSONObject state=readStateUnlocked(this);
                if(!state.optBoolean("tracking",false))return;
                JSONObject session=findSession(state,state.optString("activeSessionId"));
                if(session==null)return;
                long now=System.currentTimeMillis();
                JSONArray points=session.optJSONArray("points");if(points==null){points=new JSONArray();session.put("points",points);}
                JSONObject last=points.length()>0?points.optJSONObject(points.length()-1):null;
                boolean add=last==null;
                if(last!=null){
                    float d=distance(last.optDouble("lat"),last.optDouble("lon"),loc.getLatitude(),loc.getLongitude());
                    add=d>=20f||now-last.optLong("at")>=60000L;
                }
                if(add){
                    JSONObject p=new JSONObject();p.put("lat",loc.getLatitude());p.put("lon",loc.getLongitude());p.put("at",now);p.put("accuracy",Math.round(loc.getAccuracy()));
                    points.put(p);
                    while(points.length()>8000)points.remove(0);
                }
                double aLat=session.optDouble("anchorLat",Double.NaN),aLon=session.optDouble("anchorLon",Double.NaN);
                long aSince=session.optLong("anchorSince",0L);
                if(Double.isNaN(aLat)||Double.isNaN(aLon)||aSince==0L){
                    session.put("anchorLat",loc.getLatitude());session.put("anchorLon",loc.getLongitude());session.put("anchorSince",now);
                    writeStateUnlocked(this,state);return;
                }
                float fromAnchor=distance(aLat,aLon,loc.getLatitude(),loc.getLongitude());
                String activeStopId=session.optString("activeStopId","");
                if(fromAnchor<=STOP_RADIUS_M){
                    if(now-aSince>=STOP_MS){
                        JSONArray stops=session.optJSONArray("stops");if(stops==null){stops=new JSONArray();session.put("stops",stops);}
                        JSONObject stop=findStop(stops,activeStopId);
                        if(stop==null){
                            stop=new JSONObject();
                            String id="stop_"+Long.toString(now,36);
                            stop.put("id",id);stop.put("lat",aLat);stop.put("lon",aLon);stop.put("startAt",aSince);stop.put("endAt",now);stop.put("active",true);
                            JSONObject known=findSavedPlace(state,aLat,aLon);
                            if(known!=null){stop.put("name",known.optString("name",""));stop.put("pending",false);stop.put("savedPlaceId",known.optString("id",""));}
                            else{stop.put("name","");stop.put("pending",true);}
                            stops.put(stop);session.put("activeStopId",id);
                            if(stop.optBoolean("pending"))notifyStop(stop);
                        }else stop.put("endAt",now);
                    }
                }else{
                    if(!activeStopId.isEmpty()){
                        JSONArray stops=session.optJSONArray("stops");JSONObject stop=findStop(stops,activeStopId);
                        if(stop!=null)stop.put("active",false);
                    }
                    session.put("activeStopId","");
                    session.put("anchorLat",loc.getLatitude());session.put("anchorLon",loc.getLongitude());session.put("anchorSince",now);
                }
                writeStateUnlocked(this,state);
            }catch(Exception ignored){}
        }
    }

    private void notifyStop(JSONObject stop){
        if(android.os.Build.VERSION.SDK_INT<33||checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)==android.content.pm.PackageManager.PERMISSION_GRANTED){
            NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
            nm.notify(NOTIFICATION_ID,notification("Застанување над 5 минути","Допри за да внесеш име или остави „Локација“."));
        }
    }

    private Notification notification(String title,String text){
        Intent open=new Intent(this,MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi=PendingIntent.getActivity(this,0,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this,CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle(title).setContentText(text)
            .setOngoing(true).setOnlyAlertOnce(true).setContentIntent(pi).build();
    }

    private static JSONObject fresh()throws Exception{
        JSONObject s=new JSONObject();s.put("version",1);s.put("tracking",false);s.put("activeSessionId","");s.put("sessions",new JSONArray());s.put("savedPlaces",new JSONArray());return s;
    }
    private static AtomicFile file(Context c){return new AtomicFile(new File(c.getFilesDir(),"wfag-routes.json"));}
    private static JSONObject readStateUnlocked(Context c){
        try{
            AtomicFile f=file(c);if(!f.getBaseFile().exists())return fresh();
            JSONObject s=new JSONObject(new String(f.readFully(),StandardCharsets.UTF_8));
            if(!s.has("sessions"))s.put("sessions",new JSONArray());
            if(!s.has("savedPlaces"))s.put("savedPlaces",new JSONArray());
            return s;
        }catch(Exception e){try{return fresh();}catch(Exception impossible){return new JSONObject();}}
    }
    private static JSONObject readState(Context c){synchronized(LOCK){return readStateUnlocked(c);}}
    private static void writeStateUnlocked(Context c,JSONObject s){
        FileOutputStream out=null;
        try{
            AtomicFile f=file(c);out=f.startWrite();out.write(s.toString().getBytes(StandardCharsets.UTF_8));f.finishWrite(out);
        }catch(Exception e){if(out!=null)try{file(c).failWrite(out);}catch(Exception ignored){}}
    }
    public static String stateJson(Context c){return readState(c).toString();}

    private static JSONObject findSession(JSONObject state,String id){
        JSONArray a=state.optJSONArray("sessions");if(a==null)return null;
        for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o!=null&&id.equals(o.optString("id")))return o;}return null;
    }
    private static JSONObject findStop(JSONArray stops,String id){
        if(stops==null||id==null||id.isEmpty())return null;
        for(int i=0;i<stops.length();i++){JSONObject o=stops.optJSONObject(i);if(o!=null&&id.equals(o.optString("id")))return o;}return null;
    }
    private static float distance(double aLat,double aLon,double bLat,double bLon){
        float[] r=new float[1];Location.distanceBetween(aLat,aLon,bLat,bLon,r);return r[0];
    }
    private static JSONObject findSavedPlace(JSONObject state,double lat,double lon){
        JSONArray a=state.optJSONArray("savedPlaces");if(a==null)return null;JSONObject best=null;float bestD=PLACE_RADIUS_M+1;
        for(int i=0;i<a.length();i++){JSONObject p=a.optJSONObject(i);if(p==null)continue;float d=distance(lat,lon,p.optDouble("lat"),p.optDouble("lon"));if(d<=PLACE_RADIUS_M&&d<bestD){best=p;bestD=d;}}
        return best;
    }
    private static void beginSession(Context c,String tripId){
        synchronized(LOCK){
            try{
                JSONObject s=readStateUnlocked(c);
                if(s.optBoolean("tracking",false)&&findSession(s,s.optString("activeSessionId"))!=null)return;
                long now=System.currentTimeMillis();JSONObject session=new JSONObject();String id="route_"+Long.toString(now,36);
                session.put("id",id);session.put("tripId",tripId==null?"":tripId);session.put("startedAt",now);session.put("endedAt",0L);session.put("points",new JSONArray());session.put("stops",new JSONArray());
                JSONArray sessions=s.optJSONArray("sessions");if(sessions==null){sessions=new JSONArray();s.put("sessions",sessions);}sessions.put(session);
                while(sessions.length()>180)sessions.remove(0);
                s.put("tracking",true);s.put("activeSessionId",id);writeStateUnlocked(c,s);
            }catch(Exception ignored){}
        }
    }
    public static void finishSession(Context c){
        synchronized(LOCK){
            try{
                JSONObject s=readStateUnlocked(c);JSONObject session=findSession(s,s.optString("activeSessionId"));long now=System.currentTimeMillis();
                if(session!=null){
                    session.put("endedAt",now);String sid=session.optString("activeStopId","");JSONObject stop=findStop(session.optJSONArray("stops"),sid);
                    if(stop!=null){stop.put("active",false);stop.put("endAt",now);}
                    session.put("activeStopId","");
                }
                s.put("tracking",false);s.put("activeSessionId","");writeStateUnlocked(c,s);
            }catch(Exception ignored){}
        }
    }
    public static boolean resolveStop(Context c,String stopId,String name,boolean remember){
        synchronized(LOCK){
            try{
                JSONObject s=readStateUnlocked(c);JSONArray sessions=s.optJSONArray("sessions");JSONObject found=null;
                for(int i=0;sessions!=null&&i<sessions.length()&&found==null;i++)found=findStop(sessions.optJSONObject(i).optJSONArray("stops"),stopId);
                if(found==null)return false;
                String clean=name==null?"":name.trim();if(clean.length()>80)clean=clean.substring(0,80);
                found.put("name",clean);found.put("pending",false);
                if(remember&&!clean.isEmpty()){
                    JSONArray places=s.optJSONArray("savedPlaces");if(places==null){places=new JSONArray();s.put("savedPlaces",places);}
                    JSONObject near=findSavedPlace(s,found.optDouble("lat"),found.optDouble("lon"));
                    if(near==null){near=new JSONObject();near.put("id","place_"+Long.toString(System.currentTimeMillis(),36));places.put(near);}
                    near.put("name",clean);near.put("lat",found.optDouble("lat"));near.put("lon",found.optDouble("lon"));
                    while(places.length()>100)places.remove(0);
                }
                writeStateUnlocked(c,s);return true;
            }catch(Exception e){return false;}
        }
    }
}
