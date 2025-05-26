@file:OptIn(
    ExperimentalMaterial3Api::class, ExperimentalStdlibApi::class,
    ExperimentalCoroutinesApi::class, ExperimentalPermissionsApi::class
)

package dev.xorkevin.multitool

import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA
import androidx.camera.core.CameraState
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionSelector.PREFER_HIGHER_RESOLUTION_OVER_CAPTURE_RATE
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.viewfinder.compose.MutableCoordinateTransformer
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.takeOrElse
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

@Composable
fun QRScannerTool() {
    val cameraPermissionState = rememberPermissionState(android.Manifest.permission.CAMERA)
    if (cameraPermissionState.status.isGranted) {
        QRScanner()
    } else {
        val scrollState = rememberScrollState()
        Column(modifier = Modifier.verticalScroll(scrollState)) {
            if (cameraPermissionState.status.shouldShowRationale) {
                Text(
                    text = "Camera permission is needed to scan qr code", modifier = Modifier
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
                Text("Grant camera permission")
            }
        }
    }
}

@Composable
fun QRScanner() = ViewModelScope(QRScannerViewModel::class) {
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
        }
    }

    var autofocusPoint by remember { mutableStateOf(false to Offset.Unspecified) }
    LaunchedEffect(autofocusPoint) {
        delay(1000)
        if (!autofocusPoint.first) return@LaunchedEffect
        autofocusPoint = false to autofocusPoint.second
    }

    surfaceRequest?.let { surfaceRequest ->
        Box {
            val coordinateTransformer = remember { MutableCoordinateTransformer() }
            CameraXViewfinder(
                surfaceRequest = surfaceRequest, coordinateTransformer = coordinateTransformer,
                modifier = Modifier.pointerInput(Unit) {
                    detectTapGestures { coordinates ->
                        qrScannerViewModel.tapToFocus(coordinateTransformer.run { coordinates.transform() })
                        autofocusPoint = true to coordinates
                    }
                },
            )

            AnimatedVisibility(
                visible = autofocusPoint.first,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .offset { autofocusPoint.second.takeOrElse { Offset.Zero }.round() }
                    .offset((-24).dp, (-24).dp)
            ) {
                Spacer(
                    Modifier
                        .border(2.dp, Color.White, CircleShape)
                        .size(48.dp)

                )
            }
        }
    }
}

class QRScannerViewModel : ViewModel() {
    private val _surfaceRequest = MutableStateFlow<SurfaceRequest?>(null)
    val surfaceRequest: StateFlow<SurfaceRequest?> = _surfaceRequest

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

    private val executor = Dispatchers.Default.asExecutor()
    private val analysisUseCase = ImageAnalysis.Builder()
        .setBackpressureStrategy(STRATEGY_KEEP_ONLY_LATEST)
        .setResolutionSelector(
            ResolutionSelector.Builder()
                .setAllowedResolutionMode(PREFER_HIGHER_RESOLUTION_OVER_CAPTURE_RATE)
                .build()
        )
        .build().apply {
            setAnalyzer(executor) { }
        }

    fun bindCamera(cameraProvider: ProcessCameraProvider, lifecycleOwner: LifecycleOwner) {
        camera = cameraProvider.bindToLifecycle(
            lifecycleOwner,
            DEFAULT_BACK_CAMERA,
            cameraPreviewUseCase
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
