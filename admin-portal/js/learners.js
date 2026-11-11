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

    window.populateLearnerSectionDropdown = function() {
      const select = document.getElementById('selectLearnerSectionFilter');
      if (!select) return;

      const currentVal = learnerSectionFilter || 'ALL';
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

      const uniqueSections = Array.from(new Set(classes.map(c => (c.section || c.className || '').trim()).filter(Boolean))).sort();

      let optionsHtml = '<option value="ALL">All Sections</option>';
      uniqueSections.forEach(sec => {
        const isSelected = (sec.toLowerCase() === currentVal.toLowerCase()) ? 'selected' : '';
        optionsHtml += `<option value="${escapeHtml(sec)}" ${isSelected}>${escapeHtml(sec)}</option>`;
      });

      select.innerHTML = optionsHtml;
    };

    window.handleLearnerSectionFilter = function(val) {
      learnerSectionFilter = val || 'ALL';
      renderPerformanceTable();
    };

    function renderPerformanceTable() {
      const tbody = document.getElementById('performanceTableBody');
      if (!tbody) return;

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
          list = list.filter(u => String(u.section || '').trim().toLowerCase() === activeLearnerSection.toLowerCase());
        }

        // Compute academic metrics helper for a student
        const getStudentMpsAndTier = (st) => {
          const stSubs = (cachedSubmissions || []).filter(s => s.studentId === st.id);
          let avg = calculateAverageScore(stSubs);
          let rawTier = (st.phil_iri_tier || st.diagnostic_tier || '').toLowerCase().trim();
          let tier = 'unassessed';
          let tierLabel = 'Unassessed';
          let tierClass = 'bg-slate-100 text-slate-700 border-slate-200';

          if (rawTier.includes('independent') || (stSubs.length > 0 && avg >= 80)) {
            tier = 'independent';
            tierLabel = 'Independent';
            tierClass = 'bg-emerald-50 text-emerald-800 border-emerald-200 font-bold';
          } else if (rawTier.includes('instructional') || (stSubs.length > 0 && avg >= 60)) {
            tier = 'instructional';
            tierLabel = 'Instructional';
            tierClass = 'bg-amber-50 text-amber-800 border-amber-200 font-bold';
          } else if (rawTier.includes('frustration') || (stSubs.length > 0 && avg > 0 && avg < 60) || (st.diagnostic_pct && st.diagnostic_pct < 60)) {
            tier = 'frustration';
            tierLabel = 'Frustration';
            tierClass = 'bg-rose-50 text-rose-800 border-rose-200 font-bold';
          }

          const mpsValue = stSubs.length > 0 ? avg : (st.diagnostic_pct || (avg > 0 ? avg : null));
          return { stSubs, avg, tier, tierLabel, tierClass, mpsValue };
        };

        // Compute real-time academic KPIs across this list before tier filtering
        let countEval = 0;
        let countInd = 0;
        let countIns = 0;
        let countFru = 0;
        let totalStreak = 0;

        list.forEach(u => {
          const m = getStudentMpsAndTier(u);
          if (m.stSubs.length > 0 || m.tier !== 'unassessed') countEval++;
          if (m.tier === 'independent') countInd++;
          else if (m.tier === 'instructional') countIns++;
          else if (m.tier === 'frustration') countFru++;
          totalStreak += (u.current_streak || 0);
        });

        const avgStreak = list.length > 0 ? Math.round(totalStreak / list.length) : 0;

        const elKpiEval = document.getElementById('perfKpiTotalEvaluated');
        if (elKpiEval) elKpiEval.textContent = countEval;
        const elKpiInd = document.getElementById('perfKpiIndependent');
        if (elKpiInd) elKpiInd.textContent = countInd;
        const elKpiIns = document.getElementById('perfKpiInstructional');
        if (elKpiIns) elKpiIns.textContent = countIns;
        const elKpiFru = document.getElementById('perfKpiFrustration');
        if (elKpiFru) elKpiFru.textContent = countFru;
        const elKpiStreak = document.getElementById('perfKpiStreak');
        if (elKpiStreak) elKpiStreak.textContent = `${avgStreak}d`;

        // Dedicated Phil-IRI Tier Filter
        const activeLearnerTier = learnerTierFilter || document.getElementById('selectLearnerTierFilter')?.value || 'ALL';
        if (activeLearnerTier !== 'ALL') {
          list = list.filter(u => {
            const m = getStudentMpsAndTier(u);
            return m.tier === activeLearnerTier;
          });
        }

        // Dedicated Smart Tokenized Search
        const activeLearnerSearch = learnerSearchQuery || (document.getElementById('inputLearnerSearch')?.value || '').toLowerCase().trim();
        if (activeLearnerSearch) {
          list = list.filter(u => {
            const textBlob = `${u.firstName || ''} ${u.lastName || ''} ${u.email || ''} ${u.username || ''} ${u.section || ''} ${u.lrn || ''} ${u.grade || ''} ${u.schoolId || ''} ${getSchoolName(u.schoolId)}`;
            return matchSmartSearch(textBlob, activeLearnerSearch);
          });
        }

        const countBadge = document.getElementById('learnerCountSummaryBadge');
        if (countBadge) {
          countBadge.textContent = `Showing ${list.length} reading performance profile(s)${selectedJurisdiction !== 'ALL' ? ' for ' + getSchoolName(selectedJurisdiction) : ''}`;
        }

        if (list.length === 0) {
          tbody.innerHTML = `
            <tr>
              <td colspan="7" class="p-8 text-center text-slate-400 font-medium">
                No active student reading performance profiles match the active search/filters.
              </td>
            </tr>`;
          return;
        }

        tbody.innerHTML = list.map(u => {
          const streak = u.current_streak || 0;
          const quests = (u.quests_completed && u.quests_completed.length) || 0;
          const fName = u.firstName || u.first_name || '';
          const lName = u.lastName || u.last_name || '';
          const fullName = `${fName} ${lName}`.trim() || u.fullName || u.displayName || 'Learner';
          const initials = ((fName[0] || '') + (lName[0] || '')).toUpperCase() || 'ST';
          const genderBadge = getStudentGender(u) === 'male' ? '<span class="text-[10px] text-blue-600 font-semibold">👦 Male</span>' : (getStudentGender(u) === 'female' ? '<span class="text-[10px] text-pink-600 font-semibold">👧 Female</span>' : '');

          const metrics = getStudentMpsAndTier(u);
          const wpmVal = u.phil_iri_wpm ? `${u.phil_iri_wpm} WPM` : (metrics.stSubs.length > 0 ? 'Diagnostic' : '—');

          let mpsDisplay = '';
          if (metrics.mpsValue !== null && metrics.mpsValue !== undefined) {
            const barColor = metrics.mpsValue >= 80 ? 'bg-emerald-500' : (metrics.mpsValue >= 60 ? 'bg-amber-500' : 'bg-rose-500');
            const textColor = metrics.mpsValue >= 80 ? 'text-emerald-700' : (metrics.mpsValue >= 60 ? 'text-amber-700' : 'text-rose-700');
            mpsDisplay = `
              <div class="flex items-center gap-2">
                <span class="font-bold ${textColor} text-xs">${metrics.mpsValue}% MPS</span>
                <div class="w-14 h-1.5 rounded-full bg-slate-100 overflow-hidden">
                  <div class="${barColor} h-full rounded-full" style="width: ${Math.min(100, Math.max(5, metrics.mpsValue))}%"></div>
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
                      <span>${u.grade ? 'Grade ' + u.grade + ' • ' : ''}${escapeHtml(u.section || 'General')}</span>
                      ${genderBadge ? `<span>&bull;</span> ${genderBadge}` : ''}
                    </div>
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
                ${wpmVal}
              </td>
              <td class="p-3.5 font-bold ${streak > 0 ? 'text-amber-600' : 'text-slate-400'}">
                ${streak > 0 ? `<span class="inline-flex items-center gap-1 text-amber-600 font-bold">${uiIcon('flame', 'w-3.5 h-3.5')} ${streak}d</span>` : '<span class="text-slate-400 font-medium">0d</span>'}
              </td>
              <td class="p-3.5">
                <span class="font-bold text-indigo-700">Lvl ${u.level || 1}</span>
                <span class="text-slate-400 text-[10px]">(${u.current_xp || 0} XP &bull; ${quests} Quests)</span>
              </td>
              <td class="p-3.5 text-right">
                <button onclick="viewStudentQuizHistory('${u.id}')" class="px-2.5 py-1 bg-indigo-50 hover:bg-indigo-100 text-indigo-700 border border-indigo-200 rounded-lg text-xs font-semibold shadow-xs inline-flex items-center gap-1 transition-all">
                  ${uiIcon('chart', 'w-3 h-3')} <span>Inspect History</span>
                </button>
              </td>
            </tr>
          `;
        }).join('');
      } catch (err) {
        console.error("Failed to render performance table:", err);
        tbody.innerHTML = `
          <tr>
            <td colspan="7" class="p-8 text-center text-rose-500 font-semibold">
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

    window.viewStudentQuizHistory = async function(uid) {
      const student = cachedUsers.find(item => item.id === uid);
      const studentName = student ? `${student.firstName || ''} ${student.lastName || ''}`.trim() : 'Learner';

      document.getElementById('historyStudentName').textContent = `${studentName} — Quiz Performance`;
      document.getElementById('historyStudentMeta').textContent = `${getSchoolName(student ? student.schoolId : '')} • Section ${student ? student.section : 'General'}`;

      const historyAvatarEl = document.getElementById('historyStudentAvatarContainer');
      if (historyAvatarEl) {
        const histPhoto = student ? (student.photoUrl || student.avatarUrl || student.profileImageUrl || '') : '';
        const histPend = student ? (student.pendingPhotoUrl || '') : '';
        const histStat = student ? (student.photoStatus || 'none') : 'none';
        const histInitials = student ? (((student.firstName?.[0] || '') + (student.lastName?.[0] || '')).toUpperCase() || 'ST') : 'ST';
        historyAvatarEl.innerHTML = renderStudentAvatarHtml(histPhoto, histPend, histStat, histInitials, 'w-11 h-11', 'text-sm');
      }
      document.getElementById('historyCurrentStreak').textContent = `${(student && student.current_streak) || 0} Days`;
      document.getElementById('historyLevelXp').textContent = `Lvl ${(student && student.level) || 1} (${(student && student.current_xp) || 0} XP)`;

      const listContainer = document.getElementById('historyAttemptsList');
      listContainer.innerHTML = `<div class="p-4 text-center text-slate-400">Loading recorded attempts...</div>`;
      document.getElementById('modalQuizHistory').classList.remove('hidden');

      try {
        const snap = await getDocs(collection(db, "users", uid, "quiz_history"));
        if (snap.empty) {
          document.getElementById('historyAverageScore').textContent = "0%";
          document.getElementById('historyTotalQuizzes').textContent = "0";
          listContainer.innerHTML = `<div class="p-4 text-center text-slate-400">No quiz attempts recorded for this learner yet.</div>`;
          return;
        }

        let totalScore = 0;
        let totalAttempts = 0;
        const attempts = snap.docs.map(d => ({ id: d.id, ...d.data() }));

        attempts.forEach(q => {
          totalScore += q.finalScore || 0;
          totalAttempts += q.totalAttempts || 1;
        });

        const avg = totalAttempts > 0 ? Math.round((totalScore / totalAttempts) * 100) : 0;
        document.getElementById('historyAverageScore').textContent = `${avg}%`;
        document.getElementById('historyTotalQuizzes').textContent = attempts.length;

        listContainer.innerHTML = attempts.map(a => {
          const date = a.timestamp ? new Date(a.timestamp).toLocaleString() : 'Recorded';
          return `
            <div class="py-2.5 flex items-center justify-between">
              <div>
                <div class="font-bold text-slate-800">${a.subject || a.materialId || 'Reading Quiz'}</div>
                <div class="text-[10px] text-slate-400">${date}</div>
              </div>
              <div class="text-right">
                <div class="font-bold text-indigo-700">${a.finalScore || 0} / ${a.totalAttempts || 0}</div>
                <div class="text-[10px] text-slate-500 font-medium">Attempts: ${a.totalAttempts || 1}</div>
              </div>
            </div>
          `;
        }).join('');

      } catch (err) {
        console.error("Quiz history load error:", err);
        listContainer.innerHTML = `<div class="p-4 text-center text-red-500 text-xs">Error loading quiz history: ${err.message}</div>`;
      }
    };

    window.closeQuizHistoryModal = function() {
      document.getElementById('modalQuizHistory').classList.add('hidden');
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
                isClaimed: true,
                is_onboarded: true
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
                  isClaimed: true,
                  is_onboarded: true
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
                    isClaimed: true,
                    is_onboarded: true
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
          matchingClaimed.is_onboarded = true;
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
