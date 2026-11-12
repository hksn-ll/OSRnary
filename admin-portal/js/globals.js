/**
 * GabAI Admin Portal - Global Shared Environment & State
 * Declares all shared application state on the global window scope.
 */

// Firebase SDK & Service Global References
var app = null;
var auth = null;
var db = null;
var initializeApp, deleteApp;
var getAuth, signInWithEmailAndPassword, createUserWithEmailAndPassword, signOut, onAuthStateChanged, sendPasswordResetEmail;
var getFirestore, collection, doc, getDoc, getDocs, setDoc, addDoc, updateDoc, deleteDoc, onSnapshot, query, where, orderBy, arrayUnion, arrayRemove;

var currentAdminUser = null;
var stopRealtimeListeners = function() {};
var startRealtimeListeners = function() {};
var isSuperAdmin = false;
var isSchoolAdmin = false;
var selectedJurisdiction = "ALL";
var teacherFilterMode = "ALL"; // ALL | PENDING | APPROVED
var searchQuery = "";
var selectedGradeFilter = "ALL"; // ALL | Grade 7 | Grade 8 | Grade 9 | Grade 10
var selectedStatusFilter = "ALL"; // ALL | PENDING | APPROVED
var selectedSubjectFilter = "ALL";
var selectedDistrictFilter = "ALL"; // ALL | District 1 | District 2
var selectedGenderFilter = "ALL"; // ALL | male | female
var schoolRankingSearchQuery = "";
var schoolRankingFilterChip = "ALL"; // ALL | District 1 | District 2 | LEADERS | DEVELOPING | SUPPORT
var schoolRankingSortCol = "rank"; // rank | name | district | enrolled | submissions | mps
var schoolRankingSortAsc = true;

var currentVaultRecords = [];
var currentVaultTypeFilter = "ALL";
var currentVaultStatusFilter = "ALL";
var currentVaultSearch = "";

var cachedUsers = [];
var cachedTeachers = [];
var cachedClasses = [];
var activeInspectClassId = null;
var currentQrModalData = null;
var learnerSectionFilter = "ALL";
var cachedPendingStudents = [];
var cachedSubjects = [];
var cachedMaterials = [];
var cachedRawMaterials = [];
var cachedLegacyMaterials = [];
var cachedWeeklyAssessments = [];
var cachedDivisionTimedAssessments = [];
var cachedTimedSubmissions = [];
var cachedSubmissions = [];
var cachedSchoolInvites = [];
var cachedAnnouncements = [];
var currentViewingAnnouncementId = null;
var activeRepliesFilter = "ALL";
var cachedRepliesForCurrent = [];
var unsubRepliesCurrent = null;
var activeViewsFilter = "ALL";
var cachedViewsForCurrent = [];
var unsubViewsCurrent = null;
var activeInspectorType = null;
var activeInspectorMaterialId = null;
var activeInspectorAssessmentId = null;
var activeInspectorQuestions = [];
var activeRemarkTarget = null;
var unsubs = [];

var renderDashboardRaf = null;
var renderLibraryRaf = null;
var renderAssessmentsRaf = null;

var currentViewerRawUrl = "";
var currentViewerDriveId = null;
var currentViewerUseGoogleDocs = false;

var activeInspectorIsTeacher = false;
var activeInspectorTeacherName = "";

var gabAiAnimFrameId = null;
var gabAiAnimStartTime = null;
var isAccountCreationInProgress = false;

var matrixSchoolSearchQuery = "";
var matrixDistrictFilter = "ALL";
var facultySearchQuery = "";
var facultySchoolFilter = "ALL";
var sectionSearchQuery = "";
var sectionGradeFilter = "ALL";
var sectionSchoolFilter = "ALL";
var learnerSearchQuery = "";
var learnerGradeFilter = "ALL";
var learnerTierFilter = "ALL";
var learnerGenderFilter = "ALL";
var learnerStatusFilter = "ALL";
var learnerSchoolFilterVal = "ALL";
var learnerViewMode = "roster";
var learnerQuickPill = "ALL";
var learnerSortColumn = "mps";
var learnerSortAsc = false;
var learnerPage = 1;
var learnerPageSize = 25;
var superAdminLearnerView = "schools"; // 'schools' | 'drilldown'
var schoolMatrixDistrictFilter = "ALL"; // 'ALL' | 'District 1' | 'District 2'
var schoolMatrixSearchQuery = "";
var schoolMatrixSort = "highest_mps"; // 'highest_mps' | 'highest_enrolled' | 'highest_wpm' | 'lowest_mps' | 'name_asc'
var timedViewMode = "catalog"; // 'catalog' | 'schools_matrix'
var superAdminTimedView = "overview"; // 'overview' | 'drilldown'
var timedSelectedAssessmentId = null; // active assessment for school matrix
var timedSchoolDistrictFilter = "ALL"; // 'ALL' | 'District 1' | 'District 2'
var timedSchoolMatrixSearch = "";
var timedSchoolMatrixSort = "highest_mps"; // 'highest_mps' | 'highest_completion' | 'highest_wpm' | 'lowest_mps' | 'name_asc'
var timedSchoolViewMode = "sections"; // 'sections' | 'submissions'
var timedSubmissionsPage = 1;
var timedSubmissionsPageSize = 25;
var timedSubmissionsSortCol = "mps";
var timedSubmissionsSortAsc = false;
var activeAnalyticsAssessmentId = null;
var activeAnalyticsTab = "dimensions"; // 'passage' | 'dimensions' | 'items' | 'schools'
var credentialSearchQuery = "";
var credentialGradeFilter = "ALL";
var credentialSectionFilter = "ALL";
var credentialStatusFilter = "ALL";
var credentialSchoolFilter = "ALL";
var materialSearchQuery = "";
var materialGradeFilter = "ALL";
var currentExecutiveViewMode = "all";
var activeTeacherResetData = null;
var activeGeneratedPasskey = "";

