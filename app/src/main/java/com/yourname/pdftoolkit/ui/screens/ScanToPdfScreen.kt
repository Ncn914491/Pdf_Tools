package com.yourname.pdftoolkit.ui.screens

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberAsyncImagePainter
import com.yourname.pdftoolkit.domain.operations.*
import com.yourname.pdftoolkit.util.CropHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.Executors

/**
 * ViewModel for Scan to PDF Screen.
 */
class ScanToPdfViewModel : ViewModel() {
    private val _state = MutableStateFlow(ScanToPdfUiState())
    val state: StateFlow<ScanToPdfUiState> = _state.asStateFlow()
    
    fun addImage(uri: Uri) {
        val updatedImages = _state.value.selectedImages + uri
        _state.value = _state.value.copy(selectedImages = updatedImages)
    }
    
    fun removeImage(index: Int) {
        val updatedImages = _state.value.selectedImages.toMutableList()
        if (index in updatedImages.indices) {
            updatedImages.removeAt(index)
            _state.value = _state.value.copy(selectedImages = updatedImages)
        }
    }
    
    fun clearImages() {
        _state.value = _state.value.copy(selectedImages = emptyList())
    }
    
    fun setPageSize(size: ScanPageSize) {
        _state.value = _state.value.copy(pageSize = size)
    }
    
    fun setColorMode(mode: ScanColorMode) {
        _state.value = _state.value.copy(colorMode = mode)
    }
    
    fun setQuality(quality: ScanQuality) {
        _state.value = _state.value.copy(quality = quality)
    }
    
    fun toggleEnhanceContrast() {
        _state.value = _state.value.copy(enhanceContrast = !_state.value.enhanceContrast)
    }
    
    fun setShowCamera(show: Boolean) {
        _state.value = _state.value.copy(showCamera = show)
    }
    
    fun replaceImage(index: Int, newUri: Uri) {
        val updatedImages = _state.value.selectedImages.toMutableList()
        if (index in updatedImages.indices) {
            updatedImages[index] = newUri
            _state.value = _state.value.copy(selectedImages = updatedImages)
        }
    }
    
    fun createPdf(
        context: android.content.Context,
        outputUri: Uri
    ) {
        if (_state.value.selectedImages.isEmpty()) return
        
        viewModelScope.launch {
            _state.value = _state.value.copy(isProcessing = true, progress = 0, error = null)
            
            val scanner = PdfScanner(context)
            val config = ScanConfig(
                pageSize = _state.value.pageSize,
                colorMode = _state.value.colorMode,
                quality = _state.value.quality,
                enhanceContrast = _state.value.enhanceContrast
            )
            
            val result = scanner.imagesToPdf(
                imageUris = _state.value.selectedImages,
                outputUri = outputUri,
                config = config,
                progressCallback = { progress ->
                    _state.value = _state.value.copy(progress = progress)
                }
            )
            
            _state.value = _state.value.copy(
                isProcessing = false,
                isComplete = result.success,
                error = result.errorMessage,
                pagesScanned = result.pagesScanned
            )
        }
    }
    
    fun reset() {
        _state.value = ScanToPdfUiState()
    }
}

data class ScanToPdfUiState(
    val selectedImages: List<Uri> = emptyList(),
    val showCamera: Boolean = false,
    val pageSize: ScanPageSize = ScanPageSize.A4,
    val colorMode: ScanColorMode = ScanColorMode.COLOR,
    val quality: ScanQuality = ScanQuality.MEDIUM,
    val enhanceContrast: Boolean = true,
    val isProcessing: Boolean = false,
    val progress: Int = 0,
    val isComplete: Boolean = false,
    val error: String? = null,
    val pagesScanned: Int = 0
)

