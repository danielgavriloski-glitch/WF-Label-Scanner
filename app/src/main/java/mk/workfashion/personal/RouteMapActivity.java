package mk.workfashion.personal;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;
import org.mapsforge.core.graphics.Paint;
import org.mapsforge.core.graphics.Style;
import org.mapsforge.core.model.LatLong;
import org.mapsforge.core.model.Point;
import org.mapsforge.map.android.graphics.AndroidGraphicFactory;
import org.mapsforge.map.android.util.AndroidUtil;
import org.mapsforge.map.android.view.MapView;
import org.mapsforge.map.datastore.MapDataStore;
import org.mapsforge.map.layer.cache.TileCache;
import org.mapsforge.map.layer.overlay.Circle;
import org.mapsforge.map.layer.overlay.Polyline;
import org.mapsforge.map.layer.renderer.TileRendererLayer;
import org.mapsforge.map.reader.MapFile;
import org.mapsforge.map.rendertheme.internal.MapsforgeThemes;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class RouteMapActivity extends Activity {
    public static final String EXTRA_FROM="routeFrom", EXTRA_TO="routeTo", EXTRA_LAT="routeLat", EXTRA_LON="routeLon";
    private static final String MAP_URL="https://download.mapsforge.org/maps/v5/europe/macedonia.map";
    private FrameLayout root; private MapView mapView; private TextView status; private Button downloadButton;
    private String from="",to=""; private double focusLat=Double.NaN,focusLon=Double.NaN;

    @Override public void onCreate(Bundle b){super.onCreate(b);AndroidGraphicFactory.createInstance(getApplication());
        from=safe(getIntent().getStringExtra(EXTRA_FROM));to=safe(getIntent().getStringExtra(EXTRA_TO));
        focusLat=getIntent().getDoubleExtra(EXTRA_LAT,Double.NaN);focusLon=getIntent().getDoubleExtra(EXTRA_LON,Double.NaN);
        root=new FrameLayout(this);root.setBackgroundColor(Color.WHITE);setContentView(root);
        File map=mapFile();if(map.exists()&&map.length()>1024*1024)showMap();else showDownloadScreen("Офлајн мапата за Македонија не е симната.");
    }
    private static String safe(String s){return s==null?"":s;}
    private File mapFile(){File d=new File(getFilesDir(),"offline-maps");if(!d.exists())d.mkdirs();return new File(d,"macedonia.map");}
    private void showDownloadScreen(String m){root.removeAllViews();LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setGravity(Gravity.CENTER);box.setPadding(48,48,48,48);
        status=new TextView(this);status.setText(m+"\n\nМапата се симнува еднаш. Потоа рутите работат без интернет.");status.setTextSize(17);status.setTextColor(Color.rgb(21,46,53));status.setGravity(Gravity.CENTER);box.addView(status,new LinearLayout.LayoutParams(-1,-2));
        downloadButton=new Button(this);downloadButton.setText("Симни офлајн мапа");box.addView(downloadButton,new LinearLayout.LayoutParams(-1,-2));downloadButton.setOnClickListener(v->downloadMap());root.addView(box,new FrameLayout.LayoutParams(-1,-1));}
    private void downloadMap(){if(downloadButton!=null)downloadButton.setEnabled(false);if(status!=null)status.setText("Се симнува офлајн мапата…");
        new Thread(()->{File target=mapFile(),part=new File(target.getParentFile(),target.getName()+".part");HttpURLConnection con=null;
            try{con=(HttpURLConnection)new URL(MAP_URL).openConnection();con.setConnectTimeout(20000);con.setReadTimeout(30000);con.setRequestProperty("User-Agent","WFAG/1.6 Android");con.connect();if(con.getResponseCode()/100!=2)throw new Exception("HTTP "+con.getResponseCode());
                long total=con.getContentLengthLong(),done=0;try(InputStream in=con.getInputStream();FileOutputStream out=new FileOutputStream(part)){byte[] buf=new byte[65536];int n,last=-1;while((n=in.read(buf))>0){out.write(buf,0,n);done+=n;if(total>0){int pct=(int)(done*100/total);if(pct!=last&&(pct%2==0||pct==100)){last=pct;final int shown=pct;runOnUiThread(()->{if(status!=null)status.setText("Се симнува офлајн мапата… "+shown+"%");});}}}}
                if(part.length()<1024*1024)throw new Exception("Мапата е нецелосна.");if(target.exists()&&!target.delete())throw new Exception("Старата мапа не може да се замени.");if(!part.renameTo(target))throw new Exception("Мапата не може да се зачува.");runOnUiThread(this::showMap);
            }catch(Exception e){part.delete();runOnUiThread(()->showDownloadScreen("Симнувањето не успеа. Провери интернет и пробај повторно."));}finally{if(con!=null)con.disconnect();}},"wfag-map-download").start();}
    private Paint routePaint(){Paint p=AndroidGraphicFactory.INSTANCE.createPaint();p.setColor(AndroidGraphicFactory.INSTANCE.createColor(org.mapsforge.core.graphics.Color.BLUE));p.setStrokeWidth(8f*getResources().getDisplayMetrics().density);p.setStyle(Style.STROKE);return p;}
    private Paint stopPaint(){Paint p=AndroidGraphicFactory.INSTANCE.createPaint();p.setColor(AndroidGraphicFactory.INSTANCE.createColor(org.mapsforge.core.graphics.Color.RED));p.setStyle(Style.FILL);return p;}
    private void showMap(){root.removeAllViews();mapView=new MapView(this);mapView.setClickable(true);mapView.getMapScaleBar().setVisible(true);mapView.setBuiltInZoomControls(true);root.addView(mapView,new FrameLayout.LayoutParams(-1,-1));
        try{TileCache cache=AndroidUtil.createTileCache(this,"wfag-offline-map-cache",mapView.getModel().displayModel.getTileSize(),1f,mapView.getModel().frameBufferModel.getOverdrawFactor());MapDataStore ds=new MapFile(mapFile());TileRendererLayer base=new TileRendererLayer(cache,ds,mapView.getModel().mapViewPosition,AndroidGraphicFactory.INSTANCE);base.setXmlRenderTheme(MapsforgeThemes.DEFAULT);mapView.getLayerManager().getLayers().add(base);
            RouteBounds rb=addRouteLayers();if(!Double.isNaN(focusLat)&&!Double.isNaN(focusLon)){mapView.setCenter(new LatLong(focusLat,focusLon));mapView.setZoomLevel((byte)16);}else if(rb.count>0){mapView.setCenter(new LatLong((rb.minLat+rb.maxLat)/2,(rb.minLon+rb.maxLon)/2));mapView.setZoomLevel(zoomForSpan(Math.max(rb.maxLat-rb.minLat,rb.maxLon-rb.minLon)));}else{mapView.setCenter(ds.boundingBox().getCenterPoint());mapView.setZoomLevel((byte)8);}
            TextView title=new TextView(this);String period=(!from.isEmpty()||!to.isEmpty())?"\n"+from+" – "+to:"";title.setText("WFAG · GPS мапа"+period+"\n"+rb.stopCount+" застанувања · "+String.format(Locale.getDefault(),"%.1f km",rb.distanceM/1000d));title.setTextSize(14);title.setTextColor(Color.rgb(21,46,53));title.setBackgroundColor(Color.argb(235,255,255,255));title.setPadding(24,14,24,14);FrameLayout.LayoutParams tp=new FrameLayout.LayoutParams(-2,-2);tp.gravity=Gravity.TOP|Gravity.START;tp.setMargins(20,20,20,20);root.addView(title,tp);
        }catch(Exception e){showDownloadScreen("Офлајн мапата не може да се отвори. Симни ја повторно.");}}
    private RouteBounds addRouteLayers(){RouteBounds b=new RouteBounds();try{JSONObject state=new JSONObject(RouteTrackingService.stateJson(this));JSONArray sessions=state.optJSONArray("sessions");if(sessions==null)return b;
        for(int i=0;i<sessions.length();i++){JSONObject s=sessions.optJSONObject(i);if(s==null)continue;String date=dateOf(s.optLong("startedAt"));if(!from.isEmpty()&&date.compareTo(from)<0)continue;if(!to.isEmpty()&&date.compareTo(to)>0)continue;
            JSONArray points=s.optJSONArray("filteredPoints");if(points==null||points.length()<2)points=s.optJSONArray("points");List<LatLong> route=new ArrayList<>();if(points!=null)for(int j=0;j<points.length();j++){JSONObject p=points.optJSONObject(j);if(p==null)continue;LatLong q=new LatLong(p.optDouble("lat"),p.optDouble("lon"));route.add(q);b.include(q);}if(route.size()>=2){RoadSnapper.SnapResult snapped=RoadSnapper.snap(mapFile(),route);route=snapped.points;b.distanceM+=snapped.distanceM;Polyline line=new Polyline(routePaint(),AndroidGraphicFactory.INSTANCE);line.setPoints(route);mapView.getLayerManager().getLayers().add(line);}
            JSONArray stops=s.optJSONArray("stops");if(stops==null)continue;for(int j=0;j<stops.length();j++){JSONObject st=stops.optJSONObject(j);if(st==null)continue;LatLong q=new LatLong(st.optDouble("lat"),st.optDouble("lon"));b.include(q);b.stopCount++;String name=st.optString("name","").trim();if(name.isEmpty())name="Локација";long start=st.optLong("startAt"),end=st.optLong("endAt");if(end<=0)end=System.currentTimeMillis();final String txt=name+"\n"+clock(start)+" – "+clock(end)+" · "+duration(start,end);Circle c=new Circle(q,35,stopPaint(),null){@Override public boolean onTap(LatLong t,Point l,Point p){if(contains(l,p,t.latitude,mapView)){Toast.makeText(RouteMapActivity.this,txt,Toast.LENGTH_LONG).show();return true;}return false;}};mapView.getLayerManager().getLayers().add(c);}}
        }catch(Exception ignored){}return b;}
    private static byte zoomForSpan(double s){if(s<.005)return 16;if(s<.015)return 14;if(s<.05)return 12;if(s<.15)return 10;if(s<.5)return 9;if(s<1.5)return 8;return 7;}
    private static String dateOf(long ms){return new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(new Date(ms));}
    private static String clock(long ms){return ms<=0?"—":new SimpleDateFormat("HH:mm",Locale.getDefault()).format(new Date(ms));}
    private static String duration(long a,long z){long m=Math.max(0,Math.round((z-a)/60000d)),h=m/60;m%=60;return h>0?h+"ч "+m+"м":m+" мин";}
    @Override protected void onDestroy(){if(mapView!=null){mapView.destroyAll();mapView=null;}AndroidGraphicFactory.clearResourceMemoryCache();super.onDestroy();}
    private static class RouteBounds{int count,stopCount;double distanceM,minLat=Double.POSITIVE_INFINITY,maxLat=Double.NEGATIVE_INFINITY,minLon=Double.POSITIVE_INFINITY,maxLon=Double.NEGATIVE_INFINITY;void include(LatLong p){if(p==null)return;count++;minLat=Math.min(minLat,p.latitude);maxLat=Math.max(maxLat,p.latitude);minLon=Math.min(minLon,p.longitude);maxLon=Math.max(maxLon,p.longitude);}}
}