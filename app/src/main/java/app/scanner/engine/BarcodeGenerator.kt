package app.scanner.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.Encoder
import java.util.EnumMap
import kotlin.math.min

enum class QrModuleShape(val label: String) {
    SQUARE("Sharp"),
    ROUNDED("Rounded"),
    DOTS("Dots"),
    DIAMOND("Prism")
}

enum class QrEyeShape(val label: String) {
    SQUARE("Classic"),
    ROUNDED("Squircle"),
    CIRCLE("Orbital")
}

enum class QrGradientMode(val label: String) {
    SOLID("Solid"),
    EMERALD_CYAN("Aurora"),
    GOLD_AMBER("Champagne"),
    VIOLET_CYAN("Nebula"),
    SUNSET_CORAL("Crimson")
}

enum class QrCenterEmblem(val label: String, val glyph: String) {
    NONE("None", ""),
    PRISM("Prism", "◈"),
    LINK("Link", "↗"),
    WIFI("Wi-Fi", "((•))"),
    CONTACT("Card", "ID"),
    VERIFIED("Safe", "✓"),
    CUSTOM("Custom", "QR")
}

data class QrStyleOptions(
    val moduleShape: QrModuleShape = QrModuleShape.ROUNDED,
    val eyeShape: QrEyeShape = QrEyeShape.ROUNDED,
    val gradientMode: QrGradientMode = QrGradientMode.SOLID,
    val centerEmblem: QrCenterEmblem = QrCenterEmblem.NONE,
    val customEmblemText: String = "QR",
    val foregroundArgb: Int = 0xFF07090E.toInt(),
    val backgroundArgb: Int = 0xFFFFFFFF.toInt(),
    val errorCorrection: ErrorCorrectionLevel = ErrorCorrectionLevel.M,
    val quietZoneModules: Int = 2,
    val captionText: String = "",
    val cornerRadiusRatio: Float = 0.06f
)

object BarcodeGenerator {

