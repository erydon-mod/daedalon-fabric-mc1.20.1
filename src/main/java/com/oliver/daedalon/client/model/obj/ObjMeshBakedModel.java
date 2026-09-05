package com.oliver.daedalon.client.model.obj;

import com.oliver.daedalon.block.DecorShapeTransforms;
import com.oliver.daedalon.block.CorbelBlock;
import com.oliver.daedalon.block.ExedraBlock;
import com.oliver.daedalon.block.FacingDecorBlock;
import com.oliver.daedalon.block.FountainAssemblyLayout;
import com.oliver.daedalon.block.FountainBasinBlock;
import com.oliver.daedalon.block.FountainBowlModel;
import com.oliver.daedalon.block.FountainWaterShape;
import com.oliver.daedalon.block.PlinthBlock;
import com.oliver.daedalon.block.SizedDecorBlock;
import com.oliver.daedalon.block.StatueBlock;
import com.oliver.daedalon.block.TwoSizeDecorBlock;
import com.oliver.daedalon.block.UrnBlock;
import com.oliver.daedalon.block.entity.FountainBasinBlockEntity;
import net.fabricmc.fabric.api.renderer.v1.Renderer;
import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.fabricmc.fabric.api.renderer.v1.mesh.Mesh;
import net.fabricmc.fabric.api.renderer.v1.mesh.MeshBuilder;
import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.material.BlendMode;
import net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBakedModel;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.color.world.BiomeColors;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.render.model.json.ModelOverrideList;
import net.minecraft.client.render.model.json.ModelTransformation;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.texture.Sprite;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

final class ObjMeshBakedModel implements BakedModel, FabricBakedModel {
    private static final float NORMAL_EPSILON_SQUARED = 1.0E-24F;
    private static final float UV_AREA_EPSILON = 1.0E-12F;
    private static final long ESTIMATED_BYTES_PER_VERTEX = 36L;

    private final Mesh mesh;
    private final Mesh itemMesh;
    private final BakedModel metadataModel;
    private final Sprite particleSprite;
    private final TextureTransform textureTransform;
    private final TextureTransform[] worldTextureTransforms;
    private final WorldTexturePhase.Layout worldTexturePhase;
    private final List<Boolean> worldPhaseMaterials;
    private final ObjGuiIconTemplate guiIconTemplate;
    private final List<Identifier> materialTextureIds;
    private final Identifier modelId;
    private final Sprite containedWaterSprite;
    private final RenderMaterial containedWaterMaterial;

    private ObjMeshBakedModel(Mesh mesh,
                              Mesh itemMesh,
                              BakedModel metadataModel,
                              Sprite particleSprite,
                              List<Sprite> materialSprites,
                              List<Boolean> worldPhaseMaterials,
                              WorldTexturePhase.Layout worldTexturePhase,
                              ObjGuiIconTemplate guiIconTemplate,
                              List<Identifier> materialTextureIds,
                              Identifier modelId,
                              Sprite containedWaterSprite) {
        this.mesh = mesh;
        this.itemMesh = itemMesh;
        this.metadataModel = metadataModel;
        this.particleSprite = particleSprite;
        this.textureTransform = new TextureTransform(materialSprites, worldPhaseMaterials, worldTexturePhase, 0);
        this.worldTexturePhase = worldTexturePhase;
        this.worldPhaseMaterials = List.copyOf(worldPhaseMaterials);
        this.worldTextureTransforms = createWorldTextureTransforms(
                materialSprites,
                worldPhaseMaterials,
                worldTexturePhase
        );
        this.guiIconTemplate = guiIconTemplate;
        this.materialTextureIds = List.copyOf(materialTextureIds);
        this.modelId = modelId;
        this.containedWaterSprite = containedWaterSprite;
        Renderer renderer = RendererAccess.INSTANCE.getRenderer();
        this.containedWaterMaterial = containedWaterSprite == null || renderer == null
                ? null
                : renderer.materialFinder()
                        .blendMode(BlendMode.TRANSLUCENT)
                        .disableDiffuse(true)
                        .ambientOcclusion(TriState.FALSE)
                        .find();
    }

    static GeometryBakeResult bakeGeometry(ObjMeshDefinition definition, ObjMeshData data) {
        long started = System.nanoTime();
        Renderer renderer = RendererAccess.INSTANCE.getRenderer();
        if (renderer == null) {
            throw new IllegalStateException("No Fabric Renderer API implementation is active while baking " + data.objId());
        }

        Transform transform = Transform.create(definition, data.originalBounds());
        List<ObjMeshData.Vec3> transformedPositions = new ArrayList<>(data.positions().size());
        for (ObjMeshData.Vec3 position : data.positions()) {
            transformedPositions.add(transform.position(position));
        }
        ObjMeshData.Bounds transformedBounds = ObjMeshData.Bounds.of(transformedPositions);
        UvProjector uvProjector = UvProjector.create(definition, transformedPositions, transformedBounds);
        SmoothNormals smoothNormals = definition.smoothNormals()
                ? SmoothNormals.create(transformedPositions, data.faces(), definition.smoothAngleDegrees())
                : null;

        MeshBuilder meshBuilder = renderer.meshBuilder();
        QuadEmitter emitter = meshBuilder.getEmitter();
        List<String> materialNames = data.faces().stream()
                .map(ObjMeshData.Face::material)
                .distinct()
                .toList();
        BakeCounters counters = new BakeCounters(materialNames.size());

        for (ObjMeshData.Face face : data.faces()) {
            int materialTag = materialNames.indexOf(face.material());
            emitFace(emitter, definition, data, transformedPositions, uvProjector, smoothNormals,
                    face, materialTag, counters);
        }

        Mesh mesh = meshBuilder.build();
        int emittedQuadCount = data.finalQuadCount() * (definition.doubleSided() ? 2 : 1);
        long estimatedVertexBytes = (long) emittedQuadCount * 4L * ESTIMATED_BYTES_PER_VERTEX;
        return new GeometryBakeResult(
                mesh,
                transformedBounds,
                materialNames,
                emittedQuadCount,
                counters.repairedNormalCount,
                counters.smoothedNormalCornerCount,
                counters.repairedUvFaceCount,
                counters.degenerateFaceCount,
                counters.materialMaximumUs(),
                definition.textureUTiles(),
                definition.cylindricalURepeats(),
                estimatedVertexBytes,
                System.nanoTime() - started
        );
    }

    static ObjMeshBakedModel materialize(GeometryBakeResult geometry,
                                         Mesh itemMesh,
                                         BakedModel metadataModel,
                                         Sprite particleSprite,
                                         List<Sprite> materialSprites,
                                         List<Boolean> worldPhaseMaterials,
                                         WorldTexturePhase.Layout worldTexturePhase,
                                         ObjGuiIconTemplate guiIconTemplate,
                                         List<Identifier> materialTextureIds,
                                         Identifier modelId,
                                         Sprite containedWaterSprite) {
        if (materialSprites.size() != geometry.materialNames().size()) {
            throw new IllegalArgumentException("Material sprite count does not match baked OBJ material tags");
        }
        if (materialTextureIds.size() != geometry.materialNames().size()) {
            throw new IllegalArgumentException("Material texture ID count does not match baked OBJ material tags");
        }
        if (worldPhaseMaterials.size() != geometry.materialNames().size()) {
            throw new IllegalArgumentException("World-phase material count does not match baked OBJ material tags");
        }
        return new ObjMeshBakedModel(
                geometry.mesh(),
                itemMesh,
                metadataModel,
                particleSprite,
                materialSprites,
                worldPhaseMaterials,
                worldTexturePhase,
                guiIconTemplate,
                materialTextureIds,
                modelId,
                containedWaterSprite
        );
    }

    private static void emitFace(QuadEmitter emitter,
                                 ObjMeshDefinition definition,
                                 ObjMeshData data,
                                 List<ObjMeshData.Vec3> positions,
                                 UvProjector uvProjector,
                                 SmoothNormals smoothNormals,
                                 ObjMeshData.Face face,
                                 int materialTag,
                                 BakeCounters counters) {
        List<ObjMeshData.VertexRef> refs = face.vertices();
        ObjMeshData.Vec3 geometricNormal = geometricNormal(positions, refs);
        if (geometricNormal == null) {
            geometricNormal = firstUsableImportedNormal(definition, data, refs);
            counters.degenerateFaceCount++;
        }
        if (geometricNormal == null) {
            geometricNormal = new ObjMeshData.Vec3(0.0F, 1.0F, 0.0F);
        }
        Direction nominalFace = closestAxis(geometricNormal);
        FaceUvs faceUvs = faceUvs(definition, data, positions, uvProjector, face, geometricNormal, nominalFace, counters);
        boolean repairFloorNormals = definition.repairFloorNormals()
                && uvProjector.isInteriorFloorFace(positions, refs, geometricNormal);
        counters.recordMaximumU(materialTag, faceUvs.u());

        emitFaceSide(emitter, definition, data, positions, smoothNormals, refs,
                geometricNormal, nominalFace, faceUvs, materialTag, false, repairFloorNormals, counters);
        if (definition.doubleSided()) {
            emitFaceSide(emitter, definition, data, positions, smoothNormals, refs,
                    geometricNormal, nominalFace, faceUvs, materialTag, true, repairFloorNormals, counters);
        }
    }

    private static void emitFaceSide(QuadEmitter emitter,
                                     ObjMeshDefinition definition,
                                     ObjMeshData data,
                                     List<ObjMeshData.Vec3> positions,
                                     SmoothNormals smoothNormals,
                                     List<ObjMeshData.VertexRef> refs,
                                     ObjMeshData.Vec3 geometricNormal,
                                     Direction nominalFace,
                                     FaceUvs faceUvs,
                                     int materialTag,
                                     boolean reverseWinding,
                                     boolean repairFloorNormals,
                                     BakeCounters counters) {
        for (int vertexIndex = 0; vertexIndex < 4; vertexIndex++) {
            int sourceIndex = sourceIndex(refs.size(), vertexIndex, reverseWinding);
            ObjMeshData.VertexRef ref = refs.get(sourceIndex);
            ObjMeshData.Vec3 position = positions.get(ref.positionIndex());
            emitter.pos(vertexIndex, position.x(), position.y(), position.z());

            emitter.uv(vertexIndex, faceUvs.u()[sourceIndex], faceUvs.v()[sourceIndex]);

            ObjMeshData.Vec3 normal = geometricNormal;
            if (repairFloorNormals) {
                // The supplied planar floor has inconsistent corner normals even
                // among the nearly aligned corners. Give only this floor one normal.
                normal = new ObjMeshData.Vec3(0.0F, 1.0F, 0.0F);
                counters.repairedNormalCount++;
            } else if (smoothNormals != null) {
                ObjMeshData.Vec3 smoothed = smoothNormals.forCorner(ref.positionIndex(), geometricNormal);
                if (smoothed != null) {
                    normal = smoothed;
                    counters.smoothedNormalCornerCount++;
                }
            } else if (ref.normalIndex() != ObjMeshData.VertexRef.MISSING) {
                ObjMeshData.Vec3 imported = data.normals().get(ref.normalIndex());
                ObjMeshData.Vec3 transformed = transformNormal(imported, definition.scale());
                if (transformed != null) {
                    normal = transformed;
                } else {
                    counters.repairedNormalCount++;
                }
            }
            if (reverseWinding) {
                normal = negate(normal);
            }
            emitter.normal(vertexIndex, normal.x(), normal.y(), normal.z());
        }

        emitter.color(-1, -1, -1, -1);
        emitter.cullFace(null);
        emitter.nominalFace(reverseWinding ? nominalFace.getOpposite() : nominalFace);
        emitter.tag(ShaderTerrainNormalBridge.encodeObjTag(materialTag));
        emitter.emit();
    }

