# Graph Report - OSRnary  (2026-10-15)

## Corpus Check
- 72 files · ~198,652 words
- Verdict: corpus is large enough that graph structure adds value.
- Unclassified: 299 file(s) not represented in the graph (top: .xml 270, .ttf 7, (none) 5)

## Summary
- 1458 nodes · 4005 edges · 59 communities (25 shown, 34 thin omitted)
- Extraction: 99% EXTRACTED · 1% INFERRED · 0% AMBIGUOUS · INFERRED: 57 edges (avg confidence: 0.87)
- Token cost: 0 input · 0 output

## Community Hubs (Navigation)
- MainActivity
- CameraActivity.kt
- InitiationActivity
- TeacherHomeFragment
- ScanResultActivity
- ManageClassesActivity
- AuthActivity
- ClassDetailActivity
- OverviewActivity
- 2. The 5 Core System Engines (How It Actually Works)
- QuizActivity
- .showSnackbar
- .addSpringPressEffect
- Activity
- TextOverlayView
- motion-graphics-backup/gabai-motion.js
- DevEasterEggActivity
- SubjectDetailActivity
- HistoryActivity
- FloatingControlService
- WeeklyAssessmentActivity
- MaterialQuizActivity
- HomeFragment
- GabAIDialogs.kt
- .onStartCommand
- .loadAssignedMaterials
- .loadPerformanceData
- ExampleInstrumentedTest.kt
- motion-graphics/gabai-motion.js
- ExampleUnitTest.kt
- ic_launcher-playstore (main)
- ic_search_bubble (drawable)
- serve.py
- .performHaptic
- .onCreate
- GabAIApp.kt
- .onCreate
- GabAiAnimatedLogoView.kt
- GabAiLoadingView
- StudentProfileBottomSheet.kt
- .onCreate
- DailyQuestsActivity.kt
- ProfileFragment
- TextWatcher
- .onTouch
- TextWatcher
- GabAIUtils
- FavoritesAdapter
- FavoritesActivity
- 3. The 6 Critical Loopholes: Status & Resolution
- .onCreate

## God Nodes (most connected - your core abstractions)
1. `QuizActivity` - 58 edges
2. `FloatingControlService` - 29 edges
3. `ClassDetailActivity` - 27 edges
4. `AuthActivity` - 26 edges
5. `MainActivity` - 26 edges
6. `rgba()` - 26 edges
7. `WeeklyAssessmentActivity` - 25 edges
8. `rgba()` - 25 edges
9. `GabAIUtils` - 24 edges
10. `StudentIntroActivity` - 23 edges

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

## Communities (59 total, 34 thin omitted)

### Community 0 - "MainActivity"
Cohesion: 0.11
Nodes (3): MainActivity, OnBackPressedCallback, ViewOutlineProvider

### Community 1 - "CameraActivity.kt"
Cohesion: 0.07
Nodes (3): CameraActivity, OnImageSavedCallback, GabAIAccessibilityService

### Community 2 - "InitiationActivity"
Cohesion: 0.17
Nodes (4): GeneratedQuestion, InitiationActivity, OnBackPressedCallback, InitiationMaterial

### Community 4 - "ScanResultActivity"
Cohesion: 0.09
Nodes (3): ScanImageHolder, ScanResultActivity, OnTouchListener

### Community 6 - "AuthActivity"
Cohesion: 0.13
Nodes (5): AuthActivity, TakeScreenshotCallback, School, SchoolAdminInvite, SchoolRepository

### Community 8 - "OverviewActivity"
Cohesion: 0.06
Nodes (15): ApprenticeQuestHelper, QuestMeta, TextSpanType, PHRASE, SENTENCE, WORD, OverviewActivity, UtteranceProgressListener (+7 more)

### Community 9 - "2. The 5 Core System Engines (How It Actually Works)"
Cohesion: 0.15
Nodes (12): 1. The Core Persona & Responsibility Boundary, 2. The 5 Core System Engines (How It Actually Works), 4. Clear Roadmap for What to Do Next, Engine 1: School Onboarding & Classroom Creation, Engine 2: Student Account Lifecycle & Enrollment, Engine 3: The Dual Reading Pipeline (Diagnostic vs Library), Engine 4: Gamification, Vocabulary Recall & Combo Streak Engine, Engine 5: Teacher Material Assignment & Performance Roster (+4 more)

### Community 10 - "QuizActivity"
Cohesion: 0.06
Nodes (10): AchievementManager, MechanicType, CLOZE, CONTEXT_MEANING, GRAMMAR_LANDMINE, IMPOSTOR, RAPID_MATCH, QuizActivity (+2 more)

