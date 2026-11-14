import hr.phoenixart.crowdradar.core.*;
import java.util.*;
import java.nio.charset.StandardCharsets;

public class FeatureTest {
 static int checks;
 static void check(boolean value){checks++;if(!value)throw new AssertionError("Feature check "+checks);}
 public static void main(String[] args)throws Exception{
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
   byte[] clear=json.getBytes(StandardCharsets.UTF_8),sealed=ProfileCrypto.seal(new byte[32],clear);check(sealed.length<=512);check(Arrays.equals(clear,ProfileCrypto.open(new byte[32],sealed)));
  }
  ShortMessages full=new ShortMessages();for(int i=0;i<512;i++)full.receive(11,22,22,String.format("%016d",i),"X","Test",20000+i);
  check(full.history(21000).size()==20);check(full.receive(11,22,22,"9999999999999999","X","Novi",21001)==null);
  check(full.receive(11,22,22,"0000000000000000","X","Test",21002)==null);check(full.acknowledgements().contains("0000000000000000"));
  MotionState motion=new MotionState();check(!motion.fresh(1000));
  for(int i=1;i<=20;i++)motion.sample(0,0,0,i*100);check(motion.fresh(2000));check(!motion.moving(2000));
  for(int i=21;i<=40;i++)motion.sample(1,0,0,i*100);check(motion.moving(4000));check(!motion.fresh(7001));
  motion.reset();check(!motion.fresh(4000));motion.sample(Double.NaN,0,0,4100);check(!motion.fresh(4100));
  System.out.println("PASS: "+checks+" messaging, crypto envelope and motion checks");
 }
}
