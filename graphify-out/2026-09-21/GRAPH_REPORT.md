# Graph Report - OSRnary  (2026-09-21)

## Corpus Check
- 50 files · ~106,235 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 527 nodes · 976 edges · 42 communities (27 shown, 12 thin omitted)
- Extraction: 99% EXTRACTED · 1% INFERRED · 0% AMBIGUOUS · INFERRED: 8 edges (avg confidence: 0.86)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `98ffd4b2`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- Intent
- .showSnackbar
- InitiationActivity
- ManageClassesActivity
- ScanResultActivity
- FloatingControlService
- AuthActivity
- LibraryActivity
- OverviewActivity
- SchoolRepository
- QuizActivity
- QuizEditorActivity
- PdfViewerActivity
- GitHubUpdateHelper.kt
- TextOverlayView
- ProfileFragment.kt
- launch.ps1
- QuestManager
- Callback
- ClassDetailActivity
- WeeklyAssessmentActivity
- DailyQuestsActivity.kt
- HistoryActivity
- FavoritesActivity
- FirebaseAuth
- FavoriteDetailActivity.kt
- GabAIApp
- gradlew
- ExampleInstrumentedTest
- Earthquakes: Movement of the Earth's Crust
- ExampleUnitTest
- ImageView
- ic_launcher-playstore (main)
- ic_search_bubble (drawable)
- DualStackHandler
- TextView
- Fragment
- LayoutInflater
- ViewGroup

## God Nodes (most connected - your core abstractions)
1. `OverviewActivity` - 24 edges
2. `AuthActivity` - 20 edges
3. `ClassDetailActivity` - 19 edges
4. `WeeklyAssessmentActivity` - 19 edges
5. `FloatingControlService` - 18 edges
6. `TextOverlayView` - 18 edges
7. `SubjectDetailActivity` - 18 edges
8. `ScanResultActivity` - 17 edges
9. `HomeFragment` - 17 edges
10. `QuizEditorActivity` - 17 edges

## Surprising Connections (you probably didn't know these)
- `MaterialQuizActivity` --inherits--> `AppCompatActivity`  [EXTRACTED]
  app/src/main/java/com/example/gabai/MaterialQuizActivity.kt →   _Bridges community 2 → community 24_
- `AuthActivity` --references--> `FirebaseAuth`  [EXTRACTED]
  app/src/main/java/com/example/gabai/AuthActivity.kt →   _Bridges community 6 → community 24_

## Import Cycles
- None detected.

## Communities (42 total, 12 thin omitted)

### Community 0 - "Intent"
Cohesion: 0.07
Nodes (25): ActivityMainBinding, HomeFragment, Bundle, Fragment, LayoutInflater, View, ViewGroup, AppCompatActivity (+17 more)

### Community 1 - ".showSnackbar"
Cohesion: 0.12
Nodes (11): GabAIUtils, Context, AppCompatActivity, Bundle, Button, LinearLayout, ProgressBar, TextView (+3 more)

### Community 2 - "InitiationActivity"
Cohesion: 0.17
Nodes (7): GeneratedQuestion, InitiationActivity, InitiationMaterial, AppCompatActivity, Bundle, FirebaseFirestore, MaterialQuizActivity

### Community 3 - "ManageClassesActivity"
Cohesion: 0.25
Nodes (3): AppCompatActivity, Bundle, ManageClassesActivity

### Community 4 - "ScanResultActivity"
Cohesion: 0.17
Nodes (7): android, AppCompatActivity, Bitmap, Bundle, OnTouchListener, ScanResultActivity, OnTouchListener

### Community 5 - "FloatingControlService"
Cohesion: 0.14
Nodes (14): FloatingControlService, Callback, OnTouchListener, Bitmap, MotionEvent, OnTouchListener, View, IBinder (+6 more)

### Community 6 - "AuthActivity"
Cohesion: 0.23
Nodes (5): ActivityAuthBinding, AuthActivity, AppCompatActivity, Bundle, FirebaseFirestore

### Community 7 - "LibraryActivity"
Cohesion: 0.43
Nodes (3): AppCompatActivity, Bundle, LibraryActivity

### Community 8 - "OverviewActivity"
Cohesion: 0.11
Nodes (13): AppCompatActivity, Bitmap, Bundle, OverviewActivity, WebViewClient, VisualMode, DIAGRAMS, OVERVIEW (+5 more)

### Community 9 - "SchoolRepository"
Cohesion: 0.33
Nodes (3): School, SchoolAdminInvite, SchoolRepository

