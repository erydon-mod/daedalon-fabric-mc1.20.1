package com.oliver.daedalon.block;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.util.function.BooleanBiFunction;
import net.minecraft.util.StringIdentifiable;

import java.util.List;

/** Internal bowl geometry used by a plinth-controlled fountain assembly. */
public final class FountainBowlModel {
    /** Measurements taken from Oliver's corrected Gothic bowl OBJ. */
    public static final float NORMALIZED_MODEL_HEIGHT = 0.8810146F;
    public static final float CONNECTION_RISE_PER_DIAMETER = 0.6430F;
    public static final float WATER_FLOOR_PER_DIAMETER = 0.6430F;
    public static final float WATER_SURFACE_PER_DIAMETER = 0.8700F;
    // The conservative solid collision inset is independent of the near-rim waterline.
    public static final float WATER_HALF_WIDTH_PER_DIAMETER = 0.3700F;
    public static final float WATER_BEVEL_PER_DIAMETER = 0.1400F;

    private static final double WATER_PICK_THICKNESS = 1.0D / 64.0D;
    private static final double WATER_INTERSECTION_EPSILON = 1.0E-7D;
    private static final VoxelShape[][] COLLISION_SHAPES = createCollisionShapes();
    private static final VoxelShape[][] DRY_OUTLINE_SHAPES = COLLISION_SHAPES;
    private static final VoxelShape[][] WET_OUTLINE_SHAPES = createWetOutlineShapes();

    private FountainBowlModel() {
    }

    public static List<Size> sequence(TierCount tierCount) {
        return tierCount.sequence();
    }

    public static VoxelShape collisionShape(Size size) {
        return collisionShape(Style.GOTHIC, size);
    }

    public static VoxelShape collisionShape(Style style, Size size) {
        return COLLISION_SHAPES[style.ordinal()][size.ordinal()];
    }

    public static VoxelShape outlineShape(Size size, boolean filled) {
        return outlineShape(Style.GOTHIC, size, filled);
    }

    public static VoxelShape outlineShape(Style style, Size size, boolean filled) {
        return (filled ? WET_OUTLINE_SHAPES : DRY_OUTLINE_SHAPES)
                [style.ordinal()][size.ordinal()];
    }

    public static double containedWaterHeight(BlockPos anchorPos,
                                              double baseY,
                                              Size size,
                                              Box box) {
        return containedWaterHeight(anchorPos, baseY, Style.GOTHIC, size, box);
    }

    public static double containedWaterHeight(BlockPos anchorPos,
                                              double baseY,
                                              Style style,
                                              Size size,
                                              Box box) {
        double bottom = anchorPos.getY() + baseY + style.waterFloorY(size);
        double surface = anchorPos.getY() + baseY + style.waterSurfaceY(size);
        if (box.maxY <= bottom + WATER_INTERSECTION_EPSILON
                || box.minY > surface + WATER_INTERSECTION_EPSILON) {
            return 0.0D;
        }

        double centerX = anchorPos.getX() + 0.5D;
        double centerZ = anchorPos.getZ() + 0.5D;
        double diameter = size.diameter();
        if (!style.waterOutline().intersects(
                (box.minX - centerX) / diameter, (box.minZ - centerZ) / diameter,
                (box.maxX - centerX) / diameter, (box.maxZ - centerZ) / diameter)) {
            return 0.0D;
        }
        return Math.max(0.0D, surface - box.minY);
    }

