package com.oliver.daedalon.client.model.obj;

import com.oliver.daedalon.Daedalon;
import com.oliver.daedalon.block.LanternBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.registry.Registries;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Actual icon-worker raster checks, invoked only by the opt-in client launch probe. */
final class LanternIconClientChecks {
    private LanternIconClientChecks() { }

    static void run(MinecraftClient client) {
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
            int cases = 0;
            for (String finish : List.of("aganite", "bronze", "silver")) {
                for (var style : LanternBlock.Style.values()) {
                    String name = finish + "_" + style.id() + "_lantern";
                    var item = Registries.ITEM.get(new Identifier(Daedalon.MOD_ID, name));
                    var resolved = ObjMeshBakedModel.unwrapAttachedModel(client.getItemRenderer()
                            .getModel(item.getDefaultStack(), null, null, 0));
                    require(resolved instanceof ObjMeshBakedModel, "Missing OBJ inventory model: " + name);
                    var model = (ObjMeshBakedModel) resolved;
                    var template = model.guiIconTemplate();
                    require(template != null, "Missing full-lantern icon template: " + name);
                    var textures = model.materialTextureIds();
                    int bronze = textures.lastIndexOf(new Identifier(Daedalon.MOD_ID, "block/bronze"));
                    int lead = textures.indexOf(LanternGlazing.LEAD_TEXTURE);
                    int glass = textures.indexOf(LanternGlazing.TEXTURE);
                    int flame = textures.indexOf(LanternFlame.TEXTURE);
                    int burnerSamples = 0, leadSamples = 0;
                    int sampleCount = template.resolution() * ObjGuiIconTemplate.SUPERSAMPLE;
                    sampleCount *= sampleCount;
                    for (int sample = 0; sample < sampleCount; sample++) if (template.isCovered(sample)) {
                        int material = template.materialAt(sample);
                        require(material != glass && material != flame,
                                "Pane/flame cards must not mask the burner in inventory: " + name);
                        if (material == bronze) burnerSamples++;
                        if (material == lead) leadSamples++;
                    }
                    require(bronze >= 0 && burnerSamples > 0, "Inventory burner is hidden: " + name);
                    require(lead >= 0 && leadSamples > 0, "Inventory lead frame is missing: " + name);
                    try (NativeImage icon = (NativeImage) generate.invoke(null, model, epoch)) {
                        int covered = 0, exterior = 0;
                        BufferedImage image = new BufferedImage(icon.getWidth(), icon.getHeight(), BufferedImage.TYPE_INT_ARGB);
                        for (int y = 0; y < icon.getHeight(); y++) for (int x = 0; x < icon.getWidth(); x++) {
                            int abgr = icon.getColor(x, y);
                            int alpha = abgr >>> 24;
                            image.setRGB(x, y, (abgr & 0xFF00FF00) | (abgr >>> 16 & 0xFF) | ((abgr & 0xFF) << 16));
                            if (x == 0 || y == 0 || x == icon.getWidth() - 1 || y == icon.getHeight() - 1)
                                require(alpha == 0, "Icon gutter lost transparency: " + name);
                            else if (alpha == 0) exterior++;
                            else covered++;
                        }
                        require(covered > 0, "Actual icon worker returned an empty image: " + name);
                        require(exterior > 0, "Actual icon worker returned a solid material square: " + name);
                        icon.writeTo(output.resolve(name + ".png"));
                        images.add(image);
                    }
                    cases++;
                }
            }
            writeContactSheet(images, output.resolve("lantern-icons-comparison.png"));
            Daedalon.LOGGER.info("DAEDALON_LANTERN_ICONS_OK: {} actual worker icons; transparent silhouettes, visible burners and lead, source alpha preserved; files={}", cases, output);
        } catch (InvocationTargetException exception) {
            throw new AssertionError("Lantern icon worker failed", exception.getCause());
        } catch (ReflectiveOperationException | IOException exception) {
            throw new IllegalStateException("Unable to verify lantern inventory icons", exception);
        }
    }

    private static Path reportDirectory(MinecraftClient client) {
        Path ancestor = client.runDirectory.toPath().toAbsolutePath().normalize();
        for (int depth = 0; ancestor != null && depth < 6; depth++, ancestor = ancestor.getParent()) {
            if (Files.isRegularFile(ancestor.resolve("build.gradle"))
                    && Files.isDirectory(ancestor.resolve("src/main/resources/assets/daedalon")))
                return ancestor.resolve("build/reports/lantern-render-fixes/icons");
        }
        throw new IllegalStateException("The opt-in icon probe requires an isolated client directory inside the source project");
    }

    private static void writeContactSheet(List<BufferedImage> icons, Path output) throws IOException {
        int cellWidth = 300, cellHeight = 300, heading = 42;
        BufferedImage sheet = new BufferedImage(cellWidth * 3, heading + cellHeight * 3, BufferedImage.TYPE_INT_ARGB);
        var graphics = sheet.createGraphics();
        try {
            graphics.setColor(new Color(245, 245, 242));
            graphics.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
            graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 20));
            graphics.setColor(new Color(35, 35, 35));
            String[] styles = {"Georgian", "Gothic", "Byzantine"};
            for (int column = 0; column < 3; column++) graphics.drawString(styles[column], column * cellWidth + 84, 28);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            String[] finishes = {"Aganite", "Bronze", "Silver"};
            for (int index = 0; index < icons.size(); index++) {
                int x = index % 3 * cellWidth + 20, y = heading + index / 3 * cellHeight;
                for (int checkerY = 0; checkerY < 260; checkerY += 10)
                    for (int checkerX = 0; checkerX < 260; checkerX += 10) {
                        graphics.setColor(((checkerX + checkerY) / 10 & 1) == 0
                                ? new Color(220, 220, 218) : new Color(238, 238, 235));
                        graphics.fillRect(x + checkerX, y + checkerY, 10, 10);
                    }
                graphics.drawImage(icons.get(index), x, y, 260, 260, null);
                graphics.setColor(new Color(35, 35, 35));
                graphics.drawString(finishes[index / 3], x + 90, y + 285);
            }
        } finally { graphics.dispose(); }
        require(ImageIO.write(sheet, "png", output.toFile()), "No PNG writer for the icon comparison");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
