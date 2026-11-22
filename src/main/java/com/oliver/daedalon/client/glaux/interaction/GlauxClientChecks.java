package com.oliver.daedalon.client.glaux.interaction;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import com.oliver.daedalon.Daedalon;
import com.oliver.daedalon.client.glaux.GlauxVisuals;
import com.oliver.daedalon.client.glaux.model.GlauxHeadMeshes;
import com.oliver.daedalon.client.glaux.model.GlauxMotion;
import com.oliver.daedalon.client.glaux.model.GlauxRenderChecks;
import com.oliver.daedalon.client.glaux.model.GlauxRig;
import com.oliver.daedalon.glaux.GlauxBlock;
import com.oliver.daedalon.glaux.GlauxRegistry;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.client.Keyboard;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.level.LevelInfo;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL20;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Explicitly gated native fixture, never registered in ordinary play. It only
 * writes a marked test world in build/ or run-glaux-checks beneath this project.
 * Synthetic callbacks exercise the actual mixins without OS window activation.
 */
public final class GlauxClientChecks {
    private static final String WORLD = "glaux-client-checks";
    private static final String QUESTION = "How do I rotate this?";
    private static final String LONG_DRAFT = "Glaux, this text should be clear. "
            + "Please help me choose bronze lanterns for a courtyard. ".repeat(6)
            + "Keep this exact draft while I move the caret.";
    private static final BlockPos OWL = new BlockPos(0, 71, 0);
    private static final BlockPos[] OWLS = {OWL, new BlockPos(-2, 71, 1), new BlockPos(2, 71, 1), new BlockPos(0, 71, 3)};
    private final List<Map<String,Object>> checks = new ArrayList<>(), timings = new ArrayList<>();
    private final ThreadMXBean cpu = ManagementFactory.getThreadMXBean();
    private final Map<String,Object> report = new LinkedHashMap<>();
    private final boolean shaderChecks = Boolean.getBoolean("daedalon.debug.glauxShaderChecks");
    private Path output;
    private int phase, ticks;
    private long phaseTime, started = System.nanoTime(), renderFrames, phaseFrame;
    private long renderCpuStart, previousFrameCpu = -1, previousFrameWall = -1;
    private CompletableFuture<Boolean> scene;
    private boolean finished, safeProfile, ownedFixture, oldPause, oldHud;
    private Perspective oldPerspective;
    private GlauxHeadMeshes cachedHeads;
    private GlauxRig cachedRig;
    private Samples samples;
    private double previousShake;
    private long profanitySentAt;

    private GlauxClientChecks() { }
    public static void register() {
        if (!Boolean.getBoolean("daedalon.debug.glauxChecks")) return;
        GlauxClientChecks fixture = new GlauxClientChecks();
        try {
            if (fixture.cpu.isCurrentThreadCpuTimeSupported() && !fixture.cpu.isThreadCpuTimeEnabled()) fixture.cpu.setThreadCpuTimeEnabled(true);
        } catch (RuntimeException ignored) { }
        WorldRenderEvents.START.register(context -> fixture.renderCpuStart = fixture.cpuTime());
        WorldRenderEvents.END.register(context -> fixture.frame());
        ClientTickEvents.END_CLIENT_TICK.register(fixture::tick);
    }

    private long cpuTime() {
        try { return cpu.isCurrentThreadCpuTimeSupported() && cpu.isThreadCpuTimeEnabled() ? cpu.getCurrentThreadCpuTime() : -1; }
        catch (RuntimeException ignored) { return -1; }
    }

    private void frame() {
        long wall = System.nanoTime(), processor = cpuTime();
        renderFrames++;
        if (samples != null && samples.count < 120 && renderFrames - phaseFrame >= 120
                && millis() - phaseTime >= 2000 && previousFrameWall > 0) {
            int i = samples.count++;
            samples.wall[i] = (wall - previousFrameWall) / 1_000_000.0;
            samples.worldCpu[i] = processor < 0 || renderCpuStart < 0 ? Double.NaN : (processor - renderCpuStart) / 1_000_000.0;
            samples.frameCpu[i] = processor < 0 || previousFrameCpu < 0 ? Double.NaN : (processor - previousFrameCpu) / 1_000_000.0;
        }
        previousFrameWall = wall;
        previousFrameCpu = processor;
    }

