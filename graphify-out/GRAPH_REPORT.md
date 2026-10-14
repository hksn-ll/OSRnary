# Graph Report - OSRnary  (2026-10-14)

## Corpus Check
- 72 files · ~216,180 words
- Verdict: corpus is large enough that graph structure adds value.
- Unclassified: 295 file(s) not represented in the graph (top: .xml 266, .ttf 7, (none) 5)

## Summary
- 1456 nodes · 3977 edges · 63 communities (25 shown, 38 thin omitted)
- Extraction: 99% EXTRACTED · 1% INFERRED · 0% AMBIGUOUS · INFERRED: 56 edges (avg confidence: 0.87)
- Token cost: 0 input · 0 output

## Community Hubs (Navigation)
- MainActivity
- CameraActivity.kt
- InitiationActivity
- ScanResultActivity
- .showSnackbar
- AuthActivity
- ClassDetailActivity
- OverviewActivity
- 2. The 5 Core System Engines (How It Actually Works)
- QuizActivity
- QuizEditorActivity
- .addSpringPressEffect
- Activity
- TextOverlayView
- motion-graphics-backup/gabai-motion.js
- SubjectDetailActivity
- HistoryActivity
- FloatingControlService
- WeeklyAssessmentActivity
- HomeFragment
- FastBlurView
- .onStartCommand
- .loadPerformanceData
- ExampleInstrumentedTest.kt
- motion-graphics/gabai-motion.js
- ExampleUnitTest.kt
- ManageClassesActivity
- ic_launcher-playstore (main)
- ic_search_bubble (drawable)
- serve.py
- .performHaptic
- .onCreate
- Activity
- .onCreate
- GabAIAccessibilityService.kt
- FavoritesActivity
- GabAIUtils
- TextWatcher
- .addProgress
- ProfileFragment
- TextWatcher
- GabAiLoadingView
- .onCreate
- QuizHistoryActivity.kt
- StudentProfileBottomSheet.kt
- 3. The 6 Critical Loopholes: Status & Resolution
- ViewOutlineProvider
- .loadAssignedMaterials

## God Nodes (most connected - your core abstractions)
1. `QuizActivity` - 56 edges
2. `OverviewActivity` - 34 edges
3. `FloatingControlService` - 29 edges
4. `ClassDetailActivity` - 27 edges
5. `rgba()` - 26 edges
6. `AuthActivity` - 25 edges
7. `WeeklyAssessmentActivity` - 25 edges
8. `rgba()` - 25 edges
9. `GabAIUtils` - 24 edges
10. `MainActivity` - 23 edges

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

## Communities (63 total, 38 thin omitted)

### Community 2 - "InitiationActivity"
Cohesion: 0.17
Nodes (4): GeneratedQuestion, InitiationActivity, OnBackPressedCallback, InitiationMaterial

### Community 4 - "ScanResultActivity"
Cohesion: 0.07
Nodes (8): TextSpanClassification, TextSpanType, PHRASE, SENTENCE, WORD, ScanImageHolder, ScanResultActivity, OnTouchListener

### Community 6 - "AuthActivity"
Cohesion: 0.10
Nodes (5): AuthActivity, TakeScreenshotCallback, School, SchoolAdminInvite, SchoolRepository

### Community 8 - "OverviewActivity"
Cohesion: 0.05
Nodes (12): ApprenticeQuestHelper, QuestMeta, OverviewActivity, UtteranceProgressListener, WebViewClient, TtsPlaybackState, IDLE, LOADING (+4 more)

### Community 9 - "2. The 5 Core System Engines (How It Actually Works)"
Cohesion: 0.15
Nodes (12): 1. The Core Persona & Responsibility Boundary, 2. The 5 Core System Engines (How It Actually Works), 4. Clear Roadmap for What to Do Next, Engine 1: School Onboarding & Classroom Creation, Engine 2: Student Account Lifecycle & Enrollment, Engine 3: The Dual Reading Pipeline (Diagnostic vs Library), Engine 4: Gamification, Vocabulary Recall & Combo Streak Engine, Engine 5: Teacher Material Assignment & Performance Roster (+4 more)

### Community 10 - "QuizActivity"
Cohesion: 0.06
Nodes (10): AchievementManager, MechanicType, CLOZE, CONTEXT_MEANING, GRAMMAR_LANDMINE, IMPOSTOR, RAPID_MATCH, QuizActivity (+2 more)

### Community 13 - "Activity"
Cohesion: 0.07
Nodes (9): GabAIApp, GitHubUpdateHelper, Callback, Callback, Callback, Callback, ActivityLifecycleCallbacks, VersionInfo (+1 more)

### Community 14 - "TextOverlayView"
Cohesion: 0.09
Nodes (7): DragMode, DRAG_END_HANDLE, DRAG_START_HANDLE, NONE, SELECTION, TextOverlayView, WordBox

### Community 15 - "motion-graphics-backup/gabai-motion.js"
Cohesion: 0.14
Nodes (54): at(), avatar(), caret(), checkBadge(), chipRow(), codeLine(), confetti(), drawArticle() (+46 more)

### Community 17 - "SubjectDetailActivity"
Cohesion: 0.08
Nodes (5): AIContentValidator, AIValidationResult, SubjectDetailActivity, TextWatcher, TextWatcher

