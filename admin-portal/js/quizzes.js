    // =========================================================================
    // 6.5 QUIZ & PRACTICE ATTEMPTS HUB (With Exact Mobile Recall Mechanics)
    // =========================================================================
    // Shared state variables (cachedAllAttempts, cachedQuizHistoryDocs,
    // selectedQuizTypeFilter, selectedQuizGradeFilter, selectedQuizMasteryFilter,
    // quizSearchQuery, activeInspectorAttempt, isLoadingQuizAttempts) are
    // declared globally in globals.js so analytics.js and other modules stay in sync.
    let quizHistoryLoadedScope = null;

    function resolveAttemptTimestampMs(rawTs) {
      if (!rawTs) return Date.now();
      if (typeof rawTs === 'number' && !isNaN(rawTs)) return rawTs;
      if (typeof rawTs?.toMillis === 'function') return rawTs.toMillis();
      if (typeof rawTs?.seconds === 'number') return rawTs.seconds * 1000;
      const parsed = Date.parse(rawTs);
      return isNaN(parsed) ? Date.now() : parsed;
    }

    window.filterQuizType = function(type) {
      selectedQuizTypeFilter = type;
      renderQuizCategoryFolders();
      renderQuizAttemptsTable();
    };

    window.handleQuizSearchChange = function(q) {
      quizSearchQuery = (q || '').toLowerCase().trim();
      renderQuizAttemptsTable();
    };

    window.handleQuizGradeFilterChange = function(grade) {
      selectedQuizGradeFilter = grade;
      renderQuizAttemptsTable();
    };

    window.handleQuizMasteryFilterChange = function(tier) {
      selectedQuizMasteryFilter = tier;
      renderQuizAttemptsTable();
    };

    window.refreshQuizAttemptsData = async function() {
      cachedQuizHistoryDocs = [];
      quizHistoryLoadedScope = null;
      await loadAllQuizAttempts(true);
      window.showToast("Quiz and practice attempts refreshed.", "success");
    };

    window.syncSubmissionsToQuizAttempts = function() {
      const tabEl = document.getElementById('tab-quiz-attempts');
      if (tabEl && !tabEl.classList.contains('hidden')) {
        compileAllQuizAttempts();
      }
    };

    window.syncUsersToQuizAttempts = function() {
      const tabEl = document.getElementById('tab-quiz-attempts');
      if (tabEl && !tabEl.classList.contains('hidden')) {
        if (quizHistoryLoadedScope === null && cachedUsers.length > 0 && !isLoadingQuizAttempts) {
          loadAllQuizAttempts();
        } else {
          compileAllQuizAttempts();
        }
      }
    };

    async function loadAllQuizAttempts(forceReload = false) {
      if (isLoadingQuizAttempts) return;
      isLoadingQuizAttempts = true;

      const tbody = document.getElementById('quizAttemptsTableBody');
      if (tbody && cachedAllAttempts.length === 0) {
        tbody.innerHTML = `<tr><td colspan="7" class="p-8 text-center text-slate-400 font-medium">Gathering student attempts across modules...</td></tr>`;
      }

      try {
        // Determine target students by jurisdiction
        let targetStudents = cachedUsers.filter(u => u.role === 'student');
        if (selectedJurisdiction !== 'ALL') {
          targetStudents = targetStudents.filter(u => String(u.schoolId) === selectedJurisdiction);
        }

        // Fetch subcollection quiz_history for target students if not cached for this scope
        if (forceReload || quizHistoryLoadedScope !== selectedJurisdiction || cachedQuizHistoryDocs.length === 0) {
          const batchSize = 25;
          const allDocs = [];
          for (let i = 0; i < targetStudents.length; i += batchSize) {
            const batch = targetStudents.slice(i, i + batchSize);
            const batchResults = await Promise.all(batch.map(async (st) => {
              try {
                const snap = await getDocs(collection(db, "users", st.id, "quiz_history"));
                return snap.docs.map(d => ({
                  ...d.data(),
                  id: d.id,
                  studentId: st.id,
                  studentName: `${st.firstName || ''} ${st.lastName || ''}`.trim() || st.username || 'Learner',
                  schoolId: st.schoolId || '',
                  grade: st.grade || '',
                  section: st.section || 'General'
                }));
              } catch (err) {
                return [];
              }
            }));
            allDocs.push(...batchResults.flat());
          }
          cachedQuizHistoryDocs = allDocs;
          if (cachedUsers.length > 0) {
            quizHistoryLoadedScope = selectedJurisdiction;
          }
        }

        compileAllQuizAttempts();

      } catch (err) {
        console.error("Error loading quiz attempts:", err);
        if (tbody) {
          tbody.innerHTML = `<tr><td colspan="7" class="p-8 text-center text-rose-500 font-medium">Error loading quiz attempts: ${escapeHtml(err.message)}</td></tr>`;
        }
      } finally {
        isLoadingQuizAttempts = false;
      }
    }

    function compileAllQuizAttempts() {
      const attempts = [];

      // 1. Weekly Assessment Submissions (assessment_submissions root collection)
      let weeklySubs = cachedSubmissions;
      if (selectedJurisdiction !== 'ALL') {
        weeklySubs = weeklySubs.filter(sub => {
          const subSchool = sub.schoolId || (cachedUsers.find(u => u.id === (sub.studentId || sub.studentUid))?.schoolId);
          return String(subSchool) === selectedJurisdiction;
        });
      }

      weeklySubs.forEach(sub => {
        const sUid = sub.studentId || sub.studentUid;
        const st = cachedUsers.find(u => u.id === sUid);
        const totalQ = Number(sub.totalQuestions) || (sub.answers && sub.answers.length) || 10;
        const score = Number(sub.score) || 0;
        const pct = getSubmissionPercentage(sub, totalQ);

        attempts.push({
          id: `weekly_${sub.id}`,
          quizType: "weekly_assessment",
          categoryName: "Weekly Assessment",
          categoryBadgeClass: "bg-blue-50 text-blue-700 border-blue-200",
          iconType: "clipboard",
          title: sub.assessmentTitle || sub.title || `${sub.subjectName || 'Reading'} Diagnostic Test`,
          studentId: sUid,
          studentName: sub.studentName || (st ? `${st.firstName || ''} ${st.lastName || ''}`.trim() : 'Learner'),
          schoolId: sub.schoolId || (st ? st.schoolId : ''),
          grade: sub.grade || (st ? st.grade : ''),
          section: sub.className || (st ? st.section : 'General'),
          score: score,
          totalQuestions: totalQ,
          percentage: pct,
          timestamp: resolveAttemptTimestampMs(sub.submittedAt || sub.timestamp),
          items: sub.answers || sub.items || [],
          xpEarned: pct >= 60 ? 50 : 0,
          raw: sub
        });
      });

      // 2. Vocabulary Recall & Library Material Quizzes (from student quiz_history)
      cachedQuizHistoryDocs.forEach(doc => {
        // Skip duplicate weekly assessments that are already added via assessment_submissions
        if (doc.quizType === "weekly_assessment") return;

        // Apply school jurisdiction filter if set
        if (selectedJurisdiction !== 'ALL') {
          const docSchool = doc.schoolId || (cachedUsers.find(u => u.id === doc.studentId)?.schoolId);
          if (String(docSchool) !== selectedJurisdiction) return;
        }

        const qType = doc.quizType || "recall";
        const isMat = qType === "material";
        const items = doc.items || [];
        const score = Number(doc.finalScore) || 0;
        const docAttempts = Number(doc.totalAttempts) || 0;
        const docTotalQ = Number(doc.totalQuestions) || 0;

        // Resolve true total questions with fallback to material metadata or attempts
        const mat = isMat ? cachedMaterials.find(m => m.id === doc.materialId) : null;
        let matPoolCount = 0;
        if (mat) {
          matPoolCount = Number(mat.quiz_max_items) || 0;
          if (!matPoolCount && mat.quiz_pool_json) {
            try { matPoolCount = JSON.parse(mat.quiz_pool_json).length; } catch (_) {}
          }
        }

        let totalQ = docTotalQ || matPoolCount || docAttempts || items.length || 10;
        if (items.length > totalQ) totalQ = items.length;
        if (docAttempts > totalQ) totalQ = docAttempts;
        if (totalQ < score) totalQ = score;
        totalQ = Math.max(totalQ, 1);

        const pct = Math.min(100, Math.round((score * 100) / totalQ));

        attempts.push({
          id: `hist_${doc.id}`,
          quizType: qType,
          categoryName: isMat ? "Library Reading Quiz" : "Vocabulary Recall Drill",
          categoryBadgeClass: isMat ? "bg-amber-50 text-amber-700 border-amber-200" : "bg-purple-50 text-[#5341CD] border-purple-200",
          iconType: isMat ? "book" : "flame",
          title: isMat 
            ? (cachedMaterials.find(m => m.id === doc.materialId)?.title || doc.title || "PDF Reading Comprehension")
            : (doc.title || "Vocabulary Spaced Repetition Drill"),
          studentId: doc.studentId,
          studentName: doc.studentName,
          schoolId: doc.schoolId,
          grade: doc.grade,
          section: doc.section,
          score: score,
          totalQuestions: totalQ,
          percentage: pct,
          timestamp: resolveAttemptTimestampMs(doc.timestamp),
          items: items,
          xpEarned: doc.xpEarned || 0,
          maxComboStreak: doc.maxComboStreak || 0,
          raw: doc
        });
      });

      // 3.1 Division Standardized Timed Assessments (timed_assessment_submissions)
      let timedSubs = cachedTimedSubmissions || [];
      if (selectedJurisdiction !== 'ALL') {
        timedSubs = timedSubs.filter(sub => {
          const subSchool = sub.schoolId || (cachedUsers.find(u => u.id === (sub.studentId || sub.studentUid))?.schoolId);
          return String(subSchool) === selectedJurisdiction;
        });
      }

      timedSubs.forEach(sub => {
        const sUid = sub.studentId || sub.studentUid;
        const st = cachedUsers.find(u => u.id === sUid);
        const totalQ = Number(sub.totalQuestions) || (sub.answers && sub.answers.length) || 5;
        const score = Number(sub.score) || 0;
        const pct = sub.percentage !== undefined ? sub.percentage : Math.round((score / Math.max(totalQ, 1)) * 100);

        attempts.push({
          id: `timed_${sub.id}`,
          quizType: "division_timed_assessment",
          categoryName: "Standardized Reading Test",
          categoryBadgeClass: "bg-purple-50 text-[#5341CD] border-purple-200",
          iconType: "clock",
          title: sub.assessmentTitle || sub.title || "Phil-IRI Standardized Reading Test",
          studentId: sUid,
          studentName: sub.studentName || (st ? `${st.firstName || ''} ${st.lastName || ''}`.trim() : 'Learner'),
          schoolId: sub.schoolId || (st ? st.schoolId : ''),
          grade: sub.grade || (st ? st.grade : ''),
          section: sub.className || (st ? st.section : 'General'),
          score: score,
          totalQuestions: totalQ,
          percentage: pct,
          timestamp: resolveAttemptTimestampMs(sub.submittedAt || sub.timestamp),
          items: sub.answers || sub.items || [],
          xpEarned: pct >= 60 ? 50 : 0,
          raw: sub
        });
      });

      // 3. Phil-IRI Diagnostic Pre-Tests (From Student Profiles)
      let diagStudents = cachedUsers.filter(u => u.role === 'student' && (u.diagnostic_pct !== undefined || u.diagnostic_score !== undefined || u.phil_iri_tier));
      if (selectedJurisdiction !== 'ALL') {
        diagStudents = diagStudents.filter(u => String(u.schoolId) === selectedJurisdiction);
      }

      diagStudents.forEach(st => {
        const tot = Number(st.diagnostic_total) || 10;
        const score = Number(st.diagnostic_score) || (st.diagnostic_pct ? Math.round((st.diagnostic_pct * tot) / 100) : 7);
        const pct = st.diagnostic_pct !== undefined ? Math.round(Number(st.diagnostic_pct)) : Math.round((score * 100) / tot);

        attempts.push({
          id: `diag_${st.id}`,
          quizType: "diagnostic",
          categoryName: "Phil-IRI Pre-Test",
          categoryBadgeClass: "bg-emerald-50 text-emerald-700 border-emerald-200",
          iconType: "shield-check",
          title: "Phil-IRI Reading Diagnostic Assessment",
          studentId: st.id,
          studentName: `${st.firstName || ''} ${st.lastName || ''}`.trim() || st.username || 'Learner',
          schoolId: st.schoolId,
          grade: st.grade,
          section: st.section || 'General',
          score: score,
          totalQuestions: tot,
          percentage: pct,
          timestamp: st.diagnostic_completed_at?.toMillis ? st.diagnostic_completed_at.toMillis() : (st.createdAt || Date.now()),
          items: [],
          philIriTier: st.phil_iri_tier || (pct >= 80 ? "Independent" : (pct >= 60 ? "Instructional" : "Frustration")),
          raw: st
        });
      });

      // Sort by newest timestamp first
      attempts.sort((a, b) => (b.timestamp || 0) - (a.timestamp || 0));
      cachedAllAttempts = attempts;

      renderQuizCategoryFolders();
      renderQuizAttemptsTable();
    }

    function renderQuizCategoryFolders() {
      const container = document.getElementById('quizCategoryFoldersContainer');
      if (!container) return;

      const totalAll = cachedAllAttempts.length;
      const countMaterial = cachedAllAttempts.filter(a => a.quizType === 'material').length;
      const countRecall = cachedAllAttempts.filter(a => a.quizType === 'recall').length;
      const countWeekly = cachedAllAttempts.filter(a => a.quizType === 'weekly_assessment').length;
      const countDiag = cachedAllAttempts.filter(a => a.quizType === 'diagnostic').length;

      const countTimed = cachedAllAttempts.filter(a => a.quizType === 'division_timed_assessment').length;

      const folders = [
        {
          id: 'ALL',
          name: 'All Quiz Types',
          desc: 'Comprehensive ledger of all student practice',
          count: totalAll,
          color: 'indigo',
          icon: uiIcon('clipboard', 'w-4 h-4 text-indigo-600 inline')
        },
        {
          id: 'material',
          name: 'Library Quizzes',
          desc: 'Reading comprehension on lesson PDFs',
          count: countMaterial,
          color: 'amber',
          icon: uiIcon('folder', 'w-4 h-4 text-amber-500 inline')
        },
        {
          id: 'recall',
          name: 'Recall Drills',
          desc: 'Spaced repetition speed vocabulary',
          count: countRecall,
          color: 'purple',
          icon: uiIcon('flame', 'w-4 h-4 text-[#5341CD] inline')
        },
        {
          id: 'division_timed_assessment',
          name: 'Standardized Reading Tests',
          desc: 'Phil-IRI standardized reading assessments',
          count: countTimed,
          color: 'purple',
          icon: uiIcon('clock', 'w-4 h-4 text-[#5341CD] inline')
        },
        {
          id: 'weekly_assessment',
          name: 'Weekly Diagnostics',
          desc: 'Official faculty-published assessments',
          count: countWeekly,
          color: 'blue',
          icon: uiIcon('calendar', 'w-4 h-4 text-blue-600 inline')
        },
        {
          id: 'diagnostic',
          name: 'Phil-IRI Pre-Tests',
          desc: 'Initial student literacy diagnostic level',
          count: countDiag,
          color: 'emerald',
          icon: uiIcon('check', 'w-4 h-4 text-emerald-600 inline')
        }
      ];

      container.innerHTML = folders.map(f => {
        const isActive = selectedQuizTypeFilter === f.id;
        return `
          <div onclick="filterQuizType('${f.id}')" class="p-3.5 rounded-xl border transition-all cursor-pointer space-y-1.5 select-none ${isActive ? 'bg-indigo-50 border-indigo-500 shadow-md ring-2 ring-indigo-500/20' : 'bg-slate-50 border-slate-200 hover:border-indigo-300 hover:bg-white'}">
            <div class="flex items-center justify-between">
              <span class="font-bold text-slate-800 text-xs flex items-center gap-1.5">
                ${f.icon} <span>${f.name}</span>
              </span>
              <span class="px-2 py-0.5 rounded-full text-[10px] font-bold ${isActive ? 'bg-indigo-600 text-white' : 'bg-slate-200 text-slate-700'}">
                ${f.count}
              </span>
            </div>
            <div class="text-[10px] text-slate-500 line-clamp-1">${f.desc}</div>
            <div class="pt-1 border-t border-slate-200/60 flex items-center justify-between text-[11px]">
              <span class="font-bold text-indigo-700">${f.count} Attempt${f.count === 1 ? '' : 's'}</span>
              <span class="font-semibold ${isActive ? 'text-indigo-700 underline' : 'text-slate-400'}">${isActive ? 'Active' : 'Filter &rarr;'}</span>
            </div>
          </div>
        `;
      }).join('');
    }

    function renderQuizAttemptsTable() {
      const tbody = document.getElementById('quizAttemptsTableBody');
      if (!tbody) return;

      let list = cachedAllAttempts;

      // 1. Category Filter
      if (selectedQuizTypeFilter !== 'ALL') {
        list = list.filter(a => a.quizType === selectedQuizTypeFilter);
      }

      // 1b. School Filter
      if (selectedJurisdiction !== 'ALL') {
        list = list.filter(a => String(a.schoolId) === selectedJurisdiction);
      }

      // 2. Grade Filter
      if (selectedQuizGradeFilter !== 'ALL') {
        const targetNum = selectedQuizGradeFilter.replace('Grade ', '').trim();
        list = list.filter(a => String(a.grade || '').trim() === targetNum || (a.grade || '').trim() === selectedQuizGradeFilter);
      }

      // 3. Mastery Tier Filter
      if (selectedQuizMasteryFilter === 'MASTERY') {
        list = list.filter(a => a.percentage >= 80);
      } else if (selectedQuizMasteryFilter === 'PROFICIENT') {
        list = list.filter(a => a.percentage >= 60 && a.percentage < 80);
      } else if (selectedQuizMasteryFilter === 'SUPPORT') {
        list = list.filter(a => a.percentage < 60);
      }

      // 4. Search Filter
      if (quizSearchQuery) {
        list = list.filter(a => {
          const blob = `${a.studentName || ''} ${a.title || ''} ${a.section || ''} ${getSchoolName(a.schoolId)} ${a.categoryName || ''} ${a.grade || ''}`;
          return matchSmartSearch(blob, quizSearchQuery);
        });
      }

      // Update KPI metrics based on current category list
      const totalAttempts = list.length;
      const sumPct = list.reduce((acc, curr) => acc + (curr.percentage || 0), 0);
      const avgAccuracy = totalAttempts > 0 ? Math.round(sumPct / totalAttempts) : 0;
      const masteryCount = list.filter(a => a.percentage >= 80).length;
      const masteryRate = totalAttempts > 0 ? Math.round((masteryCount * 100) / totalAttempts) : 0;
      const uniqueScholars = new Set(list.map(a => a.studentId)).size;

      const elTotal = document.getElementById('statTotalQuizAttempts');
      const elAcc = document.getElementById('statAvgQuizAccuracy');
      const elMast = document.getElementById('statHighMasteryRate');
      const elScholars = document.getElementById('statActiveQuizScholars');
      const elBadge = document.getElementById('quizAttemptsCountBadge');
      const elTitle = document.getElementById('quizAttemptsTableTitle');

      if (elTotal) elTotal.textContent = totalAttempts;
      if (elAcc) elAcc.textContent = `${avgAccuracy}%`;
      if (elMast) elMast.textContent = `${masteryRate}%`;
      if (elScholars) elScholars.textContent = uniqueScholars;
      if (elBadge) elBadge.textContent = `${totalAttempts} Attempt${totalAttempts === 1 ? '' : 's'}`;

      const catNames = {
        'ALL': 'All Recorded Quiz Attempts',
        'material': 'Library Reading Quiz Attempts',
        'recall': 'Vocabulary Recall Practice Attempts',
        'division_timed_assessment': 'Standardized Reading Test Submissions',
        'weekly_assessment': 'Weekly Classroom Diagnostic Submissions',
        'diagnostic': 'Phil-IRI Initiation Diagnostic Pre-Tests'
      };
      if (elTitle) elTitle.textContent = catNames[selectedQuizTypeFilter] || 'Quiz Attempts';

      if (list.length === 0) {
        tbody.innerHTML = `
          <tr>
            <td colspan="7" class="p-8 text-center text-slate-400 font-medium">
              No quiz attempts found matching the current category or filter criteria.
            </td>
          </tr>`;
        return;
      }

      tbody.innerHTML = list.map(a => {
        const dateStr = a.timestamp ? new Date(a.timestamp).toLocaleString(undefined, { month: 'short', day: 'numeric', year: 'numeric', hour: '2-digit', minute: '2-digit' }) : 'Recorded';
        const pct = a.percentage || 0;

        let badgeClass = 'bg-rose-50 text-rose-700 border-rose-200';
        let badgeLabel = 'Needs Support';
        if (pct >= 80) {
          badgeClass = 'bg-emerald-50 text-emerald-700 border-emerald-200';
          badgeLabel = 'High Mastery';
        } else if (pct >= 60) {
          badgeClass = 'bg-amber-50 text-amber-700 border-amber-200';
          badgeLabel = 'Proficient';
        }

        return `
          <tr class="hover:bg-slate-50/80 transition-colors">
            <td class="p-3.5">
              <div class="font-bold text-slate-900">${escapeHtml(a.studentName)}</div>
              <div class="text-[10px] text-slate-400 font-medium">${a.grade ? a.grade + ' &bull; ' : ''}Section ${escapeHtml(a.section || 'General')}</div>
            </td>
            <td class="p-3.5 text-slate-600 font-medium">
              ${escapeHtml(getSchoolName(a.schoolId))}
            </td>
            <td class="p-3.5">
              <span class="inline-block px-2 py-0.5 rounded text-[10px] font-bold border ${a.categoryBadgeClass} mb-0.5">
                ${escapeHtml(a.categoryName)}
              </span>
              <div class="font-semibold text-slate-800 line-clamp-1" title="${escapeHtml(a.title)}">${escapeHtml(a.title)}</div>
            </td>
            <td class="p-3.5">
              <div class="font-black text-sm ${pct >= 80 ? 'text-emerald-700' : (pct >= 60 ? 'text-amber-700' : 'text-rose-700')}">
                ${a.score} / ${a.totalQuestions} <span class="text-xs font-bold text-slate-500">(${pct}%)</span>
              </div>
            </td>
            <td class="p-3.5">
              <span class="inline-flex items-center gap-1 px-2.5 py-1 rounded-lg text-[10px] font-bold border ${badgeClass}">
                <span class="w-1.5 h-1.5 rounded-full ${pct >= 80 ? 'bg-emerald-500' : (pct >= 60 ? 'bg-amber-500' : 'bg-rose-500')}"></span>
                <span>${badgeLabel}</span>
              </span>
            </td>
            <td class="p-3.5 text-slate-500 text-[11px]">
              ${dateStr}
            </td>
            <td class="p-3.5 text-right">
              <button onclick="openQuizAttemptInspector('${a.id}')" class="px-2.5 py-1 bg-white hover:bg-slate-100 text-slate-700 border border-slate-300 rounded-lg text-xs font-semibold shadow-xs inline-flex items-center gap-1 transition-all">
                ${uiIcon('eye', 'w-3 h-3 text-indigo-600')} <span>Inspect &rarr;</span>
              </button>
            </td>
          </tr>
        `;
      }).join('');
    }


    // =========================================================================
    // INSPECTOR MODAL: Full Mobile App Mechanics Rendering (Cloze, Detective, Glitch, Landmine, Rapid)
    // =========================================================================
    window.openQuizAttemptInspector = function(attemptId) {
      const attempt = cachedAllAttempts.find(a => a.id === attemptId);
      if (!attempt) {
        window.showToast("Attempt details not found.", "error");
        return;
      }
      activeInspectorAttempt = attempt;

      const dateStr = attempt.timestamp ? new Date(attempt.timestamp).toLocaleString() : 'Recorded';
      const pct = attempt.percentage || 0;

      document.getElementById('inspectorAttemptTitle').textContent = attempt.title;
      document.getElementById('inspectorAttemptMeta').textContent = `${attempt.studentName} • ${getSchoolName(attempt.schoolId)} • ${attempt.section || 'General'} • ${dateStr}`;
      document.getElementById('inspectorAttemptCategoryBadge').textContent = attempt.categoryName;
      document.getElementById('inspectorAttemptCategoryBadge').className = `px-2 py-0.5 rounded text-[10px] font-bold border ${attempt.categoryBadgeClass}`;

      const masteryEl = document.getElementById('inspectorAttemptMasteryBadge');
      if (pct >= 80) {
        masteryEl.textContent = "High Mastery (Passed)";
        masteryEl.className = "px-2 py-0.5 rounded text-[10px] font-bold bg-emerald-50 text-emerald-700 border border-emerald-200";
      } else if (pct >= 60) {
        masteryEl.textContent = "Proficient Tier";
        masteryEl.className = "px-2 py-0.5 rounded text-[10px] font-bold bg-amber-50 text-amber-700 border border-amber-200";
      } else {
        masteryEl.textContent = "Needs Support";
        masteryEl.className = "px-2 py-0.5 rounded text-[10px] font-bold bg-rose-50 text-rose-700 border border-rose-200";
      }

      document.getElementById('inspectorScoreAccuracy').textContent = `${attempt.score} / ${attempt.totalQuestions} (${pct}%)`;
      document.getElementById('inspectorTotalQuestions').textContent = attempt.totalQuestions;
      document.getElementById('inspectorXpEarned').textContent = `+${attempt.xpEarned || 0} XP`;
      document.getElementById('inspectorMaxCombo').textContent = attempt.maxComboStreak ? `${attempt.maxComboStreak}x Streak` : 'N/A';

      const itemsContainer = document.getElementById('inspectorItemsList');
      const itemsCountBadge = document.getElementById('inspectorItemsCountBadge');

      const items = attempt.items || [];
      if (itemsCountBadge) itemsCountBadge.textContent = `${items.length} item${items.length === 1 ? '' : 's'}`;

      if (items.length === 0) {
        if (attempt.quizType === 'diagnostic') {
          itemsContainer.innerHTML = `
            <div class="p-6 text-center bg-slate-50 rounded-xl border border-dashed border-slate-300 space-y-2">
              <div class="text-emerald-700 font-bold text-sm">Phil-IRI Reading Diagnostic Pre-Test</div>
              <div class="text-xs text-slate-500">Learner demonstrated a reading proficiency level evaluated at: <strong class="text-slate-800">${escapeHtml(attempt.philIriTier || 'Instructional')}</strong>.</div>
              <div class="text-xs font-mono text-slate-400">Recorded Score: ${attempt.score} / ${attempt.totalQuestions} (${pct}%)</div>
            </div>`;
        } else {
          itemsContainer.innerHTML = `
            <div class="p-6 text-center text-slate-400 bg-slate-50 rounded-xl border border-dashed border-slate-300">
              No question-by-question results logged for this session. Score recorded: ${attempt.score} / ${attempt.totalQuestions} (${pct}%).
            </div>`;
        }
      } else {
        let partialNotice = '';
        if (items.length < attempt.totalQuestions) {
          partialNotice = `
            <div class="p-3 bg-amber-50 border border-amber-200 rounded-xl text-xs text-amber-900 flex items-center gap-2 mb-3">
              <span class="text-sm">ℹ️</span>
              <span>Displaying <strong>${items.length} question(s)</strong> logged from this session. Cumulative final score: <strong>${attempt.score} / ${attempt.totalQuestions}</strong>.</span>
            </div>
          `;
        }
        itemsContainer.innerHTML = partialNotice + items.map((item, idx) => {
          return renderInspectorQuestionCard(item, idx, attempt.quizType);
        }).join('');
      }

      document.getElementById('modalAttemptInspector').classList.remove('hidden');
    };

    function renderInspectorQuestionCard(item, idx, quizType) {
      const qNum = idx + 1;
      const isCorrect = item.isCorrect === true || item.correct === true;
      const questionText = item.question || item.q || item.title || item.contextSentence || 'Question item';
      const targetWord = item.targetWord || item.word || '';
      const rawUserAns = String(item.userAnswer || item.selectedAnswer || item.ans || item.answer || '').trim();
      const rawCorrectAns = String(item.correctAnswer || item.correct || item.solution || '').trim();
      const cleanUserAns = rawUserAns.replace(/[*]/g, '').trim().replace(/[.]$/, '');
      const cleanCorrectAns = rawCorrectAns.replace(/[*]/g, '').trim().replace(/[.]$/, '');
      const explanation = item.explanation || item.rationale || '';
      const mechanic = String(item.mechanic || (quizType === 'material' ? 'READING_COMPREHENSION' : 'CLOZE')).toUpperCase();

      // Mechanic Tag Badge configuration matching mobile app
      let mechTagHtml = '';
      if (mechanic.includes('CLOZE')) {
        mechTagHtml = `<span class="px-2 py-0.5 rounded text-[10px] font-bold bg-purple-50 text-[#5341CD] border border-purple-200">⚡ Sentence Cloze</span>`;
      } else if (mechanic.includes('CONTEXT') || mechanic.includes('DETECTIVE')) {
        mechTagHtml = `<span class="px-2 py-0.5 rounded text-[10px] font-bold bg-blue-50 text-blue-700 border border-blue-200">🔍 Context Detective</span>`;
      } else if (mechanic.includes('IMPOSTOR') || mechanic.includes('GLITCH')) {
        mechTagHtml = `<span class="px-2 py-0.5 rounded text-[10px] font-bold bg-amber-50 text-amber-700 border border-amber-200">👾 Impostor Sentence</span>`;
      } else if (mechanic.includes('LANDMINE') || mechanic.includes('DEFUSAL')) {
        mechTagHtml = `<span class="px-2 py-0.5 rounded text-[10px] font-bold bg-rose-50 text-rose-700 border border-rose-200">💣 Sentence Defusal</span>`;
      } else if (mechanic.includes('RAPID')) {
        mechTagHtml = `<span class="px-2 py-0.5 rounded text-[10px] font-bold bg-indigo-50 text-indigo-700 border border-indigo-200">⚡ Speed Vocabulary Pairing</span>`;
      } else {
        mechTagHtml = `<span class="px-2 py-0.5 rounded text-[10px] font-bold bg-emerald-50 text-emerald-700 border border-emerald-200">📚 Reading Comprehension</span>`;
      }

      // Options rendering
      let optionsList = [];
      if (Array.isArray(item.options)) optionsList = item.options;
      else if (Array.isArray(item.choices)) optionsList = item.choices;

      let optionsHtml = '';
      if (optionsList.length > 0) {
        optionsHtml = `
          <div class="grid grid-cols-1 sm:grid-cols-2 gap-2 pt-2">
            ${optionsList.map(opt => {
              const cleanOpt = String(opt).replace(/[*]/g, '').trim().replace(/[.]$/, '');
              const isOptionCorrect = cleanOpt.toLowerCase() === cleanCorrectAns.toLowerCase();
              const isOptionSelected = cleanOpt.toLowerCase() === cleanUserAns.toLowerCase();

              let optStyle = "bg-slate-50 border-slate-200 text-slate-700";
              let badgeOpt = "";

              if (isOptionCorrect) {
                optStyle = "bg-emerald-50 border-emerald-500 text-emerald-900 font-bold ring-1 ring-emerald-400";
                badgeOpt = `<span class="ml-auto text-[10px] font-bold text-emerald-700 flex items-center gap-1">${uiIcon('check', 'w-3 h-3 inline text-emerald-600')} Correct</span>`;
              } else if (isOptionSelected && !isCorrect) {
                optStyle = "bg-rose-50 border-rose-500 text-rose-900 font-bold ring-1 ring-rose-400";
                badgeOpt = `<span class="ml-auto text-[10px] font-bold text-rose-700 flex items-center gap-1">${uiIcon('x', 'w-3 h-3 inline text-rose-600')} Chosen</span>`;
              }

              return `
                <div class="p-2.5 rounded-xl border text-xs flex items-center justify-between ${optStyle}">
                  <span>${escapeHtml(opt)}</span>
                  ${badgeOpt}
                </div>
              `;
            }).join('')}
          </div>
        `;
      } else {
        // Simple direct comparison
        optionsHtml = `
          <div class="grid grid-cols-1 sm:grid-cols-2 gap-2 pt-2">
            <div class="p-2.5 rounded-xl border ${isCorrect ? 'bg-emerald-50 border-emerald-400' : 'bg-rose-50 border-rose-400'} text-xs">
              <span class="text-[10px] font-bold uppercase text-slate-400 block">Learner Response</span>
              <span class="font-bold ${isCorrect ? 'text-emerald-800' : 'text-rose-800'}">${escapeHtml(cleanUserAns || 'No Answer')}</span>
            </div>
            <div class="p-2.5 rounded-xl border bg-emerald-50 border-emerald-400 text-xs">
              <span class="text-[10px] font-bold uppercase text-emerald-600 block">Correct Solution</span>
              <span class="font-bold text-emerald-900">${escapeHtml(cleanCorrectAns || 'Standard Key')}</span>
            </div>
          </div>
        `;
      }

      // Mechanic-specific UI enhancements (strictly conditioned on the active mechanic)
      let mechanicExtraHtml = '';

      if (mechanic.includes('IMPOSTOR') || mechanic.includes('GLITCH')) {
        const genText = item.genuineSentence || cleanCorrectAns;
        const impText = item.impostorSentence || (!isCorrect ? cleanUserAns : '');
        mechanicExtraHtml = `
          <div class="space-y-2 pt-1">
            <div class="text-[10px] font-bold text-slate-400 uppercase tracking-wider">Spot the Impostor Choices</div>
            <div class="grid grid-cols-1 sm:grid-cols-2 gap-2 text-xs">
              <div class="p-3 bg-emerald-50 border ${isCorrect ? 'border-emerald-500 ring-2 ring-emerald-500/20' : 'border-emerald-300'} rounded-xl relative">
                <div class="flex items-center justify-between mb-1">
                  <span class="text-[10px] font-bold text-emerald-700 uppercase flex items-center gap-1">${uiIcon('shield-check', 'w-3 h-3')} Genuine Authentic Card</span>
                  ${isCorrect ? `<span class="px-2 py-0.5 rounded text-[9px] font-bold bg-emerald-600 text-white">Chosen &bull; Correct</span>` : ''}
                </div>
                <p class="font-semibold text-emerald-900 mt-1">${escapeHtml(genText)}</p>
              </div>
              <div class="p-3 bg-rose-50 border ${!isCorrect ? 'border-rose-500 ring-2 ring-rose-500/20' : 'border-rose-300'} rounded-xl relative">
                <div class="flex items-center justify-between mb-1">
                  <span class="text-[10px] font-bold text-rose-700 uppercase flex items-center gap-1">👾 Impostor / Glitch Card</span>
                  ${!isCorrect ? `<span class="px-2 py-0.5 rounded text-[9px] font-bold bg-rose-600 text-white">Chosen &bull; Glitch Caught</span>` : ''}
                </div>
                <p class="font-semibold text-rose-900 mt-1 line-through">${escapeHtml(impText || 'Improper word usage sentence')}</p>
              </div>
            </div>
          </div>
        `;
        // The two cards already represent the direct user choices for Impostor mechanic
        optionsHtml = '';
      } else if (mechanic.includes('LANDMINE') || mechanic.includes('DEFUSAL')) {
        mechanicExtraHtml = `
          <div class="p-3 bg-rose-50/70 border border-rose-200 rounded-xl space-y-1 text-xs">
            <div class="font-bold text-rose-900 flex items-center gap-1.5">
              <span>💣 Sentence with Grammatical Fault:</span>
              <span class="font-normal font-mono bg-white px-2 py-0.5 rounded border border-rose-300">${escapeHtml(item.landmineSentence || questionText)}</span>
            </div>
            ${item.landmineFaultySegment ? `<div class="text-rose-800"><strong class="font-semibold">Faulty Segment:</strong> <span class="line-through text-rose-700 font-bold">${escapeHtml(item.landmineFaultySegment)}</span></div>` : ''}
            ${item.landmineCorrection ? `<div class="text-emerald-800 font-bold flex items-center gap-1">${uiIcon('check', 'w-3.5 h-3.5 inline text-emerald-600')} Defusal Correction: ${escapeHtml(item.landmineCorrection)}</div>` : ''}
          </div>
        `;
      } else if (mechanic.includes('CONTEXT') || mechanic.includes('DETECTIVE')) {
        if (item.contextSentence && item.contextSentence !== questionText) {
          mechanicExtraHtml = `
            <div class="p-3 bg-blue-50/70 border border-blue-200 rounded-xl space-y-1 text-xs">
              <div class="font-bold text-blue-900 flex items-center gap-1.5">
                <span>🔍 Context Clue Passage:</span>
              </div>
              <p class="text-blue-950 italic">"${escapeHtml(item.contextSentence)}"</p>
              ${item.briefMeaning ? `<div class="text-blue-800 text-[11px]"><strong class="font-semibold">Core Definition:</strong> ${escapeHtml(item.briefMeaning)}</div>` : ''}
            </div>
          `;
        }
      } else if (mechanic.includes('RAPID')) {
        const rawPairs = cleanCorrectAns || cleanUserAns || '';
        const tokens = rawPairs.split(';').map(t => t.trim()).filter(Boolean);
        const parsedPairs = tokens.map(t => {
          const parts = t.split(/➔|->/).map(p => p.trim());
          return { word: parts[0] || '', def: parts[1] || '' };
        }).filter(p => p.word || p.def);

        if (parsedPairs.length > 0) {
          mechanicExtraHtml = `
            <div class="space-y-2 pt-1">
              <div class="flex items-center justify-between text-[10px] font-bold uppercase tracking-wider text-indigo-700">
                <span class="flex items-center gap-1.5">⚡ Speed Vocabulary Match Pairs</span>
                <span class="text-slate-400 font-normal">${parsedPairs.length} Complete Pairs Matched</span>
              </div>
              <div class="space-y-2">
                ${parsedPairs.map(p => `
                  <div class="p-3 bg-indigo-50/60 border border-indigo-200/80 rounded-xl flex flex-col sm:flex-row sm:items-center justify-between gap-2 text-xs">
                    <div class="flex items-center gap-2">
                      <span class="font-bold text-indigo-900 bg-white px-2.5 py-1 rounded-lg border border-indigo-200 shadow-xs font-mono">
                        🎯 ${escapeHtml(p.word)}
                      </span>
                      <span class="text-indigo-400 font-bold text-sm">➔</span>
                      <span class="text-slate-800 font-medium">${escapeHtml(p.def)}</span>
                    </div>
                    <span class="px-2 py-0.5 rounded text-[10px] font-bold bg-emerald-100 text-emerald-800 border border-emerald-200 flex items-center gap-1 self-start sm:self-center">
                      ${uiIcon('check', 'w-3 h-3 inline text-emerald-600')} Matched
                    </span>
                  </div>
                `).join('')}
              </div>
            </div>
          `;
          optionsHtml = ''; // Suppress redundant raw text comparison box
        }
      }

      // Educational Insight / Explanation Pill
      let explanationHtml = '';
      if (explanation) {
        explanationHtml = `
          <div class="p-3 bg-purple-50/80 border border-purple-200 rounded-xl text-xs text-purple-950 flex items-start gap-2">
            <span class="text-base leading-none">💡</span>
            <div>
              <strong class="font-bold text-purple-900">Instructional Insight:</strong>
              <span class="text-purple-900 ml-1">${escapeHtml(explanation)}</span>
            </div>
          </div>
        `;
      }

      return `
        <div class="p-4 bg-white rounded-2xl border ${isCorrect ? 'border-slate-200' : 'border-rose-200 bg-rose-50/10'} shadow-xs space-y-3">
          <div class="flex items-center justify-between">
            <div class="flex items-center gap-2">
              <span class="w-6 h-6 rounded-full bg-slate-100 text-slate-700 font-bold text-xs flex items-center justify-center">Q${qNum}</span>
              ${mechTagHtml}
              ${targetWord ? `<span class="px-2 py-0.5 rounded text-[11px] font-bold bg-indigo-50 text-indigo-700 border border-indigo-200 font-mono">${mechanic.includes('RAPID') ? '⚡ Finale: ' : '🎯 "'}${escapeHtml(targetWord)}${mechanic.includes('RAPID') ? ' + Pairs' : '"'}</span>` : ''}
            </div>
            <span class="px-2 py-0.5 rounded text-[10px] font-bold ${isCorrect ? 'bg-emerald-100 text-emerald-800' : 'bg-rose-100 text-rose-800'}">
              ${isCorrect ? '✅ Correct (+10 XP)' : '❌ Incorrect'}
            </span>
          </div>

          <!-- Question / Context Sentence Prompt -->
          <div class="text-xs font-semibold text-slate-800 leading-relaxed bg-slate-50/80 p-3 rounded-xl border border-slate-200/60">
            ${escapeHtml(questionText)}
          </div>

          ${mechanicExtraHtml}

          ${optionsHtml}

          ${explanationHtml}
        </div>
      `;
    }

    window.closeAttemptInspectorModal = function() {
      document.getElementById('modalAttemptInspector').classList.add('hidden');
      activeInspectorAttempt = null;
    };

    window.exportQuizAttemptsCSV = function() {
      let list = cachedAllAttempts;
      if (selectedQuizTypeFilter !== 'ALL') list = list.filter(a => a.quizType === selectedQuizTypeFilter);
      if (selectedQuizGradeFilter !== 'ALL') {
        const targetNum = selectedQuizGradeFilter.replace('Grade ', '').trim();
        list = list.filter(a => String(a.grade || '').trim() === targetNum || (a.grade || '').trim() === selectedQuizGradeFilter);
      }
      if (quizSearchQuery) {
        list = list.filter(a => {
          const blob = `${a.studentName || ''} ${a.title || ''} ${a.section || ''} ${getSchoolName(a.schoolId)}`;
          return matchSmartSearch(blob, quizSearchQuery);
        });
      }

      const rows = [
        ["Learner Name", "School ID", "School Name", "Grade & Section", "Quiz Category", "Quiz Title", "Score", "Total Items", "Accuracy %", "Mastery Tier", "Submission Timestamp"]
      ];

      list.forEach(a => {
        const pct = a.percentage || 0;
        const tier = pct >= 80 ? "Mastery" : (pct >= 60 ? "Proficient" : "Needs Support");
        rows.push([
          a.studentName || 'Learner',
          a.schoolId || '',
          getSchoolName(a.schoolId),
          `${a.grade || ''} - ${a.section || 'General'}`,
          a.categoryName,
          a.title,
          a.score,
          a.totalQuestions,
          `${pct}%`,
          tier,
          a.timestamp ? new Date(a.timestamp).toISOString() : ''
        ]);
      });

      downloadCSV(`GabAI_Quiz_Attempts_${selectedQuizTypeFilter}_${new Date().toISOString().slice(0, 10)}.csv`, rows);
    };


window.loadAllQuizAttempts = loadAllQuizAttempts;
window.compileAllQuizAttempts = compileAllQuizAttempts;
window.renderQuizCategoryFolders = renderQuizCategoryFolders;
window.renderQuizAttemptsTable = renderQuizAttemptsTable;
window.renderInspectorQuestionCard = renderInspectorQuestionCard;
