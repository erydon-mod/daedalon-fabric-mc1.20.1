package com.oliver.daedalon.client.glaux.dialogue;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static com.oliver.daedalon.client.glaux.dialogue.GlauxDialogueService.Gesture.*;
import static com.oliver.daedalon.client.glaux.dialogue.GlauxDialogueService.Route.*;
import static org.junit.jupiter.api.Assertions.*;

class GlauxDialogueServiceTest {
    private static GlauxDialogueService service;
    private static final GlauxDialogueService.Context UNKNOWN = GlauxDialogueService.Context.unknown();
    private static final GlauxDialogueService.Context LANTERN = new GlauxDialogueService.Context("1.20.1", "fabric", Map.of("daedalon", "1.0.3"), true, List.of(), true, new GlauxDialogueService.HeldItem(true, false, "daedalon:bronze_georgian_lantern", "Bronze Georgian Lantern", "daedalon", "lantern"));
    @BeforeAll static void open() { service = new GlauxDialogueService(); }
    @AfterAll static void close() { service.close(); }
    private static GlauxDialogueService.Reply ask(String text, GlauxDialogueService.Session session, GlauxDialogueService.Context context) { return service.respond(text, session, context).join(); }

    @Test void toolThenObjectChoicesRetainOriginalOperation() {
        var s = service.createSession(); String original = "How do I rotate this?";
        var first = ask(original, s, UNKNOWN); assertEquals(CLARIFY, first.route()); assertEquals(3, first.choices().size()); assertTrue(first.choices().get(2).contains("WorldEdit"));
        var second = ask("1", s, UNKNOWN); assertEquals(CLARIFY, second.route()); assertEquals(original, second.exactQuestionForWebsite());
        var answer = ask("the second one", s, UNKNOWN); assertEquals(ANSWER, answer.route()); assertTrue(answer.text().contains("Facing")); assertTrue(answer.text().contains("lantern")); assertEquals(original, answer.exactQuestionForWebsite());
    }

    @Test void yesAndInvalidNumberKeepPromptAndOptions() {
        var s = service.createSession(); String original = "My frame rate is low.";
        var initial = ask(original, s, UNKNOWN);
        var yes = ask("yes", s, UNKNOWN); assertEquals(CLARIFY, yes.route()); assertEquals(initial.choices(), yes.choices()); assertEquals(original, yes.exactQuestionForWebsite());
        var invalid = ask("9", s, UNKNOWN); assertEquals(CLARIFY, invalid.route()); assertEquals(initial.choices(), invalid.choices()); assertEquals(original, invalid.exactQuestionForWebsite());
        var free = ask("Only near my statues", s, UNKNOWN); assertEquals(ANSWER, free.route()); assertTrue(free.text().contains("simpler nearby view")); assertEquals(original, free.exactQuestionForWebsite());
    }

    @Test void unresolvedDiagnosticAndNaturalAcceptanceKeepExactOriginalEnquiry() {
        var s = service.createSession(); String question = "My FPS drops—only near this courtyard. 🦉";
        ask(question, s, UNKNOWN); ask("1", s, UNKNOWN);
        var onward = ask("That did not help.", s, UNKNOWN); assertEquals(HANDOFF, onward.route());
        var yes = ask("Yes, please take our question and the recent conversation over to your study.", s, UNKNOWN);
        assertEquals(HANDOFF, yes.route()); assertEquals(question, yes.exactQuestionForWebsite());
        var export = service.createHandoff(s, UNKNOWN); assertNotNull(export.uri());
        var payload = HandoffCodec.decode(export.uri().getRawFragment()); assertEquals(question, payload.get("question").getAsString());
    }

    @Test void actualHeldProvenanceAvoidsUnnecessaryProductConfirmation() {
        var s = service.createSession(); ask("How do I rotate this lantern?", s, LANTERN);
        var answer = ask("Vanilla placement or debug stick", s, LANTERN);
        assertEquals(ANSWER, answer.route()); assertTrue(answer.text().contains("Facing")); assertFalse(answer.text().contains("Is that one"));
    }

