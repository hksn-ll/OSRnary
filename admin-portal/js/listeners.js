    function syncMergedMaterials() {
      const combined = [...cachedRawMaterials];
      cachedLegacyMaterials.forEach(item => {
        if (!combined.some(m => m.id === item.id || (m.pdfUrl && m.pdfUrl === item.pdfUrl))) {
          combined.push(item);
        }
      });
      cachedMaterials = combined;
    }
    // Ensure global caches exist on window
    window.cachedWeeklyAssessments = window.cachedWeeklyAssessments || [];
    window.cachedDivisionTimedAssessments = window.cachedDivisionTimedAssessments || [];
    window.cachedTimedSubmissions = window.cachedTimedSubmissions || [];
    window.cachedSubmissions = window.cachedSubmissions || [];

    // Local references bound directly to global window cache
    var cachedWeeklyAssessments = window.cachedWeeklyAssessments;
    var cachedDivisionTimedAssessments = window.cachedDivisionTimedAssessments;
    var cachedTimedSubmissions = window.cachedTimedSubmissions;
    var cachedSubmissions = window.cachedSubmissions;

    window.setCachedTimedSubmissionsForTesting = function(list) {
      cachedTimedSubmissions = list;
      window.cachedTimedSubmissions = list;
    };
    window.setCachedSubmissionsForTesting = function(list) {
      cachedSubmissions = list;
      window.cachedSubmissions = list;
    };
    window.setCachedUsersForTesting = function(list) {
      cachedUsers = list;
      window.cachedUsers = list;
      if (typeof window.syncCachedTeachers === 'function') window.syncCachedTeachers();
    };
    window.setCachedClassesForTesting = function(list) {
      cachedClasses = list;
      window.cachedClasses = list;
      if (typeof window.syncCachedTeachers === 'function') window.syncCachedTeachers();
    };
    window.setCachedPendingStudentsForTesting = function(list) {
      cachedPendingStudents = list;
      window.cachedPendingStudents = list;
    };

    // =========================================================================
    // REAL-TIME LISTENERS & EDUCATOR SCHOOL RESOLUTION
    // =========================================================================
    function normalizeSchoolIdToken(rawVal) {
      const raw = String(rawVal || '').trim();
      if (!raw || raw.toUpperCase() === 'ALL' || raw.toUpperCase() === 'DIVISION_OFFICE') return raw;
      const schools = (Array.isArray(VALENZUELA_SCHOOLS) && VALENZUELA_SCHOOLS.length > 0)
        ? VALENZUELA_SCHOOLS
        : (typeof DEFAULT_VALENZUELA_SCHOOLS !== 'undefined' ? DEFAULT_VALENZUELA_SCHOOLS : []);
      if (schools.some(s => String(s.id) === raw)) return raw;
      const sixDigit = raw.match(/\b(\d{6})\b/);
      if (sixDigit && schools.some(s => String(s.id) === sixDigit[1])) return sixDigit[1];
      const rawLower = raw.toLowerCase();
      const byName = schools.find(s => {
        const sName = String(s.name || '').toLowerCase().trim();
        return sName && (sName === rawLower || rawLower.includes(sName) || sName.includes(rawLower));
      });
      if (byName) return String(byName.id);
      if (sixDigit) return sixDigit[1];
      return raw;
    }

    window.resolveTeacherSchoolId = function(teacher) {
      if (!teacher) return '';
      const directRaw = teacher.schoolId || teacher.school_id || teacher.approvedSchoolId || '';
      const normalizedDirect = normalizeSchoolIdToken(directRaw);
      if (normalizedDirect && normalizedDirect !== 'Unassigned' && normalizedDirect !== 'undefined' && normalizedDirect !== 'null') {
        return normalizedDirect;
      }
      const nameCandidate = teacher.schoolName || teacher.school || '';
      if (nameCandidate) {
        const byName = normalizeSchoolIdToken(nameCandidate);
        if (byName) return byName;
      }
      const uid = String(teacher.id || teacher.uid || '').trim();
      if (uid) {
        const cls = (cachedClasses || []).find(c =>
          c.teacherId === uid ||
          c.adviserId === uid ||
          (Array.isArray(c.teacherIds) && c.teacherIds.includes(uid))
        );
        if (cls && cls.schoolId) {
          return normalizeSchoolIdToken(cls.schoolId);
        }
        const subj = (cachedSubjects || []).find(s => s.teacherId === uid && s.schoolId);
        if (subj && subj.schoolId) {
          return normalizeSchoolIdToken(subj.schoolId);
        }
        const mat = (cachedRawMaterials || []).concat(cachedLegacyMaterials || []).find(m => m.teacherId === uid && m.schoolId);
        if (mat && mat.schoolId) {
          return normalizeSchoolIdToken(mat.schoolId);
        }
      }
      return String(directRaw || '').trim();
    };

    window.syncCachedTeachers = function() {
      const linkedTeacherUids = new Set();
      (cachedClasses || []).forEach(c => {
        if (c.teacherId) linkedTeacherUids.add(String(c.teacherId).trim());
        if (c.adviserId) linkedTeacherUids.add(String(c.adviserId).trim());
        if (Array.isArray(c.teacherIds)) {
          c.teacherIds.forEach(tid => { if (tid) linkedTeacherUids.add(String(tid).trim()); });
        }
      });
      (cachedSubjects || []).forEach(s => {
        if (s.teacherId) linkedTeacherUids.add(String(s.teacherId).trim());
      });
      (cachedRawMaterials || []).concat(cachedLegacyMaterials || []).forEach(m => {
        if (m.teacherId) linkedTeacherUids.add(String(m.teacherId).trim());
      });

      const seenUids = new Set();
      const teachersList = [];

      (cachedUsers || []).forEach(u => {
        const uid = String(u.id || u.uid || '').trim();
        if (!uid || seenUids.has(uid)) return;
        const roleLower = String(u.role || '').toLowerCase().trim();
        if (roleLower === 'student' || roleLower === 'super_admin' || roleLower === 'admin' || u.schoolId === 'DIVISION_OFFICE') {
          return;
        }
        const isEducatorRole = roleLower === 'teacher' || roleLower === 'faculty' || roleLower === 'educator' || roleLower === 'school_admin';
        const isLinkedEducator = linkedTeacherUids.has(uid);
        if (isEducatorRole || isLinkedEducator) {
          seenUids.add(uid);
          const resolvedSchool = window.resolveTeacherSchoolId(u);
          teachersList.push({
            ...u,
            id: uid,
            schoolId: resolvedSchool || String(u.schoolId || '').trim(),
            isApproved: roleLower === 'school_admin' ? (u.isApproved !== false) : (u.isApproved === true)
          });
        }
      });

      // Synthesize fallback records for any class advisers whose user profile doc is missing
      (cachedClasses || []).forEach(c => {
        const uid = String(c.teacherId || c.adviserId || '').trim();
        if (!uid || seenUids.has(uid)) return;
        const adviserLabel = String(c.adviserName || c.teacherName || '').trim();
        if (!adviserLabel || adviserLabel.toLowerCase().includes('unassigned') || adviserLabel.toLowerCase().includes('vacant')) return;
        seenUids.add(uid);
        const nameParts = adviserLabel.split(/\s+/);
        const firstName = nameParts[0] || 'Educator';
        const lastName = nameParts.slice(1).join(' ') || '';
        teachersList.push({
          id: uid,
          firstName,
          lastName,
          email: c.teacherEmail || c.adviserEmail || 'Registered via Mobile App',
          schoolId: normalizeSchoolIdToken(c.schoolId || ''),
          role: 'teacher',
          isApproved: true,
          createdAt: c.createdAt || null
        });
      });

      cachedTeachers = teachersList;
    };

    function startRealtimeListeners() {
      stopRealtimeListeners();

      // 1. users (Unified division listener with school-scoped fallback)
      const unsubUsers = onSnapshot(collection(db, "users"), (snapshot) => {
        cachedUsers = snapshot.docs.map(d => ({ ...d.data(), id: d.id }));
        syncCachedTeachers();
        scheduleDashboardRender();
        scheduleLibraryRender();
        if (typeof syncUsersToQuizAttempts === 'function') {
          syncUsersToQuizAttempts();
        }
      }, (err) => {
        console.warn("Users division listener warning:", err);
        if (isSchoolAdmin && currentAdminUser?.schoolId) {
          const fallbackRef = query(collection(db, "users"), where("schoolId", "==", String(currentAdminUser.schoolId)));
          onSnapshot(fallbackRef, (snapshot) => {
            cachedUsers = snapshot.docs.map(d => ({ ...d.data(), id: d.id }));
            syncCachedTeachers();
            scheduleDashboardRender();
            scheduleLibraryRender();
            if (typeof syncUsersToQuizAttempts === 'function') {
              syncUsersToQuizAttempts();
            }
          }, (err2) => console.error("Users school fallback error:", err2));
        }
      });
      unsubs.push(unsubUsers);

      // 2. classes
      const unsubClasses = onSnapshot(collection(db, "classes"), (snapshot) => {
        cachedClasses = snapshot.docs.map(d => ({ ...d.data(), id: d.id }));
        syncCachedTeachers();
        scheduleDashboardRender();
      }, (err) => console.error("Classes listener:", err));
      unsubs.push(unsubClasses);

      // 3. pending_students (Unified division listener with school-scoped fallback)
      const unsubPending = onSnapshot(collection(db, "pending_students"), (snapshot) => {
        cachedPendingStudents = snapshot.docs.map(d => ({ ...d.data(), id: d.id }));
        scheduleDashboardRender();
      }, (err) => {
        console.warn("Pending students division listener warning:", err);
        // Resilient fallback: if division-wide listing is restricted, subscribe via school-scoped query
        if (isSchoolAdmin && currentAdminUser?.schoolId) {
          const fallbackRef = query(collection(db, "pending_students"), where("schoolId", "==", String(currentAdminUser.schoolId)));
          onSnapshot(fallbackRef, (snapshot) => {
            cachedPendingStudents = snapshot.docs.map(d => ({ ...d.data(), id: d.id }));
            scheduleDashboardRender();
          }, (err2) => console.error("Pending students fallback error:", err2));
        }
      });
      unsubs.push(unsubPending);

      // 4. library_subjects (TeacherLibraryActivity.kt)
      const unsubSubjects = onSnapshot(collection(db, "library_subjects"), (snapshot) => {
        cachedSubjects = snapshot.docs.map(d => ({ ...d.data(), id: d.id }));
        syncCachedTeachers();
        scheduleDashboardRender();
        scheduleLibraryRender();
      }, (err) => console.warn("Subjects listener:", err));
      unsubs.push(unsubSubjects);

      // 5. library_materials (SubjectDetailActivity.kt)
      const unsubMaterials = onSnapshot(collection(db, "library_materials"), (snapshot) => {
        cachedRawMaterials = snapshot.docs.map(d => ({ ...d.data(), id: d.id }));
        syncCachedTeachers();
        syncMergedMaterials();
        scheduleDashboardRender();
        scheduleLibraryRender();
      }, (err) => console.warn("Materials listener:", err));
      unsubs.push(unsubMaterials);

      // 6. library (TeacherHomeFragment.kt legacy/alternative collection)
      const unsubLegacyLibrary = onSnapshot(collection(db, "library"), (snapshot) => {
        cachedLegacyMaterials = snapshot.docs.map(d => ({ ...d.data(), id: d.id, isLegacy: true }));
        syncCachedTeachers();
        syncMergedMaterials();
        scheduleDashboardRender();
        scheduleLibraryRender();
      }, (err) => console.warn("Legacy library listener:", err));
      unsubs.push(unsubLegacyLibrary);

      // 7. weekly_assessments (SubjectDetailActivity.kt & QuizEditorActivity.kt)
      const unsubAssessments = onSnapshot(collection(db, "weekly_assessments"), (snapshot) => {
        const list = snapshot.docs.map(d => ({ ...d.data(), id: d.id }));
        cachedWeeklyAssessments = list;
        window.cachedWeeklyAssessments = list;
        scheduleAssessmentsRender();
        scheduleDashboardRender();
      }, (err) => console.warn("Weekly assessments listener:", err));
      unsubs.push(unsubAssessments);

      // 8. assessment_submissions (WeeklyAssessmentActivity.kt)
      const unsubSubmissions = onSnapshot(collection(db, "assessment_submissions"), (snapshot) => {
        const list = snapshot.docs.map(d => {
          const data = d.data();
          const studentId = String(data.studentUid || data.studentId || data.uid || data.userId || '').trim();
          return { ...data, id: d.id, studentId, studentUid: studentId };
        });
        cachedSubmissions = list;
        window.cachedSubmissions = list;
        scheduleAssessmentsRender();
        scheduleDashboardRender();
        if (typeof syncSubmissionsToQuizAttempts === 'function') {
          syncSubmissionsToQuizAttempts();
        }
      }, (err) => console.warn("Assessment submissions listener:", err));
      unsubs.push(unsubSubmissions);

      // 8.1 division_timed_assessments (SDO Super Admin standardized timed assessments)
      const unsubDivisionAssessments = onSnapshot(collection(db, "division_timed_assessments"), (snapshot) => {
        const list = snapshot.docs.map(d => ({ ...d.data(), id: d.id }));
        cachedDivisionTimedAssessments = list;
        window.cachedDivisionTimedAssessments = list;
        scheduleAssessmentsRender();
        scheduleDashboardRender();
      }, (err) => console.warn("Division timed assessments listener:", err));
      unsubs.push(unsubDivisionAssessments);

      // 8.2 timed_assessment_submissions (Submissions with Phil-IRI proficiency & performance data)
      const unsubTimedSubmissions = onSnapshot(collection(db, "timed_assessment_submissions"), (snapshot) => {
        const list = snapshot.docs.map(d => ({ ...d.data(), id: d.id }));
        cachedTimedSubmissions = list;
        window.cachedTimedSubmissions = list;
        scheduleAssessmentsRender();
        scheduleDashboardRender();
        if (typeof syncSubmissionsToQuizAttempts === 'function') {
          syncSubmissionsToQuizAttempts();
        }
      }, (err) => {
        console.warn("Timed submissions listener:", err);
        if (isSchoolAdmin && currentAdminUser?.schoolId) {
          const fallbackRef = query(collection(db, "timed_assessment_submissions"), where("schoolId", "==", String(currentAdminUser.schoolId)));
          onSnapshot(fallbackRef, (snapshot) => {
            const list = snapshot.docs.map(d => ({ ...d.data(), id: d.id }));
            cachedTimedSubmissions = list;
            window.cachedTimedSubmissions = list;
            scheduleAssessmentsRender();
            scheduleDashboardRender();
            if (typeof syncSubmissionsToQuizAttempts === 'function') {
              syncSubmissionsToQuizAttempts();
            }
          }, (err2) => console.error("Timed submissions school fallback error:", err2));
        }
      });
      unsubs.push(unsubTimedSubmissions);

      // 9. school_admin_invites
      const unsubInvites = onSnapshot(collection(db, "school_admin_invites"), (snapshot) => {
        cachedSchoolInvites = snapshot.docs.map(d => ({ id: d.id, ...d.data() }));
        scheduleDashboardRender();
      }, (err) => console.warn("Invites listener:", err));
      unsubs.push(unsubInvites);

      // 10. schools collection (Dynamic systemwide junior high school roster)
      const unsubSchools = onSnapshot(collection(db, "schools"), (snapshot) => {
        // 1. Seed base roster from official Junior High Schools of Valenzuela
        const schoolMap = new Map();
        DEFAULT_VALENZUELA_SCHOOLS.forEach(s => {
          schoolMap.set(String(s.id), {
            id: String(s.id),
            name: s.name,
            district: s.district || "District 1",
            division: "Valenzuela City",
            status: "active",
            adminUid: "",
            adminEmail: "",
            adminName: ""
          });
        });

        const NON_JUNIOR_IDS = ['305565', '305576', '305708', '340729'];

        // 2. Overlay Firestore documents (custom added schools, assigned school admins, or status updates)
        if (!snapshot.empty) {
          if (isSuperAdmin) {
            snapshot.docs.forEach(d => {
              if (NON_JUNIOR_IDS.includes(d.id)) {
                deleteDoc(doc(db, "schools", d.id)).catch(() => {});
              }
            });
          }

          snapshot.docs.forEach(d => {
            const data = d.data();
            const id = String(d.id);
            if (NON_JUNIOR_IDS.includes(id)) return;

            const status = (data.status || 'active').toLowerCase();
            if (status === 'deleted' || status === 'inactive') {
              schoolMap.delete(id);
            } else {
              const existing = schoolMap.get(id) || {};
              schoolMap.set(id, {
                id: id,
                name: data.schoolName || data.name || existing.name || id,
                district: data.district || existing.district || (DEFAULT_VALENZUELA_SCHOOLS.find(s => String(s.id) === id)?.district || "District 1"),
                division: data.division || existing.division || "Valenzuela City",
                status: "active",
                adminUid: data.adminUid || existing.adminUid || "",
                adminEmail: data.adminEmail || existing.adminEmail || "",
                adminName: data.adminName || existing.adminName || ""
              });
            }
          });
        }

        VALENZUELA_SCHOOLS = Array.from(schoolMap.values()).sort((a, b) => a.name.localeCompare(b.name));
        syncCachedTeachers();
        updateSchoolsCountUI();
        populateSchoolsDropdowns();
        scheduleDashboardRender();
      }, (err) => console.warn("Schools real-time listener error:", err));
      unsubs.push(unsubSchools);

      // 11. announcements & broadcasts
      const unsubAnnouncements = onSnapshot(collection(db, "announcements"), (snapshot) => {
        cachedAnnouncements = snapshot.docs.map(d => ({ id: d.id, ...d.data() }));
        renderAnnouncementsFeed();
        updateDirectivesBanner();
      }, (err) => console.warn("Announcements listener error:", err));
      unsubs.push(unsubAnnouncements);
    }

    function stopRealtimeListeners() {
      if (renderDashboardRaf) { cancelAnimationFrame(renderDashboardRaf); renderDashboardRaf = null; }
      if (renderLibraryRaf) { cancelAnimationFrame(renderLibraryRaf); renderLibraryRaf = null; }
      if (renderAssessmentsRaf) { cancelAnimationFrame(renderAssessmentsRaf); renderAssessmentsRaf = null; }
      if (unsubRepliesCurrent) { unsubRepliesCurrent(); unsubRepliesCurrent = null; }
      if (unsubViewsCurrent) { unsubViewsCurrent(); unsubViewsCurrent = null; }
      unsubs.forEach(fn => fn());
      unsubs = [];
    }

    window.refreshAllData = function() {
      startRealtimeListeners();
      window.showToast("Synchronizing with division data network...", "info");
    };

    function updateSchoolsCountUI() {
      const count = VALENZUELA_SCHOOLS.length;
      document.querySelectorAll('.school-count-text').forEach(el => el.textContent = count);
      const totalSecBadge = document.getElementById('totalSchoolsCountBadge');
      if (totalSecBadge) totalSecBadge.textContent = `${count} Participating Schools`;
      const jurisOpt = document.querySelector('#jurisdictionSelect option[value="ALL"]');
      if (jurisOpt) jurisOpt.textContent = `All Junior High Schools`;
    }

    function populateSchoolsDropdowns() {
      const filterDropdowns = [
        document.getElementById('jurisdictionSelect'),
        document.getElementById('selectFacultySchoolFilter'),
        document.getElementById('selectSectionSchoolFilter'),
        document.getElementById('learnerSchoolFilter'),
        document.getElementById('selectCredentialSchoolFilter'),
        document.getElementById('selectQuizSchoolFilter'),
        document.getElementById('timedAssessmentSchoolFilter')
      ];

      const modalDropdowns = [
        document.getElementById('teacherSchoolSelect'),
        document.getElementById('sectionSchoolSelect'),
        document.getElementById('batchSchoolSelect'),
        document.getElementById('subjectSchoolSelect')
      ];

      let eligibleSchools = VALENZUELA_SCHOOLS;
      if (selectedDistrictFilter !== 'ALL') {
        eligibleSchools = eligibleSchools.filter(s => getSchoolDistrict(s.id) === selectedDistrictFilter);
      }

      filterDropdowns.forEach(sel => {
        if (!sel) return;
        const prevVal = sel.value || selectedJurisdiction || 'ALL';
        const isGlobal = sel.id === 'globalSchoolFilter';
        const defaultLabel = isGlobal 
          ? (selectedDistrictFilter !== 'ALL' ? `All ${selectedDistrictFilter} Schools` : 'All Junior High Schools (SDO Division)')
          : (selectedDistrictFilter !== 'ALL' ? `All ${selectedDistrictFilter} Schools` : 'All Junior High Schools');

        sel.innerHTML = `<option value="ALL">${defaultLabel}</option>`;

        if (selectedDistrictFilter === 'ALL') {
          const d1Schools = VALENZUELA_SCHOOLS.filter(s => s.district === 'District 1');
          const d2Schools = VALENZUELA_SCHOOLS.filter(s => s.district === 'District 2');

          if (d1Schools.length > 0) {
            const grp1 = document.createElement('optgroup');
            grp1.label = 'District 1 Schools';
            d1Schools.forEach(s => {
              const opt = document.createElement('option');
              opt.value = s.id;
              opt.textContent = `${s.name} (${s.id})`;
              grp1.appendChild(opt);
            });
            sel.appendChild(grp1);
          }

          if (d2Schools.length > 0) {
            const grp2 = document.createElement('optgroup');
            grp2.label = 'District 2 Schools';
            d2Schools.forEach(s => {
              const opt = document.createElement('option');
              opt.value = s.id;
              opt.textContent = `${s.name} (${s.id})`;
              grp2.appendChild(opt);
            });
            sel.appendChild(grp2);
          }
        } else {
          eligibleSchools.forEach(s => {
            const opt = document.createElement('option');
            opt.value = s.id;
            opt.textContent = `${s.name} (${s.id})`;
            sel.appendChild(opt);
          });
        }

        if (prevVal && (prevVal === 'ALL' || VALENZUELA_SCHOOLS.some(s => s.id === prevVal))) {
          sel.value = prevVal;
        } else {
          sel.value = 'ALL';
        }

        if (isSchoolAdmin && currentAdminUser && currentAdminUser.schoolId) {
          sel.value = String(currentAdminUser.schoolId);
          sel.disabled = true;
        }
      });

      modalDropdowns.forEach(sel => {
        if (!sel) return;
        const prevVal = sel.value;
        sel.innerHTML = `<option value="">Select School...</option>`;
        VALENZUELA_SCHOOLS.forEach(s => {
          const opt = document.createElement('option');
          opt.value = s.id;
          opt.textContent = `${s.name} (${s.id})`;
          sel.appendChild(opt);
        });
        if (prevVal && VALENZUELA_SCHOOLS.some(s => s.id === prevVal)) {
          sel.value = prevVal;
        }
      });
    }

    function populateSubjectDropdown() {
      const sel = document.getElementById('matSubjectSelect');
      if (!sel) return;
      sel.innerHTML = `<option value="">Select Parent Subject Folder...</option>`;
      cachedSubjects.forEach(sub => {
        const opt = document.createElement('option');
        opt.value = sub.id;
        opt.textContent = `${sub.name || 'Unnamed Subject'} (ID: ${sub.id.substring(0, 6)}...)`;
        sel.appendChild(opt);
      });
    }

    window.handleJurisdictionChange = function(val) {
      if (isSchoolAdmin) {
        selectedJurisdiction = String(currentAdminUser.schoolId);
      } else {
        selectedJurisdiction = val || "ALL";
      }
      selectedSubjectFilter = "ALL";

      // Synchronize all school filter dropdowns across tabs
      ['jurisdictionSelect', 'globalSchoolFilter', 'selectQuizSchoolFilter', 'timedAssessmentSchoolFilter', 'learnerSchoolFilter'].forEach(id => {
        const el = document.getElementById(id);
        if (el && el.value !== selectedJurisdiction) {
          el.value = selectedJurisdiction;
        }
      });

      updateFilterResetButtonState();
      updateDashboardMetrics();
      renderTeachersTable();
      renderSectionsTable();
      renderPendingStudentsTable();
      renderPerformanceTable();
      renderSubjectFolders();
      renderMaterialsTable();
      updateAssessmentMetrics();
      renderAssessmentsTable();
      if (typeof compileAllQuizAttempts === 'function') {
        compileAllQuizAttempts();
      }
      if (typeof renderTimedAssessmentsTable === 'function') {
        renderTimedAssessmentsTable();
      }

      const label = selectedJurisdiction === 'ALL' ? 'All Secondary Schools' : getSchoolName(selectedJurisdiction);
      window.showToast(`School filter: ${label}`, 'info');
    };

    window.handleGlobalSchoolFilter = function(val) {
      handleJurisdictionChange(val);
    };

    window.handleQuizSchoolFilterChange = function(val) {
      handleJurisdictionChange(val);
    };

    window.handleTimedAssessmentSchoolFilterChange = function(val) {
      handleJurisdictionChange(val);
    };

    window.handleLearnerSchoolFilterChange = function(val) {
      handleJurisdictionChange(val);
    };


window.syncMergedMaterials = syncMergedMaterials;
window.normalizeSchoolIdToken = normalizeSchoolIdToken;
window.startRealtimeListeners = startRealtimeListeners;
window.stopRealtimeListeners = stopRealtimeListeners;
window.updateSchoolsCountUI = updateSchoolsCountUI;
window.populateSchoolsDropdowns = populateSchoolsDropdowns;
window.populateSubjectDropdown = populateSubjectDropdown;
window.syncMergedMaterials = syncMergedMaterials;
