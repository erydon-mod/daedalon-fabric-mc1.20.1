package com.oliver.daedalon.client.model.obj;

import com.oliver.daedalon.Daedalon;
import com.oliver.daedalon.block.ClassicalStatueBlock;
import com.oliver.daedalon.block.BustBlock;
import com.oliver.daedalon.block.CapitalBlock;
import com.oliver.daedalon.block.CorbelBlock;
import com.oliver.daedalon.block.BenchBlock;
import com.oliver.daedalon.block.FixedDecorBlock;
import com.oliver.daedalon.block.FountainBasinBlock;
import com.oliver.daedalon.block.FountainBowlModel;
import com.oliver.daedalon.block.PlinthBlock;
import com.oliver.daedalon.block.UrnBlock;
import com.oliver.daedalon.registry.ModBlocks;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.PreparableModelLoadingPlugin;
import net.fabricmc.fabric.api.renderer.v1.mesh.Mesh;
import net.minecraft.block.Block;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.Baker;
import net.minecraft.client.render.model.ModelBakeSettings;
import net.minecraft.client.render.model.UnbakedModel;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.render.model.json.Transformation;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.ModelIdentifier;
import net.minecraft.client.util.SpriteIdentifier;
import net.minecraft.resource.ResourceManager;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.function.Function;

/**
 * Loads Daedalon OBJ-mesh model families. Each source OBJ is parsed and
 * baked once per resource reload; material variants only retain their atlas
 * sprites and item-display metadata.
 */
public final class ObjMeshModelLoadingPlugin {
    public static final String SPARTAN_BLOCK_PREFIX = "statue_spartan_promachos_";
    public static final List<String> URN_SHAPES = Arrays.stream(UrnBlock.UrnStyle.values())
            .map(UrnBlock.UrnStyle::resourceStem)
            .toList();

    private static final List<String> MATERIALS = List.of(
            "aganite",
            "aterzon",
            "borealis",
            "brectite",
            "calacattum",
            "chalstrom",
            "chrysonyx",
            "etruscus",
            "gelastrum",
            "glacium",
            "hesperion",
            "imperium",
            "kylorion",
            "kelastrion",
            "latmion",
            "laurentium",
            "mielonyx",
            "nerium",
            "noxoplis",
            "porphyros",
            "psamatheon",
            "portorium",
            "rosinium",
            "sanguenite",
            "selenephos",
            "solistra",
            "striatus"
    );
    private static final Identifier BRONZE_TEXTURE = id("block/bronze");
    private static final Identifier WATER_STILL_TEXTURE = new Identifier(
            "minecraft",
            "block/water_still"
    );

    private static final List<MeshFamily> FAMILIES = createFamilies();

    private ObjMeshModelLoadingPlugin() {
    }

    public static void register() {
        for (MeshFamily family : activeFamilies()) {
            PreparableModelLoadingPlugin.register(
                    (resourceManager, executor) -> load(family, resourceManager, executor),
                    ObjMeshModelLoadingPlugin::initialize
            );
        }
    }

    public static List<String> blockIds() {
        List<String> result = new ArrayList<>();
        for (MeshFamily family : activeFamilies()) {
            result.addAll(family.variants().stream()
                    .filter(MeshVariant::publicBlockModel)
                    .map(MeshVariant::sourceBlockId)
                    .toList());
        }
        return List.copyOf(result);
    }

    public static Identifier fountainBowlModelId(PlinthBlock plinth) {
        return fountainBowlModelId(plinth, plinth.style().fountainBowlStyle());
    }

    public static Identifier fountainBowlModelId(PlinthBlock plinth,
                                                 FountainBowlModel.Style style) {
        return id(internalFountainBowlModelPath(
                plinth.fountainMaterialKey(),
                style
        ));
    }

    private static List<MeshFamily> activeFamilies() {
        return FAMILIES.stream()
                .filter(family -> family.variants().stream()
                        .anyMatch(variant -> ModBlocks.blocksById()
                                .containsKey(id(variant.sourceBlockId()))))
                .toList();
    }

    private static CompletableFuture<PreparedModels> load(MeshFamily family,
                                                           ResourceManager resourceManager,
                                                           Executor executor) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ObjMeshDefinition definition = ObjMeshDefinition.load(resourceManager, family.definitionResource());
                ObjMeshData data = ObjMeshParser.parse(resourceManager, definition);
                if (data.triangulatedNgonCount() > 0) {
                    Daedalon.LOGGER.warn(
                            "[Daedalon OBJ Mesh] {} fan-triangulated {} n-gons into {} triangles",
                            data.objId(),
                            data.triangulatedNgonCount(),
                            data.triangulatedNgonTriangleCount()
                    );
                }

