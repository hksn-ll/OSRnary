# Graph Report - OSRnary  (2026-10-05)

## Corpus Check
- 57 files · ~143,988 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 812 nodes · 1608 edges · 40 communities (29 shown, 7 thin omitted)
- Extraction: 99% EXTRACTED · 1% INFERRED · 0% AMBIGUOUS · INFERRED: 9 edges (avg confidence: 0.86)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `22f5f3ee`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- Intent
- CameraActivity
- FirebaseAuth
- ManageClassesActivity
- ScanResultActivity
- FloatingControlService
- AuthActivity
- ClassDetailActivity
- OverviewActivity
- FavoritesActivity
- QuizActivity
- QuizEditorActivity
- PdfViewerActivity
- Activity
- TextOverlayView
- ProfileFragment
- launch.ps1
- SubjectDetailActivity
- HistoryActivity
- GabAIDialogs
- WeeklyAssessmentActivity
- GabAiLoadingView
- HomeFragment
- FastBlurTarget
- TeacherHomeFragment
- QuestManager
- GabAIApp
- gradlew
- ExampleInstrumentedTest
- Earthquakes: Movement of the Earth's Crust
- ExampleUnitTest
- ScanImageHolder
- ic_launcher-playstore (main)
- ic_search_bubble (drawable)
- DualStackHandler
- ProgressDashboardActivity.kt

## God Nodes (most connected - your core abstractions)
1. `OverviewActivity` - 33 edges
2. `QuizActivity` - 29 edges
3. `FloatingControlService` - 23 edges
4. `TextOverlayView` - 22 edges
5. `AuthActivity` - 21 edges
6. `HomeFragment` - 21 edges
7. `GabAiLoadingView` - 20 edges
8. `WeeklyAssessmentActivity` - 20 edges
9. `ClassDetailActivity` - 19 edges
10. `MainActivity` - 19 edges

## Surprising Connections (you probably didn't know these)
- `FavoritesActivity` --references--> `GabAiLoadingView`  [EXTRACTED]
  app/src/main/java/com/example/gabai/FavoritesActivity.kt → app/src/main/java/com/example/gabai/GabAiLoadingView.kt
- `ItemViewHolder` --references--> `GabAiLoadingView`  [EXTRACTED]
  app/src/main/java/com/example/gabai/FavoritesActivity.kt → app/src/main/java/com/example/gabai/GabAiLoadingView.kt
- `HistoryActivity` --references--> `GabAiLoadingView`  [EXTRACTED]
  app/src/main/java/com/example/gabai/HistoryActivity.kt → app/src/main/java/com/example/gabai/GabAiLoadingView.kt
- `ItemViewHolder` --references--> `GabAiLoadingView`  [EXTRACTED]
  app/src/main/java/com/example/gabai/HistoryActivity.kt → app/src/main/java/com/example/gabai/GabAiLoadingView.kt
- `AuthActivity` --references--> `School`  [EXTRACTED]
  app/src/main/java/com/example/gabai/AuthActivity.kt → app/src/main/java/com/example/gabai/SchoolRepository.kt

## Import Cycles
- None detected.

## Communities (40 total, 7 thin omitted)

### Community 0 - "Intent"
Cohesion: 0.06
Nodes (26): ActivityMainBinding, DailyQuestsActivity, AppCompatActivity, Bundle, AppCompatActivity, Bundle, LibraryActivity, android (+18 more)

### Community 1 - "CameraActivity"
Cohesion: 0.10
Nodes (15): AnimatorSet, CameraActivity, OnImageSavedCallback, AppCompatActivity, Bundle, ValueAnimator, GabAiAnimatedLogoView, AnimatorListenerAdapter (+7 more)

### Community 2 - "FirebaseAuth"
Cohesion: 0.09
Nodes (16): GeneratedQuestion, InitiationActivity, InitiationMaterial, AppCompatActivity, Bundle, FirebaseFirestore, AppCompatActivity, Bundle (+8 more)

### Community 3 - "ManageClassesActivity"
Cohesion: 0.25
Nodes (3): AppCompatActivity, Bundle, ManageClassesActivity

### Community 4 - "ScanResultActivity"
Cohesion: 0.14
Nodes (8): android, AppCompatActivity, Bitmap, Bundle, OnTouchListener, ScanResultActivity, OnTouchListener, WindowManager

### Community 5 - "FloatingControlService"
Cohesion: 0.11
Nodes (15): FloatingControlService, Callback, OnTouchListener, Bitmap, Callback, MotionEvent, OnTouchListener, View (+7 more)

### Community 6 - "AuthActivity"
Cohesion: 0.13
Nodes (8): ActivityAuthBinding, AuthActivity, AppCompatActivity, Bundle, FirebaseFirestore, School, SchoolAdminInvite, SchoolRepository

### Community 7 - "ClassDetailActivity"
Cohesion: 0.19
Nodes (5): ClassDetailActivity, AppCompatActivity, Bundle, Dialog, Uri

### Community 8 - "OverviewActivity"
Cohesion: 0.08
Nodes (15): AppCompatActivity, Bitmap, Bundle, OverviewActivity, UtteranceProgressListener, WebViewClient, TtsPlaybackState, IDLE (+7 more)

### Community 9 - "FavoritesActivity"
Cohesion: 0.12
Nodes (18): FavoriteItem, FavoritesActivity, OnScrollListener, FavoritesAdapter, ItemViewHolder, Adapter, AppCompatActivity, Bundle (+10 more)

