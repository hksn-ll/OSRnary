    // =========================================================================
    // ADMIN PORTAL IMMERSIVE LOADING & TRANSITIONS
    // Authentically mirrors com.example.gabai.GabAiLoadingView
    // =========================================================================
    let gabAiAnimFrameId = null;
    let gabAiAnimStartTime = null;

    function startGabAiLoadingAnimation() {
      const canvas = document.getElementById('gabAiLoadingCanvas');
      if (!canvas) return;
      const ctx = canvas.getContext('2d');
      if (!ctx) return;

      if (gabAiAnimFrameId) return; // Already animating

      function buildRoundRectPath(path, x, y, w, h, r) {
        path.moveTo(x + r, y);
        path.lineTo(x + w - r, y);
        path.quadraticCurveTo(x + w, y, x + w, y + r);
        path.lineTo(x + w, y + h - r);
        path.quadraticCurveTo(x + w, y + h, x + w - r, y + h);
        path.lineTo(x + r, y + h);
        path.quadraticCurveTo(x, y + h, x, y + h - r);
        path.lineTo(x, y + r);
        path.quadraticCurveTo(x, y, x + r, y);
        path.closePath();
      }

      const squirclePath = new Path2D();
      buildRoundRectPath(squirclePath, 100, 100, 400, 400, 100);

      const innerBorderPath = new Path2D();
      buildRoundRectPath(innerBorderPath, 102, 102, 396, 396, 98);

      const sheenPath = new Path2D("M 100,200 C 100,145 145,100 200,100 L 400,100 C 455,100 500,145 500,200 C 500,240 450,268 300,268 C 150,268 100,240 100,200 Z");
      const spineCoverPath = new Path2D("M 300,358 C 267,345 212,345 164,362 L 164,254 C 212,238 267,238 300,252 C 333,238 388,238 436,254 L 436,362 C 388,345 333,345 300,358 Z");
      const leftPagePath = new Path2D("M 294,351 C 262,338 212,338 170,353 C 166,354 162,351 162,347 L 162,241 C 162,237 166,233 170,232 C 212,218 262,218 294,232 Z");
      const rightPagePath = new Path2D("M 306,351 C 338,338 388,338 430,353 C 434,354 438,351 438,347 L 438,241 C 438,237 434,233 430,232 C 388,218 338,218 306,232 Z");

      const sparkMainGlowPath = new Path2D("M 300,142 Q 300,180 338,180 Q 300,180 300,218 Q 300,180 262,180 Q 300,180 300,142 Z");
      const sparkMainCorePath = new Path2D("M 300,151 Q 300,180 329,180 Q 300,180 300,209 Q 300,180 271,180 Q 300,180 300,151 Z");
      const sparkPinkGlowPath = new Path2D("M 378,166 Q 378,182 394,182 Q 378,182 378,198 Q 378,182 362,182 Q 378,182 378,166 Z");
      const sparkPinkCorePath = new Path2D("M 378,171 Q 378,182 389,182 Q 378,182 378,193 Q 378,182 367,182 Q 378,182 378,171 Z");

      function easeInOutCubic(t) {
        return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
      }

      function drawReadingLine(x, y, w, h, r, alpha) {
        ctx.save();
        ctx.fillStyle = `rgba(83, 65, 205, ${alpha})`;
        ctx.beginPath();
        buildRoundRectPath(ctx, x, y, w, h, r);
        ctx.fill();
        ctx.restore();
      }

      const cycleDuration = 1400; // 1400ms cycle identical to GabAiLoadingView

      function renderFrame(timestamp) {
        if (!gabAiAnimStartTime) gabAiAnimStartTime = timestamp;
        const elapsed = timestamp - gabAiAnimStartTime;
        const t = (elapsed % cycleDuration) / cycleDuration;
        const u = easeInOutCubic(t);

        const omega = 2 * Math.PI * t;
        const sinOmega = Math.sin(omega);
        const cosOmega = Math.cos(omega);
        const halfOmega = Math.PI * u;
        const sinHalf = Math.sin(halfOmega);
        const cosHalf = Math.cos(halfOmega);

        ctx.clearRect(0, 0, canvas.width, canvas.height);

        const w = canvas.width;
        const h = canvas.height;
        const scale = Math.min(w, h) / 600;
        const dx = (w - 600 * scale) / 2;
        const dy = (h - 600 * scale) / 2;

        ctx.save();
        ctx.translate(dx, dy);
        ctx.scale(scale, scale);

        // 1. Squircle Drop Shadow & Body
        ctx.save();
        ctx.fillStyle = "rgba(59, 40, 181, 0.35)";
        ctx.shadowColor = "rgba(59, 40, 181, 0.5)";
        ctx.shadowBlur = 24 * scale;
        ctx.shadowOffsetY = 8;
        const shadowPath = new Path2D();
        buildRoundRectPath(shadowPath, 100, 108, 400, 400, 100);
        ctx.fill(shadowPath);
        ctx.restore();

        const sqGrad = ctx.createLinearGradient(100, 100, 500, 500);
        sqGrad.addColorStop(0.0, "#705CF6");
        sqGrad.addColorStop(0.5, "#5341CD");
        sqGrad.addColorStop(1.0, "#3B28B5");
        ctx.fillStyle = sqGrad;
        ctx.fill(squirclePath);

        // Top Gloss Sheen
        ctx.save();
        ctx.clip(squirclePath);
        const sheenAlpha = (100 + sinHalf * 25) / 255;
        const sheenGrad = ctx.createLinearGradient(300, 100, 300, 268);
        sheenGrad.addColorStop(0, `rgba(255, 255, 255, ${sheenAlpha})`);
        sheenGrad.addColorStop(0.5, `rgba(255, 255, 255, ${sheenAlpha * 0.3})`);
        sheenGrad.addColorStop(1, "rgba(255, 255, 255, 0)");
        ctx.fillStyle = sheenGrad;
        ctx.fill(sheenPath);
        ctx.restore();

        // Inner Border
        const borderGrad = ctx.createLinearGradient(102, 102, 498, 418);
        borderGrad.addColorStop(0.0, "rgba(255, 255, 255, 0.45)");
        borderGrad.addColorStop(0.4, "rgba(255, 255, 255, 0.14)");
        borderGrad.addColorStop(1.0, "rgba(255, 255, 255, 0)");
        ctx.strokeStyle = borderGrad;
        ctx.lineWidth = 4;
        ctx.stroke(innerBorderPath);

        // 2. Animated Frosted Glass Orbs
        // Rose Pink Glass Orb (Center: 440, 160 | Radius: 110)
        const pinkDriftX = sinOmega * 8;
        const pinkDriftY = -sinOmega * 7;
        const pinkScale = 1.0 + sinOmega * 0.05;

        ctx.save();
        ctx.translate(440 + pinkDriftX, 160 + pinkDriftY);
        ctx.scale(pinkScale, pinkScale);
        ctx.translate(-440, -160);

        const pinkGrad = ctx.createLinearGradient(370, 90, 510, 230);
        pinkGrad.addColorStop(0, "rgba(255, 140, 210, 0.65)");
        pinkGrad.addColorStop(1, "rgba(255, 95, 195, 0.51)");
        ctx.fillStyle = pinkGrad;
        ctx.beginPath();
        ctx.arc(440, 160, 110, 0, Math.PI * 2);
        ctx.fill();

        ctx.strokeStyle = "rgba(255, 255, 255, 0.65)";
        ctx.lineWidth = 2.5;
        ctx.stroke();

        const pinkArcShift = (sinOmega * 15) * Math.PI / 180;
        ctx.beginPath();
        ctx.arc(440, 160, 110, (195 * Math.PI / 180) + pinkArcShift, (285 * Math.PI / 180) + pinkArcShift);
        ctx.strokeStyle = "rgba(255, 255, 255, 0.84)";
        ctx.lineWidth = 3.2;
        ctx.lineCap = "round";
        ctx.stroke();
        ctx.restore();

        // Mint Teal Glass Orb (Center: 160, 440 | Radius: 110)
        const tealDriftX = -sinOmega * 8;
        const tealDriftY = sinOmega * 7;
        const tealScale = 1.0 - sinOmega * 0.05;

        ctx.save();
        ctx.translate(160 + tealDriftX, 440 + tealDriftY);
        ctx.scale(tealScale, tealScale);
        ctx.translate(-160, -440);

        const tealGrad = ctx.createLinearGradient(90, 370, 230, 510);
        tealGrad.addColorStop(0, "rgba(80, 235, 230, 0.65)");
        tealGrad.addColorStop(1, "rgba(0, 230, 195, 0.53)");
        ctx.fillStyle = tealGrad;
        ctx.beginPath();
        ctx.arc(160, 440, 110, 0, Math.PI * 2);
        ctx.fill();

        ctx.strokeStyle = "rgba(255, 255, 255, 0.65)";
        ctx.lineWidth = 2.5;
        ctx.stroke();

        const tealArcShift = (-sinOmega * 15) * Math.PI / 180;
        ctx.beginPath();
        ctx.arc(160, 440, 110, (105 * Math.PI / 180) + tealArcShift, (195 * Math.PI / 180) + tealArcShift);
        ctx.strokeStyle = "rgba(255, 255, 255, 0.84)";
        ctx.lineWidth = 3.2;
        ctx.lineCap = "round";
        ctx.stroke();
        ctx.restore();

        // 3. Base Book Cover & Shadow
        ctx.fillStyle = "rgba(30, 20, 99, 0.35)";
        ctx.beginPath();
        ctx.ellipse(300, 380, 130, 15, 0, 0, Math.PI * 2);
        ctx.fill();

        ctx.fillStyle = "rgba(46, 28, 145, 0.53)";
        ctx.fill(spineCoverPath);

        // 4. Underlying Paper Decks
        ctx.save();
        ctx.translate(-4, 3);
        ctx.fillStyle = "#C7BAFD";
        ctx.fill(leftPagePath);
        ctx.translate(2, -1.5);
        ctx.fillStyle = "#DDD6FE";
        ctx.fill(leftPagePath);
        ctx.restore();

        ctx.save();
        ctx.translate(4, 3);
        ctx.fillStyle = "#C7BAFD";
        ctx.fill(rightPagePath);
        ctx.translate(-2, -1.5);
        ctx.fillStyle = "#DDD6FE";
        ctx.fill(rightPagePath);
        ctx.restore();

        // 5. Base Stationary Wings
        const leftBaseGrad = ctx.createLinearGradient(162, 290, 294, 290);
        leftBaseGrad.addColorStop(0, "#E8E2FF");
        leftBaseGrad.addColorStop(1, "#FFFFFF");
        ctx.fillStyle = leftBaseGrad;
        ctx.fill(leftPagePath);

        drawReadingLine(190, 256, 68, 5, 2.5, 0.27);
        drawReadingLine(190, 274, 76, 5, 2.5, 0.27);
        drawReadingLine(190, 292, 54, 5, 2.5, 0.27);

        const rightBaseGrad = ctx.createLinearGradient(306, 290, 438, 290);
        rightBaseGrad.addColorStop(0, "#FFFFFF");
        rightBaseGrad.addColorStop(1, "#D9CEFF");
        ctx.fillStyle = rightBaseGrad;
        ctx.fill(rightPagePath);

        drawReadingLine(334, 256, 76, 5, 2.5, 0.27);
        drawReadingLine(334, 274, 64, 5, 2.5, 0.27);
        drawReadingLine(334, 292, 72, 5, 2.5, 0.27);

        // Spine Center Line
        ctx.beginPath();
        ctx.moveTo(300, 226);
        ctx.lineTo(300, 352);
        ctx.strokeStyle = "#5341CD";
        ctx.lineWidth = 4;
        ctx.lineCap = "round";
        ctx.stroke();

        // 6. Perfected Natural Page Turn
        const liftY = -sinHalf * 32;
        const curlAngleRad = (1.0 - u * 2.0) * (sinHalf * 0.16);

        if (sinHalf > 0.005) {
          const shadowAlpha = sinHalf * 72 / 255;
          const shadowX = 300 + cosHalf * 65;
          const shadowW = (55 * Math.abs(cosHalf) + 18) / 2;
          ctx.fillStyle = `rgba(30, 20, 99, ${shadowAlpha})`;
          ctx.beginPath();
          ctx.ellipse(shadowX, 356, shadowW, 6, 0, 0, Math.PI * 2);
          ctx.fill();
        }

        ctx.save();
        if (cosHalf >= 0) {
          // Lifting from Right (0° to 90°)
          ctx.translate(300, 289 + liftY);
          ctx.rotate(curlAngleRad);
          ctx.scale(cosHalf, 1.0 + sinHalf * 0.12);
          ctx.translate(-300, -289);

          const frontShade = 1.0 - sinHalf * 0.12;
          const turnRightGrad = ctx.createLinearGradient(306, 290, 438, 290);
          turnRightGrad.addColorStop(0, `rgb(${Math.round(255 * frontShade)}, ${Math.round(255 * frontShade)}, ${Math.round(255 * frontShade)})`);
          turnRightGrad.addColorStop(1, `rgb(${Math.round(217 * frontShade)}, ${Math.round(206 * frontShade)}, ${Math.round(255 * frontShade)})`);
          ctx.fillStyle = turnRightGrad;
          ctx.fill(rightPagePath);

          const lineAlpha = (70 * frontShade) / 255;
          drawReadingLine(334, 256, 76, 5, 2.5, lineAlpha);
          drawReadingLine(334, 274, 64, 5, 2.5, lineAlpha);
          drawReadingLine(334, 292, 72, 5, 2.5, lineAlpha);
        } else {
          // Descending to Left (90° to 180°)
          const leftScaleX = -cosHalf;
          ctx.translate(300, 289 + liftY);
          ctx.rotate(curlAngleRad);
          ctx.scale(leftScaleX, 1.0 + sinHalf * 0.12);
          ctx.translate(-300, -289);

          const backShade = 1.0 - sinHalf * 0.10;
          const turnLeftGrad = ctx.createLinearGradient(162, 290, 294, 290);
          turnLeftGrad.addColorStop(0, `rgb(${Math.round(232 * backShade)}, ${Math.round(226 * backShade)}, ${Math.round(255 * backShade)})`);
          turnLeftGrad.addColorStop(1, `rgb(${Math.round(255 * backShade)}, ${Math.round(255 * backShade)}, ${Math.round(255 * backShade)})`);
          ctx.fillStyle = turnLeftGrad;
          ctx.fill(leftPagePath);

          const lineAlpha = (70 * backShade) / 255;
          drawReadingLine(190, 256, 68, 5, 2.5, lineAlpha);
          drawReadingLine(190, 274, 76, 5, 2.5, lineAlpha);
          drawReadingLine(190, 292, 54, 5, 2.5, lineAlpha);
        }
        ctx.restore();

        // 7. AI Stars & Lights (Astronomically Fixed, Twinkle in place)
        const starScintillation = 0.50 + 0.50 * Math.abs(sinOmega);
        const pinkScintillation = 0.45 + 0.55 * Math.abs(cosOmega);
        const pipScintillation  = 0.40 + 0.60 * Math.abs(Math.sin(omega + Math.PI / 3));

        // Primary Teal Star at (300, 180)
        ctx.fillStyle = `rgba(0, 245, 212, ${starScintillation})`;
        ctx.fill(sparkMainGlowPath);
        ctx.fillStyle = `rgba(255, 255, 255, ${0.75 + 0.25 * starScintillation})`;
        ctx.fill(sparkMainCorePath);

        // Secondary Rose Spark at (378, 182)
        ctx.fillStyle = `rgba(255, 180, 230, ${Math.min(1.0, 0.9 * pinkScintillation)})`;
        ctx.fill(sparkPinkGlowPath);
        ctx.fillStyle = "rgba(255, 255, 255, 1.0)";
        ctx.fill(sparkPinkCorePath);

        // Tertiary Cyan Floating Pip at (225, 188)
        ctx.beginPath();
        ctx.arc(225, 188, 5.5, 0, Math.PI * 2);
        ctx.fillStyle = `rgba(0, 245, 212, ${pipScintillation})`;
        ctx.fill();

        ctx.beginPath();
        ctx.arc(225, 188, 3.0, 0, Math.PI * 2);
        ctx.fillStyle = "rgba(255, 255, 255, 1.0)";
        ctx.fill();

        ctx.restore(); // restore 600x600 space

        gabAiAnimFrameId = requestAnimationFrame(renderFrame);
      }

      gabAiAnimFrameId = requestAnimationFrame(renderFrame);
    }

    function stopGabAiLoadingAnimation() {
      if (gabAiAnimFrameId) {
        cancelAnimationFrame(gabAiAnimFrameId);
        gabAiAnimFrameId = null;
        gabAiAnimStartTime = null;
      }
    }

    // Auto-start animation for initial load screen if visible
    if (document.getElementById('adminLoadingScreen') && !document.getElementById('adminLoadingScreen').classList.contains('hidden')) {
      startGabAiLoadingAnimation();
    }

    window.showAdminLoader = function(statusMessage = "Authenticating administrative session...", initialProgress = 30) {
      const loader = document.getElementById('adminLoadingScreen');
      const statusText = document.getElementById('adminLoadingStatus');
      const bar = document.getElementById('adminLoadingProgressBar');
      if (!loader) return;
      
      startGabAiLoadingAnimation();
      if (statusText && statusMessage) statusText.textContent = statusMessage;
      if (bar && initialProgress !== undefined) bar.style.width = `${initialProgress}%`;
      loader.classList.remove('hidden', 'opacity-0', 'pointer-events-none');
      loader.classList.add('opacity-100');
    };

    window.updateAdminLoader = function(statusMessage, progressPercent) {
      const statusText = document.getElementById('adminLoadingStatus');
      const bar = document.getElementById('adminLoadingProgressBar');
      if (statusText && statusMessage) statusText.textContent = statusMessage;
      if (bar && progressPercent !== undefined) bar.style.width = `${progressPercent}%`;
    };

    window.hideAdminLoader = function(delayMs = 400, completionMessage = null) {
      const loader = document.getElementById('adminLoadingScreen');
      const bar = document.getElementById('adminLoadingProgressBar');
      const statusText = document.getElementById('adminLoadingStatus');
      if (!loader) return;

      // Only display completion text and advance progress bar to 100% when explicit completionMessage is provided
      if (completionMessage) {
        if (statusText) statusText.textContent = completionMessage;
        if (bar) bar.style.width = '100%';
      }

      if (delayMs <= 0) {
        loader.classList.add('opacity-0', 'pointer-events-none');
        setTimeout(() => {
          loader.classList.add('hidden');
          stopGabAiLoadingAnimation();
          // Reset default status and progress bar for subsequent appearances
          if (statusText) statusText.textContent = "Authenticating administrative session...";
          if (bar) bar.style.width = '25%';
        }, 200);
        return;
      }

      setTimeout(() => {
        loader.classList.add('opacity-0', 'pointer-events-none');
        setTimeout(() => {
          loader.classList.add('hidden');
          stopGabAiLoadingAnimation();
          // Reset default status and progress bar for subsequent appearances
          if (statusText) statusText.textContent = "Authenticating administrative session...";
          if (bar) bar.style.width = '25%';
        }, 400);
      }, delayMs);
    };


    // =========================================================================
    // AUTHENTICATION OBSERVER & STATE
    // =========================================================================
    let isAccountCreationInProgress = false;

    onAuthStateChanged(auth, async (user) => {
      if (isAccountCreationInProgress) {
        // Suppress premature logout while an account is actively being provisioned/claimed
        return;
      }
      if (user) {
        try {
          updateAdminLoader("Loading school jurisdiction & records...", 60);
          let userDoc = await getDoc(doc(db, "users", user.uid));
          // Transient propagation retry: if doc not immediately detected, wait briefly
          if (!userDoc.exists()) {
            await new Promise(r => setTimeout(r, 600));
            userDoc = await getDoc(doc(db, "users", user.uid));
          }

          if (userDoc.exists()) {
            const data = userDoc.data();
            const schoolAdmin = data.role === 'school_admin';
            const superAdmin = !schoolAdmin && (data.role === 'super_admin' || data.role === 'admin' || data.isAdmin === true || data.schoolId === 'DIVISION_OFFICE');

            if (superAdmin || schoolAdmin) {
              isSuperAdmin = superAdmin;
              isSchoolAdmin = schoolAdmin;
              const resolvedAdminSchoolId = schoolAdmin ? (window.resolveTeacherSchoolId ? window.resolveTeacherSchoolId(data) : String(data.schoolId || '').trim()) : String(data.schoolId || '').trim();
              currentAdminUser = { uid: user.uid, ...data, schoolId: resolvedAdminSchoolId || data.schoolId, email: user.email };
              
              const welcomeTitle = isSuperAdmin ? 'Division Super Admin' : `School Admin (${getSchoolName(data.schoolId)})`;
              updateAdminLoader(`Welcome, ${data.firstName || welcomeTitle}! Preparing dashboard...`, 90);
              
              showAdminDashboard();
              startRealtimeListeners();
              window.showToast(`Authenticated: Welcome, ${data.firstName || welcomeTitle}`, 'success');
              
              const readyText = isSuperAdmin ? "Super Admin Dashboard ready ✓" : "School Admin Dashboard ready ✓";
              hideAdminLoader(750, readyText);
              return;
            }
          }

          if (!isAccountCreationInProgress) {
            await signOut(auth);
            showLoginError("Access Restricted: This account does not possess Administrative authority. Web Portal is reserved for Super Admin & School Admin.");
            showAuthScreen();
            hideAdminLoader(150, "Access restricted");
          }

        } catch (err) {
          console.error("Auth error:", err);
          if (!isAccountCreationInProgress) {
            showLoginError("Authentication check error: " + err.message);
            showAuthScreen();
            hideAdminLoader(150, "Authentication error");
          }
        }
      } else {
        const wasAuthenticated = !!currentAdminUser;
        currentAdminUser = null;
        isSuperAdmin = false;
        isSchoolAdmin = false;
        stopRealtimeListeners();
        showAuthScreen();
        if (wasAuthenticated) {
          hideAdminLoader(250, "Signed out successfully ✓");
        } else {
          hideAdminLoader(150);
        }
      }
    });

    function showAuthScreen() {
      document.getElementById('authScreen').classList.remove('hidden');
      document.getElementById('mainDashboard').classList.add('hidden');
      document.getElementById('navSectionSuperAdminSettings')?.classList.add('hidden');
      window.centralizedGeminiApiKey = "";
      window.centralizedGeminiConfig = null;
    }

    function showAdminDashboard() {
      document.getElementById('authScreen').classList.add('hidden');
      document.getElementById('mainDashboard').classList.remove('hidden');
      
      const nameElem = document.getElementById('adminUserDisplayName');
      const emailElem = document.getElementById('adminUserEmail');
      const roleBadge = document.getElementById('adminRoleBadge');
      const jurisdictionSelect = document.getElementById('jurisdictionSelect');
      const btnMatrix = document.getElementById('btn-tab-school-matrix');
      const overviewBtnText = document.getElementById('btn-tab-division-dashboard');
      
      if (nameElem) nameElem.textContent = `${currentAdminUser?.firstName || ''} ${currentAdminUser?.lastName || ''}`.trim() || (isSuperAdmin ? 'Division Admin' : 'School Admin');
      if (emailElem) emailElem.textContent = currentAdminUser?.email || '';

      const quickTitle = document.getElementById('quickActionsTitle');
      const roleBanner = document.getElementById('facultyRoleBanner');
      const roleBannerIcon = document.getElementById('facultyRoleBannerIcon');
      const roleBannerTitle = document.getElementById('facultyRoleBannerTitle');
      const roleBannerDesc = document.getElementById('facultyRoleBannerDesc');
      const roleBannerTag = document.getElementById('facultyRoleJurisdictionTag');

      const navFaculty = document.getElementById('navSectionFacultyClasses');
      const btnBatch = document.getElementById('btn-tab-batch-credentials');
      const btnLib = document.getElementById('btn-tab-library-management');
      const btnQuiz = document.getElementById('btn-tab-quiz-attempts');
      const btnTimed = document.getElementById('btn-tab-timed-assessments');
      const btnWeekly = document.getElementById('btn-tab-weekly-assessments');

      if (isSchoolAdmin) {
        const schoolName = getSchoolName(currentAdminUser.schoolId);
        if (roleBadge) {
          roleBadge.textContent = `School Admin • ${schoolName}`;
          roleBadge.className = "hidden sm:inline-block bg-indigo-50 text-indigo-700 border border-indigo-200 text-[10px] px-2 py-0.5 rounded-full font-bold";
        }
        const roleHdrBadgeSchool = document.getElementById('roleHeaderBadge');
        if (roleHdrBadgeSchool) {
          roleHdrBadgeSchool.className = "hidden sm:inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-bold bg-indigo-50 text-indigo-800 border border-indigo-200 font-display";
          roleHdrBadgeSchool.innerHTML = `${uiIcon('building', 'w-3.5 h-3.5 text-indigo-700')} <span>School Authority • ${schoolName}</span>`;
        }
        ['facultySchoolFilterContainer', 'sectionSchoolFilterContainer', 'credentialSchoolFilterContainer', 'learnerSchoolFilterContainer'].forEach(id => {
          const el = document.getElementById(id);
          if (el) el.classList.add('hidden');
        });
        // Lock jurisdiction to their assigned school
        selectedJurisdiction = String(currentAdminUser.schoolId);
        if (jurisdictionSelect) {
          jurisdictionSelect.innerHTML = `<option value="${currentAdminUser.schoolId}">${schoolName} (${currentAdminUser.schoolId})</option>`;
          jurisdictionSelect.value = currentAdminUser.schoolId;
          jurisdictionSelect.disabled = true;
        }
        if (btnMatrix) btnMatrix.style.display = 'none';
        if (overviewBtnText) overviewBtnText.innerHTML = `${uiIcon('dashboard', 'w-4 h-4 shrink-0')} <span id="overviewBtnLabel">School Overview</span>`;
        if (quickTitle) quickTitle.innerHTML = `${uiIcon('shield-check', 'w-4 h-4 text-amber-500 inline')} <span>Pending School Approvals</span>`;

        if (roleBanner) roleBanner.className = "p-3.5 rounded-2xl border text-xs flex items-center justify-between gap-3 bg-indigo-50/80 border-indigo-200 text-indigo-950";
        if (roleBannerIcon) roleBannerIcon.innerHTML = uiIcon('building', 'w-5 h-5 text-indigo-700');
        if (roleBannerTitle) roleBannerTitle.textContent = `School Accreditation Authority — ${schoolName}`;
        if (roleBannerDesc) roleBannerDesc.textContent = "As School Administrator, you review and accredit teachers applying for classroom instruction at your school.";
        if (roleBannerTag) {
          roleBannerTag.textContent = "School Authority";
          roleBannerTag.className = "px-2.5 py-1 rounded-full text-[10px] font-bold uppercase tracking-wider bg-white border border-indigo-200 text-indigo-700 shadow-xs shrink-0";
        }

        // Dashboard Visibility
        document.getElementById('schoolAdminKpiRow')?.classList.remove('hidden');
        document.getElementById('superAdminKpiRow')?.classList.add('hidden');
        document.getElementById('tourTargetImmediateActions')?.classList.remove('hidden');
        document.getElementById('superAdminAssessmentPulse')?.classList.add('hidden');
        document.getElementById('divisionSpotlightSection')?.classList.add('hidden');

        // Dynamic Header for School Admin
        const mainTitle = document.getElementById('dashboardMainTitle');
        const scopeTag = document.getElementById('dashboardScopeTag');
        if (mainTitle) mainTitle.textContent = `School Reading Performance Dashboard — ${schoolName}`;
        if (scopeTag) scopeTag.textContent = `School Governance • ${schoolName}`;

        // Role-based Chart & Briefing Visibility: Hide Division-level macro bloat from School Admin
        document.getElementById('superAdminChartsRow')?.classList.add('hidden');
        document.getElementById('executivePodiumSection')?.classList.add('hidden');
        document.getElementById('briefCardDistrictParity')?.classList.add('hidden');
        document.getElementById('briefCardSchoolSection')?.classList.remove('hidden');

        // Sidebar Navigation Visibility
        if (navFaculty) navFaculty.style.display = 'block';
        if (btnBatch) btnBatch.style.display = 'flex';
        if (btnLib) btnLib.style.display = 'flex';
        if (btnQuiz) btnQuiz.style.display = 'flex';
        if (btnTimed) btnTimed.style.display = 'flex';
        if (btnWeekly) btnWeekly.style.display = 'flex';

        // Standardized Reading Tests: Scoped controls for School Admin (Deploy is SDO authority)
        const btnDeployTimed = document.getElementById('btnOpenAITimedAssessmentGenerator');
        if (btnDeployTimed) btnDeployTimed.style.display = 'none';

        const timedSchFilter = document.getElementById('timedAssessmentSchoolFilter');
        if (timedSchFilter) {
          timedSchFilter.innerHTML = `<option value="${currentAdminUser.schoolId}">${schoolName} (${currentAdminUser.schoolId})</option>`;
          timedSchFilter.value = String(currentAdminUser.schoolId);
          timedSchFilter.disabled = true;
        }

        const timedDistFilter = document.getElementById('timedAssessmentDistrictFilter');
        if (timedDistFilter) {
          const adminDistrict = getSchoolDistrict(currentAdminUser.schoolId);
          timedDistFilter.value = adminDistrict;
          timedDistFilter.disabled = true;
        }

        // Restrict Super Admin Settings from School Admin
        document.getElementById('navSectionSuperAdminSettings')?.classList.add('hidden');
        window.centralizedGeminiApiKey = "";
        window.centralizedGeminiConfig = null;
      } else {
        // Super Admin (SDO)
        if (roleBadge) {
          roleBadge.textContent = "Super Admin (SDO)";
          roleBadge.className = "hidden sm:inline-block bg-purple-50 text-[#6C5CE7] border border-purple-200 text-[10px] px-2 py-0.5 rounded-full font-bold";
        }
        const roleHdrBadgeSuper = document.getElementById('roleHeaderBadge');
        if (roleHdrBadgeSuper) {
          roleHdrBadgeSuper.className = "hidden sm:inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-bold bg-purple-50 text-[#5341CD] border border-purple-200 font-display";
          roleHdrBadgeSuper.innerHTML = `${uiIcon('shield', 'w-3.5 h-3.5 text-[#5341CD]')} <span>Super Admin (SDO Division)</span>`;
        }
        ['facultySchoolFilterContainer', 'sectionSchoolFilterContainer', 'credentialSchoolFilterContainer', 'learnerSchoolFilterContainer'].forEach(id => {
          const el = document.getElementById(id);
          if (el) el.classList.remove('hidden');
        });
        if (btnMatrix) btnMatrix.style.display = 'flex';
        if (overviewBtnText) overviewBtnText.innerHTML = `${uiIcon('dashboard', 'w-4 h-4 shrink-0')} <span id="overviewBtnLabel">Executive Overview</span>`;
        if (quickTitle) quickTitle.innerHTML = `${uiIcon('shield', 'w-4 h-4 text-amber-500 inline')} <span>Division Accreditation Status</span>`;

        if (roleBanner) roleBanner.className = "p-3.5 rounded-2xl border text-xs flex items-center justify-between gap-3 bg-purple-50/80 border-purple-200 text-purple-950";
        if (roleBannerIcon) roleBannerIcon.innerHTML = uiIcon('building', 'w-5 h-5 text-purple-700');
        if (roleBannerTitle) roleBannerTitle.textContent = "Division Oversight (Schools Division Office - SDO)";
        if (roleBannerDesc) roleBannerDesc.textContent = "Viewing division literacy performance across participating junior high schools. Teacher verification is managed directly by designated School Administrators.";
        if (roleBannerTag) {
          roleBannerTag.textContent = "Division Audit";
          roleBannerTag.className = "px-2.5 py-1 rounded-full text-[10px] font-bold uppercase tracking-wider bg-white border border-purple-200 text-purple-700 shadow-xs shrink-0";
        }

        // Dashboard Visibility
        document.getElementById('schoolAdminKpiRow')?.classList.add('hidden');
        document.getElementById('superAdminKpiRow')?.classList.remove('hidden');
        document.getElementById('tourTargetImmediateActions')?.classList.add('hidden');
        document.getElementById('superAdminAssessmentPulse')?.classList.remove('hidden');
        document.getElementById('divisionSpotlightSection')?.classList.remove('hidden');

        // Dynamic Header for Super Admin
        const mainTitle = document.getElementById('dashboardMainTitle');
        const scopeTag = document.getElementById('dashboardScopeTag');
        if (mainTitle) mainTitle.textContent = "Division Reading & Literacy Command Center (SDO Valenzuela)";
        if (scopeTag) scopeTag.textContent = "SDO Valenzuela Division Governance";

        // Role-based Chart & Briefing Visibility: Show Division-level macro charts
        document.getElementById('superAdminChartsRow')?.classList.remove('hidden');
        document.getElementById('executivePodiumSection')?.classList.remove('hidden');
        document.getElementById('briefCardDistrictParity')?.classList.remove('hidden');
        document.getElementById('briefCardSchoolSection')?.classList.add('hidden');

        // Sidebar Navigation Visibility: Super Admin focuses strictly on division macro intelligence
        if (navFaculty) navFaculty.style.display = 'none';
        if (btnBatch) btnBatch.style.display = 'flex';
        if (btnLib) btnLib.style.display = 'none';
        if (btnQuiz) btnQuiz.style.display = 'none';
        if (btnTimed) btnTimed.style.display = 'flex';
        if (btnWeekly) btnWeekly.style.display = 'none';

        const btnDeployTimedSuper = document.getElementById('btnOpenAITimedAssessmentGenerator');
        if (btnDeployTimedSuper) btnDeployTimedSuper.style.display = 'inline-flex';

        const timedDistFilterSuper = document.getElementById('timedAssessmentDistrictFilter');
        if (timedDistFilterSuper) timedDistFilterSuper.disabled = false;

        const timedSchFilterSuper = document.getElementById('timedAssessmentSchoolFilter');
        if (timedSchFilterSuper) timedSchFilterSuper.disabled = false;

        populateSchoolsDropdowns();
        if (jurisdictionSelect) {
          jurisdictionSelect.disabled = false;
          jurisdictionSelect.value = selectedJurisdiction;
        }

        // Unhide Super Admin System Settings & load centralized Gemini key
        document.getElementById('navSectionSuperAdminSettings')?.classList.remove('hidden');
        if (typeof window.loadCentralizedGeminiConfig === 'function') {
          window.loadCentralizedGeminiConfig();
        }
        if (typeof window.loadDivisionKeyConfig === 'function') {
          window.loadDivisionKeyConfig();
        }
        
      }

      renderSchoolsMatrix();
      checkFirstTimeTourPrompt();

      // Guard against browser password manager autofilling admin email into search inputs on login transition
      const clearDashboardAutofill = () => {
        const searchInputs = [
          { id: 'inputFacultySearch', btn: 'btnClearFacultySearch', reset: () => { facultySearchQuery = ''; } },
          { id: 'inputSectionSearch', btn: 'btnClearSectionSearch', reset: () => { sectionSearchQuery = ''; } },
          { id: 'inputLearnerSearch', btn: 'btnClearLearnerSearch', reset: () => { learnerSearchQuery = ''; } },
          { id: 'inputCredentialSearch', btn: 'btnClearCredentialSearch', reset: () => { credentialSearchQuery = ''; } }
        ];
        let clearedAny = false;
        searchInputs.forEach(({ id, btn, reset }) => {
          const el = document.getElementById(id);
          if (el && document.activeElement !== el && el.value) {
            el.value = '';
            reset();
            const b = document.getElementById(btn);
            if (b) b.classList.add('hidden');
            clearedAny = true;
          }
        });
        if (clearedAny) {
          renderTeachersTable();
        }
      };
      clearDashboardAutofill();
      setTimeout(clearDashboardAutofill, 150);
      setTimeout(clearDashboardAutofill, 600);
    }
    window.showAdminDashboard = showAdminDashboard;

    function showLoginError(msg) {
      const box = document.getElementById('loginAlertBox');
      box.className = "p-3 rounded-lg text-xs font-medium bg-red-50 text-red-700 border border-red-200";
      box.textContent = msg;
      box.classList.remove('hidden');
    }
    window.showLoginError = showLoginError;

    window.handleLoginSubmit = async function(e) {
      e.preventDefault();
      const email = document.getElementById('loginEmail').value.trim();
      const password = document.getElementById('loginPassword').value.trim();
      
      const btn = document.getElementById('btnLoginSubmit');
      const btnText = document.getElementById('btnLoginText');
      const spinner = document.getElementById('btnLoginSpinner');

      btn.disabled = true;
      btnText.textContent = "Verifying Credentials...";
      spinner.classList.remove('hidden');
      document.getElementById('loginAlertBox').classList.add('hidden');
      showAdminLoader("Verifying administrative credentials...", 35);

      try {
        await signInWithEmailAndPassword(auth, email, password);
      } catch (err) {
        hideAdminLoader(0);
        let msg = "Invalid administrative credentials.";
        if (err.code === 'auth/user-not-found') msg = "No administrator registered with this email. Click 'Provision Admin Key' below.";
        if (err.code === 'auth/wrong-password') msg = "Incorrect password.";
        if (err.code === 'auth/invalid-credential') msg = "Invalid login credentials. Please re-check or use Provision Admin Key.";
        showLoginError(msg);
      } finally {
        btn.disabled = false;
        btnText.textContent = "Sign In to Management Portal";
        spinner.classList.add('hidden');
      }
    };

    window.handleLogout = async function() {
      const confirmed = await showConfirmModal({
        title: "End Administrative Session?",
        message: "Are you sure you want to sign out of the GabAI School Admin Portal?",
        badgeIcon: "🚪",
        badgeBg: "bg-indigo-50 text-[#5341CD] border-indigo-200",
        confirmText: "Sign Out",
        isDestructive: false
      });
      if (confirmed) {
        showAdminLoader("Ending administrative session...", 50);
        await signOut(auth);
        window.showToast("Signed out successfully.", "info");
      }
    };

    window.toggleProvisionModal = function(show) {
      const m = document.getElementById('provisionModal');
      if (show) m.classList.remove('hidden');
      else m.classList.add('hidden');
    };

    window.handleProvisionSubmit = async function(e) {
      e.preventDefault();
      const fName = document.getElementById('provFirstName').value.trim();
      const lName = document.getElementById('provLastName').value.trim();
      const email = document.getElementById('provEmail').value.trim();
      const pass = document.getElementById('provPassword').value.trim();
      const key = document.getElementById('provMasterKey').value.trim();

      const alertBox = document.getElementById('provisionAlertBox');
      alertBox.classList.add('hidden');

      // Cryptographically verify passkey via SHA-256 hash using native Web Crypto against Firestore config or secure fallback
      const hashBuffer = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(key));
      const keyHash = Array.from(new Uint8Array(hashBuffer)).map(b => b.toString(16).padStart(2, '0')).join('');

      let authorized = false;
      try {
        const configDoc = await getDoc(doc(db, "system_config", "admin_keys"));
        if (configDoc.exists() && configDoc.data().masterKeyHash) {
          authorized = (keyHash === configDoc.data().masterKeyHash);
        } else {
          authorized = (keyHash === DIVISION_MASTER_KEY_HASH);
        }
      } catch (_) {
        authorized = (keyHash === DIVISION_MASTER_KEY_HASH);
      }

      if (!authorized) {
        document.getElementById('provMasterKey').value = '';
        alertBox.className = "p-2.5 rounded-lg text-xs font-medium bg-red-50 text-red-700 border border-red-200";
        alertBox.textContent = "Unauthorized: Invalid Division Master Password.";
        alertBox.classList.remove('hidden');
        return;
      }
      document.getElementById('provMasterKey').value = '';

      isAccountCreationInProgress = true;
      try {
        const cred = await createUserWithEmailAndPassword(auth, email, pass);
        const user = cred.user;

        const adminDocData = {
          firstName: fName,
          lastName: lName,
          email: email,
          role: "admin",
          schoolId: "DIVISION_OFFICE",
          isApproved: true,
          emailVerified: true,
          createdAt: Date.now()
        };

        await setDoc(doc(db, "users", user.uid), adminDocData);

        currentAdminUser = { uid: user.uid, ...adminDocData };
        isSuperAdmin = true;
        isSchoolAdmin = false;
        isAccountCreationInProgress = false;

        window.showToast("Division Administrator provisioned successfully!", "success");
        window.toggleProvisionModal(false);
        showAdminDashboard();
        startRealtimeListeners();

      } catch (err) {
        isAccountCreationInProgress = false;
        console.error("Provisioning error:", err);
        alertBox.className = "p-2.5 rounded-lg text-xs font-medium bg-red-50 text-red-700 border border-red-200";
        alertBox.textContent = err.message;
        alertBox.classList.remove('hidden');
      }
    };

    window.showResetPasswordModal = async function(e) {
      e.preventDefault();
      const email = prompt("Enter your official administrative email address for password reset:");
      if (email) {
        try {
          await sendPasswordResetEmail(auth, email.trim());
          window.showToast("Password reset instructions dispatched to " + email, "success");
        } catch (err) {
          window.showToast("Reset request failed: " + err.message, "error");
        }
      }
    };


window.startGabAiLoadingAnimation = startGabAiLoadingAnimation;
window.stopGabAiLoadingAnimation = stopGabAiLoadingAnimation;
window.showAuthScreen = showAuthScreen;
window.showAdminDashboard = showAdminDashboard;
window.showLoginError = showLoginError;
