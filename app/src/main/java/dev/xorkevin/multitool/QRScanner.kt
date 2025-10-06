@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalStdlibApi::class,
    ExperimentalCoroutinesApi::class,
    ExperimentalPermissionsApi::class
)

package dev.xorkevin.multitool

import android.graphics.ImageFormat
import android.util.Size
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraState
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.viewfinder.compose.MutableCoordinateTransformer
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.takeOrElse
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.setFrom
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.google.zxing.BinaryBitmap
import com.google.zxing.ChecksumException
import com.google.zxing.FormatException
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@Composable
fun QRScannerTool() = ViewModelScope(QRScannerToolViewModel::class) {
    val qrScannerToolViewModel: QRScannerToolViewModel = scopedViewModel()
    val scrollState = rememberScrollState()
    var scanOutput by qrScannerToolViewModel.data.collectAsStateWithLifecycle()
    Column(modifier = Modifier.verticalScroll(scrollState)) {
        QRScannerLauncher(
            onScan = { scanOutput = it ?: "" },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp, 8.dp),
        ) {
            Text(text = "Scan")
        }
        Text(
            text = "QR data", modifier = Modifier
                .padding(16.dp, 8.dp)
                .fillMaxWidth()
        )
        Text(
            text = scanOutput,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .padding(16.dp, 8.dp)
                .fillMaxWidth()
        )
    }
}

class QRScannerToolViewModel : ViewModel() {
    val data = MutableViewModelStateFlow("")
}

