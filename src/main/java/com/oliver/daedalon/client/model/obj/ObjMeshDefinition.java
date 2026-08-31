package com.oliver.daedalon.client.model.obj;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.io.Reader;
import java.util.LinkedHashMap;
import java.util.Map;

record ObjMeshDefinition(Identifier definitionId,
                         Identifier objId,
                         Identifier explicitMtlId,
                         boolean flipV,
                         boolean repairDegenerateUvs,
                         boolean forceUvProjection,
                         boolean faceOrientedUvs,
                         boolean doubleSided,
                         UvProjection uvProjection,
                         float cylindricalURepeats,
                         int textureUTiles,
                         Map<String, UvProjection> materialUvProjections,
                         boolean smoothNormals,
                         float smoothAngleDegrees,
                         boolean fitToBlock,
                         Vec3 scale,
                         Vec3 translate,
                         Map<String, Identifier> materialOverrides,
                         Identifier particleTexture) {
    ObjMeshDefinition {
        materialUvProjections = Map.copyOf(materialUvProjections);
        materialOverrides = Map.copyOf(materialOverrides);
    }

    static ObjMeshDefinition load(ResourceManager resourceManager, Identifier definitionId) throws IOException {
        Resource resource = resourceManager.getResource(definitionId)
                .orElseThrow(() -> new IOException("Missing OBJ mesh definition " + definitionId));

        try (Reader reader = resource.getReader()) {
            JsonElement root = JsonParser.parseReader(reader);
            if (!root.isJsonObject()) {
                throw new IOException("OBJ mesh definition " + definitionId + " must contain a JSON object");
            }

            JsonObject json = root.getAsJsonObject();
            Identifier objId = requiredIdentifier(json, "obj", definitionId);
            Identifier mtlId = optionalIdentifier(json, "mtl", definitionId);
            boolean flipV = optionalBoolean(json, "flip_v", true, definitionId);
            boolean repairDegenerateUvs = optionalBoolean(json, "repair_degenerate_uvs", true, definitionId);
            boolean forceUvProjection = optionalBoolean(json, "force_uv_projection", false, definitionId);
            boolean faceOrientedUvs = optionalBoolean(json, "face_oriented_uvs", false, definitionId);
            boolean doubleSided = optionalBoolean(json, "double_sided", false, definitionId);
            UvProjection uvProjection = UvProjection.fromConfigValue(
                    optionalString(json, "uv_projection", UvProjection.BOX.configValue(), definitionId),
                    definitionId
            );
            float cylindricalURepeats = optionalFloat(json, "cylindrical_u_repeats", 1.0F, definitionId);
            int textureUTiles = optionalInt(json, "texture_u_tiles", 1, definitionId);
            Map<String, UvProjection> materialUvProjections = materialUvProjections(json, definitionId);
            boolean smoothNormals = optionalBoolean(json, "smooth_normals", false, definitionId);
            float smoothAngleDegrees = optionalFloat(json, "smooth_angle_degrees", 60.0F, definitionId);
            boolean fitToBlock = optionalBoolean(json, "fit_to_block", false, definitionId);
            Vec3 scale = optionalScale(json.get("scale"), definitionId);
            Vec3 translate = optionalVector(json.get("translate"), new Vec3(0.0F, 0.0F, 0.0F), "translate", definitionId);
            Identifier particle = optionalIdentifier(json, "particle", definitionId);

            Map<String, Identifier> overrides = new LinkedHashMap<>();
            JsonElement materialsElement = json.get("materials");
            if (materialsElement != null) {
                if (!materialsElement.isJsonObject()) {
                    throw new IOException("OBJ mesh definition " + definitionId + " field 'materials' must be an object");
                }
                for (Map.Entry<String, JsonElement> entry : materialsElement.getAsJsonObject().entrySet()) {
                    if (!entry.getValue().isJsonPrimitive() || !entry.getValue().getAsJsonPrimitive().isString()) {
                        throw new IOException("OBJ mesh definition " + definitionId + " material '" + entry.getKey() + "' must name a texture");
                    }
                    overrides.put(entry.getKey(), parseIdentifier(entry.getValue().getAsString(), definitionId, "material '" + entry.getKey() + "'"));
                }
            }

            if (particle == null) {
                particle = overrides.values().stream().findFirst().orElse(new Identifier("minecraft", "missingno"));
            }

            validateTransform(scale, translate, definitionId);
            if (!Float.isFinite(smoothAngleDegrees) || smoothAngleDegrees <= 0.0F || smoothAngleDegrees > 180.0F) {
                throw new IOException("OBJ mesh definition " + definitionId + " field 'smooth_angle_degrees' must be greater than 0 and at most 180");
            }
            if (!Float.isFinite(cylindricalURepeats) || cylindricalURepeats <= 0.0F) {
                throw new IOException("OBJ mesh definition " + definitionId + " field 'cylindrical_u_repeats' must be finite and greater than zero");
            }
            if (textureUTiles <= 0) {
                throw new IOException("OBJ mesh definition " + definitionId + " field 'texture_u_tiles' must be greater than zero");
            }
            boolean usesCylindricalProjection = uvProjection.isCylindrical()
                    || materialUvProjections.values().stream().anyMatch(UvProjection::isCylindrical);
            if (usesCylindricalProjection && cylindricalURepeats + 1.0F > textureUTiles) {
                throw new IOException("OBJ mesh definition " + definitionId
                        + " cylindrical projection needs at least one spare horizontal texture tile after 'cylindrical_u_repeats'");
            }
            return new ObjMeshDefinition(definitionId, objId, mtlId, flipV, repairDegenerateUvs,
                    forceUvProjection, faceOrientedUvs, doubleSided, uvProjection, cylindricalURepeats,
                    textureUTiles, materialUvProjections, smoothNormals, smoothAngleDegrees, fitToBlock,
                    scale, translate, overrides, particle);
        } catch (RuntimeException exception) {
            if (exception instanceof IllegalArgumentException) {
                throw new IOException("Invalid OBJ mesh definition " + definitionId + ": " + exception.getMessage(), exception);
            }
            throw exception;
        }
    }

    private static Identifier requiredIdentifier(JsonObject json, String name, Identifier definitionId) throws IOException {
        Identifier value = optionalIdentifier(json, name, definitionId);
        if (value == null) {
            throw new IOException("OBJ mesh definition " + definitionId + " is missing required field '" + name + "'");
        }
        return value;
    }

    private static Identifier optionalIdentifier(JsonObject json, String name, Identifier definitionId) throws IOException {
        JsonElement element = json.get(name);
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new IOException("OBJ mesh definition " + definitionId + " field '" + name + "' must be a resource identifier");
        }
        return parseIdentifier(element.getAsString(), definitionId, "field '" + name + "'");
    }

    private static Identifier parseIdentifier(String value, Identifier definitionId, String fieldName) throws IOException {
        Identifier identifier = Identifier.tryParse(value);
        if (identifier == null) {
            throw new IOException("OBJ mesh definition " + definitionId + " " + fieldName + " has invalid resource identifier '" + value + "'");
        }
        return identifier;
    }

    private static boolean optionalBoolean(JsonObject json, String name, boolean fallback, Identifier definitionId) throws IOException {
        JsonElement element = json.get(name);
        if (element == null) {
            return fallback;
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
            throw new IOException("OBJ mesh definition " + definitionId + " field '" + name + "' must be true or false");
        }
        return element.getAsBoolean();
    }

    private static String optionalString(JsonObject json, String name, String fallback, Identifier definitionId) throws IOException {
        JsonElement element = json.get(name);
        if (element == null) {
            return fallback;
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new IOException("OBJ mesh definition " + definitionId + " field '" + name + "' must be a string");
        }
        return element.getAsString();
    }

    private static float optionalFloat(JsonObject json, String name, float fallback, Identifier definitionId) throws IOException {
        JsonElement element = json.get(name);
        if (element == null) {
            return fallback;
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new IOException("OBJ mesh definition " + definitionId + " field '" + name + "' must be a number");
        }
        return element.getAsFloat();
    }

    private static int optionalInt(JsonObject json, String name, int fallback, Identifier definitionId) throws IOException {
        JsonElement element = json.get(name);
        if (element == null) {
            return fallback;
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new IOException("OBJ mesh definition " + definitionId + " field '" + name + "' must be an integer");
        }
        try {
            double numericValue = element.getAsDouble();
            int integerValue = element.getAsInt();
            if (!Double.isFinite(numericValue) || numericValue != integerValue) {
                throw new IOException("OBJ mesh definition " + definitionId + " field '" + name + "' must be an integer");
            }
            return integerValue;
        } catch (RuntimeException exception) {
            throw new IOException("OBJ mesh definition " + definitionId + " field '" + name + "' must be an integer", exception);
        }
    }

    private static Map<String, UvProjection> materialUvProjections(JsonObject json,
                                                                   Identifier definitionId) throws IOException {
        JsonElement element = json.get("material_uv_projections");
        if (element == null) {
            return Map.of();
        }
        if (!element.isJsonObject()) {
            throw new IOException("OBJ mesh definition " + definitionId
                    + " field 'material_uv_projections' must be an object");
        }

        Map<String, UvProjection> result = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
            if (!entry.getValue().isJsonPrimitive() || !entry.getValue().getAsJsonPrimitive().isString()) {
                throw new IOException("OBJ mesh definition " + definitionId
                        + " material UV projection '" + entry.getKey() + "' must name a projection mode");
            }
            result.put(
                    entry.getKey(),
                    UvProjection.fromConfigValue(entry.getValue().getAsString(), definitionId)
            );
        }
        return result;
    }

    private static Vec3 optionalScale(JsonElement element, Identifier definitionId) throws IOException {
        if (element == null) {
            return new Vec3(1.0F, 1.0F, 1.0F);
        }
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            float value = element.getAsFloat();
            return new Vec3(value, value, value);
        }
        return optionalVector(element, new Vec3(1.0F, 1.0F, 1.0F), "scale", definitionId);
    }

    private static Vec3 optionalVector(JsonElement element, Vec3 fallback, String name, Identifier definitionId) throws IOException {
        if (element == null) {
            return fallback;
        }
        if (!element.isJsonArray()) {
            throw new IOException("OBJ mesh definition " + definitionId + " field '" + name + "' must be a number or a three-number array");
        }
        JsonArray array = element.getAsJsonArray();
        if (array.size() != 3) {
            throw new IOException("OBJ mesh definition " + definitionId + " field '" + name + "' must contain exactly three numbers");
        }
        try {
            return new Vec3(array.get(0).getAsFloat(), array.get(1).getAsFloat(), array.get(2).getAsFloat());
        } catch (RuntimeException exception) {
            throw new IOException("OBJ mesh definition " + definitionId + " field '" + name + "' must contain exactly three numbers", exception);
        }
    }

    private static void validateTransform(Vec3 scale, Vec3 translate, Identifier definitionId) throws IOException {
        if (!scale.isFinite() || scale.x() <= 0.0F || scale.y() <= 0.0F || scale.z() <= 0.0F) {
            throw new IOException("OBJ mesh definition " + definitionId + " scale values must be finite and greater than zero");
        }
        if (!translate.isFinite()) {
            throw new IOException("OBJ mesh definition " + definitionId + " translation values must be finite");
        }
    }

    record Vec3(float x, float y, float z) {
        boolean isFinite() {
            return Float.isFinite(x) && Float.isFinite(y) && Float.isFinite(z);
        }
    }

    enum UvProjection {
        BOX("box"),
        AXIS_STABILIZED_BOX("axis_stabilized_box"),
        CYLINDRICAL("cylindrical"),
        CYLINDRICAL_WITH_RADIAL_CAPS("cylindrical_with_radial_caps"),
        GLOBAL_PLANAR("global_planar"),
        PLANAR_WITH_BOX_FALLBACK("planar_with_box_fallback");

        private final String configValue;

        UvProjection(String configValue) {
            this.configValue = configValue;
        }

        String configValue() {
            return configValue;
        }

        boolean isCylindrical() {
            return this == CYLINDRICAL || this == CYLINDRICAL_WITH_RADIAL_CAPS;
        }

        private static UvProjection fromConfigValue(String value, Identifier definitionId) throws IOException {
            for (UvProjection projection : values()) {
                if (projection.configValue.equals(value)) {
                    return projection;
                }
            }
            throw new IOException("OBJ mesh definition " + definitionId
                    + " field 'uv_projection' must be 'box', 'axis_stabilized_box', 'cylindrical',"
                    + " 'cylindrical_with_radial_caps',"
                    + " 'global_planar', or 'planar_with_box_fallback'");
        }
    }
}
