# Graph Report - OSRnary  (2026-09-07)

## Corpus Check
- 49 files · ~86,698 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 473 nodes · 882 edges · 39 communities (29 shown, 7 thin omitted)
- Extraction: 99% EXTRACTED · 1% INFERRED · 0% AMBIGUOUS · INFERRED: 6 edges (avg confidence: 0.87)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `bca8bd76`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- Intent
- SubjectDetailActivity
- InitiationActivity
- ManageClassesActivity
- ScanResultActivity
- FloatingControlService
- AuthActivity
- TeacherHomeFragment.kt
- OverviewActivity
- GitHubUpdateHelper.kt
- FirebaseAuth
- QuizEditorActivity
- PdfViewerActivity
- CameraActivity.kt
- TextOverlayView
- ProfileFragment.kt
- launch.ps1
- QuestManager
- QuestDetailsActivity.kt
- .showSnackbar
- WeeklyAssessmentActivity
- DailyQuestsActivity.kt
- FavoritesActivity
- HistoryActivity
- QuizHistoryActivity
- FavoriteDetailActivity.kt
- GabAIApp
- gradlew
- ExampleInstrumentedTest
- Earthquakes: Movement of the Earth's Crust
- ExampleUnitTest
- LeaderboardActivity.kt
- ic_launcher-playstore (main)
- ic_search_bubble (drawable)
- DualStackHandler
- LibraryActivity

## God Nodes (most connected - your core abstractions)
1. `SubjectDetailActivity` - 24 edges
2. `AuthActivity` - 19 edges
3. `WeeklyAssessmentActivity` - 19 edges
4. `FloatingControlService` - 18 edges
5. `HomeFragment` - 17 edges
6. `QuizEditorActivity` - 17 edges
7. `InitiationActivity` - 14 edges
8. `QuizActivity` - 14 edges
9. `ClassDetailActivity` - 13 edges
10. `OverviewActivity` - 13 edges

## Surprising Connections (you probably didn't know these)
- `AuthActivity` --references--> `FirebaseAuth`  [EXTRACTED]
  app/src/main/java/com/example/gabai/AuthActivity.kt →   _Bridges community 6 → community 10_

## Import Cycles
- None detected.

## Communities (39 total, 7 thin omitted)

### Community 0 - "Intent"
Cohesion: 0.09
Nodes (18): ActivityMainBinding, HomeFragment, Bundle, Fragment, LayoutInflater, View, ViewGroup, AppCompatActivity (+10 more)

### Community 1 - "SubjectDetailActivity"
Cohesion: 0.13
Nodes (9): AppCompatActivity, Bundle, Button, LinearLayout, ProgressBar, TextView, Uri, SubjectDetailActivity (+1 more)

### Community 2 - "InitiationActivity"
Cohesion: 0.15
Nodes (9): GeneratedQuestion, InitiationActivity, InitiationMaterial, AppCompatActivity, Bundle, FirebaseFirestore, AppCompatActivity, Bundle (+1 more)

### Community 3 - "ManageClassesActivity"
Cohesion: 0.25
Nodes (3): AppCompatActivity, Bundle, ManageClassesActivity

### Community 4 - "ScanResultActivity"
Cohesion: 0.10
Nodes (15): AchievementsActivity, Badge, AppCompatActivity, Bundle, android, AppCompatActivity, Bitmap, Bundle (+7 more)

### Community 5 - "FloatingControlService"
Cohesion: 0.13
Nodes (15): FloatingControlService, Callback, OnTouchListener, Bitmap, Callback, MotionEvent, OnTouchListener, View (+7 more)

### Community 6 - "AuthActivity"
Cohesion: 0.24
Nodes (5): ActivityAuthBinding, AuthActivity, AppCompatActivity, Bundle, FirebaseFirestore

### Community 7 - "TeacherHomeFragment.kt"
Cohesion: 0.27
Nodes (7): Bundle, Fragment, LayoutInflater, View, ViewGroup, TeacherHomeFragment, FragmentTeacherHomeBinding

### Community 8 - "OverviewActivity"
Cohesion: 0.18
Nodes (6): android, AppCompatActivity, Bundle, OverviewActivity, WebViewClient, TextToSpeech

### Community 9 - "GitHubUpdateHelper.kt"
Cohesion: 0.30
Nodes (7): Activity, GitHubUpdateHelper, Callback, Callback, VersionInfo, Call, Response

### Community 10 - "FirebaseAuth"
Cohesion: 0.15
Nodes (8): AppCompatActivity, Bundle, ProgressDashboardActivity, AppCompatActivity, Bundle, QuizActivity, com, FirebaseAuth

