package com.oliver.daedalon.client.glaux.dialogue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Local, session-only conversation over a learned recogniser and reviewed notes.
 * Calls never inspect Minecraft or make network requests. The client supplies an
 * immutable, deliberately limited context snapshot when the player submits.
 */
public final class GlauxDialogueService implements AutoCloseable {
    public enum Route { ANSWER, CLARIFY, HANDOFF, BOUNDARY }
    public enum Gesture { NONE, SHAKE, STILL, ATTENTIVE, GOODBYE }
    public record HeldItem(boolean known, boolean empty, String id, String displayName, String product, String family) {
        public HeldItem { id = safe(id); displayName = safe(displayName); product = safe(product); family = safe(family); }
        public static HeldItem unknown() { return new HeldItem(false, false, "", "", "", ""); }
    }
    public record Pack(int resolution, boolean available, boolean active, Boolean familyCompatible) { }
    public record Context(String minecraft, String loader, Map<String, String> mods, boolean modsKnown,
                          List<Pack> packs, boolean packsKnown, HeldItem held) {
        public Context { minecraft = safe(minecraft); loader = safe(loader).toLowerCase(Locale.ROOT); mods = mods == null ? Map.of() : Map.copyOf(mods); packs = packs == null ? List.of() : List.copyOf(packs); held = held == null ? HeldItem.unknown() : held; }
        public static Context unknown() { return new Context("", "", Map.of(), false, List.of(), false, HeldItem.unknown()); }
    }
    public record Reply(String text, Route route, List<String> choices, Gesture gesture, String exactQuestionForWebsite) {
        public Reply { text = safe(text); choices = List.copyOf(choices); if (choices.size() > 3) throw new IllegalArgumentException("At most three choices"); exactQuestionForWebsite = safe(exactQuestionForWebsite); }
    }
    public record Turn(String role, String text) { }

    /** Mutable only under its own monitor; public getters return snapshots. */
    public static final class Session {
        private final List<Turn> turns = new ArrayList<>();
        private String pending = "", unanswered = "", lastQuestion = "", lastIntent = "", subjectFamily = "", subjectProduct = "", product = "", diagnosticKind = "", diagnosticQuestion = "";
        private String controlOperation = "", controlTool = "", controlQuestion = "";
        private JsonObject lastEntry;
        private Clarification clarification;
        private boolean historyTruncated, attentive = true, discoveryDeclined, teasing = true;
        private int uncertaintyCount, diagnosticSteps;
        private long lastShake = Long.MIN_VALUE;
        private Session() { }
        public synchronized List<Turn> history() { return List.copyOf(turns); }
        public synchronized String unansweredQuestion() { return pending.isEmpty() ? unanswered : pending; }
        public synchronized String lastQuestion() { return lastQuestion; }
        public synchronized boolean isAttentive() { return attentive; }
    }

    private record Option(String label, String value, String match) { }
    private record Clarification(String kind, String question, String intent, List<Option> options) { }
    private record Speech(String recognition, boolean rude, boolean swear, boolean hasQuestion) { }
    private record Subject(String family, String product, boolean ambiguous) { }
    private record Output(String text, Route route, List<String> choices, Gesture gesture, String intent, String origin, JsonObject entry) { }
    private static final Set<String> CONTEXTUAL = Set.of("simplify", "next_step", "fuller_answer", "stay_local", "session_recall", "tone_banter", "attention_on", "attention_off", "goodbye");
    private static final Set<String> CONTROL_IDS = Set.of("controls_general", "bench_width", "lantern_controls", "lantern_mount", "panel_mount", "statue_orientation", "axiom_copy");
    private static final Map<String, String> PRODUCT_NAMES = Map.of("erydon", "ERYDON", "daedalon", "Daedalon", "themelios", "Themelios", "metrakron", "Metrakron", "lithotheca", "Lithotheca");
    private static final Map<String, String> SUBJECT_WORDS = Map.ofEntries(Map.entry("owl", "glaux"), Map.entry("glaux", "glaux"), Map.entry("statue", "statue"), Map.entry("sculpture", "statue"), Map.entry("bust", "statue"), Map.entry("bench", "bench"), Map.entry("seat", "bench"), Map.entry("hedra", "bench"), Map.entry("exedra", "bench"), Map.entry("lantern", "lantern"), Map.entry("lamp", "lantern"), Map.entry("panel", "panel"), Map.entry("roof", "roof"), Map.entry("wall", "wall"), Map.entry("column", "column"), Map.entry("ceiling", "ceiling"), Map.entry("cornice", "cornice"), Map.entry("urn", "urn"), Map.entry("vase", "urn"), Map.entry("book", "book"));
    private static final Pattern QUOTED = Pattern.compile("\"[^\"]*\"|(?<![a-z])'[^']*'(?![a-z])");
    private static final Pattern RUDE = Pattern.compile("\\b(?:fuck\\s+(?:you|off)|you(?:'re| are)?\\s+(?:a |an )?(?:fucking )?(?:idiot|stupid|useless|arsehole|asshole)|stupid\\s+(?:owl|glaux))\\b");
    private static final Pattern SWEAR = Pattern.compile("\\b(?:fuck(?:ing|ed|ers?|s)?|(?:bull)?shit(?:ty|ting|s)?|bloody|damn(?:ed)?|hell|arsehole|asshole|bollocks|wanker|twat|cunt|piss(?:ed|ing)?|bitch)\\b");
    private static final Pattern USEFUL = Pattern.compile("\\b(?:how|what|why|which|where|can i|could i|do i|help me)\\b[\\s\\S]*", Pattern.CASE_INSENSITIVE);
    private final ExecutorService worker;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final LongSupplier clock;
    private Engine engine;

    public GlauxDialogueService() { this(System::nanoTime); }
    GlauxDialogueService(LongSupplier clock) {
        this.clock = clock;
        worker = Executors.newSingleThreadExecutor(task -> { Thread thread = new Thread(task, "Daedalon-Glaux"); thread.setDaemon(true); return thread; });
    }
    public Session createSession() { return new Session(); }

    public CompletableFuture<Reply> respond(String question, Session session, Context context) {
        Objects.requireNonNull(session, "session"); String raw = safe(question); Context snapshot = context == null ? Context.unknown() : context;
        if (closed.get()) return CompletableFuture.failedFuture(new IllegalStateException("Glaux service is closed"));
        try {
            return CompletableFuture.supplyAsync(() -> {
                synchronized (session) {
                    if (raw.isBlank()) return new Reply("What would you like to work on?", Route.CLARIFY, List.of(), Gesture.NONE, enquiryText(session));
                    if (raw.length() > 2000) return new Reply("Could you keep this question within 2,000 characters? Your wording has not been shortened or sent anywhere.", Route.BOUNDARY, List.of(), Gesture.NONE, enquiryText(session));
                    try {
                        if (engine == null) engine = Engine.load();
                        return process(raw, session, snapshot, engine);
                    } catch (IOException | IllegalStateException failure) {
                        session.unanswered = raw;
                        return record(raw, session, output("My local notes could not be opened. I have kept your question; you can copy it or continue in my study.", Route.HANDOFF, "local_unavailable", raw), false);
                    }
                }
            }, worker);
        } catch (RejectedExecutionException stopped) { return CompletableFuture.failedFuture(stopped); }
    }

    public String enquiryText(Session session) { synchronized (session) { return !session.pending.isEmpty() ? session.pending : !session.unanswered.isEmpty() ? session.unanswered : session.lastQuestion; } }
    public HandoffCodec.Export createHandoff(Session session, Context context) { return createHandoff(session, context, enquiryText(session)); }
    public HandoffCodec.Export createHandoff(Session session, Context context, String editedQuestion) {
        synchronized (session) { return HandoffCodec.create(editedQuestion, session.turns, context == null ? Context.unknown() : context, session.historyTruncated); }
    }
    @Override public void close() { if (closed.compareAndSet(false, true)) worker.shutdown(); }

