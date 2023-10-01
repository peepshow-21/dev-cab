package systems.proto.devicecabinet.data;

import eu.hsah.dbobjects.DbObjects;
import eu.hsah.dbobjects.Param;

public class Params {

    public static final String PARAM_ACTIVE     = "ACTIVE";
    public static final String PARAM_HOSTNAME   = "HOSTNAME";

    public static Param getParam(DbObjects dbo, String name) {

        Param p = (Param) dbo.getRow(Param.class, "type=?", name);
        if (p==null) {
            p = new Param();
            p.type = name;
            p.key = 0;
        }
        return p;

    }

}
