package com.oliver.daedalon.client.model.obj;

import com.oliver.daedalon.Daedalon;
import com.mojang.blaze3d.platform.TextureUtil;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.ResourceReloadListenerKeys;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;

/**
 * Shared flat-icon cache for high-detail OBJ items. A complete family mesh is
 * rasterized once into a shared texture-independent template; each material
 * variant then occupies one small dynamic-atlas cell and renders as one quad.
 * Unavailable OBJ icons use a flat material sprite rather than a full mesh.
 */
public final class ObjGuiIconCache {
    private static final float UV_EPSILON = 1.0E-5F;
    private static final float[] SRGB_TO_LINEAR = createSrgbToLinearTable();
    private static final int[] LINEAR_TO_SRGB = createLinearToSrgbTable();
    static final int ATLAS_PAGE_SIZE = 2048;
    static final int ICON_GUTTER = 1;
    static final int ICON_CELL_SIZE = ObjGuiIconTemplate.ICON_RESOLUTION + ICON_GUTTER * 2;
    static final int CELLS_PER_ROW = ATLAS_PAGE_SIZE / ICON_CELL_SIZE;
    static final int CELLS_PER_PAGE = CELLS_PER_ROW * CELLS_PER_ROW;
    // 225 cells/page: all 4,734 catalogue items fit in 22 pages. GPU storage is
    // allocated only as visited (352 MiB worst case), without full CPU page copies.
    static final int MAX_ATLAS_PAGES = 22;
    static final int MAX_CACHED_ICONS = CELLS_PER_PAGE * MAX_ATLAS_PAGES;
    static final int MAX_PENDING_ICONS = 512;
    static final int MAX_UPLOADS_PER_TICK = 16;
    static final long UPLOAD_BUDGET_NANOS = 2_000_000L;

    private static final Identifier RELOAD_ID =
            new Identifier(Daedalon.MOD_ID, "obj_gui_icon_cache");
    private static final ObjGuiIconCache INSTANCE = new ObjGuiIconCache();
    private static boolean registered;

    private final Map<ObjMeshBakedModel, IconCell> cells = new IdentityHashMap<>();
    private final Set<ObjMeshBakedModel> failedModels =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private final ObjGuiIconWorkQueue<ObjMeshBakedModel, NativeImage> work =
            new ObjGuiIconWorkQueue<>(Executors.newSingleThreadExecutor(task -> {
                Thread thread = new Thread(task, "Daedalon icon colour worker");
                thread.setDaemon(true);
                return thread;
            }), MAX_PENDING_ICONS);
    private final List<AtlasPage> pages = new ArrayList<>();
    private static final int MAX_PREVIEW_IMAGES = 128;
    private static final float[][] PREVIEW_QUAD = {
            {-.5F,-.5F,0,1},{.5F,-.5F,1,1},{.5F,.5F,1,0},{-.5F,.5F,0,0}
    };
    private final Map<ObjMeshBakedModel, PreviewImage> previewImages = new LinkedHashMap<>(128, .75F, true);
    private final Map<net.fabricmc.fabric.api.renderer.v1.mesh.Mesh, ObjGuiIconTemplate> previewTemplates = new LinkedHashMap<>(8, .75F, true);
    private final Set<ObjMeshBakedModel> failedPreviews = Collections.newSetFromMap(new IdentityHashMap<>());
    private long nextPreviewGeneration;
    private record PreviewImage(Identifier id, long usedAt) {}
    private ResourceEpoch resources;
    private int nextCellIndex;

