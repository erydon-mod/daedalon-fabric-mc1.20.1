package com.oliver.daedalon.client.glaux.dialogue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

/** Immutable Java inference for the reviewed, quantized Potion/word-character model.
 * No network access, generation, command execution or learning from player text.
 */
public final class GlauxIntentModel {
    private static final String ROOT = "/assets/daedalon/glaux/brain/";
    private static final Pattern WORDS = Pattern.compile("[\\p{L}\\p{N}]+");
    private static final Pattern SPACING = Pattern.compile("([\\p{P}\\p{S}\\u4e00-\\u9fff])");
    private final String[] intents;
    private final int dimensions;
    private final Map<String, Integer> vocabulary;
    private final byte[] embedding;
    private final float[] embeddingScale;
    private final byte[] prototypes;
    private final int[] prototypeClasses;
    private final float[] prototypeScale;
    private final float[] centroids;
    private final float[] head;
    private final double[] bias;
    private final double knnWeight, centroidWeight, headWeight, headScale, temperature;
    private final double semanticWeight, lexicalWeight;
    private final Lexical lexical;

    public record Candidate(String id, double score) { }
    public record Prediction(String intentId, double confidence, double margin,
                             List<Candidate> candidates, double unknownWordRatio) {
        public boolean confidentlyRecognised() { return confidence >= .5 && margin >= .08; }
    }

    public static GlauxIntentModel load() throws IOException {
        try (InputStream raw = GlauxIntentModel.class.getResourceAsStream(ROOT + "model.json.gz")) {
            if (raw == null) throw new IOException("Glaux model resource is missing");
            try (GZIPInputStream gzip = new GZIPInputStream(raw);
                 InputStreamReader reader = new InputStreamReader(gzip, StandardCharsets.UTF_8)) {
                return new GlauxIntentModel(JsonParser.parseReader(reader).getAsJsonObject());
            }
        }
    }

    private GlauxIntentModel(JsonObject model) throws IOException {
        if (model.get("schemaVersion").getAsInt() != 2
                || !"glaux-potion-semantic-intent".equals(model.get("kind").getAsString())
                || !model.get("trained").getAsBoolean()) throw new IOException("Unsupported Glaux model");
        intents = strings(model.getAsJsonArray("intents"));
        dimensions = model.get("dimensions").getAsInt();
        if (dimensions < 8 || dimensions > 256 || intents.length < 2 || intents.length > 200)
            throw new IOException("Invalid Glaux model dimensions");
        String[] tokens = strings(model.getAsJsonArray("vocabulary"));
        vocabulary = new HashMap<>(tokens.length * 2);
        for (int i = 0; i < tokens.length; i++) vocabulary.put(tokens[i], i);
        embedding = bytes(model, "embeddingBase64");
        embeddingScale = floats(model, "embeddingScaleBase64", tokens.length);
        prototypes = bytes(model, "prototypeBase64");
        prototypeClasses = integers(model.getAsJsonArray("prototypeClasses"));
        prototypeScale = floats(model, "prototypeScaleBase64", prototypeClasses.length);
        centroids = floats(model, "centroidBase64", dimensions * intents.length);
        head = floats(model, "headBase64", dimensions * intents.length);
        bias = doubles(model.getAsJsonArray("bias"));
        if (embedding.length != tokens.length * dimensions || prototypes.length != prototypeClasses.length * dimensions
                || bias.length != intents.length) throw new IOException("Incomplete Glaux weights");
        for (int c : prototypeClasses) if (c < 0 || c >= intents.length) throw new IOException("Invalid Glaux prototype");
        JsonObject mixture = model.getAsJsonObject("mixture");
        knnWeight = mixture.get("knn").getAsDouble(); centroidWeight = mixture.get("centroid").getAsDouble();
        headWeight = mixture.get("head").getAsDouble(); headScale = mixture.get("headScale").getAsDouble();
        temperature = model.get("temperature").getAsDouble();
        JsonObject hybrid = model.getAsJsonObject("hybrid");
        semanticWeight = hybrid.get("semanticWeight").getAsDouble(); lexicalWeight = hybrid.get("lexicalWeight").getAsDouble();
        lexical = new Lexical(model.getAsJsonObject("lexicalModel"), intents);
    }

