# Graph Report - OSRnary  (2026-11-08)

## Corpus Check
- 80 files · ~824,366 words
- Verdict: corpus is large enough that graph structure adds value.
- Unclassified: 311 file(s) not represented in the graph (top: .xml 282, .ttf 7, (none) 5)

## Summary
- 1621 nodes · 4517 edges · 69 communities (29 shown, 40 thin omitted)
- Extraction: 98% EXTRACTED · 2% INFERRED · 0% AMBIGUOUS · INFERRED: 69 edges (avg confidence: 0.86)
- Token cost: 0 input · 0 output

## Community Hubs (Navigation)
- QuizActivity
- motion-graphics/gabai-motion.js
- motion-graphics-backup/gabai-motion.js
- StudentIntroActivity.kt
- GabAiLoadingView
- OverviewActivity
- AuthActivity
- GabAIDialogs.kt
- Activity
- ScanResultActivity
- GabAIApp.kt
- HomeFragment
- TimedAssessmentActivity
- SubjectDetailActivity
- GabAIUtils
- ClassDetailActivity
- TextOverlayView
- MainActivity
- .addSpringPressEffect
- FavoritesActivity
- HistoryActivity
- LibraryQuizOverviewActivity.kt
- TextWatcher
- .onTouch
- WeeklyAssessmentActivity
- CameraActivity.kt
- TextWatcher
- ProfileFragment
- Quad
- FloatingControlService
- InitiationActivity
- capture_all_screens.sh
- .showGlobalLoading
- .performHaptic
- HelpGuidesAdapter
- QuizEditorActivity
- .showSnackbar
- GabAIAccessibilityService.kt
- 3. The 6 Critical Loopholes: Status & Resolution
- TeacherPerformanceActivity.kt
- TeacherHomeFragment
- 2. The 5 Core System Engines (How It Actually Works)
- generate_manual_docx.py
- .onCreate
- ExampleInstrumentedTest.kt
- .onStartCommand
- .setupBlurView
- TextWatcher
- ExampleUnitTest.kt
- .loadAssignedMaterials
- ic_launcher-playstore (main)
- ic_search_bubble (drawable)
- .applyHardwareMaxRefreshRate
- .onCreate
- TextWatcher

## God Nodes (most connected - your core abstractions)
1. `QuizActivity` - 58 edges
2. `TimedAssessmentActivity` - 33 edges
3. `ClassDetailActivity` - 29 edges
4. `FloatingControlService` - 29 edges
5. `MainActivity` - 27 edges
6. `AuthActivity` - 26 edges
7. `HomeFragment` - 26 edges
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

## Communities (69 total, 40 thin omitted)

### Community 0 - "QuizActivity"
Cohesion: 0.06
Nodes (10): AchievementManager, MechanicType, CLOZE, CONTEXT_MEANING, GRAMMAR_LANDMINE, IMPOSTOR, RAPID_MATCH, QuizActivity (+2 more)

### Community 1 - "motion-graphics/gabai-motion.js"
Cohesion: 0.13
Nodes (59): at(), avatar(), caret(), checkBadge(), chipRow(), codeLine(), confetti(), drawArticle() (+51 more)

### Community 2 - "motion-graphics-backup/gabai-motion.js"
Cohesion: 0.14
Nodes (54): at(), avatar(), caret(), checkBadge(), chipRow(), codeLine(), confetti(), drawArticle() (+46 more)

### Community 3 - "StudentIntroActivity.kt"
Cohesion: 0.08
Nodes (6): IntroSlideAdapter, IntroSlideViewHolder, SampleWordData, SlideTheme, StudentIntroActivity, OnPageChangeCallback

### Community 4 - "GabAiLoadingView"
Cohesion: 0.06
Nodes (5): DevEasterEggActivity, OnSeekBarChangeListener, GabAiAnimatedLogoView, AnimatorListenerAdapter, GabAiLoadingView

### Community 5 - "OverviewActivity"
Cohesion: 0.07
Nodes (11): ApprenticeQuestHelper, QuestMeta, OverviewActivity, UtteranceProgressListener, TtsPlaybackState, IDLE, LOADING, PAUSED (+3 more)

### Community 6 - "AuthActivity"
Cohesion: 0.09
Nodes (7): ArchiveRecord, ArchiveVaultRepository, AuthActivity, TakeScreenshotCallback, School, SchoolAdminInvite, SchoolRepository

### Community 7 - "GabAIDialogs.kt"
Cohesion: 0.07
Nodes (10): 1. Always Use Graphify (Token Efficiency & Navigation), 2. Engineering & Design System Standards, 3. Build & Testing Protocols, Project Rules: OSRnary (GabAI), FastBlurTarget, FastBlurView, 1. Always Use Graphify (Token Efficiency & Navigation), 2. Engineering & Design System Standards (+2 more)

### Community 8 - "Activity"
Cohesion: 0.09
Nodes (8): GitHubUpdateHelper, Callback, Callback, Callback, Callback, ActivityLifecycleCallbacks, VersionInfo, SplashActivity

### Community 9 - "ScanResultActivity"
Cohesion: 0.07
Nodes (8): TextSpanClassification, TextSpanType, PHRASE, SENTENCE, WORD, ScanImageHolder, ScanResultActivity, OnTouchListener

### Community 10 - "GabAIApp.kt"
Cohesion: 0.07
Nodes (5): GabAIApp, NetworkCallback, OfflineLockoutHelper, ActivityLifecycleCallbacks, NetworkCallback

