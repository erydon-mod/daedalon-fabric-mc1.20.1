package com.oliver.daedalon.client.model.obj;

import com.oliver.daedalon.Daedalon;
import com.oliver.daedalon.block.FinialBlock;
import com.oliver.daedalon.block.FixedDecorBlock;
import com.oliver.daedalon.block.TwoSizeDecorBlock;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.renderer.v1.Renderer;
import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.fabricmc.fabric.api.renderer.v1.mesh.MeshBuilder;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.registry.Registries;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Opt-in assertions against actual loaded finial geometry, atlas and icon workers. */
public final class FinialRebuildClientChecks {
    private static final List<String> FINISHES = List.of("aganite", "bronze", "silver");
    private FinialRebuildClientChecks() { }

    public static void register() {
        boolean[] completed = {false};
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (completed[0] || client.getOverlay() != null) return;
            completed[0] = true;
            run(client);
        });
    }

    private static void run(MinecraftClient client) {
        Map<String, Geometry> sharedGeometry = new HashMap<>();
        List<FixedDecorBlock.Style> styles = java.util.Arrays.stream(FixedDecorBlock.Style.values())
                .filter(FixedDecorBlock.Style::isFinial).toList();
        require(styles.size() == 5, "Expected the five existing finial styles");
        int cases = 0;
        for (String finish : FINISHES) for (var style : styles) {
            Identifier id = new Identifier(Daedalon.MOD_ID, finish + "_" + style.idSuffix());
            var block = Registries.BLOCK.get(id);
            require(block instanceof FinialBlock, "Missing finial: " + id);
            for (var size : TwoSizeDecorBlock.Size.values()) {
                BlockState state = block.getDefaultState().with(TwoSizeDecorBlock.SIZE, size);
                var resolved = ObjMeshBakedModel.unwrapAttachedModel(client.getBlockRenderManager().getModel(state));
                require(resolved instanceof ObjMeshBakedModel, "Finial has no loaded OBJ model: " + state);
                var model = (ObjMeshBakedModel) resolved;
                Capture capture = new Capture(client, model);
                // Default support NONE uses only saved fitting data, with no world query.
                // This probes the real placed-model/Fabric pipeline, not a live world scene.
                model.emitBlockQuads(null, state, BlockPos.ORIGIN, () -> Random.create(0), capture.context);
                capture.flush();
                require(capture.transforms.isEmpty(), "Unbalanced finial render transforms");
                double height = size == TwoSizeDecorBlock.Size.SMALL ? 1 : 2;
                require(capture.quads > 0, "Finial emitted no geometry: " + state);
                close(0, capture.min[1], "Finial foot is not flush: " + state);
                close(height, capture.max[1], "Incorrect finial height: " + state);
                close(style.width() * height, capture.max[0] - capture.min[0], "Incorrect finial width: " + state);
                close(style.depth() * height, capture.max[2] - capture.min[2], "Incorrect finial depth: " + state);
                close(.5, (capture.min[0] + capture.max[0]) * .5, "Finial X centre moved");
                close(.5, (capture.min[2] + capture.max[2]) * .5, "Finial Z centre moved");
                Geometry actual = new Geometry(capture.quads, capture.geometryHash);
                Geometry first = sharedGeometry.putIfAbsent(style.name() + "/" + size, actual);
                require(first == null || first.equals(actual), "A material changed the shared finial geometry: " + id);
                cases++;
            }
        }
        int icons = checkIcons(client, styles);
        Daedalon.LOGGER.info("DAEDALON_FINIAL_REBUILD_CHECKS_OK: {} placed cases; 5 styles x stone/bronze/silver x 1m/2m, finite positions/normals/atlas UVs, exact footprints, shared geometry; icons={}", cases, icons);
    }

    private static int checkIcons(MinecraftClient client, List<FixedDecorBlock.Style> styles) {
        try {
            Path output = reportDirectory(client);
            Files.createDirectories(output);
            Class<?> epochType = Class.forName(ObjGuiIconCache.class.getName() + "$ResourceEpoch");
            var epochConstructor = epochType.getDeclaredConstructor(ResourceManager.class);
            epochConstructor.setAccessible(true);
            Object epoch = epochConstructor.newInstance(client.getResourceManager());
            var generate = ObjGuiIconCache.class.getDeclaredMethod("generateCell", ObjMeshBakedModel.class, epochType);
            generate.setAccessible(true);
            List<BufferedImage> images = new ArrayList<>();
            for (String finish : FINISHES) for (var style : styles) {
                String name = finish + "_" + style.idSuffix();
                var item = Registries.ITEM.get(new Identifier(Daedalon.MOD_ID, name));
                var resolved = ObjMeshBakedModel.unwrapAttachedModel(client.getItemRenderer()
                        .getModel(item.getDefaultStack(), null, null, 0));
                require(resolved instanceof ObjMeshBakedModel, "Missing finial inventory OBJ: " + name);
                var model = (ObjMeshBakedModel) resolved;
                require(model.guiIconTemplate() != null, "Finial icon has no geometry template: " + name);
                try (NativeImage icon = (NativeImage) generate.invoke(null, model, epoch)) {
                    int covered = 0, exterior = 0;
                    BufferedImage image = new BufferedImage(icon.getWidth(), icon.getHeight(), BufferedImage.TYPE_INT_ARGB);
                    for (int y = 0; y < icon.getHeight(); y++) for (int x = 0; x < icon.getWidth(); x++) {
                        int abgr = icon.getColor(x, y), alpha = abgr >>> 24;
                        image.setRGB(x, y, (abgr & 0xFF00FF00) | (abgr >>> 16 & 0xFF) | ((abgr & 0xFF) << 16));
                        if (x == 0 || y == 0 || x == icon.getWidth() - 1 || y == icon.getHeight() - 1)
                            require(alpha == 0, "Finial icon gutter is not transparent: " + name);
                        else if (alpha == 0) exterior++;
                        else covered++;
                    }
                    require(covered > 0 && exterior > 0, "Finial icon is empty or a flat material square: " + name);
                    icon.writeTo(output.resolve(name + ".png"));
                    images.add(image);
                }
            }
            writeContactSheet(images, styles, output.resolve("finial-icons-comparison.png"));
            Daedalon.LOGGER.info("DAEDALON_FINIAL_ICONS_OK: {} actual worker icons; transparent silhouettes; files={}", images.size(), output);
            return images.size();
        } catch (InvocationTargetException exception) {
            throw new AssertionError("Finial icon worker failed", exception.getCause());
        } catch (ReflectiveOperationException | IOException exception) {
            throw new IllegalStateException("Unable to verify finial inventory icons", exception);
        }
    }

    private static Path reportDirectory(MinecraftClient client) {
        Path ancestor = client.runDirectory.toPath().toAbsolutePath().normalize();
        for (int depth = 0; ancestor != null && depth < 6; depth++, ancestor = ancestor.getParent()) {
            if (Files.isRegularFile(ancestor.resolve("build.gradle"))
                    && Files.isDirectory(ancestor.resolve("src/main/resources/assets/daedalon")))
                return ancestor.resolve("build/reports/finial-rebuild/client");
        }
        throw new IllegalStateException("The opt-in finial probe needs an isolated client directory inside the source project");
    }

    private static void writeContactSheet(List<BufferedImage> icons, List<FixedDecorBlock.Style> styles, Path output) throws IOException {
        int cellWidth = 240, cellHeight = 270, heading = 36;
        BufferedImage sheet = new BufferedImage(cellWidth * styles.size(), heading + cellHeight * FINISHES.size(), BufferedImage.TYPE_INT_ARGB);
        var graphics = sheet.createGraphics();
        try {
            graphics.setColor(new Color(245, 245, 242));
            graphics.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
            graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 18));
            graphics.setColor(new Color(35, 35, 35));
            for (int column = 0; column < styles.size(); column++)
                graphics.drawString(styles.get(column).displayName().replace(" Finial", ""), column * cellWidth + 65, 25);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            for (int index = 0; index < icons.size(); index++) {
                int x = index % styles.size() * cellWidth + 10, y = heading + index / styles.size() * cellHeight;
                for (int row = 0; row < 230; row += 10) for (int column = 0; column < 220; column += 10) {
                    graphics.setColor(((column + row) / 10 & 1) == 0 ? new Color(220, 220, 218) : new Color(238, 238, 235));
                    graphics.fillRect(x + column, y + row, 10, 10);
                }
                graphics.drawImage(icons.get(index), x, y, 220, 230, null);
                graphics.setColor(new Color(35, 35, 35));
                graphics.drawString(FINISHES.get(index / styles.size()), x + 65, y + 255);
            }
        } finally { graphics.dispose(); }
        require(ImageIO.write(sheet, "png", output.toFile()), "No PNG writer for finial icon comparison");
    }

    private static void close(double expected, double actual, String message) {
        require(Double.isFinite(actual) && Math.abs(expected - actual) <= 5e-6,
                message + ": expected=" + expected + " actual=" + actual);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private record Geometry(int quads, long signature) { }

    /** Replay real renderer meshes through their transform stack without drawing a world. */
    private static final class Capture {
        final ArrayDeque<RenderContext.QuadTransform> transforms = new ArrayDeque<>();
        final List<Sprite> sprites;
        final Renderer renderer = Objects.requireNonNull(RendererAccess.INSTANCE.getRenderer());
        MeshBuilder pending = renderer.meshBuilder();
        final QuadEmitter raw = renderer.meshBuilder().getEmitter();
        final double[] min = {Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY};
        final double[] max = {Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY};
        int quads;
        long geometryHash = 0xcbf29ce484222325L;
        final RenderContext context = (RenderContext) Proxy.newProxyInstance(RenderContext.class.getClassLoader(),
                new Class<?>[]{RenderContext.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getEmitter" -> pending.getEmitter();
                    case "pushTransform" -> { flush(); transforms.push((RenderContext.QuadTransform) args[0]); yield null; }
                    case "popTransform" -> { flush(); transforms.pop(); yield null; }
                    default -> throw new AssertionError("Unexpected finial render operation: " + method.getName());
                });

        Capture(MinecraftClient client, ObjMeshBakedModel model) {
            var atlas = client.getSpriteAtlas(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE);
            sprites = model.materialTextureIds().stream().map(atlas).toList();
        }

        void flush() {
            var mesh = pending.build();
            pending = renderer.meshBuilder();
            mesh.forEach(quad -> {
                raw.copyFrom(quad);
                for (var transform : transforms) if (!transform.transform(raw)) return;
                inspect();
            });
        }

        private void inspect() {
            int material = ShaderTerrainNormalBridge.decodeObjMaterialTag(raw.tag());
            require(material >= 0 && material < sprites.size(), "Finial lost its owned material tag");
            Sprite sprite = sprites.get(material);
            require(!raw.material().emissive(), "An ordinary finial became emissive");
            for (int vertex = 0; vertex < 4; vertex++) {
                float[] xyz = {raw.x(vertex), raw.y(vertex), raw.z(vertex)};
                for (int axis = 0; axis < 3; axis++) {
                    require(Float.isFinite(xyz[axis]), "Nonfinite finial position");
                    min[axis] = Math.min(min[axis], xyz[axis]); max[axis] = Math.max(max[axis], xyz[axis]);
                    hash(xyz[axis]);
                }
                require(raw.hasNormal(vertex), "Prepared finial corner normal is missing");
                float nx = raw.normalX(vertex), ny = raw.normalY(vertex), nz = raw.normalZ(vertex);
                double length = nx * nx + ny * ny + nz * nz;
                // Fabric packs each component into a signed byte, and the pose
                // pass packs it again. Allow that quantization, not missing normals.
                require(Double.isFinite(length) && Math.abs(Math.sqrt(length) - 1) < .028,
                        "Invalid prepared finial corner normal");
                hash(nx); hash(ny); hash(nz);
                require(Float.isFinite(raw.u(vertex)) && Float.isFinite(raw.v(vertex))
                                && raw.u(vertex) >= sprite.getMinU() - 1e-6F && raw.u(vertex) <= sprite.getMaxU() + 1e-6F
                                && raw.v(vertex) >= sprite.getMinV() - 1e-6F && raw.v(vertex) <= sprite.getMaxV() + 1e-6F,
                        "Finial UV left its selected atlas sprite");
            }
            quads++;
        }

        private void hash(float value) {
            geometryHash = (geometryHash ^ Float.floatToIntBits(value)) * 0x100000001b3L;
        }
    }
}