    private static int sourceIndex(int vertexCount, int emitterIndex, boolean reverseWinding) {
        if (!reverseWinding) {
            return vertexCount == 3 && emitterIndex == 3 ? 2 : emitterIndex;
        }
        if (emitterIndex == 0) {
            return 0;
        }
        return Math.max(1, vertexCount - emitterIndex);
    }

    private static ObjMeshData.Vec3 negate(ObjMeshData.Vec3 vector) {
        return new ObjMeshData.Vec3(-vector.x(), -vector.y(), -vector.z());
    }

    private static ObjMeshData.Vec3 geometricNormal(List<ObjMeshData.Vec3> positions,
                                                    List<ObjMeshData.VertexRef> refs) {
        ObjMeshData.Vec3 a = positions.get(refs.get(0).positionIndex());
        ObjMeshData.Vec3 b = positions.get(refs.get(1).positionIndex());
        ObjMeshData.Vec3 c = positions.get(refs.get(2).positionIndex());
        ObjMeshData.Vec3 normal = crossSubtract(a, b, c);
        ObjMeshData.Vec3 normalized = normalize(normal);
        if (normalized != null || refs.size() != 4) {
            return normalized;
        }

        ObjMeshData.Vec3 d = positions.get(refs.get(3).positionIndex());
        return normalize(crossSubtract(a, c, d));
    }

    private static FaceUvs faceUvs(ObjMeshDefinition definition,
                                   ObjMeshData data,
                                   List<ObjMeshData.Vec3> positions,
                                   UvProjector uvProjector,
                                   ObjMeshData.Face face,
                                   ObjMeshData.Vec3 geometricNormal,
                                   Direction nominalFace,
                                   BakeCounters counters) {
        List<ObjMeshData.VertexRef> refs = face.vertices();
        float[] u = new float[refs.size()];
        float[] v = new float[refs.size()];
        boolean missing = false;

        for (int i = 0; i < refs.size(); i++) {
            int textureIndex = refs.get(i).textureCoordinateIndex();
            if (textureIndex == ObjMeshData.VertexRef.MISSING) {
                missing = true;
                break;
            }
            ObjMeshData.Vec2 uv = data.textureCoordinates().get(textureIndex);
            u[i] = uv.u();
            v[i] = definition.flipV() ? 1.0F - uv.v() : uv.v();
        }

        boolean invalid = !missing && !hasUsableUvs(u, v, refs.size());
        if (definition.forceUvProjection() || missing || (definition.repairDegenerateUvs() && invalid)) {
            counters.repairedUvFaceCount++;
            projectUvs(u, v, positions, refs, face.material(), geometricNormal, nominalFace, uvProjector);
        }
        return new FaceUvs(u, v);
    }

    private static boolean hasUsableUvs(float[] u, float[] v, int vertexCount) {
        for (int i = 0; i < vertexCount; i++) {
            if (!Float.isFinite(u[i]) || !Float.isFinite(v[i])
                    || u[i] < 0.0F || u[i] > 1.0F
                    || v[i] < 0.0F || v[i] > 1.0F) {
                return false;
            }
        }
        if (Math.abs(uvArea(u, v, 0, 1, 2)) <= UV_AREA_EPSILON) {
            return false;
        }
        return vertexCount != 4 || Math.abs(uvArea(u, v, 0, 2, 3)) > UV_AREA_EPSILON;
    }

    private static float uvArea(float[] u, float[] v, int a, int b, int c) {
        return (u[b] - u[a]) * (v[c] - v[a]) - (v[b] - v[a]) * (u[c] - u[a]);
    }

    private static void projectUvs(float[] u,
                                   float[] v,
                                   List<ObjMeshData.Vec3> positions,
                                   List<ObjMeshData.VertexRef> refs,
                                   String materialName,
                                   ObjMeshData.Vec3 geometricNormal,
                                   Direction nominalFace,
                                   UvProjector uvProjector) {
        UvProjector.ProjectionChoice projection = uvProjector.chooseProjection(
                positions,
                refs,
                materialName,
                geometricNormal,
                nominalFace
        );
        for (int i = 0; i < refs.size(); i++) {
            ObjMeshData.Vec3 position = positions.get(refs.get(i).positionIndex());
            uvProjector.project(u, v, i, position, projection);
        }
    }

    private static float normalizeCoordinate(float value, float minimum, float span) {
        return span <= 0.0F ? 0.5F : (value - minimum) / span;
    }

    private static ObjMeshData.Vec3 crossSubtract(ObjMeshData.Vec3 origin,
                                                  ObjMeshData.Vec3 second,
                                                  ObjMeshData.Vec3 third) {
        float abX = second.x() - origin.x();
        float abY = second.y() - origin.y();
        float abZ = second.z() - origin.z();
        float acX = third.x() - origin.x();
        float acY = third.y() - origin.y();
        float acZ = third.z() - origin.z();
        return new ObjMeshData.Vec3(
                abY * acZ - abZ * acY,
                abZ * acX - abX * acZ,
                abX * acY - abY * acX
        );
    }

    private static ObjMeshData.Vec3 transformNormal(ObjMeshData.Vec3 normal, ObjMeshDefinition.Vec3 scale) {
        if (!normal.isFinite()) {
            return null;
        }
        return normalize(new ObjMeshData.Vec3(
                normal.x() / scale.x(),
                normal.y() / scale.y(),
                normal.z() / scale.z()
        ));
    }

    private static float normalDot(ObjMeshData.Vec3 first, ObjMeshData.Vec3 second) {
        return first.x() * second.x() + first.y() * second.y() + first.z() * second.z();
    }

    private static ObjMeshData.Vec3 firstUsableImportedNormal(ObjMeshDefinition definition,
                                                              ObjMeshData data,
                                                              List<ObjMeshData.VertexRef> refs) {
        for (ObjMeshData.VertexRef ref : refs) {
            if (ref.normalIndex() != ObjMeshData.VertexRef.MISSING) {
                ObjMeshData.Vec3 normal = transformNormal(data.normals().get(ref.normalIndex()), definition.scale());
                if (normal != null) {
                    return normal;
                }
            }
        }
        return null;
    }

    private static ObjMeshData.Vec3 normalize(ObjMeshData.Vec3 vector) {
        if (!vector.isFinite()) {
            return null;
        }
        float lengthSquared = vector.x() * vector.x() + vector.y() * vector.y() + vector.z() * vector.z();
        if (!Float.isFinite(lengthSquared) || lengthSquared <= NORMAL_EPSILON_SQUARED) {
            return null;
        }
        float inverseLength = (float) (1.0D / Math.sqrt(lengthSquared));
        return new ObjMeshData.Vec3(
                vector.x() * inverseLength,
                vector.y() * inverseLength,
                vector.z() * inverseLength
        );
    }

    private static Direction closestAxis(ObjMeshData.Vec3 normal) {
        float absX = Math.abs(normal.x());
        float absY = Math.abs(normal.y());
        float absZ = Math.abs(normal.z());
        if (absX >= absY && absX >= absZ) {
            return normal.x() >= 0.0F ? Direction.EAST : Direction.WEST;
        }
        if (absY >= absZ) {
            return normal.y() >= 0.0F ? Direction.UP : Direction.DOWN;
        }
        return normal.z() >= 0.0F ? Direction.SOUTH : Direction.NORTH;
    }

    @Override
    public boolean isVanillaAdapter() {
        return false;
    }

    @Override
    public void emitBlockQuads(BlockRenderView blockView,
                               BlockState state,
                               BlockPos pos,
                               Supplier<Random> randomSupplier,
                               RenderContext context) {
        RenderContext.QuadTransform positionTransform = null;
        if (state.getBlock() instanceof StatueBlock) {
            positionTransform = StatueTransform.forState(state);
        } else if (state.getBlock() instanceof UrnBlock) {
            positionTransform = UrnTransform.forState(state);
        } else if (state.getBlock() instanceof CorbelBlock) {
            positionTransform = CorbelTransform.forState(state);
        } else if (state.getBlock() instanceof ExedraBlock) {
            positionTransform = GroundScaleTransform.forExedraState(state);
        } else if (state.getBlock() instanceof FacingDecorBlock) {
            positionTransform = FacingDecorTransform.forState(state);
        } else if (state.getBlock() instanceof SizedDecorBlock) {
            positionTransform = SizedDecorTransform.forState(state);
        } else if (state.getBlock() instanceof FountainBasinBlock) {
            positionTransform = BasinTransform.forState(state);
        } else if (state.getBlock() instanceof PlinthBlock) {
            positionTransform = PlinthTransform.forState(state, 0.0F);
        } else if (state.getBlock() instanceof TwoSizeDecorBlock) {
            positionTransform = GroundScaleTransform.forState(state);
        }

        int worldPhaseIndex = worldTexturePhase.index(pos.getX(), pos.getY(), pos.getZ());
        context.pushTransform(worldTextureTransforms[worldPhaseIndex]);
        if (positionTransform != null) {
            context.pushTransform(positionTransform);
        }
        try {
            mesh.outputTo(context.getEmitter());
        } finally {
            if (positionTransform != null) {
                context.popTransform();
            }
            context.popTransform();
        }
        emitContainedWater(blockView, state, pos, context, 0.0F, false);
        if (state.getBlock() instanceof FountainBasinBlock) {
            emitBasinComponents(blockView, state, pos, context);
        }
    }

    private void emitContainedWater(BlockRenderView blockView,
                                    BlockState state,
                                    BlockPos pos,
                                    RenderContext context,
                                    float baseY,
                                    boolean forceFilled) {
        if (containedWaterSprite == null
                || containedWaterMaterial == null) {
            return;
        }

        if (!(state.getBlock() instanceof FountainBasinBlock basinBlock)
                || (!forceFilled && !state.get(FountainBasinBlock.WATERLOGGED))) {
            return;
        }
        FountainBasinBlock.Size size = state.get(FountainBasinBlock.SIZE);
        FountainBasinBlock.Style style = basinBlock.style();
        FountainWaterShape outline = style.waterOutline();
        if (outline != null) {
            emitWaterProfile(blockView, pos, context, outline, size.modelScale(),
                    baseY + style.waterSurfaceY(size));
            return;
        }
        emitWaterSurface(
                blockView,
                pos,
                context,
                style.waterHalfWidth(size),
                style.waterBevel(size),
                baseY + style.waterSurfaceY(size)
        );
    }

