package hr.phoenixart.crowdradar.core;

import java.util.*;
import java.security.SecureRandom;
import java.util.Base64;

/** Foreground-only mailbox. IDs are random; entries never touch disk or logs. */
public final class ShortMessages {
    public static final int MAX_TEXT=48;
    public static final long TTL=60000;
    public static final class Entry {
        public final String id,text,name;public final int peer;public final boolean outgoing;
        public final long at;public String state;
        Entry(String id,int peer,String name,String text,boolean outgoing,long at,String state){this.id=id;this.peer=peer;this.name=name;this.text=text;this.outgoing=outgoing;this.at=at;this.state=state;}
    }
    private final List<Entry> history=new ArrayList<>();
    private final LinkedHashMap<String,Long> seen=new LinkedHashMap<>();
    private final LinkedHashSet<String> acks=new LinkedHashSet<>();
    private Entry pending;
    public static boolean validText(String text){
        if(text==null||text.trim().isEmpty()||text.length()>MAX_TEXT)return false;
        for(int i=0;i<text.length();i++){char c=text.charAt(i);if(Character.isISOControl(c)||c=='\u2028'||c=='\u2029')return false;
            if(Character.isHighSurrogate(c)){if(i+1>=text.length()||!Character.isLowSurrogate(text.charAt(++i)))return false;}
            else if(Character.isLowSurrogate(c))return false;}
        return true;
    }
    public static boolean validId(String id){return id!=null&&id.matches("[A-Za-z0-9_-]{16}");}
    void trim(){while(history.size()>20)history.remove(0);}
    public synchronized Entry send(int peer,String name,String text,long now){
        expire(now);if(pending!=null)throw new IllegalStateException("Poruka već čeka potvrdu.");
        if(text!=null)text=text.trim();if(!validText(text))throw new IllegalArgumentException("Upiši kratku poruku, najviše 48 znakova, bez prijeloma retka.");
        byte[] random=new byte[12];new SecureRandom().nextBytes(random);
        pending=new Entry(Base64.getUrlEncoder().withoutPadding().encodeToString(random),peer,name,text,true,now,"ČEKA POTVRDU");history.add(pending);trim();return pending;
    }
    public synchronized Entry pending(long now){expire(now);return pending;}
    public synchronized boolean acknowledge(int peer,String id,long now){
        expire(now);for(Entry entry:history)if(entry.outgoing&&entry.peer==peer&&entry.id.equals(id)&&!entry.state.startsWith("DOSTAVLJENO")){
            entry.state=entry.state.equals("NIJE POTVRĐENO")?"DOSTAVLJENO NAKNADNO":"DOSTAVLJENO";
            if(pending==entry)pending=null;return true;
        }return false;
    }
    public synchronized Entry receive(int peer,int local,int target,String id,String name,String text,long now){
        if(target!=local||!validId(id)||!validText(text))return null;
        seen.entrySet().removeIf(e->now-e.getValue()>120000||now<e.getValue());
        String token=peer+":"+id;boolean first=!seen.containsKey(token);
        if(first&&seen.size()>=512)return null;
        seen.put(token,now);
        acks.remove(id);acks.add(id);while(acks.size()>6)acks.remove(acks.iterator().next());
        if(!first)return null;Entry entry=new Entry(id,peer,name,text,false,now,"PRIMLJENO");history.add(entry);trim();return entry;
    }
    public synchronized List<String> acknowledgements(){return new ArrayList<>(acks);}
    public synchronized List<Entry> history(long now){expire(now);return new ArrayList<>(history);}
    public synchronized void expire(long now){if(pending!=null&&(now-pending.at>=TTL||now<pending.at)){pending.state="NIJE POTVRĐENO";pending=null;}}
    public synchronized void stop(){if(pending!=null){pending.state="PREKINUTO";pending=null;}acks.clear();seen.clear();}
    public synchronized void clear(){stop();history.clear();}
}
