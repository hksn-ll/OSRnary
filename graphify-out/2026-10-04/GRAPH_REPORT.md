# Graph Report - OSRnary  (2026-10-04)

## Corpus Check
- 52 files · ~132,795 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 647 nodes · 1270 edges · 39 communities (30 shown, 6 thin omitted)
- Extraction: 100% EXTRACTED · 0% INFERRED · 0% AMBIGUOUS · INFERRED: 4 edges (avg confidence: 0.88)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `225e4f69`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- Intent
- GabAiAnimatedLogoView
- FirebaseAuth
- ManageClassesActivity
- ScanResultActivity
- FloatingControlService
- AuthActivity
- ImageView
- OverviewActivity
- SchoolRepository
- QuizActivity
- QuizEditorActivity
- PdfViewerActivity
- Call
- TextOverlayView
- ProfileFragment
- launch.ps1
- SubjectDetailActivity
- ClassDetailActivity
- Dialog
- WeeklyAssessmentActivity
- DailyQuestsActivity.kt
- HomeFragment
- FastBlurView
- TeacherHomeFragment
- QuizHistoryActivity
- GabAIApp
- gradlew
- ExampleInstrumentedTest
- Earthquakes: Movement of the Earth's Crust
- ExampleUnitTest
- QuestDetailsActivity.kt
- ic_launcher-playstore (main)
- ic_search_bubble (drawable)
- DualStackHandler
- QuestManager

## God Nodes (most connected - your core abstractions)
1. `OverviewActivity` - 30 edges
2. `QuizActivity` - 23 edges
3. `AuthActivity` - 21 edges
4. `FloatingControlService` - 21 edges
5. `WeeklyAssessmentActivity` - 20 edges
6. `ClassDetailActivity` - 19 edges
7. `HomeFragment` - 19 edges
8. `SubjectDetailActivity` - 18 edges
9. `TextOverlayView` - 18 edges
10. `MainActivity` - 17 edges

## Surprising Connections (you probably didn't know these)
- `AuthActivity` --references--> `School`  [EXTRACTED]
  app/src/main/java/com/example/gabai/AuthActivity.kt → app/src/main/java/com/example/gabai/SchoolRepository.kt

## Import Cycles
- None detected.

## Communities (39 total, 6 thin omitted)

### Community 0 - "Intent"
Cohesion: 0.09
Nodes (17): ActivityMainBinding, AppCompatActivity, Bundle, LibraryActivity, android, AppCompatActivity, Bundle, Fragment (+9 more)

### Community 1 - "GabAiAnimatedLogoView"
Cohesion: 0.23
Nodes (7): AnimatorSet, GabAiAnimatedLogoView, AnimatorListenerAdapter, android, Canvas, View, ValueAnimator

### Community 2 - "FirebaseAuth"
Cohesion: 0.14
Nodes (10): GeneratedQuestion, InitiationActivity, InitiationMaterial, AppCompatActivity, Bundle, FirebaseFirestore, AppCompatActivity, Bundle (+2 more)

### Community 3 - "ManageClassesActivity"
Cohesion: 0.25
Nodes (3): AppCompatActivity, Bundle, ManageClassesActivity

### Community 4 - "ScanResultActivity"
Cohesion: 0.17
Nodes (7): android, AppCompatActivity, Bitmap, Bundle, OnTouchListener, ScanResultActivity, OnTouchListener

### Community 5 - "FloatingControlService"
Cohesion: 0.11
Nodes (15): FloatingControlService, Callback, OnTouchListener, Bitmap, Callback, MotionEvent, OnTouchListener, View (+7 more)

### Community 6 - "AuthActivity"
Cohesion: 0.23
Nodes (5): ActivityAuthBinding, AuthActivity, AppCompatActivity, Bundle, FirebaseFirestore

### Community 7 - "ImageView"
Cohesion: 0.11
Nodes (14): AchievementsActivity, Badge, AppCompatActivity, Bundle, FavoritesActivity, AppCompatActivity, Bundle, HistoryActivity (+6 more)

### Community 8 - "OverviewActivity"
Cohesion: 0.08
Nodes (19): AppCompatActivity, Bitmap, Bundle, TextView, OverviewActivity, WebViewClient, UtteranceProgressListener, TtsPlaybackState (+11 more)

### Community 9 - "SchoolRepository"
Cohesion: 0.29
Nodes (3): School, SchoolAdminInvite, SchoolRepository

### Community 10 - "QuizActivity"
Cohesion: 0.11
Nodes (14): AppCompatActivity, Bundle, LeaderboardActivity, AppCompatActivity, Bundle, OnBackPressedCallback, ProgressBar, TextView (+6 more)

### Community 11 - "QuizEditorActivity"
Cohesion: 0.22
Nodes (6): ClassInfo, AppCompatActivity, Bundle, LinearLayout, TextView, QuizEditorActivity