@Composable
fun QRScannerLauncher(
    modifier: Modifier = Modifier,
    onScan: (value: String?) -> Unit = {},
    content: @Composable (RowScope.() -> Unit),
) = ViewModelScope(QRScannerViewModel::class) {
    val qrScannerViewModel: QRScannerViewModel = scopedViewModel()
    val cameraPermissionState = rememberPermissionState(android.Manifest.permission.CAMERA)
    var scanEnabled by qrScannerViewModel.scanEnabled.collectAsStateWithLifecycle()
    Button(
        onClick = { scanEnabled = true },
        modifier = modifier,
        content = content,
    )
    if (scanEnabled) {
        Dialog(
            onDismissRequest = { scanEnabled = false },
            properties = DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = true,
                usePlatformDefaultWidth = false,
            ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                if (cameraPermissionState.status.isGranted) {
                    QRScanner(onScan = {
                        scanEnabled = false
                        onScan(it)
                    }, onDismiss = { scanEnabled = false })
                } else {
                    val scrollState = rememberScrollState()
                    Column(modifier = Modifier.verticalScroll(scrollState)) {
                        if (cameraPermissionState.status.shouldShowRationale) {
                            Text(
                                text = "Camera permission is needed to scan qr code",
                                modifier = Modifier
                                    .padding(16.dp, 8.dp)
                                    .fillMaxWidth()
                            )
                        }
                        Button(
                            onClick = { cameraPermissionState.launchPermissionRequest() },
                            modifier = Modifier
                                .padding(16.dp, 8.dp)
                                .fillMaxWidth()
                        ) {
                            Text(text = "Grant camera permission")
                        }
                        Button(
                            onClick = { scanEnabled = false },
                            modifier = Modifier
                                .padding(16.dp, 8.dp)
                                .fillMaxWidth()
                        ) {
                            Text(text = "Cancel")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun QRScanner(onScan: (value: String?) -> Unit, onDismiss: () -> Unit) {
    val qrScannerViewModel: QRScannerViewModel = scopedViewModel()

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val surfaceRequest by qrScannerViewModel.surfaceRequest.collectAsStateWithLifecycle()
    LaunchedEffect(lifecycleOwner) {
        val cameraProvider = ProcessCameraProvider.awaitInstance(context.applicationContext)
        qrScannerViewModel.bindCamera(cameraProvider, lifecycleOwner)
        try {
            awaitCancellation()
        } finally {
            cameraProvider.unbindAll()
            qrScannerViewModel.clearScanResult()
        }
    }

    val transformationInfo by produceState<SurfaceRequest.TransformationInfo?>(
        null, surfaceRequest
    ) {
        try {
            surfaceRequest?.setTransformationInfoListener(Runnable::run) { transformationInfo ->
                value = transformationInfo
            }
            awaitCancellation()
        } finally {
            surfaceRequest?.clearTransformationInfoListener()
        }
    }

    val scanResult by qrScannerViewModel.scanResult.collectAsStateWithLifecycle()
    var showScanResult by remember { mutableStateOf(false) }
    LaunchedEffect(scanResult) {
        if (scanResult == null) {
            showScanResult = false
            return@LaunchedEffect
        }
        showScanResult = true
        delay(5000)
        showScanResult = false
    }

    var autofocusPoint by remember { mutableStateOf(false to Offset.Unspecified) }
    LaunchedEffect(autofocusPoint) {
        delay(1000)
        if (!autofocusPoint.first) return@LaunchedEffect
        autofocusPoint = false to autofocusPoint.second
    }

    surfaceRequest?.let { surfaceRequest ->
        val coordinateTransformer = remember { MutableCoordinateTransformer() }
        val sensorToUiTransformMatrix =
            remember(transformationInfo, coordinateTransformer.transformMatrix) {
                transformationInfo?.let {
                    Matrix().apply {
                        setFrom(it.sensorToBufferTransform)
                        timesAssign(Matrix().apply {
                            setFrom(coordinateTransformer.transformMatrix)
                            invert()
                        })
                    }
                }
            }
        CameraXViewfinder(
            surfaceRequest = surfaceRequest,
            coordinateTransformer = coordinateTransformer,
            alignment = Alignment.TopCenter,
            contentScale = ContentScale.Fit,
            modifier = Modifier.pointerInput(Unit) {
                detectTapGestures { coordinates ->
                    qrScannerViewModel.tapToFocus(coordinateTransformer.run { coordinates.transform() })
                    autofocusPoint = true to coordinates
                }
            },
        )
        Box(modifier = Modifier.fillMaxSize()) {
            AnimatedVisibility(
                visible = showScanResult,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize(),
            ) {
                scanResult?.let { scanResult ->
                    sensorToUiTransformMatrix?.let { sensorToUiTransformMatrix ->
                        scanResult.points.forEach {
                            Box(
                                modifier = Modifier
                                    .offset {
                                        sensorToUiTransformMatrix.map(it).round()
                                    }
                                    .offset((-8).dp, (-8).dp)) {
                                Spacer(
                                    modifier = Modifier
                                        .border(2.dp, Color.Green, CircleShape)
                                        .size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
            Column(
                modifier = Modifier.align(Alignment.BottomCenter),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Button(
                    onClick = { onScan(qrScannerViewModel.scanResult.value?.text) },
                    modifier = Modifier.padding(16.dp, 8.dp)
                ) {
                    Text(text = "Scan")
                }
                Button(
                    onClick = onDismiss, modifier = Modifier.padding(16.dp, 8.dp)
                ) {
                    Text(text = "Cancel")
                }
            }
        }
        AnimatedVisibility(
            visible = autofocusPoint.first,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .offset { autofocusPoint.second.takeOrElse { Offset.Zero }.round() }
                .offset((-24).dp, (-24).dp)) {
            Spacer(
                Modifier
                    .border(2.dp, Color.White, CircleShape)
                    .size(48.dp)
            )
        }
    }
}

data class ScanResult(val text: String, val points: List<Offset>)

class QRScannerViewModel : ViewModel() {
    val scanEnabled = MutableViewModelStateFlow(false)

    private val _surfaceRequest = MutableStateFlow<SurfaceRequest?>(null)
    val surfaceRequest = _surfaceRequest.asStateFlow()

    private var camera: Camera? = null
    private var surfacePointFactory: SurfaceOrientedMeteringPointFactory? = null

    private val cameraPreviewUseCase = Preview.Builder().build().apply {
        setSurfaceProvider { newSurfaceRequest ->
            _surfaceRequest.update { newSurfaceRequest }
            surfacePointFactory = SurfaceOrientedMeteringPointFactory(
                newSurfaceRequest.resolution.width.toFloat(),
                newSurfaceRequest.resolution.height.toFloat()
            )
        }
    }

    private val _scanResult = MutableStateFlow<ScanResult?>(null)
    val scanResult = _scanResult.asStateFlow()
    fun clearScanResult() = _scanResult.update { null }

    var imageBuffer = ByteArray(0)
    private val executor = Dispatchers.Default.asExecutor()
    private val analysisUseCase = ImageAnalysis.Builder().run {
        setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        setResolutionSelector(ResolutionSelector.Builder().run {
            setAllowedResolutionMode(ResolutionSelector.PREFER_HIGHER_RESOLUTION_OVER_CAPTURE_RATE)
            setResolutionStrategy(
                ResolutionStrategy(
                    Size(1600, 1200), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                )
            )
            build()
        })
        build()
    }.apply {
        setAnalyzer(executor) { image ->
            image.use {
                if (image.format != ImageFormat.YUV_420_888) return@use
                val plane = image.planes[0]
                val buffer = plane.buffer
                val pos = buffer.position()
                val targetSize = buffer.remaining()
                if (imageBuffer.size < targetSize) {
                    imageBuffer = ByteArray(targetSize)
                }
                buffer.get(imageBuffer)
                buffer.position(pos)
                val bitmap = BinaryBitmap(
                    HybridBinarizer(
                        PlanarYUVLuminanceSource(
                            imageBuffer,
                            plane.rowStride,
                            image.height,
                            0,
                            0,
                            image.width,
                            image.height,
                            false
                        )
                    )
                )
                val reader = QRCodeReader()
                val result = try {
                    reader.decode(bitmap)
                } catch (_: NotFoundException) {
                    null
                } catch (_: ChecksumException) {
                    null
                } catch (_: FormatException) {
                    null
                } ?: return@use
                val bufferToSensorTransformMatrix = Matrix().apply {
                    setFrom(image.imageInfo.sensorToBufferTransformMatrix)
                    invert()
                }
                _scanResult.update {
                    ScanResult(result.text, result.resultPoints.map {
                        bufferToSensorTransformMatrix.map(Offset(it.x, it.y))
                    })
                }
            }
        }
    }

    fun bindCamera(cameraProvider: ProcessCameraProvider, lifecycleOwner: LifecycleOwner) {
        camera = cameraProvider.bindToLifecycle(
            lifecycleOwner,
            CameraSelector.DEFAULT_BACK_CAMERA,
            cameraPreviewUseCase,
            analysisUseCase,
        )
    }

    fun tapToFocus(coordinates: Offset) {
        val camera = camera ?: return
        val cameraState = camera.cameraInfo.cameraState.value ?: return
        if (cameraState.type != CameraState.Type.OPEN) return
        val point = surfacePointFactory?.createPoint(coordinates.x, coordinates.y) ?: return
        val meteringAction = FocusMeteringAction.Builder(point).build()
        camera.cameraControl.startFocusAndMetering(meteringAction)
    }
}