    private void tick(MinecraftClient client) {
        if (finished) return;
        try {
            if ((System.nanoTime() - started) / 1_000_000 > (shaderChecks ? 480_000 : 240_000))
                throw new AssertionError("Native fixture exceeded its " + (shaderChecks ? "eight" : "four") + " minute budget at phase " + phase);
            ticks++;
            switch (phase) {
                case 0 -> { if (client.getOverlay() == null) boot(client); }
                case 1 -> {
                    if (client.world != null && client.player != null && client.getServer() != null) {
                        scene = prepare(client, 1, true); next(2);
                    }
                }
                case 2 -> {
                    if (!scene.isDone() || client.world == null || client.player == null || !GlauxRegistry.isGlaux(client.world.getBlockState(OWL))) return;
                    scene.join();
                    if (client.player.squaredDistanceTo(.5, 71, -3.5) > .2) return;
                    client.setScreen(null);
                    client.onWindowFocusChanged(true); // logical fixture focus only; does not activate an OS window
                    if (ownedFixture) client.inGameHud.getChatHud().clear(false);
                    aim(client, 0);
                    next(3);
                }
                case 3 -> {
                    aim(client, 0);
                    GlauxInteraction.DebugState state = GlauxInteraction.debugState();
                    if (millis() - phaseTime < 500 || state.hint() == null || !state.projectionReady()) return;
                    check("Looking at the real owl offers a hint without keyboard capture", !state.composing() && state.hint().equals(OWL.asLong()));
                    if (shaderChecks) {
                        report.put("shaderAtStart", shaderProof());
                        check("Real Glaux item reaches the visible hotbar renderer", client.player.getInventory().getStack(8).isOf(GlauxRegistry.ITEM));
                        screenshot(client, "glaux-native-icon-hotbar.png");
                    }
                    report.put("hintProjection", GlauxInteraction.debugProjection());
                    screenshot(client, "glaux-native-hint.png");
                    check("World anchor projects near the actual crosshair", Math.abs(state.anchorX() - client.getWindow().getScaledWidth() / 2) < client.getWindow().getScaledWidth() / 5
                            && Math.abs(state.anchorY() - client.getWindow().getScaledHeight() / 2) < client.getWindow().getScaledHeight() / 5);
                    client.player.getInventory().selectedSlot = 4;
                    key(client, GLFW.GLFW_KEY_1, GLFW.GLFW_PRESS);
                    key(client, GLFW.GLFW_KEY_1, GLFW.GLFW_RELEASE);
                    next(4);
                }
                case 4 -> {
                    if (ticks < 3) return;
                    check("Number key remains a normal hotbar control outside conversation", client.player.getInventory().selectedSlot == 0);
                    nativeInput(client);
                    next(5);
                }
                case 5 -> {
                    GlauxInteraction.DebugState state = GlauxInteraction.debugState();
                    if (state.busy() || state.choices().size() < 2) return;
                    check("Submitted question reaches local clarification", state.question().equals(QUESTION) && state.choices().size() == 3);
                    check("Held Enter produces one enquiry", state.userTurns() == 1);
                    next(6);
                }
                case 6 -> {
                    if (renderFrames - phaseFrame < 5) return;
                    screenshot(client, "glaux-native-choices.png");
                    type(client, "1"); key(client, GLFW.GLFW_KEY_ENTER, GLFW.GLFW_PRESS); key(client, GLFW.GLFW_KEY_ENTER, GLFW.GLFW_RELEASE);
                    next(7);
                }
                case 7 -> {
                    GlauxInteraction.DebugState state = GlauxInteraction.debugState();
                    if (state.busy() || state.userTurns() != 2) return;
                    check("Typed number resolves verified held-lantern controls", state.reply().contains("Facing") && state.reply().toLowerCase(java.util.Locale.ROOT).contains("lantern"));
                    check("Clarification retains original enquiry for the website", state.enquiry().equals(QUESTION));
                    next(8);
                }
                case 8 -> {
                    if (renderFrames - phaseFrame < 5) return;
                    screenshot(client, "glaux-native-answer.png");
                    testMouseLook(client);
                    type(client, LONG_DRAFT);
                    next(11);
                }
                case 9 -> {
                    aim(client, 90);
                    if (millis() - phaseTime < 400) return;
                    GlauxInteraction.DebugState state = GlauxInteraction.debugState();
                    check("Actual look-away yields keyboard ownership", !state.composing());
                    check("Look-away preserves unsent draft", state.draft().equals("keep this draft"));
                    aim(client, 0); next(10);
                }
                case 10 -> {
                    aim(client, 0);
                    if (millis() - phaseTime < 400 || GlauxInteraction.debugState().hint() == null) return;
                    mouse(client, GLFW.GLFW_MOUSE_BUTTON_RIGHT, GLFW.GLFW_PRESS);
                    mouse(client, GLFW.GLFW_MOUSE_BUTTON_RIGHT, GLFW.GLFW_RELEASE);
                    check("Returning and interacting restores the draft", GlauxInteraction.debugState().composing() && GlauxInteraction.debugState().draft().equals("keep this draft"));
                    key(client, GLFW.GLFW_KEY_ESCAPE, GLFW.GLFW_PRESS);
                    check("Escape closes Glaux first without a pause Screen", !GlauxInteraction.isComposing() && client.currentScreen == null);
                    key(client, GLFW.GLFW_KEY_ESCAPE, GLFW.GLFW_RELEASE);
                    client.options.hudHidden = true;
                    scene = prepare(client, 0, false); next(20);
                }
                case 11 -> {
                    if (renderFrames - phaseFrame < 5) return;
                    GlauxComposerWidget composer = composer();
                    GlauxComposerViewport.Window line = composer.viewport();
                    check("Long draft keeps its exact text while its caret scrolls into view", composer.getText().equals(LONG_DRAFT)
                            && line.end() == LONG_DRAFT.length() && line.caret() >= 0 && line.caret() < composer.getWidth());
                    report.put("composerEnd", composerSnapshot(composer));
                    screenshot(client, "glaux-native-composer-end.png");
                    key(client, GLFW.GLFW_KEY_HOME, GLFW.GLFW_PRESS); key(client, GLFW.GLFW_KEY_HOME, GLFW.GLFW_RELEASE);
                    next(12);
                }
                case 12 -> {
                    if (renderFrames - phaseFrame < 5) return;
                    GlauxComposerWidget composer = composer();
                    check("Native Home returns the long draft to its first glyph without editing it", composer.getCursor() == 0
                            && composer.viewport().start() == 0 && composer.getText().equals(LONG_DRAFT));
                    report.put("composerHome", composerSnapshot(composer));
                    screenshot(client, "glaux-native-composer-home.png");
                    // GLFW polls physical modifiers. This fixture cannot synthesize that
                    // hardware state, so use the same public selection endpoint as the
                    // vanilla Shift path and identify that boundary in the report.
                    composer.setSelectionEnd(32);
                    next(13);
                }
                case 13 -> {
                    if (renderFrames - phaseFrame < 5) return;
                    GlauxComposerWidget composer = composer();
                    GlauxComposerViewport.Window line = composer.viewport();
                    check("Vanilla selection endpoint highlights the visible draft without changing it", composer.getSelectedText().equals(LONG_DRAFT.substring(0, 32))
                            && composer.getText().equals(LONG_DRAFT) && line.selectionLeft() == 0
                            && line.selectionRight() > 0 && line.selectionRight() < composer.getWidth());
                    report.put("composerSelection", composerSnapshot(composer));
                    report.put("selectionInputMethod", "Vanilla TextFieldWidget.setSelectionEnd, equivalent selection state to Shift; physical modifier delivery is not asserted");
                    screenshot(client, "glaux-native-composer-selection.png");
                    key(client, GLFW.GLFW_KEY_END, GLFW.GLFW_PRESS); key(client, GLFW.GLFW_KEY_END, GLFW.GLFW_RELEASE);
                    check("Native End restores the caret to the complete draft's end", composer.getCursor() == LONG_DRAFT.length());
                    composer.setText("");
                    previousShake = GlauxVisuals.pose(OWL).shake;
                    profanitySentAt = client.world.getTime();
                    type(client, "fuck");
                    key(client, GLFW.GLFW_KEY_ENTER, GLFW.GLFW_PRESS); key(client, GLFW.GLFW_KEY_ENTER, GLFW.GLFW_RELEASE);
                    next(14);
                }
                case 14 -> {
                    GlauxInteraction.DebugState state = GlauxInteraction.debugState();
                    if (state.busy() || !state.question().equals("fuck")) return;
                    GlauxVisuals.Pose pose = GlauxVisuals.pose(OWL);
                    check("Submitted bare profanity reaches the real Glaux shake gesture", pose.shake != previousShake && pose.shake >= profanitySentAt
                            && pose.lastShakeNanos > 0 && pose.attentive && !GlauxVisuals.reducedMotion());
                    report.put("profanityGesture", row("question", state.question(), "reply", state.reply(), "previousShakeTick", previousShake,
                            "submittedAtTick", profanitySentAt, "shakeTick", pose.shake, "cooldownClock", pose.lastShakeNanos));
                    next(15);
                }
                case 15 -> {
                    if (ticks < 2) return;
                    GlauxVisuals.Pose pose = GlauxVisuals.pose(OWL);
                    float roll = pose.roll(client.world.getTime() + .5);
                    check("Profanity shake produces a visible finite animation envelope", Float.isFinite(roll) && Math.abs(roll) > .00001f);
                    report.put("profanityRollRadians", roll);
                    screenshot(client, "glaux-native-profanity-shake.png");
                    composer().setText("");
                    GlauxInteraction.debugRelease();
                    scene = teleport(client, 2, -1.5);
                    next(16);
                }
                case 16 -> {
                    if (!scene.isDone() || client.player.squaredDistanceTo(2, 71, -1.5) > .2) return;
                    scene.join();
                    aimAtOwl(client);
                    if (millis() - phaseTime < 3000) return;
                    GlauxVisuals.Pose pose = GlauxVisuals.pose(OWL);
                    double distance = awarenessDistance(client);
                    check("Real nearby player enters the three-block attention radius and turns the head", GlauxMotion.withinAwareness(distance)
                            && pose.visible && Math.abs(pose.motion.yaw()) > .2);
                    report.put("attentionInside", row("squaredDistance", distance, "yawRadians", pose.motion.yaw(), "visible", pose.visible));
                    screenshot(client, "glaux-native-attention-near.png");
                    scene = teleport(client, 3.6, -3.5);
                    next(17);
                }
                case 17 -> {
                    if (!scene.isDone() || client.player.squaredDistanceTo(3.6, 71, -3.5) > .2) return;
                    scene.join();
                    aimAtOwl(client);
                    if (millis() - phaseTime < 4000) return;
                    GlauxVisuals.Pose pose = GlauxVisuals.pose(OWL);
                    double distance = awarenessDistance(client);
                    check("Real distant player leaves attention and the head settles to rest", !GlauxMotion.withinAwareness(distance)
                            && Math.abs(pose.motion.yaw()) < .04);
                    report.put("attentionOutside", row("squaredDistance", distance, "yawRadians", pose.motion.yaw()));
                    screenshot(client, "glaux-native-attention-far.png");
                    if (shaderChecks) client.options.hudHidden = true;
                    scene = teleport(client, shaderChecks ? .3218 : .5, shaderChecks ? -1.8009 : -3.5);
                    next(shaderChecks ? 30 : 18);
                }
                case 18 -> {
                    if (!scene.isDone() || client.player.squaredDistanceTo(.5, 71, -3.5) > .2) return;
                    scene.join();
                    aim(client, 0);
                    if (millis() - phaseTime < 500 || GlauxInteraction.debugState().hint() == null) return;
                    mouse(client, GLFW.GLFW_MOUSE_BUTTON_RIGHT, GLFW.GLFW_PRESS); mouse(client, GLFW.GLFW_MOUSE_BUTTON_RIGHT, GLFW.GLFW_RELEASE);
                    check("Returning to the original scene restores deliberate conversation focus", GlauxInteraction.isComposing());
                    type(client, "keep this draft");
                    aim(client, 90);
                    next(9);
                }
                case 20, 21, 22 -> benchmark(client);
                case 30 -> {
                    if (!scene.isDone() || client.player.squaredDistanceTo(.3218, 71, -1.8009) > .2) return;
                    scene.join(); aimAtOwl(client);
                    if (millis() - phaseTime < 3000) return;
                    check("Shader front view keeps the actual attentive head at rest", Math.abs(GlauxVisuals.pose(OWL).motion.yaw()) < .08);
                    report.put("shaderTurn0", poseSnapshot(client));
                    screenshot(client, "glaux-native-shader-front-0.png");
                    scene = teleport(client, 2.5218, .3991); next(31);
                }
                case 31 -> {
                    if (!scene.isDone() || client.player.squaredDistanceTo(2.5218, 71, .3991) > .2) return;
                    scene.join(); aimAtOwl(client);
                    if (millis() - phaseTime < 3000) return;
                    check("Shader side view follows the real nearby player through ninety degrees", Math.abs(GlauxMotion.shortestDelta(
                            GlauxVisuals.pose(OWL).motion.yaw(), -Math.PI / 2)) < .15);
                    report.put("shaderTurn90", poseSnapshot(client));
                    screenshot(client, "glaux-native-shader-side-90.png");
                    scene = teleport(client, .3218, 2.5991); next(32);
                }
                case 32 -> {
                    if (!scene.isDone() || client.player.squaredDistanceTo(.3218, 71, 2.5991) > .2) return;
                    scene.join(); aimAtOwl(client);
                    if (millis() - phaseTime < 5000) return;
                    check("Shader rear view follows the real nearby player through one hundred eighty degrees", Math.abs(
                            Math.abs(GlauxVisuals.pose(OWL).motion.yaw()) - Math.PI) < .15);
                    report.put("shaderTurn180", poseSnapshot(client));
                    screenshot(client, "glaux-native-shader-rear-180.png");
                    client.options.hudHidden = false;
                    scene = teleport(client, .5, -3.5); next(18);
                }
                default -> throw new IllegalStateException("Unexpected phase " + phase);
            }
        } catch (Throwable failure) { finish(client, failure); }
    }

