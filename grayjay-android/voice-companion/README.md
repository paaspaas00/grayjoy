# Grayjoy Voice companion

Grayjoy Voice adds offline voice commands to Grayjoy through a separate Android APK. Microphone listening is disabled until the user starts it, and Grayjoy must separately allow companion commands. Recognition, interpretation and spoken replies run on the phone; searching or streaming YouTube still requires connectivity.

## Installation and activation

Install Grayjoy and Grayjoy Voice builds signed with the same certificate. The companion supports ARM64 and x86-64 devices running Android 9 or later. The main app retains its existing architecture support.

1. In Grayjoy, open Prefs → Controllo vocale and enable command access.
2. Open Grayjoy Voice, select STT and TTS languages and a voice, and download the selected models.
3. Grant microphone permission and tap Attiva. The foreground notification provides a stop action.

The activation phrase is “ok Grayjoy”, independently of the command language. A short blip acknowledges it. Say a command after the blip: “abbassa il volume”, “metti in pausa”, “avvia la playlist Songs”, “riproduci Bohemian Rhapsody”, or “next song”. A double blip acknowledges the recognized operation, which is dispatched without waiting for speech synthesis. Simple controls do not speak over the music; optional spoken confirmations accompany named playback requests.

During a command, Grayjoy's player runs at 50% of its current volume by default. The companion offers a 0–100% setting and restores normal playback afterward, including disconnection or companion termination. This does not modify Android's music-volume setting and does not stack Android's generic audio-focus attenuation with Grayjoy's own reduction.

Explicit playlist requests go directly to local playlist matching. Generic named requests such as “avvia Nueva” and “metti in play Nueva” can also match a local playlist. Video requests check completed, playable downloads first; otherwise they choose the most-viewed relevant YouTube result. A missing or ambiguous playlist name produces a specific clarification. Follow-up answers retain the expected name slot. Say “annulla” to cancel a clarification.

“Che cosa puoi fare?” explains the available commands. “Elenca le playlist” reads five names at a time and offers to continue. Named playback requests use semantic tool selection by the chosen language model; clear transport controls such as pause retain their immediate path. Reply interpretation accepts both familiar short answers and free-form agreement or refusal through the model.

New YouTube selections start playback and an audio download together. MCP exposes `network_status`, `download_audio`, `play_local`, and `play_random_music`; downloads accept only a known supported video ID, not arbitrary URLs. Without Internet, matching downloads still play. Otherwise the assistant offers local playback and, after consent, asks which downloaded song or playlist to use.

“Metti su qualcosa” asks whether any music is suitable. Confirmation chooses randomly among the latest 100 available, identified music items in history, avoiding the currently playing item when another candidate exists. Offline selection includes only playable downloads. A stated mood asks for a suitable song or playlist rather than pretending to know the mood of every recording.

## Messages and telephone calls

Both features are off by default and require separate opt-in in Grayjoy Voice settings. Message announcements additionally require Android notification-listener access; calls require Contacts and Phone runtime permissions.

For enabled WhatsApp and Telegram notifications, the assistant announces the sender and application, then asks permission to read. A refusal or silence discards the dialogue. Message text is never interpreted as a command, supplied to the language model, or saved to disk. The in-memory inbox is bounded to eight messages, expires after two minutes, and ignores old notifications at activation. Android may redact content; the assistant cannot recover text that the notification does not expose.

“Chiama Mario” uses the stored phone number of a matching contact through Android Telecom. An exact unique match can call directly; fuzzy matches and multiple numbers require selection or confirmation. The assistant never dials a number invented by a model. Permissions are rechecked and the selected contact row is reread before calling. Listening suspends during telephone or VoIP calls and resumes afterward.

Silero detects speech after activation, so steady road noise and silence do not become requests merely because they are loud. Empty input and accidental non-command speech end the listening window silently. Clear short controls bypass inference; other phrasings reach the language model without a prefix whitelist. The model has a silent ignore result, and uncertain non-command input never triggers an unsolicited clarification or a search.

Stopping from either app releases the microphone. Listening must be restarted manually after a reboot or force-stop. Private sessions and protected profiles do not accept voice commands; switching profiles stops listening.

## Models and latency