var cachedAllAttempts = [];
var cachedQuizHistoryDocs = [];
var selectedQuizTypeFilter = "ALL";
var selectedQuizGradeFilter = "ALL";
var selectedQuizMasteryFilter = "ALL";
var quizSearchQuery = "";
var activeInspectorAttempt = null;
var isLoadingQuizAttempts = false;

var currentTourStep = 0;
var activeTourSteps = [];
var tourRepositionTimer = null;
var cachedCompetencyStats = null;
var activeViewingPassageAssessmentId = null;
var activeViewingPassageText = "";
var aiTimedGeneratedQuizPool = [];
var isKeyRevealed = false;
var centralizedGeminiApiKey = "";
var centralizedGeminiConfig = null;

// Valenzuela Junior High Schools (Default seed & fallback)
var DEFAULT_VALENZUELA_SCHOOLS = [
  { name: "Caruhatan National High School", id: "305445", district: "District 2" },
  { name: "Sitero Francisco Memorial National High School", id: "305446", district: "District 2" },
  { name: "Justice Eliezer R. De Los Santos High School", id: "305566", district: "District 2" },
  { name: "Lingunan National High School", id: "305567", district: "District 1" },
  { name: "Paso De Blas National High School", id: "305568", district: "District 2" },
  { name: "Disiplina Village-Bignay National High School", id: "305705", district: "District 1" },
  { name: "Malanday National High School", id: "305706", district: "District 1" },
  { name: "Veinte Reales National High School", id: "305707", district: "District 1" },
  { name: "Valenzuela City School of Mathematics and Science", id: "320401", district: "District 1" },
  { name: "Vicente Trinidad National High School (Punturin NHS)", id: "320402", district: "District 1" },
  { name: "Mapulang Lupa National High School", id: "320403", district: "District 2" },
  { name: "Bignay National High School", id: "320404", district: "District 1" },
  { name: "Arkong Bato National High School", id: "320405", district: "District 1" },
  { name: "Canumay East National High School", id: "320406", district: "District 1" },
  { name: "Wawang Pulo National High School", id: "320407", district: "District 1" },
  { name: "Bagbaguin National High School", id: "320408", district: "District 2" },
  { name: "Polo National High School", id: "305436", district: "District 1" },
  { name: "Dalandanan National High School", id: "305437", district: "District 1" },
  { name: "Malinta National High School", id: "305438", district: "District 1" },
  { name: "Canumay West National High School", id: "305439", district: "District 1" },
  { name: "Lawang Bato National High School", id: "305440", district: "District 1" },
  { name: "Valenzuela National High School", id: "305441", district: "District 2" },
  { name: "Parada National High School", id: "305442", district: "District 2" },
  { name: "Gen. Tiburcio de Leon National High School", id: "305443", district: "District 2" },
  { name: "Maysan National High School", id: "305444", district: "District 2" }
];

var VALENZUELA_SCHOOLS = [...DEFAULT_VALENZUELA_SCHOOLS];
var DIVISION_MASTER_KEY_HASH = "886905510448e38e8068295263755b335714cba339c05c3fa0ca1369bce11ca5";

// Testability & session helper for automated browser verification
window.setTestAdminSession = function(user, role = 'school_admin') {
  currentAdminUser = user;
  isSuperAdmin = (role === 'super_admin');
  isSchoolAdmin = (role === 'school_admin');
  const brandingTitle = document.getElementById('portalBrandingTitle');
  const brandingSubtitle = document.getElementById('portalBrandingSubtitle');
  if (isSuperAdmin) {
    document.title = "GabAI Super Admin Portal";
    if (brandingTitle) brandingTitle.textContent = "GabAI Super Admin Portal";
    if (brandingSubtitle) brandingSubtitle.textContent = "Schools Division Office (SDO) Valenzuela City";
  } else {
    document.title = "GabAI School Admin Portal";
    if (brandingTitle) brandingTitle.textContent = "GabAI School Admin Portal";
    if (brandingSubtitle) brandingSubtitle.textContent = "School Records & Reading Management";
  }
  if (typeof showAdminDashboard === 'function') {
    showAdminDashboard();
  }
  if (typeof renderSchoolRankingTable === 'function') {
    renderSchoolRankingTable();
  }
};
window.getTestAdminSession = function() {
  return { currentAdminUser, isSuperAdmin, isSchoolAdmin };
};

// Automated testing hook via URL query parameter
if (typeof window !== 'undefined' && window.location && window.location.search) {
  const testRole = new URLSearchParams(window.location.search).get('test_session');
  if (testRole === 'super_admin' || testRole === 'school_admin') {
    const mockSchoolId = testRole === 'school_admin' ? '305445' : 'DIVISION_OFFICE';
    window.addEventListener('DOMContentLoaded', function() {
      setTimeout(function() {
        if (typeof window.setTestAdminSession === 'function') {
          window.setTestAdminSession({
            email: testRole === 'super_admin' ? 'superadmin@gabai.edu' : 'caruhatan.admin@valenzuela.edu.ph',
            uid: 'test-' + testRole,
            schoolId: mockSchoolId,
            firstName: testRole === 'super_admin' ? 'Super Admin' : 'School Admin'
          }, testRole);
        }
      }, 200);
    });
  }
}