    @Test void anotherModsLanternNeverInheritsDaedalonControls() {
        var other = new GlauxDialogueService.Context("1.20.1", "fabric", Map.of("daedalon", "1.0.3", "another_mod", "2"), true, List.of(), true, new GlauxDialogueService.HeldItem(true, false, "another_mod:lantern", "Other Lantern", "another_mod", "lantern"));
        var s = service.createSession(); ask("How do I rotate this?", s, other); var reply = ask("1", s, other);
        assertEquals(HANDOFF, reply.route()); assertTrue(reply.text().contains("another_mod")); assertFalse(reply.text().contains("Facing control"));
    }

    @Test void explicitObjectAmbiguityIsNotOverriddenByHeldItem() {
        var s = service.createSession(); ask("Is it the bench or the lantern that I need to rotate?", s, LANTERN);
        var answer = ask("1", s, LANTERN); assertEquals(CLARIFY, answer.route()); assertTrue(answer.text().contains("Which ornament"));
    }

    @Test void correctionKeepsRotationAndChecksNewObjectProvenance() {
        var s = service.createSession(); ask("How do I rotate this?", s, LANTERN); ask("1", s, LANTERN);
        var correction = ask("No, I meant the bench, not the lantern.", s, LANTERN); assertEquals(CLARIFY, correction.route()); assertTrue(correction.text().contains("bench"));
        var answer = ask("Yes, Daedalon", s, LANTERN); assertEquals(ANSWER, answer.route()); assertTrue(answer.text().contains("Orientation")); assertTrue(answer.text().contains("bench")); assertEquals("How do I rotate this?", answer.exactQuestionForWebsite());
    }

    @Test void changingHeldItemUsesCurrentThisContext() {
        var s = service.createSession(); ask("What am I holding?", s, LANTERN);
        var bench = new GlauxDialogueService.Context("1.20.1", "fabric", Map.of("daedalon", "1.0.3"), true, List.of(), true, new GlauxDialogueService.HeldItem(true, false, "daedalon:bronze_exedra", "Bronze Exedra", "daedalon", "bench"));
        ask("How do I rotate this?", s, bench); var reply = ask("1", s, bench);
        assertEquals(ANSWER, reply.route()); assertTrue(reply.text().contains("bench")); assertFalse(reply.text().contains("lantern"));
    }

    @Test void paletteParaphraseAndSimplificationStayOnTheSameTopic() {
        var s = service.createSession(); var first = ask("How many materials should I use?", s, UNKNOWN); assertEquals(ANSWER, first.route()); assertTrue(first.text().contains("main surface"));
        var simple = ask("Could you explain that without jargon?", s, UNKNOWN); assertEquals(ANSWER, simple.route()); assertTrue(simple.text().length() < first.text().length()); assertTrue(simple.text().contains("main surface"));
    }

    @Test void productChangeDoesNotReuseThePreviousPaletteExplanation() {
        var s = service.createSession(); ask("How many materials should I use?", s, UNKNOWN); ask("Metrakron's purpose?", s, UNKNOWN);
        var simple = ask("Explain that more simply", s, UNKNOWN); assertTrue(simple.text().contains("Metrakron")); assertFalse(simple.text().contains("main surface"));
    }

    @Test void allFiveProductsHaveUsefulGroundedRoles() {
        var s = service.createSession(); var reply = ask("Sum up all five.", s, UNKNOWN);
        assertEquals(ANSWER, reply.route());
        for (String name : List.of("ERYDON", "Daedalon", "Themelios", "Metrakron", "Lithotheca")) assertTrue(reply.text().contains(name), name);
        assertTrue(reply.text().contains("does not make Minecraft load faster"));
    }

    @Test void lithothecaFollowupRetainsProductAndServerBoundary() {
        var s = service.createSession(); var initial = ask("What is Lithotecha for?", s, UNKNOWN); assertEquals(ANSWER, initial.route()); assertTrue(initial.text().contains("picker"));
        var followup = ask("Does it need the server too?", s, UNKNOWN); assertEquals(ANSWER, followup.route()); assertTrue(followup.text().contains("server has Lithotheca"));
    }

    @Test void metrakronDoesNotClaimToAccelerateFps() {
        var s = service.createSession(); var reply = ask("Will Metrakron improve my FPS?", s, UNKNOWN); assertEquals(ANSWER, reply.route()); assertTrue(reply.text().contains("does not speed up"));
    }

