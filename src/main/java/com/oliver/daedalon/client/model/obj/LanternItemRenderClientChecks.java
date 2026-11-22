package com.oliver.daedalon.client.model.obj;

import com.oliver.daedalon.Daedalon;
import com.oliver.daedalon.block.FriezeBlock;
import com.oliver.daedalon.block.LanternBlock;
import com.oliver.daedalon.registry.ModBlocks;
import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.fabricmc.fabric.api.renderer.v1.mesh.MeshBuilder;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.model.ForwardingBakedModel;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Opt-in real ItemRenderer interception and loaded component-routing checks. */
final class LanternItemRenderClientChecks {
    private LanternItemRenderClientChecks() { }

    static void run(MinecraftClient client) {
        Constructor<?> continuity = continuityConstructor();
        int itemCases = 0;
        ObjMeshBakedModel cachedModel = null;
        ItemStack cachedStack = null;
        BakedModel cachedWrapped = null;
        for (String finish : List.of("aganite", "bronze", "silver")) {
            for (var style : LanternBlock.Style.values()) {
                var item = Registries.ITEM.get(new Identifier(Daedalon.MOD_ID,
                        finish + "_" + style.id() + "_lantern"));
                ItemStack stack = item.getDefaultStack();
                BakedModel loaded = client.getItemRenderer().getModel(stack, null, null, 0);
                ObjMeshBakedModel model = obj(loaded);
                require(model.guiIconTemplate() != null, "Inventory model lost its shared icon template");
                List<BakedModel> wrapped = new ArrayList<>(List.of(model, new Layer(new Layer(model))));
                if (continuity != null) wrapped.add(continuityWrapper(continuity, model));
                for (BakedModel candidate : wrapped) {
                    if (candidate != model) {
                        require(!(candidate instanceof ObjMeshBakedModel)
                                        && ObjMeshBakedModel.unwrapAttachedModel(candidate) == model,
                                "Wrapper fixture must exercise the former direct-instance check failure");
                    }
                    BakedModel originalChild = candidate instanceof ForwardingBakedModel layer
                            ? layer.getWrappedModel() : null;
                    for (var mode : new ModelTransformationMode[]{ModelTransformationMode.GUI,
                            ModelTransformationMode.GROUND, ModelTransformationMode.FIXED,
                            ModelTransformationMode.THIRD_PERSON_RIGHT_HAND,
                            ModelTransformationMode.FIRST_PERSON_RIGHT_HAND,
                            ModelTransformationMode.FIRST_PERSON_LEFT_HAND}) {
                        int expected = mode == ModelTransformationMode.GUI ? 4
                                : mode == ModelTransformationMode.FIRST_PERSON_RIGHT_HAND
                                || mode == ModelTransformationMode.FIRST_PERSON_LEFT_HAND ? 0 : 8;
                        // Before the colour worker uploads the icon, the bounded
                        // fallback is a readable two-sided material card (8 vertices).
                        draw(client, stack, candidate, mode, expected,
                                mode == ModelTransformationMode.GUI ? 8 : expected);
                        itemCases++;
                    }
                    if (candidate instanceof ForwardingBakedModel layer)
                        require(layer.getWrappedModel() == originalChild,
                                "Inventory lookup changed the world's installed model wrapper");
                }
                cachedModel = model;
                cachedStack = stack;
                cachedWrapped = wrapped.get(wrapped.size() - 1);
            }
        }
        awaitUploadedIcon(cachedModel);
        draw(client, cachedStack, cachedWrapped, ModelTransformationMode.GUI, 4);
        draw(client, cachedStack, cachedWrapped, ModelTransformationMode.GROUND, 8);

        // Axiom uses its material-independent assembly projection, independently
        // of inventory wrappers and the newly populated GUI image atlas.
        var assembly = new LanternPreview(LanternBlock.Mount.HANGING, LanternBlock.Size.SMALL,
                Direction.SOUTH, 1, true, true);
        int previewBefore = cachedModel.createAxiomPreviewTemplate(assembly).sourceQuadCount();
        draw(client, cachedStack, cachedWrapped, ModelTransformationMode.GUI, 4);
        require(previewBefore == cachedModel.createAxiomPreviewTemplate(assembly).sourceQuadCount(),
                "Flat inventory rendering changed the Axiom assembly geometry");
        int chains = checkChainRouting(client);
        int friezes = checkFriezeRouting(client);
        Daedalon.LOGGER.info("DAEDALON_ITEM_RENDER_INTERCEPTION_OK: {} actual ItemRenderer cases; "
                        + "ContinuityEmissive={} cached=2 GUIcold=4or8vertices GUIcached=4vertices "
                        + "other=8vertices firstPerson=0 "
                        + "worldWrappers=preserved AxiomAssembly=unchanged chainCases={} friezeCases={}",
                itemCases, continuity != null, chains, friezes);
    }

