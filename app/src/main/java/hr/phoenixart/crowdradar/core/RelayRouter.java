package hr.phoenixart.crowdradar.core;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;

/** Bounded app-level forwarding, not Bluetooth SIG Mesh. No coordinates or radio ranges interpreted here. */
public final class RelayRouter {
    public static final int MAX_HOPS=4,MAX_BODY=230;
    public static final long QUERY_TIMEOUT=20000,ROUTE_TTL=30000;
    public static final class Packet {
        public final char kind;public final String id,body;public final int origin,target;public final List<Integer> path;
        public Packet(char k,String i,int o,int t,List<Integer> p,String b){
            if("aqr".indexOf(k)<0||i==null||!i.matches("[A-Za-z0-9_-]{16}")||b==null||b.getBytes(StandardCharsets.UTF_8).length>MAX_BODY||p==null||p.isEmpty()||p.size()>MAX_HOPS||p.get(0)!=o||new HashSet<>(p).size()!=p.size())throw new IllegalArgumentException("Invalid relay packet");
            kind=k;id=i;origin=o;target=t;path=List.copyOf(p);body=b;
        }
        Packet forward(int local){List<Integer> p=new ArrayList<>(path);p.add(local);return new Packet(kind,id,origin,target,p,body);}
        String key(){return kind+":"+origin+":"+id;}
    }
    public static final class Route {
        public final int endpoint,via,hops;public final List<Integer> path;public final long seen;
        Route(Packet p,int from,long now){endpoint=p.origin;via=from;hops=p.path.size();path=p.path;seen=now;}
    }
    public static final class Delivery {
        public final Packet packet;public final Route route;public final long roundTrip;
        Delivery(Packet p,Route r,long time){packet=p;route=r;roundTrip=time;}
    }
    static final class Queued {final Packet packet;final long expires;Queued(Packet p,long expiry){packet=p;expires=expiry;}}
    static final class Query {final int target;final long sent;Query(int t,long s){target=t;sent=s;}}
    final int local;final Map<String,Long> seen=new LinkedHashMap<>();final Map<Integer,Route> routes=new LinkedHashMap<>();
    final Map<String,Queued> queue=new LinkedHashMap<>();final Map<String,Query> pending=new LinkedHashMap<>();int urgentCursor,adCursor,calls;
    public RelayRouter(int own){local=own;}
    static String id(){byte[] b=new byte[12];new SecureRandom().nextBytes(b);return Base64.getUrlEncoder().withoutPadding().encodeToString(b);}
    void prune(long now){seen.entrySet().removeIf(e->now-e.getValue()>120000||now<e.getValue());routes.entrySet().removeIf(e->now-e.getValue().seen>ROUTE_TTL||now<e.getValue().seen);
        queue.entrySet().removeIf(e->now>e.getValue().expires);pending.entrySet().removeIf(e->now-e.getValue().sent>QUERY_TIMEOUT||now<e.getValue().sent);}
    void put(Packet p,long now){if(queue.containsKey(p.key()))return;if(queue.size()>=48){String discard=null;for(var e:queue.entrySet())if(e.getValue().packet.kind=='a'){discard=e.getKey();break;}if(discard==null)return;queue.remove(discard);}
        queue.put(p.key(),new Queued(p,now+(p.kind=='a'?16000:8000)));}
    public synchronized void advertise(String body,long now){prune(now);Packet p=new Packet('a',id(),local,0,List.of(local),body);
        queue.entrySet().removeIf(e->e.getValue().packet.kind=='a'&&e.getValue().packet.origin==local);put(p,now);}
    public synchronized boolean query(int target,String body,long now){prune(now);if(target==local||pending.size()>=8)return false;
        for(Query q:pending.values())if(q.target==target)return false;Packet p=new Packet('q',id(),local,target,List.of(local),body);pending.put(p.id,new Query(target,now));put(p,now);return true;}
    public synchronized void cancelQuery(int target){pending.entrySet().removeIf(e->e.getValue().target==target);queue.entrySet().removeIf(e->e.getValue().packet.kind=='q'&&e.getValue().packet.origin==local&&e.getValue().packet.target==target);}
    public synchronized void respond(Packet request,String body,long now){if(request.kind!='q'||request.target!=local)throw new IllegalArgumentException();put(new Packet('r',request.id,local,request.origin,List.of(local),body),now);}
    public synchronized Delivery receive(int from,Packet p,long now){prune(now);
        if(p.origin==local||p.path.contains(local)||p.path.get(p.path.size()-1)!=from||seen.containsKey(p.key()))return null;
        if(seen.size()>=512)seen.remove(seen.keySet().iterator().next());
        // An unsolicited reply must not poison the pending request or discovery state.
        Query q=p.kind=='r'&&p.target==local?pending.get(p.id):null;
        if(p.kind=='r'&&p.target==local&&(q==null||q.target!=p.origin||now<q.sent||now-q.sent>QUERY_TIMEOUT))return null;
        if(p.kind=='r'){String requestKey="q:"+p.target+":"+p.id;Queued request=queue.get(requestKey);if(request!=null&&request.packet.target==p.origin)queue.remove(requestKey);}
        seen.put(p.key(),now);Route route=new Route(p,from,now);Route old=routes.get(p.origin);
        if((p.kind=='r'&&p.target==local)||old==null||route.hops<=old.hops||now-old.seen>10000){if(routes.size()<24||old!=null)routes.put(p.origin,route);}
        if(p.kind=='q'&&p.target==local)return new Delivery(p,route,0);
        if(p.kind=='r'&&p.target==local){pending.remove(p.id);return new Delivery(p,route,now-q.sent);}
        if(p.path.size()<MAX_HOPS)put(p.forward(local),now);
        return p.kind=='a'?new Delivery(p,route,0):null;
    }
    public synchronized Packet outgoing(long now){prune(now);List<Packet> ads=new ArrayList<>(),urgent=new ArrayList<>();for(Queued q:queue.values())(q.packet.kind=='a'?ads:urgent).add(q.packet);
        boolean ad=(++calls%4==0||urgent.isEmpty())&&!ads.isEmpty();if(ad)return ads.get(Math.floorMod(adCursor++,ads.size()));if(!urgent.isEmpty())return urgent.get(Math.floorMod(urgentCursor++,urgent.size()));return null;}
    public synchronized Route route(int endpoint,long now){prune(now);return routes.get(endpoint);}
    public synchronized List<Route> routes(long now){prune(now);return new ArrayList<>(routes.values());}
    public synchronized void clear(){seen.clear();routes.clear();queue.clear();pending.clear();}
    public static long fixAge(long originAge,long roundTrip){if(originAge<0||roundTrip<0||originAge>10000||roundTrip>QUERY_TIMEOUT||originAge+roundTrip>10000)return Long.MAX_VALUE;return originAge+roundTrip;}
}
