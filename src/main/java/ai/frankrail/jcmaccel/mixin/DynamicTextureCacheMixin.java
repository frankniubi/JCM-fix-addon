package ai.frankrail.jcmaccel.mixin;

import ai.frankrail.jcmaccel.MemoKeys;
import ai.frankrail.jcmaccel.RenderMemo;
import org.mtr.mod.client.DynamicTextureCache;
import org.mtr.mod.data.IGui;
import org.mtr.mod.route.RouteMapPurpose;
import org.mtr.mod.route.RouteSignStyleMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;

/**
 * Route signs, PSD/APG route maps and railway signs ask {@link DynamicTextureCache} for the same texture every frame. Each
 * call formats a String key, builds and validates a canonical route-asset key (regex) and copies the platform set, which
 * cost ~14% of the render thread at 树园北. A ready texture is reused for {@link RenderMemo#TTL_MILLIS}, a placeholder for
 * {@link RenderMemo#PENDING_TTL_MILLIS}, and every MTR invalidation path below drops the memo.
 */
@Mixin(value = DynamicTextureCache.class, remap = false)
public abstract class DynamicTextureCacheMixin {

	@Unique
	private static final RenderMemo<Object, DynamicTextureCache.DynamicResource> jcmaccel$memo = new RenderMemo<>(16384);

	@Inject(method = "getDirectionArrow", at = @At("HEAD"), cancellable = true)
	private void jcmaccel$directionArrowHead(long platformId, boolean hasLeft, boolean hasRight, IGui.HorizontalAlignment horizontalAlignment, boolean showToString, float paddingScale, float aspectRatio, int backgroundColor, int textColor, int transparentColor, CallbackInfoReturnable<DynamicTextureCache.DynamicResource> cir) {
		if (!RenderMemo.usable()) return;
		jcmaccel$lookup(new MemoKeys.DirectionArrow(platformId, hasLeft, hasRight, horizontalAlignment, showToString, paddingScale, aspectRatio, backgroundColor, textColor, transparentColor), cir);
	}

	@Inject(method = "getDirectionArrow", at = @At("RETURN"))
	private void jcmaccel$directionArrowReturn(long platformId, boolean hasLeft, boolean hasRight, IGui.HorizontalAlignment horizontalAlignment, boolean showToString, float paddingScale, float aspectRatio, int backgroundColor, int textColor, int transparentColor, CallbackInfoReturnable<DynamicTextureCache.DynamicResource> cir) {
		if (!jcmaccel$storable(cir)) return;
		jcmaccel$store(new MemoKeys.DirectionArrow(platformId, hasLeft, hasRight, horizontalAlignment, showToString, paddingScale, aspectRatio, backgroundColor, textColor, transparentColor), cir);
	}

	@Inject(method = "getRouteSignMap(Ljava/util/Set;Lorg/mtr/mod/route/RouteSignStyleMode;Ljava/lang/String;F)Lorg/mtr/mod/client/DynamicTextureCache$DynamicResource;", at = @At("HEAD"), cancellable = true)
	private void jcmaccel$routeSignMapHead(Set<Long> platformIds, RouteSignStyleMode styleMode, String customPlatformHeader, float aspectRatio, CallbackInfoReturnable<DynamicTextureCache.DynamicResource> cir) {
		if (!RenderMemo.usable()) return;
		if (platformIds != null) {
			jcmaccel$lookup(new MemoKeys.RouteSignMap(platformIds, styleMode, customPlatformHeader, aspectRatio), cir);
		}
	}

	@Inject(method = "getRouteSignMap(Ljava/util/Set;Lorg/mtr/mod/route/RouteSignStyleMode;Ljava/lang/String;F)Lorg/mtr/mod/client/DynamicTextureCache$DynamicResource;", at = @At("RETURN"))
	private void jcmaccel$routeSignMapReturn(Set<Long> platformIds, RouteSignStyleMode styleMode, String customPlatformHeader, float aspectRatio, CallbackInfoReturnable<DynamicTextureCache.DynamicResource> cir) {
		if (!jcmaccel$storable(cir)) return;
		if (platformIds != null) {
			// The caller's set belongs to the block entity and may change later, so the stored key holds a copy.
			jcmaccel$store(new MemoKeys.RouteSignMap(Set.copyOf(platformIds), styleMode, customPlatformHeader, aspectRatio), cir);
		}
	}

