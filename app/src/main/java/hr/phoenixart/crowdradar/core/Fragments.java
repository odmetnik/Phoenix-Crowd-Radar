package hr.phoenixart.crowdradar.core;
import java.nio.ByteBuffer;
import java.util.*;

/** Bounded reassembly for small Aware management frames; authenticate the joined ciphertext separately. */
public final class Fragments {
    private Fragments(){}
    public static List<byte[]> split(byte[] bytes,int id,int limit){
        if(bytes==null||bytes.length<28||bytes.length>4096||limit<80||limit>255)throw new IllegalArgumentException();int payload=limit-12,count=(bytes.length+payload-1)/payload;
        List<byte[]> out=new ArrayList<>();for(int i=0;i<count;i++){int from=i*payload,n=Math.min(payload,bytes.length-from);ByteBuffer b=ByteBuffer.allocate(12+n);b.putShort((short)0x5037).putInt(id).putShort((short)i).putShort((short)count).putShort((short)bytes.length).put(bytes,from,n);out.add(b.array());}return out;
    }
    public static final class Assembly {
        public boolean expired(long now){return parts==null||now<started||now-started>6000;}
        byte[][] parts;int id,total,got;long started;
        public byte[] accept(byte[] frame,long now){
            if(frame==null||frame.length<13||frame.length>255)return null;ByteBuffer b=ByteBuffer.wrap(frame);if((b.getShort()&65535)!=0x5037)return null;
            int next=b.getInt(),index=b.getShort()&65535,count=b.getShort()&65535,length=b.getShort()&65535;
            if(count<1||count>64||index>=count||length<28||length>4096)return null;
            if(parts==null||id!=next||now-started>6000||now<started){parts=new byte[count][];id=next;total=length;got=0;started=now;}
            if(parts.length!=count||total!=length)return null;byte[] part=Arrays.copyOfRange(frame,12,frame.length);
            if(parts[index]!=null){if(!Arrays.equals(parts[index],part)){parts=null;}return null;}
            parts[index]=part;got+=part.length;if(got>total){parts=null;return null;}for(byte[] p:parts)if(p==null)return null;
            if(got!=total){parts=null;return null;}byte[] result=new byte[total];int offset=0;for(byte[] p:parts){System.arraycopy(p,0,result,offset,p.length);offset+=p.length;}parts=null;return result;
        }
    }
}
