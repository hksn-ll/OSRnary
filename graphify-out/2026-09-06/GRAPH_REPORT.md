# Graph Report - OSRnary  (2026-09-06)

## Corpus Check
- Corpus is ~41,450 words - fits in a single context window. You may not need a graph.

## Summary
- 435 nodes · 778 edges · 52 communities (27 shown, 22 thin omitted)
- Extraction: 99% EXTRACTED · 1% INFERRED · 0% AMBIGUOUS · INFERRED: 8 edges (avg confidence: 0.86)
- Token cost: 1,200 input · 400 output

## Community Hubs (Navigation)
- Home & Class Join Fragment
- Global Utility & Dialog Helpers
- Question Initiation & Teacher Config
- Leaderboard & Ranking Activity
- Achievements & Badges System
- Floating Screen Capture Service
- Authentication & Navigation Flow
- Main Activity & View Binding
- Overview Dashboard Activity
- Class Details & Management
- Quiz Taking & Question Evaluation
- Quiz Creator & Editor
- PDF Document Viewer Activity
- Camera Capture & OCR Preview
- Text Overlay & Touch Interaction
- User Profile Fragment
- Teacher Material Library
- Quest Progress Management
- Student Progress Dashboard
- Quest Details & Objectives
- Teacher Performance & Student Analytics
- Daily Quests Interface
- Favorites Management Activity
- Scan & Study History Activity
- Student Subject Navigation
- Favorite Item Detail View
- Application Lifecycle & Initialization
- Gradle Wrapper Script
- Android Instrumented Tests
- Sample Geology Curriculum Material
- Unit Tests Suite
- Graphify Rule & Workflow Config
- Play Store App Icon
- Search Bubble Drawable Asset
- HDPI Foreground Launcher Asset
- HDPI Launcher Icon Asset
- HDPI Round Launcher Asset
- MDPI Foreground Launcher Asset
- MDPI Launcher Icon Asset
- MDPI Round Launcher Asset
- XHDPI Foreground Launcher Asset
- XHDPI Launcher Icon Asset
- XHDPI Round Launcher Asset
- XXHDPI Foreground Launcher Asset
- XXHDPI Launcher Icon Asset
- XXHDPI Round Launcher Asset
- XXXHDPI Foreground Launcher Asset
- XXXHDPI Launcher Icon Asset
- XXXHDPI Round Launcher Asset

## God Nodes (most connected - your core abstractions)
1. `AuthActivity` - 19 edges
2. `FloatingControlService` - 18 edges
3. `SubjectDetailActivity` - 18 edges
4. `HomeFragment` - 14 edges
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

## Communities (52 total, 22 thin omitted)

### Community 0 - "Home & Class Join Fragment"
Cohesion: 0.10
Nodes (16): HomeFragment, Bundle, Fragment, LayoutInflater, View, ViewGroup, AppCompatActivity, Bundle (+8 more)

### Community 1 - "Global Utility & Dialog Helpers"
Cohesion: 0.12
Nodes (10): GabAIUtils, Context, AppCompatActivity, Bundle, LinearLayout, TextView, Uri, SubjectDetailActivity (+2 more)

### Community 2 - "Question Initiation & Teacher Config"
Cohesion: 0.15
Nodes (9): GeneratedQuestion, InitiationActivity, InitiationMaterial, AppCompatActivity, Bundle, FirebaseFirestore, AppCompatActivity, Bundle (+1 more)

### Community 3 - "Leaderboard & Ranking Activity"
Cohesion: 0.12
Nodes (10): AppCompatActivity, Bundle, LeaderboardActivity, AppCompatActivity, Bundle, ManageClassesActivity, AppCompatActivity, Bundle (+2 more)

### Community 4 - "Achievements & Badges System"
Cohesion: 0.13
Nodes (12): AchievementsActivity, Badge, AppCompatActivity, Bundle, android, AppCompatActivity, Bitmap, Bundle (+4 more)

### Community 5 - "Floating Screen Capture Service"
Cohesion: 0.14
Nodes (14): FloatingControlService, Callback, OnTouchListener, Bitmap, MotionEvent, OnTouchListener, View, IBinder (+6 more)

### Community 6 - "Authentication & Navigation Flow"
Cohesion: 0.24
Nodes (5): ActivityAuthBinding, AuthActivity, AppCompatActivity, Bundle, FirebaseFirestore

### Community 7 - "Main Activity & View Binding"
Cohesion: 0.18
Nodes (12): ActivityMainBinding, AppCompatActivity, Bundle, Fragment, MainActivity, Bundle, Fragment, LayoutInflater (+4 more)

### Community 8 - "Overview Dashboard Activity"
Cohesion: 0.18
Nodes (6): android, AppCompatActivity, Bundle, OverviewActivity, WebViewClient, TextToSpeech

