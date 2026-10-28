# Graph Report - OSRnary  (2026-10-29)

## Corpus Check
- 78 files · ~240,447 words
- Verdict: corpus is large enough that graph structure adds value.
- Unclassified: 307 file(s) not represented in the graph (top: .xml 278, .ttf 7, (none) 5)

## Summary
- 1589 nodes · 4446 edges · 69 communities (28 shown, 41 thin omitted)
- Extraction: 98% EXTRACTED · 2% INFERRED · 0% AMBIGUOUS · INFERRED: 69 edges (avg confidence: 0.86)
- Token cost: 0 input · 0 output

## Community Hubs (Navigation)
- MainActivity.kt
- CameraActivity.kt
- InitiationActivity
- GabAIUtils
- ScanResultActivity
- AuthActivity
- ClassDetailActivity
- OverviewActivity
- 2. The 5 Core System Engines (How It Actually Works)
- QuizActivity
- QuizEditorActivity
- GabAIAccessibilityService.kt
- Activity
- TextOverlayView
- motion-graphics-backup/gabai-motion.js
- GabAiLoadingView
- SubjectDetailActivity
- HistoryActivity
- FloatingControlService
- WeeklyAssessmentActivity
- TimedAssessmentActivity
- HomeFragment
- GabAIDialogs.kt
- .addProgress
- .loadAssignedMaterials
- ExampleInstrumentedTest.kt
- motion-graphics/gabai-motion.js
- ExampleUnitTest.kt
- ClassDetailActivity.kt
- ic_launcher-playstore (main)
- ic_search_bubble (drawable)
- serve.py
- StudentIntroActivity.kt
- .loadQuizHistory
- GabAIApp.kt
- HelpGuidesAdapter
- .showConfirmDialog
- AnnouncementRepository
- Quad
- .showSnackbar
- LibraryQuizOverviewActivity
- ProfileFragment
- .loadBadges
- .onCreate
- .showGlobalLoading
- TextWatcher
- TakeScreenshotCallback
- FavoritesActivity
- .performHaptic
- .animateCascade
- .loadPerformanceData
- TextWatcher
- .onCreate
- 3. The 6 Critical Loopholes: Status & Resolution
- TextWatcher
- .loadLeaderboard
- .onCreate
- .onStartCommand

## God Nodes (most connected - your core abstractions)
1. `QuizActivity` - 58 edges
2. `TimedAssessmentActivity` - 34 edges
3. `FloatingControlService` - 29 edges
4. `ClassDetailActivity` - 27 edges
5. `AuthActivity` - 26 edges
6. `HomeFragment` - 26 edges
7. `MainActivity` - 26 edges
8. `rgba()` - 26 edges
9. `WeeklyAssessmentActivity` - 25 edges
10. `rgba()` - 25 edges

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

## Communities (69 total, 41 thin omitted)

### Community 0 - "MainActivity.kt"
Cohesion: 0.09
Nodes (3): MainActivity, OnBackPressedCallback, ViewOutlineProvider

### Community 2 - "InitiationActivity"
Cohesion: 0.17
Nodes (4): GeneratedQuestion, InitiationActivity, OnBackPressedCallback, InitiationMaterial

### Community 3 - "GabAIUtils"
Cohesion: 0.17
Nodes (6): GabAIUtils, TextSpanClassification, TextSpanType, PHRASE, SENTENCE, WORD

### Community 4 - "ScanResultActivity"
Cohesion: 0.09
Nodes (3): ScanImageHolder, ScanResultActivity, OnTouchListener

### Community 6 - "AuthActivity"
Cohesion: 0.09
Nodes (6): ArchiveRecord, ArchiveVaultRepository, AuthActivity, School, SchoolAdminInvite, SchoolRepository

### Community 8 - "OverviewActivity"
Cohesion: 0.07
Nodes (11): ApprenticeQuestHelper, QuestMeta, OverviewActivity, UtteranceProgressListener, TtsPlaybackState, IDLE, LOADING, PAUSED (+3 more)

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

### Community 16 - "GabAiLoadingView"
Cohesion: 0.06
Nodes (5): DevEasterEggActivity, OnSeekBarChangeListener, GabAiAnimatedLogoView, AnimatorListenerAdapter, GabAiLoadingView

### Community 17 - "SubjectDetailActivity"
Cohesion: 0.15
Nodes (3): AIContentValidator, AIValidationResult, SubjectDetailActivity

### Community 18 - "HistoryActivity"
Cohesion: 0.11
Nodes (6): HistoryActivity, OnScrollListener, HistoryAdapter, HistoryItem, ItemViewHolder, LoadingViewHolder