    public Prediction predict(String raw) {
        String text = raw == null ? "" : raw;
        Tokens tokens = tokenize(text.substring(0, Math.min(2000, text.length())));
        double[] vector = new double[dimensions];
        for (int token : tokens.ids) {
            int offset = token * dimensions;
            for (int d = 0; d < dimensions; d++) vector[d] += embedding[offset + d] * (double) embeddingScale[token];
        }
        normalise(vector);
        int classes = intents.length;
        double[][] top = new double[classes][3];
        for (double[] values : top) Arrays.fill(values, Double.NEGATIVE_INFINITY);
        for (int p = 0; p < prototypeClasses.length; p++) {
            double score = 0; int offset = p * dimensions;
            for (int d = 0; d < dimensions; d++) score += vector[d] * prototypes[offset + d] * prototypeScale[p];
            double[] values = top[prototypeClasses[p]];
            if (score > values[0]) { values[2] = values[1]; values[1] = values[0]; values[0] = score; }
            else if (score > values[1]) { values[2] = values[1]; values[1] = score; }
            else if (score > values[2]) values[2] = score;
        }
        double[] probabilities = new double[classes];
        for (int c = 0; c < classes; c++) {
            double centre = 0, classHead = bias[c];
            for (int d = 0; d < dimensions; d++) {
                centre += vector[d] * centroids[c * dimensions + d];
                classHead += vector[d] * head[d * classes + c];
            }
            double[] t = top[c];
            double nearest = t[0] * .65 + (t[0] + t[1] + t[2]) / 3 * .35;
            probabilities[c] = (knnWeight * nearest + centroidWeight * centre + headWeight * headScale * classHead) / temperature;
        }
        softmax(probabilities);
        double[] wordProbabilities = lexical.predict(text);
        for (int c = 0; c < classes; c++) probabilities[c] = probabilities[c] * semanticWeight + wordProbabilities[c] * lexicalWeight;
        List<Candidate> candidates = new ArrayList<>(classes);
        for (int c = 0; c < classes; c++) candidates.add(new Candidate(intents[c], probabilities[c]));
        candidates.sort(Comparator.comparingDouble(Candidate::score).reversed());
        double confidence = candidates.get(0).score, margin = confidence - candidates.get(1).score;
        return new Prediction(candidates.get(0).id, confidence, margin, List.copyOf(candidates.subList(0, 5)), tokens.unknownRatio);
    }

