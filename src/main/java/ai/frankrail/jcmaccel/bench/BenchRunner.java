package ai.frankrail.jcmaccel.bench;

import ai.frankrail.jcmaccel.JcmAccelClient;
import jdk.jfr.Configuration;
import jdk.jfr.Recording;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Scripted camera flight through the 树园北 + 都会中心 cluster. Started with -Djcmaccel.bench=(label); writes
 * bench/(label).csv, .json and .jfr in the run directory and then closes the game.
 */
public final class BenchRunner {

	/** One measured piece of the route: fly from a to b (or hold at a when a == b) for the given time. */
	private record Leg(String name, Vec3d a, Vec3d b, float yaw, float pitch, double seconds) {
		Vec3d at(double f) {
			return a.lerp(b, Math.min(1, Math.max(0, f)));
		}
	}

	// 树园北 platform level (PIDS at y72-74) eastwards over 都会中心 (PIDS at y86); z-370 runs through the densest cells.
	private static final List<Leg> ROUTE = List.of(
			new Leg("shuyuan_fly", new Vec3d(100.5, 78, -370.5), new Vec3d(245.5, 78, -370.5), -90, 10, 24),
			new Leg("duhui_fly", new Vec3d(248.5, 88, -370.5), new Vec3d(298.5, 88, -370.5), -90, 15, 10),
			new Leg("shuyuan_hold", new Vec3d(205.5, 77, -372.5), new Vec3d(205.5, 77, -372.5), -90, 8, 12),
			new Leg("duhui_hold", new Vec3d(262.5, 88, -372.5), new Vec3d(262.5, 88, -372.5), -90, 15, 12)
	);
	private static final int PASSES = 3;
	private static final double WARMUP_SECONDS = 45;

	private static String label;
	private static int phase; // 0 wait for world, 1 warm-up, 2 pre-roll, 3 measuring, 4 done
	private static long phaseStart;
	private static int pass;
	private static int legIndex;
	private static long legStart;
	private static long lastFrame;
	private static final List<String> rows = new ArrayList<>();
	private static final Map<String, List<Double>> frameMillis = new LinkedHashMap<>();
	private static Recording recording;

	private BenchRunner() {
	}

	public static void register(String benchLabel) {
		label = benchLabel;
		ClientTickEvents.END_CLIENT_TICK.register(BenchRunner::tick);
		WorldRenderEvents.START.register(context -> frame());
	}

	private static void tick(MinecraftClient client) {
		final ClientPlayerEntity player = client.player;
		if (player == null || client.world == null) {
			return;
		}
		final long now = System.nanoTime();
		if (phase == 0) {
			player.networkHandler.sendChatCommand("gamemode spectator");
			player.networkHandler.sendChatCommand("time set 6000");
			player.networkHandler.sendChatCommand("gamerule doDaylightCycle false");
			player.networkHandler.sendChatCommand("weather clear 100000");
			final Leg first = ROUTE.get(0);
			player.networkHandler.sendChatCommand(String.format(Locale.ROOT, "tp @s %.1f %.1f %.1f %.1f %.1f", first.a.x, first.a.y, first.a.z, first.yaw, first.pitch));
			phase = 1;
			phaseStart = now;
			JcmAccelClient.LOGGER.info("[bench] {} warm-up {} s", label, WARMUP_SECONDS);
		} else if (phase == 1 && seconds(now - phaseStart) >= WARMUP_SECONDS) {
			phase = 2; // one unmeasured pass so every chunk on the route is built
			startLeg(0, now);
			JcmAccelClient.LOGGER.info("[bench] {} pre-roll", label);
		}
	}