    private void boot(MinecraftClient client) throws Exception {
        Path run = client.runDirectory.toPath().toRealPath(), project = run;
        for (int i = 0; i < 7 && project != null; i++, project = project.getParent())
            if (Files.isRegularFile(project.resolve("build.gradle")) && Files.isDirectory(project.resolve("src/main/resources/assets/daedalon"))) break;
        if (project == null || !Files.isRegularFile(project.resolve("build.gradle"))) throw new IllegalStateException("Native checks require the Daedalon development project");
        output = project.resolve("build/reports/glaux-client"); Files.createDirectories(output);
        safeProfile = run.startsWith(project.resolve("build").toRealPath()) || run.getFileName().toString().equals("run-glaux-checks");
        if (!safeProfile || !client.getLevelStorage().getSavesDirectory().toAbsolutePath().normalize().startsWith(run))
            throw new IllegalStateException("Refusing a live profile. Use -PdaedalonClientDirectory=build/glaux-client or run-glaux-checks");
        if (client.world != null) throw new IllegalStateException("Refusing to change a world already open before the native fixture");
        Path marker = run.resolve(".glaux-client-fixture.json");
        boolean existing = client.getLevelStorage().levelExists(WORLD);
        if (existing && (!Files.isRegularFile(marker) || Files.size(marker) > 2048
                || !JsonParser.parseString(Files.readString(marker)).getAsJsonObject().get("world").getAsString().equals(WORLD)))
            throw new IllegalStateException("Refusing an unmarked existing fixture world");
        Files.writeString(marker, "{\"schema\":1,\"world\":\"" + WORLD + "\"}\n", StandardCharsets.UTF_8);
        ownedFixture = true;
        oldPause = client.options.pauseOnLostFocus; oldHud = client.options.hudHidden; oldPerspective = client.options.getPerspective();
        client.options.pauseOnLostFocus = false; client.options.hudHidden = false; client.options.setPerspective(Perspective.FIRST_PERSON);
        report.put("modelChecks", GlauxRenderChecks.inspect(client));
        cachedHeads = GlauxHeadMeshes.current; cachedRig = GlauxRig.current;
        report.put("profile", run.toString());
        report.put("syntheticWindowFocus", true);
        report.put("shaderChecksRequested", shaderChecks);
        report.put("inputMethod", "Actual mixed-in Keyboard/Mouse callbacks with synthetic fixture events; no OS window activation or hardware key injection");
        check("Native fixture uses default gaze-release controls", !GlauxInteraction.debugState().pinned());
        Daedalon.LOGGER.info("DAEDALON_GLAUX_CLIENT_CHECKS_STARTED: {}", output);
        next(1);
        if (existing) client.createIntegratedServerLoader().start(client.currentScreen, WORLD);
        else {
            GameRules rules = new GameRules(); rules.get(GameRules.DO_MOB_SPAWNING).set(false, null); rules.get(GameRules.DO_DAYLIGHT_CYCLE).set(false, null); rules.get(GameRules.DO_WEATHER_CYCLE).set(false, null);
            LevelInfo level = new LevelInfo("Glaux native client fixture", GameMode.CREATIVE, false, Difficulty.PEACEFUL, true, rules, DataConfiguration.SAFE_MODE);
            client.createIntegratedServerLoader().createAndStart(WORLD, level, new GeneratorOptions(17L, false, false),
                    registries -> registries.get(RegistryKeys.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createDimensionsRegistryHolder());
        }
    }

    private CompletableFuture<Boolean> prepare(MinecraftClient client, int count, boolean teleport) {
        var server = client.getServer();
        var uuid = client.player.getUuid();
        return server.submit(() -> {
            ServerWorld world = server.getOverworld();
            world.setTimeOfDay(6000); world.setWeather(100000, 0, false, false);
            if (teleport) for (int x = -6; x <= 6; x++) for (int z = -6; z <= 6; z++) world.setBlockState(new BlockPos(x, 70, z), Blocks.SMOOTH_QUARTZ.getDefaultState(), Block.NOTIFY_LISTENERS);
            for (int i = 0; i < OWLS.length; i++) {
                world.setBlockState(OWLS[i].up(), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
                world.setBlockState(OWLS[i], Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
                if (i < count) {
                    Direction facing = i == 0 ? Direction.NORTH : i == 1 ? Direction.SOUTH : i == 2 ? Direction.WEST : Direction.EAST;
                    var state = GlauxRegistry.BLOCK.getDefaultState().with(GlauxBlock.FACING, facing);
                    world.setBlockState(OWLS[i], state.with(GlauxBlock.HALF, DoubleBlockHalf.LOWER), Block.NOTIFY_LISTENERS);
                    world.setBlockState(OWLS[i].up(), state.with(GlauxBlock.HALF, DoubleBlockHalf.UPPER), Block.NOTIFY_LISTENERS);
                }
            }
            if (teleport) {
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
                if (player == null) throw new IllegalStateException("Fixture player not present on integrated server");
                player.changeGameMode(GameMode.CREATIVE);
                player.teleport(world, .5, 71, -3.5, 0, 0);
                player.setVelocity(Vec3d.ZERO);
                Item lantern = Registries.ITEM.getIds().stream().filter(id -> id.getNamespace().equals("daedalon") && id.getPath().startsWith("bronze_") && id.getPath().endsWith("_lantern"))
                        .sorted().findFirst().map(Registries.ITEM::get).orElseThrow(() -> new IllegalStateException("No reviewed bronze lantern for fixture"));
                player.getInventory().setStack(0, new ItemStack(lantern));
                if (shaderChecks) player.getInventory().setStack(8, new ItemStack(GlauxRegistry.ITEM));
                player.getInventory().selectedSlot = 4;
                player.playerScreenHandler.sendContentUpdates();
            }
            return true;
        });
    }

    private void nativeInput(MinecraftClient client) throws Exception {
        aim(client, 0);
        mouse(client, GLFW.GLFW_MOUSE_BUTTON_RIGHT, GLFW.GLFW_PRESS);
        check("Deliberate mouse use opens floating text focus", GlauxInteraction.isComposing());
        check("Conversation leaves Screen null and mouse locked", client.currentScreen == null && client.mouse.isCursorLocked());
        mouse(client, GLFW.GLFW_MOUSE_BUTTON_RIGHT, GLFW.GLFW_RELEASE);
        key(client, GLFW.GLFW_KEY_W, GLFW.GLFW_PRESS); type(client, "w");
        check("W becomes text without movement binding", GlauxInteraction.debugState().draft().equals("w") && !client.options.forwardKey.isPressed());
        GlauxInteraction.debugRelease();
        check("Held typing key is quarantined on leaving", GlauxInteraction.debugQuarantined(GLFW.GLFW_KEY_W));
        key(client, GLFW.GLFW_KEY_W, GLFW.GLFW_REPEAT);
        check("Held repeat cannot move after leaving conversation", !client.options.forwardKey.isPressed());
        key(client, GLFW.GLFW_KEY_W, GLFW.GLFW_RELEASE);
        key(client, GLFW.GLFW_KEY_W, GLFW.GLFW_PRESS);
        check("Fresh press after release returns normal movement binding", client.options.forwardKey.isPressed());
        key(client, GLFW.GLFW_KEY_W, GLFW.GLFW_RELEASE);
        mouse(client, GLFW.GLFW_MOUSE_BUTTON_RIGHT, GLFW.GLFW_PRESS); mouse(client, GLFW.GLFW_MOUSE_BUTTON_RIGHT, GLFW.GLFW_RELEASE);
        key(client, GLFW.GLFW_KEY_BACKSPACE, GLFW.GLFW_PRESS); key(client, GLFW.GLFW_KEY_BACKSPACE, GLFW.GLFW_RELEASE);
        type(client, QUESTION);
        key(client, GLFW.GLFW_KEY_ENTER, GLFW.GLFW_PRESS);
        key(client, GLFW.GLFW_KEY_ENTER, GLFW.GLFW_REPEAT); key(client, GLFW.GLFW_KEY_ENTER, GLFW.GLFW_REPEAT);
        key(client, GLFW.GLFW_KEY_ENTER, GLFW.GLFW_RELEASE);
    }

    private void testMouseLook(MinecraftClient client) throws Exception {
        float before = client.player.getYaw();
        Method cursor = Mouse.class.getDeclaredMethod("onCursorPos", long.class, double.class, double.class); cursor.setAccessible(true);
        double x = client.mouse.getX(), y = client.mouse.getY();
        cursor.invoke(client.mouse, client.getWindow().getHandle(), x + 1, y);
        cursor.invoke(client.mouse, client.getWindow().getHandle(), x + 18, y);
        client.mouse.updateMouse();
        check("Actual mouse-look path changes orientation while composing", Math.abs(client.player.getYaw() - before) > .001f && GlauxInteraction.isComposing());
        aim(client, 0);
    }

    private GlauxComposerWidget composer() throws Exception {
        if (!ownedFixture) throw new IllegalStateException("Composer inspection requires an owned native fixture");
        Field instance = GlauxInteraction.class.getDeclaredField("instance"); instance.setAccessible(true);
        Field field = GlauxInteraction.class.getDeclaredField("composer"); field.setAccessible(true);
        return (GlauxComposerWidget)field.get(instance.get(null));
    }

    private static Map<String,Object> composerSnapshot(GlauxComposerWidget composer) {
        GlauxComposerViewport.Window line = composer.viewport();
        return row("draft", composer.getText(), "visibleText", line.text(), "visibleStart", line.start(), "visibleEnd", line.end(),
                "cursorIndex", composer.getCursor(), "caretPixels", line.caret(), "selectionLeftPixels", line.selectionLeft(),
                "selectionRightPixels", line.selectionRight(), "fieldWidthPixels", composer.getWidth(),
                "textShadow", false, "foreground", "#302a22", "background", "#fffdf7", "contrastRatio", 13.95);
    }

    private static Map<String,Object> poseSnapshot(MinecraftClient client) {
        GlauxVisuals.Pose pose = GlauxVisuals.pose(OWL);
        double maximumHinge = 0;
        if (GlauxRig.current != null) for (int i = 0; i < GlauxRig.current.hinges.length; i++)
            maximumHinge = Math.max(maximumHinge, Math.abs(GlauxRig.current.featherAngle(i, (float)pose.motion.yaw())));
        return row("player", List.of(client.player.getX(), client.player.getY(), client.player.getZ()),
                "squaredAwarenessDistance", awarenessDistance(client), "headYawDegrees", Math.toDegrees(pose.motion.yaw()),
                "maximumFeatherHingeDegrees", Math.toDegrees(maximumHinge));
    }

    private Map<String,Object> shaderProof() throws Exception {
        Class<?> iris = Class.forName("net.irisshaders.iris.Iris");
        boolean pack = ((Optional<?>)iris.getMethod("getCurrentPack").invoke(null)).isPresent();
        String name = (String)iris.getMethod("getCurrentPackName").invoke(null);
        boolean fallback = (boolean)iris.getMethod("isFallback").invoke(null);
        Object manager = iris.getMethod("getPipelineManager").invoke(null);
        Object pipeline = manager.getClass().getMethod("getPipelineNullable").invoke(manager);
        check("Iris has a live shader rendering pipeline rather than enabled flags alone", pack && !fallback && pipeline != null
                && pipeline.getClass().getName().equals("net.irisshaders.iris.pipeline.IrisRenderingPipeline"));
        Field loaded = pipeline.getClass().getDeclaredField("loadedShaders"); loaded.setAccessible(true);
        Set<?> programs = (Set<?>)loaded.get(pipeline);
        List<Integer> linked = new ArrayList<>();
        for (Object object : programs) if (object instanceof ShaderProgram program) {
            int id = program.getGlRef();
            if (id > 0 && GL20.glIsProgram(id) && GL20.glGetProgrami(id, GL20.GL_LINK_STATUS) != 0) linked.add(id);
        }
        check("Actual shader-pack GL programs are live and linked", !linked.isEmpty());
        return row("pack", name, "pipelineClass", pipeline.getClass().getName(), "fallback", fallback,
                "loadedShaderPrograms", programs.size(), "linkedLiveGlPrograms", linked.size(),
                "sampleProgramIds", linked.subList(0, Math.min(8, linked.size())));
    }

    private CompletableFuture<Boolean> teleport(MinecraftClient client, double x, double z) {
        if (!ownedFixture) throw new IllegalStateException("Attention scene requires an owned native fixture");
        var server = client.getServer();
        var uuid = client.player.getUuid();
        return server.submit(() -> {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
            if (player == null) throw new IllegalStateException("Fixture player not present on integrated server");
            player.teleport(server.getOverworld(), x, 71, z, 0, 0);
            player.setVelocity(Vec3d.ZERO);
            return true;
        });
    }

    private static double awarenessDistance(MinecraftClient client) {
        GlauxRig rig = GlauxRig.current;
        double facing = Math.toRadians(GlauxBlock.rotationDegrees(client.world.getBlockState(OWL).get(GlauxBlock.FACING)));
        double pivotX = rig == null ? .17818823 : rig.pivot.x, pivotZ = rig == null ? .10094695 : rig.pivot.z;
        Vec3d eye = client.player.getEyePos();
        double dx = eye.x - OWL.getX() - .5 - (Math.cos(facing) * pivotX + Math.sin(facing) * pivotZ);
        double dz = eye.z - OWL.getZ() - .5 - (Math.cos(facing) * pivotZ - Math.sin(facing) * pivotX);
        double dy = eye.y - OWL.getY() - 1.7;
        return dx * dx + dy * dy + dz * dz;
    }

    private static void aimAtOwl(MinecraftClient client) {
        double dx = OWL.getX() + .5 - client.player.getX(), dz = OWL.getZ() + .5 - client.player.getZ();
        float yaw = (float)Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float)-Math.toDegrees(Math.atan2(OWL.getY() + 1.65 - client.player.getEyeY(), Math.sqrt(dx * dx + dz * dz)));
        client.player.setYaw(yaw); client.player.prevYaw = yaw;
        client.player.setPitch(pitch); client.player.prevPitch = pitch;
        client.gameRenderer.getCamera().update(client.world, client.player, false, false, 1);
        client.gameRenderer.updateTargetedEntity(1);
    }

    private void benchmark(MinecraftClient client) throws Exception {
        if (!scene.isDone()) return;
        scene.join();
        aim(client, 0);
        if (samples == null) {
            int count = phase == 20 ? 0 : phase == 21 ? 1 : 4;
            samples = new Samples(count); phaseTime = millis(); phaseFrame = renderFrames;
            Daedalon.LOGGER.info("DAEDALON_GLAUX_PERF_SCENE: {} nearby owls, 120 warm frames then 120 samples", count);
        }
        if (samples.count < 120) return;
        timings.add(samples.report());
        if (phase == 22) {
            screenshot(client, "glaux-native-four.png");
            check("Native head and pose geometry caches are reused across frames/scenes", cachedHeads == GlauxHeadMeshes.current && cachedRig == GlauxRig.current);
            if (shaderChecks) report.put("shaderAtEnd", shaderProof());
            finish(client, null);
        } else {
            int count = phase == 20 ? 1 : 4;
            scene = prepare(client, count, false); samples = null; next(phase + 1);
        }
    }

    private static void aim(MinecraftClient client, float yaw) {
        client.player.setYaw(yaw); client.player.prevYaw = yaw;
        float pitch = (float)-Math.toDegrees(Math.atan2(OWL.getY() + 1.65 - client.player.getEyeY(), 4));
        client.player.setPitch(pitch); client.player.prevPitch = pitch;
        client.gameRenderer.getCamera().update(client.world, client.player, false, false, 1);
        client.gameRenderer.updateTargetedEntity(1);
    }

    private static void key(MinecraftClient client, int key, int action) {
        client.keyboard.onKey(client.getWindow().getHandle(), key, GLFW.glfwGetKeyScancode(key), action, 0);
    }
    private static void type(MinecraftClient client, String text) throws Exception {
        Method method = Keyboard.class.getDeclaredMethod("onChar", long.class, int.class, int.class); method.setAccessible(true);
        for (int point : text.codePoints().toArray()) method.invoke(client.keyboard, client.getWindow().getHandle(), point, 0);
    }
    private static void mouse(MinecraftClient client, int button, int action) throws Exception {
        Method method = Mouse.class.getDeclaredMethod("onMouseButton", long.class, int.class, int.class, int.class); method.setAccessible(true);
        method.invoke(client.mouse, client.getWindow().getHandle(), button, action, 0);
    }
    private void screenshot(MinecraftClient client, String name) throws Exception {
        try (NativeImage image = ScreenshotRecorder.takeScreenshot(client.getFramebuffer())) { image.writeTo(output.resolve(name)); }
    }
    private void next(int next) { phase = next; ticks = 0; phaseTime = millis(); phaseFrame = renderFrames; }
    private static long millis() { return System.nanoTime() / 1_000_000; }
    private void check(String name, boolean passed) {
        checks.add(row("name", name, "passed", passed));
        if (!passed) throw new AssertionError(name);
    }

    private void finish(MinecraftClient client, Throwable failure) {
        if (finished) return;
        finished = true;
        if (failure != null && ownedFixture && output != null) {
            report.put("failureProjection", GlauxInteraction.debugProjection());
            try { screenshot(client, "glaux-native-failure.png"); }
            catch (Exception error) { report.put("failureScreenshot", error.toString()); }
        }
        GlauxInteraction.debugRelease();
        report.put("passed", failure == null);
        report.put("checks", checks);
        report.put("nativeWorldRenderFrames", renderFrames);
        report.put("benchmarks", timings);
        report.put("limitations", List.of("Synthetic callbacks verify native mixin routing, not OS hardware key release across alt-tab.",
                "CPU samples are this controlled fixture on this machine, not an FPS guarantee; GPU time is not measured.",
                "ThreadMXBean CPU ticks can be coarse (15.625 ms observed on this Windows runtime). A zero median is below timer resolution, not zero CPU cost. Totals and means still share this quantization and cannot establish precise per-owl cost.",
                "Selection rendering uses the vanilla selection endpoint API; synthetic callbacks cannot prove GLFW physical Shift-key polling.",
                "No browser, website request or online AI was invoked."));
        if (failure != null) report.put("failure", failure.toString());
        try {
            if (output != null) { Files.createDirectories(output); Files.writeString(output.resolve("glaux-native-report.json"), new GsonBuilder().setPrettyPrinting().serializeNulls().create().toJson(report), StandardCharsets.UTF_8); }
        } catch (Exception error) { Daedalon.LOGGER.error("Unable to write Glaux native result", error); }
        if (failure == null) Daedalon.LOGGER.info("DAEDALON_GLAUX_CLIENT_CHECKS_OK: {} assertions; report={}", checks.size(), output);
        else Daedalon.LOGGER.error("DAEDALON_GLAUX_CLIENT_CHECKS_FAILED: phase={} report={}", phase, output, failure);
        if (ownedFixture) {
            client.options.pauseOnLostFocus = oldPause; client.options.hudHidden = oldHud; client.options.setPerspective(oldPerspective);
            if (!Boolean.getBoolean("daedalon.debug.glauxChecks.keepOpen")) client.scheduleStop();
        }
    }

    private static Map<String,Object> row(Object... pairs) { Map<String,Object> result = new LinkedHashMap<>(); for (int i = 0; i < pairs.length; i += 2) result.put(pairs[i].toString(), pairs[i + 1]); return result; }
    private static Object quantile(double[] data, double p) { double[] finite = Arrays.stream(data).filter(Double::isFinite).sorted().toArray(); return finite.length == 0 ? null : finite[Math.min(finite.length - 1, (int)Math.floor(finite.length * p))]; }
    private static Map<String,Object> sampleSummary(double[] data) {
        double[] finite = Arrays.stream(data).filter(Double::isFinite).toArray();
        double total = Arrays.stream(finite).sum();
        return row("validSamples", finite.length, "total", finite.length == 0 ? null : total,
                "mean", finite.length == 0 ? null : total / finite.length,
                "median", quantile(data, .5), "p95", quantile(data, .95));
    }
    private static final class Samples {
        final int owls;
        final double[] wall = new double[120], worldCpu = new double[120], frameCpu = new double[120];
        int count;
        Samples(int owls) { this.owls = owls; }
        Map<String,Object> report() {
            return row("nearbyOwls", owls, "samples", count, "warmFrames", 120,
                    "worldRenderThreadCpuMs", sampleSummary(worldCpu),
                    "renderThreadCpuBetweenFramesMs", sampleSummary(frameCpu),
                    "frameIntervalWallMs", sampleSummary(wall));
        }
    }
}