    private void emitWaterProfile(BlockRenderView blockView,
                                   BlockPos pos,
                                   RenderContext context,
                                   FountainWaterShape outline,
                                   float scale,
                                   float waterSurfaceY) {
        int waterColor = 0xFF000000 | BiomeColors.getWaterColor(blockView, pos);
        QuadEmitter emitter = context.getEmitter();
        for (int i = 0; i < outline.vertexCount(); i++) {
            int next = (i + 1) % outline.vertexCount();
            emitWaterTriangle(emitter, waterColor, 0.5F,
                    0.5F + outline.x(i) * scale, 0.5F + outline.z(i) * scale,
                    0.5F + outline.x(next) * scale, 0.5F + outline.z(next) * scale,
                    outline.halfWidth() * scale, waterSurfaceY);
        }
    }

    private void emitContainedBowlWater(BlockRenderView blockView,
                                        BlockPos pos,
                                        RenderContext context,
                                        FountainBowlModel.Style style,
                                        FountainBowlModel.Size size,
                                        float baseY,
                                        boolean filled) {
        if (!filled || containedWaterSprite == null || containedWaterMaterial == null) {
            return;
        }
        emitWaterProfile(
                blockView,
                pos,
                context,
                style.waterOutline(),
                size.diameter(),
                baseY + style.waterSurfaceY(size)
        );
    }

    private void emitWaterSurface(BlockRenderView blockView,
                                  BlockPos pos,
                                  RenderContext context,
                                  float halfWidth,
                                  float bevel,
                                  float waterSurfaceY) {
        float center = 0.5F;
        int waterColor = 0xFF000000 | BiomeColors.getWaterColor(blockView, pos);
        QuadEmitter emitter = context.getEmitter();
        for (int index = 0; index < 8; index++) {
            int next = (index + 1) & 7;
            emitWaterTriangle(
                    emitter,
                    waterColor,
                    center,
                    waterOutlineX(index, center, halfWidth, bevel),
                    waterOutlineZ(index, center, halfWidth, bevel),
                    waterOutlineX(next, center, halfWidth, bevel),
                    waterOutlineZ(next, center, halfWidth, bevel),
                    halfWidth,
                    waterSurfaceY
            );
        }
    }

    private static float waterOutlineX(int index, float center, float halfWidth, float bevel) {
        return switch (index) {
            case 0, 5 -> center - bevel;
            case 1, 4 -> center + bevel;
            case 2, 3 -> center + halfWidth;
            case 6, 7 -> center - halfWidth;
            default -> throw new IllegalArgumentException("Water outline index must be between 0 and 7");
        };
    }

    private static float waterOutlineZ(int index, float center, float halfWidth, float bevel) {
        return switch (index) {
            case 0, 1 -> center - halfWidth;
            case 2, 7 -> center - bevel;
            case 3, 6 -> center + bevel;
            case 4, 5 -> center + halfWidth;
            default -> throw new IllegalArgumentException("Water outline index must be between 0 and 7");
        };
    }

    private void emitBasinComponents(BlockRenderView blockView,
                                     BlockState basinState,
                                     BlockPos pos,
                                     RenderContext context) {
        if (!(blockView.getBlockEntity(pos) instanceof FountainBasinBlockEntity basin)) {
            return;
        }
        FountainAssemblyLayout layout = basin.layout(basinState);
        FountainAssemblyLayout.PlacedPlinth plinth = layout.plinth();
        if (plinth == null || !(plinth.state().getBlock() instanceof PlinthBlock plinthBlock)) {
            return;
        }
        boolean filled = basinState.get(FountainBasinBlock.WATERLOGGED);
        BakedModel plinthModel = MinecraftClient.getInstance()
                .getBlockRenderManager()
                .getModel(plinth.state());
        if (plinthModel instanceof ObjMeshBakedModel objModel) {
            objModel.emitAttachedPlinthQuads(
                    plinth.state(),
                    pos,
                    context,
                    (float) plinth.baseY()
            );
        }

        for (FountainAssemblyLayout.PlacedBowl bowl : layout.bowls()) {
            BakedModel bowlModel = MinecraftClient.getInstance()
                    .getBakedModelManager()
                    .getModel(ObjMeshModelLoadingPlugin.fountainBowlModelId(
                            plinthBlock,
                            bowl.style()
                    ));
            if (bowlModel instanceof ObjMeshBakedModel objModel) {
                objModel.emitAttachedBowlQuads(
                        blockView,
                        pos,
                        context,
                        bowl.style(),
                        bowl.size(),
                        (float) bowl.baseY(),
                        filled
                );
            }
        }
    }

    private void emitAttachedPlinthQuads(BlockState state,
                                         BlockPos pos,
                                         RenderContext context,
                                         float baseY) {
        RenderContext.QuadTransform positionTransform = PlinthTransform.forState(state, baseY);
        int worldPhaseIndex = worldTexturePhase.index(pos.getX(), pos.getY(), pos.getZ());
        context.pushTransform(worldTextureTransforms[worldPhaseIndex]);
        context.pushTransform(positionTransform);
        try {
            mesh.outputTo(context.getEmitter());
        } finally {
            context.popTransform();
            context.popTransform();
        }
    }

    private void emitAttachedBowlQuads(BlockRenderView blockView,
                                       BlockPos pos,
                                       RenderContext context,
                                       FountainBowlModel.Style style,
                                       FountainBowlModel.Size size,
                                       float baseY,
                                       boolean filled) {
        RenderContext.QuadTransform positionTransform = BowlTransform.forSize(size, baseY);
        int worldPhaseIndex = worldTexturePhase.index(pos.getX(), pos.getY(), pos.getZ());
        context.pushTransform(worldTextureTransforms[worldPhaseIndex]);
        context.pushTransform(positionTransform);
        try {
            mesh.outputTo(context.getEmitter());
        } finally {
            context.popTransform();
            context.popTransform();
        }
        emitContainedBowlWater(blockView, pos, context, style, size, baseY, filled);
    }

    private void emitWaterTriangle(QuadEmitter emitter,
                                   int waterColor,
                                   float center,
                                   float currentX,
                                   float currentZ,
                                   float nextX,
                                   float nextZ,
                                   float halfWidth,
                                   float waterSurfaceY) {
        // Reverse the clockwise X/Z outline order so the triangles face upward.
        emitWaterVertex(emitter, 0, center, center, center, halfWidth, waterSurfaceY);
        emitWaterVertex(emitter, 1, nextX, nextZ, center, halfWidth, waterSurfaceY);
        emitWaterVertex(emitter, 2, currentX, currentZ, center, halfWidth, waterSurfaceY);
        emitWaterVertex(emitter, 3, currentX, currentZ, center, halfWidth, waterSurfaceY);
        emitter.color(waterColor, waterColor, waterColor, waterColor);
        emitter.material(containedWaterMaterial);
        emitter.cullFace(null);
        emitter.nominalFace(Direction.UP);
        emitter.tag(ShaderTerrainNormalBridge.containedWaterTag());
        emitter.spriteBake(containedWaterSprite, MutableQuadView.BAKE_NORMALIZED);
        emitter.emit();
    }

    private static void emitWaterVertex(QuadEmitter emitter,
                                        int vertex,
                                        float x,
                                        float z,
                                        float center,
                                        float halfWidth,
                                        float waterSurfaceY) {
        emitter.pos(vertex, x, waterSurfaceY, z);
        emitter.uv(
                vertex,
                0.5F + (x - center) / (2.0F * halfWidth),
                0.5F + (z - center) / (2.0F * halfWidth)
        );
        emitter.normal(vertex, 0.0F, 1.0F, 0.0F);
    }

    @Override
    public void emitItemQuads(ItemStack stack, Supplier<Random> randomSupplier, RenderContext context) {
        context.pushTransform(textureTransform);
        try {
            if (itemMesh != null && context.itemTransformationMode() != ModelTransformationMode.GUI) {
                itemMesh.outputTo(context.getEmitter());
            } else {
                mesh.outputTo(context.getEmitter());
            }
        } finally {
            context.popTransform();
        }
    }

    @Override
    public List<BakedQuad> getQuads(BlockState state, Direction face, Random random) {
        return Collections.emptyList();
    }

    @Override
    public boolean useAmbientOcclusion() {
        return metadataModel.useAmbientOcclusion();
    }

    @Override
    public boolean hasDepth() {
        return metadataModel.hasDepth();
    }

    @Override
    public boolean isSideLit() {
        return metadataModel.isSideLit();
    }

    @Override
    public boolean isBuiltin() {
        return false;
    }

    @Override
    public Sprite getParticleSprite() {
        return particleSprite;
    }

    @Override
    public ModelTransformation getTransformation() {
        return metadataModel.getTransformation();
    }

    @Override
    public ModelOverrideList getOverrides() {
        return metadataModel.getOverrides();
    }

    ObjGuiIconTemplate guiIconTemplate() {
        return guiIconTemplate;
    }

    List<Identifier> materialTextureIds() {
        return materialTextureIds;
    }

    Identifier modelId() {
        return modelId;
    }

    float guiTextureU(int materialIndex, float u) {
        return materialUsesWorldPhase(materialIndex)
                ? worldTexturePhase.mapU(u, 0)
                : u;
    }

    float guiTextureV(int materialIndex, float v) {
        return materialUsesWorldPhase(materialIndex)
                ? worldTexturePhase.mapV(v)
                : v;
    }

    private boolean materialUsesWorldPhase(int materialIndex) {
        return materialIndex >= 0
                && materialIndex < worldPhaseMaterials.size()
                && worldPhaseMaterials.get(materialIndex);
    }

    record GeometryBakeResult(Mesh mesh,
                              ObjMeshData.Bounds transformedBounds,
                              List<String> materialNames,
                              int emittedQuadCount,
                              int repairedNormalCount,
                              int smoothedNormalCornerCount,
                              int repairedUvFaceCount,
                              int degenerateFaceCount,
                              List<Float> materialMaximumUs,
                              int textureUTiles,
                              float occupiedUTiles,
                              long estimatedVertexBytes,
                              long bakeNanos) {
        GeometryBakeResult {
            materialNames = List.copyOf(materialNames);
            materialMaximumUs = List.copyOf(materialMaximumUs);
            if (materialMaximumUs.size() != materialNames.size()) {
                throw new IllegalArgumentException("Material maximum-U values must match baked OBJ material tags");
            }
        }

    }

    private static final class BakeCounters {
        private final float[] materialMaximumUs;
        private int repairedNormalCount;
        private int smoothedNormalCornerCount;
        private int repairedUvFaceCount;
        private int degenerateFaceCount;

