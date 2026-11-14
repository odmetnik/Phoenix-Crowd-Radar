package hr.phoenixart.crowdradar.core;

public final class Capabilities {
    public static final int BLE=1, UWB=2, AWARE=4, RTT=8, GPS=16, COMPASS=32;
    private Capabilities() {}
    public static String supported(int bits) {
        StringBuilder b=new StringBuilder();
        int[] flags={BLE,UWB,AWARE,RTT,GPS,COMPASS};
        String[] names={"BLE","UWB","Wi-Fi Aware","Wi-Fi RTT","GPS","kompas"};
        for(int i=0;i<flags.length;i++)if((bits&flags[i])!=0){if(b.length()>0)b.append(" • ");b.append(names[i]);}
        return b.length()==0?"Nema prijavljenih mogućnosti":b.toString();
    }
    public static String pairSummary(int mine,int peer) {
        int common=mine&peer;
        if((common&UWB)!=0)return "Zajednički UWB hardver • podrška hardvera ne jamči aktivnu sesiju";
        if((common&(AWARE|RTT))==(AWARE|RTT))return "Zajednički Wi-Fi Aware/RTT hardver • RTT mjerenje nije integrirano";
        return "BLE blizina • nema izmjerenog smjera ni točne udaljenosti";
    }
}
