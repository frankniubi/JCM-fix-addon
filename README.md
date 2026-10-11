# JCM-fix-addon (`jcmaccel`) — MTR/JCM render accel

A client-only Fabric 1.20.1 addon that removes per-frame work which [MTR](https://github.com/frankniubi/mtrmod-fixes) and [Joban Client Mod](https://github.com/DistrictOfJoban/Joban-Client-Mod) repeat for every sign, door and PIDS, even though the result only changes when route data or textures change.

**+19 % to +28 % FPS** at the busiest stations on the FrankRail server, without changing MTR or JCM source.

> 中文简介：纯客户端 Fabric 1.20.1 附属 mod。把 MTR/JCM 每帧重复做的贴图查找、最近站台搜索缓存最多 1 秒，数据一变就清空。树园北 / 都会中心实测 FPS 提升 19–28%。不改 MTR/JCM 源码；MTR 版本对不上时会自动停用，不会崩溃。

## Targets

| Component | Version |
| --- | --- |
| Minecraft / loader | 1.20.1, Fabric Loader ≥ 0.15 (tested on 0.19.3 with MixinExtras 0.5.4), Fabric API |
| MTR | the route-sign fork, [frankniubi/mtrmod-fixes](https://github.com/frankniubi/mtrmod-fixes) (4.0.5) |
| JCM | 2.2.3 (optional at runtime; JCM PIDS and departure timers benefit through `findClosePlatform`) |

## Why

It started as a JCM study. A JFR profile of the render thread at 树园北 (North Treetrunk, the densest station) showed that JCM itself is only ~2–3 %. MTR's own per-frame lookups are the large, cheaply removable part:

| Render-thread path (inclusive share of samples) | Share |
| --- | ---: |
| `DynamicTextureCache.get*` — `String.format` key, canonical route-asset key + regex validation, platform-set copy | 17.2 % |
| `RenderRouteSign.render` | 16.3 % |
| `RenderPSDAPGDoor.render` | 8.3 % |
| `InitClient.findClosePlatform` (mostly from `RenderRouteBase`, some from JCM) | 5.6 % |
| everything under `com.lx862` (JCM) | ~2–3 % |

## What it changes

| Mixin | Per-frame work removed | Staleness bound |
| --- | --- | --- |
| `DynamicTextureCacheMixin` | `getRouteSignMap` / `getDirectionArrow` / `getRouteMap` / `getColorStrip`: key formatting, canonical key building and validation, platform-set copy | ready texture 1 s, placeholder 0.25 s; cleared on `reload`, `onWorldReset`, `onRouteDataChanged`, `onRouteVariantChanged`, `refresh`, `destroyRouteAssetTexture`, world change, disconnect |
| `InitClientMixin` | `findClosePlatform` scanning every platform (route maps on PSD/APG, MTR PIDS, JCM PIDS, departure timers) | 1 s; same invalidations |

- Every memo entry expires after at most 1 s, so each visible sign still calls MTR about once per second and MTR's own 10 s texture cooldown stays alive.
- Memos are only touched on the render thread.
- Only `@Inject` mixins are used. The mixin config is `required: false`, so if a future MTR build changes these methods the addon disables itself with a mixin warning instead of crashing the client.
- `-Djcmaccel.optimize=false` turns everything off (A/B baseline). `-Djcmaccel.debug=true` logs memo hits/misses and invalidation sources every 10 s.

## Performance

Scripted flight through 树园北 and 都会中心 (fixed route, then 12 s holds), player mod set (Sodium, Indium, Sodium Extra, ImmediatelyFast, EntityCulling, ModernFix, JCM 2.2.3, LU/Russian addons), alternating off/on/off/on, 3 passes each:

| Leg | off | on | change |
| --- | ---: | ---: | ---: |
| 树园北 fly | 45.6 fps | 58.6 fps | **+28 %** |
| 树园北 hold | 52.9 fps | 63.0 fps | **+19 %** |
| 都会中心 fly | 90.5 fps | 114.0 fps | **+26 %** |
| 都会中心 hold | 82.7 fps | 105.3 fps | **+27 %** |

树园北 fly p95 frame time ~39 ms → ~31 ms. On the real launcher stack (Fabric Loader 0.19.3 / MixinExtras 0.5.4, full client mod list) 0.1.1 completed the flight with a 99.3 % memo hit rate. Run-to-run drift on a laptop is ±15 %, so only compare alternating runs.

## Install

Drop `jcmaccel-<version>.jar` into the client's `mods/` next to the MTR fork jar. Servers don't need it.

## Build

The MTR and JCM jars are compile-only dependencies and are not in this repository. Put them in `libs/` or pass their paths:

```shell
# libs/MTR-fabric-4.0.5+1.20.1-route-sign.jar and libs/JCM-fabric-2.2.3+1.20.1.jar by default
./gradlew build -PmtrJar=/path/to/MTR-fabric-4.0.5+1.20.1-route-sign.jar -PjcmJar=/path/to/JCM-fabric-2.2.3+1.20.1.jar
# jar: build/libs/jcmaccel-0.1.1.jar
```

Gradle needs Java 21 (Loom); the mod targets Java 17.

## Benchmark & testing tools

- `./gradlew runClient -Pbench=<label> -Popt=true|false` loads a singleplayer world called `jcmbench` from `run/saves/` (bring your own; the route in `src/.../bench/BenchRunner.java` is for the FrankRail world), flies the route, records frame times and a JFR to `run/bench/<label>.{json,csv,jfr}` and quits. Put the other client mods in `run/mods/`.
- `bench/jfr_stacks.py <x.jfr>` → `<x>.stacks` (render thread, full stacks); `bench/stackq.py <x.stacks> inclusive|callers|children ...`. `jfr print` only keeps 5 frames by default, so use these for inclusive numbers.
- **Before releasing**, test on the players' real loader stack, not only the Loom dev client:
  `JCMACCEL_MC_DIR=<launcher .minecraft> python bench/prodlaunch.py <throwaway gamedir> -Djcmaccel.bench=<label>` (mods in `<gamedir>/mods`). The dev client (newer loader) hid a crash that hit every player.

## History

- **0.1.0** crashed every client at startup: a `@Redirect` of `new Identifier(String)` in `RenderPSDAPGDoor` hits a MixinExtras 0.5.4 bug (`FactoryRedirectWrapperMixinTransformer` ClassCastException). That is a critical mixin error, which `required: false` does not catch.
- **0.1.1** removed the door mixin; only `@Inject` remains. Verified with `bench/prodlaunch.py`.

## License

MIT, see [LICENSE](LICENSE).