	@Inject(method = "getRouteMap(JLorg/mtr/mod/route/RouteMapPurpose;ZZFZ)Lorg/mtr/mod/client/DynamicTextureCache$DynamicResource;", at = @At("HEAD"), cancellable = true)
	private void jcmaccel$routeMapHead(long platformId, RouteMapPurpose purpose, boolean vertical, boolean flip, float aspectRatio, boolean transparentWhite, CallbackInfoReturnable<DynamicTextureCache.DynamicResource> cir) {
		if (!RenderMemo.usable()) return;
		jcmaccel$lookup(new MemoKeys.RouteMap(platformId, purpose, vertical, flip, aspectRatio, transparentWhite), cir);
	}

	@Inject(method = "getRouteMap(JLorg/mtr/mod/route/RouteMapPurpose;ZZFZ)Lorg/mtr/mod/client/DynamicTextureCache$DynamicResource;", at = @At("RETURN"))
	private void jcmaccel$routeMapReturn(long platformId, RouteMapPurpose purpose, boolean vertical, boolean flip, float aspectRatio, boolean transparentWhite, CallbackInfoReturnable<DynamicTextureCache.DynamicResource> cir) {
		if (!jcmaccel$storable(cir)) return;
		jcmaccel$store(new MemoKeys.RouteMap(platformId, purpose, vertical, flip, aspectRatio, transparentWhite), cir);
	}

	@Inject(method = "getColorStrip", at = @At("HEAD"), cancellable = true)
	private void jcmaccel$colorStripHead(long platformId, CallbackInfoReturnable<DynamicTextureCache.DynamicResource> cir) {
		if (!RenderMemo.usable()) return;
		jcmaccel$lookup(new MemoKeys.ColorStrip(platformId), cir);
	}

	@Inject(method = "getColorStrip", at = @At("RETURN"))
	private void jcmaccel$colorStripReturn(long platformId, CallbackInfoReturnable<DynamicTextureCache.DynamicResource> cir) {
		if (!jcmaccel$storable(cir)) return;
		jcmaccel$store(new MemoKeys.ColorStrip(platformId), cir);
	}

	@Inject(method = "reload", at = @At("HEAD"))
	private void jcmaccel$reload(CallbackInfo ci) {
		RenderMemo.invalidateAll("reload");
	}

	@Inject(method = "onWorldReset", at = @At("HEAD"))
	private void jcmaccel$worldReset(CallbackInfo ci) {
		RenderMemo.invalidateAll("onWorldReset");
	}

	@Inject(method = "onRouteDataChanged", at = @At("HEAD"))
	private void jcmaccel$routeDataChanged(CallbackInfo ci) {
		RenderMemo.invalidateAll("onRouteDataChanged");
	}

	@Inject(method = "onRouteVariantChanged", at = @At("HEAD"))
	private void jcmaccel$routeVariantChanged(CallbackInfo ci) {
		RenderMemo.invalidateAll("onRouteVariantChanged");
	}

	@Inject(method = "refresh", at = @At("HEAD"))
	private void jcmaccel$refresh(CallbackInfo ci) {
		RenderMemo.invalidateAll("refresh");
	}

	@Inject(method = "destroyRouteAssetTexture", at = @At("HEAD"))
	private void jcmaccel$destroyRouteAssetTexture(CallbackInfo ci) {
		RenderMemo.invalidateAll("destroyRouteAssetTexture");
	}

	@Unique
	private static void jcmaccel$lookup(Object key, CallbackInfoReturnable<DynamicTextureCache.DynamicResource> cir) {
		final DynamicTextureCache.DynamicResource resource = jcmaccel$memo.get(key, System.currentTimeMillis());
		if (resource != null) {
			cir.setReturnValue(resource);
		}
	}

	@Unique
	private static boolean jcmaccel$storable(CallbackInfoReturnable<DynamicTextureCache.DynamicResource> cir) {
		return cir.getReturnValue() != null && RenderMemo.usable();
	}

	/** A placeholder (no texture yet) is only kept briefly so the generated texture shows up soon after MTR finishes it. */
	@Unique
	private static void jcmaccel$store(Object key, CallbackInfoReturnable<DynamicTextureCache.DynamicResource> cir) {
		final DynamicTextureCache.DynamicResource resource = cir.getReturnValue();
		jcmaccel$memo.put(key, resource, System.currentTimeMillis(), resource.isReady() ? RenderMemo.TTL_MILLIS : RenderMemo.PENDING_TTL_MILLIS);
	}
}