    private Reply process(String raw, Session s, Context c, Engine e) {
        Speech speech = tone(raw); String n = norm(speech.recognition); String origin = raw;
        Subject explicit = subject(n, s, c);
        if (explicit != null && !explicit.ambiguous && !explicit.family.equals("glaux")) { s.subjectFamily = explicit.family; s.subjectProduct = explicit.product; }
        else if (explicit != null && explicit.ambiguous) { s.subjectFamily = ""; s.subjectProduct = ""; }
        Output result = ownAndBoundaries(raw, n, s, c, e);
        if (result == null && speech.rude && !speech.hasQuestion) result = s.teasing ? local(e, "tone_banter", raw) : output("Tell me what you need help with; I’ll keep the answer plain.", Route.CLARIFY, "tone_banter", raw);
        if (result == null && speech.swear && n.length() < 4) result = output("Something not going to plan? Tell me what you are trying to do.", Route.CLARIFY, "tone_banter", raw);
        if (result == null && !s.pending.isEmpty() && (matches(n, "^(?:yes|okay|ok|sure|please|go ahead|continue|take|bring|carry|send|let s|let us)\\b") && !matches(n, "\\b(?:but|also|and) (?:what|why|how|which|where)\\b") || matches(n, "\\b(?:website|your study)\\b")))
            result = handoff(s.pending, s, "Certainly. The original enquiry and our recent exchange are ready to review before you continue in my study.");
        if (result == null && !s.controlQuestion.isEmpty() && matches(n, "\\b(?:i mean|i meant|actually|rather than|not the|instead)\\b")
                && (explicit != null || !controlOperation(n).isEmpty()))
            result = controlRequest(controlOperation(n).isEmpty() ? s.controlQuestion : raw, n, s, c, e);
        if (result == null && s.clarification != null && isControlClarification(s.clarification) && !controlOperation(n).isEmpty()) result = controlRequest(raw, n, s, c, e);
        if (result == null && s.clarification != null) result = resolveClarification(raw, n, s, c, e);
        if (result == null && s.clarification == null && matches(n, "^(?:option |number )?[1-9]$")) result = output("There are no numbered choices open yet. Tell me what you would like help with.", Route.CLARIFY, "clarify_other", raw);
        if (result == null && heldQuestion(n)) result = heldReply(raw, s, c);
        if (result == null && c.held.known && matches(n, "\\b(?:this|that|held)\\b") && matches(n, "\\b(?:which mod|from daedalon|from erydon|from another mod|what mod)\\b")) result = heldReply(raw, s, c);
        if (result == null && isInstalledQuestion(n)) result = installed(raw, n, c);
        if (result == null) result = collection(raw, n, s, c, e);
        if (result == null && matches(n, "\\b(?:simpler|more simply|without jargon|less jargon|explain that|explain the last|last part)\\b")) result = simplify(raw, s, e);
        if (result == null && matches(n, "\\b(?:next step|what next|add next|do next)\\b")) result = s.lastEntry != null && !text(s.lastEntry, "nextStep").isEmpty() ? output(text(s.lastEntry, "nextStep"), Route.ANSWER, "next_step", s.lastQuestion) : local(e, "next_step", raw);
        if (result == null && matches(n, "\\b(?:carry on|continue|return|back)\\b.*\\b(?:last|previous|earlier|topic|question|discussed)\\b")) result = recall(raw, s);
        if (result == null && matches(n, "\\b(?:website|your study|take this across|take it across|fuller answer|more detail|go deeper)\\b")) result = handoff(!s.unanswered.isEmpty() ? s.unanswered : !s.lastQuestion.isEmpty() ? s.lastQuestion : raw, s, "I can bring the enquiry and the useful part of our exchange to my study. You can review it before opening the website.");
        if (result == null) {
            GlauxIntentModel.Prediction prediction = e.model.predict(speech.recognition);
            JsonObject card = e.cards.get(prediction.intentId());
            if (card != null && prediction.confidence() >= .35 && prediction.margin() >= .08 && modelEligible(prediction.intentId(), raw))
                result = applyCard(raw, n, s, c, e, prediction.intentId(), card);
            if (result == null) result = unknown(raw, s);
        }
        Gesture gesture = result.gesture;
        long now = clock.getAsLong();
        if (gesture == Gesture.NONE && (speech.rude || speech.swear) && s.attentive && s.teasing && (s.lastShake == Long.MIN_VALUE || now - s.lastShake >= TimeUnit.SECONDS.toNanos(12))) { gesture = Gesture.SHAKE; s.lastShake = now; }
        if (gesture != result.gesture) result = new Output(result.text, result.route, result.choices, gesture, result.intent, result.origin, result.entry);
        return record(raw, s, result, true);
    }

    private Output ownAndBoundaries(String raw, String n, Session s, Context c, Engine e) {
        boolean own = (matches(n, "\\b(?:you|your|owl|glaux)\\b") || matches(n, "^(?:please )?(?:stop (?:staring|looking|watching)|stay still|keep still|remain still|look around|resume watching)\\b")) && !matches(n, "\\b(?:lantern|bench|panel|statue|column|roof|wall)\\b");
        if (own && stillRequest(n)) { s.attentive = false; return gesture(local(e, "attention_off", raw), Gesture.STILL); }
        if (own && matches(n, "\\b(?:start|resume|again|can look|may look|look around|keep an eye)\\b") && matches(n, "\\b(?:look|watch|move|attention|attentive)\\w*\\b") && !matches(n, "\\b(?:not|don t|do not|stop|stay still|motionless)\\b")) { s.attentive = true; return gesture(local(e, "attention_on", raw), Gesture.ATTENTIVE); }
        if (own && matches(n, "\\b(?:height|tall|taller|shorter|metres?|meters?|size|resize|bigger|smaller|shrink|enlarge)\\b") && !matches(n, "\\b(?:not|don t|do not)\\b.{0,20}\\b(?:size|bigger|smaller|resize)\\b")) return local(e, "fixed_height", raw);
        if (c.held.known && c.held.family.equals("glaux") && matches(n, "\\b(?:this|it|owl|glaux)\\b") && matches(n, "\\b(?:height|size|resize|bigger|smaller|shrink|enlarge)\\b")) return local(e, "fixed_height", raw);
        if (own && matches(n, "\\b(?:color|colour|texture|material|finish|paint|bronze|silver|gold|copper|pewter|stone|white)\\b") && !matches(n, "\\b(?:pack|background|interface|menu|build)\\b")) return local(e, "bronze_only", raw);
        if (matches(n, "\\b(?:stop|don t|do not) (?:keep )?(?:suggesting|recommend(?:ing)?|promot(?:ing|e))\\b|\\bi (?:don t|do not) want (?:any )?(?:new|extra|more|another|additional) (?:mods?|packs?|textures?)\\b")) { s.discoveryDeclined = true; return output("Certainly. We can use your current setup. I will leave extra mods and packs out of this session unless you ask for them.", Route.ANSWER, "stay_local", raw); }
        if (matches(n, "\\b(?:keep|stop|less|minimum|enough|no|without|avoid)\\b.*\\b(?:teasing|banter|jokes)\\b|\\bjust (?:need|want) (?:a )?(?:clear|plain) answer\\b")) { s.teasing = false; return output("Of course. I’ll keep the replies plain and useful.", Route.ANSWER, "tone_banter", raw); }
        if (matches(n, "^(?:bye|goodbye|see you|thanks that s all|that s all|i m off|i am off|see you later)(?: for now| thanks)?$")) return gesture(local(e, "goodbye", raw), Gesture.GOODBYE);
        if (matches(n, "\\b(?:remove|removing|uninstall|disable|disabling|turn off)\\b") && matches(n, "\\b(?:resource pack|texture pack|32x|64x)\\b") && matches(n, "\\b(?:delete|lose|break|world|build|blocks)\\b")) return output("Disabling only an optional resource pack leaves the registered blocks in place while the mods remain loaded. Their appearance falls back to the other active packs or native assets. Removing a content mod is a separate question.", Route.ANSWER, "pack_choice", raw);
        if (matches(n, "\\b(?:see|inspect|judge|look at|view)\\b") && matches(n, "\\b(?:screen|picture|screenshot|photo|image)\\b")) return output("I do not inspect the view or capture screenshots through this conversation. Describe the part you want help with; you can choose to share a screenshot on the website.", Route.CLARIFY, "visual_context", raw);
        if (matches(n, "\\b(?:invent|make up|guess)\\b.*\\b(?:block id|registry id|registered id)\\b|\\b(?:weather|stock price|share price|bitcoin|medical|diagnose my health)\\b")) return output(text(e.cards.get("boundary"), "answer"), Route.BOUNDARY, "boundary", raw);
        return null;
    }