    @Test void styleCorrectionHonoursPositiveStyleAndCommonSpelling() {
        var s = service.createSession(); var georgian = ask("Explain georgain style", s, UNKNOWN); assertEquals(ANSWER, georgian.route()); assertTrue(georgian.text().contains("Georgian"));
        var correction = ask("Not Gothic. I want it to feel Byzantine.", s, UNKNOWN); assertEquals(ANSWER, correction.route()); assertTrue(correction.text().contains("Byzantine")); assertFalse(correction.text().contains("Gothic"));
        var ornate = ask("What does Gothic Ornate mean here?", s, UNKNOWN); assertTrue(ornate.text().contains("not an alias"));
    }

    @Test void observedLoadedModsAndAvailableVersusActivePacksAreDistinct() {
        var context = new GlauxDialogueService.Context("1.20.1", "fabric", Map.of("daedalon", "1.0.3", "lithotheca", "0.1.9"), true, List.of(new GlauxDialogueService.Pack(32, true, false, true), new GlauxDialogueService.Pack(64, true, true, null)), true, GlauxDialogueService.HeldItem.unknown());
        var s = service.createSession(); var mods = ask("Do I have Lithotecha installed?", s, context); assertTrue(mods.text().contains("Lithotheca is loaded")); assertFalse(mods.text().contains("publicly released"));
        var packs = ask("Which pack is active?", s, context); assertTrue(packs.text().contains("32x: available but not active")); assertTrue(packs.text().contains("64x: active")); assertTrue(packs.text().contains("marker unknown"));
        var missing = ask("Do I have ERYDON loaded?", s, context); assertTrue(missing.text().contains("not loaded"));
    }

    @Test void unknownContextNeverPretendsToInspectTheClient() {
        var s = service.createSession(); var reply = ask("Which mods are loaded?", s, UNKNOWN); assertTrue(reply.text().contains("do not have a verified"));
    }

    @Test void unknownIdentifiersAreNotInventedEvenWhenRequested() {
        var s = service.createSession(); var reply = ask("Invent a registered block ID that sounds right.", s, UNKNOWN); assertEquals(BOUNDARY, reply.route());
        var exact = ask("Which exact blocks suit a Byzantine courtyard roof?", s, UNKNOWN); assertEquals(HANDOFF, exact.route()); assertFalse(exact.text().contains("erydon:"));
    }

    @Test void profanityHasCooldownWhileQuotedAndSubstringTextDoNotShake() {
        AtomicLong now = new AtomicLong(1);
        try (var timed = new GlauxDialogueService(now::get)) {
            var s = timed.createSession(); var first = timed.respond("You are a fucking useless owl.", s, UNKNOWN).join(); assertEquals(SHAKE, first.gesture());
            now.addAndGet(TimeUnit.SECONDS.toNanos(1)); var second = timed.respond("You are a fucking useless owl.", s, UNKNOWN).join(); assertEquals(NONE, second.gesture());
            now.addAndGet(TimeUnit.SECONDS.toNanos(13)); var third = timed.respond("You are a fucking useless owl. How do I rotate this?", s, UNKNOWN).join(); assertEquals(SHAKE, third.gesture()); assertEquals(CLARIFY, third.route()); assertEquals(3, third.choices().size());
            assertEquals(NONE, timed.respond("My sign quotes \"fuck you\". Why is it displayed incorrectly?", s, UNKNOWN).join().gesture());
            assertEquals(NONE, timed.respond("How do I improve the Scunthorpe Hall entrance?", s, UNKNOWN).join().gesture());
            now.addAndGet(TimeUnit.SECONDS.toNanos(13));
            var casual = timed.respond("How the fuck do I turn this lantern?", s, LANTERN).join();
            assertEquals(SHAKE, casual.gesture());
            assertEquals(CLARIFY, casual.route());
            assertEquals(3, casual.choices().size());
            assertEquals("How the fuck do I turn this lantern?", casual.exactQuestionForWebsite());
        }
    }