The implementation reuses [Vosk](https://alphacephei.com/vosk/models) for bounded activation recognition and streaming shortcuts, [sherpa-onnx](https://k2-fsa.github.io/sherpa/onnx/android/index.html) for Silero, quantized Whisper and neural speech, and [llama.cpp](https://github.com/ggml-org/llama.cpp/blob/master/docs/android.md) for command interpretation. The GUI offers Qwen 3 0.6B (397 MB), [Qwen 3 1.7B](https://huggingface.co/Qwen/Qwen3-1.7B) (1.11 GB) and [Qwen 2.5 Instruct 3B](https://huggingface.co/Qwen/Qwen2.5-3B-Instruct) (2.10 GB), all Q4_K_M. The latter uses the Qwen Research License; license links accompany downloads. Native dependencies and model downloads are pinned and checksum-verified. Weights are not bundled in either APK.

The voice picker offers Piper Paola and Riccardo in Italian, Piper Lessac in English, Kokoro Sara and Nicola in Italian plus selected English voices, and ten Supertonic 3 presets in each supported UI language. The “Prova voce” button plays a synthetic example without opening the microphone. A speaker preset reuses its engine's shared download: Piper is approximately 13–21 MB per voice, Kokoro 132 MB, and Supertonic 3 129 MB. Only the selected engine is loaded. Kokoro uses Apache 2.0 weights; Supertonic 3 uses OpenRAIL-M weights and MIT sample code. License links are available alongside downloads.

Fixed replies, including the five clarification variants, are pre-generated in the background. Preparation pauses when a command starts. A local 64 MB cache retains synthetic replies across service restarts and keys them by voice, model checksum, and synthesis settings; checksums and bounded audio headers reject corrupt cache files. Stock replies can play directly from this cache without waiting for a cold TTS model. Playlist names, messages, and other dynamic text are never persisted in the speech cache. Long speech is split into bounded chunks.

Whisper Base int8 is the balanced default; Tiny remains available for a smaller footprint and Small int8 for more accurate recognition at a higher memory and compute cost. Small downloads a 639 MB archive and retains about 375 MB of int8 files. The compact English activation model recognizes the app name; Italian commands use a separate Italian streaming model. Confident streaming transcripts avoid an extra transcription pass, and unambiguous transport commands avoid LLM inference. Command models prepare when listening starts and retain their context while listening remains enabled; the GUI shows when preparation is complete. Other prepared speech models are released after two idle minutes. Constrained decoding limits model output to supported operations, with a separate restricted grammar for consent. Speech playback drains its actual audio frames and a silent output tail.

A Pixel 9a integration test measured 336–367 ms from a simulated end of speech to the volume action, including a 300 ms endpoint allowance, through the streaming shortcut. With the revised command prompt, the two directly tested Qwen pause paraphrases took 773 and 895 ms for interpretation alone with a prepared context. Transcription, cold model preparation, spoken replies, and network playback loading add further time: the free-form path does not meet a universal one-second latency limit. Real microphone tests with music, road noise and different speakers remain necessary; the models do not guarantee correct recognition of every phrase. The current backend uses CPU acceleration, not a verified Pixel NPU path.

A subsequent Pixel 9a check exercised nine requests per command model, including three semantic paraphrases without shortcuts and three accidental/non-command inputs. All expected actions matched. Warm semantic interpretation measured roughly 0.9–2.2 seconds for 0.6B, 2.2–4.6 seconds for 1.7B, and 2.4–4.6 seconds for 3B. These are interpretation-only measurements, not microphone-to-playback latency. Larger-model preparation completed in approximately 11 and 20 seconds in that run and happens in the background when listening starts.

## Local MCP transport and security

The main app exposes MCP JSON-RPC methods `initialize`, `tools/list`, and `tools/call`. This is a documented custom transport: newline-delimited UTF-8 JSON over TCP bound only to `127.0.0.1`, with bounded frames. Android SELinux blocks the initially evaluated Unix socket connection between the two app UIDs.

A signature-protected bound Android service supplies the ephemeral port and a random 256-bit session token only to the paired companion package. Every socket request authenticates that token. The bridge is not reachable from the LAN and does not accept arbitrary URLs, intents, file operations, or profile changes. A profile or privacy change invalidates pending commands before their results are applied.

The app's shared player publishes its media session only for playback. Import-only engines do not advertise empty sessions. Playback starts after its notification service has entered foreground, and pauses if that host disappears. Voice attenuation takes priority over generic competing-audio attenuation; assistant output is excluded from the competing-player count, including delayed Bluetooth output.

Audio stays in bounded RAM buffers. The companion has no telemetry or cloud inference API. Model downloads use HTTPS and app-private storage with disk reserves, integrity checks and safe extraction.

## Build and tests

Build both modules with the normal Gradle wrapper. Add `-PGRAYJOY_DEBUG_APPLICATION_ID_SUFFIX=.graytest` for the isolated test pair. The companion inherits Grayjoy’s signing configuration; do not introduce a different release key.

Unit tests cover command routing, malformed model output, bounded MCP frames, and randomized inputs. Device tests cover Italian wake recognition, short streaming commands, neural synthesis and transcription, bilingual interpretation, and authenticated MCP access. Run device tests with explicit APK installation and `am instrument` when retaining downloaded test models is important; the Gradle connected-test runner can uninstall its target package afterward.
