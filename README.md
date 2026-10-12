# Local Voice Assistant (Android)

Fully on-device AI assistant: voice chat, text chat, PDF/image Q&A (OCR-based),
and a LAN mode that lets your phone act as an LLM server for other devices
(or as a client talking to another phone running this app). No cloud calls
anywhere -- everything runs on the device.

Built/tested target: mid-range chips like MediaTek Dimensity 6020, 8GB RAM.

## 1. Open the project

Open this folder directly in Android Studio (Koala or newer). Let Gradle sync.

## 2. Add the model file (REQUIRED before first build)

Download **Gemma 3 1B IT (int4, .task format)** from Hugging Face's
`litert-community/Gemma3-1B-IT` repo (accept Google's Gemma license first --
it's gated) or from Kaggle Models.

Place the file at exactly:
```
app/src/main/assets/gemma3-1B-it-int4.task
```
Filename casing must match exactly -- it's referenced in `LocalLlmEngine.kt`
as `MODEL_ASSET_NAME`. If your downloaded filename differs, either rename the
file or update that constant to match.

**Note:** the model is ~554MB, so first build/install will be slow (Gradle
has to package it into the APK, then push the whole APK to your device).
This also means the APK is far too large for Play Store's 150MB base APK
limit -- fine for sideloading/testing, but you'd need to switch to
download-on-first-launch before publishing.

## 3. Enable offline speech recognition on your device

Settings → System → Languages & input → On-device speech recognition →
download your language pack. Without this, voice input silently falls back
to online recognition (or fails if you have none).

## 4. Run

Build and run on a real device (not emulator). Grant microphone permission
when prompted.

## Features

- **Voice chat**: tap the mic, speak, get a spoken + streamed text reply.
- **Text chat**: type in the input box, same streamed reply (no TTS).
- **Document Q&A**: tap the paperclip, pick a PDF or image. Text is
  extracted on-device via ML Kit OCR and used as context for your next
  questions until you clear the attachment.
- **Server mode** (default): loads the model and can optionally expose it
  over the LAN. Toggle "Server ON" to start listening on port 8080 --
  any device on the same WiFi can POST `{"prompt": "..."}` to
  `http://<phone-ip>:8080/generate` and get `{"response": "..."}` back.
- **Client mode**: switch the top-right toggle to "Client". The model is
  never loaded on this device -- instead, every message is forwarded to
  another phone's Server-mode instance over WiFi. Enter that phone's IP
  in the field that appears.

## Known limitations / next steps

- No conversation memory across turns (each prompt is sent standalone,
  plus document context if attached).
- No wake-word -- push-to-talk only, by design.
- LAN server has no authentication -- anyone on the WiFi can query it while
  Server mode is on. Fine for home/personal use; don't expose it beyond that.
- Long PDFs can produce more extracted text than the model's context window
  comfortably handles -- very long documents may need truncation/chunking
  if you see cut-off or confused answers.
- Expected performance on a Dimensity 6020 class chip: ~5-10 tokens/sec for
  text generation; OCR on a single image/page is usually 1-3 seconds.

## Project structure

```
app/src/main/java/com/muruga/localvoiceassistant/
├── MainActivity.kt              # Compose UI: chat, mode switch, attach, mic
├── VoiceAssistantViewModel.kt   # Orchestrates everything, holds state
├── LocalLlmEngine.kt            # MediaPipe LLM Inference wrapper
├── LocalLlmServer.kt            # NanoHTTPD server (Server mode)
├── RemoteLlmClient.kt           # HTTP client (Client mode)
├── DocumentProcessor.kt         # PDF/image OCR via ML Kit
├── SpeechToText.kt              # Offline SpeechRecognizer wrapper
└── VoiceOutput.kt               # TextToSpeech wrapper
```