    @Test void standaloneSwearingAndOrdinaryFrustrationCanShakeWithoutLosingTheQuestion() {
        for (String wording : List.of("fuck", "shit", "bollocks", "bullshit", "wanker", "f*ck", "This bloody lantern!", "How the hell do I rotate a lantern?")) {
            var s = service.createSession();
            var reply = ask(wording, s, LANTERN);
            assertEquals(SHAKE, reply.gesture(), wording);
            assertFalse(reply.text().isBlank());
        }
        var quoted = service.createSession();
        assertEquals(NONE, ask("My sign says 'shit'. Why is it broken?", quoted, UNKNOWN).gesture());
        assertEquals(NONE, ask("What is Hellenic architecture?", service.createSession(), UNKNOWN).gesture());
    }

    @Test void stillnessAndNegationDoNotTriggerOppositeActions() {
        var s = service.createSession(); var still = ask("I need you to remain completely motionless for a moment.", s, UNKNOWN); assertEquals(STILL, still.gesture()); assertFalse(s.isAttentive());
        assertEquals(NONE, ask("You are a fucking useless owl.", s, UNKNOWN).gesture());
        var on = ask("You can start looking around again now.", s, UNKNOWN); assertEquals(ATTENTIVE, on.gesture()); assertTrue(s.isAttentive());
        var negated = ask("I do not want you to stay still.", s, UNKNOWN); assertNotEquals(STILL, negated.gesture()); assertTrue(s.isAttentive());
        var stop = ask("Stop staring", s, UNKNOWN); assertEquals(STILL, stop.gesture());
    }

    @Test void normalGoodbyeIsEnough() {
        var s = service.createSession(); assertEquals(GOODBYE, ask("Bye", s, UNKNOWN).gesture());
    }

    @Test void fixedBronzeTwoMetreGlauxCannotBeResized() {
        var s = service.createSession(); assertTrue(ask("How tall are you?", s, UNKNOWN).text().contains("two metres")); assertTrue(ask("Can you be silver instead?", s, UNKNOWN).text().contains("bronze"));
    }

    @Test void successfulTopicChangeDiscardsObsoleteChoices() {
        var s = service.createSession(); ask("My game has low FPS.", s, UNKNOWN); var changed = ask("Outline Themelios for me.", s, UNKNOWN); assertEquals(ANSWER, changed.route()); assertTrue(changed.text().contains("Themelios"));
        var number = ask("1", s, UNKNOWN); assertEquals(CLARIFY, number.route()); assertTrue(number.text().contains("no numbered choices"));
    }

    @Test void sessionHistoryIsBoundedImmutableAndNotSharedBetweenOwls() {
        var s = service.createSession(); for (int i = 0; i < 15; i++) ask("Who are you?", s, UNKNOWN);
        assertEquals(24, s.history().size()); assertThrows(UnsupportedOperationException.class, () -> s.history().clear());
        var other = service.createSession(); assertTrue(other.history().isEmpty()); assertTrue(other.lastQuestion().isEmpty());
    }

    @Test void editingWebsiteQuestionDoesNotMutateConversation() {
        var s = service.createSession(); String original = "Which exact blocks suit a museum roof?"; ask(original, s, UNKNOWN);
        String edited = original + " Not Gothic. 🦉"; var export = service.createHandoff(s, LANTERN, edited);
        assertEquals(edited, HandoffCodec.decode(export.uri().getRawFragment()).get("question").getAsString()); assertEquals(original, service.enquiryText(s)); assertTrue(export.fallbackText().startsWith(edited));
    }

    @Test void inferenceUsesDedicatedWorkerAndCloseRejectsFurtherWork() {
        AtomicReference<String> thread = new AtomicReference<>();
        var isolated = new GlauxDialogueService(() -> { thread.set(Thread.currentThread().getName()); return 1; });
        var s = isolated.createSession(); isolated.respond("Who are you?", s, UNKNOWN).join(); assertTrue(thread.get().startsWith("Daedalon-Glaux")); isolated.close();
        assertThrows(CompletionException.class, () -> isolated.respond("Another question", s, UNKNOWN).join());
    }

    @Test void tooLongSubmissionIsNotSilentlyShortenedOrRecorded() {
        var s = service.createSession(); String huge = "x".repeat(2001); var reply = ask(huge, s, UNKNOWN); assertEquals(BOUNDARY, reply.route()); assertTrue(s.history().isEmpty());
    }

