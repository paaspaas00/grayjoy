# Grayjay Compose

`app-compose` is the migration target for Grayjay's Jetpack Compose and
Material 3 rewrite. It currently provides the new design system, adaptive app
shell, repository-backed immutable UI state, and the first responsive feature
slices for discovery, channel and playlist details, queued playback with a
mini-player, library, preferences, and source management.
Watch Later, download markers, and watch progress are persisted locally through
the repository boundary and update Library immediately.
Installed-source enablement is also repository-backed and persisted locally;
discovery results are recomputed from the active source set.
The module consumes Grayjay's upstream source registry, exposes all registered
sources with payload availability, runs debounced engine-backed local search,
and shares a Media3 player/session between Now Playing and the mini-player.

See [`../docs/compose-rewrite.md`](../docs/compose-rewrite.md) for the migration
architecture, build constraints, sequencing, and definition of done.
