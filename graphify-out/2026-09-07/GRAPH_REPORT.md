# Graph Report - OSRnary  (2026-09-07)

## Corpus Check
- 47 files · ~65,812 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 424 nodes · 790 edges · 38 communities (27 shown, 8 thin omitted)
- Extraction: 99% EXTRACTED · 1% INFERRED · 0% AMBIGUOUS · INFERRED: 8 edges (avg confidence: 0.86)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `66a0d856`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- Intent
- SubjectDetailActivity
- InitiationActivity
- FirebaseAuth
- ScanResultActivity
- FloatingControlService
- AuthActivity
- TeacherHomeFragment.kt
- OverviewActivity
- .showSnackbar
- QuizActivity
- QuizEditorActivity
- PdfViewerActivity
- CameraActivity.kt
- TextOverlayView
- ProfileFragment.kt
- launch.ps1
- QuestManager
- ProgressDashboardActivity.kt
- LeaderboardActivity.kt
- DailyQuestsActivity.kt
- FavoritesActivity
- HistoryActivity
- StudentSubjectActivity.kt
- FavoriteDetailActivity.kt
- GabAIApp
- gradlew
- ExampleInstrumentedTest
- Earthquakes: Movement of the Earth's Crust
- ExampleUnitTest
- Graphify Knowledge Graph Rule
- ic_launcher-playstore (main)
- ic_search_bubble (drawable)
- LibraryActivity
- user_preferences.md

## God Nodes (most connected - your core abstractions)
1. `AuthActivity` - 19 edges
2. `FloatingControlService` - 18 edges
3. `SubjectDetailActivity` - 18 edges
4. `HomeFragment` - 15 edges
5. `InitiationActivity` - 14 edges
6. `QuizActivity` - 14 edges
7. `ClassDetailActivity` - 13 edges
8. `OverviewActivity` - 13 edges
9. `ManageClassesActivity` - 12 edges
10. `QuizEditorActivity` - 12 edges

## Surprising Connections (you probably didn't know these)
- `Graphify Workflow Command` --references--> `Graphify Knowledge Graph Rule`  [EXTRACTED]
  .agents/workflows/graphify.md → .agents/rules/graphify.md

## Import Cycles
- None detected.

## Communities (38 total, 8 thin omitted)

### Community 0 - "Intent"
Cohesion: 0.09
Nodes (18): ActivityMainBinding, HomeFragment, Bundle, Fragment, LayoutInflater, View, ViewGroup, AppCompatActivity (+10 more)

### Community 1 - "SubjectDetailActivity"
Cohesion: 0.17
Nodes (7): AppCompatActivity, Bundle, LinearLayout, TextView, Uri, SubjectDetailActivity, Canvas

### Community 2 - "InitiationActivity"
Cohesion: 0.12
Nodes (12): GabAIUtils, Context, GeneratedQuestion, InitiationActivity, InitiationMaterial, AppCompatActivity, Bundle, FirebaseFirestore (+4 more)

### Community 3 - "FirebaseAuth"
Cohesion: 0.12
Nodes (10): AppCompatActivity, Bundle, ManageClassesActivity, AppCompatActivity, Bundle, QuizHistoryActivity, AppCompatActivity, Bundle (+2 more)

### Community 4 - "ScanResultActivity"
Cohesion: 0.13
Nodes (12): AchievementsActivity, Badge, AppCompatActivity, Bundle, android, AppCompatActivity, Bitmap, Bundle (+4 more)

### Community 5 - "FloatingControlService"
Cohesion: 0.14
Nodes (14): FloatingControlService, Callback, OnTouchListener, Bitmap, MotionEvent, OnTouchListener, View, IBinder (+6 more)

### Community 6 - "AuthActivity"
Cohesion: 0.24
Nodes (5): ActivityAuthBinding, AuthActivity, AppCompatActivity, Bundle, FirebaseFirestore