        private BakeCounters(int materialCount) {
            materialMaximumUs = new float[materialCount];
        }

        private void recordMaximumU(int materialTag, float[] faceUs) {
            for (float faceU : faceUs) {
                materialMaximumUs[materialTag] = Math.max(materialMaximumUs[materialTag], faceU);
            }
        }

        private List<Float> materialMaximumUs() {
            List<Float> result = new ArrayList<>(materialMaximumUs.length);
            for (float maximumU : materialMaximumUs) {
                result.add(maximumU);
            }
            return List.copyOf(result);
        }
    }

    private static TextureTransform[] createWorldTextureTransforms(
            List<Sprite> materialSprites,
            List<Boolean> worldPhaseMaterials,
            WorldTexturePhase.Layout worldTexturePhase
    ) {
        TextureTransform[] transforms = new TextureTransform[worldTexturePhase.phaseCount()];
        for (int phaseIndex = 0; phaseIndex < transforms.length; phaseIndex++) {
            transforms[phaseIndex] = new TextureTransform(
                    materialSprites,
                    worldPhaseMaterials,
                    worldTexturePhase,
                    phaseIndex
            );
        }
        return transforms;
    }

    private record TextureTransform(
            List<Sprite> materialSprites,
            List<Boolean> worldPhaseMaterials,
            WorldTexturePhase.Layout worldTexturePhase,
            int phaseIndex
    )
            implements RenderContext.QuadTransform {
        private TextureTransform {
            materialSprites = List.copyOf(materialSprites);
            worldPhaseMaterials = List.copyOf(worldPhaseMaterials);
            if (materialSprites.isEmpty()) {
                throw new IllegalArgumentException("OBJ material sprite list must not be empty");
            }
            if (materialSprites.size() != worldPhaseMaterials.size()) {
                throw new IllegalArgumentException("OBJ world-phase material flags must match the sprite list");
            }
            if (phaseIndex < 0 || phaseIndex >= worldTexturePhase.phaseCount()) {
                throw new IllegalArgumentException("OBJ texture phase index is outside the repeat layout");
            }
        }

        @Override
        public boolean transform(MutableQuadView quad) {
            int materialTag = ShaderTerrainNormalBridge.decodeObjMaterialTag(quad.tag());
            Sprite sprite = materialTag >= 0 && materialTag < materialSprites.size()
                    ? materialSprites.get(materialTag)
                    : materialSprites.get(0);
            boolean materialUsesWorldPhase = materialTag >= 0
                    && materialTag < worldPhaseMaterials.size()
                    && worldPhaseMaterials.get(materialTag);
            if (materialUsesWorldPhase) {
                for (int vertex = 0; vertex < 4; vertex++) {
                    quad.uv(
                            vertex,
                            worldTexturePhase.mapU(quad.u(vertex), phaseIndex),
                            worldTexturePhase.mapV(quad.v(vertex))
                    );
                }
            }
            quad.spriteBake(sprite, MutableQuadView.BAKE_NORMALIZED);
            return true;
        }
    }

    private static final class StatueTransform implements RenderContext.QuadTransform {
        private static final Map<StatueBlock.Profile, StatueTransform[][][]> CACHE =
                new ConcurrentHashMap<>();

        private final float scale;
        private final int clockwiseSteps;
        private final float supportCenterX;
        private final float groundContactY;
        private final float supportCenterZ;
        private final float offsetX;
        private final float baseY;
        private final float offsetZ;

        private StatueTransform(
                float scale,
                int clockwiseSteps,
                float supportCenterX,
                float groundContactY,
                float supportCenterZ,
                float offsetX,
                float baseY,
                float offsetZ
        ) {
            this.scale = scale;
            this.clockwiseSteps = clockwiseSteps;
            this.supportCenterX = supportCenterX;
            this.groundContactY = groundContactY;
            this.supportCenterZ = supportCenterZ;
            this.offsetX = offsetX;
            this.baseY = baseY;
            this.offsetZ = offsetZ;
        }

        private static StatueTransform forState(BlockState state) {
            StatueBlock block = (StatueBlock) state.getBlock();
            StatueBlock.StatueSize size = block.effectiveSize(state);
            Direction facing = state.get(StatueBlock.FACING);
            StatueBlock.Profile profile = block.statueProfile();
            StatueTransform[][][] transforms = CACHE.computeIfAbsent(
                    profile,
                    StatueTransform::createCache
            );
            return transforms[size.ordinal()][state.get(StatueBlock.OFFSET) ? 1 : 0]
                    [horizontalIndex(facing)];
        }

        private static StatueTransform[][][] createCache(StatueBlock.Profile profile) {
            StatueBlock.StatueSize[] sizes = StatueBlock.StatueSize.values();
            StatueTransform[][][] cache = new StatueTransform[sizes.length][2][4];
            for (StatueBlock.StatueSize size : sizes) {
                for (int offsetIndex = 0; offsetIndex < 2; offsetIndex++) {
                    boolean offset = offsetIndex == 1;
                    for (Direction facing : new Direction[]{Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST}) {
                        cache[size.ordinal()][offsetIndex][horizontalIndex(facing)] = new StatueTransform(
                                size.scale(),
                                profile.rotationStepsFromSource(facing),
                                profile.modelSupportCenterX(),
                                profile.modelGroundContactY(),
                                profile.modelSupportCenterZ(),
                                offset
                                        ? facing.getOffsetX() * DecorShapeTransforms.OFFSET_DISTANCE
                                        : 0.0F,
                                offset ? DecorShapeTransforms.OFFSET_BASE_Y : 0.0F,
                                offset
                                        ? facing.getOffsetZ() * DecorShapeTransforms.OFFSET_DISTANCE
                                        : 0.0F
                        );
                    }
                }
            }
            return cache;
        }

        @Override
        public boolean transform(MutableQuadView quad) {
            for (int vertex = 0; vertex < 4; vertex++) {
                float x = (quad.x(vertex) - supportCenterX) * scale;
                float y = (quad.y(vertex) - groundContactY) * scale;
                float z = (quad.z(vertex) - supportCenterZ) * scale;
                float rotatedX = rotateX(x, z);
                float rotatedZ = rotateZ(x, z);
                quad.pos(
                        vertex,
                        rotatedX + 0.5F + offsetX,
                        y + baseY,
                        rotatedZ + 0.5F + offsetZ
                );

                // The source mesh represents the full three-block statue. Shrink its UV span with the
                // geometry so every size keeps the same world-space texture density. Render transforms run
                // before TextureTransform sprite-bakes these still-normalised coordinates into the atlas.
                quad.uv(
                        vertex,
                        scaleUvFromCenter(quad.u(vertex), scale),
                        scaleUvFromCenter(quad.v(vertex), scale)
                );

                if (quad.hasNormal(vertex)) {
                    float normalX = quad.normalX(vertex);
                    float normalZ = quad.normalZ(vertex);
                    quad.normal(vertex, rotateX(normalX, normalZ), quad.normalY(vertex), rotateZ(normalX, normalZ));
                }
            }

            quad.cullFace(null);
            Direction nominalFace = quad.nominalFace();
            quad.nominalFace(nominalFace == null ? null : rotateFace(nominalFace));
            return true;
        }

        private static float scaleUvFromCenter(float uv, float scale) {
            return 0.5F + (uv - 0.5F) * scale;
        }

        private float rotateX(float x, float z) {
            return switch (clockwiseSteps) {
                case 0 -> x;
                case 1 -> -z;
                case 2 -> -x;
                case 3 -> z;
                default -> throw new IllegalStateException("Invalid quarter-turn count " + clockwiseSteps);
            };
        }

        private float rotateZ(float x, float z) {
            return switch (clockwiseSteps) {
                case 0 -> z;
                case 1 -> x;
                case 2 -> -z;
                case 3 -> -x;
                default -> throw new IllegalStateException("Invalid quarter-turn count " + clockwiseSteps);
            };
        }

        private Direction rotateFace(Direction face) {
            if (face == Direction.UP || face == Direction.DOWN) {
                return face;
            }
            Direction rotated = face;
            for (int step = 0; step < clockwiseSteps; step++) {
                rotated = rotated.rotateYClockwise();
            }
            return rotated;
        }

        private static int horizontalIndex(Direction direction) {
            return switch (direction) {
                case NORTH -> 0;
                case EAST -> 1;
                case SOUTH -> 2;
                case WEST -> 3;
                default -> throw new IllegalArgumentException("Statue facing must be horizontal: " + direction);
            };
        }
    }

    private static final class UrnTransform implements RenderContext.QuadTransform {
        // The offset translation matches the authored oil-burner offset model. Size is a
        // separate state: SMALL preserves the previous 61.56% offset urn, while MEDIUM is the
        // original one-block urn and the equally spaced LARGE size is 138.44%.
        private static final float OFFSET_DISTANCE = 0.8003375F;
        private static final float OFFSET_BASE_Y = 0.0F;
        private static final UrnTransform[][][] CACHE = createCache();

        private final float scale;
        private final float offsetDistance;
        private final float baseY;
        private final int clockwiseSteps;

        private UrnTransform(float scale, float offsetDistance, float baseY, int clockwiseSteps) {
            this.scale = scale;
            this.offsetDistance = offsetDistance;
            this.baseY = baseY;
            this.clockwiseSteps = clockwiseSteps;
        }

        private static UrnTransform forState(BlockState state) {
            return CACHE[state.get(UrnBlock.SIZE).ordinal()]
                    [state.get(UrnBlock.OFFSET) ? 1 : 0]
                    [horizontalIndex(state.get(UrnBlock.FACING))];
        }

        private static UrnTransform[][][] createCache() {
            UrnBlock.UrnSize[] sizes = UrnBlock.UrnSize.values();
            UrnTransform[][][] cache = new UrnTransform[sizes.length][2][4];
            for (UrnBlock.UrnSize size : sizes) {
                for (int offsetIndex = 0; offsetIndex < 2; offsetIndex++) {
                    boolean offset = offsetIndex == 1;
                    for (Direction facing : new Direction[]{Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST}) {
                        cache[size.ordinal()][offsetIndex][horizontalIndex(facing)] = new UrnTransform(
                                size.scale(),
                                offset ? OFFSET_DISTANCE : 0.0F,
                                offset ? OFFSET_BASE_Y : 0.0F,
                                horizontalIndex(facing)
                        );
                    }
                }
            }
            return cache;
        }

