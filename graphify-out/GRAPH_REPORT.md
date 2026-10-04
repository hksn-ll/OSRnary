# Graph Report - OSRnary  (2026-10-05)

## Corpus Check
- 53 files · ~136,449 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 703 nodes · 1385 edges · 38 communities (28 shown, 7 thin omitted)
- Extraction: 100% EXTRACTED · 0% INFERRED · 0% AMBIGUOUS · INFERRED: 4 edges (avg confidence: 0.88)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `fe86bdb5`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- MainActivity
- CameraActivity
- InitiationActivity
- FirebaseAuth
- ScanResultActivity
- Intent
- AuthActivity
- ClassDetailActivity
- OverviewActivity
- LibraryActivity
- QuizActivity
- QuizEditorActivity
- PdfViewerActivity
- Activity
- TextOverlayView
- ProfileFragment
- TeacherLibraryActivity
- SubjectDetailActivity
- HistoryActivity.kt
- GabAIDialogs
- WeeklyAssessmentActivity
- HomeFragment
- GabAIUtils
- TeacherHomeFragment
- FavoritesActivity.kt
- GabAIApp
- gradlew
- ExampleInstrumentedTest
- Earthquakes: Movement of the Earth's Crust
- ExampleUnitTest
- ScanImageHolder
- ic_launcher-playstore (main)
- ic_search_bubble (drawable)
- DualStackHandler
- QuestManager

## God Nodes (most connected - your core abstractions)
1. `OverviewActivity` - 32 edges
2. `QuizActivity` - 23 edges
3. `AuthActivity` - 21 edges
4. `FloatingControlService` - 21 edges
5. `HomeFragment` - 21 edges
6. `TextOverlayView` - 20 edges
7. `WeeklyAssessmentActivity` - 20 edges
8. `ClassDetailActivity` - 19 edges
9. `MainActivity` - 18 edges
10. `SubjectDetailActivity` - 18 edges

## Surprising Connections (you probably didn't know these)
- `AuthActivity` --references--> `School`  [EXTRACTED]
  app/src/main/java/com/example/gabai/AuthActivity.kt → app/src/main/java/com/example/gabai/SchoolRepository.kt

## Import Cycles
- None detected.

## Communities (38 total, 7 thin omitted)

### Community 0 - "MainActivity"
Cohesion: 0.15
Nodes (9): ActivityMainBinding, android, AppCompatActivity, Bundle, Fragment, OnBackPressedCallback, MainActivity, OnBackPressedCallback (+1 more)

### Community 1 - "CameraActivity"
Cohesion: 0.10
Nodes (15): AnimatorSet, CameraActivity, OnImageSavedCallback, AppCompatActivity, Bundle, ValueAnimator, GabAiAnimatedLogoView, AnimatorListenerAdapter (+7 more)

### Community 2 - "InitiationActivity"
Cohesion: 0.15
Nodes (9): GeneratedQuestion, InitiationActivity, InitiationMaterial, AppCompatActivity, Bundle, FirebaseFirestore, AppCompatActivity, Bundle (+1 more)

### Community 3 - "FirebaseAuth"
Cohesion: 0.09
Nodes (13): AppCompatActivity, Bundle, LeaderboardActivity, AppCompatActivity, Bundle, ManageClassesActivity, AppCompatActivity, Bundle (+5 more)

### Community 4 - "ScanResultActivity"
Cohesion: 0.17
Nodes (7): android, AppCompatActivity, Bitmap, Bundle, OnTouchListener, ScanResultActivity, OnTouchListener

### Community 5 - "Intent"
Cohesion: 0.06
Nodes (28): DailyQuestsActivity, AppCompatActivity, Bundle, FloatingControlService, Callback, OnTouchListener, Bitmap, Callback (+20 more)

### Community 6 - "AuthActivity"
Cohesion: 0.13
Nodes (8): ActivityAuthBinding, AuthActivity, AppCompatActivity, Bundle, FirebaseFirestore, School, SchoolAdminInvite, SchoolRepository

### Community 7 - "ClassDetailActivity"
Cohesion: 0.19
Nodes (5): ClassDetailActivity, AppCompatActivity, Bundle, Dialog, Uri

### Community 8 - "OverviewActivity"
Cohesion: 0.06
Nodes (24): AchievementsActivity, Badge, AppCompatActivity, Bundle, AppCompatActivity, Bitmap, Bundle, TextView (+16 more)

### Community 9 - "LibraryActivity"
Cohesion: 0.43
Nodes (3): AppCompatActivity, Bundle, LibraryActivity

### Community 10 - "QuizActivity"
Cohesion: 0.14
Nodes (11): AppCompatActivity, Bundle, OnBackPressedCallback, ProgressBar, TextView, View, QuizActivity, OnBackPressedCallback (+3 more)

