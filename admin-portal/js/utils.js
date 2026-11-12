    function matchSmartSearch(textBlob, query) {
      if (!query) return true;
      if (!textBlob) return false;
      const tokens = query.toLowerCase().split(/\s+/).filter(Boolean);
      const target = String(textBlob).toLowerCase();
      return tokens.every(tok => target.includes(tok));
    }


    // =========================================================================
    // CENTRALIZED ANALYTICS ENGINE & DEBOUNCED RENDER COORDINATOR
    // =========================================================================
    function getSubmissionPercentage(sub, defaultTotal = 10) {
      if (!sub) return 0;
      if (sub.percentage !== undefined && sub.percentage !== null && !isNaN(sub.percentage)) {
        return Number(sub.percentage);
      }
      const score = Number(sub.score) || 0;
      const total = Number(sub.totalQuestions) || defaultTotal;
      return total > 0 ? (score * 100) / total : 0;
    }

    function calculateAverageScore(submissions = [], defaultTotal = 10) {
      if (!submissions || submissions.length === 0) return 0;
      const sum = submissions.reduce((acc, curr) => acc + getSubmissionPercentage(curr, defaultTotal), 0);
      return Math.round(sum / submissions.length);
    }

    function calculateScoreMetrics(submissions = [], students = []) {
      const subsByStudent = new Map();
      (submissions || []).forEach(sub => {
        const sUid = String(sub.studentUid || sub.studentId || sub.uid || sub.userId || '').trim();
        if (!sUid) return;
        if (!subsByStudent.has(sUid)) subsByStudent.set(sUid, []);
        subsByStudent.get(sUid).push(sub);
      });

      let mastery = 0;
      let proficient = 0;
      let support = 0;
      let scoreSum = 0;
      let evaluatedCount = 0;

      (students || []).forEach(st => {
        const stId = String(st.id || st.uid || '').trim();
        const stSubs = subsByStudent.get(stId) || [];

        let studentScore = null;

        if (stSubs.length > 0) {
          studentScore = calculateAverageScore(stSubs);
        } else if (st.diagnostic_pct !== undefined && st.diagnostic_pct !== null && !isNaN(st.diagnostic_pct)) {
          studentScore = Math.round(Number(st.diagnostic_pct));
        } else if (st.diagnostic_score !== undefined && st.diagnostic_score !== null && !isNaN(st.diagnostic_score)) {
          const tot = Number(st.diagnostic_total) || 10;
          studentScore = tot > 0 ? Math.round((Number(st.diagnostic_score) * 100) / tot) : 0;
        } else if (st.phil_iri_tier) {
          const tier = String(st.phil_iri_tier).toLowerCase();
          if (tier.includes('independent') || tier.includes('mastery')) studentScore = 85;
          else if (tier.includes('instructional') || tier.includes('proficient')) studentScore = 70;
          else studentScore = 45;
        }

        if (studentScore !== null) {
          scoreSum += studentScore;
          evaluatedCount++;
          if (studentScore >= 80) mastery++;
          else if (studentScore >= 60) proficient++;
          else support++;
        } else {
          support++;
        }
      });

      const totalEvaluated = students ? students.length : 0;
      const avgScore = evaluatedCount > 0 
        ? Math.round(scoreSum / evaluatedCount) 
        : (submissions && submissions.length > 0 ? calculateAverageScore(submissions) : 0);
      const masteryPct = totalEvaluated > 0 ? Math.round((mastery * 100) / totalEvaluated) : 0;
      const proficientPct = totalEvaluated > 0 ? Math.round((proficient * 100) / totalEvaluated) : 0;
      const supportPct = totalEvaluated > 0 ? Math.max(0, 100 - masteryPct - proficientPct) : 0;

      return {
        avgScore,
        mastery,
        proficient,
        support,
        masteryPct,
        proficientPct,
        supportPct,
        totalEvaluated,
        evaluatedCount,
        totalSubmissions: (submissions || []).length
      };
    }

    let renderDashboardRaf = null;
    function scheduleDashboardRender() {
      if (renderDashboardRaf) cancelAnimationFrame(renderDashboardRaf);
      renderDashboardRaf = requestAnimationFrame(() => {
        renderDashboardRaf = null;
        try { updateDashboardMetrics(); } catch (e) { console.error("Error updating dashboard metrics:", e); }
        try { renderTeachersTable(); } catch (e) { console.error("Error rendering teachers table:", e); }
        try { renderSectionsTable(); } catch (e) { console.error("Error rendering sections table:", e); }
        try { renderPendingStudentsTable(); } catch (e) { console.error("Error rendering pending students table:", e); }
        try { renderPerformanceTable(); } catch (e) { console.error("Error rendering performance table:", e); }
        try { renderSchoolsMatrix(); } catch (e) { console.error("Error rendering schools matrix:", e); }
        try { if (typeof renderSchoolRankingTable === 'function') renderSchoolRankingTable(); } catch (e) { console.error("Error rendering school ranking table:", e); }
      });
    }

    let renderLibraryRaf = null;
    function scheduleLibraryRender() {
      if (renderLibraryRaf) cancelAnimationFrame(renderLibraryRaf);
      renderLibraryRaf = requestAnimationFrame(() => {
        renderLibraryRaf = null;
        populateSubjectDropdown();
        renderSubjectFolders();
        renderMaterialsTable();
      });
    }

    let renderAssessmentsRaf = null;
    function scheduleAssessmentsRender() {
      if (renderAssessmentsRaf) cancelAnimationFrame(renderAssessmentsRaf);
      renderAssessmentsRaf = requestAnimationFrame(() => {
        renderAssessmentsRaf = null;
        if (typeof updateTimedAssessmentMetrics === 'function') updateTimedAssessmentMetrics();
        if (typeof renderTimedAssessmentsTable === 'function') renderTimedAssessmentsTable();
        updateAssessmentMetrics();
        renderAssessmentsTable();
        if (typeof renderPerformanceTable === 'function') {
          const perfTab = document.getElementById('tab-learner-performance');
          if (perfTab && !perfTab.classList.contains('hidden')) {
            renderPerformanceTable();
          }
        }
      });
    }

    // Toast Utility
    window.showToast = function(message, type = 'info') {
      const container = document.getElementById('toastContainer');
      const toast = document.createElement('div');
      
      let bg = 'bg-slate-900 text-white';
      if (type === 'success') bg = 'bg-emerald-600 text-white';
      if (type === 'error') bg = 'bg-red-600 text-white';
      if (type === 'warning') bg = 'bg-amber-500 text-white';

      toast.className = `${bg} px-4 py-3 rounded-xl shadow-xl text-xs font-semibold flex items-center gap-2 transform transition-all duration-300 pointer-events-auto border border-white/20 animate-toast-in`;
      const iconKey = type === 'success' ? 'check' : type === 'error' ? 'alert-triangle' : type === 'warning' ? 'alert-triangle' : 'help';
      toast.innerHTML = `<span class="shrink-0">${window.uiIcon ? window.uiIcon(iconKey, 'w-4 h-4 text-white inline-block') : '•'}</span> <span>${message}</span>`;
      
      container.appendChild(toast);
      setTimeout(() => {
        toast.style.opacity = '0';
        toast.style.transform = 'translateY(10px)';
        setTimeout(() => toast.remove(), 300);
      }, 4000);
    };


    // =========================================================================
    // EXPORT UTILITIES (CSV AUTO-GENERATION & DOWNLOAD)
    // =========================================================================
    function downloadCSV(filename, rows) {
      if (!rows || rows.length <= 1) {
        window.showToast("No data available to export.", "warning");
        return;
      }
      const escapeCell = (cell) => {
        if (cell === null || cell === undefined) return '""';
        const str = String(cell).replace(/"/g, '""');
        return `"${str}"`;
      };
      const csvContent = "\uFEFF" + rows.map(r => r.map(escapeCell).join(",")).join("\r\n");
      const blob = new Blob([csvContent], { type: "text/csv;charset=utf-8;" });
      const url = URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.setAttribute("href", url);
      link.setAttribute("download", filename);
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      URL.revokeObjectURL(url);
      window.showToast(`Exported ${filename}`, "success");
    }


    // =========================================================================
    // TAB SWITCHING & UTILITIES
    // =========================================================================
    window.switchTab = function(tabId) {
      if (typeof window.toggleMobileSidebar === 'function') window.toggleMobileSidebar(false);

      const SUPER_ADMIN_BLOCKED_TABS = [
        'tab-faculty-verification',
        'tab-classroom-sections',
        'tab-library-management',
        'tab-quiz-attempts'
      ];

      if (isSuperAdmin && (SUPER_ADMIN_BLOCKED_TABS.includes(tabId) || tabId === 'tab-weekly-assessments')) {
        if (SUPER_ADMIN_BLOCKED_TABS.includes(tabId) && typeof window.showToast === 'function') {
          window.showToast("Faculty accreditation, classroom join codes, student credentials, and curriculum remarks are administered exclusively by designated School Administrators.", "info");
        }
        tabId = 'tab-timed-assessments';
      }

      if (tabId === 'tab-school-matrix' && isSchoolAdmin) {
        tabId = 'tab-division-dashboard';
      }

      if (tabId === 'tab-system-settings' && isSchoolAdmin) {
        if (typeof window.showToast === 'function') {
          window.showToast("Access Restricted: System and API settings are administered exclusively by SDO Super Administrators.", "warning");
        }
        tabId = 'tab-division-dashboard';
      }

      document.querySelectorAll('.tab-content').forEach(el => el.classList.add('hidden'));
      const target = document.getElementById(tabId);
      if (target) target.classList.remove('hidden');

      // No global filters container to toggle; each tab has independent dedicated controls

      document.querySelectorAll('aside nav button').forEach(btn => {
        btn.classList.remove('nav-active');
        btn.classList.add('text-slate-700', 'hover:bg-purple-50', 'hover:text-[#5341CD]');
        btn.removeAttribute('aria-current');
      });

      const activeBtn = document.getElementById(`btn-${tabId}`);
      if (activeBtn) {
        activeBtn.classList.remove('text-slate-700', 'hover:bg-purple-50', 'hover:text-[#5341CD]');
        activeBtn.classList.add('nav-active');
        activeBtn.setAttribute('aria-current', 'page');
      }

      const titles = {
        'tab-division-dashboard': isSchoolAdmin 
          ? ['School Overview', `Live indicators for ${getSchoolName(currentAdminUser?.schoolId)}.`]
          : ['Division Executive Overview', 'Live indicators across participating junior high schools in Valenzuela City.'],
        'tab-faculty-verification': isSchoolAdmin
          ? ['School Faculty Accreditation', 'Review and grant accreditation to teachers registered for your school.']
          : ['Division Faculty Registry & Audit', 'Directory of educators across participating junior high schools. Accreditation managed by school administrators.'],
        'tab-classroom-sections': ['Classroom Sections & Join Codes', 'Inspect class rosters, master codes, and initiation assessments (ClassDetailActivity.kt).'],
        'tab-learner-performance': ['Learner Academic Performance', 'Real quiz history, average scores, and streaks (TeacherPerformanceActivity.kt).'],
        'tab-batch-credentials': ['Student Credential Vault', 'Manage batch-generated student accounts (pending_students) & onboarding states.'],
        'tab-school-matrix': ['Valenzuela City School Matrix', 'Overview of participating junior high schools with DepEd IDs.'],
        'tab-library-management': ['Library Subjects & PDF Materials', 'PDF documents (pdfUrl) and interactive multiple-choice question pools (quiz_pool_json).'],
        'tab-timed-assessments': isSchoolAdmin
          ? ['Standardized Reading Tests', `Monitor, evaluate, and inspect standardized uniform timed diagnostic and summative assessments for ${getSchoolName(currentAdminUser?.schoolId) || 'your school'}.`]
          : ['Division Timed Assessments', 'Deploy, monitor, and evaluate standardized uniform timed diagnostic and summative assessments across all Valenzuela junior high schools.'],
        'tab-weekly-assessments': ['Weekly Classroom Assessments', 'Inspect, monitor, and evaluate classroom weekly formative assessments assigned by school educators.'],
        'tab-quiz-attempts': ['Quiz & Practice Attempts Hub', 'Audit student quiz performance across Library modules, Vocabulary Recall, Weekly Assessments, and Phil-IRI Diagnostics.'],
        'tab-announcements': isSchoolAdmin
          ? ['School Announcements & Bulletins', `Broadcast announcements to learners and faculty at ${getSchoolName(currentAdminUser?.schoolId)}.`]
          : ['Division Announcements & Directives', 'Broadcast official memorandums, directives, and advisories to school administrators, faculty, and learners.'],
        'tab-recycle-vault': ['System Recycle Bin & Data Recovery', 'School-scoped protection for deleted learners, curriculum materials, subjects, and classroom rosters. 30-day retention with instant 1-click restoration.'],
        'tab-system-settings': ['System Configuration & AI Engine', 'Manage centralized Google Gemini API keys, division AI engine models, and global platform security parameters.']
      };

      if (titles[tabId]) {
        document.getElementById('currentTabTitle').textContent = titles[tabId][0];
        document.getElementById('currentTabSubtitle').textContent = titles[tabId][1];
      }

      if (tabId === 'tab-batch-credentials') {
        const credSearchEl = document.getElementById('inputCredentialSearch');
        if (credSearchEl && !credentialSearchQuery) {
          credSearchEl.value = '';
          const btnClear = document.getElementById('btnClearCredentialSearch');
          if (btnClear) btnClear.classList.add('hidden');
        }
        populateCredentialSectionDropdown();
        renderPendingStudentsTable();
      }

      if (tabId === 'tab-announcements') {
        renderAnnouncementsFeed();
      }

      if (tabId === 'tab-recycle-vault') {
        renderRecycleVault();
      }

      if (tabId === 'tab-library-management') {
        populateSubjectDropdown();
        renderSubjectFolders();
        renderMaterialsTable();
      }

      if (tabId === 'tab-timed-assessments') {
        updateTimedAssessmentMetrics();
        renderTimedAssessmentsTable();
        renderCompetenciesMatrix();
      }

      if (tabId === 'tab-weekly-assessments') {
        updateAssessmentMetrics();
        renderAssessmentsTable();
      }

      if (tabId === 'tab-quiz-attempts') {
        loadAllQuizAttempts();
      }

      if (tabId === 'tab-classroom-sections') {
        renderSectionsTable();
      }

      if (tabId === 'tab-learner-performance') {
        renderPerformanceTable();
      }

      if (tabId === 'tab-faculty-verification') {
        const facSearchEl = document.getElementById('inputFacultySearch');
        const adminEmail = (currentAdminUser?.email || '').toLowerCase().trim();
        if (facSearchEl && (!facultySearchQuery || (adminEmail && facultySearchQuery === adminEmail))) {
          facSearchEl.value = '';
          facultySearchQuery = '';
          const btnClear = document.getElementById('btnClearFacultySearch');
          if (btnClear) btnClear.classList.add('hidden');
        }
        renderTeachersTable();
      }

      if (tabId === 'tab-system-settings') {
        if (typeof renderSystemSettingsView === 'function') {
          renderSystemSettingsView();
        }
        if (typeof renderDivisionKeySettingsView === 'function') {
          renderDivisionKeySettingsView();
        }
        if (typeof window.loadDivisionKeyConfig === 'function') {
          window.loadDivisionKeyConfig();
        }
      }

      if (tabId === 'tab-school-matrix') {
        renderSchoolsMatrix();
      }

      if (tabId === 'tab-division-dashboard') {
        updateDashboardMetrics();
        if (typeof renderSchoolRankingTable === 'function') {
          renderSchoolRankingTable();
        }
      }
    };

    window.copyToClipboard = function(text, msg) {
      navigator.clipboard.writeText(text).then(() => {
        window.showToast(msg || "Copied to clipboard!", "success");
      }).catch(err => {
        prompt("Copy text manually:", text);
      });
    };

    function escapeHtml(str) {
      if (!str) return '';
      return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#039;');
    }


window.matchSmartSearch = matchSmartSearch;
window.getSubmissionPercentage = getSubmissionPercentage;
window.calculateAverageScore = calculateAverageScore;
window.calculateScoreMetrics = calculateScoreMetrics;
window.scheduleDashboardRender = scheduleDashboardRender;
window.scheduleLibraryRender = scheduleLibraryRender;
window.scheduleAssessmentsRender = scheduleAssessmentsRender;
window.downloadCSV = downloadCSV;
window.escapeHtml = escapeHtml;
