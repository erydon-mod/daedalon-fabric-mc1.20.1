package com.oliver.daedalon.client.glaux.model;

/** Finite gesture envelopes and the shared still/reduced-motion gate. */
public final class GlauxGestures {
    public static final long SHAKE_COOLDOWN_NANOS=12_000_000_000L;
    private GlauxGestures() { }
    public static boolean mayMove(boolean attentive,boolean reducedMotion){return attentive&&!reducedMotion;}
    public static boolean mayShake(boolean attentive,boolean reducedMotion,long now,long previous){return mayMove(attentive,reducedMotion)&&now-previous>=SHAKE_COOLDOWN_NANOS;}
    public static float nod(double elapsedTicks){double u=elapsedTicks/20.0;return u>=0&&u<1?(float)(Math.pow(Math.sin(Math.PI*u),2)*.028):0;}
    public static float shiver(double elapsedTicks){double u=elapsedTicks/12.4;return u>=0&&u<1?(float)(Math.sin(u*Math.PI*6)*.025*Math.pow(1-u,1.4)):0;}
}
