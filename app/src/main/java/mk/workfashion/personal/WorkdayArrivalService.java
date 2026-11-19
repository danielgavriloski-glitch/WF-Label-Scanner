package mk.workfashion.personal;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

/** Compatibility class for stopping legacy monitoring; simple hours never use location. */
public class WorkdayArrivalService extends Service {
    @Override public IBinder onBind(Intent intent){return null;}
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        stopSelf();
        return START_NOT_STICKY;
    }
}
