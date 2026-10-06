package com.mbidesign.terminal

import android.content.Context
import android.graphics.Bitmap
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import org.opencv.objdetect.FaceDetectorYN
import org.opencv.objdetect.FaceRecognizerSF
import java.io.File
import java.security.MessageDigest

class FaceEngine(context: Context) {
    private val detector: FaceDetectorYN
    private val recognizer: FaceRecognizerSF
    init {
        check(OpenCVLoader.initLocal()) { "Не може да се вклучи моделот за лице на овој уред." }
        fun model(name: String, expected: String): String {
            val dest = File(context.filesDir, name)
            if (!dest.exists()) {
                val tmp = File(context.filesDir, "$name.tmp")
                context.assets.open(name).use { input -> tmp.outputStream().use { input.copyTo(it) } }
                check(tmp.renameTo(dest)) { "Не може да се зачува моделот за лице." }
            }
            val digest = MessageDigest.getInstance("SHA-256")
            dest.inputStream().use { input -> val buffer = ByteArray(65536); while (true) {
                val read = input.read(buffer); if (read < 0) break; digest.update(buffer, 0, read)
            } }
            val actual = digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
            check(actual == expected) { "Моделот за лице е оштетен. Инсталирај ја проверената APK." }
            return dest.absolutePath
        }
        val detection = model("yunet.onnx", "8f2383e4dd3cfbb4553ea8718107fc0423210dc964f9f4280604804ed2552fa4")
        val recognition = model("sface.onnx", "0ba9fbfa01b5270c96627c4ef784da859931e02f04419c829e83484087c34e79")
        detector = FaceDetectorYN.create(detection, "", Size(640.0, 480.0), .9f, .3f, 5000)
        recognizer = FaceRecognizerSF.create(recognition, "")
    }
    @Synchronized fun feature(bitmap: Bitmap): FloatArray? {
        val rgba = Mat(); val bgr = Mat(); val faces = Mat(); val aligned = Mat(); val output = Mat()
        try {
            Utils.bitmapToMat(bitmap, rgba)
            Imgproc.cvtColor(rgba, bgr, Imgproc.COLOR_RGBA2BGR)
            detector.setInputSize(Size(bitmap.width.toDouble(), bitmap.height.toDouble()))
            detector.detect(bgr, faces)
            if (faces.rows() != 1) return null
            val detected = faces.row(0)
            try { recognizer.alignCrop(bgr, detected, aligned) } finally { detected.release() }
            recognizer.feature(aligned, output)
            if (output.total() != 128L || output.type() != CvType.CV_32F) return null
            return FloatArray(128).also { output.get(0, 0, it) }
        } finally { rgba.release(); bgr.release(); faces.release(); aligned.release(); output.release() }
    }
    companion object {
        fun similarity(a: FloatArray, b: FloatArray): Float {
            if (a.size != 128 || b.size != 128) return -1f
            var product = 0.0; var aa = 0.0; var bb = 0.0
            for (i in a.indices) { product += a[i] * b[i]; aa += a[i] * a[i]; bb += b[i] * b[i] }
            if (aa <= 0 || bb <= 0 || !product.isFinite()) return -1f
            return (product / kotlin.math.sqrt(aa * bb)).toFloat()
        }
        fun matches(feature: FloatArray, samples: List<FloatArray>, threshold: Float): Boolean =
            samples.count { similarity(feature, it) >= threshold } >= 2
    }
}
