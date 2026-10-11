package ai.frankrail.jcmaccel;

import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Short-lived memo for per-frame lookups whose answer only changes when MTR data, resources or route assets change.
 * Used from the render thread only; invalidation may come from any thread and is applied lazily through an epoch.
 * Entries also expire after {@link #TTL_MILLIS} so every visible sign still calls through to MTR about once per second,
 * which keeps MTR's own 10 s texture cooldown alive and bounds any staleness we did not foresee.
 */
public final class RenderMemo<K, V> {

	public static final long TTL_MILLIS = 1000;
	/** Placeholders are re-checked sooner so a freshly generated texture replaces them within a quarter second. */
	public static final long PENDING_TTL_MILLIS = 250;
	private static final AtomicLong EPOCH = new AtomicLong();

	private final Object2ObjectOpenHashMap<K, Entry<V>> entries = new Object2ObjectOpenHashMap<>();
	private final int maxEntries;
	private long epoch = -1;

	public RenderMemo(int maxEntries) {
		this.maxEntries = maxEntries;
	}

	/** Drops every memo; safe to call from any thread. */
	public static void invalidateAll() {
		EPOCH.incrementAndGet();
	}

	/** Same as {@link #invalidateAll()}, counting the source when -Djcmaccel.debug=true. */
	public static void invalidateAll(String source) {
		EPOCH.incrementAndGet();
		if (Stats.ENABLED) Stats.INVALIDATIONS.computeIfAbsent(source, k -> new java.util.concurrent.atomic.AtomicLong()).incrementAndGet();
	}

	/** Debug counters, logged every 10 s by {@link JcmAccelClient} when -Djcmaccel.debug=true. */
	public static final class Stats {
		public static final boolean ENABLED = Boolean.getBoolean("jcmaccel.debug");
		public static final java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicLong> INVALIDATIONS = new java.util.concurrent.ConcurrentHashMap<>();
		public static long hits, misses;
	}

	public static boolean usable() {
		return JcmAccelClient.OPTIMIZE && RenderSystem.isOnRenderThread();
	}

	/** @return the memoised value, or {@code null} on a miss (the value itself may be a sentinel chosen by the caller) */
	public V get(K key, long now) {
		sync();
		final Entry<V> entry = entries.get(key);
		final V value = entry == null || entry.expiresAt < now ? null : entry.value;
		if (Stats.ENABLED) {
			if (value == null) Stats.misses++;
			else Stats.hits++;
		}
		return value;
	}

	public void put(K key, V value, long now) {
		put(key, value, now, TTL_MILLIS);
	}

	public void put(K key, V value, long now, long ttlMillis) {
		sync();
		if (entries.size() >= maxEntries) {
			entries.clear();
		}
		entries.put(key, new Entry<>(value, now + ttlMillis));
	}

	private void sync() {
		final long current = EPOCH.get();
		if (current != epoch) {
			entries.clear();
			epoch = current;
		}
	}

	private record Entry<V>(V value, long expiresAt) {
	}
}