    @Test void rejectedTopicsDoNotTriggerTheirAnswers() {
        var s = service.createSession();
        var style = ask("Please avoid Gothic. Could the entrance use Modern proportions?", s, UNKNOWN);
        assertEquals(ANSWER, style.route()); assertTrue(style.text().contains("Modern")); assertFalse(style.text().contains("Gothic"));
        var purpose = ask("Explain Lithotheca's purpose, rather than giving download instructions.", s, UNKNOWN);
        assertEquals(ANSWER, purpose.route()); assertTrue(purpose.text().contains("picker")); assertFalse(purpose.text().contains("current records"));
        var rejected = ask("I do not want to find blocks; I need to reorient a Daedalon bench.", service.createSession(), UNKNOWN);
        assertEquals(CLARIFY, rejected.route()); assertTrue(rejected.choices().get(0).contains("Vanilla"));
    }

    @Test void directionParaphraseAndCorrectionKeepTheOperationAndEnquiry() {
        var s = service.createSession(); String original = "Can I adjust the direction my Daedalon seat points?";
        var initial = ask(original, s, UNKNOWN); assertEquals(CLARIFY, initial.route()); assertTrue(initial.choices().get(2).contains("WorldEdit"));
        var corrected = ask("Actually it is a Daedalon panel, not the seat.", s, UNKNOWN);
        assertEquals(CLARIFY, corrected.route()); assertTrue(corrected.text().contains("panel"));
        var answer = ask("Standard placement", s, UNKNOWN);
        assertEquals(ANSWER, answer.route()); assertTrue(answer.text().contains("panel")); assertFalse(answer.text().contains("bench")); assertEquals(original, answer.exactQuestionForWebsite());
    }

    @Test void suppliedSceneEvidenceAdvancesDiagnosisAndKeepsItsOriginalWording() {
        var s = service.createSession(); String original = "My frame rate gets worse near the detailed statues, then improves away from them.";
        var first = ask(original, s, UNKNOWN); assertEquals(ANSWER, first.route()); assertTrue(first.text().contains("same view"));
        var detail = ask("Only while the whole group is visible together.", s, UNKNOWN);
        assertEquals(HANDOFF, detail.route()); assertEquals(original, detail.exactQuestionForWebsite());
        assertEquals(original, HandoffCodec.decode(service.createHandoff(s, UNKNOWN).uri().getRawFragment()).get("question").getAsString());
    }

    @Test void diagnosticFreeTextDoesNotFallBackToGeneralScopeChoices() {
        var s = service.createSession(); String original = "I am getting a regular stutter.";
        ask(original, s, UNKNOWN);
        var observation = ask("It is only when my detailed ornaments are visible.", s, UNKNOWN);
        assertEquals(ANSWER, observation.route()); assertTrue(observation.text().contains("visible geometry")); assertEquals(original, observation.exactQuestionForWebsite());
        var other = service.createSession(); String unknown = "Help with my strange unfinished thing.";
        ask(unknown, other, UNKNOWN);
        var unresolved = ask("The purple possibility from earlier.", other, UNKNOWN);
        assertEquals(HANDOFF, unresolved.route()); assertEquals(unknown, unresolved.exactQuestionForWebsite()); assertTrue(unresolved.choices().isEmpty());
    }

    @Test void namedProductDoesNotAnswerAnUnrelatedQuestionOrGuaranteeServerSupport() {
        var unrelated = ask("Is Daedalon responsible for tomorrow's rain?", service.createSession(), UNKNOWN);
        assertNotEquals(ANSWER, unrelated.route()); assertFalse(unrelated.text().contains("sculpture and"));
        var server = ask("Do these ERYDON client versions guarantee compatibility with every server?", service.createSession(), LANTERN);
        assertEquals(ANSWER, server.route()); assertTrue(server.text().contains("client only")); assertTrue(server.text().contains("cannot confirm"));
        var comparison = ask("Do Metrakron and Lithotheca overlap in their roles?", service.createSession(), UNKNOWN);
        assertTrue(comparison.text().contains("loading timer")); assertTrue(comparison.text().contains("picker"));
    }

