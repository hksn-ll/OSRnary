package com.example.gabai

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
    fun applyModernWindowStyles(dialog: Dialog) {
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
                }
            } catch (_: Exception) {}
        }

        try {
            window.setWindowAnimations(R.style.SleekDialogAnimation)
        } catch (_: Exception) {}

        // Enforce card sizing on show
        dialog.setOnShowListener {
            applyDialogDimensions(dialog)
        }
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
        hint: String? = null,
        initialText: String? = null,
        confirmText: String = "Confirm",
        isNumeric: Boolean = false,
        badgeIcon: String = "✏️",
        onConfirm: (String) -> Unit
    ): Dialog {
        val dialog = Dialog(context)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_modern_input, null)
        dialog.setContentView(view)
        applyModernWindowStyles(dialog)

        val tvBadge = view.findViewById<TextView>(R.id.tv_dialog_badge_icon)
        val tvTitle = view.findViewById<TextView>(R.id.tv_dialog_title)
        val tvSubtitle = view.findViewById<TextView>(R.id.tv_dialog_subtitle)
        val etInput = view.findViewById<EditText>(R.id.et_dialog_input)
        val btnCancel = view.findViewById<Button>(R.id.btn_dialog_cancel)
        val btnConfirm = view.findViewById<Button>(R.id.btn_dialog_confirm)

        tvBadge.text = badgeIcon
        tvTitle.text = title
        if (subtitle != null) {
            tvSubtitle.text = subtitle
            tvSubtitle.visibility = View.VISIBLE
        } else {
            tvSubtitle.visibility = View.GONE
        }

        if (hint != null) etInput.hint = hint
        if (initialText != null) {
            etInput.setText(initialText)
            etInput.setSelection(initialText.length)
        }

        if (isNumeric) {
            etInput.inputType = InputType.TYPE_CLASS_NUMBER
        }

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
        onConfirm: () -> Unit
    ): Dialog {
        val dialog = Dialog(context)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_modern_confirm, null)
        dialog.setContentView(view)
        applyModernWindowStyles(dialog)

        val tvBadge = view.findViewById<TextView>(R.id.tv_dialog_badge_icon)
        val tvTitle = view.findViewById<TextView>(R.id.tv_dialog_title)
        val tvMessage = view.findViewById<TextView>(R.id.tv_dialog_message)
        val btnCancel = view.findViewById<Button>(R.id.btn_dialog_cancel)
        val btnConfirm = view.findViewById<Button>(R.id.btn_dialog_confirm)

        tvBadge.text = badgeIcon
        tvTitle.text = title
        tvMessage.text = message
        btnCancel.text = cancelText
        btnConfirm.text = confirmText

        if (isDestructive) {
            btnConfirm.setBackgroundResource(R.drawable.bg_btn_modern_destructive)
        }

        GabAIUtils.addSpringPressEffect(btnCancel) {
            dialog.dismiss()
        }

        GabAIUtils.addSpringPressEffect(btnConfirm) {
            dialog.dismiss()
            onConfirm()
        }

        dialog.show()
        applyDialogDimensions(dialog)
        return dialog
    }
}