    private Tokens tokenize(String raw) {
        String text = Normalizer.normalize(raw.toLowerCase(Locale.ROOT).replace('’', '\''), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").replaceAll("\\p{C}", " ");
        text = SPACING.matcher(text).replaceAll(" $1 ");
        String[] words = text.trim().isEmpty() ? new String[0] : text.trim().split("\\s+");
        List<Integer> result = new ArrayList<>(); int unknown = 0;
        for (String word : words) {
            if (word.length() > 100) { unknown++; continue; }
            int start = 0; List<Integer> pieces = new ArrayList<>(); boolean failed = false;
            while (start < word.length()) {
                Integer found = null; int end = word.length();
                for (; end > start; end--) {
                    found = vocabulary.get((start == 0 ? "" : "##") + word.substring(start, end));
                    if (found != null) break;
                }
                if (found == null) { failed = true; break; }
                pieces.add(found); start = end;
            }
            if (failed) unknown++;
            else for (int id : pieces) if (id >= 5) result.add(id);
        }
        return new Tokens(result, unknown / (double) Math.max(1, words.length));
    }
    private record Tokens(List<Integer> ids, double unknownRatio) { }

    public static String normalise(String raw) {
        String text = Normalizer.normalize(raw == null ? "" : raw, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).replace('’', '\'');
        String[][] replacements = {{"what's", "what is"}, {"who's", "who is"}, {"can't", "cannot"}, {"don't", "do not"},
                {"doesn't", "does not"}, {"isn't", "is not"}, {"aren't", "are not"}, {"i'm", "i am"}, {"it's", "it is"},
                {"colour", "color"}, {"colours", "colors"}, {"metres", "meters"}, {"metre", "meter"}};
        for (String[] pair : replacements) text = text.replace(pair[0], pair[1]);
        Matcher matcher = WORDS.matcher(text); StringBuilder out = new StringBuilder();
        while (matcher.find()) { if (!out.isEmpty()) out.append(' '); out.append(matcher.group()); }
        return out.toString();
    }

    private static final class Lexical {
        final int dimensions, maximumCharacters;
        final boolean partitioned;
        final double wordWeight, bigramWeight, wordGroupWeight, characterGroupWeight, temperature;
        final int[] ngrams;
        final double[] characterWeights, scales, bias;
        final byte[] weights;
        final float[] idf;

        Lexical(JsonObject model, String[] intents) throws IOException {
            JsonObject config = model.getAsJsonObject("featureConfig"); dimensions = config.get("dimensions").getAsInt();
            if (!Arrays.equals(strings(model.getAsJsonArray("intents")), intents)) throw new IOException("Glaux classifier labels differ");
            partitioned = "word-first-half-character-second-half".equals(config.get("partition").getAsString());
            maximumCharacters = config.get("maximumFeatureCharacters").getAsInt(); wordWeight = config.get("wordWeight").getAsDouble();
            bigramWeight = config.get("bigramWeight").getAsDouble();
            wordGroupWeight = config.has("wordGroupWeight") ? config.get("wordGroupWeight").getAsDouble() : 1;
            characterGroupWeight = config.has("characterGroupWeight") ? config.get("characterGroupWeight").getAsDouble() : 1;
            JsonObject chars = config.getAsJsonObject("characterWeights"); ngrams = chars.keySet().stream().mapToInt(Integer::parseInt).sorted().toArray();
            characterWeights = Arrays.stream(ngrams).mapToDouble(n -> chars.get(Integer.toString(n)).getAsDouble()).toArray();
            scales = doubles(model.getAsJsonArray("scale")); bias = doubles(model.getAsJsonArray("bias"));
            weights = bytes(model, "weightsBase64"); idf = floats(model, "idfBase64", dimensions);
            temperature = model.get("temperature").getAsDouble();
            if (weights.length != dimensions * intents.length || scales.length != intents.length || bias.length != intents.length)
                throw new IOException("Incomplete Glaux lexical weights");
        }

        int hash(String text) {
            int value = 0x811c9dc5;
            for (int i = 0; i < text.length(); i++) value = (value ^ text.charAt(i)) * 16777619;
            int modulus = partitioned ? dimensions / 2 : dimensions;
            return Integer.remainderUnsigned(value, modulus) + (partitioned && text.charAt(0) == 'c' ? dimensions / 2 : 0);
        }

        void add(Map<Integer, Double> counts, String token, double weight) { counts.merge(hash(token), weight, Double::sum); }
        double[] predict(String raw) {
            String normalized = normalise(raw); normalized = normalized.substring(0, Math.min(maximumCharacters, normalized.length()));
            String[] words = normalized.isEmpty() ? new String[0] : normalized.split(" "); Map<Integer, Double> counts = new LinkedHashMap<>();
            for (String word : words) add(counts, "w:" + word, wordWeight);
            for (int i = 1; i < words.length; i++) add(counts, "b:" + words[i - 1] + " " + words[i], bigramWeight);
            String characters = "<" + normalized + ">";
            for (int k = 0; k < ngrams.length; k++) for (int i = 0; i <= characters.length() - ngrams[k]; i++)
                add(counts, "c" + ngrams[k] + ":" + characters.substring(i, i + ngrams[k]), characterWeights[k]);
            int[] ids = new int[counts.size()]; double[] values = new double[counts.size()]; int n = 0;
            for (Map.Entry<Integer, Double> entry : counts.entrySet()) {
                double value = Math.sqrt(entry.getValue()) * idf[entry.getKey()];
                if (value > 0) { ids[n] = entry.getKey(); values[n] = value; n++; }
            }
            if (partitioned) {
                double wordSquare = 0, charSquare = 0;
                for (int i = 0; i < n; i++) { if (ids[i] < dimensions / 2) wordSquare += values[i] * values[i]; else charSquare += values[i] * values[i]; }
                double wordNorm = Math.sqrt(wordSquare), charNorm = Math.sqrt(charSquare);
                for (int i = 0; i < n; i++) values[i] *= ids[i] < dimensions / 2 ? wordGroupWeight / (wordNorm == 0 ? 1 : wordNorm) : characterGroupWeight / (charNorm == 0 ? 1 : charNorm);
            }
            normalise(values);
            double[] logits = bias.clone(); int classes = logits.length;
            for (int i = 0; i < n; i++) for (int c = 0; c < classes; c++) logits[c] += values[i] * weights[ids[i] * classes + c] * scales[c];
            for (int c = 0; c < classes; c++) logits[c] /= temperature;
            softmax(logits); return logits;
        }
    }

    private static void normalise(double[] values) { double square = 0; for (double value : values) square += value * value; double norm = Math.sqrt(square); if (norm != 0) for (int i = 0; i < values.length; i++) values[i] /= norm; }
    private static void softmax(double[] values) { double maximum = Arrays.stream(values).max().orElse(0), sum = 0; for (int i = 0; i < values.length; i++) { values[i] = Math.exp(values[i] - maximum); sum += values[i]; } for (int i = 0; i < values.length; i++) values[i] /= sum; }
    private static byte[] bytes(JsonObject model, String key) { return Base64.getDecoder().decode(model.get(key).getAsString()); }
    private static float[] floats(JsonObject model, String key, int count) throws IOException { byte[] bytes = bytes(model, key); if (bytes.length != count * 4) throw new IOException("Invalid Glaux float table"); ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN); float[] out = new float[count]; for (int i = 0; i < count; i++) out[i] = buffer.getFloat(); return out; }
    private static String[] strings(JsonArray array) { String[] out = new String[array.size()]; for (int i = 0; i < out.length; i++) out[i] = array.get(i).getAsString(); return out; }
    private static int[] integers(JsonArray array) { int[] out = new int[array.size()]; for (int i = 0; i < out.length; i++) out[i] = array.get(i).getAsInt(); return out; }
    private static double[] doubles(JsonArray array) { double[] out = new double[array.size()]; for (int i = 0; i < out.length; i++) out[i] = array.get(i).getAsDouble(); return out; }
}