    private Output resolveClarification(String raw, String n, Session s, Context c, Engine e) {
        Clarification q = s.clarification; Option selected = select(n, q.options);
        if (selected == INVALID) return repeat("Choose one of the displayed numbers, or describe what you mean.", q, s);
        if (selected == null && matches(n, "^(?:yes|yes please|okay|ok|sure)$") && !q.kind.equals("confirm_daedalon")) return repeat("Which option do you mean? You can type its number or describe it.", q, s);
        if (matches(n, "^(?:neither|none of those|something else)$")) { s.clarification = null; s.unanswered = q.question; return output("Tell me what you mean in your own words. I’ll keep the original question in mind.", Route.CLARIFY, "clarify_other", q.question); }
        if (selected == null) {
            // A diagnostic observation need not repeat one of our exact labels.
            // Keep the issue's original wording when the new evidence is narrower.
            if (q.kind.equals("diagnostic") && sceneObservation(n)) { s.clarification = null; return diagnosticAnswer(q.question, "scene", s, e); }
            if (q.kind.equals("control_subject")) {
                Subject subject = subject(n, s, c);
                if (subject != null && !subject.ambiguous && !subject.family.isEmpty() && !subject.family.equals("glaux")) { s.subjectFamily = subject.family; s.subjectProduct = subject.product; s.clarification = null; return controls(q.question, norm(q.question), s, c, e, q.intent); }
            }
            if (q.kind.equals("confirm_daedalon") && matches(n, "^(?:yes|yes daedalon|daedalon|it is daedalon|it s daedalon|yes it is|correct)$")) selected = new Option("Daedalon", "yes", "");
            if (q.kind.equals("confirm_daedalon") && matches(n, "\\b(?:another|different|not daedalon|no)\\b")) selected = new Option("Another mod", "no", "");
            if (q.kind.equals("control_tool")) {
                Subject subject = subject(n, s, c);
                if (subject != null && !subject.ambiguous && !subject.family.isEmpty() && !subject.family.equals("glaux")) { s.subjectFamily = subject.family; s.subjectProduct = subject.product; return repeat("I have the " + subject.family + ". Which controls are you using?", q, s); }
            }
            return null;
        }
        s.clarification = null; String origin = q.question;
        if (q.kind.equals("control_tool")) s.controlTool = selected.value;
        return switch (q.kind) {
            case "help" -> switch (selected.value) {
                case "design" -> ask(s, "design", origin, "", "Which part of the design would you like to work on?", options("Understanding a style|style", "Choosing materials|palette", "Shape and proportions|composition"));
                case "technical" -> problemChoice(origin, s);
                default -> ask(s, "product_group", origin, "", "Which part of the Collection is your question about?", options("Building blocks or decoration|content", "Metrakron loading timer|metrakron", "Lithotheca picker or hotbar|lithotheca"));
            };
            case "design" -> selected.value.equals("style") ? styleChoice(origin, s) : local(e, selected.value.equals("palette") ? "coherent_palette" : "composition_start", origin);
            case "style" -> styleReply(origin, selected.value, s, e);
            case "product_group" -> selected.value.equals("content") ? ask(s, "product", origin, "", "Which building mod do you mean?", options("ERYDON|erydon", "Themelios|themelios", "Daedalon|daedalon")) : productReply(origin, norm(origin), selected.value, s, e);
            case "product" -> productReply(origin, norm(origin), selected.value, s, e);
            case "problem" -> beginDiagnostic(origin, selected.value, s);
            case "diagnostic" -> diagnosticAnswer(origin, selected.value, s, e);
            case "control_tool" -> controlRequest(origin, "", s, c, e);
            case "control_operation" -> { s.controlOperation = selected.value; yield controls(origin, "", s, c, e, "controls_general"); }
            case "control_subject" -> { s.subjectFamily = selected.value; s.subjectProduct = "daedalon"; yield controls(origin, norm(origin), s, c, e, q.intent); }
            case "confirm_daedalon" -> { if (selected.value.equals("yes")) { s.subjectProduct = "daedalon"; yield controls(origin, norm(origin), s, c, e, q.intent); } yield handoff(origin, s, "That object needs its own controls. I can take the exact enquiry to my study rather than apply Daedalon’s settings to it."); }
            default -> unknown(origin, s);
        };
    }

    private Output collection(String raw, String n, Session s, Context c, Engine e) {
        List<String> products = products(n);
        // Live publication and server requirements take precedence over a named
        // product's overview. A familiar name is not evidence for a safe answer.
        if (!products.isEmpty() && releaseQuestion(raw)) {
            String prefix = matches(n, "\\b(?:purpose|what .* for|what .* do|overview)\\b") ? text(e.products.getAsJsonObject(products.get(0)), "overview") + " " : "";
            if (affirmed(raw, "install(?:ation)?|enable|update") && matches(n, "\\b(?:you|for me)\\b")) prefix += "I can explain the steps, but this conversation cannot install or change your setup. ";
            return handoff(raw, s, prefix + "Public downloads and exact supported versions need the current records. I can carry this specific question to my study; a client installation is not proof that another release is available.");
        }
        if (matches(n, "\\b(?:server|multiplayer|joining)\\b") && matches(n, "\\b(?:safe|support|compatib\\w*|require\\w*|version|numbers?|definitely|any)\\b") && !products.equals(List.of("lithotheca")))
            return output("The loaded versions describe this client only. They do not establish a server’s Minecraft target, loader, installed mods or compatibility generation. Check the server’s stated requirements before joining; I cannot confirm an arbitrary server from this snapshot.", Route.ANSWER, "compatibility", raw);
        if ((products.size() >= 3 || products.size() >= 2 && matches(n, "\\b(?:compare|difference|overview|roles?|what|summary|rundown|same|both|overlap|jobs?)\\b") || matches(n, "\\b(?:all five|five mods|five products)\\b")) && !isInstalledQuestion(n) && diagnosticKind(n).isEmpty()) {
            List<String> ids = products.isEmpty() ? List.of("erydon", "daedalon", "themelios", "metrakron", "lithotheca") : products;
            List<String> descriptions = new ArrayList<>(); for (String id : ids) descriptions.add(PRODUCT_NAMES.get(id) + ": " + text(e.products.getAsJsonObject(id), "overview"));
            return output(String.join("\n", descriptions) + (ids.size() == 2 ? "\nThey serve different roles; choose each for the job you need. This does not establish their exact release compatibility." : ""), Route.ANSWER, "collection_scope", raw);
        }
        String product = products.isEmpty() ? "" : products.get(products.size() - 1);
        if (product.isEmpty() && matches(n, "\\b(?:extra|extended|scrolling) hotbar\\b|\\bblock picker\\b")) product = "lithotheca";
        if (product.isEmpty() && matches(n, "\\b(?:loading|startup|load) timer\\b")) product = "metrakron";
        if (product.isEmpty() && !s.product.isEmpty() && s.lastIntent.startsWith("product_") && matches(n, "\\b(?:it|its|this|that|same)\\b")) product = s.product;
        if (!product.isEmpty()) s.product = product;
        if (product.equals("metrakron") && matches(n, "\\b(?:fps|performance|faster|speed|accelerat\\w*|improv\\w*)\\b")) return output("Metrakron measures loading time; it does not speed up loading or increase your frame rate. Its timer can help compare loading runs. A frame-rate problem needs its own checks.", Route.ANSWER, "product_metrakron", raw);
        if (matches(n, "^(?:what is|what s|what does)\\b.{0,15}\\b(?:fps|frame rate)\\b")) return output("FPS means frames per second: how often the game draws a new view. A low frame rate feels continuously slow, a stutter is a distinct pause, and a crash stops the game. Those need different checks.", Route.ANSWER, "technical_definition", raw);
        if (s.diagnosticKind.equals("menu") && s.lastIntent.equals("troubleshooting_menu") && menuObservation(n)) {
            if (s.diagnosticSteps > 1) return handoff(s.diagnosticQuestion, s, "Those first-opening and repeat-opening observations are useful context. I can carry the original menu issue and these results to my study without guessing a cause.");
            Output next = diagnosticAnswer(s.diagnosticQuestion, "menu", s, e);
            return matches(n, "\\bfirst\\b") && matches(n, "\\b(?:reopen\\w*|repeat\\w*|again)\\b") ? new Output("A slow first opening with normal reopening is different evidence from a delay on every opening. " + next.text, next.route, next.choices, next.gesture, next.intent, next.origin, next.entry) : next;
        }
        String diagnostic = diagnosticKind(n);
        if (!diagnostic.isEmpty()) {
            if (s.diagnosticSteps > 0 && diagnostic.equals(s.diagnosticKind) && s.lastIntent.startsWith("troubleshooting_")) return handoff(s.diagnosticQuestion, s, "We have a useful first check, but that result does not establish the cause. I can carry the original issue and your observations to my study.");
            if (s.clarification != null && s.clarification.kind.equals("diagnostic") && diagnostic.equals(s.diagnosticKind)) return repeat("I have kept the original issue. Which of these is closest to what you just described?", s.clarification, s);
            if (Set.of("fps", "stutter").contains(diagnostic) && sceneObservation(n)) { s.diagnosticKind = diagnostic; s.diagnosticQuestion = raw; s.diagnosticSteps = 0; return diagnosticAnswer(raw, "scene", s, e); }
            if (diagnostic.equals("crash") && matches(n, "\\bexit code\\b")) return handoff(raw, s, "An exit code alone does not identify the responsible JAR. Keep the first useful error and the relevant crash-report excerpt, then review it before sharing it in my study. I will not name a culprit from that code.");
            if (diagnostic.equals("menu")) { s.diagnosticKind = "menu"; s.diagnosticQuestion = raw; s.diagnosticSteps = 0; return diagnosticAnswer(raw, "menu", s, e); }
            return beginDiagnostic(raw, diagnostic, s);
        }
        if (s.diagnosticSteps > 0 && s.lastIntent.startsWith("troubleshooting_") && (sceneObservation(n) || matches(n, "^(?:only|when|after|before|while|still|now)\\b"))) return handoff(s.diagnosticQuestion, s, "That is useful evidence to keep with the original issue. It still does not identify a cause; I can take both the issue and your observations to my study.");
        if (s.diagnosticSteps > 0 && s.lastIntent.startsWith("troubleshooting_") && matches(n, "^(?:yes|no|still|it still|that did not|that didn t|nothing changed|didn t help|did not help)\\b")) return handoff(s.diagnosticQuestion, s, "Thank you—that observation belongs with the original issue. We can continue in my study without starting again.");
        if (matches(n, "\\b(?:troubleshoot\\w*|technical help|technical problem|something is wrong|having problems|not working properly)\\b")) return problemChoice(raw, s);
        if ((rotationRequest(n) || !controlOperation(n).isEmpty()) && !matches(n, "\\b(?:you|owl|glaux|your head)\\b")) return controlRequest(raw, n, s, c, e);
        if (exactCatalogueQuestion(n)) {
            for (JsonElement material : array(e.knowledge, "materialGuidance", "knownMaterials")) { JsonObject m = material.getAsJsonObject(); String name = text(m, "id"); if (!name.isEmpty() && matches(n, "\\b" + name + "\\b") && matches(n, "\\b(?:plain|full|block)\\b") && !matches(n, "\\b(?:aged|cornice|surround|column|arch|slab|stairs|wall)\\b")) return output("The reviewed plain block is " + text(m, "verifiedPlainBlockId") + ". Other shapes and finishes need their own verified registration.", Route.ANSWER, "verified_material_id", raw); }
            return handoff(raw, s, "Let’s check the exact catalogue so the names, forms and IDs are real. I can take the design brief and our relevant exchange to my study.");
        }
        List<String> styles = styles(raw, e);
        if (!styles.isEmpty() && !matches(n, "\\b(?:rotate|control|key|installed|pack|shader|size|light off)\\b")) return styleReply(raw, String.join(",", styles), s, e);
        if (matches(n, "\\b(?:style|styles|architecture|architectural)\\b") && matches(n, "\\b(?:explain|understand|learn|what|which|help|know)\\b")) return styleChoice(raw, s);
        if (matches(n, "\\b(?:pale|light|dark|palette|materials?)\\b") && matches(n, "\\b(?:stone|museum|accent|erydon)\\b")) {
            if (s.discoveryDeclined && c.modsKnown && !c.mods.containsKey("erydon")) return local(e, "coherent_palette", raw);
            return output("If ERYDON is available in your current catalogue, " + lowerFirst(text(e.knowledge.getAsJsonObject("materialGuidance"), "answer")), Route.ANSWER, "material_guidance", raw);
        }
        if (!product.isEmpty() && productQuestion(raw, product, e)) return productReply(raw, n, product, s, e);
        if (designQuestion(n) && matches(n, "\\b(?:fussy|busy|overdecorat\\w*|too much detail|calm|restrain\\w*|simplif\\w*)\\b")) return local(e, "restraint_detail", raw);
        if (designQuestion(n) && matches(n, "\\b(?:monoton\\w*|repetiti\\w*|rhythm|repeated|boring)\\b")) return local(e, "repetition", raw);
        return null;
    }

