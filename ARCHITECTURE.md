# CleanTube architecture and upstream references

CleanTube is an independently branded Android client. The following projects are studied as upstream references; their product identities and artwork are not copied.

## LibreTube — screen and component patterns
- Reference: https://github.com/libre-tube/LibreTube
- Use its information architecture as the reference for the home feed, search, subscriptions, library, history, and navigation.
- CleanTube screens are implemented in its own Compose UI package and retain the CleanTube identity.

## Flow — Compose playback and playback lifecycle
- Reference: https://github.com/Flow-Tube/Flow
- Reference areas: player manager, player state, background playback policy, and Media3 integration.
- CleanTube uses AndroidX Media3 ExoPlayer and a dedicated MediaSessionService so playback ownership is not tied to a Compose screen lifecycle.
- The current playback screen still needs a full Compose-native control surface and richer playback-state handling; the existing Media3 PlayerView is a transitional video surface.

## NewPipe — extraction and stream handling
- Reference: https://github.com/TeamNewPipe/NewPipe
- NewPipeExtractor is already an application dependency and is used by `data/Repo.kt` for search, video resolution, stream metadata, related videos, and stream selection.
- Media3 builds the playback source from extracted progressive/HLS streams.
- This is not a full port of NewPipe's player service or its advanced DASH/SABR handling.

## Current implementation boundary
- Implemented: NewPipeExtractor-backed repository, Media3 playback, MediaSessionService ownership, Android media-session notification integration, Compose navigation and feed components.
- Not yet implemented/verified: full Flow-style adaptive playback pipeline, complete LibreTube feature parity, independent concurrent audio sessions per tab, complete queue/history/subscription synchronization, and physical-device background playback verification.

## Licensing
LibreTube, Flow, and NewPipe are GPL-3.0 projects. NewPipeExtractor is also GPL-licensed. Any source code copied or adapted from these projects must retain required notices and CleanTube distribution must comply with the applicable GPL terms. Merely studying their architecture does not mean their source code has been copied.
