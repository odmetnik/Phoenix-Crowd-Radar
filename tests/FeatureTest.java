import hr.phoenixart.crowdradar.core.*;
import java.util.*;
import java.nio.charset.StandardCharsets;

public class FeatureTest {
 static int checks;
 static void check(boolean value){checks++;if(!value)throw new AssertionError("Feature check "+checks);}
 public static void main(String[] args)throws Exception{
  byte[] key=new byte[32];new java.security.SecureRandom().nextBytes(key);byte[] other=key.clone();other[0]^=1;
  byte[] clear="GPS / UWB / poruka — Željko".getBytes(StandardCharsets.UTF_8);byte[] envelope=NearCrypto.seal(key,clear);
  check(Arrays.equals(clear,NearCrypto.open(key,envelope)));check(!NearCrypto.service(key).equals(NearCrypto.service(other)));
  check(!Arrays.equals(envelope,NearCrypto.seal(key,clear)));
  try{NearCrypto.open(other,envelope);throw new AssertionError("Wrong group accepted");}catch(javax.crypto.AEADBadTagException expected){checks++;}
  byte[] tampered=envelope.clone();tampered[tampered.length-1]^=1;
  try{NearCrypto.open(key,tampered);throw new AssertionError("Tamper accepted");}catch(javax.crypto.AEADBadTagException expected){checks++;}
  try{ProfileCrypto.open(key,envelope);throw new AssertionError("Cross-transport frame accepted");}catch(javax.crypto.AEADBadTagException expected){checks++;}
  for(byte[] invalid:new byte[][]{new byte[0],new byte[27],new byte[4097]})try{NearCrypto.open(key,invalid);throw new AssertionError("Invalid frame accepted");}catch(IllegalArgumentException expected){checks++;}
  check(NearCrypto.seal(key,new byte[4068]).length==4096);
  try{NearCrypto.seal(key,new byte[4069]);throw new AssertionError("Oversize clear accepted");}catch(IllegalArgumentException expected){checks++;}
  check(ReplyWindow.accepts("fresh","fresh",2,2,1,1,1000,7000));
  check(!ReplyWindow.accepts("fresh","replay",2,2,1,1,1000,2000));check(!ReplyWindow.accepts(null,"fresh",2,2,1,1,1000,2000));
  check(!ReplyWindow.accepts("fresh","fresh",2,3,1,1,1000,2000));check(!ReplyWindow.accepts("fresh","fresh",2,2,1,3,1000,2000));
  check(!ReplyWindow.accepts("fresh","fresh",2,2,1,1,1000,7001));check(!ReplyWindow.accepts("fresh","fresh",2,2,1,1,1000,999));
  check(ArGuide.signed(359)==-1);check(ArGuide.signed(181)==-179);check(ArGuide.signed(-90)==-90);
  check(ArGuide.turn(359).equals("ISPRED TEBE"));check(ArGuide.turn(270).equals("OKRENI ULIJEVO"));check(ArGuide.turn(90).equals("OKRENI UDESNO"));
  check(ArGuide.turn(180).contains("IZA TEBE"));check(ArGuide.turn(Double.NaN).contains("NIJE IZMJEREN"));check(Double.isNaN(ArGuide.position(Double.NaN)));
  byte[] payload=new byte[900];new java.security.SecureRandom().nextBytes(payload);var fragments=Fragments.split(payload,11,180);check(fragments.size()>1);
  Fragments.Assembly assembly=new Fragments.Assembly();byte[] joined=null;
  for(int i=fragments.size()-1;i>=0;i--){byte[] next=assembly.accept(fragments.get(i),1000);if(next!=null)joined=next;}check(Arrays.equals(payload,joined));
  assembly=new Fragments.Assembly();check(assembly.accept(fragments.get(0),1000)==null);check(assembly.accept(fragments.get(0),1001)==null);
  check(assembly.expired(7001));check(assembly.accept(new byte[4000],2000)==null);
  var sealedParts=Fragments.split(NearCrypto.seal(key,payload),22,180);assembly=new Fragments.Assembly();joined=null;for(byte[] part:sealedParts){byte[] next=assembly.accept(part,3000);if(next!=null)joined=next;}check(Arrays.equals(payload,NearCrypto.open(key,joined)));
  byte[] conflicting=fragments.get(0).clone();conflicting[conflicting.length-1]^=1;assembly=new Fragments.Assembly();assembly.accept(fragments.get(0),1000);check(assembly.accept(conflicting,1001)==null);check(assembly.expired(1002));
  check(ArGuide.range(.6)==1);check(ArGuide.range(38)==50);check(ArGuide.range(0)==.5);check(ArGuide.range(Double.NaN)==.5);
  LinkHealth health=new LinkHealth();check(health.sample(false,1000)==0);check(health.sample(false,20000)==0);check(health.sample(true,20001)==0);check(health.sample(false,21000)==0);check(health.sample(false,26999)==0);check(health.sample(false,27000)==-1);check(health.sample(false,40000)==0);check(health.sample(true,41000)==0);check(health.sample(true,44000)==1);health.reset();check(health.sample(false,99999)==0);
  ShortMessages a=new ShortMessages(),b=new ShortMessages();
  ShortMessages.Entry sent=a.send(22,"Vlado","Kod glavnog ulaza.",1000);
  check(ShortMessages.validId(sent.id));check(a.pending(1001)!=null);
  check(b.receive(11,22,99,sent.id,"Željko",sent.text,1002)==null);check(b.acknowledgements().isEmpty());
  check(b.receive(11,22,22,sent.id,"Željko",sent.text,1003)!=null);check(b.history(1004).size()==1);
  check(b.receive(11,22,22,sent.id,"Željko",sent.text,1005)==null);check(b.history(1006).size()==1);
  a.acknowledge(33,sent.id,1007);check(a.pending(1007)!=null);a.acknowledge(22,"bad",1008);check(a.pending(1008)!=null);
  a.acknowledge(22,b.acknowledgements().get(0),1009);check(a.pending(1009)==null);check(sent.state.equals("DOSTAVLJENO"));
  ShortMessages.Entry timeout=a.send(22,"Vlado","Druga poruka",2000);check(a.pending(61999)!=null);check(a.pending(62000)==null);check(timeout.state.equals("NIJE POTVRĐENO"));
  a.acknowledge(22,timeout.id,63000);check(timeout.state.equals("DOSTAVLJENO NAKNADNO"));
  ShortMessages.Entry stopped=a.send(22,"Vlado","Test",64000);a.stop();check(stopped.state.equals("PREKINUTO"));check(a.pending(64001)==null);check(a.history(64001).size()==3);
  a.clear();check(a.history(64002).isEmpty());check(b.receive(11,22,22,"not-id","X","Test",5000)==null);
  for(String text:new String[]{"","   ","x\ny","x\ty","\ud800","\udc00","x".repeat(49)})check(!ShortMessages.validText(text));
  check(ShortMessages.validText("Željko, čekam kod izlaza."));check(ShortMessages.validText("\ud83d\ude00"));
  for(int i=0;i<10;i++)b.receive(11,22,22,String.format("%016d",i),"X","Test",10000+i);
  check(b.acknowledgements().size()==6);
  StringJoiner ack=new StringJoiner(",");for(String id:b.acknowledgements())ack.add("\""+id+"\"");
  for(String text:new String[]{"漢".repeat(48),"\\\"".repeat(24),"😀".repeat(24)}){
   check(ShortMessages.validText(text));String escaped=text.replace("\\","\\\\").replace("\"","\\\"");
   String json="{\"kind\":\"m\",\"session\":-2147483648,\"n\":\"AAAAAAAAAAAAAAAAAAAAAA==\",\"acks\":["+ack+"],\"msg\":{\"id\":\"1234567890123456\",\"to\":-2147483648,\"text\":\""+escaped+"\"}}";
   byte[] clearMail=json.getBytes(StandardCharsets.UTF_8),sealed=ProfileCrypto.seal(new byte[32],clearMail);check(sealed.length<=512);check(Arrays.equals(clearMail,ProfileCrypto.open(new byte[32],sealed)));
  }
  ShortMessages full=new ShortMessages();for(int i=0;i<512;i++)full.receive(11,22,22,String.format("%016d",i),"X","Test",20000+i);
  check(full.history(21000).size()==20);check(full.receive(11,22,22,"9999999999999999","X","Novi",21001)==null);
  check(full.receive(11,22,22,"0000000000000000","X","Test",21002)==null);check(full.acknowledgements().contains("0000000000000000"));
  MotionState motion=new MotionState();check(!motion.fresh(1000));
  for(int i=1;i<=20;i++)motion.sample(0,0,0,i*100);check(motion.fresh(2000));check(!motion.aboveThreshold(2000));
  for(int i=21;i<=40;i++)motion.sample(1,0,0,i*100);check(motion.aboveThreshold(4000));check(!motion.fresh(7001));
  motion.reset();check(!motion.fresh(4000));motion.sample(Double.NaN,0,0,4100);check(!motion.fresh(4100));
  System.out.println("PASS: "+checks+" messaging, crypto envelope and motion checks");
 }
}