### Community 11 - "QuizEditorActivity"
Cohesion: 0.22
Nodes (6): ClassInfo, AppCompatActivity, Bundle, LinearLayout, TextView, QuizEditorActivity

### Community 12 - "PdfViewerActivity"
Cohesion: 0.29
Nodes (4): AppCompatActivity, Bundle, TextView, PdfViewerActivity

### Community 13 - "CameraActivity.kt"
Cohesion: 0.29
Nodes (6): CameraActivity, OnImageSavedCallback, AppCompatActivity, Bundle, ImageCapture, ImageCaptureException

### Community 14 - "TextOverlayView"
Cohesion: 0.26
Nodes (5): MotionEvent, View, TextOverlayView, WordBox, Text

### Community 15 - "ProfileFragment.kt"
Cohesion: 0.31
Nodes (7): Bundle, Fragment, LayoutInflater, View, ViewGroup, ProfileFragment, FragmentProfileBinding

### Community 16 - "launch.ps1"
Cohesion: 0.20
Nodes (7): AppCompatActivity, Bundle, TeacherLibraryActivity, AppCompatActivity, Bundle, StudentStats, TeacherPerformanceActivity

### Community 18 - "QuestDetailsActivity.kt"
Cohesion: 0.48
Nodes (3): AppCompatActivity, Bundle, QuestDetailsActivity

### Community 19 - ".showSnackbar"
Cohesion: 0.17
Nodes (7): androidx, ClassDetailActivity, AppCompatActivity, Bundle, Uri, GabAIUtils, Context

### Community 20 - "WeeklyAssessmentActivity"
Cohesion: 0.18
Nodes (8): AssessmentQuestion, AppCompatActivity, Bundle, Button, ProgressBar, TextView, View, WeeklyAssessmentActivity

### Community 21 - "DailyQuestsActivity.kt"
Cohesion: 0.53
Nodes (3): DailyQuestsActivity, AppCompatActivity, Bundle

### Community 22 - "FavoritesActivity"
Cohesion: 0.53
Nodes (3): FavoritesActivity, AppCompatActivity, Bundle

### Community 23 - "HistoryActivity"
Cohesion: 0.53
Nodes (3): HistoryActivity, AppCompatActivity, Bundle

### Community 24 - "QuizHistoryActivity"
Cohesion: 0.48
Nodes (3): AppCompatActivity, Bundle, QuizHistoryActivity

### Community 25 - "FavoriteDetailActivity.kt"
Cohesion: 0.60
Nodes (3): FavoriteDetailActivity, AppCompatActivity, Bundle

### Community 27 - "gradlew"
Cohesion: 0.83
Nodes (3): gradlew script, die(), warn()

### Community 29 - "Earthquakes: Movement of the Earth's Crust"
Cohesion: 1.00
Nodes (3): Earthquakes: Movement of the Earth's Crust, Seismic Waves, Tectonic Plates and Fault Lines

### Community 31 - "LeaderboardActivity.kt"
Cohesion: 0.53
Nodes (3): AppCompatActivity, Bundle, LeaderboardActivity

### Community 52 - "LibraryActivity"
Cohesion: 0.43
Nodes (3): AppCompatActivity, Bundle, LibraryActivity

## Knowledge Gaps
- **2 isolated node(s):** `ic_launcher-playstore (main)`, `ic_search_bubble (drawable)`
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 36 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **7 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `HomeFragment` connect `Intent` to `.showSnackbar`?**
  _High betweenness centrality (0.064) - this node is a cross-community bridge._
- **What connects `ic_launcher-playstore (main)`, `ic_search_bubble (drawable)` to the rest of the system?**
  _2 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Intent` be split into smaller, more focused modules?**
  _Cohesion score 0.09291521486643438 - nodes in this community are weakly interconnected._
- **Should `SubjectDetailActivity` be split into smaller, more focused modules?**
  _Cohesion score 0.1330049261083744 - nodes in this community are weakly interconnected._
- **Should `InitiationActivity` be split into smaller, more focused modules?**
  _Cohesion score 0.1455026455026455 - nodes in this community are weakly interconnected._
- **Should `ScanResultActivity` be split into smaller, more focused modules?**
  _Cohesion score 0.10483870967741936 - nodes in this community are weakly interconnected._
- **Should `FloatingControlService` be split into smaller, more focused modules?**
  _Cohesion score 0.12535612535612536 - nodes in this community are weakly interconnected._