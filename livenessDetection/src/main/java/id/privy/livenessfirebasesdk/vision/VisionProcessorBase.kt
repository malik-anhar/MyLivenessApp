package id.privy.livenessfirebasesdk.vision

import android.graphics.Bitmap
import androidx.annotation.GuardedBy
import com.google.android.gms.tasks.Task
import com.google.android.gms.vision.Frame
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import com.google.firebase.ml.vision.common.FirebaseVisionImageMetadata
import id.privy.livenessfirebasesdk.common.BitmapUtils
import id.privy.livenessfirebasesdk.common.FrameMetadata
import id.privy.livenessfirebasesdk.common.GraphicOverlay
import id.privy.livenessfirebasesdk.common.VisionImageProcessor
import java.nio.ByteBuffer

/**
 * Abstract base class for ML Kit frame processors. Subclasses need to implement [ ][.onSuccess] to define what they want to with
 * the detection results and [.detectInImage] to specify the detector
 * object.
 *
 * @param <T> The type of the detected feature.
</T> */
abstract class VisionProcessorBase<T> : VisionImageProcessor {

    // To keep the latest images and its metadata.
    @GuardedBy("this")
    private var latestImage: ByteBuffer? = null

    @GuardedBy("this")
    private var latestImageMetaData: FrameMetadata? = null

    // To keep the images and metadata in process.
    @GuardedBy("this")
    private var processingImage: ByteBuffer? = null

    @GuardedBy("this")
    private var processingMetaData: FrameMetadata? = null

    @Synchronized
    override fun process(
            data: ByteBuffer, frameMetadata: FrameMetadata, graphicOverlay: GraphicOverlay) {
        latestImage = data
        latestImageMetaData = frameMetadata
        if (processingImage == null && processingMetaData == null) {
            processLatestImage(graphicOverlay)
        }
    }

    // Bitmap version
    override fun process(bitmap: Bitmap, graphicOverlay: GraphicOverlay) {
        detectInVisionImage(null, FirebaseVisionImage.fromBitmap(bitmap), null,
                graphicOverlay)/* bitmap */
    }

    @Synchronized
    private fun processLatestImage(graphicOverlay: GraphicOverlay) {
        processingImage = latestImage
        processingMetaData = latestImageMetaData
        latestImage = null
        latestImageMetaData = null
        if (processingImage != null && processingMetaData != null) {
            processImage(processingImage!!, processingMetaData!!, graphicOverlay)
        }
    }

    private fun processImage(
            data: ByteBuffer, frameMetadata: FrameMetadata,
            graphicOverlay: GraphicOverlay) {
        val metadata = FirebaseVisionImageMetadata.Builder()
                .setFormat(FirebaseVisionImageMetadata.IMAGE_FORMAT_NV21)
                .setWidth(frameMetadata.width)
                .setHeight(frameMetadata.height)
                .setRotation(frameMetadata.rotation)
                .build()

        val bitmap = BitmapUtils.getBitmap(data, frameMetadata)
        detectInVisionImage(
                bitmap, FirebaseVisionImage.fromByteBuffer(data, metadata), frameMetadata,
                graphicOverlay)
    }

    private fun detectInVisionImage(
            originalCameraImage: Bitmap?,
            image: FirebaseVisionImage,
            metadata: FrameMetadata?,
            graphicOverlay: GraphicOverlay) {
        detectInImage(image)
                .addOnSuccessListener { results ->
                    this@VisionProcessorBase.onSuccess(originalCameraImage, results,
                            metadata!!,
                            graphicOverlay)
                    processLatestImage(graphicOverlay)
                }
                .addOnFailureListener { e -> this@VisionProcessorBase.onFailure(e) }
    }

    override fun stop() {}

    protected abstract fun detectInImage(image: FirebaseVisionImage): Task<T>

    /**
     * Callback that executes with a successful detection result.
     *
     * @param originalCameraImage hold the original image from camera, used to draw the background
     * image.
     */
    protected abstract fun onSuccess(
            originalCameraImage: Bitmap?,
            results: T,
            frameMetadata: FrameMetadata,
            graphicOverlay: GraphicOverlay)

    protected abstract fun onFailure(e: Exception)
}