    private static VoxelShape[][] createCollisionShapes() {
        VoxelShape[][] result = new VoxelShape[Style.values().length][Size.values().length];
        for (Style style : Style.values()) {
            for (Size size : Size.values()) {
                double diameter = size.diameter();
                double previousY = 0.0D;
                VoxelShape outer = VoxelShapes.empty();
                for (CollisionStage stage : style.geometry().collisionStages()) {
                    VoxelShape band = FountainBasinGeometry.createOctagonalPrism(
                            stage.halfWidth() * diameter,
                            stage.straightHalfLength() * diameter,
                            previousY * diameter,
                            stage.maximumY() * diameter,
                            false
                    );
                    outer = VoxelShapes.combine(outer, band, BooleanBiFunction.OR);
                    previousY = stage.maximumY();
                }
                VoxelShape interior = FountainBasinGeometry.createOctagonalPrism(
                        style.geometry().waterHalfWidth() * diameter,
                        style.geometry().waterBevel() * diameter,
                        style.waterFloorY(size),
                        style.modelHeight(size) + 0.001D,
                        true
                );
                VoxelShape shape = VoxelShapes.combine(
                        outer,
                        interior,
                        BooleanBiFunction.ONLY_FIRST
                );
                GeometryProfile geometry = style.geometry();
                if (geometry.coreMaximumY() > 0.0F) {
                    VoxelShape core = FountainBasinGeometry.createOctagonalPrism(
                            geometry.coreHalfWidth() * diameter,
                            geometry.coreStraightHalfLength() * diameter,
                            0.0D,
                            geometry.coreMaximumY() * diameter,
                            false
                    );
                    shape = VoxelShapes.combine(shape, core, BooleanBiFunction.OR);
                }
                result[style.ordinal()][size.ordinal()] = shape;
            }
        }
        return result;
    }

    private static VoxelShape[][] createWetOutlineShapes() {
        VoxelShape[][] result = new VoxelShape[Style.values().length][Size.values().length];
        for (Style style : Style.values()) {
            for (Size size : Size.values()) {
                VoxelShape waterSurface = style.waterOutline().prism(
                        size.diameter(),
                        style.waterSurfaceY(size) - WATER_PICK_THICKNESS,
                        style.waterSurfaceY(size),
                        false
                );
                result[style.ordinal()][size.ordinal()] = VoxelShapes.combine(
                        COLLISION_SHAPES[style.ordinal()][size.ordinal()],
                        waterSurface,
                        BooleanBiFunction.OR
                );
            }
        }
        return result;
    }

    public enum Size {
        SMALL(1.0F),
        MEDIUM(1.5F),
        LARGE(2.0F);

        private final float diameter;

        Size(float diameter) {
            this.diameter = diameter;
        }

        public float diameter() {
            return diameter;
        }

        public float renderScale() {
            return diameter / 2.0F;
        }

        public float modelHeight() {
            return diameter * NORMALIZED_MODEL_HEIGHT;
        }

        public float modelHeight(Style style) {
            return style.modelHeight(this);
        }

        public float connectionRise() {
            return diameter * CONNECTION_RISE_PER_DIAMETER;
        }

        public float connectionRise(Style style) {
            return style.connectionRise(this);
        }

        public float waterSurfaceY() {
            return diameter * WATER_SURFACE_PER_DIAMETER;
        }

        public float waterSurfaceY(Style style) {
            return style.waterSurfaceY(this);
        }

        public float waterFloorY() {
            return diameter * WATER_FLOOR_PER_DIAMETER;
        }

        public float waterFloorY(Style style) {
            return style.waterFloorY(this);
        }

        public float waterHalfWidth() {
            return Style.GOTHIC.waterHalfWidth(this);
        }

        public float waterHalfWidth(Style style) {
            return style.waterHalfWidth(this);
        }

        public float waterBevel() {
            return diameter * WATER_BEVEL_PER_DIAMETER;
        }

        public float waterBevel(Style style) {
            return style.waterBevel(this);
        }
    }

    /** Player-facing debug values for the number of installed bowl tiers. */
    public enum TierCount implements StringIdentifiable {
        NONE("none", List.of()),
        ONE("one", List.of(Size.SMALL)),
        TWO("two", List.of(Size.MEDIUM, Size.SMALL)),
        THREE("three", List.of(Size.LARGE, Size.MEDIUM, Size.SMALL));

        private static final List<TierCount> ALL = List.of(values());

        private final String name;
        private final List<Size> sequence;

        TierCount(String name, List<Size> sequence) {
            this.name = name;
            this.sequence = sequence;
        }

        public int bowlCount() {
            return sequence.size();
        }

        public List<Size> sequence() {
            return sequence;
        }

        @Override
        public String asString() {
            return name;
        }

        public static TierCount fromBowlCount(int bowlCount) {
            return switch (bowlCount) {
                case 0 -> NONE;
                case 1 -> ONE;
                case 2 -> TWO;
                case 3 -> THREE;
                default -> throw new IllegalArgumentException(
                        "Unsupported fountain tier count: " + bowlCount
                );
            };
        }

