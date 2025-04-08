package com.example.tflitetest

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.media.Image
import android.media.ImageReader
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.tflitetest.ml.Decoder
import com.example.tflitetest.ml.Experiment
import com.example.tflitetest.ml.Predictor
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.concurrent.ExecutorService
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Size
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.annotation.RequiresPermission
import java.io.ByteArrayOutputStream
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.min
import android.content.Intent
import android.os.Looper
import android.speech.RecognizerIntent
import org.tensorflow.lite.gpu.GpuDelegate
import kotlin.math.abs
import android.graphics.Canvas



object GlobalData {
    var isSupportedTofData: Boolean = false
}


class LiveCameraActivity : ComponentActivity(),TextToSpeech.OnInitListener {
    private lateinit var predictor: Predictor
    private lateinit var yuvToRgbConverter: YuvToRgbConverter
    private lateinit var tts: TextToSpeech
    private val CAMERA_PERMISSION_CODE = 100
    private lateinit var cameraManager: CameraManager
    private var cameraDevice: CameraDevice? = null
    private var tofCameraDevice: CameraDevice? = null
    private lateinit var cameraCaptureSession: CameraCaptureSession
    private lateinit var tofCaptureSession: CameraCaptureSession
    private lateinit var cameraImageReader: ImageReader
    private lateinit var tofImageReader: ImageReader
    private lateinit var backgroundHandler: Handler
    private lateinit var backgroundThread: HandlerThread
    private lateinit var imageView: ImageView
    private lateinit var cameraPreview: SurfaceView
    private lateinit var previewSurface: Surface
    private var isRegularBitmapReady = false
    private var isTofDepthDataReady = false
    private var regularBitmap: Bitmap? = null
    private var tofDepthData: ShortArray? = null
    private val YUV_420_888 = ImageFormat.YUV_420_888
    private val DEPTH16 = ImageFormat.DEPTH16
    private val NV21 = ImageFormat.NV21

    private var isSpeechRecognitionComplete = false
    private var isListening = false
    private val SPEECH_REQUEST_CODE = 1001
    private lateinit var objectSpeech: ObjectSpeech


    private var lastImageProcessingTime: Long = 0
    private var lastInferenceTime: Long = 0

