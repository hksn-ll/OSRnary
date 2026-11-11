    // =========================================================================
    // 2. CLASSROOM SECTIONS & JOIN CODE ENGINE
    // =========================================================================
    window.resolveClassJoinCode = function(c) {
      if (c && c.joinCode && String(c.joinCode).trim() !== '' && String(c.joinCode).trim().toUpperCase() !== 'NONE') {
        return String(c.joinCode).trim().toUpperCase();
      }
      const adviser = (cachedUsers || []).find(u => u.id === (c?.teacherId || c?.adviserId));
      if (adviser && adviser.joinCode && String(adviser.joinCode).trim() !== '') {
        return String(adviser.joinCode).trim().toUpperCase();
      }
      if (c && c.id) {
        const cleanId = c.id.replace(/[^A-Za-z0-9]/g, '').toUpperCase();
        return ('SEC' + (cleanId.length >= 3 ? cleanId.substring(0, 3) : '7K9')).substring(0, 6);
      }
      return 'SEC' + Math.random().toString(36).substring(2, 5).toUpperCase();
    };

    window.openSectionQrModal = function(classId) {
      const c = (cachedClasses || []).find(item => item.id === classId);
      if (!c) return;
      const joinCode = resolveClassJoinCode(c);
      const secName = c.section || c.className || 'Section';
      const gradeText = c.grade ? (String(c.grade).startsWith('Grade') ? c.grade : `Grade ${c.grade}`) : 'Grade 7';
      const schoolName = getSchoolName(c.schoolId);

      currentQrModalData = { classId, joinCode, secName, gradeText, schoolName };

      document.getElementById('qrModalSectionTitle').textContent = `${secName} (${gradeText})`;
      document.getElementById('qrModalSchoolTitle').textContent = `${schoolName} • Join Code Presentation`;
      document.getElementById('qrModalCodeText').textContent = `#${joinCode}`;

      const qrContainer = document.getElementById('qrCodeContainer');
      const qrData = encodeURIComponent(joinCode);
      qrContainer.innerHTML = `
        <div class="relative inline-block p-3 bg-white rounded-2xl border border-slate-200 shadow-sm">
          <img src="https://api.qrserver.com/v1/create-qr-code/?size=220x220&data=${qrData}" alt="QR Code" class="w-48 h-48 rounded-xl object-contain" onerror="this.parentElement.innerHTML='<div class=\'w-48 h-48 bg-indigo-50 text-indigo-700 flex flex-col items-center justify-center font-mono font-bold p-4 text-center rounded-xl\'><span class=\'text-3xl mb-2\'>📱</span><span class=\'text-lg\'>#${joinCode}</span><span class=\'text-[10px] text-slate-500 font-sans mt-2\'>Enter code in GabAI app</span></div>'" />
        </div>
      `;

      document.getElementById('modalSectionQrCode').classList.remove('hidden');
    };

    window.closeSectionQrModal = function() {
      document.getElementById('modalSectionQrCode').classList.add('hidden');
      currentQrModalData = null;
    };

    window.copyCurrentQrModalCode = function() {
      if (currentQrModalData && currentQrModalData.joinCode) {
        copyToClipboard(currentQrModalData.joinCode, `Section join code #${currentQrModalData.joinCode} copied!`);
      }
    };

    window.printSectionQrCode = function() {
      if (!currentQrModalData) return;
      const { joinCode, secName, gradeText, schoolName } = currentQrModalData;

      const existing = document.getElementById('printReportContainer');
      if (existing) existing.remove();

      const container = document.createElement('div');
      container.id = 'printReportContainer';
      container.innerHTML = `
        <style>
          @media print {
            body * { visibility: hidden !important; }
            #printReportContainer, #printReportContainer * { visibility: visible !important; }
            #printReportContainer { position: absolute !important; left: 0 !important; top: 0 !important; width: 100% !important; display: flex !important; justify-content: center !important; padding-top: 40px !important; }
            .qr-slip-card { border: 2px dashed #4F46E5 !important; border-radius: 20px !important; padding: 32px !important; text-align: center !important; max-width: 380px !important; }
          }
        </style>
        <div class="qr-slip-card">
          <div style="font-size: 13px; color: #64748B; font-weight: 600; text-transform: uppercase; margin-bottom: 4px;">${escapeHtml(schoolName)}</div>
          <h1 style="font-size: 22px; font-weight: 800; color: #0F172A; margin: 0 0 16px 0;">${escapeHtml(gradeText)} — ${escapeHtml(secName)}</h1>
          <div style="padding: 12px; background: #EEF2FF; border-radius: 16px; display: inline-block; margin-bottom: 16px;">
            <img src="https://api.qrserver.com/v1/create-qr-code/?size=220x220&data=${encodeURIComponent(joinCode)}" alt="QR Code" style="width: 220px; height: 220px; display: block; border-radius: 10px;" />
          </div>
          <div style="font-size: 11px; text-transform: uppercase; font-weight: 700; color: #64748B; letter-spacing: 0.05em;">Classroom Join Code</div>
          <div style="font-family: ui-monospace, monospace; font-size: 28px; font-weight: 900; color: #4F46E5; letter-spacing: 0.1em; margin: 4px 0 12px 0;">#${escapeHtml(joinCode)}</div>
          <p style="font-size: 12px; color: #475569; line-height: 1.4; margin: 0;">Open the <strong>GabAI App</strong> &rarr; Tap <strong>Join Class</strong> &rarr; Scan this QR or enter code <strong>#${escapeHtml(joinCode)}</strong>.</p>
        </div>
      `;
      document.body.appendChild(container);
      document.body.classList.add('is-printing');

      setTimeout(() => {
        window.print();
        setTimeout(() => {
          document.body.classList.remove('is-printing');
          container.remove();
        }, 800);
      }, 100);
    };

    window.generateRandomJoinCode = function() {
      const code = Math.random().toString(36).substring(2, 8).toUpperCase();
      const el = document.getElementById('sectionJoinCodeInput');
      if (el) el.value = code;
    };

    window.openCreateSectionModal = function() {
      window.showToast("Classroom sections are created exclusively by Class Advisers in the GabAI mobile application.", "warning");
    };

    window.openEditSectionModal = function(id) {
      window.showToast("Classroom sections are authored by faculty in the mobile app. Administrators inspect rosters and view section join codes in read-only mode.", "warning");
    };

    window.closeSectionModal = function() {};

    window.handleSectionFormSubmit = function(e) {
      e?.preventDefault();
    };

    window.deleteSection = function(id, name) {
      window.showToast("Institutional Safeguard: Neither School Admins nor Super Admins have permission to delete classroom sections. Sections and enrollments are managed by faculty in the mobile app.", "warning");
    };

    window.copyCurrentInspectCode = function() {
      if (activeInspectClassId) {
        const c = cachedClasses.find(item => item.id === activeInspectClassId);
        if (c) {
          const code = resolveClassJoinCode(c);
          copyToClipboard(code, `Section join code #${code} copied!`);
        }
      }
    };

    window.openQrFromInspector = function() {
      if (activeInspectClassId) {
        openSectionQrModal(activeInspectClassId);
      }
    };

    window.inspectClassRoster = function(classId) {
      activeInspectClassId = classId;
      const c = cachedClasses.find(item => item.id === classId);
      if (!c) return;

      const secName = c.section || c.className || 'Class';
      const joinCode = resolveClassJoinCode(c);
      const gradeText = c.grade ? (String(c.grade).startsWith('Grade') ? c.grade : `Grade ${c.grade}`) : 'Grade 7';
      const schoolName = getSchoolName(c.schoolId);

      const adviser = (cachedUsers || []).find(u => u.id === (c.teacherId || c.adviserId));
      const adviserName = adviser ? `${adviser.firstName || ''} ${adviser.lastName || ''}`.trim() : (c.adviserName || 'Class Adviser');

      document.getElementById('inspectSectionName').textContent = `Section: ${secName}`;
      document.getElementById('inspectJoinCodeBadge').textContent = `#${joinCode}`;
      document.getElementById('inspectSchoolAndGrade').textContent = `${schoolName} • ${gradeText} • Adviser: ${adviserName}`;

      const initiationCount = c.initiation_items || 5;
      const elInit = document.getElementById('inspectInitiationCount');
      if (elInit) elInit.textContent = `${initiationCount} Items`;

      const joinedIds = c.studentIds || c.joinedStudents || [];
      const enrolledStudents = cachedUsers.filter(u => u.role === 'student' && (joinedIds.includes(u.id) || (u.section === secName && String(u.schoolId) === String(c.schoolId))));
      document.getElementById('inspectEnrolledCount').textContent = enrolledStudents.length;

      // Calculate Section Academic MPS and Phil-IRI Distribution
      let secScoreSum = 0;
      let secScoreCount = 0;
      let indCount = 0;
      let insCount = 0;
      let fruCount = 0;

      enrolledStudents.forEach(st => {
        const subs = (cachedSubmissions || []).filter(s => s.studentId === st.id);
        const avg = calculateAverageScore(subs);
        const rawTier = (st.phil_iri_tier || st.diagnostic_tier || '').toLowerCase().trim();

        if (rawTier.includes('independent') || (subs.length > 0 && avg >= 80)) {
          indCount++;
        } else if (rawTier.includes('instructional') || (subs.length > 0 && avg >= 60)) {
          insCount++;
        } else if (rawTier.includes('frustration') || (subs.length > 0 && avg > 0 && avg < 60) || (st.diagnostic_pct && st.diagnostic_pct < 60)) {
          fruCount++;
        }

        const effectiveMps = subs.length > 0 ? avg : (st.diagnostic_pct || (avg > 0 ? avg : null));
        if (effectiveMps !== null && effectiveMps !== undefined) {
          secScoreSum += effectiveMps;
          secScoreCount++;
        }
      });

      const sectionMps = secScoreCount > 0 ? Math.round(secScoreSum / secScoreCount) : 0;
      const elMps = document.getElementById('inspectSectionMps');
      if (elMps) {
        elMps.textContent = `${sectionMps}%`;
        elMps.className = `text-lg font-black mt-0.5 ${sectionMps >= 80 ? 'text-emerald-600' : (sectionMps >= 60 ? 'text-amber-600' : 'text-rose-600')}`;
      }

      const elTiers = document.getElementById('inspectSectionTiers');
      if (elTiers) {
        elTiers.innerHTML = `Ind: <span class="text-emerald-600 font-bold">${indCount}</span> • Ins: <span class="text-amber-600 font-bold">${insCount}</span> • Fru: <span class="text-rose-600 font-bold">${fruCount}</span>`;
      }

      const enrolledContainer = document.getElementById('inspectEnrolledList');
      if (enrolledStudents.length === 0) {
        enrolledContainer.innerHTML = `<div class="p-3 text-slate-400 text-center">No active learners enrolled yet.</div>`;
      } else {
        enrolledContainer.innerHTML = enrolledStudents.map(s => {
          const subs = (cachedSubmissions || []).filter(sub => sub.studentId === s.id);
          const avg = calculateAverageScore(subs);
          const rawTier = (s.phil_iri_tier || s.diagnostic_tier || '').toLowerCase().trim();
          let tierBadge = '<span class="px-2 py-0.5 rounded text-[9px] font-bold bg-slate-100 text-slate-600">Pending</span>';

          if (rawTier.includes('independent') || (subs.length > 0 && avg >= 80)) {
            tierBadge = '<span class="px-2 py-0.5 rounded text-[9px] font-bold bg-emerald-50 text-emerald-700 border border-emerald-200">Independent</span>';
          } else if (rawTier.includes('instructional') || (subs.length > 0 && avg >= 60)) {
            tierBadge = '<span class="px-2 py-0.5 rounded text-[9px] font-bold bg-amber-50 text-amber-700 border border-amber-200">Instructional</span>';
          } else if (rawTier.includes('frustration') || (subs.length > 0 && avg > 0 && avg < 60) || (s.diagnostic_pct && s.diagnostic_pct < 60)) {
            tierBadge = '<span class="px-2 py-0.5 rounded text-[9px] font-bold bg-rose-50 text-rose-700 border border-rose-200">Frustration</span>';
          }

          const stMps = subs.length > 0 ? `${avg}% MPS` : (s.diagnostic_pct ? `${s.diagnostic_pct}% MPS` : '—');

          return `
            <div class="py-2.5 flex items-center justify-between">
              <div>
                <div class="font-bold text-slate-800">${escapeHtml(s.firstName || '')} ${escapeHtml(s.lastName || '')}</div>
                <div class="text-[10px] text-slate-400 font-mono flex items-center gap-1.5 mt-0.5">
                  <span>@${escapeHtml(s.username || s.id.substring(0,6))}</span>
                  <span>&bull;</span>
                  <span class="text-indigo-600 font-bold">${stMps}</span>
                </div>
              </div>
              <div class="flex items-center gap-2">
                ${tierBadge}
                <span class="font-bold text-indigo-700 text-[10px]">Lvl ${s.level || 1}</span>
                <button onclick="viewStudentQuizHistory('${s.id}')" class="px-2 py-1 bg-white hover:bg-slate-50 border border-slate-300 rounded-lg text-[10px] font-semibold text-slate-700 transition shadow-2xs">
                  Performance &rarr;
                </button>
              </div>
            </div>
          `;
        }).join('');
      }

      const pendingInSec = cachedPendingStudents.filter(s => s.section === secName && String(s.schoolId) === String(c.schoolId));
      document.getElementById('inspectPendingCount').textContent = pendingInSec.length;

      const pendingContainer = document.getElementById('inspectPendingList');
      if (pendingInSec.length === 0) {
        pendingContainer.innerHTML = `<div class="p-3 text-slate-400 text-center">No pending batch accounts for this section.</div>`;
      } else {
        pendingContainer.innerHTML = pendingInSec.map(s => `
          <div class="py-2 flex items-center justify-between">
            <div>
              <span class="font-bold text-slate-800">${escapeHtml(s.firstName || '')} ${escapeHtml(s.lastName || '')}</span>
              <div class="font-mono text-[10px] text-slate-500">User: @${escapeHtml(s.username)} | Password: •••••••• (Protected)</div>
            </div>
            <span class="badge-pending px-2 py-0.5 rounded text-[10px] font-bold">Unclaimed Slip</span>
          </div>
        `).join('');
      }

      document.getElementById('modalClassInspector').classList.remove('hidden');
    };

    window.closeClassInspector = function() {
      document.getElementById('modalClassInspector').classList.add('hidden');
    };

    window.exportSingleSectionRosterPDF = function(classId) {
      const c = cachedClasses.find(item => item.id === classId);
      if (!c) {
        window.showToast("Classroom section not found.", "error");
        return;
      }

      const secName = c.section || c.className || 'Section';
      const joinCode = resolveClassJoinCode(c);
      const gradeText = c.grade ? (String(c.grade).startsWith('Grade') ? c.grade : `Grade ${c.grade}`) : 'Grade 7';
      const schoolName = getSchoolName(c.schoolId);

      const adviser = (cachedUsers || []).find(u => u.id === (c.teacherId || c.adviserId));
      const adviserName = adviser ? `${adviser.firstName || ''} ${adviser.lastName || ''}`.trim() : (c.adviserName || 'Class Adviser');

      const joinedIds = c.studentIds || c.joinedStudents || [];
      const enrolled = cachedUsers.filter(u => u.role === 'student' && (joinedIds.includes(u.id) || (u.section === secName && String(u.schoolId) === String(c.schoolId))));
      const pending = cachedPendingStudents.filter(s => s.section === secName && String(s.schoolId) === String(c.schoolId));

      let secScoreSum = 0;
      let secScoreCount = 0;
      let indCount = 0;
      let insCount = 0;
      let fruCount = 0;

      const rows = [];
      enrolled.forEach((st, idx) => {
        const subs = (cachedSubmissions || []).filter(s => s.studentId === st.id);
        const avg = calculateAverageScore(subs);
        const rawTier = (st.phil_iri_tier || st.diagnostic_tier || '').toLowerCase().trim();
        let tierLabel = 'Unassessed';

        if (rawTier.includes('independent') || (subs.length > 0 && avg >= 80)) {
          tierLabel = 'Independent';
          indCount++;
        } else if (rawTier.includes('instructional') || (subs.length > 0 && avg >= 60)) {
          tierLabel = 'Instructional';
          insCount++;
        } else if (rawTier.includes('frustration') || (subs.length > 0 && avg > 0 && avg < 60) || (st.diagnostic_pct && st.diagnostic_pct < 60)) {
          tierLabel = 'Frustration';
          fruCount++;
        }

        const effectiveMps = subs.length > 0 ? avg : (st.diagnostic_pct || (avg > 0 ? avg : null));
        if (effectiveMps !== null && effectiveMps !== undefined) {
          secScoreSum += effectiveMps;
          secScoreCount++;
        }

        const mpsDisplay = effectiveMps !== null ? `${effectiveMps}%` : '—';
        const stName = `${st.firstName || ''} ${st.lastName || ''}`.trim() || st.username || 'Learner';
        rows.push([idx + 1, stName, `@${st.username || st.id.substring(0,6)}`, `Lvl ${st.level || 1}`, mpsDisplay, tierLabel, "Claimed Active"]);
      });

      pending.forEach((p, idx) => {
        const pName = `${p.firstName || ''} ${p.lastName || ''}`.trim() || p.username || 'Learner';
        rows.push([enrolled.length + idx + 1, pName, `@${p.username}`, "Lvl 1", "—", "Pending Evaluation", "Unclaimed Slip"]);
      });

      const sectionMps = secScoreCount > 0 ? Math.round(secScoreSum / secScoreCount) : 0;

      window.printReportDoc({
        title: `Official Section Roster & Reading Performance — ${secName}`,
        subtitle: `${schoolName} • ${gradeText} • Section Join Code: #${joinCode} • Adviser: ${adviserName}`,
        scopeBadge: `${secName} (${gradeText})`,
        kpis: [
          { label: "Total Learners", value: enrolled.length + pending.length },
          { label: "Active Claimed", value: enrolled.length },
          { label: "Unclaimed Slips", value: pending.length },
          { label: "Section MPS", value: `${sectionMps}%` },
          { label: "Literacy Spread", value: `Ind: ${indCount} | Ins: ${insCount} | Fru: ${fruCount}` }
        ],
        headers: [
          { label: "#", align: "left" },
          { label: "Learner Name", align: "left" },
          { label: "Username", align: "left" },
          { label: "Level", align: "left" },
          { label: "Comprehension MPS", align: "right" },
          { label: "Phil-IRI Tier", align: "left" },
          { label: "Account Status", align: "left" }
        ],
        rows: rows,
        signatories: [
          { role: "Class Homeroom Adviser", name: adviserName },
          { role: "School Administrator / Principal", name: `${currentAdminUser?.firstName || ''} ${currentAdminUser?.lastName || ''}`.trim() || 'School Head' }
        ]
      });
    };

    window.exportSingleSectionRosterCSV = function(classId) {
      const c = cachedClasses.find(item => item.id === classId);
      if (!c) return;

      const secName = c.section || c.className || 'Section';
      const joinCode = resolveClassJoinCode(c);
      const gradeText = c.grade ? (String(c.grade).startsWith('Grade') ? c.grade : `Grade ${c.grade}`) : 'Grade 7';
      const schoolName = getSchoolName(c.schoolId);

      const adviser = (cachedUsers || []).find(u => u.id === (c.teacherId || c.adviserId));
      const adviserName = adviser ? `${adviser.firstName || ''} ${adviser.lastName || ''}`.trim() : (c.adviserName || 'Class Adviser');

      const joinedIds = c.studentIds || c.joinedStudents || [];
      const enrolled = cachedUsers.filter(u => u.role === 'student' && (joinedIds.includes(u.id) || (u.section === secName && String(u.schoolId) === String(c.schoolId))));
      const pending = cachedPendingStudents.filter(s => s.section === secName && String(s.schoolId) === String(c.schoolId));

      let csv = "﻿# Classroom Section Roster & Academic Performance\n";
      csv += `# Section: ${secName} (${gradeText}) | Join Code: #${joinCode} | School: ${schoolName} | Adviser: ${adviserName}\n\n`;
      csv += "Number,Learner Name,Username,Grade,Section,Level,Current Streak (Days),Comprehension MPS (%),Phil-IRI Reading Tier,Account Status\n";

      enrolled.forEach((st, idx) => {
        const subs = (cachedSubmissions || []).filter(s => s.studentId === st.id);
        const avg = calculateAverageScore(subs);
        const rawTier = (st.phil_iri_tier || st.diagnostic_tier || '').toLowerCase().trim();
        let tierLabel = 'Unassessed';

        if (rawTier.includes('independent') || (subs.length > 0 && avg >= 80)) {
          tierLabel = 'Independent';
        } else if (rawTier.includes('instructional') || (subs.length > 0 && avg >= 60)) {
          tierLabel = 'Instructional';
        } else if (rawTier.includes('frustration') || (subs.length > 0 && avg > 0 && avg < 60) || (st.diagnostic_pct && st.diagnostic_pct < 60)) {
          tierLabel = 'Frustration';
        }

        const effectiveMps = subs.length > 0 ? avg : (st.diagnostic_pct || (avg > 0 ? avg : 'N/A'));
        const stName = `"${(`${st.firstName || ''} ${st.lastName || ''}`.trim() || st.username || 'Learner').replace(/"/g, '""')}"`;
        csv += `${idx + 1},${stName},${st.username || st.id.substring(0,6)},${st.grade || c.grade || 7},${secName},${st.level || 1},${st.current_streak || 0},${effectiveMps},${tierLabel},Active\n`;
      });

      pending.forEach((p, idx) => {
        const pName = `"${(`${p.firstName || ''} ${p.lastName || ''}`.trim() || p.username || 'Learner').replace(/"/g, '""')}"`;
        csv += `${enrolled.length + idx + 1},${pName},${p.username},${p.grade || c.grade || 7},${secName},1,0,N/A,Pending Evaluation,Unclaimed Slip\n`;
      });

      const blob = new Blob([csv], { type: "text/csv;charset=utf-8;" });
      const url = URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.setAttribute("href", url);
      link.setAttribute("download", `Section_Roster_${secName.replace(/\s+/g, '_')}_${joinCode}.csv`);
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      window.showToast(`Exported CSV for Section ${secName}.`, "success");
    };

    function renderSectionsTable() {
      const tbody = document.getElementById('sectionsTableBody');
      if (!tbody) return;

      let list = cachedClasses || [];

      if (selectedJurisdiction !== 'ALL') {
        list = list.filter(c => String(c.schoolId) === selectedJurisdiction);
      }

      // Dedicated School Filter
      if (isSchoolAdmin && currentAdminUser?.schoolId) {
        list = list.filter(c => String(c.schoolId) === String(currentAdminUser.schoolId));
      } else if (sectionSchoolFilter !== 'ALL') {
        list = list.filter(c => String(c.schoolId) === String(sectionSchoolFilter));
      }

      // Dedicated Grade Filter
      const activeGradeFilter = sectionGradeFilter || document.getElementById('selectSectionGradeFilter')?.value || 'ALL';
      if (activeGradeFilter !== 'ALL') {
        const targetGradeNum = activeGradeFilter.replace('Grade ', '').trim();
        list = list.filter(c => String(c.grade || '').trim() === targetGradeNum || (c.grade || '').trim() === activeGradeFilter || `Grade ${c.grade}` === activeGradeFilter);
      }

      // Dedicated Smart Tokenized Search
      const activeSectionSearch = sectionSearchQuery || (document.getElementById('inputSectionSearch')?.value || '').toLowerCase().trim();
      if (activeSectionSearch) {
        list = list.filter(c => {
          const joinCode = resolveClassJoinCode(c);
          const adviser = (cachedUsers || []).find(u => u.id === (c.teacherId || c.adviserId));
          const adviserName = adviser ? `${adviser.firstName || ''} ${adviser.lastName || ''}`.trim() : (c.adviserName || '');
          const textBlob = `${c.section || c.className || ''} ${joinCode} Grade ${c.grade || ''} ${adviserName} ${getSchoolName(c.schoolId)}`;
          return matchSmartSearch(textBlob, activeSectionSearch);
        });
      }

      const countDisplay = document.getElementById('sectionsCountDisplay');
      if (countDisplay) countDisplay.textContent = list.length;

      // Auto-backfill join code to Firestore & calculate section telemetry
      let totalLearners = 0;
      let totalActive = 0;
      let totalPending = 0;
      let totalMpsSum = 0;
      let evaluatedSectionsCount = 0;

      list.forEach(c => {
        // Auto-heal missing or NONE joinCode in Firestore
        if (!c.joinCode || String(c.joinCode).trim().toUpperCase() === 'NONE') {
          const code = resolveClassJoinCode(c);
          c.joinCode = code;
          if (typeof updateDoc === 'function' && db && c.id) {
            updateDoc(doc(db, "classes", c.id), { joinCode: code, updatedAt: Date.now() })
              .catch(err => console.warn("Auto-backfill class join code:", err));
          }
        }

        const secName = c.section || c.className || '';
        const joinedIds = c.studentIds || c.joinedStudents || [];
        const enrolled = cachedUsers.filter(u => u.role === 'student' && (joinedIds.includes(u.id) || (u.section === secName && String(u.schoolId) === String(c.schoolId))));
        const pending = cachedPendingStudents.filter(s => s.section === secName && String(s.schoolId) === String(c.schoolId));

        totalActive += enrolled.length;
        totalPending += pending.length;
        totalLearners += (enrolled.length + pending.length);

        let secSum = 0;
        let secCount = 0;
        enrolled.forEach(st => {
          const subs = (cachedSubmissions || []).filter(s => s.studentId === st.id);
          const avg = calculateAverageScore(subs);
          const mpsVal = subs.length > 0 ? avg : (st.diagnostic_pct || (avg > 0 ? avg : null));
          if (mpsVal !== null && mpsVal !== undefined) {
            secSum += mpsVal;
            secCount++;
          }
        });
        if (secCount > 0) {
          totalMpsSum += (secSum / secCount);
          evaluatedSectionsCount++;
        }
      });

      // Update Bento KPIs for sections
      const elKpiTotal = document.getElementById('secKpiTotal');
      if (elKpiTotal) elKpiTotal.textContent = list.length;

      const elKpiLearners = document.getElementById('secKpiLearners');
      if (elKpiLearners) elKpiLearners.textContent = totalLearners;

      const elKpiClaimedRate = document.getElementById('secKpiClaimedRate');
      if (elKpiClaimedRate) {
        const rate = totalLearners > 0 ? Math.round((totalActive / totalLearners) * 100) : 0;
        elKpiClaimedRate.textContent = `${rate}% claimed (${totalActive} active, ${totalPending} pending)`;
      }

      const elKpiAvgSize = document.getElementById('secKpiAvgSize');
      if (elKpiAvgSize) {
        const avgSize = list.length > 0 ? (totalLearners / list.length).toFixed(1) : '0';
        elKpiAvgSize.textContent = `${avgSize} / sec`;
      }

      const elKpiAvgMps = document.getElementById('secKpiAvgMps');
      if (elKpiAvgMps) {
        const avgMps = evaluatedSectionsCount > 0 ? Math.round(totalMpsSum / evaluatedSectionsCount) : 0;
        elKpiAvgMps.textContent = `${avgMps}%`;
        elKpiAvgMps.className = `text-xl font-black font-display ${avgMps >= 80 ? 'text-emerald-600' : (avgMps >= 60 ? 'text-amber-600' : 'text-rose-600')}`;
      }

      if (list.length === 0) {
        tbody.innerHTML = `
          <tr>
            <td colspan="8" class="p-8 text-center text-slate-400 font-medium">
              No classroom sections match the selected filter.
            </td>
          </tr>`;
        return;
      }

      tbody.innerHTML = list.map(c => {
        const joinCode = resolveClassJoinCode(c);
        const secName = c.section || c.className || 'Unnamed Section';
        const gradeText = c.grade ? (String(c.grade).startsWith('Grade') ? c.grade : `Grade ${c.grade}`) : 'Grade 7';

        const adviser = (cachedUsers || []).find(u => u.id === (c.teacherId || c.adviserId));
        const adviserName = adviser ? `${adviser.firstName || ''} ${adviser.lastName || ''}`.trim() : (c.adviserName || 'Unassigned');
        const coTeachersCount = (c.teacherIds && Array.isArray(c.teacherIds)) ? Math.max(0, c.teacherIds.length - 1) : 0;

        const joinedIds = c.studentIds || c.joinedStudents || [];
        const enrolled = cachedUsers.filter(u => u.role === 'student' && (joinedIds.includes(u.id) || (u.section === secName && String(u.schoolId) === String(c.schoolId))));
        const pending = cachedPendingStudents.filter(s => s.section === secName && String(s.schoolId) === String(c.schoolId));
        const totalSectionStudents = enrolled.length + pending.length;

        let secScoreSum = 0;
        let secScoreCount = 0;
        let indCount = 0;
        let insCount = 0;
        let fruCount = 0;

        enrolled.forEach(st => {
          const subs = (cachedSubmissions || []).filter(s => s.studentId === st.id);
          const avg = calculateAverageScore(subs);
          const rawTier = (st.phil_iri_tier || st.diagnostic_tier || '').toLowerCase().trim();

          if (rawTier.includes('independent') || (subs.length > 0 && avg >= 80)) {
            indCount++;
          } else if (rawTier.includes('instructional') || (subs.length > 0 && avg >= 60)) {
            insCount++;
          } else if (rawTier.includes('frustration') || (subs.length > 0 && avg > 0 && avg < 60) || (st.diagnostic_pct && st.diagnostic_pct < 60)) {
            fruCount++;
          }

          const effectiveMps = subs.length > 0 ? avg : (st.diagnostic_pct || (avg > 0 ? avg : null));
          if (effectiveMps !== null && effectiveMps !== undefined) {
            secScoreSum += effectiveMps;
            secScoreCount++;
          }
        });

        const sectionMps = secScoreCount > 0 ? Math.round(secScoreSum / secScoreCount) : 0;
        const mpsColor = sectionMps >= 80 ? 'text-emerald-700' : (sectionMps >= 60 ? 'text-amber-700' : (secScoreCount > 0 ? 'text-rose-700' : 'text-slate-400'));
        const mpsBarBg = sectionMps >= 80 ? 'bg-emerald-500' : (sectionMps >= 60 ? 'bg-amber-500' : 'bg-rose-500');

        return `
          <tr class="hover:bg-slate-50/80 transition-colors">
            <td class="p-3.5">
              <span onclick="inspectClassRoster('${c.id}')" class="font-bold text-indigo-700 hover:text-indigo-900 cursor-pointer underline flex items-center gap-1.5">
                ${escapeHtml(secName)}
              </span>
            </td>
            <td class="p-3.5 font-semibold text-slate-700">${escapeHtml(gradeText)}</td>
            <td class="p-3.5">
              <div class="font-bold text-slate-800">${escapeHtml(adviserName)}</div>
              ${coTeachersCount > 0 ? `<div class="text-[10px] text-slate-500 mt-0.5">+${coTeachersCount} co-educator(s)</div>` : '<div class="text-[10px] text-slate-400 mt-0.5">Homeroom Adviser</div>'}
            </td>
            <td class="p-3.5">
              <div class="inline-flex items-center gap-1.5 px-2.5 py-1 rounded bg-indigo-50 border border-indigo-200 text-indigo-700 font-mono font-bold text-xs shadow-2xs">
                <span>#${escapeHtml(joinCode)}</span>
                <button onclick="copyToClipboard('${escapeHtml(joinCode)}', 'Section join code copied!')" title="Copy Code" class="hover:text-indigo-900 inline-flex items-center">
                  ${uiIcon('copy', 'w-3 h-3')}
                </button>
                <button onclick="openSectionQrModal('${c.id}')" title="View Section QR Code" class="hover:text-indigo-900 inline-flex items-center ml-0.5">
                  ${uiIcon('qr', 'w-3 h-3')}
                </button>
              </div>
            </td>
            <td class="p-3.5">
              ${secScoreCount > 0 ? `
                <div class="flex items-center gap-2">
                  <span class="font-bold text-xs ${mpsColor}">${sectionMps}% MPS</span>
                  <div class="w-14 h-1.5 rounded-full bg-slate-100 overflow-hidden">
                    <div class="${mpsBarBg} h-full rounded-full" style="width: ${Math.min(100, Math.max(5, sectionMps))}%"></div>
                  </div>
                </div>
              ` : '<span class="text-slate-400 text-xs font-medium">Pending Data</span>'}
            </td>
            <td class="p-3.5">
              <div class="flex items-center gap-1 text-[10px] font-semibold">
                <span class="px-1.5 py-0.5 rounded bg-emerald-50 text-emerald-700 border border-emerald-200" title="Independent Readers">Ind: ${indCount}</span>
                <span class="px-1.5 py-0.5 rounded bg-amber-50 text-amber-700 border border-amber-200" title="Instructional Readers">Ins: ${insCount}</span>
                <span class="px-1.5 py-0.5 rounded bg-rose-50 text-rose-700 border border-rose-200" title="Frustration Readers">Fru: ${fruCount}</span>
              </div>
            </td>
            <td class="p-3.5 font-semibold text-slate-800">
              <div>${totalSectionStudents} Learners</div>
              <div class="text-[10px] text-slate-400 font-normal mt-0.5">${enrolled.length} active &bull; <span class="text-amber-600 font-medium">${pending.length} pending</span></div>
            </td>
            <td class="p-3.5 text-right">
              <div class="flex items-center justify-end gap-1.5">
                <button onclick="inspectClassRoster('${c.id}')" title="Inspect Roster" class="px-2.5 py-1 bg-indigo-50 hover:bg-indigo-100 text-indigo-700 font-bold rounded-lg text-xs transition-colors shadow-2xs">
                  Roster &rarr;
                </button>
                <button onclick="openSectionQrModal('${c.id}')" title="Display Section QR Code" class="p-1 bg-white hover:bg-slate-50 text-slate-600 hover:text-indigo-700 border border-slate-200 rounded-lg text-xs transition-colors shadow-2xs">
                  ${uiIcon('qr', 'w-3.5 h-3.5')}
                </button>
                <button onclick="exportSingleSectionRosterPDF('${c.id}')" title="Print Section Roster" class="p-1 bg-white hover:bg-slate-50 text-slate-600 hover:text-indigo-700 border border-slate-200 rounded-lg text-xs transition-colors shadow-2xs">
                  ${uiIcon('file', 'w-3.5 h-3.5')}
                </button>
              </div>
            </td>
          </tr>
        `;
      }).join('');
    }

    // 3. LEARNER PERFORMANCE & QUIZ HISTORY (users/{uid}/quiz_history)

window.renderSectionsTable = renderSectionsTable;
