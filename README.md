# Local Voice Assistant (Android)

Fully on-device voice assistant: push-to-talk STT (Android offline
SpeechRecognizer) → local LLM (Gemma 3 1B via MediaPipe LLM Inference) →
Android TextToSpeech. No network calls at runtime.

Built/tested target: mid-range chips like MediaTek Dimensity 6020, 8GB RAM.

## 1. Open the project

Open this folder directly in Android Studio (Koala or newer). Let Gradle sync.

## 2. Get a model

Download a MediaPipe-ready `.task` model — e.g. **Gemma 3 1B IT (int4)** —
from Hugging Face or Kaggle Models (search "gemma litert" / "gemma
mediapipe task"). File will be roughly 500MB–1.2GB.

## 3. Push the model to your device (for development/testing)

```bash
adb shell mkdir -p /data/local/tmp/llm
adb push gemma3-1b-it-int4.task /data/local/tmp/llm/
```

This matches `LocalLlmEngine.DEFAULT_MODEL_PATH`. For a real release build,
don't rely on `adb push` — instead bundle the model in `assets/` (mind APK
size limits) or download it on first launch into `context.filesDir`, then
update the path passed into `LocalLlmEngine`.

## 4. Enable offline speech recognition on the device

Settings → System → Languages & input → On-device speech recognition →
download your language pack. Without this, `EXTRA_PREFER_OFFLINE` silently
falls back to online recognition.

## 5. Run

Build and run on your device (not emulator — you want real CPU/GPU
performance numbers). Tap the mic button, speak, and the assistant will
transcribe, generate a response with the local LLM, and speak it back.

## Expected performance (Dimensity 6020 / similar mid-range, 8GB RAM)

- Gemma 3 1B int4: ~5–10 tokens/sec, no NPU offload (runtime falls back to
  CPU/GPU delegate on this chip tier).
- First model load: a few seconds, keep it lazy-loaded (already done in
  `VoiceAssistantViewModel`) so app launch isn't blocked.

## Project structure

```
app/src/main/java/com/muruga/localvoiceassistant/
├── MainActivity.kt              # Compose UI + permission handling
├── VoiceAssistantViewModel.kt   # orchestrates STT -> LLM -> TTS
├── LocalLlmEngine.kt            # MediaPipe LLM Inference wrapper
├── SpeechToText.kt              # offline SpeechRecognizer wrapper
└── VoiceOutput.kt               # TextToSpeech wrapper
```

## Next steps / known gaps

- No conversation memory yet — each prompt is sent to the LLM standalone.
  To add multi-turn context, maintain a running string of prior turns and
  prepend it to each new prompt (MediaPipe's LlmInference call is stateless
  per invocation).
- No wake-word — push-to-talk only, by design.
- No VAD/streaming audio — SpeechRecognizer handles endpointing internally.
- Consider a foreground service if you later want always-listening mode,
  since Android will kill background mic access aggressively otherwise.
