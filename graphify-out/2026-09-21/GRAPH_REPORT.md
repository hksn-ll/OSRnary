# Graph Report - OSRnary  (2026-09-21)

## Corpus Check
- 50 files · ~106,156 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 523 nodes · 976 edges · 40 communities (29 shown, 8 thin omitted)
- Extraction: 99% EXTRACTED · 1% INFERRED · 0% AMBIGUOUS · INFERRED: 8 edges (avg confidence: 0.86)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `47377c65`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- Intent
- .showSnackbar
- FirebaseAuth
- ManageClassesActivity
- ScanResultActivity
- FloatingControlService
- AuthActivity
- TeacherHomeFragment.kt
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
- DailyQuestsActivity.kt
- HistoryActivity
- FavoritesActivity
- ProgressDashboardActivity
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
- LeaderboardActivity.kt

## God Nodes (most connected - your core abstractions)
1. `OverviewActivity` - 24 edges
2. `AuthActivity` - 20 edges
3. `ClassDetailActivity` - 19 edges
4. `WeeklyAssessmentActivity` - 19 edges
5. `FloatingControlService` - 18 edges
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

## Communities (40 total, 8 thin omitted)

### Community 0 - "Intent"
Cohesion: 0.09
Nodes (19): ActivityMainBinding, HomeFragment, Bundle, Fragment, LayoutInflater, View, ViewGroup, AppCompatActivity (+11 more)

### Community 1 - ".showSnackbar"
Cohesion: 0.12
Nodes (11): GabAIUtils, Context, AppCompatActivity, Bundle, Button, LinearLayout, ProgressBar, TextView (+3 more)

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
Cohesion: 0.13
Nodes (15): FloatingControlService, Callback, OnTouchListener, Bitmap, Callback, MotionEvent, OnTouchListener, View (+7 more)

### Community 6 - "AuthActivity"
Cohesion: 0.23
Nodes (5): ActivityAuthBinding, AuthActivity, AppCompatActivity, Bundle, FirebaseFirestore

### Community 7 - "TeacherHomeFragment.kt"
Cohesion: 0.27
Nodes (7): Bundle, Fragment, LayoutInflater, View, ViewGroup, TeacherHomeFragment, FragmentTeacherHomeBinding

### Community 8 - "OverviewActivity"
Cohesion: 0.11
Nodes (13): AppCompatActivity, Bitmap, Bundle, TextView, OverviewActivity, WebViewClient, VisualMode, DIAGRAMS (+5 more)

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
Nodes (7): Bundle, Fragment, LayoutInflater, View, ViewGroup, ProfileFragment, FragmentProfileBinding

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

### Community 24 - "ProgressDashboardActivity"
Cohesion: 0.22
Nodes (6): AppCompatActivity, Bundle, ProgressDashboardActivity, AppCompatActivity, Bundle, QuizHistoryActivity

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

### Community 36 - "QuestDetailsActivity.kt"
Cohesion: 0.48
Nodes (3): AppCompatActivity, Bundle, QuestDetailsActivity

### Community 37 - "LeaderboardActivity.kt"
Cohesion: 0.53
Nodes (3): AppCompatActivity, Bundle, LeaderboardActivity

## Knowledge Gaps
- **5 isolated node(s):** `OVERVIEW`, `REAL_WORLD`, `DIAGRAMS`, `ic_launcher-playstore (main)`, `ic_search_bubble (drawable)`
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 45 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **8 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `TextOverlayView` connect `TextOverlayView` to `ScanResultActivity`?**
  _High betweenness centrality (0.061) - this node is a cross-community bridge._
- **What connects `OVERVIEW`, `REAL_WORLD`, `DIAGRAMS` to the rest of the system?**
  _5 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Intent` be split into smaller, more focused modules?**
  _Cohesion score 0.08879492600422834 - nodes in this community are weakly interconnected._
- **Should `.showSnackbar` be split into smaller, more focused modules?**
  _Cohesion score 0.11954022988505747 - nodes in this community are weakly interconnected._
- **Should `FirebaseAuth` be split into smaller, more focused modules?**
  _Cohesion score 0.14039408866995073 - nodes in this community are weakly interconnected._
- **Should `FloatingControlService` be split into smaller, more focused modules?**
  _Cohesion score 0.13333333333333333 - nodes in this community are weakly interconnected._
- **Should `OverviewActivity` be split into smaller, more focused modules?**
  _Cohesion score 0.10756302521008404 - nodes in this community are weakly interconnected._