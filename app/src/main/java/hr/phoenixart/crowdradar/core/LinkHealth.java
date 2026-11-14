package hr.phoenixart.crowdradar.core;

/** One alert per debounced lost/restored transition; absence before first contact is not a loss. */
public final class LinkHealth {
    boolean known,lost;long missing=-1,recovering=-1;
    public void reset(){known=lost=false;missing=recovering=-1;}
    public int sample(boolean fresh,long now){
        if(fresh){known=true;missing=-1;if(lost){if(recovering<0)recovering=now;if(now-recovering>=3000){lost=false;recovering=-1;return 1;}}else recovering=-1;}
        else{recovering=-1;if(known&&!lost){if(missing<0)missing=now;if(now-missing>=6000){lost=true;missing=-1;return -1;}}}
        return 0;
    }
}