    private ObjGuiIconCache() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ClientTickEvents.END_CLIENT_TICK.register(client -> INSTANCE.uploadReadyIcons());
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(
                new SimpleSynchronousResourceReloadListener() {
                    @Override
                    public Identifier getFabricId() {
                        return RELOAD_ID;
                    }

                    @Override
                    public Collection<Identifier> getFabricDependencies() {
                        return List.of(
                                ResourceReloadListenerKeys.TEXTURES,
                                ResourceReloadListenerKeys.MODELS
                        );
                    }

                    @Override
                    public void reload(ResourceManager manager) {
                        INSTANCE.reload(manager);
                    }
                }
        );
    }

    public static boolean tryRender(ItemStack stack,
                                    ModelTransformationMode renderMode,
                                    boolean leftHanded,
                                    MatrixStack matrices,
                                    VertexConsumerProvider vertexConsumers,
                                    int light,
                                    int overlay,
                                    BakedModel bakedModel) {
        if (!Daedalon.MOD_ID.equals(Registries.ITEM.getId(stack.getItem()).getNamespace())) {
            return false;
        }
        // Cancel before requesting an icon or emitting geometry, including the off hand.
        if (renderMode == ModelTransformationMode.FIRST_PERSON_LEFT_HAND
                || renderMode == ModelTransformationMode.FIRST_PERSON_RIGHT_HAND) {
            return true;
        }
        if (renderMode != ModelTransformationMode.GUI && bakedModel instanceof ObjMeshBakedModel objModel) {
            return renderWorldIcon(stack, renderMode, matrices, vertexConsumers, light, overlay, objModel);
        }
        if (renderMode != ModelTransformationMode.GUI
                || leftHanded
                || overlay != OverlayTexture.DEFAULT_UV
                || stack.hasGlint()
                || !(bakedModel instanceof ObjMeshBakedModel objModel)
                || objModel.guiIconTemplate() == null) {
            return bakedModel instanceof ObjMeshBakedModel objModel
                    && renderWorldIcon(stack, renderMode, matrices, vertexConsumers, light, overlay, objModel);
        }

        IconCell cell = INSTANCE.getOrCreate(objModel);
        if (cell == null) {
            return renderWorldIcon(stack, renderMode, matrices, vertexConsumers, light, overlay, objModel);
        }

        MatrixStack.Entry matrixEntry = matrices.peek();
        Matrix4f positionMatrix = matrixEntry.getPositionMatrix();
        Matrix3f normalMatrix = matrixEntry.getNormalMatrix();
        VertexConsumer consumer = vertexConsumers.getBuffer(
                RenderLayer.getEntityTranslucent(cell.atlasId(), false)
        );
        vertex(consumer, positionMatrix, normalMatrix,
                -0.5F, -0.5F, cell.minU(), cell.maxV(), light, overlay);
        vertex(consumer, positionMatrix, normalMatrix,
                -0.5F, 0.5F, cell.minU(), cell.minV(), light, overlay);
        vertex(consumer, positionMatrix, normalMatrix,
                0.5F, 0.5F, cell.maxU(), cell.minV(), light, overlay);
        vertex(consumer, positionMatrix, normalMatrix,
                0.5F, -0.5F, cell.maxU(), cell.maxV(), light, overlay);
        return true;
    }

    private static boolean renderWorldIcon(ItemStack stack,
                                           ModelTransformationMode mode,
                                           MatrixStack matrices,
                                           VertexConsumerProvider vertexConsumers,
                                           int light, int overlay,
                                           ObjMeshBakedModel model) {
        IconCell cell = model.guiIconTemplate() == null ? null : INSTANCE.getOrCreate(model);
        if (cell == null) {
            // Resource-pack/cache failures must not bring back an expensive 3D item.
            var sprite = model.getParticleSprite();
            cell = new IconCell(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE,
                    sprite.getMinU(), sprite.getMinV(), sprite.getMaxU(), sprite.getMaxV());
        }
        matrices.push();
        try {
            // Use vanilla flat-item size and offsets, independent of OBJ dimensions.
            switch (mode) {
                case THIRD_PERSON_LEFT_HAND, THIRD_PERSON_RIGHT_HAND -> {
                    matrices.translate(0.0, 3.0 / 16.0, 1.0 / 16.0);
                    // A slight tilt keeps a paper-thin icon visible in a front view.
                    matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(45.0F));
                    matrices.scale(0.55F, 0.55F, 0.55F);
                }
                case GROUND -> {
                    matrices.translate(0.0, 2.0 / 16.0, 0.0);
                    matrices.scale(0.5F, 0.5F, 0.5F);
                }
                case HEAD -> {
                    matrices.translate(0.0, 13.0 / 16.0, 7.0 / 16.0);
                    matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F));
                }
                case FIXED -> matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F));
                default -> { }
            }
            VertexConsumer consumer = ItemRenderer.getItemGlintConsumer(vertexConsumers,
                    RenderLayer.getEntityCutout(cell.atlasId()), true, stack.hasGlint());
            MatrixStack.Entry entry = matrices.peek();
            // Opposite winding and mirrored back UVs keep the picture readable from both sides.
            worldVertex(consumer, entry, -0.5F, -0.5F, cell.minU(), cell.maxV(), 1, light, overlay);
            worldVertex(consumer, entry, 0.5F, -0.5F, cell.maxU(), cell.maxV(), 1, light, overlay);
            worldVertex(consumer, entry, 0.5F, 0.5F, cell.maxU(), cell.minV(), 1, light, overlay);
            worldVertex(consumer, entry, -0.5F, 0.5F, cell.minU(), cell.minV(), 1, light, overlay);
            worldVertex(consumer, entry, 0.5F, -0.5F, cell.minU(), cell.maxV(), -1, light, overlay);
            worldVertex(consumer, entry, -0.5F, -0.5F, cell.maxU(), cell.maxV(), -1, light, overlay);
            worldVertex(consumer, entry, -0.5F, 0.5F, cell.maxU(), cell.minV(), -1, light, overlay);
            worldVertex(consumer, entry, 0.5F, 0.5F, cell.minU(), cell.minV(), -1, light, overlay);
        } finally {
            matrices.pop();
        }
        return true;
    }

    private static void worldVertex(VertexConsumer consumer, MatrixStack.Entry entry,
                                    float x, float y, float u, float v, float normalZ,
                                    int light, int overlay) {
        consumer.vertex(entry.getPositionMatrix(), x, y, 0.0F)
                .color(255, 255, 255, 255).texture(u, v).overlay(overlay).light(light)
                .normal(entry.getNormalMatrix(), 0.0F, 0.0F, normalZ).next();
    }

    /** One image quad; the caller provides the camera-facing world matrix. */
    public static void renderAxiomPreview(ItemStack stack, MatrixStack matrices,
                                          VertexConsumerProvider.Immediate consumers, float opacity) {
        if (!Daedalon.MOD_ID.equals(Registries.ITEM.getId(stack.getItem()).getNamespace())) return;
        BakedModel baked = ObjMeshBakedModel.unwrapAttachedModel(MinecraftClient.getInstance()
                .getItemRenderer().getModel(stack, null, null, 0));
        if (!(baked instanceof ObjMeshBakedModel model)) return;
        Identifier image = INSTANCE.previewImage(model, consumers);
        IconCell cell = image == null ? INSTANCE.getOrCreate(model) : new IconCell(image, 0, 0, 1, 1);
        if (cell == null) return;
        VertexConsumer consumer = consumers.getBuffer(RenderLayer.getEntityTranslucentEmissive(cell.atlasId()));
        var entry = matrices.peek();
        int alpha = Math.round(Math.max(0, Math.min(1, opacity)) * 255);
        for (float[] vertex : PREVIEW_QUAD) consumer.vertex(entry.getPositionMatrix(), vertex[0], vertex[1], 0)
                .color(255,255,255,alpha).texture(cell.minU() + vertex[2] * (cell.maxU() - cell.minU()),
                        cell.minV() + vertex[3] * (cell.maxV() - cell.minV()))
                .overlay(OverlayTexture.DEFAULT_UV).light(LightmapTextureManager.MAX_LIGHT_COORDINATE)
                .normal(entry.getNormalMatrix(),0,0,1).next();
    }

    private synchronized Identifier previewImage(ObjMeshBakedModel model, VertexConsumerProvider.Immediate consumers) {
        long now = System.nanoTime();
        PreviewImage cached = previewImages.get(model);
        if (cached != null) {
            previewImages.put(model, new PreviewImage(cached.id(), now));
            return cached.id();
        }
        if (failedPreviews.contains(model) || now < nextPreviewGeneration) return null;
        var oldest = previewImages.isEmpty() ? null : previewImages.entrySet().iterator().next();
        // Keep images used by the current selection; excess materials use the existing GUI atlas.
        boolean full = previewImages.size() >= MAX_PREVIEW_IMAGES;
        if (full && now - oldest.getValue().usedAt() < 1_000_000_000L) return null;
        nextPreviewGeneration = now + 50_000_000L;
        NativeImage image = null;
        try {
            ObjGuiIconTemplate template = previewTemplates.computeIfAbsent(model.previewMesh(),
                    ignored -> model.createAxiomPreviewTemplate());
            if (previewTemplates.size() > 8) previewTemplates.remove(previewTemplates.keySet().iterator().next());
            ResourceEpoch epoch = resourceEpoch();
            List<SourceTexture> materials = new ArrayList<>();
            for (Identifier id : model.materialTextureIds()) materials.add(sourceTexture(epoch, id));
            image = new NativeImage(256, 256, true);
            image.fillRect(0, 0, 256, 256, 0);
            // The projection already supplies its own transparent edge padding.
            renderIcon(model, template, materials, image, -ICON_GUTTER, -ICON_GUTTER);
            NativeImageBackedTexture texture = new NativeImageBackedTexture(image);
            Identifier id = full ? oldest.getValue().id()
                    : new Identifier(Daedalon.MOD_ID, "dynamic/axiom_preview_" + previewImages.size());
            if (full) {
                // Deferred vertices may still reference this slot. Submit them before reusing it.
                consumers.draw();
                previewImages.remove(oldest.getKey());
                MinecraftClient.getInstance().getTextureManager().destroyTexture(id);
            }
            MinecraftClient.getInstance().getTextureManager().registerTexture(id, texture);
            texture.setFilter(true, false);
            image = null; // Owned by the texture manager until resource reload.
            previewImages.put(model, new PreviewImage(id, now));
            return id;
        } catch (IOException | RuntimeException exception) {
            failedPreviews.add(model);
            Daedalon.LOGGER.warn("Unable to create Axiom decoration preview for {}: {}", model.modelId(), exception.getMessage());
            return null;
        } finally {
            if (image != null) image.close();
        }
    }

    private synchronized IconCell getOrCreate(ObjMeshBakedModel model) {
        IconCell cached = cells.get(model);
        if (cached != null) return cached;
        if (failedModels.contains(model) || nextCellIndex >= MAX_CACHED_ICONS) return null;
        ResourceEpoch epoch = resourceEpoch();
        // Rendering only requests work. No PNG decoding, colour raster or GL upload here.
        // Identity de-duplication also covers the flat fallback's second request.
        work.request(model, () -> generateCell(model, epoch));
        return null;
    }

    private ResourceEpoch resourceEpoch() {
        if (resources == null) resources = new ResourceEpoch(MinecraftClient.getInstance().getResourceManager());
        return resources;
    }

    private static NativeImage generateCell(ObjMeshBakedModel model, ResourceEpoch epoch) throws IOException {
        List<SourceTexture> materials = new ArrayList<>(model.materialTextureIds().size());
        for (Identifier id : model.materialTextureIds()) materials.add(sourceTexture(epoch, id));
        NativeImage image = new NativeImage(ICON_CELL_SIZE, ICON_CELL_SIZE, true);
        try {
            image.fillRect(0, 0, ICON_CELL_SIZE, ICON_CELL_SIZE, 0);
            renderIcon(model, materials, image, 0, 0);
            return image; // Owned by the queue, then by the upload batch.
        } catch (IOException | RuntimeException exception) {
            image.close();
            throw exception;
        }
    }

    /** Runs between frames on the client thread, never from an item draw. */
    private synchronized void uploadReadyIcons() {
        long started = System.nanoTime();
        int processed = 0;
        while (processed < MAX_UPLOADS_PER_TICK && nextCellIndex < MAX_CACHED_ICONS) {
            int slot = nextCellIndex % CELLS_PER_PAGE;
            int rowRemaining = CELLS_PER_ROW - slot % CELLS_PER_ROW;
            int limit = Math.min(rowRemaining, MAX_UPLOADS_PER_TICK - processed);
            List<ObjGuiIconWorkQueue.Completed<ObjMeshBakedModel, NativeImage>> batch = new ArrayList<>(limit);
            while (batch.size() < limit && processed < MAX_UPLOADS_PER_TICK) {
                if (processed > 0 && System.nanoTime() - started >= UPLOAD_BUDGET_NANOS) break;
                var result = work.poll();
                if (result == null) break;
                processed++;
                if (result.failure() != null) {
                    iconFailed(result.key(), result.failure());
                } else {
                    batch.add(result);
                }
            }
            if (batch.isEmpty()) return;
            try (NativeImage strip = new NativeImage(batch.size() * ICON_CELL_SIZE, ICON_CELL_SIZE, true)) {
                // Each source includes a zero-filled gutter. Merge consecutive cells into one upload.
                for (int index = 0; index < batch.size(); index++) {
                    batch.get(index).value().copyRect(strip, 0, 0, index * ICON_CELL_SIZE, 0,
                            ICON_CELL_SIZE, ICON_CELL_SIZE, false, false);
                }
                AtlasPage page = atlasPage(nextCellIndex / CELLS_PER_PAGE);
                int cellX = slot % CELLS_PER_ROW * ICON_CELL_SIZE;
                int cellY = slot / CELLS_PER_ROW * ICON_CELL_SIZE;
                page.upload(strip, cellX, cellY);
                // Publish only after the GPU contains the pixels. Cells are never recycled.
                for (var result : batch) {
                    IconCell created = iconCell(page.id(), nextCellIndex % CELLS_PER_PAGE);
                    cells.put(result.key(), created);
                    nextCellIndex++;
                }
            } catch (RuntimeException exception) {
                for (var result : batch) iconFailed(result.key(), exception);
            } finally {
                for (var result : batch) result.value().close();
            }
            if (nextCellIndex >= MAX_CACHED_ICONS) {
                work.reset(); // Close any excess completed images instead of retaining them indefinitely.
                return;
            }
            // Soft time limit: a single allocation/upload is indivisible, with a hard cell cap too.
            if (System.nanoTime() - started >= UPLOAD_BUDGET_NANOS) return;
        }
    }

    private void iconFailed(ObjMeshBakedModel model, Exception exception) {
        failedModels.add(model);
        Daedalon.LOGGER.warn(
                "[Daedalon OBJ GUI] Unable to cache full-model icon for {}; using flat material icon fallback: {}",
                model.modelId(), exception.getMessage());
    }

    private static IconCell iconCell(Identifier page, int slot) {
        int x = slot % CELLS_PER_ROW * ICON_CELL_SIZE + ICON_GUTTER;
        int y = slot / CELLS_PER_ROW * ICON_CELL_SIZE + ICON_GUTTER;
        return new IconCell(page, x / (float) ATLAS_PAGE_SIZE, y / (float) ATLAS_PAGE_SIZE,
                (x + ObjGuiIconTemplate.ICON_RESOLUTION) / (float) ATLAS_PAGE_SIZE,
                (y + ObjGuiIconTemplate.ICON_RESOLUTION) / (float) ATLAS_PAGE_SIZE);
    }

    private static SourceTexture sourceTexture(ResourceEpoch epoch, Identifier logicalTextureId)
            throws IOException {
        synchronized (epoch.sourceTextures) {
            SourceTexture cached = epoch.sourceTextures.get(logicalTextureId);
            if (cached != null) return cached;
            SourceTexture loaded = readTexture(epoch.manager, logicalTextureId);
            epoch.sourceTextures.put(logicalTextureId, loaded);
            return loaded;
        }
    }

    static SourceTexture readTexture(ResourceManager manager, Identifier logicalTextureId)
            throws IOException {
        Identifier pngId = textureResourceId(logicalTextureId);
        Identifier metadataId = pngId.withPath(pngId.getPath() + ".mcmeta");
        if (manager.getResource(metadataId).isPresent()) {
            throw new IOException("animated or metadata-backed texture " + logicalTextureId);
        }

        Optional<Resource> resource = manager.getResource(pngId);
        if (resource.isEmpty()) {
            throw new IOException("missing active texture " + pngId);
        }

        // Worker and Axiom can share immutable ABGR pixels. Never retain/close a source
        // NativeImage under another reader, including when a resource reload cancels work.
        try (InputStream input = resource.get().getInputStream(); NativeImage image = NativeImage.read(input)) {
            int[] pixels = new int[image.getWidth() * image.getHeight()];
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    if ((image.getOpacity(x, y) & 0xFF) != 0xFF) {
                        throw new IOException("transparent texture " + logicalTextureId);
                    }
                    pixels[y * image.getWidth() + x] = image.getColor(x, y);
                }
            }
            return new SourceTexture(image.getWidth(), image.getHeight(), pixels);
        }
    }

    private AtlasPage atlasPage(int pageIndex) {
        while (pages.size() <= pageIndex) {
            int index = pages.size();
            Identifier id = new Identifier(
                    Daedalon.MOD_ID,
                    "dynamic/obj_gui_icons_" + index
            );
            IconAtlasTexture texture = new IconAtlasTexture();
            texture.setFilter(true, false);
            MinecraftClient.getInstance().getTextureManager().registerTexture(id, texture);
            pages.add(new AtlasPage(id, texture));
            Daedalon.LOGGER.info(
                    "[Daedalon OBJ GUI] allocated bounded icon atlas page {} of {}",
                    index + 1,
                    MAX_ATLAS_PAGES
            );
        }
        return pages.get(pageIndex);
    }

    private static void renderIcon(ObjMeshBakedModel model,
                                   List<SourceTexture> materials,
                                   NativeImage atlas,
                                   int cellX,
                                   int cellY) throws IOException {
        renderIcon(model, model.guiIconTemplate(), materials, atlas, cellX, cellY);
    }

    static void renderIcon(ObjMeshBakedModel model, ObjGuiIconTemplate template,
                                   List<SourceTexture> materials, NativeImage atlas,
                                   int cellX, int cellY) throws IOException {
        int writtenPixels = 0;
        for (int iconY = 0; iconY < template.resolution(); iconY++) {
            for (int iconX = 0; iconX < template.resolution(); iconX++) {
                long alphaSum = 0L;
                double redPremultiplied = 0.0D;
                double greenPremultiplied = 0.0D;
                double bluePremultiplied = 0.0D;

                for (int sampleY = 0; sampleY < ObjGuiIconTemplate.SUPERSAMPLE; sampleY++) {
                    for (int sampleX = 0; sampleX < ObjGuiIconTemplate.SUPERSAMPLE; sampleX++) {
                        int rasterX = iconX * ObjGuiIconTemplate.SUPERSAMPLE + sampleX;
                        int rasterY = iconY * ObjGuiIconTemplate.SUPERSAMPLE + sampleY;
                        int sampleIndex = rasterY * (template.resolution() * ObjGuiIconTemplate.SUPERSAMPLE) + rasterX;
                        if (!template.isCovered(sampleIndex)) {
                            continue;
                        }

                        int material = template.materialAt(sampleIndex);
                        if (material < 0 || material >= materials.size()) {
                            throw new IOException("GUI raster material tag outside active material list");
                        }
                        int color = materials.get(material).sample(
                                model.guiTextureU(material, template.uAt(sampleIndex)),
                                model.guiTextureV(material, template.vAt(sampleIndex))
                        );
                        int alpha = color >>> 24 & 0xFF;
                        int blue = color >>> 16 & 0xFF;
                        int green = color >>> 8 & 0xFF;
                        int red = color & 0xFF;
                        float shade = template.shadeAt(sampleIndex);
                        alphaSum += alpha;
                        redPremultiplied += Math.min(1.0F, SRGB_TO_LINEAR[red] * shade) * alpha;
                        greenPremultiplied += Math.min(1.0F, SRGB_TO_LINEAR[green] * shade) * alpha;
                        bluePremultiplied += Math.min(1.0F, SRGB_TO_LINEAR[blue] * shade) * alpha;
                    }
                }

                if (alphaSum == 0L) {
                    continue;
                }
                int outputAlpha = clampColor(Math.round(
                        alphaSum / (float) (ObjGuiIconTemplate.SUPERSAMPLE
                                * ObjGuiIconTemplate.SUPERSAMPLE)
                ));
                int outputRed = linearToSrgb(redPremultiplied / alphaSum);
                int outputGreen = linearToSrgb(greenPremultiplied / alphaSum);
                int outputBlue = linearToSrgb(bluePremultiplied / alphaSum);
                int output = outputAlpha << 24
                        | outputBlue << 16
                        | outputGreen << 8
                        | outputRed;
                atlas.setColor(
                        cellX + ICON_GUTTER + iconX,
                        cellY + ICON_GUTTER + iconY,
                        output
                );
                writtenPixels++;
            }
        }
        if (writtenPixels == 0) {
            throw new IOException("full-model GUI raster produced an empty icon");
        }
    }

    private synchronized void reload(ResourceManager manager) {
        // Cancels queued generations and closes every completed staged image. A running
        // old job owns its immutable source arrays and closes its late image itself.
        work.reset();
        resources = new ResourceEpoch(manager);
        TextureManager textureManager = MinecraftClient.getInstance().getTextureManager();
        for (AtlasPage page : pages) {
            textureManager.destroyTexture(page.id());
        }
        pages.clear();
        for (PreviewImage image : previewImages.values()) textureManager.destroyTexture(image.id());
        nextPreviewGeneration = 0;
        previewImages.clear();
        previewTemplates.clear();
        failedPreviews.clear();
        cells.clear();
        failedModels.clear();
        nextCellIndex = 0;
    }

    private static Identifier textureResourceId(Identifier logicalTextureId) {
        String path = logicalTextureId.getPath();
        if (path.startsWith("textures/") && path.endsWith(".png")) {
            return logicalTextureId;
        }
        return new Identifier(
                logicalTextureId.getNamespace(),
                "textures/" + path + ".png"
        );
    }

    private static int clampColor(int component) {
        return Math.max(0, Math.min(255, component));
    }

    private static float[] createSrgbToLinearTable() {
        float[] table = new float[256];
        for (int channel = 0; channel < table.length; channel++) {
            table[channel] = (float) Math.pow(channel / 255.0F, 2.2D);
        }
        return table;
    }

    private static int[] createLinearToSrgbTable() {
        int[] table = new int[65_536];
        for (int index = 0; index < table.length; index++) {
            table[index] = clampColor((int) Math.round(Math.pow(index / (double) (table.length - 1), 1.0D / 2.2D) * 255.0D));
        }
        return table;
    }

    static int linearToSrgb(double linear) {
        int index = (int) Math.round(Math.max(0.0D, Math.min(1.0D, linear)) * (LINEAR_TO_SRGB.length - 1));
        return LINEAR_TO_SRGB[index];
    }

    private static void vertex(VertexConsumer consumer,
                               Matrix4f positionMatrix,
                               Matrix3f normalMatrix,
                               float x,
                               float y,
                               float u,
                               float v,
                               int light,
                               int overlay) {
        consumer.vertex(positionMatrix, x, y, 0.0F)
                .color(255, 255, 255, 255)
                .texture(u, v)
                .overlay(overlay)
                // The atlas already contains deterministic model lighting;
                // full-bright avoids Axiom/REI applying a second dim pass.
                .light(LightmapTextureManager.MAX_LIGHT_COORDINATE)
                // The cached pixels already contain the model's vanilla
                // two-light shading. An upward quad normal makes the entity
                // shader contribute exactly 1.0 instead of dimming the whole
                // icon a second time (a forward normal contributed only 0.74).
                .normal(normalMatrix, 0.0F, 1.0F, 0.0F)
                .next();
    }

    private record IconCell(Identifier atlasId, float minU, float minV, float maxU, float maxV) {
    }

    private record AtlasPage(Identifier id, IconAtlasTexture texture) {
        private void upload(NativeImage strip, int x, int y) {
            texture.bindTexture();
            // Explicit blur/clamp/mipmap/close flags: the shorter overload's first
            // boolean controls mipmaps, not linear filtering.
            strip.upload(0, x, y, 0, 0, strip.getWidth(), strip.getHeight(), true, false, false, false);
        }
    }

    /** GPU allocation only: cells supply their own fully initialized pixels and gutters. */
    private static final class IconAtlasTexture extends AbstractTexture {
        private IconAtlasTexture() {
            TextureUtil.prepareImage(getGlId(), ATLAS_PAGE_SIZE, ATLAS_PAGE_SIZE);
        }

        @Override public void setFilter(boolean bilinear, boolean mipmap) {
            // Entity render phases request nearest filtering whenever they bind a
            // texture. These pictures retain linear sampling at every GUI scale.
            super.setFilter(true, false);
        }

        @Override public void load(ResourceManager manager) { }
        @Override public void close() { clearGlId(); }
    }

    private static final class ResourceEpoch {
        private final ResourceManager manager;
        private final Map<Identifier, SourceTexture> sourceTextures = new LinkedHashMap<>();

        private ResourceEpoch(ResourceManager manager) { this.manager = manager; }
    }

    record SourceTexture(int width, int height, int[] pixels) {
        int sample(float u, float v) throws IOException {
            if (!Float.isFinite(u) || !Float.isFinite(v)) {
                throw new IOException("non-finite generated GUI UV");
            }
            if (u < -UV_EPSILON || u > 1.0F + UV_EPSILON
                    || v < -UV_EPSILON || v > 1.0F + UV_EPSILON) {
                throw new IOException("GUI UV outside the source texture");
            }
            int x = Math.round(
                    Math.max(0.0F, Math.min(1.0F, u)) * (width - 1)
            );
            int y = Math.round(
                    Math.max(0.0F, Math.min(1.0F, v)) * (height - 1)
            );
            return pixels[y * width + x];
        }
    }
}
