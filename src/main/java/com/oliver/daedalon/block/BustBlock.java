package com.oliver.daedalon.block;

import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;

/** Shared placed-state implementation for the classical bust collection. */
public final class BustBlock extends StatueBlock {
    private static final VoxelShape THREE_HIGH_NORTH_SHAPE = shape(
            -1.020, 0.000, -0.400, 2.020, 1.125, 1.400,
            -0.850, 1.125, -0.400, 1.850, 1.500, 1.400,
            -0.400, 1.500, -0.400, 1.400, 2.625, 1.400,
            -0.400, 2.625, -0.400, 1.400, 3.000, 1.400
    );

    public BustBlock(Settings settings, Style style) {
        super(settings, style.profile());
        // Busts place at the previously approved compact size, while their
        // Medium and Large states now scale normally when selected.
        setDefaultState(getDefaultState().with(SIZE, StatueSize.SMALL));
    }

    /** One immutable transform/collision profile shared by all 56 finishes. */
    public enum Style {
        APHRODITE("aphrodite", "Aphrodite", 0.4504320F, 0.6264431F),
        APOLLO("apollo", "Apollo", 0.4517038F, 0.6249626F),
        ARES("ares", "Ares", 0.4994324F, 0.5412111F),
        ARTEMIS("artemis", "Artemis", 0.5131261F, 0.5025154F),
        ATHENA("athena", "Athena", 0.5024868F, 0.6263738F),
        DEMETER("demeter", "Demeter", 0.5079903F, 0.5798224F),
        DIONYSUS("dionysus", "Dionysus", 0.3609965F, 0.6732582F),
        HEPHAESTUS("hephaestus", "Hephaestus", 0.4932719F, 0.4393166F),
        HERA("hera", "Hera", 0.5046978F, 0.6497283F),
        HERMES("hermes", "Hermes", 0.4655674F, 0.4573966F),
        HESTIA("hestia", "Hestia", 0.4594176F, 0.5000260F, shape(
                -1.020000, 0.000000, -0.452610, 2.018821, 0.361222, 1.452610,
                -1.020000, 0.361222, -0.452610, 2.020000, 0.722444, 1.450868,
                -0.969004, 0.722444, -0.345129, 1.996863, 1.083666, 1.393803,
                -0.663220, 1.083666, 0.075615, 1.681942, 1.444889, 1.366171,
                -0.286594, 1.444889, -0.167388, 1.368119, 1.806111, 1.294037,
                -0.247614, 1.806111, -0.236023, 1.286497, 2.167333, 1.215532,
                -0.215718, 2.167333, -0.280964, 1.222885, 2.528555, 1.159999,
                -0.188086, 2.528555, -0.280964, 1.086083, 2.889777, 1.065877
        )),
        POSEIDON("poseidon", "Poseidon", 0.4681214F, 0.5383594F),
        ZEUS("zeus", "Zeus", 0.5231352F, 0.7276151F);

        private final String subjectId;
        private final String displayName;
        private final Profile profile;

        Style(
                String subjectId,
                String displayName,
                float modelSupportCenterX,
                float modelSupportCenterZ
        ) {
            this(subjectId, displayName, modelSupportCenterX, modelSupportCenterZ,
                    THREE_HIGH_NORTH_SHAPE);
        }

        Style(
                String subjectId,
                String displayName,
                float modelSupportCenterX,
                float modelSupportCenterZ,
                VoxelShape threeHighSourceShape
        ) {
            this.subjectId = subjectId;
            this.displayName = displayName;
            this.profile = createProfile(
                    Direction.NORTH,
                    modelSupportCenterX,
                    0.0F,
                    modelSupportCenterZ,
                    threeHighSourceShape
            );
        }

        public String subjectId() {
            return subjectId;
        }

        public String resourceStem() {
            return "bust_" + subjectId;
        }

        public String displayName() {
            return displayName;
        }

        private Profile profile() {
            return profile;
        }
    }

    private static VoxelShape shape(double... coordinates) {
        if (coordinates.length == 0 || coordinates.length % 6 != 0) {
            throw new IllegalArgumentException("Bust shape coordinates must be non-empty boxes");
        }
        VoxelShape result = VoxelShapes.empty();
        for (int index = 0; index < coordinates.length; index += 6) {
            result = VoxelShapes.union(
                    result,
                    VoxelShapes.cuboid(
                            coordinates[index],
                            coordinates[index + 1],
                            coordinates[index + 2],
                            coordinates[index + 3],
                            coordinates[index + 4],
                            coordinates[index + 5]
                    )
            );
        }
        return result;
    }
}
