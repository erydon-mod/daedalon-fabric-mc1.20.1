package com.oliver.daedalon.client.model.obj;

import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class ObjMeshParser {
    private ObjMeshParser() {
    }

    static ObjMeshData parse(ResourceManager resourceManager, ObjMeshDefinition definition) throws IOException {
        long started = System.nanoTime();
        ParsedObj parsed = parseObj(resourceManager, definition.objId());

        List<Identifier> mtlIds;
        if (definition.explicitMtlId() != null) {
            mtlIds = List.of(definition.explicitMtlId());
        } else {
            mtlIds = List.copyOf(parsed.declaredMtlIds);
        }

        Map<String, Identifier> materialTextures = new LinkedHashMap<>();
        for (Identifier mtlId : mtlIds) {
            materialTextures.putAll(parseMtl(resourceManager, mtlId));
        }

        if (parsed.positions.isEmpty()) {
            throw new IOException("OBJ resource " + definition.objId() + " contains no vertices");
        }
        if (parsed.faces.isEmpty()) {
            throw new IOException("OBJ resource " + definition.objId() + " contains no faces");
        }

        return new ObjMeshData(
                definition.objId(),
                parsed.positions,
                parsed.textureCoordinates,
                parsed.normals,
                parsed.faces,
                mtlIds,
                materialTextures,
                parsed.sourceTriangleCount,
                parsed.sourceQuadCount,
                parsed.triangulatedNgonCount,
                parsed.triangulatedNgonTriangleCount,
                parsed.objectNames.size(),
                parsed.groupNames.size(),
                ObjMeshData.Bounds.of(parsed.positions),
                System.nanoTime() - started
        );
    }

    private static ParsedObj parseObj(ResourceManager resourceManager, Identifier objId) throws IOException {
        Resource resource = resourceManager.getResource(objId)
                .orElseThrow(() -> new IOException("Missing OBJ resource " + objId));
        ParsedObj parsed = new ParsedObj();
        String currentMaterial = "";
        String currentObject = "";
        String currentGroup = "";

        try (BufferedReader reader = resource.getReader()) {
            String rawLine;
            int lineNumber = 0;
            while ((rawLine = reader.readLine()) != null) {
                lineNumber++;
                String line = stripComment(rawLine).trim();
                if (line.isEmpty()) {
                    continue;
                }

                Directive directive = splitDirective(line);
                try {
                    switch (directive.name) {
                        case "v" -> parsed.positions.add(parsePosition(directive.value, objId, lineNumber));
                        case "vt" -> parsed.textureCoordinates.add(parseTextureCoordinate(directive.value, objId, lineNumber));
                        case "vn" -> parsed.normals.add(parseNormal(directive.value, objId, lineNumber));
                        case "f" -> parseFace(parsed, directive.value, currentMaterial, currentObject, currentGroup, objId, lineNumber);
                        case "o" -> {
                            currentObject = directive.value;
                            if (!currentObject.isEmpty()) {
                                parsed.objectNames.add(currentObject);
                            }
                        }
                        case "g" -> {
                            currentGroup = directive.value;
                            if (!currentGroup.isEmpty()) {
                                parsed.groupNames.add(currentGroup);
                            }
                        }
                        case "usemtl" -> currentMaterial = directive.value;
                        case "mtllib" -> {
                            List<String> references = tokenize(directive.value);
                            if (references.isEmpty()) {
                                throw parseError(objId, lineNumber, "mtllib requires at least one resource reference", null);
                            }
                            for (String reference : references) {
                                parsed.declaredMtlIds.add(resolveRelativeResource(objId, reference, objId, lineNumber));
                            }
                        }
                        default -> {
                            // OBJ directives outside the prototype's geometry/material subset are intentionally ignored.
                        }
                    }
                } catch (IOException exception) {
                    throw exception;
                } catch (RuntimeException exception) {
                    throw parseError(objId, lineNumber, exception.getMessage(), exception);
                }
            }
        }

        return parsed;
    }

    private static Map<String, Identifier> parseMtl(ResourceManager resourceManager, Identifier mtlId) throws IOException {
        Resource resource = resourceManager.getResource(mtlId)
                .orElseThrow(() -> new IOException("Missing MTL resource " + mtlId));
        Map<String, Identifier> materialTextures = new LinkedHashMap<>();
        String currentMaterial = null;

        try (BufferedReader reader = resource.getReader()) {
            String rawLine;
            int lineNumber = 0;
            while ((rawLine = reader.readLine()) != null) {
                lineNumber++;
                String line = stripComment(rawLine).trim();
                if (line.isEmpty()) {
                    continue;
                }

                Directive directive = splitDirective(line);
                try {
                    if (directive.name.equals("newmtl")) {
                        if (directive.value.isEmpty()) {
                            throw parseError(mtlId, lineNumber, "newmtl requires a material name", null);
                        }
                        currentMaterial = directive.value;
                    } else if (directive.name.equals("map_Kd")) {
                        if (currentMaterial == null) {
                            throw parseError(mtlId, lineNumber, "map_Kd appears before newmtl", null);
                        }
                        String textureReference = extractMapKdPath(directive.value, mtlId, lineNumber);
                        materialTextures.put(currentMaterial, resolveTextureSprite(mtlId, textureReference, lineNumber));
                    }
                } catch (IOException exception) {
                    throw exception;
                } catch (RuntimeException exception) {
                    throw parseError(mtlId, lineNumber, exception.getMessage(), exception);
                }
            }
        }

        return materialTextures;
    }

    private static ObjMeshData.Vec3 parsePosition(String value, Identifier objId, int lineNumber) throws IOException {
        List<String> parts = tokenize(value);
        requireComponentCount(parts, 3, "v", objId, lineNumber);
        float x = parseFiniteFloat(parts.get(0), "vertex X", objId, lineNumber);
        float y = parseFiniteFloat(parts.get(1), "vertex Y", objId, lineNumber);
        float z = parseFiniteFloat(parts.get(2), "vertex Z", objId, lineNumber);
        return new ObjMeshData.Vec3(x, y, z);
    }

    private static ObjMeshData.Vec2 parseTextureCoordinate(String value, Identifier objId, int lineNumber) throws IOException {
        List<String> parts = tokenize(value);
        requireComponentCount(parts, 2, "vt", objId, lineNumber);
        float u = parseFiniteFloat(parts.get(0), "texture U", objId, lineNumber);
        float v = parseFiniteFloat(parts.get(1), "texture V", objId, lineNumber);
        return new ObjMeshData.Vec2(u, v);
    }

    private static ObjMeshData.Vec3 parseNormal(String value, Identifier objId, int lineNumber) throws IOException {
        List<String> parts = tokenize(value);
        requireComponentCount(parts, 3, "vn", objId, lineNumber);
        try {
            return new ObjMeshData.Vec3(
                    Float.parseFloat(parts.get(0)),
                    Float.parseFloat(parts.get(1)),
                    Float.parseFloat(parts.get(2))
            );
        } catch (NumberFormatException exception) {
            throw parseError(objId, lineNumber, "normal contains an invalid number", exception);
        }
    }

    private static void parseFace(ParsedObj parsed,
                                  String value,
                                  String material,
                                  String objectName,
                                  String groupName,
                                  Identifier objId,
                                  int lineNumber) throws IOException {
        List<String> tokens = tokenize(value);
        if (tokens.size() < 3) {
            throw parseError(objId, lineNumber, "face requires at least three vertices", null);
        }

        List<ObjMeshData.VertexRef> vertices = new ArrayList<>(tokens.size());
        for (String token : tokens) {
            vertices.add(parseFaceVertex(token, parsed, objId, lineNumber));
        }

        if (vertices.size() == 3) {
            parsed.faces.add(new ObjMeshData.Face(vertices, material, objectName, groupName));
            parsed.sourceTriangleCount++;
        } else if (vertices.size() == 4) {
            parsed.faces.add(new ObjMeshData.Face(vertices, material, objectName, groupName));
            parsed.sourceQuadCount++;
        } else {
            parsed.triangulatedNgonCount++;
            parsed.triangulatedNgonTriangleCount += vertices.size() - 2;
            ObjMeshData.VertexRef first = vertices.get(0);
            for (int i = 1; i < vertices.size() - 1; i++) {
                parsed.faces.add(new ObjMeshData.Face(
                        List.of(first, vertices.get(i), vertices.get(i + 1)),
                        material,
                        objectName,
                        groupName
                ));
            }
        }
    }

    private static ObjMeshData.VertexRef parseFaceVertex(String token,
                                                         ParsedObj parsed,
                                                         Identifier objId,
                                                         int lineNumber) throws IOException {
        String[] indices = token.split("/", -1);
        if (indices.length < 1 || indices.length > 3 || indices[0].isEmpty()) {
            throw parseError(objId, lineNumber, "unsupported face vertex '" + token + "'", null);
        }

        int position = parseIndex(indices[0], parsed.positions.size(), "position", objId, lineNumber);
        int texture = ObjMeshData.VertexRef.MISSING;
        int normal = ObjMeshData.VertexRef.MISSING;
        if (indices.length >= 2 && !indices[1].isEmpty()) {
            texture = parseIndex(indices[1], parsed.textureCoordinates.size(), "texture coordinate", objId, lineNumber);
        }
        if (indices.length == 3 && !indices[2].isEmpty()) {
            normal = parseIndex(indices[2], parsed.normals.size(), "normal", objId, lineNumber);
        }
        return new ObjMeshData.VertexRef(position, texture, normal);
    }

    private static int parseIndex(String value,
                                  int currentSize,
                                  String kind,
                                  Identifier objId,
                                  int lineNumber) throws IOException {
        final int rawIndex;
        try {
            rawIndex = Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw parseError(objId, lineNumber, "invalid " + kind + " index '" + value + "'", exception);
        }
        if (rawIndex == 0) {
            throw parseError(objId, lineNumber, kind + " index 0 is invalid in OBJ", null);
        }

        int resolved = rawIndex > 0 ? rawIndex - 1 : currentSize + rawIndex;
        if (resolved < 0 || resolved >= currentSize) {
            throw parseError(objId, lineNumber,
                    kind + " index " + rawIndex + " resolves outside the available range of " + currentSize,
                    null);
        }
        return resolved;
    }

    private static String extractMapKdPath(String value, Identifier mtlId, int lineNumber) throws IOException {
        List<String> tokens = tokenize(value);
        int index = 0;
        while (index < tokens.size() && tokens.get(index).startsWith("-")) {
            String option = tokens.get(index++).toLowerCase(Locale.ROOT);
            int argumentCount = switch (option) {
                case "-mm" -> 2;
                case "-o", "-s", "-t" -> variableNumericOptionLength(tokens, index);
                case "-blendu", "-blendv", "-boost", "-bm", "-cc", "-clamp", "-imfchan", "-texres", "-type" -> 1;
                default -> throw parseError(mtlId, lineNumber, "unsupported map_Kd option '" + option + "'", null);
            };
            if (index + argumentCount > tokens.size()) {
                throw parseError(mtlId, lineNumber, "map_Kd option '" + option + "' is missing an argument", null);
            }
            index += argumentCount;
        }

        if (index >= tokens.size()) {
            throw parseError(mtlId, lineNumber, "map_Kd requires a texture resource", null);
        }
        return String.join(" ", tokens.subList(index, tokens.size()));
    }

    private static int variableNumericOptionLength(List<String> tokens, int start) {
        int count = 0;
        while (start + count < tokens.size() && count < 3 && isFloat(tokens.get(start + count))) {
            count++;
        }
        return Math.max(count, 1);
    }

    private static boolean isFloat(String value) {
        try {
            Float.parseFloat(value);
            return true;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static Identifier resolveTextureSprite(Identifier mtlId, String reference, int lineNumber) throws IOException {
        rejectOperatingSystemPath(reference, mtlId, lineNumber);
        Identifier resolved = reference.contains(":")
                ? parseIdentifier(reference, mtlId, lineNumber)
                : resolveRelativeResource(mtlId, reference, mtlId, lineNumber);

        String path = resolved.getPath();
        if (path.startsWith("models/")) {
            path = "textures/" + path.substring("models/".length());
        } else if (!path.startsWith("textures/")) {
            path = "textures/" + path;
        }
        path = path.substring("textures/".length());
        if (path.endsWith(".png")) {
            path = path.substring(0, path.length() - ".png".length());
        }
        return new Identifier(resolved.getNamespace(), path);
    }

    private static Identifier resolveRelativeResource(Identifier base,
                                                      String reference,
                                                      Identifier errorResource,
                                                      int lineNumber) throws IOException {
        rejectOperatingSystemPath(reference, errorResource, lineNumber);
        if (reference.contains(":")) {
            return parseIdentifier(reference, errorResource, lineNumber);
        }

        String basePath = base.getPath();
        int lastSlash = basePath.lastIndexOf('/');
        String parent = lastSlash < 0 ? "" : basePath.substring(0, lastSlash + 1);
        Deque<String> parts = new ArrayDeque<>();
        appendPath(parts, parent, errorResource, lineNumber);
        appendPath(parts, reference, errorResource, lineNumber);
        return new Identifier(base.getNamespace(), String.join("/", parts));
    }

    private static void appendPath(Deque<String> parts,
                                   String path,
                                   Identifier errorResource,
                                   int lineNumber) throws IOException {
        for (String part : path.split("/")) {
            if (part.isEmpty() || part.equals(".")) {
                continue;
            }
            if (part.equals("..")) {
                if (parts.isEmpty()) {
                    throw parseError(errorResource, lineNumber, "resource reference escapes its namespace root", null);
                }
                parts.removeLast();
            } else {
                parts.addLast(part);
            }
        }
    }

    private static Identifier parseIdentifier(String value, Identifier errorResource, int lineNumber) throws IOException {
        Identifier identifier = Identifier.tryParse(value);
        if (identifier == null) {
            throw parseError(errorResource, lineNumber, "invalid Minecraft resource identifier '" + value + "'", null);
        }
        return identifier;
    }

    private static void rejectOperatingSystemPath(String value, Identifier resource, int lineNumber) throws IOException {
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw parseError(resource, lineNumber, "empty resource reference", null);
        }
        if (trimmed.startsWith("/")
                || trimmed.startsWith("\\")
                || trimmed.contains("\\")
                || trimmed.matches("^[A-Za-z]:.*")
                || trimmed.toLowerCase(Locale.ROOT).startsWith("file:")) {
            throw parseError(resource, lineNumber,
                    "operating-system paths are not allowed; use a Minecraft resource reference",
                    null);
        }
    }

    private static void requireComponentCount(List<String> parts,
                                              int minimum,
                                              String directive,
                                              Identifier resource,
                                              int lineNumber) throws IOException {
        if (parts.size() < minimum) {
            throw parseError(resource, lineNumber,
                    directive + " requires at least " + minimum + " numeric components",
                    null);
        }
    }

    private static float parseFiniteFloat(String value,
                                          String description,
                                          Identifier resource,
                                          int lineNumber) throws IOException {
        final float parsed;
        try {
            parsed = Float.parseFloat(value);
        } catch (NumberFormatException exception) {
            throw parseError(resource, lineNumber, description + " is not a valid number", exception);
        }
        if (!Float.isFinite(parsed)) {
            throw parseError(resource, lineNumber, description + " must be finite", null);
        }
        return parsed;
    }

    private static String stripComment(String line) {
        int comment = line.indexOf('#');
        return comment < 0 ? line : line.substring(0, comment);
    }

    private static Directive splitDirective(String line) {
        int split = 0;
        while (split < line.length() && !Character.isWhitespace(line.charAt(split))) {
            split++;
        }
        String name = line.substring(0, split);
        String value = split == line.length() ? "" : line.substring(split).trim();
        return new Directive(name, value);
    }

    private static List<String> tokenize(String value) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        char quote = 0;

        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (quote != 0) {
                if (character == quote) {
                    quote = 0;
                } else {
                    current.append(character);
                }
            } else if (character == '\'' || character == '"') {
                quote = character;
            } else if (Character.isWhitespace(character)) {
                if (!current.isEmpty()) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(character);
            }
        }
        if (!current.isEmpty()) {
            tokens.add(current.toString());
        }
        return tokens;
    }

    private static IOException parseError(Identifier resource, int lineNumber, String message, Throwable cause) {
        String fullMessage = resource + " line " + lineNumber + ": " + (message == null ? "parse failure" : message);
        return cause == null ? new IOException(fullMessage) : new IOException(fullMessage, cause);
    }

    private record Directive(String name, String value) {
    }

    private static final class ParsedObj {
        private final List<ObjMeshData.Vec3> positions = new ArrayList<>();
        private final List<ObjMeshData.Vec2> textureCoordinates = new ArrayList<>();
        private final List<ObjMeshData.Vec3> normals = new ArrayList<>();
        private final List<ObjMeshData.Face> faces = new ArrayList<>();
        private final List<Identifier> declaredMtlIds = new ArrayList<>();
        private final Set<String> objectNames = new LinkedHashSet<>();
        private final Set<String> groupNames = new LinkedHashSet<>();
        private int sourceTriangleCount;
        private int sourceQuadCount;
        private int triangulatedNgonCount;
        private int triangulatedNgonTriangleCount;
    }
}
