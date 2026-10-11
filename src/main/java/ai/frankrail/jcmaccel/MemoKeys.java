package ai.frankrail.jcmaccel;

import org.mtr.mod.data.IGui;
import org.mtr.mod.route.RouteMapPurpose;
import org.mtr.mod.route.RouteSignStyleMode;

import java.util.Set;

/** Memo keys mirroring the arguments of the memoised MTR methods (kept outside the mixin package, which cannot be loaded directly). */
public final class MemoKeys {

	private MemoKeys() {
	}

	public record DirectionArrow(long platformId, boolean hasLeft, boolean hasRight, IGui.HorizontalAlignment horizontalAlignment, boolean showToString,
								 float paddingScale, float aspectRatio, int backgroundColor, int textColor, int transparentColor) {
	}

	public record RouteSignMap(Set<Long> platformIds, RouteSignStyleMode styleMode, String customPlatformHeader, float aspectRatio) {
	}

	public record RouteMap(long platformId, RouteMapPurpose purpose, boolean vertical, boolean flip, float aspectRatio, boolean transparentWhite) {
	}

	public record ColorStrip(long platformId) {
	}

	public record ClosePlatform(int x, int y, int z, int radius) {
	}
}
