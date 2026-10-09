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
import org.mapsforge.core.model.LatLong;
import org.mapsforge.map.reader.MapFile;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RouteTrackingService extends Service implements LocationListener {
    public static final String ACTION_START="mk.workfashion.personal.ROUTE_START";
    private static final String CHANNEL_ID="wfag_route_tracking";
    private static final int NOTIFICATION_ID=1401;
    private static final long GPS_MIN_TIME_MS=2000L;
    private static final float GPS_MIN_DISTANCE_M=0f;
    private static final long NETWORK_MIN_TIME_MS=10000L;
    private static final float NETWORK_MIN_DISTANCE_M=10f;
    private static final float MAX_ACCEPTABLE_ACCURACY_M=80f;
    private static final long GPS_PREFERENCE_WINDOW_MS=20000L;
    private static final float MIN_POINT_DISTANCE_M=3f;
    private static final long MAX_POINT_INTERVAL_MS=2000L;
    private static final float MAX_REASONABLE_SPEED_MPS=65f;
    private static final long HOUR_SEGMENT_MS=60L*60L*1000L;
    private static final long STOP_MS=5L*60L*1000L;
    private static final float STOP_RADIUS_M=140f;
    private static final float STOP_EXIT_RADIUS_M=220f;
    private static final float STOP_MOVING_SPEED_MPS=2.8f;
    private static final long STOP_MOVING_CONFIRM_MS=30000L;
    private static final float PLACE_RADIUS_M=120f;
    private static final long HISTORY_RETENTION_MS=5L*365L*24L*60L*60L*1000L;
    private static final Object LOCK=new Object();
    private static final Set<String> COMPACTIONS=Collections.synchronizedSet(new HashSet<>());
    private LocationManager locationManager;
    private long lastGpsFixAt=0L;

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
        resumePendingCompactions(this);
        return START_STICKY;
    }

    private void startUpdates(){
        locationManager=(LocationManager)getSystemService(LOCATION_SERVICE);
        try{
            try{locationManager.removeUpdates(this);}catch(Exception ignored){}
            if(locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER))
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,GPS_MIN_TIME_MS,GPS_MIN_DISTANCE_M,this);
            if(locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER))
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,NETWORK_MIN_TIME_MS,NETWORK_MIN_DISTANCE_M,this);
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
        if(loc==null||!loc.hasAccuracy()||loc.getAccuracy()>MAX_ACCEPTABLE_ACCURACY_M)return;
        final long now=System.currentTimeMillis();
        final boolean gps=LocationManager.GPS_PROVIDER.equals(loc.getProvider());
        if(gps)lastGpsFixAt=now;
        else if(lastGpsFixAt>0L&&now-lastGpsFixAt<GPS_PREFERENCE_WINDOW_MS)return;

        synchronized(LOCK){
            try{
                JSONObject state=readStateUnlocked(this);
                if(!state.optBoolean("tracking",false))return;
                JSONObject session=findSession(state,state.optString("activeSessionId"));
                if(session==null)return;

                JSONArray points=session.optJSONArray("points");
                if(points==null){points=new JSONArray();session.put("points",points);}
                long hourStart=session.optLong("hourStartAt",0L);
                if(hourStart<=0L){
                    JSONObject first=points.length()>0?points.optJSONObject(0):null;
                    hourStart=first==null?now:first.optLong("at",now);
                    session.put("hourStartAt",hourStart);
                }

                String compactSegmentId="";
                if(points.length()>=2&&now-hourStart>=HOUR_SEGMENT_MS){
                    compactSegmentId=closeHourSegment(session,points,hourStart,now);
                    JSONArray nextPoints=new JSONArray();
                    JSONObject bridge=points.optJSONObject(points.length()-1);
                    if(bridge!=null)nextPoints.put(new JSONObject(bridge.toString()));
                    points=nextPoints;
                    session.put("points",points);
                    session.put("hourStartAt",now);
                }

                JSONObject last=points.length()>0?points.optJSONObject(points.length()-1):null;
                boolean add=last==null;
                float stepDistance=0f;
                long stepElapsed=0L;

                if(last!=null){
                    stepDistance=distance(last.optDouble("lat"),last.optDouble("lon"),loc.getLatitude(),loc.getLongitude());
                    stepElapsed=Math.max(1000L,now-last.optLong("at"));
                    float lastAccuracy=(float)last.optDouble("accuracy",30d);
                    float plausibleMax=Math.max(120f,(stepElapsed/1000f)*MAX_REASONABLE_SPEED_MPS+lastAccuracy+loc.getAccuracy());
                    if(stepDistance>plausibleMax)return;
                    add=stepDistance>=MIN_POINT_DISTANCE_M||stepElapsed>=MAX_POINT_INTERVAL_MS;
                }

                if(add){
                    JSONObject p=new JSONObject();
                    p.put("lat",loc.getLatitude());
                    p.put("lon",loc.getLongitude());
                    p.put("at",now);
                    p.put("accuracy",Math.round(loc.getAccuracy()));
                    p.put("provider",loc.getProvider()==null?"":loc.getProvider());
                    if(loc.hasSpeed())p.put("speedMps",Math.round(loc.getSpeed()*10f)/10f);
                    points.put(p);
                    if(last!=null)session.put("distanceM",session.optDouble("distanceM",0d)+stepDistance);
                }

                updateStopState(state,session,loc,now,stepDistance,stepElapsed);
                writeStateUnlocked(this,state);
                if(!compactSegmentId.isEmpty())queueCompaction(this,session.optString("id"),compactSegmentId);
            }catch(Exception ignored){}
        }
    }

    private void updateStopState(JSONObject state,JSONObject session,Location loc,long now,float stepDistance,long stepElapsed)throws Exception{
        double aLat=session.optDouble("anchorLat",Double.NaN),aLon=session.optDouble("anchorLon",Double.NaN);
        long aSince=session.optLong("anchorSince",0L);
        if(Double.isNaN(aLat)||Double.isNaN(aLon)||aSince==0L){
            session.put("anchorLat",loc.getLatitude());
            session.put("anchorLon",loc.getLongitude());
            session.put("anchorSince",now);
            session.put("moveSince",0L);
            return;
        }

        float fromAnchor=distance(aLat,aLon,loc.getLatitude(),loc.getLongitude());
        float calculatedSpeed=stepElapsed>0L?stepDistance/(stepElapsed/1000f):0f;
        float speed=loc.hasSpeed()?loc.getSpeed():calculatedSpeed;
        boolean moving=speed>=STOP_MOVING_SPEED_MPS&&stepDistance>=12f;
        String activeStopId=session.optString("activeStopId","");

        if(!moving){
            session.put("moveSince",0L);

            // GPS drift while parked must not restart the 5-minute timer.
            if(fromAnchor<=STOP_EXIT_RADIUS_M||loc.getAccuracy()>=45f){
                if(fromAnchor<=STOP_RADIUS_M){
                    double blend=0.08d;
                    double smoothLat=aLat*(1d-blend)+loc.getLatitude()*blend;
                    double smoothLon=aLon*(1d-blend)+loc.getLongitude()*blend;
                    session.put("anchorLat",smoothLat);
                    session.put("anchorLon",smoothLon);
                    aLat=smoothLat;aLon=smoothLon;
                }
                if(now-aSince>=STOP_MS){
                    JSONArray stops=session.optJSONArray("stops");
                    if(stops==null){stops=new JSONArray();session.put("stops",stops);}
                    JSONObject stop=findStop(stops,activeStopId);
                    if(stop==null){
                        stop=new JSONObject();
                        String id="stop_"+Long.toString(now,36);
                        stop.put("id",id);
                        stop.put("lat",aLat);
                        stop.put("lon",aLon);
                        stop.put("startAt",aSince);
                        stop.put("endAt",now);
                        stop.put("active",true);
                        JSONObject known=findSavedPlace(state,aLat,aLon);
                        if(known!=null){
                            stop.put("name",known.optString("name",""));
                            stop.put("pending",false);
                            stop.put("savedPlaceId",known.optString("id",""));
                        }else{
                            stop.put("name","");
                            stop.put("pending",true);
                        }
                        stops.put(stop);
                        session.put("activeStopId",id);
                        if(stop.optBoolean("pending"))notifyStop(stop);
                    }else{
                        stop.put("endAt",now);
                    }
                }
                return;
            }

            // A single far but slow fix is treated as drift, not confirmed movement.
            if(activeStopId.isEmpty()){
                session.put("anchorLat",loc.getLatitude());
                session.put("anchorLon",loc.getLongitude());
                session.put("anchorSince",now);
            }
            return;
        }

        long moveSince=session.optLong("moveSince",0L);
        if(moveSince<=0L){
            session.put("moveSince",now);
            return;
        }
        if(now-moveSince<STOP_MOVING_CONFIRM_MS)return;

        if(!activeStopId.isEmpty()){
            JSONArray stops=session.optJSONArray("stops");
            JSONObject stop=findStop(stops,activeStopId);
            if(stop!=null){stop.put("active",false);stop.put("endAt",now);}
        }
        session.put("activeStopId","");
        session.put("anchorLat",loc.getLatitude());
        session.put("anchorLon",loc.getLongitude());
        session.put("anchorSince",now);
        session.put("moveSince",0L);
    }

    private static String closeHourSegment(JSONObject session,JSONArray points,long startAt,long now)throws Exception{
        JSONArray hourly=session.optJSONArray("hourSegments");
        if(hourly==null){hourly=new JSONArray();session.put("hourSegments",hourly);}
        String id="hour_"+Long.toString(startAt,36);
        JSONObject segment=new JSONObject();
        segment.put("id",id);
        segment.put("startAt",startAt);
        JSONObject last=points.optJSONObject(points.length()-1);
        segment.put("endAt",last==null?now:last.optLong("at",now));
        segment.put("colorIndex",hourly.length()%6);
        segment.put("status","pending");
        segment.put("rawPoints",new JSONArray(points.toString()));
        hourly.put(segment);
        return id;
    }

    private static JSONObject findHourSegment(JSONObject session,String segmentId){
        JSONArray hourly=session==null?null:session.optJSONArray("hourSegments");
        if(hourly==null)return null;
        for(int i=0;i<hourly.length();i++){
            JSONObject o=hourly.optJSONObject(i);
            if(o!=null&&segmentId.equals(o.optString("id")))return o;
        }
        return null;
    }

    private static void queueCompaction(Context context,String sessionId,String segmentId){
        if(context==null||sessionId==null||segmentId==null||sessionId.isEmpty()||segmentId.isEmpty())return;
        final Context app=context.getApplicationContext();
        final String key=sessionId+"::"+segmentId;
        if(!COMPACTIONS.add(key))return;

        new Thread(()->{
            JSONArray raw=null;
            long endedAt=0L;
            try{
                synchronized(LOCK){
                    JSONObject state=readStateUnlocked(app);
                    JSONObject session=findSession(state,sessionId);
                    JSONObject segment=findHourSegment(session,segmentId);
                    if(segment==null||"done".equals(segment.optString("status"))){COMPACTIONS.remove(key);return;}
                    JSONArray source=segment.optJSONArray("rawPoints");
                    if(source==null||source.length()<2){segment.put("status","pending");writeStateUnlocked(app,state);COMPACTIONS.remove(key);return;}
                    raw=new JSONArray(source.toString());
                    endedAt=segment.optLong("endAt",0L);
                    segment.put("status","processing");
                    writeStateUnlocked(app,state);
                }

                JSONArray snapped=null;
                File mapPath=new File(new File(app.getFilesDir(),"offline-maps"),"macedonia.map");
                if(mapPath.exists()&&mapPath.length()>1024L*1024L){
                    MapFile map=null;
                    try{
                        map=new MapFile(mapPath);
                        JSONObject temp=new JSONObject();
                        temp.put("id",sessionId+"_"+segmentId);
                        temp.put("points",raw);
                        temp.put("endedAt",endedAt);
                        OfflineRoadSnapper.Result result=OfflineRoadSnapper.snap(app,map,temp);
                        if(result!=null&&!result.segments.isEmpty())snapped=roadSegmentsJson(result.segments);
                    }finally{
                        if(map!=null)try{map.close();}catch(Exception ignored){}
                    }
                }

                synchronized(LOCK){
                    JSONObject state=readStateUnlocked(app);
                    JSONObject session=findSession(state,sessionId);
                    JSONObject segment=findHourSegment(session,segmentId);
                    if(segment!=null){
                        if(snapped!=null&&snapped.length()>0){
                            segment.put("lineSegments",snapped);
                            segment.put("status","done");
                            segment.put("roadSource","offline-osm");
                            segment.remove("rawPoints");
                        }else{
                            segment.put("status","pending");
                        }
                        writeStateUnlocked(app,state);
                    }
                }
            }catch(Exception ignored){
                synchronized(LOCK){
                    try{
                        JSONObject state=readStateUnlocked(app);
                        JSONObject session=findSession(state,sessionId);
                        JSONObject segment=findHourSegment(session,segmentId);
                        if(segment!=null){segment.put("status","pending");writeStateUnlocked(app,state);}
                    }catch(Exception ignored2){}
                }
            }finally{
                COMPACTIONS.remove(key);
            }
        },"wfag-hour-compact").start();
    }

    private static JSONArray roadSegmentsJson(List<List<LatLong>> segments)throws Exception{
        JSONArray out=new JSONArray();
        for(List<LatLong> line:segments){
            if(line==null||line.size()<2)continue;
            JSONArray arr=new JSONArray();
            LatLong lastKept=null;
            for(int i=0;i<line.size();i++){
                LatLong p=line.get(i);
                if(p==null)continue;
                boolean edge=i==0||i==line.size()-1;
                if(!edge&&lastKept!=null&&distance(lastKept.latitude,lastKept.longitude,p.latitude,p.longitude)<12f)continue;
                JSONArray q=new JSONArray();
                q.put(p.latitude);q.put(p.longitude);
                arr.put(q);lastKept=p;
            }
            if(arr.length()>=2)out.put(arr);
        }
        return out;
    }

    private static void resumePendingCompactions(Context c){
        try{
            JSONObject state=readState(c);
            JSONArray sessions=state.optJSONArray("sessions");
            if(sessions==null)return;
            for(int i=0;i<sessions.length();i++){
                JSONObject session=sessions.optJSONObject(i);
                if(session==null)continue;
                JSONArray hourly=session.optJSONArray("hourSegments");
                if(hourly==null)continue;
                for(int j=0;j<hourly.length();j++){
                    JSONObject segment=hourly.optJSONObject(j);
                    if(segment==null||"done".equals(segment.optString("status")))continue;
                    if(segment.optJSONArray("rawPoints")!=null)queueCompaction(c,session.optString("id"),segment.optString("id"));
                }
            }
        }catch(Exception ignored){}
    }

    public static void retryPendingCompactions(Context c){resumePendingCompactions(c);}

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
    private static void pruneOldSessions(JSONArray sessions,long now){
        if(sessions==null)return;
        long cutoff=now-HISTORY_RETENTION_MS;
        for(int i=sessions.length()-1;i>=0;i--){
            JSONObject session=sessions.optJSONObject(i);
            if(session==null)continue;
            boolean pinned=session.optBoolean("pinned",false);
            long started=session.optLong("startedAt",0L);
            if(!pinned&&started>0&&started<cutoff)sessions.remove(i);
        }
    }

    private static void beginSession(Context c,String tripId){
        synchronized(LOCK){
            try{
                JSONObject s=readStateUnlocked(c);
                if(s.optBoolean("tracking",false)&&findSession(s,s.optString("activeSessionId"))!=null)return;
                long now=System.currentTimeMillis();JSONObject session=new JSONObject();String id="route_"+Long.toString(now,36);
                session.put("id",id);session.put("tripId",tripId==null?"":tripId);session.put("startedAt",now);session.put("endedAt",0L);session.put("distanceM",0d);session.put("points",new JSONArray());session.put("hourSegments",new JSONArray());session.put("hourStartAt",now);session.put("stops",new JSONArray());
                JSONArray sessions=s.optJSONArray("sessions");if(sessions==null){sessions=new JSONArray();s.put("sessions",sessions);}sessions.put(session);
                pruneOldSessions(sessions,now);
                s.put("tracking",true);s.put("activeSessionId",id);writeStateUnlocked(c,s);
            }catch(Exception ignored){}
        }
    }
    public static void finishSession(Context c){
        synchronized(LOCK){
            try{
                JSONObject s=readStateUnlocked(c);
                JSONObject session=findSession(s,s.optString("activeSessionId"));
                long now=System.currentTimeMillis();
                String compactId="";
                String sessionId="";
                if(session!=null){
                    sessionId=session.optString("id","");
                    JSONArray points=session.optJSONArray("points");
                    if(points!=null&&points.length()>=2){
                        long hourStart=session.optLong("hourStartAt",session.optLong("startedAt",now));
                        compactId=closeHourSegment(session,points,hourStart,now);
                        session.put("points",new JSONArray());
                    }
                    session.put("endedAt",now);
                    String sid=session.optString("activeStopId","");
                    JSONObject stop=findStop(session.optJSONArray("stops"),sid);
                    if(stop!=null){stop.put("active",false);stop.put("endAt",now);}
                    session.put("activeStopId","");
                }
                s.put("tracking",false);
                s.put("activeSessionId","");
                writeStateUnlocked(c,s);
                if(!compactId.isEmpty())queueCompaction(c,sessionId,compactId);
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
