package hr.phoenixart.crowdradar;

import hr.phoenixart.crowdradar.core.RelayRouter;
import org.json.*;
import java.util.*;

/** Compact allowlisted envelope. Never forward UWB keys, antenna angles or intermediate RTT measurements. */
final class RelayWire {
    private RelayWire(){}
    static JSONObject json(RelayRouter.Packet p)throws JSONException{
        if(p==null)return new JSONObject();JSONArray path=new JSONArray();for(int id:p.path)path.put(id);
        return new JSONObject().put("k",String.valueOf(p.kind)).put("i",p.id).put("o",p.origin).put("t",p.target).put("p",path).put("b",new JSONObject(p.body));
    }
    static RelayRouter.Packet parse(JSONObject value)throws JSONException{
        JSONArray path=value.getJSONArray("p");if(path.length()>RelayRouter.MAX_HOPS)throw new IllegalArgumentException();List<Integer> ids=new ArrayList<>();for(int i=0;i<path.length();i++)ids.add(path.getInt(i));
        String kind=value.getString("k");if(kind.length()!=1)throw new IllegalArgumentException();return new RelayRouter.Packet(kind.charAt(0),value.getString("i"),value.getInt("o"),value.getInt("t"),ids,value.getJSONObject("b").toString());
    }
}