    private Output applyCard(String raw, String n, Session s, Context c, Engine e, String id, JsonObject card) {
        if (CONTROL_IDS.contains(id)) return controls(raw, n, s, c, e, id);
        if (id.equals("simplify")) return simplify(raw, s, e);
        if (id.equals("next_step")) return s.lastEntry != null && !text(s.lastEntry, "nextStep").isEmpty() ? output(text(s.lastEntry, "nextStep"), Route.ANSWER, id, s.lastQuestion) : local(e, id, raw);
        if (id.equals("session_recall")) return recall(raw, s);
        if (id.equals("held_info") || id.equals("held_switch")) return heldReply(raw, s, c);
        if (id.equals("installed_status")) return installed(raw, n, c);
        if (id.equals("stay_local")) return output("Certainly. We can stay here and work with what you already have. Which simpler part would you like to explore?", Route.CLARIFY, id, raw);
        if (id.equals("optional_discovery") && s.discoveryDeclined) return output("We can use what you already have. I will leave optional additions out of this session unless you ask for them.", Route.ANSWER, id, raw);
        if (id.equals("ambiguous_followup")) return s.clarification != null ? handoff(s.clarification.question, s, "I have not resolved that follow-up reliably. I can take the original enquiry and your clarification to my study.") : ask(s, "help", !s.lastQuestion.isEmpty() ? s.lastQuestion : raw, "", "Which part of the previous topic do you mean?", options("Building design or styles|design", "Using a Collection mod or tool|use", "A technical problem|technical"));
        if (id.equals("topic_correction")) return output("Thanks for correcting me. What should I focus on instead?", Route.CLARIFY, id, raw);
        String route = text(card, "route");
        if (route.equals("handoff") || id.equals("fuller_answer") || id.equals("website_handoff")) return handoff(!s.pending.isEmpty() ? s.pending : raw, s, text(card, "answer"));
        if (route.equals("boundary")) return output(text(card, "answer"), Route.BOUNDARY, id, raw);
        if (route.equals("clarify")) return unknown(raw, s);
        if (id.equals("goodbye")) return gesture(local(e, id, raw), Gesture.GOODBYE);
        return local(e, id, raw);
    }

    private Output controls(String raw, String n, Session s, Context c, Engine e, String intent) {
        // The current submission or explicit choice already resolved the subject.
        // Re-reading an earlier "this" here would replace a corrected object with
        // whatever happens to be held when a later numeric reply is submitted.
        if (s.subjectFamily.isEmpty() || !Set.of("bench", "lantern", "panel", "statue").contains(s.subjectFamily)) return ask(s, "control_subject", raw, intent, "Which ornament are you working with? You can also name a different object.", options("A Daedalon bench|bench", "A Daedalon lantern|lantern", "A Daedalon panel|panel"));
        if (!s.subjectProduct.isEmpty() && !s.subjectProduct.equals("daedalon")) return handoff(raw, s, "That object is from " + s.subjectProduct + ". I do not have its verified controls here. Let’s check the actual object rather than apply Daedalon’s controls to it.");
        if (!s.subjectProduct.equals("daedalon")) return ask(s, "confirm_daedalon", raw, intent, "Is that one of Daedalon’s " + s.subjectFamily + " designs? Controls can differ between mods.", List.of(new Option("Yes, Daedalon", "yes", "^(?:yes|daedalon|correct)(?:.*)?$"), new Option("It is from another mod", "no", "\\b(?:another|different|not daedalon|no)\\b")));
        JsonObject guide = e.controls.getAsJsonObject(s.subjectFamily);
        if (guide == null) return handoff(raw, s, "I know the object now. I should check the exact controls rather than invent a setting.");
        String requested = controlOperation(n);
        if (requested.isEmpty()) requested = switch (intent) { case "bench_width" -> "width"; case "lantern_controls" -> "light"; case "lantern_mount", "panel_mount" -> "mount"; case "statue_orientation", "axiom_copy" -> "orientation"; default -> s.controlOperation; };
        s.controlOperation = requested; s.controlQuestion = raw;
        JsonObject properties = guide.getAsJsonObject("controls");
        if (requested.isEmpty() || requested.equals("ambiguous")) {
            List<Option> choices = new ArrayList<>();
            for (var property : properties.entrySet()) if (choices.size() < 3) choices.add(new Option(property.getValue().getAsString(), property.getKey(), ""));
            return ask(s, "control_operation", raw, "controls_general", "Which property are you trying to change? You can also name it in your own words.", choices);
        }
        String property = requested.equals("mount") && s.subjectFamily.equals("panel") ? "ceiling" : requested;
        if (!properties.has(property)) return handoff(raw, s, "My local notes for this " + s.subjectFamily + " do not establish that setting. I can take the exact operation and object to my study rather than substitute a different control.");
        String answer = switch (requested) {
            case "width" -> text(e.cards.get("bench_width"), "answer");
            case "light" -> text(guide, "lightAnswer");
            case "mount" -> s.subjectFamily.equals("panel") ? text(e.cards.get("panel_mount"), "answer") : text(guide, "mountAnswer");
            case "size", "offset" -> "For a Daedalon " + s.subjectFamily + ", select the debug stick’s " + text(properties, property) + " control to cycle its supported states. Direction is a separate property; I have not changed your build.";
            default -> text(guide, "answer");
        };
        if (answer.isBlank()) return handoff(raw, s, "I have identified the requested property, but do not have its verified instructions here. I can carry that exact request to my study.");
        JsonObject entry = new JsonObject(); entry.addProperty("answer", answer); entry.addProperty("shortAnswer", answer.split("(?<=[.!?])\\s+")[0]);
        return new Output(answer, Route.ANSWER, List.of(), Gesture.NONE, "controls_general", raw, entry);
    }

    private Output controlRequest(String raw, String n, Session s, Context c, Engine e) {
        String requested = controlOperation(n), tool = controlTool(n);
        if (!requested.isEmpty()) s.controlOperation = requested;
        if (!raw.equals(s.controlQuestion)) { s.controlQuestion = raw; s.pending = ""; }
        if (!tool.isEmpty()) s.controlTool = tool;
        if (s.controlTool.isEmpty()) return ask(s, "control_tool", raw, "controls_general", "Which controls are you using for " + (s.subjectFamily.isEmpty() ? "this" : "the " + s.subjectFamily) + "?", List.of(new Option("Vanilla placement or debug stick", "vanilla", "\\b(?:vanilla|debug stick|(?:normal|standard|ordinary|minecraft) placement|normal minecraft)\\b"), new Option("Axiom", "axiom", "\\baxiom\\b"), new Option("WorldEdit", "worldedit", "\\bworld ?edit\\b")));
        if (!s.controlTool.equals("vanilla")) {
            if (!Set.of("", "orientation").contains(s.controlOperation)) return handoff(raw, s, "My " + s.controlTool + " notes cover selection transforms, not this object's " + s.controlOperation + " property. I can carry the specific tool, object and requested setting to my study.");
            return toolReply(raw, s.controlTool, s, e);
        }
        return controls(raw, n, s, c, e, "controls_general");
    }

