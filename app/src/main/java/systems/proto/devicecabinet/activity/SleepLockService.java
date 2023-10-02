package systems.proto.devicecabinet.activity;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

public class SleepLockService extends Service {

    private SleepLockBinder binder = new SleepLockBinder();
    private PowerManager.WakeLock cpuWakeLock;
    private WifiManager.WifiLock wifiWakeLock;

    @Override
    public IBinder onBind(Intent intent) {
        Log.d("SleepLockService","binding...");
        return binder;
    }

    @Override
    public void onCreate() {

        super.onCreate();

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        cpuWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "cpuWakeLock");
        cpuWakeLock.acquire();

        WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        wifiWakeLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "wifiWakeLock");
        wifiWakeLock.acquire();

        Log.d("SleepLockService","onCreate, locked");

    }

    @Override
    public void onDestroy() {
        Log.d("SleepLockService","onDestroy, locked");
        cpuWakeLock.release();
        wifiWakeLock.release();
        super.onDestroy();
    }

    public class SleepLockBinder extends Binder {

        SleepLockService getService() {
            return SleepLockService.this;
        }

    }

}
