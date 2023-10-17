package systems.proto.devicecabinet.activity;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.location.LocationManagerCompat;

import android.Manifest;
import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationRequest;
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
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.Permission;

import eu.hsah.dbobjects.DbObjects;
import eu.hsah.dbobjects.Param;
import systems.proto.devicecabinet.R;
import systems.proto.devicecabinet.data.Params;
import systems.proto.devicecabinet.fragment.PowerFragment;


public class MonitorActivity extends AppCompatActivity implements Runnable {

    public static final String LOGTAG = "MonitorActivity";

    private PowerFragment powerFrag;
    private String androidId;
    private PowerManager.WakeLock wl;
    private Thread thread;
    private BufferedReader r;
    private DbObjects dbo;
    private boolean keepRunning;
    private String command;

    private SleepLockService sleepLockService;
    private boolean boundSleepLockService;

    private ServiceConnection bindConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            Log.d("SleepLockService", "Sleep Service Bound!!");
            SleepLockService.SleepLockBinder b = (SleepLockService.SleepLockBinder) iBinder;
            sleepLockService = b.getService();
            boundSleepLockService = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            Log.d("SleepLockService", "Sleep Service Unbound!!");
            boundSleepLockService = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        dbo = DbObjects.getInstance(this);
        dbo.checkVersionUpdates(0, new int[0]);

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

        powerFrag.setDeviceId(androidId);

        if (getIntent() != null && getIntent().getAction() != null) {
            String a = getIntent().getAction();
            Log.d(LOGTAG, "action: " + a);
            if ("off".equals(a)) {
                deviceRemoved();
            } else if ("on".equals(a)) {
                if (thread == null) {
                    Log.d(LOGTAG, "starting initial message listener");
                    keepRunning = true;
                    command = "returned";
                    thread = new Thread(this);
                    thread.start();
                }
            }
        }

        /*
        LocationManager locationManager = (LocationManager)
                getSystemService(this.LOCATION_SERVICE);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION},0);
            return;
        }
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000, 2, new LocationListener() {
            @Override
            public void onLocationChanged(@NonNull Location location) {
                Log.d(LOGTAG,"Im here: "+location);
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {
                Log.d(LOGTAG,provider+", "+status);
            }
        });
        */

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
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
                        p.strVal,androidId,command));
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                in.close();
                conn.disconnect();
                Log.d(LOGTAG, "notified that device removed");
            }
            catch (Throwable ex) {
                Log.e(LOGTAG,"removing device",ex);
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
                p.strVal = ConfigActivity.DEFAULT_HOST;
                dbo.set(p);
            }

            URL url = new URL(String.format(
                    "http://%s/notify?id=%s&action=%s",
                    p.strVal,androidId,command));
            command = "none";
            Log.d(LOGTAG, "connecting to "+url);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            r = new BufferedReader(new InputStreamReader(conn.getInputStream()));

            String s;
            Log.d(LOGTAG, "listening ...");
            boolean first = true;
            while ((s=r.readLine())!=null) {
                if (first) {
                    Log.d(LOGTAG, "screen off soon ...");
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) { }
                    screenOff();
                    first = false;
                }
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
            runOnUiThread(()->{
                Toast.makeText(this,ex.toString(),Toast.LENGTH_LONG).show();
            });
        }
        catch (Throwable ex) {
            Log.e(LOGTAG, "aborted reader", ex);
            runOnUiThread(()->{
                Toast.makeText(this,ex.toString(),Toast.LENGTH_LONG).show();
            });
        }
        finally {
            thread = null;
            r = null;
            Log.d(LOGTAG, "reader thread terminated");
        }
        if (keepRunning) {
            Log.d(LOGTAG, "restarting, as keep running set");
            try {
                Thread.sleep(30000);
            } catch (InterruptedException e) {
            }
            thread = new Thread(this);
            thread.start();
        }

    }

}