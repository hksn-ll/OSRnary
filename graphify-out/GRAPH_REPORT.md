# Graph Report - OSRnary  (2026-10-10)

## Corpus Check
- 67 files · ~165,745 words
- Verdict: corpus is large enough that graph structure adds value.
- Unclassified: 288 file(s) not represented in the graph (top: .xml 263, .ttf 7, (none) 5)

## Summary
- 1245 nodes · 3241 edges · 57 communities (19 shown, 38 thin omitted)
- Extraction: 100% EXTRACTED · 0% INFERRED · 0% AMBIGUOUS · INFERRED: 15 edges (avg confidence: 0.88)
- Token cost: 0 input · 0 output

## Community Hubs (Navigation)
- MainActivity.kt
- CameraActivity
- InitiationActivity
- .showSnackbar
- ScanResultActivity
- FloatingControlService
- AuthActivity
- ClassDetailActivity
- OverviewActivity
- FavoritesActivity.kt
- QuizActivity
- QuizEditorActivity
- .onCreate
- Activity
- TextOverlayView
- ProfileFragment
- .onCreate
- SubjectDetailActivity
- HistoryActivity.kt
- .addSpringPressEffect
- WeeklyAssessmentActivity
- DevEasterEggActivity
- HomeFragment
- GabAIDialogs.kt
- TeacherHomeFragment
- .onCreate
- GabAIApp.kt
- ExampleInstrumentedTest.kt
- Earthquakes: Movement of the Earth's Crust
- ExampleUnitTest.kt
- ic_launcher-playstore (main)
- ic_search_bubble (drawable)
- serve.py
- StudentIntroActivity.kt
- .applyHardwareMaxRefreshRate
- Activity
- HelpActivity.kt
- GabAIAccessibilityService.kt
- GabAiLoadingView
- FirebaseAuth
- .loadLeaderboard
- StudentProfileBottomSheet.kt
- .onCreate
- .onCreate
- .onConfigurationChanged

## God Nodes (most connected - your core abstractions)
1. `QuizActivity` - 55 edges
2. `OverviewActivity` - 34 edges
3. `FloatingControlService` - 29 edges
4. `WeeklyAssessmentActivity` - 25 edges
5. `GabAIUtils` - 24 edges
6. `MainActivity` - 23 edges
7. `StudentIntroActivity` - 23 edges
8. `HomeFragment` - 22 edges
9. `TextOverlayView` - 22 edges
10. `AuthActivity` - 21 edges

## Surprising Connections (you probably didn't know these)
- `2. Engineering & Design System Standards` --references--> `GabAIDialogs`  [INFERRED]
  AGENTS.md → app/src/main/java/com/example/gabai/GabAIDialogs.kt
- `2. Engineering & Design System Standards` --references--> `GabAIDialogs`  [INFERRED]
  GEMINI.md → app/src/main/java/com/example/gabai/GabAIDialogs.kt
- `2. Engineering & Design System Standards` --references--> `FastBlurView`  [INFERRED]
  AGENTS.md → app/src/main/java/com/example/gabai/FastBlurView.kt
- `2. Engineering & Design System Standards` --references--> `FastBlurView`  [INFERRED]
  GEMINI.md → app/src/main/java/com/example/gabai/FastBlurView.kt
- `DevEasterEggActivity` --references--> `GabAiLoadingView`  [EXTRACTED]
  app/src/main/java/com/example/gabai/DevEasterEggActivity.kt → app/src/main/java/com/example/gabai/GabAiLoadingView.kt

## Import Cycles
- None detected.

## Communities (57 total, 38 thin omitted)

### Community 0 - "MainActivity.kt"
Cohesion: 0.09
Nodes (3): MainActivity, OnBackPressedCallback, ViewOutlineProvider

### Community 2 - "InitiationActivity"
Cohesion: 0.11
Nodes (4): GeneratedQuestion, InitiationActivity, InitiationMaterial, MaterialQuizActivity

### Community 4 - "ScanResultActivity"
Cohesion: 0.07
Nodes (8): TextSpanClassification, TextSpanType, PHRASE, SENTENCE, WORD, ScanImageHolder, ScanResultActivity, OnTouchListener

### Community 6 - "AuthActivity"
Cohesion: 0.09
Nodes (5): AuthActivity, TakeScreenshotCallback, School, SchoolAdminInvite, SchoolRepository

### Community 8 - "OverviewActivity"
Cohesion: 0.05
Nodes (12): ApprenticeQuestHelper, QuestMeta, OverviewActivity, UtteranceProgressListener, WebViewClient, TtsPlaybackState, IDLE, LOADING (+4 more)

