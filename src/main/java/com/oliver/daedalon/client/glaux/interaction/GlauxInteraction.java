package com.oliver.daedalon.client.glaux.interaction;

import com.oliver.daedalon.client.glaux.GlauxVisuals;
import com.oliver.daedalon.client.glaux.dialogue.GlauxDialogueService;
import com.oliver.daedalon.glaux.GlauxRegistry;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.Items;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/** A small HUD composer. No chat Screen, mouse unlock, world scan or online AI. */
public final class GlauxInteraction {
    private static final int SCAN_BASE = 0x10000;
    private static final double RANGE_SQUARED = 36;
    private static GlauxInteraction instance;
    private final MinecraftClient client = MinecraftClient.getInstance();
    private final GlauxInputState input = new GlauxInputState();
    private final GlauxFocusState focus = new GlauxFocusState();
    private final GlauxSessionCache<View> sessions = new GlauxSessionCache<>(24);
    private final GlauxDialogueService dialogue = new GlauxDialogueService();
    private final GlauxContextSampler context = new GlauxContextSampler();
    private final GlauxInteractionConfig config = GlauxInteractionConfig.load();
    private final KeyBinding speak, pin, motion;
    private final Matrix4f projection = new Matrix4f();
    private final Quaternionf inverseCamera = new Quaternionf();
    private final Vector3f relative = new Vector3f();
    private final Vector4f projected = new Vector4f();
    private Vec3d cameraPosition = Vec3d.ZERO;
    private ClientWorld world;
    private GlauxComposerWidget composer;
    private Long displayed;
    private boolean projectionReady, ignoreActivationCharacter;
    private int activationToken = -1;
    private int layoutGeneration;
    private String cachedHint;
    private int cachedHintWidth = -1, cachedHintGeneration = -1;
    private List<OrderedText> hintLines = List.of();
    private long noticeUntil;
    private String notice = "";

    static final class View {
        final GlauxDialogueService.Session session;
        String draft = "", question = "", handoffDraft;
        String exportedQuestion;
        com.oliver.daedalon.client.glaux.dialogue.HandoffCodec.Export exported;
        GlauxDialogueService.Context lastContext;
        GlauxDialogueService.Reply reply;
        List<String> choices = List.of(), historyPreview = List.of();
        List<String> wrappedChoiceSource = List.of();
        List<List<OrderedText>> choiceLines = List.of();
        List<OrderedText> wrappedLines = List.of();
        String wrappedAnswer;
        int wrappedWidth = -1;
        int renderedLayoutGeneration = -1;
        boolean busy, showHistory;
        int scroll;
        View(GlauxDialogueService.Session session) { this.session = session; }
    }

    private GlauxInteraction() {
        speak = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.daedalon.glaux.speak", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_G, "category.daedalon.glaux"));
        pin = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.daedalon.glaux.pin_focus", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, "category.daedalon.glaux"));
        motion = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.daedalon.glaux.reduced_motion", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, "category.daedalon.glaux"));
    }