    private Output toolReply(String raw, String tool, Session s, Engine e) {
        JsonObject guide = e.knowledge.getAsJsonObject("toolGuidance").getAsJsonObject(tool);
        if (guide == null) return handoff(raw, s, "Let’s check the exact tool and version in my study.");
        return output(text(guide, "answer"), Route.ANSWER, "tool_" + tool, raw);
    }

    private Output heldReply(String raw, Session s, Context c) {
        HeldItem h = c.held;
        if (!h.known) return output("I do not have a reliable held-item context just now. Tell me its name or what you want to do with it.", Route.CLARIFY, "held_info", raw);
        if (h.empty) return output("Your hand is empty. We can still discuss the build—tell me which part you are working on.", Route.ANSWER, "held_info", raw);
        if (!h.family.isEmpty()) { s.subjectFamily = h.family; s.subjectProduct = h.product; }
        String name = h.displayName.isBlank() ? h.id : h.displayName, source = h.product.isEmpty() || norm(name).contains(norm(h.product)) ? "" : " from " + PRODUCT_NAMES.getOrDefault(h.product, h.product);
        return output("You are holding " + name + source + ". What would you like to do with it?", Route.ANSWER, "held_info", raw);
    }

    private Output installed(String raw, String n, Context c) {
        StringBuilder answer = new StringBuilder(); List<String> requested = products(n);
        if (matches(n, "\\b(?:mod|mods|installed|loaded|do i have|have i got)\\b") && !matches(n, "\\b(?:only|just) (?:the )?pack\\b")) {
            if (!c.modsKnown) answer.append("I do not have a verified loaded-mod snapshot just now.");
            else if (!requested.isEmpty()) for (String id : requested) answer.append(PRODUCT_NAMES.get(id)).append(c.mods.containsKey(id) ? " is loaded on this client (" + c.mods.get(id) + "). " : " is not loaded on this client. ");
            else { List<String> loaded = new ArrayList<>(); for (String id : List.of("erydon", "daedalon", "themelios", "metrakron", "lithotheca")) if (c.mods.containsKey(id)) loaded.add(PRODUCT_NAMES.get(id) + " " + c.mods.get(id)); answer.append(loaded.isEmpty() ? "No Collection mods are listed as loaded in this client snapshot. " : "Loaded here: " + String.join(", ", loaded) + ". "); }
        }
        if (matches(n, "\\b(?:pack|packs|32x|64x|textures|active|enabled)\\b") || requested.isEmpty()) {
            if (!answer.isEmpty() && answer.charAt(answer.length() - 1) != ' ') answer.append(' ');
            if (!c.packsKnown) answer.append("The available/active official-pack state is not confirmed.");
            else if (c.packs.isEmpty()) answer.append("No official Collection pack markers are listed in this snapshot.");
            else for (Pack pack : c.packs) answer.append(pack.resolution).append("x: ").append(pack.active ? "active" : pack.available ? "available but not active" : "not available").append(Boolean.FALSE.equals(pack.familyCompatible) ? "; compatibility marker does not match" : pack.familyCompatible == null ? "; compatibility marker unknown" : "; family marker matches").append(". ");
        }
        return output(answer.toString().trim() + " That does not establish what a remote server supports or predict your GPU’s frame rate.", Route.ANSWER, "installed_status", raw);
    }

    private Output productReply(String raw, String n, String id, Session s, Engine e) {
        s.product = id; JsonObject product = e.products.getAsJsonObject(id);
        if (product == null) return unknown(raw, s);
        JsonObject best = null; double bestScore = 0;
        for (JsonElement item : product.getAsJsonArray("practicalHelp")) { JsonObject card = item.getAsJsonObject(); double score = 0;
            for (JsonElement keyword : card.getAsJsonArray("keywords")) { String word = keyword.getAsString(); if (affirmed(raw, Pattern.quote(word))) score += Set.of("what", "add", "building", "architecture", "mod").contains(word) ? .1 : 1 + word.split(" ").length; }
            if (score > bestScore) { best = card; bestScore = score; }
        }
        String answer = best != null ? text(best, "answer") : text(product, "overview");
        // Release dates and publication are deliberately not inferred from source.
        return output(answer, Route.ANSWER, "product_" + id, raw);
    }

    private Output styleReply(String raw, String ids, Session s, Engine e) {
        List<String> answers = new ArrayList<>(); for (String id : ids.split(",")) { JsonObject style = e.styles.getAsJsonObject(id); if (style != null) answers.add(text(style, "answer")); }
        if (answers.isEmpty()) return styleChoice(raw, s);
        JsonObject entry = new JsonObject(); entry.addProperty("answer", String.join("\n\n", answers)); entry.addProperty("shortAnswer", answers.get(0)); entry.addProperty("nextStep", "Choose one feature of the style to emphasise first, then repeat it consistently before adding more detail.");
        return new Output(text(entry, "answer"), Route.ANSWER, List.of(), Gesture.NONE, "collection_style", raw, entry);
    }
    private Output styleChoice(String raw, Session s) { return ask(s, "style", raw, "", "Which style would you like to understand? You can also name another style.", options("Georgian|georgian", "Gothic|gothic", "Byzantine|byzantine")); }
    private Output problemChoice(String raw, Session s) { return ask(s, "problem", raw, "", "Which best describes the problem? You can also name a texture or compatibility issue.", options("Low frame rate|fps", "Pauses or stuttering|stutter", "Minecraft crashes|crash")); }

    private Output beginDiagnostic(String raw, String kind, Session s) {
        s.diagnosticKind = kind; s.diagnosticQuestion = raw; s.diagnosticSteps = 0;
        return switch (kind) {
            case "fps" -> ask(s, "diagnostic", raw, kind, "Let’s narrow down the expensive part before changing your build. Where is the frame rate low?", List.of(new Option("Only near a detailed build", "scene", "\\b(?:near|look|scene|area|statue|ornament|bust|detailed)\\b"), new Option("Everywhere I play", "everywhere", "\\b(?:everywhere|all the time|whole world)\\b"), new Option("After changing a pack or shader", "appearance", "\\b(?:shader|pack|appearance)\\b")));
            case "stutter" -> ask(s, "diagnostic", raw, kind, "When does the pause happen?", List.of(new Option("While moving or turning", "moving", "\\b(?:move|moving|turn|turning|chunk)\\b"), new Option("When opening a menu", "menu", "\\b(?:menu|inventory|picker|screen)\\b"), new Option("At intervals while standing still", "periodic", "\\b(?:interval|periodic|standing|seconds|regular)\\b")));
            case "crash" -> ask(s, "diagnostic", raw, kind, "Keep the exact error message. At what point does Minecraft stop?", List.of(new Option("Before the main menu", "startup", "\\b(?:startup|launch|before.*menu)\\b"), new Option("While loading a world", "world", "\\b(?:world|joining|server)\\b"), new Option("After a particular action", "action", "\\b(?:action|place|placing|click|copy)\\b")));
            case "visual" -> ask(s, "diagnostic", raw, kind, "Which part looks wrong?", List.of(new Option("Textures or connected patterns", "textures", "\\b(?:texture|connected|ctm|pattern)\\w*\\b"), new Option("Lighting or shiny materials", "lighting", "\\b(?:light|shiny|shine|shader|bronze)\\w*\\b"), new Option("An object is missing or distorted", "geometry", "\\b(?:missing|invisible|distorted|model|object)\\b")));
            default -> ask(s, "diagnostic", raw, "compatibility", "Where are you seeing the compatibility problem?", List.of(new Option("A launch or dependency error", "startup", "\\b(?:launch|dependency|requires|version error)\\b"), new Option("A server or multiplayer problem", "server", "\\b(?:server|multiplayer|disconnect)\\b"), new Option("An optional Collection pack", "pack", "\\b(?:pack|32x|64x|generation)\\b")));
        };
    }