### Community 12 - "PdfViewerActivity"
Cohesion: 0.22
Nodes (7): android, AppCompatActivity, Bundle, ProgressBar, TextView, PdfViewerActivity, PDFView

### Community 13 - "Call"
Cohesion: 0.18
Nodes (11): GitHubUpdateHelper, Callback, Callback, Callback, Callback, Activity, Callback, Context (+3 more)

### Community 14 - "TextOverlayView"
Cohesion: 0.14
Nodes (9): android, Canvas, MotionEvent, View, TextOverlayView, WordBox, selectedText, surroundingSentence (+1 more)

### Community 15 - "ProfileFragment"
Cohesion: 0.23
Nodes (8): Bundle, FirebaseFirestore, Fragment, LayoutInflater, View, ViewGroup, ProfileFragment, FragmentProfileBinding

### Community 16 - "launch.ps1"
Cohesion: 0.20
Nodes (7): AppCompatActivity, Bundle, TeacherLibraryActivity, AppCompatActivity, Bundle, StudentStats, TeacherPerformanceActivity

### Community 17 - "SubjectDetailActivity"
Cohesion: 0.11
Nodes (14): CameraActivity, OnImageSavedCallback, AppCompatActivity, Bundle, AppCompatActivity, Bundle, Button, LinearLayout (+6 more)

### Community 18 - "ClassDetailActivity"
Cohesion: 0.19
Nodes (5): androidx, ClassDetailActivity, AppCompatActivity, Bundle, Uri

### Community 19 - "Dialog"
Cohesion: 0.41
Nodes (5): GabAIDialogs, Activity, Bitmap, Context, Dialog

### Community 20 - "WeeklyAssessmentActivity"
Cohesion: 0.15
Nodes (10): AssessmentQuestion, AppCompatActivity, Bundle, Button, OnBackPressedCallback, ProgressBar, TextView, View (+2 more)

### Community 21 - "DailyQuestsActivity.kt"
Cohesion: 0.48
Nodes (3): DailyQuestsActivity, AppCompatActivity, Bundle

### Community 22 - "HomeFragment"
Cohesion: 0.12
Nodes (11): HomeFragment, Bundle, Fragment, LayoutInflater, View, ViewGroup, Context, SharedPreferences (+3 more)

### Community 23 - "FastBlurView"
Cohesion: 0.12
Nodes (11): FastBlurTarget, FastBlurView, Bitmap, Canvas, View, GabAIUtils, Activity, Context (+3 more)

### Community 24 - "TeacherHomeFragment"
Cohesion: 0.25
Nodes (7): Bundle, Fragment, LayoutInflater, View, ViewGroup, TeacherHomeFragment, FragmentTeacherHomeBinding

### Community 25 - "QuizHistoryActivity"
Cohesion: 0.48
Nodes (3): AppCompatActivity, Bundle, QuizHistoryActivity

### Community 27 - "gradlew"
Cohesion: 0.83
Nodes (3): gradlew script, die(), warn()

### Community 29 - "Earthquakes: Movement of the Earth's Crust"
Cohesion: 1.00
Nodes (3): Earthquakes: Movement of the Earth's Crust, Seismic Waves, Tectonic Plates and Fault Lines

### Community 31 - "QuestDetailsActivity.kt"
Cohesion: 0.48
Nodes (3): AppCompatActivity, Bundle, QuestDetailsActivity

### Community 36 - "QuestManager"
Cohesion: 0.47
Nodes (3): Context, SharedPreferences, QuestManager

## Knowledge Gaps
- **9 isolated node(s):** `IDLE`, `LOADING`, `PLAYING`, `PAUSED`, `OVERVIEW` (+4 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 57 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **6 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `AuthActivity` connect `AuthActivity` to `SchoolRepository`, `FirebaseAuth`?**
  _High betweenness centrality (0.070) - this node is a cross-community bridge._
- **Why does `TextOverlayView` connect `TextOverlayView` to `ScanResultActivity`?**
  _High betweenness centrality (0.058) - this node is a cross-community bridge._
- **What connects `IDLE`, `LOADING`, `PLAYING` to the rest of the system?**
  _9 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Intent` be split into smaller, more focused modules?**
  _Cohesion score 0.08773784355179703 - nodes in this community are weakly interconnected._
- **Should `FirebaseAuth` be split into smaller, more focused modules?**
  _Cohesion score 0.14039408866995073 - nodes in this community are weakly interconnected._
- **Should `FloatingControlService` be split into smaller, more focused modules?**
  _Cohesion score 0.11494252873563218 - nodes in this community are weakly interconnected._
- **Should `ImageView` be split into smaller, more focused modules?**
  _Cohesion score 0.11375661375661375 - nodes in this community are weakly interconnected._