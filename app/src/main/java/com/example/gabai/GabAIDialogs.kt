package com.example.gabai

import android.animation.ObjectAnimator
import android.app.Activity
import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.text.InputType
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.*
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

object GabAIDialogs {

    /**
     * Traverses ContextWrapper hierarchy to find the hosting Activity.
     */
    private fun findActivity(context: Context): Activity? {
        var ctx = context
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

    /**
     * Enforces responsive, modern card width (88% of screen, clamped between 310dp and 400dp).
     */
    fun applyDialogDimensions(dialog: Dialog) {
        val window = dialog.window ?: return
        val metrics = dialog.context.resources.displayMetrics
        val density = metrics.density
        val screenWidth = metrics.widthPixels
        val minWidth = (310 * density).toInt()
        val maxWidth = (400 * density).toInt()
        val targetWidth = (screenWidth * 0.88f).toInt()
            .coerceIn(minWidth, maxWidth)
            .coerceAtMost((screenWidth * 0.92f).toInt())

        window.setLayout(targetWidth, WindowManager.LayoutParams.WRAP_CONTENT)
        window.setGravity(Gravity.CENTER)
    }

    /**
     * Applies hardware-accelerated frosted glass backdrop blur, dimming, and window animations.
     */
    fun applyModernWindowStyles(dialog: Dialog, onDismiss: (() -> Unit)? = null) {
        val window = dialog.window ?: return
        val context = dialog.context
        window.setBackgroundDrawableResource(android.R.color.transparent)
        window.setDimAmount(0.55f)

        // Native WindowManager backdrop blur (when supported by OS & vendor, API 31+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                window.attributes = window.attributes.apply {
                    blurBehindRadius = 32
                }
            } catch (_: Exception) {}
        }

        // Direct RenderEffect blur on hosting Activity DecorView (guaranteed frosted glass on Android 12+, including Samsung One UI)
        val activity = findActivity(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && activity != null) {
            try {
                val decorView = activity.window.decorView
                decorView.setRenderEffect(RenderEffect.createBlurEffect(25f, 25f, Shader.TileMode.CLAMP))
                dialog.setOnDismissListener {
                    try {
                        decorView.setRenderEffect(null)
                    } catch (_: Exception) {}
                    onDismiss?.invoke()
                }
            } catch (_: Exception) {
                if (onDismiss != null) {
                    dialog.setOnDismissListener { onDismiss.invoke() }
                }
            }
        } else if (onDismiss != null) {
            dialog.setOnDismissListener { onDismiss.invoke() }
        }

        try {
            window.setWindowAnimations(R.style.SleekDialogAnimation)
        } catch (_: Exception) {}

        applyDialogDimensions(dialog)
    }

    /**
     * Generates a high-contrast QR code bitmap offline using ZXing.
     */
    fun generateQrBitmap(content: String, size: Int = 512): Bitmap? {
        return try {
            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
            val darkColor = Color.parseColor("#2D3436")
            val lightColor = Color.WHITE

            for (x in 0 until width) {
                for (y in 0 until height) {
                    bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) darkColor else lightColor)
                }
            }
            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Modern Join Class Dialog with 1-tap QR scanning and monospace letter-spaced code entry.
     */
    fun showJoinClassDialog(
        context: Context,
        onScanQrClicked: (Dialog) -> Unit,
        onJoinCodeSubmitted: (Dialog, String) -> Unit
    ): Dialog {
        val dialog = Dialog(context)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_join_class_modern, null)
        dialog.setContentView(view)
        applyModernWindowStyles(dialog)

        val btnClose = view.findViewById<ImageButton>(R.id.btn_dialog_close)
        val btnScanQr = view.findViewById<View>(R.id.btn_scan_qr)
        val etJoinCode = view.findViewById<EditText>(R.id.et_join_code)
        val btnCancel = view.findViewById<Button>(R.id.btn_dialog_cancel)
        val btnConfirm = view.findViewById<Button>(R.id.btn_dialog_confirm)

        GabAIUtils.addSpringPressEffect(btnScanQr) {
            onScanQrClicked(dialog)
        }

        GabAIUtils.addSpringPressEffect(btnClose) {
            dialog.dismiss()
        }

        GabAIUtils.addSpringPressEffect(btnCancel) {
            dialog.dismiss()
        }

        GabAIUtils.addSpringPressEffect(btnConfirm) {
            val code = etJoinCode.text.toString().trim().uppercase()
            if (code.length == 6) {
                onJoinCodeSubmitted(dialog, code)
            } else {
                GabAIUtils.showSnackbar(context, "Please enter a valid 6-character code")
            }
        }

        dialog.show()
        applyDialogDimensions(dialog)
        return dialog
    }

    /**
     * Modern Teacher QR Code Dialog displaying offline generated QR code and 1-tap copy.
     */
    fun showQrCodeDialog(
        context: Context,
        title: String = "Your Classroom QR Code",
        subtitle: String = "Display this on your screen for students to scan.",
        qrContent: String,
        displayCode: String
    ): Dialog {
        val dialog = Dialog(context)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_qr_code_modern, null)
        dialog.setContentView(view)
        applyModernWindowStyles(dialog)

        val tvTitle = view.findViewById<TextView>(R.id.tv_dialog_title)
        val tvSubtitle = view.findViewById<TextView>(R.id.tv_dialog_subtitle)
        val ivQrCode = view.findViewById<ImageView>(R.id.iv_qr_code)
        val tvQrCodeText = view.findViewById<TextView>(R.id.tv_qr_code_text)
        val btnCopyCode = view.findViewById<View>(R.id.btn_copy_code)
        val btnDone = view.findViewById<Button>(R.id.btn_dialog_done)

        tvTitle.text = title
        tvSubtitle.text = subtitle
        tvQrCodeText.text = displayCode

        val qrBitmap = generateQrBitmap(qrContent, 600)
        if (qrBitmap != null) {
            ivQrCode.setImageBitmap(qrBitmap)
        }

        GabAIUtils.addSpringPressEffect(btnCopyCode) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Join Code", displayCode)
            clipboard.setPrimaryClip(clip)
            GabAIUtils.performHaptic(btnCopyCode, android.view.HapticFeedbackConstants.CLOCK_TICK)
            GabAIUtils.showSnackbar(context, "Copied $displayCode to clipboard! 📋")
        }

        GabAIUtils.addSpringPressEffect(btnDone) {
            dialog.dismiss()
        }

        dialog.show()
        applyDialogDimensions(dialog)
        return dialog
    }

    /**
     * Universal Modern Input Dialog (Replaces "Rename PDF", "Jump to Page", "Create Folder", etc.).
     */
    fun showInputDialog(
        context: Context,
        title: String,
        subtitle: String? = null,
        message: String? = null,
        hint: String? = null,
        initialText: String? = null,
        confirmText: String = "Confirm",
        cancelText: String = "Cancel",
        isNumeric: Boolean = false,
        inputType: Int? = null,
        minLines: Int = 1,
        badgeIcon: String = "✏️",
        iconRes: Int? = null,
        onConfirm: (String) -> Unit
    ): Dialog {
        val dialog = Dialog(context)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_modern_input, null)
        dialog.setContentView(view)
        applyModernWindowStyles(dialog)

        val tvBadge = view.findViewById<TextView>(R.id.tv_dialog_badge_icon)
        val ivBadge = view.findViewById<ImageView?>(R.id.iv_dialog_badge_icon)
        val tvTitle = view.findViewById<TextView>(R.id.tv_dialog_title)
        val tvSubtitle = view.findViewById<TextView>(R.id.tv_dialog_subtitle)
        val etInput = view.findViewById<EditText>(R.id.et_dialog_input)
        val btnCancel = view.findViewById<Button>(R.id.btn_dialog_cancel)
        val btnConfirm = view.findViewById<Button>(R.id.btn_dialog_confirm)

        if (iconRes != null && ivBadge != null) {
            ivBadge.setImageResource(iconRes)
            ivBadge.visibility = View.VISIBLE
            tvBadge.visibility = View.GONE
        } else {
            tvBadge.text = badgeIcon
            tvBadge.visibility = View.VISIBLE
            ivBadge?.visibility = View.GONE
        }

        tvTitle.text = title
        val subText = subtitle ?: message
        if (subText != null) {
            tvSubtitle.text = subText
            tvSubtitle.visibility = View.VISIBLE
        } else {
            tvSubtitle.visibility = View.GONE
        }

        if (hint != null) etInput.hint = hint
        if (initialText != null) {
            etInput.setText(initialText)
            etInput.setSelection(initialText.length)
        }

        if (inputType != null) {
            etInput.inputType = inputType
        } else if (isNumeric) {
            etInput.inputType = InputType.TYPE_CLASS_NUMBER
        }

        if (minLines > 1) {
            etInput.minLines = minLines
            etInput.gravity = android.view.Gravity.TOP
        }

        btnCancel.text = cancelText
        btnConfirm.text = confirmText

        GabAIUtils.addSpringPressEffect(btnCancel) {
            dialog.dismiss()
        }

        GabAIUtils.addSpringPressEffect(btnConfirm) {
            val text = etInput.text.toString().trim()
            if (text.isNotEmpty()) {
                dialog.dismiss()
                onConfirm(text)
            } else {
                GabAIUtils.showSnackbar(context, "Please enter a value")
            }
        }

        dialog.show()
        applyDialogDimensions(dialog)
        return dialog
    }

    /**
     * Universal Modern Confirmation Dialog (Replaces "Delete Material?", "Sign Out?", etc.).
     */
    fun showConfirmDialog(
        context: Context,
        title: String,
        message: String,
        confirmText: String = "Confirm",
        cancelText: String = "Cancel",
        isDestructive: Boolean = false,
        badgeIcon: String = if (isDestructive) "🗑️" else "⚠️",
        iconRes: Int? = null,
        onCancel: (() -> Unit)? = null,
        onConfirm: () -> Unit
    ): Dialog {
        val dialog = Dialog(context)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_modern_confirm, null)
        dialog.setContentView(view)
        applyModernWindowStyles(dialog)

        val tvBadge = view.findViewById<TextView>(R.id.tv_dialog_badge_icon)
        val ivBadge = view.findViewById<ImageView?>(R.id.iv_dialog_badge_icon)
        val tvTitle = view.findViewById<TextView>(R.id.tv_dialog_title)
        val tvMessage = view.findViewById<TextView>(R.id.tv_dialog_message)
        val btnCancel = view.findViewById<Button>(R.id.btn_dialog_cancel)
        val btnConfirm = view.findViewById<Button>(R.id.btn_dialog_confirm)

        if (iconRes != null && ivBadge != null) {
            ivBadge.setImageResource(iconRes)
            ivBadge.visibility = View.VISIBLE
            tvBadge.visibility = View.GONE
        } else {
            tvBadge.text = badgeIcon
            tvBadge.visibility = View.VISIBLE
            ivBadge?.visibility = View.GONE
        }

        tvTitle.text = title
        tvMessage.text = message
        btnCancel.text = cancelText
        btnConfirm.text = confirmText

        if (isDestructive) {
            btnConfirm.setBackgroundResource(R.drawable.bg_btn_modern_destructive)
        }

        GabAIUtils.addSpringPressEffect(btnCancel) {
            dialog.dismiss()
            onCancel?.invoke()
        }

        GabAIUtils.addSpringPressEffect(btnConfirm) {
            dialog.dismiss()
            onConfirm()
        }

        dialog.show()
        applyDialogDimensions(dialog)
        return dialog
    }

    /**
     * Universal Modern Notice / Info Dialog (Single Action).
     */
    fun showNoticeDialog(
        context: Context,
        title: String,
        message: String,
        buttonText: String = "Got It",
        badgeIcon: String = "ℹ️",
        iconRes: Int? = null,
        cancelable: Boolean = true,
        onAction: (() -> Unit)? = null,
        onDismiss: (() -> Unit)? = null
    ): Dialog {
        val dialog = Dialog(context)
        dialog.setCancelable(cancelable)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_modern_notice, null)
        dialog.setContentView(view)
        applyModernWindowStyles(dialog, onDismiss)

        val tvBadge = view.findViewById<TextView>(R.id.tv_dialog_badge_icon)
        val ivBadge = view.findViewById<ImageView?>(R.id.iv_dialog_badge_icon)
        val tvTitle = view.findViewById<TextView>(R.id.tv_dialog_title)
        val tvMessage = view.findViewById<TextView>(R.id.tv_dialog_message)
        val btnAction = view.findViewById<Button>(R.id.btn_dialog_action)

        if (iconRes != null && ivBadge != null) {
            ivBadge.setImageResource(iconRes)
            ivBadge.visibility = View.VISIBLE
            tvBadge.visibility = View.GONE
        } else {
            tvBadge.text = badgeIcon
            tvBadge.visibility = View.VISIBLE
            ivBadge?.visibility = View.GONE
        }

        tvTitle.text = title
        tvMessage.text = message
        btnAction.text = buttonText

        GabAIUtils.addSpringPressEffect(btnAction) {
            dialog.dismiss()
            onAction?.invoke() ?: onDismiss?.invoke()
        }

        dialog.show()
        applyDialogDimensions(dialog)
        return dialog
    }

    /**
     * Universal Modern Bento Selection Dialog.
     */
    fun showSelectionDialog(
        context: Context,
        title: String,
        subtitle: String? = null,
        items: List<String>,
        badgeIcon: String = "📋",
        onSelected: (Int, String) -> Unit
    ): Dialog {
        val dialog = Dialog(context)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_modern_selection, null)
        dialog.setContentView(view)
        applyModernWindowStyles(dialog)

        val tvBadge = view.findViewById<TextView>(R.id.tv_dialog_badge_icon)
        val tvTitle = view.findViewById<TextView>(R.id.tv_dialog_title)
        val tvSubtitle = view.findViewById<TextView>(R.id.tv_dialog_subtitle)
        val container = view.findViewById<LinearLayout>(R.id.ll_selection_container)
        val btnCancel = view.findViewById<Button>(R.id.btn_dialog_cancel)

        tvBadge.text = badgeIcon
        tvTitle.text = title
        if (subtitle != null) {
            tvSubtitle.text = subtitle
            tvSubtitle.visibility = View.VISIBLE
        } else {
            tvSubtitle.visibility = View.GONE
        }

        val inflater = LayoutInflater.from(context)
        items.forEachIndexed { index, itemText ->
            val itemView = inflater.inflate(R.layout.item_dialog_selection, container, false)
            val tvItem = itemView.findViewById<TextView>(R.id.tv_selection_item_text)
            tvItem.text = itemText

            GabAIUtils.addSpringPressEffect(itemView) {
                dialog.dismiss()
                onSelected(index, itemText)
            }
            container.addView(itemView)
        }

        GabAIUtils.addSpringPressEffect(btnCancel) {
            dialog.dismiss()
        }

        dialog.show()
        applyDialogDimensions(dialog)
        return dialog
    }

    /**
     * Universal Modern Custom Content Dialog.
     */
    fun showCustomDialog(
        context: Context,
        title: String,
        subtitle: String? = null,
        customView: View,
        confirmText: String? = "Confirm",
        cancelText: String? = "Cancel",
        badgeIcon: String = "✨",
        iconRes: Int? = null,
        onConfirm: ((Dialog) -> Unit)? = null,
        onCancel: ((Dialog) -> Unit)? = null
    ): Dialog {
        val dialog = Dialog(context)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_modern_custom, null)
        dialog.setContentView(view)
        applyModernWindowStyles(dialog)

        val tvBadge = view.findViewById<TextView>(R.id.tv_dialog_badge_icon)
        val ivBadge = view.findViewById<ImageView?>(R.id.iv_dialog_badge_icon)
        val tvTitle = view.findViewById<TextView>(R.id.tv_dialog_title)
        val tvSubtitle = view.findViewById<TextView>(R.id.tv_dialog_subtitle)
        val flCustom = view.findViewById<FrameLayout>(R.id.fl_custom_container)
        val btnCancel = view.findViewById<Button>(R.id.btn_dialog_cancel)
        val btnConfirm = view.findViewById<Button>(R.id.btn_dialog_confirm)

        if (iconRes != null && ivBadge != null) {
            ivBadge.setImageResource(iconRes)
            ivBadge.visibility = View.VISIBLE
            tvBadge.visibility = View.GONE
        } else {
            tvBadge.text = badgeIcon
            tvBadge.visibility = View.VISIBLE
            ivBadge?.visibility = View.GONE
        }

        tvTitle.text = title
        if (subtitle != null) {
            tvSubtitle.text = subtitle
            tvSubtitle.visibility = View.VISIBLE
        } else {
            tvSubtitle.visibility = View.GONE
        }

        flCustom.addView(customView)

        if (confirmText != null && onConfirm != null) {
            btnConfirm.text = confirmText
            btnConfirm.visibility = View.VISIBLE
            GabAIUtils.addSpringPressEffect(btnConfirm) {
                onConfirm(dialog)
            }
        } else {
            btnConfirm.visibility = View.GONE
        }

        if (cancelText != null) {
            btnCancel.text = cancelText
            btnCancel.visibility = View.VISIBLE
            GabAIUtils.addSpringPressEffect(btnCancel) {
                onCancel?.invoke(dialog) ?: dialog.dismiss()
            }
        } else {
            btnCancel.visibility = View.GONE
        }

        dialog.show()
        applyDialogDimensions(dialog)
        return dialog
    }

    /**
     * Interactive Quest & Feature Navigation Tutorial Dialog with animated mockup pointer.
     */
    fun showQuestNavigationDialog(context: Context, questId: String): Dialog {
        val dialog = Dialog(context)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_quest_nav_tutorial, null)
        dialog.setContentView(view)
        applyModernWindowStyles(dialog)

        val tvTag = view.findViewById<TextView>(R.id.tv_quest_nav_tag)
        val tvTitle = view.findViewById<TextView>(R.id.tv_quest_nav_title)
        val tvInstruction = view.findViewById<TextView>(R.id.tv_quest_nav_instruction)
        val btnGotIt = view.findViewById<Button>(R.id.btn_quest_nav_got_it)

        val mockupBubble = view.findViewById<View?>(R.id.mockup_bubble)
        val mockupBubbleScan = view.findViewById<View?>(R.id.mockup_bubble_scan)
        val mockupScan = view.findViewById<View?>(R.id.mockup_scan)
        val mockupTest = view.findViewById<View?>(R.id.mockup_test)
        val mockupSave = view.findViewById<View?>(R.id.mockup_save)
        val mockupHistory = view.findViewById<View?>(R.id.mockup_history)
        val mockupDetail = view.findViewById<View?>(R.id.mockup_detail)
        val mockupLibrary = view.findViewById<View?>(R.id.mockup_library)
        val mockupTeacherQr = view.findViewById<View?>(R.id.mockup_teacher_qr)
        val mockupTeacherClasses = view.findViewById<View?>(R.id.mockup_teacher_classes)
        val mockupTeacherLibrary = view.findViewById<View?>(R.id.mockup_teacher_library)
        val mockupTeacherQuiz = view.findViewById<View?>(R.id.mockup_teacher_quiz)
        val mockupTeacherAnalytics = view.findViewById<View?>(R.id.mockup_teacher_analytics)

        mockupBubble?.visibility = View.GONE
        mockupBubbleScan?.visibility = View.GONE
        mockupScan?.visibility = View.GONE
        mockupTest?.visibility = View.GONE
        mockupSave?.visibility = View.GONE
        mockupHistory?.visibility = View.GONE
        mockupDetail?.visibility = View.GONE
        mockupLibrary?.visibility = View.GONE
        mockupTeacherQr?.visibility = View.GONE
        mockupTeacherClasses?.visibility = View.GONE
        mockupTeacherLibrary?.visibility = View.GONE
        mockupTeacherQuiz?.visibility = View.GONE
        mockupTeacherAnalytics?.visibility = View.GONE

        when (questId) {
            ApprenticeQuestHelper.QUEST_BUBBLE -> {
                tvTag.text = "HOME SCREEN"
                tvTitle.text = "Activate the Floating Bubble"
                tvInstruction.text = "Tap 'Launch Bubble' at the top of your Home screen to turn on the floating companion."
                mockupBubble?.visibility = View.VISIBLE
            }
            ApprenticeQuestHelper.QUEST_BUBBLE_SCAN -> {
                tvTag.text = "ANY APP OR PDF"
                tvTitle.text = "Scan Text with Floating Bubble"
                tvInstruction.text = "When reading in any app or PDF, tap the floating bubble on your screen, then tap a word to see its definition."
                mockupBubbleScan?.visibility = View.VISIBLE
            }
            ApprenticeQuestHelper.QUEST_SCAN -> {
                tvTag.text = "BOTTOM NAVIGATION"
                tvTitle.text = "Scan Text with the Camera"
                tvInstruction.text = "Tap the camera button on the bottom bar, then point at printed text to look up words."
                mockupScan?.visibility = View.VISIBLE
            }
            ApprenticeQuestHelper.QUEST_TEST, "read" -> {
                tvTag.text = "HOME SCREEN"
                tvTitle.text = "Initiation Trials"
                tvInstruction.text = "Tap 'View Quest Details' on your Home screen, read the passage, and answer the questions."
                mockupTest?.visibility = View.VISIBLE
            }
            ApprenticeQuestHelper.QUEST_SAVE -> {
                tvTag.text = "WORD OVERVIEW"
                tvTitle.text = "Save Words to Favorites"
                tvInstruction.text = "When looking up a word, tap the star icon (⭐) at the top right to save it to your favorites."
                mockupSave?.visibility = View.VISIBLE
            }
            ApprenticeQuestHelper.QUEST_HISTORY -> {
                tvTag.text = "HOME SCREEN"
                tvTitle.text = "Check Scan History"
                tvInstruction.text = "On your Home screen, scroll down to 'Review' and tap 'History' to see words you recently looked up."
                mockupHistory?.visibility = View.VISIBLE
            }
            ApprenticeQuestHelper.QUEST_DETAIL -> {
                tvTag.text = "HOME SCREEN"
                tvTitle.text = "Saved Words (Favorites)"
                tvInstruction.text = "On your Home screen, scroll down to 'Review' and tap 'Favourites' to review all your saved words."
                mockupDetail?.visibility = View.VISIBLE
            }
            ApprenticeQuestHelper.QUEST_LIBRARY -> {
                tvTag.text = "HOME SCREEN"
                tvTitle.text = "Digital Library"
                tvInstruction.text = "On your Home screen, scroll down to 'Tools' and tap 'Digital Library' to open reading materials from your teacher."
                mockupLibrary?.visibility = View.VISIBLE
            }
            "teacher_qr" -> {
                tvTag.text = "HOME SCREEN"
                tvTitle.text = "Class Join Code & QR Code"
                tvInstruction.text = "Tap 'Show QR' on your Home screen to display your class QR code and 6-letter join code for students."
                mockupTeacherQr?.visibility = View.VISIBLE
            }
            "teacher_classes" -> {
                tvTag.text = "HOME SCREEN"
                tvTitle.text = "Class Sections & Rosters"
                tvInstruction.text = "On your Home screen, tap 'Class Sections' to view rosters, add classes, or remove students."
                mockupTeacherClasses?.visibility = View.VISIBLE
            }
            "teacher_library" -> {
                tvTag.text = "HOME SCREEN"
                tvTitle.text = "Learning Materials"
                tvInstruction.text = "On your Home screen, tap 'Learning Materials' to upload reading PDFs for your classes."
                mockupTeacherLibrary?.visibility = View.VISIBLE
            }
            "teacher_quiz" -> {
                tvTag.text = "READING MATERIALS"
                tvTitle.text = "Comprehension Quizzes"
                tvInstruction.text = "Open any material in your library and tap 'Generate AI Quiz' or 'Create Quiz' to add questions."
                mockupTeacherQuiz?.visibility = View.VISIBLE
            }
            "teacher_analytics" -> {
                tvTag.text = "HOME SCREEN"
                tvTitle.text = "Learner Analytics"
                tvInstruction.text = "On your Home screen, tap 'Learner Analytics' to view student progress and difficult words."
                mockupTeacherAnalytics?.visibility = View.VISIBLE
            }
        }

        val activePointer: View? = when (questId) {
            ApprenticeQuestHelper.QUEST_BUBBLE -> view.findViewById(R.id.pointer_bubble)
            ApprenticeQuestHelper.QUEST_BUBBLE_SCAN -> view.findViewById(R.id.pointer_bubble_scan)
            ApprenticeQuestHelper.QUEST_SCAN -> view.findViewById(R.id.pointer_scan)
            ApprenticeQuestHelper.QUEST_TEST, "read" -> view.findViewById(R.id.pointer_test)
            ApprenticeQuestHelper.QUEST_SAVE -> view.findViewById(R.id.pointer_save)
            ApprenticeQuestHelper.QUEST_HISTORY -> view.findViewById(R.id.pointer_history)
            ApprenticeQuestHelper.QUEST_DETAIL -> view.findViewById(R.id.pointer_detail)
            ApprenticeQuestHelper.QUEST_LIBRARY -> view.findViewById(R.id.pointer_library)
            "teacher_qr" -> view.findViewById(R.id.pointer_teacher_qr)
            "teacher_classes" -> view.findViewById(R.id.pointer_teacher_classes)
            "teacher_library" -> view.findViewById(R.id.pointer_teacher_library)
            "teacher_quiz" -> view.findViewById(R.id.pointer_teacher_quiz)
            "teacher_analytics" -> view.findViewById(R.id.pointer_teacher_analytics)
            else -> null
        }

        var pointerAnimator: ObjectAnimator? = null
        if (activePointer != null) {
            val property = if (questId == ApprenticeQuestHelper.QUEST_BUBBLE_SCAN) "translationX" else "translationY"
            val targetOffset = if (questId == ApprenticeQuestHelper.QUEST_BUBBLE_SCAN || questId == ApprenticeQuestHelper.QUEST_SCAN) 8f else -8f
            pointerAnimator = ObjectAnimator.ofFloat(activePointer, property, 0f, targetOffset, 0f).apply {
                duration = 850L
                startDelay = 260L
                repeatCount = ObjectAnimator.INFINITE
                repeatMode = ObjectAnimator.REVERSE
                interpolator = AccelerateDecelerateInterpolator()
            }
        }

        GabAIUtils.addSpringPressEffect(btnGotIt) {
            GabAIUtils.performHaptic(btnGotIt, android.view.HapticFeedbackConstants.CLOCK_TICK)
            pointerAnimator?.cancel()
            dialog.dismiss()
        }

        dialog.setOnShowListener {
            pointerAnimator?.start()
        }

        applyDialogDimensions(dialog)
        dialog.show()
        return dialog
    }
}
