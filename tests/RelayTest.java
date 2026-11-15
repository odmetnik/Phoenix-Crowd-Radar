import hr.phoenixart.crowdradar.core.*;
import java.util.*;
import java.nio.charset.StandardCharsets;

/** Real forwarding state machine simulated without any Android/radio fake precision claims. */
public class RelayTest {
 static int checks;
 static void check(boolean ok){checks++;if(!ok)throw new AssertionError("Relay check "+checks);}
 static RelayRouter.Packet take(RelayRouter r,char kind,int origin,long now){for(int i=0;i<100;i++){RelayRouter.Packet p=r.outgoing(now);if(p!=null&&p.kind==kind&&p.origin==origin)return p;}throw new AssertionError("No queued "+kind+" from "+origin);}
 static RelayRouter.Packet packet(char kind,String id,int origin,int target,List<Integer> path,String body){return new RelayRouter.Packet(kind,id,origin,target,path,body);}
 public static void main(String[] args)throws Exception{
  RelayRouter a=new RelayRouter(11),b=new RelayRouter(22),c=new RelayRouter(33);
  c.advertise("{}",1000);RelayRouter.Packet ad=take(c,'a',33,1000);
  check(b.receive(33,ad,1010)!=null);RelayRouter.Packet bAd=take(b,'a',33,1010);check(bAd.path.equals(List.of(33,22)));
  check(a.receive(22,bAd,1020)!=null);check(a.route(33,1020).via==22);check(a.route(33,1020).hops==2);
  check(a.receive(22,bAd,1030)==null);check(b.receive(11,take(a,'a',33,1030),1040)==null);
  check(a.query(33,"{}",1100));check(!a.query(33,"{}",1101));RelayRouter.Packet query=take(a,'q',11,1100);
  check(b.receive(11,query,1200)==null);RelayRouter.Packet bQuery=take(b,'q',11,1200);
  RelayRouter.Delivery request=c.receive(22,bQuery,1300);check(request!=null&&request.packet.target==33);
  c.respond(request.packet,"{}",1400);RelayRouter.Packet reply=take(c,'r',33,1400);
  check(b.receive(33,reply,1500)==null);RelayRouter.Packet bReply=take(b,'r',33,1500);
  RelayRouter.Delivery answer=a.receive(22,bReply,1800);check(answer!=null);check(answer.roundTrip==700);check(answer.route.via==22&&answer.route.hops==2);
  check(a.receive(22,bReply,1801)==null);check(RelayRouter.fixAge(100,answer.roundTrip)==800);
  check(RelayRouter.fixAge(100,10000)==Long.MAX_VALUE);check(RelayRouter.fixAge(-1,10)==Long.MAX_VALUE);check(RelayRouter.fixAge(0,10001)==Long.MAX_VALUE);
  // A cached or unsolicited response cannot refresh endpoint coordinates after the request expires.
  RelayRouter fresh=new RelayRouter(11);check(fresh.receive(22,bReply,2000)==null);
  a.query(33,"{}",3000);RelayRouter.Packet lateQuery=take(a,'q',11,3000);RelayRouter.Packet late=packet('r',lateQuery.id,33,11,List.of(33,22),"{}");check(a.receive(22,late,23001)==null);
  a.query(33,"{}",25000);RelayRouter.Packet expected=take(a,'q',11,25000);
  check(a.receive(22,packet('r',expected.id,44,11,List.of(44,22),"{}"),25010)==null);
  check(a.receive(22,packet('r',expected.id,33,11,List.of(33,22),"{}"),25020)!=null);
  check(a.receive(99,ad,26000)==null); // last hop must match authenticated transport peer
  RelayRouter d=new RelayRouter(44),e=new RelayRouter(55),f=new RelayRouter(66);d.receive(11,packet('a',ad.id,33,0,List.of(33,22,11),"{}"),1600);
  RelayRouter.Packet four=take(d,'a',33,1600);check(four.path.size()==4);check(e.receive(44,four,1700)!=null);check(e.outgoing(1700)==null);
  check(f.route(33,1700)==null);check(a.route(33,60000)==null);
  a.query(77,"{}",61000);a.cancelQuery(77);check(a.query(77,"{}",61001));a.clear();check(a.outgoing(61002)==null);
  for(List<Integer> invalid:List.of(List.of(1,1),List.of(2),List.of(1,2,3,4,5)))try{packet('a',"1234567890123456",1,0,invalid,"{}");throw new AssertionError();}catch(IllegalArgumentException good){checks++;}
  try{packet('a',"bad",1,0,List.of(1),"{}");throw new AssertionError();}catch(IllegalArgumentException good){checks++;}
  // Worst supported Unicode/escaping, signed IDs, 4-hop path and native GATT nonce binding fit 512 bytes.
  String path="[-2147483648,-2147483647,-2147483646,-2147483645]";
  for(String body:List.of("{\"u\":\"12345678-1234-1234-1234-123456789012\",\"n\":\""+"漢".repeat(40)+"\",\"c\":63}","{\"i\":\"1234567890123456\",\"s\":\""+"漢".repeat(48)+"\",\"z\":2147483647}","{\"i\":\"1234567890123456\",\"s\":\""+"\\\"".repeat(48)+"\",\"z\":2147483647}")){
   check(body.getBytes(StandardCharsets.UTF_8).length<=RelayRouter.MAX_BODY);
   String frame="{\"k\":\"q\",\"i\":\"1234567890123456\",\"o\":-2147483648,\"t\":-2147483648,\"p\":"+path+",\"b\":"+body+",\"kind\":\"x\",\"session\":-2147483648,\"n\":\"AAAAAAAAAAAAAAAAAAAAAA==\"}";
   check(ProfileCrypto.seal(new byte[32],frame.getBytes(StandardCharsets.UTF_8)).length<=512);
  }
  System.out.println("PASS: "+checks+" multi-hop routing, loops, nonce/expiry and BLE envelope checks");
 }
}
