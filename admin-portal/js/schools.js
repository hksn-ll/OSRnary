    // =========================================================================
    // 6. 28-SCHOOL MATRIX
    // =========================================================================
    function renderSchoolsMatrix() {
      const tbody = document.getElementById('schoolsTableBody');
      if (!tbody) return;

      let list = VALENZUELA_SCHOOLS;

      if (matrixDistrictFilter !== 'ALL') {
        list = list.filter(s => s.district === matrixDistrictFilter);
      }

      if (matrixSchoolSearchQuery) {
        list = list.filter(s => {
          const blob = `${s.name || ''} ${s.id || ''} ${s.district || ''} ${s.address || ''}`.toLowerCase();
          return blob.includes(matrixSchoolSearchQuery);
        });
      }

      const countLabel = document.getElementById('matrixResultsCount');
      if (countLabel) {
        countLabel.textContent = `Showing ${list.length} school(s)`;
      }

      if (list.length === 0) {
        tbody.innerHTML = `
          <tr>
            <td colspan="7" class="p-8 text-center text-slate-400 font-medium">
              No schools match the active search or district filter.
              <button onclick="clearMatrixSearch(); setMatrixDistrictFilter('ALL');" class="block mx-auto mt-2 text-indigo-600 hover:underline font-bold text-xs">Reset filters</button>
            </td>
          </tr>`;
        return;
      }

      tbody.innerHTML = list.map(school => {
        const teachersInSchool = cachedTeachers.filter(t => String(t.schoolId) === school.id);
        const approvedCount = teachersInSchool.filter(t => t.isApproved === true).length;
        const pendingCount = teachersInSchool.filter(t => t.isApproved !== true).length;
        const classesInSchool = cachedClasses.filter(c => String(c.schoolId) === school.id).length;

        // Enrolled and claimed students
        const claimedInSchool = cachedUsers.filter(u => u.role === 'student' && String(u.schoolId) === school.id);
        const pendingInSchool = cachedPendingStudents.filter(s => String(s.schoolId) === school.id);
        const totalLearners = claimedInSchool.length + pendingInSchool.length;

        // Reading Mastery Analytics for this school
        const schoolSubs = cachedSubmissions.filter(s => {
          const a = cachedWeeklyAssessments.find(item => item.id === s.assessmentId);
          return (a && String(a.schoolId) === school.id) || String(s.schoolId) === school.id;
        });

        const sMetrics = calculateScoreMetrics(schoolSubs, claimedInSchool);
        const schoolAvg = sMetrics.avgScore;
        const sMastery = sMetrics.mastery;
        const sProf = sMetrics.proficient;
        const sSupp = sMetrics.support;
        const mPct = sMetrics.masteryPct;
        const pPct = sMetrics.proficientPct;
        const sPct = sMetrics.supportPct;

        // Multi-admin lookup: find all assigned school admins
        const schoolAdmins = cachedUsers.filter(u => u.role === 'school_admin' && String(u.schoolId) === school.id);
        // Unclaimed pending SDO invites
        const pendingInvites = cachedSchoolInvites.filter(inv => String(inv.schoolId) === school.id && !inv.claimed);
        const latestPending = pendingInvites.length > 0 ? pendingInvites[pendingInvites.length - 1] : null;

        let adminColumnHtml = '';
        if (schoolAdmins.length > 0) {
          adminColumnHtml = `
            <div class="flex items-center justify-between gap-2">
              <div class="flex flex-wrap items-center gap-1.5 max-w-[280px]">
                ${schoolAdmins.map(adm => `
                  <span class="px-2 py-0.5 rounded-full bg-emerald-50 text-emerald-800 border border-emerald-200 text-[10px] font-bold flex items-center gap-1 shadow-2xs" title="${adm.email || ''}">
                    ${uiIcon('check', 'w-3 h-3 text-emerald-700')} <span class="truncate max-w-[120px]">${escapeHtml((adm.firstName || '') + ' ' + (adm.lastName || 'Admin')).trim()}</span>
                  </span>
                `).join('')}
                ${latestPending ? `
                  <button onclick="showInviteCodeModal('${latestPending.inviteCode}', '${school.id}')" class="px-2 py-0.5 rounded-full bg-amber-50 text-amber-800 border border-amber-300 text-[10px] font-bold font-mono flex items-center gap-1 hover:bg-amber-100 shadow-2xs" title="Active Claim Code (Click to view)">
                    ${uiIcon('ticket', 'w-3 h-3 text-amber-700')} <span>${latestPending.inviteCode}</span>
                  </button>
                ` : ''}
              </div>
              <div class="flex items-center gap-1 shrink-0">
                <button onclick="generateSchoolAdminInvite('${school.id}')" class="px-2 py-1 bg-purple-50 hover:bg-purple-100 text-[#5341CD] border border-purple-200 rounded-lg text-xs font-bold flex items-center gap-1 shadow-2xs transition" title="Generate another SDO Claim Code">
                  ${uiIcon('ticket', 'w-3 h-3')} <span>+ Code</span>
                </button>
                <button onclick="openManageSchoolAdminsModal('${school.id}')" class="p-1.5 text-slate-500 hover:text-[#5341CD] hover:bg-slate-100 rounded-lg text-xs inline-flex items-center transition border border-slate-200" title="Manage School Administrators & Access">
                  ${uiIcon('settings', 'w-3.5 h-3.5')}
                </button>
              </div>
            </div>
          `;
        } else if (latestPending) {
          adminColumnHtml = `
            <div class="flex items-center justify-between gap-1.5">
              <button onclick="showInviteCodeModal('${latestPending.inviteCode}', '${school.id}')" class="px-2.5 py-1 bg-amber-50 hover:bg-amber-100 text-amber-900 border border-amber-300 rounded-lg text-xs font-bold font-mono shadow-xs flex items-center gap-1" title="View Confidential SDO Invite Code">
                ${uiIcon('ticket', 'w-3 h-3')} <span>${latestPending.inviteCode}</span>
              </button>
              <div class="flex items-center gap-1 shrink-0">
                <button onclick="generateSchoolAdminInvite('${school.id}')" class="px-2 py-1 bg-purple-50 hover:bg-purple-100 text-[#5341CD] border border-purple-200 rounded-lg text-xs font-bold flex items-center gap-1 shadow-2xs transition" title="Generate another SDO Claim Code">
                  ${uiIcon('ticket', 'w-3 h-3')} <span>+ Code</span>
                </button>
                <button onclick="openManageSchoolAdminsModal('${school.id}')" class="p-1.5 text-slate-500 hover:text-[#5341CD] hover:bg-slate-100 rounded-lg text-xs inline-flex items-center transition border border-slate-200" title="Manage School Administrators & Access">
                  ${uiIcon('settings', 'w-3.5 h-3.5')}
                </button>
              </div>
            </div>
          `;
        } else {
          adminColumnHtml = `
            <div class="flex items-center gap-1.5">
              <button onclick="generateSchoolAdminInvite('${school.id}')" class="px-2.5 py-1 bg-gradient-to-r from-[#6C5CE7] to-[#5341CD] hover:opacity-90 text-white rounded-lg text-xs font-bold shadow-xs flex items-center gap-1 font-display" title="Generate SDO Claim Code">
                ${uiIcon('ticket', 'w-3 h-3')} <span>+ Claim Code</span>
              </button>
              <button onclick="openManageSchoolAdminsModal('${school.id}')" class="px-2 py-1 bg-white hover:bg-slate-100 border border-slate-300 text-slate-700 rounded-lg text-xs font-bold shadow-xs flex items-center gap-1" title="Manage School Administrators & Access">
                ${uiIcon('settings', 'w-3 h-3')} <span>Manage</span>
              </button>
            </div>
          `;
        }

        return `
          <tr class="hover:bg-slate-50/80 transition-colors">
            <td class="p-3.5 font-mono font-bold text-slate-700">${school.id}</td>
            <td class="p-3.5">
              <div class="font-bold text-slate-900">${school.name}</div>
              <div class="flex items-center gap-1.5 mt-0.5">
                <span class="text-[10px] text-slate-400 font-mono">SDO Valenzuela</span>
                <span class="px-1.5 py-0.2 rounded text-[9px] font-bold ${school.district === 'District 2' ? 'bg-purple-100 text-purple-800' : 'bg-blue-100 text-blue-800'}">${school.district || getSchoolDistrict(school.id)}</span>
              </div>
            </td>
            <td class="p-3.5">
              <span class="font-bold text-emerald-700">${approvedCount} Verified</span>
              ${pendingCount > 0 ? `<span class="ml-1.5 px-1.5 py-0.5 rounded-full bg-amber-100 text-amber-800 text-[10px] font-bold">${pendingCount} Pending</span>` : ''}
              <div class="text-[11px] text-slate-500 font-medium mt-0.5">${classesInSchool} Section${classesInSchool === 1 ? '' : 's'}</div>
            </td>
            <td class="p-3.5">
              <div class="font-bold text-slate-800">${claimedInSchool.length} Claimed</div>
              <div class="text-[10px] text-slate-400 font-medium">${totalLearners} Total Accounts</div>
            </td>
            <td class="p-3.5">
              ${schoolSubs.length > 0 ? `
                <div class="flex items-center gap-1.5 mb-1">
                  <span class="font-bold text-xs ${schoolAvg >= 80 ? 'text-emerald-700' : (schoolAvg >= 60 ? 'text-amber-700' : 'text-rose-700')}">${schoolAvg}%</span>
                  <span class="px-1.5 py-0.5 rounded-full text-[9px] font-bold ${schoolAvg >= 80 ? 'bg-emerald-50 text-emerald-700 border border-emerald-200' : (schoolAvg >= 60 ? 'bg-amber-50 text-amber-700 border border-amber-200' : 'bg-rose-50 text-rose-700 border border-rose-200')}">
                    ${schoolAvg >= 80 ? '<span class="inline-flex items-center gap-1"><span class="w-1.5 h-1.5 rounded-full bg-emerald-500 inline-block"></span>Mastery</span>' : (schoolAvg >= 60 ? '<span class="inline-flex items-center gap-1"><span class="w-1.5 h-1.5 rounded-full bg-amber-500 inline-block"></span>Proficient</span>' : '<span class="inline-flex items-center gap-1"><span class="w-1.5 h-1.5 rounded-full bg-rose-500 inline-block"></span>Support</span>')}
                  </span>
                </div>
                <div class="w-28 bg-slate-100 h-1.5 rounded-full overflow-hidden flex">
                  <div style="width: ${mPct}%" class="bg-emerald-500 h-full" title="Mastery: ${sMastery}"></div>
                  <div style="width: ${pPct}%" class="bg-amber-400 h-full" title="Proficient: ${sProf}"></div>
                  <div style="width: ${sPct}%" class="bg-rose-400 h-full" title="Needs Support: ${sSupp}"></div>
                </div>
                <div class="text-[9px] text-slate-400 font-mono mt-0.5">
                  ${sMastery} High &bull; ${sProf} Mid &bull; ${sSupp} Supp
                </div>
              ` : `
                <span class="text-slate-400 text-[11px] font-medium">No assessment data</span>
              `}
            </td>
            <td class="p-3.5">${adminColumnHtml}</td>
            <td class="p-3.5 text-right">
              <div class="flex items-center justify-end gap-1.5">
                <button onclick="inspectSchool('${school.id}')" class="px-2.5 py-1.5 bg-white border border-slate-300 hover:bg-slate-100 rounded-lg text-xs font-semibold text-slate-700 shadow-2xs">
                  Filter School
                </button>
                ${isSuperAdmin ? `
                  <button type="button" onclick="removeSchoolPrompt('${school.id}')" class="px-2 py-1.5 bg-rose-50 hover:bg-rose-100 text-rose-700 border border-rose-200 rounded-lg text-xs font-bold shadow-2xs flex items-center gap-1 transition" title="Delete School from Division">
                    ${uiIcon('trash', 'w-3.5 h-3.5')} <span>Remove</span>
                  </button>
                ` : ''}
              </div>
            </td>
          </tr>
        `;
      }).join('');
    }


    // =========================================================================
    // DYNAMIC SCHOOL MANAGEMENT (SUPER ADMIN ADD / REMOVE - SYSTEMWIDE)
    // =========================================================================
    window.openAddSchoolModal = function() {
      if (!isSuperAdmin) {
        window.showToast("Only Division Super Admin can add new schools.", "error");
        return;
      }
      document.getElementById('addSchoolForm')?.reset();
      document.getElementById('modalAddSchool')?.classList.remove('hidden');
    };

    window.closeAddSchoolModal = function() {
      document.getElementById('modalAddSchool')?.classList.add('hidden');
    };

    window.saveNewSchool = async function(e) {
      if (e) e.preventDefault();
      if (!isSuperAdmin) {
        window.showToast("Only Division Super Admin can add new schools.", "error");
        return;
      }
      const idInput = document.getElementById('newSchoolId');
      const nameInput = document.getElementById('newSchoolName');
      const divInput = document.getElementById('newSchoolDivision');

      const districtInput = document.getElementById('newSchoolDistrict');
      const schoolId = idInput?.value.trim();
      const schoolName = nameInput?.value.trim();
      const division = divInput?.value.trim() || 'Valenzuela City';
      const district = districtInput?.value || 'District 1';

      if (!schoolId || !schoolName) {
        window.showToast("Please enter both School ID and School Name.", "warning");
        return;
      }

      if (VALENZUELA_SCHOOLS.some(s => s.id === schoolId)) {
        window.showToast(`A school with ID "${schoolId}" already exists in the system.`, "warning");
        return;
      }

      try {
        await setDoc(doc(db, "schools", schoolId), {
          schoolId: schoolId,
          schoolName: schoolName,
          division: division,
          district: district,
          status: "active",
          createdAt: Date.now()
        });

        if (!VALENZUELA_SCHOOLS.some(s => s.id === schoolId)) {
          VALENZUELA_SCHOOLS.push({ id: schoolId, name: schoolName, division: division, district: district, status: "active" });
          VALENZUELA_SCHOOLS.sort((a, b) => a.name.localeCompare(b.name));
          updateSchoolsCountUI();
          populateSchoolsDropdowns();
          scheduleDashboardRender();
        }

        window.closeAddSchoolModal();
        window.showToast(`School "${schoolName}" (${schoolId}) successfully added systemwide!`, 'success');
      } catch (err) {
        console.error("Error adding school:", err);
        window.showToast("Failed to add school: " + err.message, "error");
      }
    };

    window.removeSchoolPrompt = async function(schoolId, schoolName) {
      if (!isSuperAdmin) {
        window.showToast("Only Division Super Admin can remove schools.", "error");
        return;
      }
      const targetName = schoolName || getSchoolName(schoolId);

      // Check active dependencies
      const activeTeachers = cachedTeachers.filter(t => String(t.schoolId) === String(schoolId)).length;
      const activeClasses = cachedClasses.filter(c => String(c.schoolId) === String(schoolId)).length;
      const activeStudents = cachedUsers.filter(u => u.role === 'student' && String(u.schoolId) === String(schoolId)).length;

      const depMsg = (activeTeachers > 0 || activeClasses > 0 || activeStudents > 0)
        ? `This school currently has ${activeTeachers} faculty member(s), ${activeClasses} active class section(s), and ${activeStudents} enrolled learner(s). Removing it will safely unpublish the school and store a complete snapshot in the Recycle Bin.`
        : "";

      const requireTyped = (activeTeachers > 0 || activeClasses > 0) ? String(schoolId) : "";

      const confirmed = await showConfirmModal({
        title: `Remove School "${targetName}"?`,
        message: "This will remove the school systemwide from active lists and store it in the Recycle Bin with 30-day recovery.",
        badgeIcon: "🏛️",
        badgeBg: "bg-rose-50 text-rose-600 border-rose-200",
        confirmText: "Archive School",
        isDestructive: true,
        dependencyWarning: depMsg,
        requireTypedText: requireTyped
      });
      if (!confirmed) return;

      try {
        // ALWAYS soft-delete (never permanent deleteDoc)
        await setDoc(doc(db, "schools", String(schoolId)), {
          schoolId: String(schoolId),
          schoolName: targetName,
          status: "deleted",
          deletedAt: Date.now(),
          deletedBy: currentAdminUser?.email || "super_admin",
          deletedByRole: isSuperAdmin ? "super_admin" : "school_admin"
        }, { merge: true });

        // Backup to system_archives
        const archiveId = `school_${schoolId}_${Date.now()}`;
        await setDoc(doc(db, "system_archives", archiveId), {
          archiveId: archiveId,
          entityType: "school",
          originalCollection: "schools",
          originalDocId: String(schoolId),
          schoolId: String(schoolId),
          displayName: targetName,
          subtitle: `School ID: ${schoolId} • ${activeTeachers} Faculty • ${activeClasses} Sections`,
          archivedAt: Date.now(),
          archivedByUid: currentAdminUser?.uid || "",
          archivedByName: `${currentAdminUser?.firstName || ''} ${currentAdminUser?.lastName || ''}`.trim() || 'Super Admin',
          archivedByRole: isSuperAdmin ? "super_admin" : "school_admin",
          deletionReason: "Archived from School Directory",
          snapshot: {
            id: String(schoolId),
            schoolId: String(schoolId),
            schoolName: targetName,
            status: "active",
            division: "Valenzuela City"
          },
          status: "archived",
          retentionUntil: Date.now() + (30 * 24 * 60 * 60 * 1000)
        });

        // Optimistic local update
        VALENZUELA_SCHOOLS = VALENZUELA_SCHOOLS.filter(s => s.id !== String(schoolId));
        updateSchoolsCountUI();
        populateSchoolsDropdowns();
        scheduleDashboardRender();
        renderRecycleVault();

        window.showToast(`School "${targetName}" archived to Recycle Bin.`, 'success');
      } catch (err) {
        console.error("Error removing school:", err);
        window.showToast("Failed to remove school: " + err.message, "error");
      }
    };


    // =========================================================================
    // SDO INVITE ISSUANCE & SCHOOL ACCESS MANAGEMENT
    // =========================================================================
    window.generateSchoolAdminInvite = async function(schoolId, schoolName) {
      if (!isSuperAdmin) {
        window.showToast("Only Division Super Admin (SDO) can issue School Admin invite codes.", "error");
        return;
      }
      const targetName = schoolName || getSchoolName(schoolId);
      const randSuffix = Math.random().toString(36).substring(2, 6).toUpperCase();
      const code = `SDO-${schoolId}-${randSuffix}`;

      try {
        const inviteData = {
          inviteCode: code,
          schoolId: String(schoolId),
          schoolName: targetName,
          claimed: false,
          claimedByUid: "",
          issuedBy: currentAdminUser.uid,
          issuedByEmail: currentAdminUser.email,
          issuedAt: Date.now()
        };
        await setDoc(doc(db, "school_admin_invites", code), inviteData);
        // Optimistic local cache update
        cachedSchoolInvites = cachedSchoolInvites.filter(i => i.inviteCode !== code).concat([{ id: code, ...inviteData }]);

        showInviteCodeModal(code, targetName);
        window.showToast(`Official invite code generated for ${targetName}`, 'success');
        renderSchoolsMatrix();

        const modal = document.getElementById('modalManageSchoolAdmins');
        if (modal && !modal.classList.contains('hidden') && String(document.getElementById('manageAdminsSchoolId')?.value) === String(schoolId)) {
          renderManageSchoolAdminsContent(schoolId, targetName);
        }
      } catch (err) {
        console.error("Error issuing invite:", err);
        let msg = "Failed to issue invite: " + err.message;
        if (err.code === 'permission-denied' || err.message?.includes('permission')) {
          msg = "Missing or insufficient permissions: Firestore security rules blocked creating school_admin_invites. Please deploy or update your Firestore rules.";
        }
        window.showToast(msg, "error");
      }
    };

    window.showInviteCodeModal = function(code, schoolNameOrId) {
      const displayName = VALENZUELA_SCHOOLS.find(s => s.id === String(schoolNameOrId))?.name || schoolNameOrId || 'School';
      document.getElementById('inviteModalSchoolName').textContent = displayName;
      document.getElementById('inviteModalCode').textContent = code;
      const btnCopyInst = document.getElementById('btnCopyClaimInstructions');
      if (btnCopyInst) {
        btnCopyInst.onclick = () => copyClaimInstructions(code, displayName);
      }
      document.getElementById('modalInviteCodeDisplay')?.classList.remove('hidden');
    };

    window.copyClaimInstructions = function(code, schoolName) {
      const portalUrl = window.location.origin ? (window.location.origin + window.location.pathname) : "https://gab-ai.online/admin";
      const instructions = `Official GabAI SDO Valenzuela Claim Code for ${schoolName}:\n\nCode: ${code}\nPortal: ${portalUrl}\n\nInstructions: Visit the portal link, click "Claim School Account" on the login screen, enter this code, and create your school administrator credentials.`;
      copyToClipboard(instructions, "Claim instructions copied to clipboard!");
    };

    window.toggleSchoolAdminClaimModal = function(show) {
      const m = document.getElementById('modalSchoolAdminClaim');
      if (!m) return;
      if (show) {
        m.classList.remove('hidden');
        document.getElementById('claimAlertBox')?.classList.add('hidden');
      } else {
        m.classList.add('hidden');
      }
    };


    // =========================================================================
    // SCHOOL ACCESS & ADMINISTRATORS MANAGEMENT MODAL (SUPER ADMIN)
    // =========================================================================
    window.openManageSchoolAdminsModal = function(schoolId, schoolName) {
      if (!isSuperAdmin) {
        window.showToast("Only Division Super Admin can manage school administrators.", "error");
        return;
      }
      const targetName = schoolName || getSchoolName(schoolId);
      const schoolIdStr = String(schoolId);

      const elId = document.getElementById('manageAdminsSchoolId');
      const elName = document.getElementById('manageAdminsSchoolName');
      const elTitle = document.getElementById('manageAdminsSchoolTitle');
      const elSubtitle = document.getElementById('manageAdminsSchoolSubtitle');

      if (elId) elId.value = schoolIdStr;
      if (elName) elName.value = targetName;
      if (elTitle) elTitle.textContent = `${targetName}`;
      if (elSubtitle) elSubtitle.textContent = `School ID: ${schoolIdStr} • Administrative Authority & SDO Claim Codes`;

      renderManageSchoolAdminsContent(schoolIdStr, targetName);
      document.getElementById('modalManageSchoolAdmins')?.classList.remove('hidden');
    };

    window.closeManageSchoolAdminsModal = function() {
      document.getElementById('modalManageSchoolAdmins')?.classList.add('hidden');
    };

    window.renderManageSchoolAdminsContent = function(schoolId, schoolName) {
      const targetId = String(schoolId || document.getElementById('manageAdminsSchoolId')?.value || '');
      const targetName = schoolName || document.getElementById('manageAdminsSchoolName')?.value || getSchoolName(targetId);

      // 1. Active School Admins
      const schoolAdmins = cachedUsers.filter(u => u.role === 'school_admin' && String(u.schoolId) === targetId);
      const countBadge = document.getElementById('manageAdminsCountBadge');
      if (countBadge) countBadge.textContent = String(schoolAdmins.length);

      const adminsContainer = document.getElementById('manageAdminsListContainer');
      if (adminsContainer) {
        if (schoolAdmins.length === 0) {
          adminsContainer.innerHTML = `
            <div class="p-3.5 bg-slate-50 border border-slate-200 rounded-2xl text-center">
              <p class="text-xs font-semibold text-slate-600">No administrators currently assigned to this school.</p>
              <p class="text-[11px] text-slate-400 mt-0.5">Generate an SDO claim code below or promote a verified faculty member.</p>
            </div>
          `;
        } else {
          adminsContainer.innerHTML = schoolAdmins.map(adm => `
            <div class="p-3 bg-slate-50 border border-slate-200 rounded-2xl flex items-center justify-between gap-3">
              <div class="flex items-center gap-2.5 min-w-0">
                <div class="w-8 h-8 rounded-xl bg-indigo-100 text-[#5341CD] font-bold text-xs flex items-center justify-center shrink-0">
                  ${escapeHtml((adm.firstName || 'A').charAt(0).toUpperCase())}
                </div>
                <div class="min-w-0">
                  <div class="font-bold text-xs text-slate-900 truncate">
                    ${escapeHtml((adm.firstName || '') + ' ' + (adm.lastName || 'Admin')).trim()}
                    <span class="ml-1.5 px-2 py-0.2 rounded-full text-[9px] font-extrabold bg-emerald-100 text-emerald-800">Active</span>
                  </div>
                  <div class="text-[10px] text-slate-500 truncate">${escapeHtml(adm.position || 'School Administrator')} &bull; <span class="font-mono text-slate-400">${escapeHtml(adm.email || '')}</span></div>
                </div>
              </div>
              <button type="button" onclick="revokeSchoolAdminRole('${adm.id}', '${targetId}')" class="px-2.5 py-1 text-[11px] font-bold text-rose-700 bg-rose-50 hover:bg-rose-100 border border-rose-200 rounded-lg shrink-0 transition" title="Revoke administrative authority">
                Revoke
              </button>
            </div>
          `).join('');
        }
      }

      // 2. SDO Claim Codes
      const codesContainer = document.getElementById('manageClaimCodesContainer');
      if (codesContainer) {
        const schoolInvites = cachedSchoolInvites.filter(inv => String(inv.schoolId) === targetId);
        const pending = schoolInvites.filter(inv => !inv.claimed);
        const claimed = schoolInvites.filter(inv => inv.claimed);

        if (schoolInvites.length === 0) {
          codesContainer.innerHTML = `
            <div class="p-3 bg-slate-50 border border-slate-200 rounded-2xl text-center text-xs text-slate-400 font-medium">
              No claim codes issued for this school yet. Click "Generate New Code" to issue one.
            </div>
          `;
        } else {
          let html = '';
          if (pending.length > 0) {
            html += `
              <div class="space-y-1.5 mb-2">
                <span class="text-[10px] font-bold uppercase tracking-wider text-amber-700">Active Unclaimed Codes (${pending.length})</span>
                ${pending.map(inv => `
                  <div class="p-2.5 bg-amber-50/80 border border-amber-200 rounded-xl flex items-center justify-between gap-2 text-xs">
                    <div class="flex items-center gap-2 font-mono font-bold text-amber-900">
                      ${uiIcon('ticket', 'w-3.5 h-3.5 text-amber-700')}
                      <span>${inv.inviteCode}</span>
                      <span class="text-[10px] font-sans font-normal text-amber-700">Issued ${inv.issuedAt ? new Date(inv.issuedAt).toLocaleDateString() : ''}</span>
                    </div>
                    <div class="flex items-center gap-1.5">
                      <button type="button" onclick="copyClaimInstructions('${inv.inviteCode}', '${escapeHtml(targetName)}')" class="px-2 py-1 bg-white hover:bg-amber-100 text-amber-900 border border-amber-300 rounded-lg text-[10px] font-bold shadow-2xs flex items-center gap-1" title="Copy full claim instructions">
                        ${uiIcon('copy', 'w-3 h-3')} <span>Copy</span>
                      </button>
                      <button type="button" onclick="voidSchoolAdminInvite('${inv.inviteCode}', '${targetId}')" class="p-1 text-slate-400 hover:text-rose-600 rounded text-[10px]" title="Void this unused code">
                        ${uiIcon('trash', 'w-3 h-3')}
                      </button>
                    </div>
                  </div>
                `).join('')}
              </div>
            `;
          }

          if (claimed.length > 0) {
            html += `
              <div class="space-y-1.5">
                <span class="text-[10px] font-bold uppercase tracking-wider text-slate-400">Claim History (${claimed.length})</span>
                ${claimed.map(inv => `
                  <div class="p-2 bg-slate-50 border border-slate-200 rounded-xl flex items-center justify-between text-[11px] text-slate-500">
                    <div class="font-mono text-slate-600 font-semibold">${inv.inviteCode}</div>
                    <div class="text-[10px] text-slate-400">Claimed by ${escapeHtml(inv.claimedEmail || 'Admin')} ${inv.claimedAt ? 'on ' + new Date(inv.claimedAt).toLocaleDateString() : ''}</div>
                  </div>
                `).join('')}
              </div>
            `;
          }

          codesContainer.innerHTML = html;
        }
      }

      // 3. Faculty Dropdown for Promotion
      const facultySelect = document.getElementById('selectFacultyToPromote');
      if (facultySelect) {
        const teachersInSchool = cachedTeachers.filter(t => String(t.schoolId) === targetId && t.role !== 'school_admin');
        facultySelect.innerHTML = `<option value="">Select a teacher from this school...</option>` +
          teachersInSchool.map(t => `
            <option value="${t.id}">${escapeHtml((t.firstName || '') + ' ' + (t.lastName || 'Educator')).trim()} (${escapeHtml(t.email || 'No email')}) - ${t.isApproved ? 'Accredited' : 'Pending Accreditation'}</option>
          `).join('');
      }
    };

    window.handleGenerateCodeFromModal = async function() {
      const schoolId = document.getElementById('manageAdminsSchoolId')?.value;
      const schoolName = document.getElementById('manageAdminsSchoolName')?.value;
      if (!schoolId) return;
      await generateSchoolAdminInvite(schoolId, schoolName);
      renderManageSchoolAdminsContent(schoolId, schoolName);
    };

    window.voidSchoolAdminInvite = async function(inviteCode, schoolId) {
      if (!isSuperAdmin) {
        window.showToast("Unauthorized.", "error");
        return;
      }
      const confirmed = await showConfirmModal({
        title: "Void SDO Claim Code?",
        message: `Are you sure you want to void invite code ${inviteCode}? Anyone with this code will no longer be able to claim it.`,
        badgeIcon: "🎫",
        badgeBg: "bg-rose-50 text-rose-700 border-rose-200",
        confirmText: "Void Code",
        isDestructive: true
      });
      if (!confirmed) return;

      try {
        await deleteDoc(doc(db, "school_admin_invites", inviteCode));
        cachedSchoolInvites = cachedSchoolInvites.filter(inv => inv.inviteCode !== inviteCode);
        window.showToast(`Claim code ${inviteCode} voided.`, "warning");
        renderSchoolsMatrix();
        renderManageSchoolAdminsContent(schoolId);
      } catch (err) {
        console.error("Error voiding invite:", err);
        window.showToast("Failed to void code: " + err.message, "error");
      }
    };

    window.revokeSchoolAdminRole = async function(uid, schoolId) {
      if (!isSuperAdmin) {
        window.showToast("Unauthorized.", "error");
        return;
      }
      const user = cachedUsers.find(u => u.id === uid);
      const name = user ? `${user.firstName || ''} ${user.lastName || 'Admin'}`.trim() : "this administrator";

      const confirmed = await showConfirmModal({
        title: "Revoke Administrator Authority?",
        message: `Remove administrative authority from ${name}? Their account will be converted to a faculty profile.`,
        badgeIcon: "⚠️",
        badgeBg: "bg-amber-50 text-amber-700 border-amber-200",
        confirmText: "Revoke Admin Role",
        isDestructive: true
      });
      if (!confirmed) return;

      try {
        await updateDoc(doc(db, "users", uid), {
          role: "teacher",
          revokedAdminAt: Date.now(),
          revokedAdminBy: currentAdminUser.email
        });

        // Update local cache
        const localUser = cachedUsers.find(u => u.id === uid);
        if (localUser) localUser.role = "teacher";

        // Check if schools doc has this adminUid
        const schoolDocRef = doc(db, "schools", String(schoolId));
        const remainingAdmins = cachedUsers.filter(u => u.role === 'school_admin' && String(u.schoolId) === String(schoolId) && u.id !== uid);
        if (remainingAdmins.length > 0) {
          const nextLead = remainingAdmins[0];
          await setDoc(schoolDocRef, {
            adminUid: nextLead.id,
            adminEmail: nextLead.email || "",
            adminName: `${nextLead.firstName || ''} ${nextLead.lastName || ''}`.trim(),
            updatedAt: Date.now()
          }, { merge: true });
        } else {
          await setDoc(schoolDocRef, {
            adminUid: "",
            adminEmail: "",
            adminName: "",
            updatedAt: Date.now()
          }, { merge: true });
        }

        window.showToast(`Administrative authority revoked for ${name}.`, "success");
        renderSchoolsMatrix();
        renderManageSchoolAdminsContent(schoolId);
      } catch (err) {
        console.error("Revoke error:", err);
        window.showToast("Failed to revoke role: " + err.message, "error");
      }
    };

    window.handlePromoteFacultySubmit = async function() {
      if (!isSuperAdmin) {
        window.showToast("Unauthorized.", "error");
        return;
      }
      const select = document.getElementById('selectFacultyToPromote');
      const teacherUid = select?.value;
      const schoolId = document.getElementById('manageAdminsSchoolId')?.value;
      const schoolName = document.getElementById('manageAdminsSchoolName')?.value;

      if (!teacherUid) {
        window.showToast("Please select a teacher to appoint.", "error");
        return;
      }

      const teacher = cachedTeachers.find(t => t.id === teacherUid);
      const name = teacher ? `${teacher.firstName || ''} ${teacher.lastName || 'Educator'}`.trim() : "Teacher";

      const confirmed = await showConfirmModal({
        title: "Appoint School Administrator?",
        message: `Grant School Administrator authority to ${name} for ${schoolName}? They will gain administrative authority over faculty, classes, and learner accounts for this campus.`,
        badgeIcon: "🛡️",
        badgeBg: "bg-indigo-50 text-indigo-700 border-indigo-200",
        confirmText: "Appoint Administrator",
        isDestructive: false
      });
      if (!confirmed) return;

      try {
        await updateDoc(doc(db, "users", teacherUid), {
          role: "school_admin",
          isApproved: true,
          promotedAt: Date.now(),
          promotedBy: currentAdminUser.email
        });

        // Update local cache
        if (teacher) teacher.role = "school_admin";
        const localUser = cachedUsers.find(u => u.id === teacherUid);
        if (localUser) localUser.role = "school_admin";

        // Update school document lead admin info
        await setDoc(doc(db, "schools", String(schoolId)), {
          adminUid: teacherUid,
          adminEmail: teacher.email || "",
          adminName: name,
          updatedAt: Date.now()
        }, { merge: true });

        window.showToast(`${name} appointed as School Administrator!`, "success");
        renderSchoolsMatrix();
        renderManageSchoolAdminsContent(schoolId, schoolName);
      } catch (err) {
        console.error("Promotion error:", err);
        window.showToast("Failed to appoint admin: " + err.message, "error");
      }
    };

    // ONBOARDING CLAIM SUBMISSION
    window.handleSchoolAdminClaimSubmit = async function(e) {
      e.preventDefault();
      const code = document.getElementById('claimInviteCode').value.trim().toUpperCase();
      const firstName = document.getElementById('claimFirstName').value.trim();
      const lastName = document.getElementById('claimLastName').value.trim();
      const position = document.getElementById('claimPosition').value;
      const email = document.getElementById('claimEmail').value.trim();
      const password = document.getElementById('claimPassword').value.trim();

      const alertBox = document.getElementById('claimAlertBox');
      const submitBtn = document.getElementById('btnClaimSubmit');
      if (alertBox) alertBox.classList.add('hidden');

      if (submitBtn) {
        submitBtn.disabled = true;
        submitBtn.textContent = "Verifying SDO Code...";
      }

      isAccountCreationInProgress = true;

      try {
        let schoolId = "";
        let schoolName = "";
        let invData = null;
        const inviteRef = doc(db, "school_admin_invites", code);

        // Step 1: Verify invite code
        try {
          const inviteDoc = await getDoc(inviteRef);
          if (inviteDoc.exists()) {
            invData = inviteDoc.data();
            if (invData.claimed === true) {
              throw new Error("This Invite Code has already been claimed by another administrator.");
            }
            schoolId = String(invData.schoolId || "");
            schoolName = invData.schoolName || getSchoolName(schoolId);
          } else {
            // Check if code has format SDO-schoolId-XXXX
            const match = code.match(/^SDO-(\d{6})-[A-Z0-9]+$/);
            if (!match) {
              throw new Error("Invalid SDO Invite Code. Please verify the code issued by SDO Valenzuela.");
            }
            schoolId = match[1];
            schoolName = getSchoolName(schoolId);
          }
        } catch (readErr) {
          // If unauthenticated get is blocked by Firestore rules, fallback to pattern check and post-auth verification
          if (readErr.code === 'permission-denied' || readErr.message?.includes('permission')) {
            console.warn("Unauthenticated invite read restricted by Firestore rules. Continuing with authenticated validation...");
            const match = code.match(/^SDO-(\d{6})-[A-Z0-9]+$/);
            if (!match) {
              throw new Error("Invalid SDO Invite Code format. Expected format: SDO-XXXXXX-XXXX.");
            }
            schoolId = match[1];
            schoolName = getSchoolName(schoolId);
          } else {
            throw readErr;
          }
        }

        if (!schoolId) {
          throw new Error("Could not determine school ID from this invite code.");
        }

        // Step 2: Create Auth user
        submitBtn.textContent = "Creating School Administrator...";
        const userCred = await createUserWithEmailAndPassword(auth, email, password);
        const user = userCred.user;

        // Verify invite as authenticated user if unauthenticated read failed earlier
        if (!invData) {
          try {
            const authInviteDoc = await getDoc(inviteRef);
            if (authInviteDoc.exists()) {
              invData = authInviteDoc.data();
              if (invData.claimed === true) {
                await user.delete();
                throw new Error("This Invite Code has already been claimed by another administrator.");
              }
              schoolId = String(invData.schoolId || schoolId);
              schoolName = invData.schoolName || schoolName;
            }
          } catch (postAuthErr) {
            console.warn("Post-auth invite check notice:", postAuthErr);
          }
        }

        // Step 3: Create user document in users/{uid}
        submitBtn.textContent = "Setting Up Authority...";
        const userData = {
          firstName: firstName,
          lastName: lastName,
          position: position,
          email: email,
          role: "school_admin",
          schoolId: schoolId,
          schoolName: schoolName,
          isApproved: true,
          emailVerified: true,
          claimedInviteCode: code,
          createdAt: Date.now()
        };

        await setDoc(doc(db, "users", user.uid), userData);

        // Step 4: Mark invite as claimed (gracefully handled if rules restrict non-superadmin updates)
        try {
          await updateDoc(inviteRef, {
            claimed: true,
            claimedByUid: user.uid,
            claimedEmail: email,
            claimedAt: Date.now()
          });
        } catch (invErr) {
          console.warn("Notice: school_admin_invites update restricted by rules:", invErr);
        }

        // Step 5: Update school doc with admin details (gracefully handled if rules restrict non-superadmin updates)
        try {
          await setDoc(doc(db, "schools", schoolId), {
            schoolId: schoolId,
            schoolName: schoolName,
            adminUid: user.uid,
            adminEmail: email,
            adminName: `${firstName} ${lastName}`,
            updatedAt: Date.now()
          }, { merge: true });
        } catch (schErr) {
          console.warn("Notice: schools update restricted by rules:", schErr);
        }

        currentAdminUser = { uid: user.uid, ...userData };
        isSuperAdmin = false;
        isSchoolAdmin = true;
        isAccountCreationInProgress = false;

        window.showToast(`School Admin account activated for ${schoolName}!`, 'success');
        toggleSchoolAdminClaimModal(false);
        showAdminDashboard();
        startRealtimeListeners();

      } catch (err) {
        isAccountCreationInProgress = false;
        console.error("School Admin Claim Error:", err);
        if (alertBox) {
          let msg = err.message || "Failed to activate School Admin account.";
          if (err.code === 'permission-denied' || msg.includes('permission')) {
            msg = "Missing or insufficient permissions: Firestore security rules blocked the operation. Please ensure Firestore rules in Firebase Console allow 'users' and 'school_admin_invites' access.";
          } else if (err.code === 'auth/email-already-in-use') {
            msg = "This email is already registered. If you already have an account, please sign in with your credentials on the login screen.";
          }
          alertBox.className = "p-2.5 rounded-lg text-xs font-medium bg-red-50 text-red-700 border border-red-200";
          alertBox.textContent = msg;
          alertBox.classList.remove('hidden');
        }
      } finally {
        if (submitBtn) {
          submitBtn.disabled = false;
          submitBtn.textContent = "Verify & Activate School Admin";
        }
      }
    };


window.renderSchoolsMatrix = renderSchoolsMatrix;
