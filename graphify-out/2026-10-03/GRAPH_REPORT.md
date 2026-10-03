# Graph Report - OSRnary  (2026-10-03)

## Corpus Check
- 50 files · ~110,120 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 560 nodes · 1062 edges · 39 communities (30 shown, 6 thin omitted)
- Extraction: 99% EXTRACTED · 1% INFERRED · 0% AMBIGUOUS · INFERRED: 8 edges (avg confidence: 0.86)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `81cf56ee`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- Intent
- SubjectDetailActivity
- FirebaseAuth
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
- XPManager
- ClassDetailActivity
- WeeklyAssessmentActivity
- TeacherHomeFragment
- LeaderboardActivity.kt
- GabAIUtils
- ProgressDashboardActivity.kt
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
- QuestDetailsActivity.kt

## God Nodes (most connected - your core abstractions)
1. `OverviewActivity` - 26 edges
2. `FloatingControlService` - 21 edges
3. `AuthActivity` - 20 edges
4. `WeeklyAssessmentActivity` - 20 edges
5. `ClassDetailActivity` - 19 edges
6. `SubjectDetailActivity` - 18 edges
7. `TextOverlayView` - 18 edges
8. `HomeFragment` - 17 edges
9. `QuizEditorActivity` - 17 edges
10. `ScanResultActivity` - 17 edges

## Surprising Connections (you probably didn't know these)
- `AuthActivity` --references--> `FirebaseAuth`  [EXTRACTED]
  app/src/main/java/com/example/gabai/AuthActivity.kt →   _Bridges community 6 → community 2_

## Import Cycles
- None detected.

## Communities (39 total, 6 thin omitted)

### Community 0 - "Intent"
Cohesion: 0.08
Nodes (21): ActivityMainBinding, DailyQuestsActivity, AppCompatActivity, Bundle, HomeFragment, Bundle, Fragment, LayoutInflater (+13 more)

### Community 1 - "SubjectDetailActivity"
Cohesion: 0.15
Nodes (9): AppCompatActivity, Bundle, Button, LinearLayout, ProgressBar, TextView, Uri, SubjectDetailActivity (+1 more)

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

### Community 7 - "LibraryActivity"
Cohesion: 0.21
Nodes (6): AppCompatActivity, Bundle, LibraryActivity, AppCompatActivity, Bundle, QuizHistoryActivity

### Community 8 - "OverviewActivity"
Cohesion: 0.10
Nodes (13): AppCompatActivity, Bitmap, Bundle, TextView, OverviewActivity, WebViewClient, VisualMode, DIAGRAMS (+5 more)

### Community 9 - "SchoolRepository"
Cohesion: 0.29
Nodes (3): School, SchoolAdminInvite, SchoolRepository

### Community 10 - "QuizActivity"
Cohesion: 0.24
Nodes (4): AppCompatActivity, Bundle, QuizActivity, com

### Community 11 - "QuizEditorActivity"
Cohesion: 0.22
Nodes (6): ClassInfo, AppCompatActivity, Bundle, LinearLayout, TextView, QuizEditorActivity

### Community 12 - "PdfViewerActivity"
Cohesion: 0.29
Nodes (4): AppCompatActivity, Bundle, TextView, PdfViewerActivity

### Community 13 - "GitHubUpdateHelper.kt"
Cohesion: 0.12
Nodes (16): CameraActivity, OnImageSavedCallback, AppCompatActivity, Bundle, GitHubUpdateHelper, Callback, Callback, Callback (+8 more)

### Community 14 - "TextOverlayView"
Cohesion: 0.16
Nodes (8): android, MotionEvent, View, TextOverlayView, WordBox, selectedText, surroundingSentence, Text

### Community 15 - "ProfileFragment.kt"
Cohesion: 0.27
Nodes (7): Bundle, Fragment, LayoutInflater, View, ViewGroup, ProfileFragment, FragmentProfileBinding

### Community 16 - "launch.ps1"
Cohesion: 0.20
Nodes (7): AppCompatActivity, Bundle, TeacherLibraryActivity, AppCompatActivity, Bundle, StudentStats, TeacherPerformanceActivity

### Community 17 - "QuestManager"
Cohesion: 0.47
Nodes (3): Context, SharedPreferences, QuestManager

### Community 18 - "XPManager"
Cohesion: 0.39
Nodes (3): Context, SharedPreferences, XPManager

### Community 19 - "ClassDetailActivity"
Cohesion: 0.18
Nodes (5): androidx, ClassDetailActivity, AppCompatActivity, Bundle, Uri

### Community 20 - "WeeklyAssessmentActivity"
Cohesion: 0.17
Nodes (9): AssessmentQuestion, AppCompatActivity, Bundle, Button, ProgressBar, TextView, View, WeeklyAssessmentActivity (+1 more)

### Community 21 - "TeacherHomeFragment"
Cohesion: 0.25
Nodes (7): Bundle, Fragment, LayoutInflater, View, ViewGroup, TeacherHomeFragment, FragmentTeacherHomeBinding

### Community 22 - "LeaderboardActivity.kt"
Cohesion: 0.53
Nodes (3): AppCompatActivity, Bundle, LeaderboardActivity

### Community 23 - "GabAIUtils"
Cohesion: 0.18
Nodes (7): GabAIUtils, Activity, android, Context, ProgressBar, View, eightbitlab

### Community 24 - "ProgressDashboardActivity.kt"
Cohesion: 0.48
Nodes (3): AppCompatActivity, Bundle, ProgressDashboardActivity

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
Cohesion: 0.12
Nodes (14): AchievementsActivity, Badge, AppCompatActivity, Bundle, FavoritesActivity, AppCompatActivity, Bundle, HistoryActivity (+6 more)

### Community 36 - "QuestDetailsActivity.kt"
Cohesion: 0.48
Nodes (3): AppCompatActivity, Bundle, QuestDetailsActivity

## Knowledge Gaps
- **5 isolated node(s):** `OVERVIEW`, `REAL_WORLD`, `DIAGRAMS`, `ic_launcher-playstore (main)`, `ic_search_bubble (drawable)`
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 48 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **6 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `TextOverlayView` connect `TextOverlayView` to `SubjectDetailActivity`, `ScanResultActivity`?**
  _High betweenness centrality (0.059) - this node is a cross-community bridge._
- **What connects `OVERVIEW`, `REAL_WORLD`, `DIAGRAMS` to the rest of the system?**
  _5 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Intent` be split into smaller, more focused modules?**
  _Cohesion score 0.08309178743961353 - nodes in this community are weakly interconnected._
- **Should `FirebaseAuth` be split into smaller, more focused modules?**
  _Cohesion score 0.14039408866995073 - nodes in this community are weakly interconnected._
- **Should `FloatingControlService` be split into smaller, more focused modules?**
  _Cohesion score 0.11494252873563218 - nodes in this community are weakly interconnected._
- **Should `OverviewActivity` be split into smaller, more focused modules?**
  _Cohesion score 0.1021021021021021 - nodes in this community are weakly interconnected._
- **Should `GitHubUpdateHelper.kt` be split into smaller, more focused modules?**
  _Cohesion score 0.11742424242424243 - nodes in this community are weakly interconnected._