    private Output diagnosticAnswer(String original, String detail, Session s, Engine e) {
        s.diagnosticSteps++;
        String specific = switch (detail) {
            case "scene" -> "Compare the same view with a simpler nearby view. A drop that follows the detailed scene is evidence to investigate visible geometry and rendering cost; it does not prove which mod caused it.";
            case "everywhere" -> "Check whether the low frame rate also happens in a simple scene. A problem everywhere needs broader settings and hardware context before blaming an ornament.";
            case "appearance" -> "Compare the same view after changing only one appearance setting, such as temporarily disabling the shader or one optional pack. Wait for resource loading to finish before comparing.";
            case "moving" -> "Note whether the pause follows entering new terrain or merely turning in the same loaded area. Those observations lead to different investigations.";
            case "menu" -> "Record which menu pauses and whether it happens on the first opening or every opening. That separates initial loading from a repeatable interaction problem.";
            case "periodic" -> "Record roughly how often the pause happens and whether it continues in a simple scene. A repeating pause is different evidence from continuously low FPS.";
            case "startup" -> "Keep the exact launch or dependency error and the loaded mod versions. A named missing dependency or mismatch is stronger evidence than the last mod you installed.";
            case "world" -> "Keep the crash report and note whether it affects one world or also a separate new test world. Preserve the original world and its content mods while gathering evidence.";
            case "action" -> "Write down the smallest sequence that repeats the crash, including the item or block and tool used. Keep the original world intact while investigating.";
            case "textures" -> "Check the active pack order and identify the exact block first. The reviewed family versions have their own texture renderers; adding another CTM mod is not a general fix.";
            case "lighting" -> "Compare the same object with the shader temporarily disabled. A change in shine can be a lighting or material-map difference; it does not prove a missing texture.";
            case "geometry" -> "Identify the object and whether its inventory icon, its placed model, or both are affected. Keep those observations separate when investigating rendering.";
            case "server" -> "Separate what happens on this client from what the server reports. Client-loaded mods alone do not establish the server’s versions or requirements.";
            default -> "Compare the official pack’s family compatibility marker with the installed block-content mods. The 32x and 64x packs are alternatives; resolution alone does not establish compatibility.";
        };
        JsonObject entry = new JsonObject(); entry.addProperty("answer", specific); entry.addProperty("shortAnswer", specific); entry.addProperty("nextStep", "Keep the exact versions, error text if present, and the smallest repeatable example. You can review those details before continuing on the website.");
        return new Output(specific + " If that does not narrow it down, I can take the original issue and these observations to my study.", Route.ANSWER, List.of(), Gesture.NONE, "troubleshooting_" + s.diagnosticKind, original, entry);
    }

    private Output simplify(String raw, Session s, Engine e) { if (s.lastEntry == null) return output("Which part would you like me to explain more simply?", Route.CLARIFY, "simplify", raw); String shortAnswer = text(s.lastEntry, "shortAnswer"); return output(shortAnswer.isEmpty() ? text(s.lastEntry, "answer").split("(?<=[.!?])\\s+")[0] : shortAnswer, Route.ANSWER, "simplify", s.lastQuestion); }
    private Output recall(String raw, Session s) { return output(s.lastQuestion.isEmpty() ? "We have not settled on a topic yet. Tell me what you are working on." : "We were discussing “" + s.lastQuestion + "”. " + (!s.pending.isEmpty() ? "That enquiry is still ready to take to my study." : "Would you like to carry on from there?"), Route.ANSWER, "session_recall", s.lastQuestion.isEmpty() ? raw : s.lastQuestion); }
    private Output unknown(String raw, Session s) { s.uncertaintyCount++; String origin = s.clarification != null ? s.clarification.question : raw; if (s.uncertaintyCount == 1 && s.clarification == null) return ask(s, "help", origin, "", "Let’s narrow that down. What kind of help are you looking for? You can choose a number or explain it in your own words.", options("Building design or styles|design", "Using a Collection mod or tool|use", "A technical problem|technical")); return handoff(origin, s, "I have not matched that reliably to my local notes. I can take the original enquiry and this exchange to my study so you do not have to start again."); }
    private Output handoff(String raw, Session s, String answer) { s.clarification = null; return output(answer, Route.HANDOFF, "website_handoff", raw); }
    private Output local(Engine e, String id, String original) { JsonObject card = e.cards.get(id); if (card == null) return output("Tell me a little more about what you want to do.", Route.CLARIFY, "clarify_other", original); return new Output(text(card, "answer"), Route.ANSWER, List.of(), Gesture.NONE, id, original, card); }
    private static Output output(String text, Route route, String id, String original) { return new Output(text, route, List.of(), Gesture.NONE, id, original, null); }
    private static Output gesture(Output value, Gesture gesture) { return new Output(value.text, value.route, value.choices, gesture, value.intent, value.origin, value.entry); }
    private Output ask(Session s, String kind, String original, String intent, String prompt, List<Option> options) { s.clarification = new Clarification(kind, original, intent, List.copyOf(options)); return new Output(prompt, Route.CLARIFY, options.stream().map(Option::label).toList(), Gesture.NONE, "clarify_" + kind, original, null); }
    private Output repeat(String prompt, Clarification q, Session s) { return ask(s, q.kind, q.question, q.intent, prompt, q.options); }

    private Reply record(String raw, Session s, Output out, boolean successfulEngine) {
        out = new Output(playerText(out.text), out.route, out.choices, out.gesture, out.intent, out.origin, out.entry);
        String origin = out.origin == null || out.origin.isBlank() ? raw : out.origin;
        s.turns.add(new Turn("user", raw)); StringBuilder historyAnswer = new StringBuilder(out.text);
        for (int i = 0; i < out.choices.size(); i++) historyAnswer.append('\n').append(i + 1).append(". ").append(out.choices.get(i));
        s.turns.add(new Turn("local_authored", historyAnswer.toString()));
        if (s.turns.size() > 24) { s.turns.subList(0, s.turns.size() - 24).clear(); s.historyTruncated = true; }
        if (out.route == Route.HANDOFF) { s.pending = origin; s.unanswered = origin; }
        else if (out.route == Route.CLARIFY) { s.unanswered = origin; }
        else if (out.route == Route.ANSWER) {
            s.clarification = null; s.uncertaintyCount = 0;
            if (!CONTEXTUAL.contains(out.intent)) {
                s.lastQuestion = origin; s.lastIntent = out.intent; s.pending = ""; s.unanswered = out.intent.startsWith("troubleshooting_") ? origin : "";
                if (!out.intent.equals("controls_general") && !out.intent.startsWith("tool_")) { s.controlQuestion = ""; s.controlOperation = ""; }
                if (out.entry != null) s.lastEntry = out.entry;
                else { JsonObject entry = new JsonObject(); entry.addProperty("answer", out.text); entry.addProperty("shortAnswer", out.text.split("(?<=[.!?])\\s+")[0]); s.lastEntry = entry; }
            }
        }
        return new Reply(out.text, out.route, out.choices, out.gesture, enquiryText(s));
    }

    /** A few display-only editorial prefixes; knowledge and uncertainty are kept.
     * Never apply these replacements to player wording or the pending enquiry. */
    private static String playerText(String answer) {
        return answer.replace("For an existing reviewed Daedalon lantern,", "For a Daedalon lantern,")
                .replace("For the reviewed Daedalon benches,", "For a Daedalon bench,")
                .replace("For a reviewed Daedalon statue,", "For a Daedalon statue,")
                .replace("For a reviewed Daedalon panel,", "For a Daedalon panel,")
                .replace("For the reviewed Daedalon width-selectable benches,", "For a Daedalon bench with a Width control,")
                .replace("For the reviewed Daedalon stone lanterns,", "For a Daedalon stone lantern,")
                .replace("The reviewed debug-stick Mount control", "The debug stick’s Mount control")
                .replace("The reviewed Daedalon Gothic Panel", "Daedalon’s Gothic Panel")
                .replace("The inspected default opener is", "The default opener is")
                .replace("The inspected catalogue includes", "The catalogue includes");
    }

