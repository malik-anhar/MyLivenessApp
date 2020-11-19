package com.example.mylivenessapp

import android.Manifest
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.View
import android.view.WindowManager
import androidx.lifecycle.Observer
import id.privy.livenessfirebasesdk.common.CameraSource
import id.privy.livenessfirebasesdk.common.CameraSourcePreview
import id.privy.livenessfirebasesdk.common.GraphicOverlay
import id.privy.livenessfirebasesdk.common.PermissionUtil
import id.privy.livenessfirebasesdk.event.LivenessEventProvider
import id.privy.livenessfirebasesdk.vision.VisionDetectionProcessor
import kotlinx.android.synthetic.main.activity_main.*
import java.io.IOException

class MainActivity : AppCompatActivity() {
    private val successText = "Silahkan lihat ke kamera lagi untuk mengambil foto"
    private val motionInstructions = arrayOf("Lihat ke kiri", "Lihat ke kanan")

    internal var graphicOverlay: GraphicOverlay? = null
    internal var preview: CameraSourcePreview? = null

    private var visionDetectionProcessor: VisionDetectionProcessor? = null
    private var cameraSource: CameraSource? = null

    private var success = false
    private var isDebug = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initView()
        startLiveness()

        LivenessEventProvider.getEventLiveData().observe(this, Observer {
            it?.let {
                when {
                    it.getType() == LivenessEventProvider.LivenessEvent.Type.HeadShake -> {
                        onHeadShakeEvent()
                    }

                    it.getType() == LivenessEventProvider.LivenessEvent.Type.Default -> {
                        onDefaultEvent()
                        //set normal brightness
                        val lp = this.window.attributes
                        lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                        this.window.attributes = lp
                    }
                }
            }
        })
    }

    private fun initView(){
        preview = findViewById(R.id.cameraPreview)
        graphicOverlay = findViewById(R.id.faceOverlay)

        instructions.text = "Lihat ke kamera dan tempatkan wajah pada overlay"
    }

    private fun restartLiveness(){
        preview?.stop()
        LivenessEventProvider.getEventLiveData().postValue(null)
        startLiveness()
        startCameraSource()
    }

    private fun startLiveness(){
        success = false
        if (PermissionUtil.with(this).isCameraPermissionGranted) {
            createCameraSource()
            startHeadShakeChallenge()
        }
        else {
            PermissionUtil.requestPermission(this, 1, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.CAMERA)
        }
    }

    private fun startHeadShakeChallenge() {
        visionDetectionProcessor?.setVerificationStep(1)
    }

    @Suppress("DEPRECATION")
    private fun onHeadShakeEvent() {
        if (!success) {
            success = true

            //set max brightness
            val lp = this.window.attributes
            lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
            this.window.attributes = lp

            motionInstruction.text = successText

            Handler().postDelayed({
                visionDetectionProcessor?.setChallengeDone(true)
            }, 1000)


        }
    }

    @Suppress("DEPRECATION")
    private fun onDefaultEvent() {
        if (success) {
            progress_bar.visibility = View.VISIBLE
            Handler().postDelayed({
                cameraSource?.takePicture(null,
                    com.google.android.gms.vision.CameraSource.PictureCallback {
                        restartLiveness()
                        progress_bar.visibility = View.GONE
                    })
            }, 1000)
        }
    }

    override fun onResume() {
        super.onResume()
        startCameraSource()
    }

    override fun onPause() {
        super.onPause()
        preview?.stop()
        LivenessEventProvider.getEventLiveData().postValue(null)
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraSource?.release()
    }

    private fun createCameraSource() {
        // If there's no existing cameraSource, create one.
        if (cameraSource == null) {
            cameraSource = CameraSource(this, graphicOverlay)
            cameraSource?.setFacing(CameraSource.CAMERA_FACING_FRONT)
        }

        val motion = VisionDetectionProcessor.Motion.values()[java.util.Random().nextInt(
            VisionDetectionProcessor.Motion.values().size)]

        when (motion) {
            VisionDetectionProcessor.Motion.Left -> {
                motionInstruction.text = this.motionInstructions[0]
            }

            VisionDetectionProcessor.Motion.Right -> {
                motionInstruction.text = this.motionInstructions[1]
            }
        }

        visionDetectionProcessor = VisionDetectionProcessor()
        visionDetectionProcessor?.apply {
            isSimpleLiveness(true, this@MainActivity, motion)
            isDebugMode(isDebug)
        }

        cameraSource?.setMachineLearningFrameProcessor(visionDetectionProcessor)
    }

    private fun startCameraSource() {
        if (cameraSource != null) {
            try {
                if (preview == null) {
                    Log.d("CAMERA SOURCE", "resume: Preview is null")
                }
                preview?.start(cameraSource, graphicOverlay)
            } catch (e: IOException) {
                Log.e("CAMERA SOURCE", "CAMERA SOURCE", e)
                cameraSource?.release()
                cameraSource = null
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        createCameraSource()
    }
}