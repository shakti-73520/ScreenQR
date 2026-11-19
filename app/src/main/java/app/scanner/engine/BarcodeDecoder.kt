package app.scanner.engine

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import androidx.camera.core.ImageProxy
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.Result
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.multi.GenericMultipleBarcodeReader
import com.google.zxing.multi.qrcode.QRCodeMultiReader
import java.util.EnumMap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class BarcodeDecoder(
    private val contentParser: ContentParser,
    private val safetyAnalyzer: SafetyAnalyzer
) {

    private val allFormats = listOf(
        BarcodeFormat.QR_CODE,
        BarcodeFormat.DATA_MATRIX,
        BarcodeFormat.AZTEC,
        BarcodeFormat.PDF_417,
        BarcodeFormat.MAXICODE,
        BarcodeFormat.EAN_13,
        BarcodeFormat.EAN_8,
        BarcodeFormat.UPC_A,
        BarcodeFormat.UPC_E,
        BarcodeFormat.CODE_128,
        BarcodeFormat.CODE_39,
        BarcodeFormat.CODE_93,
        BarcodeFormat.CODABAR,
        BarcodeFormat.ITF,
        BarcodeFormat.RSS_14,
        BarcodeFormat.RSS_EXPANDED
    )

    private fun baseHints(tryHarder: Boolean): Map<DecodeHintType, Any> =
        EnumMap<DecodeHintType, Any>(DecodeHintType::class.java).apply {
            put(DecodeHintType.POSSIBLE_FORMATS, allFormats)
            put(DecodeHintType.TRY_HARDER, tryHarder)
            put(DecodeHintType.CHARACTER_SET, "UTF-8")
        }

    /**
     * Thorough whole-screen & photo multi-code detector.
     * Finds ALL QR codes and ALL 1D/2D barcodes across the entire image, including
     * inverted/dark-mode codes, small codes in corners, and vertical 1D barcodes.
     */
    fun decodeAllFromBitmap(
        bitmap: Bitmap,
        safetyEnabled: Boolean,
        isCancelled: () -> Boolean = { false }
    ): List<DetectedCode> {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0 || isCancelled()) return emptyList()

        val collected = mutableListOf<RawDetection>()
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val fullSource = RGBLuminanceSource(width, height, pixels)
        scanRegionMulti(fullSource, 0, 0, width, height, collected, tryInverted = true)
        if (isCancelled()) return emptyList()

        // Multi-scale overlapping grid pass to catch small QR codes & barcodes across the whole screen
        // 2x2 overlapping quadrants (60% each)
        val quadW = (width * 0.60f).toInt().coerceAtLeast(1)
        val quadH = (height * 0.60f).toInt().coerceAtLeast(1)
        val quadX = listOf(0, width - quadW)
        val quadY = listOf(0, height - quadH)
        for (y in quadY) {
            for (x in quadX) {
                if (isCancelled()) return emptyList()
                val crop = fullSource.crop(x, y, quadW, quadH)
                scanRegionMulti(crop, x, y, width, height, collected, tryInverted = true)
            }
        }

        // 3x4 overlapping dense tiles for tiny codes anywhere on a phone display
        val cols = 3
        val rows = 4
        val tileW = (width * 0.44f).toInt().coerceAtLeast(1)
        val tileH = (height * 0.34f).toInt().coerceAtLeast(1)
        val stepX = if (cols > 1) (width - tileW) / (cols - 1) else 0
        val stepY = if (rows > 1) (height - tileH) / (rows - 1) else 0

        for (r in 0 until rows) {
            for (c in 0 until cols) {
                if (isCancelled()) return emptyList()
                val x = (c * stepX).coerceIn(0, width - tileW)
                val y = (r * stepY).coerceIn(0, height - tileH)
                val crop = fullSource.crop(x, y, tileW, tileH)
                scanRegionSingleAndMulti(crop, x, y, width, height, collected)
            }
        }

        if (isCancelled()) return emptyList()
        return finalizeDetections(collected, safetyEnabled, prefix = "code")
    }

    /**
     * Fast single-region detector used by Screen QR's Manual Area Selector.
     * Scans only the selected normalized rectangle (+ small edge padding) with inverted,
     * 90-degree rotated, and 2x upscaled tiny-code fallbacks for sub-100ms decoding.
     */
    fun decodeRegionFromBitmap(
        bitmap: Bitmap,
        normalizedRegion: RectF,
        safetyEnabled: Boolean,
        isCancelled: () -> Boolean = { false }
    ): List<DetectedCode> {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0 || isCancelled()) return emptyList()

        val padW = (normalizedRegion.width() * 0.06f).coerceAtLeast(0.015f)
        val padH = (normalizedRegion.height() * 0.06f).coerceAtLeast(0.015f)
        val leftPx = ((normalizedRegion.left - padW).coerceIn(0f, 0.96f) * width).toInt()
        val topPx = ((normalizedRegion.top - padH).coerceIn(0f, 0.96f) * height).toInt()
        val rightPx = ((normalizedRegion.right + padW).coerceIn(0.04f, 1f) * width).toInt()
        val bottomPx = ((normalizedRegion.bottom + padH).coerceIn(0.04f, 1f) * height).toInt()

        val cropW = (rightPx - leftPx).coerceIn(16, width - leftPx)
        val cropH = (bottomPx - topPx).coerceIn(16, height - topPx)

        val collected = mutableListOf<RawDetection>()
        val cropPixels = IntArray(cropW * cropH)
        bitmap.getPixels(cropPixels, 0, cropW, leftPx, topPx, cropW, cropH)

        val cropSource = RGBLuminanceSource(cropW, cropH, cropPixels)
        scanRegionMulti(cropSource, leftPx, topPx, width, height, collected, tryInverted = true)
        if (isCancelled()) return emptyList()

        // If user selected a tiny code, try a crisp 2x upscale pass for extra resolution
        if (collected.isEmpty() && min(cropW, cropH) < 420) {
            val cropBmp = Bitmap.createBitmap(cropPixels, cropW, cropH, Bitmap.Config.ARGB_8888)
            val upW = cropW * 2
            val upH = cropH * 2
            val upBmp = Bitmap.createScaledBitmap(cropBmp, upW, upH, true)
            val upPixels = IntArray(upW * upH)
            upBmp.getPixels(upPixels, 0, upW, 0, 0, upW, upH)
            val upSource = RGBLuminanceSource(upW, upH, upPixels)
            val upHits = mutableListOf<RawDetection>()
            scanRegionMulti(upSource, 0, 0, upW, upH, upHits, tryInverted = true)
            upHits.forEach { hit ->
                val mapped = RectF(
                    (leftPx + hit.normRect.left * cropW) / width.toFloat(),
                    (topPx + hit.normRect.top * cropH) / height.toFloat(),
                    (leftPx + hit.normRect.right * cropW) / width.toFloat(),
                    (topPx + hit.normRect.bottom * cropH) / height.toFloat()
                )
                collected += RawDetection(hit.result, mapped)
            }
            if (upBmp != cropBmp) upBmp.recycle()
            cropBmp.recycle()
        }

        // Also check 90-degree rotation inside the region if still empty (vertical 1D barcodes)
        if (collected.isEmpty() && !isCancelled()) {
            val cropBmp = Bitmap.createBitmap(cropPixels, cropW, cropH, Bitmap.Config.ARGB_8888)
            val matrix = Matrix().apply { postRotate(90f) }
            val rotBmp = Bitmap.createBitmap(cropBmp, 0, 0, cropW, cropH, matrix, true)
            val rw = rotBmp.width
            val rh = rotBmp.height
            val rotPixels = IntArray(rw * rh)
            rotBmp.getPixels(rotPixels, 0, rw, 0, 0, rw, rh)
            val rotSource = RGBLuminanceSource(rw, rh, rotPixels)
            val cwReader = MultiFormatReader().apply { setHints(baseHints(tryHarder = true)) }
            runCatching { cwReader.decodeWithState(BinaryBitmap(HybridBinarizer(rotSource))) }.getOrNull()?.let { res ->
                val rawRect = computeBounds(res, 0, 0, rw, rh)
                val mapped = RectF(
                    (leftPx + rawRect.top * cropW) / width.toFloat(),
                    (topPx + (1f - rawRect.right) * cropH) / height.toFloat(),
                    (leftPx + rawRect.bottom * cropW) / width.toFloat(),
                    (topPx + (1f - rawRect.left) * cropH) / height.toFloat()
                )
                collected += RawDetection(res, mapped)
            }
            if (rotBmp != cropBmp) rotBmp.recycle()
            cropBmp.recycle()
        }

        if (isCancelled()) return emptyList()
        return finalizeDetections(collected, safetyEnabled, prefix = "area")
    }

    private fun finalizeDetections(
        collected: List<RawDetection>,
        safetyEnabled: Boolean,
        prefix: String
    ): List<DetectedCode> {
        val deduped = deduplicate(collected)
        return deduped.mapIndexed { idx, raw ->
            val parsed = contentParser.parse(raw.result.text, raw.result.barcodeFormat)
            val safety = safetyAnalyzer.analyze(raw.result.text, parsed, safetyEnabled)
            DetectedCode(
                id = "${prefix}_${idx}_${raw.result.barcodeFormat.name}_${raw.result.text.hashCode()}",
                rawText = raw.result.text,
                format = raw.result.barcodeFormat,
                formatLabel = raw.result.barcodeFormat.displayLabel(),
                normalizedBounds = raw.normRect,
                parsed = parsed,
                safety = safety
            )
        }
    }

    /**
     * Real-time CameraX frame decoder supporting multi-code and rotated 1D barcodes.
     */
    fun decodeCameraFrame(
        image: ImageProxy,
        safetyEnabled: Boolean
    ): List<DetectedCode> {
        val yPlane = image.planes.firstOrNull() ?: return emptyList()
        val buffer = yPlane.buffer
        val data = ByteArray(buffer.remaining())
        buffer.get(data)

        val srcWidth = image.width
        val srcHeight = image.height
        val rotation = image.imageInfo.rotationDegrees

        val (rotatedData, rotW, rotH) = rotateLuminance(data, srcWidth, srcHeight, rotation, yPlane.rowStride)
        val source = PlanarYUVLuminanceSource(
            rotatedData,
            rotW,
            rotH,
            0,
            0,
            rotW,
            rotH,
            false
        )

        val collected = mutableListOf<RawDetection>()
        scanRegionMulti(source, 0, 0, rotW, rotH, collected, tryInverted = false)

        // Fallback 90-degree pass for vertical 1D barcodes if nothing read yet
        if (collected.isEmpty()) {
            val cw = rotate90(rotatedData, rotW, rotH)
            val cwSource = PlanarYUVLuminanceSource(cw, rotH, rotW, 0, 0, rotH, rotW, false)
            val cwReader = MultiFormatReader().apply { setHints(baseHints(tryHarder = true)) }
            runCatching { cwReader.decodeWithState(BinaryBitmap(HybridBinarizer(cwSource))) }.getOrNull()?.let { res ->
                val rawRect = computeBounds(res, 0, 0, rotH, rotW)
                // Map rotated coords back to portrait normalized space
                val mapped = RectF(
                    rawRect.top,
                    1f - rawRect.right,
                    rawRect.bottom,
                    1f - rawRect.left
                )
                collected += RawDetection(res, mapped)
            }
        }

        return deduplicate(collected).mapIndexed { idx, raw ->
            val parsed = contentParser.parse(raw.result.text, raw.result.barcodeFormat)
            val safety = safetyAnalyzer.analyze(raw.result.text, parsed, safetyEnabled)
            DetectedCode(
                id = "cam_${idx}_${raw.result.text.hashCode()}",
                rawText = raw.result.text,
                format = raw.result.barcodeFormat,
                formatLabel = raw.result.barcodeFormat.displayLabel(),
                normalizedBounds = raw.normRect,
                parsed = parsed,
                safety = safety
            )
        }
    }

    private fun scanRegionMulti(
        source: LuminanceSource,
        offsetX: Int,
        offsetY: Int,
        fullWidth: Int,
        fullHeight: Int,
        out: MutableList<RawDetection>,
        tryInverted: Boolean
    ) {
        val hints = baseHints(tryHarder = true)
        val binary = BinaryBitmap(HybridBinarizer(source))

        // Multi-QR pass
        runCatching {
            val qrMulti = QRCodeMultiReader()
            qrMulti.decodeMultiple(binary, hints)
        }.getOrNull()?.forEach { res ->
            out += RawDetection(res, computeBounds(res, offsetX, offsetY, fullWidth, fullHeight))
        }

        // Generic multi-barcode pass (1D + DataMatrix + Aztec + PDF417 + QR)
        runCatching {
            val multiReader = GenericMultipleBarcodeReader(MultiFormatReader().apply { setHints(hints) })
            multiReader.decodeMultiple(binary, hints)
        }.getOrNull()?.forEach { res ->
            out += RawDetection(res, computeBounds(res, offsetX, offsetY, fullWidth, fullHeight))
        }

        if (tryInverted) {
            val invertedBinary = BinaryBitmap(HybridBinarizer(source.invert()))
            runCatching {
                QRCodeMultiReader().decodeMultiple(invertedBinary, hints)
            }.getOrNull()?.forEach { res ->
                out += RawDetection(res, computeBounds(res, offsetX, offsetY, fullWidth, fullHeight))
            }
            runCatching {
                MultiFormatReader().apply { setHints(hints) }.decodeWithState(invertedBinary)
            }.getOrNull()?.let { res ->
                out += RawDetection(res, computeBounds(res, offsetX, offsetY, fullWidth, fullHeight))
            }
        }
    }

    private fun scanRegionSingleAndMulti(
        source: LuminanceSource,
        offsetX: Int,
        offsetY: Int,
        fullWidth: Int,
        fullHeight: Int,
        out: MutableList<RawDetection>
    ) {
        val hints = baseHints(tryHarder = true)
        val binary = BinaryBitmap(HybridBinarizer(source))
        runCatching {
            QRCodeMultiReader().decodeMultiple(binary, hints)
        }.getOrNull()?.forEach { res ->
            out += RawDetection(res, computeBounds(res, offsetX, offsetY, fullWidth, fullHeight))
        }

        runCatching {
            MultiFormatReader().apply { setHints(hints) }.decodeWithState(binary)
        }.getOrNull()?.let { res ->
            out += RawDetection(res, computeBounds(res, offsetX, offsetY, fullWidth, fullHeight))
        }

        val invBinary = BinaryBitmap(HybridBinarizer(source.invert()))
        runCatching {
            MultiFormatReader().apply { setHints(hints) }.decodeWithState(invBinary)
        }.getOrNull()?.let { res ->
            out += RawDetection(res, computeBounds(res, offsetX, offsetY, fullWidth, fullHeight))
        }
    }

    private fun computeBounds(
        result: Result,
        offsetX: Int,
        offsetY: Int,
        fullWidth: Int,
        fullHeight: Int
    ): RectF {
        val pts = result.resultPoints?.filterNotNull().orEmpty()
        if (pts.isEmpty()) {
            return RectF(
                offsetX.toFloat() / fullWidth,
                offsetY.toFloat() / fullHeight,
                min(1f, (offsetX + fullWidth * 0.3f) / fullWidth),
                min(1f, (offsetY + fullHeight * 0.2f) / fullHeight)
            )
        }
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (pt in pts) {
            val gx = offsetX + pt.x
            val gy = offsetY + pt.y
            minX = min(minX, gx)
            minY = min(minY, gy)
            maxX = max(maxX, gx)
            maxY = max(maxY, gy)
        }
        // ZXing QR finder centers lie inside the corners by ~3.5 modules (~14%), expand cleanly for visual enclosure
        val spanW = (maxX - minX).coerceAtLeast(fullWidth * 0.04f)
        val spanH = (maxY - minY).coerceAtLeast(fullHeight * 0.025f)
        val padX = spanW * 0.22f
        val padY = if (result.barcodeFormat == BarcodeFormat.QR_CODE ||
            result.barcodeFormat == BarcodeFormat.DATA_MATRIX ||
            result.barcodeFormat == BarcodeFormat.AZTEC
        ) {
            max(spanH, spanW) * 0.22f
        } else {
            max(spanH * 0.45f, fullHeight * 0.018f)
        }

        val left = ((minX - padX) / fullWidth).coerceIn(0.01f, 0.98f)
        val top = ((minY - padY) / fullHeight).coerceIn(0.01f, 0.98f)
        val right = ((maxX + padX) / fullWidth).coerceIn(left + 0.04f, 0.99f)
        val bottom = ((maxY + padY) / fullHeight).coerceIn(top + 0.03f, 0.99f)
        return RectF(left, top, right, bottom)
    }

    private fun deduplicate(items: List<RawDetection>): List<RawDetection> {
        val result = mutableListOf<RawDetection>()
        for (candidate in items) {
            val duplicate = result.any { existing ->
                existing.result.text == candidate.result.text &&
                    existing.result.barcodeFormat == candidate.result.barcodeFormat &&
                    centersClose(existing.normRect, candidate.normRect)
            }
            if (!duplicate) {
                result += candidate
            }
        }
        return result.sortedWith(compareBy({ it.normRect.top }, { it.normRect.left }))
    }

    private fun centersClose(a: RectF, b: RectF): Boolean {
        val dx = abs(a.centerX() - b.centerX())
        val dy = abs(a.centerY() - b.centerY())
        return dx < 0.14f && dy < 0.12f
    }

    private fun rotateLuminance(
        plane: ByteArray,
        width: Int,
        height: Int,
        rotation: Int,
        rowStride: Int
    ): Triple<ByteArray, Int, Int> {
        val compact = ByteArray(width * height)
        if (rowStride == width) {
            System.arraycopy(plane, 0, compact, 0, min(plane.size, compact.size))
        } else {
            for (y in 0 until height) {
                val srcOffset = y * rowStride
                if (srcOffset + width <= plane.size) {
                    System.arraycopy(plane, srcOffset, compact, y * width, width)
                }
            }
        }

        return when (rotation) {
            90 -> Triple(rotate90(compact, width, height), height, width)
            180 -> {
                val out = ByteArray(compact.size)
                for (i in compact.indices) out[compact.size - 1 - i] = compact[i]
                Triple(out, width, height)
            }
            270 -> {
                val out = ByteArray(compact.size)
                for (y in 0 until height) {
                    for (x in 0 until width) {
                        out[(width - 1 - x) * height + y] = compact[y * width + x]
                    }
                }
                Triple(out, height, width)
            }
            else -> Triple(compact, width, height)
        }
    }

    private fun rotate90(input: ByteArray, width: Int, height: Int): ByteArray {
        val out = ByteArray(input.size)
        for (y in 0 until height) {
            for (x in 0 until width) {
                out[x * height + (height - 1 - y)] = input[y * width + x]
            }
        }
        return out
    }

    private data class RawDetection(
        val result: Result,
        val normRect: RectF
    )
}

fun Bitmap.scaleDownIfLarge(maxDimension: Int = 1800): Bitmap {
    val maxSide = max(width, height)
    if (maxSide <= maxDimension) return this
    val scale = maxDimension.toFloat() / maxSide.toFloat()
    val matrix = Matrix().apply { postScale(scale, scale) }
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}
