package com.kitchensync.app

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage

@OptIn(ExperimentalGetImage::class)
@Composable
fun BarcodeScanner(onCode: (String) -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.CAMERA) }
    var torch by remember { mutableStateOf(false) }
    var cam by remember { mutableStateOf<Camera?>(null) }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (granted) {
            AndroidView(modifier = Modifier.fillMaxSize(), factory = { c ->
                val pv = PreviewView(c)
                val future = ProcessCameraProvider.getInstance(c)
                future.addListener({
                    val provider = future.get()
                    val preview = Preview.Builder().build().also { it.setSurfaceProvider(pv.surfaceProvider) }
                    val scanner = BarcodeScanning.getClient(
                        BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A).build())
                    val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                    var done = false
                    analysis.setAnalyzer(ContextCompat.getMainExecutor(c)) { proxy ->
                        val img = proxy.image
                        if (img == null || done) { proxy.close(); return@setAnalyzer }
                        scanner.process(InputImage.fromMediaImage(img, proxy.imageInfo.rotationDegrees))
                            .addOnSuccessListener { list -> list.firstOrNull()?.rawValue?.let { if (!done) { done = true; onCode(it) } } }
                            .addOnCompleteListener { proxy.close() }
                    }
                    provider.unbindAll()
                    cam = provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                }, ContextCompat.getMainExecutor(c))
                pv
            })
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width * 0.8f; val h = w * 0.55f
                drawRoundRect(Color.White, Offset((size.width - w) / 2, (size.height - h) / 2), Size(w, h), CornerRadius(48f), style = Stroke(6f))
            }
            Text("Наведіть камеру на штрихкод", color = Color.White, modifier = Modifier.align(Alignment.Center).offset(y = 120.dp))
        } else Text("Потрібен дозвіл на камеру", color = Color.White, modifier = Modifier.align(Alignment.Center))
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(onClose) { Icon(Icons.Default.Close, "Закрити", tint = Color.White) }
            IconButton({ torch = !torch; cam?.cameraControl?.enableTorch(torch) }) {
                Icon(if (torch) Icons.Default.FlashOn else Icons.Default.FlashOff, "Ліхтарик", tint = Color.White)
            }
        }
    }
}