### Community 18 - "HistoryActivity"
Cohesion: 0.11
Nodes (6): HistoryActivity, OnScrollListener, HistoryAdapter, HistoryItem, ItemViewHolder, LoadingViewHolder

### Community 20 - "WeeklyAssessmentActivity"
Cohesion: 0.13
Nodes (3): AssessmentQuestion, WeeklyAssessmentActivity, OnBackPressedCallback

### Community 22 - "HomeFragment"
Cohesion: 0.08
Nodes (3): HomeFragment, MaterialQuizActivity, XPManager

### Community 23 - "FastBlurView"
Cohesion: 0.09
Nodes (10): 1. Always Use Graphify (Token Efficiency & Navigation), 2. Engineering & Design System Standards, 3. Build & Testing Protocols, Project Rules: OSRnary (GabAI), FastBlurTarget, FastBlurView, 1. Always Use Graphify (Token Efficiency & Navigation), 2. Engineering & Design System Standards (+2 more)

### Community 26 - ".loadPerformanceData"
Cohesion: 0.20
Nodes (3): LeaderboardActivity, StudentStats, TeacherPerformanceActivity

### Community 29 - "motion-graphics/gabai-motion.js"
Cohesion: 0.12
Nodes (59): at(), avatar(), caret(), checkBadge(), chipRow(), codeLine(), confetti(), drawArticle() (+51 more)

### Community 35 - "serve.py"
Cohesion: 0.21
Nodes (3): DualStackHandler, run(), find_service_account()

### Community 36 - ".performHaptic"
Cohesion: 0.07
Nodes (7): QuestDetailsActivity, IntroSlideAdapter, IntroSlideViewHolder, SampleWordData, SlideTheme, StudentIntroActivity, OnPageChangeCallback

### Community 39 - "Activity"
Cohesion: 0.14
Nodes (3): OfflineLockoutHelper, ActivityLifecycleCallbacks, NetworkCallback

### Community 40 - ".onCreate"
Cohesion: 0.14
Nodes (4): GuideViewHolder, HelpActivity, HelpGuide, HelpGuidesAdapter

### Community 42 - "FavoritesActivity"
Cohesion: 0.11
Nodes (6): FavoriteItem, FavoritesActivity, OnScrollListener, FavoritesAdapter, ItemViewHolder, LoadingViewHolder

### Community 43 - "GabAIUtils"
Cohesion: 0.09
Nodes (5): AchievementsActivity, Badge, GabAIUtils, ProgressDashboardActivity, StudentSubjectActivity

### Community 46 - ".addProgress"
Cohesion: 0.17
Nodes (3): DailyQuestsActivity, DailyQuestMeta, QuestManager

### Community 52 - "GabAiLoadingView"
Cohesion: 0.06
Nodes (5): DevEasterEggActivity, OnSeekBarChangeListener, GabAiAnimatedLogoView, AnimatorListenerAdapter, GabAiLoadingView

### Community 60 - "3. The 6 Critical Loopholes: Status & Resolution"
Cohesion: 0.25
Nodes (7): 3. The 6 Critical Loopholes: Status & Resolution, Loophole 1: Students Creating Database Classrooms, Loophole 2: Adviser vs Subject Teacher Student Visibility, Loophole 3: Subject Teacher Linking to an Advisory Section, Loophole 4: Unwanted Floating Bubble on Quiz Exit, Loophole 5: Profile Loading Flicker & Mastered Words, Loophole 6: Quest Initiation vs Library Material Separation

## Knowledge Gaps
- **18 isolated node(s):** `WORD`, `PHRASE`, `SENTENCE`, `IDLE`, `LOADING` (+13 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 203 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **38 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `QuizActivity` connect `QuizActivity` to `color`, `3. The 6 Critical Loopholes: Status & Resolution`, `HomeFragment`?**
  _High betweenness centrality (0.048) - this node is a cross-community bridge._
- **Are the 2 inferred relationships involving `FloatingControlService` (e.g. with `Executive Summary: You Are Not Failing, The System Is Massive` and `Loophole 4: Unwanted Floating Bubble on Quiz Exit`) actually correct?**
  _`FloatingControlService` has 2 INFERRED edges - model-reasoned connections that need verification._
- **What connects `WORD`, `PHRASE`, `SENTENCE` to the rest of the system?**
  _18 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `MainActivity` be split into smaller, more focused modules?**
  _Cohesion score 0.09206349206349207 - nodes in this community are weakly interconnected._
- **Why does `GabAIUtils` connect `GabAIUtils` to `ScanResultActivity`, `.showSnackbar`, `.performHaptic`, `ClassDetailActivity`, `OverviewActivity`, `QuizActivity`, `.addSpringPressEffect`, `GabAIUtils.kt`?**
  _High betweenness centrality (0.031) - this node is a cross-community bridge._
- **Should `CameraActivity.kt` be split into smaller, more focused modules?**
  _Cohesion score 0.12307692307692308 - nodes in this community are weakly interconnected._
- **Why does `FastBlurView` connect `FastBlurView` to `2. The 5 Core System Engines (How It Actually Works)`, `GabAIUtils`?**
  _High betweenness centrality (0.031) - this node is a cross-community bridge._