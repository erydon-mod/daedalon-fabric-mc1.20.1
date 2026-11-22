package com.oliver.daedalon.client.glaux.dialogue;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Bounded, lower-trust game-to-website context. Creating a link never sends it. */
public final class HandoffCodec {
    public static final int MAXIMUM_QUESTION_CHARACTERS = 2000;
    public static final int MAXIMUM_JSON_BYTES = 6144;
    public static final int MAXIMUM_FRAGMENT_CHARACTERS = 8192;
    public static final Set<String> MOD_IDS = Set.of("erydon", "daedalon", "themelios", "metrakron", "lithotheca");
    public static final Set<String> ROLES = Set.of("user", "local_authored", "local_generated");
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final String TARGET = "https://www.erydon.co.uk/ask#glaux=";
    private HandoffCodec() { }

    /** uri is null only if the exact question cannot fit the bounded link.
     * fallbackText always retains the exact question and available exchange. */
    public record Export(URI uri, String fallbackText, boolean historyTruncated) { }

    public static Export create(String question, List<GlauxDialogueService.Turn> history,
                                GlauxDialogueService.Context context, boolean previouslyTruncated) {
        if (question == null || question.isBlank() || question.length() > MAXIMUM_QUESTION_CHARACTERS)
            throw new IllegalArgumentException("The enquiry must contain 1–2000 characters");
        List<GlauxDialogueService.Turn> original = List.copyOf(history);
        String fallback = fallbackText(question, original);
        if (!wellFormed(question)) return new Export(null, fallback, true);
        List<GlauxDialogueService.Turn> selected = new ArrayList<>(); int previousCharacters = 0;
        boolean truncated = previouslyTruncated;
        // Keep whole turns, most recent first during selection. Never cut a word,
        // surrogate pair or Unicode enquiry to make a URL fit.
        for (int i = original.size() - 1; i >= 0; i--) {
            GlauxDialogueService.Turn turn = original.get(i);
            if (!ROLES.contains(turn.role())) throw new IllegalArgumentException("Unknown local context role");
            if (turn.text() == null || turn.text().isBlank()) continue;
            if (!wellFormed(turn.text())) { truncated = true; continue; }
            // The website treats question as the new pending enquiry, separately
            // from prior context. Do not duplicate it as the last prior user turn.
            if (turn.role().equals("user") && turn.text().equals(question)) continue;
            if (selected.size() >= 8 || turn.text().length() > 1000 || previousCharacters + turn.text().length() > 3000) { truncated = true; continue; }
            selected.add(0, turn); previousCharacters += turn.text().length();
        }
        JsonObject scope = scope(context);
        String id = UUID.randomUUID().toString();
        for (;;) {
            JsonObject payload = payload(id, question, selected, scope, truncated);
            byte[] bytes;
            try {
                ByteBuffer encodedJson = StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(GSON.toJson(payload)));
                bytes = new byte[encodedJson.remaining()]; encodedJson.get(bytes);
            } catch (CharacterCodingException invalidUnicode) { return new Export(null, fallback, true); }
            String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            if (bytes.length <= MAXIMUM_JSON_BYTES && encoded.length() <= MAXIMUM_FRAGMENT_CHARACTERS)
                return new Export(URI.create(TARGET + encoded), fallback, truncated);
            if (!selected.isEmpty()) { selected.remove(0); truncated = true; }
            else if (scope != null) { scope = null; truncated = true; }
            else return new Export(null, fallback, true);
        }
    }

    static JsonObject scope(GlauxDialogueService.Context context) {
        if (context == null) return null;
        JsonObject result = new JsonObject();
        if (validOptional(context.minecraft(), 32)) result.addProperty("minecraft", context.minecraft());
        if (Set.of("fabric", "forge", "neoforge").contains(context.loader())) result.addProperty("loader", context.loader());
        if (context.modsKnown()) {
            JsonObject mods = new JsonObject();
            for (String id : MOD_IDS.stream().sorted().toList()) {
                String version = context.mods().get(id);
                if (validOptional(version, 64)) mods.addProperty(id, version);
            }
            if (!mods.keySet().isEmpty()) result.add("mods", mods);
        }
        return result.keySet().isEmpty() ? null : result;
    }

    private static boolean validOptional(String value, int maximum) { return value != null && !value.isBlank() && value.length() <= maximum && wellFormed(value); }
    private static boolean wellFormed(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isHighSurrogate(c)) { if (i + 1 >= text.length() || !Character.isLowSurrogate(text.charAt(++i))) return false; }
            else if (Character.isLowSurrogate(c)) return false;
        }
        return true;
    }
    private static JsonObject payload(String id, String question, List<GlauxDialogueService.Turn> turns, JsonObject scope, boolean truncated) {
        JsonObject payload = new JsonObject(); payload.addProperty("v", 1); payload.addProperty("id", id);
        payload.addProperty("source", "daedalon"); payload.addProperty("question", question);
        JsonArray context = new JsonArray();
        for (GlauxDialogueService.Turn turn : turns) { JsonObject value = new JsonObject(); value.addProperty("role", turn.role()); value.addProperty("text", turn.text()); context.add(value); }
        payload.add("turns", context); if (scope != null) payload.add("scope", scope); payload.addProperty("historyTruncated", truncated);
        return payload;
    }

    public static String fallbackText(String question, List<GlauxDialogueService.Turn> history) {
        StringBuilder out = new StringBuilder(question);
        if (!history.isEmpty()) { out.append("\n\nRecent Glaux conversation (local reference context):\n");
            for (GlauxDialogueService.Turn turn : history) out.append("user".equals(turn.role()) ? "You: " : "Glaux (local): ").append(turn.text()).append('\n'); }
        return out.toString();
    }

    /** Strict decoder used by contract checks; imported strings are data only. */
    public static JsonObject decode(String fragment) {
        String encoded = fragment.startsWith("glaux=") ? fragment.substring(6) : fragment;
        if (encoded.isEmpty() || encoded.length() > MAXIMUM_FRAGMENT_CHARACTERS || !encoded.matches("[A-Za-z0-9_-]+"))
            throw new IllegalArgumentException("Invalid Glaux fragment");
        byte[] bytes;
        try { bytes = Base64.getUrlDecoder().decode(encoded); } catch (IllegalArgumentException e) { throw new IllegalArgumentException("Invalid Glaux encoding", e); }
        if (bytes.length > MAXIMUM_JSON_BYTES) throw new IllegalArgumentException("Glaux fragment is too large");
        String json;
        try { json = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString(); }
        catch (CharacterCodingException e) { throw new IllegalArgumentException("Invalid Glaux UTF-8", e); }
        JsonElement parsed;
        try (JsonReader reader = new JsonReader(new StringReader(json))) {
            reader.setLenient(false); parsed = read(reader, 0);
            if (reader.peek() != JsonToken.END_DOCUMENT) throw new IOException("Trailing data");
        } catch (IOException | RuntimeException e) { throw new IllegalArgumentException("Invalid Glaux JSON", e); }
        if (!parsed.isJsonObject()) throw new IllegalArgumentException("Invalid Glaux payload");
        JsonObject value = parsed.getAsJsonObject();
        keys(value, Set.of("v", "id", "source", "question", "turns", "scope", "historyTruncated"), Set.of("v", "id", "source", "question", "turns", "historyTruncated"));
        if (!value.get("v").isJsonPrimitive() || !value.get("v").getAsJsonPrimitive().isNumber() || !"1".equals(value.get("v").getAsString())) throw new IllegalArgumentException("Unknown Glaux version");
        String id = string(value.get("id"), 36); if (!UUID.fromString(id).toString().equalsIgnoreCase(id)) throw new IllegalArgumentException("Invalid Glaux ID");
        if (!"daedalon".equals(string(value.get("source"), 16))) throw new IllegalArgumentException("Invalid Glaux source");
        string(value.get("question"), MAXIMUM_QUESTION_CHARACTERS);
        if (!value.get("historyTruncated").isJsonPrimitive() || !value.get("historyTruncated").getAsJsonPrimitive().isBoolean()) throw new IllegalArgumentException("Invalid Glaux truncation marker");
        if (!value.get("turns").isJsonArray() || value.getAsJsonArray("turns").size() > 8) throw new IllegalArgumentException("Too many Glaux turns");
        int characters = 0;
        for (JsonElement entry : value.getAsJsonArray("turns")) {
            if (!entry.isJsonObject()) throw new IllegalArgumentException("Invalid Glaux turn"); JsonObject turn = entry.getAsJsonObject();
            keys(turn, Set.of("role", "text"), Set.of("role", "text"));
            if (!ROLES.contains(string(turn.get("role"), 32))) throw new IllegalArgumentException("Unknown Glaux role");
            characters += string(turn.get("text"), 1000).length();
        }
        JsonArray priorTurns = value.getAsJsonArray("turns");
        for (int i = priorTurns.size() - 1; i >= 0; i--) {
            JsonObject turn = priorTurns.get(i).getAsJsonObject();
            if ("user".equals(turn.get("role").getAsString())) {
                if (turn.get("text").getAsString().equals(value.get("question").getAsString()))
                    throw new IllegalArgumentException("Pending Glaux enquiry duplicated in prior context");
                break;
            }
        }
        if (characters > 3000) throw new IllegalArgumentException("Too much Glaux context");
        if (value.has("scope")) {
            if (!value.get("scope").isJsonObject()) throw new IllegalArgumentException("Invalid Glaux scope"); JsonObject scope = value.getAsJsonObject("scope");
            keys(scope, Set.of("minecraft", "loader", "mods"), Set.of());
            if (scope.has("minecraft")) string(scope.get("minecraft"), 32);
            if (scope.has("loader") && !Set.of("fabric", "forge", "neoforge").contains(string(scope.get("loader"), 16))) throw new IllegalArgumentException("Unknown Glaux loader");
            if (scope.has("mods")) {
                if (!scope.get("mods").isJsonObject()) throw new IllegalArgumentException("Invalid Glaux mods"); JsonObject mods = scope.getAsJsonObject("mods");
                keys(mods, MOD_IDS, Set.of()); if (mods.size() > 5) throw new IllegalArgumentException("Too many Glaux mods");
                for (String mod : mods.keySet()) string(mods.get(mod), 64);
            }
        }
        return value;
    }

    private static String string(JsonElement value, int maximum) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw new IllegalArgumentException("Expected Glaux text");
        String text = value.getAsString(); if (text.isBlank() || text.length() > maximum) throw new IllegalArgumentException("Invalid Glaux text length"); return text;
    }
    private static void keys(JsonObject object, Set<String> allowed, Set<String> required) {
        if (!allowed.containsAll(object.keySet()) || !object.keySet().containsAll(required)) throw new IllegalArgumentException("Unknown or missing Glaux fields");
    }
    private static JsonElement read(JsonReader reader, int depth) throws IOException {
        if (depth > 8) throw new IOException("Glaux JSON is too deep");
        return switch (reader.peek()) {
            case BEGIN_OBJECT -> { JsonObject out = new JsonObject(); Set<String> names = new HashSet<>(); reader.beginObject(); while (reader.hasNext()) { String name = reader.nextName(); if (!names.add(name)) throw new IOException("Duplicate Glaux field"); out.add(name, read(reader, depth + 1)); } reader.endObject(); yield out; }
            case BEGIN_ARRAY -> { JsonArray out = new JsonArray(); reader.beginArray(); while (reader.hasNext()) { if (out.size() >= 64) throw new IOException("Glaux array too long"); out.add(read(reader, depth + 1)); } reader.endArray(); yield out; }
            case STRING -> new JsonPrimitive(reader.nextString());
            case NUMBER -> new JsonPrimitive(new BigDecimal(reader.nextString()));
            case BOOLEAN -> new JsonPrimitive(reader.nextBoolean());
            case NULL -> { reader.nextNull(); yield JsonNull.INSTANCE; }
            default -> throw new IOException("Unexpected Glaux JSON token");
        };
    }
}