    private static void draw(MinecraftClient client, ItemStack stack, BakedModel model,
                             ModelTransformationMode mode, int expected) {
        draw(client, stack, model, mode, expected, expected);
    }

    private static void draw(MinecraftClient client, ItemStack stack, BakedModel model,
                             ModelTransformationMode mode, int minimum, int maximum) {
        VertexCounter counter = new VertexCounter();
        // This invokes the actual mixed-in vanilla method, as REI's item entry
        // renderer does. Direct calls to tryRender would not prove interception.
        client.getItemRenderer().renderItem(stack, mode, false, new MatrixStack(), counter,
                LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV, model);
        require(counter.vertices == minimum || counter.vertices == maximum,
                "ItemRenderer bypassed flat icon interception: " + mode + " vertices=" + counter.vertices);
    }

    private static void awaitUploadedIcon(ObjMeshBakedModel model) {
        try {
            Field singleton = ObjGuiIconCache.class.getDeclaredField("INSTANCE");
            Field cellsField = ObjGuiIconCache.class.getDeclaredField("cells");
            Method upload = ObjGuiIconCache.class.getDeclaredMethod("uploadReadyIcons");
            singleton.setAccessible(true);
            cellsField.setAccessible(true);
            upload.setAccessible(true);
            Object cache = singleton.get(null);
            long deadline = System.nanoTime() + 10_000_000_000L;
            while (System.nanoTime() < deadline) {
                upload.invoke(cache);
                if (((Map<?, ?>) cellsField.get(cache)).containsKey(model)) return;
                Thread.sleep(5);
            }
            throw new AssertionError("Actual wrapped item requests never produced an uploaded flat icon");
        } catch (ReflectiveOperationException | InterruptedException failure) {
            throw new AssertionError("Unable to verify the uploaded GUI icon branch", failure);
        }
    }

    private static int checkChainRouting(MinecraftClient client) {
        int cases = 0;
        for (var size : LanternBlock.Size.values()) for (boolean canopy : new boolean[]{false, true}) {
            var state = ModBlocks.lanternChain().getDefaultState().with(LanternBlock.SIZE, size)
                    .with(LanternBlock.CANOPY, canopy);
            ObjMeshBakedModel model = obj(client.getBlockRenderManager().getModel(state));
            require(field(model, "lanternParts") != null, "Loaded chain lost its lantern component routing");
            MeshCapture capture = new MeshCapture();
            model.emitBlockQuads(null, state, BlockPos.ORIGIN, () -> Random.create(0), capture.context);
            capture.flush();
            // Six source hardware variants contain 86,384 faces. A chain cell
            // must select only its sized full-chain and optional canopy.
            require(capture.quads > 10_000 && capture.quads < 50_000,
                    "Chain emitted the complete hardware source or no selected link: " + capture.quads);
            cases++;
        }
        return cases;
    }

