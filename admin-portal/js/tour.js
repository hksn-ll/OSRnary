    // =========================================================================
    // ROLE-AWARE ADMINISTRATIVE HELP & GUIDED TOUR SYSTEM
    // =========================================================================
    const SUPER_ADMIN_TOUR_STEPS = [
      {
        tabId: 'tab-division-dashboard',
        targetId: 'tourTargetJurisdiction',
        title: 'Division Scope & Jurisdiction',
        icon: 'building',
        description: 'You have division-wide administrative oversight across all junior high schools in Valenzuela City. Use this Jurisdiction dropdown at any time to focus the entire portal on a single school.',
        tip: 'Filtering by a specific school instantly scopes academic metrics, assessments, and learner diagnostics to that school.'
      },
      {
        tabId: 'tab-school-matrix',
        targetId: 'tourTargetSchoolMatrix',
        title: 'School Matrix & SDO Management',
        icon: 'building',
        description: 'This master matrix rosters all public junior high schools with their official DepEd IDs and Congressional Districts. As Super Admin, you can add new schools, remove schools, and issue invite codes for School Administrators.',
        tip: 'Each SDO code (e.g. SDO-305445-XXXX) is single-use and irreversibly binds the claiming administrator to their school.'
      },
      {
        tabId: 'tab-timed-assessments',
        targetId: 'tourTargetTimedAssessmentsTable',
        title: 'Division Standardized Timed Assessments',
        icon: 'clock',
        description: 'Create, schedule, deploy, and evaluate division-wide uniform timed assessments across all participating schools. SDO sets the timer duration and question pools.',
        tip: 'Assessments deployed with "ALL" scope automatically disburse to classrooms across all participating schools.'
      },
      {
        tabId: 'tab-learner-performance',
        targetId: 'tourTargetPerformanceTable',
        title: 'Core Competencies & Learner Performance',
        icon: 'chart',
        description: 'Monitor real-time literacy diagnostics, Phil-IRI Pre-Test vs Post-Test Growth Matrix (ΔMPS), and Least Mastered Competencies (EN-LIT-01 to EN-CRI-05).',
        tip: 'Filter by Congressional District (District 1 vs District 2) and Gender to pinpoint priority schools requiring academic reading interventions.'
      },
      {
        tabId: 'tab-announcements',
        targetId: 'tourTargetAnnouncementsFeed',
        title: 'Division Directives & Advisories',
        icon: 'bell',
        description: 'Broadcast official division memorandums, advisories, and administrative instructions to school principals, teachers, and scholars.',
        tip: 'Broadcasts can be sent division-wide or focused on specific school jurisdictions.'
      },
      {
        tabId: 'tab-recycle-vault',
        targetId: 'tourTargetRecycleTable',
        title: 'System Archives & Recovery Vault',
        icon: 'archive',
        description: 'Institutional-grade safety net providing 30-day recovery for archived schools and faculty with instant 1-click restoration.',
        tip: 'All soft-deleted schools remain protected in the vault before any permanent purge.'
      }
    ];

    const SCHOOL_ADMIN_TOUR_STEPS = [
      {
        tabId: 'tab-division-dashboard',
        targetId: 'adminRoleBadge',
        title: 'Assigned School Boundary',
        icon: 'building',
        description: 'Welcome to your school’s administrative portal. Your account is strictly bound to your DepEd School ID, giving you focused governance over your faculty, classrooms, and learners.',
        tip: 'Notice your school badge in the top header. Your portal metrics are pre-filtered exclusively for your school.'
      },
      {
        tabId: 'tab-division-dashboard',
        targetId: 'tourTargetImmediateActions',
        title: 'Faculty Accreditation Queue (Action Item)',
        icon: 'shield-check',
        description: 'This is your primary administrative queue: when teachers from your school register on the mobile app, they appear here. Click "Approve" to accredit them for immediate mobile app classroom access.',
        tip: 'Teachers cannot log into the mobile app until an administrator accredits their profile.'
      },
      {
        tabId: 'tab-classroom-sections',
        targetId: 'tourTargetSectionsTable',
        title: 'Classroom Sections & Join Codes',
        icon: 'tag',
        description: 'Review all active classrooms created by your faculty. Monitor class join codes, student counts, and initiation assessment results.',
        tip: 'Click any section to inspect the full student roster and diagnostic reading baselines.'
      },
      {
        tabId: 'tab-batch-credentials',
        targetId: 'tourTargetBatchVault',
        title: 'Student Credential Vault',
        icon: 'key',
        description: 'Manage student onboarding accounts. Students claim these credentials on the mobile app to access the AI reading tutor, gamified quests, and diagnostic assessments.',
        tip: 'Monitor claimed accounts and provide student login assistance directly from this vault.'
      },
      {
        tabId: 'tab-learner-performance',
        targetId: 'tourTargetPerformanceTable',
        title: 'School Diagnostic Literacy Metrics',
        icon: 'chart',
        description: 'Track real-time student quiz history, average accuracy percentages, and reading mastery trends specifically for your school.',
        tip: 'Use these indicators to support teachers whose classes show reading comprehension gaps.'
      },
      {
        tabId: 'tab-library-management',
        targetId: 'tourTargetMaterialsTable',
        title: 'Curriculum Review & Remarks Drawer',
        icon: 'message',
        description: 'Inspect instructional PDF documents and weekly reading assessments. Click "Remarks" to submit administrative notes, curriculum alignments, and commendations directly to your educators.',
        tip: 'Your remarks provide constructive oversight while preserving the educator’s original content.'
      }
    ];

    let currentTourStep = 0;
    let activeTourSteps = [];
    let tourRepositionTimer = null;

    window.openHelpHubModal = function() {
      const modal = document.getElementById('modalHelpHub');
      if (!modal) return;

      const subTitle = document.getElementById('helpHubRoleSubtitle');
      const detailsContainer = document.getElementById('helpHubRoleDetails');

      if (isSchoolAdmin) {
        const schoolName = getSchoolName(currentAdminUser?.schoolId);
        if (subTitle) subTitle.textContent = `School Admin Portal • ${schoolName} (${currentAdminUser?.schoolId || ''})`;
        if (detailsContainer) {
          detailsContainer.innerHTML = `
            <div class="flex items-center gap-2 font-bold text-slate-800">
              <span class="px-2 py-0.5 rounded bg-indigo-50 text-indigo-700 border border-indigo-200 text-[10px]">School Administrator</span>
              <span>School Principal / IT Coordinator</span>
            </div>
            <ul class="list-disc list-inside space-y-1 text-slate-600 mt-1">
              <li><strong>School Authority:</strong> Governance scoped strictly to School ID <code>${currentAdminUser?.schoolId || 'Assigned School'}</code>.</li>
              <li><strong>Faculty Accreditation:</strong> Evaluate and grant 1-click approval for newly registered teachers at your school.</li>
              <li><strong>Student Credential Vault:</strong> Manage batch-generated student accounts and onboarding claims.</li>
              <li><strong>Instructional Oversight:</strong> Review teacher modules and post constructive feedback via the <em>Remarks</em> drawer.</li>
            </ul>
          `;
        }
      } else {
        if (subTitle) subTitle.textContent = `Division Super Admin • Schools Division Office of Valenzuela City`;
        if (detailsContainer) {
          detailsContainer.innerHTML = `
            <div class="flex items-center gap-2 font-bold text-slate-800">
              <span class="px-2 py-0.5 rounded bg-purple-50 text-[#5341CD] border border-purple-200 text-[10px]">Division Level</span>
              <span>Schools Division Office (SDO) Super Admin</span>
            </div>
            <ul class="list-disc list-inside space-y-1 text-slate-600 mt-1">
              <li><strong>Division Oversight:</strong> Comprehensive monitoring across participating junior high schools in Valenzuela City.</li>
              <li><strong>SDO School Manager:</strong> Generate and issue confidential invite codes (<code>SDO-XXXX-YYYY</code>) for School Principals and direct-provision school administrators.</li>
              <li><strong>Faculty Oversight:</strong> Audit accreditation status across schools (teacher approvals are performed exclusively by school administrators).</li>
              <li><strong>Curriculum & Intervention Guidance:</strong> Monitor weekly diagnostic assessments and submit administrative remarks to educators.</li>
            </ul>
          `;
        }
      }

      modal.classList.remove('hidden');
    };

    window.closeHelpHubModal = function() {
      const modal = document.getElementById('modalHelpHub');
      if (modal) modal.classList.add('hidden');
    };

    window.startInteractiveTour = function() {
      closeHelpHubModal();
      activeTourSteps = isSchoolAdmin ? SCHOOL_ADMIN_TOUR_STEPS : SUPER_ADMIN_TOUR_STEPS;
      currentTourStep = 0;

      const overlay = document.getElementById('tutorialTourOverlay');
      if (overlay) overlay.classList.remove('hidden');

      // Attach resize and scroll listeners during active tour
      window.removeEventListener('resize', updateActiveTourSpotlight);
      window.removeEventListener('scroll', updateActiveTourSpotlight, true);
      window.addEventListener('resize', updateActiveTourSpotlight);
      window.addEventListener('scroll', updateActiveTourSpotlight, true);

      renderTourStep(0);
    };

    function updateActiveTourSpotlight() {
      if (!activeTourSteps || activeTourSteps.length === 0) return;
      const step = activeTourSteps[currentTourStep];
      if (!step) return;
      positionTourElements(step.targetId);
    }

    function positionTourElements(targetId) {
      const spotlight = document.getElementById('tourSpotlight');
      const tourCard = document.getElementById('tourCard');
      if (!tourCard) return;

      const targetElem = document.getElementById(targetId);

      if (targetElem) {
        const rect = targetElem.getBoundingClientRect();
        const pad = 10;

        // Morph spotlight frame over target
        if (spotlight) {
          spotlight.style.display = 'block';
          spotlight.style.top = `${Math.max(4, rect.top - pad)}px`;
          spotlight.style.left = `${Math.max(4, rect.left - pad)}px`;
          spotlight.style.width = `${Math.min(window.innerWidth - 8, rect.width + (pad * 2))}px`;
          spotlight.style.height = `${Math.min(window.innerHeight - 8, rect.height + (pad * 2))}px`;
        }

        // Card Dimensions
        const cardWidth = Math.min(440, window.innerWidth - 32);
        const cardHeight = tourCard.offsetHeight || 240;

        let topPos;
        let leftPos;

        // Position logic: avoid obscuring small targets, dock neatly for large targets
        const isLargeTarget = rect.height > 240 || rect.width > 600;

        if (isLargeTarget) {
          // For master tables/cards, anchor card at upper-right or lower-right
          // so it never clips past the bottom taskbar
          if (window.innerWidth > 960) {
            topPos = Math.max(90, Math.min(window.innerHeight - cardHeight - 32, rect.top + 28));
            leftPos = Math.max(20, Math.min(window.innerWidth - cardWidth - 28, rect.right - cardWidth - 24));
          } else {
            topPos = window.innerHeight - cardHeight - 24;
            leftPos = Math.max(16, (window.innerWidth - cardWidth) / 2);
          }
        } else {
          // Standard / small target (e.g. dropdown, badge, action button)
          topPos = rect.bottom + 16;
          leftPos = Math.max(16, Math.min(window.innerWidth - cardWidth - 16, rect.left));

          // If too low, place above target
          if (topPos + cardHeight > window.innerHeight - 20) {
            topPos = rect.top - cardHeight - 16;
          }
        }

        // Strict Clamping - NEVER overflow viewport, NEVER clip into taskbar
        topPos = Math.max(20, Math.min(window.innerHeight - cardHeight - 24, topPos));
        leftPos = Math.max(16, Math.min(window.innerWidth - cardWidth - 16, leftPos));

        tourCard.style.top = `${topPos}px`;
        tourCard.style.left = `${leftPos}px`;
        tourCard.style.transform = 'none';
      } else {
        // Fallback: center on screen
        if (spotlight) spotlight.style.display = 'none';
        tourCard.style.top = '50%';
        tourCard.style.left = '50%';
        tourCard.style.transform = 'translate(-50%, -50%)';
      }
    }

    function renderTourStep(index) {
      if (index < 0 || index >= activeTourSteps.length) return;

      const step = activeTourSteps[index];

      // Update Card Content
      const badge = document.getElementById('tourStepBadge');
      const roleTag = document.getElementById('tourRoleTag');
      const title = document.getElementById('tourStepTitle');
      const desc = document.getElementById('tourStepDescription');
      const tip = document.getElementById('tourStepTipText');

      if (badge) badge.textContent = `Step ${index + 1} of ${activeTourSteps.length}`;
      if (roleTag) roleTag.textContent = isSchoolAdmin ? 'School Admin Tour' : 'Super Admin Tour';
      if (title) {
        const iconSvg = ICONS[step.icon] ? uiIcon(step.icon, 'w-4 h-4 text-[#6C5CE7]') : (step.icon || '');
        title.innerHTML = `<span class="w-7 h-7 rounded-lg bg-indigo-50 text-[#6C5CE7] flex items-center justify-center shrink-0">${iconSvg}</span> <span>${step.title}</span>`;
      }
      if (desc) desc.textContent = step.description;
      if (tip) tip.textContent = step.tip;

      // Button states
      const btnPrev = document.getElementById('btnTourPrev');
      const btnNext = document.getElementById('btnTourNext');

      if (btnPrev) {
        btnPrev.disabled = index === 0;
        btnPrev.style.opacity = index === 0 ? '0.4' : '1';
        btnPrev.style.cursor = index === 0 ? 'not-allowed' : 'pointer';
      }

      if (btnNext) {
        if (index === activeTourSteps.length - 1) {
          btnNext.innerHTML = `<span class="inline-flex items-center gap-1">${uiIcon('check', 'w-3.5 h-3.5 text-white inline')} Complete Tour</span>`;
          btnNext.className = "px-4 py-1.5 bg-gradient-to-r from-emerald-600 to-teal-600 hover:opacity-95 text-white rounded-xl text-xs font-bold shadow-md font-display transition-all hover:scale-105 active:scale-95";
        } else {
          btnNext.innerHTML = `<span>Next &rarr;</span>`;
          btnNext.className = "px-4 py-1.5 bg-gradient-to-r from-[#6C5CE7] to-[#5341CD] hover:opacity-95 text-white rounded-xl text-xs font-bold shadow-md font-display transition-all hover:scale-105 active:scale-95";
        }
      }

      // Switch Tab if needed
      if (step.tabId) {
        switchTab(step.tabId);
      }

      // Smooth scroll target into view and position spotlight & card
      clearTimeout(tourRepositionTimer);
      tourRepositionTimer = setTimeout(() => {
        const targetElem = document.getElementById(step.targetId);
        if (targetElem) {
          targetElem.scrollIntoView({ behavior: 'smooth', block: 'center' });
        }
        positionTourElements(step.targetId);
        setTimeout(() => positionTourElements(step.targetId), 220);
      }, 120);
    }

    window.nextTourStep = function() {
      if (currentTourStep < activeTourSteps.length - 1) {
        currentTourStep++;
        renderTourStep(currentTourStep);
      } else {
        finishTour();
      }
    };

    window.prevTourStep = function() {
      if (currentTourStep > 0) {
        currentTourStep--;
        renderTourStep(currentTourStep);
      }
    };

    function cleanupTour() {
      clearTimeout(tourRepositionTimer);
      window.removeEventListener('resize', updateActiveTourSpotlight);
      window.removeEventListener('scroll', updateActiveTourSpotlight, true);

      const spotlight = document.getElementById('tourSpotlight');
      if (spotlight) spotlight.style.display = 'none';

      const overlay = document.getElementById('tutorialTourOverlay');
      if (overlay) overlay.classList.add('hidden');
    }

    window.finishTour = function() {
      cleanupTour();
      if (currentAdminUser?.uid) {
        localStorage.setItem(`gabai_admin_tour_seen_${currentAdminUser.uid}`, 'true');
      }
      window.showToast("Interactive tour completed! Click 'Help & Tour' anytime to revisit.", "success");
    };

    window.skipTour = function() {
      cleanupTour();
      if (currentAdminUser?.uid) {
        localStorage.setItem(`gabai_admin_tour_seen_${currentAdminUser.uid}`, 'true');
      }
    };

    window.startTourAtStep = function(stepIndex) {
      closeHelpHubModal();
      activeTourSteps = isSchoolAdmin ? SCHOOL_ADMIN_TOUR_STEPS : SUPER_ADMIN_TOUR_STEPS;
      currentTourStep = Math.max(0, Math.min(stepIndex, activeTourSteps.length - 1));
      const overlay = document.getElementById('tutorialTourOverlay');
      if (overlay) overlay.classList.remove('hidden');
      renderTourStep(currentTourStep);
    };

    window.startWelcomeTourNow = function() {
      const modal = document.getElementById('modalWelcomeTour');
      if (modal) modal.classList.add('hidden');
      window.startInteractiveTour();
    };

    window.dismissWelcomeTourPrompt = function() {
      const modal = document.getElementById('modalWelcomeTour');
      if (modal) modal.classList.add('hidden');
      if (currentAdminUser?.uid) {
        localStorage.setItem(`gabai_admin_tour_prompt_dismissed_${currentAdminUser.uid}`, 'true');
      }
    };

    function checkFirstTimeTourPrompt() {
      if (!currentAdminUser?.uid) return;
      const seen = localStorage.getItem(`gabai_admin_tour_seen_${currentAdminUser.uid}`);
      if (!seen) {
        setTimeout(() => {
          window.showToast("Welcome! Click 'Help & Tour' in the top bar for a 1-minute guided tour.", "info");
        }, 1200);
      }
    }

    // Keyboard shortcut listeners for Tour & Help Hub
    document.addEventListener('keydown', (e) => {
      const tourOverlay = document.getElementById('tutorialTourOverlay');
      const isTourOpen = tourOverlay && !tourOverlay.classList.contains('hidden');

      if (e.key === 'Escape') {
        if (isTourOpen) {
          skipTour();
        } else {
          closeHelpHubModal();
        }
      } else if (isTourOpen) {
        if (e.key === 'ArrowRight') {
          nextTourStep();
        } else if (e.key === 'ArrowLeft') {
          prevTourStep();
        }
      }
    });
  

window.updateActiveTourSpotlight = updateActiveTourSpotlight;
window.positionTourElements = positionTourElements;
window.renderTourStep = renderTourStep;
window.cleanupTour = cleanupTour;
window.checkFirstTimeTourPrompt = checkFirstTimeTourPrompt;
