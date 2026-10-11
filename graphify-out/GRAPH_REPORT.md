# Graph Report - OSRnary  (2026-10-12)

## Corpus Check
- 68 files · ~172,392 words
- Verdict: corpus is large enough that graph structure adds value.
- Unclassified: 290 file(s) not represented in the graph (top: .xml 265, .ttf 7, (none) 5)

## Summary
- 1311 nodes · 3394 edges · 54 communities (19 shown, 35 thin omitted)
- Extraction: 99% EXTRACTED · 1% INFERRED · 0% AMBIGUOUS · INFERRED: 20 edges (avg confidence: 0.9)
- Token cost: 0 input · 0 output

## Community Hubs (Navigation)
- MainActivity.kt
- CameraActivity.kt
- InitiationActivity
- ManageClassesActivity
- ScanResultActivity
- ProfileFragment
- AuthActivity
- ClassDetailActivity
- OverviewActivity
- 3. The 6 Critical Loopholes: Status & Resolution
- QuizActivity
- QuizEditorActivity
- .showSnackbar
- Activity
- TextOverlayView
- MaterialQuizActivity
- FloatingControlService
- SubjectDetailActivity
- HistoryActivity.kt
- .addSpringPressEffect
- WeeklyAssessmentActivity
- DevEasterEggActivity
- HomeFragment
- FastBlurView
- .onStartCommand
- TeacherHomeFragment
- .onTouch
- ExampleInstrumentedTest.kt
- .onCreate
- ExampleUnitTest.kt
- GabAIApp.kt
- ic_launcher-playstore (main)
- ic_search_bubble (drawable)
- serve.py
- .performHaptic
- Activity
- HelpActivity.kt
- GabAIAccessibilityService.kt
- FavoritesActivity.kt
- GabAIUtils
- .onCreate
- TextWatcher
- TextWatcher
- .loadAssignedMaterials
- OverviewActivity.kt
- TextWatcher

## God Nodes (most connected - your core abstractions)
1. `QuizActivity` - 56 edges
2. `OverviewActivity` - 34 edges
3. `FloatingControlService` - 29 edges
4. `AuthActivity` - 25 edges
5. `ClassDetailActivity` - 25 edges
6. `WeeklyAssessmentActivity` - 25 edges
7. `GabAIUtils` - 24 edges
8. `MainActivity` - 23 edges
9. `StudentIntroActivity` - 23 edges
10. `HomeFragment` - 22 edges

## Surprising Connections (you probably didn't know these)
- `Loophole 3: Subject Teacher Linking to an Advisory Section` --references--> `ManageClassesActivity`  [INFERRED]
  SYSTEM_WORKFLOW_AUDIT.md → app/src/main/java/com/example/gabai/ManageClassesActivity.kt
- `Next Tactical Enhancements:` --references--> `ManageClassesActivity`  [INFERRED]
  SYSTEM_WORKFLOW_AUDIT.md → app/src/main/java/com/example/gabai/ManageClassesActivity.kt
- `Executive Summary: You Are Not Failing, The System Is Massive` --references--> `FastBlurView`  [INFERRED]
  SYSTEM_WORKFLOW_AUDIT.md → app/src/main/java/com/example/gabai/FastBlurView.kt
- `Executive Summary: You Are Not Failing, The System Is Massive` --references--> `FloatingControlService`  [INFERRED]
  SYSTEM_WORKFLOW_AUDIT.md → app/src/main/java/com/example/gabai/FloatingControlService.kt
- `Loophole 4: Unwanted Floating Bubble on Quiz Exit` --references--> `FloatingControlService`  [INFERRED]
  SYSTEM_WORKFLOW_AUDIT.md → app/src/main/java/com/example/gabai/FloatingControlService.kt

## Import Cycles
- None detected.

## Communities (54 total, 35 thin omitted)

### Community 0 - "MainActivity.kt"
Cohesion: 0.09
Nodes (3): MainActivity, OnBackPressedCallback, ViewOutlineProvider

### Community 2 - "InitiationActivity"
Cohesion: 0.17
Nodes (4): GeneratedQuestion, InitiationActivity, OnBackPressedCallback, InitiationMaterial

### Community 4 - "ScanResultActivity"
Cohesion: 0.07
Nodes (8): TextSpanClassification, TextSpanType, PHRASE, SENTENCE, WORD, ScanImageHolder, ScanResultActivity, OnTouchListener

### Community 5 - "ProfileFragment"
Cohesion: 0.10
Nodes (3): DailyQuestsActivity, ProfileFragment, QuestManager

### Community 6 - "AuthActivity"
Cohesion: 0.10
Nodes (5): AuthActivity, TakeScreenshotCallback, School, SchoolAdminInvite, SchoolRepository

### Community 8 - "OverviewActivity"
Cohesion: 0.05
Nodes (12): ApprenticeQuestHelper, QuestMeta, OverviewActivity, UtteranceProgressListener, WebViewClient, TtsPlaybackState, IDLE, LOADING (+4 more)