        public static List<TierCount> allowed() {
            return ALL;
        }
    }

    public enum Style {
        GOTHIC(
                "gothic",
                "fountain_gothic_bowl",
                new GeometryProfile(
                        0.8810146F,
                        0.6430F,
                        0.6430F,
                        0.8700F,
                        0.3700F,
                        0.1400F,
                        0.0F,
                        0.0F,
                        0.0F,
                        new CollisionStage[]{
                                new CollisionStage(0.4500F, 0.1800F, 0.0750F),
                                new CollisionStage(0.5000F, 0.2500F, 0.1040F),
                                new CollisionStage(0.5800F, 0.3600F, 0.1490F),
                                new CollisionStage(0.8810146F, 0.5000F, 0.2070F)
                        }
                )
        ),
        GEORGIAN(
                "georgian",
                "fountain_georgian_bowl",
                new GeometryProfile(
                        0.5700245F,
                        0.4100F,
                        0.5000F,
                        0.5620F,
                        0.3700F,
                        0.0100F,
                        0.0F,
                        0.0F,
                        0.0F,
                        new CollisionStage[]{
                                new CollisionStage(0.3400F, 0.0600F, 0.0200F),
                                new CollisionStage(0.4000F, 0.1600F, 0.0020F),
                                new CollisionStage(0.4600F, 0.3080F, 0.0020F),
                                new CollisionStage(0.5200F, 0.4080F, 0.0020F),
                                new CollisionStage(0.5700245F, 0.4780F, 0.0020F)
                        }
                )
        ),
        GREEK(
                "greek",
                "fountain_greek_bowl",
                new GeometryProfile(
                        0.6385216F,
                        0.5500F,
                        0.5500F,
                        0.6300F,
                        0.4000F,
                        0.1650F,
                        0.6125F,
                        0.1050F,
                        0.0430F,
                        new CollisionStage[]{
                                new CollisionStage(0.3200F, 0.1100F, 0.0460F),
                                new CollisionStage(0.4000F, 0.2600F, 0.1080F),
                                new CollisionStage(0.5000F, 0.4500F, 0.1860F),
                                new CollisionStage(0.6385216F, 0.5000F, 0.2070F)
                        }
                )
        );

        private final String id;
        private final String resourceStem;
        private final GeometryProfile geometry;

        Style(String id, String resourceStem, GeometryProfile geometry) {
            this.id = id;
            this.resourceStem = resourceStem;
            this.geometry = geometry;
        }

        public String id() {
            return id;
        }

        public String resourceStem() {
            return resourceStem;
        }

        public String basinIdSuffix() {
            return id + "_fountain_basin";
        }

        public GeometryProfile geometry() {
            return geometry;
        }

        public float modelHeight(Size size) {
            return geometry.modelHeight() * size.diameter();
        }

        public float connectionRise(Size size) {
            return geometry.connectionRise() * size.diameter();
        }

        public float waterFloorY(Size size) {
            return geometry.waterFloorY() * size.diameter();
        }

        public float waterSurfaceY(Size size) {
            return geometry.waterSurfaceY() * size.diameter();
        }

        public float waterHalfWidth(Size size) {
            return waterOutline().halfWidth() * size.diameter();
        }

        /** Keyed by source mesh; basin style pairing is intentionally independent. */
        public FountainWaterShape waterOutline() {
            return switch (this) {
                case GOTHIC -> FountainWaterShape.GOTHIC_BOWL;
                case GEORGIAN -> FountainWaterShape.GEORGIAN_BOWL;
                case GREEK -> FountainWaterShape.GREEK_BOWL;
            };
        }

        public float waterBevel(Size size) {
            return geometry.waterBevel() * size.diameter();
        }
    }

    public record GeometryProfile(float modelHeight,
                                  float connectionRise,
                                  float waterFloorY,
                                  float waterSurfaceY,
                                  float waterHalfWidth,
                                  float waterBevel,
                                  float coreMaximumY,
                                  float coreHalfWidth,
                                  float coreStraightHalfLength,
                                  CollisionStage[] collisionStages) {
    }

    public record CollisionStage(float maximumY,
                                 float halfWidth,
                                 float straightHalfLength) {
    }
}