    public static void register() {
        if (instance != null) return;
        instance = new GlauxInteraction();
        GlauxVisuals.setReducedMotion(instance.config.reducedMotion);
        instance.context.registerReload();
        ClientTickEvents.START_CLIENT_TICK.register(ignored -> instance.tick());
        HudRenderCallback.EVENT.register((draw, delta) -> instance.render(draw, delta));
        WorldRenderEvents.BLOCK_OUTLINE.register((render, outline) -> {
            Long active = instance.focus.active();
            return !instance.input.focused() || active == null || instance.client.world == null
                    || !GlauxRegistry.isGlaux(outline.blockState())
                    || GlauxRegistry.basePos(instance.client.world, outline.blockPos()).asLong() != active;
        });
        WorldRenderEvents.END.register(render -> {
            if (instance.visibleOwl() == null) return;
            instance.projection.set(render.projectionMatrix());
            instance.inverseCamera.set(render.camera().getRotation()).conjugate();
            instance.cameraPosition = render.camera().getPos();
            instance.projectionReady = true;
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> client.execute(() -> instance.context.scanPacks(client)));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> instance.resetWorld());
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> instance.dialogue.close());
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (!world.isClient || player != instance.client.player || instance.client.currentScreen != null
                    || !instance.useMayFocus() || !GlauxRegistry.isGlaux(world.getBlockState(hit.getBlockPos()))) return ActionResult.PASS;
            instance.activate(GlauxRegistry.basePos(world, hit.getBlockPos()));
            return ActionResult.SUCCESS;
        });
        if (Boolean.getBoolean("daedalon.debug.glauxChecks")) GlauxClientChecks.register();
    }

    public static boolean reducedMotion() { return instance != null && instance.config.reducedMotion; }
    public static boolean isComposing() { return instance != null && instance.input.focused(); }
    public static void invalidateLayout() {
        if (instance != null) {
            instance.layoutGeneration++;
            if (instance.composer != null) instance.composer.invalidateLayout();
        }
    }

    private View view(long pos) { return sessions.get(pos, () -> new View(dialogue.createSession())); }
    private static long now() { return System.nanoTime() / 1_000_000L; }
    private String tr(String suffix, Object... values) { return Text.translatable("gui.daedalon.glaux." + suffix, values).getString(); }

    private boolean useMayFocus() {
        return client.player != null && GlauxInteractionIntent.useMayFocus(
                client.player.isSneaking() || client.options.sneakKey.isPressed(),
                client.player.getMainHandStack().isOf(Items.DEBUG_STICK) || client.player.getOffHandStack().isOf(Items.DEBUG_STICK));
    }

    private BlockPos target() {
        if (client.player == null || client.world == null || !(client.crosshairTarget instanceof BlockHitResult hit)) return null;
        if (!GlauxRegistry.isGlaux(client.world.getBlockState(hit.getBlockPos()))) return null;
        BlockPos pos = GlauxRegistry.basePos(client.world, hit.getBlockPos());
        return valid(pos) ? pos : null;
    }

    private boolean valid(BlockPos pos) {
        return client.world != null && client.player != null && client.world.isChunkLoaded(pos)
                && GlauxRegistry.isGlaux(client.world.getBlockState(pos))
                && client.player.squaredDistanceTo(Vec3d.ofCenter(pos).add(0, .65, 0)) <= RANGE_SQUARED;
    }

    private void tick() {
        ignoreActivationCharacter = false;
        if (client.world != world) { resetWorld(); world = client.world; }
        reconcileReleases();
        if (client.world == null || client.player == null || !client.player.isAlive()
                || client.currentScreen != null || !client.isWindowFocused() || client.isPaused()) {
            release(false);
            focus.reset();
            return;
        }
        BlockPos gazed = target();
        Long active = focus.active();
        if (active != null && !valid(BlockPos.fromLong(active))) release(true);
        boolean lost = focus.tick(gazed == null ? null : gazed.asLong(), now(), config.pinnedFocus);
        if (lost && input.focused()) release(false);
        if (input.focused() && composer != null) composer.tick();
        suppressBindings();
    }

    private void resetWorld() {
        release(false);
        focus.reset();
        sessions.clear();
        displayed = null;
        projectionReady = false;
        world = null;
    }

    private Long visibleOwl() {
        if (client.world == null || client.player == null || client.currentScreen != null) return null;
        return focus.active() != null ? focus.active() : focus.hint(now());
    }

    private boolean activate(BlockPos pos) {
        if (!valid(pos) || client.currentScreen != null || !client.isWindowFocused()) return false;
        if (input.focused() && focus.active() != null && focus.active() == pos.asLong()) return true;
        release(false);
        View state = view(pos.asLong());
        focus.activate(pos.asLong(), now());
        input.begin();
        KeyBinding.unpressAll();
        ensureComposer();
        composer.setText(state.draft);
        composer.setFocused(true);
        displayed = pos.asLong();
        if (!client.mouse.isCursorLocked()) client.mouse.lockCursor();
        GlauxVisuals.gesture(pos, "acknowledge");
        return true;
    }

    private void ensureComposer() {
        if (composer != null) return;
        composer = new GlauxComposerWidget(client.textRenderer, 0, 0, 240, 17, Text.translatable("gui.daedalon.glaux.composer"));
        composer.setMaxLength(2000);
        composer.setDrawsBackground(false);
        composer.setEditableColor(0xff302a22);
        composer.setPlaceholder(Text.translatable("gui.daedalon.glaux.placeholder"));
        composer.setChangedListener(value -> {
            Long active = focus.active();
            if (active != null) view(active).draft = value;
        });
    }

    private void release(boolean dismiss) {
        if (input.focused()) {
            Long active = focus.active();
            // A gaze timeout clears active first; displayed still names the draft owner.
            if (active == null) active = displayed;
            if (active != null && composer != null) {
                View state = sessions.peek(active);
                if (state != null) state.draft = composer.getText();
            }
            input.end();
            KeyBinding.unpressAll();
            if (composer != null) composer.setFocused(false);
        }
        focus.release(dismiss);
    }

    private void submit() {
        Long pos = focus.active();
        if (pos == null || composer == null) return;
        View state = view(pos);
        if (state.busy || composer.getText().isBlank()) return;
        String question = composer.getText();
        if (question.length() > 2000) return;
        state.busy = true;
        updateHistory(state, false);
        state.question = question;
        state.scroll = 0;
        state.lastContext = context.sample(client);
        state.handoffDraft = null;
        state.exported = null;
        composer.setText("");
        ClientWorld submittedWorld = client.world;
        dialogue.respond(question, state.session, state.lastContext).whenComplete((reply, error) -> client.execute(() -> {
            if (client.world != submittedWorld || sessions.peek(pos) != state) return;
            state.busy = false;
            if (error != null) {
                state.reply = null;
                state.choices = List.of();
                state.draft = question;
                if (input.focused() && pos.equals(focus.active())) composer.setText(question);
                showNotice("failed");
                return;
            }
            state.reply = reply;
            state.choices = reply.choices().stream().limit(3).toList();
            updateHistory(state, true);
            state.handoffDraft = reply.exactQuestionForWebsite();
            String gesture = reply.gesture().name().toLowerCase(java.util.Locale.ROOT);
            GlauxVisuals.gesture(BlockPos.fromLong(pos), gesture);
            if (reply.gesture() == GlauxDialogueService.Gesture.STILL) GlauxVisuals.setAttentive(BlockPos.fromLong(pos), false);
            if (reply.gesture() == GlauxDialogueService.Gesture.ATTENTIVE) GlauxVisuals.setAttentive(BlockPos.fromLong(pos), true);
        }));
    }

    private void showNotice(String key) { notice = tr(key); noticeUntil = now() + 4000; }

    private void updateHistory(View state, boolean excludeLatest) {
        List<GlauxDialogueService.Turn> history = state.session.history();
        int end = Math.max(0, history.size() - (excludeLatest ? 2 : 0)), start = Math.max(0, end - 4);
        state.historyPreview = history.subList(start, end).stream().map(turn ->
                (turn.role().equals("user") ? "› " : "Glaux: ") + turn.text()).toList();
    }

    private void togglePin() {
        config.pinnedFocus = !config.pinnedFocus;
        config.save();
        showNotice(config.pinnedFocus ? "pinned_on" : "pinned_off");
    }
    private void toggleMotion() {
        config.reducedMotion = !config.reducedMotion;
        config.save();
        GlauxVisuals.setReducedMotion(config.reducedMotion);
        showNotice(config.reducedMotion ? "motion_on" : "motion_off");
    }

    private void openStudy() {
        Long pos = focus.active();
        if (pos == null) return;
        View state = view(pos);
        if (state.busy) return;
        String unsent = composer == null ? "" : composer.getText();
        if (!unsent.isBlank()) {
            state.handoffDraft = unsent;
            state.lastContext = context.sample(client);
        } else if (state.reply == null || state.reply.route() == GlauxDialogueService.Route.BOUNDARY) return;
        if (state.handoffDraft == null || state.handoffDraft.isBlank()) state.handoffDraft = dialogue.enquiryText(state.session);
        if (state.handoffDraft.isBlank()) return;
        release(false);
        client.setScreen(new GlauxHandoffReviewScreen(dialogue, state, state.lastContext == null ? context.sample(client) : state.lastContext));
    }

    private void copyEnquiry() {
        Long pos = focus.active();
        if (pos == null) return;
        View state = view(pos);
        if (state.busy) return;
        try {
            String pending = composer != null && !composer.getText().isBlank() ? composer.getText()
                    : state.handoffDraft == null || state.handoffDraft.isBlank() ? dialogue.enquiryText(state.session) : state.handoffDraft;
            client.keyboard.setClipboard(dialogue.createHandoff(state.session, state.lastContext == null ? context.sample(client) : state.lastContext, pending).fallbackText());
            showNotice("copied");
        } catch (RuntimeException error) { showNotice("copy_failed"); }
    }

    public static boolean onKey(long window, int key, int scan, int action, int modifiers) {
        GlauxInteraction self = instance;
        if (self == null || window != self.client.getWindow().getHandle()) return false;
        int token = key == GLFW.GLFW_KEY_UNKNOWN ? SCAN_BASE + (scan & 0xffff) : key;
        boolean freshPress = action == GLFW.GLFW_PRESS && !self.input.isPhysicalDown(token);
        boolean captured = self.input.observe(token, action);
        if (action == GLFW.GLFW_RELEASE) {
            if (self.activationToken == token) self.activationToken = -1;
            if (captured) KeyBinding.setKeyPressed(InputUtil.fromKeyCode(key, scan), false);
            return captured;
        }
        if (self.client.currentScreen != null || self.client.world == null || !self.client.isWindowFocused()) return captured;
        if (!self.input.focused()) {
            if (self.input.isQuarantined(token)) return true;
            if (freshPress && self.pin.matchesKey(key, scan)) { self.togglePin(); return true; }
            if (freshPress && self.motion.matchesKey(key, scan)) { self.toggleMotion(); return true; }
            boolean explicit = self.speak.matchesKey(key, scan), use = self.client.options.useKey.matchesKey(key, scan) && self.useMayFocus();
            if (freshPress && (explicit || use)) {
                BlockPos pos = use ? self.target() : self.focus.hint(now()) == null ? null : BlockPos.fromLong(self.focus.hint(now()));
                if (pos != null && self.activate(pos)) {
                    self.activationToken = token;
                    self.ignoreActivationCharacter = key >= GLFW.GLFW_KEY_SPACE && key <= GLFW.GLFW_KEY_GRAVE_ACCENT;
                    return true;
                }
            }
            return captured;
        }
        if (token == self.activationToken) {
            self.ignoreActivationCharacter = key >= GLFW.GLFW_KEY_SPACE && key <= GLFW.GLFW_KEY_GRAVE_ACCENT;
            return true;
        }
        boolean first = freshPress, ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        if (first && key == GLFW.GLFW_KEY_ESCAPE) { self.release(true); return true; }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            if (first) { if (ctrl) self.openStudy(); else self.submit(); }
            return true;
        }
        if (first && (self.pin.matchesKey(key, scan) || ctrl && key == GLFW.GLFW_KEY_P)) { self.togglePin(); return true; }
        if (first && (self.motion.matchesKey(key, scan) || ctrl && key == GLFW.GLFW_KEY_M)) { self.toggleMotion(); return true; }
        if (first && ctrl && key == GLFW.GLFW_KEY_H) {
            Long pos = self.focus.active(); if (pos != null) self.view(pos).showHistory = !self.view(pos).showHistory;
            return true;
        }
        if (first && ctrl && (modifiers & GLFW.GLFW_MOD_SHIFT) != 0 && key == GLFW.GLFW_KEY_C) { self.copyEnquiry(); return true; }
        if (key == GLFW.GLFW_KEY_PAGE_UP || key == GLFW.GLFW_KEY_PAGE_DOWN) {
            Long pos = self.focus.active(); if (pos != null) self.view(pos).scroll = Math.max(0, self.view(pos).scroll + (key == GLFW.GLFW_KEY_PAGE_DOWN ? 5 : -5));
            return true;
        }
        self.ensureComposer();
        if (ctrl && key == GLFW.GLFW_KEY_V) {
            if (first) {
                int capacity = 2000 - self.composer.getText().length() + self.composer.getSelectedText().length();
                self.composer.write(GlauxTextLimits.prefix(self.client.keyboard.getClipboard(), capacity));
            }
            return true;
        }
        self.composer.keyPressed(key, scan, modifiers);
        return true;
    }

    public static boolean onChar(long window, int codePoint, int modifiers) {
        GlauxInteraction self = instance;
        if (self != null && window == self.client.getWindow().getHandle() && self.client.isWindowFocused()
                && self.client.currentScreen instanceof GlauxHandoffReviewScreen review) return review.codePoint(codePoint);
        if (self == null || !self.input.focused() || window != self.client.getWindow().getHandle()
                || self.client.currentScreen != null || !self.client.isWindowFocused()) return false;
        if (self.ignoreActivationCharacter) { self.ignoreActivationCharacter = false; return true; }
        if (Character.isValidCodePoint(codePoint)) {
            self.ensureComposer();
            String text = new String(Character.toChars(codePoint));
            int capacity = 2000 - self.composer.getText().length() + self.composer.getSelectedText().length();
            if (text.length() <= capacity) self.composer.write(text);
        }
        return true;
    }

    public static boolean onMouseButton(long window, int button, int action, int modifiers) {
        GlauxInteraction self = instance;
        if (self == null || window != self.client.getWindow().getHandle()) return false;
        boolean captured = self.input.observe(GlauxInputState.mouse(button), action);
        // Let vanilla clear Mouse's own button latches on every release.
        if (action == GLFW.GLFW_RELEASE) return false;
        if (self.client.currentScreen != null || self.client.world == null) return captured;
        if (captured) return true;
        if (action == GLFW.GLFW_PRESS && self.client.options.useKey.matchesMouse(button) && self.useMayFocus()) {
            BlockPos pos = self.target();
            if (pos != null && self.activate(pos)) return true;
        }
        return false;
    }

    public static boolean onScroll(long window, double amount) {
        GlauxInteraction self = instance;
        if (self == null || !self.input.focused() || window != self.client.getWindow().getHandle()) return false;
        Long pos = self.focus.active();
        if (pos != null) self.view(pos).scroll = Math.max(0, self.view(pos).scroll - (int)Math.signum(amount) * 3);
        return true;
    }

    public static void afterBindingPoll() { if (instance != null) instance.suppressBindings(); }
    public static void onWindowFocusChanged(boolean focused) {
        if (!focused && instance != null) { instance.release(false); instance.focus.reset(); }
    }

    /** Package-private instrumentation, inert unless the opt-in native fixture runs. */
    static DebugState debugState() {
        requireDebug();
        Long active = instance.focus.active(), visible = instance.visibleOwl(), key = visible == null ? instance.displayed : visible;
        View view = key == null ? null : instance.sessions.peek(key);
        int[] point = key == null ? new int[]{-1, -1} : instance.anchor(key,
                instance.client.getWindow().getScaledWidth(), instance.client.getWindow().getScaledHeight());
        int turns = view == null || view.busy ? -1 : (int)view.session.history().stream().filter(turn -> turn.role().equals("user")).count();
        return new DebugState(instance.input.focused(), active, instance.focus.hint(now()), view == null ? "" : view.draft,
                view == null ? "" : view.question, view == null || view.reply == null ? "" : view.reply.text(),
                view == null ? List.of() : view.choices, view != null && view.busy,
                view == null || view.busy ? "" : instance.dialogue.enquiryText(view.session), turns,
                instance.projectionReady, point[0], point[1], instance.config.pinnedFocus);
    }

    static void debugRelease() { requireDebug(); instance.release(false); }
    static boolean debugQuarantined(int key) { requireDebug(); return instance.input.isQuarantined(key); }
    static java.util.Map<String, Object> debugProjection() {
        requireDebug();
        java.util.Map<String, Object> values = new java.util.LinkedHashMap<>();
        Long owl = instance.visibleOwl();
        values.put("owl", owl == null ? null : BlockPos.fromLong(owl).toShortString());
        values.put("ready", instance.projectionReady);
        values.put("cameraPosition", List.of(instance.cameraPosition.x, instance.cameraPosition.y, instance.cameraPosition.z));
        Quaternionf camera = instance.inverseCamera.conjugate(new Quaternionf());
        values.put("cameraRotation", List.of(camera.x, camera.y, camera.z, camera.w));
        values.put("projectionMatrix", instance.projection.get(new float[16]));
        values.put("scaledWindow", List.of(instance.client.getWindow().getScaledWidth(), instance.client.getWindow().getScaledHeight()));
        if (instance.client.player != null) {
            values.put("playerPosition", List.of(instance.client.player.getX(), instance.client.player.getY(), instance.client.player.getZ()));
            values.put("playerYawPitch", List.of(instance.client.player.getYaw(), instance.client.player.getPitch()));
        }
        if (owl != null) {
            int[] anchor = instance.anchor(owl, instance.client.getWindow().getScaledWidth(), instance.client.getWindow().getScaledHeight());
            values.put("anchor", anchor);
            values.put("clip", List.of(instance.projected.x, instance.projected.y, instance.projected.z, instance.projected.w));
        }
        return values;
    }
    private static void requireDebug() {
        if (!Boolean.getBoolean("daedalon.debug.glauxChecks") || instance == null) throw new IllegalStateException("Native Glaux checks are not enabled");
    }
    record DebugState(boolean composing, Long active, Long hint, String draft, String question, String reply,
                      List<String> choices, boolean busy, String enquiry, int userTurns, boolean projectionReady,
                      int anchorX, int anchorY, boolean pinned) { }

    private void suppressBindings() {
        if (input.focused()) {
            KeyBinding.unpressAll();
            if (client.player != null) {
                client.player.input.movementForward = 0;
                client.player.input.movementSideways = 0;
                client.player.input.jumping = false;
                client.player.input.sneaking = false;
            }
        }
        for (int token : input.blockedTokens()) {
            InputUtil.Key key = token >= GlauxInputState.MOUSE_BASE ? InputUtil.Type.MOUSE.createFromCode(token - GlauxInputState.MOUSE_BASE)
                    : token >= SCAN_BASE ? InputUtil.Type.SCANCODE.createFromCode(token - SCAN_BASE) : InputUtil.Type.KEYSYM.createFromCode(token);
            KeyBinding.setKeyPressed(key, false);
        }
    }

    private void reconcileReleases() {
        long window = client.getWindow().getHandle();
        for (int token : input.blockedTokens()) {
            boolean up = token >= GlauxInputState.MOUSE_BASE ? GLFW.glfwGetMouseButton(window, token - GlauxInputState.MOUSE_BASE) == GLFW.GLFW_RELEASE
                    : token < SCAN_BASE && token >= GLFW.GLFW_KEY_SPACE && token <= GLFW.GLFW_KEY_LAST && GLFW.glfwGetKey(window, token) == GLFW.GLFW_RELEASE;
            if (up) input.releaseIfUp(token);
        }
    }

    private int[] anchor(long pos, int width, int height) {
        if (!projectionReady) return new int[]{width / 2, height / 2};
        BlockPos block = BlockPos.fromLong(pos);
        relative.set((float)(block.getX() + .5 - cameraPosition.x), (float)(block.getY() + 1.65 - cameraPosition.y), (float)(block.getZ() + .5 - cameraPosition.z));
        GlauxHudProjection.toClip(relative, inverseCamera, projection, projected);
        if (projected.w <= .001f) return new int[]{width - 14, height / 2};
        return new int[]{Math.round((projected.x / projected.w * .5f + .5f) * width), Math.round((.5f - projected.y / projected.w * .5f) * height)};
    }

    private void render(DrawContext draw, float delta) {
        Long pos = visibleOwl();
        if (pos == null || client.options.hudHidden || client.currentScreen != null) return;
        int screenWidth = draw.getScaledWindowWidth(), screenHeight = draw.getScaledWindowHeight();
        int[] anchor = anchor(pos, screenWidth, screenHeight);
        if (!input.focused()) {
            if (!useMayFocus() && speak.isUnbound()) return;
            String hint = useMayFocus() ? tr("hint", client.options.useKey.getBoundKeyLocalizedText(), speak.getBoundKeyLocalizedText())
                    : Text.translatable("key.daedalon.glaux.speak").getString() + ": " + speak.getBoundKeyLocalizedText().getString();
            int width = Math.min(GlauxHudLayout.width(screenWidth), client.textRenderer.getWidth(hint) + 18);
            if (!hint.equals(cachedHint) || width != cachedHintWidth || cachedHintGeneration != layoutGeneration) {
                hintLines = client.textRenderer.wrapLines(Text.literal(hint), width - 14).stream().limit(2).toList();
                cachedHint = hint; cachedHintWidth = width; cachedHintGeneration = layoutGeneration;
            }
            int height = 10 + hintLines.size() * 10;
            int x = GlauxHudLayout.x(anchor[0], screenWidth, width), y = Math.max(8, Math.min(screenHeight - height - 8, anchor[1]));
            draw.fill(x, y, x + width, y + height, 0xeaf8f3e9);
            draw.drawBorder(x, y, width, height, 0xffaa8855);
            for (int i = 0; i < hintLines.size(); i++) draw.drawText(client.textRenderer, hintLines.get(i), x + 7, y + 6 + i * 10, 0xff4c3b26, false);
            return;
        }
        View state = view(pos);
        int width = GlauxHudLayout.width(screenWidth), content = width - 20;
        String answer = state.busy ? tr("thinking") : state.reply == null ? tr("greeting") : state.reply.text();
        List<String> choices = state.reply == null || state.busy ? List.of() : state.choices;
        if (state.wrappedWidth != content || state.renderedLayoutGeneration != layoutGeneration || !answer.equals(state.wrappedAnswer) || choices != state.wrappedChoiceSource) {
            state.wrappedLines = client.textRenderer.wrapLines(Text.literal(answer), content);
            java.util.ArrayList<List<OrderedText>> lines = new java.util.ArrayList<>();
            for (int i = 0; i < choices.size(); i++) lines.add(client.textRenderer.wrapLines(Text.literal((i + 1) + ". " + choices.get(i)), content).stream().limit(2).toList());
            state.choiceLines = List.copyOf(lines);
            state.wrappedChoiceSource = choices;
            state.wrappedWidth = content;
            state.wrappedAnswer = answer;
            state.renderedLayoutGeneration = layoutGeneration;
        }
        List<OrderedText> answerLines = state.wrappedLines;
        int choiceSpace = state.choiceLines.stream().mapToInt(lines -> lines.size() * 10 + 3).sum();
        int historyLines = state.showHistory ? Math.min(4, Math.max(0, (screenHeight - 190) / 10)) : 0;
        int fixed = 90 + choiceSpace + (choices.isEmpty() ? 0 : 13) + historyLines * 10;
        int available = Math.max(1, (screenHeight - 16 - fixed) / 10);
        int shown = Math.min(10, Math.min(available, answerLines.size()));
        int height = fixed + shown * 10;
        int x = GlauxHudLayout.x(anchor[0], screenWidth, width);
        int y = Math.max(8, Math.min(screenHeight - height - 8, anchor[1] - 25));
        draw.fill(x, y, x + width, y + height, 0xf1f8f3e9);
        draw.drawBorder(x, y, width, height, 0xffaa8855);
        draw.drawText(client.textRenderer, tr("name"), x + 10, y + 9, 0xff795226, false);
        int cursor = y + 24;
        if (!state.question.isBlank()) {
            draw.drawText(client.textRenderer, client.textRenderer.trimToWidth(tr("you", state.question), content), x + 10, cursor, 0xff756a59, false);
            cursor += 13;
        } else cursor += 3;
        state.scroll = Math.max(0, Math.min(state.scroll, Math.max(0, answerLines.size() - shown)));
        for (int i = 0; i < shown; i++) draw.drawText(client.textRenderer, answerLines.get(state.scroll + i), x + 10, cursor + i * 10, 0xff302a22, false);
        cursor += shown * 10 + 6;
        for (List<OrderedText> lines : state.choiceLines) {
            for (OrderedText line : lines) { draw.drawText(client.textRenderer, line, x + 10, cursor, 0xff795226, false); cursor += 10; }
            cursor += 3;
        }
        if (!choices.isEmpty()) { draw.drawText(client.textRenderer, client.textRenderer.trimToWidth(tr("choices"), content), x + 10, cursor, 0xff756a59, false); cursor += 13; }
        if (state.showHistory && historyLines > 0) {
            List<String> history = state.historyPreview;
            int count = Math.min(historyLines, history.size());
            for (int i = 0; i < count; i++) {
                String line = history.get(history.size() - count + i);
                draw.drawText(client.textRenderer, client.textRenderer.trimToWidth(line, content), x + 10, cursor, 0xff756a59, false);
                cursor += 10;
            }
            cursor += (historyLines - count) * 10;
        }
        ensureComposer();
        composer.setX(x + 10); composer.setY(cursor + 2); composer.setWidth(content); composer.setFocused(true);
        draw.fill(x + 7, cursor - 2, x + width - 7, cursor + 19, 0xfffffdf7);
        composer.render(draw, -1, -1, delta);
        cursor += 22;
        String footer = noticeUntil > now() ? notice : answerLines.size() > shown ? tr("scroll") : tr("controls");
        draw.drawText(client.textRenderer, client.textRenderer.trimToWidth(footer, content), x + 10, cursor, 0xff756a59, false);
        if (!state.busy && state.reply != null && state.reply.route() != GlauxDialogueService.Route.BOUNDARY) draw.drawText(client.textRenderer, client.textRenderer.trimToWidth(tr("website_shortcut"), content), x + 10, cursor + 12, 0xff795226, false);
    }
}