    fun generateBitmap(
        content: String,
        format: BarcodeFormat,
        widthPx: Int = 900,
        heightPx: Int = if (format.is2D()) 900 else 420,
        foregroundArgb: Int = 0xFF07090E.toInt(),
        backgroundArgb: Int = 0xFFFFFFFF.toInt(),
        errorCorrection: ErrorCorrectionLevel = ErrorCorrectionLevel.M,
        qrStyle: QrStyleOptions? = null
    ): Result<Bitmap> {
        if (content.isBlank()) {
            return Result.failure(IllegalArgumentException("Content cannot be empty"))
        }

        if (format == BarcodeFormat.QR_CODE && qrStyle != null) {
            return generateStyledQr(content, widthPx, qrStyle)
        }

        return runCatching {
            val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java).apply {
                put(EncodeHintType.CHARACTER_SET, "UTF-8")
                put(EncodeHintType.MARGIN, 2)
                if (format == BarcodeFormat.QR_CODE) {
                    put(EncodeHintType.ERROR_CORRECTION, errorCorrection)
                }
            }
            val matrix = MultiFormatWriter().encode(content, format, widthPx, heightPx, hints)
            val w = matrix.width
            val h = matrix.height
            val pixels = IntArray(w * h)
            for (y in 0 until h) {
                val offset = y * w
                for (x in 0 until w) {
                    pixels[offset + x] = if (matrix[x, y]) foregroundArgb else backgroundArgb
                }
            }
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
                setPixels(pixels, 0, w, 0, 0, w, h)
            }
        }
    }

    private fun generateStyledQr(
        content: String,
        sizePx: Int,
        style: QrStyleOptions
    ): Result<Bitmap> = runCatching {
        val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java).apply {
            put(EncodeHintType.CHARACTER_SET, "UTF-8")
        }
        val preferredEcc = if (style.centerEmblem != QrCenterEmblem.NONE) {
            ErrorCorrectionLevel.H
        } else {
            style.errorCorrection
        }
        val fallbackOrder = listOf(preferredEcc, ErrorCorrectionLevel.M, ErrorCorrectionLevel.L).distinct()
        var chosenEcc = preferredEcc
        var qrCode = runCatching { Encoder.encode(content, preferredEcc, hints) }.getOrNull()
        if (qrCode == null) {
            for (ecc in fallbackOrder) {
                val attempt = runCatching { Encoder.encode(content, ecc, hints) }.getOrNull()
                if (attempt != null) {
                    qrCode = attempt
                    chosenEcc = ecc
                    break
                }
            }
        }
        if (qrCode == null) {
            qrCode = Encoder.encode(content, ErrorCorrectionLevel.L, hints)
            chosenEcc = ErrorCorrectionLevel.L
        }
        val showCenterEmblem = style.centerEmblem != QrCenterEmblem.NONE &&
            (chosenEcc == ErrorCorrectionLevel.H || chosenEcc == ErrorCorrectionLevel.Q)
        val matrix = qrCode.matrix
        val mSize = matrix.width
        val quiet = style.quietZoneModules.coerceIn(1, 5)
        val totalModules = mSize + quiet * 2

        val caption = style.captionText.trim()
        val captionExtraH = if (caption.isNotEmpty()) (sizePx * 0.14f).toInt() else 0
        val outW = sizePx
        val outH = sizePx + captionExtraH

        val bitmap = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Draw rounded card background
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = style.backgroundArgb
            this.style = Paint.Style.FILL
        }
        val cardCorner = sizePx * style.cornerRadiusRatio.coerceIn(0f, 0.12f)
        canvas.drawRoundRect(
            RectF(0f, 0f, outW.toFloat(), outH.toFloat()),
            cardCorner,
            cardCorner,
            bgPaint
        )

        val moduleSize = sizePx.toFloat() / totalModules.toFloat()
        val origin = quiet * moduleSize

        // Foreground paint with optional linear gradient
        val fgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.style = Paint.Style.FILL
            shader = createGradientShader(style, sizePx.toFloat())
            if (shader == null) {
                color = style.foregroundArgb
            }
        }

        // Finder pattern boxes in matrix coordinates (7x7 at TL, TR, BL)
        fun isInFinder(mx: Int, my: Int): Boolean {
            val tl = mx in 0..6 && my in 0..6
            val tr = mx in (mSize - 7) until mSize && my in 0..6
            val bl = mx in 0..6 && my in (mSize - 7) until mSize
            return tl || tr || bl
        }

        // Center clear window for optional emblem
        val emblemNormRadius = if (showCenterEmblem) 0.145f else 0f
        val centerMatrix = mSize / 2f
        val emblemModuleRadius = mSize * emblemNormRadius
        fun isInCenterEmblem(mx: Int, my: Int): Boolean {
            if (emblemModuleRadius <= 0f) return false
            val dx = (mx + 0.5f) - centerMatrix
            val dy = (my + 0.5f) - centerMatrix
            return dx * dx + dy * dy <= emblemModuleRadius * emblemModuleRadius
        }

        // Render data modules
        for (my in 0 until mSize) {
            for (mx in 0 until mSize) {
                if (matrix[mx, my].toInt() != 1) continue
                if (isInFinder(mx, my) || isInCenterEmblem(mx, my)) continue

                val left = origin + mx * moduleSize
                val top = origin + my * moduleSize
                val right = left + moduleSize
                val bottom = top + moduleSize

                when (style.moduleShape) {
                    QrModuleShape.SQUARE -> {
                        canvas.drawRect(left, top, right + 0.35f, bottom + 0.35f, fgPaint)
                    }
                    QrModuleShape.ROUNDED -> {
                        val inset = moduleSize * 0.08f
                        val r = moduleSize * 0.28f
                        canvas.drawRoundRect(
                            left + inset,
                            top + inset,
                            right - inset,
                            bottom - inset,
                            r,
                            r,
                            fgPaint
                        )
                    }
                    QrModuleShape.DOTS -> {
                        val cx = (left + right) / 2f
                        val cy = (top + bottom) / 2f
                        canvas.drawCircle(cx, cy, moduleSize * 0.41f, fgPaint)
                    }
                    QrModuleShape.DIAMOND -> {
                        val cx = (left + right) / 2f
                        val cy = (top + bottom) / 2f
                        val half = moduleSize * 0.47f
                        val path = Path().apply {
                            moveTo(cx, cy - half)
                            lineTo(cx + half, cy)
                            lineTo(cx, cy + half)
                            lineTo(cx - half, cy)
                            close()
                        }
                        canvas.drawPath(path, fgPaint)
                    }
                }
            }
        }

        // Render 3 custom finder eyes
        val eyeOrigins = listOf(
            0 to 0,
            (mSize - 7) to 0,
            0 to (mSize - 7)
        )
        for ((ex, ey) in eyeOrigins) {
            drawFinderEye(
                canvas = canvas,
                left = origin + ex * moduleSize,
                top = origin + ey * moduleSize,
                moduleSize = moduleSize,
                eyeShape = style.eyeShape,
                fgPaint = fgPaint,
                bgPaint = bgPaint
            )
        }

        // Render center emblem badge
        if (showCenterEmblem) {
            val cx = sizePx / 2f
            val cy = sizePx / 2f
            val badgeRadius = sizePx * 0.10f

            // White/bg halo ring
            canvas.drawCircle(cx, cy, badgeRadius * 1.18f, bgPaint)

            val emblemPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.style = Paint.Style.FILL
                shader = createGradientShader(style, sizePx.toFloat())
                if (shader == null) color = style.foregroundArgb
            }
            canvas.drawCircle(cx, cy, badgeRadius, emblemPaint)

            val glyph = if (style.centerEmblem == QrCenterEmblem.CUSTOM) {
                style.customEmblemText.trim().take(3).ifBlank { "QR" }
            } else {
                style.centerEmblem.glyph
            }
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = style.backgroundArgb
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textSize = when (glyph.length) {
                    1 -> badgeRadius * 1.12f
                    2 -> badgeRadius * 0.90f
                    else -> badgeRadius * 0.68f
                }
            }
            val textY = cy - (textPaint.descent() + textPaint.ascent()) / 2f
            canvas.drawText(glyph, cx, textY, textPaint)
        }

        // Render optional scan callout caption at bottom
        if (caption.isNotEmpty()) {
            val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.style = Paint.Style.FILL
                shader = createGradientShader(style, sizePx.toFloat())
                if (shader == null) color = style.foregroundArgb
            }
            val padX = sizePx * 0.08f
            val pillTop = sizePx.toFloat() - moduleSize * 0.4f
            val pillBottom = outH - sizePx * 0.032f
            val pillRect = RectF(padX, pillTop, outW - padX, pillBottom)
            canvas.drawRoundRect(pillRect, (pillBottom - pillTop) / 2f, (pillBottom - pillTop) / 2f, pillPaint)

            val capPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = style.backgroundArgb
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textSize = (pillBottom - pillTop) * 0.48f
                letterSpacing = 0.08f
            }
            val textY = pillRect.centerY() - (capPaint.descent() + capPaint.ascent()) / 2f
            canvas.drawText(caption.take(24).uppercase(), pillRect.centerX(), textY, capPaint)
        }

        bitmap
    }

    private fun drawFinderEye(
        canvas: Canvas,
        left: Float,
        top: Float,
        moduleSize: Float,
        eyeShape: QrEyeShape,
        fgPaint: Paint,
        bgPaint: Paint
    ) {
        val outerSize = moduleSize * 7f
        val innerGap = moduleSize
        val pupilInset = moduleSize * 2f

        val outerRect = RectF(left, top, left + outerSize, top + outerSize)
        val midRect = RectF(
            left + innerGap,
            top + innerGap,
            left + outerSize - innerGap,
            top + outerSize - innerGap
        )
        val pupilRect = RectF(
            left + pupilInset,
            top + pupilInset,
            left + outerSize - pupilInset,
            top + outerSize - pupilInset
        )

        when (eyeShape) {
            QrEyeShape.SQUARE -> {
                canvas.drawRect(outerRect, fgPaint)
                canvas.drawRect(midRect, bgPaint)
                canvas.drawRect(pupilRect, fgPaint)
            }
            QrEyeShape.ROUNDED -> {
                val rOuter = moduleSize * 1.9f
                val rMid = moduleSize * 1.25f
                val rPupil = moduleSize * 0.9f
                canvas.drawRoundRect(outerRect, rOuter, rOuter, fgPaint)
                canvas.drawRoundRect(midRect, rMid, rMid, bgPaint)
                canvas.drawRoundRect(pupilRect, rPupil, rPupil, fgPaint)
            }
            QrEyeShape.CIRCLE -> {
                val cx = outerRect.centerX()
                val cy = outerRect.centerY()
                canvas.drawCircle(cx, cy, outerSize * 0.5f, fgPaint)
                canvas.drawCircle(cx, cy, (outerSize - innerGap * 2f) * 0.5f, bgPaint)
                canvas.drawCircle(cx, cy, (outerSize - pupilInset * 2f) * 0.5f, fgPaint)
            }
        }
    }

    private fun createGradientShader(style: QrStyleOptions, size: Float): Shader? {
        val colors = when (style.gradientMode) {
            QrGradientMode.SOLID -> return null
            QrGradientMode.EMERALD_CYAN -> intArrayOf(0xFF0A8F63.toInt(), 0xFF0077B6.toInt())
            QrGradientMode.GOLD_AMBER -> intArrayOf(0xFFB37B15.toInt(), 0xFFD9534F.toInt())
            QrGradientMode.VIOLET_CYAN -> intArrayOf(0xFF5E35B1.toInt(), 0xFF0096C7.toInt())
            QrGradientMode.SUNSET_CORAL -> intArrayOf(0xFFC9184A.toInt(), 0xFFF77F00.toInt())
        }
        return LinearGradient(0f, 0f, size, size, colors, null, Shader.TileMode.CLAMP)
    }

    private fun BarcodeFormat.is2D(): Boolean =
        this == BarcodeFormat.QR_CODE ||
            this == BarcodeFormat.AZTEC ||
            this == BarcodeFormat.DATA_MATRIX ||
            this == BarcodeFormat.PDF_417
}
