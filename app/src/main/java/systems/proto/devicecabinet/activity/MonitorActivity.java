package systems.proto.devicecabinet.activity;

import androidx.appcompat.app.AppCompatActivity;

import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.AsyncTask;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import eu.hsah.dbobjects.DbObjects;
import eu.hsah.dbobjects.Param;
import systems.proto.devicecabinet.R;
import systems.proto.devicecabinet.data.Params;
import systems.proto.devicecabinet.fragment.PowerFragment;


public class MonitorActivity extends AppCompatActivity implements Runnable {

    public static final String LOGTAG = "MonitorActivity";

    private static String DEFAULT_HOST = "sgbdumtom01:8080/Cabinet_Monitor";

    private PowerFragment powerFrag;
    private String androidId;
    private PowerManager.WakeLock wl;
    private Thread thread;
    private BufferedReader r;
    private DbObjects dbo;
    private boolean keepRunning;

    private SleepLockService sleepLockService;
    private boolean boundSleepLockService;

    private ServiceConnection bindConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            Log.d("SleepLockService","Sleep Service Bound!!");
            SleepLockService.SleepLockBinder b = (SleepLockService.SleepLockBinder) iBinder;
            sleepLockService = b.getService();
            boundSleepLockService = true;
        }
        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            Log.d("SleepLockService","Sleep Service Unbound!!");
            boundSleepLockService = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        dbo = DbObjects.getInstance(this);
        dbo.checkVersionUpdates(0,new int[0]);

        Intent bindIntent = new Intent(this, SleepLockService.class);
        bindService(bindIntent, bindConnection, BIND_AUTO_CREATE);

        supportRequestWindowFeature(Window.FEATURE_NO_TITLE);
        int uiOptions = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN;
        getWindow().getDecorView().setSystemUiVisibility(uiOptions);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_monitor);

        powerFrag = (PowerFragment) getSupportFragmentManager().findFragmentById(R.id.power_fragment);
        androidId = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ANDROID_ID);

        if (getIntent()!=null && getIntent().getAction()!=null) {
            String a = getIntent().getAction();
            Log.d(LOGTAG,"action: "+a);
            if ("off".equals(a)) {
                deviceRemoved();
            }
            else if ("on".equals(a)) {
                if (thread == null) {
                    keepRunning = true;
                    thread = new Thread(this);
                    thread.start();
                }
            }
        }

        powerFrag.setDeviceId(androidId);

    }

    @Override
    protected void onStart() {

        super.onStart();

    }

    private void showPower() {

        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = registerReceiver(null, ifilter);
        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

        runOnUiThread(() -> {
            powerFrag.showPower(level,scale);
        });

    }

    private void screenOn() {

        showPower();

        Log.d(LOGTAG, "screen on ...");
        if (wl!=null) {
            wl.release();
            wl = null;
        }
        PowerManager powerManager = (PowerManager) this.getSystemService(POWER_SERVICE);
        wl = powerManager.newWakeLock(PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.ON_AFTER_RELEASE | PowerManager.SCREEN_BRIGHT_WAKE_LOCK, "id:wakeupscreen");
        wl.acquire();
        Log.d(LOGTAG, "screen on acquire ...");
        runOnUiThread(()->{
            WindowManager.LayoutParams params = getWindow().getAttributes();
            params.flags |= WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
            params.screenBrightness = 1;
            getWindow().setAttributes(params);
            Log.d(LOGTAG, "screen bright ...");
        });

    }

    private void screenOff() {

        Log.d(LOGTAG, "screen off ...");
        if (wl!=null) {
            wl.release();
            wl = null;
        }
        runOnUiThread(()->{
            WindowManager.LayoutParams params = getWindow().getAttributes();
            params.flags |= WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
            params.screenBrightness = 0;
            getWindow().setAttributes(params);
            powerFrag.screenOff();
        });

    }

    @Override
    protected void onDestroy() {

        if (boundSleepLockService) {
            unbindService(bindConnection);
            boundSleepLockService = false;
        }

        if (wl != null && wl.isHeld()) {
            wl.release();
            wl = null;
        }

        keepRunning = false;

        try {
            if (r != null) {
                Log.d(LOGTAG, "closing reader to kill thread ...");
                r.close();
                Log.d(LOGTAG, "closed");
            }
        } catch (Exception ex) {
            Log.e(LOGTAG, "error closing reader", ex);
        }

        Log.d(LOGTAG, "destroyed");
        super.onDestroy();

    }

    private void deviceRemoved() {

        RemoveDeviceTask task = new RemoveDeviceTask();
        task.execute();

    }

    class RemoveDeviceTask extends AsyncTask {

        @Override
        protected Object doInBackground(Object[] objects) {
            try {
                Param p = Params.getParam(dbo,Params.PARAM_HOSTNAME);
                URL url = new URL(String.format(
                        "http://%s/notify?id=%s&action=removed",
                        p.strVal,androidId));
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                in.close();
                conn.disconnect();
                Log.d(LOGTAG, "notified that device removed");
            }
            catch (Throwable ex) {
                ex.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Object o) {
            Log.d(LOGTAG, "about to finish app");
            finish();
        }

    }

    @Override
    public void run() {

        try {
            Param p = Params.getParam(dbo,Params.PARAM_HOSTNAME);
            if (p.strVal==null) {
                p.strVal = DEFAULT_HOST;
                dbo.set(p);
            }

            URL url = new URL(String.format(
                    "http://%s/notify?id=%s&action=returned",
                    p.strVal,androidId));
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            r = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String s;
            Log.d(LOGTAG, "Listening ...");
            while ((s=r.readLine())!=null) {
                Log.d(LOGTAG, "read: ["+s+"]");
                if (s.contains("power")) {
                    screenOn();
                }
                else if (s.contains("sleep")) {
                    screenOff();
                }
            }
        }
        catch (Exception ex) {
            Log.e(LOGTAG, "aborted reader", ex);
        }
        catch (Throwable ex) {
            Log.e(LOGTAG, "aborted reader", ex);
        }
        finally {
            thread = null;
            r = null;
            Log.d(LOGTAG, "reader thread terminated");
        }
        if (keepRunning) {
            Log.d(LOGTAG, "restarting, as keep running set");
            thread = new Thread(this);
            thread.start();
        }

    }

}