### Community 13 - "Activity"
Cohesion: 0.09
Nodes (8): GitHubUpdateHelper, Callback, Callback, Callback, Callback, ActivityLifecycleCallbacks, VersionInfo, SplashActivity

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

### Community 23 - "GabAIDialogs.kt"
Cohesion: 0.07
Nodes (10): 1. Always Use Graphify (Token Efficiency & Navigation), 2. Engineering & Design System Standards, 3. Build & Testing Protocols, Project Rules: OSRnary (GabAI), FastBlurTarget, FastBlurView, 1. Always Use Graphify (Token Efficiency & Navigation), 2. Engineering & Design System Standards (+2 more)

### Community 26 - ".loadPerformanceData"
Cohesion: 0.21
Nodes (3): LeaderboardActivity, StudentStats, TeacherPerformanceActivity

### Community 29 - "motion-graphics/gabai-motion.js"
Cohesion: 0.13
Nodes (59): at(), avatar(), caret(), checkBadge(), chipRow(), codeLine(), confetti(), drawArticle() (+51 more)

### Community 35 - "serve.py"
Cohesion: 0.21
Nodes (3): DualStackHandler, run(), find_service_account()

### Community 36 - ".performHaptic"
Cohesion: 0.07
Nodes (7): QuestDetailsActivity, IntroSlideAdapter, IntroSlideViewHolder, SampleWordData, SlideTheme, StudentIntroActivity, OnPageChangeCallback

### Community 39 - "GabAIApp.kt"
Cohesion: 0.08
Nodes (5): GabAIApp, NetworkCallback, OfflineLockoutHelper, ActivityLifecycleCallbacks, NetworkCallback

### Community 40 - ".onCreate"
Cohesion: 0.14
Nodes (4): GuideViewHolder, HelpActivity, HelpGuide, HelpGuidesAdapter

### Community 46 - "DailyQuestsActivity.kt"
Cohesion: 0.15
Nodes (3): DailyQuestsActivity, DailyQuestMeta, QuestManager

### Community 55 - "GabAIUtils"
Cohesion: 0.07
Nodes (7): AchievementsActivity, Badge, GabAIUtils, TextSpanClassification, ProgressDashboardActivity, QuizHistoryActivity, StudentSubjectActivity

### Community 57 - "FavoritesAdapter"
Cohesion: 0.22
Nodes (3): FavoritesAdapter, ItemViewHolder, LoadingViewHolder

### Community 59 - "3. The 6 Critical Loopholes: Status & Resolution"
Cohesion: 0.25
Nodes (7): 3. The 6 Critical Loopholes: Status & Resolution, Loophole 1: Students Creating Database Classrooms, Loophole 2: Adviser vs Subject Teacher Student Visibility, Loophole 3: Subject Teacher Linking to an Advisory Section, Loophole 4: Unwanted Floating Bubble on Quiz Exit, Loophole 5: Profile Loading Flicker & Mastered Words, Loophole 6: Quest Initiation vs Library Material Separation

## Knowledge Gaps
- **18 isolated node(s):** `WORD`, `PHRASE`, `SENTENCE`, `IDLE`, `LOADING` (+13 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 200 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **34 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `QuizActivity` connect `QuizActivity` to `3. The 6 Critical Loopholes: Status & Resolution`, `HomeFragment`, `MainActivity.kt`?**
  _High betweenness centrality (0.044) - this node is a cross-community bridge._
- **Are the 2 inferred relationships involving `FloatingControlService` (e.g. with `Executive Summary: You Are Not Failing, The System Is Massive` and `Loophole 4: Unwanted Floating Bubble on Quiz Exit`) actually correct?**
  _`FloatingControlService` has 2 INFERRED edges - model-reasoned connections that need verification._
- **What connects `WORD`, `PHRASE`, `SENTENCE` to the rest of the system?**
  _18 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `MainActivity` be split into smaller, more focused modules?**
  _Cohesion score 0.10606060606060606 - nodes in this community are weakly interconnected._
- **Why does `GabAIUtils` connect `GabAIUtils` to `.performHaptic`, `ClassDetailActivity`, `OverviewActivity`, `QuizActivity`, `.showSnackbar`, `.addSpringPressEffect`, `GabAIDialogs.kt`, `MainActivity.kt`?**
  _High betweenness centrality (0.033) - this node is a cross-community bridge._
- **Should `CameraActivity.kt` be split into smaller, more focused modules?**
  _Cohesion score 0.06755260243632337 - nodes in this community are weakly interconnected._
- **Why does `FastBlurView` connect `GabAIDialogs.kt` to `2. The 5 Core System Engines (How It Actually Works)`?**
  _High betweenness centrality (0.028) - this node is a cross-community bridge._