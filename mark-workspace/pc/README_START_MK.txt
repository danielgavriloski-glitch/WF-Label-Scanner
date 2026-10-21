MarK + TD AI Studio PRO — Integrated build v0.2 (15.10.2026)

1. Extract whole ZIP to H:\MarK_TD_AI_Studio_PRO (or another NEW folder). Do not overwrite your old TD setup.
2. Double-click INSTALL_ALL_IN_ONE.bat, requires Windows 64-bit, Python 3.11–3.13 and internet ONCE.
   Installs isolated Python environment and downloads official llama.cpp runtime, Qwen3-VL-4B Q4 vision model + projector. 3GB+ internet download and enough free space required. A shortcut is created on Desktop and the program launches automatically after installation.
3. Start via Desktop shortcut or START_MarK_TD.bat, sign in using PIN displayed in the Windows console, and click MarK ENGINE in original TD main UI.
4. Legacy TD panel is preserved (video, photo, Premiere bridge, director, AI assistant). Separate MarK chat has SQLite memory.
5. For Premiere UXP: plugin source is in app/TD_Premiere_Bridge_uxp; enabling this UXP plugin inside Adobe still depends on supported Adobe deployment flow. No guarantee that it is installed.
6. Lightroom plugin sources included; Lightroom Classic plugin activation may require Lightroom Plug-in Manager; installers do not overwrite existing plugins.
7. Booking: local JSON import bridge exists but live pairing/sync with Android is NOT implemented because TD Booking Android source was not supplied. The APK continues standalone; no app data is changed.
8. Legacy TD Studio's media pipeline still uses existing project paths and commands. This build is not proven on the user's PC or in Premiere; model-based wedding processing needs real device tests.
9. Our rebranded MarK core is a llama.cpp and Apache-2.0 Qwen3-VL open-source model orchestration, NOT a newly trained proprietary model; check third-party licenses for commercial distribution.
10. Security: main TD app binds on LAN as it did previously. The MarK chat/memory/Booking routes accept loopback requests only. DO NOT expose port 8765 to internet. Keep your PIN secret.
11. Original project path H:\TD_AI_Assistant is not modified. Default media workspace is inside new installation. Only choose original media as source if intentional.

Troubleshooting: install log is data/install_log.txt. On Windows missing DLLs prefer matching GPU driver/runtime or switch to CPU llama.cpp release. If port 8765 is already used stop old TD instance before launching new one.
