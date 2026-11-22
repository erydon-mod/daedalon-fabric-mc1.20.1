package com.oliver.daedalon.client.config;

import net.minecraft.text.Text;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DaedalonControlTextureTest {
    @Test
    void movingARealButtonDoesNotResampleItsTexture() {
        var button = new DaedalonConfigUi.Button(40, 160, 248, Text.literal("Setting"), ignored -> { },
                DaedalonConfigUi.Button.Style.NEUTRAL);
        var sample = button.controlSample();
        for (int scroll : new int[] {0, 1, 26, 80, 200, 400}) {
            button.setY(160 - scroll);
            assertSame(sample, button.controlSample(), "Scrolling must move the button without moving its UV sample");
        }
        button.setX(80);
        assertSame(sample, button.controlSample());
    }

    @Test
    void neighboringButtonsKeepDifferentStableSamples() {
        var first = new DaedalonConfigUi.Button(40, 160, 248, Text.literal("First"), ignored -> { },
                DaedalonConfigUi.Button.Style.NEUTRAL);
        var second = new DaedalonConfigUi.Button(40, 186, 248, Text.literal("Second"), ignored -> { },
                DaedalonConfigUi.Button.Style.NEUTRAL);
        assertNotEquals(first.controlSample(), second.controlSample());
        var expected = first.controlSample();
        var rebuilt = new DaedalonConfigUi.Button(40, 160, 248, Text.literal("Rebuilt"), ignored -> { },
                DaedalonConfigUi.Button.Style.NEUTRAL);
        assertEquals(expected, rebuilt.controlSample(), "Rebuilding the same layout must not randomize its texture");
    }

    @Test
    void samplesStayInsideTheSourceTextureForFullAndNarrowControls() {
        for (int width : new int[] {1, 7, 80, 152, 248, 312, 360}) {
            for (int seed : new int[] {-1000, -26, 0, 40, 160, 1000}) {
                var sample = DaedalonConfigLayout.controlSample(seed, seed + 26, width, 20);
                assertTrue(sample.sourceX() >= 0 && sample.sourceY() >= 0);
                assertTrue(sample.sourceWidth() > 0 && sample.sourceHeight() > 0);
                assertTrue(sample.sourceX() + sample.sourceWidth() <= 1024);
                assertTrue(sample.sourceY() + sample.sourceHeight() <= 1024);
            }
        }
    }
}