### Community 9 - "3. The 6 Critical Loopholes: Status & Resolution"
Cohesion: 0.10
Nodes (19): 1. The Core Persona & Responsibility Boundary, 2. The 5 Core System Engines (How It Actually Works), 3. The 6 Critical Loopholes: Status & Resolution, 4. Clear Roadmap for What to Do Next, Engine 1: School Onboarding & Classroom Creation, Engine 2: Student Account Lifecycle & Enrollment, Engine 3: The Dual Reading Pipeline (Diagnostic vs Library), Engine 4: Gamification, Vocabulary Recall & Combo Streak Engine (+11 more)

### Community 10 - "QuizActivity"
Cohesion: 0.06
Nodes (10): AchievementManager, MechanicType, CLOZE, CONTEXT_MEANING, GRAMMAR_LANDMINE, IMPOSTOR, RAPID_MATCH, QuizActivity (+2 more)

### Community 13 - "Activity"
Cohesion: 0.09
Nodes (8): GitHubUpdateHelper, Callback, Callback, Callback, Callback, ActivityLifecycleCallbacks, VersionInfo, SplashActivity

### Community 14 - "TextOverlayView"
Cohesion: 0.09
Nodes (7): DragMode, DRAG_END_HANDLE, DRAG_START_HANDLE, NONE, SELECTION, TextOverlayView, WordBox

### Community 17 - "SubjectDetailActivity"
Cohesion: 0.10
Nodes (3): SubjectDetailActivity, TextWatcher, TextWatcher

### Community 18 - "HistoryActivity.kt"
Cohesion: 0.12
Nodes (6): HistoryActivity, OnScrollListener, HistoryAdapter, HistoryItem, ItemViewHolder, LoadingViewHolder

### Community 20 - "WeeklyAssessmentActivity"
Cohesion: 0.13
Nodes (3): AssessmentQuestion, WeeklyAssessmentActivity, OnBackPressedCallback

### Community 23 - "FastBlurView"
Cohesion: 0.06
Nodes (10): 1. Always Use Graphify (Token Efficiency & Navigation), 2. Engineering & Design System Standards, 3. Build & Testing Protocols, Project Rules: OSRnary (GabAI), FastBlurTarget, FastBlurView, 1. Always Use Graphify (Token Efficiency & Navigation), 2. Engineering & Design System Standards (+2 more)

### Community 36 - ".performHaptic"
Cohesion: 0.07
Nodes (7): QuestDetailsActivity, IntroSlideAdapter, IntroSlideViewHolder, SampleWordData, SlideTheme, StudentIntroActivity, OnPageChangeCallback

### Community 39 - "Activity"
Cohesion: 0.14
Nodes (3): OfflineLockoutHelper, ActivityLifecycleCallbacks, NetworkCallback

### Community 40 - "HelpActivity.kt"
Cohesion: 0.16
Nodes (4): GuideViewHolder, HelpActivity, HelpGuide, HelpGuidesAdapter

### Community 42 - "FavoritesActivity.kt"
Cohesion: 0.05
Nodes (9): FavoriteItem, FavoritesActivity, OnScrollListener, FavoritesAdapter, ItemViewHolder, LoadingViewHolder, GabAiAnimatedLogoView, AnimatorListenerAdapter (+1 more)

### Community 43 - "GabAIUtils"
Cohesion: 0.08
Nodes (7): AchievementsActivity, Badge, GabAIUtils, LeaderboardActivity, ProgressDashboardActivity, StudentStats, TeacherPerformanceActivity

## Knowledge Gaps
- **18 isolated node(s):** `WORD`, `PHRASE`, `SENTENCE`, `IDLE`, `LOADING` (+13 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 196 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **35 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `QuizActivity` connect `QuizActivity` to `3. The 6 Critical Loopholes: Status & Resolution`, `OverviewActivity.kt`?**
  _High betweenness centrality (0.069) - this node is a cross-community bridge._
- **Are the 2 inferred relationships involving `FloatingControlService` (e.g. with `Executive Summary: You Are Not Failing, The System Is Massive` and `Loophole 4: Unwanted Floating Bubble on Quiz Exit`) actually correct?**
  _`FloatingControlService` has 2 INFERRED edges - model-reasoned connections that need verification._
- **What connects `WORD`, `PHRASE`, `SENTENCE` to the rest of the system?**
  _18 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `MainActivity.kt` be split into smaller, more focused modules?**
  _Cohesion score 0.09365079365079365 - nodes in this community are weakly interconnected._
- **Why does `MainActivity` connect `MainActivity.kt` to `OverviewActivity`, `.performHaptic`, `.showSnackbar`?**
  _High betweenness centrality (0.046) - this node is a cross-community bridge._
- **Should `CameraActivity.kt` be split into smaller, more focused modules?**
  _Cohesion score 0.1111111111111111 - nodes in this community are weakly interconnected._
- **Why does `FastBlurView` connect `FastBlurView` to `3. The 6 Critical Loopholes: Status & Resolution`, `GabAIUtils`?**
  _High betweenness centrality (0.037) - this node is a cross-community bridge._