package com.oliver.daedalon.block;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class FinialSupportProfileTest {
    private static final List<String> SURFACES=List.of("flat","slope","shallow_lower","shallow_upper","steep_lower",
            "steep_upper","steep_upper_offset","diagonal","shallow_broad_right","shallow_broad_left",
            "shallow_broad_wide_right","shallow_broad_wide_left","shallow_narrow_right","shallow_narrow_left",
            "shallow_narrow_thin_right","shallow_narrow_thin_left","flat_half");

    @Test void bothCopingDesignsKeepTheSameSurfaceGeometryWithTheirOwnThickness() {
        for(String surface:SURFACES) {
            var ordinary=FinialSupport.Profile.find(surface,true);
            var grand=FinialSupport.Profile.find(surface,true,true);
            assertNotEquals(FinialSupport.Profile.NONE,ordinary,surface);
            assertNotEquals(FinialSupport.Profile.NONE,grand,surface);
            assertNotSame(ordinary,grand);
            assertEquals(ordinary.start,grand.start);
            assertEquals(ordinary.end,grand.end);
            assertEquals(ordinary.high,grand.high);
            assertEquals(ordinary.low,grand.low);
            assertEquals(ordinary.gradient(),grand.gradient());
            assertEquals(ordinary.alignment,grand.alignment);
            assertEquals(ordinary.offset,grand.offset);
            assertEquals(3.6/16,ordinary.copingThickness);
            assertEquals(8.0/16,grand.copingThickness);
            assertTrue(grand.grand());
            assertFalse(ordinary.grand());
            assertEquals("coping_"+surface,ordinary.asString());
            assertEquals("coping_grand_"+surface,grand.asString());
        }
    }

    @Test void copiedMirrorsKeepTheCopingDesignAndRoundTrip() {
        for(var profile:FinialSupport.Profile.values()) {
            assertSame(profile,profile.reflected().reflected());
            assertEquals(profile.copingThickness,profile.reflected().copingThickness);
        }
        assertSame(FinialSupport.Profile.COPING_GRAND_SHALLOW_BROAD_LEFT,
                FinialSupport.Profile.COPING_GRAND_SHALLOW_BROAD_RIGHT.reflected());
    }

    @Test void publishedProfileNamesRemainFirstAndNewSupportValuesStayPacked() {
        var profiles=FinialSupport.Profile.values();
        assertEquals(51,profiles.length);
        assertSame(FinialSupport.Profile.NONE,profiles[0]);
        assertSame(FinialSupport.Profile.COPING_SHALLOW_NARROW_THIN_LEFT,profiles[31]);
        assertSame(FinialSupport.Profile.COPING_FLAT_HALF,profiles[32]);
        assertSame(FinialSupport.Profile.HALF_HEIGHT,FinialSupport.Profile.find("half_height",false));
        assertEquals(-.5,FinialSupport.Profile.HALF_HEIGHT.high);
        assertEquals(0,FinialSupport.Profile.HALF_HEIGHT.copingThickness);
        assertSame(FinialSupport.Profile.NONE,FinialSupport.Profile.find("unknown",true,true));
    }
}
