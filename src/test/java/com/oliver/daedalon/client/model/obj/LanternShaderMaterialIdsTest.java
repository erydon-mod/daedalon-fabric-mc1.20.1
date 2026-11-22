package com.oliver.daedalon.client.model.obj;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class LanternShaderMaterialIdsTest {
    private record State(String material,boolean lit,String brightness) {
        State off() { return new State(material,false,brightness); }
    }

    @Test void bothLightLevelsKeepTheirMatchingPolishedSurfaceWithoutReplacingAnExplicitOnId() {
        var full=new State("polished",true,"full");
        var low=new State("polished",true,"low");
        var explicit=new State("custom",true,"full");
        var source=new Object2IntOpenHashMap<State>();source.defaultReturnValue(-1);
        source.put(full.off(),12041);source.put(low.off(),12025);source.put(low,0);
        source.put(explicit.off(),12033);source.put(explicit,23100);
        var result=LanternShaderMaterialIds.inheritOffSurfaceIds(source,List.of(full,low,explicit),State::off);
        assertEquals(12041,result.getInt(full));assertEquals(12025,result.getInt(low));
        assertEquals(23100,result.getInt(explicit));
        assertEquals(12041,result.getInt(full.off()));assertEquals(12025,result.getInt(low.off()));
        assertEquals(-1,source.getInt(full));assertEquals(0,source.getInt(low));
        assertEquals(source.defaultReturnValue(),result.defaultReturnValue());
    }

    @Test void unmappedOrZeroOffStatesAndOtherShaderDefaultsCannotInventSurfaceIds() {
        var on=new State("aged",true,"full");
        var source=new Object2IntOpenHashMap<State>();source.defaultReturnValue(73);
        assertSame(source,LanternShaderMaterialIds.inheritOffSurfaceIds(source,List.of(on),State::off));
        source.put(on.off(),0);
        assertSame(source,LanternShaderMaterialIds.inheritOffSurfaceIds(source,List.of(on),State::off));
        assertNull(LanternShaderMaterialIds.inheritOffSurfaceIds(null,List.of(on),State::off));
        source.put(on,23000);source.put(on.off(),12041);
        assertSame(source,LanternShaderMaterialIds.inheritOffSurfaceIds(source,List.of(on),State::off));
    }

    @Test void inheritanceRunsBeforeNeutralFallbackSoPolishedAndAgedShapesRemainSeparate() {
        var source=new Object2IntOpenHashMap<String>();source.defaultReturnValue(-1);
        source.put("water",32000);source.put("ladder",10721);source.put("polished_off",12041);
        var surfaces=LanternShaderMaterialIds.inheritOffSurfaceIds(source,List.of("polished_on","aged_on"),s->s.replace("_on","_off"));
        var result=PanelShaderMaterialIds.classify(surfaces,
                List.of("polished_on","polished_off","aged_on","aged_off"),"water","ladder");
        assertEquals(12041,result.getInt("polished_on"));assertEquals(12041,result.getInt("polished_off"));
        assertEquals(5000,result.getInt("aged_on"));assertEquals(5000,result.getInt("aged_off"));
        assertEquals(-1,source.getInt("aged_on"));
    }
}