    private val rotationMatrix = Matrix().apply { postRotate(90f) }
    private var rotatedBitmap: Bitmap? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_live)

        //TODO:

        GlobalData.isSupportedTofData = false
        isSpeechRecognitionComplete = true


        imageView = findViewById(R.id.imageView)
        cameraPreview = findViewById(R.id.cameraPreview)
        cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
        cameraPreview.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                previewSurface = holder.surface
                if (checkCameraPermission()) {
                    setupCamera()
                } else {
                    requestCameraPermission()
                }
            }
            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            }
            override fun surfaceDestroyed(holder: SurfaceHolder) {
            }

        })
        yuvToRgbConverter = YuvToRgbConverter(this)
        tts = TextToSpeech(this, this)

        val tfliteModel = loadModelFile("model.tflite")
        val gpuDelegate = GpuDelegate()
        val options = Interpreter.Options().addDelegate(gpuDelegate)
        val interpreter = Interpreter(tfliteModel, options)


        val experiment = Experiment(
            numClasses = 80,
            testConf = 0.3f,
            nmsthre = 0.45f,
            testSize = intArrayOf(416, 416)
        )
        val COCO_CLASSES = listOf(
            "person", "bicycle", "car", "motorcycle", "airplane", "bus",
            "train", "truck", "boat", "traffic light", "fire hydrant", "stop sign",
            "parking meter", "bench", "bird", "cat", "dog", "horse", "sheep", "cow",
            "elephant", "bear", "zebra", "giraffe", "backpack", "umbrella", "handbag",
            "tie", "suitcase", "frisbee", "skis", "snowboard", "sports ball",
            "kite", "baseball bat", "baseball glove", "skateboard", "surfboard",
            "tennis racket", "bottle", "wine glass", "cup", "fork", "knife",
            "spoon", "bowl", "banana", "apple", "sandwich", "orange", "broccoli",
            "carrot", "hot dog", "pizza", "donut", "cake", "chair", "couch",
            "potted plant", "bed", "dining table", "toilet", "tv", "laptop",
            "mouse", "remote", "keyboard", "cell phone", "microwave", "oven",
            "toaster", "sink", "refrigerator", "book", "clock", "vase",
            "scissors", "teddy bear", "hair drier", "toothbrush"
        )
        predictor = Predictor(
            tflite = interpreter,
            exp = experiment,
            clsNames = COCO_CLASSES,
            decoder = Decoder(),
            device = "cpu",
            fp16 = false
        )
    }


    // Start speech recognition to listen for "URDU" or "CONTINUE"
    fun startSpeechRecognition() {
        if (!isListening) {
            isListening = true
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US.toString())
                putExtra(
                    RecognizerIntent.EXTRA_PROMPT,
                    "Does your device support depth estimation? Answer with yes or no."
                )
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            startActivityForResult(intent, SPEECH_REQUEST_CODE)
        }
    }

    // Handle the speech recognition result
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
       super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == SPEECH_REQUEST_CODE && resultCode == RESULT_OK) {
            val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            results?.let {
                if (it.isNotEmpty()) {
                    when (it[0].uppercase(Locale.US)) { // Case-insensitive comparison
                        "YES" -> {
                           GlobalData.isSupportedTofData=true
                            isListening = false // Stop listening after switching to Urdu
                            isSpeechRecognitionComplete = true
                        }

                        "NO" -> {
                            GlobalData.isSupportedTofData=false
                            isListening = false // Stop listening after choosing English
                            isSpeechRecognitionComplete = true
                        }

                        else -> {
                            objectSpeech.speak("Please say 'URDU' to switch to Urdu or 'CONTINUE' to stay in English.")
                            Handler(Looper.getMainLooper()).postDelayed({
                                startSpeechRecognition() // Restart listening if invalid input
                            }, 2000)
                        }
                    }
                }
            }
        } else {
            // Restart listening if no result or error, unless explicitly stopped
            if (isListening) {
                Handler(Looper.getMainLooper()).postDelayed({
                    startSpeechRecognition()
                }, 1000)
            }
        }
    }




    private fun checkCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }
    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.CAMERA),
            CAMERA_PERMISSION_CODE
        )
    }
    @RequiresPermission(Manifest.permission.CAMERA)
    private fun setupCamera() {
        try {
            startBackgroundThread()
            setupRegularCamera { bitmap ->
                val startTime = System.currentTimeMillis()
                regularBitmap = bitmap
                isRegularBitmapReady = true
                tryRunYolox()
                val endTime = System.currentTimeMillis()
                val latency = endTime - startTime
                Log.d("RegularCamera", "Bitmap processing latency: $latency ms")
            }


            if (GlobalData.isSupportedTofData) {
                setupToFCamera { depthData ->
                    val startTime = System.currentTimeMillis()
                    tofDepthData = depthData
                    isTofDepthDataReady = true
                    tryRunYolox()
                    val endTime = System.currentTimeMillis()
                    val latency = endTime - startTime
                    Log.d("TimeLog", "LiveCamera Activity: Depth data setup latency: $latency ms")
                }
            }


        } catch (e: Exception) {
            Log.e("CameraSetup", "Error in setupCamera: ${e.message}", e)
        }
    }

    private fun tryRunYolox() {
        if (isRegularBitmapReady && regularBitmap != null) {
            // Log interval between inferences
            val currentTime = System.currentTimeMillis()
            if (lastInferenceTime != 0L) {
                val interval = currentTime - lastInferenceTime
                Log.d("TimeLog", "LiveCamera Activity: Time between inferences: $interval ms")
            }
            lastInferenceTime = currentTime

            // Inference
            val startTimeInference = System.currentTimeMillis()
            val (outputs, imgInfo) = predictor.inference(regularBitmap!!)
            val endTimeInference = System.currentTimeMillis()
            val latencyInference = endTimeInference - startTimeInference
            Log.d("TimeLog", "LiveCamera Activity: Inference latency: $latencyInference ms")

            // Visualization
            val startTimeVisual = System.currentTimeMillis()
            val resultBitmap = predictor.visual(outputs, imgInfo, predictor.confThre)
            val endTimeVisual = System.currentTimeMillis()
            val latencyVisual = endTimeVisual - startTimeVisual
            Log.d("TimeLog", "LiveCamera Activity: Visualization latency: $latencyVisual ms")

            speakFirstDetection(outputs, tofDepthData)

            // UI Update
            runOnUiThread {
                val startTimeUI = System.currentTimeMillis()
                imageView.setImageBitmap(resultBitmap)
                val endTimeUI = System.currentTimeMillis()
                val latencyUI = endTimeUI - startTimeUI
                Log.d("TimeLog", "LiveCamera Activity: UI update latency: $latencyUI ms")
            }

            regularBitmap = null
            tofDepthData = null
            isRegularBitmapReady = false
            isTofDepthDataReady = false
        }
    }



    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("CameraBackground").apply { start() }
        backgroundHandler = Handler(backgroundThread.looper)
    }

    private fun stopBackgroundThread() {
        backgroundThread.quitSafely()
        try {
            backgroundThread.join()
        } catch (e: InterruptedException) {
            Log.e("Camera", "Background thread interrupted: ${e.message}")
        }
    }

    @RequiresPermission(Manifest.permission.CAMERA)
    private fun setupRegularCamera(onImageReady: (Bitmap) -> Unit) {
        try {
            for (cameraId in cameraManager.getCameraIdList()) {
                val characteristics = cameraManager.getCameraCharacteristics(cameraId)
                val capabilities = characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
                if (capabilities != null && !capabilities.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_DEPTH_OUTPUT)) {
                    Log.d("Camera", "Found regular camera with ID: $cameraId")
                    val configMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    if (configMap == null) {
                        Log.e("Camera", "SCALER_STREAM_CONFIGURATION_MAP is null!")
                        return
                    }
                    val supportedSizes = configMap.getOutputSizes(ImageFormat.YUV_420_888)
                    Log.d("Supported Sizes", supportedSizes.joinToString { "${it.width}x${it.height}" })

                    if (supportedSizes.isNullOrEmpty()) {
                        Log.e("Camera", "No supported YUV sizes found!")
                        return
                    }

                    // Define preferred sizes
                    val desiredSize = Size(640, 480)
                    val fallbackSize = Size(1088, 1088)

                    // Select the size: 640x480 if available, else 1088x1088, else closest to 416
                    val selectedSize = if (supportedSizes.contains(desiredSize)) {
                        desiredSize
                    } else if (supportedSizes.contains(fallbackSize)) {
                        fallbackSize
                    } else {
                        // Fallback: size where min(width, height) is closest to 416
                        supportedSizes.minByOrNull { abs(min(it.width, it.height) - 416) } ?: supportedSizes.first()
                    }

                    Log.d("Camera", "Selected size: ${selectedSize.width}x${selectedSize.height}")

                    // Set up ImageReader with the selected size
                    cameraImageReader = ImageReader.newInstance(
                        selectedSize.width, selectedSize.height, ImageFormat.YUV_420_888, 2
                    )

                    cameraImageReader.setOnImageAvailableListener({ reader ->
                        val startTimeTotal = System.currentTimeMillis()

                        // Log interval between consecutive image captures
                        val currentTime = System.currentTimeMillis()
                        if (lastImageProcessingTime != 0L) {
                            val interval = currentTime - lastImageProcessingTime
                            Log.d("TimeLog", "LiveCamera Activity: Time between image captures: $interval ms")
                        }
                        lastImageProcessingTime = currentTime

                        val image = reader.acquireLatestImage()
                        image?.let {
                            // YUV to RGB conversion
                            val startTimeConversion = System.currentTimeMillis()
                            val bitmap = yuvToRgbConverter.yuvToRgb(it)
                            val endTimeConversion = System.currentTimeMillis()
                            Log.d("TimeLog", "LiveCamera Activity: YUV to RGB conversion latency: ${endTimeConversion - startTimeConversion} ms")

                            // Crop to square
                            val startTimeCrop = System.currentTimeMillis()
                            val croppedBitmap = cropToSquare(bitmap)
                            val endTimeCrop = System.currentTimeMillis()
                            Log.d("TimeLog", "LiveCamera Activity: Crop to square latency: ${endTimeCrop - startTimeCrop} ms")

                            // Rotate bitmap
// Rotate bitmap
                            val startTimeRotate = System.currentTimeMillis()
                            val rotatedBitmap = rotateBitmap(croppedBitmap)
                            val endTimeRotate = System.currentTimeMillis()
                            Log.d("TimeLog", "LiveCamera Activity: Rotate bitmap latency: ${endTimeRotate - startTimeRotate} ms")
                            onImageReady(rotatedBitmap)
                            it.close()
                        }
                        val endTimeTotal = System.currentTimeMillis()
                        Log.d("TimeLog", "LiveCamera Activity: Total image processing latency: ${endTimeTotal - startTimeTotal} ms")
                    }, backgroundHandler)

                    cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                        override fun onOpened(camera: CameraDevice) {
                            cameraDevice = camera
                            createCameraCaptureSession()
                        }

                        override fun onDisconnected(camera: CameraDevice) {
                            camera.close()
                            cameraDevice = null
                        }

                        override fun onError(camera: CameraDevice, error: Int) {
                            camera.close()
                            cameraDevice = null
                            Log.e("Camera", "Camera error: $error")
                        }
                    }, backgroundHandler)
                    return
                }
            }
        } catch (e: CameraAccessException) {
            Log.e("Camera", "Camera access exception: ${e.message}")
        }
    }

    fun cropToSquare(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val squareSize = min(width, height) // Use the smaller dimension

        val xOffset = (width - squareSize) / 2
        val yOffset = (height - squareSize) / 2

        return Bitmap.createBitmap(bitmap, xOffset, yOffset, squareSize, squareSize)
    }

    private fun rotateBitmap(bitmap: Bitmap): Bitmap {
        // Reuse bitmap if possible
        val width = bitmap.width
        val height = bitmap.height

        if (rotatedBitmap == null || rotatedBitmap?.width != height || rotatedBitmap?.height != width) {
            // Recycle previous bitmap if it exists but dimensions don't match
            rotatedBitmap?.recycle()
            rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height, rotationMatrix, false)
        } else {
            // Reuse existing bitmap
            Canvas(rotatedBitmap!!).apply {
                rotate(90f, height / 2f, width / 2f)
                drawBitmap(bitmap, 0f, 0f, null)
            }
        }

        return rotatedBitmap!!
    }

    @RequiresPermission(Manifest.permission.CAMERA)
    private fun setupToFCamera(onDepthReady: (ShortArray) -> Unit) {
        try {
            for (cameraId in cameraManager.getCameraIdList()) {
                val characteristics = cameraManager.getCameraCharacteristics(cameraId)
                val capabilities = characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
                if (capabilities != null && capabilities.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_DEPTH_OUTPUT)) {
                    Log.d("Camera", "Found ToF sensor with ID: $cameraId")
                    val configMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    val depthSizes = configMap?.getOutputSizes(DEPTH16) // Use numeric constant
                    val selectedSize = depthSizes?.maxByOrNull { it.width * it.height } ?: Size(240, 180)
                    tofImageReader = ImageReader.newInstance(selectedSize.width, selectedSize.height, DEPTH16, 2) // Use numeric constant
                    tofImageReader.setOnImageAvailableListener({ reader ->
                        val image = reader.acquireLatestImage()
                        image?.let {
                            val depthData = extractDepthData(it)
                            val scaledDepthData = scaleDepthData(depthData, it.width, it.height, 416, 416)
                            onDepthReady(scaledDepthData)
                            it.close()
                        }
                    }, backgroundHandler)

                    cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                        override fun onOpened(camera: CameraDevice) {
                            tofCameraDevice = camera
                            createToFCaptureSession()
                        }

                        override fun onDisconnected(camera: CameraDevice) {
                            camera.close()
                            tofCameraDevice = null
                        }

                        override fun onError(camera: CameraDevice, error: Int) {
                            camera.close()
                            tofCameraDevice = null
                            Log.e("Camera", "ToF error: $error")
                        }
                    }, backgroundHandler)
                    return
                }
            }
        } catch (e: CameraAccessException) {
            Log.e("Camera", "ToF access exception: ${e.message}")
        }
    }

    private fun createCameraCaptureSession() {
        cameraDevice?.let { device ->
            try {
                val captureRequestBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                captureRequestBuilder.addTarget(previewSurface)
                captureRequestBuilder.addTarget(cameraImageReader.surface)
                device.createCaptureSession(
                    listOf(previewSurface, cameraImageReader.surface),
                    object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(session: CameraCaptureSession) {
                            cameraCaptureSession = session
                            Log.d("Camera", "Regular camera capture session configured")
                            session.setRepeatingRequest(captureRequestBuilder.build(), null, backgroundHandler)
                        }
                        override fun onConfigureFailed(session: CameraCaptureSession) {
                            Log.e("Camera", "Regular camera capture session configuration failed")
                        }
                    },
                    backgroundHandler
                )
            } catch (e: CameraAccessException) {
                Log.e("Camera", "Camera session creation failed: ${e.message}")
            }
        }
    }

    private fun createToFCaptureSession() {
        tofCameraDevice?.let { device ->
            try {
                val captureRequestBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                captureRequestBuilder.addTarget(tofImageReader.surface)
                device.createCaptureSession(
                    listOf(tofImageReader.surface),
                    object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(session: CameraCaptureSession) {
                            tofCaptureSession = session
                            session.setRepeatingRequest(captureRequestBuilder.build(), null, backgroundHandler)
                        }
                        override fun onConfigureFailed(session: CameraCaptureSession) {
                            Log.e("Camera", "ToF capture session configuration failed")
                        }
                    },
                    backgroundHandler
                )
            } catch (e: CameraAccessException) {
                Log.e("Camera", "ToF session creation failed: ${e.message}")
            }
        }
    }

    private fun extractDepthData(image: Image): ShortArray {
        val depthBuffer = image.planes[0].buffer
        val depthArray = ShortArray(depthBuffer.remaining() / 2)
        depthBuffer.asShortBuffer().get(depthArray)
        return depthArray
    }

    private fun scaleDepthData(
        depthData: ShortArray,
        depthWidth: Int,
        depthHeight: Int,
        targetWidth: Int,
        targetHeight: Int
    ): ShortArray {
        val scaledData = ShortArray(targetWidth * targetHeight)
        val widthRatio = depthWidth.toFloat() / targetWidth.toFloat()
        val heightRatio = depthHeight.toFloat() / targetHeight.toFloat()
        for (y in 0 until targetHeight) {
            for (x in 0 until targetWidth) {
                val srcX = x * widthRatio
                val srcY = y * heightRatio
                val x1 = srcX.toInt().coerceIn(0, depthWidth - 1)
                val y1 = srcY.toInt().coerceIn(0, depthHeight - 1)
                val x2 = (x1 + 1).coerceIn(0, depthWidth - 1)
                val y2 = (y1 + 1).coerceIn(0, depthHeight - 1)
                val wx = srcX - x1
                val wy = srcY - y1
                val v11 = depthData[y1 * depthWidth + x1].toInt() and 0x1FFF
                val v12 = depthData[y1 * depthWidth + x2].toInt() and 0x1FFF
                val v21 = depthData[y2 * depthWidth + x1].toInt() and 0x1FFF
                val v22 = depthData[y2 * depthWidth + x2].toInt() and 0x1FFF
                val interpolatedValue = (
                        v11 * (1 - wx) * (1 - wy) +
                                v12 * wx * (1 - wy) +
                                v21 * (1 - wx) * wy +
                                v22 * wx * wy
                        )
                val scaledValue = (interpolatedValue * 10000.0 / 0x1FFF).toInt()
                    .coerceIn(0, 10000)
                scaledData[y * targetWidth + x] = scaledValue.toShort()
            }
        }
        return scaledData
    }

    private var isSpeaking = false
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val res = tts.setLanguage(Locale.US)
            if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("LiveCameraActivity", "TTS language not supported.")
            } else {
                Log.d("LiveCameraActivity", "TTS initialized successfully.")
                tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        isSpeaking = true
                    }
                    override fun onDone(utteranceId: String?) {
                        isSpeaking = false
                    }
                    override fun onError(utteranceId: String?) {
                        isSpeaking = false
                    }
                })
            }
        } else {
            Log.e("LiveCameraActivity", "TTS initialization failed.")
        }
    }

    private fun speakFirstDetection(outputs: Any?, tofDepthData:ShortArray?) {
        Log.d("Check", "NewCheck5")
        if (!::tts.isInitialized || outputs == null || !isSpeechRecognitionComplete) return
       var roundedDepth: Double= 10.0
        var className:String="defaultObject"
        if (isSpeaking) {
            return
        }
        val detections = outputs as? List<FloatArray> ?: return
        if (detections.isEmpty()) return
        val firstDet = detections[0]
        if(GlobalData.isSupportedTofData==true && tofDepthData == null) return
        if(GlobalData.isSupportedTofData==true && tofDepthData !=null) {
            val centerX = (firstDet[0] + firstDet[2]) / 2
            val centerY = (firstDet[1] + firstDet[3]) / 2

            if (centerX >= 5 && centerX < 411 && centerY >= 5 && centerY < 411) {
                var depthSum = 0.0
                var count = 0

                for (i in -5..4) {
                    for (j in -5..4) {
                        val index = ((centerY + i) * 416 + (centerX + j)).toInt()
                        depthSum += ((tofDepthData.get(index).toInt()) and 0xFFFF)
                        count++
                    }
                }

                val avgDepth = depthSum / count
                roundedDepth = (avgDepth / 1000 * 10).roundToInt() / 10.0
            }
        }
        val clsId = firstDet[6].toInt()
        val names = predictor.clsNames
        className = if (names != null && clsId in names.indices) {
            names[clsId]
        } else {
        clsId.toString()
        }
        val spokenText = "$className is  $roundedDepth metres away."
        tts.speak(spokenText, TextToSpeech.QUEUE_FLUSH, null, "detectedObject")
    }


    private fun Bitmap.rotate(degrees: Float): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    }

    private fun loadModelFile(modelName: String): MappedByteBuffer {
        val fileDescriptor = assets.openFd(modelName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, fileDescriptor.startOffset, fileDescriptor.declaredLength)
    }

    // TTS ADDED: Clean up TTS
    override fun onDestroy() {
        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
//        cameraExecutor.shutdown()
        cameraDevice?.close()
        tofCameraDevice?.close()
        stopBackgroundThread()
        super.onDestroy()
    }
}