        @Override
        public boolean transform(MutableQuadView quad) {
            for (int vertex = 0; vertex < 4; vertex++) {
                float x = (quad.x(vertex) - 0.5F) * scale;
                float z = (quad.z(vertex) - 0.5F) * scale - offsetDistance;
                quad.pos(
                        vertex,
                        rotateX(x, z) + 0.5F,
                        quad.y(vertex) * scale + baseY,
                        rotateZ(x, z) + 0.5F
                );

                // Urn geometry changes size at runtime, so its UV span must change by the
                // same factor. The urn's four-by-one source window has enough spare room in
                // the twelve-by-six repeat sheet for LARGE without sampling outside it.
                quad.uv(
                        vertex,
                        quad.u(vertex) * scale,
                        quad.v(vertex) * scale
                );

                if (quad.hasNormal(vertex)) {
                    float normalX = quad.normalX(vertex);
                    float normalZ = quad.normalZ(vertex);
                    quad.normal(vertex, rotateX(normalX, normalZ), quad.normalY(vertex), rotateZ(normalX, normalZ));
                }
            }

            quad.cullFace(null);
            Direction nominalFace = quad.nominalFace();
            quad.nominalFace(nominalFace == null ? null : rotateFace(nominalFace));
            return true;
        }

        private float rotateX(float x, float z) {
            return switch (clockwiseSteps) {
                case 0 -> x;
                case 1 -> -z;
                case 2 -> -x;
                case 3 -> z;
                default -> throw new IllegalStateException("Invalid quarter-turn count " + clockwiseSteps);
            };
        }

        private float rotateZ(float x, float z) {
            return switch (clockwiseSteps) {
                case 0 -> z;
                case 1 -> x;
                case 2 -> -z;
                case 3 -> -x;
                default -> throw new IllegalStateException("Invalid quarter-turn count " + clockwiseSteps);
            };
        }

        private Direction rotateFace(Direction face) {
            if (face == Direction.UP || face == Direction.DOWN) {
                return face;
            }
            Direction rotated = face;
            for (int step = 0; step < clockwiseSteps; step++) {
                rotated = rotated.rotateYClockwise();
            }
            return rotated;
        }

        private static int horizontalIndex(Direction direction) {
            return switch (direction) {
                case NORTH -> 0;
                case EAST -> 1;
                case SOUTH -> 2;
                case WEST -> 3;
                default -> throw new IllegalArgumentException("Urn facing must be horizontal: " + direction);
            };
        }
    }

    /** Keeps the corbel top on Y=16 while style-specific uniform scales grow downwards. */
    private static final class CorbelTransform implements RenderContext.QuadTransform {
        private static final CorbelTransform[][][] CACHE = createCache();

        private final float scale;
        private final float wallAnchorShiftZ;
        private final int clockwiseSteps;

        private CorbelTransform(float scale, float wallAnchorShiftZ, int clockwiseSteps) {
            this.scale = scale;
            this.wallAnchorShiftZ = wallAnchorShiftZ;
            this.clockwiseSteps = clockwiseSteps;
        }

        private static CorbelTransform forState(BlockState state) {
            CorbelBlock block = (CorbelBlock) state.getBlock();
            return CACHE[block.style().ordinal()][state.get(CorbelBlock.SIZE).ordinal()]
                    [horizontalIndex(state.get(CorbelBlock.FACING))];
        }

        private static CorbelTransform[][][] createCache() {
            CorbelBlock.Style[] styles = CorbelBlock.Style.values();
            CorbelBlock.CorbelSize[] sizes = CorbelBlock.CorbelSize.values();
            CorbelTransform[][][] cache = new CorbelTransform[styles.length][sizes.length][4];
            for (CorbelBlock.Style style : styles) {
                for (CorbelBlock.CorbelSize size : sizes) {
                    for (Direction facing : new Direction[]{Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST}) {
                        cache[style.ordinal()][size.ordinal()][horizontalIndex(facing)] = new CorbelTransform(
                                style.modelScale(size),
                                style.wallAnchorShift(size),
                                CorbelBlock.rotationStepsFromSouth(facing)
                        );
                    }
                }
            }
            return cache;
        }

        @Override
        public boolean transform(MutableQuadView quad) {
            for (int vertex = 0; vertex < 4; vertex++) {
                float x = (quad.x(vertex) - 0.5F) * scale;
                float y = CorbelBlock.TOP_ANCHOR_Y
                        + (quad.y(vertex) - CorbelBlock.SOURCE_TOP_Y) * scale;
                float z = (quad.z(vertex) - 0.5F) * scale + wallAnchorShiftZ;
                quad.pos(
                        vertex,
                        rotateX(x, z) + 0.5F,
                        y,
                        rotateZ(x, z) + 0.5F
                );
                quad.uv(
                        vertex,
                        scaleUvFromCenter(quad.u(vertex), scale),
                        scaleUvFromCenter(quad.v(vertex), scale)
                );
                if (quad.hasNormal(vertex)) {
                    float normalX = quad.normalX(vertex);
                    float normalZ = quad.normalZ(vertex);
                    quad.normal(
                            vertex,
                            rotateX(normalX, normalZ),
                            quad.normalY(vertex),
                            rotateZ(normalX, normalZ)
                    );
                }
            }
            quad.cullFace(null);
            Direction nominalFace = quad.nominalFace();
            quad.nominalFace(nominalFace == null ? null : rotateFace(nominalFace));
            return true;
        }

        private static float scaleUvFromCenter(float uv, float scale) {
            return 0.5F + (uv - 0.5F) * scale;
        }

        private float rotateX(float x, float z) {
            return switch (clockwiseSteps) {
                case 0 -> x;
                case 1 -> -z;
                case 2 -> -x;
                case 3 -> z;
                default -> throw new IllegalStateException("Invalid quarter-turn count " + clockwiseSteps);
            };
        }

        private float rotateZ(float x, float z) {
            return switch (clockwiseSteps) {
                case 0 -> z;
                case 1 -> x;
                case 2 -> -z;
                case 3 -> -x;
                default -> throw new IllegalStateException("Invalid quarter-turn count " + clockwiseSteps);
            };
        }

        private Direction rotateFace(Direction face) {
            if (face == Direction.UP || face == Direction.DOWN) {
                return face;
            }
            Direction rotated = face;
            for (int step = 0; step < clockwiseSteps; step++) {
                rotated = rotated.rotateYClockwise();
            }
            return rotated;
        }

        private static int horizontalIndex(Direction direction) {
            return switch (direction) {
                case NORTH -> 0;
                case EAST -> 1;
                case SOUTH -> 2;
                case WEST -> 3;
                default -> throw new IllegalArgumentException("Corbel facing must be horizontal: " + direction);
            };
        }
    }

    private record BasinTransform(float scale) implements RenderContext.QuadTransform {
        private static final BasinTransform[] CACHE = Arrays.stream(FountainBasinBlock.Size.values())
                .map(size -> new BasinTransform(size.renderScale()))
                .toArray(BasinTransform[]::new);

        private static BasinTransform forState(BlockState state) {
            return CACHE[state.get(FountainBasinBlock.SIZE).ordinal()];
        }

        @Override
        public boolean transform(MutableQuadView quad) {
            for (int vertex = 0; vertex < 4; vertex++) {
                quad.pos(
                        vertex,
                        0.5F + (quad.x(vertex) - 0.5F) * scale,
                        quad.y(vertex) * scale,
                        0.5F + (quad.z(vertex) - 0.5F) * scale
                );
                quad.uv(
                        vertex,
                        0.5F + (quad.u(vertex) - 0.5F) * scale,
                        0.5F + (quad.v(vertex) - 0.5F) * scale
                );
            }
            quad.cullFace(null);
            return true;
        }
    }

    private static final class FacingDecorTransform implements RenderContext.QuadTransform {
        private static final FacingDecorTransform[] CACHE = createCache();
        private final int clockwiseSteps;

        private FacingDecorTransform(int clockwiseSteps) {
            this.clockwiseSteps = clockwiseSteps;
        }

        private static FacingDecorTransform forState(BlockState state) {
            return CACHE[horizontalIndex(state.get(FacingDecorBlock.FACING))];
        }

        private static FacingDecorTransform[] createCache() {
            FacingDecorTransform[] result = new FacingDecorTransform[4];
            for (Direction facing : new Direction[]{Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST}) {
                result[horizontalIndex(facing)] =
                        new FacingDecorTransform(FacingDecorBlock.rotationStepsFromSouth(facing));
            }
            return result;
        }

        @Override
        public boolean transform(MutableQuadView quad) {
            for (int vertex = 0; vertex < 4; vertex++) {
                float x = (quad.x(vertex) - 0.5F) * FacingDecorBlock.MODEL_SCALE;
                float z = (quad.z(vertex) - 0.5F) * FacingDecorBlock.MODEL_SCALE;
                quad.pos(vertex,
                        rotateX(x, z) + 0.5F,
                        quad.y(vertex) * FacingDecorBlock.MODEL_SCALE,
                        rotateZ(x, z) + 0.5F);
                if (quad.hasNormal(vertex)) {
                    float nx = quad.normalX(vertex);
                    float nz = quad.normalZ(vertex);
                    quad.normal(vertex, rotateX(nx, nz), quad.normalY(vertex), rotateZ(nx, nz));
                }
            }
            quad.cullFace(null);
            Direction face = quad.nominalFace();
            if (face != null && face != Direction.UP && face != Direction.DOWN) {
                for (int step = 0; step < clockwiseSteps; step++) face = face.rotateYClockwise();
                quad.nominalFace(face);
            }
            return true;
        }

        private float rotateX(float x, float z) { return switch (clockwiseSteps) {
            case 0 -> x; case 1 -> -z; case 2 -> -x; case 3 -> z;
            default -> throw new IllegalStateException("Invalid quarter-turn count " + clockwiseSteps);
        }; }
        private float rotateZ(float x, float z) { return switch (clockwiseSteps) {
            case 0 -> z; case 1 -> x; case 2 -> -z; case 3 -> -x;
            default -> throw new IllegalStateException("Invalid quarter-turn count " + clockwiseSteps);
        }; }
        private static int horizontalIndex(Direction direction) { return switch (direction) {
            case NORTH -> 0; case EAST -> 1; case SOUTH -> 2; case WEST -> 3;
            default -> throw new IllegalArgumentException("Decor facing must be horizontal: " + direction);
        }; }
    }

    private record SizedDecorTransform(float scale) implements RenderContext.QuadTransform {
        private static final SizedDecorTransform[] CACHE = Arrays.stream(SizedDecorBlock.Size.values())
                .map(size -> new SizedDecorTransform(size.scale()))
                .toArray(SizedDecorTransform[]::new);

        private static SizedDecorTransform forState(BlockState state) {
            return CACHE[state.get(SizedDecorBlock.SIZE).ordinal()];
        }

        @Override
        public boolean transform(MutableQuadView quad) {
            for (int vertex = 0; vertex < 4; vertex++) {
                quad.pos(vertex,
                        0.5F + (quad.x(vertex) - 0.5F) * scale,
                        quad.y(vertex) * scale,
                        0.5F + (quad.z(vertex) - 0.5F) * scale);
                quad.uv(vertex,
                        0.5F + (quad.u(vertex) - 0.5F) * scale,
                        0.5F + (quad.v(vertex) - 0.5F) * scale);
            }
            quad.cullFace(null);
            return true;
        }
    }

