package com.yourname.pdftoolkit.ui.screens

import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import com.yourname.pdftoolkit.ui.components.*
import com.yourname.pdftoolkit.util.CacheManager
import com.yourname.pdftoolkit.util.ImageProcessor
import com.yourname.pdftoolkit.util.OutputFormat
import com.yourname.pdftoolkit.util.ResolutionPreset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream

/**
 * Image tool operation types.
 */
enum class ImageOperation(val title: String, val description: String) {
    RESIZE("Resize", "Change image dimensions"),
    COMPRESS("Compress", "Reduce file size"),
    CONVERT("Convert", "Change image format"),
    STRIP_METADATA("Strip Metadata", "Remove EXIF data")
}

/**
 * Screen for low-bloat image tools.
 * Features: Resize, Compress, Format Conversion, Metadata Stripping.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageToolsScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // State
    var selectedImages by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var selectedOperation by remember { mutableStateOf(ImageOperation.RESIZE) }
    var isProcessing by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var showResult by remember { mutableStateOf(false) }
    var resultSuccess by remember { mutableStateOf(false) }
    var resultMessage by remember { mutableStateOf("") }
    
    // Resize settings
    var selectedPreset by remember { mutableStateOf(ResolutionPreset.HD) }
    var customWidth by remember { mutableStateOf("1280") }
    var customHeight by remember { mutableStateOf("720") }
    var useCustomSize by remember { mutableStateOf(false) }
    var maintainAspectRatio by remember { mutableStateOf(true) }
    
    // Compress settings
    var compressionQuality by remember { mutableStateOf(75f) }
    
    // Convert settings
    var targetFormat by remember { mutableStateOf(OutputFormat.WEBP) }
    
    // Image picker launcher
    val pickImagesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            selectedImages = uris
        }
    }
    
    // Open gallery function
    fun openGallery() {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                type = "image/*"
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            // Ignore
        }
    }
    
    // Save processed image to gallery
    suspend fun saveToGallery(inputFile: File, baseName: String, format: OutputFormat): Uri? = 
        withContext(Dispatchers.IO) {
            try {
                val mimeType = format.mimeType
                val extension = format.extension
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val contentValues = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, "${baseName}.${extension}")
                        put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/PDF Toolkit")
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }
                    
                    val uri = context.contentResolver.insert(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        contentValues
                    ) ?: return@withContext null
                    
                    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                        FileInputStream(inputFile).use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                    
                    contentValues.clear()
                    contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                    context.contentResolver.update(uri, contentValues, null, null)
                    
                    uri
                } else {
                    @Suppress("DEPRECATION")
                    val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                    val appDir = File(picturesDir, "PDF Toolkit")
                    if (!appDir.exists()) appDir.mkdirs()
                    
                    val destFile = File(appDir, "${baseName}.${extension}")
                    inputFile.copyTo(destFile, overwrite = true)
                    
                    val values = ContentValues().apply {
                        put(MediaStore.Images.Media.DATA, destFile.absolutePath)
                        put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                    }
                    context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    
                    Uri.fromFile(destFile)
                }
            } catch (e: Exception) {
                null
            }
        }
    
    // Process images
    fun processImages() {
        if (selectedImages.isEmpty()) return
        
        scope.launch {
            isProcessing = true
            progress = 0f
            
            var successCount = 0
            var totalSaved = 0L
            var totalOriginal = 0L
            
            val format = when (selectedOperation) {
                ImageOperation.CONVERT -> targetFormat
                else -> OutputFormat.WEBP
            }
            
            selectedImages.forEachIndexed { index, uri ->
                val result = when (selectedOperation) {
                    ImageOperation.RESIZE -> {
                        if (useCustomSize) {
                            ImageProcessor.resize(
                                context = context,
                                inputUri = uri,
                                targetWidth = customWidth.toIntOrNull() ?: 1280,
                                targetHeight = customHeight.toIntOrNull() ?: 720,
                                maintainAspectRatio = maintainAspectRatio,
                                format = format,
                                quality = compressionQuality.toInt()
                            )
                        } else {
                            ImageProcessor.resizeToPreset(
                                context = context,
                                inputUri = uri,
                                preset = selectedPreset,
                                format = format,
                                quality = compressionQuality.toInt()
                            )
                        }
                    }
                    ImageOperation.COMPRESS -> {
                        ImageProcessor.compress(
                            context = context,
                            inputUri = uri,
                            quality = compressionQuality.toInt(),
                            format = OutputFormat.WEBP
                        )
                    }
                    ImageOperation.CONVERT -> {
                        ImageProcessor.convertFormat(
                            context = context,
                            inputUri = uri,
                            targetFormat = targetFormat,
                            quality = compressionQuality.toInt()
                        )
                    }
                    ImageOperation.STRIP_METADATA -> {
                        ImageProcessor.stripMetadata(
                            context = context,
                            inputUri = uri,
                            format = format,
                            quality = 95
                        )
                    }
                }
                
                if (result.success && result.outputFile != null) {
                    val baseName = FileManager.getFileInfo(context, uri)?.name
                        ?.substringBeforeLast(".") ?: "image_${index + 1}"
                    
                    val savedUri = saveToGallery(
                        inputFile = result.outputFile,
                        baseName = "${baseName}_${selectedOperation.name.lowercase()}",
                        format = format
                    )
                    
                    if (savedUri != null) {
                        successCount++
                        totalOriginal += result.originalSize
                        totalSaved += result.savedBytes
                    }
                    
                    // Clean up temp file
                    result.outputFile.delete()
                }
                
                progress = (index + 1).toFloat() / selectedImages.size
            }
            
            // Clean up image processing cache
            CacheManager.clearImageProcessingCache(context)
            
            resultSuccess = successCount > 0
            resultMessage = if (successCount > 0) {
                val savedKB = totalSaved / 1024
                when (selectedOperation) {
                    ImageOperation.COMPRESS -> 
                        "Compressed $successCount images.\nSaved ${savedKB}KB (~${(totalSaved * 100 / totalOriginal.coerceAtLeast(1))}% reduction)"
                    ImageOperation.RESIZE -> 
                        "Resized $successCount images to ${if (useCustomSize) "${customWidth}x${customHeight}" else selectedPreset.displayName}"
                    ImageOperation.CONVERT -> 
                        "Converted $successCount images to ${targetFormat.extension.uppercase()}"
                    ImageOperation.STRIP_METADATA -> 
                        "Removed metadata from $successCount images"
                }
            } else {
                "No images processed successfully"
            }
            
            isProcessing = false
            showResult = true
            selectedImages = emptyList()
        }
    }
    
    Scaffold(
        topBar = {
            ToolTopBar(
                title = "Image Tools",
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
                        icon = Icons.Default.Photo,
                        title = "No Images Selected",
                        subtitle = "Select images to resize, compress, convert, or strip metadata",
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(vertical = 16.dp)
                    ) {
                        // Selected images count
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Photo,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = "${selectedImages.size} image${if (selectedImages.size > 1) "s" else ""} selected",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Spacer(modifier = Modifier.weight(1f))
                                    IconButton(onClick = { selectedImages = emptyList() }) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Clear",
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }
                        }
                        
                        // Operation selection
                        item {
                            Text(
                                text = "Select Operation",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        item {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(ImageOperation.entries) { operation ->
                                    FilterChip(
                                        selected = selectedOperation == operation,
                                        onClick = { selectedOperation = operation },
                                        label = { Text(operation.title) },
                                        leadingIcon = if (selectedOperation == operation) {
                                            {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        } else null
                                    )
                                }
                            }
                        }
                        
                        // Operation-specific settings
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
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text(
                                        text = selectedOperation.title + " Settings",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    
                                    when (selectedOperation) {
                                        ImageOperation.RESIZE -> {
                                            // Preset or Custom toggle
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text("Use Custom Size", modifier = Modifier.weight(1f))
                                                Switch(
                                                    checked = useCustomSize,
                                                    onCheckedChange = { useCustomSize = it }
                                                )
                                            }
                                            
                                            if (useCustomSize) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    OutlinedTextField(
                                                        value = customWidth,
                                                        onValueChange = { customWidth = it.filter { c -> c.isDigit() } },
                                                        label = { Text("Width") },
                                                        modifier = Modifier.weight(1f),
                                                        singleLine = true
                                                    )
                                                    OutlinedTextField(
                                                        value = customHeight,
                                                        onValueChange = { customHeight = it.filter { c -> c.isDigit() } },
                                                        label = { Text("Height") },
                                                        modifier = Modifier.weight(1f),
                                                        singleLine = true
                                                    )
                                                }
                                                
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text("Maintain Aspect Ratio", modifier = Modifier.weight(1f))
                                                    Switch(
                                                        checked = maintainAspectRatio,
                                                        onCheckedChange = { maintainAspectRatio = it }
                                                    )
                                                }
                                            } else {
                                                Text("Target Resolution", style = MaterialTheme.typography.bodySmall)
                                                LazyRow(
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    items(ResolutionPreset.entries) { preset ->
                                                        FilterChip(
                                                            selected = selectedPreset == preset,
                                                            onClick = { selectedPreset = preset },
                                                            label = { 
                                                                Text(
                                                                    preset.displayName.substringBefore(" ("),
                                                                    maxLines = 1
                                                                ) 
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        
                                        ImageOperation.COMPRESS -> {
                                            // Quality slider
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text("Quality")
                                                Text(
                                                    "${compressionQuality.toInt()}%",
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            
                                            Slider(
                                                value = compressionQuality,
                                                onValueChange = { compressionQuality = it },
                                                valueRange = 10f..100f,
                                                steps = 8
                                            )
                                            
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    "Smaller file",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Text(
                                                    "Better quality",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            
                                            Text(
                                                "Uses WebP format for optimal compression",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        
                                        ImageOperation.CONVERT -> {
                                            Text("Target Format")
                                            LazyRow(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                items(OutputFormat.entries) { format ->
                                                    FilterChip(
                                                        selected = targetFormat == format,
                                                        onClick = { targetFormat = format },
                                                        label = { Text(format.extension.uppercase()) }
                                                    )
                                                }
                                            }
                                            
                                            // Quality slider for lossy formats
                                            if (targetFormat != OutputFormat.PNG) {
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text("Quality")
                                                    Text(
                                                        "${compressionQuality.toInt()}%",
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                                
                                                Slider(
                                                    value = compressionQuality,
                                                    onValueChange = { compressionQuality = it },
                                                    valueRange = 10f..100f,
                                                    steps = 8
                                                )
                                            }
                                        }
                                        
                                        ImageOperation.STRIP_METADATA -> {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Info,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Column {
                                                    Text(
                                                        "Removes EXIF data",
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                    Text(
                                                        "GPS location, camera info, timestamps, etc.",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        
                        // Info card
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = "Processed images will be saved to Pictures/PDF Toolkit folder.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                }
                            }
                        }
                    }
                }
                
                // Progress overlay
                if (isProcessing) {
                    Card(
                        modifier = Modifier
                            .align(Alignment.Center)
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
                                message = "Processing images..."
                            )
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
                            icon = Icons.Default.Photo
                        )
                    } else {
                        ActionButton(
                            text = "Process ${selectedImages.size} Image${if (selectedImages.size > 1) "s" else ""}",
                            onClick = { processImages() },
                            isLoading = isProcessing,
                            icon = when (selectedOperation) {
                                ImageOperation.RESIZE -> Icons.Default.AspectRatio
                                ImageOperation.COMPRESS -> Icons.Default.Compress
                                ImageOperation.CONVERT -> Icons.Default.Transform
                                ImageOperation.STRIP_METADATA -> Icons.Default.DeleteSweep
                            }
                        )
                    }
                }
            }
        }
    }
    
    // Result dialog
    if (showResult) {
        AlertDialog(
            onDismissRequest = { showResult = false },
            icon = {
                Icon(
                    imageVector = if (resultSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                    contentDescription = null,
                    tint = if (resultSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            },
            title = {
                Text(if (resultSuccess) "Processing Complete" else "Processing Failed")
            },
            text = {
                Text(resultMessage)
            },
            confirmButton = {
                if (resultSuccess) {
                    Button(
                        onClick = {
                            showResult = false
                            openGallery()
                        }
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Open Gallery")
                    }
                } else {
                    TextButton(onClick = { showResult = false }) {
                        Text("OK")
                    }
                }
            },
            dismissButton = {
                if (resultSuccess) {
                    TextButton(onClick = { showResult = false }) {
                        Text("Close")
                    }
                }
            }
        )
    }
}