/**
 * Scan to PDF Screen - Capture photos and convert to PDF.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanToPdfScreen(
    onNavigateBack: () -> Unit,
    viewModel: ScanToPdfViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by viewModel.state.collectAsState()
    
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (granted) {
            viewModel.setShowCamera(true)
        }
    }
    
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        uris.forEach { uri -> viewModel.addImage(uri) }
    }
    
    val saveDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        uri?.let { viewModel.createPdf(context, it) }
    }
    
    // Crop state
    var cropImageIndex by remember { mutableStateOf(-1) }
    
    // Crop launcher
    val cropLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val croppedUri = CropHelper.getResultUri(result.resultCode, result.data)
            if (croppedUri != null && cropImageIndex >= 0 && cropImageIndex < state.selectedImages.size) {
                viewModel.replaceImage(cropImageIndex, croppedUri)
            }
        }
        cropImageIndex = -1
    }
    
    if (state.showCamera && hasCameraPermission) {
        CameraScreen(
            onImageCaptured = { uri ->
                viewModel.addImage(uri)
                viewModel.setShowCamera(false)
            },
            onClose = { viewModel.setShowCamera(false) }
        )
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Scan to PDF") },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Image Source Selection
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Add Images",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    if (hasCameraPermission) {
                                        viewModel.setShowCamera(true)
                                    } else {
                                        permissionLauncher.launch(Manifest.permission.CAMERA)
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.CameraAlt, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Camera")
                            }
                            
                            OutlinedButton(
                                onClick = { imagePickerLauncher.launch(arrayOf("image/*")) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Image, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Gallery")
                            }
                        }
                    }
                }
                
                // Selected Images
                if (state.selectedImages.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Pages (${state.selectedImages.size})",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                TextButton(onClick = { viewModel.clearImages() }) {
                                    Text("Clear All")
                                }
                            }
                            
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(state.selectedImages.size) { index ->
                                    Box(
                                        modifier = Modifier
                                            .size(100.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                // Tap to crop
                                                cropImageIndex = index
                                                val cropIntent = CropHelper.getCropIntent(
                                                    context = context,
                                                    sourceUri = state.selectedImages[index],
                                                    aspectRatio = null,
                                                    maxSize = 2048
                                                )
                                                cropLauncher.launch(cropIntent)
                                            }
                                    ) {
                                        Image(
                                            painter = rememberAsyncImagePainter(state.selectedImages[index]),
                                            contentDescription = "Page ${index + 1}",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                        
                                        // Page number badge
                                        Badge(
                                            modifier = Modifier
                                                .align(Alignment.TopStart)
                                                .padding(4.dp)
                                        ) {
                                            Text("${index + 1}")
                                        }
                                        
                                        // Crop button
                                        IconButton(
                                            onClick = {
                                                cropImageIndex = index
                                                val cropIntent = CropHelper.getCropIntent(
                                                    context = context,
                                                    sourceUri = state.selectedImages[index],
                                                    aspectRatio = null,
                                                    maxSize = 2048
                                                )
                                                cropLauncher.launch(cropIntent)
                                            },
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .size(24.dp)
                                                .background(
                                                    MaterialTheme.colorScheme.primary,
                                                    CircleShape
                                                )
                                        ) {
                                            Icon(
                                                Icons.Default.Crop,
                                                contentDescription = "Crop",
                                                tint = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                        
                                        // Delete button
                                        IconButton(
                                            onClick = { viewModel.removeImage(index) },
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .size(24.dp)
                                                .background(
                                                    MaterialTheme.colorScheme.error,
                                                    CircleShape
                                                )
                                        ) {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = "Remove",
                                                tint = MaterialTheme.colorScheme.onError,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                
                // Scan Settings
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Settings",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        
                        // Page Size
                        Text("Page Size", style = MaterialTheme.typography.bodyMedium)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(ScanPageSize.entries) { size ->
                                FilterChip(
                                    selected = state.pageSize == size,
                                    onClick = { viewModel.setPageSize(size) },
                                    label = { Text(size.displayName.split(" ").first()) }
                                )
                            }
                        }
                        
                        // Color Mode
                        Text("Color Mode", style = MaterialTheme.typography.bodyMedium)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(ScanColorMode.entries) { mode ->
                                FilterChip(
                                    selected = state.colorMode == mode,
                                    onClick = { viewModel.setColorMode(mode) },
                                    label = { Text(mode.name.replace("_", " ")) }
                                )
                            }
                        }
                        
                        // Quality
                        Text("Quality", style = MaterialTheme.typography.bodyMedium)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(ScanQuality.entries) { quality ->
                                FilterChip(
                                    selected = state.quality == quality,
                                    onClick = { viewModel.setQuality(quality) },
                                    label = { Text(quality.name) }
                                )
                            }
                        }
                        
                        // Enhance Contrast
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Enhance Contrast", modifier = Modifier.weight(1f))
                            Switch(
                                checked = state.enhanceContrast,
                                onCheckedChange = { viewModel.toggleEnhanceContrast() }
                            )
                        }
                    }
                }
                
                // Processing State
                AnimatedVisibility(visible = state.isProcessing) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator()
                            Text("Creating PDF... ${state.progress}%")
                            LinearProgressIndicator(
                                progress = state.progress / 100f,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
                
                // Success State
                AnimatedVisibility(visible = state.isComplete && !state.isProcessing) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    "PDF Created!",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "${state.pagesScanned} pages",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
                
                // Error State
                state.error?.let { error ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Error,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(error, color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Create PDF Button
                Button(
                    onClick = {
                        val fileName = "scanned_${System.currentTimeMillis()}.pdf"
                        saveDocumentLauncher.launch(fileName)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state.selectedImages.isNotEmpty() && !state.isProcessing
                ) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Create PDF")
                }
                
                // Reset Button
                if (state.isComplete) {
                    OutlinedButton(
                        onClick = { viewModel.reset() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Scan More Documents")
                    }
                }
            }
        }
    }
}

/**
 * Camera preview screen for capturing images.
 */
@Composable
private fun CameraScreen(
    onImageCaptured: (Uri) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    
    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(surfaceProvider)
                        }
                        
                        imageCapture = ImageCapture.Builder()
                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                            .build()
                        
                        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                        
                        try {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                preview,
                                imageCapture
                            )
                        } catch (e: Exception) {
                            // Handle camera binding error
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        
        // Controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(32.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Close button
            FloatingActionButton(
                onClick = onClose,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close")
            }
            
            // Capture button
            FloatingActionButton(
                onClick = {
                    val photoFile = File(
                        context.cacheDir,
                        "capture_${System.currentTimeMillis()}.jpg"
                    )
                    
                    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
                    
                    imageCapture?.takePicture(
                        outputOptions,
                        cameraExecutor,
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                onImageCaptured(Uri.fromFile(photoFile))
                            }
                            
                            override fun onError(exception: ImageCaptureException) {
                                // Handle capture error
                            }
                        }
                    )
                },
                containerColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(72.dp)
            ) {
                Icon(
                    Icons.Default.CameraAlt,
                    contentDescription = "Capture",
                    modifier = Modifier.size(32.dp)
                )
            }
            
            // Placeholder for symmetry
            Spacer(modifier = Modifier.size(56.dp))
        }
    }
}