### Community 9 - "Class Details & Management"
Cohesion: 0.22
Nodes (6): androidx, ClassDetailActivity, AppCompatActivity, Bundle, Uri, Button

### Community 10 - "Quiz Taking & Question Evaluation"
Cohesion: 0.25
Nodes (4): AppCompatActivity, Bundle, QuizActivity, com

### Community 11 - "Quiz Creator & Editor"
Cohesion: 0.30
Nodes (4): AppCompatActivity, Bundle, LinearLayout, QuizEditorActivity

### Community 12 - "PDF Document Viewer Activity"
Cohesion: 0.29
Nodes (4): AppCompatActivity, Bundle, TextView, PdfViewerActivity

### Community 13 - "Camera Capture & OCR Preview"
Cohesion: 0.29
Nodes (6): CameraActivity, OnImageSavedCallback, AppCompatActivity, Bundle, ImageCapture, ImageCaptureException

### Community 14 - "Text Overlay & Touch Interaction"
Cohesion: 0.26
Nodes (5): MotionEvent, View, TextOverlayView, WordBox, Text

### Community 15 - "User Profile Fragment"
Cohesion: 0.31
Nodes (7): Bundle, Fragment, LayoutInflater, View, ViewGroup, ProfileFragment, FragmentProfileBinding

### Community 16 - "Teacher Material Library"
Cohesion: 0.42
Nodes (3): AppCompatActivity, Bundle, TeacherLibraryActivity

### Community 18 - "Student Progress Dashboard"
Cohesion: 0.48
Nodes (3): AppCompatActivity, Bundle, ProgressDashboardActivity

### Community 19 - "Quest Details & Objectives"
Cohesion: 0.48
Nodes (3): AppCompatActivity, Bundle, QuestDetailsActivity

### Community 20 - "Teacher Performance & Student Analytics"
Cohesion: 0.48
Nodes (4): AppCompatActivity, Bundle, StudentStats, TeacherPerformanceActivity

### Community 21 - "Daily Quests Interface"
Cohesion: 0.53
Nodes (3): DailyQuestsActivity, AppCompatActivity, Bundle

### Community 22 - "Favorites Management Activity"
Cohesion: 0.53
Nodes (3): FavoritesActivity, AppCompatActivity, Bundle

### Community 23 - "Scan & Study History Activity"
Cohesion: 0.53
Nodes (3): HistoryActivity, AppCompatActivity, Bundle

### Community 24 - "Student Subject Navigation"
Cohesion: 0.53
Nodes (3): AppCompatActivity, Bundle, StudentSubjectActivity

### Community 25 - "Favorite Item Detail View"
Cohesion: 0.60
Nodes (3): FavoriteDetailActivity, AppCompatActivity, Bundle

### Community 27 - "Gradle Wrapper Script"
Cohesion: 0.83
Nodes (3): gradlew script, die(), warn()

### Community 29 - "Sample Geology Curriculum Material"
Cohesion: 1.00
Nodes (3): Earthquakes: Movement of the Earth's Crust, Seismic Waves, Tectonic Plates and Fault Lines

## Knowledge Gaps
- **19 isolated node(s):** `Graphify Knowledge Graph Rule`, `Graphify Workflow Command`, `ic_launcher-playstore (main)`, `ic_search_bubble (drawable)`, `ic_launcher (mipmap-hdpi)` (+14 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 44 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **22 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `SubjectDetailActivity` connect `Global Utility & Dialog Helpers` to `Class Details & Management`?**
  _High betweenness centrality (0.051) - this node is a cross-community bridge._
- **Why does `HomeFragment` connect `Home & Class Join Fragment` to `Global Utility & Dialog Helpers`, `Main Activity & View Binding`?**
  _High betweenness centrality (0.049) - this node is a cross-community bridge._
- **Why does `AuthActivity` connect `Authentication & Navigation Flow` to `Leaderboard & Ranking Activity`?**
  _High betweenness centrality (0.048) - this node is a cross-community bridge._
- **What connects `Graphify Knowledge Graph Rule`, `Graphify Workflow Command`, `ic_launcher-playstore (main)` to the rest of the system?**
  _19 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Home & Class Join Fragment` be split into smaller, more focused modules?**
  _Cohesion score 0.09759759759759759 - nodes in this community are weakly interconnected._
- **Should `Global Utility & Dialog Helpers` be split into smaller, more focused modules?**
  _Cohesion score 0.12413793103448276 - nodes in this community are weakly interconnected._
- **Should `Question Initiation & Teacher Config` be split into smaller, more focused modules?**
  _Cohesion score 0.1455026455026455 - nodes in this community are weakly interconnected._