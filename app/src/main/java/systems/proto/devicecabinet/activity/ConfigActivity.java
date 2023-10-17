package systems.proto.devicecabinet.activity;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;

import eu.hsah.dbobjects.DbObjects;
import eu.hsah.dbobjects.Param;
import systems.proto.devicecabinet.R;
import systems.proto.devicecabinet.data.Params;

public class ConfigActivity extends AppCompatActivity {

    public static String DEFAULT_HOST = "sgbdumtom01.covetrus.net:8080/Cabinet_Monitor";

    private CheckBox activeCB;
    private DbObjects dbo;
    private Param activeParam;
    private Param hostParam;
    private TextView hostTV;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_config);
        dbo = DbObjects.getInstance(this);
        dbo.setShowSql(true);
        activeParam = Params.getParam(dbo,Params.PARAM_ACTIVE);
        if (activeParam.intVal==null) {
            activeParam.intVal = 1;
            dbo.set(activeParam);
        }
        activeCB = (CheckBox) findViewById(R.id.active_cb);
        activeCB.setChecked(activeParam.intVal==1);
        hostParam = Params.getParam(dbo,Params.PARAM_HOSTNAME);
        hostTV = (TextView) findViewById(R.id.hostname_tf);
        hostTV.setText(hostParam.strVal);
        hostTV.setOnLongClickListener(view -> {
            hostTV.setText(DEFAULT_HOST);
            return false;
        });

    }

    @Override
    public void onBackPressed() {

        System.out.println("back!");
        activeParam.intVal = activeCB.isChecked()?1:0;
        dbo.set(activeParam);
        hostParam.strVal = hostTV.getText().toString();
        dbo.set(hostParam);
        super.onBackPressed();

    }
}