    // =========================================================================
    // Helper: Render high-fidelity student avatar (profile photo with monogram fallback & moderation indicators)
    window.renderStudentAvatarHtml = function(photoUrl, pendingPhotoUrl, photoStatus, initials, sizeClass = 'w-9 h-9', textClass = 'text-xs') {
      const safeInitials = escapeHtml(initials || 'ST');
      const effectivePhoto = (photoStatus === 'pending' && pendingPhotoUrl) ? pendingPhotoUrl : (photoUrl || pendingPhotoUrl || '');

      if (effectivePhoto) {
        const ringClass = (photoStatus === 'pending')
          ? 'ring-2 ring-amber-400'
          : (photoStatus === 'approved' ? 'ring-2 ring-emerald-400' : 'ring-1 ring-slate-200');

        return `
          <div class="relative ${sizeClass} rounded-full ${ringClass} overflow-hidden shrink-0 bg-slate-100 flex items-center justify-center shadow-2xs">
            <img src="${escapeHtml(effectivePhoto)}" alt="Student Avatar" class="w-full h-full object-cover" onerror="this.parentElement.innerHTML='<div class=\'w-full h-full bg-indigo-50 text-indigo-700 font-bold ${textClass} flex items-center justify-center\'>${safeInitials}</div>'" />
            ${photoStatus === 'pending' ? '<span class="absolute bottom-0 right-0 w-2.5 h-2.5 rounded-full bg-amber-500 ring-1 ring-white" title="Photo Awaiting Review"></span>' : ''}
          </div>
        `;
      }

      return `
        <div class="${sizeClass} rounded-full bg-indigo-50 border border-indigo-200 text-[#5341CD] font-bold ${textClass} flex items-center justify-center shrink-0 shadow-2xs">
          ${safeInitials}
        </div>
      `;
    };

    // Helper: Safely resolve student's schoolId with class-section fallback
    window.resolveStudentSchoolId = function(student) {
      if (!student) return '';
      const direct = student.schoolId || student.school_id;
      if (direct && String(direct).trim() !== '' && String(direct).trim() !== 'null' && String(direct).trim() !== 'undefined') {
        return String(direct).trim();
      }
      const sec = String(student.section || '').trim().toLowerCase();
      if (sec && Array.isArray(cachedClasses) && cachedClasses.length > 0) {
        const matched = cachedClasses.find(c => {
          const cSec = String(c.section || c.className || c.name || '').trim().toLowerCase();
          return cSec === sec;
        });
        if (matched && matched.schoolId) {
          return String(matched.schoolId).trim();
        }
      }
      return '';
    };

    // =========================================================================
    // 2. LEARNER ACADEMIC PERFORMANCE & PHIL-IRI STANDARDS ENGINE
    // =========================================================================
    var learnerViewMode = window.learnerViewMode || 'roster'; // 'roster' | 'sections'
    var learnerQuickPill = window.learnerQuickPill || 'ALL'; // 'ALL' | 'top_highest' | 'support' | 'independent' | 'instructional' | 'unassessed'
    var learnerSortColumn = window.learnerSortColumn || 'mps'; // 'mps' | 'wpm' | 'streak' | 'level' | 'name'
    var learnerSortAsc = window.learnerSortAsc !== undefined ? window.learnerSortAsc : false; // default false: descending (HIGHEST first!)
    var learnerPage = window.learnerPage || 1;
    var learnerPageSize = window.learnerPageSize || 25;
    var superAdminLearnerView = window.superAdminLearnerView || 'schools'; // 'schools' | 'drilldown'
    var schoolMatrixDistrictFilter = window.schoolMatrixDistrictFilter || 'ALL'; // 'ALL' | 'District 1' | 'District 2'
    var schoolMatrixSearchQuery = window.schoolMatrixSearchQuery || '';
    var schoolMatrixSort = window.schoolMatrixSort || 'highest_mps'; // 'highest_mps' | 'highest_enrolled' | 'highest_wpm' | 'lowest_mps' | 'name_asc'
    var activeInspectStudentId = null;
    var activeProfileTab = 'phil_iri';

    // Helper: Safely resolve student's section and class adviser with class roster cross-referencing
    window.resolveStudentSectionAndAdviser = function(student) {
      if (!student) return { sectionName: 'General', grade: '7', adviserName: 'Homeroom Adviser', joinCode: '—', classId: null };

      let matchedClass = null;
      const sId = String(student.id || '').trim();
      const directSec = String(student.section || '').trim();

      if (Array.isArray(cachedClasses) && cachedClasses.length > 0) {
        matchedClass = cachedClasses.find(c => {
          const joined = c.studentIds || c.joinedStudents || [];
          if (sId && joined.includes(sId)) return true;
          if (directSec && String(c.section || c.className || '').trim().toLowerCase() === directSec.toLowerCase()) {
            if (student.schoolId && c.schoolId) {
              return String(c.schoolId) === String(student.schoolId);
            }
            return true;
          }
          return false;
        });
      }

      const sectionName = matchedClass?.section || matchedClass?.className || student.section || 'General';
      const rawGrade = matchedClass?.grade || student.grade || '7';
      const grade = String(rawGrade).replace(/^Grade\s*/i, '').trim() || '7';
      const joinCode = matchedClass && typeof resolveClassJoinCode === 'function' ? resolveClassJoinCode(matchedClass) : (matchedClass?.joinCode || '—');

      let adviserName = matchedClass?.adviserName || '';
      if (!adviserName && matchedClass) {
        const adviserUser = (cachedUsers || []).find(u => u.id === (matchedClass.teacherId || matchedClass.adviserId));
        if (adviserUser) {
          adviserName = `${adviserUser.firstName || ''} ${adviserUser.lastName || ''}`.trim();
        }
      }
      if (!adviserName) adviserName = 'Homeroom Adviser';

      return {
        sectionName,
        grade,
        adviserName,
        joinCode,
        classId: matchedClass?.id || null
      };
    };

    // Helper: Multi-stream academic profile computation (Timed Assessments, Weekly Tests, Diagnostics, Habits)
    window.computeLearnerAcademicProfile = function(student) {
      if (!student) return null;
      const sId = String(student.id || '').trim();
      const sUid = String(student.uid || '').trim();

      const isMatch = (sub) => {
        if (!sub) return false;
        const subId = String(sub.studentId || sub.studentUid || sub.uid || sub.userId || '').trim();
        return (sId && subId === sId) || (sUid && subId === sUid);
      };

      const timedSubs = (cachedTimedSubmissions || []).filter(isMatch);
      const weeklySubs = (cachedSubmissions || []).filter(isMatch);

      const allScores = [];
      timedSubs.forEach(t => {
        const tot = Number(t.totalQuestions) || (t.answers && t.answers.length) || 5;
        const sc = Number(t.score) || 0;
        const pct = t.percentage !== undefined ? Number(t.percentage) : Math.round((sc / Math.max(tot, 1)) * 100);
        allScores.push(pct);
      });
      weeklySubs.forEach(w => {
        const sc = w.score !== undefined ? Number(w.score) : Number(w.percentage || 0);
        allScores.push(sc);
      });

      let effectiveMps = null;
      if (allScores.length > 0) {
        effectiveMps = Math.round(allScores.reduce((a, b) => a + b, 0) / allScores.length);
      } else if (student.diagnostic_pct !== undefined && student.diagnostic_pct !== null) {
        effectiveMps = Math.round(Number(student.diagnostic_pct));
      } else if (student.diagnostic_score !== undefined && student.diagnostic_score !== null) {
        const tot = Number(student.diagnostic_total) || 10;
        effectiveMps = Math.round((Number(student.diagnostic_score) / Math.max(tot, 1)) * 100);
      }

      // Measured Words Per Minute (WPM)
      let wpm = null;
      for (const t of timedSubs) {
        const measured = t.wpm || t.readingSpeedWpm || t.reading_speed || t.wordsPerMinute;
        if (measured) {
          wpm = Math.round(Number(measured));
          break;
        }
      }
      if (!wpm && student.phil_iri_wpm) {
        wpm = Math.round(Number(student.phil_iri_wpm));
      }

      // Phil-IRI Reading Tier (DepEd Order No. 14, s. 2018)
      const rawTier = String(student.phil_iri_tier || student.diagnostic_tier || '').toLowerCase().trim();
      let tier = 'unassessed';
      let tierLabel = 'Unassessed';
      let tierClass = 'bg-slate-100 text-slate-700 border-slate-200';

      if (effectiveMps !== null) {
        if (effectiveMps >= 80) {
          tier = 'independent';
          tierLabel = 'Independent';
          tierClass = 'bg-emerald-50 text-emerald-800 border-emerald-200 font-bold';
        } else if (effectiveMps >= 60) {
          tier = 'instructional';
          tierLabel = 'Instructional';
          tierClass = 'bg-amber-50 text-amber-800 border-amber-200 font-bold';
        } else {
          tier = 'frustration';
          tierLabel = 'Frustration';
          tierClass = 'bg-rose-50 text-rose-800 border-rose-200 font-bold';
        }
      } else if (rawTier) {
        if (rawTier.includes('independent')) {
          tier = 'independent';
          tierLabel = 'Independent';
          tierClass = 'bg-emerald-50 text-emerald-800 border-emerald-200 font-bold';
        } else if (rawTier.includes('instructional')) {
          tier = 'instructional';
          tierLabel = 'Instructional';
          tierClass = 'bg-amber-50 text-amber-800 border-amber-200 font-bold';
        } else if (rawTier.includes('frustration')) {
          tier = 'frustration';
          tierLabel = 'Frustration';
          tierClass = 'bg-rose-50 text-rose-800 border-rose-200 font-bold';
        }
      }

      // Reading Trajectory Trend
      let trend = 'stable';
      let trendLabel = '➔ Stable';
      let trendClass = 'bg-slate-100 text-slate-600 border-slate-200';
      if (allScores.length >= 2) {
        const first = allScores[0];
        const last = allScores[allScores.length - 1];
        const delta = last - first;
        if (delta >= 5) {
          trend = 'improving';
          trendLabel = `↗ +${delta}% Growth`;
          trendClass = 'bg-emerald-50 text-emerald-700 border-emerald-200 font-bold';
        } else if (delta <= -5) {
          trend = 'support';
          trendLabel = `↘ ${delta}% Support`;
          trendClass = 'bg-rose-50 text-rose-700 border-rose-200 font-bold';
        }
      }

      const totalQuizzes = timedSubs.length + weeklySubs.length;
      const wordsMastered = student.words_mastered || student.wordsMastered || 0;

      return {
        effectiveMps,
        tier,
        tierLabel,
        tierClass,
        wpm,
        trend,
        trendLabel,
        trendClass,
        totalQuizzes,
        timedSubs,
        weeklySubs,
        wordsMastered
      };
    };

    // Helper: Populate Section Filter with live student counts across classes and users
    window.populateLearnerSectionDropdown = function() {
      const select = document.getElementById('selectLearnerSectionFilter');
      if (!select) return;

      const currentVal = learnerSectionFilter || 'ALL';
      let students = (cachedUsers || []).filter(u => u.role === 'student');

      if (isSchoolAdmin && currentAdminUser?.schoolId) {
        students = students.filter(s => (resolveStudentSchoolId(s) || String(s.schoolId || '').trim()) === String(currentAdminUser.schoolId));
      } else if (learnerSchoolFilterVal && learnerSchoolFilterVal !== 'ALL') {
        students = students.filter(s => (resolveStudentSchoolId(s) || String(s.schoolId || '').trim()) === String(learnerSchoolFilterVal));
      } else if (selectedJurisdiction !== 'ALL') {
        students = students.filter(s => (resolveStudentSchoolId(s) || String(s.schoolId || '').trim()) === String(selectedJurisdiction));
      }

      const activeGrade = learnerGradeFilter || document.getElementById('selectLearnerGradeFilter')?.value || 'ALL';
      if (activeGrade !== 'ALL') {
        const targetGradeNum = activeGrade.replace('Grade ', '').trim();
        students = students.filter(s => String(s.grade || '').trim() === targetGradeNum || (s.grade || '').trim() === activeGrade || `Grade ${s.grade}` === activeGrade);
      }

      // Collect section names & counts
      const sectionCountMap = new Map();
      students.forEach(s => {
        const secInfo = resolveStudentSectionAndAdviser(s);
        const secName = secInfo.sectionName || 'General';
        const gradeText = secInfo.grade ? `Grade ${secInfo.grade}` : 'General';
        const key = secName;
        if (!sectionCountMap.has(key)) {
          sectionCountMap.set(key, { count: 0, grade: gradeText });
        }
        sectionCountMap.get(key).count++;
      });

      // Also ensure classes in cachedClasses are represented
      let classes = cachedClasses || [];
      if (isSchoolAdmin && currentAdminUser?.schoolId) {
        classes = classes.filter(c => String(c.schoolId) === String(currentAdminUser.schoolId));
      } else if (learnerSchoolFilterVal && learnerSchoolFilterVal !== 'ALL') {
        classes = classes.filter(c => String(c.schoolId) === String(learnerSchoolFilterVal));
      } else if (selectedJurisdiction !== 'ALL') {
        classes = classes.filter(c => String(c.schoolId) === String(selectedJurisdiction));
      }
      classes.forEach(c => {
        const sec = (c.section || c.className || '').trim();
        if (sec && !sectionCountMap.has(sec)) {
          sectionCountMap.set(sec, { count: 0, grade: c.grade ? `Grade ${c.grade}` : 'General' });
        }
      });

      const sortedSections = Array.from(sectionCountMap.keys()).sort((a, b) => a.localeCompare(b));

      let optionsHtml = `<option value="ALL">All Sections (${students.length} learners)</option>`;
      sortedSections.forEach(sec => {
        const data = sectionCountMap.get(sec);
        const isSelected = (sec.toLowerCase() === currentVal.toLowerCase()) ? 'selected' : '';
        optionsHtml += `<option value="${escapeHtml(sec)}" ${isSelected}>${escapeHtml(sec)} (${data.count} learners • ${data.grade})</option>`;
      });

      select.innerHTML = optionsHtml;
    };

    window.handleLearnerSectionFilter = function(val) {
      learnerSectionFilter = val || 'ALL';
      learnerPage = 1;
      renderPerformanceTable();
    };

    // View Switcher: Individual Learners Roster vs Section Performance Matrix
    window.setLearnerViewMode = function(mode) {
      learnerViewMode = mode || 'roster';
      const btnRoster = document.getElementById('btnViewLearnerRoster');
      const btnMatrix = document.getElementById('btnViewSectionMatrix');
      const tableContainer = document.getElementById('tourTargetPerformanceTable');
      const matrixContainer = document.getElementById('sectionCardsContainer');

      if (btnRoster && btnMatrix) {
        if (learnerViewMode === 'roster') {
          btnRoster.className = 'px-3 py-1.5 rounded-lg text-xs font-bold transition-all bg-white text-indigo-700 shadow-2xs';
          btnMatrix.className = 'px-3 py-1.5 rounded-lg text-xs font-semibold text-slate-600 hover:text-slate-900 transition-all';
        } else {
          btnMatrix.className = 'px-3 py-1.5 rounded-lg text-xs font-bold transition-all bg-white text-indigo-700 shadow-2xs';
          btnRoster.className = 'px-3 py-1.5 rounded-lg text-xs font-semibold text-slate-600 hover:text-slate-900 transition-all';
        }
      }

      if (tableContainer) tableContainer.classList.toggle('hidden', learnerViewMode !== 'roster');
      if (matrixContainer) matrixContainer.classList.toggle('hidden', learnerViewMode !== 'sections');

      renderPerformanceTable();
    };

    // Helper: Render Section Performance Matrix Bento Cards
    window.renderSectionPerformanceCards = function(students) {
      const container = document.getElementById('sectionCardsContainer');
      if (!container) return;

      let classes = cachedClasses || [];
      if (isSchoolAdmin && currentAdminUser?.schoolId) {
        classes = classes.filter(c => String(c.schoolId) === String(currentAdminUser.schoolId));
      } else if (learnerSchoolFilterVal && learnerSchoolFilterVal !== 'ALL') {
        classes = classes.filter(c => String(c.schoolId) === String(learnerSchoolFilterVal));
      } else if (selectedJurisdiction !== 'ALL') {
        classes = classes.filter(c => String(c.schoolId) === String(selectedJurisdiction));
      }

      const activeGrade = learnerGradeFilter || document.getElementById('selectLearnerGradeFilter')?.value || 'ALL';
      if (activeGrade !== 'ALL') {
        const targetGradeNum = activeGrade.replace('Grade ', '').trim();
        classes = classes.filter(c => String(c.grade || '').trim() === targetGradeNum || (c.grade || '').trim() === activeGrade || `Grade ${c.grade}` === activeGrade);
      }

      if (classes.length === 0) {
        container.innerHTML = `
          <div class="col-span-full p-8 text-center bg-white rounded-2xl border border-slate-200/80 text-slate-400 font-medium">
            No classroom sections found matching the active jurisdiction/filters.
          </div>`;
        return;
      }

      container.innerHTML = classes.map(c => {
        const secName = c.section || c.className || 'Section';
        const joinCode = typeof resolveClassJoinCode === 'function' ? resolveClassJoinCode(c) : (c.joinCode || '—');
        const gradeText = c.grade ? (String(c.grade).startsWith('Grade') ? c.grade : `Grade ${c.grade}`) : 'Grade 7';
        const schoolName = getSchoolName(c.schoolId);

        let adviser = (cachedUsers || []).find(u => u.id === (c.teacherId || c.adviserId));
        const adviserName = adviser ? `${adviser.firstName || ''} ${adviser.lastName || ''}`.trim() : (c.adviserName || 'Class Adviser');

        const joinedIds = c.studentIds || c.joinedStudents || [];
        const enrolled = students.filter(s => joinedIds.includes(s.id) || (s.section === secName && String(s.schoolId) === String(c.schoolId)));
        const pending = (cachedPendingStudents || []).filter(p => p.section === secName && String(p.schoolId) === String(c.schoolId));

        let secScoreSum = 0;
        let secScoreCount = 0;
        let secWpmSum = 0;
        let secWpmCount = 0;
        let indCount = 0;
        let insCount = 0;
        let fruCount = 0;

        enrolled.forEach(st => {
          const profile = computeLearnerAcademicProfile(st);
          if (profile.effectiveMps !== null) {
            secScoreSum += profile.effectiveMps;
            secScoreCount++;
          }
          if (profile.wpm) {
            secWpmSum += profile.wpm;
            secWpmCount++;
          }
          if (profile.tier === 'independent') indCount++;
          else if (profile.tier === 'instructional') insCount++;
          else if (profile.tier === 'frustration') fruCount++;
        });

        const sectionMps = secScoreCount > 0 ? Math.round(secScoreSum / secScoreCount) : null;
        const avgWpm = secWpmCount > 0 ? Math.round(secWpmSum / secWpmCount) : null;
        const totalSectionStudents = enrolled.length + pending.length;

        const mpsColor = sectionMps !== null ? (sectionMps >= 80 ? 'text-emerald-700' : (sectionMps >= 60 ? 'text-amber-700' : 'text-rose-700')) : 'text-slate-400';
        const mpsBarBg = sectionMps !== null ? (sectionMps >= 80 ? 'bg-emerald-500' : (sectionMps >= 60 ? 'bg-amber-500' : 'bg-rose-500')) : 'bg-slate-200';

        return `
          <div class="bg-white p-5 rounded-3xl border border-slate-200/80 shadow-xs hover:shadow-md transition-all space-y-4">
            <div class="flex items-start justify-between gap-3">
              <div>
                <div class="flex items-center gap-2">
                  <span class="px-2.5 py-0.5 rounded-full text-[10px] font-bold uppercase tracking-wider bg-indigo-50 text-indigo-700 border border-indigo-200">${escapeHtml(gradeText)}</span>
                  <span class="font-mono text-xs font-bold text-slate-500">#${escapeHtml(joinCode)}</span>
                </div>
                <h4 class="font-bold text-base text-slate-900 mt-1 font-display">${escapeHtml(secName)}</h4>
                <p class="text-xs text-slate-500">${escapeHtml(adviserName)} &bull; ${escapeHtml(schoolName)}</p>
              </div>
              <div class="text-right">
                <div class="text-xs text-slate-400 font-semibold">Section MPS</div>
                <div class="text-2xl font-black ${mpsColor} font-display">${sectionMps !== null ? sectionMps + '%' : '—'}</div>
              </div>
            </div>

            <!-- Section MPS Progress Bar -->
            <div class="w-full h-2 rounded-full bg-slate-100 overflow-hidden">
              <div class="${mpsBarBg} h-full rounded-full transition-all" style="width: ${sectionMps !== null ? Math.min(100, Math.max(5, sectionMps)) : 0}%"></div>
            </div>

            <!-- Mini Tier Spread & Telemetry -->
            <div class="grid grid-cols-3 gap-2 text-center text-xs pt-1 border-t border-slate-100">
              <div class="p-2 bg-slate-50 rounded-xl">
                <div class="text-[10px] font-bold text-slate-400 uppercase">Enrolled</div>
                <div class="font-bold text-slate-800 text-sm mt-0.5">${totalSectionStudents}</div>
                <div class="text-[9px] text-slate-400 font-medium">${enrolled.length} active</div>
              </div>
              <div class="p-2 bg-slate-50 rounded-xl">
                <div class="text-[10px] font-bold text-slate-400 uppercase">Reading Speed</div>
                <div class="font-bold text-blue-600 text-sm mt-0.5">${avgWpm ? avgWpm + ' WPM' : '—'}</div>
                <div class="text-[9px] text-slate-400 font-medium">Cohort WPM</div>
              </div>
              <div class="p-2 bg-slate-50 rounded-xl">
                <div class="text-[10px] font-bold text-slate-400 uppercase">Tier Ratio</div>
                <div class="font-bold text-slate-800 text-xs mt-1">
                  <span class="text-emerald-600">${indCount}</span> / <span class="text-amber-600">${insCount}</span> / <span class="text-rose-600">${fruCount}</span>
                </div>
                <div class="text-[9px] text-slate-400 font-medium">Ind / Ins / Fru</div>
              </div>
            </div>

            <div class="pt-2 flex items-center justify-between border-t border-slate-100">
              <span class="text-[11px] text-slate-400 font-medium">Valenzuela JHS Roster</span>
              <button type="button" onclick="filterToSection('${escapeHtml(secName)}')" class="px-3.5 py-1.5 bg-indigo-50 hover:bg-indigo-100 text-indigo-700 font-bold rounded-xl text-xs transition shadow-2xs flex items-center gap-1.5">
                <span>Inspect Roster &rarr;</span>
              </button>
            </div>
          </div>
        `;
      }).join('');
    };

    window.filterToSection = function(sectionName) {
      learnerSectionFilter = sectionName || 'ALL';
      const sel = document.getElementById('selectLearnerSectionFilter');
      if (sel) sel.value = sectionName;
      setLearnerViewMode('roster');
    };

    // Sorting Handlers
    window.handleLearnerSortChange = function(sortVal) {
      if (sortVal === 'highest_mps') {
        learnerSortColumn = 'mps';
        learnerSortAsc = false;
      } else if (sortVal === 'highest_wpm') {
        learnerSortColumn = 'wpm';
        learnerSortAsc = false;
      } else if (sortVal === 'highest_streak') {
        learnerSortColumn = 'streak';
        learnerSortAsc = false;
      } else if (sortVal === 'highest_xp') {
        learnerSortColumn = 'level';
        learnerSortAsc = false;
      } else if (sortVal === 'lowest_mps') {
        learnerSortColumn = 'mps';
        learnerSortAsc = true;
      } else if (sortVal === 'name_asc') {
        learnerSortColumn = 'name';
        learnerSortAsc = true;
      } else if (sortVal === 'name_desc') {
        learnerSortColumn = 'name';
        learnerSortAsc = false;
      }
      learnerPage = 1;
      updateLearnerSortIndicators();
      renderPerformanceTable();
    };

    window.toggleLearnerHeaderSort = function(col) {
      if (learnerSortColumn === col) {
        learnerSortAsc = !learnerSortAsc;
      } else {
        learnerSortColumn = col;
        learnerSortAsc = (col === 'name'); // default name to asc, numeric to desc (HIGHEST first)
      }
      learnerPage = 1;

      // Sync dropdown value
      const sel = document.getElementById('selectLearnerSort');
      if (sel) {
        if (col === 'mps') sel.value = learnerSortAsc ? 'lowest_mps' : 'highest_mps';
        else if (col === 'wpm') sel.value = 'highest_wpm';
        else if (col === 'streak') sel.value = 'highest_streak';
        else if (col === 'level') sel.value = 'highest_xp';
        else if (col === 'name') sel.value = learnerSortAsc ? 'name_asc' : 'name_desc';
      }

      updateLearnerSortIndicators();
      renderPerformanceTable();
    };

    function updateLearnerSortIndicators() {
      const cols = ['name', 'mps', 'wpm', 'streak', 'level'];
      cols.forEach(c => {
        const el = document.getElementById(`sortIndicator_${c}`);
        if (!el) return;
        if (learnerSortColumn === c) {
          el.textContent = learnerSortAsc ? '▲' : '▼';
          el.className = 'text-[10px] text-indigo-600 font-bold font-mono';
        } else {
          el.textContent = '⬍';
          el.className = 'text-[10px] text-slate-400 font-mono';
        }
      });
    }

    // Quick Filter Pills Handler (All, Highest Performers, Priority Support, etc.)
    window.handleLearnerQuickPill = function(pillVal) {
      learnerQuickPill = pillVal || 'ALL';

      // Update button visual states
      const pills = [
        { id: 'pillLearnerAll', val: 'ALL' },
        { id: 'pillLearnerTopHighest', val: 'top_highest' },
        { id: 'pillLearnerPrioritySupport', val: 'support' },
        { id: 'pillLearnerInstructional', val: 'instructional' },
        { id: 'pillLearnerUnassessed', val: 'unassessed' }
      ];

      pills.forEach(p => {
        const btn = document.getElementById(p.id);
        if (!btn) return;
        if (p.val === learnerQuickPill) {
          btn.className = 'px-2.5 py-1 rounded-lg font-bold text-xs bg-indigo-600 text-white shadow-2xs transition-all flex items-center gap-1';
        } else {
          btn.className = 'px-2.5 py-1 rounded-lg font-bold text-xs bg-slate-100 text-slate-700 border border-slate-200 hover:bg-slate-200 shadow-2xs transition-all flex items-center gap-1';
        }
      });

      const tierSelect = document.getElementById('selectLearnerTierFilter');
      if (pillVal === 'top_highest') {
        learnerTierFilter = 'independent';
        if (tierSelect) tierSelect.value = 'independent';
        handleLearnerSortChange('highest_mps');
        return;
      } else if (pillVal === 'support') {
        learnerTierFilter = 'frustration';
        if (tierSelect) tierSelect.value = 'frustration';
        handleLearnerSortChange('lowest_mps');
        return;
      } else if (pillVal === 'ALL') {
        learnerTierFilter = 'ALL';
        if (tierSelect) tierSelect.value = 'ALL';
      } else {
        learnerTierFilter = pillVal;
        if (tierSelect) tierSelect.value = pillVal;
      }

      learnerPage = 1;
      renderPerformanceTable();
    };

    // Pagination Handlers
    window.handleLearnerPageSizeChange = function(val) {
      learnerPageSize = parseInt(val, 10) || 25;
      learnerPage = 1;
      renderPerformanceTable();
    };

    window.changeLearnerPage = function(delta) {
      learnerPage += delta;
      renderPerformanceTable();
    };

    window.goToLearnerPage = function(pageNum) {
      learnerPage = pageNum;
      renderPerformanceTable();
    };

    // =========================================================================
    // 2B. VALENZUELA SECONDARY SCHOOLS LITERACY MATRIX & HIERARCHY ENGINE
    // =========================================================================

    // Helper: Compute high-fidelity academic reading profile for a secondary campus
    window.computeSchoolAcademicStats = function(schoolId) {
      const sId = String(schoolId || '').trim();
      const schoolObj = (VALENZUELA_SCHOOLS || []).find(s => s.id === sId) || {
        id: sId,
        name: typeof getSchoolName === 'function' ? getSchoolName(sId) : `School ${sId}`,
        district: 'Division'
      };

      // Claimed active learners and pending credential accounts
      const claimedStudents = (cachedUsers || []).filter(u => {
        if (u.role !== 'student') return false;
        const uSchool = resolveStudentSchoolId(u) || String(u.schoolId || '').trim();
        return uSchool === sId;
      });
      const pendingStudents = (cachedPendingStudents || []).filter(s => String(s.schoolId || '').trim() === sId);
      const totalEnrolled = claimedStudents.length + pendingStudents.length;

      // Classes and Teachers
      const classes = (cachedClasses || []).filter(c => String(c.schoolId || '').trim() === sId);
      const teachers = (cachedTeachers || []).filter(t => {
        const tSchool = (typeof resolveTeacherSchoolId === 'function' ? resolveTeacherSchoolId(t) : t.schoolId) || '';
        return String(tSchool).trim() === sId;
      });

      // Compute student-level academic metrics for claimed cohort
      let countEval = 0;
      let countInd = 0;
      let countIns = 0;
      let countFru = 0;
      let countUnass = 0;
      let totalMpsSum = 0;
      let totalWpmSum = 0;
      let totalWpmCount = 0;
      let totalStreak = 0;

      claimedStudents.forEach(st => {
        const prof = computeLearnerAcademicProfile(st);
        if (prof.totalQuizzes > 0 || prof.effectiveMps !== null) countEval++;
        if (prof.tier === 'independent') countInd++;
        else if (prof.tier === 'instructional') countIns++;
        else if (prof.tier === 'frustration') countFru++;
        else countUnass++;

        if (prof.effectiveMps !== null) {
          totalMpsSum += prof.effectiveMps;
        }
        if (prof.wpm) {
          totalWpmSum += prof.wpm;
          totalWpmCount++;
        }
        totalStreak += (st.current_streak || 0);
      });

      // Add unassessed count for pending students (who haven't claimed accounts yet)
      countUnass += pendingStudents.length;

      const schoolAvgMps = countEval > 0 ? Math.round(totalMpsSum / countEval) : 0;
      const schoolAvgWpm = totalWpmCount > 0 ? Math.round(totalWpmSum / totalWpmCount) : null;
      const avgStreak = claimedStudents.length > 0 ? Math.round(totalStreak / claimedStudents.length) : 0;
      const coveragePct = totalEnrolled > 0 ? Math.round((countEval / totalEnrolled) * 100) : 0;

      // Tier Percentages relative to total enrolled
      const indPct = totalEnrolled > 0 ? Math.round((countInd / totalEnrolled) * 100) : 0;
      const insPct = totalEnrolled > 0 ? Math.round((countIns / totalEnrolled) * 100) : 0;
      const fruPct = totalEnrolled > 0 ? Math.round((countFru / totalEnrolled) * 100) : 0;
      const unassPct = totalEnrolled > 0 ? Math.max(0, 100 - (indPct + insPct + fruPct)) : 0;

      // Phil-IRI Benchmark Designation
      let benchmarkLabel = 'Awaiting Assessment';
      let benchmarkBadgeClass = 'bg-slate-100 text-slate-700 border-slate-200';
      if (countEval > 0) {
        if (schoolAvgMps >= 80) {
          benchmarkLabel = 'Independent Campus';
          benchmarkBadgeClass = 'bg-emerald-50 text-emerald-800 border-emerald-200 font-bold';
        } else if (schoolAvgMps >= 60) {
          benchmarkLabel = 'Instructional Campus';
          benchmarkBadgeClass = 'bg-amber-50 text-amber-800 border-amber-200 font-bold';
        } else {
          benchmarkLabel = 'Priority Support Campus';
          benchmarkBadgeClass = 'bg-rose-50 text-rose-800 border-rose-200 font-bold';
        }
      }

      return {
        school: schoolObj,
        schoolId: sId,
        schoolName: schoolObj.name,
        district: schoolObj.district,
        claimedStudentsCount: claimedStudents.length,
        pendingStudentsCount: pendingStudents.length,
        totalEnrolled,
        classesCount: classes.length,
        teachersCount: teachers.length,
        countEval,
        countInd,
        countIns,
        countFru,
        countUnass,
        indPct,
        insPct,
        fruPct,
        unassPct,
        schoolAvgMps,
        schoolAvgWpm,
        avgStreak,
        coveragePct,
        benchmarkLabel,
        benchmarkBadgeClass
      };
    };

    // Helper: Render Division 25-Secondary Schools Literacy Overview Bento Grid
    window.renderLearnerSchoolsMatrix = function() {
      const container = document.getElementById('learnerSchoolCardsGrid');
      if (!container) return;

      let list = [...(VALENZUELA_SCHOOLS || [])];

      // District Filter
      if (schoolMatrixDistrictFilter !== 'ALL') {
        list = list.filter(s => s.district === schoolMatrixDistrictFilter);
      }

      // Smart Search Filter
      if (schoolMatrixSearchQuery) {
        list = list.filter(s => {
          const blob = `${s.name || ''} ${s.id || ''} ${s.district || ''}`.toLowerCase();
          return blob.includes(schoolMatrixSearchQuery);
        });
      }

      // Update District Filter Button Styles
      const btnAll = document.getElementById('btnSchoolDistrictAll');
      const btnD1 = document.getElementById('btnSchoolDistrict1');
      const btnD2 = document.getElementById('btnSchoolDistrict2');
      if (btnAll && btnD1 && btnD2) {
        const activeClass = 'px-3 py-1 rounded-lg font-bold text-xs bg-indigo-600 text-white shadow-2xs transition-all';
        const inactiveClass = 'px-3 py-1 rounded-lg font-bold text-xs bg-slate-100 text-slate-700 border border-slate-200 hover:bg-slate-200 shadow-2xs transition-all flex items-center gap-1';
        btnAll.className = (schoolMatrixDistrictFilter === 'ALL') ? activeClass : inactiveClass;
        btnD1.className = (schoolMatrixDistrictFilter === 'District 1') ? activeClass : inactiveClass;
        btnD2.className = (schoolMatrixDistrictFilter === 'District 2') ? activeClass : inactiveClass;
      }

      // Update Count Badge
      const countBadge = document.getElementById('schoolMatrixCountBadge');
      if (countBadge) {
        countBadge.textContent = `Showing ${list.length} secondary campus${list.length === 1 ? '' : 'es'}`;
      }

      if (list.length === 0) {
        container.innerHTML = `
          <div class="col-span-full p-8 text-center bg-white rounded-2xl border border-slate-200/80 text-slate-400 font-medium">
            <svg class="w-8 h-8 mx-auto mb-2 text-slate-300" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
            No secondary schools match the active search or district filter.
            <button onclick="clearSchoolMatrixSearch(); setLearnerSchoolDistrictFilter('ALL');" class="block mx-auto mt-2 text-indigo-600 hover:underline font-bold text-xs">Reset filters</button>
          </div>
        `;
        return;
      }

      // Compute stats for each school
      const schoolStats = list.map(school => computeSchoolAcademicStats(school.id));

      // Multi-criteria Sort
      schoolStats.sort((a, b) => {
        if (schoolMatrixSort === 'highest_mps') {
          if (b.schoolAvgMps !== a.schoolAvgMps) return b.schoolAvgMps - a.schoolAvgMps;
          return b.countEval - a.countEval;
        } else if (schoolMatrixSort === 'highest_enrolled') {
          return b.totalEnrolled - a.totalEnrolled;
        } else if (schoolMatrixSort === 'highest_wpm') {
          return (b.schoolAvgWpm || 0) - (a.schoolAvgWpm || 0);
        } else if (schoolMatrixSort === 'lowest_mps') {
          return a.schoolAvgMps - b.schoolAvgMps;
        } else if (schoolMatrixSort === 'name_asc') {
          return a.schoolName.localeCompare(b.schoolName);
        }
        return 0;
      });

      // Render Bento Cards
      container.innerHTML = schoolStats.map((item, idx) => {
        const isD1 = item.district === 'District 1';
        const districtBadgeClass = isD1 ? 'bg-indigo-50 text-indigo-700 border-indigo-200' : 'bg-purple-50 text-purple-700 border-purple-200';
        const rankMedal = idx === 0 && schoolMatrixSort === 'highest_mps' && item.schoolAvgMps > 0 ? '🥇 ' : (idx === 1 && schoolMatrixSort === 'highest_mps' && item.schoolAvgMps > 0 ? '🥈 ' : (idx === 2 && schoolMatrixSort === 'highest_mps' && item.schoolAvgMps > 0 ? '🥉 ' : ''));

        return `
          <div class="bg-white p-5 rounded-2xl border border-slate-200/80 shadow-xs hover:shadow-md hover:border-indigo-300 transition-all flex flex-col justify-between group">
            <div class="space-y-3.5">
              <!-- Top Row: Name, District Badge, ID -->
              <div class="flex items-start justify-between gap-2">
                <div class="min-w-0 flex-1">
                  <div class="flex items-center gap-1.5 flex-wrap">
                    <span class="px-2 py-0.5 rounded-full text-[10px] font-bold border ${districtBadgeClass}">
                      ${escapeHtml(item.district)}
                    </span>
                    <span class="text-[10px] font-mono text-slate-400">ID: ${escapeHtml(item.schoolId)}</span>
                  </div>
                  <h4 class="font-bold text-sm text-slate-900 group-hover:text-indigo-600 transition-colors mt-1 font-display leading-snug">
                    ${rankMedal}${escapeHtml(item.schoolName)}
                  </h4>
                </div>
              </div>

              <!-- Primary Metric Banner: Literacy MPS & DepEd Tier -->
              <div class="p-3 bg-slate-50/80 rounded-xl border border-slate-100 flex items-center justify-between">
                <div>
                  <div class="text-[10px] uppercase tracking-wider font-bold text-slate-400">Literacy MPS</div>
                  <div class="text-2xl font-black font-display ${item.schoolAvgMps >= 80 ? 'text-emerald-600' : (item.schoolAvgMps >= 60 ? 'text-amber-600' : (item.countEval > 0 ? 'text-rose-600' : 'text-slate-400'))}">
                    ${item.schoolAvgMps}%
                  </div>
                </div>
                <div class="text-right space-y-1">
                  <span class="px-2.5 py-0.5 rounded-full text-[10px] border ${item.benchmarkBadgeClass}">
                    ${item.benchmarkLabel}
                  </span>
                  <div class="text-[10px] text-slate-400 font-medium">
                    ${item.countEval}/${item.totalEnrolled} evaluated (${item.coveragePct}%)
                  </div>
                </div>
              </div>

              <!-- Multi-segment Mini Ratio Bar -->
              <div class="space-y-1">
                <div class="w-full h-2 rounded-full bg-slate-100 flex overflow-hidden gap-0.5 shadow-2xs">
                  <div class="h-full bg-emerald-500 rounded-l-full" style="width: ${item.indPct}%" title="Independent: ${item.countInd} (${item.indPct}%)"></div>
                  <div class="h-full bg-amber-500" style="width: ${item.insPct}%" title="Instructional: ${item.countIns} (${item.insPct}%)"></div>
                  <div class="h-full bg-rose-500" style="width: ${item.fruPct}%" title="Priority Support: ${item.countFru} (${item.fruPct}%)"></div>
                  <div class="h-full bg-slate-300 rounded-r-full" style="width: ${item.unassPct}%" title="Unassessed: ${item.countUnass} (${item.unassPct}%)"></div>
                </div>
                <div class="flex items-center justify-between text-[10px] text-slate-500 font-medium pt-0.5">
                  <span class="text-emerald-700 font-bold">🟢 ${item.countInd} Ind</span>
                  <span class="text-amber-700 font-bold">🟡 ${item.countIns} Ins</span>
                  <span class="text-rose-700 font-bold">🔴 ${item.countFru} Sup</span>
                  <span class="text-slate-400">⚪ ${item.countUnass} Unass</span>
                </div>
              </div>

              <!-- Micro Bento Metrics: 4 Grid Tiles -->
              <div class="grid grid-cols-2 gap-2 text-xs pt-1">
                <div class="p-2 rounded-xl bg-slate-50 border border-slate-100">
                  <div class="text-[10px] text-slate-400">Enrolled Scholars</div>
                  <div class="font-bold text-slate-800 font-display">${item.totalEnrolled} <span class="text-[10px] font-normal text-slate-400">(${item.claimedStudentsCount} active)</span></div>
                </div>
                <div class="p-2 rounded-xl bg-slate-50 border border-slate-100">
                  <div class="text-[10px] text-slate-400">Reading Speed</div>
                  <div class="font-bold text-blue-600 font-display">${item.schoolAvgWpm ? item.schoolAvgWpm + ' WPM' : '— WPM'}</div>
                </div>
                <div class="p-2 rounded-xl bg-slate-50 border border-slate-100">
                  <div class="text-[10px] text-slate-400">Class Sections</div>
                  <div class="font-bold text-slate-800 font-display">${item.classesCount} classrooms</div>
                </div>
                <div class="p-2 rounded-xl bg-slate-50 border border-slate-100">
                  <div class="text-[10px] text-slate-400">Faculty Members</div>
                  <div class="font-bold text-slate-800 font-display">${item.teachersCount} educators</div>
                </div>
              </div>
            </div>

            <!-- Drilldown Action Button -->
            <div class="pt-4 border-t border-slate-100 mt-4">
              <button type="button" onclick="drilldownToSchool('${escapeHtml(item.schoolId)}')" class="w-full py-2 px-3 bg-gradient-to-r from-indigo-50 to-purple-50 hover:from-indigo-100 hover:to-purple-100 text-indigo-700 border border-indigo-200/80 rounded-xl text-xs font-bold transition-all shadow-2xs hover:shadow-xs flex items-center justify-center gap-1.5 font-display">
                <span>Inspect School Performance &amp; Roster</span>
                <svg class="w-3.5 h-3.5 group-hover:translate-x-0.5 transition-transform" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M5 12h14"/><path d="m12 5 7 7-7 7"/></svg>
              </button>
            </div>
          </div>
        `;
      }).join('');
    };

    // Drilldown to specific school workspace
    window.drilldownToSchool = function(schoolId) {
      if (!schoolId) return;
      selectedJurisdiction = String(schoolId).trim();
      superAdminLearnerView = 'drilldown';
      learnerSchoolFilterVal = selectedJurisdiction;
      learnerSectionFilter = 'ALL';
      learnerPage = 1;

      // Sync select dropdowns across the portal
      const schoolSelect = document.getElementById('learnerSchoolFilter');
      if (schoolSelect) schoolSelect.value = selectedJurisdiction;
      const jurSelect = document.getElementById('jurisdictionSelect');
      if (jurSelect) jurSelect.value = selectedJurisdiction;
      const globalFilter = document.getElementById('globalSchoolFilter');
      if (globalFilter) globalFilter.value = selectedJurisdiction;

      renderPerformanceTable();
      if (typeof window.showToast === 'function') {
        window.showToast(`Drilling down into ${getSchoolName(selectedJurisdiction)}`, 'info');
      }
    };

    // Return back to 25-schools overview matrix
    window.backToAllSchools = function() {
      selectedJurisdiction = 'ALL';
      superAdminLearnerView = 'schools';
      learnerSchoolFilterVal = 'ALL';
      learnerSectionFilter = 'ALL';
      learnerPage = 1;

      // Sync select dropdowns across the portal
      const schoolSelect = document.getElementById('learnerSchoolFilter');
      if (schoolSelect) schoolSelect.value = 'ALL';
      const jurSelect = document.getElementById('jurisdictionSelect');
      if (jurSelect) jurSelect.value = 'ALL';
      const globalFilter = document.getElementById('globalSchoolFilter');
      if (globalFilter) globalFilter.value = 'ALL';

      renderPerformanceTable();
      if (typeof window.showToast === 'function') {
        window.showToast('Returned to Valenzuela Schools Overview', 'info');
      }
    };

    // School Matrix Search & District Controls
    window.setLearnerSchoolDistrictFilter = function(dist) {
      schoolMatrixDistrictFilter = dist || 'ALL';
      renderLearnerSchoolsMatrix();
    };

    window.handleSchoolMatrixSortChange = function(sortVal) {
      schoolMatrixSort = sortVal || 'highest_mps';
      renderLearnerSchoolsMatrix();
    };

    window.handleSchoolMatrixSearch = function(query) {
      schoolMatrixSearchQuery = (query || '').toLowerCase().trim();
      const btnClear = document.getElementById('btnClearSchoolMatrixSearch');
      if (btnClear) btnClear.classList.toggle('hidden', !schoolMatrixSearchQuery);
      renderLearnerSchoolsMatrix();
    };

    window.clearSchoolMatrixSearch = function() {
      schoolMatrixSearchQuery = '';
      const input = document.getElementById('inputSchoolMatrixSearch');
      if (input) input.value = '';
      const btnClear = document.getElementById('btnClearSchoolMatrixSearch');
      if (btnClear) btnClear.classList.add('hidden');
      renderLearnerSchoolsMatrix();
    };

    // =========================================================================
    // MAIN RENDERER: PERFORMANCE TABLE & LITERACY DASHBOARD
    // =========================================================================
    function renderPerformanceTable() {
      const tbody = document.getElementById('performanceTableBody');
      if (!tbody) return;

      if (isSuperAdmin) {
        if (selectedJurisdiction === 'ALL') {
          superAdminLearnerView = 'schools';
        } else {
          superAdminLearnerView = 'drilldown';
        }
      }
      const isSuperAdminSchoolsOverview = isSuperAdmin && (selectedJurisdiction === 'ALL');

      const schoolsContainer = document.getElementById('learnerSchoolsContainer');
      const schoolWorkspace = document.getElementById('learnerSchoolWorkspace');
      const breadcrumbBar = document.getElementById('learnerBreadcrumbBar');
      const viewModeToggle = document.getElementById('learnerViewModeToggleGroup');
      const scopeBadge = document.getElementById('academicPerformanceScopeBadge');
      const schoolFilterContainer = document.getElementById('learnerSchoolFilterContainer');

      if (isSuperAdminSchoolsOverview) {
        if (schoolsContainer) schoolsContainer.classList.remove('hidden');
        if (schoolWorkspace) schoolWorkspace.classList.add('hidden');
        if (breadcrumbBar) breadcrumbBar.classList.add('hidden');
        if (viewModeToggle) viewModeToggle.classList.add('hidden');
        if (scopeBadge) scopeBadge.textContent = 'SDO Valenzuela City • 25 Junior High Schools Overview';

        renderLearnerSchoolsMatrix();
        return;
      }

      // If we are here, we are in single school mode (School Admin OR Super Admin drilled into a school)
      if (schoolsContainer) schoolsContainer.classList.add('hidden');
      if (schoolWorkspace) schoolWorkspace.classList.remove('hidden');
      if (viewModeToggle) viewModeToggle.classList.remove('hidden');

      const activeSchoolId = isSchoolAdmin ? String(currentAdminUser?.schoolId || '') : String(selectedJurisdiction || '');
      const schoolObj = (VALENZUELA_SCHOOLS || []).find(s => s.id === activeSchoolId);
      const schoolName = schoolObj ? schoolObj.name : (typeof getSchoolName === 'function' ? getSchoolName(activeSchoolId) : activeSchoolId);
      const schoolDistrict = schoolObj ? schoolObj.district : '';

      if (isSuperAdmin) {
        if (breadcrumbBar) {
          breadcrumbBar.classList.remove('hidden');
          const elName = document.getElementById('learnerBreadcrumbSchoolName');
          if (elName) elName.textContent = schoolName;
          const elDist = document.getElementById('learnerBreadcrumbDistrictBadge');
          if (elDist) {
            elDist.textContent = schoolDistrict || 'District';
            elDist.className = `px-2 py-0.5 rounded-full text-[10px] font-bold ${schoolDistrict === 'District 1' ? 'bg-indigo-50 text-indigo-700 border border-indigo-200' : 'bg-purple-50 text-purple-700 border border-purple-200'}`;
          }
          const elId = document.getElementById('learnerBreadcrumbSchoolId');
          if (elId) elId.textContent = `ID: ${activeSchoolId}`;
        }
        if (schoolFilterContainer) schoolFilterContainer.classList.remove('hidden');
        if (scopeBadge) scopeBadge.textContent = `${schoolName} • ${schoolDistrict || 'Secondary Campus'}`;
      } else {
        // School Admin
        if (breadcrumbBar) breadcrumbBar.classList.add('hidden');
        if (schoolFilterContainer) schoolFilterContainer.classList.add('hidden');
        if (scopeBadge) scopeBadge.textContent = `${schoolName} • ${schoolDistrict || 'Secondary Campus'}`;
      }

      if (typeof populateLearnerSectionDropdown === 'function') {
        populateLearnerSectionDropdown();
      }

      try {
        let list = (cachedUsers || []).filter(u => u.role === 'student');

        // Robust School Scoping with class section fallback
        if (isSchoolAdmin && currentAdminUser?.schoolId) {
          const targetSchool = String(currentAdminUser.schoolId).trim();
          list = list.filter(u => (resolveStudentSchoolId(u) || String(u.schoolId || '').trim()) === targetSchool);
        } else {
          const activeSchool = (learnerSchoolFilterVal && learnerSchoolFilterVal !== 'ALL')
            ? learnerSchoolFilterVal
            : (selectedJurisdiction !== 'ALL' ? selectedJurisdiction : 'ALL');
          if (activeSchool !== 'ALL') {
            const targetSchool = String(activeSchool).trim();
            list = list.filter(u => (resolveStudentSchoolId(u) || String(u.schoolId || '').trim()) === targetSchool);
          }
        }

        // Dedicated Grade Filter
        const activeLearnerGrade = learnerGradeFilter || document.getElementById('selectLearnerGradeFilter')?.value || 'ALL';
        if (activeLearnerGrade !== 'ALL') {
          const targetGradeNum = activeLearnerGrade.replace('Grade ', '').trim();
          list = list.filter(u => String(u.grade || '').trim() === targetGradeNum || (u.grade || '').trim() === activeLearnerGrade || `Grade ${u.grade}` === activeLearnerGrade);
        }

        // Dedicated Gender Demographics Filter
        const activeLearnerGender = learnerGenderFilter || document.getElementById('selectLearnerGenderFilter')?.value || 'ALL';
        if (activeLearnerGender !== 'ALL') {
          list = list.filter(u => getStudentGender(u) === activeLearnerGender);
        }

        // Dedicated Section Filter
        const activeLearnerSection = learnerSectionFilter || document.getElementById('selectLearnerSectionFilter')?.value || 'ALL';
        if (activeLearnerSection !== 'ALL') {
          list = list.filter(u => {
            const secInfo = resolveStudentSectionAndAdviser(u);
            return secInfo.sectionName.toLowerCase() === activeLearnerSection.toLowerCase() || String(u.section || '').trim().toLowerCase() === activeLearnerSection.toLowerCase();
          });
        }

        // If in Section Matrix mode, render cards and return early
        if (learnerViewMode === 'sections') {
          renderSectionPerformanceCards(list);
        }

        // Precompute Academic Profiles for all students in cohort
        const profilesMap = new Map();
        let countEval = 0;
        let countInd = 0;
        let countIns = 0;
        let countFru = 0;
        let countUnass = 0;
        let totalStreak = 0;
        let totalScoreSum = 0;
        let totalWpmSum = 0;
        let totalWpmCount = 0;
        let maleCount = 0;
        let femaleCount = 0;
        let totalWordsMasteredSum = 0;

        list.forEach(u => {
          const prof = computeLearnerAcademicProfile(u);
          profilesMap.set(u.id, prof);

          if (prof.totalQuizzes > 0 || prof.effectiveMps !== null) countEval++;
          if (prof.tier === 'independent') countInd++;
          else if (prof.tier === 'instructional') countIns++;
          else if (prof.tier === 'frustration') countFru++;
          else countUnass++;

          if (prof.effectiveMps !== null) {
            totalScoreSum += prof.effectiveMps;
          }
          if (prof.wpm) {
            totalWpmSum += prof.wpm;
            totalWpmCount++;
          }

          totalStreak += (u.current_streak || 0);
          totalWordsMasteredSum += (prof.wordsMastered || 0);

          const gen = getStudentGender(u);
          if (gen === 'male') maleCount++;
          else if (gen === 'female') femaleCount++;
        });

        const cohortTotal = list.length;
        const avgStreak = cohortTotal > 0 ? Math.round(totalStreak / cohortTotal) : 0;
        const avgCohortMps = countEval > 0 ? Math.round(totalScoreSum / countEval) : 0;
        const avgCohortWpm = totalWpmCount > 0 ? Math.round(totalWpmSum / totalWpmCount) : null;
        const coveragePct = cohortTotal > 0 ? Math.round((countEval / cohortTotal) * 100) : 0;

        // 1. Update Phil-IRI 3-Tier Distribution Stacked Ratio Bar
        const indPct = cohortTotal > 0 ? Math.round((countInd / cohortTotal) * 100) : 0;
        const insPct = cohortTotal > 0 ? Math.round((countIns / cohortTotal) * 100) : 0;
        const fruPct = cohortTotal > 0 ? Math.round((countFru / cohortTotal) * 100) : 0;
        const unassPct = cohortTotal > 0 ? Math.max(0, 100 - (indPct + insPct + fruPct)) : 0;

        const elBarInd = document.getElementById('barTierIndependent');
        if (elBarInd) elBarInd.style.width = `${indPct}%`;
        const elBarIns = document.getElementById('barTierInstructional');
        if (elBarIns) elBarIns.style.width = `${insPct}%`;
        const elBarFru = document.getElementById('barTierFrustration');
        if (elBarFru) elBarFru.style.width = `${fruPct}%`;
        const elBarUnass = document.getElementById('barTierUnassessed');
        if (elBarUnass) elBarUnass.style.width = `${unassPct}%`;

        const elLegendInd = document.getElementById('perfLegendIndCount');
        if (elLegendInd) elLegendInd.textContent = `${countInd} (${indPct}%)`;
        const elLegendIns = document.getElementById('perfLegendInsCount');
        if (elLegendIns) elLegendIns.textContent = `${countIns} (${insPct}%)`;
        const elLegendFru = document.getElementById('perfLegendFruCount');
        if (elLegendFru) elLegendFru.textContent = `${countFru} (${fruPct}%)`;
        const elLegendUnass = document.getElementById('perfLegendUnassCount');
        if (elLegendUnass) elLegendUnass.textContent = `${countUnass} (${unassPct}%)`;

        const elRatioSummary = document.getElementById('perfRatioSummaryText');
        if (elRatioSummary) {
          elRatioSummary.textContent = `${countInd} Independent (${indPct}%) • ${countIns} Instructional (${insPct}%) • ${countFru} Support (${fruPct}%)`;
        }
        const elCovPill = document.getElementById('perfEvaluatedRatePill');
        if (elCovPill) elCovPill.textContent = `Coverage: ${coveragePct}% (${countEval}/${cohortTotal})`;

        // 2. Update Bento 6-Tile KPI Summary Strip
        const elKpiEval = document.getElementById('perfKpiTotalEvaluated');
        if (elKpiEval) elKpiEval.textContent = countEval;
        const elKpiEvalSub = document.getElementById('perfKpiEvaluatedSub');
        if (elKpiEvalSub) elKpiEvalSub.textContent = `Coverage: ${coveragePct}% of ${cohortTotal}`;

        const elKpiMps = document.getElementById('perfKpiAverageMps');
        if (elKpiMps) {
          elKpiMps.textContent = `${avgCohortMps}%`;
          elKpiMps.className = `text-xl font-black font-display ${avgCohortMps >= 80 ? 'text-emerald-600' : (avgCohortMps >= 60 ? 'text-amber-600' : 'text-rose-600')}`;
        }

        const elKpiWpm = document.getElementById('perfKpiAverageWpm');
        if (elKpiWpm) elKpiWpm.textContent = avgCohortWpm ? `${avgCohortWpm} WPM` : '— WPM';

        const elKpiFru = document.getElementById('perfKpiFrustration');
        if (elKpiFru) elKpiFru.textContent = countFru;

        const elKpiGender = document.getElementById('perfKpiGenderRatio');
        if (elKpiGender) elKpiGender.textContent = `👦 ${maleCount} • 👧 ${femaleCount}`;

        const elKpiStreak = document.getElementById('perfKpiStreak');
        if (elKpiStreak) elKpiStreak.textContent = `${avgStreak}d`;
        const elKpiStreakSub = document.getElementById('perfKpiWordsMasteredSub');
        if (elKpiStreakSub) elKpiStreakSub.textContent = `${totalWordsMasteredSum} words mastered`;

        // 3. Filter by Reading Tier
        const activeLearnerTier = learnerTierFilter || document.getElementById('selectLearnerTierFilter')?.value || 'ALL';
        if (activeLearnerTier !== 'ALL') {
          list = list.filter(u => {
            const prof = profilesMap.get(u.id);
            return prof && prof.tier === activeLearnerTier;
          });
        }

        // 4. Tokenized Smart Search
        const activeLearnerSearch = learnerSearchQuery || (document.getElementById('inputLearnerSearch')?.value || '').toLowerCase().trim();
        if (activeLearnerSearch) {
          list = list.filter(u => {
            const secInfo = resolveStudentSectionAndAdviser(u);
            const textBlob = `${u.firstName || ''} ${u.lastName || ''} ${u.email || ''} ${u.username || ''} ${secInfo.sectionName} ${secInfo.adviserName} ${secInfo.joinCode} ${u.lrn || ''} ${u.grade || ''} ${u.schoolId || ''} ${getSchoolName(u.schoolId)}`;
            return matchSmartSearch(textBlob, activeLearnerSearch);
          });
        }

        const countBadge = document.getElementById('learnerCountSummaryBadge');
        if (countBadge) {
          countBadge.textContent = `Showing ${list.length} reading profile(s)${selectedJurisdiction !== 'ALL' ? ' for ' + getSchoolName(selectedJurisdiction) : ''}`;
        }

        if (list.length === 0) {
          tbody.innerHTML = `
            <tr>
              <td colspan="8" class="p-8 text-center text-slate-400 font-medium">
                No active student reading performance profiles match the active search/filters.
              </td>
            </tr>`;
          const pagSummary = document.getElementById('learnerPaginationSummary');
          if (pagSummary) pagSummary.textContent = "Showing 0 of 0 records";
          const pagPills = document.getElementById('learnerPagePillsContainer');
          if (pagPills) pagPills.innerHTML = "";
          return;
        }

        // 5. Multi-column Sorting (with HIGHEST first by default)
        list.sort((a, b) => {
          const profA = profilesMap.get(a.id) || {};
          const profB = profilesMap.get(b.id) || {};

          let res = 0;
          if (learnerSortColumn === 'mps') {
            const mpsA = profA.effectiveMps !== null && profA.effectiveMps !== undefined ? profA.effectiveMps : -1;
            const mpsB = profB.effectiveMps !== null && profB.effectiveMps !== undefined ? profB.effectiveMps : -1;
            res = mpsA - mpsB;
          } else if (learnerSortColumn === 'wpm') {
            const wpmA = profA.wpm || 0;
            const wpmB = profB.wpm || 0;
            res = wpmA - wpmB;
          } else if (learnerSortColumn === 'streak') {
            res = (a.current_streak || 0) - (b.current_streak || 0);
          } else if (learnerSortColumn === 'level') {
            const xpA = (a.level || 1) * 1000 + (a.current_xp || 0);
            const xpB = (b.level || 1) * 1000 + (b.current_xp || 0);
            res = xpA - xpB;
          } else if (learnerSortColumn === 'name') {
            const nameA = `${a.lastName || ''} ${a.firstName || ''}`.trim().toLowerCase();
            const nameB = `${b.lastName || ''} ${b.firstName || ''}`.trim().toLowerCase();
            res = nameA.localeCompare(nameB);
          }

          return learnerSortAsc ? res : -res;
        });

        // 6. Client-Side Pagination
        const totalItems = list.length;
        const totalPages = Math.ceil(totalItems / learnerPageSize) || 1;
        if (learnerPage > totalPages) learnerPage = totalPages;
        if (learnerPage < 1) learnerPage = 1;

        const startIndex = (learnerPage - 1) * learnerPageSize;
        const endIndex = Math.min(startIndex + learnerPageSize, totalItems);
        const paginatedList = list.slice(startIndex, endIndex);

        // Update Pagination Footer
        const elPagSummary = document.getElementById('learnerPaginationSummary');
        if (elPagSummary) {
          elPagSummary.textContent = `Showing ${startIndex + 1}–${endIndex} of ${totalItems} learner profile(s)`;
        }
        const btnPrev = document.getElementById('btnLearnerPagePrev');
        if (btnPrev) btnPrev.disabled = (learnerPage <= 1);
        const btnNext = document.getElementById('btnLearnerPageNext');
        if (btnNext) btnNext.disabled = (learnerPage >= totalPages);

        const elPillsContainer = document.getElementById('learnerPagePillsContainer');
        if (elPillsContainer) {
          let pillsHtml = '';
          const maxPills = 5;
          let startP = Math.max(1, learnerPage - 2);
          let endP = Math.min(totalPages, startP + maxPills - 1);
          if (endP - startP < maxPills - 1) {
            startP = Math.max(1, endP - maxPills + 1);
          }

          for (let p = startP; p <= endP; p++) {
            const isAct = p === learnerPage;
            pillsHtml += `
              <button type="button" onclick="goToLearnerPage(${p})" class="w-7 h-7 rounded-lg text-xs font-bold transition ${isAct ? 'bg-indigo-600 text-white shadow-2xs' : 'bg-white border border-slate-200 text-slate-700 hover:bg-slate-100'}">
                ${p}
              </button>
            `;
          }
          elPillsContainer.innerHTML = pillsHtml;
        }

        // 7. Render Paginated Table Rows
        tbody.innerHTML = paginatedList.map(u => {
          const streak = u.current_streak || 0;
          const quests = (u.quests_completed && u.quests_completed.length) || 0;
          const fName = u.firstName || u.first_name || '';
          const lName = u.lastName || u.last_name || '';
          const fullName = `${fName} ${lName}`.trim() || u.fullName || u.displayName || 'Learner';
          const initials = ((fName[0] || '') + (lName[0] || '')).toUpperCase() || 'ST';
          const genderBadge = getStudentGender(u) === 'male' ? '<span class="text-[10px] text-blue-600 font-semibold">👦 Male</span>' : (getStudentGender(u) === 'female' ? '<span class="text-[10px] text-pink-600 font-semibold">👧 Female</span>' : '');

          const metrics = profilesMap.get(u.id) || computeLearnerAcademicProfile(u);
          const secInfo = resolveStudentSectionAndAdviser(u);

          const wpmVal = metrics.wpm ? `${metrics.wpm} WPM` : (metrics.totalQuizzes > 0 ? 'Assessed' : '—');
          const wpmBadge = metrics.wpm ? (metrics.wpm >= 120 ? '<span class="text-[9px] px-1.5 py-0.2 rounded bg-emerald-50 text-emerald-700 font-bold border border-emerald-200 ml-1">High</span>' : '<span class="text-[9px] px-1.5 py-0.2 rounded bg-amber-50 text-amber-700 font-bold border border-amber-200 ml-1">Norm</span>') : '';

          let mpsDisplay = '';
          if (metrics.effectiveMps !== null) {
            const barColor = metrics.effectiveMps >= 80 ? 'bg-emerald-500' : (metrics.effectiveMps >= 60 ? 'bg-amber-500' : 'bg-rose-500');
            const textColor = metrics.effectiveMps >= 80 ? 'text-emerald-700' : (metrics.effectiveMps >= 60 ? 'text-amber-700' : 'text-rose-700');
            mpsDisplay = `
              <div class="space-y-1">
                <div class="flex items-center gap-1.5">
                  <span class="font-bold ${textColor} text-xs">${metrics.effectiveMps}% MPS</span>
                  <span class="text-[9px] px-1.5 py-0.5 rounded-full border ${metrics.trendClass}">${metrics.trendLabel}</span>
                </div>
                <div class="w-20 h-1.5 rounded-full bg-slate-100 overflow-hidden">
                  <div class="${barColor} h-full rounded-full" style="width: ${Math.min(100, Math.max(5, metrics.effectiveMps))}%"></div>
                </div>
              </div>
            `;
          } else {
            mpsDisplay = '<span class="text-slate-400 text-xs font-medium">Pending Assessment</span>';
          }

          const photoUrl = u.photoUrl || u.avatarUrl || u.profileImageUrl || '';
          const pendingPhotoUrl = u.pendingPhotoUrl || '';
          const photoStatus = u.photoStatus || (photoUrl ? 'approved' : 'none');
          const avatarHtml = renderStudentAvatarHtml(photoUrl, pendingPhotoUrl, photoStatus, initials, 'w-9 h-9', 'text-xs');

          return `
            <tr class="hover:bg-slate-50/80 transition-colors">
              <td class="p-3.5">
                <div class="flex items-center gap-2.5">
                  ${avatarHtml}
                  <div>
                    <div class="font-bold text-slate-900">${escapeHtml(fullName)}</div>
                    <div class="text-[10px] text-slate-500 flex items-center gap-1.5 mt-0.5">
                      <span class="font-mono text-slate-400">@${escapeHtml(u.username || u.id.substring(0,6))}</span>
                      ${u.lrn ? `<span>&bull;</span> <span class="font-mono text-slate-500">LRN: ${escapeHtml(u.lrn)}</span>` : ''}
                      ${genderBadge ? `<span>&bull;</span> ${genderBadge}` : ''}
                    </div>
                  </div>
                </div>
              </td>
              <td class="p-3.5">
                <div class="space-y-0.5">
                  <div class="flex items-center gap-1.5">
                    <span class="font-bold text-indigo-900 text-xs">${escapeHtml(secInfo.sectionName)}</span>
                    <span class="px-1.5 py-0.2 rounded bg-slate-100 text-slate-600 text-[10px] font-bold">Gr. ${escapeHtml(secInfo.grade)}</span>
                  </div>
                  <div class="text-[10px] text-slate-500 flex items-center gap-1">
                    <span>${escapeHtml(secInfo.adviserName)}</span>
                    ${secInfo.joinCode !== '—' ? `<span class="font-mono text-slate-400 text-[9px]">#${escapeHtml(secInfo.joinCode)}</span>` : ''}
                  </div>
                </div>
              </td>
              <td class="p-3.5">
                <span class="px-2.5 py-1 rounded-full text-[10px] font-bold border inline-block ${metrics.tierClass}">
                  ${metrics.tierLabel}
                </span>
              </td>
              <td class="p-3.5">
                ${mpsDisplay}
              </td>
              <td class="p-3.5 font-semibold text-slate-700">
                <div class="flex items-center">
                  <span>${wpmVal}</span>
                  ${wpmBadge}
                </div>
              </td>
              <td class="p-3.5 font-bold ${streak > 0 ? 'text-amber-600' : 'text-slate-400'}">
                <div class="space-y-0.5">
                  <div class="flex items-center gap-1">
                    ${streak > 0 ? `<span class="inline-flex items-center gap-1 text-amber-600 font-bold">${uiIcon('flame', 'w-3.5 h-3.5')} ${streak}d</span>` : '<span class="text-slate-400 font-medium">0d</span>'}
                  </div>
                  ${metrics.wordsMastered > 0 ? `<div class="text-[9px] text-slate-400 font-normal">📚 ${metrics.wordsMastered} words</div>` : ''}
                </div>
              </td>
              <td class="p-3.5">
                <div class="space-y-0.5">
                  <span class="font-bold text-indigo-700 text-xs">Lvl ${u.level || 1}</span>
                  <div class="text-slate-400 text-[10px]">(${u.current_xp || 0} XP &bull; ${quests} Quests)</div>
                </div>
              </td>
              <td class="p-3.5 text-right">
                <button type="button" onclick="openStudentAcademicProfile('${u.id}')" class="px-3 py-1.5 bg-indigo-50 hover:bg-indigo-100 text-indigo-700 border border-indigo-200 rounded-xl text-xs font-bold shadow-2xs inline-flex items-center gap-1.5 transition-all">
                  <svg class="w-3.5 h-3.5 text-indigo-600" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M2 3h6a4 4 0 0 1 4 4v14a3 3 0 0 0-3-3H2z"/><path d="M22 3h-6a4 4 0 0 0-4 4v14a3 3 0 0 1 3-3h7z"/></svg>
                  <span>360° Profile</span>
                </button>
              </td>
            </tr>
          `;
        }).join('');
      } catch (err) {
        console.error("Failed to render performance table:", err);
        tbody.innerHTML = `
          <tr>
            <td colspan="8" class="p-8 text-center text-rose-500 font-semibold">
              Failed to load student performance profiles: ${escapeHtml(err.message)}
            </td>
          </tr>`;
      }
    }

    window.approveStudentPhoto = async function(uid, encodedUrl) {
      const user = cachedUsers.find(u => u.id === uid);
      const photoUrl = (encodedUrl ? decodeURIComponent(encodedUrl) : null) || (user ? user.pendingPhotoUrl : '');
      if (!photoUrl) {
        window.showToast("No pending photo found to approve.", "error");
        return;
      }
      const confirmed = await showConfirmModal({
        title: "Approve Profile Photo?",
        message: "Approve this learner's submitted photo for school visibility?",
        badgeIcon: "📷",
        badgeBg: "bg-emerald-50 text-emerald-700 border-emerald-200",
        confirmText: "Approve Photo",
        isDestructive: false
      });
      if (!confirmed) return;
      try {
        await updateDoc(doc(db, "users", uid), {
          photoUrl: photoUrl,
          photoStatus: "approved",
          pendingPhotoUrl: null
        });
        if (user) {
          user.photoUrl = photoUrl;
          user.photoStatus = "approved";
          user.pendingPhotoUrl = null;
        }
        window.showToast("Student photo approved successfully!", "success");
        renderPerformanceTable();
      } catch (err) {
        console.error("Failed to approve photo:", err);
        window.showToast("Failed to approve photo: " + err.message, "error");
      }
    };

    window.declineStudentPhoto = async function(uid) {
      const confirmed = await showConfirmModal({
        title: "Decline Profile Photo?",
        message: "Decline this learner's submitted photo? They will receive actionable feedback in their profile.",
        badgeIcon: "📷",
        badgeBg: "bg-amber-50 text-amber-700 border-amber-200",
        confirmText: "Decline Photo",
        isDestructive: true
      });
      if (!confirmed) return;
      try {
        await updateDoc(doc(db, "users", uid), {
          photoStatus: "declined",
          pendingPhotoUrl: null
        });
        const user = cachedUsers.find(u => u.id === uid);
        if (user) {
          user.photoStatus = "declined";
          user.pendingPhotoUrl = null;
        }
        window.showToast("Student photo declined. Feedback set for student.", "info");
        renderPerformanceTable();
      } catch (err) {
        console.error("Failed to decline photo:", err);
        window.showToast("Failed to decline photo: " + err.message, "error");
      }
    };

    window.removeStudentPhoto = async function(uid) {
      const confirmed = await showConfirmModal({
        title: "Remove Profile Photo?",
        message: "Remove this learner's current profile photo?",
        badgeIcon: "🗑️",
        badgeBg: "bg-rose-50 text-rose-600 border-rose-200",
        confirmText: "Remove Photo",
        isDestructive: true
      });
      if (!confirmed) return;
      try {
        await updateDoc(doc(db, "users", uid), {
          photoUrl: null,
          photoStatus: "none",
          pendingPhotoUrl: null
        });
        const user = cachedUsers.find(u => u.id === uid);
        if (user) {
          user.photoUrl = null;
          user.photoStatus = "none";
          user.pendingPhotoUrl = null;
        }
        window.showToast("Student photo removed.", "info");
        renderPerformanceTable();
      } catch (err) {
        console.error("Failed to remove photo:", err);
        window.showToast("Failed to remove photo: " + err.message, "error");
      }
    };

    // =========================================================================
    // 360° LEARNER ACADEMIC & LITERACY PROFILE MODAL CONTROLLER
    // =========================================================================
    window.openStudentAcademicProfile = async function(uid) {
      activeInspectStudentId = uid;
      activeProfileTab = 'phil_iri';

      const student = (cachedUsers || []).find(item => item.id === uid) || (cachedPendingStudents || []).find(p => p.id === uid || p.username === uid);
      const studentName = student ? `${student.firstName || ''} ${student.lastName || ''}`.trim() || student.username || 'Learner' : 'Learner';
      const schoolName = getSchoolName(student ? student.schoolId : '');
      const secInfo = resolveStudentSectionAndAdviser(student);
      const metrics = computeLearnerAcademicProfile(student) || {
        effectiveMps: null,
        tier: 'unassessed',
        tierLabel: 'Unassessed',
        tierClass: 'bg-slate-100 text-slate-700 border-slate-200',
        wpm: null,
        trend: 'stable',
        trendLabel: '➔ Stable',
        trendClass: 'bg-slate-100 text-slate-600 border-slate-200',
        totalQuizzes: 0,
        timedSubs: [],
        weeklySubs: [],
        wordsMastered: 0
      };

      // 1. Header Information
      document.getElementById('historyStudentName').textContent = studentName;
      const metaEl = document.getElementById('historyStudentMeta');
      if (metaEl) {
        metaEl.innerHTML = `
          <span class="font-semibold text-slate-700">${escapeHtml(schoolName)}</span>
          <span>&bull;</span>
          <span class="font-bold text-indigo-900">${escapeHtml(secInfo.sectionName)} (Grade ${escapeHtml(secInfo.grade)})</span>
          <span>&bull;</span>
          <span>Adviser: ${escapeHtml(secInfo.adviserName)}</span>
          ${secInfo.joinCode !== '—' ? `<span>&bull;</span> <span class="font-mono text-slate-400">#${escapeHtml(secInfo.joinCode)}</span>` : ''}
          ${student?.lrn ? `<span>&bull;</span> <span class="font-mono text-slate-500">LRN: ${escapeHtml(student.lrn)}</span>` : ''}
        `;
      }

      const tierBadgeEl = document.getElementById('historyStudentTierBadge');
      if (tierBadgeEl) {
        tierBadgeEl.textContent = metrics.tierLabel;
        tierBadgeEl.className = `px-2.5 py-0.5 rounded-full text-[10px] font-bold border ${metrics.tierClass}`;
      }
      const trendBadgeEl = document.getElementById('historyStudentTrendBadge');
      if (trendBadgeEl) {
        trendBadgeEl.textContent = metrics.trendLabel;
        trendBadgeEl.className = `px-2 py-0.5 rounded-full text-[10px] font-bold border ${metrics.trendClass}`;
      }

      // Avatar
      const historyAvatarEl = document.getElementById('historyStudentAvatarContainer');
      if (historyAvatarEl) {
        const histPhoto = student ? (student.photoUrl || student.avatarUrl || student.profileImageUrl || '') : '';
        const histPend = student ? (student.pendingPhotoUrl || '') : '';
        const histStat = student ? (student.photoStatus || 'none') : 'none';
        const histInitials = student ? (((student.firstName?.[0] || '') + (student.lastName?.[0] || '')).toUpperCase() || 'ST') : 'ST';
        historyAvatarEl.innerHTML = renderStudentAvatarHtml(histPhoto, histPend, histStat, histInitials, 'w-12 h-12', 'text-sm');
      }

      // 2. Bento Quick 4-Tile Metrics
      const mpsEl = document.getElementById('historyAverageScore');
      if (mpsEl) {
        mpsEl.textContent = metrics.effectiveMps !== null ? `${metrics.effectiveMps}%` : 'Pending';
        mpsEl.className = `text-xl font-black font-display mt-0.5 ${metrics.effectiveMps >= 80 ? 'text-emerald-600' : (metrics.effectiveMps >= 60 ? 'text-amber-600' : (metrics.effectiveMps !== null ? 'text-rose-600' : 'text-slate-400'))}`;
      }
      const mpsLabelEl = document.getElementById('historyMpsTierLabel');
      if (mpsLabelEl) mpsLabelEl.textContent = `${metrics.tierLabel} Reader`;

      const wpmEl = document.getElementById('historyReadingSpeed');
      if (wpmEl) wpmEl.textContent = metrics.wpm ? `${metrics.wpm} WPM` : '— WPM';
      const wpmBenchEl = document.getElementById('historyWpmBenchmark');
      if (wpmBenchEl) wpmBenchEl.textContent = `Grade ${secInfo.grade} Norm: 100-140 WPM`;

      const totalQEl = document.getElementById('historyTotalQuizzes');
      if (totalQEl) totalQEl.textContent = metrics.totalQuizzes;
      const totalQSub = document.getElementById('historyTotalAttemptsSub');
      if (totalQSub) totalQSub.textContent = `${metrics.timedSubs.length} timed • ${metrics.weeklySubs.length} weekly`;

      const streakEl = document.getElementById('historyCurrentStreak');
      if (streakEl) streakEl.textContent = `${student?.current_streak || 0} Days`;
      const wordsEl = document.getElementById('historyWordsMasteredCount');
      if (wordsEl) wordsEl.textContent = `${metrics.wordsMastered || 0} words mastered`;

      // 3. Cognitive Dimensions Breakdown (Literal, Inferential, Critical Evaluation)
      let litTotal = 0, litCorrect = 0;
      let infTotal = 0, infCorrect = 0;
      let critTotal = 0, critCorrect = 0;
      metrics.timedSubs.forEach(t => {
        const items = t.items || t.answers || [];
        items.forEach(it => {
          const cat = String(it.dimension || it.cognitiveDimension || it.category || '').toLowerCase();
          const isCorr = Boolean(it.isCorrect || it.correct);
          if (cat.includes('infer')) {
            infTotal++;
            if (isCorr) infCorrect++;
          } else if (cat.includes('crit') || cat.includes('eval')) {
            critTotal++;
            if (isCorr) critCorrect++;
          } else {
            litTotal++;
            if (isCorr) litCorrect++;
          }
        });
      });

      const baseMps = metrics.effectiveMps !== null ? metrics.effectiveMps : 75;
      const litPct = litTotal > 0 ? Math.round((litCorrect / litTotal) * 100) : Math.min(100, Math.round(baseMps * 1.05));
      const infPct = infTotal > 0 ? Math.round((infCorrect / infTotal) * 100) : Math.max(0, Math.round(baseMps * 0.95));
      const critPct = critTotal > 0 ? Math.round((critCorrect / critTotal) * 100) : Math.max(0, Math.round(baseMps * 0.90));

      const elLit = document.getElementById('cogMpsLiteral');
      if (elLit) elLit.textContent = `${litPct}%`;
      const elBarLit = document.getElementById('barCogLiteral');
      if (elBarLit) elBarLit.style.width = `${Math.min(100, Math.max(5, litPct))}%`;

      const elInf = document.getElementById('cogMpsInferential');
      if (elInf) elInf.textContent = `${infPct}%`;
      const elBarInf = document.getElementById('barCogInferential');
      if (elBarInf) elBarInf.style.width = `${Math.min(100, Math.max(5, infPct))}%`;

      const elCrit = document.getElementById('cogMpsCritical');
      if (elCrit) elCrit.textContent = `${critPct}%`;
      const elBarCrit = document.getElementById('barCogCritical');
      if (elBarCrit) elBarCrit.style.width = `${Math.min(100, Math.max(5, critPct))}%`;

      // 4. Tab 1: Render Standardized Timed Tests List
      const timedListEl = document.getElementById('historyTimedAssessmentsList');
      if (timedListEl) {
        if (metrics.timedSubs.length === 0) {
          timedListEl.innerHTML = `<div class="p-4 text-center text-slate-400">No division standardized timed reading tests taken yet.</div>`;
        } else {
          timedListEl.innerHTML = metrics.timedSubs.map(t => {
            const dateStr = t.submittedAt || t.timestamp ? new Date(t.submittedAt || t.timestamp).toLocaleDateString() : 'Recorded';
            const tot = Number(t.totalQuestions) || (t.answers && t.answers.length) || 5;
            const sc = Number(t.score) || 0;
            const pct = t.percentage !== undefined ? Number(t.percentage) : Math.round((sc / Math.max(tot, 1)) * 100);
            const wpmRead = t.wpm || t.readingSpeedWpm || t.reading_speed || null;
            const badgeClass = pct >= 80 ? 'bg-emerald-50 text-emerald-700 border-emerald-200' : (pct >= 60 ? 'bg-amber-50 text-amber-700 border-amber-200' : 'bg-rose-50 text-rose-700 border-rose-200');

            return `
              <div class="py-2.5 flex items-center justify-between gap-2">
                <div>
                  <div class="font-bold text-slate-800">${escapeHtml(t.assessmentTitle || t.title || 'Phil-IRI Standardized Test')}</div>
                  <div class="text-[10px] text-slate-400 mt-0.5">
                    <span>${dateStr}</span>
                    ${wpmRead ? ` &bull; <span class="font-semibold text-blue-600">${wpmRead} WPM Reading Speed</span>` : ''}
                    <span> &bull; Passage: ${escapeHtml(t.passageTitle || 'Standard Passage')}</span>
                  </div>
                </div>
                <div class="text-right shrink-0">
                  <div class="font-bold text-slate-900">${sc} / ${tot} (${pct}%)</div>
                  <span class="px-2 py-0.5 rounded-full text-[9px] font-bold border inline-block mt-0.5 ${badgeClass}">
                    ${pct >= 80 ? 'Independent' : (pct >= 60 ? 'Instructional' : 'Frustration')}
                  </span>
                </div>
              </div>
            `;
          }).join('');
        }
      }

      // 5. Tab 2: Render Weekly & Module Quizzes List
      const quizListEl = document.getElementById('historyAttemptsList');
      if (quizListEl) {
        quizListEl.innerHTML = `<div class="p-4 text-center text-slate-400">Loading curriculum quiz attempts...</div>`;
      }

      // 6. Tab 3: Reading Habits & Gamification
      const lvlXpEl = document.getElementById('historyLevelXp');
      if (lvlXpEl) lvlXpEl.textContent = `Level ${student?.level || 1} (${student?.current_xp || 0} XP)`;
      const questsEl = document.getElementById('historyQuestsList');
      if (questsEl) {
        const qList = student?.quests_completed || [];
        if (qList.length > 0) {
          questsEl.innerHTML = qList.map(q => `<span class="inline-block px-2 py-0.5 bg-indigo-50 border border-indigo-200 rounded text-indigo-700 text-[10px] font-bold mr-1 mb-1">🏅 ${escapeHtml(q)}</span>`).join('');
        } else {
          questsEl.textContent = "No special quest achievements logged yet.";
        }
      }
      const wordsBadgeEl = document.getElementById('historyWordsMasteredBadge');
      if (wordsBadgeEl) wordsBadgeEl.textContent = `${metrics.wordsMastered || 0} Mastered`;
      const intWordsEl = document.getElementById('historyIntervalFourWords');
      if (intWordsEl) intWordsEl.textContent = `${metrics.wordsMastered || 0} words in long-term memory`;

      // 7. Tab 4: Remedial Interventions & Guidance Notes
      const statusSelect = document.getElementById('selectInterventionStatus');
      if (statusSelect) {
        statusSelect.value = student?.interventionStatus || (metrics.tier === 'frustration' ? 'remedial_reading' : 'none');
      }
      const notesTextarea = document.getElementById('textareaInterventionNotes');
      if (notesTextarea) {
        notesTextarea.value = student?.interventionNotes || '';
      }
      const statusBadge = document.getElementById('interventionStatusBadge');
      if (statusBadge) {
        const curStatus = student?.interventionStatus || 'none';
        if (curStatus === 'remedial_reading') {
          statusBadge.textContent = '🚨 Remedial Session Assigned';
          statusBadge.className = 'px-2.5 py-1 rounded-full text-[10px] font-bold bg-rose-50 text-rose-700 border border-rose-200';
        } else if (curStatus === 'peer_tutoring') {
          statusBadge.textContent = '👥 Peer Tutoring Assigned';
          statusBadge.className = 'px-2.5 py-1 rounded-full text-[10px] font-bold bg-amber-50 text-amber-700 border border-amber-200';
        } else {
          statusBadge.textContent = 'Standard Program';
          statusBadge.className = 'px-2.5 py-1 rounded-full text-[10px] font-bold bg-slate-100 text-slate-700 border border-slate-200';
        }
      }
      const updatedEl = document.getElementById('interventionLastUpdated');
      if (updatedEl) {
        updatedEl.textContent = student?.interventionUpdatedAt ? `Updated ${new Date(student.interventionUpdatedAt).toLocaleDateString()}` : 'No previous remarks recorded';
      }

      // Reset active tab to Tab 1
      switchProfileTab('phil_iri');

      // Open Modal
      document.getElementById('modalQuizHistory').classList.remove('hidden');

      // Async fetch quiz_history subcollection for Tab 2
      try {
        const snap = await getDocs(collection(db, "users", uid, "quiz_history"));
        let allQuizzes = [...metrics.weeklySubs];

        if (!snap.empty) {
          const attempts = snap.docs.map(d => ({ id: d.id, ...d.data() }));
          attempts.forEach(a => {
            if (!allQuizzes.some(q => q.id === a.id)) {
              allQuizzes.push(a);
            }
          });
        }

        if (quizListEl) {
          if (allQuizzes.length === 0) {
            quizListEl.innerHTML = `<div class="p-4 text-center text-slate-400">No practice quiz attempts recorded for this learner yet.</div>`;
          } else {
            quizListEl.innerHTML = allQuizzes.map(a => {
              const dateStr = a.timestamp || a.submittedAt ? new Date(a.timestamp || a.submittedAt).toLocaleDateString() : 'Recorded';
              const sc = a.finalScore !== undefined ? a.finalScore : (a.score !== undefined ? a.score : 0);
              const tot = a.totalAttempts || a.totalQuestions || 10;
              const pct = tot > 0 ? Math.round((sc / tot) * 100) : 0;
              const col = pct >= 80 ? 'text-emerald-700' : (pct >= 60 ? 'text-amber-700' : 'text-rose-700');

              return `
                <div class="py-2.5 flex items-center justify-between gap-2">
                  <div>
                    <div class="font-bold text-slate-800">${escapeHtml(a.title || a.subject || a.materialId || 'Reading Comprehension Quiz')}</div>
                    <div class="text-[10px] text-slate-400 mt-0.5">
                      <span>${dateStr}</span>
                      <span> &bull; Attempts: ${a.totalAttempts || 1}</span>
                    </div>
                  </div>
                  <div class="text-right shrink-0">
                    <div class="font-bold ${col}">${sc} / ${tot} (${pct}%)</div>
                    <div class="w-16 h-1 rounded-full bg-slate-100 overflow-hidden mt-1 ml-auto">
                      <div class="${pct >= 80 ? 'bg-emerald-500' : (pct >= 60 ? 'bg-amber-500' : 'bg-rose-500')} h-full" style="width: ${pct}%"></div>
                    </div>
                  </div>
                </div>
              `;
            }).join('');
          }
        }
      } catch (err) {
        console.warn("Could not load subcollection quiz_history:", err);
        if (quizListEl && metrics.weeklySubs.length === 0) {
          quizListEl.innerHTML = `<div class="p-4 text-center text-slate-400">No recorded quiz attempts.</div>`;
        }
      }
    };

    // Alias for backwards compatibility with any existing buttons
    window.viewStudentQuizHistory = window.openStudentAcademicProfile;

    window.switchProfileTab = function(tabName) {
      activeProfileTab = tabName;
      const tabs = ['phil_iri', 'quizzes', 'habits', 'interventions'];
      tabs.forEach(t => {
        const btn = document.getElementById(`btnProfileTab${t.charAt(0).toUpperCase() + t.slice(1).replace('_', '')}`);
        const content = document.getElementById(`profileTabContent_${t}`);
        const isAct = (t === tabName);

        if (content) content.classList.toggle('hidden', !isAct);
        if (btn) {
          if (isAct) {
            btn.className = 'px-3 py-2 border-b-2 border-indigo-600 text-indigo-700 font-bold transition flex items-center gap-1.5 whitespace-nowrap';
          } else {
            btn.className = 'px-3 py-2 border-b-2 border-transparent text-slate-500 hover:text-slate-800 transition flex items-center gap-1.5 whitespace-nowrap';
          }
        }
      });
    };

    window.saveStudentIntervention = async function() {
      if (!activeInspectStudentId) return;
      const status = document.getElementById('selectInterventionStatus')?.value || 'none';
      const notes = document.getElementById('textareaInterventionNotes')?.value.trim() || '';
      const saveBtn = document.getElementById('btnSaveStudentIntervention');

      if (saveBtn) {
        saveBtn.disabled = true;
        saveBtn.textContent = "Saving...";
      }

      try {
        await updateDoc(doc(db, "users", activeInspectStudentId), {
          interventionStatus: status,
          interventionNotes: notes,
          interventionUpdatedAt: Date.now()
        });

        // Update local memory cache
        const u = (cachedUsers || []).find(item => item.id === activeInspectStudentId);
        if (u) {
          u.interventionStatus = status;
          u.interventionNotes = notes;
          u.interventionUpdatedAt = Date.now();
        }

        const statusBadge = document.getElementById('interventionStatusBadge');
        if (statusBadge) {
          if (status === 'remedial_reading') {
            statusBadge.textContent = '🚨 Remedial Session Assigned';
            statusBadge.className = 'px-2.5 py-1 rounded-full text-[10px] font-bold bg-rose-50 text-rose-700 border border-rose-200';
          } else if (status === 'peer_tutoring') {
            statusBadge.textContent = '👥 Peer Tutoring Assigned';
            statusBadge.className = 'px-2.5 py-1 rounded-full text-[10px] font-bold bg-amber-50 text-amber-700 border border-amber-200';
          } else {
            statusBadge.textContent = 'Standard Program';
            statusBadge.className = 'px-2.5 py-1 rounded-full text-[10px] font-bold bg-slate-100 text-slate-700 border border-slate-200';
          }
        }
        const updatedEl = document.getElementById('interventionLastUpdated');
        if (updatedEl) updatedEl.textContent = `Updated ${new Date().toLocaleDateString()}`;

        window.showToast("Learner intervention record saved successfully!", "success");
        renderPerformanceTable();
      } catch (err) {
        console.error("Save intervention error:", err);
        window.showToast("Failed to save intervention: " + err.message, "error");
      } finally {
        if (saveBtn) {
          saveBtn.disabled = false;
          saveBtn.textContent = "Save Intervention Record";
        }
      }
    };

    window.printActiveStudentIndividualPDF = function() {
      if (!activeInspectStudentId) return;
      if (typeof window.exportIndividualLearnerPhilIriPDF === 'function') {
        window.exportIndividualLearnerPhilIriPDF(activeInspectStudentId);
      } else {
        window.showToast("Preparing official student PDF reading profile...", "info");
      }
    };

    window.closeQuizHistoryModal = function() {
      document.getElementById('modalQuizHistory').classList.add('hidden');
      activeInspectStudentId = null;
    };


    // =========================================================================
    // 4. BATCH CREDENTIAL VAULT (Unified: pending_students + active users)
    // =========================================================================
    window.openBatchStudentsModal = function() {
      window.showToast("Student accounts are created by learners or teachers in the mobile application. Administrators oversee performance and issue temporary passkeys for password recovery.", "info");
    };

    window.closeBatchStudentsModal = function() {};

    window.handleBatchStudentsSubmit = function(e) {
      e?.preventDefault();
      window.showToast("Student account generation is restricted to the mobile application.", "warning");
    };

    let activeGeneratedPasskey = "";

    window.openResetPasskeyModal = async function(username, docId) {
      const cleanUser = (username || '').replace(/^@/, '').trim().toLowerCase();
      const tempPass = 'gab' + Math.floor(10000 + Math.random() * 90000);
      activeGeneratedPasskey = tempPass;

      try {
        let updated = false;

        // 1. If docId provided, prioritize checking users collection (claimed account)
        if (docId) {
          try {
            const uDoc = await getDoc(doc(db, "users", docId));
            if (uDoc.exists()) {
              await updateDoc(doc(db, "users", docId), {
                tempPassword: tempPass,
                password: tempPass,
                mustChangePassword: true,
                isClaimed: true
              });
              updated = true;
            }
          } catch (e) {
            console.warn("Could not check users by docId:", e);
          }
        }

        // 2. Query users collection by clean username or @username
        if (!updated && cleanUser) {
          try {
            const q1 = query(collection(db, "users"), where("username", "==", cleanUser));
            const snap1 = await getDocs(q1);
            if (!snap1.empty) {
              for (const d of snap1.docs) {
                await updateDoc(doc(db, "users", d.id), {
                  tempPassword: tempPass,
                  password: tempPass,
                  mustChangePassword: true,
                  isClaimed: true
                });
                updated = true;
              }
            } else {
              const q2 = query(collection(db, "users"), where("username", "==", "@" + cleanUser));
              const snap2 = await getDocs(q2);
              if (!snap2.empty) {
                for (const d of snap2.docs) {
                  await updateDoc(doc(db, "users", d.id), {
                    tempPassword: tempPass,
                    password: tempPass,
                    mustChangePassword: true,
                    isClaimed: true
                  });
                  updated = true;
                }
              }
            }
          } catch (e) {
            console.warn("Could not query users by username:", e);
          }
        }

        // If updated in users, sync temporary reset passkey to pending_students for instant unauthenticated mobile access
        if (updated && cleanUser) {
          try {
            const matchedUser = (cachedUsers || []).find(u => {
              const uClean = String(u.username || '').replace(/^@/, '').trim().toLowerCase();
              return uClean === cleanUser || u.id === docId;
            });
            const slipData = {
              username: cleanUser,
              role: 'student',
              password: tempPass,
              tempPassword: tempPass,
              mustChangePassword: true,
              isReset: true,
              isClaimed: true,
              claimedUid: docId || matchedUser?.id || '',
              schoolId: matchedUser?.schoolId || '',
              grade: matchedUser?.grade || '',
              section: matchedUser?.section || '',
              firstName: matchedUser?.firstName || '',
              lastName: matchedUser?.lastName || '',
              gender: matchedUser?.gender || 'unspecified',
              updatedAt: Date.now()
            };
            await setDoc(doc(db, "pending_students", cleanUser), slipData, { merge: true });
          } catch (e) {
            console.warn("Could not sync pending_students reset slip:", e);
          }
        }

        // 3. Fallback: Check pending_students (unclaimed student slip)
        if (!updated && cleanUser) {
          const pDoc = await getDoc(doc(db, "pending_students", cleanUser));
          if (pDoc.exists()) {
            await updateDoc(doc(db, "pending_students", cleanUser), {
              password: tempPass,
              tempPassword: tempPass,
              mustChangePassword: true
            });
            updated = true;
          } else {
            const pDocAt = await getDoc(doc(db, "pending_students", "@" + cleanUser));
            if (pDocAt.exists()) {
              await updateDoc(doc(db, "pending_students", "@" + cleanUser), {
                password: tempPass,
                tempPassword: tempPass,
                mustChangePassword: true
              });
              updated = true;
            }
          }
        }

        if (!updated) {
          throw new Error(`Learner account @${cleanUser || docId} could not be located in directory.`);
        }

        // Synchronize in-memory caches and re-render table immediately
        const matchingClaimed = (cachedUsers || []).find(u => u.id === docId || (cleanUser && String(u.username || '').toLowerCase().replace(/^@/, '') === cleanUser));
        if (matchingClaimed) {
          matchingClaimed.tempPassword = tempPass;
          matchingClaimed.password = tempPass;
          matchingClaimed.mustChangePassword = true;
          matchingClaimed.isClaimed = true;
        }
        const matchingPending = (cachedPendingStudents || []).find(p => p.id === cleanUser || String(p.username || '').toLowerCase().replace(/^@/, '') === cleanUser);
        if (matchingPending) {
          matchingPending.password = tempPass;
          matchingPending.tempPassword = tempPass;
          matchingPending.mustChangePassword = true;
        }
        if (typeof renderPendingStudentsTable === 'function') {
          renderPendingStudentsTable();
        }

        const displayTarget = cleanUser ? `@${cleanUser}` : (docId ? `@${docId.substring(0, 8)}` : 'Learner');
        document.getElementById('resetTargetUsername').textContent = displayTarget;
        document.getElementById('resetGeneratedPasskey').textContent = tempPass;
        document.getElementById('modalResetPasskey').classList.remove('hidden');
        window.showToast(`Temporary password generated for ${displayTarget}.`, "success");
      } catch (err) {
        console.error("Password reset error:", err);
        window.showToast("Failed to generate temporary password: " + err.message, "error");
      }
    };

    window.copyGeneratedPasskey = function() {
      if (activeGeneratedPasskey) {
        copyToClipboard(activeGeneratedPasskey, "Temporary password copied to clipboard!");
      }
    };

    window.closeResetPasskeyModal = function() {
      document.getElementById('modalResetPasskey').classList.add('hidden');
      activeGeneratedPasskey = "";
    };

    window.openEditStudentNameModal = function(username, docId) {
      // Find learner in cachedUsers or cachedPendingStudents
      let target = null;
      if (docId) {
        target = cachedUsers.find(u => u.id === docId && u.role === 'student');
      }
      if (!target && username) {
        target = cachedUsers.find(u => (u.username === username || u.authEmail?.startsWith(username + '@')) && u.role === 'student') ||
                 cachedPendingStudents.find(p => p.username === username || p.id === username);
      }
      if (!target && docId) {
        target = cachedPendingStudents.find(p => p.id === docId || p.username === docId);
      }

      const fName = target ? (target.firstName || target.first_name || '') : '';
      const lName = target ? (target.lastName || target.last_name || '') : '';
      const uName = target ? (target.username || (target.authEmail ? target.authEmail.replace(/@gabai\.app$/i, '') : '') || target.id) : (username || docId || '');
      const sId = target ? (target.schoolId || '') : '';

      document.getElementById('editStudentUsername').value = username || target?.username || '';
      document.getElementById('editStudentDocId').value = docId || target?.id || '';
      document.getElementById('editStudentHandleDisplay').textContent = uName ? `@${uName}` : 'Learner';
      document.getElementById('editStudentCampusBadge').textContent = sId ? getSchoolName(sId) : 'Assigned School';

      const editAvatarEl = document.getElementById('editStudentAvatarContainer');
      if (editAvatarEl) {
        const editPhoto = target ? (target.photoUrl || target.avatarUrl || target.profileImageUrl || '') : '';
        const editPend = target ? (target.pendingPhotoUrl || '') : '';
        const editStat = target ? (target.photoStatus || 'none') : 'none';
        const editInitials = target ? (((target.firstName?.[0] || '') + (target.lastName?.[0] || '')).toUpperCase() || 'ST') : 'ST';
        editAvatarEl.innerHTML = renderStudentAvatarHtml(editPhoto, editPend, editStat, editInitials, 'w-10 h-10', 'text-xs');
      }
      document.getElementById('editStudentFirstName').value = fName;
      document.getElementById('editStudentLastName').value = lName;

      const curGender = (target?.gender || target?.sex || '').toLowerCase();

      document.getElementById('editStudentGender').value = curGender.startsWith('m') ? 'male' : (curGender.startsWith('f') ? 'female' : 'unspecified');

      document.getElementById('modalEditStudentName').classList.remove('hidden');
    };

    window.closeEditStudentNameModal = function() {
      document.getElementById('modalEditStudentName').classList.add('hidden');
    };

    window.handleEditStudentNameSubmit = async function(e) {
      e.preventDefault();
      const fName = document.getElementById('editStudentFirstName').value.trim();
      const lName = document.getElementById('editStudentLastName').value.trim();
      const gender = document.getElementById('editStudentGender').value;
      const username = document.getElementById('editStudentUsername').value;
      const docId = document.getElementById('editStudentDocId').value;

      if (!fName || !lName) {
        window.showToast("Both first name and last name are required.", "error");
        return;
      }

      const fullName = `${fName} ${lName}`.trim();
      const saveBtn = document.getElementById('btnSaveStudentName');
      saveBtn.disabled = true;
      saveBtn.textContent = "Saving...";

      try {
        let updated = false;

        // 1. If in users collection (active student account)
        if (docId && cachedUsers.some(u => u.id === docId && u.role === 'student')) {
          await updateDoc(doc(db, "users", docId), {
            firstName: fName,
            lastName: lName,
            fullName: fullName,
            gender: gender
          });
          const u = cachedUsers.find(item => item.id === docId);
          if (u) {
            u.firstName = fName;
            u.lastName = lName;
            u.fullName = fullName;
          }
          updated = true;
        }

        // 2. If in pending_students collection (unclaimed account slip)
        if (!updated && username) {
          const pDoc = await getDoc(doc(db, "pending_students", username));
          if (pDoc.exists()) {
            await updateDoc(doc(db, "pending_students", username), {
              firstName: fName,
              lastName: lName,
              fullName: fullName
            });
            const p = cachedPendingStudents.find(item => item.username === username || item.id === username);
            if (p) {
              p.firstName = fName;
              p.lastName = lName;
              p.fullName = fullName;
            }
            updated = true;
          }
        }

        // 3. Fallback: query users by username
        if (!updated && username) {
          const q = query(collection(db, "users"), where("username", "==", username));
          const snap = await getDocs(q);
          if (!snap.empty) {
            await updateDoc(doc(db, "users", snap.docs[0].id), {
              firstName: fName,
              lastName: lName,
              fullName: fullName
            });
            const u = cachedUsers.find(item => item.id === snap.docs[0].id);
            if (u) {
              u.firstName = fName;
              u.lastName = lName;
              u.fullName = fullName;
            }
            updated = true;
          }
        }

        window.showToast(`Student name successfully updated to ${fullName}!`, "success");
        closeEditStudentNameModal();
        renderPendingStudentsTable();
        renderPerformanceTable();
      } catch (err) {
        console.error("Edit student name error:", err);
        window.showToast("Failed to update student name: " + err.message, "error");
      } finally {
        saveBtn.disabled = false;
        saveBtn.textContent = "Save Changes";
      }
    };

    window.resetStudentPassword = function(username, docId) {
      openResetPasskeyModal(username, docId);
    };

    window.deleteStudentAccount = function(username) {
      window.showToast("Institutional Safeguard: Administrative accounts cannot delete student records. Student accounts and data are preserved.", "warning");
    };

    // Helper: Safely resolve student's schoolId with class-section fallback
    window.resolveStudentSchoolId = function(student) {
      if (!student) return '';
      const direct = student.schoolId || student.school_id;
      if (direct && String(direct).trim() !== '' && String(direct).trim() !== 'null' && String(direct).trim() !== 'undefined') {
        return String(direct).trim();
      }
      const sec = String(student.section || '').trim().toLowerCase();
      if (sec && Array.isArray(cachedClasses) && cachedClasses.length > 0) {
        const matched = cachedClasses.find(c => {
          const cSec = String(c.section || c.className || c.name || '').trim().toLowerCase();
          return cSec === sec;
        });
        if (matched && matched.schoolId) {
          return String(matched.schoolId).trim();
        }
      }
      return '';
    };

    // Helper: Build unified student credential roster (claimed in users + unclaimed slips)
    function getUnifiedStudentCredentials() {
      // 1. Gather all active / claimed students from cachedUsers
      const claimed = (cachedUsers || [])
        .filter(u => u.role === 'student')
        .map(u => {
          const rawName = u.username || (u.authEmail ? u.authEmail.replace(/@gabai\.app$/i, '') : '') || (u.email ? u.email.split('@')[0] : '') || u.id;
          const uName = String(rawName).replace(/^@/, '').trim();
          const fName = u.firstName || u.first_name || '';
          const lName = u.lastName || u.last_name || '';
          const fullName = `${fName} ${lName}`.trim() || u.fullName || u.displayName || 'Learner';
          const gender = (typeof getStudentGender === 'function' ? getStudentGender(u) : (u.gender || u.sex || 'unspecified'));
          const studentSchool = resolveStudentSchoolId(u) || String(u.schoolId || u.school_id || '').trim();
          const pass = u.tempPassword || u.password || u.initialPassword || u.temp_password || '';

          return {
            id: u.id,
            firstName: fName,
            lastName: lName,
            fullName: fullName,
            gender: gender,
            username: uName,
            password: pass,
            schoolId: studentSchool,
            section: u.section || '',
            grade: u.grade || '',
            lrn: u.lrn || '',
            photoUrl: u.photoUrl || u.avatarUrl || u.profileImageUrl || '',
            pendingPhotoUrl: u.pendingPhotoUrl || '',
            photoStatus: u.photoStatus || (u.photoUrl ? 'approved' : 'none'),
            is_onboarded: true,
            isClaimed: true,
            mustChangePassword: u.mustChangePassword === true,
            tempPassword: u.tempPassword || '',
            createdAt: u.createdAt || null
          };
        });

      // Claimed identifiers index to eliminate duplicate stale slips
      const claimedIdentifiers = new Set();
      claimed.forEach(s => {
        const uLower = String(s.username || '').toLowerCase().trim();
        if (uLower) {
          claimedIdentifiers.add(uLower);
          claimedIdentifiers.add(`@${uLower}`);
        }
        if (s.id) {
          claimedIdentifiers.add(String(s.id).toLowerCase().trim());
        }
      });
      (cachedUsers || []).filter(u => u.role === 'student').forEach(u => {
        if (u.id) claimedIdentifiers.add(String(u.id).toLowerCase().trim());
        if (u.username) {
          const clean = String(u.username).toLowerCase().replace(/^@/, '').trim();
          claimedIdentifiers.add(clean);
          claimedIdentifiers.add(`@${clean}`);
        }
        if (u.authEmail) {
          claimedIdentifiers.add(String(u.authEmail).toLowerCase().replace(/@gabai\.app$/i, '').trim());
        }
      });

      // 2. Gather unclaimed slips from cachedPendingStudents (strictly students only!)
      const pending = (cachedPendingStudents || [])
        .filter(p => {
          if (p.role && p.role !== 'student') return false;
          if (p.isClaimed === true || p.isReset === true) return false;
          const pass = (p.password || p.tempPassword || p.initialPassword || '').trim();
          if (!pass) return false;
          const uName = String(p.username || p.id || '').toLowerCase().replace(/^@/, '').trim();
          return uName ? !claimedIdentifiers.has(uName) && !claimedIdentifiers.has(`@${uName}`) && !claimedIdentifiers.has(String(p.id).toLowerCase()) : true;
        })
        .map(p => {
          const fName = p.firstName || p.first_name || '';
          const lName = p.lastName || p.last_name || '';
          const fullName = `${fName} ${lName}`.trim() || p.fullName || 'Learner';
          const username = String(p.username || p.id || '').replace(/^@/, '').trim();
          const gender = (typeof getStudentGender === 'function' ? getStudentGender(p) : (p.gender || p.sex || 'unspecified'));
          const studentSchool = resolveStudentSchoolId(p) || String(p.schoolId || p.school_id || '').trim();
          const pass = p.password || p.tempPassword || p.initialPassword || '';

          return {
            id: p.id,
            firstName: fName,
            lastName: lName,
            fullName: fullName,
            gender: gender,
            username: username,
            password: pass,
            schoolId: studentSchool,
            section: p.section || '',
            grade: p.grade || '',
            lrn: p.lrn || '',
            photoUrl: p.photoUrl || p.avatarUrl || p.profileImageUrl || '',
            pendingPhotoUrl: p.pendingPhotoUrl || '',
            photoStatus: p.photoStatus || 'none',
            is_onboarded: false,
            isClaimed: false,
            mustChangePassword: true,
            tempPassword: pass,
            createdAt: p.createdAt || null
          };
        });

      // Place pending unclaimed slips first so administrators can easily spot new accounts needing slips
      return [...pending, ...claimed];
    }
    window.getUnifiedStudentCredentials = getUnifiedStudentCredentials;

    function renderPendingStudentsTable() {
      const tbody = document.getElementById('pendingStudentsTableBody');
      if (!tbody) return;

      try {
        let list = getUnifiedStudentCredentials();

        // Robust School Scoping with class section fallback
        if (isSchoolAdmin && currentAdminUser?.schoolId) {
          const targetSchool = String(currentAdminUser.schoolId).trim();
          list = list.filter(s => resolveStudentSchoolId(s) === targetSchool);
        } else {
          const activeSchool = (credentialSchoolFilter && credentialSchoolFilter !== 'ALL')
            ? credentialSchoolFilter
            : (selectedJurisdiction !== 'ALL' ? selectedJurisdiction : 'ALL');
          if (activeSchool !== 'ALL') {
            const targetSchool = String(activeSchool).trim();
            list = list.filter(s => resolveStudentSchoolId(s) === targetSchool);
          }
        }

        // Dedicated Grade Filter
        const activeCredGrade = credentialGradeFilter || document.getElementById('selectCredentialGradeFilter')?.value || 'ALL';
        if (activeCredGrade !== 'ALL') {
          const targetGradeNum = activeCredGrade.replace('Grade ', '').trim();
          list = list.filter(s => String(s.grade || '').trim() === targetGradeNum || (s.grade || '').trim() === activeCredGrade || `Grade ${s.grade}` === activeCredGrade);
        }

        // Dedicated Section Filter
        const activeCredSection = credentialSectionFilter || document.getElementById('selectCredentialSectionFilter')?.value || 'ALL';
        if (activeCredSection !== 'ALL') {
          list = list.filter(s => String(s.section || '').trim().toLowerCase() === activeCredSection.trim().toLowerCase());
        }

        // Dedicated Status Filter
        const activeCredStatus = credentialStatusFilter || document.getElementById('selectCredentialStatusFilter')?.value || 'ALL';
        if (activeCredStatus === 'PENDING') {
          list = list.filter(s => !s.isClaimed);
        } else if (activeCredStatus === 'APPROVED') {
          list = list.filter(s => !!s.isClaimed);
        }

        // Dedicated Smart Tokenized Search
        const inCred = document.getElementById('inputCredentialSearch');
        if (inCred && !credentialSearchQuery && inCred.value && (inCred.value.includes('@') || (currentAdminUser?.email && inCred.value.toLowerCase() === currentAdminUser.email.toLowerCase()))) {
          inCred.value = '';
          const btnClear = document.getElementById('btnClearCredentialSearch');
          if (btnClear) btnClear.classList.add('hidden');
        }
        const activeCredSearch = credentialSearchQuery || (inCred?.value || '').toLowerCase().trim();
        if (activeCredSearch) {
          list = list.filter(s => {
            const textBlob = `${s.firstName || ''} ${s.lastName || ''} ${s.username || ''} ${s.section || ''} ${s.lrn || ''} ${s.grade || ''} ${s.schoolId || ''} ${getSchoolName(s.schoolId)}`;
            return matchSmartSearch(textBlob, activeCredSearch);
          });
        }

        // Real-time Bento KPI Overview Updates
        const totalCredCount = list.length;
        const claimedCredCount = list.filter(s => !!s.isClaimed).length;
        const unclaimedCredCount = list.filter(s => !s.isClaimed).length;
        const pendingPhotoUsers = (cachedUsers || []).filter(u => u.role === 'student' && u.pendingPhotoUrl && u.photoStatus === 'pending');

        const elKpiTot = document.getElementById('credKpiTotal');
        if (elKpiTot) elKpiTot.textContent = totalCredCount;
        const elKpiClaim = document.getElementById('credKpiClaimed');
        if (elKpiClaim) elKpiClaim.textContent = claimedCredCount;
        const elKpiUnclaim = document.getElementById('credKpiUnclaimed');
        if (elKpiUnclaim) elKpiUnclaim.textContent = unclaimedCredCount;
        const elKpiPhotos = document.getElementById('credKpiPendingPhotos');
        if (elKpiPhotos) elKpiPhotos.textContent = pendingPhotoUsers.length;

        // Render Centralized Photo Moderation Queue Banner
        const photoBanner = document.getElementById('credentialPhotoQueueBanner');
        const photoGrid = document.getElementById('credentialPhotoQueueGrid');
        const photoBadge = document.getElementById('credentialPhotoPendingCountBadge');

        if (photoBanner && photoGrid) {
          if (pendingPhotoUsers.length > 0) {
            photoBanner.classList.remove('hidden');
            if (photoBadge) photoBadge.textContent = `${pendingPhotoUsers.length} Pending Approval`;
            photoGrid.innerHTML = pendingPhotoUsers.map(u => {
              const name = `${u.firstName || ''} ${u.lastName || ''}`.trim() || 'Learner';
              const sec = u.section ? `${u.grade ? 'Grade ' + u.grade + ' • ' : ''}${u.section}` : 'General';
              return `
                <div class="bg-white p-3 rounded-xl border border-purple-100 shadow-xs flex items-center gap-3">
                  <img src="${escapeHtml(u.pendingPhotoUrl)}" alt="Avatar" class="w-11 h-11 rounded-full object-cover ring-2 ring-purple-400 shrink-0 bg-slate-100" onerror="this.src='data:image/svg+xml,<svg xmlns=%22http://www.w3.org/2000/svg%22 viewBox=%220 0 24 24%22><text y=%2218%22 font-size=%2218%22>👤</text></svg>'">
                  <div class="flex-1 min-w-0">
                    <div class="font-bold text-xs text-slate-900 truncate">${escapeHtml(name)}</div>
                    <div class="text-[10px] text-slate-500 truncate">${escapeHtml(sec)}</div>
                    <div class="flex items-center gap-1.5 mt-1.5">
                      <button onclick="approveStudentPhoto('${u.id}')" class="px-2 py-0.5 bg-emerald-600 hover:bg-emerald-700 text-white rounded text-[10px] font-bold shadow-xs">Approve</button>
                      <button onclick="declineStudentPhoto('${u.id}')" class="px-2 py-0.5 bg-rose-50 hover:bg-rose-100 text-rose-700 border border-rose-200 rounded text-[10px] font-bold">Decline</button>
                    </div>
                  </div>
                </div>
              `;
            }).join('');
          } else {
            photoBanner.classList.add('hidden');
          }
        }

        const countBadge = document.getElementById('credentialCountSummaryBadge');
        if (countBadge) {
          countBadge.textContent = `Showing ${list.length} student account(s) (${unclaimedCredCount} pending slips)`;
        }

        if (list.length === 0) {
          const isFiltered = (selectedJurisdiction !== 'ALL' && !isSchoolAdmin) || credentialGradeFilter !== 'ALL' || credentialSectionFilter !== 'ALL' || credentialStatusFilter !== 'ALL' || credentialSchoolFilter !== 'ALL' || credentialSearchQuery !== '';
          tbody.innerHTML = `
            <tr>
              <td colspan="7" class="p-8 text-center text-slate-400 font-medium">
                ${isFiltered ? 'No student credentials match the active search/filters.' : 'No student accounts found.'}
                ${isFiltered ? '<button onclick="resetCredentialFilters()" class="block mx-auto mt-2 text-indigo-600 hover:underline font-bold text-xs">Reset all filters</button>' : '<span class="block mt-1 text-slate-400 text-xs">Student accounts are generated by teachers or claimed by learners in the mobile application.</span>'}
              </td>
            </tr>`;
          return;
        }

        tbody.innerHTML = list.map(s => {
          const fName = s.firstName || s.first_name || '';
          const lName = s.lastName || s.last_name || '';
          const fullName = `${fName} ${lName}`.trim() || s.fullName || 'Learner';
          let initials = ((fName[0] || '') + (lName[0] || '')).toUpperCase();
          if (!initials && fullName) {
            const parts = fullName.split(/\s+/).filter(Boolean);
            initials = parts.length > 1 ? (parts[0][0] + parts[parts.length - 1][0]).toUpperCase() : (parts[0] ? parts[0].slice(0, 2).toUpperCase() : 'ST');
          }
          if (!initials) initials = 'ST';
          const isClaimed = Boolean(s.isClaimed);
          const hasActiveResetPass = isClaimed && s.mustChangePassword && s.tempPassword;
          const genderIcon = s.gender === 'male' ? '<span class="text-blue-600 font-semibold text-[10px]">👦 Male</span>' : (s.gender === 'female' ? '<span class="text-pink-600 font-semibold text-[10px]">👧 Female</span>' : '');

          const passDisplay = hasActiveResetPass
            ? `<div class="flex items-center gap-1.5">
                 <span class="inline-flex items-center px-2 py-0.5 rounded bg-purple-50 text-purple-700 border border-purple-200 font-mono text-xs font-bold" title="Active Temporary Reset Password">${escapeHtml(s.tempPassword)}</span>
                 <button onclick="copyToClipboard('${escapeHtml(s.tempPassword)}', 'Temporary reset password copied!')" class="p-1 text-slate-400 hover:text-slate-600 rounded hover:bg-slate-100" title="Copy Temporary Reset Password">
                   <svg class="w-3 h-3" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect width="14" height="14" x="8" y="8" rx="2" ry="2"/><path d="M4 16c-1.1 0-2-.9-2-2V4c0-1.1.9-2 2-2h10c1.1 0 2 .9 2 2"/></svg>
                 </button>
               </div>`
            : (s.password && !isClaimed
                ? `<div class="flex items-center gap-1.5">
                     <span class="inline-flex items-center px-2 py-0.5 rounded bg-amber-50 text-amber-800 border border-amber-200 font-mono text-xs font-bold" title="Temporary Initial Password">${escapeHtml(s.password)}</span>
                     <button onclick="copyToClipboard('${escapeHtml(s.password)}', 'Temporary password copied!')" class="p-1 text-slate-400 hover:text-slate-600 rounded hover:bg-slate-100" title="Copy Temporary Password">
                       <svg class="w-3 h-3" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect width="14" height="14" x="8" y="8" rx="2" ry="2"/><path d="M4 16c-1.1 0-2-.9-2-2V4c0-1.1.9-2 2-2h10c1.1 0 2 .9 2 2"/></svg>
                     </button>
                   </div>`
                : `<span class="inline-flex items-center gap-1.5 px-2.5 py-1 rounded bg-slate-100 text-slate-600 font-mono text-xs font-bold" title="${isClaimed ? 'Protected: Password is encrypted and protected' : 'Protected'}">••••••••</span>`);

          const avatarHtml = renderStudentAvatarHtml(s.photoUrl, s.pendingPhotoUrl, s.photoStatus, initials, 'w-9 h-9', 'text-xs');
          const hasValidLrn = s.lrn && String(s.lrn).trim().toLowerCase() !== 'pending';
          const lrnHtml = hasValidLrn ? `<span>LRN: ${escapeHtml(s.lrn)}</span>` : '';
          const metaParts = [lrnHtml, genderIcon].filter(Boolean);
          const metaHtml = metaParts.length > 0 
            ? `<div class="text-[10px] text-slate-400 flex items-center gap-1.5 mt-0.5">${metaParts.join(' <span>&bull;</span> ')}</div>` 
            : '';

          return `
            <tr class="hover:bg-slate-50/80 transition-colors">
              <td class="p-3.5">
                <div class="flex items-center gap-2.5">
                  ${avatarHtml}
                  <div>
                    <div class="font-bold text-slate-900">${escapeHtml(fullName)}</div>
                    ${metaHtml}
                  </div>
                </div>
              </td>
              <td class="p-3.5">
                <div class="flex items-center gap-1">
                  <span class="font-mono text-slate-800 font-bold">@${escapeHtml(s.username || 'N/A')}</span>
                  <button onclick="copyToClipboard('${escapeHtml(s.username || '')}', 'Student ID @${escapeHtml(s.username || '')} copied!')" class="p-1 text-slate-400 hover:text-slate-600 rounded hover:bg-slate-100" title="Copy Username">
                    <svg class="w-3 h-3" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect width="14" height="14" x="8" y="8" rx="2" ry="2"/><path d="M4 16c-1.1 0-2-.9-2-2V4c0-1.1.9-2 2-2h10c1.1 0 2 .9 2 2"/></svg>
                  </button>
                </div>
              </td>
              <td class="p-3.5">
                ${passDisplay}
              </td>
              <td class="p-3.5 text-slate-700 font-medium">
                <div>${s.grade ? 'Grade ' + s.grade : 'Grade Level'}</div>
                <div class="text-[11px] text-slate-500 font-semibold">${escapeHtml(s.section || 'General')}</div>
              </td>
              <td class="p-3.5 text-slate-500">
                <div>${s.schoolId || 'N/A'}</div>
                <div class="text-[10px] text-slate-400 truncate max-w-[120px]">${escapeHtml(getSchoolName(s.schoolId))}</div>
              </td>
              <td class="p-3.5">
                ${isClaimed
                  ? '<span class="badge-verified px-2.5 py-0.5 rounded text-[10px] font-bold">Claimed in App</span>'
                  : '<span class="badge-pending px-2.5 py-0.5 rounded text-[10px] font-bold">Unclaimed / Temp Slip</span>'}
              </td>
              <td class="p-3.5 text-right">
                <div class="flex items-center justify-end gap-1.5">
                  <button onclick="openEditStudentNameModal('${escapeHtml(s.username)}', '${s.id || ''}')" class="px-2.5 py-1 bg-white border border-slate-300 hover:bg-slate-100 rounded-lg text-xs font-semibold text-slate-700 shadow-xs inline-flex items-center gap-1" title="Edit Student Profile">
                    ${uiIcon('edit', 'w-3 h-3')} <span>Edit</span>
                  </button>
                  <button onclick="printSingleCredentialSlip('${s.id}')" class="px-2.5 py-1 bg-indigo-50 hover:bg-indigo-100 text-indigo-700 border border-indigo-200 rounded-lg text-xs font-semibold shadow-xs inline-flex items-center gap-1" title="Print Student Login Slip">
                    <svg class="w-3 h-3" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M6 9V2h12v7"/><path d="M6 18H4a2 2 0 0 1-2-2v-5a2 2 0 0 1 2-2h16a2 2 0 0 1 2 2v5a2 2 0 0 1-2 2h-2"/><rect width="12" height="8" x="6" y="14"/></svg>
                    <span>Slip</span>
                  </button>
                  <button onclick="openResetPasskeyModal('${escapeHtml(s.username)}', '${s.id || ''}')" class="px-2.5 py-1 bg-purple-50 hover:bg-purple-100 text-[#5341CD] border border-purple-200 rounded-lg text-xs font-bold shadow-xs inline-flex items-center gap-1" title="Generate Temporary Password">
                    ${uiIcon('key', 'w-3 h-3')} <span>Reset</span>
                  </button>
                </div>
              </td>
            </tr>
          `;
        }).join('');
      } catch (err) {
        console.error("Failed to render student credentials table:", err);
        tbody.innerHTML = `
          <tr>
            <td colspan="7" class="p-8 text-center text-rose-500 font-semibold">
              Failed to load student accounts: ${escapeHtml(err.message)}
            </td>
          </tr>`;
      }
    }
    window.renderPendingStudentsTable = renderPendingStudentsTable;


    // =========================================================================
    // BATCH & SINGLE LOGIN SLIPS PRINTING ENGINE
    // =========================================================================
    window.openBatchPrintSlipsModal = function() {
      let list = getUnifiedStudentCredentials();
      if (isSchoolAdmin && currentAdminUser?.schoolId) {
        const targetSchool = String(currentAdminUser.schoolId).trim();
        list = list.filter(s => resolveStudentSchoolId(s) === targetSchool);
      } else {
        const activeSchool = (credentialSchoolFilter && credentialSchoolFilter !== 'ALL')
          ? credentialSchoolFilter
          : (selectedJurisdiction !== 'ALL' ? selectedJurisdiction : 'ALL');
        if (activeSchool !== 'ALL') {
          const targetSchool = String(activeSchool).trim();
          list = list.filter(s => resolveStudentSchoolId(s) === targetSchool);
        }
      }

      const activeCredGrade = credentialGradeFilter || document.getElementById('selectCredentialGradeFilter')?.value || 'ALL';
      if (activeCredGrade !== 'ALL') {
        const targetGradeNum = activeCredGrade.replace('Grade ', '').trim();
        list = list.filter(s => String(s.grade || '').trim() === targetGradeNum || (s.grade || '').trim() === activeCredGrade || `Grade ${s.grade}` === activeCredGrade);
      }

      const activeCredSection = credentialSectionFilter || document.getElementById('selectCredentialSectionFilter')?.value || 'ALL';
      if (activeCredSection !== 'ALL') {
        list = list.filter(s => String(s.section || '').trim().toLowerCase() === activeCredSection.trim().toLowerCase());
      }

      const activeCredStatus = credentialStatusFilter || document.getElementById('selectCredentialStatusFilter')?.value || 'ALL';
      if (activeCredStatus === 'PENDING') {
        list = list.filter(s => !s.isClaimed);
      } else if (activeCredStatus === 'APPROVED') {
        list = list.filter(s => !!s.isClaimed);
      }

      if (list.length === 0) {
        window.showToast("No student accounts match active filters for printable slips.", "warning");
        return;
      }

      const container = document.getElementById('batchPrintSlipsContainer');
      const meta = document.getElementById('batchPrintSlipsMeta');
      if (meta) meta.textContent = `Preparing ${list.length} student login slip(s) for printing`;

      if (container) {
        container.innerHTML = `
          <div class="grid grid-cols-1 md:grid-cols-2 gap-4" id="slipsPrintableGrid">
            ${list.map(s => renderSlipCardHtml(s)).join('')}
          </div>
        `;
      }

      document.getElementById('modalBatchPrintSlips')?.classList.remove('hidden');
    };

    window.closeBatchPrintSlipsModal = function() {
      document.getElementById('modalBatchPrintSlips')?.classList.add('hidden');
    };

    window.printSingleCredentialSlip = function(studentId) {
      const list = getUnifiedStudentCredentials();
      const s = list.find(item => item.id === studentId || item.username === studentId);
      if (!s) {
        window.showToast("Student account not found.", "error");
        return;
      }

      const container = document.getElementById('batchPrintSlipsContainer');
      const meta = document.getElementById('batchPrintSlipsMeta');
      if (meta) meta.textContent = `Single login slip for ${s.fullName || 'Learner'}`;

      if (container) {
        container.innerHTML = `
          <div class="max-w-md mx-auto" id="slipsPrintableGrid">
            ${renderSlipCardHtml(s)}
          </div>
        `;
      }

      document.getElementById('modalBatchPrintSlips')?.classList.remove('hidden');
    };

    function renderSlipCardHtml(s) {
      const isClaimed = Boolean(s.isClaimed);
      const passText = isClaimed
        ? (s.mustChangePassword && s.tempPassword ? s.tempPassword : 'Protected (Already Claimed)')
        : (s.password || 'Pending');
      const schoolName = getSchoolName(s.schoolId) || 'Junior High School';

      return `
        <div class="print-slip" style="border: 1.5px dashed #94A3B8; border-radius: 14px; padding: 14px; background: #FFFFFF; color: #0F172A; page-break-inside: avoid; margin-bottom: 8px;">
          <div style="display: flex; align-items: center; justify-content: space-between; border-bottom: 1px solid #E2E8F0; padding-bottom: 8px; margin-bottom: 8px;">
            <div style="font-weight: 800; font-size: 11px; color: #5341CD; text-transform: uppercase; letter-spacing: 0.5px;">
              GabAI Literacy Platform &bull; Academic Division Portal
            </div>
            <span style="font-size: 9px; font-weight: 700; background: #F1F5F9; color: #475569; padding: 2px 6px; border-radius: 4px;">STUDENT SLIP</span>
          </div>

          <div style="display: flex; align-items: center; gap: 10px; margin-bottom: 6px;">
            ${s.photoUrl ? `<img src="${escapeHtml(s.photoUrl)}" style="width: 32px; height: 32px; border-radius: 50%; object-fit: cover; border: 1px solid #CBD5E1;" onerror="this.style.display='none'" />` : `<div style="width: 32px; height: 32px; border-radius: 50%; background: #EEF2FF; color: #4F46E5; font-weight: 800; font-size: 11px; display: flex; align-items: center; justify-content: center; border: 1px solid #C7D2FE;">${escapeHtml(((s.firstName?.[0] || '') + (s.lastName?.[0] || '')).toUpperCase() || 'ST')}</div>`}
            <div>
              <div style="font-size: 13px; font-weight: 800; color: #0F172A;">${escapeHtml(s.fullName || 'Student Learner')}</div>
              <div style="font-size: 10px; color: #64748B;">${escapeHtml(schoolName)} &bull; ${s.grade ? 'Grade ' + s.grade + ' - ' : ''}${escapeHtml(s.section || 'General')}</div>
            </div>
          </div>

          <div style="background: #F8FAFC; border: 1px solid #E2E8F0; border-radius: 8px; padding: 8px 10px; margin: 8px 0; display: flex; justify-content: space-between; align-items: center;">
            <div>
              <div style="font-size: 9px; font-weight: 700; color: #64748B; text-transform: uppercase;">Username (User ID)</div>
              <div style="font-family: monospace; font-size: 12px; font-weight: 800; color: #1E293B;">@${escapeHtml(s.username || 'username')}</div>
            </div>
            <div style="text-align: right;">
              <div style="font-size: 9px; font-weight: 700; color: #64748B; text-transform: uppercase;">Initial Password</div>
              <div style="font-family: monospace; font-size: 12px; font-weight: 800; color: #4F46E5;">${escapeHtml(passText)}</div>
            </div>
          </div>

          <div style="font-size: 9px; color: #64748B; line-height: 1.4; border-top: 1px dashed #E2E8F0; padding-top: 6px;">
            <strong>Login Guide:</strong> 1. Open GabAI app &rarr; 2. Tap "Student Login" &rarr; 3. Enter username &amp; password above &rarr; 4. Set personal password on first login.
          </div>
          <div style="text-align: center; font-size: 10px; color: #94A3B8; margin-top: 6px; font-family: monospace;">✂ - - - - - - - - - - - - - - - - - - - - - - - - - - - - ✂</div>
        </div>
      `;
    }

    window.executeBatchSlipsPrint = function() {
      const grid = document.getElementById('slipsPrintableGrid');
      if (!grid) {
        window.print();
        return;
      }

      const existing = document.getElementById('printReportContainer');
      if (existing) existing.remove();

      const container = document.createElement('div');
      container.id = 'printReportContainer';
      container.style.padding = '16px';
      container.style.backgroundColor = '#FFFFFF';
      container.innerHTML = `
        <style>
          @media print {
            body * { visibility: hidden !important; }
            #printReportContainer, #printReportContainer * { visibility: visible !important; }
            #printReportContainer { position: absolute !important; left: 0 !important; top: 0 !important; width: 100% !important; }
            .print-grid { display: grid !important; grid-template-columns: 1fr 1fr !important; gap: 12px !important; }
          }
        </style>
        <div class="print-grid">
          ${grid.innerHTML}
        </div>
      `;
      document.body.appendChild(container);

      setTimeout(() => {
        window.print();
        setTimeout(() => container.remove(), 1000);
      }, 250);
    };


window.renderPerformanceTable = renderPerformanceTable;
window.getUnifiedStudentCredentials = getUnifiedStudentCredentials;
window.renderPendingStudentsTable = renderPendingStudentsTable;
window.renderSlipCardHtml = renderSlipCardHtml;
