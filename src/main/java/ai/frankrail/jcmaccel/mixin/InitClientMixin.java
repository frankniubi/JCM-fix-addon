package ai.frankrail.jcmaccel.mixin;

import ai.frankrail.jcmaccel.MemoKeys;
import ai.frankrail.jcmaccel.RenderMemo;
import org.mtr.core.data.Platform;
import org.mtr.core.data.Position;
import org.mtr.mapping.holder.BlockPos;
import org.mtr.mod.Init;
import org.mtr.mod.InitClient;
import org.mtr.mod.client.MinecraftClientData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * {@link InitClient#findClosePlatform} scans every platform in the world, and PSD/APG route maps, MTR and JCM PIDS and
 * departure timers call it every frame (5.6% of the render thread at 树园北). The answer for a block position only
 * changes with platform data, which fires {@code DynamicTextureCache.onRouteDataChanged} and so clears {@link RenderMemo}.
 */
@Mixin(value = InitClient.class, remap = false)
public abstract class InitClientMixin {

	@Unique
	private static final RenderMemo<MemoKeys.ClosePlatform, Optional<Platform>> jcmaccel$closePlatforms = new RenderMemo<>(16384);

	@Inject(method = "findClosePlatform", at = @At("HEAD"), cancellable = true)
	private static void jcmaccel$findClosePlatform(BlockPos blockPos, int radius, Consumer<Platform> consumer, CallbackInfo ci) {
		if (!RenderMemo.usable()) {
			return;
		}
		final long now = System.currentTimeMillis();
		final MemoKeys.ClosePlatform key = new MemoKeys.ClosePlatform(blockPos.getX(), blockPos.getY(), blockPos.getZ(), radius);
		Optional<Platform> platform = jcmaccel$closePlatforms.get(key, now);
		if (platform == null) {
			// Same search as MTR 4.0.5 InitClient.findClosePlatform: Stream.min(comparingDouble) keeps the first of equal
			// distances and orders NaN last, which Double.compare reproduces.
			final Position position = Init.blockPosToPosition(blockPos);
			Platform closest = null;
			double closestDistance = Double.MAX_VALUE;
			for (final Platform checkPlatform : MinecraftClientData.getInstance().platforms) {
				if (checkPlatform.closeTo(position, radius)) {
					final double distance = checkPlatform.getApproximateClosestDistance(position, MinecraftClientData.getInstance());
					if (closest == null || Double.compare(distance, closestDistance) < 0) {
						closest = checkPlatform;
						closestDistance = distance;
					}
				}
			}
			platform = Optional.ofNullable(closest);
			jcmaccel$closePlatforms.put(key, platform, now);
		}
		platform.ifPresent(consumer);
		ci.cancel();
	}
}
