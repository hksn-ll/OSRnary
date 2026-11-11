    // =========================================================================
    // 7.3 CENTRALIZED SYSTEM SETTINGS & CREDENTIALS MANAGEMENT (SUPER ADMIN)
    // =========================================================================
    window.centralizedGeminiApiKey = "";
    window.centralizedGeminiConfig = null;
    let isKeyRevealed = false;

    // Load credentials exclusively from Firestore: system_config/gemini
    window.loadCentralizedGeminiConfig = async function(isManualRefresh = false) {
      if (!isSuperAdmin) {
        window.centralizedGeminiApiKey = "";
        window.centralizedGeminiConfig = null;
        return;
      }

      // Purge any lingering legacy key from localStorage to ensure Firestore-only storage
      try {
        localStorage.removeItem('gabai_gemini_api_key');
      } catch (_) {}

      try {
        const configDoc = await getDoc(doc(db, "system_config", "gemini"));
        if (configDoc.exists() && configDoc.data().apiKey) {
          window.centralizedGeminiConfig = configDoc.data();
          window.centralizedGeminiApiKey = configDoc.data().apiKey.trim();
        } else {
          window.centralizedGeminiConfig = null;
          window.centralizedGeminiApiKey = "";
        }
      } catch (err) {
        console.warn("Could not load centralized Gemini configuration:", err);
        window.centralizedGeminiConfig = null;
        window.centralizedGeminiApiKey = "";
      }

      // Update sidebar badge
      const badge = document.getElementById('badgeSystemConfigStatus');
      if (badge) {
        if (window.centralizedGeminiApiKey) {
          badge.textContent = "Active";
          badge.className = "px-2 py-0.5 rounded-full text-[10px] font-bold bg-emerald-100 text-emerald-800 border border-emerald-200";
        } else {
          badge.textContent = "Unset";
          badge.className = "px-2 py-0.5 rounded-full text-[10px] font-bold bg-amber-100 text-amber-800 border border-amber-200";
        }
      }

      // Update Live Header Badge
      const liveBadge = document.getElementById('systemConfigLiveBadge');
      if (liveBadge) {
        if (window.centralizedGeminiApiKey) {
          liveBadge.textContent = "Active System Engine";
          liveBadge.className = "px-2.5 py-0.5 rounded-full text-[10px] font-black uppercase tracking-wider bg-emerald-400/20 text-emerald-200 border border-emerald-400/30";
        } else {
          liveBadge.textContent = "Engine Unconfigured";
          liveBadge.className = "px-2.5 py-0.5 rounded-full text-[10px] font-black uppercase tracking-wider bg-amber-400/20 text-amber-200 border border-amber-400/30";
        }
      }

      // Update Timed Assessment Generator status pill if modal is currently open or loaded
      const timedStatusPill = document.getElementById('aiTimedCentralKeyStatus');
      if (timedStatusPill) {
        if (window.centralizedGeminiApiKey) {
          timedStatusPill.innerHTML = `
            <span class="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-[11px] font-semibold bg-emerald-50 text-emerald-700 border border-emerald-200">
              <span class="w-1.5 h-1.5 rounded-full bg-emerald-500 animate-pulse"></span>
              <span>Centralized Gemini AI Engine Ready</span>
            </span>`;
        } else {
          timedStatusPill.innerHTML = `
            <span class="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-[11px] font-semibold bg-amber-50 text-amber-700 border border-amber-200">
              <span class="w-1.5 h-1.5 rounded-full bg-amber-500"></span>
              <span>Gemini Key Not Configured (Set in System Settings)</span>
            </span>`;
        }
      }

      renderSystemSettingsView();

      if (isManualRefresh && typeof window.showToast === 'function') {
        window.showToast("System credentials refreshed from Cloud Firestore.", "success");
      }
    };

    // Render the System Settings page UI
    window.renderSystemSettingsView = function() {
      if (!isSuperAdmin) return;

      const maskedPreview = document.getElementById('maskedKeyPreview');
      const pill = document.getElementById('geminiConfigStatusPill');
      const updatedLabel = document.getElementById('keyLastUpdatedLabel');

      const hasKey = Boolean(window.centralizedGeminiApiKey);

      if (pill) {
        if (hasKey) {
          pill.innerHTML = `<span class="px-3 py-1 rounded-full text-xs font-bold bg-emerald-100 text-emerald-800 flex items-center gap-1.5">
            <span class="w-2 h-2 rounded-full bg-emerald-600"></span> Configured &amp; Active
          </span>`;
        } else {
          pill.innerHTML = `<span class="px-3 py-1 rounded-full text-xs font-bold bg-amber-100 text-amber-800 flex items-center gap-1.5">
            <span class="w-2 h-2 rounded-full bg-amber-600"></span> Not Configured
          </span>`;
        }
      }

      if (maskedPreview) {
        if (!hasKey) {
          maskedPreview.textContent = "No Google Gemini API Key has been configured.";
          maskedPreview.className = "font-sans italic text-xs text-slate-400 bg-white px-3 py-2 rounded-xl border border-slate-200 flex-1 truncate";
        } else {
          maskedPreview.className = "font-mono text-xs text-slate-700 bg-white px-3 py-2 rounded-xl border border-slate-200 flex-1 truncate select-all";
          if (isKeyRevealed) {
            maskedPreview.textContent = window.centralizedGeminiApiKey;
          } else {
            const k = window.centralizedGeminiApiKey;
            maskedPreview.textContent = k.length > 10 
              ? `${k.substring(0, 7)}••••••••••••••••••••${k.slice(-4)}` 
              : "••••••••••••••••••••";
          }
        }
      }

      if (updatedLabel) {
        if (window.centralizedGeminiConfig?.updatedAt) {
          const date = new Date(window.centralizedGeminiConfig.updatedAt);
          const formatted = date.toLocaleString('en-PH', { 
            dateStyle: 'medium', 
            timeStyle: 'short', 
            timeZone: 'Asia/Manila' 
          });
          const by = window.centralizedGeminiConfig.updatedBy ? `by ${window.centralizedGeminiConfig.updatedBy}` : '';
          updatedLabel.textContent = `Last synchronized: ${formatted} ${by}`;
        } else if (hasKey) {
          updatedLabel.textContent = "Configured in Firestore";
        } else {
          updatedLabel.textContent = "Awaiting initial configuration";
        }
      }
    };

    // Toggle reveal of the stored key
    window.toggleSystemApiKeyVisibility = function() {
      if (!window.centralizedGeminiApiKey) {
        if (typeof window.showToast === 'function') {
          window.showToast("No API Key is currently configured to display.", "info");
        }
        return;
      }
      isKeyRevealed = !isKeyRevealed;
      const eyeOpen = document.getElementById('eyeIconOpen');
      const eyeClosed = document.getElementById('eyeIconClosed');
      const text = document.getElementById('eyeBtnText');

      if (eyeOpen && eyeClosed && text) {
        if (isKeyRevealed) {
          eyeOpen.classList.add('hidden');
          eyeClosed.classList.remove('hidden');
          text.textContent = "Mask Key";
        } else {
          eyeOpen.classList.remove('hidden');
          eyeClosed.classList.add('hidden');
          text.textContent = "Show Key";
        }
      }
      renderSystemSettingsView();
    };

    // Test connection with Google Gemini
    window.testGeminiApiConnection = async function() {
      const inputVal = document.getElementById('inputCentralGeminiKey')?.value.trim();
      const testKey = inputVal || window.centralizedGeminiApiKey;

      const feedbackBox = document.getElementById('testConnectionFeedbackBox');
      const btn = document.getElementById('btnTestGeminiKey');

      if (!testKey) {
        window.showToast("Please enter an API Key or ensure one is configured before testing.", "warning");
        document.getElementById('inputCentralGeminiKey')?.focus();
        return;
      }

      if (btn) {
        btn.disabled = true;
        btn.innerHTML = `<span class="animate-spin mr-1.5">&#9203;</span> Testing API...`;
      }

      if (feedbackBox) {
        feedbackBox.className = "p-3.5 rounded-2xl text-xs font-medium border bg-blue-50 border-blue-200 text-blue-900";
        feedbackBox.innerHTML = `<div class="flex items-center gap-2">
          <span class="animate-spin text-blue-600">&#9203;</span>
          <span>Contacting Google Gemini (<code>gemini-3.5-flash-lite</code>)...</span>
        </div>`;
        feedbackBox.classList.remove('hidden');
      }

      const startTime = performance.now();
      try {
        const endpoint = `https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:generateContent?key=${testKey}`;
        const response = await fetch(endpoint, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            contents: [{ parts: [{ text: "Hello" }] }]
          })
        });

        const elapsed = Math.round(performance.now() - startTime);

        if (!response.ok) {
          const errData = await response.json().catch(() => ({}));
          const errMsg = errData.error?.message || `HTTP ${response.status} ${response.statusText}`;
          throw new Error(errMsg);
        }

        const data = await response.json();
        const candidate = data.candidates?.[0]?.content?.parts?.[0]?.text;
        if (!candidate && data.promptFeedback?.blockReason) {
          throw new Error(`Content Blocked: ${data.promptFeedback.blockReason}`);
        }

        if (feedbackBox) {
          feedbackBox.className = "p-3.5 rounded-2xl text-xs font-medium border bg-emerald-50 border-emerald-200 text-emerald-950";
          feedbackBox.innerHTML = `
            <div class="flex items-start gap-2">
              <svg class="w-4 h-4 text-emerald-600 shrink-0 mt-0.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/><polyline points="22 4 12 14.01 9 11.01"/></svg>
              <div>
                <strong class="font-bold text-emerald-900">Gemini Connection Successful (${elapsed}ms)</strong>
                <p class="text-[11px] text-emerald-800 mt-0.5">Verified responsiveness with model <code>gemini-3.5-flash-lite</code>. This key is authenticated and valid for SDO assessment generation.</p>
              </div>
            </div>`;
          feedbackBox.classList.remove('hidden');
        }
        window.showToast(`Gemini API Connection Verified! (${elapsed}ms)`, "success");

      } catch (err) {
        console.error("Gemini test connection failed:", err);
        if (feedbackBox) {
          feedbackBox.className = "p-3.5 rounded-2xl text-xs font-medium border bg-rose-50 border-rose-200 text-rose-950";
          feedbackBox.innerHTML = `
            <div class="flex items-start gap-2">
              <svg class="w-4 h-4 text-rose-600 shrink-0 mt-0.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>
              <div>
                <strong class="font-bold text-rose-900">Connection Failed</strong>
                <p class="text-[11px] text-rose-800 mt-0.5">${escapeHtml(err.message)}</p>
              </div>
            </div>`;
          feedbackBox.classList.remove('hidden');
        }
        window.showToast("Gemini Connection Failed: " + err.message, "error");
      } finally {
        if (btn) {
          btn.disabled = false;
          btn.innerHTML = `<svg class="w-3.5 h-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="22 12 18 12 15 21 9 3 6 12 2 12"/></svg> <span>Test Connection</span>`;
        }
      }
    };

    // Save centralized Gemini credentials to Cloud Firestore (system_config/gemini)
    window.saveCentralizedGeminiConfig = async function() {
      if (!isSuperAdmin) {
        window.showToast("Access Restricted: Only Super Admins can commit system settings.", "error");
        return;
      }

      const inputVal = document.getElementById('inputCentralGeminiKey')?.value.trim();
      if (!inputVal) {
        window.showToast("Please enter a new Gemini API Key to save.", "warning");
        document.getElementById('inputCentralGeminiKey')?.focus();
        return;
      }

      if (inputVal.length < 15) {
        window.showToast("API Key appears to be invalid or too short.", "warning");
        return;
      }

      const btn = document.getElementById('btnSaveGeminiKey');
      if (btn) {
        btn.disabled = true;
        btn.innerHTML = `<span class="animate-spin mr-1.5">&#9203;</span> Saving to Firestore...`;
      }

      try {
        const payload = {
          apiKey: inputVal,
          model: "gemini-3.5-flash-lite",
          updatedAt: Date.now(),
          updatedBy: currentAdminUser?.email || "super_admin@gabai.internal",
          status: "active"
        };

        await setDoc(doc(db, "system_config", "gemini"), payload);

        window.centralizedGeminiConfig = payload;
        window.centralizedGeminiApiKey = inputVal;

        // Clear input field and remove any lingering localStorage
        document.getElementById('inputCentralGeminiKey').value = "";
        try { localStorage.removeItem('gabai_gemini_api_key'); } catch (_) {}

        // Hide test connection feedback box if open
        const feedbackBox = document.getElementById('testConnectionFeedbackBox');
        if (feedbackBox) feedbackBox.classList.add('hidden');

        // Refresh UI
        window.loadCentralizedGeminiConfig();
        window.showToast("Centralized Gemini API Key saved to Cloud Firestore successfully!", "success");

      } catch (err) {
        console.error("Error saving Gemini configuration to Firestore:", err);
        window.showToast("Failed to save API Key to Firestore: " + err.message, "error");
      } finally {
        if (btn) {
          btn.disabled = false;
          btn.innerHTML = `<svg class="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2z"/><polyline points="17 21 17 13 7 13 7 21"/><polyline points="7 3 7 8 15 8"/></svg> <span>Save Configuration to Firestore</span>`;
        }
      }
    };

    // Remove or wipe the centralized Gemini credentials
    window.clearCentralizedGeminiConfig = async function() {
      if (!isSuperAdmin) return;

      if (!window.centralizedGeminiApiKey) {
        window.showToast("No Gemini API Key is currently configured.", "info");
        return;
      }

      const confirmed = confirm("Are you sure you want to remove the centralized Gemini API Key from Cloud Firestore? Automated timed assessment generation will be disabled until a new key is added.");
      if (!confirmed) return;

      try {
        await deleteDoc(doc(db, "system_config", "gemini"));
        window.centralizedGeminiApiKey = "";
        window.centralizedGeminiConfig = null;
        try { localStorage.removeItem('gabai_gemini_api_key'); } catch (_) {}
        
        window.loadCentralizedGeminiConfig();
        window.showToast("Centralized Gemini API Key removed from Firestore.", "info");
      } catch (err) {
        console.error("Error clearing configuration:", err);
        window.showToast("Failed to remove API key: " + err.message, "error");
      }
    };


    // =========================================================================
    // DIVISION AUTHORIZATION KEY MANAGEMENT (SUPER ADMIN EXCLUSIVE)
    // =========================================================================

    window.divisionKeyConfig = null;

    // Load division key config from Cloud Firestore (system_config/admin_keys)
    window.loadDivisionKeyConfig = async function(isManualRefresh = false) {
      if (!isSuperAdmin) {
        window.divisionKeyConfig = null;
        return;
      }

      try {
        const configDoc = await getDoc(doc(db, "system_config", "admin_keys"));
        if (configDoc.exists() && configDoc.data().masterKeyHash) {
          window.divisionKeyConfig = configDoc.data();
        } else {
          window.divisionKeyConfig = null;
        }
      } catch (err) {
        console.warn("Could not load division key configuration from Firestore:", err);
        window.divisionKeyConfig = null;
      }

      if (typeof window.renderDivisionKeySettingsView === 'function') {
        window.renderDivisionKeySettingsView();
      }

      if (isManualRefresh && typeof window.showToast === 'function') {
        window.showToast("Division authorization settings refreshed from Cloud Firestore.", "success");
      }
    };

    // Render Division Authorization Key settings UI
    window.renderDivisionKeySettingsView = function() {
      if (!isSuperAdmin) return;

      const pill = document.getElementById('divisionKeyStatusPill');
      const updatedLabel = document.getElementById('divisionKeyLastUpdatedLabel');
      const maskedPreview = document.getElementById('maskedDivisionKeyPreview');
      const btnReset = document.getElementById('btnResetDivisionKey');
      const sideStatus = document.getElementById('divisionKeySideStatus');

      const isCustom = Boolean(window.divisionKeyConfig?.masterKeyHash);

      if (pill) {
        if (isCustom) {
          pill.innerHTML = `<span class="px-3 py-1 rounded-full text-xs font-bold bg-emerald-100 text-emerald-800 flex items-center gap-1.5">
            <span class="w-2 h-2 rounded-full bg-emerald-600"></span> Custom Key Active
          </span>`;
        } else {
          pill.innerHTML = `<span class="px-3 py-1 rounded-full text-xs font-bold bg-purple-100 text-purple-800 flex items-center gap-1.5">
            <span class="w-2 h-2 rounded-full bg-[#5341CD]"></span> SDO Default Key Active
          </span>`;
        }
      }

      if (sideStatus) {
        if (isCustom) {
          sideStatus.textContent = "Custom Key Active";
          sideStatus.className = "font-bold text-emerald-700";
        } else {
          sideStatus.textContent = "SDO Default Active";
          sideStatus.className = "font-bold text-[#5341CD]";
        }
      }

      if (maskedPreview) {
        if (isCustom) {
          const h = window.divisionKeyConfig.masterKeyHash;
          maskedPreview.textContent = `SHA-256: ${h.substring(0, 10)}••••••••••••••••••••${h.slice(-6)}`;
          maskedPreview.className = "font-mono text-xs text-slate-700 bg-white px-3 py-2 rounded-xl border border-slate-200 flex-1 truncate select-all";
        } else {
          const h = DIVISION_MASTER_KEY_HASH;
          maskedPreview.textContent = `SHA-256: ${h.substring(0, 10)}••••••••••••••••••••${h.slice(-6)} (SDO Default)`;
          maskedPreview.className = "font-mono text-xs text-slate-600 bg-white px-3 py-2 rounded-xl border border-slate-200 flex-1 truncate";
        }
      }

      if (updatedLabel) {
        if (window.divisionKeyConfig?.updatedAt) {
          const date = new Date(window.divisionKeyConfig.updatedAt);
          const formatted = date.toLocaleString('en-PH', { 
            dateStyle: 'medium', 
            timeStyle: 'short', 
            timeZone: 'Asia/Manila' 
          });
          const by = window.divisionKeyConfig.updatedBy ? `by ${window.divisionKeyConfig.updatedBy}` : '';
          updatedLabel.textContent = `Last synchronized: ${formatted} ${by}`;
        } else {
          updatedLabel.textContent = "SDO Valenzuela IT factory default";
        }
      }

      if (btnReset) {
        if (isCustom) {
          btnReset.disabled = false;
          btnReset.classList.remove('opacity-50', 'cursor-not-allowed');
          btnReset.title = "Revert to SDO factory default authorization key";
        } else {
          btnReset.disabled = true;
          btnReset.classList.add('opacity-50', 'cursor-not-allowed');
          btnReset.title = "Already using factory default authorization key";
        }
      }
    };

    // Toggle password input visibility helper
    window.toggleDivisionPasswordVisibility = function(inputId, iconOpenId, iconClosedId) {
      const input = document.getElementById(inputId);
      const iconOpen = document.getElementById(iconOpenId);
      const iconClosed = document.getElementById(iconClosedId);
      if (!input) return;
      if (input.type === 'password') {
        input.type = 'text';
        if (iconOpen) iconOpen.classList.add('hidden');
        if (iconClosed) iconClosed.classList.remove('hidden');
      } else {
        input.type = 'password';
        if (iconOpen) iconOpen.classList.remove('hidden');
        if (iconClosed) iconClosed.classList.add('hidden');
      }
    };

    // Handle form submission to update Division Authorization Key
    window.handleChangeDivisionKey = async function(e) {
      e.preventDefault();
      if (!isSuperAdmin) {
        window.showToast("Access Restricted: Only Super Admins can update the Division Authorization Key.", "error");
        return;
      }

      const currKey = document.getElementById('inputCurrentDivisionKey')?.value.trim();
      const newKey = document.getElementById('inputNewDivisionKey')?.value.trim();
      const confirmKey = document.getElementById('inputConfirmDivisionKey')?.value.trim();
      const feedbackBox = document.getElementById('divisionKeyFeedbackBox');
      const btnSave = document.getElementById('btnSaveDivisionKey');

      if (feedbackBox) feedbackBox.classList.add('hidden');

      if (!currKey) {
        window.showToast("Please enter your current Division Authorization Key to authorize this change.", "warning");
        document.getElementById('inputCurrentDivisionKey')?.focus();
        return;
      }

      if (!newKey || newKey.length < 6) {
        window.showToast("The new Division Authorization Key must be at least 6 characters long.", "warning");
        document.getElementById('inputNewDivisionKey')?.focus();
        return;
      }

      if (newKey !== confirmKey) {
        if (feedbackBox) {
          feedbackBox.className = "p-3.5 rounded-2xl text-xs font-medium border bg-amber-50 border-amber-200 text-amber-950";
          feedbackBox.innerHTML = `
            <div class="flex items-start gap-2">
              <svg class="w-4 h-4 text-amber-600 shrink-0 mt-0.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3Z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>
              <div>
                <strong class="font-bold text-amber-900">Key Confirmation Mismatch</strong>
                <p class="text-[11px] text-amber-800 mt-0.5">The new key and confirmation key do not match. Please verify and re-type.</p>
              </div>
            </div>`;
          feedbackBox.classList.remove('hidden');
        }
        window.showToast("New keys do not match.", "warning");
        document.getElementById('inputConfirmDivisionKey')?.focus();
        return;
      }

      if (newKey === currKey) {
        window.showToast("The new key must be different from the current authorization key.", "warning");
        document.getElementById('inputNewDivisionKey')?.focus();
        return;
      }

      if (btnSave) {
        btnSave.disabled = true;
        btnSave.innerHTML = `<span class="animate-spin mr-1.5">&#9203;</span> Verifying &amp; Updating...`;
      }

      try {
        // Step 1: Verify current key hash against active hash
        const currHashBuffer = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(currKey));
        const currKeyHash = Array.from(new Uint8Array(currHashBuffer)).map(b => b.toString(16).padStart(2, '0')).join('');

        let activeKeyHash = DIVISION_MASTER_KEY_HASH;
        try {
          const configDoc = await getDoc(doc(db, "system_config", "admin_keys"));
          if (configDoc.exists() && configDoc.data().masterKeyHash) {
            activeKeyHash = configDoc.data().masterKeyHash;
          }
        } catch (_) {}

        if (currKeyHash !== activeKeyHash) {
          if (feedbackBox) {
            feedbackBox.className = "p-3.5 rounded-2xl text-xs font-medium border bg-rose-50 border-rose-200 text-rose-950";
            feedbackBox.innerHTML = `
              <div class="flex items-start gap-2">
                <svg class="w-4 h-4 text-rose-600 shrink-0 mt-0.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>
                <div>
                  <strong class="font-bold text-rose-900">Authorization Verification Failed</strong>
                  <p class="text-[11px] text-rose-800 mt-0.5">The current Division Authorization Key you entered is incorrect. Unauthorized to commit changes.</p>
                </div>
              </div>`;
            feedbackBox.classList.remove('hidden');
          }
          window.showToast("Current Division Authorization Key is incorrect.", "error");
          document.getElementById('inputCurrentDivisionKey')?.focus();
          return;
        }

        // Step 2: Cryptographically hash the new key
        const newHashBuffer = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(newKey));
        const newKeyHash = Array.from(new Uint8Array(newHashBuffer)).map(b => b.toString(16).padStart(2, '0')).join('');

        // Step 3: Commit payload to Firestore
        const payload = {
          masterKeyHash: newKeyHash,
          updatedAt: Date.now(),
          updatedBy: currentAdminUser?.email || "super_admin@deped.gov.ph",
          status: "active"
        };

        await setDoc(doc(db, "system_config", "admin_keys"), payload);
        window.divisionKeyConfig = payload;

        // Reset form inputs
        if (document.getElementById('inputCurrentDivisionKey')) document.getElementById('inputCurrentDivisionKey').value = '';
        if (document.getElementById('inputNewDivisionKey')) document.getElementById('inputNewDivisionKey').value = '';
        if (document.getElementById('inputConfirmDivisionKey')) document.getElementById('inputConfirmDivisionKey').value = '';

        if (feedbackBox) {
          feedbackBox.className = "p-3.5 rounded-2xl text-xs font-medium border bg-emerald-50 border-emerald-200 text-emerald-950";
          feedbackBox.innerHTML = `
            <div class="flex items-start gap-2">
              <svg class="w-4 h-4 text-emerald-600 shrink-0 mt-0.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/><polyline points="22 4 12 14.01 9 11.01"/></svg>
              <div>
                <strong class="font-bold text-emerald-900">Division Authorization Key Updated Successfully!</strong>
                <p class="text-[11px] text-emerald-800 mt-0.5">The new SHA-256 cryptographic hash has been committed to Cloud Firestore (<code class="font-mono">system_config/admin_keys</code>). All future Division Super Admin provisioning will require this new key.</p>
              </div>
            </div>`;
          feedbackBox.classList.remove('hidden');
        }

        window.renderDivisionKeySettingsView();
        window.showToast("Division Authorization Key updated successfully!", "success");

      } catch (err) {
        console.error("Error updating Division Authorization Key:", err);
        if (feedbackBox) {
          feedbackBox.className = "p-3.5 rounded-2xl text-xs font-medium border bg-rose-50 border-rose-200 text-rose-950";
          feedbackBox.innerHTML = `
            <div class="flex items-start gap-2">
              <svg class="w-4 h-4 text-rose-600 shrink-0 mt-0.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>
              <div>
                <strong class="font-bold text-rose-900">Failed to Update Key</strong>
                <p class="text-[11px] text-rose-800 mt-0.5">${escapeHtml(err.message)}</p>
              </div>
            </div>`;
          feedbackBox.classList.remove('hidden');
        }
        window.showToast("Failed to update Division Authorization Key: " + err.message, "error");
      } finally {
        if (btnSave) {
          btnSave.disabled = false;
          btnSave.innerHTML = `<svg class="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2z"/><polyline points="17 21 17 13 7 13 7 21"/><polyline points="7 3 7 8 15 8"/></svg> <span>Update Authorization Key</span>`;
        }
      }
    };

    // Revert Division Authorization Key to default factory hash
    window.resetDivisionAuthorizationKey = async function() {
      if (!isSuperAdmin) {
        window.showToast("Access Restricted: Only Super Admins can reset the key.", "error");
        return;
      }

      if (!window.divisionKeyConfig?.masterKeyHash) {
        window.showToast("The system is already using the SDO factory default authorization key.", "info");
        return;
      }

      const confirmed = confirm("Are you sure you want to reset the Division Authorization Key back to the SDO factory default? Any custom key will be removed and the factory key will be reinstated.");
      if (!confirmed) return;

      try {
        await deleteDoc(doc(db, "system_config", "admin_keys"));
        window.divisionKeyConfig = null;

        // Reset form inputs if any
        if (document.getElementById('inputCurrentDivisionKey')) document.getElementById('inputCurrentDivisionKey').value = '';
        if (document.getElementById('inputNewDivisionKey')) document.getElementById('inputNewDivisionKey').value = '';
        if (document.getElementById('inputConfirmDivisionKey')) document.getElementById('inputConfirmDivisionKey').value = '';
        const feedbackBox = document.getElementById('divisionKeyFeedbackBox');
        if (feedbackBox) feedbackBox.classList.add('hidden');

        window.renderDivisionKeySettingsView();
        window.showToast("Division Authorization Key has been reset to SDO factory default.", "info");
      } catch (err) {
        console.error("Error resetting Division Authorization Key:", err);
        window.showToast("Failed to reset key: " + err.message, "error");
      }
    };


    // Unified helper to refresh all credentials and settings
    window.refreshAllSystemSettings = async function() {
      if (typeof window.loadCentralizedGeminiConfig === 'function') {
        await window.loadCentralizedGeminiConfig(false);
      }
      if (typeof window.loadDivisionKeyConfig === 'function') {
        await window.loadDivisionKeyConfig(false);
      }
      if (typeof window.showToast === 'function') {
        window.showToast("System credentials refreshed from Cloud Firestore.", "success");
      }
    };



