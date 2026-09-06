# Graph Report - OSRnary  (2026-09-07)

## Corpus Check
- 46 files · ~78,981 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 425 nodes · 792 edges · 36 communities (26 shown, 7 thin omitted)
- Extraction: 99% EXTRACTED · 1% INFERRED · 0% AMBIGUOUS · INFERRED: 8 edges (avg confidence: 0.86)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `2b5bab44`
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
- .onCreate
- OverviewActivity
- .showSnackbar
- QuizActivity
- QuizEditorActivity
- PdfViewerActivity
- CameraActivity.kt
- TextOverlayView
- ProfileFragment.kt
- TeacherLibraryActivity
- QuestManager
- QuestDetailsActivity.kt
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
- ic_launcher-playstore (main)
- ic_search_bubble (drawable)
- DualStackHandler
- LibraryActivity

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
- `AuthActivity` --references--> `FirebaseAuth`  [EXTRACTED]
  app/src/main/java/com/example/gabai/AuthActivity.kt →   _Bridges community 6 → community 3_
- `SubjectDetailActivity` --references--> `Button`  [EXTRACTED]
  app/src/main/java/com/example/gabai/SubjectDetailActivity.kt →   _Bridges community 1 → community 9_
- `SubjectDetailActivity` --references--> `ProgressBar`  [EXTRACTED]
  app/src/main/java/com/example/gabai/SubjectDetailActivity.kt →   _Bridges community 1 → community 2_

## Import Cycles
- None detected.

## Communities (36 total, 7 thin omitted)

### Community 0 - "Intent"
Cohesion: 0.10
Nodes (16): HomeFragment, Bundle, Fragment, LayoutInflater, View, ViewGroup, AppCompatActivity, Bundle (+8 more)

### Community 1 - "SubjectDetailActivity"
Cohesion: 0.17
Nodes (7): AppCompatActivity, Bundle, LinearLayout, TextView, Uri, SubjectDetailActivity, Canvas

### Community 2 - "InitiationActivity"
Cohesion: 0.12
Nodes (12): GabAIUtils, Context, GeneratedQuestion, InitiationActivity, InitiationMaterial, AppCompatActivity, Bundle, FirebaseFirestore (+4 more)

### Community 3 - "FirebaseAuth"
Cohesion: 0.12
Nodes (10): AppCompatActivity, Bundle, LeaderboardActivity, AppCompatActivity, Bundle, ManageClassesActivity, AppCompatActivity, Bundle (+2 more)

### Community 4 - "ScanResultActivity"
Cohesion: 0.13
Nodes (12): AchievementsActivity, Badge, AppCompatActivity, Bundle, android, AppCompatActivity, Bitmap, Bundle (+4 more)

### Community 5 - "FloatingControlService"
Cohesion: 0.14
Nodes (14): FloatingControlService, Callback, OnTouchListener, Bitmap, MotionEvent, OnTouchListener, View, IBinder (+6 more)

### Community 6 - "AuthActivity"
Cohesion: 0.24
Nodes (5): ActivityAuthBinding, AuthActivity, AppCompatActivity, Bundle, FirebaseFirestore

### Community 7 - ".onCreate"
Cohesion: 0.16
Nodes (12): ActivityMainBinding, AppCompatActivity, Bundle, Fragment, MainActivity, Bundle, Fragment, LayoutInflater (+4 more)

### Community 8 - "OverviewActivity"
Cohesion: 0.18
Nodes (6): android, AppCompatActivity, Bundle, OverviewActivity, WebViewClient, TextToSpeech

### Community 9 - ".showSnackbar"
Cohesion: 0.22
Nodes (6): androidx, ClassDetailActivity, AppCompatActivity, Bundle, Uri, Button

### Community 10 - "QuizActivity"
Cohesion: 0.15
Nodes (8): AppCompatActivity, Bundle, QuizActivity, AppCompatActivity, Bundle, StudentStats, TeacherPerformanceActivity, com

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

### Community 16 - "TeacherLibraryActivity"
Cohesion: 0.42
Nodes (3): AppCompatActivity, Bundle, TeacherLibraryActivity

### Community 18 - "QuestDetailsActivity.kt"
Cohesion: 0.48
Nodes (3): AppCompatActivity, Bundle, QuestDetailsActivity

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

### Community 52 - "LibraryActivity"
Cohesion: 0.43
Nodes (3): AppCompatActivity, Bundle, LibraryActivity

## Knowledge Gaps
- **2 isolated node(s):** `ic_launcher-playstore (main)`, `ic_search_bubble (drawable)`
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 30 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **7 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `AuthActivity` connect `AuthActivity` to `FirebaseAuth`?**
  _High betweenness centrality (0.053) - this node is a cross-community bridge._
- **Why does `SubjectDetailActivity` connect `SubjectDetailActivity` to `.showSnackbar`, `InitiationActivity`?**
  _High betweenness centrality (0.053) - this node is a cross-community bridge._
- **Why does `HomeFragment` connect `Intent` to `.showSnackbar`, `.onCreate`?**
  _High betweenness centrality (0.053) - this node is a cross-community bridge._
- **What connects `ic_launcher-playstore (main)`, `ic_search_bubble (drawable)` to the rest of the system?**
  _2 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Intent` be split into smaller, more focused modules?**
  _Cohesion score 0.1 - nodes in this community are weakly interconnected._
- **Should `InitiationActivity` be split into smaller, more focused modules?**
  _Cohesion score 0.11586452762923351 - nodes in this community are weakly interconnected._
- **Should `FirebaseAuth` be split into smaller, more focused modules?**
  _Cohesion score 0.1164021164021164 - nodes in this community are weakly interconnected._