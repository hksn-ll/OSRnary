    // =========================================================================
    // 7. WEEKLY STUDENT ASSESSMENTS MANAGEMENT & ANALYTICS
    // =========================================================================
    window.updateAssessmentMetrics = function() {
      let list = cachedWeeklyAssessments.filter(a => !a.isDivisionUniform && a.sourceType !== 'division_uniform');
      let subs = cachedSubmissions;

      if (selectedJurisdiction !== 'ALL') {
        list = list.filter(a => String(a.schoolId) === selectedJurisdiction);
        subs = subs.filter(s => {
          const a = cachedWeeklyAssessments.find(item => item.id === s.assessmentId);
          return a && String(a.schoolId) === selectedJurisdiction;
        });
      }

      const now = Date.now();
      const activeCount = list.filter(a => a.status === 'active' && (!a.dueDate || a.dueDate > now)).length;

      const statTotal = document.getElementById('statTotalAssessments');
      const statActive = document.getElementById('statActiveAssessments');
      const statSub = document.getElementById('statTotalSubmissions');
      const statAvg = document.getElementById('statAvgAssessmentScore');

      if (statTotal) statTotal.textContent = list.length;
      if (statActive) statActive.textContent = activeCount;
      if (statSub) statSub.textContent = subs.length;

      if (statAvg) {
        statAvg.textContent = `${calculateAverageScore(subs)}%`;
      }
    };

    window.renderAssessmentsTable = function() {
      const tbody = document.getElementById('assessmentsTableBody');
      if (!tbody) return;

      let list = cachedWeeklyAssessments.filter(a => !a.isDivisionUniform && a.sourceType !== 'division_uniform');

      // School jurisdiction filter
      if (selectedJurisdiction !== 'ALL') {
        list = list.filter(a => String(a.schoolId) === selectedJurisdiction);
      }

      // Search filter (local or global)
      const search = (document.getElementById('assessmentSearchInput')?.value || searchQuery || '').toLowerCase().trim();
      if (search) {
        list = list.filter(a => {
          const textBlob = `${a.title || ''} ${a.subjectName || ''} ${a.teacherName || ''} ${a.className || ''} ${a.grade || ''}`;
          return matchSmartSearch(textBlob, search);
        });
      }

      // Grade filter (local or global)
      const gradeFilter = document.getElementById('assessmentGradeFilter')?.value || selectedGradeFilter || 'ALL';
      if (gradeFilter !== 'ALL') {
        const targetGradeNum = gradeFilter.replace('Grade ', '').trim();
        list = list.filter(a => (a.grade || '').includes(gradeFilter) || (a.className || '').includes(gradeFilter) || (a.grade || '').includes(targetGradeNum));
      }

      // Status filter
      const statusFilter = document.getElementById('assessmentStatusFilter')?.value || 'ALL';
      const now = Date.now();
      if (statusFilter === 'active') {
        list = list.filter(a => a.status === 'active' && (!a.dueDate || a.dueDate > now));
      } else if (statusFilter === 'closed') {
        list = list.filter(a => a.status === 'closed' || (a.dueDate && a.dueDate <= now));
      }

      const countBadge = document.getElementById('assessmentsCountBadge');
      if (countBadge) countBadge.textContent = `${list.length} Assessment${list.length === 1 ? '' : 's'}`;

      if (list.length === 0) {
        tbody.innerHTML = `
          <tr>
            <td colspan="7" class="p-8 text-center text-slate-400 font-medium">
              No weekly assessments found matching criteria.
            </td>
          </tr>`;
        return;
      }

      tbody.innerHTML = list.map(a => {
        const subs = [...cachedTimedSubmissions, ...cachedSubmissions].filter(s => s.assessmentId === a.id);
        const indCount = subs.filter(s => (s.philIriLevel && s.philIriLevel.toLowerCase().includes('independent')) || (s.percentage !== undefined ? s.percentage >= 80 : false)).length;
        const instCount = subs.filter(s => (s.philIriLevel && s.philIriLevel.toLowerCase().includes('instructional')) || (s.percentage !== undefined ? (s.percentage >= 59 && s.percentage < 80) : false)).length;
        const frustCount = subs.filter(s => (s.philIriLevel && s.philIriLevel.toLowerCase().includes('frustration')) || (s.percentage !== undefined ? s.percentage < 59 : false)).length;
        const subCount = subs.length;
        const avgPct = calculateAverageScore(subs, a.questionCount || 10);

        const isExpired = a.dueDate && a.dueDate <= now;
        const isActive = a.status === 'active' && !isExpired;

        let dueDateStr = "No due date";
        if (a.dueDate) {
          dueDateStr = new Date(a.dueDate).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' });
        }

        let sourceBadge = "";
        if (a.sourceType === "pdf") {
          sourceBadge = `<span class="px-2 py-0.5 rounded bg-blue-50 text-blue-700 text-[10px] font-bold border border-blue-200">PDF Upload</span>`;
        } else if (a.sourceType === "material") {
          sourceBadge = `<span class="px-2 py-0.5 rounded bg-purple-50 text-purple-700 text-[10px] font-bold border border-purple-200">Library Doc</span>`;
        } else {
          sourceBadge = `<span class="px-2 py-0.5 rounded bg-amber-50 text-amber-700 text-[10px] font-bold border border-amber-200">Topic Prompt</span>`;
        }

        const safeTitle = (a.title || 'Weekly Assessment').replace(/'/g, "\'");

        return `
          <tr class="hover:bg-slate-50/80 transition-colors">
            <td class="p-3.5">
              <div class="font-bold text-slate-900 text-sm">${a.title || 'Weekly Assessment'}</div>
              <div class="flex items-center gap-1.5 mt-1">
                <span class="px-2 py-0.5 rounded-full bg-purple-100 text-[#5341CD] text-[10px] font-bold">${a.subjectName || 'Subject'}</span>
                <span class="px-2 py-0.5 rounded-full bg-slate-100 text-slate-600 text-[10px] font-bold">${a.questionCount || 10} Items</span>
                ${sourceBadge}
              </div>
            </td>
            <td class="p-3.5">
              <div class="font-bold text-slate-800">${a.className || 'Class'}</div>
              <div class="text-[11px] text-slate-500">${a.grade || ''} &bull; ${getSchoolName(a.schoolId)}</div>
            </td>
            <td class="p-3.5">
              <div class="font-semibold text-slate-800">${a.teacherName || 'Faculty'}</div>
              <div class="text-[10px] text-slate-400 font-mono">ID: ${(a.teacherId || '').substring(0,8)}...</div>
            </td>
            <td class="p-3.5">
              <div class="font-semibold ${isExpired ? 'text-red-600' : 'text-slate-700'}">${dueDateStr}</div>
              ${isExpired ? '<span class="text-[10px] text-red-500 font-bold">Past Due</span>' : ''}
            </td>
            <td class="p-3.5">
              <div class="flex items-center gap-2">
                <button onclick="openAssessmentSubmissionsModal('${a.id}')" class="font-bold text-slate-800 hover:text-indigo-600 underline">
                  ${subCount} Turned In
                </button>
                ${subCount > 0 ? `<span class="px-2 py-0.5 rounded-full text-[10px] font-bold ${avgPct >= 75 ? 'bg-emerald-100 text-emerald-800' : 'bg-amber-100 text-amber-800'}">${avgPct}% Avg</span>` : ''}
              </div>
            </td>
            <td class="p-3.5">
              ${isActive ? `
                <span class="px-2.5 py-1 rounded-full bg-emerald-50 text-emerald-700 border border-emerald-200 text-[11px] font-bold flex items-center gap-1 w-fit">
                  <span class="w-1.5 h-1.5 rounded-full bg-emerald-500 animate-pulse"></span> Active
                </span>
              ` : `
                <span class="px-2.5 py-1 rounded-full bg-slate-100 text-slate-600 border border-slate-200 text-[11px] font-bold w-fit">
                  Closed
                </span>
              `}
            </td>
            <td class="p-3.5 text-right">
              <div class="flex items-center justify-end gap-1.5">
                <button onclick="openAdminRemarksModal('assessment', '${a.id}', '${safeTitle}')" class="px-2.5 py-1 bg-purple-50 hover:bg-purple-100 text-[#5341CD] border border-purple-200 rounded-lg text-xs font-bold shadow-xs flex items-center gap-1.5" title="Pedagogical Guidance & Remarks">
                  ${uiIcon('message', 'w-3.5 h-3.5')} <span>Remarks</span>
                </button>
                <button onclick="openAssessmentInspectorModal('${a.id}')" class="px-2.5 py-1 bg-emerald-50 hover:bg-emerald-100 text-emerald-800 border border-emerald-200 rounded-lg text-xs font-bold shadow-xs flex items-center gap-1.5">
                  ${uiIcon('clipboard', 'w-3.5 h-3.5')} <span>Questions</span>
                </button>
                <button onclick="openAssessmentSubmissionsModal('${a.id}')" class="px-2.5 py-1 bg-indigo-50 hover:bg-indigo-100 text-indigo-700 border border-indigo-200 rounded-lg text-xs font-semibold shadow-xs flex items-center gap-1.5">
                  ${uiIcon('chart', 'w-3.5 h-3.5')} <span>Submissions</span>
                </button>
                <span class="px-2 py-1 bg-slate-100 text-slate-500 rounded text-[11px] font-semibold border border-slate-200 select-none flex items-center gap-1" title="Classroom Faculty Assessment: Read-only inspection">
                  ${uiIcon('lock', 'w-3 h-3')} <span>Read-Only</span>
                </span>
              </div>
            </td>
          </tr>
        `;
      }).join('');
    };

    window.deleteAssessment = async function(id, title) {
      window.showToast("Institutional Safeguard: Weekly assessments are created and managed exclusively by teachers in the mobile app. Neither School Admins nor Super Admins can delete faculty assessments.", "warning");
    };

    window.openAssessmentSubmissionsModal = function(assessmentId) {
      const a = cachedDivisionTimedAssessments.find(item => item.id === assessmentId) || cachedWeeklyAssessments.find(item => item.id === assessmentId);
      if (!a) return;

      let subs = [...cachedTimedSubmissions.filter(s => s.assessmentId === assessmentId), ...cachedSubmissions.filter(s => s.assessmentId === assessmentId)];
      if (isSchoolAdmin && currentAdminUser?.schoolId) {
        const targetSchool = String(currentAdminUser.schoolId);
        subs = subs.filter(s => getSubmissionSchoolId(s) === targetSchool);
      } else if (selectedJurisdiction !== 'ALL') {
        subs = subs.filter(s => getSubmissionSchoolId(s) === selectedJurisdiction);
      }
      
      const isStandardized = Boolean(a.isDivisionUniform || a.sourceType === 'division_uniform' || (a.id && a.id.startsWith('timed_assessment_')));
      document.getElementById('submissionsModalTitle').textContent = isStandardized
        ? `Standardized Submissions: ${a.title || 'Division Timed Assessment'}`
        : `Submissions: ${a.title || 'Weekly Assessment'}`;

      const schoolScope = isSchoolAdmin 
        ? getSchoolName(currentAdminUser?.schoolId) 
        : (a.schoolId && a.schoolId !== 'ALL' ? getSchoolName(a.schoolId) : (selectedJurisdiction !== 'ALL' ? getSchoolName(selectedJurisdiction) : 'All Schools (Division-Wide)'));
      document.getElementById('submissionsModalSubtitle').textContent = `${a.className || (isStandardized ? 'All Sections' : 'Class')} • ${a.subjectName || (isStandardized ? 'Phil-IRI Standard' : 'Subject')} • ${schoolScope}`;

      // Lock modal school filter to prevent cross-school inspection by School Admin
      const modalSchFilter = document.getElementById('subModalSchoolFilter');
      if (modalSchFilter) {
        if (isSchoolAdmin && currentAdminUser?.schoolId) {
          modalSchFilter.innerHTML = `<option value="${currentAdminUser.schoolId}">${getSchoolName(currentAdminUser.schoolId)}</option>`;
          modalSchFilter.value = String(currentAdminUser.schoolId);
          modalSchFilter.disabled = true;
          modalSchFilter.classList.add('hidden');
        } else {
          modalSchFilter.classList.remove('hidden');
          modalSchFilter.disabled = false;
        }
      }

      const totalItems = a.questionCount || 10;
      const maleSubs = subs.filter(s => {
        const u = cachedUsers.find(item => item.id === s.studentUid || item.uid === s.studentUid);
        const g = (s.studentGender || u?.gender || u?.sex || '').toLowerCase();
        return g.startsWith('m');
      });
      const femaleSubs = subs.filter(s => {
        const u = cachedUsers.find(item => item.id === s.studentUid || item.uid === s.studentUid);
        const g = (s.studentGender || u?.gender || u?.sex || '').toLowerCase();
        return g.startsWith('f');
      });

      const avg = calculateAverageScore(subs, totalItems);
      const maleAvg = calculateAverageScore(maleSubs, totalItems);
      const femaleAvg = calculateAverageScore(femaleSubs, totalItems);

      document.getElementById('subModalTurnedIn').textContent = `${subs.length}`;
      if (document.getElementById('subModalBoysStat')) {
        document.getElementById('subModalBoysStat').textContent = `${maleSubs.length} turned in • ${maleAvg}% avg`;
      }
      if (document.getElementById('subModalGirlsStat')) {
        document.getElementById('subModalGirlsStat').textContent = `${femaleSubs.length} turned in • ${femaleAvg}% avg`;
      }
      document.getElementById('subModalClassAvg').textContent = `${avg}%`;

      const tbody = document.getElementById('assessmentSubmissionsTableBody');
      if (subs.length === 0) {
        tbody.innerHTML = `
          <tr>
            <td colspan="6" class="p-8 text-center text-slate-400 font-medium">
              ${isSchoolAdmin ? `No students from ${escapeHtml(schoolScope || 'your school')} have completed this assessment yet.` : 'No students have completed this assessment yet.'}
            </td>
          </tr>`;
      } else {
        tbody.innerHTML = subs.map(sub => {
          const studentName = sub.studentName || 'Student';
          const studentEmail = sub.studentEmail || sub.studentUid || '';
          const score = sub.score !== undefined ? sub.score : 0;
          const total = sub.totalQuestions || totalItems;
          const pct = sub.percentage !== undefined ? sub.percentage : Math.round((score * 100) / total);
          const dateStr = sub.submittedAt ? new Date(sub.submittedAt).toLocaleString() : 'Recently';

          const u = cachedUsers.find(item => item.id === sub.studentUid || item.uid === sub.studentUid);
          const rawG = (sub.studentGender || u?.gender || u?.sex || '').toLowerCase();
          const genderBadge = rawG.startsWith('m')
            ? '<span class="px-2 py-0.5 rounded-full bg-indigo-50 text-indigo-700 border border-indigo-200 text-[10px] font-bold">👦 Male</span>'
            : (rawG.startsWith('f')
              ? '<span class="px-2 py-0.5 rounded-full bg-rose-50 text-rose-700 border border-rose-200 text-[10px] font-bold">👧 Female</span>'
              : '<span class="px-2 py-0.5 rounded-full bg-slate-100 text-slate-500 border border-slate-200 text-[10px] font-bold">—</span>');

          const pctBadgeClass = pct >= 75 ? 'bg-emerald-50 text-emerald-700 border-emerald-200' : (pct >= 50 ? 'bg-amber-50 text-amber-700 border-amber-200' : 'bg-red-50 text-red-700 border-red-200');
          const philIri = sub.philIriLevel || (pct >= 80 ? 'Independent' : (pct >= 59 ? 'Instructional' : 'Frustration'));
          const wpmStr = sub.readingSpeedWpm ? ` • ${sub.readingSpeedWpm} WPM${sub.readingSpeedWps ? ` (${Number(sub.readingSpeedWps).toFixed(1)} WPS)` : ''}` : '';
          const philBadgeClass = philIri.toLowerCase().includes('ind')
            ? 'bg-emerald-50 text-emerald-700 border-emerald-200'
            : (philIri.toLowerCase().includes('ins')
              ? 'bg-amber-50 text-amber-700 border-amber-200'
              : 'bg-rose-50 text-rose-700 border-rose-200 font-extrabold');

          return `
            <tr class="hover:bg-slate-50/80 transition-colors">
              <td class="p-3 font-bold text-slate-900">${studentName}</td>
              <td class="p-3">${genderBadge}</td>
              <td class="p-3 text-slate-500 font-mono text-[11px]">${studentEmail}</td>
              <td class="p-3 font-bold text-slate-800">${score} / ${total}</td>
              <td class="p-3">
                <div class="flex items-center gap-1.5 flex-wrap">
                  <span class="px-2 py-0.5 rounded-full border text-[11px] font-bold ${pctBadgeClass}">
                    ${pct}%
                  </span>
                  <span class="px-2 py-0.5 rounded-full border text-[10px] font-bold ${philBadgeClass}">
                    ${escapeHtml(philIri)}${escapeHtml(wpmStr)}
                  </span>
                </div>
              </td>
              <td class="p-3 text-right text-slate-400 text-[11px]">${dateStr}</td>
            </tr>
          `;
        }).join('');
      }

      document.getElementById('modalAssessmentSubmissions').classList.remove('hidden');
    };

    window.closeAssessmentSubmissionsModal = function() {
      document.getElementById('modalAssessmentSubmissions').classList.add('hidden');
    };


    // =========================================================================
    // 7.1 STANDARDIZED DIVISION TIMED ASSESSMENTS ROSTER & STUDIO ENGINE
    // =========================================================================

        // Aggregates standardized timed assessments from division collection & weekly fallback with de-duplication
    function getUnifiedTimedTests() {
      const map = new Map();
      (cachedDivisionTimedAssessments || []).forEach(item => {
        if (item && item.id) {
          map.set(item.id, { ...item, sourceType: 'division_uniform' });
        }
      });
      (cachedWeeklyAssessments || []).forEach(item => {
        if (item && item.id) {
          if (item.isDivisionUniform === true || item.sourceType === 'division_uniform' || item.id.startsWith('timed_assessment_')) {
            map.set(item.id, { ...item, sourceType: 'division_uniform' });
          }
        }
      });
      return Array.from(map.values());
    }
    window.getUnifiedTimedTests = getUnifiedTimedTests;

    // Resolves authentic schoolId for any assessment submission across models
    function getSubmissionSchoolId(s) {
      if (!s) return '';
      if (s.schoolId && s.schoolId !== 'ALL') return String(s.schoolId);
      const studentUid = String(s.studentUid || s.studentId || s.uid || '').trim();
      if (studentUid) {
        const student = (cachedUsers || []).find(u => u.id === studentUid || u.uid === studentUid);
        if (student && student.schoolId) return String(student.schoolId);
        const pending = (cachedPendingStudents || []).find(p => p.id === studentUid || p.uid === studentUid || p.studentUid === studentUid);
        if (pending && pending.schoolId) return String(pending.schoolId);
      }
      return '';
    }
    window.getSubmissionSchoolId = getSubmissionSchoolId;

    // Updates KPI summary cards for Standardized Reading Tests (Scoped for School Admin)
    window.updateTimedAssessmentMetrics = function() {
      let allTests = getUnifiedTimedTests();
      const adminSchoolId = (isSchoolAdmin && currentAdminUser?.schoolId) ? String(currentAdminUser.schoolId) : null;
      const adminDistrict = adminSchoolId ? getSchoolDistrict(adminSchoolId) : null;

      if (adminSchoolId) {
        allTests = allTests.filter(a => {
          if (a.schoolId && a.schoolId !== 'ALL' && String(a.schoolId) !== adminSchoolId) return false;
          if (a.districtScope && a.districtScope !== 'ALL' && a.districtScope !== adminDistrict) return false;
          return true;
        });
      }

      const now = Date.now();
      const activeTests = allTests.filter(a => a.status === 'active' && (!a.dueDate || a.dueDate > now));
      
      const allSubs = [...(cachedTimedSubmissions || []), ...(cachedSubmissions || [])];
      const divTestIds = new Set(allTests.map(t => t.id));
      let relevantSubs = allSubs.filter(s => divTestIds.has(s.assessmentId));

      if (adminSchoolId) {
        relevantSubs = relevantSubs.filter(s => getSubmissionSchoolId(s) === adminSchoolId);
      } else if (selectedJurisdiction !== 'ALL') {
        relevantSubs = relevantSubs.filter(s => getSubmissionSchoolId(s) === selectedJurisdiction);
      }

      const totalEl = document.getElementById('statTotalTimedTests');
      if (totalEl) totalEl.textContent = allTests.length;

      const activeEl = document.getElementById('statActiveTimedTests');
      if (activeEl) activeEl.textContent = activeTests.length;

      const subsEl = document.getElementById('statDivisionSubmissions');
      if (subsEl) subsEl.textContent = relevantSubs.length;

      const avgEl = document.getElementById('statDivisionAvgScore');
      if (avgEl) {
        if (relevantSubs.length === 0) {
          avgEl.textContent = "0%";
        } else {
          let sumPct = 0;
          relevantSubs.forEach(s => {
            const pct = s.percentage !== undefined ? s.percentage : (s.score ? Math.round((s.score / (s.totalQuestions || 5)) * 100) : 0);
            sumPct += pct;
          });
          avgEl.textContent = Math.round(sumPct / relevantSubs.length) + "%";
        }
      }

      const labelActive = document.getElementById('labelStatActiveTimedTests');
      if (labelActive) labelActive.textContent = isSchoolAdmin ? "Active School Tests" : "Active Division Tests";

      const labelSubs = document.getElementById('labelStatDivisionSubmissions');
      if (labelSubs) labelSubs.textContent = isSchoolAdmin ? "School Submissions" : "Division Submissions";

      const labelAvg = document.getElementById('labelStatDivisionAvgScore');
      if (labelAvg) labelAvg.textContent = isSchoolAdmin ? "School MPS Avg" : "Division MPS Avg";
    };


    // =========================================================================
    // PASSAGE VIEWER MODAL LIFECYCLE
    // =========================================================================
    window.activeViewingPassageAssessmentId = null;
    window.activeViewingPassageText = "";

    window.openPassageViewerModal = function(titleOrObj, encodedPassage = "", metadata = {}) {
      const modal = document.getElementById('modalPassageViewer');
      if (!modal) return;

      let title = "Standardized Reading Passage";
      let passageText = "";
      let assessmentId = null;
      let purpose = "Phil-IRI Reading Diagnostic";
      let grade = "Junior High (Grades 7-10)";

      if (typeof titleOrObj === 'object' && titleOrObj !== null) {
        const a = titleOrObj;
        assessmentId = a.id;
        title = a.title || title;
        passageText = a.passage || "";
        purpose = a.assessmentPurpose || purpose;
        grade = a.grade || grade;
      } else {
        title = titleOrObj || title;
        try {
          passageText = encodedPassage ? decodeURIComponent(encodedPassage) : "";
        } catch (_) {
          passageText = encodedPassage || "";
        }
        assessmentId = metadata.id || null;
        purpose = metadata.assessmentPurpose || purpose;
        grade = metadata.grade || grade;
      }

      if (!assessmentId) {
        const all = getUnifiedTimedTests();
        const found = all.find(x => x.title === title || (x.passage && x.passage === passageText));
        if (found) assessmentId = found.id;
      }

      window.activeViewingPassageAssessmentId = assessmentId;
      window.activeViewingPassageText = passageText;

      const words = passageText ? passageText.trim().split(/\s+/).filter(Boolean).length : 0;
      const readMins = Math.max(1, Math.round(words / 150));

      const titleEl = document.getElementById('passageViewerTitle');
      if (titleEl) titleEl.textContent = title;

      const metaEl = document.getElementById('passageViewerMeta');
      if (metaEl) metaEl.textContent = `${grade} • ${purpose}`;

      const wordBadge = document.getElementById('passageViewerWordCountBadge');
      if (wordBadge) wordBadge.textContent = `${words} words`;

      const timeBadge = document.getElementById('passageViewerTimeBadge');
      if (timeBadge) timeBadge.textContent = words > 0 ? `~${readMins} min read (@150 wpm)` : '~0 min read';

      const bodyEl = document.getElementById('passageViewerBody');
      if (bodyEl) {
        bodyEl.textContent = passageText || "No reading passage attached to this standardized assessment.";
      }

      const btnEditStudio = document.getElementById('btnPassageViewerEditStudio');
      if (btnEditStudio) {
        btnEditStudio.style.display = isSchoolAdmin ? 'none' : 'inline-flex';
      }

      modal.classList.remove('hidden');
    };

    window.closePassageViewerModal = function() {
      const modal = document.getElementById('modalPassageViewer');
      if (modal) modal.classList.add('hidden');
      window.activeViewingPassageAssessmentId = null;
      window.activeViewingPassageText = "";
    };

    window.copyPassageText = async function() {
      const text = window.activeViewingPassageText || document.getElementById('passageViewerBody')?.textContent || "";
      if (!text) {
        window.showToast("No passage text to copy.", "warning");
        return;
      }
      try {
        await navigator.clipboard.writeText(text);
        window.showToast("Passage copied to clipboard!", "success");
      } catch (_) {
        const tArea = document.createElement('textarea');
        tArea.value = text;
        document.body.appendChild(tArea);
        tArea.select();
        document.execCommand('copy');
        tArea.remove();
        window.showToast("Passage copied to clipboard!", "success");
      }
    };

    window.openStudioFromPassageViewer = function() {
      if (isSchoolAdmin) {
        window.showToast("Standardized assessment authoring and studio editing are reserved exclusively for SDO Super Administrators.", "info");
        return;
      }
      const id = window.activeViewingPassageAssessmentId;
      window.closePassageViewerModal();
      if (id) {
        window.openStandardizedTestStudio(id);
      } else {
        window.openStandardizedTestStudio(null);
      }
    };


    // =========================================================================
    // STANDARDIZED TEST STUDIO & QUESTION BUILDER
    // =========================================================================
    let aiTimedGeneratedQuizPool = [];
    window.aiTimedGeneratedQuizPool = aiTimedGeneratedQuizPool;
    window.activeEditingAssessmentId = null;

    window.openStandardizedTestStudio = function(assessmentId = null) {
      if (isSchoolAdmin) {
        window.showToast("Standardized assessment authoring and studio editing are reserved exclusively for SDO Super Administrators.", "info");
        return;
      }
      window.activeEditingAssessmentId = assessmentId;
      const modal = document.getElementById('modalAITimedAssessmentGenerator');
      if (!modal) return;

      const modeBadge = document.getElementById('aiTimedModalModeBadge');
      const titleInput = document.getElementById('aiTimedTitle');
      const purposeSelect = document.getElementById('aiTimedPurpose');
      const gradeSelect = document.getElementById('aiTimedGrade');
      const districtSelect = document.getElementById('aiTimedDistrict');
      const timerSelect = document.getElementById('aiTimedTimer');
      const dueDateInput = document.getElementById('aiTimedDueDate');
      const passageInput = document.getElementById('aiTimedPastedPassage');
      const btnDeploy = document.getElementById('btnDeployAITimedAssessment');

      if (assessmentId) {
        const all = getUnifiedTimedTests();
        const item = all.find(a => a.id === assessmentId);
        if (!item) {
          window.showToast("Assessment not found.", "error");
          return;
        }
        if (modeBadge) {
          modeBadge.textContent = item.status === 'draft' ? "Editing Draft" : "Editing Active Test";
          modeBadge.className = item.status === 'draft' 
            ? "px-2 py-0.5 rounded-full text-[10px] font-bold bg-amber-100 text-amber-800"
            : "px-2 py-0.5 rounded-full text-[10px] font-bold bg-purple-100 text-[#5341CD]";
        }
        if (titleInput) titleInput.value = item.title || "";
        if (purposeSelect) purposeSelect.value = item.assessmentPurpose || "Phil-IRI Pre-Test (Baseline Diagnostic)";
        if (gradeSelect) gradeSelect.value = item.grade || "Grades 7-10";
        if (districtSelect) districtSelect.value = item.districtScope || "ALL";
        if (timerSelect) timerSelect.value = String(item.timerMinutes || item.durationMinutes || 15);
        if (dueDateInput && item.dueDate) {
          dueDateInput.value = new Date(item.dueDate).toISOString().split('T')[0];
        }
        if (passageInput) passageInput.value = item.passage || "";

        aiTimedGeneratedQuizPool = [];
        if (item.quiz_pool_json) {
          try {
            const parsed = typeof item.quiz_pool_json === 'string' ? JSON.parse(item.quiz_pool_json) : item.quiz_pool_json;
            if (Array.isArray(parsed)) aiTimedGeneratedQuizPool = parsed;
          } catch (_) {}
        }
        if (aiTimedGeneratedQuizPool.length === 0 && Array.isArray(item.questions)) {
          aiTimedGeneratedQuizPool = item.questions;
        }
        if (btnDeploy) {
          btnDeploy.innerHTML = `<svg class="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/><circle cx="12" cy="11" r="3"/></svg><span>${item.status === 'draft' ? 'Deploy Division-Wide' : 'Update &amp; Deploy'}</span>`;
        }
      } else {
        if (modeBadge) {
          modeBadge.textContent = "New Assessment Draft";
          modeBadge.className = "px-2 py-0.5 rounded-full text-[10px] font-bold bg-purple-100 text-[#5341CD]";
        }
        // Blank studio workspace: completely empty by default for user authoring
        if (titleInput) titleInput.value = "";
        if (purposeSelect) purposeSelect.value = "Phil-IRI Pre-Test (Baseline Diagnostic)";
        if (gradeSelect) gradeSelect.value = "Grades 7-10";
        if (districtSelect) districtSelect.value = "ALL";
        if (timerSelect) timerSelect.value = "15"; // Phil-IRI Standard default 15 min
        if (dueDateInput) {
          const d = new Date();
          d.setDate(d.getDate() + 14);
          dueDateInput.value = d.toISOString().split('T')[0];
        }
        if (passageInput) passageInput.value = "";
        aiTimedGeneratedQuizPool = [];
        window.aiTimedGeneratedQuizPool = aiTimedGeneratedQuizPool;
        if (btnDeploy) {
          btnDeploy.innerHTML = `<svg class="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/><circle cx="12" cy="11" r="3"/></svg><span>Deploy Division-Wide</span>`;
        }
      }

      // Check centralized AI key status
      const statusPill = document.getElementById('aiTimedCentralKeyStatus');
      if (statusPill) {
        if (window.centralizedGeminiApiKey) {
          statusPill.innerHTML = `
            <span class="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-[11px] font-semibold bg-emerald-50 text-emerald-700 border border-emerald-200">
              <span class="w-1.5 h-1.5 rounded-full bg-emerald-500 animate-pulse"></span>
              <span>Centralized Gemini AI Engine Ready</span>
            </span>`;
        } else {
          statusPill.innerHTML = `
            <span class="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-[11px] font-semibold bg-amber-50 text-amber-700 border border-amber-200">
              <span class="w-1.5 h-1.5 rounded-full bg-amber-500"></span>
              <span>Gemini Key Not Configured (Set in System Settings)</span>
            </span>`;
        }
      }

      window.updateAITimedPassageStats();
      window.updateAITimedBlueprintPreview();
      window.renderAITimedQuestions();
      modal.classList.remove('hidden');
    };

        window.loadSampleAITimedPassage = function() {
      const pInput = document.getElementById('aiTimedPastedPassage');
      const tInput = document.getElementById('aiTimedTitle');
      if (pInput) {
        pInput.value = "The La Mesa Watershed in Quezon City is the last remaining rainforest of its size in Metro Manila. Covering an area of 2,700 hectares, it surrounds the reservoir from which roughly 1.5 million residents of the metropolis draw their drinking water. Over recent decades, illegal logging and slash-and-burn farming severely depleted the forest cover, threatening both water purity and local biodiversity. In response, environmental groups and local governments initiated large-scale reforestation programs, planting native trees such as narra, kupang, and white lauan. Today, the revived canopy serves as a vital ecological lung for the National Capital Region, regulating urban temperatures and providing sanctuary for endangered Philippine wildlife.";
        window.updateAITimedPassageStats();
      }
      if (tInput && !tInput.value) {
        tInput.value = "Phil-IRI Reading Comprehension: La Mesa Watershed";
      }
      window.showToast("DepEd sample passage loaded into studio workspace.", "info");
    };

    window.openAITimedAssessmentGeneratorModal = function() {
      if (isSchoolAdmin) {
        window.showToast("Standardized assessment authoring is reserved exclusively for SDO Super Administrators.", "info");
        return;
      }
      window.openStandardizedTestStudio(null);
    };

    window.closeAITimedAssessmentGeneratorModal = function() {
      const modal = document.getElementById('modalAITimedAssessmentGenerator');
      if (modal) modal.classList.add('hidden');
      window.activeEditingAssessmentId = null;
    };

    window.updateAITimedPassageStats = function() {
      const text = document.getElementById('aiTimedPastedPassage')?.value.trim() || '';
      const words = text ? text.split(/\s+/).filter(Boolean).length : 0;
      const wordBadge = document.getElementById('aiTimedPassageWordBadge');
      const timeBadge = document.getElementById('aiTimedReadingTimeBadge');

      if (wordBadge) wordBadge.textContent = `${words} word${words === 1 ? '' : 's'}`;
      if (timeBadge) {
        const minutes = Math.max(1, Math.round(words / 150));
        timeBadge.textContent = words > 0 ? `~${minutes} min read` : '~0 min read';
      }
    };

    // Cognitive Blueprint Calculator for DepEd Phil-IRI Standard (DepEd Order No. 14, s. 2018)
    window.getAITimedCognitiveBlueprint = function(qCount, compFocus) {
      const n = parseInt(qCount, 10) || 5;
      const focus = String(compFocus || 'BALANCED').toUpperCase();

      let litCount = 0;
      let infCount = 0;
      let critCount = 0;

      if (focus === 'LITERAL') {
        litCount = n;
      } else if (focus === 'INFERENTIAL') {
        infCount = n;
      } else if (focus === 'CRITICAL') {
        critCount = n;
      } else {
        // BALANCED: 40% Literal, 30% Inferential, 30% Critical (DepEd Phil-IRI Standard)
        if (n === 3) {
          litCount = 1;
          infCount = 1;
          critCount = 1;
        } else if (n === 5) {
          litCount = 2;
          infCount = 2;
          critCount = 1;
        } else if (n === 8) {
          litCount = 3;
          infCount = 3;
          critCount = 2;
        } else if (n === 10) {
          litCount = 4;
          infCount = 3;
          critCount = 3;
        } else if (n === 15) {
          litCount = 6;
          infCount = 5;
          critCount = 4;
        } else {
          litCount = Math.max(1, Math.round(n * 0.40));
          infCount = Math.max(1, Math.round(n * 0.30));
          critCount = n - (litCount + infCount);
          if (critCount < 1 && n >= 3) {
            if (infCount > 1) {
              infCount--;
              critCount++;
            } else if (litCount > 1) {
              litCount--;
              critCount++;
            }
          }
        }
      }

      const itemSchedule = [];
      for (let i = 0; i < litCount; i++) itemSchedule.push('LITERAL');
      for (let i = 0; i < infCount; i++) itemSchedule.push('INFERENTIAL');
      for (let i = 0; i < critCount; i++) itemSchedule.push('CRITICAL');

      const litPct = Math.round((litCount / n) * 100);
      const infPct = Math.round((infCount / n) * 100);
      const critPct = Math.round((critCount / n) * 100);

      let summaryText = "";
      if (focus === 'BALANCED') {
        summaryText = `Target Blueprint: ${litCount} Literal (${litPct}%) • ${infCount} Inferential (${infPct}%) • ${critCount} Critical (${critPct}%)`;
      } else {
        const readableFocus = focus === 'LITERAL' ? 'Literal Recall' : (focus === 'INFERENTIAL' ? 'Inferential Reasoning' : 'Critical Evaluation');
        summaryText = `Target Blueprint: ${n} ${readableFocus} Items (100% Focused)`;
      }

      let orderGuide = "";
      if (focus === 'BALANCED') {
        orderGuide = `Item 1 to ${litCount}: LITERAL; Item ${litCount + 1} to ${litCount + infCount}: INFERENTIAL; Item ${litCount + infCount + 1} to ${n}: CRITICAL`;
      } else {
        orderGuide = `All items 1 to ${n}: ${focus}`;
      }

      let promptDirectives = "";
      if (focus === 'BALANCED') {
        promptDirectives = `MANDATORY COGNITIVE DIMENSION BREAKDOWN FOR ${n} QUESTIONS:
- EXACTLY ${litCount} question(s) of cognitive dimension "LITERAL" (Directly stated facts, dates, quantities, names, or explicit details found in the passage).
- EXACTLY ${infCount} question(s) of cognitive dimension "INFERENTIAL" (Logical deductions, character motives, cause-and-effect reasoning, context clues).
- EXACTLY ${critCount} question(s) of cognitive dimension "CRITICAL" (Higher-Order Thinking Skills / HOTS: evaluating author's intent/tone, evaluating arguments, determining overarching themes).`;
      } else {
        promptDirectives = `MANDATORY COGNITIVE DIMENSION BREAKDOWN FOR ${n} QUESTIONS:
- ALL ${n} question(s) must be strictly of cognitive dimension "${focus}".`;
      }

      return {
        total: n,
        focus: focus,
        literal: litCount,
        inferential: infCount,
        critical: critCount,
        literalPct: litPct,
        inferentialPct: infPct,
        criticalPct: critPct,
        summaryText: summaryText,
        orderGuide: orderGuide,
        promptDirectives: promptDirectives,
        itemSchedule: itemSchedule
      };
    };

    window.updateAITimedBlueprintPreview = function() {
      const qCount = parseInt(document.getElementById('aiTimedQuestionCount')?.value, 10) || 5;
      const compFocus = document.getElementById('aiTimedCompetencyFocus')?.value || 'BALANCED';
      const blueprint = window.getAITimedCognitiveBlueprint(qCount, compFocus);
      const previewText = document.getElementById('aiTimedDistributionPreviewText');
      if (previewText) {
        previewText.textContent = blueprint.summaryText;
      }
    };

    // AI Question Generator: APPEND mode by default
    window.runAITimedAssessmentGeneration = async function() {
      const title = document.getElementById('aiTimedTitle')?.value.trim();
      const purpose = document.getElementById('aiTimedPurpose')?.value || 'Phil-IRI Pre-Test (Baseline Diagnostic)';
      const grade = document.getElementById('aiTimedGrade')?.value || 'Grades 7-10';
      const qCount = parseInt(document.getElementById('aiTimedQuestionCount')?.value, 10) || 5;
      const compFocus = document.getElementById('aiTimedCompetencyFocus')?.value || 'BALANCED';
      const passageText = document.getElementById('aiTimedPastedPassage')?.value.trim() || '';

      if (!title) {
        window.showToast("Please enter an Assessment Title.", "warning");
        document.getElementById('aiTimedTitle')?.focus();
        return;
      }

      if (!passageText || passageText.split(/\s+/).filter(Boolean).length < 20) {
        window.showToast("Please provide your reading passage (at least 20 words) for Gemini 3.5 Flash Lite to analyze.", "warning");
        document.getElementById('aiTimedPastedPassage')?.focus();
        return;
      }

      let apiKey = window.centralizedGeminiApiKey;
      if (!apiKey) {
        try {
          const cfgSnap = await getDoc(doc(db, "system_config", "gemini"));
          if (cfgSnap.exists() && cfgSnap.data().apiKey) {
            apiKey = cfgSnap.data().apiKey.trim();
            window.centralizedGeminiApiKey = apiKey;
            window.centralizedGeminiConfig = cfgSnap.data();
          }
        } catch (_) {}
      }

      if (!apiKey) {
        window.showToast("Cannot generate questions: Gemini API Key is not configured. Please set the key in System Settings (Super Admin only).", "warning");
        return;
      }

      const blueprint = window.getAITimedCognitiveBlueprint(qCount, compFocus);

      const btn = document.getElementById('btnRunAITimedGeneration');
      if (btn) {
        btn.disabled = true;
        btn.innerHTML = `<span class="animate-spin mr-1.5">&#9203;</span> Gemini 3.5 Flash Lite is formulating ${qCount} balanced items...`;
      }

      try {
        const prompt = `You are an expert reading comprehension assessment specialist for the Philippine Department of Education (DepEd).
Analyze this exact reading selection provided by the educator:
---
${passageText}
---

Generate exactly ${qCount} high-quality, authentic multiple-choice reading comprehension questions based STRICTLY and DIRECTLY on the passage above.
Assessment Purpose: ${purpose}
Target Grade: ${grade}
Cognitive Dimension Focus: ${compFocus}

${blueprint.promptDirectives}

DETAILED DEPED PHIL-IRI COGNITIVE DIMENSION GUIDELINES (DO NOT GENERATE TRIVIAL OR GENERIC ITEMS):
1. "LITERAL":
   - Ask about explicitly stated facts, dates, names, locations, numerical quantities, or concrete actions directly observable in the text.
   - The correct answer must be directly confirmable from the text.
2. "INFERENTIAL":
   - Require students to read between the lines and synthesize context clues.
   - Ask about unspoken motives, implied cause-and-effect relationships, character perspectives, deductive reasoning, or the meaning of words/phrases as used in the passage's specific context.
   - The correct answer cannot be found verbatim; it must be logically deduced from clues.
3. "CRITICAL":
   - Higher-Order Thinking Skills (HOTS) and evaluative comprehension.
   - Ask students to assess the author's primary purpose, tone, underlying assumptions, evaluate arguments or actions described, judge effectiveness/credibility, or synthesize the central theme and broader implications.
   - Distractors must represent plausible misinterpretations, not silly absurdities.

STRICT MANDATORY REQUIREMENTS:
1. Every question must be uniquely grounded in the passage text above. Never use generic, filler, or placeholder phrasing.
2. Provide exactly 4 plausible choices per question (A, B, C, D) all grounded in the context of the passage. Distractors must represent plausible misinterpretations, not absurd or silly filler.
3. Exactly 1 unambiguous correct answer, with "ans" as a 0-indexed integer (0, 1, 2, or 3).
4. "competency" must be strictly one of: "LITERAL", "INFERENTIAL", or "CRITICAL".
5. Item sequence must strictly follow the required blueprint order:
   ${blueprint.orderGuide}
6. "explanation" must be a concise sentence citing the exact textual evidence or deductive rationale from the passage.

Return ONLY a valid JSON array matching this exact schema:
[
  {
    "q": "Specific question about the passage?",
    "options": ["Choice A", "Choice B", "Choice C", "Choice D"],
    "ans": 0,
    "competency": "LITERAL",
    "explanation": "Evidence from text explaining the answer."
  }
]`;

        const endpoint = `https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:generateContent?key=${apiKey}`;
        const response = await fetch(endpoint, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            contents: [
              {
                parts: [{ text: prompt }]
              }
            ]
          })
        });

        if (!response.ok) {
          const errData = await response.json().catch(() => ({}));
          const errMsg = errData.error?.message || `HTTP ${response.status} ${response.statusText}`;
          throw new Error(`Gemini API Error: ${errMsg}`);
        }

        const data = await response.json();
        const jsonText = data.candidates?.[0]?.content?.parts?.[0]?.text || "";
        const startIndex = jsonText.indexOf("[");
        const endIndex = jsonText.lastIndexOf("]");
        if (startIndex === -1 || endIndex === -1) {
          throw new Error("Gemini returned a response that did not contain a valid JSON array.");
        }

        const parsed = JSON.parse(jsonText.substring(startIndex, endIndex + 1));
        if (!Array.isArray(parsed) || parsed.length === 0) {
          throw new Error("Gemini generated an empty question array.");
        }

        const expectedSchedule = blueprint.itemSchedule;

        const normalizeCompetency = (rawComp, idx) => {
          const s = String(rawComp || '').toUpperCase();
          if (s.includes('CRIT') || s.includes('EVAL') || s.includes('HOTS')) return 'CRITICAL';
          if (s.includes('INF') || s.includes('REASON') || s.includes('DEDUC') || s.includes('CAUSE')) return 'INFERENTIAL';
          if (s.includes('LIT') || s.includes('FACT') || s.includes('RECALL') || s.includes('DETAIL')) return 'LITERAL';
          // Fallback to scheduled blueprint dimension for this slot
          return expectedSchedule[idx] || 'LITERAL';
        };

        const newItems = parsed.map((item, idx) => {
          const comp = normalizeCompetency(item.competency, idx);
          return {
            q: item.q || `Reading Comprehension Question ${idx + 1}`,
            options: Array.isArray(item.options) && item.options.length === 4 
              ? item.options 
              : ["Option A", "Option B", "Option C", "Option D"],
            ans: typeof item.ans === 'number' && item.ans >= 0 && item.ans <= 3 ? item.ans : 0,
            competency: comp,
            explanation: item.explanation || "Text-based comprehension rationale."
          };
        });

        // Append new items to the pool (Append mode as selected by user)
        aiTimedGeneratedQuizPool = [...aiTimedGeneratedQuizPool, ...newItems];

        window.renderAITimedQuestions();
        window.showToast(`Gemini 3.5 Flash Lite formulated & added ${newItems.length} balanced questions to the pool!`, "success");
      } catch (err) {
        console.error("Gemini generation error:", err);
        window.showToast("AI Generation Failed: " + err.message, "error");
      } finally {
        if (btn) {
          btn.disabled = false;
          btn.innerHTML = `<svg class="w-4 h-4 inline mr-1" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="m12 3-1.912 5.813a2 2 0 0 1-1.275 1.275L3 12l5.813 1.912a2 2 0 0 1 1.275 1.275L12 21l1.912-5.813a2 2 0 0 1 1.275-1.275L21 12l-5.813-1.912a2 2 0 0 1-1.275-1.275L12 3Z"/></svg> <span>Generate Questions with Gemini 3.5 Flash Lite</span>`;
        }
      }
    };

    window.renderAITimedQuestions = function() {
      const container = document.getElementById('aiTimedQuestionsContainer');
      const badge = document.getElementById('aiTimedQuestionsCountBadge');
      const meters = document.getElementById('aiTimedCognitiveMeters');
      if (!container) return;

      if (badge) badge.textContent = `${aiTimedGeneratedQuizPool.length} Question${aiTimedGeneratedQuizPool.length === 1 ? '' : 's'}`;

      if (aiTimedGeneratedQuizPool.length === 0) {
        container.innerHTML = `
          <div class="p-8 text-center text-slate-400 font-medium bg-slate-50 rounded-xl border border-dashed border-slate-200">
            Provide your reading passage above and click <span class="font-bold text-[#5341CD]">"Generate Questions with Gemini 3.5 Flash Lite"</span>.<br>
            Or click <span class="font-bold text-slate-700">"Add Custom Item"</span> to author items manually.
          </div>`;
        if (meters) meters.classList.add('hidden');
        return;
      }

      let litCount = 0, infCount = 0, critCount = 0;
      aiTimedGeneratedQuizPool.forEach(q => {
        const c = (q.competency || '').toUpperCase();
        if (c.includes('LIT')) litCount++;
        else if (c.includes('INF')) infCount++;
        else if (c.includes('CRIT')) critCount++;
      });

      const totalPool = aiTimedGeneratedQuizPool.length;
      const litPct = totalPool > 0 ? Math.round((litCount / totalPool) * 100) : 0;
      const infPct = totalPool > 0 ? Math.round((infCount / totalPool) * 100) : 0;
      const critPct = totalPool > 0 ? Math.round((critCount / totalPool) * 100) : 0;

      if (meters) {
        meters.classList.remove('hidden');
        meters.innerHTML = `
          <span class="px-2.5 py-0.5 rounded-full bg-emerald-50 text-emerald-700 font-bold border border-emerald-200 flex items-center gap-1">
            <span class="w-1.5 h-1.5 rounded-full bg-emerald-500"></span>Literal: ${litCount} (${litPct}%)
          </span>
          <span class="px-2.5 py-0.5 rounded-full bg-amber-50 text-amber-700 font-bold border border-amber-200 flex items-center gap-1">
            <span class="w-1.5 h-1.5 rounded-full bg-amber-500"></span>Inferential: ${infCount} (${infPct}%)
          </span>
          <span class="px-2.5 py-0.5 rounded-full bg-indigo-50 text-indigo-700 font-bold border border-indigo-200 flex items-center gap-1">
            <span class="w-1.5 h-1.5 rounded-full bg-indigo-500"></span>Critical: ${critCount} (${critPct}%)
          </span>
        `;
      }

      const getCompBadgeStyle = (comp) => {
        const c = String(comp || '').toUpperCase();
        if (c.includes('CRIT')) return 'border-indigo-300 bg-indigo-50/80 text-indigo-800';
        if (c.includes('INF')) return 'border-amber-300 bg-amber-50/80 text-amber-800';
        return 'border-emerald-300 bg-emerald-50/80 text-emerald-800';
      };

      container.innerHTML = aiTimedGeneratedQuizPool.map((qObj, qIdx) => `
        <div class="p-3.5 rounded-xl border border-slate-200 bg-slate-50/70 space-y-2.5">
          <div class="flex items-center justify-between">
            <div class="flex items-center gap-2">
              <span class="w-6 h-6 rounded-full bg-[#5341CD] text-white flex items-center justify-center font-bold text-xs shrink-0">${qIdx + 1}</span>
              <select onchange="aiTimedGeneratedQuizPool[${qIdx}].competency = this.value; renderAITimedQuestions();" 
                class="px-2.5 py-1 rounded-lg text-[10px] font-bold border ${getCompBadgeStyle(qObj.competency)} focus:outline-none focus:ring-2 focus:ring-[#5341CD]/20">
                <option value="LITERAL" ${qObj.competency === 'LITERAL' ? 'selected' : ''}>LITERAL (Factual Recall)</option>
                <option value="INFERENTIAL" ${qObj.competency === 'INFERENTIAL' ? 'selected' : ''}>INFERENTIAL (Reasoning &amp; Subtext)</option>
                <option value="CRITICAL" ${qObj.competency === 'CRITICAL' ? 'selected' : ''}>CRITICAL (HOTS &amp; Evaluative)</option>
              </select>
            </div>
            <div class="flex items-center gap-1">
              <button type="button" onclick="moveAITimedQuestion(${qIdx}, -1)" ${qIdx === 0 ? 'disabled' : ''} class="p-1 text-slate-400 hover:text-slate-700 disabled:opacity-30" title="Move Up">&uarr;</button>
              <button type="button" onclick="moveAITimedQuestion(${qIdx}, 1)" ${qIdx === aiTimedGeneratedQuizPool.length - 1 ? 'disabled' : ''} class="p-1 text-slate-400 hover:text-slate-700 disabled:opacity-30" title="Move Down">&darr;</button>
              <button type="button" onclick="duplicateAITimedQuestion(${qIdx})" class="p-1 text-slate-400 hover:text-slate-700" title="Duplicate Item">
                <svg class="w-3.5 h-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect width="14" height="14" x="8" y="8" rx="2" ry="2"/><path d="M4 16c-1.1 0-2-.9-2-2V4c0-1.1.9-2 2-2h10c1.1 0 2 .9 2 2"/></svg>
              </button>
              <button type="button" onclick="deleteAITimedQuestion(${qIdx})" class="p-1 text-rose-500 hover:text-rose-700" title="Delete Question">&times;</button>
            </div>
          </div>
          <div>
            <label class="block text-[10px] font-bold text-slate-500 mb-0.5 uppercase tracking-wider">Question Stem:</label>
            <input type="text" value="${(qObj.q || '').replace(/"/g, '&quot;')}" oninput="aiTimedGeneratedQuizPool[${qIdx}].q = this.value" 
              placeholder="Question text based on passage..." class="w-full px-2.5 py-1.5 bg-white border border-slate-300 rounded-lg text-xs font-semibold text-slate-800 focus:outline-none focus:border-[#5341CD]">
          </div>
          <div class="space-y-1.5 pl-2">
            <label class="block text-[10px] font-bold text-slate-500 uppercase tracking-wider">Options (Select radio for correct answer):</label>
            ${(qObj.options || []).map((opt, optIdx) => `
              <div class="flex items-center gap-2">
                <input type="radio" name="aiTimedCorrectChoice_${qIdx}" ${qObj.ans === optIdx ? 'checked' : ''} onchange="aiTimedGeneratedQuizPool[${qIdx}].ans = ${optIdx}; renderAITimedQuestions();" class="text-[#5341CD] focus:ring-[#5341CD]">
                <input type="text" value="${(opt || '').replace(/"/g, '&quot;')}" oninput="aiTimedGeneratedQuizPool[${qIdx}].options[${optIdx}] = this.value" 
                  class="flex-1 px-2.5 py-1 bg-white border border-slate-200 rounded-lg text-xs ${qObj.ans === optIdx ? 'font-bold border-emerald-400 bg-emerald-50/50 text-emerald-950' : 'text-slate-700'}">
              </div>
            `).join('')}
          </div>
          <div>
            <label class="block text-[10px] font-bold text-slate-500 mb-0.5 uppercase tracking-wider">Text-Based Explanation:</label>
            <input type="text" value="${(qObj.explanation || '').replace(/"/g, '&quot;')}" oninput="aiTimedGeneratedQuizPool[${qIdx}].explanation = this.value" 
              placeholder="Pedagogical rationale citing passage evidence" class="w-full px-2.5 py-1 bg-white border border-slate-200 rounded-lg text-[11px] text-slate-600 italic">
          </div>
        </div>
      `).join('');
    };

    window.addManualAITimedQuestion = function() {
      const nextComp = aiTimedGeneratedQuizPool.length % 3 === 0 ? "LITERAL" : (aiTimedGeneratedQuizPool.length % 3 === 1 ? "INFERENTIAL" : "CRITICAL");
      aiTimedGeneratedQuizPool.push({
        q: "New Passage-Based Comprehension Question Item:",
        options: [
          "Option A (Correct Answer)",
          "Option B",
          "Option C",
          "Option D"
        ],
        ans: 0,
        competency: nextComp,
        explanation: "Evidence directly from the reading passage."
      });
      window.renderAITimedQuestions();
    };

    window.duplicateAITimedQuestion = function(idx) {
      if (aiTimedGeneratedQuizPool[idx]) {
        const item = JSON.parse(JSON.stringify(aiTimedGeneratedQuizPool[idx]));
        item.q += " (Copy)";
        aiTimedGeneratedQuizPool.splice(idx + 1, 0, item);
        window.renderAITimedQuestions();
      }
    };

    window.moveAITimedQuestion = function(idx, direction) {
      const newIdx = idx + direction;
      if (newIdx < 0 || newIdx >= aiTimedGeneratedQuizPool.length) return;
      const temp = aiTimedGeneratedQuizPool[idx];
      aiTimedGeneratedQuizPool[idx] = aiTimedGeneratedQuizPool[newIdx];
      aiTimedGeneratedQuizPool[newIdx] = temp;
      window.renderAITimedQuestions();
    };

    window.deleteAITimedQuestion = function(index) {
      aiTimedGeneratedQuizPool.splice(index, 1);
      window.renderAITimedQuestions();
    };

    // Save as Draft
    window.saveStandardizedAssessmentDraft = async function() {
      await window.saveStandardizedAssessment(false);
    };

    // Deploy Division-Wide
    window.deployAITimedAssessment = async function() {
      await window.saveStandardizedAssessment(true);
    };

    // Unified Save Function (Draft vs Active)
    window.saveStandardizedAssessment = async function(isDeploying = false) {
      if (isSchoolAdmin || (!isSuperAdmin && currentAdminUser && currentAdminUser.role !== 'super_admin')) {
        window.showToast("Standardized assessment authoring and publishing are reserved exclusively for SDO Super Administrators.", "error");
        return;
      }
      const title = document.getElementById('aiTimedTitle')?.value.trim();
      const purpose = document.getElementById('aiTimedPurpose')?.value || 'Phil-IRI Pre-Test (Baseline Diagnostic)';
      const grade = document.getElementById('aiTimedGrade')?.value || 'Grades 7-10';
      const district = document.getElementById('aiTimedDistrict')?.value || 'ALL';
      const timerMinutes = parseInt(document.getElementById('aiTimedTimer')?.value, 10) || 15;
      const dueDate = document.getElementById('aiTimedDueDate')?.value;
      const passage = document.getElementById('aiTimedPastedPassage')?.value.trim();

      if (!title) {
        window.showToast("Please enter an assessment title.", "warning");
        document.getElementById('aiTimedTitle')?.focus();
        return;
      }

      if (isDeploying) {
        if (!passage || passage.split(/\s+/).filter(Boolean).length < 20) {
          window.showToast("Please provide the standardized reading passage before deploying.", "warning");
          document.getElementById('aiTimedPastedPassage')?.focus();
          return;
        }
        if (aiTimedGeneratedQuizPool.length === 0) {
          window.showToast("Please formulate or generate at least 1 question item before deploying.", "warning");
          return;
        }
      }

      const btnDeploy = document.getElementById('btnDeployAITimedAssessment');
      const btnDraft = document.getElementById('btnSaveStandardizedDraft');
      if (btnDeploy) btnDeploy.disabled = true;
      if (btnDraft) btnDraft.disabled = true;

      try {
        const assessmentId = window.activeEditingAssessmentId || `timed_assessment_${Date.now()}`;
        const dueDateTimestamp = dueDate ? new Date(dueDate).getTime() : Date.now() + (14 * 24 * 60 * 60 * 1000);
        const targetStatus = isDeploying ? "active" : "draft";

        const newRecord = {
          id: assessmentId,
          title: title,
          subjectName: "Phil-IRI Reading Diagnostic & Summative",
          className: "All Sections (Division Standard)",
          grade: grade,
          dueDate: dueDateTimestamp,
          timerMinutes: timerMinutes,
          durationMinutes: timerMinutes,
          isDivisionUniform: true,
          districtScope: district,
          passage: passage || "",
          assessmentPurpose: purpose,
          quiz_pool_json: JSON.stringify(aiTimedGeneratedQuizPool),
          questionCount: aiTimedGeneratedQuizPool.length,
          status: targetStatus,
          updatedAt: Date.now(),
          deployedBy: currentAdminUser?.email || "super_admin@gabai.internal",
          deployedByName: "Schools Division Office of Valenzuela City",
          sourceType: "division_uniform"
        };

        if (!window.activeEditingAssessmentId) {
          newRecord.createdAt = Date.now();
        }

        // Zero-latency optimistic state unshift / update
        const existingIdx = cachedDivisionTimedAssessments.findIndex(a => a.id === assessmentId);
        if (existingIdx >= 0) {
          cachedDivisionTimedAssessments[existingIdx] = { ...cachedDivisionTimedAssessments[existingIdx], ...newRecord };
        } else {
          cachedDivisionTimedAssessments.unshift(newRecord);
        }

        const wkIdx = cachedWeeklyAssessments.findIndex(a => a.id === assessmentId);
        if (wkIdx >= 0) {
          cachedWeeklyAssessments[wkIdx] = { ...cachedWeeklyAssessments[wkIdx], ...newRecord };
        }

        window.closeAITimedAssessmentGeneratorModal();
        window.updateTimedAssessmentMetrics();
        window.renderTimedAssessmentsTable();

        const toastMsg = isDeploying 
          ? "Phil-IRI Standardized Assessment deployed division-wide successfully!" 
          : "Standardized Reading Assessment saved as draft!";
        window.showToast(toastMsg, "success");

        // Background Cloud Firestore persist
        try {
          await setDoc(doc(db, "division_timed_assessments", assessmentId), newRecord, { merge: true });
        } catch (netErr) {
          console.warn("Firestore sync background notification:", netErr);
        }
      } catch (err) {
        console.error("Error saving assessment:", err);
        window.showToast("Failed to save assessment: " + err.message, "error");
      } finally {
        if (btnDeploy) btnDeploy.disabled = false;
        if (btnDraft) btnDraft.disabled = false;
      }
    };

    // 1-Click Publishing for Drafts
    window.deployDraftAssessment = async function(assessmentId) {
      if (!isSuperAdmin) {
        window.showToast("Only Super Admins can deploy standardized assessments.", "error");
        return;
      }
      const all = getUnifiedTimedTests();
      const target = all.find(a => a.id === assessmentId);
      if (!target) {
        window.showToast("Draft assessment not found.", "error");
        return;
      }

      if (!target.passage || target.passage.trim().length === 0) {
        window.showToast("Cannot deploy: this draft has no passage. Edit in Studio first.", "warning");
        window.openStandardizedTestStudio(assessmentId);
        return;
      }

      let qCount = target.questionCount || 0;
      if (!qCount && target.quiz_pool_json) {
        try { qCount = JSON.parse(target.quiz_pool_json).length; } catch (_) {}
      }
      if (qCount === 0) {
        window.showToast("Cannot deploy: this draft has 0 questions. Edit in Studio first.", "warning");
        window.openStandardizedTestStudio(assessmentId);
        return;
      }

      try {
        await updateDoc(doc(db, "division_timed_assessments", assessmentId), {
          status: "active",
          updatedAt: Date.now()
        });

        const divIdx = cachedDivisionTimedAssessments.findIndex(a => a.id === assessmentId);
        if (divIdx >= 0) cachedDivisionTimedAssessments[divIdx].status = "active";
        const wkIdx = cachedWeeklyAssessments.findIndex(a => a.id === assessmentId);
        if (wkIdx >= 0) cachedWeeklyAssessments[wkIdx].status = "active";

        window.updateTimedAssessmentMetrics();
        window.renderTimedAssessmentsTable();
        window.showToast(`"${target.title}" deployed division-wide successfully!`, "success");
      } catch (err) {
        console.error("Error deploying draft:", err);
        window.showToast("Failed to deploy draft: " + err.message, "error");
      }
    };

    // Renders the Standardized Reading Tests Table with Draft Support
    window.renderTimedAssessmentsTable = function() {
      const tbody = document.getElementById('timedAssessmentsTableBody');
      if (!tbody) return;

      const thSubs = document.getElementById('thTimedAssessmentsSubmissions');
      if (thSubs) thSubs.textContent = isSchoolAdmin ? "School Submissions & MPS" : "Division Submissions & MPS";

      let list = getUnifiedTimedTests();

      // Scoping for School Admin: only show assessments targeting their school/district or division-wide
      if (isSchoolAdmin && currentAdminUser?.schoolId) {
        const adminSchoolId = String(currentAdminUser.schoolId);
        const adminDistrict = getSchoolDistrict(adminSchoolId);
        list = list.filter(a => {
          if (a.schoolId && a.schoolId !== 'ALL' && String(a.schoolId) !== adminSchoolId) return false;
          if (a.districtScope && a.districtScope !== 'ALL' && a.districtScope !== adminDistrict) return false;
          return true;
        });
      }

      // Search filter
      const search = (document.getElementById('timedAssessmentSearchInput')?.value || '').toLowerCase().trim();
      if (search) {
        list = list.filter(a => {
          const textBlob = `${a.title || ''} ${a.assessmentPurpose || ''} ${a.districtScope || ''} ${a.passage || ''}`;
          return matchSmartSearch(textBlob, search);
        });
      }

      // District filter
      const districtFilter = document.getElementById('timedAssessmentDistrictFilter')?.value || 'ALL';
      if (districtFilter !== 'ALL') {
        list = list.filter(a => (a.districtScope || 'ALL').includes(districtFilter) || (a.districtScope || '') === 'ALL');
      }

      // School filter
      if (selectedJurisdiction !== 'ALL') {
        list = list.filter(a => !a.schoolId || a.schoolId === 'ALL' || String(a.schoolId) === selectedJurisdiction);
      }

      // Grade Level filter
      const gradeFilter = document.getElementById('timedAssessmentGradeFilter')?.value || 'ALL';
      if (gradeFilter !== 'ALL') {
        list = list.filter(a => (a.grade || '').includes(gradeFilter) || (a.grade || '') === 'Grades 7-10');
      }

      // Purpose filter
      const purposeFilter = document.getElementById('timedAssessmentPurposeFilter')?.value || 'ALL';
      if (purposeFilter !== 'ALL') {
        list = list.filter(a => (a.assessmentPurpose || '').toLowerCase().includes(purposeFilter.toLowerCase()));
      }

      // Status filter (Active vs Drafts vs Closed)
      const statusFilter = document.getElementById('timedAssessmentStatusFilter')?.value || 'ALL';
      const now = Date.now();
      if (statusFilter === 'active') {
        list = list.filter(a => a.status === 'active' && (!a.dueDate || a.dueDate > now));
      } else if (statusFilter === 'draft') {
        list = list.filter(a => a.status === 'draft');
      } else if (statusFilter === 'closed') {
        list = list.filter(a => a.status === 'closed' || (a.status !== 'draft' && a.dueDate && a.dueDate <= now));
      }

      const countBadge = document.getElementById('timedAssessmentsCountBadge');
      if (countBadge) countBadge.textContent = `${list.length} Assessment${list.length === 1 ? '' : 's'}`;

      if (list.length === 0) {
        const isFiltered = !!(search || (districtFilter !== 'ALL' && !isSchoolAdmin) || (selectedJurisdiction !== 'ALL' && !isSchoolAdmin) || gradeFilter !== 'ALL' || purposeFilter !== 'ALL' || statusFilter !== 'ALL');

        let emptyTitle = "No Standardized Reading Tests Found";
        let emptyMsg = `No standardized timed assessments have been published or drafted yet. Click <span class="font-bold text-[#5341CD]">"Deploy Standardized Timed Assessment"</span> to author your first test.`;

        if (isFiltered) {
          emptyTitle = "No Matching Standardized Tests";
          emptyMsg = "No standardized reading tests matched your filter criteria. Try adjusting your search query, grade level, or assessment status.";
        } else if (isSchoolAdmin) {
          const schoolName = (typeof getSchoolName === 'function' && currentAdminUser?.schoolId ? getSchoolName(currentAdminUser.schoolId) : '') || 'your school';
          emptyTitle = "No Standardized Assessments Scheduled";
          emptyMsg = `No division standardized timed assessments are currently scheduled for ${escapeHtml(schoolName)} at this time. When uniform Phil-IRI tests or diagnostic assessments are deployed division-wide by the Schools Division Office (SDO), they will automatically appear here for monitoring and evaluation.`;
        }

        tbody.innerHTML = `
          <tr>
            <td colspan="7" class="p-10 text-center text-slate-400">
              <div class="max-w-md mx-auto space-y-2">
                <div class="w-12 h-12 rounded-2xl bg-purple-50 text-[#5341CD] flex items-center justify-center mx-auto mb-2">
                  <svg class="w-6 h-6" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>
                </div>
                <div class="font-bold text-slate-700 text-sm">${emptyTitle}</div>
                <p class="text-xs text-slate-400 leading-relaxed">${emptyMsg}</p>
              </div>
            </td>
          </tr>`;
        return;
      }

      tbody.innerHTML = list.map(a => {
        let subs = [...(cachedTimedSubmissions || []), ...(cachedSubmissions || [])].filter(s => s.assessmentId === a.id);
        if (isSchoolAdmin && currentAdminUser?.schoolId) {
          const targetSchool = String(currentAdminUser.schoolId);
          subs = subs.filter(s => getSubmissionSchoolId(s) === targetSchool);
        } else if (selectedJurisdiction !== 'ALL') {
          subs = subs.filter(s => getSubmissionSchoolId(s) === selectedJurisdiction);
        }
        const subCount = subs.length;
        const avgPct = calculateAverageScore(subs, a.questionCount || 5);
        const isDraft = a.status === 'draft';
        const isExpired = a.dueDate && a.dueDate <= now;
        const isActive = a.status === 'active' && !isExpired;

        let purposeBadge = '<span class="px-2 py-0.5 rounded-full text-[10px] font-bold bg-purple-100 text-[#5341CD]">Summative</span>';
        const purpLower = (a.assessmentPurpose || '').toLowerCase();
        if (purpLower.includes('screen') || purpLower.includes('gst')) {
          purposeBadge = '<span class="px-2 py-0.5 rounded-full text-[10px] font-bold bg-indigo-100 text-indigo-800">Phil-IRI GST</span>';
        } else if (purpLower.includes('diag') || purpLower.includes('pre-test')) {
          purposeBadge = '<span class="px-2 py-0.5 rounded-full text-[10px] font-bold bg-blue-100 text-blue-700">Diagnostic Pre-Test</span>';
        } else if (purpLower.includes('interv') || purpLower.includes('remedial')) {
          purposeBadge = '<span class="px-2 py-0.5 rounded-full text-[10px] font-bold bg-amber-100 text-amber-800">Targeted Remediation</span>';
        }

        const safeTitle = (a.title || 'Division Timed Assessment').replace(/'/g, "\\'");
        const encodedPassage = encodeURIComponent(a.passage || '');

        let statusPillHtml = '';
        if (isDraft) {
          statusPillHtml = `
            <span class="px-2.5 py-1 rounded-full bg-amber-50 text-amber-700 border border-amber-200 text-[11px] font-bold flex items-center gap-1 w-fit">
              <span class="w-1.5 h-1.5 rounded-full bg-amber-500"></span> Draft
            </span>`;
        } else if (isActive) {
          statusPillHtml = `
            <span class="px-2.5 py-1 rounded-full bg-emerald-50 text-emerald-700 border border-emerald-200 text-[11px] font-bold flex items-center gap-1 w-fit">
              <span class="w-1.5 h-1.5 rounded-full bg-emerald-500 animate-pulse"></span> Active
            </span>`;
        } else {
          statusPillHtml = `
            <span class="px-2.5 py-1 rounded-full bg-slate-100 text-slate-600 border border-slate-200 text-[11px] font-bold w-fit">
              Closed
            </span>`;
        }

        const scopeLabel = isSchoolAdmin ? getSchoolName(currentAdminUser?.schoolId) : 'All Junior High Schools';

        return `
          <tr class="hover:bg-slate-50/80 transition-colors">
            <td class="p-3.5">
              <div class="font-bold text-slate-900 text-sm">${escapeHtml(a.title || 'Standardized Assessment')}</div>
              <div class="flex items-center gap-1.5 mt-1">
                ${purposeBadge}
                <span class="px-2 py-0.5 rounded-full bg-slate-100 text-slate-600 text-[10px] font-bold">${a.questionCount || 5} Questions</span>
                <span class="px-2 py-0.5 rounded-full bg-purple-50 text-[#5341CD] border border-purple-200 text-[10px] font-bold">Phil-IRI Form</span>
              </div>
            </td>
            <td class="p-3.5">
              <div class="font-bold text-slate-800">${escapeHtml(a.districtScope || 'Division-Wide')}</div>
              <div class="text-[11px] text-slate-500">${escapeHtml(a.grade || 'Grades 7-10')} &bull; ${escapeHtml(scopeLabel)}</div>
            </td>
            <td class="p-3.5">
              <span class="px-2.5 py-1 rounded-full bg-purple-100 text-[#5341CD] text-xs font-bold font-mono flex items-center gap-1 w-fit shadow-2xs">
                ${uiIcon('clock', 'w-3 h-3')} <span>${a.timerMinutes || 15} min limit</span>
              </span>
            </td>
            <td class="p-3.5">
              ${a.passage ? `
                <button onclick="openPassageViewerModal('${safeTitle}', '${encodedPassage}')" class="px-2.5 py-1 bg-purple-50 hover:bg-purple-100 text-[#5341CD] border border-purple-200 rounded-lg text-xs font-bold flex items-center gap-1 shadow-2xs transition">
                  ${uiIcon('file', 'w-3 h-3')} <span>View Passage</span>
                </button>
              ` : `
                <span class="text-slate-400 text-xs italic">No passage attached</span>
              `}
            </td>
            <td class="p-3.5">
              <div class="flex items-center gap-2">
                <button onclick="openAssessmentSubmissionsModal('${a.id}')" class="font-bold text-slate-800 hover:text-indigo-600 underline">
                  ${subCount} Turned In
                </button>
                ${subCount > 0 ? `<span class="px-2 py-0.5 rounded-full text-[10px] font-bold ${avgPct >= 75 ? 'bg-emerald-100 text-emerald-800' : 'bg-amber-100 text-amber-800'}">${avgPct}% MPS</span>` : ''}
              </div>
            </td>
            <td class="p-3.5">
              ${statusPillHtml}
            </td>
            <td class="p-3.5 text-right">
              <div class="flex items-center justify-end gap-1.5">
                ${isSchoolAdmin ? `
                  <button onclick="openAssessmentInspectorModal('${a.id}')" class="px-2.5 py-1 bg-emerald-50 hover:bg-emerald-100 text-emerald-800 border border-emerald-200 rounded-lg text-xs font-bold shadow-xs flex items-center gap-1.5" title="Inspect Test Questions (Read-Only)">
                    ${uiIcon('clipboard', 'w-3.5 h-3.5')} <span>Questions</span>
                  </button>
                  <button onclick="openAssessmentSubmissionsModal('${a.id}')" class="px-2.5 py-1 bg-indigo-50 hover:bg-indigo-100 text-indigo-700 border border-indigo-200 rounded-lg text-xs font-semibold shadow-xs flex items-center gap-1.5" title="School Submissions &amp; Results">
                    ${uiIcon('chart', 'w-3.5 h-3.5')} <span>Submissions</span>
                  </button>
                  <span class="px-2 py-1 bg-slate-100 text-slate-500 rounded text-[11px] font-semibold border border-slate-200 select-none flex items-center gap-1" title="SDO Valenzuela Standardized Assessment">
                    ${uiIcon('shield', 'w-3 h-3 text-[#5341CD]')} <span>SDO Mandate</span>
                  </span>
                ` : `
                  <button onclick="openStandardizedTestStudio('${a.id}')" class="px-2.5 py-1 bg-purple-50 hover:bg-purple-100 text-[#5341CD] border border-purple-200 rounded-lg text-xs font-bold shadow-xs flex items-center gap-1" title="Open Studio &amp; Edit Questions">
                    ${uiIcon('pencil', 'w-3 h-3')} <span>Edit &amp; Inspect</span>
                  </button>
                  ${isDraft ? `
                    <button onclick="deployDraftAssessment('${a.id}')" class="px-2.5 py-1 bg-emerald-600 hover:bg-emerald-700 text-white rounded-lg text-xs font-bold shadow-xs flex items-center gap-1 transition" title="1-Click Deploy Division-Wide">
                      ${uiIcon('upload', 'w-3 h-3')} <span>Deploy</span>
                    </button>
                  ` : `
                    <button onclick="openAssessmentSubmissionsModal('${a.id}')" class="px-2.5 py-1 bg-indigo-50 hover:bg-indigo-100 text-indigo-700 border border-indigo-200 rounded-lg text-xs font-semibold shadow-xs flex items-center gap-1" title="Submissions &amp; Results">
                      ${uiIcon('chart', 'w-3 h-3')} <span>Submissions</span>
                    </button>
                    <button onclick="toggleDivisionAssessmentStatus('${a.id}')" class="px-2 py-1 bg-slate-100 hover:bg-slate-200 text-slate-700 rounded-lg text-xs font-semibold" title="${isActive ? 'Close Test' : 'Reactivate Test'}">
                      ${isActive ? 'Close' : 'Reactivate'}
                    </button>
                  `}
                  <button onclick="deleteDivisionAssessment('${a.id}', '${safeTitle}')" class="px-2 py-1 bg-rose-50 hover:bg-rose-100 text-rose-700 border border-rose-200 rounded-lg text-xs font-semibold" title="Delete Assessment">
                    ${uiIcon('trash', 'w-3 h-3')}
                  </button>
                `}
              </div>
            </td>
          </tr>
        `;
      }).join('');
    };

    window.toggleDivisionAssessmentStatus = async function(id) {
      if (!isSuperAdmin) return;
      const target = cachedDivisionTimedAssessments.find(a => a.id === id) || cachedWeeklyAssessments.find(a => a.id === id);
      if (!target) return;
      const newStatus = target.status === 'active' ? 'closed' : 'active';
      try {
        const colName = cachedDivisionTimedAssessments.some(a => a.id === id) ? "division_timed_assessments" : "weekly_assessments";
        await updateDoc(doc(db, colName, id), { status: newStatus });
        target.status = newStatus;
        window.showToast(`Assessment ${newStatus === 'active' ? 'reactivated' : 'closed'} successfully.`, "success");
        window.updateTimedAssessmentMetrics();
        window.renderTimedAssessmentsTable();
      } catch (e) {
        window.showToast("Failed to update status: " + e.message, "error");
      }
    };

    window.deleteDivisionAssessment = async function(id, title) {
      if (!isSuperAdmin) return;
      
      const confirmed = await showConfirmModal({
        title: "Archive & Remove Assessment?",
        message: `Remove "${title || 'this division assessment'}"? It will be safely moved to the Recycle Bin, where it can be restored at any time within 30 days.`,
        badgeIcon: "🗑️",
        badgeBg: "bg-rose-50 text-rose-600 border-rose-200",
        confirmText: "Move to Recycle Bin",
        isDestructive: true
      });
      if (!confirmed) return;

      try {
        const colName = cachedDivisionTimedAssessments.some(a => a.id === id) ? "division_timed_assessments" : "weekly_assessments";
        const docRef = doc(db, colName, id);
        const docSnap = await getDoc(docRef);
        const assessmentData = docSnap.exists() ? docSnap.data() : (cachedDivisionTimedAssessments.find(a => a.id === id) || {});

        const archiveId = `assessment_${id}_${Date.now()}`;
        await setDoc(doc(db, "system_archives", archiveId), {
          archiveId: archiveId,
          originalDocId: id,
          collectionSource: colName,
          archivedAt: Date.now(),
          archivedBy: currentAdminUser?.email || "super_admin@gabai.internal",
          recordType: "division_assessment",
          data: assessmentData
        });

        await deleteDoc(docRef);

        cachedDivisionTimedAssessments = cachedDivisionTimedAssessments.filter(a => a.id !== id);
        cachedWeeklyAssessments = cachedWeeklyAssessments.filter(a => a.id !== id);

        window.showToast("Standardized assessment archived to Recycle Bin.", "info");
        window.updateTimedAssessmentMetrics();
        window.renderTimedAssessmentsTable();
      } catch (e) {
        console.error("Error archiving assessment:", e);
        window.showToast("Failed to archive assessment: " + e.message, "error");
      }
    };

    window.exportDivisionTimedTestsCSV = function() {
      let tests = getUnifiedTimedTests();
      const adminSchoolId = (isSchoolAdmin && currentAdminUser?.schoolId) ? String(currentAdminUser.schoolId) : null;
      const adminDistrict = adminSchoolId ? getSchoolDistrict(adminSchoolId) : null;

      if (adminSchoolId) {
        tests = tests.filter(a => {
          if (a.schoolId && a.schoolId !== 'ALL' && String(a.schoolId) !== adminSchoolId) return false;
          if (a.districtScope && a.districtScope !== 'ALL' && a.districtScope !== adminDistrict) return false;
          return true;
        });
      }

      const rows = [
        ["Assessment ID", "Title", "Purpose", "District Scope", "Grade Level", "Timer (Mins)", "Questions", "Total Submissions", "Average Score %", "Independent Readers", "Instructional Readers", "Frustration Readers", "Status", "Created At"]
      ];
      tests.forEach(a => {
        let subs = [...(cachedTimedSubmissions || []), ...(cachedSubmissions || [])].filter(s => s.assessmentId === a.id);
        if (adminSchoolId) {
          subs = subs.filter(s => getSubmissionSchoolId(s) === adminSchoolId);
        } else if (selectedJurisdiction !== 'ALL') {
          subs = subs.filter(s => getSubmissionSchoolId(s) === selectedJurisdiction);
        }
        const indCount = subs.filter(s => (s.philIriLevel && s.philIriLevel.toLowerCase().includes('independent')) || (s.percentage !== undefined ? s.percentage >= 80 : false)).length;
        const instCount = subs.filter(s => (s.philIriLevel && s.philIriLevel.toLowerCase().includes('instructional')) || (s.percentage !== undefined ? (s.percentage >= 59 && s.percentage < 80) : false)).length;
        const frustCount = subs.filter(s => (s.philIriLevel && s.philIriLevel.toLowerCase().includes('frustration')) || (s.percentage !== undefined ? s.percentage < 59 : false)).length;
        const avgScore = calculateAverageScore(subs, a.questionCount || 5);
        rows.push([
          a.id,
          `"${(a.title || '').replace(/"/g, '""')}"`,
          a.assessmentPurpose || 'summative',
          a.districtScope || 'ALL',
          a.grade || 'Grades 7-10',
          a.timerMinutes || 15,
          a.questionCount || 5,
          subs.length,
          avgScore,
          indCount,
          instCount,
          frustCount,
          a.status || 'active',
          a.createdAt ? new Date(a.createdAt).toISOString() : ''
        ]);
      });
      const filePrefix = isSchoolAdmin ? `GabAI_School_${currentAdminUser?.schoolId}_Standardized_Tests_` : `GabAI_Standardized_Reading_Tests_`;
      downloadCSV(`${filePrefix}${new Date().toISOString().slice(0, 10)}.csv`, rows);
    };

    // Submissions Modal Filtering & CSV Export
    window.filterAssessmentSubmissionsTable = function() {
      const search = (document.getElementById('subModalSearchInput')?.value || '').toLowerCase().trim();
      const school = document.getElementById('subModalSchoolFilter')?.value || 'ALL';
      const tier = document.getElementById('subModalTierFilter')?.value || 'ALL';
      const rows = document.querySelectorAll('#assessmentSubmissionsTableBody tr');

      rows.forEach(r => {
        const text = r.textContent.toLowerCase();
        const matchesSearch = !search || text.includes(search);
        const matchesSchool = school === 'ALL' || text.includes(school.toLowerCase());
        const matchesTier = tier === 'ALL' || text.includes(tier.toLowerCase());
        r.style.display = (matchesSearch && matchesSchool && matchesTier) ? '' : 'none';
      });
    };

    window.exportCurrentSubmissionsModalCSV = function() {
      const rows = [["Student Name", "Gender", "LRN / Email", "Score", "Accuracy", "Reading Tier", "Submitted At"]];
      const tableRows = document.querySelectorAll('#assessmentSubmissionsTableBody tr');
      tableRows.forEach(r => {
        if (r.style.display !== 'none') {
          const cells = Array.from(r.querySelectorAll('td')).map(td => `"${td.textContent.trim().replace(/"/g, '""')}"`);
          if (cells.length >= 5) rows.push(cells);
        }
      });
      downloadCSV(`Assessment_Submissions_${Date.now()}.csv`, rows);
    };



window.getUnifiedTimedTests = getUnifiedTimedTests;
window.getSubmissionSchoolId = getSubmissionSchoolId;
