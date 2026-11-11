    // =========================================================================
    // UNIVERSAL PDF & QUIZ DETECTORS (Fixes PDF & Quiz detection)
    // =========================================================================
    let currentViewerRawUrl = "";
    let currentViewerDriveId = "";
    let currentViewerUseGoogleDocs = false;

    window.getMaterialPdfUrl = function(m) {
      if (!m) return "";
      if (m.pdfUrl && typeof m.pdfUrl === 'string' && m.pdfUrl.trim()) return m.pdfUrl.trim();
      if (m.downloadUrl && typeof m.downloadUrl === 'string' && m.downloadUrl.trim()) return m.downloadUrl.trim();
      if (m.url && typeof m.url === 'string' && m.url.trim()) return m.url.trim();
      if (m.pdf && typeof m.pdf === 'string' && m.pdf.trim()) return m.pdf.trim();
      const driveId = m.driveFileId || m.fileId || m.driveId;
      if (driveId) return `https://drive.google.com/file/d/${driveId}/view`;
      return "";
    };

    window.getEmbedPdfUrl = function(url, driveId, forceDocsViewer = false) {
      if (!url && driveId) return `https://drive.google.com/file/d/${driveId}/preview`;
      if (!url) return "";
      
      // 1. Google Drive link -> always embed with /preview
      const driveMatch = url.match(/\/file\/d\/([a-zA-Z0-9_-]+)/) || url.match(/[?&]id=([a-zA-Z0-9_-]+)/);
      if (driveMatch && driveMatch[1]) {
        return `https://drive.google.com/file/d/${driveMatch[1]}/preview`;
      }
      if (url.includes("drive.google.com") && url.includes("/view")) {
        return url.replace(/\/view(\?.*)?$/, "/preview");
      }

      // 2. If user explicitly requested Google Docs Viewer fallback:
      if (forceDocsViewer) {
        return `https://docs.google.com/viewer?url=${encodeURIComponent(url)}&embedded=true`;
      }

      // 3. Direct PDF file (Firebase Storage, direct .pdf URL, blobs):
      // Modern browsers (Chrome, Edge, Firefox) natively render PDFs inside iframes with high fidelity and zoom controls.
      if (url.toLowerCase().includes(".pdf") || url.includes("firebasestorage.googleapis.com") || url.includes(".appspot.com") || url.startsWith("blob:") || url.startsWith("data:")) {
        return url;
      }

      // 4. Fallback to Google Docs Viewer for other remote document URLs:
      if (url.startsWith("http://") || url.startsWith("https://")) {
        return `https://docs.google.com/viewer?url=${encodeURIComponent(url)}&embedded=true`;
      }
      return url;
    };

    window.togglePdfPreviewEngine = function() {
      if (!currentViewerRawUrl) return;
      currentViewerUseGoogleDocs = !currentViewerUseGoogleDocs;
      
      const label = document.getElementById('pdfEngineLabel');
      if (label) {
        label.textContent = currentViewerUseGoogleDocs ? "Switch to Direct PDF" : "Switch to Docs Viewer";
      }

      const notice = document.getElementById('pdfLoadingNotice');
      if (notice) {
        notice.textContent = currentViewerUseGoogleDocs ? "Loading via Google Docs Viewer..." : "Loading native document stream...";
        notice.classList.remove('hidden');
      }

      const embedUrl = getEmbedPdfUrl(currentViewerRawUrl, currentViewerDriveId, currentViewerUseGoogleDocs);
      document.getElementById('pdfViewerIframe').src = embedUrl;
    };

    window.getMaterialQuestions = function(m) {
      if (!m) return [];
      const raw = m.quiz_pool_json || m.quiz_json || m.quiz || m.questions || m.quizList || m.quizPool || "[]";
      try {
        let parsed = typeof raw === 'string' ? JSON.parse(raw) : raw;
        if (!Array.isArray(parsed) && typeof parsed === 'object' && parsed !== null) {
          parsed = parsed.questions || parsed.quizList || parsed.pool || parsed.items || Object.values(parsed);
        }
        if (Array.isArray(parsed)) {
          return parsed.map((item, idx) => {
            let opts = [];
            if (Array.isArray(item.options)) opts = item.options;
            else if (Array.isArray(item.choices)) opts = item.choices;
            else if (typeof item.options === 'object' && item.options !== null) opts = Object.values(item.options);
            else if (typeof item.choices === 'object' && item.choices !== null) opts = Object.values(item.choices);
            else opts = ["Option A", "Option B", "Option C", "Option D"];

            let ans = 0;
            if (typeof item.ans === 'number') ans = item.ans;
            else if (typeof item.correctAnswer === 'number') ans = item.correctAnswer;
            else if (typeof item.correct_answer === 'number') ans = item.correct_answer;
            else if (typeof item.answer === 'number') ans = item.answer;
            else if (typeof item.ans === 'string') {
              const letter = item.ans.trim().toUpperCase();
              if (letter === 'A' || letter === '0') ans = 0;
              else if (letter === 'B' || letter === '1') ans = 1;
              else if (letter === 'C' || letter === '2') ans = 2;
              else if (letter === 'D' || letter === '3') ans = 3;
            }

            return {
              q: item.q || item.question || item.title || `Question ${idx + 1}`,
              options: opts.map(o => String(o)),
              ans: ans
            };
          });
        }
      } catch (e) {
        console.warn("Error parsing quiz JSON for material", m.id, e);
      }
      return [];
    };

    // =========================================================================
    // IN-BROWSER PDF VIEWER MODAL
    // =========================================================================
    window.openPdfViewerModal = function(materialId) {
      const m = cachedMaterials.find(item => item.id === materialId);
      if (!m) return;

      const rawUrl = getMaterialPdfUrl(m);
      if (!rawUrl) {
        window.showToast("No valid PDF URL or Google Drive file was recorded for this document.", "error");
        return;
      }

      currentViewerRawUrl = rawUrl;
      currentViewerDriveId = m.driveFileId || m.fileId || "";
      currentViewerUseGoogleDocs = false; // default to native direct embed for speed and privacy

      const label = document.getElementById('pdfEngineLabel');
      if (label) label.textContent = "Switch to Docs Viewer";

      const embedUrl = getEmbedPdfUrl(rawUrl, currentViewerDriveId, false);
      const sub = cachedSubjects.find(s => s.id === m.subjectId);

      document.getElementById('pdfViewerTitle').textContent = m.title || "Document Viewer";
      document.getElementById('pdfViewerMeta').textContent = `${sub ? sub.name : 'General Subject'} • ${getSchoolName(sub ? sub.schoolId : m.schoolId)}`;
      document.getElementById('pdfDirectLink').href = rawUrl;

      const notice = document.getElementById('pdfLoadingNotice');
      if (notice) {
        notice.textContent = "Loading document preview...";
        notice.classList.remove('hidden');
      }

      document.getElementById('pdfViewerIframe').src = embedUrl;
      document.getElementById('modalPdfViewer').classList.remove('hidden');
    };

    window.closePdfViewer = function() {
      document.getElementById('pdfViewerIframe').src = "about:blank";
      document.getElementById('modalPdfViewer').classList.add('hidden');
      currentViewerRawUrl = "";
      currentViewerDriveId = "";
    };


    // =========================================================================
    // IN-BROWSER QUIZ QUESTION INSPECTOR & EDITOR MODAL
    // =========================================================================
    let activeInspectorIsTeacher = false;
    let activeInspectorTeacherName = "";

    window.openQuizInspectorModal = function(materialId) {
      const m = cachedMaterials.find(item => item.id === materialId);
      if (!m) return;

      activeInspectorType = 'material';
      activeInspectorMaterialId = materialId;
      activeInspectorAssessmentId = null;
      activeInspectorQuestions = getMaterialQuestions(m);
      activeInspectorIsTeacher = true; // Strictly read-only for School Admin and Super Admin
      activeInspectorTeacherName = m.uploaderName || m.teacherName || "Faculty";

      document.getElementById('quizInspectorTitle').textContent = `Quiz Pool: ${m.title || 'Untitled Document'}`;
      document.getElementById('quizInspectorSubtitle').textContent = `${activeInspectorQuestions.length} Questions Configured (quiz_pool_json) • Read-Only Inspection`;
      
      renderQuizQuestionsList();
      document.getElementById('modalQuizInspector').classList.remove('hidden');
    };

    window.openAssessmentInspectorModal = function(assessmentId) {
      const a = cachedDivisionTimedAssessments.find(item => item.id === assessmentId) || cachedWeeklyAssessments.find(item => item.id === assessmentId);
      if (!a) return;

      activeInspectorType = 'assessment';
      activeInspectorAssessmentId = assessmentId;
      activeInspectorMaterialId = null;
      activeInspectorIsTeacher = true; // Strictly read-only for School Admin and Super Admin
      activeInspectorTeacherName = a.teacherName || "Faculty";
      const isStandardized = Boolean(a.isDivisionUniform || a.sourceType === 'division_uniform' || (a.id && a.id.startsWith('timed_assessment_')));
      const inspTitleEl = document.getElementById('quizInspectorTitle');
      if (inspTitleEl) {
        inspTitleEl.textContent = isStandardized 
          ? `Standardized Reading Test: ${a.title || 'Division Timed Assessment'}`
          : (a.title || 'Attached Reading Assessment Pool');
      }
      
      let qs = [];
      try {
        if (a.quiz_pool_json) {
          qs = typeof a.quiz_pool_json === 'string' ? JSON.parse(a.quiz_pool_json) : a.quiz_pool_json;
        } else if (a.quiz_json) {
          qs = typeof a.quiz_json === 'string' ? JSON.parse(a.quiz_json) : a.quiz_json;
        }
      } catch (e) {
        console.error("Parse assessment quiz error:", e);
      }
      activeInspectorQuestions = Array.isArray(qs) ? qs : [];

      document.getElementById('quizInspectorTitle').textContent = `Weekly Assessment: ${a.title || 'Untitled Assessment'}`;
      document.getElementById('quizInspectorSubtitle').textContent = `${activeInspectorQuestions.length} Questions Configured • ${a.className || 'Class'} (${a.subjectName || 'Subject'})`;
      
      renderQuizQuestionsList();
      document.getElementById('modalQuizInspector').classList.remove('hidden');
    };

    window.closeQuizInspector = function() {
      document.getElementById('modalQuizInspector').classList.add('hidden');
      activeInspectorType = null;
      activeInspectorMaterialId = null;
      activeInspectorAssessmentId = null;
      activeInspectorQuestions = [];
      activeInspectorIsTeacher = false;
      activeInspectorTeacherName = "";
    };

    function renderQuizQuestionsList() {
      const container = document.getElementById('quizQuestionsListContainer');
      document.getElementById('quizInspectorSubtitle').textContent = `${activeInspectorQuestions.length} Questions Configured (quiz_pool_json)${activeInspectorIsTeacher ? ' • Read-Only Inspection' : ''}`;

      let bannerHtml = "";
      if (activeInspectorIsTeacher) {
        bannerHtml = `
          <div class="p-3 bg-amber-50 border border-amber-200 rounded-xl text-xs text-amber-900 flex items-center gap-2.5 mb-3 shadow-xs">
            <span class="shrink-0 text-amber-700">${uiIcon('lock', 'w-4 h-4')}</span>
            <div>
              <strong class="font-bold">Instructional Safeguard:</strong>
              <span> This assessment was crafted by faculty <em>(${activeInspectorTeacherName})</em>. School &amp; Division Admins have read-only inspection access and cannot alter classroom questions.</span>
            </div>
          </div>
        `;
      }

      if (activeInspectorQuestions.length === 0) {
        container.innerHTML = bannerHtml + `
          <div class="p-8 text-center bg-white rounded-xl border border-dashed border-slate-300 text-slate-500">
            <div class="w-12 h-12 rounded-2xl bg-indigo-50 text-indigo-600 flex items-center justify-center mx-auto mb-2">
              ${uiIcon('clipboard', 'w-6 h-6')}
            </div>
            <p class="font-bold text-sm text-slate-800">No Questions in Pool Yet</p>
            <p class="text-xs text-slate-400 mt-1">${activeInspectorIsTeacher ? 'Faculty has not configured question items yet.' : 'Click <strong>Add New Question</strong> below to initialize multiple-choice questions for this reading material.'}</p>
          </div>`;
        return;
      }

      container.innerHTML = bannerHtml + activeInspectorQuestions.map((q, idx) => {
        const optionsHtml = q.options.map((opt, optIdx) => {
          const isCorrect = optIdx === q.ans;
          return `
            <div class="p-2.5 rounded-lg border text-xs flex items-center justify-between ${isCorrect ? 'bg-emerald-50 border-emerald-300 text-emerald-900 font-bold' : 'bg-slate-50 border-slate-200 text-slate-700'}">
              <span><strong class="mr-1">${String.fromCharCode(65 + optIdx)}.</strong> ${opt}</span>
              ${isCorrect ? `<span class="px-2 py-0.5 bg-emerald-600 text-white text-[10px] rounded-full inline-flex items-center gap-1">${uiIcon('check', 'w-3 h-3 text-white')} Correct Choice</span>` : ''}
            </div>
          `;
        }).join('');

        return `
          <div class="p-4 bg-white rounded-xl border border-slate-200 shadow-sm space-y-3">
            <div class="flex items-start justify-between gap-3">
              <div class="flex items-center gap-2">
                <span class="w-6 h-6 rounded-full bg-indigo-600 text-white font-bold text-xs flex items-center justify-center shrink-0">
                  ${idx + 1}
                </span>
                <h4 class="font-bold text-sm text-slate-900">${q.q}</h4>
              </div>
              ${activeInspectorIsTeacher ? `
                <span class="px-2 py-0.5 bg-slate-100 text-slate-500 rounded text-[10px] font-bold border border-slate-200">
                  Read-Only
                </span>
              ` : `
                <div class="flex items-center gap-1 shrink-0">
                  <button onclick="editQuestionPrompt(${idx})" class="p-1 text-slate-400 hover:text-indigo-600 text-xs font-bold" title="Edit Question">
                    ${uiIcon('edit', 'w-3.5 h-3.5')}
                  </button>
                  <button onclick="deleteQuestion(${idx})" class="p-1 text-slate-400 hover:text-red-600 text-xs font-bold" title="Delete Question">
                    ${uiIcon('trash', 'w-3.5 h-3.5')}
                  </button>
                </div>
              `}
            </div>

            <div class="grid grid-cols-1 sm:grid-cols-2 gap-2 pl-8">
              ${optionsHtml}
            </div>

            ${q.explanation ? `
              <div class="mt-2 p-2.5 bg-indigo-50/80 border border-indigo-200 rounded-lg text-xs text-indigo-950 flex items-start justify-between gap-2">
                <div>
                  <span class="font-bold text-indigo-800 inline-flex items-center gap-1.5">
                    ${uiIcon('help', 'w-3.5 h-3.5 text-indigo-600')} Educational Explanation:
                  </span>
                  <p class="mt-0.5 text-indigo-900">${q.explanation}</p>
                </div>
                ${!activeInspectorIsTeacher ? `
                  <button onclick="editQuestionExplanation(${idx})" class="text-[11px] font-bold text-indigo-600 hover:text-indigo-800 shrink-0">
                    Edit
                  </button>
                ` : ''}
              </div>
            ` : `
              ${!activeInspectorIsTeacher ? `
                <div class="mt-2 text-right">
                  <button onclick="editQuestionExplanation(${idx})" class="text-[11px] text-slate-400 hover:text-indigo-600 font-semibold">
                    + Add Educational Explanation
                  </button>
                </div>
              ` : ''}
            `}
          </div>
        `;
      }).join('');
    }

    window.openAddQuestionForm = function() {
      if (activeInspectorIsTeacher) {
        window.showToast("Classroom quiz pools are managed directly by faculty.", "warning");
        return;
      }
      const promptText = prompt("Enter question text:");
      if (!promptText || !promptText.trim()) return;

      const optA = prompt("Choice A:", "");
      const optB = prompt("Choice B:", "");
      const optC = prompt("Choice C:", "");
      const optD = prompt("Choice D:", "");

      if (!optA || !optB) {
        window.showToast("Please provide at least Choices A and B.", "warning");
        return;
      }

      const correctLetter = (prompt("Which choice is correct? (A, B, C, or D):", "A") || "A").trim().toUpperCase();
      let ansIndex = 0;
      if (correctLetter === 'B') ansIndex = 1;
      if (correctLetter === 'C') ansIndex = 2;
      if (correctLetter === 'D') ansIndex = 3;

      const explanation = prompt("Educational Explanation for students (displayed after answering):", "") || "";

      activeInspectorQuestions.push({
        q: promptText.trim(),
        options: [optA.trim(), optB.trim(), optC ? optC.trim() : "None", optD ? optD.trim() : "None"],
        ans: ansIndex,
        explanation: explanation.trim()
      });

      renderQuizQuestionsList();
      window.showToast("Question added. Click 'Save All Questions' to publish changes.", "info");
    };

    window.editQuestionExplanation = function(idx) {
      if (activeInspectorIsTeacher) {
        window.showToast("Classroom quiz pools are managed directly by faculty.", "warning");
        return;
      }
      const q = activeInspectorQuestions[idx];
      const newExp = prompt("Edit Educational Explanation:", q.explanation || "");
      if (newExp !== null) {
        q.explanation = newExp.trim();
        renderQuizQuestionsList();
      }
    };

    window.editQuestionPrompt = function(idx) {
      if (activeInspectorIsTeacher) {
        window.showToast("Classroom quiz pools are managed directly by faculty.", "warning");
        return;
      }
      const q = activeInspectorQuestions[idx];
      const newPrompt = prompt("Edit Question Text:", q.q);
      if (newPrompt && newPrompt.trim()) {
        q.q = newPrompt.trim();
        renderQuizQuestionsList();
      }
    };

    window.deleteQuestion = async function(idx) {
      if (activeInspectorIsTeacher) {
        window.showToast("Classroom quiz pools are managed directly by faculty.", "warning");
        return;
      }
      const confirmed = await showConfirmModal({
        title: `Remove Question ${idx + 1}?`,
        message: "Are you sure you want to remove this question from the quiz pool?",
        badgeIcon: "🗑️",
        badgeBg: "bg-rose-50 text-rose-600 border-rose-200",
        confirmText: "Remove Question",
        isDestructive: true
      });
      if (confirmed) {
        activeInspectorQuestions.splice(idx, 1);
        renderQuizQuestionsList();
        window.showToast("Question removed from pool.", "info");
      }
    };

    window.saveCurrentQuizPool = async function() {
      if (activeInspectorIsTeacher) {
        window.showToast("Classroom quiz pools are managed directly by faculty.", "warning");
        return;
      }
      if (!activeInspectorMaterialId && !activeInspectorAssessmentId) return;

      const btn = document.getElementById('btnSaveQuizPool');
      btn.disabled = true;
      btn.textContent = "Saving...";

      const jsonString = JSON.stringify(activeInspectorQuestions);

      try {
        if (activeInspectorAssessmentId) {
          await updateDoc(doc(db, "weekly_assessments", activeInspectorAssessmentId), {
            quiz_pool_json: jsonString,
            quiz_json: jsonString,
            questionCount: activeInspectorQuestions.length,
            updatedAt: Date.now()
          });
          window.showToast(`Weekly assessment updated! (${activeInspectorQuestions.length} questions attached)`, "success");
        } else if (activeInspectorMaterialId) {
          await updateDoc(doc(db, "library_materials", activeInspectorMaterialId), {
            quiz_pool_json: jsonString,
            quiz_json: jsonString,
            quiz_max_items: activeInspectorQuestions.length,
            updatedAt: Date.now()
          });
          window.showToast(`Quiz pool saved! (${activeInspectorQuestions.length} questions attached)`, "success");
        }
        closeQuizInspector();
      } catch (err) {
        console.error("Save quiz error:", err);
        window.showToast("Failed to save questions: " + err.message, "error");
      } finally {
        btn.disabled = false;
        btn.textContent = "Save All Questions";
      }
    };

    window.loadSampleQuizJson = function() {
      document.getElementById('matQuizJson').value = JSON.stringify([
        {
          "q": "What was Dr. Pio Valenzuela's primary contribution to the Katipunan?",
          "options": ["Physician, co-founder and editor of Kalayaan", "Chief military strategist in Cavite", "Diplomat in Spain", "Treasurer in Hong Kong"],
          "ans": 0
        },
        {
          "q": "What publication was overseen by Dr. Pio Valenzuela?",
          "options": ["La Solidaridad", "Kalayaan", "El Filibusterismo", "Diario de Manila"],
          "ans": 1
        }
      ], null, 2);
    };


    // =========================================================================
    // 5. REAL LIBRARY ARCHITECTURE (library_subjects + library_materials)
    // =========================================================================
    window.openCreateSubjectModal = function() {
      window.showToast("Library subject folders are authored and organized exclusively by faculty in the mobile application.", "info");
    };

    window.closeSubjectModal = function() {};

    window.handleSubjectSubmit = function(e) {
      e?.preventDefault();
    };

    window.deleteSubject = function(subjectId, name) {
      window.showToast("Institutional Safeguard: Neither School Admins nor Super Admins have permission to delete faculty library folders.", "warning");
    };

    window.materialBelongsToSubject = function(m, s) {
      if (!m || !s) return false;
      const sId = String(s.id || '').trim();
      const sName = String(s.name || s.title || '').trim();

      const mSubId = String(m.subjectId || m.subject_id || m.subject || m.folderId || '').trim();
      const mSubName = String(m.subjectName || m.subject || '').trim();

      if (sId && mSubId && (sId === mSubId || sId.toLowerCase() === mSubId.toLowerCase())) return true;
      if (sName && mSubId && (sName === mSubId || sName.toLowerCase() === mSubId.toLowerCase())) return true;
      if (sName && mSubName && (sName === mSubName || sName.toLowerCase() === mSubName.toLowerCase())) return true;
      return false;
    };

    window.findSubjectForMaterial = function(m) {
      if (!m) return null;
      return cachedSubjects.find(s => materialBelongsToSubject(m, s)) || null;
    };

    window.getSchoolIdForLibraryItem = function(item) {
      if (!item) return '';
      // 1. Explicit schoolId on the item itself
      if (item.schoolId && String(item.schoolId).trim() !== '') {
        return String(item.schoolId).trim();
      }

      // 2. Direct teacher UID lookup
      const teacherId = item.teacherId || item.uploaderId || item.uploadedBy || item.userId || item.authorId;
      if (teacherId) {
        const teacher = (cachedTeachers && cachedTeachers.find(t => t.id === teacherId)) ||
                        (cachedUsers && cachedUsers.find(u => u.id === teacherId));
        if (teacher && teacher.schoolId) return String(teacher.schoolId).trim();
      }

      // 3. If item is a material, check its parent subject folder
      if (item.subjectId || item.subject_id || item.subject || item.folderId) {
        const sub = (typeof findSubjectForMaterial === 'function') ? findSubjectForMaterial(item) : null;
        if (sub && sub !== item) {
          if (sub.schoolId && String(sub.schoolId).trim() !== '') return String(sub.schoolId).trim();
          const subTeacherId = sub.teacherId || sub.uploaderId || sub.uploadedBy || sub.userId;
          if (subTeacherId) {
            const subTeacher = (cachedTeachers && cachedTeachers.find(t => t.id === subTeacherId)) ||
                               (cachedUsers && cachedUsers.find(u => u.id === subTeacherId));
            if (subTeacher && subTeacher.schoolId) return String(subTeacher.schoolId).trim();
          }
        }
      }

      // 4. By teacher/uploader display name fallback
      const uploaderName = (item.teacherName || item.uploaderName || item.author || '').trim().toLowerCase();
      if (uploaderName) {
        const teacherByName = (cachedTeachers && cachedTeachers.find(t => {
          const fn = `${t.firstName || ''} ${t.lastName || ''}`.trim().toLowerCase();
          return fn && (fn === uploaderName || uploaderName.includes(fn));
        })) || (cachedUsers && cachedUsers.find(u => {
          const fn = `${u.firstName || ''} ${u.lastName || ''}`.trim().toLowerCase();
          return fn && (fn === uploaderName || uploaderName.includes(fn));
        }));
        if (teacherByName && teacherByName.schoolId) return String(teacherByName.schoolId).trim();
      }

      // 5. If it's a subject, check if any child materials belong to a known school
      if (cachedMaterials && !item.subjectId && !item.pdfUrl) {
        const childMat = cachedMaterials.find(m => materialBelongsToSubject(m, item) && (m.schoolId || m.teacherId || m.uploadedBy));
        if (childMat) {
          if (childMat.schoolId) return String(childMat.schoolId).trim();
          const matTeacherId = childMat.teacherId || childMat.uploadedBy;
          const matTeacher = (cachedTeachers && cachedTeachers.find(t => t.id === matTeacherId)) ||
                             (cachedUsers && cachedUsers.find(u => u.id === matTeacherId));
          if (matTeacher && matTeacher.schoolId) return String(matTeacher.schoolId).trim();
        }
      }

      return '';
    };

    window.toggleSubjectFilter = function(subId) {
      if (selectedSubjectFilter === subId) {
        selectedSubjectFilter = "ALL";
      } else {
        selectedSubjectFilter = subId;
      }
      renderSubjectFolders();
      renderMaterialsTable();
      
      if (selectedSubjectFilter === "ALL") {
        window.showToast("Showing all PDF documents across all subjects.", "info");
      } else {
        const sub = cachedSubjects.find(s => s.id === subId);
        window.showToast(`Filtered documents: ${sub ? sub.name : 'Selected Subject'}`, "info");
      }
    };

    function renderSubjectFolders() {
      const container = document.getElementById('subjectFoldersContainer');
      if (!container) return;

      let list = cachedSubjects;
      if (selectedJurisdiction !== 'ALL') {
        list = list.filter(s => getSchoolIdForLibraryItem(s) === selectedJurisdiction);
      }

      if (list.length === 0) {
        container.innerHTML = `
          <div class="col-span-3 p-6 text-center text-slate-400 bg-slate-50 rounded-xl border border-dashed border-slate-300">
            No subject folders found. Subject folders are organized by faculty in the mobile app.
          </div>`;
        return;
      }

      const visibleMats = (selectedJurisdiction === 'ALL')
        ? cachedMaterials
        : cachedMaterials.filter(m => getSchoolIdForLibraryItem(m) === selectedJurisdiction);
      const allTotal = visibleMats.length;
      const isAllActive = selectedSubjectFilter === 'ALL';

      // "All Folders" overview card
      let cardsHtml = `
        <div onclick="toggleSubjectFilter('ALL')" class="p-4 rounded-xl border transition-all cursor-pointer space-y-2 select-none ${isAllActive ? 'bg-indigo-50 border-indigo-500 shadow-md ring-2 ring-indigo-500/20' : 'bg-slate-50 border-slate-200 hover:border-indigo-300 hover:bg-white'}">
          <div class="flex items-center justify-between">
            <span class="font-bold text-slate-800 text-sm flex items-center gap-1.5">
              ${uiIcon('book', 'w-4 h-4 text-indigo-600 inline')} <span>All Subject Folders</span>
            </span>
            <span class="px-2 py-0.5 rounded-full text-[10px] font-bold ${isAllActive ? 'bg-indigo-600 text-white' : 'bg-slate-200 text-slate-700'}">
              ${list.length} Folders
            </span>
          </div>
          <div class="text-[11px] text-slate-500">Comprehensive division document registry</div>
          <div class="flex items-center justify-between pt-1 border-t border-slate-200/80 text-xs">
            <span class="font-bold text-indigo-700">${allTotal} PDF Document${allTotal === 1 ? '' : 's'}</span>
            <span class="text-[11px] font-semibold ${isAllActive ? 'text-indigo-700 underline' : 'text-slate-500'}">
              ${isAllActive ? `Active View ${uiIcon('check', 'w-3 h-3 inline text-indigo-700')}` : 'View All &rarr;'}
            </span>
          </div>
        </div>
      `;

      cardsHtml += list.map(s => {
        const matsInSub = cachedMaterials.filter(m => materialBelongsToSubject(m, s)).length;
        const isActive = selectedSubjectFilter === s.id;
        const sSchool = getSchoolIdForLibraryItem(s);

        return `
          <div onclick="toggleSubjectFilter('${s.id}')" class="p-4 rounded-xl border transition-all cursor-pointer space-y-2 select-none ${isActive ? 'bg-indigo-50 border-indigo-500 shadow-md ring-2 ring-indigo-500/20' : 'bg-slate-50 border-slate-200 hover:border-indigo-300 hover:bg-white'}">
            <div class="flex items-center justify-between">
              <span class="font-bold text-slate-800 text-sm flex items-center gap-1.5">
                ${uiIcon('folder', 'w-4 h-4 text-amber-500 inline')} <span>${escapeHtml(s.name || 'Subject')}</span>
              </span>
            </div>
            <div class="text-[11px] text-slate-500 flex items-center justify-between">
              <span>By: ${escapeHtml(s.teacherName || 'Teacher')}</span>
              <span class="text-slate-400 text-[10px] font-mono">${escapeHtml(getSchoolName(sSchool))}</span>
            </div>
            <div class="flex items-center justify-between pt-1 border-t border-slate-200/80 text-xs">
              <span class="font-bold ${matsInSub > 0 ? 'text-indigo-600' : 'text-slate-400'}">
                ${matsInSub} PDF Document${matsInSub === 1 ? '' : 's'}
              </span>
              <span class="text-[11px] font-semibold ${isActive ? 'text-indigo-700 underline' : 'text-slate-500'}">
                ${isActive ? `Active Filter ${uiIcon('check', 'w-3 h-3 inline text-indigo-700')}` : 'Filter &rarr;'}
              </span>
            </div>
          </div>
        `;
      }).join('');

      container.innerHTML = cardsHtml;
    }

    // Material CRUD (library_materials) - Disabled for Admins (Teacher Mobile Only)
    window.openCreateMaterialModal = function() {
      window.showToast("Curricular PDF uploads are strictly managed by Teachers in the mobile application.", "warning");
    };

    window.openEditMaterialModal = function(id) {
      window.showToast("Institutional Safeguard: Classroom materials and PDFs are authored exclusively by faculty in the mobile app. Neither School Admins nor Super Admins can edit teacher materials.", "warning");
    };

    window.closeMaterialModal = function() {
      document.getElementById('modalMaterial').classList.add('hidden');
    };

    window.handleMaterialSubmit = async function(e) {
      e?.preventDefault();
      window.showToast("Curricular materials cannot be created or modified from the administrative portal.", "warning");
      closeMaterialModal();
    };

    window.deleteMaterial = async function(id, title) {
      window.showToast("Institutional Safeguard: Neither School Admins nor Super Admins have permission to delete faculty learning materials or PDFs.", "warning");
    };

    function renderMaterialsTable() {
      const tbody = document.getElementById('materialsTableBody');
      if (!tbody) return;

      let list = cachedMaterials;

      // 1. School Jurisdiction filter
      if (selectedJurisdiction !== 'ALL') {
        list = list.filter(m => {
          const mSchool = getSchoolIdForLibraryItem(m);
          return mSchool === selectedJurisdiction;
        });
      }

      // 2. Subject Folder filter
      const banner = document.getElementById('materialsActiveFilterBanner');
      if (selectedSubjectFilter !== 'ALL') {
        const activeSub = cachedSubjects.find(s => s.id === selectedSubjectFilter);
        list = list.filter(m => materialBelongsToSubject(m, activeSub));

        if (banner) {
          banner.classList.remove('hidden');
          document.getElementById('filterBannerSubjectName').textContent = activeSub ? activeSub.name : 'Selected Subject';
          document.getElementById('filterBannerCount').textContent = `${list.length} PDF Document${list.length === 1 ? '' : 's'}`;
        }
      } else {
        if (banner) banner.classList.add('hidden');
      }

      // 3. Dedicated Grade Filter
      const activeMatGrade = materialGradeFilter || document.getElementById('selectMaterialGradeFilter')?.value || 'ALL';
      if (activeMatGrade !== 'ALL') {
        const targetGradeNum = activeMatGrade.replace('Grade ', '').trim();
        list = list.filter(m => {
          const sub = findSubjectForMaterial(m);
          const subName = sub ? (sub.name || '') : '';
          const blob = `${m.title || ''} ${subName} ${m.grade || ''}`;
          return blob.includes(activeMatGrade) || blob.includes(`Grade ${targetGradeNum}`) || blob.includes(targetGradeNum);
        });
      }

      // 4. Dedicated Smart Tokenized Search
      const activeMatSearch = materialSearchQuery || (document.getElementById('inputMaterialSearch')?.value || '').toLowerCase().trim();
      if (activeMatSearch) {
        list = list.filter(m => {
          const sub = findSubjectForMaterial(m);
          const subName = sub ? (sub.name || '') : '';
          const mSchool = getSchoolIdForLibraryItem(m);
          const textBlob = `${m.title || ''} ${subName} ${m.uploaderName || ''} ${getSchoolName(mSchool)} ${m.grade || ''}`;
          return matchSmartSearch(textBlob, activeMatSearch);
        });
      }

      if (list.length === 0) {
        const isFiltered = selectedSubjectFilter !== 'ALL' || selectedGradeFilter !== 'ALL' || searchQuery !== '';
        tbody.innerHTML = `
          <tr>
            <td colspan="6" class="p-8 text-center text-slate-400 font-medium">
              ${isFiltered ? 'No PDF reading materials match the active filters.' : 'No PDF materials uploaded yet.'}
              ${isFiltered ? '<button onclick="resetAllFilters()" class="block mx-auto mt-2 text-indigo-600 hover:underline font-bold text-xs">Reset all search filters</button>' : '<span class="block mt-1 text-slate-400 text-xs">Classroom reading materials are uploaded by teachers in the mobile app.</span>'}
            </td>
          </tr>`;
        return;
      }

      tbody.innerHTML = list.map(m => {
        const sub = findSubjectForMaterial(m);
        const subName = sub ? sub.name : (m.subjectName || m.subject || 'General Subject');
        const questions = getMaterialQuestions(m);
        const qCount = questions.length;
        const pdfUrl = getMaterialPdfUrl(m);
        const mSchool = getSchoolIdForLibraryItem(m);

        return `
          <tr class="hover:bg-slate-50/80 transition-colors">
            <td class="p-3">
              <span onclick="openPdfViewerModal('${m.id}')" class="font-bold text-slate-900 hover:text-indigo-600 cursor-pointer underline flex items-center gap-1.5">
                ${uiIcon('file', 'w-3.5 h-3.5 text-indigo-600 inline')} <span>${escapeHtml(m.title || 'Untitled Document')}</span>
              </span>
              <div class="text-[10px] text-slate-400 font-mono mt-0.5">ID: ${m.id ? m.id.substring(0,8) : 'N/A'}...</div>
            </td>
            <td class="p-3">
              <span class="font-semibold text-slate-700 flex items-center gap-1.5">
                ${uiIcon('folder', 'w-3.5 h-3.5 text-slate-500 inline')} <span>${escapeHtml(subName)}</span>
              </span>
              ${sub ? `<button onclick="toggleSubjectFilter('${sub.id}')" class="text-[10px] text-indigo-600 hover:underline font-semibold mt-0.5 block">Filter folder</button>` : ''}
            </td>
            <td class="p-3 text-slate-500">${escapeHtml(getSchoolName(mSchool))}</td>
            <td class="p-3">
              ${pdfUrl ? `
                <button onclick="openPdfViewerModal('${m.id}')" class="px-2.5 py-1 bg-indigo-50 hover:bg-indigo-100 text-indigo-700 rounded-lg text-xs font-semibold flex items-center gap-1.5 shadow-sm">
                  ${uiIcon('eye', 'w-3.5 h-3.5')} <span>View Document</span>
                </button>
              ` : `
                <span class="text-slate-400 text-xs italic">No URL</span>
              `}
            </td>
            <td class="p-3">
              <button onclick="openQuizInspectorModal('${m.id}')" class="px-2.5 py-1 bg-emerald-50 hover:bg-emerald-100 text-emerald-800 border border-emerald-200 rounded-lg text-xs font-bold flex items-center gap-1.5 shadow-sm">
                ${uiIcon('clipboard', 'w-3.5 h-3.5')} <span>${qCount} Question${qCount === 1 ? '' : 's'} &bull; Inspect &rarr;</span>
              </button>
            </td>
            <td class="p-3 text-right">
              <div class="flex items-center justify-end gap-1.5">
                <button onclick="openAdminRemarksModal('material', '${m.id}', '${(m.title || 'Material').replace(/'/g, "\'")}')" class="px-2 py-1 bg-indigo-50 hover:bg-indigo-100 text-indigo-700 border border-indigo-200 rounded text-xs font-bold flex items-center gap-1" title="Pedagogical Guidance & Remarks">
                  ${uiIcon('message', 'w-3.5 h-3.5')} <span>Remarks</span>
                </button>
                <span class="px-2 py-1 bg-slate-100 text-slate-500 rounded text-[11px] font-semibold border border-slate-200 select-none flex items-center gap-1" title="Classroom Faculty Upload: Read-only inspection">
                  ${uiIcon('lock', 'w-3 h-3')} <span>Read-Only</span>
                </span>
              </div>
            </td>
          </tr>
        `;
      }).join('');
    }

window.renderQuizQuestionsList = renderQuizQuestionsList;
window.renderSubjectFolders = renderSubjectFolders;
window.renderMaterialsTable = renderMaterialsTable;