    private record BowlTransform(float scale, float baseY) implements RenderContext.QuadTransform {
        private static BowlTransform forSize(FountainBowlModel.Size size, float baseY) {
            return new BowlTransform(size.renderScale(), baseY);
        }

        @Override
        public boolean transform(MutableQuadView quad) {
            for (int vertex = 0; vertex < 4; vertex++) {
                quad.pos(
                        vertex,
                        0.5F + (quad.x(vertex) - 0.5F) * scale,
                        baseY + quad.y(vertex) * scale,
                        0.5F + (quad.z(vertex) - 0.5F) * scale
                );
                quad.uv(
                        vertex,
                        0.5F + (quad.u(vertex) - 0.5F) * scale,
                        0.5F + (quad.v(vertex) - 0.5F) * scale
                );
            }
            quad.cullFace(null);
            return true;
        }
    }

    private record PlinthTransform(
            float scale,
            float offsetDistance,
            float baseY,
            int clockwiseSteps
    ) implements RenderContext.QuadTransform {
        private static PlinthTransform forState(BlockState state, float assemblyBaseY) {
            boolean offset = state.get(PlinthBlock.OFFSET);
            return new PlinthTransform(
                    state.get(PlinthBlock.SIZE).renderScale(),
                    offset ? DecorShapeTransforms.OFFSET_DISTANCE : 0.0F,
                    assemblyBaseY + (offset ? DecorShapeTransforms.OFFSET_BASE_Y : 0.0F),
                    horizontalIndex(state.get(PlinthBlock.FACING))
            );
        }

        @Override
        public boolean transform(MutableQuadView quad) {
            for (int vertex = 0; vertex < 4; vertex++) {
                float x = (quad.x(vertex) - 0.5F) * scale;
                float z = (quad.z(vertex) - 0.5F) * scale - offsetDistance;
                quad.pos(
                        vertex,
                        rotateX(x, z) + 0.5F,
                        quad.y(vertex) * scale + baseY,
                        rotateZ(x, z) + 0.5F
                );
                quad.uv(
                        vertex,
                        0.5F + (quad.u(vertex) - 0.5F) * scale,
                        0.5F + (quad.v(vertex) - 0.5F) * scale
                );
                if (quad.hasNormal(vertex)) {
                    float normalX = quad.normalX(vertex);
                    float normalZ = quad.normalZ(vertex);
                    quad.normal(
                            vertex,
                            rotateX(normalX, normalZ),
                            quad.normalY(vertex),
                            rotateZ(normalX, normalZ)
                    );
                }
            }
            quad.cullFace(null);
            Direction nominalFace = quad.nominalFace();
            quad.nominalFace(nominalFace == null ? null : rotateFace(nominalFace));
            return true;
        }

        private float rotateX(float x, float z) {
            return switch (clockwiseSteps) {
                case 0 -> x;
                case 1 -> -z;
                case 2 -> -x;
                case 3 -> z;
                default -> throw new IllegalStateException("Invalid quarter-turn count " + clockwiseSteps);
            };
        }

        private float rotateZ(float x, float z) {
            return switch (clockwiseSteps) {
                case 0 -> z;
                case 1 -> x;
                case 2 -> -z;
                case 3 -> -x;
                default -> throw new IllegalStateException("Invalid quarter-turn count " + clockwiseSteps);
            };
        }

        private Direction rotateFace(Direction face) {
            if (face == Direction.UP || face == Direction.DOWN) {
                return face;
            }
            Direction rotated = face;
            for (int step = 0; step < clockwiseSteps; step++) {
                rotated = rotated.rotateYClockwise();
            }
            return rotated;
        }

        private static int horizontalIndex(Direction direction) {
            return switch (direction) {
                case NORTH -> 0;
                case EAST -> 1;
                case SOUTH -> 2;
                case WEST -> 3;
                default -> throw new IllegalArgumentException("Plinth facing must be horizontal: " + direction);
            };
        }
    }

    private record GroundScaleTransform(
            float scale,
            float offsetDistance,
            float baseY,
            int clockwiseSteps
    ) implements RenderContext.QuadTransform {
        private static final GroundScaleTransform[] CENTRED_CACHE =
                Arrays.stream(TwoSizeDecorBlock.Size.values())
                        .map(size -> new GroundScaleTransform(size.scale(), 0.0F, 0.0F, 0))
                        .toArray(GroundScaleTransform[]::new);
        private static final GroundScaleTransform[][][] MOVABLE_CACHE = createMovableCache();
        private static final GroundScaleTransform[] EXEDRA_CACHE = createExedraCache();

        private static GroundScaleTransform forExedraState(BlockState state) {
            return EXEDRA_CACHE[horizontalIndex(state.get(ExedraBlock.FACING))];
        }

        private static GroundScaleTransform[] createExedraCache() {
            GroundScaleTransform[] cache = new GroundScaleTransform[4];
            for (Direction facing : new Direction[]{Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST}) {
                cache[horizontalIndex(facing)] = new GroundScaleTransform(
                        1.0F, 0.0F, 0.0F, ExedraBlock.rotationStepsFromSouth(facing));
            }
            return cache;
        }

        private static GroundScaleTransform forState(BlockState state) {
            TwoSizeDecorBlock block = (TwoSizeDecorBlock) state.getBlock();
            if (!block.supportsOffsetAndFacing()) {
                return CENTRED_CACHE[state.get(TwoSizeDecorBlock.SIZE).ordinal()];
            }
            return MOVABLE_CACHE[state.get(TwoSizeDecorBlock.SIZE).ordinal()]
                    [state.get(TwoSizeDecorBlock.OFFSET) ? 1 : 0]
                    [horizontalIndex(state.get(TwoSizeDecorBlock.FACING))];
        }

        private static GroundScaleTransform[][][] createMovableCache() {
            GroundScaleTransform[][][] cache =
                    new GroundScaleTransform[TwoSizeDecorBlock.Size.values().length][2][4];
            for (TwoSizeDecorBlock.Size size : TwoSizeDecorBlock.Size.values()) {
                for (int offsetIndex = 0; offsetIndex < 2; offsetIndex++) {
                    boolean offset = offsetIndex == 1;
                    for (Direction facing : new Direction[]{Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST}) {
                        cache[size.ordinal()][offsetIndex][horizontalIndex(facing)] = new GroundScaleTransform(
                                size.scale(),
                                offset ? DecorShapeTransforms.OFFSET_DISTANCE : 0.0F,
                                offset ? DecorShapeTransforms.OFFSET_BASE_Y : 0.0F,
                                horizontalIndex(facing)
                        );
                    }
                }
            }
            return cache;
        }

        @Override
        public boolean transform(MutableQuadView quad) {
            for (int vertex = 0; vertex < 4; vertex++) {
                float x = (quad.x(vertex) - 0.5F) * scale;
                float z = (quad.z(vertex) - 0.5F) * scale - offsetDistance;
                quad.pos(vertex,
                        rotateX(x, z) + 0.5F,
                        quad.y(vertex) * scale + baseY,
                        rotateZ(x, z) + 0.5F);
                quad.uv(vertex,
                        0.5F + (quad.u(vertex) - 0.5F) * scale,
                        0.5F + (quad.v(vertex) - 0.5F) * scale);
                if (quad.hasNormal(vertex)) {
                    float normalX = quad.normalX(vertex);
                    float normalZ = quad.normalZ(vertex);
                    quad.normal(vertex, rotateX(normalX, normalZ), quad.normalY(vertex), rotateZ(normalX, normalZ));
                }
            }
            quad.cullFace(null);
            Direction nominalFace = quad.nominalFace();
            quad.nominalFace(nominalFace == null ? null : rotateFace(nominalFace));
            return true;
        }

        private float rotateX(float x, float z) {
            return switch (clockwiseSteps) {
                case 0 -> x; case 1 -> -z; case 2 -> -x; case 3 -> z;
                default -> throw new IllegalStateException("Invalid quarter-turn count " + clockwiseSteps);
            };
        }

        private float rotateZ(float x, float z) {
            return switch (clockwiseSteps) {
                case 0 -> z; case 1 -> x; case 2 -> -z; case 3 -> -x;
                default -> throw new IllegalStateException("Invalid quarter-turn count " + clockwiseSteps);
            };
        }

        private Direction rotateFace(Direction face) {
            if (!face.getAxis().isHorizontal()) {
                return face;
            }
            Direction rotated = face;
            for (int step = 0; step < clockwiseSteps; step++) {
                rotated = rotated.rotateYClockwise();
            }
            return rotated;
        }

        private static int horizontalIndex(Direction direction) {
            return switch (direction) {
                case NORTH -> 0; case EAST -> 1; case SOUTH -> 2; case WEST -> 3;
                default -> throw new IllegalArgumentException("Plinth facing must be horizontal: " + direction);
            };
        }
    }

    private static final class SmoothNormals {
        private final List<List<NormalContribution>> incidentNormals;
        private final float minimumDot;

        private SmoothNormals(List<List<NormalContribution>> incidentNormals, float minimumDot) {
            this.incidentNormals = incidentNormals;
            this.minimumDot = minimumDot;
        }

        private static SmoothNormals create(List<ObjMeshData.Vec3> positions,
                                            List<ObjMeshData.Face> faces,
                                            float angleDegrees) {
            List<List<NormalContribution>> incidentNormals = new ArrayList<>(positions.size());
            for (int i = 0; i < positions.size(); i++) {
                incidentNormals.add(new ArrayList<>());
            }

            for (ObjMeshData.Face face : faces) {
                ObjMeshData.Vec3 faceNormal = geometricNormal(positions, face.vertices());
                if (faceNormal == null) {
                    continue;
                }
                List<ObjMeshData.VertexRef> refs = face.vertices();
                for (int i = 0; i < refs.size(); i++) {
                    int positionIndex = refs.get(i).positionIndex();
                    boolean duplicate = false;
                    for (int previous = 0; previous < i; previous++) {
                        if (refs.get(previous).positionIndex() == positionIndex) {
                            duplicate = true;
                            break;
                        }
                    }
                    if (!duplicate) {
                        incidentNormals.get(positionIndex).add(new NormalContribution(
                                faceNormal,
                                cornerAngle(positions, refs, i)
                        ));
                    }
                }
            }

            float minimumDot = (float) Math.cos(Math.toRadians(angleDegrees));
            return new SmoothNormals(incidentNormals, minimumDot);
        }

        private ObjMeshData.Vec3 forCorner(int positionIndex, ObjMeshData.Vec3 faceNormal) {
            float x = 0.0F;
            float y = 0.0F;
            float z = 0.0F;
            for (NormalContribution contribution : incidentNormals.get(positionIndex)) {
                ObjMeshData.Vec3 candidate = contribution.normal();
                float dot = faceNormal.x() * candidate.x()
                        + faceNormal.y() * candidate.y()
                        + faceNormal.z() * candidate.z();
                if (dot >= minimumDot) {
                    x += candidate.x() * contribution.weight();
                    y += candidate.y() * contribution.weight();
                    z += candidate.z() * contribution.weight();
                }
            }
            return normalize(new ObjMeshData.Vec3(x, y, z));
        }

