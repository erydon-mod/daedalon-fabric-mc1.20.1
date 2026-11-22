package com.oliver.daedalon.client.glaux.model;

/** Allocation-free angular controller; one state per recently visible sculpture. */
public final class GlauxMotion {
    public static final double AWARENESS_BLOCKS = 3;
    public static boolean withinAwareness(double squaredDistance) {
        return squaredDistance >= 0 && squaredDistance <= AWARENESS_BLOCKS * AWARENESS_BLOCKS;
    }
    private double yaw, velocity;
    public double yaw() { return yaw; }
    public static double wrap(double angle) { return Math.atan2(Math.sin(angle),Math.cos(angle)); }
    public static double shortestDelta(double current,double target) { return wrap(target-current); }
    public void advance(double target,double seconds) {
        double dt=Math.max(0,Math.min(.1,seconds)),delta=shortestDelta(yaw,target);
        if(Math.abs(delta)<Math.toRadians(1.5)){velocity*=Math.max(0,1-dt*12);if(Math.abs(delta)<.001)yaw=wrap(target);return;}
        double speed=Math.copySign(Math.min(Math.toRadians(45),Math.sqrt(2*Math.toRadians(100)*Math.abs(delta))),delta);
        double acceleration=Math.toRadians(100)*dt;
        velocity+=Math.max(-acceleration,Math.min(acceleration,speed-velocity));
        double step=velocity*dt;
        if(Math.signum(step)==Math.signum(delta)&&Math.abs(step)>Math.abs(delta)){yaw=wrap(target);velocity=0;}else yaw=wrap(yaw+step);
    }
    public void reset() { yaw=0; velocity=0; }
}
