package com.oliver.daedalon.client.glaux.interaction;

/** Gaze hysteresis and one locked owl, driven by monotonic milliseconds. */
public final class GlauxFocusState {
    public static final long DWELL_MILLIS = 200;
    public static final long LOOK_AWAY_MILLIS = 250;
    private Long hover, active, dismissed;
    private long hoverSince, awaySince = -1;

    public boolean tick(Long gazed, long now, boolean pinned) {
        if (!java.util.Objects.equals(gazed, dismissed)) dismissed = null;
        if (!java.util.Objects.equals(gazed, hover)) { hover = gazed; hoverSince = now; }
        if (active == null) return false;
        if (pinned || active.equals(gazed)) { awaySince = -1; return false; }
        if (awaySince < 0) awaySince = now;
        if (now - awaySince < LOOK_AWAY_MILLIS) return false;
        active = null;
        awaySince = -1;
        return true;
    }

    public Long hint(long now) {
        return hover != null && !hover.equals(dismissed) && now - hoverSince >= DWELL_MILLIS ? hover : null;
    }

    public void activate(long owl, long now) { active = owl; hover = owl; hoverSince = now; awaySince = -1; dismissed = null; }
    public void release(boolean dismiss) { if (dismiss) dismissed = active != null ? active : hover; active = null; awaySince = -1; }
    public void reset() { hover = null; active = null; dismissed = null; awaySince = -1; }
    public Long active() { return active; }
}