    private static final Option INVALID = new Option("", "", "");
    private static Option select(String n, List<Option> options) {
        Matcher number = Pattern.compile("^(?:(?:option|number|no) )?([1-9])(?: please| thanks)?$").matcher(n);
        if (number.matches()) { int index = Integer.parseInt(number.group(1)) - 1; return index < options.size() ? options.get(index) : INVALID; }
        Matcher ordinal = Pattern.compile("^(?:(?:the|i mean|i meant|choose|let s try) )?(first|second|third)(?: one| option)?(?: please)?$").matcher(n);
        if (ordinal.matches()) { int index = List.of("first", "second", "third").indexOf(ordinal.group(1)); return index < options.size() ? options.get(index) : INVALID; }
        List<Option> matches = new ArrayList<>();
        for (Option option : options) if (norm(option.label).equals(n) || !option.match.isEmpty() && matches(n, option.match)) matches.add(option);
        if (matches.size() == 1) return matches.get(0);
        if (!matches(n, "\\b(?:not|no|what|why|how|can|could)\\b")) {
            List<String> words = List.of(n.split(" ")).stream().filter(word -> !Set.of("the", "a", "an", "about", "please", "i", "mean", "meant").contains(word)).toList();
            if (!words.isEmpty() && words.size() <= 4) { List<Option> unique = options.stream().filter(option -> words.stream().allMatch(word -> List.of(norm(option.label).split(" ")).contains(word))).toList(); if (unique.size() == 1) return unique.get(0); }
        }
        return null;
    }
    private static List<Option> options(String... values) { List<Option> out = new ArrayList<>(); for (String value : values) { String[] parts = value.split("\\|", 2); out.add(new Option(parts[0], parts[1], "")); } return out; }
    private static Speech tone(String raw) {
        String unquoted = QUOTED.matcher(raw.toLowerCase(Locale.ROOT).replace('’', '\'')).replaceAll("").replaceAll("\\bf[@4*]c?k\\b", "fuck");
        boolean rude = RUDE.matcher(unquoted).find(), swear = SWEAR.matcher(unquoted).find();
        String recognition = SWEAR.matcher(raw.toLowerCase(Locale.ROOT).replaceAll("\\bf[@4*]c?k\\b", "fuck")).replaceAll(" ").replaceAll("\\s+", " ").trim();
        Matcher useful = USEFUL.matcher(recognition); boolean hasQuestion = useful.find(); if (rude && hasQuestion) recognition = useful.group();
        return new Speech(recognition, rude, swear, hasQuestion);
    }
    private static boolean stillRequest(String n) { boolean still = matches(n, "\\b(?:motionless|stationary|unmoving|immobile)\\b|\\b(?:stay|keep|remain|be)\\b.{0,24}\\bstill\\b|\\b(?:stop|quit|cease) (?:staring|watching|looking|moving)\\b"), negated = matches(n, "\\b(?:don t|do not|not|no longer)\\b.{0,20}\\b(?:stay|remain|be|keep|stop)\\b.{0,20}\\b(?:still|motionless|stationary|looking|watching)\\b"); return still && !negated; }
    private static boolean rotationRequest(String n) { return matches(n, "\\b(?:rotate|roatate|reorient|rotation|orientation|turn (?:this|it|the|my)|copy|paste|worldedit|world edit|axiom|debug stick)\\b|\\b(?:point(?:ing|s)?|face|facing|turn)\\b.{0,35}\\b(?:other way|opposite|direction|backwards)\\b|\\b(?:change|choose|adjust)\\b.{0,65}\\b(?:direction|point(?:ing|s)?|facing|which way)\\b") && !matches(n, "\\b(?:not|don t|do not) (?:want to )?(?:rotate|turn|copy|paste)\\b"); }
    private static boolean isInstalledQuestion(String n) { return matches(n, "\\b(?:mod|mods|pack|packs|32x|64x|erydon|daedalon|themelios|metrakron|lithotheca|lithotecha)\\b") && matches(n, "\\b(?:installed|loaded|do i have|have i got|am i using|am i looking|at the moment)\\b") || matches(n, "\\b(?:pack|packs|32x|64x)\\b") && matches(n, "\\b(?:active|enabled|available)\\b"); }
    private static String diagnosticKind(String n) {
        if (matches(n, "\\b(?:crash\\w*|exit code|won t launch|will not launch|cannot launch|can not launch|fail\\w* to (?:launch|start))\\b")) return "crash";
        if (matches(n, "\\b(?:compatib\\w*|dependency|dependencies|mismatch|generation)\\b")) return "compatibility";
        if (matches(n, "\\b(?:menu|catalogue|catalog|inventory|picker|screen)\\b") && matches(n, "\\b(?:stall\\w*|slow\\w*|stutter\\w*|freez\\w*|hitch\\w*|pauses?|hangs?|lag\\w*)\\b")) return "menu";
        // Hanging an architectural object is ordinary placement vocabulary.
        // Require an explicit runtime subject/state before 'hang' means a pause.
        boolean runtimeHang = matches(n, "\\b(?:game|minecraft|client|screen|loading|world) (?:(?:keeps?|is|gets?|still|sometimes|always|just|now) ){0,3}hang(?:s|ing)?\\b|\\bhangs? (?:when|while|during|on) (?:loading|startup|gameplay|world loading)\\b|\\bhang(?:s|ing)?\\b.{0,25}\\bunresponsive\\b");
        if (runtimeHang || matches(n, "\\b(?:stutter\\w*|freez\\w*|hitch\\w*|pauses?|judder\\w*)\\b")) return "stutter";
        if (matches(n, "\\b(?:fps|frame ?rate|slow(?:er|s|ing)?|lag(?:gy|ging)?)\\b")) return "fps";
        if (matches(n, "\\b(?:missing|broken|not working|disappear\\w*|invisible|wrong|problem)\\b") && matches(n, "\\b(?:texture|ctm|connected|model|shader|lighting|pattern)\\w*\\b")) return "visual";
        return "";
    }
    private static boolean heldQuestion(String n) { return matches(n, "\\b(?:what|which)\\b.{0,25}\\b(?:holding|held item|in my hand)\\b|\\b(?:switched items|changed (?:my )?held item|my hand is empty)\\b") && !matches(n, "\\b(?:ignore|not about|i mean|question is about)\\b"); }
    private static boolean designQuestion(String n) { return matches(n, "\\b(?:build\\w*|design|facade|front|wall|roof|ridge|entrance|doorway|courtyard|hall|ornament|cornice|column|architecture|proportion|composition)\\b"); }
    private static boolean sceneObservation(String n) { return matches(n, "\\b(?:only|near|view|visible|look\\w*|away|scene)\\b") && matches(n, "\\b(?:statues?|ornaments?|busts?|detailed|garden|geometry|together|scene|view)\\b"); }
    private static boolean menuObservation(String n) { return matches(n, "\\b(?:first|reopen\\w*|opening|menu|catalogue|catalog|inventory|picker|repeat\\w*)\\b"); }
    private static boolean isControlClarification(Clarification q) { return q.kind.startsWith("control_") || q.kind.equals("confirm_daedalon"); }
    private static String controlTool(String n) { return matches(n, "\\bworld ?edit\\b") ? "worldedit" : matches(n, "\\baxiom\\b") ? "axiom" : matches(n, "\\b(?:vanilla|debug stick|(?:normal|standard|ordinary|minecraft) placement|normal minecraft)\\b") ? "vanilla" : ""; }
    private static String controlOperation(String n) {
        LinkedHashSet<String> operations = new LinkedHashSet<>();
        if (matches(n, "\\b(?:width|widen|wider|narrower|narrow|span)\\b")) operations.add("width");
        if (matches(n, "\\b(?:resize|size|bigger|smaller|larger|scale)\\b")) operations.add("size");
        if (matches(n, "\\b(?:brightness|light level|extinguish|relight)\\b|\\b(?:change|adjust|set|cycle|turn|switch)\\b.{0,35}\\b(?:light|glow|lit|off)\\b")) operations.add("light");
        if (matches(n, "\\b(?:hang|hanging|mount|mounting|attach|attaching|seated|ceiling)\\b")) operations.add("mount");
        if (matches(n, "\\boffset\\b")) operations.add("offset");
        if (matches(n, "\\b(?:rotate|roatate|reorient|rotation|orientation|facing|copy|paste)\\b|\\b(?:change|choose|adjust)\\b.{0,65}\\b(?:direction|point(?:ing|s)?|which way)\\b") || !operations.contains("light") && matches(n, "\\bturn (?:this|it|the|my)\\b")) operations.add("orientation");
        return operations.size() > 1 ? "ambiguous" : operations.isEmpty() ? "" : operations.iterator().next();
    }
    private static boolean exactCatalogueQuestion(String n) { return matches(n, "\\b(?:exact|registered|registry|block id|block names|identifier|give)\\b") && matches(n, "\\b(?:blocks?|ids?|cornice|column|forms?|names?|palette|identifier)\\b"); }

