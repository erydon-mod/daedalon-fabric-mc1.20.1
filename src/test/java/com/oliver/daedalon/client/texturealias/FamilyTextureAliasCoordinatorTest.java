package com.oliver.daedalon.client.texturealias;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FamilyTextureAliasCoordinatorTest {
    @Test
    void leaderPriorityIsErydonThenThemeliosThenDaedalon() {
        assertEquals(
                "erydon",
                FamilyTextureAliasCoordinator.selectLeader(
                        List.of("daedalon", "erydon_themelios", "erydon")
                )
        );
        assertEquals(
                "erydon_themelios",
                FamilyTextureAliasCoordinator.selectLeader(
                        List.of("daedalon", "erydon_themelios")
                )
        );
        assertEquals(
                "themelios",
                FamilyTextureAliasCoordinator.selectLeader(
                        List.of("daedalon", "themelios")
                )
        );
        assertEquals(
                "daedalon",
                FamilyTextureAliasCoordinator.selectLeader(
                        List.of("daedalon")
                )
        );
    }

    @Test
    void unrelatedCapabilitiesCannotDisplaceDaedalon() {
        assertEquals(
                "daedalon",
                FamilyTextureAliasCoordinator.selectLeader(
                        List.of("unrelated-resolver")
                )
        );
    }
}