### Community 20 - "WeeklyAssessmentActivity"
Cohesion: 0.13
Nodes (3): AssessmentQuestion, WeeklyAssessmentActivity, OnBackPressedCallback

### Community 21 - "TimedAssessmentActivity"
Cohesion: 0.09
Nodes (4): TimedAssessmentActivity, OnBackPressedCallback, CountDownTimer, TimedQuestion

### Community 22 - "HomeFragment"
Cohesion: 0.07
Nodes (4): HomeFragment, MaterialQuizActivity, ProgressDashboardActivity, XPManager

### Community 23 - "GabAIDialogs.kt"
Cohesion: 0.07
Nodes (10): 1. Always Use Graphify (Token Efficiency & Navigation), 2. Engineering & Design System Standards, 3. Build & Testing Protocols, Project Rules: OSRnary (GabAI), FastBlurTarget, FastBlurView, 1. Always Use Graphify (Token Efficiency & Navigation), 2. Engineering & Design System Standards (+2 more)

### Community 24 - ".addProgress"
Cohesion: 0.17
Nodes (3): DailyQuestsActivity, DailyQuestMeta, QuestManager

### Community 29 - "motion-graphics/gabai-motion.js"
Cohesion: 0.13
Nodes (59): at(), avatar(), caret(), checkBadge(), chipRow(), codeLine(), confetti(), drawArticle() (+51 more)

### Community 35 - "serve.py"
Cohesion: 0.21
Nodes (3): DualStackHandler, run(), find_service_account()

### Community 36 - "StudentIntroActivity.kt"
Cohesion: 0.10
Nodes (5): IntroSlideAdapter, SampleWordData, SlideTheme, StudentIntroActivity, OnPageChangeCallback

### Community 39 - "GabAIApp.kt"
Cohesion: 0.09
Nodes (5): GabAIApp, NetworkCallback, OfflineLockoutHelper, ActivityLifecycleCallbacks, NetworkCallback

### Community 40 - "HelpGuidesAdapter"
Cohesion: 0.16
Nodes (4): GuideViewHolder, HelpActivity, HelpGuide, HelpGuidesAdapter

### Community 42 - "AnnouncementRepository"
Cohesion: 0.21
Nodes (3): Announcement, AnnouncementViewReceipt, AnnouncementRepository

### Community 46 - "LibraryQuizOverviewActivity"
Cohesion: 0.22
Nodes (4): ClassModel, LibraryQuizOverviewActivity, QuestionModel, StudentSubmissionState

### Community 56 - "FavoritesActivity"
Cohesion: 0.11
Nodes (6): FavoriteItem, FavoritesActivity, OnScrollListener, FavoritesAdapter, ItemViewHolder, LoadingViewHolder

### Community 67 - "3. The 6 Critical Loopholes: Status & Resolution"
Cohesion: 0.25
Nodes (7): 3. The 6 Critical Loopholes: Status & Resolution, Loophole 1: Students Creating Database Classrooms, Loophole 2: Adviser vs Subject Teacher Student Visibility, Loophole 3: Subject Teacher Linking to an Advisory Section, Loophole 4: Unwanted Floating Bubble on Quiz Exit, Loophole 5: Profile Loading Flicker & Mastered Words, Loophole 6: Quest Initiation vs Library Material Separation

## Knowledge Gaps
- **18 isolated node(s):** `WORD`, `PHRASE`, `SENTENCE`, `IDLE`, `LOADING` (+13 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 214 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **41 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `QuizActivity` connect `QuizActivity` to `3. The 6 Critical Loopholes: Status & Resolution`, `ClassDetailActivity.kt`?**
  _High betweenness centrality (0.041) - this node is a cross-community bridge._
- **What connects `WORD`, `PHRASE`, `SENTENCE` to the rest of the system?**
  _18 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `MainActivity.kt` be split into smaller, more focused modules?**
  _Cohesion score 0.09041835357624832 - nodes in this community are weakly interconnected._
- **Why does `TextOverlayView` connect `TextOverlayView` to `ScanResultActivity`?**
  _High betweenness centrality (0.031) - this node is a cross-community bridge._
- **Should `CameraActivity.kt` be split into smaller, more focused modules?**
  _Cohesion score 0.1168091168091168 - nodes in this community are weakly interconnected._
- **Should `ScanResultActivity` be split into smaller, more focused modules?**
  _Cohesion score 0.08677098150782361 - nodes in this community are weakly interconnected._
- **Should `.addSpringPressEffect` be split into smaller, more focused modules?**
  _Cohesion score 0.1021021021021021 - nodes in this community are weakly interconnected._