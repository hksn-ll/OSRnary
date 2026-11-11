    // =========================================================================
    // ANNOUNCEMENTS & DIRECTIVES SYSTEM WITH SEEN-BY & DISCREET REPLIES
    // =========================================================================

    window.updateDirectivesBanner = function() {
      const banner = document.getElementById('divisionDirectivesBanner');
      if (!banner) return;
      if (!isSchoolAdmin || !currentAdminUser?.schoolId) {
        banner.classList.add('hidden');
        return;
      }

      const schoolId = String(currentAdminUser.schoolId);
      const directives = cachedAnnouncements.filter(a => 
        a.isActive !== false &&
        Array.isArray(a.targetRoles) && a.targetRoles.includes('school_admin') &&
        (a.schoolId === 'ALL' || a.schoolId === schoolId)
      ).sort((a, b) => (b.createdAt || 0) - (a.createdAt || 0));

      if (directives.length > 0) {
        const d = directives[0];
        document.getElementById('directiveTitle').textContent = d.title || 'Official Division Directive';
        document.getElementById('directiveSnippet').textContent = d.content || '';
        document.getElementById('directiveDate').textContent = d.createdAt ? new Date(d.createdAt).toLocaleDateString() : 'Today';
        banner.classList.remove('hidden');

        // Automatically mark seen by this School Admin
        markAnnouncementSeen(d.id);
      } else {
        banner.classList.add('hidden');
      }
    };

    window.filterAnnouncementsTable = function() {
      renderAnnouncementsFeed();
    };

    window.renderAnnouncementsFeed = function() {
      const container = document.getElementById('announcementsFeedContainer');
      if (!container) return;

      const search = (document.getElementById('announcementSearchInput')?.value || '').toLowerCase().trim();
      const audienceFilter = document.getElementById('announcementAudienceFilter')?.value || 'ALL';
      const scopeFilter = document.getElementById('announcementScopeFilter')?.value || 'ALL';

      // Base filter by active status & jurisdiction
      let list = cachedAnnouncements.filter(a => a.isActive !== false);

      if (isSchoolAdmin) {
        const sId = String(currentAdminUser?.schoolId);
        list = list.filter(a => a.schoolId === sId || a.schoolId === 'ALL' || a.authorUid === currentAdminUser?.uid);
      } else {
        if (selectedJurisdiction !== 'ALL') {
          list = list.filter(a => a.schoolId === selectedJurisdiction || a.schoolId === 'ALL');
        }
      }

      // Update KPI Cards
      const totalActive = list.length;
      const totalReplies = list.reduce((sum, a) => sum + (a.replyCount || 0), 0);

      const statActive = document.getElementById('statActiveAnnouncements');
      if (statActive) statActive.textContent = totalActive;
      const statReplies = document.getElementById('statTotalReplies');
      if (statReplies) statReplies.textContent = totalReplies;
      const statScope = document.getElementById('statAnnouncementsScope');
      if (statScope) {
        statScope.textContent = isSchoolAdmin ? getSchoolName(currentAdminUser?.schoolId) : (selectedJurisdiction === 'ALL' ? 'Division-Wide' : getSchoolName(selectedJurisdiction));
      }

      // Apply Filters
      if (audienceFilter !== 'ALL') {
        list = list.filter(a => Array.isArray(a.targetRoles) && a.targetRoles.includes(audienceFilter));
      }
      if (scopeFilter !== 'ALL') {
        list = list.filter(a => a.scope === scopeFilter || (scopeFilter === 'division' ? a.schoolId === 'ALL' : a.schoolId !== 'ALL'));
      }
      if (search) {
        list = list.filter(a => 
          (a.title && a.title.toLowerCase().includes(search)) ||
          (a.content && a.content.toLowerCase().includes(search)) ||
          (a.authorName && a.authorName.toLowerCase().includes(search))
        );
      }

      // Sort newest first
      list.sort((a, b) => (b.createdAt || 0) - (a.createdAt || 0));

      if (list.length === 0) {
        container.innerHTML = `
          <div class="col-span-full bg-white rounded-2xl border border-slate-200/80 p-8 text-center text-slate-400 font-medium">
            No announcements match your selected criteria. Click "+ Broadcast Announcement" to publish one.
          </div>
        `;
        return;
      }

      container.innerHTML = list.map(a => {
        const isDivision = a.schoolId === 'ALL' || a.scope === 'division';
        const scopeBadgeText = isDivision ? 'Division-Wide' : getSchoolName(a.schoolId);
        const scopeBadgeClass = isDivision ? 'bg-purple-50 text-[#6C5CE7] border-purple-200' : 'bg-slate-100 text-slate-700 border-slate-200';

        const roles = Array.isArray(a.targetRoles) ? a.targetRoles : [];
        const rolePills = roles.map(r => {
          if (r === 'student') return `<span class="px-2 py-0.5 rounded-full text-[10px] font-bold bg-blue-50 text-blue-700 border border-blue-200">Learners</span>`;
          if (r === 'teacher') return `<span class="px-2 py-0.5 rounded-full text-[10px] font-bold bg-emerald-50 text-emerald-700 border border-emerald-200">Faculty</span>`;
          if (r === 'school_admin') return `<span class="px-2 py-0.5 rounded-full text-[10px] font-bold bg-indigo-50 text-indigo-700 border border-indigo-200">School Admins</span>`;
          return '';
        }).join(' ');

        const isUrgent = a.priority === 'urgent';
        const priorityBadge = isUrgent 
          ? `<span class="px-2 py-0.5 rounded-full text-[10px] font-bold bg-rose-50 text-rose-700 border border-rose-200 flex items-center gap-1">
               <span class="w-1.5 h-1.5 rounded-full bg-rose-500 animate-pulse"></span> Urgent
             </span>`
          : '';

        const dateStr = a.createdAt ? new Date(a.createdAt).toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' }) : 'Recently';
        const seenCount = a.seenCount || (Array.isArray(a.seenUserIds) ? a.seenUserIds.length : 0);
        const replyCount = a.replyCount || 0;

        const canDelete = isSuperAdmin || (currentAdminUser && a.authorUid === currentAdminUser.uid);

        return `
          <div class="bg-white rounded-2xl border border-slate-200/80 p-5 shadow-xs hover:shadow-md transition-all flex flex-col justify-between">
            <div class="space-y-3">
              <div class="flex flex-wrap items-center justify-between gap-2">
                <div class="flex flex-wrap items-center gap-1.5">
                  <span class="px-2.5 py-0.5 rounded-full text-[10px] font-bold border ${scopeBadgeClass}">
                    ${scopeBadgeText}
                  </span>
                  ${priorityBadge}
                  ${rolePills}
                </div>
                <span class="text-[11px] font-medium text-slate-400">${dateStr}</span>
              </div>

              <div>
                <h3 class="text-sm sm:text-base font-bold text-slate-900 font-display leading-snug">${escapeHtml(a.title || 'Untitled Bulletin')}</h3>
                <p class="text-xs text-slate-600 mt-1.5 line-clamp-3 leading-relaxed whitespace-pre-line">${escapeHtml(a.content || '')}</p>
              </div>

              <div class="text-[11px] text-slate-400 flex items-center gap-1.5 pt-1">
                <span>By ${escapeHtml(a.authorName || 'Administration')}</span>
                <span>&bull;</span>
                <span class="capitalize">${(a.authorRole || 'admin').replace('_', ' ')}</span>
              </div>
            </div>

            <div class="pt-4 mt-4 border-t border-slate-100 flex flex-wrap items-center justify-between gap-2">
              <div class="flex items-center gap-2">
                <!-- Seen By Button -->
                <button onclick="openAnnouncementViews('${a.id}')" class="px-2.5 py-1.5 rounded-xl border border-slate-200 hover:border-emerald-300 hover:bg-emerald-50 text-slate-600 hover:text-emerald-700 font-bold text-[11px] transition-colors flex items-center gap-1.5" title="View who has opened this notice">
                  <svg class="w-3.5 h-3.5 text-emerald-600" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M2 12s3-7 10-7 10 7 10 7-3 7-10 7-10-7-10-7Z"/><circle cx="12" cy="12" r="3"/></svg>
                  <span>Seen by ${seenCount}</span>
                </button>

                <!-- Discreet Replies Button -->
                <button onclick="openAnnouncementReplies('${a.id}')" class="px-2.5 py-1.5 rounded-xl border border-slate-200 hover:border-purple-300 hover:bg-purple-50 text-slate-600 hover:text-[#5341CD] font-bold text-[11px] transition-colors flex items-center gap-1.5">
                  <svg class="w-3.5 h-3.5 text-[#6C5CE7]" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg>
                  <span>Discreet Replies (${replyCount})</span>
                </button>
              </div>

              ${canDelete ? `
                <button onclick="deleteAnnouncement('${a.id}')" class="p-1.5 text-slate-400 hover:text-rose-600 hover:bg-rose-50 rounded-lg transition-colors" title="Deactivate / Archive announcement">
                  <svg class="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M3 6h18"/><path d="M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6"/><path d="M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2"/></svg>
                </button>
              ` : ''}
            </div>
          </div>
        `;
      }).join('');
    };

    window.openCreateAnnouncementModal = function() {
      const select = document.getElementById('announcementSchoolSelect');
      const adminContainer = document.getElementById('targetAudienceAdminsContainer');
      const scopeNote = document.getElementById('announcementScopeNote');

      if (isSuperAdmin) {
        if (select) {
          select.innerHTML = `
            <option value="ALL">Division-wide (All Junior High Schools)</option>
            ${VALENZUELA_SCHOOLS.map(s => `<option value="${s.id}">${s.name} (${s.id})</option>`).join('')}
          `;
          select.disabled = false;
        }
        if (adminContainer) adminContainer.classList.remove('hidden');
        if (scopeNote) scopeNote.textContent = 'Super Admins can broadcast to all schools or target a specific school.';
      } else {
        const sId = currentAdminUser?.schoolId;
        const sName = getSchoolName(sId);
        if (select) {
          select.innerHTML = `<option value="${sId}">${sName} (${sId})</option>`;
          select.disabled = true;
        }
        if (adminContainer) adminContainer.classList.add('hidden');
        if (scopeNote) scopeNote.textContent = 'Announcements published by School Admins are delivered to your school learners and faculty.';
      }

      document.getElementById('announcementTitleInput').value = '';
      document.getElementById('announcementContentInput').value = '';
      document.getElementById('targetAudienceStudents').checked = true;
      document.getElementById('targetAudienceTeachers').checked = true;
      const targetAdmin = document.getElementById('targetAudienceAdmins');
      if (targetAdmin) targetAdmin.checked = false;
      document.getElementById('announcementPrioritySelect').value = 'normal';
      document.getElementById('announcementAllowReplies').checked = true;

      document.getElementById('modalCreateAnnouncement').classList.remove('hidden');
    };

    window.closeCreateAnnouncementModal = function() {
      document.getElementById('modalCreateAnnouncement').classList.add('hidden');
    };

    window.submitCreateAnnouncement = async function() {
      const title = document.getElementById('announcementTitleInput').value.trim();
      const content = document.getElementById('announcementContentInput').value.trim();
      const schoolId = document.getElementById('announcementSchoolSelect').value;
      const priority = document.getElementById('announcementPrioritySelect').value;
      const allowReplies = document.getElementById('announcementAllowReplies').checked;

      const targetRoles = [];
      if (document.getElementById('targetAudienceStudents').checked) targetRoles.push('student');
      if (document.getElementById('targetAudienceTeachers').checked) targetRoles.push('teacher');
      if (isSuperAdmin && document.getElementById('targetAudienceAdmins')?.checked) targetRoles.push('school_admin');

      if (!title) {
        window.showToast("Please provide an announcement title.", "warning");
        return;
      }
      if (!content) {
        window.showToast("Please provide the announcement message content.", "warning");
        return;
      }
      if (targetRoles.length === 0) {
        window.showToast("Please select at least one recipient audience.", "warning");
        return;
      }

      const btn = document.getElementById('btnPublishAnnouncement');
      btn.disabled = true;
      btn.innerHTML = `<span class="inline-block animate-spin mr-2">↻</span> Publishing...`;

      try {
        const authorName = `${currentAdminUser?.firstName || ''} ${currentAdminUser?.lastName || ''}`.trim() || (isSuperAdmin ? 'DepEd Division Office' : 'School Administration');
        const docData = {
          title,
          content,
          authorUid: currentAdminUser.uid,
          authorName,
          authorRole: isSuperAdmin ? 'super_admin' : 'school_admin',
          authorSchoolId: isSuperAdmin ? 'DIVISION_OFFICE' : String(currentAdminUser.schoolId),
          scope: schoolId === 'ALL' ? 'division' : 'school',
          schoolId,
          targetRoles,
          priority,
          allowReplies,
          replyCount: 0,
          seenCount: 1,
          seenUserIds: [currentAdminUser.uid],
          isActive: true,
          createdAt: Date.now()
        };

        const docRef = await addDoc(collection(db, "announcements"), docData);

        // Record author initial view receipt
        await setDoc(doc(db, "announcements", docRef.id, "views", currentAdminUser.uid), {
          uid: currentAdminUser.uid,
          userName: authorName,
          userRole: isSuperAdmin ? 'super_admin' : 'school_admin',
          schoolId: docData.authorSchoolId,
          seenAt: Date.now()
        });

        window.showToast("Announcement successfully broadcast!", "success");
        closeCreateAnnouncementModal();
      } catch (err) {
        console.error("Publish announcement error:", err);
        window.showToast("Failed to publish announcement: " + err.message, "error");
      } finally {
        btn.disabled = false;
        btn.innerHTML = `
          <svg class="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="m22 2-7 20-4-9-9-4Z"/><path d="M22 2 11 13"/></svg>
          <span>Publish Announcement</span>
        `;
      }
    };

    window.markAnnouncementSeen = async function(announcementId) {
      if (!currentAdminUser || !announcementId) return;
      try {
        const a = cachedAnnouncements.find(item => item.id === announcementId);
        if (a && Array.isArray(a.seenUserIds) && a.seenUserIds.includes(currentAdminUser.uid)) {
          return;
        }
        const authorName = `${currentAdminUser.firstName || ''} ${currentAdminUser.lastName || ''}`.trim() || (isSuperAdmin ? 'Division Admin' : 'School Admin');
        const role = isSuperAdmin ? 'super_admin' : 'school_admin';
        const sId = isSuperAdmin ? 'DIVISION_OFFICE' : String(currentAdminUser.schoolId);

        await setDoc(doc(db, "announcements", announcementId, "views", currentAdminUser.uid), {
          uid: currentAdminUser.uid,
          userName: authorName,
          userRole: role,
          schoolId: sId,
          seenAt: Date.now()
        }, { merge: true });

        const seenList = Array.isArray(a?.seenUserIds) ? [...a.seenUserIds] : [];
        if (!seenList.includes(currentAdminUser.uid)) {
          seenList.push(currentAdminUser.uid);
          await updateDoc(doc(db, "announcements", announcementId), {
            seenUserIds: seenList,
            seenCount: seenList.length
          });
        }
      } catch (err) {
        console.warn("markAnnouncementSeen error:", err);
      }
    };

    // -------------------------------------------------------------------------
    // DISCREET REPLIES DRAWER
    // -------------------------------------------------------------------------

    window.openAnnouncementReplies = function(announcementId) {
      const a = cachedAnnouncements.find(item => item.id === announcementId);
      if (!a) return;

      currentViewingAnnouncementId = announcementId;
      document.getElementById('repliesAnnouncementTitle').textContent = a.title || 'Announcement';
      document.getElementById('repliesAnnouncementSnippet').textContent = a.content || '';
      document.getElementById('repliesDateBadge').textContent = a.createdAt ? new Date(a.createdAt).toLocaleDateString() : '';
      document.getElementById('repliesScopeBadge').textContent = a.schoolId === 'ALL' ? 'Division-Wide' : getSchoolName(a.schoolId);

      const tabAdmins = document.getElementById('tabRepliesAdmins');
      if (tabAdmins) {
        if (isSuperAdmin || (Array.isArray(a.targetRoles) && a.targetRoles.includes('school_admin'))) {
          tabAdmins.classList.remove('hidden');
        } else {
          tabAdmins.classList.add('hidden');
        }
      }

      setRepliesFilter('student');
      document.getElementById('modalAnnouncementReplies').classList.remove('hidden');

      if (unsubRepliesCurrent) { unsubRepliesCurrent(); unsubRepliesCurrent = null; }
      const repliesRef = collection(db, "announcements", announcementId, "replies");
      unsubRepliesCurrent = onSnapshot(repliesRef, (snapshot) => {
        cachedRepliesForCurrent = snapshot.docs.map(d => ({ id: d.id, ...d.data() }));
        cachedRepliesForCurrent.sort((x, y) => (x.createdAt || 0) - (y.createdAt || 0));
        updateRepliesCounters();
        renderRepliesList();
      }, (err) => console.warn("Replies listener error:", err));
    };

    window.closeAnnouncementRepliesModal = function() {
      document.getElementById('modalAnnouncementReplies').classList.add('hidden');
      if (unsubRepliesCurrent) {
        unsubRepliesCurrent();
        unsubRepliesCurrent = null;
      }
      currentViewingAnnouncementId = null;
    };

    window.setRepliesFilter = function(role) {
      activeRepliesFilter = role;
      const btnStudents = document.getElementById('tabRepliesStudents');
      const btnTeachers = document.getElementById('tabRepliesTeachers');
      const btnAdmins = document.getElementById('tabRepliesAdmins');

      [btnStudents, btnTeachers, btnAdmins].forEach(b => {
        if (b) {
          b.className = "px-3 py-1.5 rounded-lg font-bold text-slate-500 hover:bg-slate-100 transition-colors";
        }
      });

      if (role === 'student' && btnStudents) {
        btnStudents.className = "px-3 py-1.5 rounded-lg font-bold bg-purple-50 text-[#5341CD] transition-colors";
      } else if (role === 'teacher' && btnTeachers) {
        btnTeachers.className = "px-3 py-1.5 rounded-lg font-bold bg-purple-50 text-[#5341CD] transition-colors";
      } else if (role === 'school_admin' && btnAdmins) {
        btnAdmins.className = "px-3 py-1.5 rounded-lg font-bold bg-purple-50 text-[#5341CD] transition-colors";
      }

      renderRepliesList();
    };

    function updateRepliesCounters() {
      const studentCount = cachedRepliesForCurrent.filter(r => r.senderRole === 'student').length;
      const teacherCount = cachedRepliesForCurrent.filter(r => r.senderRole === 'teacher').length;
      const adminCount = cachedRepliesForCurrent.filter(r => r.senderRole === 'school_admin').length;

      const cS = document.getElementById('countStudentReplies');
      if (cS) cS.textContent = studentCount;
      const cT = document.getElementById('countTeacherReplies');
      if (cT) cT.textContent = teacherCount;
      const cA = document.getElementById('countAdminReplies');
      if (cA) cA.textContent = adminCount;
    }

    window.renderRepliesList = function() {
      const container = document.getElementById('announcementRepliesContainer');
      if (!container) return;

      const announcementId = currentViewingAnnouncementId;

      if (activeRepliesFilter === 'student') {
        const studentMessages = cachedRepliesForCurrent.filter(r => r.senderRole === 'student');
        if (studentMessages.length === 0) {
          container.innerHTML = `
            <div class="text-center py-10 text-slate-400">
              <svg class="w-8 h-8 mx-auto text-slate-300 mb-2" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg>
              <p class="font-semibold">No student inquiries received yet.</p>
              <p class="text-[11px] mt-0.5">Learners can send discreet replies directly to teachers and administrators from the mobile app.</p>
            </div>
          `;
          return;
        }

        const studentUids = Array.from(new Set(studentMessages.map(m => m.senderUid)));

        container.innerHTML = studentUids.map(sUid => {
          const sReplies = cachedRepliesForCurrent.filter(r => r.senderUid === sUid);
          const studentName = sReplies[0]?.senderName || 'Student';
          const studentSchool = getSchoolName(sReplies[0]?.senderSchoolId);
          const adminAnswers = cachedRepliesForCurrent.filter(r => r.isAuthorResponse === true && r.replyToUid === sUid);
          const thread = [...sReplies, ...adminAnswers].sort((x, y) => (x.createdAt || 0) - (y.createdAt || 0));
          const inputId = `reply_input_student_${sUid}`;

          return `
            <div class="bg-white rounded-2xl border border-slate-200 p-4 shadow-xs space-y-3">
              <div class="flex items-center justify-between pb-2 border-b border-slate-100">
                <div class="flex items-center gap-2">
                  <div class="w-7 h-7 rounded-full bg-blue-100 text-blue-700 font-bold flex items-center justify-center text-xs">
                    ${studentName.charAt(0)}
                  </div>
                  <div>
                    <strong class="text-slate-900">${escapeHtml(studentName)}</strong>
                    <span class="text-[10px] text-slate-400 ml-1.5">${escapeHtml(studentSchool)}</span>
                  </div>
                </div>
                <span class="px-2 py-0.5 rounded-full text-[9px] font-bold bg-blue-50 text-blue-700 border border-blue-200">Private Thread</span>
              </div>

              <div class="space-y-2">
                ${thread.map(msg => {
                  const isStaff = msg.isAuthorResponse || msg.senderRole === 'teacher' || msg.senderRole === 'school_admin' || msg.senderRole === 'super_admin';
                  const bubbleBg = isStaff ? 'bg-purple-50 text-[#5341CD] border border-purple-200 ml-6' : 'bg-slate-50 text-slate-800 border border-slate-200 mr-6';
                  const dateStr = msg.createdAt ? new Date(msg.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : '';

                  return `
                    <div class="p-2.5 rounded-xl text-xs ${bubbleBg}">
                      <div class="flex items-center justify-between text-[10px] font-bold opacity-75 mb-1">
                        <span>${escapeHtml(msg.senderName || (isStaff ? 'Administrator' : 'Student'))}</span>
                        <span>${dateStr}</span>
                      </div>
                      <p class="whitespace-pre-line leading-relaxed">${escapeHtml(msg.message || '')}</p>
                    </div>
                  `;
                }).join('')}
              </div>

              <div class="pt-2 flex items-center gap-2">
                <input type="text" id="${inputId}" placeholder="Send private guidance response to ${escapeHtml(studentName)}..." 
                  class="flex-1 px-3 py-1.5 text-xs bg-slate-50 border border-slate-300 rounded-xl focus:outline-none focus:ring-2 focus:ring-purple-500/20 focus:border-[#6C5CE7]"
                  onkeydown="if(event.key === 'Enter') submitAdminReply('${announcementId}', '${sUid}', '${inputId}')">
                <button onclick="submitAdminReply('${announcementId}', '${sUid}', '${inputId}')" class="px-3 py-1.5 bg-[#5341CD] hover:bg-[#6C5CE7] text-white rounded-xl text-xs font-bold transition-colors flex items-center gap-1">
                  <span>Send</span>
                  <svg class="w-3 h-3" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="22" y1="2" x2="11" y2="13"/><polygon points="22 2 15 22 11 13 2 9 22 2"/></svg>
                </button>
              </div>
            </div>
          `;
        }).join('');

      } else if (activeRepliesFilter === 'teacher') {
        const teacherMessages = cachedRepliesForCurrent.filter(r => r.senderRole === 'teacher');
        if (teacherMessages.length === 0) {
          container.innerHTML = `
            <div class="text-center py-10 text-slate-400">
              <svg class="w-8 h-8 mx-auto text-slate-300 mb-2" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M22 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/></svg>
              <p class="font-semibold">No teacher responses submitted yet.</p>
              <p class="text-[11px] mt-0.5">Faculty members can send feedback and logistical questions directly from the Educator Console.</p>
            </div>
          `;
          return;
        }

        const teacherUids = Array.from(new Set(teacherMessages.map(m => m.senderUid)));

        container.innerHTML = teacherUids.map(tUid => {
          const tReplies = cachedRepliesForCurrent.filter(r => r.senderUid === tUid);
          const teacherName = tReplies[0]?.senderName || 'Teacher';
          const teacherSchool = getSchoolName(tReplies[0]?.senderSchoolId);
          const adminAnswers = cachedRepliesForCurrent.filter(r => r.isAuthorResponse === true && r.replyToUid === tUid);
          const thread = [...tReplies, ...adminAnswers].sort((x, y) => (x.createdAt || 0) - (y.createdAt || 0));
          const inputId = `reply_input_teacher_${tUid}`;

          return `
            <div class="bg-white rounded-2xl border border-slate-200 p-4 shadow-xs space-y-3">
              <div class="flex items-center justify-between pb-2 border-b border-slate-100">
                <div class="flex items-center gap-2">
                  <div class="w-7 h-7 rounded-full bg-emerald-100 text-emerald-700 font-bold flex items-center justify-center text-xs">
                    ${teacherName.charAt(0)}
                  </div>
                  <div>
                    <strong class="text-slate-900">${escapeHtml(teacherName)}</strong>
                    <span class="text-[10px] text-slate-400 ml-1.5">${escapeHtml(teacherSchool)}</span>
                  </div>
                </div>
                <span class="px-2 py-0.5 rounded-full text-[9px] font-bold bg-emerald-50 text-emerald-700 border border-emerald-200">Faculty Inquiry</span>
              </div>

              <div class="space-y-2">
                ${thread.map(msg => {
                  const isStaff = msg.isAuthorResponse || msg.senderRole === 'super_admin' || msg.senderRole === 'school_admin';
                  const bubbleBg = isStaff ? 'bg-purple-50 text-[#5341CD] border border-purple-200 ml-6' : 'bg-slate-50 text-slate-800 border border-slate-200 mr-6';
                  const dateStr = msg.createdAt ? new Date(msg.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : '';

                  return `
                    <div class="p-2.5 rounded-xl text-xs ${bubbleBg}">
                      <div class="flex items-center justify-between text-[10px] font-bold opacity-75 mb-1">
                        <span>${escapeHtml(msg.senderName || 'Staff')}</span>
                        <span>${dateStr}</span>
                      </div>
                      <p class="whitespace-pre-line leading-relaxed">${escapeHtml(msg.message || '')}</p>
                    </div>
                  `;
                }).join('')}
              </div>

              <div class="pt-2 flex items-center gap-2">
                <input type="text" id="${inputId}" placeholder="Send response to ${escapeHtml(teacherName)}..." 
                  class="flex-1 px-3 py-1.5 text-xs bg-slate-50 border border-slate-300 rounded-xl focus:outline-none focus:ring-2 focus:ring-purple-500/20 focus:border-[#6C5CE7]"
                  onkeydown="if(event.key === 'Enter') submitAdminReply('${announcementId}', '${tUid}', '${inputId}')">
                <button onclick="submitAdminReply('${announcementId}', '${tUid}', '${inputId}')" class="px-3 py-1.5 bg-[#5341CD] hover:bg-[#6C5CE7] text-white rounded-xl text-xs font-bold transition-colors flex items-center gap-1">
                  <span>Send</span>
                  <svg class="w-3 h-3" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="22" y1="2" x2="11" y2="13"/><polygon points="22 2 15 22 11 13 2 9 22 2"/></svg>
                </button>
              </div>
            </div>
          `;
        }).join('');

      } else if (activeRepliesFilter === 'school_admin') {
        const adminMessages = cachedRepliesForCurrent.filter(r => r.senderRole === 'school_admin');
        if (adminMessages.length === 0) {
          container.innerHTML = `
            <div class="text-center py-10 text-slate-400">
              <svg class="w-8 h-8 mx-auto text-slate-300 mb-2" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg>
              <p class="font-semibold">No school administrator replies logged.</p>
              <p class="text-[11px] mt-0.5">School Admins can acknowledge or respond to Division Directives from their school overview.</p>
            </div>
          `;
          return;
        }

        const adminUids = Array.from(new Set(adminMessages.map(m => m.senderUid)));

        container.innerHTML = adminUids.map(aUid => {
          const aReplies = cachedRepliesForCurrent.filter(r => r.senderUid === aUid);
          const adminName = aReplies[0]?.senderName || 'School Admin';
          const adminSchool = getSchoolName(aReplies[0]?.senderSchoolId);
          const superAdminAnswers = cachedRepliesForCurrent.filter(r => r.isAuthorResponse === true && r.replyToUid === aUid);
          const thread = [...aReplies, ...superAdminAnswers].sort((x, y) => (x.createdAt || 0) - (y.createdAt || 0));
          const inputId = `reply_input_admin_${aUid}`;

          return `
            <div class="bg-white rounded-2xl border border-slate-200 p-4 shadow-xs space-y-3">
              <div class="flex items-center justify-between pb-2 border-b border-slate-100">
                <div class="flex items-center gap-2">
                  <div class="w-7 h-7 rounded-full bg-indigo-100 text-indigo-700 font-bold flex items-center justify-center text-xs">
                    ${adminName.charAt(0)}
                  </div>
                  <div>
                    <strong class="text-slate-900">${escapeHtml(adminName)}</strong>
                    <span class="text-[10px] text-slate-400 ml-1.5">${escapeHtml(adminSchool)}</span>
                  </div>
                </div>
                <span class="px-2 py-0.5 rounded-full text-[9px] font-bold bg-indigo-50 text-indigo-700 border border-indigo-200">School Directive Response</span>
              </div>

              <div class="space-y-2">
                ${thread.map(msg => {
                  const isSDO = msg.isAuthorResponse || msg.senderRole === 'super_admin';
                  const bubbleBg = isSDO ? 'bg-purple-50 text-[#5341CD] border border-purple-200 ml-6' : 'bg-slate-50 text-slate-800 border border-slate-200 mr-6';
                  const dateStr = msg.createdAt ? new Date(msg.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : '';

                  return `
                    <div class="p-2.5 rounded-xl text-xs ${bubbleBg}">
                      <div class="flex items-center justify-between text-[10px] font-bold opacity-75 mb-1">
                        <span>${escapeHtml(msg.senderName || 'SDO')}</span>
                        <span>${dateStr}</span>
                      </div>
                      <p class="whitespace-pre-line leading-relaxed">${escapeHtml(msg.message || '')}</p>
                    </div>
                  `;
                }).join('')}
              </div>

              <div class="pt-2 flex items-center gap-2">
                <input type="text" id="${inputId}" placeholder="Send SDO response to ${escapeHtml(adminName)}..." 
                  class="flex-1 px-3 py-1.5 text-xs bg-slate-50 border border-slate-300 rounded-xl focus:outline-none focus:ring-2 focus:ring-purple-500/20 focus:border-[#6C5CE7]"
                  onkeydown="if(event.key === 'Enter') submitAdminReply('${announcementId}', '${aUid}', '${inputId}')">
                <button onclick="submitAdminReply('${announcementId}', '${aUid}', '${inputId}')" class="px-3 py-1.5 bg-[#5341CD] hover:bg-[#6C5CE7] text-white rounded-xl text-xs font-bold transition-colors flex items-center gap-1">
                  <span>Send</span>
                  <svg class="w-3 h-3" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="22" y1="2" x2="11" y2="13"/><polygon points="22 2 15 22 11 13 2 9 22 2"/></svg>
                </button>
              </div>
            </div>
          `;
        }).join('');
      }
    };

    window.submitAdminReply = async function(announcementId, targetRecipientUid, inputElementId) {
      const input = document.getElementById(inputElementId);
      if (!input) return;
      const message = input.value.trim();
      if (!message) {
        window.showToast("Please enter a response message.", "warning");
        return;
      }

      input.disabled = true;
      try {
        const authorName = `${currentAdminUser?.firstName || ''} ${currentAdminUser?.lastName || ''}`.trim() || (isSuperAdmin ? 'DepEd Division Office' : 'School Administration');
        const role = isSuperAdmin ? 'super_admin' : 'school_admin';
        const sId = isSuperAdmin ? 'DIVISION_OFFICE' : String(currentAdminUser.schoolId);

        const replyDoc = {
          announcementId,
          senderUid: currentAdminUser.uid,
          senderName: authorName,
          senderRole: role,
          senderSchoolId: sId,
          message,
          createdAt: Date.now(),
          isAuthorResponse: true,
          isPrivate: true,
          replyToUid: targetRecipientUid || null
        };

        await addDoc(collection(db, "announcements", announcementId, "replies"), replyDoc);

        const a = cachedAnnouncements.find(item => item.id === announcementId);
        const newCount = (a?.replyCount || 0) + 1;
        await updateDoc(doc(db, "announcements", announcementId), {
          replyCount: newCount
        });

        input.value = '';
        window.showToast("Response delivered successfully.", "success");
      } catch (err) {
        console.error("submitAdminReply error:", err);
        window.showToast("Failed to send response: " + err.message, "error");
      } finally {
        input.disabled = false;
        input.focus();
      }
    };

    // -------------------------------------------------------------------------
    // SEEN BY / READ RECEIPTS ROSTER
    // -------------------------------------------------------------------------

    window.openAnnouncementViews = function(announcementId) {
      const a = cachedAnnouncements.find(item => item.id === announcementId);
      if (!a) return;

      currentViewingAnnouncementId = announcementId;
      document.getElementById('viewsAnnouncementTitle').textContent = a.title || 'Announcement';

      const filterAdmins = document.getElementById('filterViewsAdmins');
      if (filterAdmins) {
        if (isSuperAdmin || (Array.isArray(a.targetRoles) && a.targetRoles.includes('school_admin'))) {
          filterAdmins.classList.remove('hidden');
        } else {
          filterAdmins.classList.add('hidden');
        }
      }

      setViewsFilter('ALL');
      document.getElementById('modalAnnouncementViews').classList.remove('hidden');

      if (unsubViewsCurrent) { unsubViewsCurrent(); unsubViewsCurrent = null; }
      const viewsRef = collection(db, "announcements", announcementId, "views");
      unsubViewsCurrent = onSnapshot(viewsRef, (snapshot) => {
        cachedViewsForCurrent = snapshot.docs.map(d => ({ id: d.id, ...d.data() }));
        cachedViewsForCurrent.sort((x, y) => (y.seenAt || 0) - (x.seenAt || 0));
        updateViewsCounters();
        renderViewsList();
      }, (err) => console.warn("Views listener error:", err));
    };

    window.closeAnnouncementViewsModal = function() {
      document.getElementById('modalAnnouncementViews').classList.add('hidden');
      if (unsubViewsCurrent) {
        unsubViewsCurrent();
        unsubViewsCurrent = null;
      }
      currentViewingAnnouncementId = null;
    };

    window.setViewsFilter = function(role) {
      activeViewsFilter = role;
      const bAll = document.getElementById('filterViewsAll');
      const bTeachers = document.getElementById('filterViewsTeachers');
      const bStudents = document.getElementById('filterViewsStudents');
      const bAdmins = document.getElementById('filterViewsAdmins');

      [bAll, bTeachers, bStudents, bAdmins].forEach(b => {
        if (b) b.className = "px-3 py-1 rounded-lg font-bold text-slate-500 hover:bg-slate-100 transition-colors";
      });

      if (role === 'ALL' && bAll) {
        bAll.className = "px-3 py-1 rounded-lg font-bold bg-emerald-50 text-emerald-700 transition-colors";
      } else if (role === 'teacher' && bTeachers) {
        bTeachers.className = "px-3 py-1 rounded-lg font-bold bg-emerald-50 text-emerald-700 transition-colors";
      } else if (role === 'student' && bStudents) {
        bStudents.className = "px-3 py-1 rounded-lg font-bold bg-emerald-50 text-emerald-700 transition-colors";
      } else if (role === 'school_admin' && bAdmins) {
        bAdmins.className = "px-3 py-1 rounded-lg font-bold bg-emerald-50 text-emerald-700 transition-colors";
      }

      renderViewsList();
    };

    function updateViewsCounters() {
      const allCount = cachedViewsForCurrent.length;
      const teacherCount = cachedViewsForCurrent.filter(v => v.userRole === 'teacher').length;
      const studentCount = cachedViewsForCurrent.filter(v => v.userRole === 'student').length;
      const adminCount = cachedViewsForCurrent.filter(v => v.userRole === 'school_admin' || v.userRole === 'super_admin').length;

      const totalBadge = document.getElementById('viewsTotalCountBadge');
      if (totalBadge) totalBadge.textContent = `${allCount} Total Viewers`;

      const cA = document.getElementById('viewsCountAll');
      if (cA) cA.textContent = allCount;
      const cT = document.getElementById('viewsCountTeachers');
      if (cT) cT.textContent = teacherCount;
      const cS = document.getElementById('viewsCountStudents');
      if (cS) cS.textContent = studentCount;
      const cAd = document.getElementById('viewsCountAdmins');
      if (cAd) cAd.textContent = adminCount;
    }

    window.renderViewsList = function() {
      const container = document.getElementById('announcementViewsContainer');
      if (!container) return;

      let list = cachedViewsForCurrent;
      if (activeViewsFilter !== 'ALL') {
        list = list.filter(v => v.userRole === activeViewsFilter || (activeViewsFilter === 'school_admin' && v.userRole === 'super_admin'));
      }

      if (list.length === 0) {
        container.innerHTML = `
          <div class="text-center py-10 text-slate-400">
            <svg class="w-8 h-8 mx-auto text-slate-300 mb-2" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M2 12s3-7 10-7 10 7 10 7-3 7-10 7-10-7-10-7Z"/><circle cx="12" cy="12" r="3"/></svg>
            <p class="font-semibold">No view receipts recorded for this category yet.</p>
          </div>
        `;
        return;
      }

      container.innerHTML = list.map(v => {
        const name = v.userName || 'Account User';
        const role = v.userRole || 'student';
        const school = getSchoolName(v.schoolId);
        const timeStr = v.seenAt ? new Date(v.seenAt).toLocaleString() : 'Recently';

        let roleBadge = '<span class="px-2 py-0.5 rounded-full text-[9px] font-bold bg-blue-50 text-blue-700 border border-blue-200">Learner</span>';
        if (role === 'teacher') {
          roleBadge = '<span class="px-2 py-0.5 rounded-full text-[9px] font-bold bg-emerald-50 text-emerald-700 border border-emerald-200">Faculty</span>';
        } else if (role === 'school_admin' || role === 'super_admin') {
          roleBadge = '<span class="px-2 py-0.5 rounded-full text-[9px] font-bold bg-indigo-50 text-indigo-700 border border-indigo-200">Administrator</span>';
        }

        return `
          <div class="p-3 bg-white rounded-xl border border-slate-200 shadow-2xs flex items-center justify-between gap-3">
            <div class="flex items-center gap-2.5">
              <div class="w-7 h-7 rounded-full bg-slate-100 text-slate-600 font-bold flex items-center justify-center text-xs">
                ${name.charAt(0)}
              </div>
              <div>
                <div class="font-bold text-slate-900 leading-tight">${escapeHtml(name)}</div>
                <div class="text-[10px] text-slate-400 mt-0.5">${escapeHtml(school)}</div>
              </div>
            </div>
            <div class="text-right">
              ${roleBadge}
              <div class="text-[10px] text-slate-400 mt-1">${timeStr}</div>
            </div>
          </div>
        `;
      }).join('');
    };

    window.deleteAnnouncement = async function(announcementId) {
      const confirmed = await showConfirmModal({
        title: "Deactivate Announcement?",
        message: "This will remove the announcement from recipient feeds across mobile apps and admin consoles.",
        badgeIcon: "📢",
        badgeBg: "bg-amber-50 text-amber-700 border-amber-200",
        confirmText: "Deactivate Bulletin",
        isDestructive: true
      });
      if (!confirmed) return;
      try {
        await updateDoc(doc(db, "announcements", announcementId), {
          isActive: false
        });
        window.showToast("Announcement deactivated.", "info");
      } catch (err) {
        console.error("deleteAnnouncement error:", err);
        window.showToast("Failed to deactivate announcement: " + err.message, "error");
      }
    };


window.updateRepliesCounters = updateRepliesCounters;
window.updateViewsCounters = updateViewsCounters;