        private static float cornerAngle(List<ObjMeshData.Vec3> positions,
                                         List<ObjMeshData.VertexRef> refs,
                                         int cornerIndex) {
            int vertexCount = refs.size();
            ObjMeshData.Vec3 origin = positions.get(refs.get(cornerIndex).positionIndex());
            ObjMeshData.Vec3 previous = positions.get(refs.get((cornerIndex + vertexCount - 1) % vertexCount).positionIndex());
            ObjMeshData.Vec3 next = positions.get(refs.get((cornerIndex + 1) % vertexCount).positionIndex());
            ObjMeshData.Vec3 toPrevious = normalize(new ObjMeshData.Vec3(
                    previous.x() - origin.x(),
                    previous.y() - origin.y(),
                    previous.z() - origin.z()
            ));
            ObjMeshData.Vec3 toNext = normalize(new ObjMeshData.Vec3(
                    next.x() - origin.x(),
                    next.y() - origin.y(),
                    next.z() - origin.z()
            ));
            if (toPrevious == null || toNext == null) {
                return 1.0F;
            }

            float dot = toPrevious.x() * toNext.x()
                    + toPrevious.y() * toNext.y()
                    + toPrevious.z() * toNext.z();
            float angle = (float) Math.acos(Math.max(-1.0F, Math.min(1.0F, dot)));
            return Float.isFinite(angle) && angle > 0.0F ? angle : 1.0F;
        }

        private record NormalContribution(ObjMeshData.Vec3 normal, float weight) {
        }
    }

    private record FaceUvs(float[] u, float[] v) {
    }

    private static final class UvProjector {
        // A slightly oblique plane avoids collapsing the common cardinal-facing surfaces to a line.
        private static final ObjMeshData.Vec3 GLOBAL_U_AXIS = new ObjMeshData.Vec3(-0.319255F, 0.0F, -0.947669F);
        private static final ObjMeshData.Vec3 GLOBAL_V_AXIS = new ObjMeshData.Vec3(-0.147647F, 0.987789F, 0.049740F);
        private static final ObjMeshData.Vec3 GLOBAL_NORMAL_AXIS = new ObjMeshData.Vec3(0.936096F, 0.155800F, -0.315356F);
        // Keep the continuous planar map only while its worst local foreshortening stays below about 2.86x.
        private static final float PLANAR_MIN_NORMAL_DOT = 0.35F;

        // The Promachos shield is a shallow oblique disc inside the otherwise organic mesh. Restricting the
        // continuous projection to this normalised slab keeps the shield coherent without patching the body.
        private static final ObjMeshData.Vec3 SHIELD_U_AXIS = new ObjMeshData.Vec3(0.621882F, 0.0F, 0.783111F);
        private static final ObjMeshData.Vec3 SHIELD_V_AXIS = new ObjMeshData.Vec3(-0.390775F, 0.866600F, 0.310321F);
        private static final ObjMeshData.Vec3 SHIELD_NORMAL_AXIS = new ObjMeshData.Vec3(-0.678644F, -0.499003F, 0.538923F);
        private static final float SHIELD_CENTER_X = 0.383139F;
        private static final float SHIELD_CENTER_Y = 0.593457F;
        private static final float SHIELD_CENTER_Z = 0.168980F;
        private static final float SHIELD_MAX_ABS_U = 0.225F;
        private static final float SHIELD_MIN_V = -0.190F;
        private static final float SHIELD_MAX_V = 0.230F;
        private static final float SHIELD_MIN_DEPTH = -0.090F;
        private static final float SHIELD_MAX_DEPTH = 0.010F;

        // On rotational meshes, face-normal box selection changes projection across relief triangles. Choose the
        // axis from the face's position around the vertical axis, then use the geometric normal for its sign so
        // inner and outer surfaces retain the correct UV handedness. Fall back before foreshortening exceeds 2.86x.
        private static final float AXIAL_CAP_MIN_NORMAL_Y = 0.95F;
        private static final float RADIAL_CAP_MIN_NORMAL_Y = 0.45F;
        private static final float INWARD_BOWL_MIN_NORMAL_Y = 0.02F;
        private static final float INWARD_BOWL_MAX_RADIAL_DOT = -0.02F;
        private static final float AXIAL_MIN_NORMAL_DOT = 0.35F;
        private static final float CYLINDRICAL_MIN_RADIUS = 0.04F;
        private static final float INTERIOR_FLOOR_MIN_Y = 0.06F;
        private static final float INTERIOR_FLOOR_MAX_Y = 0.09F;
        private static final float INTERIOR_FLOOR_MAX_RADIUS = 0.42F;

        private final ObjMeshDefinition definition;
        private final ObjMeshData.Bounds bounds;
        private final float centerU;
        private final float centerV;
        private final float planarProjectionSpan;
        private final float boxProjectionSpan;

        private UvProjector(ObjMeshDefinition definition,
                            ObjMeshData.Bounds bounds,
                            float centerU,
                            float centerV,
                            float planarProjectionSpan,
                            float boxProjectionSpan) {
            this.definition = definition;
            this.bounds = bounds;
            this.centerU = centerU;
            this.centerV = centerV;
            this.planarProjectionSpan = planarProjectionSpan;
            this.boxProjectionSpan = boxProjectionSpan;
        }

        private static UvProjector create(ObjMeshDefinition definition,
                                          List<ObjMeshData.Vec3> positions,
                                          ObjMeshData.Bounds bounds) {
            float minU = Float.POSITIVE_INFINITY;
            float maxU = Float.NEGATIVE_INFINITY;
            float minV = Float.POSITIVE_INFINITY;
            float maxV = Float.NEGATIVE_INFINITY;
            for (ObjMeshData.Vec3 position : positions) {
                float projectedU = dot(position, GLOBAL_U_AXIS);
                float projectedV = dot(position, GLOBAL_V_AXIS);
                minU = Math.min(minU, projectedU);
                maxU = Math.max(maxU, projectedU);
                minV = Math.min(minV, projectedV);
                maxV = Math.max(maxV, projectedV);
            }
            float span = Math.max(maxU - minU, maxV - minV);
            if (!Float.isFinite(span) || span <= 0.0F) {
                span = bounds.largestSpan();
            }
            return new UvProjector(definition, bounds,
                    (minU + maxU) * 0.5F, (minV + maxV) * 0.5F,
                    // Separately authored sizes may share a projection span to
                    // retain material density. Never shrink below their bounds:
                    // that would push UVs outside the stitched repeat sprite.
                    span, Math.max(bounds.largestSpan(), definition.boxProjectionSpan()));
        }

        private ProjectionChoice chooseProjection(List<ObjMeshData.Vec3> positions,
                                                   List<ObjMeshData.VertexRef> refs,
                                                   String materialName,
                                                   ObjMeshData.Vec3 geometricNormal,
                                                   Direction nominalFace) {
            ObjMeshData.Vec3 centroid = centroid(positions, refs);
            ObjMeshDefinition.UvProjection mode = definition.materialUvProjections()
                    .getOrDefault(materialName, definition.uvProjection());
            if (mode == ObjMeshDefinition.UvProjection.GLOBAL_PLANAR) {
                return new ProjectionChoice(ProjectionKind.PLANAR, nominalFace, false, 1);
            }
            if (mode == ObjMeshDefinition.UvProjection.PLANAR_WITH_BOX_FALLBACK
                    && Math.abs(dot(geometricNormal, GLOBAL_NORMAL_AXIS)) >= PLANAR_MIN_NORMAL_DOT
                    && isInsideShieldRegion(centroid)) {
                return new ProjectionChoice(ProjectionKind.PLANAR, nominalFace, false, 1);
            }
            if (mode == ObjMeshDefinition.UvProjection.AXIS_STABILIZED_BOX) {
                return new ProjectionChoice(
                        ProjectionKind.BOX,
                        axialProjectionFace(centroid, geometricNormal, nominalFace),
                        false,
                        1
                );
            }
            if (mode.isCylindrical()) {
                if (mode == ObjMeshDefinition.UvProjection.CYLINDRICAL_WITH_RADIAL_CAPS
                        && isUpwardInwardSurface(centroid, geometricNormal)) {
                    return new ProjectionChoice(
                            ProjectionKind.BOX,
                            Direction.UP,
                            false,
                            definition.textureUTiles()
                    );
                }
                float capNormalThreshold = mode == ObjMeshDefinition.UvProjection.CYLINDRICAL_WITH_RADIAL_CAPS
                        ? RADIAL_CAP_MIN_NORMAL_Y
                        : AXIAL_CAP_MIN_NORMAL_Y;
                if (Math.abs(geometricNormal.y()) >= capNormalThreshold) {
                    Direction capFace = geometricNormal.y() >= 0.0F ? Direction.UP : Direction.DOWN;
                    return new ProjectionChoice(
                            ProjectionKind.BOX,
                            capFace,
                            false,
                            definition.textureUTiles()
                    );
                }
                if (minimumHorizontalRadius(positions, refs) < CYLINDRICAL_MIN_RADIUS) {
                    return new ProjectionChoice(
                            ProjectionKind.BOX,
                            nominalFace,
                            false,
                            definition.textureUTiles()
                    );
                }
                return new ProjectionChoice(
                        ProjectionKind.CYLINDRICAL,
                        nominalFace,
                        crossesRearSeam(positions, refs),
                        definition.textureUTiles()
                );
            }
            return new ProjectionChoice(ProjectionKind.BOX, nominalFace, false, 1);
        }

        private boolean isUpwardInwardSurface(ObjMeshData.Vec3 centroid,
                                               ObjMeshData.Vec3 geometricNormal) {
            if (geometricNormal.y() <= INWARD_BOWL_MIN_NORMAL_Y) {
                return false;
            }
            float deltaX = centroid.x() - (bounds.minX() + bounds.maxX()) * 0.5F;
            float deltaZ = centroid.z() - (bounds.minZ() + bounds.maxZ()) * 0.5F;
            float horizontalLength = (float) Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
            if (horizontalLength <= CYLINDRICAL_MIN_RADIUS) {
                return true;
            }
            float radialDot = (geometricNormal.x() * deltaX
                    + geometricNormal.z() * deltaZ) / horizontalLength;
            return radialDot <= INWARD_BOWL_MAX_RADIAL_DOT;
        }

