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
        students = students.filter(s => String(s.schoolId) === String(currentAdminUser.schoolId));
      } else if (selectedJurisdiction !== 'ALL') {
        students = students.filter(s => String(s.schoolId) === selectedJurisdiction);
      }
      if (typeof learnerSectionFilter !== 'undefined' && learnerSectionFilter !== 'ALL') {
        students = students.filter(s => String(s.section || '').trim().toLowerCase() === learnerSectionFilter.toLowerCase());
      }
      if (selectedJurisdiction !== 'ALL') {
        students = students.filter(s => String(s.schoolId) === selectedJurisdiction);
        submissions = submissions.filter(s => {
          const a = cachedWeeklyAssessments.find(item => item.id === s.assessmentId);
          return (a && String(a.schoolId) === selectedJurisdiction) || String(s.schoolId) === selectedJurisdiction;
        });
      }

      if (students.length === 0) {
        window.showToast("No learner records available to print.", "warning");
        return;
      }

      const metrics = calculateScoreMetrics(submissions, students);
      const scopeLabel = selectedJurisdiction === 'ALL' ? 'Division-Wide (All Schools)' : getSchoolName(selectedJurisdiction);

      const rows = students.map((s, idx) => {
        const studentSubs = submissions.filter(sub => sub.studentId === s.uid);
        const sMetrics = calculateScoreMetrics(studentSubs, [s]);
        const score = sMetrics.avgScore;
        const statusBadge = score >= 85 ? '<span style="color:#059669; font-weight:700;">Mastery</span>'
          : score >= 60 ? '<span style="color:#D97706; font-weight:700;">Proficient</span>'
          : '<span style="color:#E11D48; font-weight:700;">Needs Support</span>';

        return [
          (idx + 1).toString(),
          s.fullName || s.name || s.username || 'Student',
          getSchoolName(s.schoolId),
          `${s.grade || 'Grade 7'} - ${s.section || 'General'}`,
          `${score}%`,
          studentSubs.length.toString(),
          `${s.current_streak || 0}d`,
          statusBadge
        ];
      });

      window.printReportDoc({
        title: "Learner Performance & Reading Summary",
        subtitle: `Reading diagnostics and quiz performance across ${scopeLabel}`,
        scopeBadge: selectedJurisdiction === 'ALL' ? 'Division' : 'School Scope',
        kpis: [
          { label: "Total Learners", value: students.length },
          { label: "Average Quiz Score", value: metrics.avgScore + "%" },
          { label: "Mastery (85%+)", value: metrics.mastery },
          { label: "Proficient (60-84%)", value: metrics.proficient },
          { label: "Needs Support (<60%)", value: metrics.support }
        ],
        headers: [
          { label: "#", align: "left" },
          { label: "Learner Name", align: "left" },
          { label: "School", align: "left" },
          { label: "Grade & Section", align: "left" },
          { label: "Score", align: "right" },
          { label: "Quizzes", align: "right" },
          { label: "Streak", align: "right" },
          { label: "Status", align: "center" }
        ],
        rows: rows,
        signatories: [
          { label: "Prepared by", name: currentAdminUser?.displayName || "School Administrator", role: "School Administrator" },
          { label: "Noted by", name: "Schools Division Superintendent", role: "SDO Valenzuela City" }
        ]
      });
    };

    // PDF 2: Reading Support & Intervention List
    window.exportInterventionsPDF = function() {
      let students = cachedUsers.filter(u => u.role === 'student');
      let submissions = cachedSubmissions;
      if (selectedJurisdiction !== 'ALL') {
        students = students.filter(s => String(s.schoolId) === selectedJurisdiction);
        submissions = submissions.filter(s => {
          const a = cachedWeeklyAssessments.find(item => item.id === s.assessmentId);
          return (a && String(a.schoolId) === selectedJurisdiction) || String(s.schoolId) === selectedJurisdiction;
        });
      }

      const supportList = students.filter(s => {
        const studentSubs = submissions.filter(sub => sub.studentId === s.uid);
        const sMetrics = calculateScoreMetrics(studentSubs, [s]);
        return sMetrics.avgScore < 60 || studentSubs.length === 0;
      });

      if (supportList.length === 0) {
        window.showToast("No learners currently needing reading support.", "info");
        return;
      }

      const scopeLabel = selectedJurisdiction === 'ALL' ? 'Division-Wide' : getSchoolName(selectedJurisdiction);

      const rows = supportList.map((s, idx) => {
        const studentSubs = submissions.filter(sub => sub.studentId === s.uid);
        const sMetrics = calculateScoreMetrics(studentSubs, [s]);
        const reason = sMetrics.avgScore < 60 && studentSubs.length > 0 ? "Score below 60%" : "Inactive in Reading Quizzes";

        return [
          (idx + 1).toString(),
          s.fullName || s.name || s.username || 'Student',
          getSchoolName(s.schoolId),
          `${s.grade || 'Grade 7'} - ${s.section || 'General'}`,
          `${sMetrics.avgScore}%`,
          studentSubs.length.toString(),
          `${s.current_streak || 0}d`,
          reason
        ];
      });

      window.printReportDoc({
        title: "Reading Support & Intervention List",
        subtitle: `Learners recommended for remedial reading and vocabulary assistance (${scopeLabel})`,
        scopeBadge: "Intervention Priority",
        kpis: [
          { label: "Support Queue Count", value: supportList.length },
          { label: "Total Enrolled in Scope", value: students.length },
          { label: "Support Share", value: Math.round((supportList.length / (students.length || 1)) * 100) + "%" }
        ],
        headers: [
          { label: "#", align: "left" },
          { label: "Learner Name", align: "left" },
          { label: "School", align: "left" },
          { label: "Grade & Section", align: "left" },
          { label: "Score", align: "right" },
          { label: "Quizzes", align: "right" },
          { label: "Streak", align: "right" },
          { label: "Flag Reason", align: "left" }
        ],
        rows: rows,
        signatories: [
          { label: "Prepared by", name: currentAdminUser?.displayName || "Reading Coordinator", role: "Classroom Reading Coordinator" },
          { label: "Approved for Intervention", name: "School Principal / Head", role: "School Administration" }
        ]
      });
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
      let submissions = cachedSubmissions;

      if (isSchoolAdmin && currentAdminUser?.schoolId) {
        students = students.filter(s => String(s.schoolId) === String(currentAdminUser.schoolId));
      } else if (selectedJurisdiction !== 'ALL') {
        students = students.filter(s => String(s.schoolId) === selectedJurisdiction);
      }
      if (typeof learnerSectionFilter !== 'undefined' && learnerSectionFilter !== 'ALL') {
        students = students.filter(s => String(s.section || '').trim().toLowerCase() === learnerSectionFilter.toLowerCase());
      }

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
          "Gamification Level",
          "Current XP",
          "Reading Streak (Days)",
          "Quests Completed",
          "Assessments Completed",
          "Average Score %",
          "Mastery Tier"
        ]
      ];

      students.forEach(st => {
        const stSubs = submissions.filter(s => s.studentId === st.id);
        const avg = calculateAverageScore(stSubs);

        const fullName = `${st.firstName || ''} ${st.lastName || 'Learner'}`.trim() || 'Learner';
        let tier = "Needs Support (<60%)";
        if (stSubs.length > 0) {
          if (avg >= 80) tier = "Mastery (80%+)";
          else if (avg >= 60) tier = "Proficient (60%-79%)";
        }

        rows.push([
          fullName,
          st.username ? `@${st.username}` : (st.email || ''),
          st.email || st.id || '',
          st.schoolId || '',
          getSchoolName(st.schoolId),
          st.grade || 'Unassigned',
          st.section || 'General',
          st.level || 1,
          st.current_xp || 0,
          st.current_streak || 0,
          Array.isArray(st.quests_completed) ? st.quests_completed.length : 0,
          stSubs.length,
          `${avg}%`,
          tier
        ]);
      });

      const scopeName = selectedJurisdiction === 'ALL' ? 'Division' : selectedJurisdiction;
      downloadCSV(`Learner_Performance_Roster_${scopeName}_${new Date().toISOString().slice(0, 10)}.csv`, rows);
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