### Community 10 - "QuizActivity"
Cohesion: 0.25
Nodes (4): AppCompatActivity, Bundle, QuizActivity, com

### Community 11 - "QuizEditorActivity"
Cohesion: 0.22
Nodes (6): ClassInfo, AppCompatActivity, Bundle, LinearLayout, TextView, QuizEditorActivity

### Community 12 - "PdfViewerActivity"
Cohesion: 0.29
Nodes (4): AppCompatActivity, Bundle, TextView, PdfViewerActivity

### Community 13 - "GitHubUpdateHelper.kt"
Cohesion: 0.12
Nodes (16): Activity, CameraActivity, OnImageSavedCallback, AppCompatActivity, Bundle, GitHubUpdateHelper, Callback, Callback (+8 more)

### Community 14 - "TextOverlayView"
Cohesion: 0.15
Nodes (8): android, MotionEvent, View, TextOverlayView, WordBox, selectedText, surroundingSentence, Text

### Community 15 - "ProfileFragment.kt"
Cohesion: 0.29
Nodes (7): Bundle, View, ProfileFragment, Fragment, FragmentProfileBinding, LayoutInflater, ViewGroup

### Community 16 - "launch.ps1"
Cohesion: 0.20
Nodes (7): AppCompatActivity, Bundle, TeacherLibraryActivity, AppCompatActivity, Bundle, StudentStats, TeacherPerformanceActivity

### Community 19 - "ClassDetailActivity"
Cohesion: 0.19
Nodes (5): androidx, ClassDetailActivity, AppCompatActivity, Bundle, Uri

### Community 20 - "WeeklyAssessmentActivity"
Cohesion: 0.18
Nodes (8): AssessmentQuestion, AppCompatActivity, Bundle, Button, ProgressBar, TextView, View, WeeklyAssessmentActivity

### Community 21 - "DailyQuestsActivity.kt"
Cohesion: 0.53
Nodes (3): DailyQuestsActivity, AppCompatActivity, Bundle

### Community 22 - "HistoryActivity"
Cohesion: 0.53
Nodes (3): HistoryActivity, AppCompatActivity, Bundle

### Community 23 - "FavoritesActivity"
Cohesion: 0.53
Nodes (3): FavoritesActivity, AppCompatActivity, Bundle

### Community 24 - "FirebaseAuth"
Cohesion: 0.10
Nodes (15): AppCompatActivity, Bundle, LeaderboardActivity, AppCompatActivity, Bundle, AppCompatActivity, Bundle, ProgressDashboardActivity (+7 more)

### Community 25 - "FavoriteDetailActivity.kt"
Cohesion: 0.60
Nodes (3): FavoriteDetailActivity, AppCompatActivity, Bundle

### Community 27 - "gradlew"
Cohesion: 0.83
Nodes (3): gradlew script, die(), warn()

### Community 29 - "Earthquakes: Movement of the Earth's Crust"
Cohesion: 1.00
Nodes (3): Earthquakes: Movement of the Earth's Crust, Seismic Waves, Tectonic Plates and Fault Lines

### Community 31 - "ImageView"
Cohesion: 0.23
Nodes (8): AchievementsActivity, Badge, AppCompatActivity, Bundle, AppCompatActivity, Bundle, StudentSubjectActivity, ImageView

## Knowledge Gaps
- **5 isolated node(s):** `OVERVIEW`, `REAL_WORLD`, `DIAGRAMS`, `ic_launcher-playstore (main)`, `ic_search_bubble (drawable)`
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 49 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **12 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `TextOverlayView` connect `TextOverlayView` to `ScanResultActivity`?**
  _High betweenness centrality (0.060) - this node is a cross-community bridge._
- **What connects `OVERVIEW`, `REAL_WORLD`, `DIAGRAMS` to the rest of the system?**
  _5 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Intent` be split into smaller, more focused modules?**
  _Cohesion score 0.07197763801537387 - nodes in this community are weakly interconnected._
- **Should `.showSnackbar` be split into smaller, more focused modules?**
  _Cohesion score 0.11954022988505747 - nodes in this community are weakly interconnected._
- **Should `FloatingControlService` be split into smaller, more focused modules?**
  _Cohesion score 0.13538461538461538 - nodes in this community are weakly interconnected._
- **Should `OverviewActivity` be split into smaller, more focused modules?**
  _Cohesion score 0.10756302521008404 - nodes in this community are weakly interconnected._
- **Should `GitHubUpdateHelper.kt` be split into smaller, more focused modules?**
  _Cohesion score 0.11742424242424243 - nodes in this community are weakly interconnected._