                ObjMeshDefinition itemDefinition = family.itemDefinitionResource() == null ? null
                        : ObjMeshDefinition.load(resourceManager, family.itemDefinitionResource());
                ObjMeshData itemData = itemDefinition == null ? null
                        : ObjMeshParser.parse(resourceManager, itemDefinition);
                SharedGeometry geometry = new SharedGeometry(
                        definition,
                        data,
                        family.variants().size(),
                        family.variants().get(0).worldTexturePhase(),
                        itemDefinition,
                        itemData
                );
                List<PreparedModel> prepared = new ArrayList<>(family.variants().size());
                for (MeshVariant variant : family.variants()) {
                    prepared.add(new PreparedModel(family.displayModel(), variant, geometry));
                }
                Daedalon.LOGGER.debug(
                        "[Daedalon OBJ Mesh] prepared {} {} material models from one parsed OBJ",
                        prepared.size(),
                        family.displayName()
                );
                return PreparedModels.create(prepared);
            } catch (IOException exception) {
                throw new CompletionException(
                        "Unable to prepare Daedalon " + family.displayName() + " OBJ mesh variants: " + exception.getMessage(),
                        exception
                );
            }
        }, executor);
    }

    private static void initialize(PreparedModels prepared, ModelLoadingPlugin.Context context) {
        for (PreparedModel model : prepared.models()) {
            context.addModels(model.variant.modelId());
        }
        context.resolveModel().register(resolveContext -> prepared.resolve(resolveContext.id()));
    }

    private static List<MeshVariant> createSpartanVariants() {
        List<MeshVariant> variants = new ArrayList<>(MATERIALS.size() * 2 + 1);
        for (String material : MATERIALS) {
            variants.add(variant(SPARTAN_BLOCK_PREFIX + material, SPARTAN_BLOCK_PREFIX + material));
            variants.add(variant(SPARTAN_BLOCK_PREFIX + material + "_aged", SPARTAN_BLOCK_PREFIX + material + "_aged"));
        }
        variants.add(bronzeVariant(
                "bronze_spartan_promachos_statue",
                WorldTexturePhase.detailedSurface()
        ));
        return List.copyOf(variants);
    }

    private static List<MeshVariant> createZeusVariants() {
        List<MeshVariant> variants = new ArrayList<>(MATERIALS.size() * 2 + 1);
        for (String material : MATERIALS) {
            variants.add(variant(
                    material + "_zeus_statue",
                    SPARTAN_BLOCK_PREFIX + material
            ));
            variants.add(variant(
                    material + "_aged_zeus_statue",
                    SPARTAN_BLOCK_PREFIX + material + "_aged"
            ));
        }
        variants.add(bronzeVariant(
                "bronze_zeus_statue",
                WorldTexturePhase.detailedSurface()
        ));
        return List.copyOf(variants);
    }

    private static List<MeshFamily> createFamilies() {
        List<MeshFamily> families = new ArrayList<>();
        families.add(new MeshFamily(
                "Spartan Promachos",
                id("models/mesh/statue_spartan_promachos.json"),
                id("block/mesh/statue_spartan_promachos_display"),
                createSpartanVariants()
        ));
        families.add(new MeshFamily(
                "Zeus Statue",
                id("models/mesh/zeus_statue.json"),
                id("block/mesh/zeus_statue_display"),
                createZeusVariants()
        ));
        for (ClassicalStatueBlock.Style style : ClassicalStatueBlock.Style.values()) {
            families.add(new MeshFamily(
                    style.displayName() + " Statue",
                    id("models/mesh/" + style.subjectId() + "_statue.json"),
                    id("block/mesh/zeus_statue_display"),
                    createClassicalStatueVariants(style.subjectId())
            ));
        }
        for (BustBlock.Style style : BustBlock.Style.values()) {
            families.add(new MeshFamily(
                    style.displayName() + " Bust",
                    id("models/mesh/" + style.resourceStem() + ".json"),
                    id("block/mesh/bust_display"),
                    createBustVariants(style.subjectId())
            ));
        }
        for (UrnBlock.UrnStyle style : UrnBlock.UrnStyle.values()) {
            families.add(createUrnFamily(style));
        }
        for (CorbelBlock.Style style : CorbelBlock.Style.values()) {
            families.add(new MeshFamily(
                    style.displayName() + " Corbel",
                    id("models/mesh/" + style.resourceStem() + ".json"),
                    id("block/mesh/corbel_display"),
                    createCorbelVariants(style.styleId())
            ));
        }
        for (CapitalBlock.Style style : CapitalBlock.Style.values()) {
            families.add(new MeshFamily(
                    style.displayName() + " Capital",
                    id("models/mesh/" + style.resourceStem() + ".json"),
                    id("block/mesh/capital_display"),
                    createArchitecturalVariants(style.styleId(), "capital")
            ));
        }
        for (FixedDecorBlock.Style style : FixedDecorBlock.Style.values()) {
            families.add(new MeshFamily(
                    style.displayName(),
                    id("models/mesh/" + style.resourceStem() + ".json"),
                    style.isFinial()
                            ? id("block/mesh/finial_display")
                            : id("block/mesh/decor_display"),
                    style.isFinial()
                            ? withBronze(createDetailedVariants(style.idSuffix()), "bronze_" + style.idSuffix(), WorldTexturePhase.detailedSurface())
                            : createDetailedVariants(style.idSuffix())
            ));
        }
        for (FountainBasinBlock.Style style : FountainBasinBlock.Style.values()) {
            families.add(new MeshFamily(
                    style.displayName(),
                    id("models/mesh/" + style.resourceStem() + ".json"),
                    id("block/mesh/fountain_basin_display"),
                    createDetailedVariants(style.idSuffix())
            ));
        }
        for (FountainBowlModel.Style style : FountainBowlModel.Style.values()) {
            families.add(new MeshFamily(
                    "Internal " + style.id() + " fountain bowls",
                    id("models/mesh/" + style.resourceStem() + ".json"),
                    id("block/mesh/fountain_bowl_display"),
                    createInternalFountainBowlVariants(style)
            ));
        }
        families.add(new MeshFamily(
                "Krene Fountain",
                id("models/mesh/fountain_krene.json"),
                id("block/mesh/krene_display"),
                createDetailedVariants("krene_fountain")
        ));
        families.add(new MeshFamily(
                "Obeliskos Monument",
                id("models/mesh/monument_obeliskos.json"),
                id("block/mesh/monument_display"),
                withBronze(
                        createDetailedVariants("obeliskos_monument"),
                        "bronze_obeliskos_monument",
                        WorldTexturePhase.detailedSurface()
                )
        ));
        for (PlinthBlock.Style style : PlinthBlock.Style.values()) {
            families.add(new MeshFamily(
                    style.displayName() + " Plinth",
                    id("models/mesh/" + style.resourceStem() + ".json"),
                    id("block/mesh/plinth_display"),
                    createDetailedVariants(style.styleId() + "_plinth")
            ));
        }
        for (int diameter : new int[]{4, 6, 8}) {
            List<MeshVariant> variants = withBronze(createDetailedVariants("monopteros_dome"),
                    "bronze_monopteros_dome", WorldTexturePhase.detailedSurface());
            if (diameter != 6) {
                variants = variants.stream().map(variant -> new MeshVariant(
                        id("mesh/internal/" + variant.sourceBlockId() + "_" + diameter + "m"),
                        null, null, null, variant.textureId(), variant.particleTextureId(),
                        variant.worldTexturePhase(), variant.sourceBlockId(), false, false)).toList();
            }
            families.add(new MeshFamily("Monopteros " + diameter + "m",
                    id("models/mesh/monopteros_" + diameter + "m.json"),
                    id("block/mesh/monopteros_display"), variants,
                    diameter == 6 ? id("models/mesh/monopteros_item.json") : null));
        }
        for (String bench : List.of("exedra", "hedra")) {
            for (int width = 2; width <= 4; width++) {
                families.add(new MeshFamily(
                        bench + " " + width + "m",
                        id("models/mesh/" + bench + "_" + width + "m.json"),
                        id("block/mesh/" + bench + "_display"),
                        createBenchVariants(bench, width),
                        width == BenchBlock.DEFAULT_WIDTH ? id("models/mesh/" + bench + "_item.json") : null
                ));
            }
        }
        return List.copyOf(families);
    }

    private static List<MeshVariant> createBenchVariants(String bench, int width) {
        List<MeshVariant> variants = createDetailedVariants(bench);
        if (width == BenchBlock.DEFAULT_WIDTH) {
            return variants;
        }
        // Only the default width owns the item aliases and public block list.
        // The other widths share their own geometry across the same materials.
        return variants.stream().map(variant -> new MeshVariant(
                id("mesh/internal/" + variant.sourceBlockId() + "_" + width + "m"),
                null, null, null,
                variant.textureId(), variant.particleTextureId(),
                variant.worldTexturePhase(), variant.sourceBlockId(), false, false
        )).toList();
    }

    private static List<MeshVariant> createClassicalStatueVariants(String subjectId) {
        List<MeshVariant> variants = new ArrayList<>(MATERIALS.size() * 2 + 1);
        for (String material : MATERIALS) {
            variants.add(variant(
                    material + "_" + subjectId + "_statue",
                    SPARTAN_BLOCK_PREFIX + material
            ));
            variants.add(variant(
                    material + "_aged_" + subjectId + "_statue",
                    SPARTAN_BLOCK_PREFIX + material + "_aged"
            ));
        }
        variants.add(bronzeVariant(
                "bronze_" + subjectId + "_statue",
                WorldTexturePhase.detailedSurface()
        ));
        return List.copyOf(variants);
    }

    private static List<MeshVariant> createBustVariants(String subjectId) {
        List<MeshVariant> variants = new ArrayList<>(MATERIALS.size() * 2 + 1);
        for (String material : MATERIALS) {
            variants.add(variant(
                    material + "_" + subjectId + "_bust",
                    SPARTAN_BLOCK_PREFIX + material
            ));
            variants.add(variant(
                    material + "_aged_" + subjectId + "_bust",
                    SPARTAN_BLOCK_PREFIX + material + "_aged"
            ));
        }
        variants.add(bronzeVariant(
                "bronze_" + subjectId + "_bust",
                WorldTexturePhase.detailedSurface()
        ));
        return List.copyOf(variants);
    }

    private static MeshFamily createUrnFamily(UrnBlock.UrnStyle style) {
        return new MeshFamily(
                style.displayName() + " Urn",
                id("models/mesh/" + style.resourceStem() + ".json"),
                id("block/mesh/" + style.resourceStem() + "_display"),
                createUrnVariants(style.shapeId())
        );
    }

    private static List<MeshVariant> createUrnVariants(String shapeId) {
        List<MeshVariant> variants = new ArrayList<>(MATERIALS.size() * 2 + 1);
        for (String material : MATERIALS) {
            variants.add(urnVariant(
                    material + "_" + shapeId + "_urn",
                    material + "_block"
            ));
            variants.add(urnVariant(
                    material + "_aged_" + shapeId + "_urn",
                    material + "_block_aged"
            ));
        }
        variants.add(bronzeVariant(
                "bronze_" + shapeId + "_urn",
                WorldTexturePhase.urnSurface()
        ));
        return List.copyOf(variants);
    }

    private static List<MeshVariant> withBronze(
            List<MeshVariant> variants,
            String blockId,
            WorldTexturePhase.Layout layout
    ) {
        List<MeshVariant> result = new ArrayList<>(variants.size() + 1);
        result.addAll(variants);
        result.add(bronzeVariant(blockId, layout));
        return List.copyOf(result);
    }

    private static MeshVariant bronzeVariant(
            String blockId,
            WorldTexturePhase.Layout layout
    ) {
        return new MeshVariant(
                id("mesh/" + blockId),
                new ModelIdentifier(Daedalon.MOD_ID, blockId, "inventory"),
                id("item/" + blockId),
                id(blockId),
                BRONZE_TEXTURE,
                BRONZE_TEXTURE,
                layout,
                blockId,
                true,
                false
        );
    }

    private static List<MeshVariant> createMaterialVariants(String shape) {
        List<MeshVariant> variants = new ArrayList<>(MATERIALS.size() * 2);
        for (String material : MATERIALS) {
            variants.add(variant(material + "_" + shape, material + "_block"));
            variants.add(variant(material + "_" + shape + "_aged", material + "_block_aged"));
        }
        return List.copyOf(variants);
    }

    private static List<MeshVariant> createArchitecturalVariants(
            String styleId,
            String form
    ) {
        List<MeshVariant> variants = new ArrayList<>(MATERIALS.size() * 2);
        for (String material : MATERIALS) {
            variants.add(variant(
                    material + "_" + styleId + "_" + form,
                    material + "_block"
            ));
            variants.add(variant(
                    material + "_aged_" + styleId + "_" + form,
                    material + "_block_aged"
            ));
        }
        return List.copyOf(variants);
    }

    private static List<MeshVariant> createCorbelVariants(String styleId) {
        List<MeshVariant> variants = new ArrayList<>(MATERIALS.size() * 2);
        for (String material : MATERIALS) {
            variants.add(variant(
                    material + "_" + styleId + "_corbel",
                    SPARTAN_BLOCK_PREFIX + material
            ));
            variants.add(variant(
                    material + "_aged_" + styleId + "_corbel",
                    SPARTAN_BLOCK_PREFIX + material + "_aged"
            ));
        }
        return List.copyOf(variants);
    }

    private static List<MeshVariant> createDetailedVariants(String idSuffix) {
        List<MeshVariant> variants = new ArrayList<>(MATERIALS.size() * 2);
        for (String material : MATERIALS) {
            variants.add(variant(material + "_" + idSuffix, SPARTAN_BLOCK_PREFIX + material));
            variants.add(variant(
                    material + "_aged_" + idSuffix,
                    SPARTAN_BLOCK_PREFIX + material + "_aged"
            ));
        }
        return List.copyOf(variants);
    }

    private static List<MeshVariant> createInternalFountainBowlVariants(
            FountainBowlModel.Style style
    ) {
        List<MeshVariant> variants = new ArrayList<>(MATERIALS.size() * 2);
        for (String material : MATERIALS) {
            for (boolean aged : new boolean[]{false, true}) {
                String materialKey = material + (aged ? "_aged" : "");
                String sourceBlockId = material + (aged ? "_aged" : "")
                        + "_" + style.basinIdSuffix();
                String textureBlockId = SPARTAN_BLOCK_PREFIX + materialKey;
                variants.add(new MeshVariant(
                        id(internalFountainBowlModelPath(materialKey, style)),
                        null,
                        null,
                        null,
                        id("block/" + textureBlockId),
                        id("block/" + baseTextureBlockId(textureBlockId)),
                        WorldTexturePhase.detailedSurface(),
                        sourceBlockId,
                        false,
                        true
                ));
            }
        }
        return List.copyOf(variants);
    }

    private static String internalFountainBowlModelPath(
            String materialKey,
            FountainBowlModel.Style style
    ) {
        return "mesh/internal/" + materialKey + "_" + style.resourceStem();
    }

    private static MeshVariant variant(String blockId, String textureBlockId) {
        boolean detailedSurface = textureBlockId.startsWith(SPARTAN_BLOCK_PREFIX);
        String repeatTextureBlockId = detailedSurface
                ? textureBlockId
                : detailedTextureBlockId(textureBlockId);
        return new MeshVariant(
                id("mesh/" + blockId),
                new ModelIdentifier(Daedalon.MOD_ID, blockId, "inventory"),
                id("item/" + blockId),
                id(blockId),
                id("block/" + repeatTextureBlockId),
                id("block/" + baseTextureBlockId(textureBlockId)),
                detailedSurface
                        ? WorldTexturePhase.detailedSurface()
                        : WorldTexturePhase.blockSurface(),
                blockId,
                true,
                false
        );
    }

    private static MeshVariant urnVariant(String blockId, String textureBlockId) {
        return new MeshVariant(
                id("mesh/" + blockId),
                new ModelIdentifier(Daedalon.MOD_ID, blockId, "inventory"),
                id("item/" + blockId),
                id(blockId),
                id("block/" + detailedTextureBlockId(textureBlockId)),
                id("block/" + textureBlockId),
                WorldTexturePhase.urnSurface(),
                blockId,
                true,
                false
        );
    }

    private static String detailedTextureBlockId(String textureBlockId) {
        String baseTextureBlockId = baseTextureBlockId(textureBlockId);
        boolean aged = baseTextureBlockId.endsWith("_block_aged");
        String material = aged
                ? baseTextureBlockId.substring(0, baseTextureBlockId.length() - "_block_aged".length())
                : baseTextureBlockId.substring(0, baseTextureBlockId.length() - "_block".length());
        return SPARTAN_BLOCK_PREFIX + material + (aged ? "_aged" : "");
    }

    private static String baseTextureBlockId(String textureBlockId) {
        if (!textureBlockId.startsWith(SPARTAN_BLOCK_PREFIX)) {
            if (!textureBlockId.endsWith("_block") && !textureBlockId.endsWith("_block_aged")) {
                throw new IllegalArgumentException("Unsupported Daedalon material texture " + textureBlockId);
            }
            return textureBlockId;
        }
        String material = textureBlockId.substring(SPARTAN_BLOCK_PREFIX.length());
        boolean aged = material.endsWith("_aged");
        if (aged) {
            material = material.substring(0, material.length() - "_aged".length());
        }
        return material + "_block" + (aged ? "_aged" : "");
    }

    private static Identifier id(String path) {
        return new Identifier(Daedalon.MOD_ID, path);
    }

    private record MeshVariant(Identifier modelId,
                               ModelIdentifier inventoryModelId,
                               Identifier itemFileModelId,
                               Identifier plainItemModelId,
                               Identifier textureId,
                               Identifier particleTextureId,
                               WorldTexturePhase.Layout worldTexturePhase,
                               String sourceBlockId,
                               boolean publicBlockModel,
                               boolean containedWater) {
    }

    private record MeshFamily(String displayName,
                              Identifier definitionResource,
                              Identifier displayModel,
                              List<MeshVariant> variants,
                              Identifier itemDefinitionResource) {
        private MeshFamily(String displayName, Identifier definitionResource,
                           Identifier displayModel, List<MeshVariant> variants) {
            this(displayName, definitionResource, displayModel, variants, null);
        }

        private MeshFamily {
            variants = List.copyOf(variants);
            if (variants.isEmpty()) {
                throw new IllegalArgumentException("OBJ mesh family must contain material variants");
            }
            WorldTexturePhase.Layout layout = variants.get(0).worldTexturePhase();
            if (variants.stream().anyMatch(variant -> !variant.worldTexturePhase().equals(layout))) {
                throw new IllegalArgumentException("OBJ mesh family variants must share one repeat-sheet layout");
            }
        }
    }

    private record PreparedModels(List<PreparedModel> models, Map<Identifier, UnbakedModel> byModelId) {
        private static PreparedModels create(List<PreparedModel> models) {
            Map<Identifier, UnbakedModel> byModelId = new LinkedHashMap<>();
            for (PreparedModel model : models) {
                MeshVariant variant = model.variant;
                byModelId.put(variant.modelId(), model.unbakedModel);
                if (variant.publicBlockModel()) {
                    byModelId.put(variant.inventoryModelId(), model.unbakedModel);
                    byModelId.put(variant.itemFileModelId(), model.unbakedModel);
                    byModelId.put(variant.plainItemModelId(), model.unbakedModel);
                }
            }
            // Keep vanilla Identifier and ModelIdentifier aliases side by side. Map.copyOf
            // rejects some same-path aliases because ModelIdentifier has specialised key
            // equality, even though the mutable lookup map safely distinguishes them.
            return new PreparedModels(
                    List.copyOf(models),
                    Collections.unmodifiableMap(new LinkedHashMap<>(byModelId))
            );
        }

        private UnbakedModel resolve(Identifier modelId) {
            return byModelId.get(modelId);
        }
    }

    private static final class SharedGeometry {
        private final ObjMeshDefinition definition;
        private final Map<String, Identifier> materialTextures;
        private final int modelCount;
        private final WorldTexturePhase.Layout worldTexturePhase;
        private ObjMeshData data;
        private final ObjMeshDefinition itemDefinition;
        private ObjMeshData itemData;
        private Mesh itemMesh;
        private ObjMeshBakedModel.GeometryBakeResult baked;
        private ObjGuiIconTemplate guiIconTemplate;
        private Transformation guiTransformation;

        private SharedGeometry(ObjMeshDefinition definition,
                               ObjMeshData data,
                               int modelCount,
                               WorldTexturePhase.Layout worldTexturePhase,
                               ObjMeshDefinition itemDefinition,
                               ObjMeshData itemData) {
            this.definition = definition;
            this.data = data;
            this.materialTextures = data.materialTextures();
            this.modelCount = modelCount;
            this.worldTexturePhase = worldTexturePhase;
            this.itemDefinition = itemDefinition;
            this.itemData = itemData;
        }

        private synchronized ObjMeshBakedModel.GeometryBakeResult bake(BakedModel metadataModel) {
            Transformation requestedGuiTransformation = metadataModel.getTransformation()
                    .getTransformation(ModelTransformationMode.GUI);
            if (baked != null) {
                if (!guiTransformation.equals(requestedGuiTransformation)) {
                    guiIconTemplate = null;
                    Daedalon.LOGGER.warn(
                            "[Daedalon OBJ GUI] {} material variants use different GUI transforms; using full-model fallback",
                            definition.objId()
                    );
                }
                return baked;
            }

            ObjMeshData sourceData = Objects.requireNonNull(
                    data,
                    "OBJ source data was released before shared geometry was baked"
            );
            baked = ObjMeshBakedModel.bakeGeometry(definition, sourceData);
            data = null;
            if (itemData != null) {
                // One optional small 3D item mesh per family, shared by every material.
                // Placed geometry and the full-detail GUI template still use baked.mesh().
                ObjMeshBakedModel.GeometryBakeResult itemBake =
                        ObjMeshBakedModel.bakeGeometry(itemDefinition, itemData);
                if (!itemBake.materialNames().equals(baked.materialNames())) {
                    throw new IllegalStateException("Item mesh must retain the world's material tags: " + itemDefinition.objId());
                }
                itemMesh = itemBake.mesh();
                itemData = null;
                Daedalon.LOGGER.debug(
                        "[Daedalon OBJ Item] shared item mesh obj={} fabricQuads={} materialModels={}",
                        itemDefinition.objId(), itemBake.emittedQuadCount(), modelCount
                );
            }
            guiTransformation = requestedGuiTransformation;
            try {
                guiIconTemplate = ObjGuiIconTemplate.create(
                        baked.mesh(),
                        guiTransformation,
                        baked.emittedQuadCount()
                );
            } catch (RuntimeException exception) {
                guiIconTemplate = null;
                Daedalon.LOGGER.warn(
                        "[Daedalon OBJ GUI] Unable to prepare full-mesh icon template for {}; using full-model fallback",
                        sourceData.objId(),
                        exception
                );
            }
            String mtl = sourceData.mtlIds().isEmpty() ? "none" : sourceData.mtlIds().toString();
            Daedalon.LOGGER.debug(
                    "[Daedalon OBJ Mesh] shared geometry cache MISS obj={} mtl={} vertices={} uvs={} normals={} triangles={} quads={} triangulatedNgons={} fabricQuads={} guiIconSourceQuads={} guiIconSamples={} materials={} objects={} groups={} originalBounds={} transformedBounds={} parseMs={} bakeMs={} normalMode={} smoothAngle={} uvProjection={} worldTexturePhases={} worldTextureMaxUOffset={} smoothedNormalCorners={} repairedNormals={} repairedUvFaces={} degenerateFaces={} approxVertexBytes={} meshIdentity={} materialModels={}",
                    sourceData.objId(),
                    mtl,
                    sourceData.positions().size(),
                    sourceData.textureCoordinates().size(),
                    sourceData.normals().size(),
                    sourceData.emittedTriangleCount(),
                    sourceData.sourceQuadCount(),
                    sourceData.triangulatedNgonCount(),
                    baked.emittedQuadCount(),
                    guiIconTemplate == null ? 0 : guiIconTemplate.sourceQuadCount(),
                    guiIconTemplate == null ? 0 : guiIconTemplate.coveredSampleCount(),
                    sourceData.materialCount(),
                    sourceData.objectCount(),
                    sourceData.groupCount(),
                    sourceData.originalBounds(),
                    baked.transformedBounds(),
                    milliseconds(sourceData.parseNanos()),
                    milliseconds(baked.bakeNanos()),
                    definition.smoothNormals() ? "smoothed" : "imported",
                    definition.smoothAngleDegrees(),
                    definition.uvProjection().configValue(),
                    worldTexturePhase.phaseCount(),
                    worldTexturePhase.maximumUOffset(),
                    baked.smoothedNormalCornerCount(),
                    baked.repairedNormalCount(),
                    baked.repairedUvFaceCount(),
                    baked.degenerateFaceCount(),
                    baked.estimatedVertexBytes(),
                    Integer.toHexString(System.identityHashCode(baked.mesh())),
                    modelCount
            );

            ObjMeshData.Bounds bounds = baked.transformedBounds();
            if (bounds.minX() < 0.0F || bounds.minY() < 0.0F || bounds.minZ() < 0.0F
                    || bounds.maxX() > 1.0F || bounds.maxY() > 1.0F || bounds.maxZ() > 1.0F) {
                Daedalon.LOGGER.debug(
                        "[Daedalon OBJ Mesh] {} extends outside one block at {}; chunk/frustum culling follows the source block position",
                        sourceData.objId(),
                        bounds
                );
            }
            return baked;
        }

        private ObjGuiIconTemplate guiIconTemplate() {
            return guiIconTemplate;
        }

        private ResolvedMaterials resolveMaterials(ObjMeshBakedModel.GeometryBakeResult geometry,
                                                   Function<SpriteIdentifier, Sprite> textureGetter,
                                                   Identifier variantTextureId,
                                                   Sprite variantSprite) {
            List<Sprite> sprites = new ArrayList<>(geometry.materialNames().size());
            List<Identifier> textureIds = new ArrayList<>(geometry.materialNames().size());
            List<Boolean> worldPhaseMaterials = new ArrayList<>(geometry.materialNames().size());
            for (String materialName : geometry.materialNames()) {
                Identifier textureId = materialTexture(materialName);
                Identifier resolvedTextureId = textureId == null ? variantTextureId : textureId;
                Sprite sprite = textureId == null
                        ? variantSprite
                        : textureGetter.apply(new SpriteIdentifier(
                                PlayerScreenHandler.BLOCK_ATLAS_TEXTURE,
                                textureId
                        ));
                sprites.add(sprite);
                textureIds.add(resolvedTextureId);
                // Only the variant sheet is guaranteed to follow the definition's tile layout.
                // Dedicated material sprites such as Diota's 16x16 bronze handles remain fixed.
                worldPhaseMaterials.add(textureId == null);
            }
            return new ResolvedMaterials(sprites, textureIds, worldPhaseMaterials);
        }

        private Identifier materialTexture(String materialName) {
            if (materialName.isBlank() || materialName.equals("none")) {
                return null;
            }
            Identifier textureId = definition.materialOverrides().get(materialName);
            return textureId == null ? materialTextures.get(materialName) : textureId;
        }

        private static String milliseconds(long nanos) {
            return String.format(Locale.ROOT, "%.3f", nanos / 1_000_000.0D);
        }
    }

    private static final class PreparedModel {
        private final Identifier displayModel;
        private final MeshVariant variant;
        private final SharedGeometry geometry;
        private final ObjMeshUnbakedModel unbakedModel;
        private ObjMeshBakedModel bakedModel;

        private PreparedModel(Identifier displayModel, MeshVariant variant, SharedGeometry geometry) {
            this.displayModel = displayModel;
            this.variant = variant;
            this.geometry = geometry;
            this.unbakedModel = new ObjMeshUnbakedModel(this);
        }

        private synchronized BakedModel bake(Baker baker,
                                             Function<SpriteIdentifier, Sprite> textureGetter,
                                             ModelBakeSettings settings) {
            if (bakedModel != null) {
                return bakedModel;
            }

            BakedModel metadataModel = Objects.requireNonNull(
                    baker.bake(displayModel, settings),
                    "Missing display metadata model " + displayModel
            );
            Sprite materialSprite = textureGetter.apply(new SpriteIdentifier(
                    PlayerScreenHandler.BLOCK_ATLAS_TEXTURE,
                    variant.textureId()
            ));
            Sprite particleSprite = textureGetter.apply(new SpriteIdentifier(
                    PlayerScreenHandler.BLOCK_ATLAS_TEXTURE,
                    variant.particleTextureId()
            ));
            Block waterBlock = ModBlocks.blocksById().get(id(variant.sourceBlockId()));
            boolean containsWater = variant.containedWater()
                    || waterBlock instanceof FountainBasinBlock;
            Sprite containedWaterSprite = containsWater
                    ? textureGetter.apply(new SpriteIdentifier(
                            PlayerScreenHandler.BLOCK_ATLAS_TEXTURE,
                            WATER_STILL_TEXTURE
                    ))
                    : null;
            ObjMeshBakedModel.GeometryBakeResult bakedGeometry = geometry.bake(metadataModel);
            ResolvedMaterials materials = geometry.resolveMaterials(
                    bakedGeometry,
                    textureGetter,
                    variant.textureId(),
                    materialSprite
            );
            bakedModel = ObjMeshBakedModel.materialize(
                    bakedGeometry,
                    geometry.itemMesh,
                    metadataModel,
                    particleSprite,
                    materials.sprites(),
                    materials.worldPhaseMaterials(),
                    variant.worldTexturePhase(),
                    geometry.guiIconTemplate(),
                    materials.textureIds(),
                    variant.modelId(),
                    containedWaterSprite
            );
            return bakedModel;
        }
    }

    private record ResolvedMaterials(
            List<Sprite> sprites,
            List<Identifier> textureIds,
            List<Boolean> worldPhaseMaterials
    ) {
        private ResolvedMaterials {
            sprites = List.copyOf(sprites);
            textureIds = List.copyOf(textureIds);
            worldPhaseMaterials = List.copyOf(worldPhaseMaterials);
        }
    }

    private static final class ObjMeshUnbakedModel implements UnbakedModel {
        private final PreparedModel prepared;

        private ObjMeshUnbakedModel(PreparedModel prepared) {
            this.prepared = prepared;
        }

        @Override
        public Collection<Identifier> getModelDependencies() {
            return List.of(prepared.displayModel);
        }

        @Override
        public void setParents(Function<Identifier, UnbakedModel> modelLoader) {
            // The display model supplies item transforms and is baked through Baker.
        }

        @Override
        public BakedModel bake(Baker baker,
                               Function<SpriteIdentifier, Sprite> textureGetter,
                               ModelBakeSettings rotationContainer,
                               Identifier modelId) {
            return prepared.bake(baker, textureGetter, rotationContainer);
        }
    }
}
