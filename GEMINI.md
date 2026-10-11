# Project Rules: OSRnary (GabAI)

## 1. Always Use Graphify (Token Efficiency & Navigation)
- **MANDATORY**: Whenever exploring architecture, inspecting code dependencies, answering questions about the codebase, or locating symbols, **ALWAYS query Graphify first** (`graphify query "<question>"`, `graphify path "<node1>" "<node2>"`, `graphify explain "<node>"`).
- **Conserve Tokens**: Never dump raw batches of code files into the chat context when the persistent knowledge graph in `graphify-out/graph.json` can provide the exact subgraphs and locations.
- **Maintain Graph Freshness**: After making code changes, run `graphify update .` to keep `graphify-out/` synchronized (fast, free AST parsing).

## 2. Engineering & Design System Standards
- **Tech Stack**: Kotlin (Android SDK 35, Min SDK 24), Android Gradle Plugin 9.1.1, Gradle 9.3.1, Java 17 (Temurin).
- **Design Language**: Bento squircle card UI, hardware-accelerated frosted acrylic glass (`FastBlurView`), custom dialogs via `GabAIDialogs` (never default system alert dialogs), spring press effects, and haptic feedback.
- **Zero-Latency UI**: Always pre-cache user identity and state from `SharedPreferences` to eliminate placeholder loading flickers.
- **Role Boundary**: Mobile app (`com.example.gabai`) is strictly for Students and Teachers; Super Admins and School Admins use the Web Admin Portal (`admin-portal/`).
- **Encoding & Comment Integrity**: Preserve existing comments, docstrings, and untouched code. Maintain strict UTF-8 formatting for all gamification badges, streak counters, and emojis.

## 3. Build & Testing Protocols
- **Build**: `./gradlew assembleDebug` (with `JAVA_HOME` pointing to Java 17).
- **Deploy**: Use `./deploy-wireless.ps1` for ADB deployment over Wi-Fi (`-r -d` flags).
- **Permissions**: Defensively check runtime permissions (`CAMERA`, `SYSTEM_ALERT_WINDOW`) before launching services or camera activities to prevent hanging overlays.