	private static void frame() {
		final MinecraftClient client = MinecraftClient.getInstance();
		final ClientPlayerEntity player = client.player;
		if (player == null || phase < 2 || phase > 3) {
			return;
		}
		final long now = System.nanoTime();
		Leg leg = ROUTE.get(legIndex);
		double t = seconds(now - legStart);
		if (t >= leg.seconds) {
			if (legIndex + 1 < ROUTE.size()) {
				startLeg(legIndex + 1, now);
			} else if (phase == 2) {
				phase = 3;
				pass = 0;
				startRecording();
				startLeg(0, now);
			} else if (++pass < PASSES) {
				startLeg(0, now);
			} else {
				finish(client);
				return;
			}
			leg = ROUTE.get(legIndex);
			t = 0;
		}
		final Vec3d position = leg.at(t / leg.seconds);
		player.setVelocity(Vec3d.ZERO);
		player.refreshPositionAndAngles(position.x, position.y, position.z, leg.yaw, leg.pitch);
		player.setHeadYaw(leg.yaw);

		// The first frames of a leg include the teleport; only count steady frames.
		if (phase == 3 && lastFrame != 0 && t > 0.25) {
			final double millis = (now - lastFrame) / 1e6;
			frameMillis.computeIfAbsent(leg.name, k -> new ArrayList<>()).add(millis);
			rows.add(String.format(Locale.ROOT, "%d,%s,%.4f,%.3f,%.2f,%.2f,%.2f", pass, leg.name, t, millis, position.x, position.y, position.z));
		}
		lastFrame = now;
	}

	private static void startLeg(int index, long now) {
		legIndex = index;
		legStart = now;
		lastFrame = 0;
	}

	private static void startRecording() {
		try {
			recording = new Recording(Configuration.getConfiguration("profile"));
			recording.setName("jcmaccel-" + label);
			recording.start();
		} catch (Exception e) {
			JcmAccelClient.LOGGER.error("[bench] JFR start failed", e);
		}
		JcmAccelClient.LOGGER.info("[bench] {} measuring {} passes", label, PASSES);
	}

	private static void finish(MinecraftClient client) {
		phase = 4;
		final Path dir = client.runDirectory.toPath().resolve("bench");
		try {
			Files.createDirectories(dir);
			if (recording != null) {
				recording.stop();
				recording.dump(dir.resolve(label + ".jfr"));
				recording.close();
			}
			try (Writer writer = Files.newBufferedWriter(dir.resolve(label + ".csv"), StandardCharsets.UTF_8)) {
				writer.write("pass,leg,t,frame_ms,x,y,z\n");
				for (final String row : rows) {
					writer.write(row);
					writer.write('\n');
				}
			}
			final StringBuilder json = new StringBuilder("{\n  \"label\": \"").append(label).append("\",\n  \"optimize\": ").append(JcmAccelClient.OPTIMIZE).append(",\n  \"legs\": {");
			boolean firstLeg = true;
			for (final Map.Entry<String, List<Double>> entry : frameMillis.entrySet()) {
				final double[] values = entry.getValue().stream().mapToDouble(Double::doubleValue).sorted().toArray();
				final double total = Arrays.stream(values).sum();
				json.append(firstLeg ? "\n" : ",\n").append(String.format(Locale.ROOT,
						"    \"%s\": {\"frames\": %d, \"mean_fps\": %.2f, \"p50_ms\": %.2f, \"p95_ms\": %.2f, \"p99_ms\": %.2f}",
						entry.getKey(), values.length, values.length * 1000 / total, percentile(values, 0.5), percentile(values, 0.95), percentile(values, 0.99)));
				firstLeg = false;
			}
			json.append("\n  }\n}\n");
			Files.writeString(dir.resolve(label + ".json"), json.toString());
			JcmAccelClient.LOGGER.info("[bench] {} done\n{}", label, json);
		} catch (IOException e) {
			JcmAccelClient.LOGGER.error("[bench] writing results failed", e);
		}
		client.scheduleStop();
	}

	private static double percentile(double[] sorted, double p) {
		return sorted.length == 0 ? 0 : sorted[Math.min(sorted.length - 1, (int) Math.floor(p * sorted.length))];
	}

	private static double seconds(long nanos) {
		return nanos / 1e9;
	}
}
