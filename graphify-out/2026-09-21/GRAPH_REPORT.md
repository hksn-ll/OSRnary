# Graph Report - OSRnary  (2026-09-21)

## Corpus Check
- 50 files · ~105,473 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 513 nodes · 956 edges · 39 communities (28 shown, 8 thin omitted)
- Extraction: 99% EXTRACTED · 1% INFERRED · 0% AMBIGUOUS · INFERRED: 8 edges (avg confidence: 0.86)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `1baa7dfe`
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
- TeacherHomeFragment.kt
- OverviewActivity
- SchoolRepository
- QuizActivity
- QuizEditorActivity
- PdfViewerActivity
- GitHubUpdateHelper.kt
- TextOverlayView
- ProfileFragment.kt
- TeacherLibraryActivity
- QuestManager
- LeaderboardActivity.kt
- .showSnackbar
- WeeklyAssessmentActivity
- DailyQuestsActivity.kt
- QuestDetailsActivity.kt
- HistoryActivity
- ProgressDashboardActivity
- FavoriteDetailActivity.kt
- GabAIApp
- gradlew
- ExampleInstrumentedTest
- Earthquakes: Movement of the Earth's Crust
- ExampleUnitTest
- FavoritesActivity
- ic_launcher-playstore (main)
- ic_search_bubble (drawable)
- DualStackHandler
- XPManager

## God Nodes (most connected - your core abstractions)
1. `OverviewActivity` - 24 edges
2. `AuthActivity` - 20 edges
3. `ClassDetailActivity` - 19 edges
4. `WeeklyAssessmentActivity` - 19 edges
5. `FloatingControlService` - 18 edges
6. `SubjectDetailActivity` - 18 edges
7. `HomeFragment` - 17 edges
8. `QuizEditorActivity` - 17 edges
9. `InitiationActivity` - 14 edges
10. `QuizActivity` - 14 edges

## Surprising Connections (you probably didn't know these)
- `AuthActivity` --references--> `FirebaseAuth`  [EXTRACTED]
  app/src/main/java/com/example/gabai/AuthActivity.kt →   _Bridges community 6 → community 2_

## Import Cycles
- None detected.

## Communities (39 total, 8 thin omitted)

### Community 0 - "Intent"
Cohesion: 0.09
Nodes (19): ActivityMainBinding, HomeFragment, Bundle, Fragment, LayoutInflater, View, ViewGroup, AppCompatActivity (+11 more)

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
Cohesion: 0.10
Nodes (15): android, AchievementsActivity, Badge, AppCompatActivity, Bundle, AppCompatActivity, Bitmap, Bundle (+7 more)

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
Cohesion: 0.15
Nodes (8): AppCompatActivity, Bundle, QuizActivity, AppCompatActivity, Bundle, StudentStats, TeacherPerformanceActivity, com

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
Cohesion: 0.20
Nodes (7): MotionEvent, View, TextOverlayView, WordBox, selectedText, surroundingSentence, Text

### Community 15 - "ProfileFragment.kt"
Cohesion: 0.31
Nodes (7): Bundle, Fragment, LayoutInflater, View, ViewGroup, ProfileFragment, FragmentProfileBinding

### Community 16 - "TeacherLibraryActivity"
Cohesion: 0.42
Nodes (3): AppCompatActivity, Bundle, TeacherLibraryActivity

### Community 18 - "LeaderboardActivity.kt"
Cohesion: 0.53
Nodes (3): AppCompatActivity, Bundle, LeaderboardActivity

### Community 19 - ".showSnackbar"
Cohesion: 0.14
Nodes (7): androidx, ClassDetailActivity, AppCompatActivity, Bundle, Uri, GabAIUtils, Context

### Community 20 - "WeeklyAssessmentActivity"
Cohesion: 0.18
Nodes (8): AssessmentQuestion, AppCompatActivity, Bundle, Button, ProgressBar, TextView, View, WeeklyAssessmentActivity

### Community 21 - "DailyQuestsActivity.kt"
Cohesion: 0.53
Nodes (3): DailyQuestsActivity, AppCompatActivity, Bundle

### Community 22 - "QuestDetailsActivity.kt"
Cohesion: 0.48
Nodes (3): AppCompatActivity, Bundle, QuestDetailsActivity

### Community 23 - "HistoryActivity"
Cohesion: 0.53
Nodes (3): HistoryActivity, AppCompatActivity, Bundle

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

### Community 31 - "FavoritesActivity"
Cohesion: 0.53
Nodes (3): FavoritesActivity, AppCompatActivity, Bundle

## Knowledge Gaps
- **5 isolated node(s):** `OVERVIEW`, `REAL_WORLD`, `DIAGRAMS`, `ic_launcher-playstore (main)`, `ic_search_bubble (drawable)`
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 43 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **8 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **What connects `OVERVIEW`, `REAL_WORLD`, `DIAGRAMS` to the rest of the system?**
  _5 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Intent` be split into smaller, more focused modules?**
  _Cohesion score 0.08879492600422834 - nodes in this community are weakly interconnected._
- **Should `FirebaseAuth` be split into smaller, more focused modules?**
  _Cohesion score 0.14039408866995073 - nodes in this community are weakly interconnected._
- **Should `ScanResultActivity` be split into smaller, more focused modules?**
  _Cohesion score 0.10483870967741936 - nodes in this community are weakly interconnected._
- **Should `FloatingControlService` be split into smaller, more focused modules?**
  _Cohesion score 0.12535612535612536 - nodes in this community are weakly interconnected._
- **Should `OverviewActivity` be split into smaller, more focused modules?**
  _Cohesion score 0.10756302521008404 - nodes in this community are weakly interconnected._
- **Should `GitHubUpdateHelper.kt` be split into smaller, more focused modules?**
  _Cohesion score 0.11742424242424243 - nodes in this community are weakly interconnected._