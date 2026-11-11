    // Mobile Sidebar Drawer Toggle
    window.toggleMobileSidebar = function(force) {
      const sidebar = document.getElementById('mainSidebar');
      const backdrop = document.getElementById('mobileSidebarBackdrop');
      if (!sidebar || !backdrop) return;
      const isClosed = sidebar.classList.contains('-translate-x-full');
      const shouldOpen = typeof force === 'boolean' ? force : isClosed;
      if (shouldOpen) {
        sidebar.classList.remove('-translate-x-full');
        backdrop.classList.remove('hidden');
        document.body.classList.add('overflow-hidden', 'lg:overflow-auto');
      } else {
        sidebar.classList.add('-translate-x-full');
        backdrop.classList.add('hidden');
        document.body.classList.remove('overflow-hidden', 'lg:overflow-auto');
      }
    };


    // =========================================================================
    // MODERN GABAI BENTO CONFIRMATION MODAL HELPER (REPLACES NATIVE CONFIRM)
    // =========================================================================
    window.showConfirmModal = function({
      title = "Confirm Action",
      message = "Are you sure you want to proceed?",
      badgeIcon = "⚠️",
      badgeBg = "bg-amber-50 text-amber-600 border-amber-200",
      confirmText = "Confirm",
      cancelText = "Cancel",
      isDestructive = false,
      dependencyWarning = "",
      requireTypedText = ""
    }) {
      return new Promise((resolve) => {
        const modal = document.getElementById('modalCustomConfirm');
        if (!modal) {
          resolve(window.confirm(message));
          return;
        }
        const elTitle = document.getElementById('confirmModalTitle');
        const elMsg = document.getElementById('confirmModalMessage');
        const elBadge = document.getElementById('confirmModalBadge');
        const elWarnBox = document.getElementById('confirmModalWarningBox');
        const elWarnText = document.getElementById('confirmModalWarningText');
        const elTypedBox = document.getElementById('confirmModalTypedBox');
        const elTypedReq = document.getElementById('confirmModalRequiredText');
        const elTypedInput = document.getElementById('confirmModalTypedInput');
        const btnCancel = document.getElementById('btnConfirmCancel');
        const btnProceed = document.getElementById('btnConfirmProceed');

        if (elTitle) elTitle.textContent = title;
        if (elMsg) elMsg.textContent = message;
        if (elBadge) {
          elBadge.textContent = badgeIcon;
          elBadge.className = `w-12 h-12 rounded-2xl flex items-center justify-center text-xl shrink-0 border shadow-xs ${badgeBg}`;
        }

        if (dependencyWarning && elWarnBox && elWarnText) {
          elWarnText.textContent = dependencyWarning;
          elWarnBox.classList.remove('hidden');
        } else if (elWarnBox) {
          elWarnBox.classList.add('hidden');
        }

        if (requireTypedText && elTypedBox && elTypedReq && elTypedInput) {
          elTypedReq.textContent = requireTypedText;
          elTypedInput.value = '';
          elTypedBox.classList.remove('hidden');
        } else if (elTypedBox) {
          elTypedBox.classList.add('hidden');
        }

        if (btnCancel) btnCancel.textContent = cancelText;
        if (btnProceed) {
          btnProceed.textContent = confirmText;
          if (isDestructive) {
            btnProceed.className = "px-5 py-2 bg-gradient-to-r from-rose-600 to-red-600 hover:opacity-95 text-white rounded-xl text-xs font-bold shadow-md shadow-rose-500/20 transition-all flex items-center gap-1.5";
          } else {
            btnProceed.className = "px-5 py-2 bg-gradient-to-r from-[#6C5CE7] to-[#5341CD] hover:opacity-95 text-white rounded-xl text-xs font-bold shadow-md shadow-purple-500/20 transition-all flex items-center gap-1.5";
          }
        }

        const cleanup = () => {
          modal.classList.add('hidden');
          if (btnCancel) btnCancel.onclick = null;
          if (btnProceed) btnProceed.onclick = null;
        };

        if (btnCancel) {
          btnCancel.onclick = () => {
            cleanup();
            resolve(false);
          };
        }

        if (btnProceed) {
          btnProceed.onclick = () => {
            if (requireTypedText && elTypedInput) {
              const val = (elTypedInput.value || '').trim();
              if (val.toUpperCase() !== requireTypedText.toUpperCase()) {
                window.showToast(`Please type "${requireTypedText}" exactly to confirm.`, "warning");
                elTypedInput.focus();
                return;
              }
            }
            cleanup();
            resolve(true);
          };
        }

        modal.classList.remove('hidden');
        if (requireTypedText && elTypedInput) {
          setTimeout(() => elTypedInput.focus(), 100);
        }
      });
    };


    // =========================================================================
    // MODULAR RECYCLE VAULT: 30-DAY RETENTION & 1-CLICK MULTI-ENTITY RESTORE
    // =========================================================================
    let currentVaultRecords = [];
    let currentVaultTypeFilter = 'ALL';
    let currentVaultStatusFilter = 'archived'; // 'archived', 'restored', 'ALL'
    let currentVaultSearch = '';

    window.setVaultTypeFilter = function(type) {
      currentVaultTypeFilter = type;
      ['ALL', 'student', 'teacher', 'material', 'subject', 'class', 'school', 'assessment'].forEach(t => {
        const btn = document.getElementById(`chip-vault-${t}`);
        if (!btn) return;
        if (t === type) {
          btn.className = 'px-3 py-1 rounded-full font-bold bg-[#5341CD] text-white shadow-xs transition-all';
        } else {
          btn.className = 'px-3 py-1 rounded-full font-semibold bg-slate-100 text-slate-600 hover:bg-slate-200 transition-all';
        }
      });
      filterRecycleVault();
    };

    window.setVaultStatusFilter = function(status) {
      currentVaultStatusFilter = status;
      const bArchived = document.getElementById('btnVaultStatusArchived');
      const bRestored = document.getElementById('btnVaultStatusRestored');
      const bAll = document.getElementById('btnVaultStatusAll');

      if (bArchived) bArchived.className = status === 'archived' ? 'px-3 py-1.5 rounded-lg bg-white text-slate-800 shadow-xs font-bold transition-all' : 'px-3 py-1.5 rounded-lg text-slate-500 hover:text-slate-800 transition-all';
      if (bRestored) bRestored.className = status === 'restored' ? 'px-3 py-1.5 rounded-lg bg-white text-slate-800 shadow-xs font-bold transition-all' : 'px-3 py-1.5 rounded-lg text-slate-500 hover:text-slate-800 transition-all';
      if (bAll) bAll.className = status === 'ALL' ? 'px-3 py-1.5 rounded-lg bg-white text-slate-800 shadow-xs font-bold transition-all' : 'px-3 py-1.5 rounded-lg text-slate-500 hover:text-slate-800 transition-all';

      filterRecycleVault();
    };

    window.filterRecycleVault = function() {
      const q = (document.getElementById('vaultSearchInput')?.value || '').toLowerCase().trim();
      currentVaultSearch = q;

      const filtered = currentVaultRecords.filter(r => {
        if (currentVaultStatusFilter !== 'ALL' && (r.status || 'archived') !== currentVaultStatusFilter) {
          return false;
        }
        if (currentVaultTypeFilter !== 'ALL' && (r.entityType || '').toLowerCase() !== currentVaultTypeFilter.toLowerCase()) {
          return false;
        }
        if (q) {
          const matchName = (r.displayName || '').toLowerCase().includes(q);
          const matchSub = (r.subtitle || '').toLowerCase().includes(q);
          const matchId = (r.originalDocId || '').toLowerCase().includes(q);
          const matchReason = (r.deletionReason || '').toLowerCase().includes(q);
          const matchOp = (r.archivedByName || '').toLowerCase().includes(q);
          const matchCol = (r.originalCollection || '').toLowerCase().includes(q);
          if (!matchName && !matchSub && !matchId && !matchReason && !matchOp && !matchCol) return false;
        }
        return true;
      });

      renderRecycleVaultTable(filtered);
    };

    window.renderRecycleVault = async function() {
      const tbody = document.getElementById('recycleVaultTableBody');
      const emptyState = document.getElementById('vaultEmptyState');
      if (!tbody) return;

      tbody.innerHTML = `<tr><td colspan="6" class="px-6 py-12 text-center text-slate-400">Loading Recycle Bin records from Firestore...</td></tr>`;
      if (emptyState) emptyState.classList.add('hidden');

      try {
        let qRef;
        if (isSchoolAdmin && currentAdminUser?.schoolId) {
          qRef = query(collection(db, "system_archives"), where("schoolId", "==", String(currentAdminUser.schoolId)));
        } else {
          qRef = collection(db, "system_archives");
        }

        const snapshot = await getDocs(qRef);
        currentVaultRecords = snapshot.docs.map(docSnap => ({
          id: docSnap.id,
          ...docSnap.data()
        }));
        // Sort in memory by archivedAt DESC: Eliminates any Firestore composite index requirements!
        currentVaultRecords.sort((a, b) => (b.archivedAt || 0) - (a.archivedAt || 0));

        // Also check for any soft-deleted schools in schools collection
        if (isSuperAdmin) {
          try {
            const schoolsSnap = await getDocs(collection(db, "schools"));
            schoolsSnap.docs.forEach(d => {
              const sData = d.data();
              if (sData.status === 'deleted' && !currentVaultRecords.some(r => r.originalDocId === d.id && r.entityType === 'school')) {
                currentVaultRecords.push({
                  id: `virtual_school_${d.id}`,
                  archiveId: `virtual_school_${d.id}`,
                  entityType: 'school',
                  originalCollection: 'schools',
                  originalDocId: d.id,
                  schoolId: d.id,
                  displayName: sData.schoolName || sData.name || `School ${d.id}`,
                  subtitle: `School ID: ${d.id} • SDO Valenzuela`,
                  archivedAt: sData.deletedAt || Date.now(),
                  archivedByName: sData.deletedBy || 'Super Admin',
                  archivedByRole: 'super_admin',
                  deletionReason: 'Archived from School Directory',
                  snapshot: sData,
                  status: 'archived',
                  retentionUntil: (sData.deletedAt || Date.now()) + (30 * 24 * 60 * 60 * 1000)
                });
              }
            });
          } catch (e) {
            console.warn("Could not query soft-deleted schools:", e);
          }
        }

        let countStudents = 0;
        let countMaterials = 0;
        let countClasses = 0;
        let countTeachers = 0;
        let countSchools = 0;
        let activeCount = 0;

        currentVaultRecords.forEach(r => {
          if ((r.status || 'archived') === 'archived') {
            activeCount++;
            const type = (r.entityType || '').toLowerCase();
            if (type === 'student') countStudents++;
            else if (type === 'material' || type === 'subject') countMaterials++;
            else if (type === 'class') countClasses++;
            else if (type === 'teacher') countTeachers++;
            else if (type === 'school') countSchools++;
          }
        });

        const statTotal = document.getElementById('statVaultTotal');
        const statStudents = document.getElementById('statVaultStudents');
        const statMaterials = document.getElementById('statVaultMaterials');
        const statClasses = document.getElementById('statVaultClasses');
        const badgeCount = document.getElementById('badgeRecycleVaultCount');

        if (statTotal) statTotal.textContent = activeCount;
        if (statStudents) statStudents.textContent = countStudents;
        if (statMaterials) statMaterials.textContent = countMaterials;
        if (statClasses) statClasses.textContent = countClasses;

        if (badgeCount) {
          badgeCount.textContent = activeCount;
          badgeCount.classList.toggle('hidden', activeCount === 0);
        }

        filterRecycleVault();
      } catch (err) {
        console.error('Error loading recycle vault:', err);
        tbody.innerHTML = `<tr><td colspan="6" class="px-6 py-8 text-center text-rose-500 font-semibold">Failed to load Recycle Bin: ${escapeHtml(err.message)}</td></tr>`;
      }
    };

    function renderRecycleVaultTable(records) {
      const tbody = document.getElementById('recycleVaultTableBody');
      const emptyState = document.getElementById('vaultEmptyState');
      if (!tbody) return;

      if (!records || records.length === 0) {
        tbody.innerHTML = '';
        if (emptyState) emptyState.classList.remove('hidden');
        return;
      }

      if (emptyState) emptyState.classList.add('hidden');

      const entityBadges = {
        student: { icon: '👤', label: 'Student', bg: 'bg-blue-50 text-blue-700 border-blue-200' },
        teacher: { icon: '👩‍🏫', label: 'Faculty', bg: 'bg-purple-50 text-[#5341CD] border-purple-200' },
        material: { icon: '📄', label: 'Material', bg: 'bg-indigo-50 text-indigo-700 border-indigo-200' },
        subject: { icon: '📚', label: 'Subject', bg: 'bg-purple-50 text-purple-700 border-purple-200' },
        class: { icon: '🏫', label: 'Class', bg: 'bg-emerald-50 text-emerald-700 border-emerald-200' },
        school: { icon: '🏛️', label: 'School', bg: 'bg-amber-50 text-amber-800 border-amber-300' },
        announcement: { icon: '📢', label: 'Announcement', bg: 'bg-amber-50 text-amber-700 border-amber-200' },
        assessment: { icon: '⏱️', label: 'Assessment', bg: 'bg-purple-50 text-[#5341CD] border-purple-200' }
      };

      const now = Date.now();

      tbody.innerHTML = records.map(r => {
        const type = (r.entityType || 'record').toLowerCase();
        const badge = entityBadges[type] || { icon: '📦', label: type, bg: 'bg-slate-100 text-slate-700 border-slate-200' };
        const isRestored = r.status === 'restored';

        const retention = r.retentionUntil || (r.archivedAt + 30 * 24 * 60 * 60 * 1000);
        const daysLeft = Math.max(0, Math.ceil((retention - now) / (24 * 60 * 60 * 1000)));

        const dateStr = r.archivedAt ? new Date(r.archivedAt).toLocaleString('en-US', { month: 'short', day: 'numeric', year: 'numeric', hour: 'numeric', minute: '2-digit' }) : 'N/A';

        return `
          <tr class="hover:bg-slate-50/70 transition-colors ${isRestored ? 'opacity-60 bg-slate-50/30' : ''}">
            <td class="px-5 py-4">
              <div class="flex items-center gap-3">
                <span class="inline-flex items-center gap-1 px-2.5 py-1 rounded-lg text-[10.5px] font-bold border ${badge.bg}">
                  <span>${badge.icon}</span>
                  <span>${badge.label}</span>
                </span>
                <div>
                  <div class="font-bold text-slate-800 text-xs">${escapeHtml(r.displayName || 'Untitled')}</div>
                  <div class="text-[11px] text-slate-400 font-mono mt-0.5">${escapeHtml(r.subtitle || r.originalDocId || '')}</div>
                </div>
              </div>
            </td>
            <td class="px-4 py-4">
              <span class="px-2 py-0.5 rounded-md font-mono text-[11px] bg-slate-100 text-slate-600 border border-slate-200">
                ${escapeHtml(r.originalCollection || '')}/${escapeHtml((r.originalDocId || '').substring(0, 10))}...
              </span>
            </td>
            <td class="px-4 py-4">
              <div class="text-xs text-slate-700 font-medium">${escapeHtml(r.deletionReason || 'Manual deletion')}</div>
              <div class="text-[11px] text-slate-400 mt-0.5">By ${escapeHtml(r.archivedByName || 'Faculty')} (${escapeHtml(r.archivedByRole || 'teacher')})</div>
            </td>
            <td class="px-4 py-4">
              <div class="text-xs text-slate-700 font-medium">${dateStr}</div>
              ${isRestored 
                ? `<span class="inline-block text-[10px] font-bold text-emerald-600 mt-0.5">Restored to System</span>`
                : `<span class="inline-block text-[10.5px] font-bold ${daysLeft <= 5 ? 'text-rose-600' : 'text-amber-600'} mt-0.5">⏳ ${daysLeft} days left in vault</span>`
              }
            </td>
            <td class="px-4 py-4">
              ${isRestored 
                ? `<span class="inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-[10.5px] font-bold bg-emerald-50 text-emerald-700 border border-emerald-200">
                    <svg class="w-3 h-3" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><polyline points="20 6 9 17 4 12"/></svg>
                    Restored
                   </span>`
                : `<span class="inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-[10.5px] font-bold bg-amber-50 text-amber-700 border border-amber-200">
                    <svg class="w-3 h-3" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>
                    Archived
                   </span>`
              }
            </td>
            <td class="px-5 py-4 text-right">
              <div class="inline-flex items-center gap-1.5 justify-end">
                <button onclick="inspectArchiveSnapshot('${r.id}')" title="Inspect captured data snapshot" class="p-1.5 text-slate-400 hover:text-slate-700 hover:bg-slate-100 rounded-lg transition-colors">
                  <svg class="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M2 12s3-7 10-7 10 7 10 7-3 7-10 7-10-7-10-7Z"/><circle cx="12" cy="12" r="3"/></svg>
                </button>
                ${!isRestored 
                  ? `<button onclick="restoreArchivedRecord('${r.id}')" class="px-3 py-1.5 bg-[#5341CD] hover:bg-[#6C5CE7] text-white rounded-lg text-xs font-bold transition-all shadow-xs flex items-center gap-1">
                      <svg class="w-3.5 h-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="1 4 1 10 7 10"></polyline><path d="M3.51 15a9 9 0 1 0 2.13-9.36L1 10"></path></svg>
                      <span>Restore</span>
                    </button>`
                  : `<span class="text-[11px] text-slate-400 italic px-2">Restored</span>`
                }
                <button onclick="purgeArchivedRecord('${r.id}', '${(r.displayName || 'this record').replace(/'/g, "\\'")}')" title="Force Delete Permanently (Irreversible)" class="p-1.5 text-slate-400 hover:text-rose-600 hover:bg-rose-50 rounded-lg transition-colors">
                  <svg class="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M3 6h18"/><path d="M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6"/><path d="M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2"/></svg>
                </button>
              </div>
            </td>
          </tr>
        `;
      }).join('');
    }

    window.inspectArchiveSnapshot = function(archiveId) {
      const rec = currentVaultRecords.find(r => r.id === archiveId);
      if (!rec) return;

      const modal = document.getElementById('modalArchiveSnapshot');
      const title = document.getElementById('snapshotModalTitle');
      const subtitle = document.getElementById('snapshotModalSubtitle');
      const metaGrid = document.getElementById('snapshotMetaGrid');
      const jsonPre = document.getElementById('snapshotJsonPre');
      const btnRestore = document.getElementById('btnRestoreFromSnapshot');

      if (title) title.textContent = `${rec.displayName || 'Entity'} (Snapshot)`;
      if (subtitle) subtitle.textContent = `ID: ${rec.originalDocId} • Type: ${rec.entityType} • Collection: ${rec.originalCollection}`;

      if (metaGrid) {
        metaGrid.innerHTML = `
          <div><span class="text-slate-400 font-semibold block">School ID:</span> <span class="font-bold text-slate-700">${escapeHtml(rec.schoolId || 'N/A')}</span></div>
          <div><span class="text-slate-400 font-semibold block">Archived By:</span> <span class="font-bold text-slate-700">${escapeHtml(rec.archivedByName || 'Faculty')}</span></div>
          <div><span class="text-slate-400 font-semibold block">Reason:</span> <span class="font-bold text-slate-700">${escapeHtml(rec.deletionReason || 'N/A')}</span></div>
          <div><span class="text-slate-400 font-semibold block">Timestamp:</span> <span class="font-bold text-slate-700">${rec.archivedAt ? new Date(rec.archivedAt).toLocaleString() : 'N/A'}</span></div>
          <div><span class="text-slate-400 font-semibold block">Status:</span> <span class="font-bold uppercase ${rec.status === 'restored' ? 'text-emerald-600' : 'text-amber-600'}">${rec.status || 'archived'}</span></div>
          <div><span class="text-slate-400 font-semibold block">Subcollections:</span> <span class="font-bold text-slate-700">${rec.subcollections ? Object.keys(rec.subcollections).join(', ') || 'None' : 'None'}</span></div>
        `;
      }

      const displayObj = {
        metadata: {
          archiveId: rec.id,
          entityType: rec.entityType,
          originalCollection: rec.originalCollection,
          originalDocId: rec.originalDocId,
          schoolId: rec.schoolId
        },
        documentSnapshot: rec.snapshot || {},
        subcollections: rec.subcollections || {}
      };

      if (jsonPre) jsonPre.textContent = JSON.stringify(displayObj, null, 2);

      if (btnRestore) {
        if (rec.status === 'restored') {
          btnRestore.classList.add('hidden');
        } else {
          btnRestore.classList.remove('hidden');
          btnRestore.onclick = () => {
            closeArchiveSnapshotModal();
            restoreArchivedRecord(archiveId);
          };
        }
      }

      if (modal) {
        modal.classList.remove('hidden');
        document.body.classList.add('overflow-hidden');
      }
    };

    window.closeArchiveSnapshotModal = function() {
      const modal = document.getElementById('modalArchiveSnapshot');
      if (modal) modal.classList.add('hidden');
      document.body.classList.remove('overflow-hidden');
    };

    window.purgeArchivedRecord = async function(archiveId, displayName) {
      if (!isSuperAdmin) {
        window.showToast("Permanent force-delete is restricted to Division Super Administrators.", "warning");
        return;
      }
      const confirmed = await showConfirmModal({
        title: `Force Delete Record?`,
        message: `Are you sure you want to permanently delete "${displayName || 'this record'}" from the Recycle Bin? This record will be permanently purged from the database and cannot be recovered.`,
        badgeIcon: "🗑️",
        badgeBg: "bg-rose-50 text-rose-600 border-rose-200",
        confirmText: "Force Delete Permanently",
        isDestructive: true
      });
      if (!confirmed) return;

      try {
        await deleteDoc(doc(db, "system_archives", archiveId));
        window.showToast(`Record "${displayName || archiveId}" permanently purged from vault.`, "info");
        currentVaultRecords = currentVaultRecords.filter(r => r.id !== archiveId);
        filterRecycleVault();
        updateRecycleVaultBadge();
      } catch (err) {
        console.error("Purge error:", err);
        window.showToast("Failed to purge record: " + err.message, "error");
      }
    };

    window.restoreArchivedRecord = async function(archiveId) {
      const rec = currentVaultRecords.find(r => r.id === archiveId);
      if (!rec) return;

      const confirmed = await showConfirmModal({
        title: `Restore "${rec.displayName}"?`,
        message: `This will recover the record back to active collection (${rec.originalCollection}) and restore its full relational history.`,
        badgeIcon: "♻️",
        badgeBg: "bg-emerald-50 text-emerald-700 border-emerald-200",
        confirmText: "Restore Record",
        isDestructive: false
      });
      if (!confirmed) return;

      window.showToast(`Restoring ${rec.displayName}...`, 'info');

      const origCol = rec.originalCollection;
      const origId = rec.originalDocId;
      const snapshot = rec.snapshot || {};
      const subcollections = rec.subcollections || {};

      try {
        if (rec.entityType === 'school' || origCol === 'schools') {
          // Special Restoration for School
          await setDoc(doc(db, "schools", String(origId)), {
            ...snapshot,
            status: "active",
            restoredAt: Date.now(),
            restoredBy: currentAdminUser?.email || "super_admin"
          }, { merge: true });

          if (!VALENZUELA_SCHOOLS.some(s => s.id === String(origId))) {
            const defSchool = DEFAULT_VALENZUELA_SCHOOLS.find(s => s.id === String(origId));
            VALENZUELA_SCHOOLS.push({
              id: String(origId),
              name: snapshot.schoolName || snapshot.name || (defSchool ? defSchool.name : `School ${origId}`),
              division: snapshot.division || "Valenzuela City",
              status: "active"
            });
            VALENZUELA_SCHOOLS.sort((a, b) => a.name.localeCompare(b.name));
            updateSchoolsCountUI();
            populateSchoolsDropdowns();
            scheduleDashboardRender();
          }
        } else if (rec.entityType === 'teacher' || (origCol === 'users' && snapshot.role === 'teacher')) {
          // Special Restoration for Teacher
          await setDoc(doc(db, "users", origId), {
            ...snapshot,
            isApproved: true,
            restoredAt: Date.now()
          });
          scheduleDashboardRender();
        } else if (rec.entityType === 'assessment' || origCol === 'division_timed_assessments' || origCol === 'weekly_assessments') {
          // Special Restoration for Timed Assessment
          await setDoc(doc(db, origCol, origId), {
            ...snapshot,
            status: "active",
            restoredAt: Date.now(),
            restoredBy: currentAdminUser?.email || "super_admin"
          });
          if (typeof updateTimedAssessmentMetrics === 'function') updateTimedAssessmentMetrics();
          if (typeof renderTimedAssessmentsTable === 'function') renderTimedAssessmentsTable();
        } else {
          // Standard restoration
          await setDoc(doc(db, origCol, origId), snapshot);

          // Restore subcollections
          for (const [subName, docList] of Object.entries(subcollections)) {
            if (rec.entityType === 'subject' && subName === 'materials') {
              for (const mat of docList) {
                const cleanMat = { ...mat };
                const matId = cleanMat.__matDocId || doc(collection(db, 'library_materials')).id;
                delete cleanMat.__matDocId;
                await setDoc(doc(db, 'library_materials', matId), cleanMat);
              }
            } else {
              for (const subDoc of docList) {
                const cleanSub = { ...subDoc };
                const subId = cleanSub.__subDocId || doc(collection(db, origCol, origId, subName)).id;
                delete cleanSub.__subDocId;
                await setDoc(doc(db, origCol, origId, subName, subId), cleanSub);
              }
            }
          }

          // Re-enroll student into joinedStudents array in class documents
          const enrolledClasses = snapshot.enrolledClasses;
          if (rec.entityType === 'student' && Array.isArray(enrolledClasses)) {
            for (const cId of enrolledClasses) {
              if (cId) {
                try {
                  await updateDoc(doc(db, 'classes', String(cId)), {
                    joinedStudents: arrayUnion(origId)
                  });
                } catch (e) {
                  console.warn('Could not re-add to class:', cId, e);
                }
              }
            }
          }
        }

        // Update archive doc status
        if (!archiveId.startsWith('virtual_')) {
          await updateDoc(doc(db, 'system_archives', archiveId), {
            status: 'restored',
            restoredAt: Date.now(),
            restoredByUid: currentAdminUser?.uid || ''
          });
        }

        window.showToast(`Successfully restored "${rec.displayName}"!`, 'success');
        renderRecycleVault();
        scheduleDashboardRender();
      } catch (err) {
        console.error('Error restoring record:', err);
        window.showToast(`Restoration failed: ${err.message}`, 'error');
      }
    };



    // =========================================================================
    // ADMINISTRATIVE REMARKS & PEDAGOGICAL GUIDANCE DRAWER
    // =========================================================================
    window.openAdminRemarksModal = async function(targetType, targetId, targetTitle) {
      activeRemarkTarget = { type: targetType, id: targetId, title: targetTitle };
      const titleElem = document.getElementById('remarksModalTitle');
      const subtitleElem = document.getElementById('remarksModalSubtitle');
      const container = document.getElementById('modalAdminRemarks');
      const input = document.getElementById('inputRemarkText');

      if (titleElem) titleElem.textContent = `Remarks: ${targetTitle}`;
      if (subtitleElem) subtitleElem.textContent = `${targetType === 'material' ? 'PDF Material' : 'Weekly Assessment'} • Pedagogical Guidance & Oversight`;
      if (input) input.value = '';
      if (container) container.classList.remove('hidden');

      loadRemarksFeed();
    };

    window.closeAdminRemarksModal = function() {
      const container = document.getElementById('modalAdminRemarks');
      if (container) container.classList.add('hidden');
      activeRemarkTarget = { type: null, id: null, title: null };
    };

    async function loadRemarksFeed() {
      const feed = document.getElementById('remarksFeedContainer');
      if (!feed || !activeRemarkTarget.id) return;
      feed.innerHTML = `<div class="text-slate-400 text-center text-xs py-6">Loading remarks...</div>`;

      try {
        const collectionPath = activeRemarkTarget.type === 'material' ? 'library_materials' : 'weekly_assessments';
        const remarksSnapshot = await getDocs(collection(db, collectionPath, activeRemarkTarget.id, "remarks"));
        
        if (remarksSnapshot.empty) {
          feed.innerHTML = `
            <div class="p-6 text-center text-slate-400">
              <div class="w-12 h-12 rounded-2xl bg-indigo-50 text-indigo-500 flex items-center justify-center mx-auto mb-2">
                ${uiIcon('message', 'w-6 h-6')}
              </div>
              <p class="text-xs font-semibold text-slate-600">No administrative remarks posted yet.</p>
              <p class="text-[11px] text-slate-400 mt-0.5">Submit curriculum notes, teaching feedback, or guidance for the educator below.</p>
            </div>
          `;
          return;
        }

        const remarks = remarksSnapshot.docs.map(d => ({ id: d.id, ...d.data() }))
          .sort((a, b) => (b.createdAt || 0) - (a.createdAt || 0));

        feed.innerHTML = remarks.map(r => {
          const dateStr = r.createdAt ? new Date(r.createdAt).toLocaleString('en-US', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' }) : 'Recorded';
          const isSdo = r.authorRole === 'super_admin' || r.authorRole === 'admin' || r.authorSchoolId === 'DIVISION_OFFICE';
          const badgeColor = isSdo ? 'bg-purple-100 text-[#5341CD] border-purple-200' : 'bg-indigo-100 text-indigo-800 border-indigo-200';
          const roleLabel = isSdo ? 'Super Admin (SDO)' : `School Admin • ${getSchoolName(r.authorSchoolId)}`;

          return `
            <div class="p-3 bg-slate-50 border border-slate-200 rounded-2xl space-y-1.5 shadow-xs">
              <div class="flex items-center justify-between">
                <div class="flex items-center gap-1.5">
                  <span class="font-bold text-xs text-slate-900">${r.authorName || 'Administrator'}</span>
                  <span class="px-2 py-0.5 rounded-full border text-[10px] font-bold ${badgeColor}">${roleLabel}</span>
                </div>
                <span class="text-[10px] text-slate-400">${dateStr}</span>
              </div>
              <p class="text-xs text-slate-700 whitespace-pre-line leading-relaxed">${r.text || ''}</p>
            </div>
          `;
        }).join('');

      } catch (err) {
        console.error("Load remarks error:", err);
        feed.innerHTML = `<div class="p-4 text-center text-red-500 text-xs font-medium">Failed to load remarks: ${err.message}</div>`;
      }
    }

    window.handlePostRemarkSubmit = async function(e) {
      e.preventDefault();
      if (!currentAdminUser || !activeRemarkTarget.id) return;
      const text = document.getElementById('inputRemarkText').value.trim();
      if (!text) return;

      const btn = document.getElementById('btnPostRemark');
      if (btn) {
        btn.disabled = true;
        btn.textContent = "Posting...";
      }

      try {
        const collectionPath = activeRemarkTarget.type === 'material' ? 'library_materials' : 'weekly_assessments';
        const docRef = collection(db, collectionPath, activeRemarkTarget.id, "remarks");
        
        await addDoc(docRef, {
          authorUid: currentAdminUser.uid,
          authorName: `${currentAdminUser.firstName || ''} ${currentAdminUser.lastName || ''}`.trim() || 'Administrator',
          authorEmail: currentAdminUser.email || '',
          authorRole: currentAdminUser.role || (isSuperAdmin ? 'super_admin' : 'school_admin'),
          authorSchoolId: currentAdminUser.schoolId || '',
          authorPosition: currentAdminUser.position || (isSuperAdmin ? 'Division Supervisor' : 'School Administrator'),
          text: text,
          createdAt: Date.now()
        });

        document.getElementById('inputRemarkText').value = '';
        window.showToast("Administrative remark submitted.", "success");
        await loadRemarksFeed();

      } catch (err) {
        console.error("Post remark error:", err);
        window.showToast("Failed to post remark: " + err.message, "error");
      } finally {
        if (btn) {
          btn.disabled = false;
          btn.textContent = "Submit Remark";
        }
      }
    };

    window.inspectSchool = function(id) {
      document.getElementById('jurisdictionSelect').value = id;
      handleJurisdictionChange(id);
      switchTab('tab-division-dashboard');
    };


window.renderRecycleVaultTable = renderRecycleVaultTable;
