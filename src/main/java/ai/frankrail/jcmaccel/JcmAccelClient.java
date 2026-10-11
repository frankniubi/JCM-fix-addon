package ai.frankrail.jcmaccel;

import ai.frankrail.jcmaccel.bench.BenchRunner;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.world.ClientWorld;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class JcmAccelClient implements ClientModInitializer {

	public static final Logger LOGGER = LoggerFactory.getLogger("jcmaccel");
	/** Every optimisation checks this flag, so one jar can run both sides of an A/B benchmark. */
	public static final boolean OPTIMIZE = !"false".equals(System.getProperty("jcmaccel.optimize"));

	private static ClientWorld lastWorld;
	private static long debugTicks;

	@Override
	public void onInitializeClient() {
		LOGGER.info("JCM Accel optimize={}", OPTIMIZE);
		// Memo keys are block positions without a dimension, so a world or dimension change drops them.
		ClientTickEvents.START_CLIENT_TICK.register(client -> {
			if (client.world != lastWorld) {
				lastWorld = client.world;
				RenderMemo.invalidateAll("world");
			}
			if (RenderMemo.Stats.ENABLED && ++debugTicks % 200 == 0) {
				LOGGER.info("[debug] memo hits={} misses={} invalidations={}", RenderMemo.Stats.hits, RenderMemo.Stats.misses, RenderMemo.Stats.INVALIDATIONS);
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> RenderMemo.invalidateAll("disconnect"));
		final String bench = System.getProperty("jcmaccel.bench");
		if (bench != null && !bench.isEmpty()) {
			BenchRunner.register(bench);
		}
	}
}