### Community 11 - "QuizEditorActivity"
Cohesion: 0.14
Nodes (10): ClassInfo, AppCompatActivity, Bundle, LinearLayout, TextView, QuizEditorActivity, AppCompatActivity, Bundle (+2 more)

### Community 12 - "PdfViewerActivity"
Cohesion: 0.22
Nodes (7): android, AppCompatActivity, Bundle, ProgressBar, TextView, PdfViewerActivity, PDFView

### Community 13 - "Activity"
Cohesion: 0.12
Nodes (16): GitHubUpdateHelper, Callback, Callback, Callback, Callback, ActivityLifecycleCallbacks, Activity, Application (+8 more)

### Community 14 - "TextOverlayView"
Cohesion: 0.10
Nodes (14): DragMode, DRAG_END_HANDLE, DRAG_START_HANDLE, NONE, SELECTION, android, Canvas, MotionEvent (+6 more)

### Community 15 - "ProfileFragment"
Cohesion: 0.21
Nodes (8): Bundle, FirebaseFirestore, Fragment, LayoutInflater, View, ViewGroup, ProfileFragment, FragmentProfileBinding

### Community 16 - "TeacherLibraryActivity"
Cohesion: 0.42
Nodes (3): AppCompatActivity, Bundle, TeacherLibraryActivity

### Community 17 - "SubjectDetailActivity"
Cohesion: 0.17
Nodes (8): AppCompatActivity, Bundle, Button, LinearLayout, ProgressBar, TextView, Uri, SubjectDetailActivity

### Community 18 - "HistoryActivity.kt"
Cohesion: 0.48
Nodes (3): HistoryActivity, AppCompatActivity, Bundle

### Community 19 - "GabAIDialogs"
Cohesion: 0.35
Nodes (6): GabAIDialogs, Activity, Bitmap, Context, Dialog, View

### Community 20 - "WeeklyAssessmentActivity"
Cohesion: 0.15
Nodes (10): AssessmentQuestion, AppCompatActivity, Bundle, Button, OnBackPressedCallback, ProgressBar, TextView, View (+2 more)

### Community 22 - "HomeFragment"
Cohesion: 0.12
Nodes (11): HomeFragment, Bundle, Fragment, LayoutInflater, View, ViewGroup, Context, SharedPreferences (+3 more)

### Community 23 - "GabAIUtils"
Cohesion: 0.09
Nodes (16): FastBlurTarget, FastBlurView, Bitmap, Canvas, View, GabAIUtils, Activity, Context (+8 more)

### Community 24 - "TeacherHomeFragment"
Cohesion: 0.23
Nodes (7): Bundle, Fragment, LayoutInflater, View, ViewGroup, TeacherHomeFragment, FragmentTeacherHomeBinding

### Community 25 - "FavoritesActivity.kt"
Cohesion: 0.48
Nodes (3): FavoritesActivity, AppCompatActivity, Bundle

### Community 27 - "gradlew"
Cohesion: 0.83
Nodes (3): gradlew script, die(), warn()

### Community 29 - "Earthquakes: Movement of the Earth's Crust"
Cohesion: 1.00
Nodes (3): Earthquakes: Movement of the Earth's Crust, Seismic Waves, Tectonic Plates and Fault Lines

### Community 36 - "QuestManager"
Cohesion: 0.47
Nodes (3): Context, SharedPreferences, QuestManager

## Knowledge Gaps
- **16 isolated node(s):** `WORD`, `PHRASE`, `SENTENCE`, `IDLE`, `LOADING` (+11 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 67 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **7 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `TextOverlayView` connect `TextOverlayView` to `ScanResultActivity`?**
  _High betweenness centrality (0.069) - this node is a cross-community bridge._
- **Why does `AuthActivity` connect `AuthActivity` to `FirebaseAuth`?**
  _High betweenness centrality (0.063) - this node is a cross-community bridge._
- **What connects `WORD`, `PHRASE`, `SENTENCE` to the rest of the system?**
  _16 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `CameraActivity` be split into smaller, more focused modules?**
  _Cohesion score 0.10416666666666667 - nodes in this community are weakly interconnected._
- **Should `InitiationActivity` be split into smaller, more focused modules?**
  _Cohesion score 0.1455026455026455 - nodes in this community are weakly interconnected._
- **Should `FirebaseAuth` be split into smaller, more focused modules?**
  _Cohesion score 0.09243697478991597 - nodes in this community are weakly interconnected._
- **Should `Intent` be split into smaller, more focused modules?**
  _Cohesion score 0.06291591046581972 - nodes in this community are weakly interconnected._