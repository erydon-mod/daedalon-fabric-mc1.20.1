package com.oliver.daedalon.registry;

import com.oliver.daedalon.block.ClassicalStatueBlock;
import com.oliver.daedalon.block.BustBlock;
import com.oliver.daedalon.block.CapitalBlock;
import com.oliver.daedalon.block.CorbelBlock;
import com.oliver.daedalon.block.ExedraBlock;
import com.oliver.daedalon.block.HedraBlock;
import com.oliver.daedalon.block.FacingDecorBlock;
import com.oliver.daedalon.block.FinialBlock;
import com.oliver.daedalon.block.FixedDecorBlock;
import com.oliver.daedalon.block.FountainBasinBlock;
import com.oliver.daedalon.block.FountainBasinPartBlock;
import com.oliver.daedalon.block.PlinthBlock;
import com.oliver.daedalon.block.SpartanStatueBlock;
import com.oliver.daedalon.block.SizedDecorBlock;
import com.oliver.daedalon.block.UrnBlock;
import com.oliver.daedalon.block.ZeusStatueBlock;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ModBlocks {
    public static final String MOD_ID = "daedalon";

    private static final int STONE_VARIANTS_PER_FAMILY = DecorMaterial.values().length * 2;
    private static final int BRONZE_VARIANTS_PER_SELECTED_FAMILY = 1;
    private static final int BRONZE_FAMILY_VARIANTS =
            STONE_VARIANTS_PER_FAMILY + BRONZE_VARIANTS_PER_SELECTED_FAMILY;
    private static final int FAMILY_COUNT =
            2 + UrnBlock.UrnStyle.values().length
                    + ClassicalStatueBlock.Style.values().length
                    + BustBlock.Style.values().length
                    + CorbelBlock.Style.values().length
                    + CapitalBlock.Style.values().length
                    + FixedDecorBlock.Style.values().length
                    + FountainBasinBlock.Style.values().length
                    + PlinthBlock.Style.values().length + 4;
    private static final int BRONZE_FAMILY_COUNT =
            2 + UrnBlock.UrnStyle.values().length
                    + ClassicalStatueBlock.Style.values().length
                    + BustBlock.Style.values().length + 1;
    private static final int EXPECTED_BLOCK_COUNT =
            STONE_VARIANTS_PER_FAMILY * FAMILY_COUNT + BRONZE_FAMILY_COUNT;

    private static final Map<Identifier, Block> BY_ID = new LinkedHashMap<>();
    private static final List<Block> SPARTAN_PROMACHOS_STATUES = new ArrayList<>();
    private static final List<Block> ZEUS_STATUES = new ArrayList<>();
    private static final Map<ClassicalStatueBlock.Style, List<Block>> CLASSICAL_STATUES_BY_STYLE =
            new EnumMap<>(ClassicalStatueBlock.Style.class);
    private static final Map<BustBlock.Style, List<Block>> BUSTS_BY_STYLE =
            new EnumMap<>(BustBlock.Style.class);
    private static final Map<UrnBlock.UrnStyle, List<Block>> URNS_BY_STYLE =
            new EnumMap<>(UrnBlock.UrnStyle.class);
    private static final Map<CorbelBlock.Style, List<Block>> CORBELS_BY_STYLE =
            new EnumMap<>(CorbelBlock.Style.class);
    private static final Map<CapitalBlock.Style, List<Block>> CAPITALS_BY_STYLE =
            new EnumMap<>(CapitalBlock.Style.class);
    private static final Map<FixedDecorBlock.Style, List<Block>> FIXED_DECOR_BY_STYLE =
            new EnumMap<>(FixedDecorBlock.Style.class);
    private static final Map<FountainBasinBlock.Style, List<Block>> FOUNTAIN_BASINS_BY_STYLE =
            new EnumMap<>(FountainBasinBlock.Style.class);
    private static final Map<PlinthBlock.Style, List<Block>> PLINTHS_BY_STYLE =
            new EnumMap<>(PlinthBlock.Style.class);
    private static final List<Block> KRENE_FOUNTAINS = new ArrayList<>();
    private static final List<Block> OBELISKOS_MONUMENTS = new ArrayList<>();
    private static FountainBasinPartBlock fountainBasinPart;
    private static boolean registered;

    private ModBlocks() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;

        registerInternalBlocks();
        registerSpartanPromachosStatues();
        registerZeusStatues();
        for (ClassicalStatueBlock.Style style : ClassicalStatueBlock.Style.values()) {
            registerClassicalStatues(style);
        }
        for (BustBlock.Style style : BustBlock.Style.values()) {
            registerBusts(style);
        }
        for (UrnBlock.UrnStyle style : UrnBlock.UrnStyle.values()) {
            registerUrns(style);
        }
        for (CorbelBlock.Style style : CorbelBlock.Style.values()) {
            registerCorbels(style);
        }
        for (CapitalBlock.Style style : CapitalBlock.Style.values()) {
            registerCapitals(style);
        }
        for (FixedDecorBlock.Style style : FixedDecorBlock.Style.values()) {
            registerFixedDecor(style);
        }
        for (FountainBasinBlock.Style style : FountainBasinBlock.Style.values()) {
            registerFountainBasins(style);
        }
        for (PlinthBlock.Style style : PlinthBlock.Style.values()) {
            registerPlinths(style);
        }
        registerKreneFountains();
        registerObeliskosMonuments();
        registerExedras();
        registerHedras();

        if (BY_ID.size() != EXPECTED_BLOCK_COUNT
                || ModItems.blockItems().size() != EXPECTED_BLOCK_COUNT
                || SPARTAN_PROMACHOS_STATUES.size() != BRONZE_FAMILY_VARIANTS
                || ZEUS_STATUES.size() != BRONZE_FAMILY_VARIANTS) {
            throw new IllegalStateException(
                    "Daedalon must register exactly 3986 decor blocks and items"
            );
        }
        for (UrnBlock.UrnStyle style : UrnBlock.UrnStyle.values()) {
            if (urns(style).size() != BRONZE_FAMILY_VARIANTS) {
                throw new IllegalStateException(
                        style.shapeId() + " urn must register exactly 55 variants"
                );
            }
        }
        for (ClassicalStatueBlock.Style style : ClassicalStatueBlock.Style.values()) {
            if (classicalStatues(style).size() != BRONZE_FAMILY_VARIANTS) {
                throw new IllegalStateException(
                        style.subjectId() + " statue must register exactly 55 variants"
                );
            }
        }
        for (BustBlock.Style style : BustBlock.Style.values()) {
            if (busts(style).size() != BRONZE_FAMILY_VARIANTS) {
                throw new IllegalStateException(
                        style.subjectId() + " bust must register exactly 55 variants"
                );
            }
        }
        for (CorbelBlock.Style style : CorbelBlock.Style.values()) {
            if (corbels(style).size() != STONE_VARIANTS_PER_FAMILY) {
                throw new IllegalStateException(
                        style.styleId() + " corbel must register exactly 54 variants"
                );
            }
        }
        for (CapitalBlock.Style style : CapitalBlock.Style.values()) {
            if (capitals(style).size() != STONE_VARIANTS_PER_FAMILY) {
                throw new IllegalStateException(
                        style.styleId() + " capital must register exactly 54 variants"
                );
            }
        }
        for (FixedDecorBlock.Style style : FixedDecorBlock.Style.values()) {
            if (fixedDecor(style).size() != STONE_VARIANTS_PER_FAMILY) {
                throw new IllegalStateException(style.idSuffix() + " must register exactly 54 variants");
            }
        }
        for (FountainBasinBlock.Style style : FountainBasinBlock.Style.values()) {
            if (fountainBasins(style).size() != STONE_VARIANTS_PER_FAMILY) {
                throw new IllegalStateException(style.idSuffix() + " must register exactly 54 variants");
            }
        }
        for (PlinthBlock.Style style : PlinthBlock.Style.values()) {
            if (plinths(style).size() != STONE_VARIANTS_PER_FAMILY) {
                throw new IllegalStateException(style.styleId() + " plinth must register exactly 54 variants");
            }
        }
        if (KRENE_FOUNTAINS.size() != STONE_VARIANTS_PER_FAMILY
                || OBELISKOS_MONUMENTS.size() != BRONZE_FAMILY_VARIANTS) {
            throw new IllegalStateException(
                    "Krene must register 54 variants and Obeliskos must register 55 variants"
            );
        }
    }

    public static List<Block> blocks() {
        return List.copyOf(BY_ID.values());
    }

    public static Map<Identifier, Block> blocksById() {
        return Collections.unmodifiableMap(BY_ID);
    }

    public static List<Block> spartanPromachosStatues() {
        return Collections.unmodifiableList(SPARTAN_PROMACHOS_STATUES);
    }

    public static List<Block> zeusStatues() {
        return Collections.unmodifiableList(ZEUS_STATUES);
    }

    public static List<Block> classicalStatues(ClassicalStatueBlock.Style style) {
        List<Block> statues = CLASSICAL_STATUES_BY_STYLE.get(style);
        return statues == null ? List.of() : Collections.unmodifiableList(statues);
    }

    public static List<Block> busts(BustBlock.Style style) {
        List<Block> busts = BUSTS_BY_STYLE.get(style);
        return busts == null ? List.of() : Collections.unmodifiableList(busts);
    }

    public static List<Block> urns(UrnBlock.UrnStyle style) {
        List<Block> urns = URNS_BY_STYLE.get(style);
        return urns == null ? List.of() : Collections.unmodifiableList(urns);
    }

    public static List<Block> corbels(CorbelBlock.Style style) {
        List<Block> corbels = CORBELS_BY_STYLE.get(style);
        return corbels == null ? List.of() : Collections.unmodifiableList(corbels);
    }

    public static List<Block> capitals(CapitalBlock.Style style) {
        List<Block> capitals = CAPITALS_BY_STYLE.get(style);
        return capitals == null ? List.of() : Collections.unmodifiableList(capitals);
    }

    public static List<Block> fixedDecor(FixedDecorBlock.Style style) {
        List<Block> blocks = FIXED_DECOR_BY_STYLE.get(style);
        return blocks == null ? List.of() : Collections.unmodifiableList(blocks);
    }

    public static List<Block> fountainBasins(FountainBasinBlock.Style style) {
        List<Block> blocks = FOUNTAIN_BASINS_BY_STYLE.get(style);
        return blocks == null ? List.of() : Collections.unmodifiableList(blocks);
    }

    public static FountainBasinPartBlock fountainBasinPart() {
        if (fountainBasinPart == null) {
            throw new IllegalStateException("ModBlocks.register() must run before placing a fountain basin");
        }
        return fountainBasinPart;
    }

    public static List<Block> plinths(PlinthBlock.Style style) {
        List<Block> blocks = PLINTHS_BY_STYLE.get(style);
        return blocks == null ? List.of() : Collections.unmodifiableList(blocks);
    }

    public static List<Block> koncheUrns() {
        return urns(UrnBlock.UrnStyle.KONCHE);
    }

    public static Block koncheIconBlock() {
        Block block = BY_ID.get(id("aganite_konche_urn"));
        if (block == null) {
            throw new IllegalStateException(
                    "ModBlocks.register() must run before ModItemGroups.register()"
            );
        }
        return block;
    }

    private static void registerInternalBlocks() {
        fountainBasinPart = Registry.register(
                Registries.BLOCK,
                id("fountain_basin_part"),
                new FountainBasinPartBlock(
                        AbstractBlock.Settings.create()
                                .mapColor(MapColor.IRON_GRAY)
                                .strength(-1.0F, 3_600_000.0F)
                                .dropsNothing()
                                .nonOpaque()
                                .dynamicBounds()
                                .pistonBehavior(PistonBehavior.BLOCK)
                )
        );
    }

    private static void registerSpartanPromachosStatues() {
        for (DecorMaterial material : DecorMaterial.values()) {
            registerSpartanPromachosStatue(material, false);
            registerSpartanPromachosStatue(material, true);
        }
        SPARTAN_PROMACHOS_STATUES.add(registerBlock(
                "bronze_spartan_promachos_statue",
                new SpartanStatueBlock(decorSettings())
        ));
    }

    private static void registerSpartanPromachosStatue(
            DecorMaterial material,
            boolean aged
    ) {
        String path = "statue_spartan_promachos_"
                + material.id()
                + (aged ? "_aged" : "");
        SPARTAN_PROMACHOS_STATUES.add(registerBlock(
                path,
                new SpartanStatueBlock(decorSettings())
        ));
    }

    private static void registerZeusStatues() {
        for (DecorMaterial material : DecorMaterial.values()) {
            registerZeusStatue(material, false);
            registerZeusStatue(material, true);
        }
        ZEUS_STATUES.add(registerBlock(
                "bronze_zeus_statue",
                new ZeusStatueBlock(decorSettings())
        ));
    }

    private static void registerZeusStatue(
            DecorMaterial material,
            boolean aged
    ) {
        String path = material.id()
                + (aged ? "_aged" : "")
                + "_zeus_statue";
        ZEUS_STATUES.add(registerBlock(
                path,
                new ZeusStatueBlock(decorSettings())
        ));
    }

    private static void registerClassicalStatues(ClassicalStatueBlock.Style style) {
        for (DecorMaterial material : DecorMaterial.values()) {
            registerClassicalStatue(material, false, style);
            registerClassicalStatue(material, true, style);
        }
        Block bronze = registerBlock(
                "bronze_" + style.subjectId() + "_statue",
                new ClassicalStatueBlock(decorSettings(), style)
        );
        CLASSICAL_STATUES_BY_STYLE
                .computeIfAbsent(style, ignored -> new ArrayList<>())
                .add(bronze);
    }

    private static void registerClassicalStatue(
            DecorMaterial material,
            boolean aged,
            ClassicalStatueBlock.Style style
    ) {
        String path = material.id()
                + (aged ? "_aged" : "")
                + "_"
                + style.subjectId()
                + "_statue";
        Block block = registerBlock(
                path,
                new ClassicalStatueBlock(decorSettings(), style)
        );
        CLASSICAL_STATUES_BY_STYLE
                .computeIfAbsent(style, ignored -> new ArrayList<>())
                .add(block);
    }

    private static void registerUrns(UrnBlock.UrnStyle style) {
        for (DecorMaterial material : DecorMaterial.values()) {
            registerUrn(material, false, style);
            registerUrn(material, true, style);
        }
        Block bronze = registerBlock(
                "bronze_" + style.shapeId() + "_urn",
                new UrnBlock(decorSettings(), style)
        );
        URNS_BY_STYLE.computeIfAbsent(style, ignored -> new ArrayList<>()).add(bronze);
    }

    private static void registerBusts(BustBlock.Style style) {
        for (DecorMaterial material : DecorMaterial.values()) {
            registerBust(material, false, style);
            registerBust(material, true, style);
        }
        Block bronze = registerBlock(
                "bronze_" + style.subjectId() + "_bust",
                new BustBlock(decorSettings(), style)
        );
        BUSTS_BY_STYLE.computeIfAbsent(style, ignored -> new ArrayList<>()).add(bronze);
    }

    private static void registerBust(
            DecorMaterial material,
            boolean aged,
            BustBlock.Style style
    ) {
        String path = material.id()
                + (aged ? "_aged" : "")
                + "_"
                + style.subjectId()
                + "_bust";
        Block block = registerBlock(
                path,
                new BustBlock(decorSettings(), style)
        );
        BUSTS_BY_STYLE.computeIfAbsent(style, ignored -> new ArrayList<>()).add(block);
    }

    private static void registerUrn(
            DecorMaterial material,
            boolean aged,
            UrnBlock.UrnStyle style
    ) {
        String path = material.id()
                + (aged ? "_aged" : "")
                + "_"
                + style.shapeId()
                + "_urn";
        Block block = registerBlock(
                path,
                new UrnBlock(decorSettings(), style)
        );
        URNS_BY_STYLE.computeIfAbsent(style, ignored -> new ArrayList<>()).add(block);
    }

    private static void registerCorbels(CorbelBlock.Style style) {
        for (DecorMaterial material : DecorMaterial.values()) {
            registerCorbel(material, false, style);
            registerCorbel(material, true, style);
        }
    }

    private static void registerCorbel(
            DecorMaterial material,
            boolean aged,
            CorbelBlock.Style style
    ) {
        String path = material.id()
                + (aged ? "_aged" : "")
                + "_"
                + style.styleId()
                + "_corbel";
        Block block = registerBlock(
                path,
                new CorbelBlock(decorSettings(), style)
        );
        CORBELS_BY_STYLE.computeIfAbsent(style, ignored -> new ArrayList<>()).add(block);
    }

    private static void registerCapitals(CapitalBlock.Style style) {
        for (DecorMaterial material : DecorMaterial.values()) {
            registerCapital(material, false, style);
            registerCapital(material, true, style);
        }
    }

    private static void registerCapital(
            DecorMaterial material,
            boolean aged,
            CapitalBlock.Style style
    ) {
        String path = material.id()
                + (aged ? "_aged" : "")
                + "_"
                + style.styleId()
                + "_capital";
        Block block = registerBlock(
                path,
                new CapitalBlock(decorSettings(), style)
        );
        CAPITALS_BY_STYLE.computeIfAbsent(style, ignored -> new ArrayList<>()).add(block);
    }

    private static void registerFixedDecor(FixedDecorBlock.Style style) {
        for (DecorMaterial material : DecorMaterial.values()) {
            for (boolean aged : new boolean[]{false, true}) {
                String path = material.id() + (aged ? "_aged" : "") + "_" + style.idSuffix();
                Block block = registerBlock(
                        path,
                        style.isFinial()
                                ? new FinialBlock(decorSettings(), style)
                                : new FixedDecorBlock(decorSettings(), style)
                );
                FIXED_DECOR_BY_STYLE.computeIfAbsent(style, ignored -> new ArrayList<>()).add(block);
            }
        }
    }

    private static void registerFountainBasins(FountainBasinBlock.Style style) {
        for (DecorMaterial material : DecorMaterial.values()) {
            for (boolean aged : new boolean[]{false, true}) {
                String path = material.id() + (aged ? "_aged" : "") + "_" + style.idSuffix();
                Block block = registerBlock(
                        path,
                        new FountainBasinBlock(decorSettings().dropsNothing(), style)
                );
                FOUNTAIN_BASINS_BY_STYLE.computeIfAbsent(style, ignored -> new ArrayList<>()).add(block);
            }
        }
    }

    private static void registerPlinths(PlinthBlock.Style style) {
        for (DecorMaterial material : DecorMaterial.values()) {
            for (boolean aged : new boolean[]{false, true}) {
                String path = material.id() + (aged ? "_aged" : "")
                        + "_" + style.styleId() + "_plinth";
                String fountainMaterialKey = material.id() + (aged ? "_aged" : "");
                Block block = registerBlock(
                        path,
                        new PlinthBlock(decorSettings(), style, fountainMaterialKey)
                );
                PLINTHS_BY_STYLE.computeIfAbsent(style, ignored -> new ArrayList<>()).add(block);
            }
        }
    }

    private static void registerHedras() {
        for (DecorMaterial material : DecorMaterial.values()) {
            for (boolean aged : new boolean[]{false, true}) {
                String path = material.id() + (aged ? "_aged" : "") + "_hedra";
                registerBlock(path, new HedraBlock(decorSettings()));
            }
        }
    }

    private static void registerExedras() {
        for (DecorMaterial material : DecorMaterial.values()) {
            for (boolean aged : new boolean[]{false, true}) {
                String path = material.id() + (aged ? "_aged" : "") + "_exedra";
                registerBlock(path, new ExedraBlock(decorSettings()));
            }
        }
    }

    private static void registerKreneFountains() {
        for (DecorMaterial material : DecorMaterial.values()) {
            for (boolean aged : new boolean[]{false, true}) {
                String path = material.id() + (aged ? "_aged" : "") + "_krene_fountain";
                KRENE_FOUNTAINS.add(registerBlock(path, new FacingDecorBlock(decorSettings())));
            }
        }
    }

    private static void registerObeliskosMonuments() {
        for (DecorMaterial material : DecorMaterial.values()) {
            for (boolean aged : new boolean[]{false, true}) {
                String path = material.id() + (aged ? "_aged" : "") + "_obeliskos_monument";
                OBELISKOS_MONUMENTS.add(registerBlock(path, new SizedDecorBlock(decorSettings())));
            }
        }
        OBELISKOS_MONUMENTS.add(registerBlock(
                "bronze_obeliskos_monument",
                new SizedDecorBlock(decorSettings())
        ));
    }

    static AbstractBlock.Settings decorSettings() {
        return AbstractBlock.Settings.create()
                .mapColor(MapColor.IRON_GRAY)
                .strength(1.5F, 6.0F)
                .requiresTool()
                .nonOpaque();
    }

    static Identifier id(String path) {
        return new Identifier(MOD_ID, path);
    }

    static Block registerBlock(String path, Block block) {
        Identifier id = id(path);
        if (BY_ID.containsKey(id)) {
            throw new IllegalStateException("Duplicate Daedalon block id: " + id);
        }

        Block registeredBlock = Registry.register(Registries.BLOCK, id, block);
        ModItems.registerBlockItem(id, registeredBlock);
        BY_ID.put(id, registeredBlock);
        return registeredBlock;
    }
}
