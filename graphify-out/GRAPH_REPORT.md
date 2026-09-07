# Graph Report - OSRnary  (2026-09-07)

## Corpus Check
- 50 files · ~87,148 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 475 nodes · 883 edges · 40 communities (28 shown, 9 thin omitted)
- Extraction: 99% EXTRACTED · 1% INFERRED · 0% AMBIGUOUS · INFERRED: 6 edges (avg confidence: 0.87)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `f42aa428`
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
- .showSnackbar
- GitHubUpdateHelper.kt
- QuizActivity
- QuizEditorActivity
- PdfViewerActivity
- CameraActivity.kt
- TextOverlayView
- ProfileFragment.kt
- TeacherLibraryActivity
- QuestManager
- QuestDetailsActivity.kt
- ClassDetailActivity
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
- FirebaseAuth
- ic_launcher-playstore (main)
- ic_search_bubble (drawable)
- DualStackHandler
- XPManager
- scratch_release.py

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
  app/src/main/java/com/example/gabai/AuthActivity.kt →   _Bridges community 6 → community 31_
- `InitiationActivity` --references--> `GeneratedQuestion`  [EXTRACTED]
  app/src/main/java/com/example/gabai/InitiationActivity.kt → app/src/main/java/com/example/gabai/InitiationActivity.kt  _Bridges community 31 → community 2_

## Import Cycles
- None detected.

## Communities (40 total, 9 thin omitted)

### Community 0 - "Intent"
Cohesion: 0.09
Nodes (19): ActivityMainBinding, HomeFragment, Bundle, Fragment, LayoutInflater, View, ViewGroup, AppCompatActivity (+11 more)

### Community 1 - "SubjectDetailActivity"
Cohesion: 0.16
Nodes (9): AppCompatActivity, Bundle, Button, LinearLayout, ProgressBar, TextView, Uri, SubjectDetailActivity (+1 more)

### Community 2 - "InitiationActivity"
Cohesion: 0.27
Nodes (5): InitiationActivity, InitiationMaterial, AppCompatActivity, Bundle, FirebaseFirestore

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

### Community 8 - ".showSnackbar"
Cohesion: 0.13
Nodes (8): GabAIUtils, Context, android, AppCompatActivity, Bundle, OverviewActivity, WebViewClient, TextToSpeech

### Community 9 - "GitHubUpdateHelper.kt"
Cohesion: 0.30
Nodes (7): Activity, GitHubUpdateHelper, Callback, Callback, VersionInfo, Call, Response

### Community 10 - "QuizActivity"
Cohesion: 0.15
Nodes (8): AppCompatActivity, Bundle, QuizActivity, AppCompatActivity, Bundle, StudentStats, TeacherPerformanceActivity, com

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
Cohesion: 0.23
Nodes (5): MotionEvent, View, TextOverlayView, WordBox, Text

### Community 15 - "ProfileFragment.kt"
Cohesion: 0.31
Nodes (7): Bundle, Fragment, LayoutInflater, View, ViewGroup, ProfileFragment, FragmentProfileBinding

### Community 16 - "TeacherLibraryActivity"
Cohesion: 0.42
Nodes (3): AppCompatActivity, Bundle, TeacherLibraryActivity

### Community 18 - "QuestDetailsActivity.kt"
Cohesion: 0.48
Nodes (3): AppCompatActivity, Bundle, QuestDetailsActivity

### Community 19 - "ClassDetailActivity"
Cohesion: 0.24
Nodes (5): androidx, ClassDetailActivity, AppCompatActivity, Bundle, Uri

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

### Community 31 - "FirebaseAuth"
Cohesion: 0.13
Nodes (11): GeneratedQuestion, AppCompatActivity, Bundle, LeaderboardActivity, AppCompatActivity, Bundle, MaterialQuizActivity, AppCompatActivity (+3 more)

## Knowledge Gaps
- **3 isolated node(s):** `CREDENTIAL`, `ic_launcher-playstore (main)`, `ic_search_bubble (drawable)`
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 38 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **9 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `HomeFragment` connect `Intent` to `.showSnackbar`?**
  _High betweenness centrality (0.063) - this node is a cross-community bridge._
- **What connects `CREDENTIAL`, `ic_launcher-playstore (main)`, `ic_search_bubble (drawable)` to the rest of the system?**
  _3 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Intent` be split into smaller, more focused modules?**
  _Cohesion score 0.09291521486643438 - nodes in this community are weakly interconnected._
- **Should `ScanResultActivity` be split into smaller, more focused modules?**
  _Cohesion score 0.10483870967741936 - nodes in this community are weakly interconnected._
- **Should `FloatingControlService` be split into smaller, more focused modules?**
  _Cohesion score 0.12535612535612536 - nodes in this community are weakly interconnected._
- **Should `.showSnackbar` be split into smaller, more focused modules?**
  _Cohesion score 0.12535612535612536 - nodes in this community are weakly interconnected._
- **Should `FirebaseAuth` be split into smaller, more focused modules?**
  _Cohesion score 0.12615384615384614 - nodes in this community are weakly interconnected._