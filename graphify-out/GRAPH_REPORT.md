# Graph Report - OSRnary  (2026-10-04)

## Corpus Check
- 51 files · ~108,621 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 597 nodes · 1133 edges · 40 communities (31 shown, 6 thin omitted)
- Extraction: 100% EXTRACTED · 0% INFERRED · 0% AMBIGUOUS · INFERRED: 4 edges (avg confidence: 0.88)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `c9533617`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- HomeFragment
- GabAiAnimatedLogoView
- InitiationActivity
- ManageClassesActivity
- ScanResultActivity
- FloatingControlService
- AuthActivity
- SplashActivity.kt
- OverviewActivity
- SchoolRepository
- QuizActivity
- FirebaseAuth
- PdfViewerActivity
- GitHubUpdateHelper.kt
- TextOverlayView
- ProfileFragment.kt
- launch.ps1
- CameraActivity.kt
- TeacherHomeFragment
- ClassDetailActivity
- WeeklyAssessmentActivity
- Intent
- DailyQuestsActivity
- GabAIUtils
- FavoriteDetailActivity.kt
- GabAIApp
- gradlew
- ExampleInstrumentedTest
- Earthquakes: Movement of the Earth's Crust
- ExampleUnitTest
- FavoritesActivity.kt
- ic_launcher-playstore (main)
- ic_search_bubble (drawable)
- DualStackHandler
- QuestManager
- HistoryActivity.kt
- QuizHistoryActivity

## God Nodes (most connected - your core abstractions)
1. `OverviewActivity` - 30 edges
2. `FloatingControlService` - 21 edges
3. `AuthActivity` - 20 edges
4. `WeeklyAssessmentActivity` - 20 edges
5. `ClassDetailActivity` - 19 edges
6. `SubjectDetailActivity` - 18 edges
7. `TextOverlayView` - 18 edges
8. `MainActivity` - 17 edges
9. `QuizEditorActivity` - 17 edges
10. `ScanResultActivity` - 17 edges

## Surprising Connections (you probably didn't know these)
- `AuthActivity` --references--> `FirebaseAuth`  [EXTRACTED]
  app/src/main/java/com/example/gabai/AuthActivity.kt →   _Bridges community 6 → community 11_
- `HomeFragment` --calls--> `Intent`  [EXTRACTED]
  app/src/main/java/com/example/gabai/HomeFragment.kt →   _Bridges community 21 → community 0_

## Import Cycles
- None detected.

## Communities (40 total, 6 thin omitted)

### Community 0 - "HomeFragment"
Cohesion: 0.14
Nodes (10): HomeFragment, Bundle, Fragment, LayoutInflater, View, ViewGroup, Context, SharedPreferences (+2 more)

### Community 1 - "GabAiAnimatedLogoView"
Cohesion: 0.23
Nodes (7): AnimatorSet, GabAiAnimatedLogoView, AnimatorListenerAdapter, android, Canvas, View, ValueAnimator

### Community 2 - "InitiationActivity"
Cohesion: 0.15
Nodes (9): GeneratedQuestion, InitiationActivity, InitiationMaterial, AppCompatActivity, Bundle, FirebaseFirestore, AppCompatActivity, Bundle (+1 more)

### Community 3 - "ManageClassesActivity"
Cohesion: 0.25
Nodes (3): AppCompatActivity, Bundle, ManageClassesActivity

### Community 4 - "ScanResultActivity"
Cohesion: 0.12
Nodes (12): AchievementsActivity, Badge, AppCompatActivity, Bundle, android, AppCompatActivity, Bitmap, Bundle (+4 more)

### Community 5 - "FloatingControlService"
Cohesion: 0.11
Nodes (15): FloatingControlService, Callback, OnTouchListener, Bitmap, Callback, MotionEvent, OnTouchListener, View (+7 more)

### Community 6 - "AuthActivity"
Cohesion: 0.23
Nodes (5): ActivityAuthBinding, AuthActivity, AppCompatActivity, Bundle, FirebaseFirestore

### Community 7 - "SplashActivity.kt"
Cohesion: 0.52
Nodes (3): AppCompatActivity, Bundle, SplashActivity

### Community 8 - "OverviewActivity"
Cohesion: 0.08
Nodes (19): AppCompatActivity, Bitmap, Bundle, TextView, OverviewActivity, WebViewClient, UtteranceProgressListener, TtsPlaybackState (+11 more)

### Community 9 - "SchoolRepository"
Cohesion: 0.29
Nodes (3): School, SchoolAdminInvite, SchoolRepository

### Community 10 - "QuizActivity"
Cohesion: 0.24
Nodes (4): AppCompatActivity, Bundle, QuizActivity, com

### Community 11 - "FirebaseAuth"
Cohesion: 0.15
Nodes (10): AppCompatActivity, Bundle, LeaderboardActivity, ClassInfo, AppCompatActivity, Bundle, LinearLayout, TextView (+2 more)

