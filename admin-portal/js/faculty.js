    // =========================================================================
    // 1. FACULTY ACCREDITATION & CRUD
    // =========================================================================
    window.setTeacherFilter = function(mode) {
      teacherFilterMode = mode;
      ['ALL', 'PENDING', 'APPROVED'].forEach(m => {
        const btn = document.getElementById(`filterBtn-${m}`);
        if (!btn) return;
        if (m === mode) {
          btn.className = "px-3 py-1.5 rounded-lg text-xs font-bold bg-[#6C5CE7] text-white shadow-sm";
        } else {
          btn.className = "px-3 py-1.5 rounded-lg text-xs font-bold bg-slate-100 text-slate-700 hover:bg-slate-200";
        }
      });
      renderTeachersTable();
    };

    function formatEducatorDate(rawDate) {
      if (!rawDate) return 'Recorded';
      try {
        if (typeof rawDate.toDate === 'function') {
          return rawDate.toDate().toLocaleDateString();
        }
        if (typeof rawDate === 'object' && typeof rawDate.seconds === 'number') {
          return new Date(rawDate.seconds * 1000).toLocaleDateString();
        }
        const parsed = new Date(rawDate);
        return isNaN(parsed.getTime()) ? 'Recorded' : parsed.toLocaleDateString();
      } catch (_) {
        return 'Recorded';
      }
    }

    function renderTeachersTable() {
      const tbody = document.getElementById('teachersTableBody');
      if (!tbody) return;

      try {
        if (typeof window.syncCachedTeachers === 'function') {
          window.syncCachedTeachers();
        }

        let list = Array.isArray(cachedTeachers) ? [...cachedTeachers] : [];

        // 1. Scope by School Jurisdiction first so tab counters reflect the active school
        const adminSchoolId = isSchoolAdmin && currentAdminUser?.schoolId
          ? String(resolveTeacherSchoolId(currentAdminUser) || currentAdminUser.schoolId).trim()
          : '';

        if (isSchoolAdmin && adminSchoolId) {
          list = list.filter(t => String(resolveTeacherSchoolId(t)) === adminSchoolId);
        } else if (facultySchoolFilter !== 'ALL') {
          list = list.filter(t => String(resolveTeacherSchoolId(t)) === String(facultySchoolFilter));
        } else if (selectedJurisdiction !== 'ALL') {
          list = list.filter(t => String(resolveTeacherSchoolId(t)) === String(selectedJurisdiction));
        }

        const totalAll = list.length;
        const totalPending = list.filter(t => t.isApproved !== true).length;
        const totalApproved = list.filter(t => t.isApproved === true).length;

        const elAll = document.getElementById('countAllTeachers');
        const elPending = document.getElementById('countPendingTeachers');
        const elApproved = document.getElementById('countApprovedTeachers');
        if (elAll) elAll.textContent = totalAll;
        if (elPending) elPending.textContent = totalPending;
        if (elApproved) elApproved.textContent = totalApproved;

        // 2. Apply Status Filter (ALL / PENDING / APPROVED)
        if (teacherFilterMode === 'PENDING') list = list.filter(t => t.isApproved !== true);
        else if (teacherFilterMode === 'APPROVED') list = list.filter(t => t.isApproved === true);

        // 3. Guard against browser autofill injecting admin email into #inputFacultySearch
        const facSearchEl = document.getElementById('inputFacultySearch');
        const adminEmail = (currentAdminUser?.email || '').toLowerCase().trim();
        if (facSearchEl && document.activeElement !== facSearchEl) {
          const domVal = (facSearchEl.value || '').toLowerCase().trim();
          if (!facultySearchQuery || (adminEmail && domVal === adminEmail)) {
            facSearchEl.value = '';
            if (adminEmail && facultySearchQuery === adminEmail) {
              facultySearchQuery = '';
            }
            const btnClear = document.getElementById('btnClearFacultySearch');
            if (btnClear && !facultySearchQuery) btnClear.classList.add('hidden');
          }
        }

        // 4. Dedicated Smart Tokenized Search
        const activeFacultySearch = (facultySearchQuery || '').toLowerCase().trim();
        if (activeFacultySearch) {
          list = list.filter(t => {
            const sId = resolveTeacherSchoolId(t) || t.schoolId || '';
            const fullName = `${t.firstName || ''} ${t.lastName || ''} ${t.fullName || ''} ${t.displayName || ''}`;
            const textBlob = `${fullName} ${t.email || ''} ${t.prn || ''} ${getSchoolName(sId)} ${sId}`;
            return matchSmartSearch(textBlob, activeFacultySearch);
          });
        }

        if (list.length === 0) {
          const hasActiveFilters = Boolean(activeFacultySearch) || teacherFilterMode !== 'ALL' || (!isSchoolAdmin && facultySchoolFilter !== 'ALL');
          tbody.innerHTML = `
            <tr>
              <td colspan="6" class="p-8 text-center text-slate-400 font-medium">
                <div>${hasActiveFilters ? 'No faculty records match the selected filter.' : 'No faculty accounts registered for this school yet.'}</div>
                ${hasActiveFilters ? `
                  <button onclick="resetFacultyFilters()" class="mt-2 px-3 py-1 bg-indigo-50 hover:bg-indigo-100 text-indigo-700 border border-indigo-200 rounded-lg text-xs font-bold transition-colors">
                    Reset Filters
                  </button>
                ` : ''}
              </td>
            </tr>`;
          return;
        }

        tbody.innerHTML = list.map(t => {
          const isApproved = t.isApproved === true;
          const regDate = formatEducatorDate(t.createdAt);
          const safeId = String(t.id || t.uid || '');
          const shortUid = safeId ? safeId.substring(0, 8) : 'N/A';
          const resolvedSchoolId = resolveTeacherSchoolId(t) || t.schoolId || '';
          const fullName = `${t.firstName || ''} ${t.lastName || ''}`.trim() || t.fullName || t.displayName || t.email || 'Educator';
          const isPromotedSchoolAdmin = String(t.role || '').toLowerCase() === 'school_admin';

          return `
            <tr class="hover:bg-slate-50/80 transition-colors">
              <td class="p-3.5">
                <div class="font-bold text-slate-900 flex items-center gap-1.5 flex-wrap">
                  <span>${fullName}</span>
                  ${isPromotedSchoolAdmin ? '<span class="px-1.5 py-0.5 bg-indigo-50 text-indigo-700 border border-indigo-200 rounded text-[9px] font-bold uppercase">School Admin</span>' : ''}
                </div>
                <div class="text-[10px] text-slate-400">UID: ${shortUid}...</div>
              </td>
              <td class="p-3.5 font-medium text-slate-700">${t.email || 'N/A'}</td>
              <td class="p-3.5">
                <div class="font-semibold text-slate-800">${getSchoolName(resolvedSchoolId)}</div>
                <div class="text-[10px] text-slate-400">School ID: ${resolvedSchoolId || 'Unassigned'}</div>
              </td>
              <td class="p-3.5 text-slate-500">${regDate}</td>
              <td class="p-3.5">
                ${isApproved 
                  ? '<span class="badge-verified px-2.5 py-1 rounded-full text-[11px] font-bold">Accredited / Active</span>' 
                  : '<span class="badge-pending px-2.5 py-1 rounded-full text-[11px] font-bold">Pending Review</span>'}
              </td>
              <td class="p-3.5 text-right">
                <div class="flex items-center justify-end gap-1.5">
                  ${isSchoolAdmin ? `
                    ${!isApproved ? `
                      <button onclick="approveTeacher('${safeId}')" class="px-2.5 py-1 bg-emerald-600 hover:bg-emerald-700 text-white rounded-lg text-xs font-bold shadow-sm">
                        Approve
                      </button>
                    ` : `
                      <button onclick="revokeTeacher('${safeId}')" class="px-2 py-1 bg-slate-100 hover:bg-amber-100 text-slate-600 hover:text-amber-800 border border-slate-300 rounded text-xs font-semibold">
                        Hold
                      </button>
                    `}
                    <button onclick="openResetTeacherPasswordModal('${safeId}', '${escapeHtml(t.email || '')}', '${escapeHtml(fullName)}')" class="px-2 py-1 bg-purple-50 hover:bg-purple-100 text-[#5341CD] border border-purple-200 rounded text-xs font-bold shadow-xs inline-flex items-center gap-1" title="Reset Educator Password">
                      ${uiIcon('key', 'w-3 h-3')} <span>Reset</span>
                    </button>
                    <button onclick="openEditTeacherModal('${safeId}')" class="px-2 py-1 bg-white border border-slate-300 hover:bg-slate-100 text-slate-700 rounded text-xs font-semibold inline-flex items-center" title="Edit Educator Profile">
                      ${uiIcon('edit', 'w-3.5 h-3.5')}
                    </button>
                  ` : `
                    ${!isApproved ? `
                      <span class="px-2 py-1 bg-amber-50 text-amber-800 border border-amber-200 rounded text-[11px] font-semibold" title="Accreditation is granted by the assigned School Administrator">
                        Awaiting School Principal
                      </span>
                    ` : `
                      <span class="px-2 py-1 bg-emerald-50 text-emerald-800 border border-emerald-200 rounded text-[11px] font-semibold">
                        Accredited
                      </span>
                    `}
                    <button onclick="openResetTeacherPasswordModal('${safeId}', '${escapeHtml(t.email || '')}', '${escapeHtml(fullName)}')" class="px-2 py-1 bg-purple-50 hover:bg-purple-100 text-[#5341CD] border border-purple-200 rounded text-xs font-bold shadow-xs inline-flex items-center gap-1" title="Reset Educator Password">
                      ${uiIcon('key', 'w-3 h-3')} <span>Reset</span>
                    </button>
                    <button onclick="openEditTeacherModal('${safeId}')" class="px-2 py-1 bg-white border border-slate-300 hover:bg-slate-100 text-slate-700 rounded text-xs font-semibold inline-flex items-center" title="Edit Educator Profile">
                      ${uiIcon('edit', 'w-3.5 h-3.5')}
                    </button>
                    <button onclick="deleteTeacher('${safeId}')" class="px-2 py-1 bg-white border border-red-200 hover:bg-red-50 text-red-600 rounded text-xs font-semibold inline-flex items-center" title="Permanently Delete Record (SDO Exclusive)">
                      ${uiIcon('trash', 'w-3.5 h-3.5')}
                    </button>
                  `}
                </div>
              </td>
            </tr>
          `;
        }).join('');
      } catch (err) {
        console.error("renderTeachersTable error:", err);
        tbody.innerHTML = `
          <tr>
            <td colspan="6" class="p-8 text-center text-slate-400 font-medium">
              Unable to display faculty records. Please refresh the page.
            </td>
          </tr>`;
      }
    }
    window.renderTeachersTable = renderTeachersTable;
    window.renderQuickPendingTable = renderQuickPendingTable;

    window.openCreateTeacherModal = function() {
      document.getElementById('modalTeacherTitle').textContent = "Provision Faculty Account";
      document.getElementById('teacherEditUid').value = "";
      document.getElementById('teacherFirstName').value = "";
      document.getElementById('teacherLastName').value = "";
      document.getElementById('teacherEmail').value = "";
      
      const schSelect = document.getElementById('teacherSchoolSelect');
      if (isSchoolAdmin) {
        schSelect.value = String(currentAdminUser.schoolId);
        schSelect.disabled = true;
      } else {
        schSelect.disabled = false;
        schSelect.value = selectedJurisdiction !== 'ALL' ? selectedJurisdiction : "";
      }

      if (document.getElementById('teacherRoleSelect')) {
        document.getElementById('teacherRoleSelect').value = "teacher";
      }
      document.getElementById('teacherIsApproved').checked = isSchoolAdmin ? true : false;
      document.getElementById('modalTeacher').classList.remove('hidden');
    };

    window.openEditTeacherModal = function(uid) {
      const t = cachedUsers.find(item => item.id === uid) || cachedTeachers.find(item => item.id === uid);
      if (!t) return;

      document.getElementById('modalTeacherTitle').textContent = "Edit Faculty Profile";
      document.getElementById('teacherEditUid').value = uid;
      document.getElementById('teacherFirstName').value = t.firstName || "";
      document.getElementById('teacherLastName').value = t.lastName || "";
      document.getElementById('teacherEmail').value = t.email || "";
      
      const schSelect = document.getElementById('teacherSchoolSelect');
      schSelect.value = t.schoolId || "";
      schSelect.disabled = isSchoolAdmin;

      if (document.getElementById('teacherRoleSelect')) {
        document.getElementById('teacherRoleSelect').value = t.role || "teacher";
      }
      document.getElementById('teacherIsApproved').checked = t.isApproved === true;
      document.getElementById('modalTeacher').classList.remove('hidden');
    };

    window.closeTeacherModal = function() {
      document.getElementById('modalTeacher').classList.add('hidden');
    };

    let activeTeacherResetData = null;

    window.openResetTeacherPasswordModal = async function(docId, email, fullName) {
      if (!email && !docId) {
        window.showToast("Educator record has no associated email or identifier.", "error");
        return;
      }
      const cleanEmail = (email || '').trim().toLowerCase();
      const tempPass = 'gab' + Math.floor(10000 + Math.random() * 90000);
      activeTeacherResetData = { docId, email: cleanEmail, fullName, passkey: tempPass };

      try {
        let updated = false;
        if (docId) {
          try {
            await updateDoc(doc(db, "users", docId), {
              tempPassword: tempPass,
              password: tempPass,
              mustChangePassword: true,
              isApproved: true
            });
            updated = true;
          } catch (e) {
            console.warn("Could not update teacher in users collection:", e);
          }
        }

        const matchedUser = (cachedUsers || []).find(u => u.id === docId || (cleanEmail && String(u.email || '').trim().toLowerCase() === cleanEmail));
        if (cleanEmail) {
          await setDoc(doc(db, "pending_students", cleanEmail), {
            username: cleanEmail,
            email: cleanEmail,
            password: tempPass,
            tempPassword: tempPass,
            mustChangePassword: true,
            isReset: true,
            role: "teacher",
            claimedUid: docId || matchedUser?.id || '',
            schoolId: matchedUser?.schoolId || '',
            fullName: fullName || matchedUser?.fullName || '',
            updatedAt: Date.now()
          }, { merge: true });
          updated = true;
        }

        if (matchedUser) {
          matchedUser.tempPassword = tempPass;
          matchedUser.password = tempPass;
          matchedUser.mustChangePassword = true;
        }

        document.getElementById('resetTeacherTargetName').textContent = fullName || 'Educator';
        document.getElementById('resetTeacherTargetEmail').textContent = cleanEmail || docId;
        document.getElementById('teacherGeneratedPasskey').textContent = tempPass;
        document.getElementById('modalResetTeacherPassword').classList.remove('hidden');

        copyToClipboard(tempPass, "Temporary educator passkey copied to clipboard!");
        window.showToast(`Temporary passkey generated for ${fullName || cleanEmail}: ${tempPass}`, "success");
      } catch (err) {
        console.error("Teacher passkey generation error:", err);
        window.showToast("Failed to generate temporary passkey: " + err.message, "error");
      }
    };

    window.closeResetTeacherModal = function() {
      document.getElementById('modalResetTeacherPassword').classList.add('hidden');
      activeTeacherResetData = null;
    };

    window.copyTeacherGeneratedPasskey = function() {
      const pass = document.getElementById('teacherGeneratedPasskey')?.textContent;
      if (pass) {
        copyToClipboard(pass, "Temporary educator passkey copied to clipboard!");
      }
    };

    window.handleTeacherFormSubmit = async function(e) {
      e.preventDefault();
      const editUid = document.getElementById('teacherEditUid').value;
      const firstName = document.getElementById('teacherFirstName').value.trim();
      const lastName = document.getElementById('teacherLastName').value.trim();
      const email = document.getElementById('teacherEmail').value.trim();
      const schoolId = isSchoolAdmin ? String(currentAdminUser.schoolId) : document.getElementById('teacherSchoolSelect').value;
      const role = "teacher";
      const isApproved = document.getElementById('teacherIsApproved').checked;

      if (!schoolId) {
        window.showToast("Please assign a secondary school.", "error");
        return;
      }

      const btn = document.getElementById('btnSaveTeacher');
      btn.disabled = true;
      btn.textContent = "Saving...";

      try {
        if (editUid) {
          await updateDoc(doc(db, "users", editUid), {
            firstName,
            lastName,
            email,
            schoolId,
            role,
            isApproved,
            updatedAt: Date.now()
          });

          window.showToast("Educator profile updated.", "success");
        } else {
          const newDocRef = doc(collection(db, "users"));
          const joinCode = Math.random().toString(36).substring(2, 8).toUpperCase();
          await setDoc(newDocRef, {
            firstName,
            lastName,
            email,
            schoolId,
            role,
            isApproved,
            joinCode,
            emailVerified: true,
            createdAt: Date.now(),
            createdBy: currentAdminUser ? currentAdminUser.email : 'System Admin'
          });

          window.showToast("Faculty account created successfully.", "success");
        }
        closeTeacherModal();
      } catch (err) {
        console.error("Save account error:", err);
        window.showToast("Error saving record: " + err.message, "error");
      } finally {
        btn.disabled = false;
        btn.textContent = "Save Educator Record";
      }
    };

    window.deleteTeacher = async function(uid, name) {
      if (!isSuperAdmin) {
        window.showToast("Only Division Super Admin (SDO) has authority to remove accounts.", "error");
        return;
      }
      const teacher = cachedUsers.find(u => u.id === uid) || {};
      const targetName = name || `${teacher.firstName || ''} ${teacher.lastName || 'Educator'}`.trim() || 'Educator';
      
      const confirmed = await showConfirmModal({
        title: `Remove Educator "${targetName}"?`,
        message: "This will safely disassociate classrooms, preserve lesson materials, and create a 30-day backup in the Recycle Bin.",
        badgeIcon: "👩‍🏫",
        badgeBg: "bg-rose-50 text-rose-600 border-rose-200",
        confirmText: "Archive & Remove",
        isDestructive: true
      });
      if (!confirmed) return;

      try {
        // BACKUP: Archive faculty record to system_archives before removing user doc
        const archiveId = `teacher_${uid}_${Date.now()}`;
        await setDoc(doc(db, "system_archives", archiveId), {
          archiveId: archiveId,
          entityType: "teacher",
          originalCollection: "users",
          originalDocId: uid,
          schoolId: String(teacher.schoolId || ""),
          displayName: targetName,
          subtitle: `${teacher.email || 'No email'} • School: ${getSchoolName(teacher.schoolId)}`,
          archivedAt: Date.now(),
          archivedByUid: currentAdminUser?.uid || "",
          archivedByName: `${currentAdminUser?.firstName || ''} ${currentAdminUser?.lastName || ''}`.trim() || 'Super Admin',
          archivedByRole: isSuperAdmin ? "super_admin" : "school_admin",
          deletionReason: "Educator removed from faculty registry",
          snapshot: teacher,
          status: "archived",
          retentionUntil: Date.now() + (30 * 24 * 60 * 60 * 1000)
        });

        // CASCADE 1: Disassociate adviser and subject links in classes
        const advisoryClasses = cachedClasses.filter(c => c.teacherId === uid);
        for (const c of advisoryClasses) {
          await updateDoc(doc(db, "classes", c.id), {
            teacherId: null,
            adviserName: "Unassigned (Vacant)",
            vacantAdviser: true,
            updatedAt: Date.now()
          });
        }

        const subjectClasses = cachedClasses.filter(c => Array.isArray(c.teacherIds) && c.teacherIds.includes(uid));
        for (const c of subjectClasses) {
          await updateDoc(doc(db, "classes", c.id), {
            teacherIds: arrayRemove(uid),
            updatedAt: Date.now()
          });
        }

        // CASCADE 2: Retain learning materials but mark faculty as archived
        const teacherMaterials = cachedMaterials.filter(m => m.teacherId === uid);
        for (const m of teacherMaterials) {
          await updateDoc(doc(db, "library_materials", m.id), {
            uploaderStatus: "archived_faculty",
            archivedAt: Date.now()
          });
        }

        // CASCADE 3: Remove user document
        await deleteDoc(doc(db, "users", uid));
        window.showToast(`Educator "${targetName}" removed. Backup saved in Recycle Bin.`, "info");
        renderRecycleVault();
        scheduleDashboardRender();
      } catch (err) {
        console.error("Delete educator error:", err);
        window.showToast("Failed to delete educator: " + err.message, "error");
      }
    };

    window.approveTeacher = async function(uid) {
      if (!isSchoolAdmin && !isSuperAdmin) {
        window.showToast("Teacher accreditation is performed by School Administrators or Division Super Admin.", "warning");
        return;
      }
      const teacher = cachedTeachers.find(t => t.id === uid) || cachedUsers.find(u => u.id === uid);
      if (!teacher) {
        window.showToast("Educator record not found.", "error");
        return;
      }
      const teacherSchoolId = resolveTeacherSchoolId(teacher);
      if (isSchoolAdmin && teacherSchoolId && String(teacherSchoolId) !== String(currentAdminUser.schoolId)) {
        window.showToast("You can only accredit teachers belonging to your assigned school.", "error");
        return;
      }

      try {
        await updateDoc(doc(db, "users", uid), {
          isApproved: true,
          approvedAt: Date.now(),
          approvedBy: currentAdminUser.email,
          approvedByName: `${currentAdminUser.firstName || ''} ${currentAdminUser.lastName || ''}`.trim() || (isSuperAdmin ? 'Division Superintendent' : 'School Principal'),
          approvedByRole: isSuperAdmin ? 'super_admin' : 'school_admin',
          schoolId: String(teacherSchoolId || currentAdminUser.schoolId || ''),
          approvedSchoolId: String(teacherSchoolId || currentAdminUser.schoolId || '')
        });
        window.showToast(`Faculty accreditation granted for ${teacher.firstName || 'educator'}. Teacher can now log in.`, "success");
      } catch (err) {
        console.error("Approval error:", err);
        window.showToast("Action failed: " + err.message, "error");
      }
    };

    window.revokeTeacher = async function(uid) {
      if (!isSchoolAdmin && !isSuperAdmin) {
        window.showToast("Unauthorized.", "error");
        return;
      }
      const teacher = cachedTeachers.find(t => t.id === uid) || cachedUsers.find(u => u.id === uid);
      const teacherSchoolId = resolveTeacherSchoolId(teacher);
      if (isSchoolAdmin && teacher && teacherSchoolId && String(teacherSchoolId) !== String(currentAdminUser.schoolId)) {
        window.showToast("You can only manage teachers belonging to your assigned school.", "error");
        return;
      }

      const confirmed = await showConfirmModal({
        title: "Hold Educator Accreditation?",
        message: "This will pause the teacher's mobile login access until re-accredited by administration.",
        badgeIcon: "⏸️",
        badgeBg: "bg-amber-50 text-amber-700 border-amber-200",
        confirmText: "Hold Accreditation",
        isDestructive: true
      });
      if (!confirmed) return;

      try {
        await updateDoc(doc(db, "users", uid), {
          isApproved: false,
          revokedAt: Date.now(),
          revokedBy: currentAdminUser.email,
          revokedByRole: isSuperAdmin ? 'super_admin' : 'school_admin'
        });
        window.showToast("Educator accreditation held.", "warning");
        scheduleDashboardRender();
      } catch (err) {
        console.error("Revocation error:", err);
        window.showToast("Action failed: " + err.message, "error");
      }
    };


window.formatEducatorDate = formatEducatorDate;
window.renderTeachersTable = renderTeachersTable;