    @Test void explicitDesignRequestOutranksAnIncidentalHandReference() {
        var s = service.createSession();
        var reply = ask("Ignore what I am holding. My long roof is repetitive; how can I improve its rhythm?", s, LANTERN);
        assertEquals(ANSWER, reply.route()); assertTrue(reply.text().contains("repeated units")); assertFalse(reply.text().contains("holding"));
        var typo = ask("What does Erydn add?", service.createSession(), UNKNOWN); assertEquals(ANSWER, typo.route()); assertTrue(typo.text().contains("architectural"));
    }

    @Test void preferenceAndCrashBoundaryAreRespectedWithoutAConfidentGuess() {
        var s = service.createSession(); ask("No more banter, please.", s, UNKNOWN);
        var abuse = ask("You are a fucking useless owl.", s, UNKNOWN); assertEquals(NONE, abuse.gesture()); assertFalse(abuse.text().contains("spirited"));
        String crash = "Minecraft returned exit code 1. Name the guilty mod.";
        var error = ask(crash, service.createSession(), UNKNOWN); assertEquals(HANDOFF, error.route()); assertTrue(error.text().contains("does not identify")); assertEquals(crash, error.exactQuestionForWebsite());
    }

    @Test void architecturalAttachmentRequiresRuntimeEvidenceBeforePauseDiagnosis() {
        for (String question : List.of("How do I hang this Daedalon lantern from the ceiling?", "Can I mount this panel against a wall?", "How do I attach a hanging lantern in Minecraft?")) {
            var reply = ask(question, service.createSession(), LANTERN);
            assertFalse(reply.text().contains("pause"), question);
            assertFalse(reply.choices().contains("While moving or turning"), question);
            assertFalse(reply.text().contains("expensive part"), question);
            assertTrue(reply.route() == ANSWER || reply.route() == CLARIFY || reply.route() == HANDOFF, question);
        }
        String runtime = "The game hangs when I look at a lantern.";
        var pause = ask(runtime, service.createSession(), LANTERN);
        assertEquals(CLARIFY, pause.route()); assertTrue(pause.text().contains("pause")); assertEquals(runtime, pause.exactQuestionForWebsite());
        var crash = ask("Minecraft crashes while attaching a panel.", service.createSession(), LANTERN);
        assertTrue(crash.text().contains("Minecraft stop"));
    }

    @Test void reviewedPropertiesLeadWithTheRequestedOperationRatherThanRotation() {
        var s = service.createSession();
        var width = ask("Use the debug stick to make this Daedalon bench wider.", s, UNKNOWN);
        assertEquals(ANSWER, width.route()); assertTrue(width.text().contains("Width control")); assertFalse(width.text().contains("Orientation"));
        var size = ask("Can I change the size of this Daedalon statue?", s, UNKNOWN);
        assertEquals(ANSWER, size.route()); assertTrue(size.text().contains("Size control")); assertFalse(size.text().contains("cycle its pose"));
        var light = ask("With the debug stick, turn the Daedalon lantern light off.", s, LANTERN);
        assertEquals(ANSWER, light.route()); assertTrue(light.text().startsWith("Select Light")); assertFalse(light.text().contains("Facing"));
        var mount = ask("With the debug stick, change the mounting of this Daedalon lantern.", s, LANTERN);
        assertEquals(ANSWER, mount.route()); assertTrue(mount.text().contains("Mount control")); assertFalse(mount.text().contains("Facing"));
    }

