# Third-party components

| Component | Where | License |
|---|---|---|
| [whisper.cpp](https://github.com/ggml-org/whisper.cpp) (incl. ggml) | `app/src/main/cpp/whisper.cpp` (git submodule) | MIT |
| [Inter](https://github.com/rsms/inter) | `app/src/main/res/font/inter_*.ttf` | SIL Open Font License 1.1 |
| [Space Grotesk](https://github.com/floriankarsten/space-grotesk) | `app/src/main/res/font/space_grotesk_*.ttf` | SIL Open Font License 1.1 |
| [JetBrains Mono](https://github.com/JetBrains/JetBrainsMono) | `app/src/main/res/font/jetbrains_mono_*.ttf` | SIL Open Font License 1.1 |
| Whisper models (`ggml-*.bin`) | downloaded at runtime from Hugging Face, not bundled | MIT (OpenAI Whisper) |
| [faster-whisper](https://github.com/SYSTRAN/faster-whisper) | optional, `pc/transcribir.py` | MIT |

AndroidX / Jetpack Compose / Kotlin coroutines are pulled by Gradle under the Apache License 2.0.
