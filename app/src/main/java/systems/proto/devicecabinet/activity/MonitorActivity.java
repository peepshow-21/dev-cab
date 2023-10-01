package systems.proto.devicecabinet.activity;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
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

    private static String DEFAULT_HOST = "sgbdumappsint";

    private PowerFragment powerFrag;
    private String androidId;
    private PowerManager.WakeLock wl;
    private Thread thread;
    private BufferedReader r;
    private DbObjects dbo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        dbo = DbObjects.getInstance(this);
        dbo.checkVersionUpdates(0,new int[0]);

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
        String a = getIntent().getAction();
        if ("off".equals(a)) {
            deviceRemoved();
        } else {
            if (thread==null) {
                thread = new Thread(this);
                thread.start();
            }
        }

        powerFrag.setDeviceId(androidId);

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

        System.out.printf("screen on ...");
        if (wl!=null) {
            wl.release();
            wl = null;
        }
        PowerManager powerManager = (PowerManager) this.getSystemService(POWER_SERVICE);
        if (!powerManager.isInteractive()){ // if screen is not already on, turn it on (get wake_lock)
            wl = powerManager.newWakeLock(PowerManager.ACQUIRE_CAUSES_WAKEUP |PowerManager.ON_AFTER_RELEASE |PowerManager.SCREEN_BRIGHT_WAKE_LOCK ,"id:wakeupscreen");
            wl.acquire();
            System.out.printf("screen on acquire ...");
        }

    }

    private void screenOff() {

        System.out.printf("screen off ...");
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

//        PowerManager powerManager = (PowerManager) this.getSystemService(POWER_SERVICE);
//        if (powerManager.isInteractive()){ // if screen is not already on, turn it on (get wake_lock)
//            wl = powerManager.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK ,"id:wakeupscreen");
//            wl.acquire();
//            System.out.printf("screen off acquire ...");
//        }

    }

    @Override
    protected void onDestroy() {
        System.out.println("going.....");
        EndDeviceTask task = new EndDeviceTask();
        task.execute();
        super.onDestroy();
    }

    private void deviceRemoved() {

        RemoveDeviceTask task = new RemoveDeviceTask();
        task.execute();

    }

    class EndDeviceTask extends AsyncTask {

        @Override
        protected Object doInBackground(Object[] objects) {
            if (thread!=null) {
                try {
                    r.close();
                }
                catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
            return null;
        }

    }

    class RemoveDeviceTask extends AsyncTask {

        @Override
        protected Object doInBackground(Object[] objects) {
            try {
                Param p = Params.getParam(dbo,Params.PARAM_HOSTNAME);
                URL url = new URL(String.format(
                        "http://%s/power/notify?id=%s&action=removed",
                        p.strVal,androidId));
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                in.close();
                conn.disconnect();
                finish();
            }
            catch (Throwable ex) {
                ex.printStackTrace();
            }
            return null;
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
                    "http://%s/power/notify?id=%s&action=returned",
                    p.strVal,androidId));
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            r = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String s;
            while ((s=r.readLine())!=null) {
                System.out.println(s);
                if (s.contains("power")) {
                    screenOn();
                }
                if (s.contains("sleep")) {
                    screenOff();
                }
            }
        }
        catch (Throwable ex) {
            ex.printStackTrace();
        }
        finally {
            thread = null;
            r = null;
        }

    }

}