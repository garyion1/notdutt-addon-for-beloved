package dev.khanh.feature;

/** A predicted local block change is not a new server block-update event. */
public final class PvpConfirmationFence {
    private long received,actionStarted;
    public void serverUpdate(){received++;}
    public void beginAction(){actionStarted=received;}
    public boolean confirmed(){return received>actionStarted;}
    public void reset(){received=actionStarted=0;}
}
