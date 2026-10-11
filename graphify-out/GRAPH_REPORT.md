# Graph Report - OSRnary  (2026-10-12)

## Corpus Check
- 67 files · ~169,739 words
- Verdict: corpus is large enough that graph structure adds value.
- Unclassified: 290 file(s) not represented in the graph (top: .xml 265, .ttf 7, (none) 5)

## Summary
- 1289 nodes · 3347 edges · 59 communities (17 shown, 42 thin omitted)
- Extraction: 100% EXTRACTED · 0% INFERRED · 0% AMBIGUOUS · INFERRED: 14 edges (avg confidence: 0.88)
- Token cost: 0 input · 0 output

## Community Hubs (Navigation)
- MainActivity.kt
- CameraActivity.kt
- InitiationActivity
- .showSnackbar
- ScanResultActivity
- ProfileFragment
- AuthActivity
- ClassDetailActivity
- OverviewActivity
- FavoritesActivity
- QuizActivity
- QuizEditorActivity
- .onCreate
- Activity
- TextOverlayView
- GabAiAnimatedLogoView.kt
- HistoryActivity
- SubjectDetailActivity
- HistoryAdapter
- .addSpringPressEffect
- WeeklyAssessmentActivity
- DevEasterEggActivity
- HomeFragment
- GabAIDialogs.kt
- .onStartCommand
- TeacherHomeFragment
- GabAIApp.kt
- ExampleInstrumentedTest.kt
- .onCreate
- ExampleUnitTest.kt
- TextWatcher
- ic_launcher-playstore (main)
- ic_search_bubble (drawable)
- serve.py
- .performHaptic
- GabAIUtils
- Activity
- .onCreate
- GabAIAccessibilityService.kt
- GabAiLoadingView
- .onCreate
- TextWatcher
- .loadBadges
- StudentProfileBottomSheet.kt
- TextWatcher
- .loadAssignedMaterials
- TextWatcher
- .onCreate
- FloatingControlService

## God Nodes (most connected - your core abstractions)
1. `QuizActivity` - 56 edges
2. `OverviewActivity` - 34 edges
3. `FloatingControlService` - 27 edges
4. `ClassDetailActivity` - 25 edges
5. `WeeklyAssessmentActivity` - 25 edges
6. `GabAIUtils` - 24 edges
7. `MainActivity` - 23 edges
8. `StudentIntroActivity` - 23 edges
9. `AuthActivity` - 22 edges
10. `HomeFragment` - 22 edges

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

## Communities (59 total, 42 thin omitted)

### Community 0 - "MainActivity.kt"
Cohesion: 0.09
Nodes (3): MainActivity, OnBackPressedCallback, ViewOutlineProvider

### Community 2 - "InitiationActivity"
Cohesion: 0.10
Nodes (5): GeneratedQuestion, InitiationActivity, OnBackPressedCallback, InitiationMaterial, MaterialQuizActivity

### Community 4 - "ScanResultActivity"
Cohesion: 0.07
Nodes (8): TextSpanClassification, TextSpanType, PHRASE, SENTENCE, WORD, ScanImageHolder, ScanResultActivity, OnTouchListener

### Community 5 - "ProfileFragment"
Cohesion: 0.09
Nodes (3): DailyQuestsActivity, ProfileFragment, QuestManager

### Community 6 - "AuthActivity"
Cohesion: 0.09
Nodes (5): AuthActivity, TakeScreenshotCallback, School, SchoolAdminInvite, SchoolRepository

### Community 8 - "OverviewActivity"
Cohesion: 0.05
Nodes (12): ApprenticeQuestHelper, QuestMeta, OverviewActivity, UtteranceProgressListener, WebViewClient, TtsPlaybackState, IDLE, LOADING (+4 more)

### Community 9 - "FavoritesActivity"
Cohesion: 0.11
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

### Community 18 - "HistoryAdapter"
Cohesion: 0.20
Nodes (3): HistoryAdapter, ItemViewHolder, LoadingViewHolder

### Community 20 - "WeeklyAssessmentActivity"
Cohesion: 0.13
Nodes (3): AssessmentQuestion, WeeklyAssessmentActivity, OnBackPressedCallback

### Community 23 - "GabAIDialogs.kt"
Cohesion: 0.06
Nodes (10): 1. Always Use Graphify (Token Efficiency & Navigation), 2. Engineering & Design System Standards, 3. Build & Testing Protocols, Project Rules: OSRnary (GabAI), FastBlurTarget, FastBlurView, 1. Always Use Graphify (Token Efficiency & Navigation), 2. Engineering & Design System Standards (+2 more)

### Community 36 - ".performHaptic"
Cohesion: 0.07
Nodes (7): QuestDetailsActivity, IntroSlideAdapter, IntroSlideViewHolder, SampleWordData, SlideTheme, StudentIntroActivity, OnPageChangeCallback

### Community 38 - "GabAIUtils"
Cohesion: 0.07
Nodes (6): GabAIUtils, LeaderboardActivity, ProgressDashboardActivity, StudentSubjectActivity, StudentStats, TeacherPerformanceActivity

### Community 39 - "Activity"
Cohesion: 0.14
Nodes (3): OfflineLockoutHelper, ActivityLifecycleCallbacks, NetworkCallback

### Community 40 - ".onCreate"
Cohesion: 0.14
Nodes (4): GuideViewHolder, HelpActivity, HelpGuide, HelpGuidesAdapter

## Knowledge Gaps
- **18 isolated node(s):** `WORD`, `PHRASE`, `SENTENCE`, `IDLE`, `LOADING` (+13 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 185 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **42 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `QuizActivity` connect `QuizActivity` to `HomeFragment`?**
  _High betweenness centrality (0.071) - this node is a cross-community bridge._
- **What connects `WORD`, `PHRASE`, `SENTENCE` to the rest of the system?**
  _18 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `MainActivity.kt` be split into smaller, more focused modules?**
  _Cohesion score 0.09009009009009009 - nodes in this community are weakly interconnected._
- **Why does `AuthActivity` connect `AuthActivity` to `OverviewActivity.kt`?**
  _High betweenness centrality (0.036) - this node is a cross-community bridge._
- **Should `CameraActivity.kt` be split into smaller, more focused modules?**
  _Cohesion score 0.1111111111111111 - nodes in this community are weakly interconnected._
- **Why does `GabAiLoadingView` connect `GabAiLoadingView` to `.showSnackbar`, `FavoritesActivity`, `HistoryActivity`, `HistoryAdapter`, `DevEasterEggActivity`?**
  _High betweenness centrality (0.035) - this node is a cross-community bridge._
- **Should `InitiationActivity` be split into smaller, more focused modules?**
  _Cohesion score 0.1021021021021021 - nodes in this community are weakly interconnected._