    private static int checkFriezeRouting(MinecraftClient client) {
        BlockRenderView empty = (BlockRenderView) Proxy.newProxyInstance(BlockRenderView.class.getClassLoader(),
                new Class<?>[]{BlockRenderView.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getBlockState" -> Blocks.AIR.getDefaultState();
                    case "getBlockEntity" -> null;
                    case "getFluidState" -> Blocks.AIR.getDefaultState().getFluidState();
                    case "getHeight" -> 384;
                    case "getBottomY" -> -64;
                    default -> throw new AssertionError("Unexpected frieze snapshot read: " + method.getName());
                });
        int cases = 0;
        for (var style : FriezeBlock.Style.values()) {
            var block = Registries.BLOCK.get(new Identifier(Daedalon.MOD_ID,
                    "striatus_aged_" + style.id() + "_frieze"));
            require(block instanceof FriezeBlock, "Missing actual aged frieze family: " + style);
            for (var join : FriezeBlock.Join.values()) {
                var state = block.getDefaultState().with(FriezeBlock.JOIN, join)
                        .with(FriezeBlock.MANUAL_CORNER, true);
                ObjMeshBakedModel model = obj(client.getBlockRenderManager().getModel(state));
                require(field(model, "friezeParts") != null,
                        "Loaded frieze lost selected-component routing: " + style);
                MeshCapture capture = new MeshCapture();
                model.emitBlockQuads(empty, state, new BlockPos(4880, 96, 4848), () -> Random.create(0), capture.context);
                capture.flush();
                require(capture.quads > 0 && capture.quads < 50_000,
                        "Frieze emitted every saved shape instead of selected components: " + style + "/" + join);
                cases++;
            }
        }
        return cases;
    }

    private static ObjMeshBakedModel obj(BakedModel model) {
        var resolved = ObjMeshBakedModel.unwrapAttachedModel(model);
        require(resolved instanceof ObjMeshBakedModel, "Loaded model is missing its shared OBJ geometry");
        return (ObjMeshBakedModel) resolved;
    }

    private static Object field(Object model, String name) {
        try {
            Field field = ObjMeshBakedModel.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(model);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Unable to inspect the actual loaded component routing", failure);
        }
    }

    private static Constructor<?> continuityConstructor() {
        try {
            return Class.forName("me.pepperbell.continuity.client.model.EmissiveBakedModel")
                    .getConstructor(BakedModel.class);
        } catch (ClassNotFoundException absent) {
            return null;
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Unable to use the installed Continuity model wrapper", failure);
        }
    }

    private static BakedModel continuityWrapper(Constructor<?> constructor, BakedModel model) {
        try {
            return (BakedModel) constructor.newInstance(model);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Unable to construct the actual Continuity emissive wrapper", failure);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class Layer extends ForwardingBakedModel {
        Layer(BakedModel child) { wrapped = child; }
    }

    private static final class VertexCounter implements VertexConsumer, VertexConsumerProvider {
        int vertices;
        @Override public VertexConsumer getBuffer(RenderLayer layer) { return this; }
        @Override public VertexConsumer vertex(double x, double y, double z) { return this; }
        @Override public VertexConsumer color(int r, int g, int b, int a) { return this; }
        @Override public VertexConsumer texture(float u, float v) { return this; }
        @Override public VertexConsumer overlay(int u, int v) { return this; }
        @Override public VertexConsumer light(int u, int v) { return this; }
        @Override public VertexConsumer normal(float x, float y, float z) { return this; }
        @Override public void next() {
            if (++vertices > 8) throw new AssertionError("A wrapped inventory item emitted full OBJ geometry");
        }
        @Override public void fixedColor(int r, int g, int b, int a) { }
        @Override public void unfixColor() { }
    }

    private static final class MeshCapture {
        final ArrayDeque<RenderContext.QuadTransform> transforms = new ArrayDeque<>();
        MeshBuilder pending = Objects.requireNonNull(RendererAccess.INSTANCE.getRenderer()).meshBuilder();
        final QuadEmitter scratch = RendererAccess.INSTANCE.getRenderer().meshBuilder().getEmitter();
        int quads;
        final RenderContext context = (RenderContext) Proxy.newProxyInstance(RenderContext.class.getClassLoader(),
                new Class<?>[]{RenderContext.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getEmitter" -> pending.getEmitter();
                    case "pushTransform" -> { flush(); transforms.push((RenderContext.QuadTransform) args[0]); yield null; }
                    case "popTransform" -> { flush(); transforms.pop(); yield null; }
                    default -> throw new AssertionError("Unexpected mesh capture operation: " + method.getName());
                });
        void flush() {
            var mesh = pending.build();
            pending = RendererAccess.INSTANCE.getRenderer().meshBuilder();
            mesh.forEach(quad -> {
                scratch.copyFrom(quad);
                for (var transform : transforms) if (!transform.transform(scratch)) return;
                if (++quads > 50_000) throw new AssertionError("A selected component emitted the complete multi-shape source");
            });
        }
    }
}
