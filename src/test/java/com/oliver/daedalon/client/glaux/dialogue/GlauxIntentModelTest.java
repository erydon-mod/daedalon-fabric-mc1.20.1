package com.oliver.daedalon.client.glaux.dialogue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class GlauxIntentModelTest {
    private static GlauxIntentModel model;
    @BeforeAll static void load() throws IOException { model = GlauxIntentModel.load(); }

    @Test void matchesFrozenJavaScriptHybridInference() {
        // Fixed outputs from the independently executed original JavaScript
        // inference, not labels invented to mirror this Java implementation.
        String[] questions = {"Who are you?", "Which mod has sculpture?", "How do I make an imposing entrance?", "How many materials should I use?", "Could you explain that without jargon?", "Et le toit — pas gothique ? 🦉"};
        String[] ids = {"identity", "daedalon_scope", "grand_entrance", "coherent_palette", "simplify", "website_handoff"};
        double[] confidence = {.9995940332226988, .9984720882503204, .7065429580261449, .8918818524413097, .9816085706420627, .4794787568951354};
        for (int i = 0; i < questions.length; i++) {
            var prediction = model.predict(questions[i]);
            assertEquals(ids[i], prediction.intentId(), questions[i]);
            assertEquals(confidence[i], prediction.confidence(), 2e-7, questions[i]);
            assertEquals(5, prediction.candidates().size());
            assertTrue(Double.isFinite(prediction.margin()));
        }
    }

    @Test void preservesProbabilityOrderingAndDoesNotMutateAcrossCalls() {
        var before = model.predict("How many materials should I use?");
        model.predict("𐐀 🦉 — [unrecognised text]");
        var after = model.predict("How many materials should I use?");
        assertEquals(before, after);
        for (int i = 1; i < before.candidates().size(); i++) assertTrue(before.candidates().get(i - 1).score() >= before.candidates().get(i).score());
        assertThrows(UnsupportedOperationException.class, () -> before.candidates().clear());
    }

    @Test void lexicalNormalisationMatchesReviewedEnglishRules() {
        assertEquals("what is the color in meters", GlauxIntentModel.normalise("What's the colour in metres?"));
        assertEquals("i am not sure", GlauxIntentModel.normalise("I’m not sure!"));
    }

    @Test void packagedBrainFitsTheExplicitResourceBudget() throws IOException {
        long total = 0;
        for (String file : new String[]{"model.json.gz", "answers.json", "collection.json", "MODEL-LICENSE.txt", "provenance.json"}) {
            try (var input = GlauxIntentModel.class.getResourceAsStream("/assets/daedalon/glaux/brain/" + file)) {
                assertNotNull(input, file);
                if (file.endsWith(".gz")) { try (var gzip = new java.util.zip.GZIPInputStream(input)) { total += gzip.readAllBytes().length; } }
                else total += input.readAllBytes().length;
            }
        }
        assertTrue(total > 2_000_000, "Actual learned weights must be packaged");
        assertTrue(total <= 5_000_000, "Glaux uncompressed brain resource budget exceeded: " + total);
    }
}
