import json
import os
import subprocess
import time
import urllib.request
import websocket

HTTP_PORT = 8089
CDP_PORT = 9225

def start_http_server():
    cmd = ["python", "-m", "http.server", str(HTTP_PORT), "--directory", "admin-portal"]
    proc = subprocess.Popen(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    return proc

def start_edge():
    edge_paths = [
        r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe",
        r"C:\Program Files\Microsoft\Edge\Application\msedge.exe"
    ]
    edge_path = next((p for p in edge_paths if os.path.exists(p)), None)
    if not edge_path:
        raise RuntimeError("Microsoft Edge executable not found")

    cmd = [
        edge_path,
        "--headless=new",
        f"--remote-debugging-port={CDP_PORT}",
        "--disable-gpu",
        "--no-sandbox",
        "--disable-extensions",
        f"http://127.0.0.1:{HTTP_PORT}/admin.html"
    ]
    proc = subprocess.Popen(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    return proc

def run_cdp_tests():
    # Wait for CDP endpoint
    ws_url = None
    for attempt in range(25):
        try:
            req = urllib.request.urlopen(f"http://127.0.0.1:{CDP_PORT}/json", timeout=2)
            tabs = json.loads(req.read().decode())
            for tab in tabs:
                if tab.get("type") == "page" and "webSocketDebuggerUrl" in tab:
                    ws_url = tab["webSocketDebuggerUrl"]
                    break
            if ws_url:
                break
        except Exception:
            pass
        time.sleep(0.5)

    if not ws_url:
        raise RuntimeError("Failed to connect to Edge CDP endpoint")

    print(f"Connected to CDP: {ws_url}")
    ws = websocket.create_connection(ws_url, timeout=10)

    msg_id = 1
    console_errors = []

    def send(method, params=None):
        nonlocal msg_id
        m_id = msg_id
        msg_id += 1
        payload = {"id": m_id, "method": method, "params": params or {}}
        ws.send(json.dumps(payload))
        while True:
            resp = json.loads(ws.recv())
            if resp.get("id") == m_id:
                return resp.get("result", {})
            elif resp.get("method") == "Runtime.consoleAPICalled":
                args = resp["params"].get("args", [])
                type_ = resp["params"].get("type")
                text = " ".join([str(a.get("value", a.get("description", ""))) for a in args])
                if type_ == "error":
                    console_errors.append(text)
            elif resp.get("method") == "Runtime.exceptionThrown":
                exc = resp["params"].get("exceptionDetails", {})
                console_errors.append(f"{exc.get('text', '')} line={exc.get('lineNumber')} col={exc.get('columnNumber')} {exc.get('exception', {})}")

    try:
        # Enable runtime & console
        send("Runtime.enable")
        send("Page.enable")

        # Wait for page load
        print("Waiting for page load and scripts evaluation...")
        time.sleep(3.0)

        # Drain any initial messages
        ws.settimeout(0.5)
        while True:
            try:
                resp = json.loads(ws.recv())
                if resp.get("method") == "Runtime.consoleAPICalled":
                    args = resp["params"].get("args", [])
                    type_ = resp["params"].get("type")
                    text = " ".join([str(a.get("value", a.get("description", ""))) for a in args])
                    if type_ == "error":
                        console_errors.append(text)
                elif resp.get("method") == "Runtime.exceptionThrown":
                    exc = resp["params"].get("exceptionDetails", {})
                    console_errors.append(f"Line {exc.get('lineNumber')} Col {exc.get('columnNumber')}: {exc.get('text', '')} {exc.get('exception', {})}")
            except Exception:
                break
        ws.settimeout(10)

        # 1. Check for fatal syntax/runtime console errors
        print("\n--- TEST 1: Checking for Console / Syntax Errors ---")
        filtered_errors = [e for e in console_errors if "favicon" not in e.lower() and "status of 404" not in e.lower()]
        print(f"Console errors detected: {len(filtered_errors)}")
        for err in filtered_errors:
            print("  [ERROR]", err)
        assert len(filtered_errors) == 0, f"Found JavaScript errors on admin.html: {filtered_errors}"
        print("[PASS] 0 JavaScript Syntax/Runtime Errors")

        # 2. Check getUnifiedTimedTests() output in browser context (clean DB: 0 items)
        print("\n--- TEST 2: Evaluating getUnifiedTimedTests() ---")
        eval_res = send("Runtime.evaluate", {
            "expression": "typeof window.getUnifiedTimedTests === 'function' ? window.getUnifiedTimedTests().length : -1",
            "returnByValue": True
        })
        val = eval_res.get("result", {}).get("value")
        print(f"getUnifiedTimedTests() count in live browser: {val}")
        assert val == 0, f"Expected 0 initial unified timed tests (clean database without fake seeded tests), got: {val}"
        print(f"[PASS] getUnifiedTimedTests() returned {val} (zero phantom tests on empty database)!")

        # 3. Simulate switching to tab-timed-assessments
        print("\n--- TEST 3: Switching to tab-timed-assessments and checking DOM ---")
        switch_res = send("Runtime.evaluate", {
            "expression": """
            (() => {
                window.switchTab('tab-timed-assessments');
                
                const tbody = document.getElementById('timedAssessmentsTableBody');
                const rows = tbody ? tbody.querySelectorAll('tr').length : 0;
                const tbodyHtml = tbody ? tbody.innerHTML.substring(0, 300) : '';
                const hasEmptyMsg = tbody ? tbody.textContent.includes('No Standardized Reading Tests Found') || tbody.textContent.includes('No standardized timed assessments') : false;
                const totalCard = document.getElementById('statTotalTimedTests')?.textContent?.trim();
                const activeCard = document.getElementById('statActiveTimedTests')?.textContent?.trim();
                const countBadge = document.getElementById('timedAssessmentsCountBadge')?.textContent?.trim();

                return {
                    rows,
                    hasEmptyMsg,
                    totalCard,
                    activeCard,
                    countBadge,
                    tbodyHtml
                };
            })()
            """,
            "returnByValue": True
        })
        dom_data = switch_res.get("result", {}).get("value", {})
        print("DOM State after switchTab('tab-timed-assessments'):", json.dumps(dom_data, indent=2))

        assert dom_data.get("hasEmptyMsg"), "ERROR: Table should display clean DepEd empty state when there are 0 assessments!"
        assert dom_data.get("rows", 0) == 1, f"Expected 1 table row (the empty state cell), got {dom_data.get('rows')}"
        assert dom_data.get("totalCard", "0") == "0", f"Expected totalCard == 0, got {dom_data.get('totalCard')}"
        print("[PASS] Tab loaded correctly with clean empty state and 0 Assessments badge!")

        # 4. Test openAITimedAssessmentGeneratorModal() (BLANK SLATE)
        print("\n--- TEST 4: Verifying AI Assessment Generator Modal (Clean Blank Slate) ---")
        send("Runtime.evaluate", {
            "expression": "window.setTestAdminSession({ uid: 'super_admin_test', email: 'sdo.admin@valenzuela.gov.ph', role: 'super_admin' }, 'super_admin');"
        })
        modal_res = send("Runtime.evaluate", {
            "expression": """
            (() => {
                window.openAITimedAssessmentGeneratorModal();
                const modal = document.getElementById('modalAITimedAssessmentGenerator');
                const isVisible = modal && !modal.classList.contains('hidden');
                const poolLen = Array.isArray(window.aiTimedGeneratedQuizPool) ? window.aiTimedGeneratedQuizPool.length : 0;
                const titleVal = document.getElementById('aiTimedTitle')?.value || '';
                const passageVal = document.getElementById('aiTimedPastedPassage')?.value || '';
                const hasSampleBtn = typeof window.loadSampleAITimedPassage === 'function';
                return {
                    isVisible,
                    poolLen,
                    hasTitle: Boolean(titleVal),
                    hasPassage: Boolean(passageVal),
                    hasSampleBtn
                };
            })()
            """,
            "returnByValue": True
        })
        modal_data = modal_res.get("result", {}).get("value", {})
        print("Modal State after openAITimedAssessmentGeneratorModal():", json.dumps(modal_data, indent=2))
        assert modal_data.get("isVisible"), "modalAITimedAssessmentGenerator should be visible"
        assert modal_data.get("poolLen") == 0, f"Expected 0 questions in clean blank studio, got {modal_data.get('poolLen')}"
        assert not modal_data.get("hasTitle"), "Expected clean empty title in blank studio"
        assert not modal_data.get("hasPassage"), "Expected clean empty passage in blank studio"
        assert modal_data.get("hasSampleBtn"), "loadSampleAITimedPassage helper should be available"
        print("[PASS] Modal opens cleanly as an authentic empty blank slate (0 questions, empty title, empty passage)!")

        # 5. Test Deploying a real assessment and testing optimistic rendering
        print("\n--- TEST 5: Deploying a new assessment and testing optimistic rendering ---")
        deploy_res = send("Runtime.evaluate", {
            "expression": """
            (async () => {
                document.getElementById('aiTimedTitle').value = "Live Test Assessment SDO Valenzuela";
                document.getElementById('aiTimedPastedPassage').value = "The La Mesa Watershed in Quezon City is the last remaining rainforest of its size in Metro Manila. Covering an area of 2,700 hectares, it surrounds the reservoir from which roughly 1.5 million residents of the metropolis draw their drinking water. Today, the revived canopy serves as a vital ecological lung for the National Capital Region.";
                
                // Author 1 real question item
                window.addManualAITimedQuestion();
                window.aiTimedGeneratedQuizPool[0].q = "What is the primary ecological role of the La Mesa Watershed?";
                window.aiTimedGeneratedQuizPool[0].options = ["Mineral resources", "Ecological lung and water reservoir", "Industrial zone", "Timber"];
                window.aiTimedGeneratedQuizPool[0].ans = 1;

                await window.deployAITimedAssessment();
                
                const unified = window.getUnifiedTimedTests();
                const found = unified.some(x => x.title === "Live Test Assessment SDO Valenzuela");
                const tbody = document.getElementById('timedAssessmentsTableBody');
                const tableHasNew = tbody ? tbody.textContent.includes("Live Test Assessment SDO Valenzuela") : false;
                const totalCard = document.getElementById('statTotalTimedTests')?.textContent?.trim();

                return {
                    found,
                    tableHasNew,
                    totalCard,
                    unifiedCount: unified.length
                };
            })()
            """,
            "awaitPromise": True,
            "returnByValue": True
        })
        deploy_data = deploy_res.get("result", {}).get("value", {})
        print("Deploy State:", json.dumps(deploy_data, indent=2))
        assert deploy_data.get("found"), "Newly deployed assessment should be present in unified tests"
        assert deploy_data.get("tableHasNew"), "Table should render the newly deployed assessment row immediately"
        assert deploy_data.get("unifiedCount") == 1, "Unified count should now be 1"
        assert deploy_data.get("totalCard") == "1", "Total card should now display 1"
        print("[PASS] Assessment successfully deployed! New count is 1 (authentic user data).")

        # 6. Test System Settings tab rendering
        print("\n--- TEST 6: Verifying Restored System Settings & Platform Health ---")
        settings_res = send("Runtime.evaluate", {
            "expression": """
            (() => {
                window.switchTab('tab-system-settings');
                const settingsCard = document.getElementById('tourTargetSystemSettingsCard');
                const hasHealth = document.body.textContent.includes('Platform Health');
                const hasDivKey = document.getElementById('divisionAuthKeyCard') !== null;
                const hasGeminiInput = document.getElementById('inputCentralGeminiKey') !== null;
                return {
                    hasSettingsCard: settingsCard !== null,
                    hasHealth,
                    hasDivKey,
                    hasGeminiInput
                };
            })()
            """,
            "returnByValue": True
        })
        settings_data = settings_res.get("result", {}).get("value", {})
        print("Settings State:", json.dumps(settings_data, indent=2))
        assert settings_data.get("hasSettingsCard"), "System Settings card should be present"
        assert settings_data.get("hasHealth"), "Platform Health indicators should be present"
        assert not settings_data.get("hasDivKey"), "divisionAuthKeyCard should NOT be present (removed unrequested bloat)"
        assert settings_data.get("hasGeminiInput"), "Gemini API key input should be present"
        print("[PASS] System Settings restored cleanly with Platform Health and zero unrequested cards!")

        # 7. Test School Admin Scoped Access to Standardized Reading Tests
        print("\n--- TEST 7: Verifying School Admin Scoped Access to Standardized Reading Tests ---")
        school_admin_res = send("Runtime.evaluate", {
            "expression": """
            (() => {
                try {
                    // Simulate School Admin session for Malinta National High School (305445)
                    window.setTestAdminSession({
                        uid: "school_admin_305445",
                        email: "principal@malinta.deped.gov.ph",
                        firstName: "Corazon",
                        lastName: "Aquino",
                        schoolId: "305445"
                    }, 'school_admin');

                    const btnTimed = document.getElementById('btn-tab-timed-assessments');
                    const btnTimedVisible = btnTimed && btnTimed.style.display !== 'none';

                    // Switch to standardized tests tab
                    window.switchTab('tab-timed-assessments');
                    const tabTimed = document.getElementById('tab-timed-assessments');
                    const tabVisible = tabTimed && !tabTimed.classList.contains('hidden');

                    const btnDeploy = document.getElementById('btnOpenAITimedAssessmentGenerator');
                    const btnDeployHidden = btnDeploy && btnDeploy.style.display === 'none';

                    const schoolFilter = document.getElementById('timedAssessmentSchoolFilter');
                    const schoolFilterLocked = schoolFilter && schoolFilter.disabled && schoolFilter.value === '305445';

                    const thHeader = document.getElementById('thTimedAssessmentsSubmissions')?.textContent?.trim();
                    const labelSubs = document.getElementById('labelStatDivisionSubmissions')?.textContent?.trim();
                    const labelAvg = document.getElementById('labelStatDivisionAvgScore')?.textContent?.trim();

                    // Test multi-school data isolation:
                    // Inject 2 Malinta submissions and 3 Polo (305446) submissions
                    const testId = (window.getUnifiedTimedTests()[0] || {}).id || 'timed_assessment_test';
                    window.setCachedTimedSubmissionsForTesting([
                        { id: 'sub1', assessmentId: testId, studentUid: 'stud_m1', studentName: 'Malinta Student 1', score: 4, totalQuestions: 5, percentage: 80, schoolId: '305445' },
                        { id: 'sub2', assessmentId: testId, studentUid: 'stud_m2', studentName: 'Malinta Student 2', score: 5, totalQuestions: 5, percentage: 100, schoolId: '305445' },
                        { id: 'sub3', assessmentId: testId, studentUid: 'stud_p1', studentName: 'Polo Student 1', score: 2, totalQuestions: 5, percentage: 40, schoolId: '305446' },
                        { id: 'sub4', assessmentId: testId, studentUid: 'stud_p2', studentName: 'Polo Student 2', score: 3, totalQuestions: 5, percentage: 60, schoolId: '305446' },
                        { id: 'sub5', assessmentId: testId, studentUid: 'stud_p3', studentName: 'Polo Student 3', score: 1, totalQuestions: 5, percentage: 20, schoolId: '305446' }
                    ]);

                    window.updateTimedAssessmentMetrics();
                    window.renderTimedAssessmentsTable();

                    const statSubs = document.getElementById('statDivisionSubmissions')?.textContent?.trim();
                    const statAvg = document.getElementById('statDivisionAvgScore')?.textContent?.trim();

                    // Check table row submissions cell
                    const tbody = document.getElementById('timedAssessmentsTableBody');
                    const tableSubText = tbody ? tbody.textContent : '';
                    const hasMalintaCountOnly = tableSubText.includes('2 Turned In') && !tableSubText.includes('5 Turned In');

                    // Inspect Submissions Modal
                    window.openAssessmentSubmissionsModal(testId);
                    const subModalTurnedIn = document.getElementById('subModalTurnedIn')?.textContent?.trim();
                    const subTableBody = document.getElementById('assessmentSubmissionsTableBody');
                    const modalHasMalinta = subTableBody ? subTableBody.textContent.includes('Malinta Student 1') : false;
                    const modalHasPolo = subTableBody ? subTableBody.textContent.includes('Polo Student 1') : false;
                    window.closeAssessmentSubmissionsModal();

                    // Inspect Passage Viewer Modal (Edit in Studio should be hidden for School Admin)
                    window.openPassageViewerModal('Standardized Passage Sample', 'Sample passage text for test.');
                    const btnEditPassage = document.getElementById('btnPassageViewerEditStudio');
                    const editPassageHidden = btnEditPassage ? btnEditPassage.style.display === 'none' : true;
                    window.closePassageViewerModal();

                    // Guard check: verify School Admin cannot open Studio
                    const studioModal = document.getElementById('modalAITimedAssessmentGenerator');
                    window.openStandardizedTestStudio('some_test_id');
                    const studioRemainsHidden = studioModal ? studioModal.classList.contains('hidden') : true;

                    // Verify Empty State Role-Awareness (School Admin must NEVER see "Deploy Standardized Timed Assessment" to author)
                    const searchInput = document.getElementById('timedAssessmentSearchInput');
                    const originalSearchVal = searchInput ? searchInput.value : '';
                    if (searchInput) searchInput.value = 'NON_EXISTENT_FILTER_KEYWORD_XYZ';
                    window.renderTimedAssessmentsTable();
                    const filteredEmptyText = tbody ? tbody.textContent : '';
                    if (searchInput) {
                        searchInput.value = originalSearchVal;
                        window.renderTimedAssessmentsTable();
                    }

                    const hasCheapAuthoringMessage = filteredEmptyText.includes('to author your first test') || filteredEmptyText.includes('Deploy Standardized Timed Assessment');

                    return {
                        btnTimedVisible,
                        tabVisible,
                        btnDeployHidden,
                        schoolFilterLocked,
                        thHeader,
                        labelSubs,
                        labelAvg,
                        statSubs,
                        statAvg,
                        hasMalintaCountOnly,
                        subModalTurnedIn,
                        modalHasMalinta,
                        modalHasPolo,
                        editPassageHidden,
                        studioRemainsHidden,
                        hasCheapAuthoringMessage,
                        filteredEmptyHasMatchingTitle: filteredEmptyText.includes('No Matching Standardized Tests')
                    };
                } catch (err) {
                    return { error: err.toString(), stack: err.stack };
                }
            })()
            """,
            "returnByValue": True
        })
        school_data = school_admin_res.get("result", {}).get("value", {})
        print("School Admin State:", json.dumps(school_data, indent=2))
        assert school_data.get("btnTimedVisible"), "Standardized tests nav button should be visible for School Admin"
        assert school_data.get("tabVisible"), "School Admin should be able to navigate to tab-timed-assessments"
        assert school_data.get("btnDeployHidden"), "Deploy Studio button should be hidden for School Admin"
        assert school_data.get("schoolFilterLocked"), "School filter should be locked to School Admin school"
        assert "School Submissions & MPS" in school_data.get("thHeader", ""), "Table header should reflect School Submissions"
        assert school_data.get("labelSubs") == "School Submissions", "KPI label should say School Submissions"
        assert school_data.get("labelAvg") == "School MPS Avg", "KPI label should say School MPS Avg"
        assert school_data.get("statSubs") == "2", f"KPI submissions must be 2 (Malinta only, not Polo), got {school_data.get('statSubs')}"
        assert school_data.get("statAvg") == "90%", f"KPI average must be 90% (Malinta only: 80% & 100%), got {school_data.get('statAvg')}"
        assert school_data.get("hasMalintaCountOnly"), "Table row must show '2 Turned In' (Malinta only)"
        assert school_data.get("subModalTurnedIn") == "2", f"Submissions modal must show 2 turned in, got {school_data.get('subModalTurnedIn')}"
        assert school_data.get("modalHasMalinta"), "Submissions modal should contain Malinta learners"
        assert not school_data.get("modalHasPolo"), "Submissions modal must NOT contain Polo learners (Zero Leakage)"
        assert school_data.get("editPassageHidden"), "Edit in Studio button in Passage Viewer must be hidden for School Admin"
        assert school_data.get("studioRemainsHidden"), "Studio modal must remain hidden when School Admin attempts to open it"
        assert not school_data.get("hasCheapAuthoringMessage"), "Empty state must NEVER prompt School Admin to author/deploy assessments"
        assert school_data.get("filteredEmptyHasMatchingTitle"), "Filter empty state must display professional 'No Matching Standardized Tests' title"
        print("[PASS] School Admin scoped isolation & role-aware messaging verified with 100% precision: exactly 2 Malinta submissions counted, 0 Polo leaked, 0 cheap authoring prompts!")

        print("\n" + "=" * 55)
        print("ALL BROWSER-LEVEL END-TO-END CHECKS PASSED WITH 100% SUCCESS!")
        print("=" * 55 + "\n")

    finally:
        ws.close()

if __name__ == "__main__":
    # Check if port is already in use
    http_proc = None
    edge_proc = None
    try:
        urllib.request.urlopen(f"http://127.0.0.1:{HTTP_PORT}/admin.html", timeout=1)
        print(f"HTTP server already running on port {HTTP_PORT}")
    except Exception:
        print(f"Starting HTTP server on port {HTTP_PORT}...")
        http_proc = start_http_server()
        time.sleep(1.0)

    try:
        urllib.request.urlopen(f"http://127.0.0.1:{CDP_PORT}/json", timeout=1)
        print(f"Edge CDP already running on port {CDP_PORT}")
    except Exception:
        print("Starting Edge in headless CDP mode...")
        edge_proc = start_edge()
        time.sleep(2.0)

    try:
        run_cdp_tests()
    finally:
        if edge_proc:
            edge_proc.terminate()
        if http_proc:
            http_proc.terminate()
