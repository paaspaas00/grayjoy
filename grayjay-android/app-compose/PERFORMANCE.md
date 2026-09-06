# Performance work on the optimize branch

Base: Grayjoy 2.0.0, commit c28498b. Work performed on 6 September 2026.

The objective is to reduce CPU, allocation, disk and layout work while preserving the
existing card geometry, typography, colors, navigation and animations. No source capabilities
or animation settings are removed.

## Changes

- Device memory/ABI classification is cached once per process and supplied through the theme.
  Composing every thumbnail/avatar/card no longer repeats ActivityManager queries.
- Dynamic Material color schemes are remembered by configuration and theme. Changes to player
  state or download progress no longer rebuild the color scheme or reset system-bar appearance.
- Home disk-cache parsing runs off the main thread. Restore results cannot override a newer
  feed request or another profile. Profile library parsing also runs on IO.
- Switching a cached Home tab only updates the in-memory selected tab. It no longer serializes
  every cached feed. Snapshot writes are coalesced; invalidations run on the same serial IO
  dispatcher and remove JSON branches without recreating all video models.
- Visited Home pages keep their content during horizontal swipes. The pager does not eagerly
  compose a whole additional thumbnail-heavy page while stationary.
- Lists have a bounded prefetch/retention window (320/184 dp; 160/80 dp on constrained devices).
  Reverse scrolling can reuse recently composed rows without retaining an entire feed.
- Shimmer blocks share one clock per skeleton list, cache their outlines/colors, and read
  animation state during drawing. Card entrances and controls fading also defer frame-by-frame
  state reads to drawing/layers.
- Predictive-back progress no longer invalidates the root composition on every gesture frame.
- The player morph animates a clipped viewport while measuring its detail subtree with stable
  expanded constraints. The current detail layout is retained at zero visible/hit-test height
  while minimized; hidden paging is disabled and the retained subtree is released when playback
  is dismissed. This trades a bounded current-screen layout for less work on each expansion.
- Glide requests are released when pooled AndroidViews leave composition permanently, as well
  as when reused. Existing decode-size caps, request headers and fallback thumbnails remain.
- Subscription matching uses up to three hash-set lookups per video, replacing the nested scan
  over every followed channel. Progress publication is batched.
- Library snapshots preserve unchanged video and list identities. A history checkpoint no longer
  allocates replacement lists for unrelated feeds. Empty/no-op repository mutations do not
  serialize the whole library.
- Successful playback resolution no longer performs two redundant full-library writes before
  opening Media3. History persistence already saves the resolved metadata on IO.
- Feed/channel/playlist presentation conversion runs off the UI thread.
- The six-hour plugin update interval survives process restarts, avoiding repeated startup
  update traffic.

## Measurements and limits

Device available: Pixel 9a, 1080 x 2424, Android 17, local debug build. The reported Pixel 6a
was not available. These are exploratory device measurements, not a controlled benchmark
or a guarantee of identical gains on a different SoC. Refresh rate, JIT, image caches,
background work and thermal state affect the results.

Scroll sequence: six 320 ms upward swipes and six downward swipes in the same list viewport,
using Android gfxinfo. Baseline uses the unmodified 2.0.0 source; optimized measurements use
the same package and preserved local data.

| Scenario | Baseline late frames | Optimized late frames | Baseline p95 | Optimized p95 |
|---|---:|---:|---:|---:|
| Home, loaded content | 45/546 (8.24%) | 19/493 (3.85%) | 27 ms | 16 ms |
| Prefs | 12/530 (2.26%) | 8/542 (1.48%) | 12 ms | 11 ms |

Intermediate Home loaded-content samples were 4.07%; Prefs 1.91%. A first Home scroll
two seconds after reinstall still measured 10.41% late frames (p95 31 ms, p99 61 ms).
This cold case remains a limitation; the loaded-content figures must not be represented
as cold-start results or proof of perfect fluidity.

Player expand/collapse and navigation were exercised repeatedly. Late-frame rates improved
in exploratory runs, but the active video and decoder load were not held constant, so no
quantitative morph improvement is claimed here. ADB launch-wait readings fell from 976 ms
to 675–845 ms; these are not isolated startup TTID measurements.

## Repeating on a Pixel 6a or a slower phone

1. Build the baseline and optimize branch with the same build type and signing configuration.
   Keep app data, refresh rate, network state and Android compilation mode consistent.
2. Measure cold launch/first scroll separately from repeated loaded-content scrolls.
3. With the target list at its top, run tools/measure-scroll.ps1. Its default coordinates match
   the Pixel 9a portrait viewport used above; supply explicit coordinates for another device.
4. Repeat each scenario several times and compare distributions, not a single average.
5. Include Home, Prefs, History, search results, channel paging, tab swipes, and player
   minimize/expand with a fixed local video. Check that navigation remains clickable and
   focus/IME does not reopen when the player is expanded.

## Validation

- Debug and release assemblies, including release vital lint.
- Existing JVM test suite plus identity-preserving updates and a 10,000-video/2,000-channel
  regression test that rejects iteration over the followed-channel set.
- Pixel 9a smoke checks for loading, repeated scrolling, tabs, player transitions and
  navigation with a retained/minimized player.
- The full debug lint baseline has unrelated existing findings; this work does not claim
  to clear every pre-existing lint issue.

Implementation follows the Android guidance on
[deferring reads and caching work](https://developer.android.com/develop/ui/compose/performance/bestpractices),
[strong skipping](https://developer.android.com/develop/ui/compose/performance/stability/strongskipping)
and [bounded lazy-layout cache windows](https://developer.android.com/reference/kotlin/androidx/compose/foundation/lazy/layout/LazyLayoutCacheWindow).