        private boolean isInteriorFloorFace(List<ObjMeshData.Vec3> positions,
                                            List<ObjMeshData.VertexRef> refs,
                                            ObjMeshData.Vec3 geometricNormal) {
            if (geometricNormal.y() < AXIAL_CAP_MIN_NORMAL_Y || boxProjectionSpan <= 0.0F) {
                return false;
            }
            ObjMeshData.Vec3 centroid = centroid(positions, refs);
            float normalizedY = (centroid.y() - bounds.minY()) / boxProjectionSpan;
            if (normalizedY <= INTERIOR_FLOOR_MIN_Y || normalizedY >= INTERIOR_FLOOR_MAX_Y) {
                return false;
            }
            float centerX = (bounds.minX() + bounds.maxX()) * 0.5F;
            float centerZ = (bounds.minZ() + bounds.maxZ()) * 0.5F;
            float normalizedX = (centroid.x() - centerX) / boxProjectionSpan;
            float normalizedZ = (centroid.z() - centerZ) / boxProjectionSpan;
            return normalizedX * normalizedX + normalizedZ * normalizedZ
                    < INTERIOR_FLOOR_MAX_RADIUS * INTERIOR_FLOOR_MAX_RADIUS;
        }

        private void project(float[] u,
                             float[] v,
                             int index,
                             ObjMeshData.Vec3 position,
                             ProjectionChoice projection) {
            if (projection.kind() == ProjectionKind.PLANAR) {
                u[index] = clampUv(0.5F + (dot(position, GLOBAL_U_AXIS) - centerU) / planarProjectionSpan);
                v[index] = clampUv(0.5F - (dot(position, GLOBAL_V_AXIS) - centerV) / planarProjectionSpan);
                return;
            }

            float normalizedX = normalizeCoordinate(position.x(), bounds.minX(), boxProjectionSpan);
            float normalizedY = normalizeCoordinate(position.y(), bounds.minY(), boxProjectionSpan);
            float normalizedZ = normalizeCoordinate(position.z(), bounds.minZ(), boxProjectionSpan);
            if (projection.kind() == ProjectionKind.CYLINDRICAL) {
                float centerX = (bounds.minX() + bounds.maxX()) * 0.5F;
                float centerZ = (bounds.minZ() + bounds.maxZ()) * 0.5F;
                float angle = (float) Math.atan2(position.x() - centerX, centerZ - position.z());
                float normalizedAngle = (angle + (float) Math.PI) / ((float) Math.PI * 2.0F);
                if (projection.unwrapRearSeam() && normalizedAngle < 0.5F) {
                    normalizedAngle += 1.0F;
                }
                u[index] = normalizedAngle * definition.cylindricalURepeats() / projection.textureUTiles();
                v[index] = 1.0F - normalizedY;
                return;
            }

            if (!definition.faceOrientedUvs()) {
                switch (projection.face()) {
                    case UP, DOWN -> {
                        u[index] = normalizedX;
                        v[index] = normalizedZ;
                    }
                    case NORTH, SOUTH -> {
                        u[index] = normalizedX;
                        v[index] = 1.0F - normalizedY;
                    }
                    case EAST, WEST -> {
                        u[index] = normalizedZ;
                        v[index] = 1.0F - normalizedY;
                    }
                }
                u[index] /= projection.textureUTiles();
                return;
            }

            // Match Minecraft's face UV handedness so tangent-space normal maps point out of every face.
            switch (projection.face()) {
                case UP -> {
                    u[index] = normalizedX;
                    v[index] = normalizedZ;
                }
                case DOWN -> {
                    u[index] = normalizedX;
                    v[index] = 1.0F - normalizedZ;
                }
                case NORTH -> {
                    u[index] = 1.0F - normalizedX;
                    v[index] = 1.0F - normalizedY;
                }
                case SOUTH -> {
                    u[index] = normalizedX;
                    v[index] = 1.0F - normalizedY;
                }
                case EAST -> {
                    u[index] = 1.0F - normalizedZ;
                    v[index] = 1.0F - normalizedY;
                }
                case WEST -> {
                    u[index] = normalizedZ;
                    v[index] = 1.0F - normalizedY;
                }
            }
            u[index] /= projection.textureUTiles();
        }

        private boolean isInsideShieldRegion(ObjMeshData.Vec3 centroid) {
            ObjMeshData.Vec3 centre = new ObjMeshData.Vec3(
                    bounds.minX() + SHIELD_CENTER_X * boxProjectionSpan,
                    bounds.minY() + SHIELD_CENTER_Y * boxProjectionSpan,
                    bounds.minZ() + SHIELD_CENTER_Z * boxProjectionSpan
            );
            ObjMeshData.Vec3 offset = new ObjMeshData.Vec3(
                    (centroid.x() - centre.x()) / boxProjectionSpan,
                    (centroid.y() - centre.y()) / boxProjectionSpan,
                    (centroid.z() - centre.z()) / boxProjectionSpan
            );
            float shieldU = dot(offset, SHIELD_U_AXIS);
            float shieldV = dot(offset, SHIELD_V_AXIS);
            float shieldDepth = dot(offset, SHIELD_NORMAL_AXIS);
            return Math.abs(shieldU) <= SHIELD_MAX_ABS_U
                    && shieldV >= SHIELD_MIN_V
                    && shieldV <= SHIELD_MAX_V
                    && shieldDepth >= SHIELD_MIN_DEPTH
                    && shieldDepth <= SHIELD_MAX_DEPTH;
        }

        private Direction axialProjectionFace(ObjMeshData.Vec3 centroid,
                                               ObjMeshData.Vec3 geometricNormal,
                                               Direction nominalFace) {
            if (Math.abs(geometricNormal.y()) >= AXIAL_CAP_MIN_NORMAL_Y) {
                return geometricNormal.y() >= 0.0F ? Direction.UP : Direction.DOWN;
            }

            float deltaX = centroid.x() - (bounds.minX() + bounds.maxX()) * 0.5F;
            float deltaZ = centroid.z() - (bounds.minZ() + bounds.maxZ()) * 0.5F;
            Direction radialFace = Math.abs(deltaX) >= Math.abs(deltaZ)
                    ? (deltaX >= 0.0F ? Direction.EAST : Direction.WEST)
                    : (deltaZ >= 0.0F ? Direction.SOUTH : Direction.NORTH);
            float normalDot = switch (radialFace.getAxis()) {
                case X -> Math.abs(geometricNormal.x());
                case Y -> Math.abs(geometricNormal.y());
                case Z -> Math.abs(geometricNormal.z());
            };
            Direction normalFacing = switch (radialFace.getAxis()) {
                case X -> geometricNormal.x() >= 0.0F ? Direction.EAST : Direction.WEST;
                case Y -> geometricNormal.y() >= 0.0F ? Direction.UP : Direction.DOWN;
                case Z -> geometricNormal.z() >= 0.0F ? Direction.SOUTH : Direction.NORTH;
            };
            return normalDot >= AXIAL_MIN_NORMAL_DOT ? normalFacing : nominalFace;
        }

        private boolean crossesRearSeam(List<ObjMeshData.Vec3> positions,
                                        List<ObjMeshData.VertexRef> refs) {
            float centerX = (bounds.minX() + bounds.maxX()) * 0.5F;
            float centerZ = (bounds.minZ() + bounds.maxZ()) * 0.5F;
            float minimumAngle = Float.POSITIVE_INFINITY;
            float maximumAngle = Float.NEGATIVE_INFINITY;
            for (ObjMeshData.VertexRef ref : refs) {
                ObjMeshData.Vec3 position = positions.get(ref.positionIndex());
                float angle = (float) Math.atan2(position.x() - centerX, centerZ - position.z());
                minimumAngle = Math.min(minimumAngle, angle);
                maximumAngle = Math.max(maximumAngle, angle);
            }
            return maximumAngle - minimumAngle > Math.PI;
        }

        private float minimumHorizontalRadius(List<ObjMeshData.Vec3> positions,
                                              List<ObjMeshData.VertexRef> refs) {
            float centerX = (bounds.minX() + bounds.maxX()) * 0.5F;
            float centerZ = (bounds.minZ() + bounds.maxZ()) * 0.5F;
            float minimumSquaredRadius = Float.POSITIVE_INFINITY;
            for (ObjMeshData.VertexRef ref : refs) {
                ObjMeshData.Vec3 position = positions.get(ref.positionIndex());
                float deltaX = position.x() - centerX;
                float deltaZ = position.z() - centerZ;
                minimumSquaredRadius = Math.min(
                        minimumSquaredRadius,
                        deltaX * deltaX + deltaZ * deltaZ
                );
            }
            return (float) Math.sqrt(minimumSquaredRadius);
        }

        private static ObjMeshData.Vec3 centroid(List<ObjMeshData.Vec3> positions,
                                                 List<ObjMeshData.VertexRef> refs) {
            float x = 0.0F;
            float y = 0.0F;
            float z = 0.0F;
            for (ObjMeshData.VertexRef ref : refs) {
                ObjMeshData.Vec3 position = positions.get(ref.positionIndex());
                x += position.x();
                y += position.y();
                z += position.z();
            }
            float inverseCount = 1.0F / refs.size();
            return new ObjMeshData.Vec3(x * inverseCount, y * inverseCount, z * inverseCount);
        }

        private static float dot(ObjMeshData.Vec3 vector, ObjMeshData.Vec3 axis) {
            return vector.x() * axis.x() + vector.y() * axis.y() + vector.z() * axis.z();
        }

        private static float clampUv(float value) {
            return Math.max(0.0F, Math.min(1.0F, value));
        }

        private enum ProjectionKind {
            PLANAR,
            BOX,
            CYLINDRICAL
        }

        private record ProjectionChoice(ProjectionKind kind,
                                        Direction face,
                                        boolean unwrapRearSeam,
                                        int textureUTiles) {
        }
    }

    private record Transform(boolean fitted,
                             float fitScale,
                             float centerX,
                             float baseY,
                             float centerZ,
                             ObjMeshDefinition.Vec3 scale,
                             ObjMeshDefinition.Vec3 translate) {
        static Transform create(ObjMeshDefinition definition, ObjMeshData.Bounds bounds) {
            float largestSpan = bounds.largestSpan();
            if (!Float.isFinite(largestSpan) || largestSpan <= 0.0F) {
                throw new IllegalArgumentException("Cannot fit OBJ " + definition.objId() + " because its bounds have no finite size");
            }
            float fitScale = definition.fitToBlock() ? 1.0F / largestSpan : 1.0F;
            return new Transform(
                    definition.fitToBlock(),
                    fitScale,
                    (bounds.minX() + bounds.maxX()) * 0.5F,
                    bounds.minY(),
                    (bounds.minZ() + bounds.maxZ()) * 0.5F,
                    definition.scale(),
                    definition.translate()
            );
        }

        ObjMeshData.Vec3 position(ObjMeshData.Vec3 raw) {
            if (!fitted) {
                return new ObjMeshData.Vec3(
                        raw.x() * scale.x() + translate.x(),
                        raw.y() * scale.y() + translate.y(),
                        raw.z() * scale.z() + translate.z()
                );
            }
            return new ObjMeshData.Vec3(
                    (raw.x() - centerX) * fitScale * scale.x() + 0.5F + translate.x(),
                    (raw.y() - baseY) * fitScale * scale.y() + translate.y(),
                    (raw.z() - centerZ) * fitScale * scale.z() + 0.5F + translate.z()
            );
        }
    }
}
