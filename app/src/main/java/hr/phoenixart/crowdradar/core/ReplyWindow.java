package hr.phoenixart.crowdradar.core;

/** A reply is useful only for the current peer, recipient and live, single-use request nonce. */
public final class ReplyWindow {
    private ReplyWindow(){}
    public static boolean accepts(String expected,String received,int expectedPeer,int sender,int local,int recipient,long requested,long now){
        return expected!=null&&expected.equals(received)&&expectedPeer==sender&&local==recipient&&now>=requested&&now-requested<=6000;
    }
}
