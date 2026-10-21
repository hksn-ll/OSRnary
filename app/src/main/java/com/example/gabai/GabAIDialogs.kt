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
import android.util.Log

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

        if (context is android.app.Activity && (context.isFinishing || context.isDestroyed)) {
            return dialog
        }

        try {
            dialog.show()
            applyDialogDimensions(dialog)
        } catch (e: Exception) {
            Log.w("GabAIDialogs", "Unable to show notice dialog safely: ${e.message}")
        }
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

        if (context is android.app.Activity && (context.isFinishing || context.isDestroyed)) {
            return dialog
        }

        try {
            dialog.show()
            applyDialogDimensions(dialog)
        } catch (e: Exception) {
            Log.w("GabAIDialogs", "Unable to show custom dialog safely: ${e.message}")
        }
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

    /**
     * Shows comprehensive announcement detail dialog with Seen By and discreet reply support.
     */
    fun showAnnouncementDetailDialog(
        context: Context,
        announcement: Announcement,
        currentUid: String,
        currentRole: String,
        currentUserName: String,
        userSchoolId: String,
        onReplySubmitted: (() -> Unit)? = null
    ): Dialog {
        val dialog = Dialog(context)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_announcement_detail, null)
        dialog.setContentView(view)
        applyModernWindowStyles(dialog)

        // Automatically mark as seen
        AnnouncementRepository.markAnnouncementSeen(
            announcementId = announcement.id,
            userUid = currentUid,
            userName = currentUserName,
            userRole = currentRole,
            schoolId = userSchoolId
        )

        val tvScope = view.findViewById<TextView>(R.id.tv_announcement_scope)
        val tvMeta = view.findViewById<TextView>(R.id.tv_announcement_meta)
        val tvTitle = view.findViewById<TextView>(R.id.tv_announcement_title)
        val tvBody = view.findViewById<TextView>(R.id.tv_announcement_body)
        val tvPriority = view.findViewById<TextView>(R.id.tv_announcement_priority_banner)
        val btnSeen = view.findViewById<TextView>(R.id.btn_announcement_seen)
        val btnClose = view.findViewById<Button>(R.id.btn_dialog_close)

        val layoutReplies = view.findViewById<LinearLayout>(R.id.layout_discreet_replies)
        val tvPrivacyNote = view.findViewById<TextView>(R.id.tv_reply_privacy_note)
        val btnToggleReply = view.findViewById<TextView>(R.id.btn_toggle_reply)
        val layoutComposer = view.findViewById<LinearLayout>(R.id.layout_reply_composer)
        val etReply = view.findViewById<EditText>(R.id.et_reply_message)
        val btnSendReply = view.findViewById<Button>(R.id.btn_send_reply)
        val containerThread = view.findViewById<LinearLayout>(R.id.container_thread_messages)

        val isDivision = announcement.schoolId == "ALL" || announcement.scope == "division"
        val scopeName = if (isDivision) "DIVISION-WIDE BULLETIN" else "${SchoolRepository.getSchoolName(announcement.schoolId).uppercase()} ADVISORY"
        tvScope.text = scopeName

        val dateStr = if (announcement.createdAt > 0) {
            java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault()).format(java.util.Date(announcement.createdAt))
        } else {
            "Recently"
        }
        val authorDisplay = announcement.authorName.ifEmpty { "Administration" }
        tvMeta.text = "$authorDisplay • $dateStr"
        tvTitle.text = announcement.title.ifEmpty { "Official Bulletin" }
        tvBody.text = announcement.content

        if (announcement.priority == "urgent") {
            tvPriority.visibility = View.VISIBLE
        } else {
            tvPriority.visibility = View.GONE
        }

        val seenCount = (announcement.seenCount).coerceAtLeast(announcement.seenUserIds.size.toLong())
        btnSeen.text = "👁️ $seenCount"

        val canInspectViews = currentRole == "teacher" || announcement.authorUid == currentUid
        if (canInspectViews) {
            GabAIUtils.addSpringPressEffect(btnSeen) {
                showAnnouncementViewsDialog(context, announcement)
            }
        }

        // Configure Discreet Replies
        if (!announcement.allowReplies) {
            layoutReplies.visibility = View.GONE
        } else {
            layoutReplies.visibility = View.VISIBLE

            val isAuthorTeacher = currentRole == "teacher" && announcement.authorUid == currentUid
            if (isAuthorTeacher) {
                // Teacher authored this announcement: Allow inspecting student inquiries
                tvPrivacyNote.text = "🔒 Student feedback is strictly private. Only you and school leadership can view replies."
                btnToggleReply.text = "📋 Review Student Inquiries (${announcement.replyCount})"
                layoutComposer.visibility = View.GONE
                GabAIUtils.addSpringPressEffect(btnToggleReply) {
                    showAnnouncementRepliesDialog(
                        context,
                        announcement,
                        currentUid,
                        currentRole,
                        currentUserName,
                        userSchoolId
                    )
                }
            } else {
                // Student or Teacher recipient
                if (currentRole == "teacher") {
                    tvPrivacyNote.text = "🔒 Private feedback to Division and School Administrators."
                    btnToggleReply.text = "💬 Reply to Administration..."
                } else {
                    tvPrivacyNote.text = "🔒 Private message: Only visible to your teacher & school administration."
                    btnToggleReply.text = "💬 Send a Private Response..."
                }

                GabAIUtils.addSpringPressEffect(btnToggleReply) {
                    val isOpening = layoutComposer.visibility != View.VISIBLE
                    layoutComposer.visibility = if (isOpening) View.VISIBLE else View.GONE
                    if (isOpening) etReply.requestFocus()
                }

                GabAIUtils.addSpringPressEffect(btnSendReply) {
                    val msgText = etReply.text.toString().trim()
                    if (msgText.isEmpty()) {
                        Toast.makeText(context, "Please type a message first.", Toast.LENGTH_SHORT).show()
                        return@addSpringPressEffect
                    }

                    btnSendReply.isEnabled = false
                    val reply = AnnouncementReply(
                        announcementId = announcement.id,
                        senderUid = currentUid,
                        senderName = currentUserName,
                        senderRole = currentRole,
                        senderSchoolId = userSchoolId,
                        message = msgText,
                        isAuthorResponse = false,
                        isPrivate = true
                    )

                    AnnouncementRepository.sendReply(reply) { success, _ ->
                        btnSendReply.isEnabled = true
                        if (success) {
                            etReply.setText("")
                            layoutComposer.visibility = View.GONE
                            Toast.makeText(context, "Response sent discreetly.", Toast.LENGTH_SHORT).show()
                            onReplySubmitted?.invoke()
                        } else {
                            Toast.makeText(context, "Failed to send response.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                // Listen to this user's private thread
                var replyListener: com.google.firebase.firestore.ListenerRegistration? = null
                replyListener = AnnouncementRepository.observeRepliesForAnnouncement(announcement.id) { replies ->
                    containerThread.removeAllViews()
                    val myThread = replies.filter { r ->
                        r.senderUid == currentUid || (r.isAuthorResponse && r.replyToUid == currentUid)
                    }

                    for (m in myThread) {
                        val isStaff = m.isAuthorResponse || m.senderRole != "student"
                        val tvBubble = TextView(context).apply {
                            text = "${if (isStaff) "Staff Response: " else "You: "}${m.message}"
                            textSize = 11.5f
                            setTextColor(if (isStaff) Color.parseColor("#4338CA") else Color.parseColor("#1E293B"))
                            setBackgroundResource(if (isStaff) R.drawable.bg_btn_modern_secondary else R.drawable.bg_btn_modern_secondary)
                            setPadding(24, 16, 24, 16)
                            val params = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            ).apply {
                                topMargin = 8
                            }
                            layoutParams = params
                        }
                        containerThread.addView(tvBubble)
                    }
                }

                dialog.setOnDismissListener {
                    replyListener.remove()
                }
            }
        }

        GabAIUtils.addSpringPressEffect(btnClose) {
            GabAIUtils.performHaptic(btnClose)
            dialog.dismiss()
        }

        applyDialogDimensions(dialog)
        dialog.show()
        return dialog
    }

    /**
     * Dialog for Teachers to broadcast announcements to their enrolled classes.
     */
    fun showCreateAnnouncementDialog(
        context: Context,
        teacherUid: String,
        teacherName: String,
        schoolId: String,
        classes: List<Pair<String, String>>, // (classId, className)
        onCreated: () -> Unit
    ): Dialog {
        val dialog = Dialog(context)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_create_announcement, null)
        dialog.setContentView(view)
        applyModernWindowStyles(dialog)

        val etTitle = view.findViewById<EditText>(R.id.et_create_announcement_title)
        val etContent = view.findViewById<EditText>(R.id.et_create_announcement_content)
        val spinnerClass = view.findViewById<Spinner>(R.id.spinner_target_class)
        val cbUrgent = view.findViewById<CheckBox>(R.id.cb_is_urgent)
        val btnCancel = view.findViewById<Button>(R.id.btn_cancel_create_announcement)
        val btnPublish = view.findViewById<Button>(R.id.btn_publish_announcement)

        val classOptions = mutableListOf("All My Enrolled Classes")
        classOptions.addAll(classes.map { it.second })

        val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, classOptions)
        spinnerClass.adapter = adapter

        GabAIUtils.addSpringPressEffect(btnCancel) {
            dialog.dismiss()
        }

        GabAIUtils.addSpringPressEffect(btnPublish) {
            val title = etTitle.text.toString().trim()
            val content = etContent.text.toString().trim()
            val selectedIndex = spinnerClass.selectedItemPosition

            if (title.isEmpty()) {
                Toast.makeText(context, "Please enter a title.", Toast.LENGTH_SHORT).show()
                return@addSpringPressEffect
            }
            if (content.isEmpty()) {
                Toast.makeText(context, "Please enter announcement content.", Toast.LENGTH_SHORT).show()
                return@addSpringPressEffect
            }

            btnPublish.isEnabled = false
            btnPublish.text = "Publishing..."

            val targetClassId = if (selectedIndex > 0 && selectedIndex - 1 < classes.size) {
                classes[selectedIndex - 1].first
            } else {
                null
            }

            val announcement = Announcement(
                title = title,
                content = content,
                authorUid = teacherUid,
                authorName = teacherName,
                authorRole = "teacher",
                authorSchoolId = schoolId,
                scope = if (targetClassId != null) "class" else "school",
                schoolId = schoolId,
                classId = targetClassId,
                targetRoles = listOf("student"),
                priority = if (cbUrgent.isChecked) "urgent" else "normal",
                allowReplies = true,
                createdAt = System.currentTimeMillis()
            )

            AnnouncementRepository.createAnnouncement(announcement) { success, _ ->
                btnPublish.isEnabled = true
                btnPublish.text = "Publish"
                if (success) {
                    Toast.makeText(context, "Announcement broadcast successfully!", Toast.LENGTH_SHORT).show()
                    onCreated()
                    dialog.dismiss()
                } else {
                    Toast.makeText(context, "Failed to publish notice.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        applyDialogDimensions(dialog)
        dialog.show()
        return dialog
    }

    /**
     * Dialog for Teachers to review student inquiries and reply directly.
     */
    fun showAnnouncementRepliesDialog(
        context: Context,
        announcement: Announcement,
        currentUid: String,
        currentRole: String,
        currentUserName: String,
        userSchoolId: String
    ): Dialog {
        val dialog = Dialog(context)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_announcement_replies, null)
        dialog.setContentView(view)
        applyModernWindowStyles(dialog)

        val tvTitle = view.findViewById<TextView>(R.id.tv_replies_dialog_title)
        val tvSubtitle = view.findViewById<TextView>(R.id.tv_replies_dialog_subtitle)
        val container = view.findViewById<LinearLayout>(R.id.container_student_threads)
        val tvNoReplies = view.findViewById<TextView>(R.id.tv_no_replies)
        val btnClose = view.findViewById<Button>(R.id.btn_close_replies)

        tvTitle.text = "Inquiries: ${announcement.title}"
        tvSubtitle.text = "Private student questions"

        val listener = AnnouncementRepository.observeRepliesForAnnouncement(announcement.id) { replies ->
            val studentReplies = replies.filter { it.senderRole == "student" }
            if (studentReplies.isEmpty()) {
                tvNoReplies.visibility = View.VISIBLE
                container.removeAllViews()
                container.addView(tvNoReplies)
                return@observeRepliesForAnnouncement
            }

            tvNoReplies.visibility = View.GONE
            container.removeAllViews()

            val studentUids = studentReplies.map { it.senderUid }.distinct()
            for (sUid in studentUids) {
                val studentMessages = replies.filter { it.senderUid == sUid }
                val studentName = studentMessages.firstOrNull()?.senderName ?: "Student"
                val staffAnswers = replies.filter { it.isAuthorResponse && it.replyToUid == sUid }
                val thread = (studentMessages + staffAnswers).sortedBy { it.createdAt }

                val card = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setBackgroundResource(R.drawable.bg_btn_modern_secondary)
                    setPadding(24, 20, 24, 20)
                    val params = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        bottomMargin = 14
                    }
                    layoutParams = params
                }

                val header = TextView(context).apply {
                    text = "👤 $studentName"
                    textSize = 13f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setTextColor(Color.parseColor("#1E1B4B"))
                }
                card.addView(header)

                for (msg in thread) {
                    val isStaff = msg.isAuthorResponse || msg.senderRole != "student"
                    val msgView = TextView(context).apply {
                        text = "${if (isStaff) "You: " else "$studentName: "}${msg.message}"
                        textSize = 11.5f
                        setTextColor(if (isStaff) Color.parseColor("#4338CA") else Color.parseColor("#334155"))
                        setPadding(0, 6, 0, 4)
                    }
                    card.addView(msgView)
                }

                // Quick reply row
                val replyRow = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = 10
                    }
                }

                val etResponse = EditText(context).apply {
                    hint = "Reply directly to $studentName..."
                    textSize = 11.5f
                    setPadding(16, 12, 16, 12)
                    setBackgroundResource(android.R.drawable.editbox_background_normal)
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }

                val btnSend = Button(context).apply {
                    text = "Send"
                    textSize = 11.5f
                    setBackgroundResource(R.drawable.bg_btn_modern_primary)
                    setTextColor(Color.WHITE)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        (36 * context.resources.displayMetrics.density).toInt()
                    ).apply {
                        marginStart = 8
                    }
                }

                GabAIUtils.addSpringPressEffect(btnSend) {
                    val answerText = etResponse.text.toString().trim()
                    if (answerText.isEmpty()) return@addSpringPressEffect

                    btnSend.isEnabled = false
                    val answerReply = AnnouncementReply(
                        announcementId = announcement.id,
                        senderUid = currentUid,
                        senderName = currentUserName,
                        senderRole = currentRole,
                        senderSchoolId = userSchoolId,
                        message = answerText,
                        isAuthorResponse = true,
                        isPrivate = true,
                        replyToUid = sUid
                    )

                    AnnouncementRepository.sendReply(answerReply) { ok, _ ->
                        btnSend.isEnabled = true
                        if (ok) {
                            etResponse.setText("")
                            Toast.makeText(context, "Direct guidance sent to $studentName.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                replyRow.addView(etResponse)
                replyRow.addView(btnSend)
                card.addView(replyRow)

                container.addView(card)
            }
        }

        dialog.setOnDismissListener {
            listener.remove()
        }

        GabAIUtils.addSpringPressEffect(btnClose) {
            dialog.dismiss()
        }

        applyDialogDimensions(dialog)
        dialog.show()
        return dialog
    }

    /**
     * Dialog for inspecting Seen By read receipts.
     */
    fun showAnnouncementViewsDialog(
        context: Context,
        announcement: Announcement
    ): Dialog {
        val dialog = Dialog(context)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_announcement_views, null)
        dialog.setContentView(view)
        applyModernWindowStyles(dialog)

        val tvSubtitle = view.findViewById<TextView>(R.id.tv_views_dialog_subtitle)
        val container = view.findViewById<LinearLayout>(R.id.container_viewers_list)
        val tvNoViewers = view.findViewById<TextView>(R.id.tv_no_viewers)
        val btnClose = view.findViewById<Button>(R.id.btn_close_views)

        AnnouncementRepository.fetchAnnouncementViewers(announcement.id) { viewers ->
            if (viewers.isEmpty()) {
                tvSubtitle.text = "0 Total Viewers"
                tvNoViewers.visibility = View.VISIBLE
                return@fetchAnnouncementViewers
            }

            tvSubtitle.text = "${viewers.size} Total Viewers"
            tvNoViewers.visibility = View.GONE
            container.removeAllViews()

            for (v in viewers) {
                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(16, 12, 16, 12)
                    setBackgroundResource(R.drawable.bg_btn_modern_secondary)
                    val params = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        bottomMargin = 8
                    }
                    layoutParams = params
                }

                val infoLayout = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }

                val tvName = TextView(context).apply {
                    text = v.userName.ifEmpty { "User" }
                    textSize = 12.5f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setTextColor(Color.parseColor("#1E293B"))
                }

                val roleLabel = when (v.userRole) {
                    "teacher" -> "Faculty"
                    "school_admin", "super_admin" -> "Administrator"
                    else -> "Learner"
                }
                val tvRole = TextView(context).apply {
                    text = "$roleLabel • ${SchoolRepository.getSchoolName(v.schoolId)}"
                    textSize = 10.5f
                    setTextColor(Color.parseColor("#64748B"))
                }
                infoLayout.addView(tvName)
                infoLayout.addView(tvRole)

                val dateStr = if (v.seenAt > 0) {
                    java.text.SimpleDateFormat("MMM dd, h:mm a", java.util.Locale.getDefault()).format(java.util.Date(v.seenAt))
                } else {
                    "Recently"
                }
                val tvTime = TextView(context).apply {
                    text = dateStr
                    textSize = 10.5f
                    setTextColor(Color.parseColor("#94A3B8"))
                }

                row.addView(infoLayout)
                row.addView(tvTime)
                container.addView(row)
            }
        }

        GabAIUtils.addSpringPressEffect(btnClose) {
            dialog.dismiss()
        }

        applyDialogDimensions(dialog)
        dialog.show()
        return dialog
    }

    /**
     * Archive & Recycle Vault Dialog: Review and restore accidentally deleted records (30-day retention).
     */
    fun showArchiveVaultDialog(
        context: Context,
        schoolId: String,
        initialFilter: String = "ALL",
        onRestored: (() -> Unit)? = null
    ): Dialog {
        val dialog = Dialog(context)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_archive_vault, null)
        dialog.setContentView(view)
        applyModernWindowStyles(dialog)

        val tvSubtitle = view.findViewById<TextView>(R.id.tv_vault_subtitle)
        val container = view.findViewById<LinearLayout>(R.id.container_archive_list)
        val tvNoArchives = view.findViewById<TextView>(R.id.tv_no_archives)
        val btnClose = view.findViewById<Button>(R.id.btn_close_vault)

        val chipAll = view.findViewById<Button>(R.id.chip_filter_all)
        val chipStudents = view.findViewById<Button>(R.id.chip_filter_students)
        val chipMaterials = view.findViewById<Button>(R.id.chip_filter_materials)
        val chipClasses = view.findViewById<Button>(R.id.chip_filter_classes)

        var activeFilter = initialFilter
        val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""

        val loadVault: () -> Unit = {
            tvNoArchives.text = "Loading campus archives..."
            tvNoArchives.visibility = View.VISIBLE
            container.removeAllViews()

            ArchiveVaultRepository.fetchCampusArchives(schoolId, activeFilter, { records ->
                if (records.isEmpty()) {
                    tvSubtitle.text = "0 Archived Records (Campus Safety Net)"
                    tvNoArchives.text = "No archived records found in this vault."
                    tvNoArchives.visibility = View.VISIBLE
                } else {
                    tvSubtitle.text = "${records.size} Recoverable Records (30-Day Auto Purge)"
                    tvNoArchives.visibility = View.GONE
                    container.removeAllViews()

                    val density = context.resources.displayMetrics.density

                    for (rec in records) {
                        val card = LinearLayout(context).apply {
                            orientation = LinearLayout.VERTICAL
                            setPadding((14 * density).toInt(), (12 * density).toInt(), (14 * density).toInt(), (12 * density).toInt())
                            setBackgroundResource(R.drawable.bg_btn_modern_secondary)
                            val params = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            ).apply {
                                bottomMargin = (10 * density).toInt()
                            }
                            layoutParams = params
                        }

                        val topRow = LinearLayout(context).apply {
                            orientation = LinearLayout.HORIZONTAL
                            gravity = android.view.Gravity.CENTER_VERTICAL
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            )
                        }

                        val iconText = when (rec.entityType) {
                            "student" -> "👤"
                            "material" -> "📄"
                            "subject" -> "📁"
                            "class" -> "🏫"
                            else -> "📦"
                        }

                        val tvIcon = TextView(context).apply {
                            text = iconText
                            textSize = 18f
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            ).apply {
                                marginEnd = (10 * density).toInt()
                            }
                        }

                        val infoLayout = LinearLayout(context).apply {
                            orientation = LinearLayout.VERTICAL
                            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                        }

                        val tvTitle = TextView(context).apply {
                            text = rec.displayName
                            textSize = 13f
                            setTypeface(null, android.graphics.Typeface.BOLD)
                            setTextColor(Color.parseColor("#1E293B"))
                        }

                        val tvSub = TextView(context).apply {
                            text = rec.subtitle
                            textSize = 11f
                            setTextColor(Color.parseColor("#64748B"))
                        }

                        infoLayout.addView(tvTitle)
                        infoLayout.addView(tvSub)

                        val btnRestore = Button(context).apply {
                            text = "Restore"
                            textSize = 11.5f
                            setTypeface(null, android.graphics.Typeface.BOLD)
                            setTextColor(Color.WHITE)
                            setBackgroundResource(R.drawable.bg_btn_modern_primary)
                            stateListAnimator = null
                            layoutParams = LinearLayout.LayoutParams(
                                (82 * density).toInt(),
                                (34 * density).toInt()
                            ).apply {
                                marginStart = (8 * density).toInt()
                            }
                        }

                        topRow.addView(tvIcon)
                        topRow.addView(infoLayout)
                        topRow.addView(btnRestore)

                        val dateFormatted = java.text.SimpleDateFormat("MMM dd, yyyy • h:mm a", java.util.Locale.getDefault()).format(java.util.Date(rec.archivedAt))
                        val tvFooter = TextView(context).apply {
                            text = "Archived by ${rec.archivedByName} on $dateFormatted"
                            textSize = 10f
                            setTextColor(Color.parseColor("#94A3B8"))
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            ).apply {
                                topMargin = (6 * density).toInt()
                            }
                        }

                        card.addView(topRow)
                        card.addView(tvFooter)

                        GabAIUtils.addSpringPressEffect(btnRestore) {
                            btnRestore.isEnabled = false
                            btnRestore.text = "Restoring..."
                            ArchiveVaultRepository.restoreEntity(rec.archiveId, currentUid, { restoredName ->
                                GabAIUtils.showSnackbar(context, "Restored $restoredName to active campus! ✨")
                                onRestored?.invoke()
                                card.visibility = View.GONE
                            }, { err ->
                                btnRestore.isEnabled = true
                                btnRestore.text = "Restore"
                                GabAIUtils.showSnackbar(context, "Restore failed: ${err.message}")
                            })
                        }

                        container.addView(card)
                    }
                }
            }, { err ->
                tvNoArchives.text = "Error loading archives: ${err.message}"
                tvNoArchives.visibility = View.VISIBLE
            })
        }

        val updateChipStyles = {
            val chips = listOf(chipAll, chipStudents, chipMaterials, chipClasses)
            val filters = listOf("ALL", "student", "material", "class")
            for (i in chips.indices) {
                if (activeFilter.equals(filters[i], ignoreCase = true)) {
                    chips[i].setBackgroundResource(R.drawable.bg_btn_modern_primary)
                    chips[i].setTextColor(Color.WHITE)
                } else {
                    chips[i].setBackgroundResource(R.drawable.bg_btn_modern_secondary)
                    chips[i].setTextColor(Color.parseColor("#64748B"))
                }
            }
        }

        chipAll.setOnClickListener { activeFilter = "ALL"; updateChipStyles(); loadVault() }
        chipStudents.setOnClickListener { activeFilter = "student"; updateChipStyles(); loadVault() }
        chipMaterials.setOnClickListener { activeFilter = "material"; updateChipStyles(); loadVault() }
        chipClasses.setOnClickListener { activeFilter = "class"; updateChipStyles(); loadVault() }

        loadVault()

        GabAIUtils.addSpringPressEffect(btnClose) {
            dialog.dismiss()
        }

        applyDialogDimensions(dialog)
        dialog.show()
        return dialog
    }
}
