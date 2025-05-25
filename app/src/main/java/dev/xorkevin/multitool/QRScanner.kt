@file:OptIn(
    ExperimentalMaterial3Api::class, ExperimentalStdlibApi::class,
    ExperimentalCoroutinesApi::class, ExperimentalPermissionsApi::class
)

package dev.xorkevin.multitool

import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation

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
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var surfaceRequest by remember { mutableStateOf<SurfaceRequest?>(null) }
    LaunchedEffect(lifecycleOwner) {
        val cameraProvider = ProcessCameraProvider.awaitInstance(context.applicationContext)
        val cameraPreviewUseCase = Preview.Builder().build().apply {
            setSurfaceProvider { surfaceRequest = it }
        }
        cameraProvider.bindToLifecycle(lifecycleOwner, DEFAULT_BACK_CAMERA, cameraPreviewUseCase)
        try {
            awaitCancellation()
        } finally {
            surfaceRequest = null
            cameraProvider.unbindAll()
        }
    }
    surfaceRequest?.let {
        CameraXViewfinder(surfaceRequest = it, modifier = Modifier.fillMaxWidth())
    }
}

class QRScannerViewModel : ViewModel() {
}
