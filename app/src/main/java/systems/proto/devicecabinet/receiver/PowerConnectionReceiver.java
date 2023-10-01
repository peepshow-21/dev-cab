package systems.proto.devicecabinet.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import eu.hsah.dbobjects.DbObjects;
import eu.hsah.dbobjects.Param;
import systems.proto.devicecabinet.activity.MonitorActivity;
import systems.proto.devicecabinet.data.Params;

public class PowerConnectionReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {

        DbObjects dbo = DbObjects.getInstance(context);
        if (dbo==null) {
            dbo = DbObjects.getInstance(context);
        }
        Param p = Params.getParam(dbo,Params.PARAM_ACTIVE);
        if (p.intVal==null) {
            p.intVal = 1;
            dbo.set(p);
        }
        if (p.intVal==0) {
            System.out.println("power check inactive");
            return;
        }
        System.out.println("Power changed!!!!!! "+intent.getAction());
        Intent i = new Intent(context, MonitorActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        if ("android.intent.action.ACTION_POWER_CONNECTED".equals(intent.getAction())) {
            i.setAction("on");
        } else {
            i.setAction("off");
        }
        context.startActivity(i);
    }
}
