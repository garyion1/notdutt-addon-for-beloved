package dev.khanh.feature;

/** Per-client-tick attack budget shared by manual and assisted combat actions. */
public final class PvpTickGate {
    private long tick,lastAttackTick=Long.MIN_VALUE;
    public void advance() {tick++;}
    public long tick() {return tick;}
    public boolean used() {return lastAttackTick==tick;}
    public boolean claimAttack() {
        if(lastAttackTick==tick)return false;
        lastAttackTick=tick;return true;
    }
    public void reset() {tick=0;lastAttackTick=Long.MIN_VALUE;}
}