### Community 12 - "PdfViewerActivity"
Cohesion: 0.29
Nodes (4): AppCompatActivity, Bundle, TextView, PdfViewerActivity

### Community 13 - "GitHubUpdateHelper.kt"
Cohesion: 0.20
Nodes (10): GitHubUpdateHelper, Callback, Callback, Callback, Activity, Callback, Context, VersionInfo (+2 more)

### Community 14 - "TextOverlayView"
Cohesion: 0.14
Nodes (9): android, Canvas, MotionEvent, View, TextOverlayView, WordBox, selectedText, surroundingSentence (+1 more)

### Community 15 - "ProfileFragment.kt"
Cohesion: 0.27
Nodes (7): Bundle, Fragment, LayoutInflater, View, ViewGroup, ProfileFragment, FragmentProfileBinding

### Community 16 - "launch.ps1"
Cohesion: 0.20
Nodes (7): AppCompatActivity, Bundle, TeacherLibraryActivity, AppCompatActivity, Bundle, StudentStats, TeacherPerformanceActivity

### Community 17 - "CameraActivity.kt"
Cohesion: 0.29
Nodes (6): CameraActivity, OnImageSavedCallback, AppCompatActivity, Bundle, ImageCapture, ImageCaptureException

### Community 18 - "TeacherHomeFragment"
Cohesion: 0.25
Nodes (7): Bundle, Fragment, LayoutInflater, View, ViewGroup, TeacherHomeFragment, FragmentTeacherHomeBinding

### Community 19 - "ClassDetailActivity"
Cohesion: 0.09
Nodes (13): androidx, ClassDetailActivity, AppCompatActivity, Bundle, Uri, AppCompatActivity, Bundle, Button (+5 more)

### Community 20 - "WeeklyAssessmentActivity"
Cohesion: 0.17
Nodes (9): AssessmentQuestion, AppCompatActivity, Bundle, Button, ProgressBar, TextView, View, WeeklyAssessmentActivity (+1 more)

### Community 21 - "Intent"
Cohesion: 0.08
Nodes (20): ActivityMainBinding, AppCompatActivity, Bundle, LibraryActivity, android, AppCompatActivity, Bundle, Fragment (+12 more)

### Community 22 - "DailyQuestsActivity"
Cohesion: 0.48
Nodes (3): DailyQuestsActivity, AppCompatActivity, Bundle

### Community 23 - "GabAIUtils"
Cohesion: 0.18
Nodes (7): GabAIUtils, Activity, android, Context, ProgressBar, View, eightbitlab

### Community 25 - "FavoriteDetailActivity.kt"
Cohesion: 0.60
Nodes (3): FavoriteDetailActivity, AppCompatActivity, Bundle

### Community 27 - "gradlew"
Cohesion: 0.83
Nodes (3): gradlew script, die(), warn()

### Community 29 - "Earthquakes: Movement of the Earth's Crust"
Cohesion: 1.00
Nodes (3): Earthquakes: Movement of the Earth's Crust, Seismic Waves, Tectonic Plates and Fault Lines

### Community 31 - "FavoritesActivity.kt"
Cohesion: 0.48
Nodes (3): FavoritesActivity, AppCompatActivity, Bundle

### Community 36 - "QuestManager"
Cohesion: 0.47
Nodes (3): Context, SharedPreferences, QuestManager

### Community 37 - "HistoryActivity.kt"
Cohesion: 0.48
Nodes (3): HistoryActivity, AppCompatActivity, Bundle

### Community 39 - "QuizHistoryActivity"
Cohesion: 0.48
Nodes (3): AppCompatActivity, Bundle, QuizHistoryActivity

## Knowledge Gaps
- **9 isolated node(s):** `IDLE`, `LOADING`, `PLAYING`, `PAUSED`, `OVERVIEW` (+4 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 56 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **6 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `TextOverlayView` connect `TextOverlayView` to `ScanResultActivity`?**
  _High betweenness centrality (0.061) - this node is a cross-community bridge._
- **What connects `IDLE`, `LOADING`, `PLAYING` to the rest of the system?**
  _9 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `HomeFragment` be split into smaller, more focused modules?**
  _Cohesion score 0.135632183908046 - nodes in this community are weakly interconnected._
- **Should `InitiationActivity` be split into smaller, more focused modules?**
  _Cohesion score 0.1455026455026455 - nodes in this community are weakly interconnected._
- **Should `ScanResultActivity` be split into smaller, more focused modules?**
  _Cohesion score 0.12043010752688173 - nodes in this community are weakly interconnected._
- **Should `FloatingControlService` be split into smaller, more focused modules?**
  _Cohesion score 0.11494252873563218 - nodes in this community are weakly interconnected._
- **Should `OverviewActivity` be split into smaller, more focused modules?**
  _Cohesion score 0.07591836734693877 - nodes in this community are weakly interconnected._