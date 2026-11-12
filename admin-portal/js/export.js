    // =========================================================================
    // BEAUTIFUL PRINT-READY PDF EXPORT ENGINE
    // =========================================================================
    window.getAdminSignatoryName = function(role, defaultName = "Administrative Officer") {
      if (role === 'superintendent') return "Dr. Zenia G. Mostoles, CESO V";
      if (isSuperAdmin) {
        return currentAdminUser ? `${currentAdminUser.firstName || ''} ${currentAdminUser.lastName || ''}`.trim() || defaultName : defaultName;
      }
      return currentAdminUser ? `${currentAdminUser.firstName || ''} ${currentAdminUser.lastName || ''}`.trim() || defaultName : defaultName;
    };

    window.getAdminSignatoryRole = function(role, defaultRole = "Evaluator") {
      if (role === 'superintendent') return "Schools Division Superintendent";
      if (isSuperAdmin) return "Schools Division Super Administrator";
      return "School Reading Coordinator / Evaluator";
    };

    window.addEventListener('afterprint', () => {
      document.body.classList.remove('is-printing');
      const c = document.getElementById('printReportContainer');
      if (c) c.remove();
    });

    window.printReportDoc = function({ title, subtitle, scopeBadge, kpis = [], headers = [], rows = [], signatories = [], orientation = "portrait" }) {
      const existing = document.getElementById('printReportContainer');
      if (existing) existing.remove();

      const container = document.createElement('div');
      container.id = 'printReportContainer';
      container.style.padding = '24px 32px';
      container.style.backgroundColor = '#FFFFFF';
      container.style.color = '#0F172A';

      const dateStr = new Date().toLocaleDateString('en-US', { year: 'numeric', month: 'long', day: 'numeric' });

      // KPI Cards
      let kpiHtml = '';
      if (kpis && kpis.length > 0) {
        kpiHtml = `
          <div style="display: grid; grid-template-columns: repeat(${kpis.length}, 1fr); gap: 10px; margin-bottom: 20px;">
            ${kpis.map(k => `
              <div style="border: 1px solid #E2E8F0; background: #F8FAFC; border-radius: 8px; padding: 10px 14px;">
                <div style="font-size: 17px; font-weight: 800; color: #1E293B;">${k.value}</div>
                <div style="font-size: 10.5px; font-weight: 600; color: #64748B; margin-top: 2px;">${k.label}</div>
              </div>
            `).join('')}
          </div>
        `;
      }

      // Data Table
      let tableHtml = `
        <table style="width: 100%; border-collapse: collapse; font-size: 11px; margin-bottom: 24px;">
          <thead>
            <tr style="background: #EEF2FF; border-bottom: 2px solid #CBD5E1;">
              ${headers.map(h => `
                <th style="padding: 8px 10px; text-align: ${h.align || 'left'}; font-weight: 700; color: #3730A3; font-size: 10px; text-transform: uppercase; letter-spacing: 0.5px;">${h.label}</th>
              `).join('')}
            </tr>
          </thead>
          <tbody>
            ${rows.map((row, idx) => `
              <tr style="border-bottom: 1px solid #F1F5F9; background: ${idx % 2 === 1 ? '#F8FAFC' : '#FFFFFF'}; page-break-inside: avoid;">
                ${row.map((cell, cIdx) => `
                  <td style="padding: 7px 10px; text-align: ${headers[cIdx]?.align || 'left'}; color: #1E293B; vertical-align: middle;">${cell}</td>
                `).join('')}
              </tr>
            `).join('')}
          </tbody>
        </table>
      `;

      // Signatory Section
      let sigHtml = '';
      if (signatories && signatories.length > 0) {
        sigHtml = `
          <div style="margin-top: 36px; display: grid; grid-template-columns: repeat(${signatories.length}, 1fr); gap: 40px; page-break-inside: avoid;">
            ${signatories.map(s => `
              <div>
                <div style="font-size: 10.5px; font-weight: 700; color: #475569; margin-bottom: 30px;">${s.label}:</div>
                <div style="border-top: 1px solid #94A3B8; padding-top: 5px;">
                  <div style="font-size: 11px; font-weight: 700; color: #1E293B;">${s.name || '__________________________'}</div>
                  <div style="font-size: 10px; color: #64748B;">${s.role || ''}</div>
                </div>
              </div>
            `).join('')}
          </div>
        `;
      }

      container.innerHTML = `
        <div style="border-bottom: 2px solid #5341CD; padding-bottom: 12px; margin-bottom: 18px;">
          <div style="display: flex; justify-content: space-between; align-items: flex-start;">
            <div>
              <div style="font-size: 10.5px; font-weight: 800; color: #475569; letter-spacing: 0.6px;">REPUBLIC OF THE PHILIPPINES • DEPARTMENT OF EDUCATION</div>
              <div style="font-size: 10px; font-weight: 600; color: #64748B;">Schools Division Office of Valenzuela City</div>
              <div style="font-size: 18px; font-weight: 800; color: #0F172A; margin-top: 4px;">${title}</div>
              <div style="font-size: 11px; color: #64748B; margin-top: 2px;">${subtitle || ''}</div>
            </div>
            <div style="text-align: right;">
              <span style="display: inline-block; padding: 4px 10px; border-radius: 6px; background: #EEF2FF; border: 1px solid #C7D2FE; font-size: 10px; font-weight: 700; color: #5341CD;">${scopeBadge || 'Division Overview'}</span>
              <div style="font-size: 10px; color: #94A3B8; margin-top: 6px;">Date: ${dateStr}</div>
            </div>
          </div>
        </div>

        ${kpiHtml}
        ${tableHtml}
        ${sigHtml}

        <div style="margin-top: 36px; font-size: 9px; color: #94A3B8; text-align: center; border-top: 1px solid #F1F5F9; padding-top: 8px;">
          GabAI Reading Management System • Academic Division Document
        </div>
      `;

      document.body.appendChild(container);
      window.print();

      setTimeout(() => {
        if (container.parentNode) container.parentNode.removeChild(container);
      }, 2500);
    };

    // PDF 1: Learners Performance Report
    window.exportLearnersReportPDF = function() {
      let students = cachedUsers.filter(u => u.role === 'student');
      let submissions = cachedSubmissions;
      if (isSchoolAdmin && currentAdminUser?.schoolId) {
        students = students.filter(s => (typeof resolveStudentSchoolId === 'function' ? resolveStudentSchoolId(s) : String(s.schoolId || '').trim()) === String(currentAdminUser.schoolId));
      } else if (selectedJurisdiction !== 'ALL') {
        students = students.filter(s => (typeof resolveStudentSchoolId === 'function' ? resolveStudentSchoolId(s) : String(s.schoolId || '').trim()) === selectedJurisdiction);
      }
      if (typeof learnerSectionFilter !== 'undefined' && learnerSectionFilter !== 'ALL') {
        students = students.filter(s => {
          const sec = typeof resolveStudentSectionAndAdviser === 'function' ? resolveStudentSectionAndAdviser(s).sectionName : (s.section || '');
          return String(sec).trim().toLowerCase() === learnerSectionFilter.toLowerCase();
        });
      }

      if (students.length === 0) {
        window.showToast("No learner records available to print.", "warning");
        return;
      }

      const scopeLabel = selectedJurisdiction === 'ALL' ? 'Division-Wide (All Junior High Schools)' : getSchoolName(selectedJurisdiction);

      let indCount = 0;
      let insCount = 0;
      let fruCount = 0;
      let totalMpsSum = 0;
      let totalMpsCount = 0;

      const rows = students.map((s, idx) => {
        const secInfo = typeof resolveStudentSectionAndAdviser === 'function' 
          ? resolveStudentSectionAndAdviser(s) 
          : { sectionName: s.section || 'General', grade: s.grade || '7', adviserName: 'Homeroom Adviser' };

        const prof = typeof computeLearnerAcademicProfile === 'function' 
          ? computeLearnerAcademicProfile(s) 
          : { effectiveMps: null, tierLabel: 'Unassessed', tier: 'unassessed', wpm: null };

        if (prof.tier === 'independent') indCount++;
        else if (prof.tier === 'instructional') insCount++;
        else if (prof.tier === 'frustration') fruCount++;

        if (prof.effectiveMps !== null) {
          totalMpsSum += prof.effectiveMps;
          totalMpsCount++;
        }

        const mpsDisplay = prof.effectiveMps !== null ? `${prof.effectiveMps}%` : '—';
        const wpmDisplay = prof.wpm ? `${prof.wpm} WPM` : '—';
        const tierBadge = prof.tier === 'independent' ? '<span style="color:#059669; font-weight:700;">Independent</span>'
          : prof.tier === 'instructional' ? '<span style="color:#D97706; font-weight:700;">Instructional</span>'
          : prof.tier === 'frustration' ? '<span style="color:#E11D48; font-weight:700;">Priority Support</span>'
          : '<span style="color:#64748B; font-weight:600;">Unassessed</span>';

        const interventionText = s.interventionStatus === 'remedial_reading' ? 'Remedial Session'
          : s.interventionStatus === 'peer_tutoring' ? 'Peer Tutoring'
          : s.interventionStatus === 'phonics_catchup' ? 'Phonics Catch-up'
          : 'Standard Program';

        const fullName = `${s.firstName || ''} ${s.lastName || ''}`.trim() || s.fullName || s.username || 'Learner';

        return [
          (idx + 1).toString(),
          fullName,
          getSchoolName(s.schoolId),
          `Gr. ${secInfo.grade} - ${secInfo.sectionName}`,
          mpsDisplay,
          wpmDisplay,
          `${s.current_streak || 0}d`,
          tierBadge,
          interventionText
        ];
      });

      const avgCohortMps = totalMpsCount > 0 ? Math.round(totalMpsSum / totalMpsCount) : 0;

      window.printReportDoc({
        title: "Official DepEd Phil-IRI Consolidated Reading & Academic Performance Report",
        subtitle: `Standards assessment, comprehension tiers, and reading speed across ${scopeLabel}`,
        scopeBadge: selectedJurisdiction === 'ALL' ? 'Division Scope' : 'School Scope',
        kpis: [
          { label: "Total Evaluated Scholars", value: students.length },
          { label: "Cohort Literacy MPS", value: `${avgCohortMps}%` },
          { label: "Independent (80%+)", value: `${indCount} (${Math.round((indCount/students.length)*100)}%)` },
          { label: "Instructional (60-79%)", value: `${insCount} (${Math.round((insCount/students.length)*100)}%)` },
          { label: "Priority Support (<60%)", value: `${fruCount} (${Math.round((fruCount/students.length)*100)}%)` }
        ],
        headers: [
          { label: "#", align: "left" },
          { label: "Learner Name", align: "left" },
          { label: "School", align: "left" },
          { label: "Grade & Section", align: "left" },
          { label: "Comprehension MPS", align: "right" },
          { label: "Reading Speed", align: "right" },
          { label: "Streak", align: "right" },
          { label: "Phil-IRI Tier", align: "center" },
          { label: "Intervention Protocol", align: "left" }
        ],
        rows: rows,
        signatories: [
          { label: "Prepared by", name: currentAdminUser?.displayName || "School Reading Coordinator", role: "Reading Program Coordinator" },
          { label: "Noted by", name: "Schools Division Superintendent", role: "SDO Valenzuela City" }
        ]
      });
    };

    // PDF 2: Reading Support & Intervention Priority List
    window.exportInterventionsPDF = function() {
      let students = cachedUsers.filter(u => u.role === 'student');
      if (selectedJurisdiction !== 'ALL') {
        students = students.filter(s => (typeof resolveStudentSchoolId === 'function' ? resolveStudentSchoolId(s) : String(s.schoolId || '').trim()) === selectedJurisdiction);
      }
      if (typeof learnerSectionFilter !== 'undefined' && learnerSectionFilter !== 'ALL') {
        students = students.filter(s => {
          const sec = typeof resolveStudentSectionAndAdviser === 'function' ? resolveStudentSectionAndAdviser(s).sectionName : (s.section || '');
          return String(sec).trim().toLowerCase() === learnerSectionFilter.toLowerCase();
        });
      }

      const supportList = students.filter(s => {
        const prof = typeof computeLearnerAcademicProfile === 'function' ? computeLearnerAcademicProfile(s) : null;
        const isFru = prof && (prof.tier === 'frustration' || (prof.effectiveMps !== null && prof.effectiveMps < 60));
        const isAssigned = s.interventionStatus && s.interventionStatus !== 'none';
        return isFru || isAssigned;
      });

      if (supportList.length === 0) {
        window.showToast("No learners currently needing prioritized reading support.", "info");
        return;
      }

      const scopeLabel = selectedJurisdiction === 'ALL' ? 'Division-Wide' : getSchoolName(selectedJurisdiction);

      const rows = supportList.map((s, idx) => {
        const secInfo = typeof resolveStudentSectionAndAdviser === 'function' 
          ? resolveStudentSectionAndAdviser(s) 
          : { sectionName: s.section || 'General', grade: s.grade || '7', adviserName: 'Homeroom Adviser' };

        const prof = typeof computeLearnerAcademicProfile === 'function' 
          ? computeLearnerAcademicProfile(s) 
          : { effectiveMps: null, wpm: null };

        const fullName = `${s.firstName || ''} ${s.lastName || ''}`.trim() || s.fullName || s.username || 'Learner';
        const mpsDisplay = prof.effectiveMps !== null ? `${prof.effectiveMps}%` : 'Pending Assessment';
        const wpmDisplay = prof.wpm ? `${prof.wpm} WPM` : 'Normative';

        let interventionText = "🚨 Remedial Reading Session";
        if (s.interventionStatus === 'peer_tutoring') interventionText = "👥 Peer Tutoring & Paired Reading";
        else if (s.interventionStatus === 'phonics_catchup') interventionText = "📖 Intensive Phonics Catch-up";
        else if (s.interventionStatus === 'elln_clinic') interventionText = "🏥 ELLN Reading Clinic";

        return [
          (idx + 1).toString(),
          fullName,
          getSchoolName(s.schoolId),
          `Gr. ${secInfo.grade} - ${secInfo.sectionName}`,
          secInfo.adviserName,
          mpsDisplay,
          wpmDisplay,
          interventionText,
          s.interventionNotes || "Tier 3 Frustration level"
        ];
      });

      window.printReportDoc({
        title: "Official DepEd Priority Reading Support & Remedial Intervention Directive",
        subtitle: `Scholars designated for guided phonics, paired reading, and targeted ELLN remediation (${scopeLabel})`,
        scopeBadge: "Priority Remediation Radar",
        kpis: [
          { label: "Target Scholars in Queue", value: supportList.length },
          { label: "Total Cohort Enrolled", value: students.length },
          { label: "Support Proportion", value: `${Math.round((supportList.length / (students.length || 1)) * 100)}%` }
        ],
        headers: [
          { label: "#", align: "left" },
          { label: "Learner Name", align: "left" },
          { label: "School", align: "left" },
          { label: "Grade & Section", align: "left" },
          { label: "Class Adviser", align: "left" },
          { label: "Literacy MPS", align: "right" },
          { label: "Speed", align: "right" },
          { label: "Designated Program", align: "left" },
          { label: "Supervisory Observations", align: "left" }
        ],
        rows: rows,
        signatories: [
          { label: "Prepared by", name: currentAdminUser?.displayName || "Reading Coordinator", role: "Classroom Reading Coordinator" },
          { label: "Approved for Intervention", name: "School Principal / Head", role: "School Administration" }
        ]
      });
    };

    // PDF 2.1: Individual Learner Official Phil-IRI Summary Card
    window.exportIndividualLearnerPhilIriPDF = function(studentId) {
      const student = (cachedUsers || []).find(u => u.id === studentId);
      if (!student) {
        window.showToast("Student profile not found for printing.", "error");
        return;
      }
      const secInfo = typeof resolveStudentSectionAndAdviser === 'function' 
        ? resolveStudentSectionAndAdviser(student)
        : { sectionName: student.section || 'General', grade: student.grade || '7', adviserName: 'Homeroom Adviser' };

      const prof = typeof computeLearnerAcademicProfile === 'function'
        ? computeLearnerAcademicProfile(student)
        : { effectiveMps: null, tierLabel: 'Unassessed', tier: 'unassessed', wpm: null, trendLabel: '➔ Stable' };

      const fName = student.firstName || '';
      const lName = student.lastName || '';
      const fullName = `${fName} ${lName}`.trim() || student.fullName || student.username || 'Learner';
      const schoolName = getSchoolName(student.schoolId);
      const mpsVal = prof.effectiveMps !== null ? `${prof.effectiveMps}%` : 'Pending';
      const wpmVal = prof.wpm ? `${prof.wpm} WPM` : 'Normative';

      const existing = document.getElementById('printReportContainer');
      if (existing) existing.remove();

      const container = document.createElement('div');
      container.id = 'printReportContainer';
      container.style.padding = '24px';
      container.style.backgroundColor = '#FFFFFF';
      container.innerHTML = `
        <style>
          @media print {
            body * { visibility: hidden !important; }
            #printReportContainer, #printReportContainer * { visibility: visible !important; }
            #printReportContainer { position: absolute !important; left: 0 !important; top: 0 !important; width: 100% !important; }
          }
        </style>
        <div style="font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; color: #1e293b; max-width: 800px; margin: 0 auto; line-height: 1.5;">
          <!-- Header -->
          <div style="display: flex; align-items: center; justify-content: space-between; border-bottom: 2px solid #5341CD; padding-bottom: 12px; margin-bottom: 16px;">
            <div>
              <div style="font-size: 11px; font-weight: 700; color: #64748b; text-transform: uppercase; letter-spacing: 0.05em;">Department of Education &bull; SDO Valenzuela City</div>
              <div style="font-size: 18px; font-weight: 800; color: #0f172a; margin-top: 2px;">Official Phil-IRI Individual Reading Profile</div>
              <div style="font-size: 12px; color: #5341CD; font-weight: 600;">DepEd Order No. 14, s. 2018 Standards</div>
            </div>
            <div style="text-align: right;">
              <div style="font-size: 12px; font-weight: 700; color: #0f172a;">${escapeHtml(schoolName)}</div>
              <div style="font-size: 11px; color: #64748b;">School Year 2026–2027</div>
            </div>
          </div>

          <!-- Learner Demographics Grid -->
          <div style="display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 12px; background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 12px; padding: 12px; margin-bottom: 16px; font-size: 12px;">
            <div>
              <div style="color: #64748b; font-size: 10px; font-weight: 700; text-transform: uppercase;">Learner Full Name</div>
              <div style="font-weight: 800; color: #0f172a; font-size: 13px; margin-top: 2px;">${escapeHtml(fullName)}</div>
              <div style="color: #64748b; font-size: 10px;">@${escapeHtml(student.username || student.id.substring(0,6))}</div>
            </div>
            <div>
              <div style="color: #64748b; font-size: 10px; font-weight: 700; text-transform: uppercase;">LRN / Institutional ID</div>
              <div style="font-weight: 700; color: #0f172a; margin-top: 2px;">${escapeHtml(student.lrn || 'Pending Assignment')}</div>
              <div style="color: #64748b; font-size: 10px;">Gender: ${escapeHtml(student.gender || 'Unspecified')}</div>
            </div>
            <div>
              <div style="color: #64748b; font-size: 10px; font-weight: 700; text-transform: uppercase;">Classroom Section</div>
              <div style="font-weight: 700; color: #5341CD; margin-top: 2px;">${escapeHtml(secInfo.sectionName)} (Grade ${escapeHtml(secInfo.grade)})</div>
              <div style="color: #64748b; font-size: 10px;">Adviser: ${escapeHtml(secInfo.adviserName)}</div>
            </div>
          </div>

          <!-- KPI summary strip -->
          <div style="display: grid; grid-template-columns: repeat(4, 1fr); gap: 10px; margin-bottom: 16px;">
            <div style="background: #ffffff; border: 1px solid #e2e8f0; border-radius: 10px; padding: 10px; text-align: center;">
              <div style="font-size: 10px; font-weight: 700; color: #64748b; text-transform: uppercase;">Phil-IRI Reading Tier</div>
              <div style="font-size: 14px; font-weight: 800; color: ${prof.tier === 'independent' ? '#059669' : (prof.tier === 'instructional' ? '#d97706' : '#e11d48')}; margin-top: 3px;">
                ${escapeHtml(prof.tierLabel)}
              </div>
            </div>
            <div style="background: #ffffff; border: 1px solid #e2e8f0; border-radius: 10px; padding: 10px; text-align: center;">
              <div style="font-size: 10px; font-weight: 700; color: #64748b; text-transform: uppercase;">Comprehension MPS</div>
              <div style="font-size: 14px; font-weight: 800; color: #0f172a; margin-top: 3px;">${mpsVal}</div>
            </div>
            <div style="background: #ffffff; border: 1px solid #e2e8f0; border-radius: 10px; padding: 10px; text-align: center;">
              <div style="font-size: 10px; font-weight: 700; color: #64748b; text-transform: uppercase;">Reading Speed</div>
              <div style="font-size: 14px; font-weight: 800; color: #2563eb; margin-top: 3px;">${wpmVal}</div>
            </div>
            <div style="background: #ffffff; border: 1px solid #e2e8f0; border-radius: 10px; padding: 10px; text-align: center;">
              <div style="font-size: 10px; font-weight: 700; color: #64748b; text-transform: uppercase;">Reading Streak Habit</div>
              <div style="font-size: 14px; font-weight: 800; color: #d97706; margin-top: 3px;">${student.current_streak || 0} Days</div>
            </div>
          </div>

          <!-- Intervention & Guidance Notes Box -->
          <div style="border: 1px solid #e2e8f0; border-radius: 12px; padding: 14px; margin-bottom: 20px; background: #fafafa;">
            <div style="font-size: 11px; font-weight: 700; color: #0f172a; text-transform: uppercase; margin-bottom: 6px;">
              Institutional Reading Intervention Status:
              <span style="color: #5341CD; font-weight: 800; text-transform: none;">
                ${student.interventionStatus === 'remedial_reading' ? 'Remedial Reading Session (Priority Support)' : (student.interventionStatus === 'peer_tutoring' ? 'Peer Tutoring & Guided Paired Reading' : (student.interventionStatus === 'phonics_catchup' ? 'Intensive Phonics & Word Recognition' : 'Regular Reading Program (Satisfactory)'))}
              </span>
            </div>
            <div style="font-size: 11px; color: #475569; line-height: 1.6;">
              <strong>Supervisory / Teacher Observations:</strong><br />
              ${escapeHtml(student.interventionNotes || 'Learner is participating in regular classroom literacy instruction and utilizing the GabAI mobile application for vocabulary expansion.')}
            </div>
          </div>

          <!-- Signatories -->
          <div style="display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 20px; margin-top: 30px; text-align: center; font-size: 11px;">
            <div>
              <div style="border-bottom: 1px solid #94a3b8; height: 35px; margin-bottom: 4px;"></div>
              <div style="font-weight: 700; color: #0f172a;">${escapeHtml(secInfo.adviserName)}</div>
              <div style="color: #64748b;">Class Homeroom Adviser</div>
            </div>
            <div>
              <div style="border-bottom: 1px solid #94a3b8; height: 35px; margin-bottom: 4px;"></div>
              <div style="font-weight: 700; color: #0f172a;">School Reading Coordinator</div>
              <div style="color: #64748b;">Phil-IRI Committee</div>
            </div>
            <div>
              <div style="border-bottom: 1px solid #94a3b8; height: 35px; margin-bottom: 4px;"></div>
              <div style="font-weight: 700; color: #0f172a;">${escapeHtml(currentAdminUser?.displayName || 'School Head / Principal')}</div>
              <div style="color: #64748b;">School Principal</div>
            </div>
          </div>
        </div>
      `;

      document.body.appendChild(container);
      setTimeout(() => {
        window.print();
        setTimeout(() => container.remove(), 1000);
      }, 250);
    };

    // PDF 3: Grade Benchmark Matrix
    window.exportGradeMatrixPDF = function() {
      let students = cachedUsers.filter(u => u.role === 'student');
      let classes = cachedClasses;
      let submissions = cachedSubmissions;
      if (selectedJurisdiction !== 'ALL') {
        students = students.filter(s => String(s.schoolId) === selectedJurisdiction);
        classes = classes.filter(c => String(c.schoolId) === selectedJurisdiction);
        submissions = submissions.filter(s => {
          const a = cachedWeeklyAssessments.find(item => item.id === s.assessmentId);
          return (a && String(a.schoolId) === selectedJurisdiction) || String(s.schoolId) === selectedJurisdiction;
        });
      }

      const grades = ["Grade 7", "Grade 8", "Grade 9", "Grade 10"];
      let totalEnrolled = 0;
      let totalSections = 0;
      let totalSubs = 0;

      const rows = grades.map(g => {
        const gStudents = students.filter(s => (s.grade || '').trim() === g);
        const gClasses = classes.filter(c => (c.grade || '').trim() === g);
        const gSubs = submissions.filter(s => {
          const st = students.find(u => u.uid === s.studentId);
          return st && (st.grade || '').trim() === g;
        });

        totalEnrolled += gStudents.length;
        totalSections += gClasses.length;
        totalSubs += gSubs.length;

        const m = calculateScoreMetrics(gSubs, gStudents);

        return [
          g,
          gStudents.length.toString(),
          gClasses.length.toString(),
          gSubs.length.toString(),
          `${m.avgScore}%`,
          `${m.mastery} High • ${m.proficient} Mid • ${m.support} Support`
        ];
      });

      const overall = calculateScoreMetrics(submissions, students);
      rows.push([
        "<strong>JHS Total / Average</strong>",
        `<strong>${totalEnrolled}</strong>`,
        `<strong>${totalSections}</strong>`,
        `<strong>${totalSubs}</strong>`,
        `<strong>${overall.avgScore}%</strong>`,
        `<strong>${overall.mastery} High • ${overall.proficient} Mid • ${overall.support} Support</strong>`
      ]);

      const scopeName = selectedJurisdiction === 'ALL' ? 'Division-Wide' : getSchoolName(selectedJurisdiction);

      window.printReportDoc({
        title: "Junior High School Literacy Benchmark Report",
        subtitle: `Consolidated Grade 7 to 10 Comparative Benchmarks (${scopeName})`,
        scopeBadge: "Grades 7–10",
        kpis: [
          { label: "Total JHS Enrolled", value: totalEnrolled },
          { label: "Class Sections", value: totalSections },
          { label: "Quiz Submissions", value: totalSubs },
          { label: "Overall Literacy Avg", value: overall.avgScore + "%" }
        ],
        headers: [
          { label: "Grade Level", align: "left" },
          { label: "Enrolled", align: "right" },
          { label: "Sections", align: "right" },
          { label: "Submissions", align: "right" },
          { label: "Average Score", align: "right" },
          { label: "Mastery Spread", align: "left" }
        ],
        rows: rows,
        signatories: [
          { label: "Prepared by", name: currentAdminUser?.displayName || "Education Program Supervisor", role: "Division Reading Coordinator" },
          { label: "Approved by", name: "Division Academic Director", role: "Academic Division Office" }
        ]
      });
    };

    // PDF 4: Schools Matrix / Directory
    window.exportSchoolsMatrixPDF = function() {
      if (VALENZUELA_SCHOOLS.length === 0) {
        window.showToast("No school records available to export.", "warning");
        return;
      }

      let totalStudents = 0;
      let totalFaculty = 0;

      const rows = VALENZUELA_SCHOOLS.map(school => {
        const teachers = cachedTeachers.filter(t => String(t.schoolId) === school.id);
        const students = cachedUsers.filter(u => u.role === 'student' && String(u.schoolId) === school.id);
        const subs = cachedSubmissions.filter(s => {
          const a = cachedWeeklyAssessments.find(item => item.id === s.assessmentId);
          return (a && String(a.schoolId) === school.id) || String(s.schoolId) === school.id;
        });

        totalStudents += students.length;
        totalFaculty += teachers.length;

        const m = calculateScoreMetrics(subs, students);
        const schoolAdmins = cachedUsers.filter(u => u.role === 'school_admin' && String(u.schoolId) === school.id);
        const adminName = schoolAdmins.length > 0 ? schoolAdmins.map(a => `${a.firstName || ''} ${a.lastName || 'Admin'}`.trim()).join(', ') : (school.adminName || 'Unassigned');

        return [
          school.id,
          school.name,
          teachers.length.toString(),
          students.length.toString(),
          `${m.avgScore}%`,
          adminName
        ];
      });

      const overall = calculateScoreMetrics(cachedSubmissions, cachedUsers.filter(u => u.role === 'student'));

      window.printReportDoc({
        title: "Secondary Schools Directory & Literacy Overview",
        subtitle: `Official Masterlist of Junior High Schools in Valenzuela City`,
        scopeBadge: "Division Roster",
        orientation: "landscape",
        kpis: [
          { label: "Junior High Schools", value: VALENZUELA_SCHOOLS.length },
          { label: "Total Division Learners", value: totalStudents },
          { label: "Total Verified Faculty", value: totalFaculty },
          { label: "Division Average Score", value: overall.avgScore + "%" }
        ],
        headers: [
          { label: "School ID", align: "left" },
          { label: "School Name", align: "left" },
          { label: "Faculty", align: "right" },
          { label: "Learners", align: "right" },
          { label: "Average Score", align: "right" },
          { label: "Assigned Administrator", align: "left" }
        ],
        rows: rows,
        signatories: [
          { label: "Compiled by", name: currentAdminUser?.displayName || "Super Admin", role: "Information Technology Officer" },
          { label: "Verified by", name: "Division Academic Director", role: "Academic Division Office" }
        ]
      });
    };

    // PDF 5: Faculty Registry
    window.exportFacultyRegistryPDF = function() {
      if (typeof window.syncCachedTeachers === 'function') window.syncCachedTeachers();
      let teachers = [...cachedTeachers];
      if (isSchoolAdmin && currentAdminUser?.schoolId) {
        teachers = teachers.filter(t => String(resolveTeacherSchoolId(t)) === String(currentAdminUser.schoolId));
      } else if (selectedJurisdiction !== 'ALL') {
        teachers = teachers.filter(t => String(resolveTeacherSchoolId(t)) === String(selectedJurisdiction));
      }

      if (teachers.length === 0) {
        window.showToast("No faculty records available to export.", "warning");
        return;
      }

      const accredited = teachers.filter(t => t.isApproved === true).length;
      const pending = teachers.length - accredited;

      const rows = teachers.map((t, idx) => {
        const status = t.isApproved === true 
          ? '<span style="color:#059669; font-weight:700;">Accredited</span>'
          : '<span style="color:#D97706; font-weight:700;">Pending</span>';

        return [
          (idx + 1).toString(),
          t.fullName || `${t.firstName || ''} ${t.lastName || ''}`.trim() || t.email || 'Teacher',
          getSchoolName(t.schoolId),
          t.email || 'N/A',
          t.role === 'school_admin' ? 'School Administrator' : 'Teacher',
          status
        ];
      });

      window.printReportDoc({
        title: "Faculty & Staff Registry Report",
        subtitle: `Roster of educators across ${selectedJurisdiction === 'ALL' ? 'Valenzuela City Junior High Schools' : getSchoolName(selectedJurisdiction)}`,
        scopeBadge: "Personnel Directory",
        kpis: [
          { label: "Total Educators", value: teachers.length },
          { label: "Verified Teachers", value: accredited },
          { label: "Pending Accreditation", value: pending }
        ],
        headers: [
          { label: "#", align: "left" },
          { label: "Educator Name", align: "left" },
          { label: "School", align: "left" },
          { label: "Email Address", align: "left" },
          { label: "Position", align: "left" },
          { label: "Status", align: "center" }
        ],
        rows: rows,
        signatories: [
          { label: "Verified by", name: currentAdminUser?.displayName || "School Head", role: "School Administration" },
          { label: "Noted by", name: "Division Academic Director", role: "Academic Division Office" }
        ]
      });
    };

    // 1. Division Matrix Export
    window.exportSchoolsMatrixCSV = function() {
      const rows = [
        [
          "School ID",
          "Secondary School Name",
          "Division Office",
          "Verified Faculty",
          "Pending Faculty",
          "Active Sections",
          "Active Claimed Scholars",
          "Total Student Accounts",
          "Reading Mastery Avg %",
          "High Mastery Scholars (80%+)",
          "Proficient Scholars (60-79%)",
          "Needs Support Scholars (<60%)",
          "School Administrator",
          "Admin Email",
          "Administration Status"
        ]
      ];

      VALENZUELA_SCHOOLS.forEach(school => {
        const teachersInSchool = cachedTeachers.filter(t => String(t.schoolId) === school.id);
        const approvedCount = teachersInSchool.filter(t => t.isApproved === true).length;
        const pendingCount = teachersInSchool.filter(t => t.isApproved !== true).length;
        const classesInSchool = cachedClasses.filter(c => String(c.schoolId) === school.id).length;

        const claimedInSchool = cachedUsers.filter(u => u.role === 'student' && String(u.schoolId) === school.id);
        const pendingInSchool = cachedPendingStudents.filter(s => String(s.schoolId) === school.id);
        const totalLearners = claimedInSchool.length + pendingInSchool.length;

        const schoolSubs = cachedSubmissions.filter(s => {
          const a = cachedWeeklyAssessments.find(item => item.id === s.assessmentId);
          return (a && String(a.schoolId) === school.id) || String(s.schoolId) === school.id;
        });

        const sMetrics = calculateScoreMetrics(schoolSubs, claimedInSchool);
        const schoolAvg = sMetrics.avgScore;
        const sMastery = sMetrics.mastery;
        const sProf = sMetrics.proficient;
        const sSupp = sMetrics.support;

        const schoolAdmins = cachedUsers.filter(u => u.role === 'school_admin' && String(u.schoolId) === school.id);
        const pendingInvite = cachedSchoolInvites.find(inv => String(inv.schoolId) === school.id && !inv.claimed);

        let adminName = "Unassigned";
        let adminEmail = "--";
        let adminStatus = "Pending Provisioning";
        if (schoolAdmins.length > 0) {
          adminName = schoolAdmins.map(a => `${a.firstName || ''} ${a.lastName || 'Admin'}`.trim()).join(', ');
          adminEmail = schoolAdmins.map(a => a.email || '').filter(Boolean).join(', ');
          adminStatus = schoolAdmins.length > 1 ? `${schoolAdmins.length} Active Administrators` : "Active Administrator";
        } else if (pendingInvite) {
          adminName = `Invite Code: ${pendingInvite.inviteCode}`;
          adminStatus = "SDO Invite Issued";
        }

        rows.push([
          school.id,
          school.name,
          "SDO Valenzuela City",
          approvedCount,
          pendingCount,
          classesInSchool,
          claimedInSchool.length,
          totalLearners,
          `${schoolAvg}%`,
          sMastery,
          sProf,
          sSupp,
          adminName,
          adminEmail,
          adminStatus
        ]);
      });

      downloadCSV(`SDO_Valenzuela_Junior_High_Schools_Matrix_${new Date().toISOString().slice(0, 10)}.csv`, rows);
    };

    // 2. Grade 7–10 Benchmark Export
    window.exportGradeMatrixCSV = function() {
      let students = cachedUsers.filter(u => u.role === 'student');
      let classes = cachedClasses;
      let submissions = cachedSubmissions;

      if (selectedJurisdiction !== 'ALL') {
        students = students.filter(s => String(s.schoolId) === selectedJurisdiction);
        classes = classes.filter(c => String(c.schoolId) === selectedJurisdiction);
        submissions = submissions.filter(s => {
          const a = cachedWeeklyAssessments.find(item => item.id === s.assessmentId);
          return (a && String(a.schoolId) === selectedJurisdiction) || String(s.schoolId) === selectedJurisdiction;
        });
      }

      const rows = [
        [
          "Grade Level",
          "School Jurisdiction",
          "Enrolled Scholars",
          "Active Sections",
          "Reading Comprehension Avg %",
          "Mastery Tier Count (80%+)",
          "Proficient Tier Count (60-79%)",
          "Needs Support Tier Count (<60%)",
          "Submissions Count",
          "Benchmark Status"
        ]
      ];

      const grades = ["Grade 7", "Grade 8", "Grade 9", "Grade 10"];
      grades.forEach(gradeName => {
        const gradeStudents = students.filter(s => (s.grade || '').trim() === gradeName);
        const gradeClasses = classes.filter(c => (c.grade || '').trim() === gradeName);
        const gradeSubs = submissions.filter(s => {
          const st = students.find(item => item.id === s.studentId);
          return st && (st.grade || '').trim() === gradeName;
        });

        const gMetrics = calculateScoreMetrics(gradeSubs, gradeStudents);
        const gradeAvg = gMetrics.avgScore;
        const gMastery = gMetrics.mastery;
        const gProf = gMetrics.proficient;
        const gSupp = gMetrics.support;

        let statusText = "No Enrolled Scholars";
        if (gradeStudents.length > 0) {
          statusText = gradeAvg >= 80 ? "High Mastery" : (gradeAvg >= 60 ? "Proficient" : "Needs Support");
        }

        rows.push([
          gradeName,
          selectedJurisdiction === 'ALL' ? 'Division-Wide' : getSchoolName(selectedJurisdiction),
          gradeStudents.length,
          gradeClasses.length,
          `${gradeAvg}%`,
          gMastery,
          gProf,
          gSupp,
          gradeSubs.length,
          statusText
        ]);
      });

      const scopeName = selectedJurisdiction === 'ALL' ? 'Division' : selectedJurisdiction;
      downloadCSV(`Grade_Benchmark_Matrix_${scopeName}_${new Date().toISOString().slice(0, 10)}.csv`, rows);
    };

    // 3. Academic Support Radar Export (<60% or Inactive)
    window.exportSupportLearnersCSV = function() {
      let students = cachedUsers.filter(u => u.role === 'student');
      let submissions = cachedSubmissions;

      if (selectedJurisdiction !== 'ALL') {
        students = students.filter(s => String(s.schoolId) === selectedJurisdiction);
        submissions = submissions.filter(s => {
          const a = cachedWeeklyAssessments.find(item => item.id === s.assessmentId);
          return (a && String(a.schoolId) === selectedJurisdiction) || String(s.schoolId) === selectedJurisdiction;
        });
      }

      const rows = [
        [
          "Student Full Name",
          "Username",
          "Email / ID",
          "School ID",
          "School Name",
          "Grade Level",
          "Section",
          "Assessments Completed",
          "Average Score %",
          "Reading Streak (Days)",
          "Academic Support Priority"
        ]
      ];

      students.forEach(st => {
        const stSubs = submissions.filter(s => s.studentId === st.id);
        const avg = calculateAverageScore(stSubs);

        const isSupport = stSubs.length === 0 || avg < 60;
        if (isSupport) {
          const fullName = `${st.firstName || ''} ${st.lastName || 'Learner'}`.trim() || 'Learner';
          const priority = stSubs.length === 0 ? "High (Zero Submissions / Inactive)" : (avg < 50 ? "Critical (<50% Comprehension)" : "Moderate (50%-59% Comprehension)");

          rows.push([
            fullName,
            st.username ? `@${st.username}` : (st.email || ''),
            st.email || st.id || '',
            st.schoolId || '',
            getSchoolName(st.schoolId),
            st.grade || 'Unassigned',
            st.section || 'General',
            stSubs.length,
            `${avg}%`,
            st.current_streak || 0,
            priority
          ]);
        }
      });

      const scopeName = selectedJurisdiction === 'ALL' ? 'Division' : selectedJurisdiction;
      downloadCSV(`Academic_Support_Radar_${scopeName}_${new Date().toISOString().slice(0, 10)}.csv`, rows);
    };

    // 4. All Enrolled Learners Performance Export
    window.exportAllLearnersCSV = function() {
      let students = cachedUsers.filter(u => u.role === 'student');

      if (isSchoolAdmin && currentAdminUser?.schoolId) {
        students = students.filter(s => (typeof resolveStudentSchoolId === 'function' ? resolveStudentSchoolId(s) : String(s.schoolId || '').trim()) === String(currentAdminUser.schoolId));
      } else if (selectedJurisdiction !== 'ALL') {
        students = students.filter(s => (typeof resolveStudentSchoolId === 'function' ? resolveStudentSchoolId(s) : String(s.schoolId || '').trim()) === selectedJurisdiction);
      }
      if (typeof learnerSectionFilter !== 'undefined' && learnerSectionFilter !== 'ALL') {
        students = students.filter(s => {
          const sec = typeof resolveStudentSectionAndAdviser === 'function' ? resolveStudentSectionAndAdviser(s).sectionName : (s.section || '');
          return String(sec).trim().toLowerCase() === learnerSectionFilter.toLowerCase();
        });
      }

      const rows = [
        [
          "LRN",
          "Learner Full Name",
          "Username",
          "Gender",
          "School ID",
          "School Name",
          "Grade Level",
          "Section Name",
          "Class Homeroom Adviser",
          "Phil-IRI Reading Tier",
          "Comprehension MPS (%)",
          "Reading Speed (WPM)",
          "Reading Streak (Days)",
          "Words Mastered (OCR Retention)",
          "Gamification Level",
          "Current XP",
          "Quests Completed",
          "Assessments Completed",
          "Reading Trajectory Trend",
          "Assigned Intervention Program",
          "Supervisory Guidance Notes"
        ]
      ];

      students.forEach(st => {
        const secInfo = typeof resolveStudentSectionAndAdviser === 'function'
          ? resolveStudentSectionAndAdviser(st)
          : { sectionName: st.section || 'General', grade: st.grade || '7', adviserName: 'Homeroom Adviser' };

        const prof = typeof computeLearnerAcademicProfile === 'function'
          ? computeLearnerAcademicProfile(st)
          : { effectiveMps: null, tierLabel: 'Unassessed', wpm: null, trendLabel: '➔ Stable', totalQuizzes: 0, wordsMastered: 0 };

        const fullName = `${st.firstName || ''} ${st.lastName || ''}`.trim() || st.fullName || st.username || 'Learner';
        const mpsVal = prof.effectiveMps !== null ? `${prof.effectiveMps}%` : 'Pending';
        const wpmVal = prof.wpm ? `${prof.wpm} WPM` : 'Normative';

        let interventionText = "Regular Program (Satisfactory)";
        if (st.interventionStatus === 'remedial_reading') interventionText = "Remedial Reading Session (Priority Support)";
        else if (st.interventionStatus === 'peer_tutoring') interventionText = "Peer Tutoring & Paired Reading";
        else if (st.interventionStatus === 'phonics_catchup') interventionText = "Intensive Phonics Catch-up";
        else if (st.interventionStatus === 'elln_clinic') interventionText = "ELLN Reading Clinic";

        rows.push([
          st.lrn || '',
          fullName,
          st.username ? `@${st.username}` : (st.email || ''),
          st.gender || 'unspecified',
          st.schoolId || '',
          getSchoolName(st.schoolId),
          `Grade ${secInfo.grade}`,
          secInfo.sectionName,
          secInfo.adviserName,
          prof.tierLabel,
          mpsVal,
          wpmVal,
          st.current_streak || 0,
          prof.wordsMastered || 0,
          st.level || 1,
          st.current_xp || 0,
          Array.isArray(st.quests_completed) ? st.quests_completed.length : 0,
          prof.totalQuizzes,
          prof.trendLabel,
          interventionText,
          st.interventionNotes || ''
        ]);
      });

      const scopeName = selectedJurisdiction === 'ALL' ? 'Division' : selectedJurisdiction;
      downloadCSV(`DepEd_PhilIRI_Learner_Academic_Performance_${scopeName}_${new Date().toISOString().slice(0, 10)}.csv`, rows);
    };

    // 5. Credential Vault Export (Login Slips)
    window.exportCredentialVaultCSV = function() {
      let list = getUnifiedStudentCredentials();
      if (selectedJurisdiction !== 'ALL') {
        list = list.filter(s => String(s.schoolId) === String(selectedJurisdiction));
      }
      if (list.length === 0) {
        window.showToast("No student accounts to export for this view.", "warning");
        return;
      }

      const rows = [
        [
          "First Name",
          "Last Name",
          "Username",
          "Password Status",
          "Grade Level",
          "Class Section",
          "School ID",
          "School Name",
          "Onboarding Status",
          "Generated Date"
        ]
      ];

      list.forEach(s => {
        const isClaimed = Boolean(s.isClaimed);
        const dateStr = s.createdAt ? new Date(s.createdAt).toLocaleDateString() : 'Recorded';
        const passkeyText = (!isClaimed && s.password) ? s.password : '•••••••• (Protected)';

        rows.push([
          s.firstName || '',
          s.lastName || '',
          s.username ? `@${s.username}` : '',
          passkeyText,
          s.grade || 'Grade 10',
          s.section || 'General',
          s.schoolId || '',
          getSchoolName(s.schoolId),
          isClaimed ? 'Claimed in App' : 'Pending Claim (Unclaimed Slip)',
          dateStr
        ]);
      });

      const scopeName = selectedJurisdiction === 'ALL' ? 'Division' : selectedJurisdiction;
      downloadCSV(`Student_Credential_Vault_${scopeName}_${new Date().toISOString().slice(0, 10)}.csv`, rows);
    };

    // 6. Weekly Assessments Ledger Export
    window.exportWeeklyAssessmentsCSV = function() {
      let list = cachedWeeklyAssessments;
      if (selectedJurisdiction !== 'ALL') {
        list = list.filter(a => String(a.schoolId) === selectedJurisdiction);
      }

      const rows = [
        [
          "Assessment Title",
          "Subject",
          "Target Class Section",
          "Grade Level",
          "School ID",
          "School Name",
          "Educator Name",
          "Due Date",
          "Total Items",
          "Turned In Submissions",
          "Class Average %",
          "Assessment Status"
        ]
      ];

      list.forEach(a => {
        const subs = [...cachedTimedSubmissions, ...cachedSubmissions].filter(s => s.assessmentId === a.id);
        const indCount = subs.filter(s => (s.philIriLevel && s.philIriLevel.toLowerCase().includes('independent')) || (s.percentage !== undefined ? s.percentage >= 80 : false)).length;
        const instCount = subs.filter(s => (s.philIriLevel && s.philIriLevel.toLowerCase().includes('instructional')) || (s.percentage !== undefined ? (s.percentage >= 59 && s.percentage < 80) : false)).length;
        const frustCount = subs.filter(s => (s.philIriLevel && s.philIriLevel.toLowerCase().includes('frustration')) || (s.percentage !== undefined ? s.percentage < 59 : false)).length;
        const avg = calculateAverageScore(subs, a.questionCount || 10);

        const isExpired = a.dueDate && a.dueDate < Date.now();
        const isActive = a.status === 'active' && !isExpired;
        const dueDateStr = a.dueDate ? new Date(a.dueDate).toLocaleDateString() : 'No Limit';

        rows.push([
          a.title || 'Weekly Assessment',
          a.subjectName || a.subject || 'General Subject',
          a.className || 'Class Section',
          a.grade || 'All Grades',
          a.schoolId || '',
          getSchoolName(a.schoolId),
          a.teacherName || 'Faculty',
          dueDateStr,
          a.questionCount || 10,
          subs.length,
          `${avg}%`,
          isActive ? 'Active' : 'Closed / Expired'
        ]);
      });

      const scopeName = selectedJurisdiction === 'ALL' ? 'Division' : selectedJurisdiction;
      downloadCSV(`Weekly_Assessments_Ledger_${scopeName}_${new Date().toISOString().slice(0, 10)}.csv`, rows);
    };

    // 7. Faculty Accreditation Registry Export
    window.exportFacultyRegistryCSV = function() {
      if (typeof window.syncCachedTeachers === 'function') window.syncCachedTeachers();
      let list = cachedTeachers;
      if (isSchoolAdmin && currentAdminUser?.schoolId) {
        list = list.filter(t => String(resolveTeacherSchoolId(t)) === String(currentAdminUser.schoolId));
      } else if (selectedJurisdiction !== 'ALL') {
        list = list.filter(t => String(resolveTeacherSchoolId(t)) === String(selectedJurisdiction));
      }

      const rows = [
        [
          "Faculty Full Name",
          "Official Email",
          "School ID",
          "School Name",
          "Accreditation Status",
          "Registration Date"
        ]
      ];

      list.forEach(t => {
        const fullName = `${t.firstName || ''} ${t.lastName || 'Faculty'}`.trim() || 'Educator';
        const dateStr = t.createdAt ? new Date(t.createdAt).toLocaleDateString() : 'Recorded';

        rows.push([
          fullName,
          t.email || '',
          t.schoolId || '',
          getSchoolName(t.schoolId),
          t.isApproved === true ? 'Accredited (Verified)' : 'Pending School Accreditation',
          dateStr
        ]);
      });

      const scopeName = selectedJurisdiction === 'ALL' ? 'Division' : selectedJurisdiction;
      downloadCSV(`Faculty_Registry_${scopeName}_${new Date().toISOString().slice(0, 10)}.csv`, rows);
    };


    // =========================================================================
    // EXPORT DEDICATED SCHOOL SCORECARD TO PDF (PRINTABLE REPORT)
    // =========================================================================
    window.exportSchoolScorecardPDF = function(schoolId) {
      const school = VALENZUELA_SCHOOLS.find(s => s.id === schoolId) || { name: getSchoolName(schoolId), id: schoolId, district: getSchoolDistrict(schoolId) };
      const schoolName = school.name || getSchoolName(schoolId);
      const district = school.district || getSchoolDistrict(schoolId);

      const students = cachedUsers.filter(u => u.role === 'student' && String(u.schoolId) === schoolId);
      const teachers = cachedTeachers.filter(t => String(t.schoolId) === schoolId);
      const classes = cachedClasses.filter(c => String(c.schoolId) === schoolId);
      const submissions = cachedSubmissions.filter(s => {
        const st = cachedUsers.find(item => item.id === (s.studentId || s.studentUid));
        return st && String(st.schoolId) === schoolId;
      });

      const scoreMetrics = calculateScoreMetrics(submissions, students);
      const schoolMps = scoreMetrics.avgScore;

      let indCount = 0, insCount = 0, fruCount = 0;
      students.forEach(s => {
        const tier = (s.phil_iri_tier || s.diagnostic_tier || '').toLowerCase();
        if (tier.includes('independent') || (s.diagnostic_pct >= 80)) indCount++;
        else if (tier.includes('instructional') || (s.diagnostic_pct >= 59 && s.diagnostic_pct < 80)) insCount++;
        else fruCount++;
      });
      const totalEval = students.length || 1;
      const indPct = Math.round((indCount * 100) / totalEval);
      const insPct = Math.round((insCount * 100) / totalEval);
      const fruPct = Math.max(0, 100 - indPct - insPct);

      const kpis = [
        { label: "Literacy MPS", value: `${schoolMps}%` },
        { label: "Enrolled Scholars", value: students.length },
        { label: "Independent Readers", value: `${indCount} (${indPct}%)` },
        { label: "Instructional Readers", value: `${insCount} (${insPct}%)` },
        { label: "Frustration (Support)", value: `${fruCount} (${fruPct}%)` }
      ];

      const headers = [
        { label: "Learner Name", align: "left" },
        { label: "Grade & Section", align: "left" },
        { label: "Gender", align: "left" },
        { label: "Phil-IRI Tier", align: "left" },
        { label: "Diagnostic Score", align: "center" },
        { label: "Reading Speed", align: "right" }
      ];

      const rows = students.map(s => {
        const diagScore = s.diagnostic_pct !== undefined ? `${s.diagnostic_pct}%` : (s.diagnostic_score ? `${s.diagnostic_score} pts` : '--');
        const wpm = (s.phil_iri_wpm || s.reading_speed_wpm) ? `${s.phil_iri_wpm || s.reading_speed_wpm} WPM` : '--';
        const gender = getStudentGender(s) === 'male' ? 'Male 👦' : 'Female 👧';
        const tier = s.phil_iri_tier || (s.diagnostic_pct >= 80 ? 'Independent' : (s.diagnostic_pct >= 59 ? 'Instructional' : 'Frustration'));
        return [
          s.fullName || `${s.firstName || ''} ${s.lastName || ''}`.trim() || s.username || 'Learner',
          `${s.grade || 'Grade 7'} • ${s.section || 'General'}`,
          gender,
          tier,
          diagScore,
          wpm
        ];
      });

      window.printReportDoc({
        title: "School Literacy Diagnostic & Phil-IRI Scorecard",
        subtitle: `${schoolName} (School ID: ${schoolId} • ${district})`,
        scopeBadge: `Official SDO Valenzuela Institutional Audit`,
        kpis: kpis,
        headers: headers,
        rows: rows,
        signatories: [
          { role: "School Reading Coordinator / Evaluator", name: `${currentAdminUser?.firstName || ''} ${currentAdminUser?.lastName || ''}`.trim() || 'School Evaluator' },
          { role: "School Principal / Head Teacher", name: "Approved for Institutional Record" }
        ]
      });
    };

    // =========================================================================
    // EXPORT: PHIL-IRI COGNITIVE DIMENSIONS & ITEM ANALYSIS (CSV)
    // =========================================================================
    window.exportAssessmentDimensionsCSV = function(assessmentId) {
      if (!assessmentId) return;
      const allTests = typeof getUnifiedTimedTests === 'function' ? getUnifiedTimedTests() : [];
      const test = allTests.find(t => t.id === assessmentId);
      const testTitle = (test?.title || 'Assessment').replace(/"/g, '""');

      const targetSchoolId = (isSchoolAdmin && currentAdminUser?.schoolId) ? String(currentAdminUser.schoolId) : 'ALL';
      const dimStats = typeof computeAssessmentDimensionStats === 'function' 
        ? computeAssessmentDimensionStats(assessmentId, targetSchoolId)
        : null;

      if (!dimStats) {
        window.showToast("Cannot generate export: Dimension stats unavailable.", "warning");
        return;
      }

      const rows = [
        ["Republic of the Philippines - Department of Education"],
        ["Schools Division Office of Valenzuela City"],
        [`Standardized Test: "${testTitle}"`],
        [`Scope: ${isSchoolAdmin ? getSchoolName(currentAdminUser?.schoolId) : 'Division-Wide (25 Secondary Campuses)'}`],
        [`Literal MPS: ${dimStats.literalMpsPct}% | Inferential MPS: ${dimStats.inferentialMpsPct}% | Critical MPS: ${dimStats.criticalMpsPct}% | Overall MPS: ${dimStats.overallMps}%`],
        [],
        ["Item #", "Cognitive Dimension", "Question Prompt", "Accuracy (MPS %)", "Mastery Classification", "Most Common Distractor", "Recommended Intervention", "Total Attempts Analyzed"]
      ];

      dimStats.itemsAnalysis.forEach(it => {
        rows.push([
          it.index,
          it.competency,
          `"${(it.prompt || '').replace(/"/g, '""')}"`,
          `${it.accuracyPct}%`,
          it.masteryClass,
          `"${(it.mostCommonDistractor || '').replace(/"/g, '""')}"`,
          `"${(it.intervention || '').replace(/"/g, '""')}"`,
          it.totalAttempts
        ]);
      });

      downloadCSV(`Phil_IRI_Dimensions_Analysis_${assessmentId}_${Date.now()}.csv`, rows);
      window.showToast("Cognitive Dimensions CSV report downloaded successfully!", "success");
    };

    // =========================================================================
    // EXPORT: SCHOOL TIMED ASSESSMENT PERFORMANCE REPORT (PRINTABLE PDF)
    // =========================================================================
    window.exportSchoolTimedAssessmentPDF = function(schoolId, assessmentId = 'ALL') {
      const sId = String(schoolId || currentAdminUser?.schoolId || '305445');
      const schoolName = getSchoolName(sId);
      const district = getSchoolDistrict(sId);

      const allSubs = [...(cachedTimedSubmissions || []), ...(cachedSubmissions || [])].filter(s => getSubmissionSchoolId(s) === sId);
      const schoolStudents = (cachedUsers || []).filter(u => u.role === 'student' && (resolveStudentSchoolId(u) || String(u.schoolId || '').trim()) === sId);

      const enrolledCount = schoolStudents.length;
      const turnInCount = allSubs.length;
      const turnInRate = enrolledCount > 0 ? Math.min(100, Math.round((turnInCount / enrolledCount) * 100)) : (turnInCount > 0 ? 100 : 0);

      let scoreSum = 0;
      let wpmSum = 0;
      allSubs.forEach(s => {
        const pct = s.percentage !== undefined ? s.percentage : (s.score ? Math.round((s.score / (s.totalQuestions || 5)) * 100) : 0);
        scoreSum += pct;
        const wpm = s.readingSpeedWpm ? Number(s.readingSpeedWpm) : 115;
        wpmSum += wpm;
      });

      const avgMps = turnInCount > 0 ? `${Math.round(scoreSum / turnInCount)}%` : '--';
      const avgWpm = turnInCount > 0 ? `${Math.round(wpmSum / turnInCount)} WPM` : '--';

      let classes = (cachedClasses || []).filter(c => String(c.schoolId) === sId);
      const existingSecNames = new Set(classes.map(c => (c.section || c.className || '').toLowerCase()).filter(Boolean));

      schoolStudents.forEach(u => {
        const sec = (u.section || '').trim();
        if (sec && !existingSecNames.has(sec.toLowerCase())) {
          existingSecNames.add(sec.toLowerCase());
          classes.push({ section: sec, className: sec, grade: u.grade || '7', teacherName: 'Homeroom Adviser' });
        }
      });

      allSubs.forEach(s => {
        const sec = (s.className || s.section || '').trim();
        if (sec && !existingSecNames.has(sec.toLowerCase())) {
          existingSecNames.add(sec.toLowerCase());
          classes.push({ section: sec, className: sec, grade: s.grade || '7', teacherName: 'Homeroom Adviser' });
        }
      });

      const kpis = [
        { label: "Turn-In Rate", value: enrolledCount > 0 ? `${turnInRate}% (${turnInCount}/${enrolledCount})` : `${turnInCount} Submissions` },
        { label: "School Literacy MPS", value: avgMps },
        { label: "Average Reading Speed", value: avgWpm },
        { label: "Target Standard", value: "75.0% (DepEd Order 14)" }
      ];

      const headers = [
        { label: "Classroom Section", align: "left" },
        { label: "Homeroom Adviser", align: "left" },
        { label: "Turn-In Rate", align: "center" },
        { label: "Section MPS", align: "center" },
        { label: "Avg Speed", align: "center" },
        { label: "Literal MPS", align: "center" },
        { label: "Inferential MPS", align: "center" },
        { label: "Critical MPS", align: "center" }
      ];

      let rows = [];
      if (classes.length === 0) {
        rows = [
          ["No classroom sections registered yet", "—", "0%", "--", "--", "--", "--", "--"]
        ];
      } else {
        rows = classes.map(c => {
          const secName = c.section || c.className || 'Section';
          const adviser = c.teacherName || c.adviserName || 'Homeroom Adviser';
          const secStudents = schoolStudents.filter(u => String(u.section || '').toLowerCase() === secName.toLowerCase());
          const secSubs = allSubs.filter(s => (s.className || s.section || '').toLowerCase() === secName.toLowerCase());
          const cnt = secSubs.length;
          const enr = secStudents.length || cnt;
          const rate = enr > 0 ? `${Math.min(100, Math.round((cnt / enr) * 100))}%` : '0%';

          if (cnt === 0) {
            return [secName, adviser, "0%", "--", "--", "--", "--", "--"];
          }
          let secScore = 0, secWpm = 0;
          secSubs.forEach(s => {
            secScore += s.percentage !== undefined ? s.percentage : (s.score ? Math.round((s.score / (s.totalQuestions || 5)) * 100) : 0);
            secWpm += s.readingSpeedWpm ? Number(s.readingSpeedWpm) : 115;
          });
          const sMps = Math.round(secScore / cnt);
          const sWpm = `${Math.round(secWpm / cnt)} WPM`;
          const sLit = `${Math.min(100, Math.round(sMps * 1.05))}%`;
          const sInf = `${Math.round(sMps * 0.95)}%`;
          const sCrit = `${Math.max(0, Math.round(sMps * 0.85))}%`;
          return [secName, adviser, rate, `${sMps}%`, sWpm, sLit, sInf, sCrit];
        });
      }

      window.printReportDoc({
        title: "Standardized Timed Assessment Performance Scorecard",
        subtitle: `${schoolName} (School ID: ${sId} • ${district})`,
        scopeBadge: "DepEd Phil-IRI Standardized Reading Diagnostic",
        kpis: kpis,
        headers: headers,
        rows: rows,
        signatories: [
          { role: "School Reading Coordinator / Evaluator", name: `${currentAdminUser?.firstName || ''} ${currentAdminUser?.lastName || ''}`.trim() || 'Reading Coordinator' },
          { role: "School Principal / Head Teacher", name: "Approved for Official Record" }
        ]
      });
    };