### Community 9 - "FavoritesActivity.kt"
Cohesion: 0.12
Nodes (6): FavoriteItem, FavoritesActivity, OnScrollListener, FavoritesAdapter, ItemViewHolder, LoadingViewHolder

### Community 10 - "QuizActivity"
Cohesion: 0.06
Nodes (10): AchievementManager, MechanicType, CLOZE, CONTEXT_MEANING, GRAMMAR_LANDMINE, IMPOSTOR, RAPID_MATCH, QuizActivity (+2 more)

### Community 13 - "Activity"
Cohesion: 0.10
Nodes (8): GitHubUpdateHelper, Callback, Callback, Callback, Callback, ActivityLifecycleCallbacks, VersionInfo, SplashActivity

### Community 14 - "TextOverlayView"
Cohesion: 0.09
Nodes (7): DragMode, DRAG_END_HANDLE, DRAG_START_HANDLE, NONE, SELECTION, TextOverlayView, WordBox

### Community 18 - "HistoryActivity.kt"
Cohesion: 0.12
Nodes (6): HistoryActivity, OnScrollListener, HistoryAdapter, HistoryItem, ItemViewHolder, LoadingViewHolder

### Community 20 - "WeeklyAssessmentActivity"
Cohesion: 0.13
Nodes (3): AssessmentQuestion, WeeklyAssessmentActivity, OnBackPressedCallback

### Community 23 - "GabAIDialogs.kt"
Cohesion: 0.06
Nodes (10): 1. Always Use Graphify (Token Efficiency & Navigation), 2. Engineering & Design System Standards, 3. Build & Testing Protocols, Project Rules: OSRnary (GabAI), FastBlurTarget, FastBlurView, 1. Always Use Graphify (Token Efficiency & Navigation), 2. Engineering & Design System Standards (+2 more)

### Community 25 - ".onCreate"
Cohesion: 0.15
Nodes (4): AchievementsActivity, Badge, DailyQuestsActivity, QuestManager

### Community 29 - "Earthquakes: Movement of the Earth's Crust"
Cohesion: 1.00
Nodes (3): Earthquakes: Movement of the Earth's Crust, Seismic Waves, Tectonic Plates and Fault Lines

### Community 36 - "StudentIntroActivity.kt"
Cohesion: 0.08
Nodes (6): IntroSlideAdapter, IntroSlideViewHolder, SampleWordData, SlideTheme, StudentIntroActivity, OnPageChangeCallback

### Community 38 - ".applyHardwareMaxRefreshRate"
Cohesion: 0.14
Nodes (4): QuizHistoryActivity, StudentSubjectActivity, StudentStats, TeacherPerformanceActivity

### Community 39 - "Activity"
Cohesion: 0.14
Nodes (3): OfflineLockoutHelper, ActivityLifecycleCallbacks, NetworkCallback

### Community 40 - "HelpActivity.kt"
Cohesion: 0.17
Nodes (4): GuideViewHolder, HelpActivity, HelpGuide, HelpGuidesAdapter

### Community 42 - "GabAiLoadingView"
Cohesion: 0.08
Nodes (3): GabAiAnimatedLogoView, AnimatorListenerAdapter, GabAiLoadingView

## Knowledge Gaps
- **18 isolated node(s):** `WORD`, `PHRASE`, `SENTENCE`, `IDLE`, `LOADING` (+13 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 172 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **38 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `OverviewActivity` connect `OverviewActivity` to `OverviewActivity.kt`, `HomeFragment`?**
  _High betweenness centrality (0.035) - this node is a cross-community bridge._
- **What connects `WORD`, `PHRASE`, `SENTENCE` to the rest of the system?**
  _18 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `MainActivity.kt` be split into smaller, more focused modules?**
  _Cohesion score 0.08677098150782361 - nodes in this community are weakly interconnected._
- **Why does `AuthActivity` connect `AuthActivity` to `FirebaseAuth`?**
  _High betweenness centrality (0.035) - this node is a cross-community bridge._
- **Should `CameraActivity` be split into smaller, more focused modules?**
  _Cohesion score 0.14210526315789473 - nodes in this community are weakly interconnected._
- **Should `InitiationActivity` be split into smaller, more focused modules?**
  _Cohesion score 0.11363636363636363 - nodes in this community are weakly interconnected._
- **Should `ScanResultActivity` be split into smaller, more focused modules?**
  _Cohesion score 0.0696969696969697 - nodes in this community are weakly interconnected._