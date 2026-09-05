package com.oliver.daedalon.block;

import net.minecraft.structure.StructureTemplate;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class FountainBasinPartBlockTest {
    private static final int OFFSET_Y = 3;

    @Test
    void rotationsTransformEveryHorizontalOffset() {
        for (int offsetX = -2; offsetX <= 2; offsetX++) {
            for (int offsetZ = -2; offsetZ <= 2; offsetZ++) {
                assertRotatedOffset(
                        offsetX,
                        offsetZ,
                        BlockRotation.NONE,
                        offsetX,
                        offsetZ
                );
                assertRotatedOffset(
                        offsetX,
                        offsetZ,
                        BlockRotation.CLOCKWISE_90,
                        -offsetZ,
                        offsetX
                );
                assertRotatedOffset(
                        offsetX,
                        offsetZ,
                        BlockRotation.CLOCKWISE_180,
                        -offsetX,
                        -offsetZ
                );
                assertRotatedOffset(
                        offsetX,
                        offsetZ,
                        BlockRotation.COUNTERCLOCKWISE_90,
                        offsetZ,
                        -offsetX
                );
            }
        }
    }

    @Test
    void mirrorsTransformEveryHorizontalOffset() {
        for (int offsetX = -2; offsetX <= 2; offsetX++) {
            for (int offsetZ = -2; offsetZ <= 2; offsetZ++) {
                assertMirroredOffset(
                        offsetX,
                        offsetZ,
                        BlockMirror.NONE,
                        offsetX,
                        offsetZ
                );
                assertMirroredOffset(
                        offsetX,
                        offsetZ,
                        BlockMirror.LEFT_RIGHT,
                        offsetX,
                        -offsetZ
                );
                assertMirroredOffset(
                        offsetX,
                        offsetZ,
                        BlockMirror.FRONT_BACK,
                        -offsetX,
                        offsetZ
                );
            }
        }
    }

    @Test
    void structureTransformsKeepEveryPartOwnedByItsTransformedAnchor() {
        BlockPos anchor = new BlockPos(10, 64, 20);
        BlockPos pivot = new BlockPos(3, 0, -5);
        for (BlockMirror mirror : BlockMirror.values()) {
            for (BlockRotation rotation : BlockRotation.values()) {
                BlockPos transformedAnchor = StructureTemplate.transformAround(
                        anchor,
                        mirror,
                        rotation,
                        pivot
                );
                for (int offsetX = -2; offsetX <= 2; offsetX++) {
                    for (int offsetZ = -2; offsetZ <= 2; offsetZ++) {
                        BlockPos transformedPart = StructureTemplate.transformAround(
                                anchor.add(offsetX, OFFSET_Y, offsetZ),
                                mirror,
                                rotation,
                                pivot
                        );
                        int mirroredX = FountainPartOffsetTransform.mirroredX(offsetX, mirror);
                        int mirroredZ = FountainPartOffsetTransform.mirroredZ(offsetZ, mirror);
                        int transformedX = FountainPartOffsetTransform.rotatedX(
                                mirroredX,
                                mirroredZ,
                                rotation
                        );
                        int transformedZ = FountainPartOffsetTransform.rotatedZ(
                                mirroredX,
                                mirroredZ,
                                rotation
                        );
                        BlockPos resolvedAnchor = transformedPart.add(
                                -transformedX,
                                -OFFSET_Y,
                                -transformedZ
                        );
                        assertEquals(transformedAnchor, resolvedAnchor);
                    }
                }
            }
        }
    }

    private static void assertRotatedOffset(int sourceX,
                                            int sourceZ,
                                            BlockRotation rotation,
                                            int expectedX,
                                            int expectedZ) {
        assertEquals(
                expectedX,
                FountainPartOffsetTransform.rotatedX(sourceX, sourceZ, rotation)
        );
        assertEquals(
                expectedZ,
                FountainPartOffsetTransform.rotatedZ(sourceX, sourceZ, rotation)
        );
    }

    private static void assertMirroredOffset(int sourceX,
                                             int sourceZ,
                                             BlockMirror mirror,
                                             int expectedX,
                                             int expectedZ) {
        assertEquals(
                expectedX,
                FountainPartOffsetTransform.mirroredX(sourceX, mirror)
        );
        assertEquals(
                expectedZ,
                FountainPartOffsetTransform.mirroredZ(sourceZ, mirror)
        );
    }
}