### Community 10 - "QuizActivity"
Cohesion: 0.13
Nodes (11): AppCompatActivity, Bundle, DocumentSnapshot, OnBackPressedCallback, ProgressBar, TextView, View, QuizActivity (+3 more)

### Community 11 - "QuizEditorActivity"
Cohesion: 0.15
Nodes (8): CachedOverview, OverviewCache, ClassInfo, AppCompatActivity, Bundle, LinearLayout, TextView, QuizEditorActivity

### Community 12 - "PdfViewerActivity"
Cohesion: 0.23
Nodes (7): android, AppCompatActivity, Bundle, TextView, View, PdfViewerActivity, PDFView

### Community 13 - "Activity"
Cohesion: 0.12
Nodes (16): GitHubUpdateHelper, Callback, Callback, Callback, Callback, ActivityLifecycleCallbacks, Activity, Application (+8 more)

### Community 14 - "TextOverlayView"
Cohesion: 0.10
Nodes (14): DragMode, DRAG_END_HANDLE, DRAG_START_HANDLE, NONE, SELECTION, android, Canvas, MotionEvent (+6 more)

### Community 15 - "ProfileFragment"
Cohesion: 0.21
Nodes (8): Bundle, FirebaseFirestore, Fragment, LayoutInflater, View, ViewGroup, ProfileFragment, FragmentProfileBinding

### Community 16 - "launch.ps1"
Cohesion: 0.20
Nodes (7): AppCompatActivity, Bundle, TeacherLibraryActivity, AppCompatActivity, Bundle, StudentStats, TeacherPerformanceActivity

### Community 17 - "SubjectDetailActivity"
Cohesion: 0.07
Nodes (19): GabAIUtils, Activity, Context, ProgressBar, View, TextSpanClassification, TextSpanType, PHRASE (+11 more)

### Community 18 - "HistoryActivity"
Cohesion: 0.12
Nodes (18): HistoryActivity, OnScrollListener, HistoryAdapter, HistoryItem, ItemViewHolder, Adapter, AppCompatActivity, Bundle (+10 more)

### Community 19 - "GabAIDialogs"
Cohesion: 0.35
Nodes (6): GabAIDialogs, Activity, Bitmap, Context, Dialog, View

### Community 20 - "WeeklyAssessmentActivity"
Cohesion: 0.15
Nodes (10): AssessmentQuestion, AppCompatActivity, Bundle, Button, OnBackPressedCallback, ProgressBar, TextView, View (+2 more)

### Community 21 - "GabAiLoadingView"
Cohesion: 0.09
Nodes (12): DevEasterEggActivity, OnSeekBarChangeListener, AppCompatActivity, Bundle, Button, FrameLayout, TextView, GabAiLoadingView (+4 more)

### Community 22 - "HomeFragment"
Cohesion: 0.12
Nodes (11): HomeFragment, Bundle, Fragment, LayoutInflater, View, ViewGroup, Context, SharedPreferences (+3 more)

### Community 23 - "FastBlurTarget"
Cohesion: 0.14
Nodes (11): AchievementsActivity, Badge, AppCompatActivity, Bundle, FastBlurTarget, FastBlurView, android, Bitmap (+3 more)

### Community 24 - "TeacherHomeFragment"
Cohesion: 0.23
Nodes (7): Bundle, Fragment, LayoutInflater, View, ViewGroup, TeacherHomeFragment, FragmentTeacherHomeBinding

### Community 25 - "QuestManager"
Cohesion: 0.47
Nodes (3): Context, SharedPreferences, QuestManager

### Community 27 - "gradlew"
Cohesion: 0.83
Nodes (3): gradlew script, die(), warn()

### Community 29 - "Earthquakes: Movement of the Earth's Crust"
Cohesion: 1.00
Nodes (3): Earthquakes: Movement of the Earth's Crust, Seismic Waves, Tectonic Plates and Fault Lines

### Community 36 - "ProgressDashboardActivity.kt"
Cohesion: 0.48
Nodes (3): AppCompatActivity, Bundle, ProgressDashboardActivity

## Knowledge Gaps
- **13 isolated node(s):** `WORD`, `PHRASE`, `SENTENCE`, `IDLE`, `LOADING` (+8 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 85 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **7 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `GabAiLoadingView` connect `GabAiLoadingView` to `FavoritesActivity`, `HistoryActivity`, `SubjectDetailActivity`?**
  _High betweenness centrality (0.084) - this node is a cross-community bridge._
- **Why does `TextOverlayView` connect `TextOverlayView` to `ScanResultActivity`?**
  _High betweenness centrality (0.065) - this node is a cross-community bridge._
- **What connects `WORD`, `PHRASE`, `SENTENCE` to the rest of the system?**
  _13 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Intent` be split into smaller, more focused modules?**
  _Cohesion score 0.060814383923849816 - nodes in this community are weakly interconnected._
- **Should `CameraActivity` be split into smaller, more focused modules?**
  _Cohesion score 0.10416666666666667 - nodes in this community are weakly interconnected._
- **Should `FirebaseAuth` be split into smaller, more focused modules?**
  _Cohesion score 0.08943089430894309 - nodes in this community are weakly interconnected._
- **Should `ScanResultActivity` be split into smaller, more focused modules?**
  _Cohesion score 0.14461538461538462 - nodes in this community are weakly interconnected._