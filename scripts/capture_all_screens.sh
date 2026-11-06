#!/usr/bin/env bash
set -e

echo "=== Android Emulator Automated Screenshot Capture ==="

echo "Waiting for emulator boot..."
adb wait-for-device
while [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" != "1" ]; do
  sleep 2
done
echo "Device boot completed!"

# Disable animations and configure screen
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
adb shell settings put system screen_off_timeout 600000

# Install APK
APK_FILE=$(find app/build/outputs/apk/debug -name "*.apk" | head -n 1)
echo "Installing APK: $APK_FILE"
adb install -r -g "$APK_FILE"

# Grant standard runtime permissions
adb shell pm grant com.example.gabai android.permission.CAMERA || true
adb shell pm grant com.example.gabai android.permission.POST_NOTIFICATIONS || true

# Prepare Shared Preferences with mock student data
adb root || true
sleep 1

cat << 'EOF' > /tmp/GabAI_Prefs.xml
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <string name="cached_first_name">Alex</string>
    <string name="cached_last_name">Santos</string>
    <string name="cached_full_name">Alex Santos</string>
    <string name="cached_user_role">student</string>
    <string name="cached_user_gender">male</string>
    <string name="cached_school_id">305411</string>
    <string name="cached_school_name">Valenzuela National High School</string>
    <string name="cached_section">Grade 7 - Diamond</string>
    <boolean name="has_seen_student_intro" value="true" />
    <int name="user_xp" value="1250" />
    <int name="user_level" value="5" />
    <int name="daily_streak" value="7" />
    <boolean name="is_onboarded" value="true" />
</map>
EOF

adb shell "mkdir -p /data/data/com.example.gabai/shared_prefs" || true
adb push /tmp/GabAI_Prefs.xml /data/data/com.example.gabai/shared_prefs/GabAI_Prefs.xml || true
adb shell "chmod 660 /data/data/com.example.gabai/shared_prefs/GabAI_Prefs.xml" || true

mkdir -p manual_assets/screenshots/mobile

capture_screen() {
  local filename="$1"
  local delay="${2:-3}"
  echo "Sleeping ${delay}s before capturing ${filename}..."
  sleep "$delay"
  adb exec-out screencap -p > "manual_assets/screenshots/mobile/${filename}"
  echo "Captured: manual_assets/screenshots/mobile/${filename} ($(wc -c < manual_assets/screenshots/mobile/${filename}) bytes)"
}

# 1. Student Home Screen (Figure 57)
echo "--- Launching Student Home Screen ---"
adb shell am force-stop com.example.gabai
adb shell am start -W -n com.example.gabai/.MainActivity --ez PREVIEW_MODE true --es USER_ROLE student --ez SKIP_INTRO true
capture_screen "fig_student_home.png" 4

# 2. Student Profile Screen (Figure 77)
echo "--- Launching Student Profile Screen ---"
adb shell am force-stop com.example.gabai
adb shell am start -W -n com.example.gabai/.MainActivity --ez PREVIEW_MODE true --es USER_ROLE student --es TAB profile --ez SKIP_INTRO true
capture_screen "fig_student_profile.png" 3

# 3. Teacher Home Screen (Figure 34)
echo "--- Launching Teacher Home Screen ---"
cat << 'EOF' > /tmp/GabAI_Teacher_Prefs.xml
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <string name="cached_first_name">Maria</string>
    <string name="cached_last_name">Santos</string>
    <string name="cached_full_name">Maria Santos</string>
    <string name="cached_user_role">teacher</string>
    <string name="cached_school_id">305411</string>
    <string name="cached_school_name">Valenzuela National High School</string>
    <boolean name="is_onboarded" value="true" />
</map>
EOF
adb push /tmp/GabAI_Teacher_Prefs.xml /data/data/com.example.gabai/shared_prefs/GabAI_Prefs.xml || true
adb shell "chmod 660 /data/data/com.example.gabai/shared_prefs/GabAI_Prefs.xml" || true

adb shell am force-stop com.example.gabai
adb shell am start -W -n com.example.gabai/.MainActivity --ez PREVIEW_MODE true --es USER_ROLE teacher
capture_screen "fig_teacher_home.png" 3

# 4. Teacher Profile Screen (Figure 52)
echo "--- Launching Teacher Profile Screen ---"
adb shell am force-stop com.example.gabai
adb shell am start -W -n com.example.gabai/.MainActivity --ez PREVIEW_MODE true --es USER_ROLE teacher --es TAB profile
capture_screen "fig_teacher_profile.png" 3

# 5. Class Reading Leaderboard (Figure 76)
echo "--- Launching LeaderboardActivity ---"
adb shell am force-stop com.example.gabai
adb shell am start -W -n com.example.gabai/.LeaderboardActivity
capture_screen "fig_76_leaderboard.png" 3

# 6. Achievement Badges (Figure 76)
echo "--- Launching AchievementsActivity ---"
adb shell am force-stop com.example.gabai
adb shell am start -W -n com.example.gabai/.AchievementsActivity
capture_screen "fig_achievements.png" 3

# 7. Daily Quests & Rewards Hub (Figure 72)
echo "--- Launching DailyQuestsActivity ---"
adb shell am force-stop com.example.gabai
adb shell am start -W -n com.example.gabai/.DailyQuestsActivity
capture_screen "fig_daily_quests.png" 3

# 8. Student Welcome Walkthrough Slides (Figure 56)
echo "--- Launching StudentIntroActivity ---"
adb shell am force-stop com.example.gabai
adb shell am start -W -n com.example.gabai/.StudentIntroActivity
capture_screen "fig_student_intro.png" 3

# 9. Smart Word Meaning & Pronunciation Overview (Figure 65)
echo "--- Launching OverviewActivity ---"
adb shell am force-stop com.example.gabai
adb shell am start -W -n com.example.gabai/.OverviewActivity \
  --es SELECTED_TEXT "Photosynthesis" \
  --es SURROUNDING_SENTENCE "Plants use photosynthesis to convert light energy into chemical energy." \
  --es PRELOADED_EXPLANATION "The biological process by which green plants and some organisms use sunlight to synthesize nutrients from carbon dioxide and water." \
  --es PART_OF_SPEECH "noun" \
  --es PHONETICS "/ˌfoʊ.toʊˈsɪn.θə.sɪs/" \
  --es IN_SENTENCE_ROLE "Direct Object"
capture_screen "fig_smart_word_meaning.png" 3

# 10. Help Center & Guides (Figure 78)
echo "--- Launching HelpActivity ---"
adb shell am force-stop com.example.gabai
adb shell am start -W -n com.example.gabai/.HelpActivity --es extra_user_role student
capture_screen "fig_help_center.png" 3

# 11. Class Management (Figure 36)
echo "--- Launching ManageClassesActivity ---"
adb shell am force-stop com.example.gabai
adb shell am start -W -n com.example.gabai/.ManageClassesActivity
capture_screen "fig_manage_classes.png" 3

# 12. Teacher Library (Figure 44)
echo "--- Launching TeacherLibraryActivity ---"
adb shell am force-stop com.example.gabai
adb shell am start -W -n com.example.gabai/.TeacherLibraryActivity
capture_screen "fig_teacher_library.png" 3

# 13. Student Reading Progress Dashboard (Figure 73)
echo "--- Launching ProgressDashboardActivity ---"
adb shell am force-stop com.example.gabai
adb shell am start -W -n com.example.gabai/.ProgressDashboardActivity
capture_screen "fig_progress_dashboard.png" 3

# 14. Timed Reading Assessment (Figure 61)
echo "--- Launching TimedAssessmentActivity ---"
adb shell am force-stop com.example.gabai
adb shell am start -W -n com.example.gabai/.TimedAssessmentActivity
capture_screen "fig_timed_assessment.png" 3

# 15. Weekly Classroom Quiz (Figure 62)
echo "--- Launching WeeklyAssessmentActivity ---"
adb shell am force-stop com.example.gabai
adb shell am start -W -n com.example.gabai/.WeeklyAssessmentActivity
capture_screen "fig_weekly_assessment.png" 3

# 16. Interactive Quiz Editor (Figure 48)
echo "--- Launching QuizEditorActivity ---"
adb shell am force-stop com.example.gabai
adb shell am start -W -n com.example.gabai/.QuizEditorActivity
capture_screen "fig_quiz_editor.png" 3

# 17. Class Introductory Reading Check (Figure 59)
echo "--- Launching InitiationActivity ---"
adb shell am force-stop com.example.gabai
adb shell am start -W -n com.example.gabai/.InitiationActivity
capture_screen "fig_initiation_check.png" 3

# 18. Dev Easter Egg & Logo Showcase
echo "--- Launching DevEasterEggActivity ---"
adb shell am force-stop com.example.gabai
adb shell am start -W -n com.example.gabai/.DevEasterEggActivity
capture_screen "fig_dev_easter_egg.png" 3

echo "=== All screenshots captured successfully! ==="
ls -lh manual_assets/screenshots/mobile/