    @Test void changingOperationUsesTheExactNewQuestionAndKeepsPriorContext() {
        var s = service.createSession(); String old = "With a debug stick, widen the Daedalon bench.";
        ask(old, s, UNKNOWN);
        String changed = "Actually I mean a Daedalon lantern, and I want to adjust its brightness.";
        var answer = ask(changed, s, LANTERN);
        assertEquals(ANSWER, answer.route()); assertTrue(answer.text().startsWith("Select Light")); assertEquals(changed, answer.exactQuestionForWebsite());
        var handoff = ask("Take that to your website.", s, LANTERN);
        assertEquals(HANDOFF, handoff.route()); assertEquals(changed, handoff.exactQuestionForWebsite());
        var payload = HandoffCodec.decode(service.createHandoff(s, LANTERN).uri().getRawFragment());
        assertEquals(changed, payload.get("question").getAsString()); assertTrue(payload.getAsJsonArray("turns").toString().contains(old));
        var other = service.createSession(); ask("In Axiom, rotate this Daedalon bench.", other, UNKNOWN);
        String unsupported = "Instead, make the Daedalon lantern's light level brighter.";
        var scoped = ask(unsupported, other, LANTERN); assertEquals(HANDOFF, scoped.route()); assertEquals(unsupported, scoped.exactQuestionForWebsite()); assertFalse(scoped.text().contains("rotation rings"));
    }

    @Test void pendingControlCorrectionRetainsItsToolButReplacesAnExplicitOperation() {
        var s = service.createSession(); ask("How can I widen my Daedalon bench?", s, UNKNOWN);
        String corrected = "Actually change the light level of the Daedalon lantern.";
        ask(corrected, s, LANTERN);
        var answer = ask("Vanilla", s, LANTERN);
        assertEquals(ANSWER, answer.route()); assertTrue(answer.text().startsWith("Select Light")); assertEquals(corrected, answer.exactQuestionForWebsite());
    }

    @Test void menuStallsRetainFirstVersusRepeatEvidenceAndNeverBecomeWorldFps() {
        var s = service.createSession(); String original = "Opening the block catalogue causes a brief stall. What comparison helps?";
        var initial = ask(original, s, UNKNOWN); assertEquals(ANSWER, initial.route()); assertTrue(initial.text().contains("first opening")); assertFalse(initial.text().contains("exact catalogue"));
        String detail = "The first opening is slow, but reopening it is normal.";
        var followup = ask(detail, s, UNKNOWN); assertEquals(ANSWER, followup.route()); assertTrue(followup.text().contains("normal reopening")); assertFalse(followup.text().contains("frame rate")); assertEquals(original, followup.exactQuestionForWebsite());
        ask("Continue on the website", s, UNKNOWN);
        var payload = HandoffCodec.decode(service.createHandoff(s, UNKNOWN).uri().getRawFragment());
        assertEquals(original, payload.get("question").getAsString()); assertTrue(payload.getAsJsonArray("turns").toString().contains(detail));
        var catalogue = ask("Show me the block catalogue", service.createSession(), UNKNOWN);
        assertFalse(catalogue.text().contains("exact catalogue"));
    }

    @Test void exclusionVerbsAndProductRoleParaphrasesStayWithinTheirRequestedTopics() {
        for (String verb : List.of("Skip", "Avoid", "Exclude", "Omit")) {
            var style = ask(verb + " Georgian detail; I prefer Modern for this doorway.", service.createSession(), UNKNOWN);
            assertEquals(ANSWER, style.route()); assertTrue(style.text().contains("Modern")); assertFalse(style.text().contains("Georgian"));
        }
        var daedalon = ask("Are Daedalon's additions mainly decorative objects?", service.createSession(), UNKNOWN);
        assertEquals(ANSWER, daedalon.route()); assertTrue(daedalon.text().contains("sculpture"));
        var timer = ask("Why should I keep Metrakron among my building mods?", service.createSession(), UNKNOWN);
        assertEquals(ANSWER, timer.route()); assertTrue(timer.text().contains("loading timer"));
    }

    @Test void displayProseUsesPlayerLanguageWithoutChangingExactQuestions() {
        var s = service.createSession(); String question = "With the debug stick, rotate this Daedalon lantern.";
        var controls = ask(question, s, LANTERN);
        assertTrue(controls.text().startsWith("For a Daedalon lantern,")); assertFalse(controls.text().contains("reviewed")); assertEquals(question, controls.exactQuestionForWebsite());
        var picker = ask("How do I open Lithotheca's picker?", service.createSession(), UNKNOWN);
        assertTrue(picker.text().contains("The default opener")); assertFalse(picker.text().contains("inspected default"));
        var unknown = ask("Which mods are loaded?", service.createSession(), UNKNOWN);
        assertTrue(unknown.text().contains("do not have a verified"));
    }
}