### Community 7 - "TeacherHomeFragment.kt"
Cohesion: 0.27
Nodes (7): Bundle, Fragment, LayoutInflater, View, ViewGroup, TeacherHomeFragment, FragmentTeacherHomeBinding

### Community 8 - "OverviewActivity"
Cohesion: 0.18
Nodes (6): android, AppCompatActivity, Bundle, OverviewActivity, WebViewClient, TextToSpeech

### Community 9 - ".showSnackbar"
Cohesion: 0.22
Nodes (6): androidx, ClassDetailActivity, AppCompatActivity, Bundle, Uri, Button

### Community 10 - "QuizActivity"
Cohesion: 0.25
Nodes (4): AppCompatActivity, Bundle, QuizActivity, com

### Community 11 - "QuizEditorActivity"
Cohesion: 0.30
Nodes (4): AppCompatActivity, Bundle, LinearLayout, QuizEditorActivity

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

### Community 18 - "ProgressDashboardActivity.kt"
Cohesion: 0.48
Nodes (3): AppCompatActivity, Bundle, ProgressDashboardActivity

### Community 19 - "LeaderboardActivity.kt"
Cohesion: 0.53
Nodes (3): AppCompatActivity, Bundle, LeaderboardActivity

### Community 21 - "DailyQuestsActivity.kt"
Cohesion: 0.53
Nodes (3): DailyQuestsActivity, AppCompatActivity, Bundle

### Community 22 - "FavoritesActivity"
Cohesion: 0.53
Nodes (3): FavoritesActivity, AppCompatActivity, Bundle

### Community 23 - "HistoryActivity"
Cohesion: 0.53
Nodes (3): HistoryActivity, AppCompatActivity, Bundle

### Community 24 - "StudentSubjectActivity.kt"
Cohesion: 0.53
Nodes (3): AppCompatActivity, Bundle, StudentSubjectActivity

### Community 25 - "FavoriteDetailActivity.kt"
Cohesion: 0.60
Nodes (3): FavoriteDetailActivity, AppCompatActivity, Bundle

### Community 27 - "gradlew"
Cohesion: 0.83
Nodes (3): gradlew script, die(), warn()

### Community 29 - "Earthquakes: Movement of the Earth's Crust"
Cohesion: 1.00
Nodes (3): Earthquakes: Movement of the Earth's Crust, Seismic Waves, Tectonic Plates and Fault Lines

### Community 52 - "LibraryActivity"
Cohesion: 0.43
Nodes (3): AppCompatActivity, Bundle, LibraryActivity

## Knowledge Gaps
- **5 isolated node(s):** `User Preferences & Constraints`, `Graphify Knowledge Graph Rule`, `Graphify Workflow Command`, `ic_launcher-playstore (main)`, `ic_search_bubble (drawable)`
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 31 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **8 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `AuthActivity` connect `AuthActivity` to `FirebaseAuth`?**
  _High betweenness centrality (0.054) - this node is a cross-community bridge._
- **Why does `SubjectDetailActivity` connect `SubjectDetailActivity` to `.showSnackbar`, `InitiationActivity`?**
  _High betweenness centrality (0.054) - this node is a cross-community bridge._
- **Why does `HomeFragment` connect `Intent` to `.showSnackbar`?**
  _High betweenness centrality (0.053) - this node is a cross-community bridge._
- **What connects `User Preferences & Constraints`, `Graphify Knowledge Graph Rule`, `Graphify Workflow Command` to the rest of the system?**
  _5 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Intent` be split into smaller, more focused modules?**
  _Cohesion score 0.09175377468060394 - nodes in this community are weakly interconnected._
- **Should `InitiationActivity` be split into smaller, more focused modules?**
  _Cohesion score 0.11586452762923351 - nodes in this community are weakly interconnected._
- **Should `FirebaseAuth` be split into smaller, more focused modules?**
  _Cohesion score 0.11965811965811966 - nodes in this community are weakly interconnected._