### Community 11 - "HomeFragment"
Cohesion: 0.07
Nodes (4): HomeFragment, MaterialQuizActivity, ProgressDashboardActivity, XPManager

### Community 13 - "TimedAssessmentActivity"
Cohesion: 0.09
Nodes (4): TimedAssessmentActivity, OnBackPressedCallback, CountDownTimer, TimedQuestion

### Community 14 - "SubjectDetailActivity"
Cohesion: 0.15
Nodes (3): AIContentValidator, AIValidationResult, SubjectDetailActivity

### Community 17 - "TextOverlayView"
Cohesion: 0.09
Nodes (7): DragMode, DRAG_END_HANDLE, DRAG_START_HANDLE, NONE, SELECTION, TextOverlayView, WordBox

### Community 18 - "MainActivity"
Cohesion: 0.11
Nodes (3): MainActivity, OnBackPressedCallback, ViewOutlineProvider

### Community 20 - "FavoritesActivity"
Cohesion: 0.12
Nodes (6): FavoriteItem, FavoritesActivity, OnScrollListener, FavoritesAdapter, ItemViewHolder, LoadingViewHolder

### Community 21 - "HistoryActivity"
Cohesion: 0.12
Nodes (6): HistoryActivity, OnScrollListener, HistoryAdapter, HistoryItem, ItemViewHolder, LoadingViewHolder

### Community 25 - "WeeklyAssessmentActivity"
Cohesion: 0.13
Nodes (3): AssessmentQuestion, WeeklyAssessmentActivity, OnBackPressedCallback

### Community 28 - "ProfileFragment"
Cohesion: 0.09
Nodes (4): DailyQuestsActivity, ProfileFragment, DailyQuestMeta, QuestManager

### Community 31 - "InitiationActivity"
Cohesion: 0.17
Nodes (4): GeneratedQuestion, InitiationActivity, OnBackPressedCallback, InitiationMaterial

### Community 34 - ".performHaptic"
Cohesion: 0.11
Nodes (4): ClassModel, LibraryQuizOverviewActivity, QuestionModel, StudentSubmissionState

### Community 35 - "HelpGuidesAdapter"
Cohesion: 0.16
Nodes (4): GuideViewHolder, HelpActivity, HelpGuide, HelpGuidesAdapter

### Community 39 - "3. The 6 Critical Loopholes: Status & Resolution"
Cohesion: 0.25
Nodes (7): 3. The 6 Critical Loopholes: Status & Resolution, Loophole 1: Students Creating Database Classrooms, Loophole 2: Adviser vs Subject Teacher Student Visibility, Loophole 3: Subject Teacher Linking to an Advisory Section, Loophole 4: Unwanted Floating Bubble on Quiz Exit, Loophole 5: Profile Loading Flicker & Mastered Words, Loophole 6: Quest Initiation vs Library Material Separation

### Community 41 - "TeacherHomeFragment"
Cohesion: 0.11
Nodes (4): Announcement, AnnouncementViewReceipt, AnnouncementRepository, TeacherHomeFragment

### Community 45 - "2. The 5 Core System Engines (How It Actually Works)"
Cohesion: 0.15
Nodes (12): 1. The Core Persona & Responsibility Boundary, 2. The 5 Core System Engines (How It Actually Works), 4. Clear Roadmap for What to Do Next, Engine 1: School Onboarding & Classroom Creation, Engine 2: Student Account Lifecycle & Enrollment, Engine 3: The Dual Reading Pipeline (Diagnostic vs Library), Engine 4: Gamification, Vocabulary Recall & Combo Streak Engine, Engine 5: Teacher Material Assignment & Performance Roster (+4 more)

### Community 47 - "generate_manual_docx.py"
Cohesion: 0.08
Nodes (18): DualStackHandler, run(), add_body_p(), add_bullet_p(), add_figure_block(), add_heading_1(), add_heading_2(), add_heading_3() (+10 more)

### Community 54 - ".setupBlurView"
Cohesion: 0.15
Nodes (3): LeaderboardActivity, StudentStats, TeacherPerformanceActivity

### Community 77 - ".applyHardwareMaxRefreshRate"
Cohesion: 0.16
Nodes (3): AchievementsActivity, Badge, StudentSubjectActivity

## Knowledge Gaps
- **18 isolated node(s):** `WORD`, `PHRASE`, `SENTENCE`, `IDLE`, `LOADING` (+13 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 223 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **40 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `QuizActivity` connect `QuizActivity` to `FirebaseAuth`, `3. The 6 Critical Loopholes: Status & Resolution`?**
  _High betweenness centrality (0.037) - this node is a cross-community bridge._
- **What connects `WORD`, `PHRASE`, `SENTENCE` to the rest of the system?**
  _18 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `QuizActivity` be split into smaller, more focused modules?**
  _Cohesion score 0.06142728093947606 - nodes in this community are weakly interconnected._
- **Why does `TextOverlayView` connect `TextOverlayView` to `ScanResultActivity`?**
  _High betweenness centrality (0.030) - this node is a cross-community bridge._
- **Should `motion-graphics/gabai-motion.js` be split into smaller, more focused modules?**
  _Cohesion score 0.12549603174603174 - nodes in this community are weakly interconnected._
- **Why does `GabAiLoadingView` connect `GabAiLoadingView` to `.showGlobalLoading`, `FavoritesActivity`, `HistoryActivity`?**
  _High betweenness centrality (0.024) - this node is a cross-community bridge._
- **Should `motion-graphics-backup/gabai-motion.js` be split into smaller, more focused modules?**
  _Cohesion score 0.1396843950905903 - nodes in this community are weakly interconnected._