    /** Explicit positive domain evidence gates learned labels; confidence alone
     * cannot license an unrelated authored answer. These are domain boundaries,
     * not alternate trained responses or a claim to general language parsing. */
    private static boolean modelEligible(String id, String raw) {
        String n = norm(raw);
        return switch (id) {
            case "attention_on", "attention_off", "bronze_only", "fixed_height" -> false;
            case "held_info", "held_switch", "held_context" -> heldQuestion(n);
            case "installed_status" -> isInstalledQuestion(n);
            case "identity", "owl_identity", "capabilities" -> matches(n, "\\b(?:you|your|owl|glaux)\\b") && !designQuestion(n);
            case "website_identity", "website_handoff", "fuller_answer", "web_failure" -> affirmed(raw, "website|study|browser|link|fuller|detail");
            case "simplify" -> matches(n, "\\b(?:simpl|jargon|explain|last part)\\w*\\b");
            case "session_recall", "next_step", "ambiguous_followup", "topic_correction", "stay_local" -> matches(n, "\\b(?:last|previous|earlier|next|instead|mean|local|here|that|it)\\b");
            case "tone_banter" -> matches(n, "\\b(?:insult|frustrat|useless|stupid)\\w*\\b");
            case "find_block" -> affirmed(raw, "find|search|locate");
            case "exact_catalogue" -> exactCatalogueQuestion(n);
            case "daedalon_scope" -> matches(n, "\\b(?:daedalon|sculpture|decoration)\\b") && productOverviewQuestion(n);
            case "controls_general", "bench_width", "lantern_controls", "lantern_mount", "panel_mount", "statue_orientation", "axiom_copy" -> rotationRequest(n) || matches(n, "\\b(?:controls?|width|mount|hang|light|debug stick)\\b");
            case "georgian_gothic" -> affirmed(raw, "georgian") && affirmed(raw, "gothic");
            case "byzantine" -> affirmed(raw, "byzantine");
            case "grand_entrance", "doorway_focal" -> affirmed(raw, "entrance|doorway|approach|welcoming");
            case "coherent_palette" -> affirmed(raw, "materials?|palette|colou?r|stones?");
            case "proportions_first" -> designQuestion(n) && affirmed(raw, "proportions?|silhouette|width|height|scale");
            case "courtyard_balance" -> affirmed(raw, "courtyard|court|square");
            case "flat_facade" -> affirmed(raw, "facade|front|flat|depth");
            case "statue_placement" -> affirmed(raw, "statue|sculpture|bust") && !rotationRequest(n);
            case "restraint_detail", "calm_and_ornate" -> designQuestion(n) && affirmed(raw, "detail|ornate|quiet|restrain\\w*|calm|busy|fussy|decorat\\w*");
            case "repetition" -> designQuestion(n) && affirmed(raw, "repet\\w*|rhythm|monoton\\w*|boring|long");
            case "symmetry" -> designQuestion(n) && affirmed(raw, "symmetr\\w*|balance|uneven");
            case "composition_start", "gather_context" -> designQuestion(n);
            case "optional_discovery" -> affirmed(raw, "additional|optional|extra|another|recommend");
            case "pack_choice", "pack_conflict", "pack_action", "appearance_difference" -> affirmed(raw, "packs?|textures?|shader|appearance");
            case "bronze_shine" -> affirmed(raw, "bronze|metal|shine|shiny|reflection");
            case "compatibility", "scene_performance", "technical_diagnosis" -> !diagnosticKind(n).isEmpty();
            case "privacy" -> affirmed(raw, "privacy|private|record|data|send|capture|share");
            case "animation_cost" -> matches(n, "\\b(?:glaux|you|your)\\b") && affirmed(raw, "performance|cost|fps|animation|parser|recogniser");
            case "goodbye" -> matches(n, "\\b(?:bye|goodbye|see you)\\b");
            case "boundary" -> matches(n, "\\b(?:command|install|delete|medical|weather|stock)\\b");
            default -> false;
        };
    }
    private static boolean productQuestion(String raw, String product, Engine e) {
        String n = norm(raw);
        if (productOverviewQuestion(n) && !affirmed(raw, "server|compatib\\w*|download|release|version")) return true;
        if (matches(n, "^(?:erydon|daedalon|themelios|metrakron|lithotheca)(?: s purpose)?$")) return true;
        JsonObject notes = e.products.getAsJsonObject(product);
        if (notes == null) return false;
        for (JsonElement item : notes.getAsJsonArray("practicalHelp")) for (JsonElement keyword : item.getAsJsonObject().getAsJsonArray("keywords")) {
            String word = keyword.getAsString();
            if (!Set.of("what", "add", "building", "architecture", "mod").contains(word) && affirmed(raw, Pattern.quote(word))) return true;
        }
        return false;
    }
    private static boolean productOverviewQuestion(String n) { return matches(n, "\\b(?:purpose|overview|outline|tour|toolkit|roles?|useful|tell me about|explain|what (?:is|s|does|sort|kind|job)|what .* for|where .* fit)\\b|\\b(?:additions?|contents?|features?)\\b.{0,25}\\b(?:mostly|mainly|primarily|for)\\b|\\bwhy\\b.{0,35}\\b(?:keep|use|choose|include|install|add)\\b"); }
    private static boolean releaseQuestion(String raw) { return affirmed(raw, "downloads?|releases?|latest|ports?|supported versions?|new (?:compatible )?(?:file|version)|appeared online"); }
    private static List<String> products(String n) { LinkedHashSet<String> out = new LinkedHashSet<>(); for (String word : n.replace(" s ", " ").split(" ")) { String id = switch (word) { case "eryond" -> "erydon"; case "lithotecha" -> "lithotheca"; default -> word; }; if (!PRODUCT_NAMES.containsKey(id) && word.length() >= 5) { List<String> close = PRODUCT_NAMES.keySet().stream().filter(key -> editDistance(word, key) == 1).toList(); if (close.size() == 1) id = close.get(0); } if (PRODUCT_NAMES.containsKey(id)) out.add(id); } return List.copyOf(out); }
    private static List<String> styles(String raw, Engine e) { List<String> out = new ArrayList<>(); if (affirmed(raw, "gothic ornate")) return List.of("gothic_ornate"); for (String style : List.of("georgian", "gothic", "byzantine", "modern")) if (affirmed(raw, style)) out.add(style); return out.stream().limit(2).toList(); }

    /** Clause-local rejection prevents a negated keyword from triggering an
     * answer. This deliberately does not claim to resolve arbitrary negation. */
    private static boolean affirmed(String raw, String expression) {
        for (String clause : raw.split("[.!?;]|(?i)\\b(?:but|however)\\b")) {
            String n = norm(clause); Matcher term = Pattern.compile("\\b(?:" + expression + ")\\b").matcher(n);
            while (term.find()) {
                String before = n.substring(0, term.start());
                if (!matches(before, "\\b(?:not|no|without|don t|do not|rather than|instead of|ignore|stop|avoid|skip|exclude|omit|leave out)\\b(?: [a-z0-9]+){0,5} $")) return true;
            }
        }
        return false;
    }
    private static Subject subject(String n, Session s, Context c) {
        String[] words = n.split(" "); LinkedHashSet<String> positive = new LinkedHashSet<>(); String found = "";
        for (int i = 0; i < words.length; i++) { String word = words[i].replaceFirst("s$", ""), family = SUBJECT_WORDS.get(word); if (family == null && word.length() >= 5) { List<String> close = SUBJECT_WORDS.keySet().stream().filter(key -> key.length() >= 5 && editDistance(word, key) == 1).toList(); if (close.size() == 1) family = SUBJECT_WORDS.get(close.get(0)); } if (family == null) continue;
            String before = String.join(" ", java.util.Arrays.copyOfRange(words, Math.max(0, i - 3), i)); if (matches(before, "(?:not|rather than|instead of|don t mean|do not mean)(?: the)?$")) continue; positive.add(family); found = family; }
        if (!found.isEmpty()) { if (positive.size() > 1 && matches(n, "\\bor\\b")) return new Subject("", "", true); String product = matches(n, "\\b(?:daedalon|hedra|exedra)\\b") ? "daedalon" : ""; if (product.isEmpty() && c.held.known && c.held.family.equals(found)) product = c.held.product; if (product.isEmpty() && s.subjectFamily.equals(found)) product = s.subjectProduct; return new Subject(found, product, false); }
        if (matches(n, "\\b(?:this|these|held|in my hand)\\b") && c.held.known && !c.held.empty && !c.held.family.isEmpty()) return new Subject(c.held.family, c.held.product, false);
        if (matches(n, "\\b(?:it|this|that|these|those|one|same)\\b")) { if (!s.subjectFamily.isEmpty()) return new Subject(s.subjectFamily, s.subjectProduct, false); if (c.held.known && !c.held.empty && !c.held.family.isEmpty()) return new Subject(c.held.family, c.held.product, false); }
        return null;
    }
    private static int editDistance(String a, String b) { if (Math.abs(a.length() - b.length()) > 1) return 9; int[] row = new int[b.length() + 1]; for (int j = 0; j < row.length; j++) row[j] = j; for (int i = 1; i <= a.length(); i++) { int[] next = new int[b.length() + 1]; next[0] = i; for (int j = 1; j <= b.length(); j++) next[j] = Math.min(Math.min(next[j - 1] + 1, row[j] + 1), row[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1)); row = next; } return row[b.length()]; }
    private static String norm(String value) { return Normalizer.normalize(safe(value), Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).replace('’', '\'').replaceAll("[^a-z0-9'\\s-]", " ").replace("'", " ").replaceAll("\\s+", " ").replaceAll("\\bgeorgain\\b", "georgian").replaceAll("\\bbyzantne\\b", "byzantine").replaceAll("\\bgothc\\b", "gothic").trim(); }
    private static boolean matches(String text, String regex) { return Pattern.compile(regex).matcher(text).find(); }
    private static String safe(String text) { return text == null ? "" : text; }
    private static String lowerFirst(String text) { return text.isEmpty() ? text : Character.toLowerCase(text.charAt(0)) + text.substring(1); }
    private static String text(JsonObject object, String field) { if (object == null || !object.has(field) || object.get(field).isJsonNull()) return ""; return object.get(field).getAsString(); }
    private static JsonArray array(JsonObject object, String field, String child) { JsonObject parent = object == null ? null : object.getAsJsonObject(field); return parent != null && parent.has(child) ? parent.getAsJsonArray(child) : new JsonArray(); }

    private record Engine(GlauxIntentModel model, Map<String, JsonObject> cards, JsonObject controls, JsonObject knowledge, JsonObject products, JsonObject styles) {
        static Engine load() throws IOException { JsonObject library = read("answers.json"), knowledge = read("collection.json"); Map<String, JsonObject> cards = new LinkedHashMap<>(); for (JsonElement item : library.getAsJsonArray("entries")) { JsonObject card = item.getAsJsonObject(); cards.put(text(card, "id"), card); } return new Engine(GlauxIntentModel.load(), Map.copyOf(cards), library.getAsJsonObject("controlsBySubject"), knowledge, knowledge.getAsJsonObject("products"), knowledge.getAsJsonObject("stylePrimers")); }
        static JsonObject read(String name) throws IOException { try (InputStream raw = GlauxDialogueService.class.getResourceAsStream("/assets/daedalon/glaux/brain/" + name)) { if (raw == null) throw new IOException("Glaux notes resource missing"); try (InputStreamReader reader = new InputStreamReader(raw, StandardCharsets.UTF_8)) { return JsonParser.parseReader(reader).getAsJsonObject(); } } }
    }
}
