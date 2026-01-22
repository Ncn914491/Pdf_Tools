package com.yourname.pdftoolkit.ui.screens

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yourname.pdftoolkit.data.FileManager
import com.yourname.pdftoolkit.domain.operations.ImageConverter
import com.yourname.pdftoolkit.domain.operations.PageSize
import com.yourname.pdftoolkit.ui.components.*
import com.yourname.pdftoolkit.util.CropHelper
import com.yourname.pdftoolkit.util.FileOpener
import com.yourname.pdftoolkit.util.OutputFolderManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Data class for selected image info.
 */
private data class ImageInfo(
    val uri: Uri,
    val name: String
)

/**
 * Screen for converting images to PDF.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConvertScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val imageConverter = remember { ImageConverter() }
    
    // State
    var selectedImages by remember { mutableStateOf<List<ImageInfo>>(emptyList()) }
    var pageSize by remember { mutableStateOf(PageSize.A4) }
    var quality by remember { mutableStateOf(85f) }
    var isProcessing by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var showResult by remember { mutableStateOf(false) }
    var resultSuccess by remember { mutableStateOf(false) }
    var resultMessage by remember { mutableStateOf("") }
    var resultUri by remember { mutableStateOf<Uri?>(null) }
    var useCustomLocation by remember { mutableStateOf(false) }
    
    // Crop state - track which image index is being cropped
    var cropImageIndex by remember { mutableStateOf(-1) }
    
    // Crop launcher - handles the result from uCrop activity
    val cropLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val croppedUri = CropHelper.getResultUri(result.resultCode, result.data)
            if (croppedUri != null && cropImageIndex >= 0 && cropImageIndex < selectedImages.size) {
                // Replace the original image with the cropped one
                val updatedImages = selectedImages.toMutableList()
                val originalName = updatedImages[cropImageIndex].name
                updatedImages[cropImageIndex] = ImageInfo(
                    uri = croppedUri,
                    name = "${originalName.substringBeforeLast(".")}_cropped.${originalName.substringAfterLast(".", "jpg")}"
                )
                selectedImages = updatedImages
            }
        }
        cropImageIndex = -1
    }
    
    // Image picker launcher
    val pickImagesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            val newImages = uris.mapNotNull { uri ->
                val info = FileManager.getFileInfo(context, uri)
                if (info != null) {
                    ImageInfo(uri = uri, name = info.name)
                } else null
            }
            selectedImages = selectedImages + newImages
        }
    }
    
    // Save file launcher (for custom location)
    val savePdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        uri?.let { outputUri ->
            if (selectedImages.isNotEmpty()) {
                scope.launch {
                    isProcessing = true
                    progress = 0f
                    
                    val outputStream = context.contentResolver.openOutputStream(outputUri)
                    if (outputStream != null) {
                        val result = imageConverter.imagesToPdf(
                            context = context,
                            imageUris = selectedImages.map { it.uri },
                            outputStream = outputStream,
                            pageSize = pageSize,
                            quality = quality.toInt(),
                            onProgress = { progress = it }
                        )
                        
                        outputStream.close()
                        
                        result.fold(
                            onSuccess = { count ->
                                resultSuccess = true
                                resultUri = outputUri
                                resultMessage = "Successfully converted $count images to PDF"
                                selectedImages = emptyList()
                            },
                            onFailure = { error ->
                                resultSuccess = false
                                resultMessage = error.message ?: "Conversion failed"
                            }
                        )
                    } else {
                        resultSuccess = false
                        resultMessage = "Cannot create output file"
                    }
                    
                    isProcessing = false
                    showResult = true
                }
            }
        }
    }
    
    // Function to convert with default location
    fun convertWithDefaultLocation() {
        scope.launch {
            isProcessing = true
            progress = 0f
            
            val result = withContext(Dispatchers.IO) {
                try {
                    val fileName = FileManager.generateOutputFileName("images")
                    val outputResult = OutputFolderManager.createOutputStream(context, fileName)
                    
                    if (outputResult != null) {
                        val convertResult = imageConverter.imagesToPdf(
                            context = context,
                            imageUris = selectedImages.map { it.uri },
                            outputStream = outputResult.outputStream,
                            pageSize = pageSize,
                            quality = quality.toInt(),
                            onProgress = { progress = it }
                        )
                        
                        outputResult.outputStream.close()
                        
                        convertResult.fold(
                            onSuccess = { count ->
                                Triple(true, "Successfully converted $count images to PDF\n\nSaved to: ${OutputFolderManager.getOutputFolderPath(context)}/${outputResult.outputFile.fileName}", outputResult.outputFile.contentUri)
                            },
                            onFailure = { error ->
                                outputResult.outputFile.file.delete()
                                Triple(false, error.message ?: "Conversion failed", null)
                            }
                        )
                    } else {
                        Triple(false, "Cannot create output file", null)
                    }
                } catch (e: Exception) {
                    Triple(false, e.message ?: "Conversion failed", null)
                }
            }
            
            resultSuccess = result.first
            resultMessage = result.second
            resultUri = result.third
            if (resultSuccess) {
                selectedImages = emptyList()
            }
            isProcessing = false
            showResult = true
        }
    }
    
    Scaffold(
        topBar = {
            ToolTopBar(
                title = "Convert Images",
                onNavigateBack = onNavigateBack
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Content area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (selectedImages.isEmpty()) {
                    EmptyState(
                        icon = Icons.Default.Image,
                        title = "No Images Selected",
                        subtitle = "Select one or more images to convert to PDF",
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(vertical = 16.dp)
                    ) {
                        // Image list header
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Selected Images (${selectedImages.size})",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                
                                TextButton(
                                    onClick = { selectedImages = emptyList() }
                                ) {
                                    Text("Clear All")
                                }
                            }
                        }
                        
                        // Image list
                        itemsIndexed(
                            items = selectedImages,
                            key = { index, image -> "${image.uri}-$index" }
                        ) { index, image ->
                            ImageItemCard(
                                index = index + 1,
                                image = image,
                                onRemove = {
                                    selectedImages = selectedImages.toMutableList().apply {
                                        removeAt(index)
                                    }
                                },
                                onCrop = {
                                    // Launch crop for this image
                                    cropImageIndex = index
                                    val cropIntent = CropHelper.getCropIntent(
                                        context = context,
                                        sourceUri = image.uri,
                                        aspectRatio = null, // Free crop
                                        maxSize = 2048
                                    )
                                    cropLauncher.launch(cropIntent)
                                },
                                onMoveUp = if (index > 0) {
                                    {
                                        selectedImages = selectedImages.toMutableList().apply {
                                            val item = removeAt(index)
                                            add(index - 1, item)
                                        }
                                    }
                                } else null,
                                onMoveDown = if (index < selectedImages.lastIndex) {
                                    {
                                        selectedImages = selectedImages.toMutableList().apply {
                                            val item = removeAt(index)
                                            add(index + 1, item)
                                        }
                                    }
                                } else null
                            )
                        }
                        
                        // Add more button
                        item {
                            OutlinedButton(
                                onClick = {
                                    pickImagesLauncher.launch(arrayOf("image/*"))
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Add More Images")
                            }
                        }
                        
                        // Settings section
                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Settings",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        // Page size selection
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp)
                                ) {
                                    Text(
                                        text = "Page Size",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Medium
                                    )
                                    
                                    Spacer(modifier = Modifier.height(12.dp))
                                    
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        PageSize.entries.forEach { size ->
                                            FilterChip(
                                                selected = pageSize == size,
                                                onClick = { pageSize = size },
                                                label = { Text(size.name.replace("_", " ")) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        
                        // Quality slider
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Image Quality",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = "${quality.toInt()}%",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    
                                    Spacer(modifier = Modifier.height(8.dp))
                                    
                                    Slider(
                                        value = quality,
                                        onValueChange = { quality = it },
                                        valueRange = 20f..100f,
                                        steps = 7
                                    )
                                    
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Smaller file",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "Better quality",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                
                // Progress overlay
                if (isProcessing) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = true,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier.align(Alignment.Center)
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                OperationProgress(
                                    progress = progress,
                                    message = "Converting images..."
                                )
                            }
                        }
                    }
                }
            }
            
            // Bottom action area
            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 3.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    if (selectedImages.isEmpty()) {
                        ActionButton(
                            text = "Select Images",
                            onClick = {
                                pickImagesLauncher.launch(arrayOf("image/*"))
                            },
                            icon = Icons.Default.Image
                        )
                    } else {
                        // Save location option
                        SaveLocationSelector(
                            useCustomLocation = useCustomLocation,
                            onUseCustomLocationChange = { useCustomLocation = it }
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        ActionButton(
                            text = "Convert to PDF",
                            onClick = {
                                if (useCustomLocation) {
                                    val fileName = FileManager.generateOutputFileName("images")
                                    savePdfLauncher.launch(fileName)
                                } else {
                                    convertWithDefaultLocation()
                                }
                            },
                            isLoading = isProcessing,
                            icon = Icons.Default.Transform
                        )
                    }
                }
            }
        }
    }
    
    // Result dialog with View option
    if (showResult) {
        ResultDialog(
            isSuccess = resultSuccess,
            title = if (resultSuccess) "Conversion Complete" else "Conversion Failed",
            message = resultMessage,
            onDismiss = { 
                showResult = false
                resultUri = null
            },
            onAction = resultUri?.let { uri ->
                { FileOpener.openPdf(context, uri) }
            },
            actionText = "Open PDF"
        )
    }
}

@Composable
private fun ImageItemCard(
    index: Int,
    image: ImageInfo,
    onRemove: () -> Unit,
    onCrop: (() -> Unit)? = null,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Order number
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(40.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Text(
                        text = index.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Image thumbnail (using Coil if available, otherwise just text)
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Image,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Text(
                text = image.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            
            // Crop button
            if (onCrop != null) {
                IconButton(
                    onClick = onCrop,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Crop,
                        contentDescription = "Crop image",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            // Reorder buttons
            Column {
                IconButton(
                    onClick = { onMoveUp?.invoke() },
                    enabled = onMoveUp != null,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = "Move up",
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(
                    onClick = { onMoveDown?.invoke() },
                    enabled = onMoveDown != null,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Move down",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
