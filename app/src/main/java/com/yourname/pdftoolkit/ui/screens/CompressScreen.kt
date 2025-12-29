package com.yourname.pdftoolkit.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.yourname.pdftoolkit.data.PdfFileInfo
import com.yourname.pdftoolkit.domain.operations.CompressionLevel
import com.yourname.pdftoolkit.domain.operations.PdfCompressor
import com.yourname.pdftoolkit.ui.components.*
import com.yourname.pdftoolkit.util.FileOpener
import com.yourname.pdftoolkit.util.OutputFolderManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Screen for compressing PDF files.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompressScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pdfCompressor = remember { PdfCompressor() }
    
    // State
    var selectedFile by remember { mutableStateOf<PdfFileInfo?>(null) }
    var compressionLevel by remember { mutableStateOf(CompressionLevel.MEDIUM) }
    var isProcessing by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var showResult by remember { mutableStateOf(false) }
    var resultSuccess by remember { mutableStateOf(false) }
    var resultMessage by remember { mutableStateOf("") }
    var resultUri by remember { mutableStateOf<Uri?>(null) }
    var useCustomLocation by remember { mutableStateOf(false) }
    
    // File picker launcher
    val pickPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            selectedFile = FileManager.getFileInfo(context, uri)
        }
    }
    
    // Save file launcher (for custom location)
    val savePdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        uri?.let { outputUri ->
            selectedFile?.let { file ->
                scope.launch {
                    isProcessing = true
                    progress = 0f
                    
                    val outputStream = context.contentResolver.openOutputStream(outputUri)
                    if (outputStream != null) {
                        val result = pdfCompressor.compressPdf(
                            context = context,
                            inputUri = file.uri,
                            outputStream = outputStream,
                            level = compressionLevel,
                            onProgress = { progress = it }
                        )
                        
                        outputStream.close()
                        
                        // Get compressed file size
                        val compressedInfo = FileManager.getFileInfo(context, outputUri)
                        
                        result.fold(
                            onSuccess = { compressionResult ->
                                val actualCompressedSize = compressedInfo?.size ?: compressionResult.compressedSize
                                val originalBytes = file.size
                                val savedBytes = originalBytes - actualCompressedSize
                                val savedPercent = if (originalBytes > 0) {
                                    (savedBytes.toFloat() / originalBytes * 100).toInt()
                                } else 0
                                
                                if (savedBytes > 0) {
                                    resultSuccess = true
                                    resultUri = outputUri
                                    resultMessage = buildString {
                                        append("Compression successful!\n\n")
                                        append("Before: ${file.formattedSize}\n")
                                        append("After: ${compressedInfo?.formattedSize ?: "Unknown"}\n")
                                        append("Saved: ${FileManager.formatFileSize(savedBytes)} ($savedPercent%)")
                                    }
                                } else {
                                    resultSuccess = true
                                    resultUri = outputUri
                                    resultMessage = buildString {
                                        append("Compressed PDF saved.\n\n")
                                        append("Before: ${file.formattedSize}\n")
                                        append("After: ${compressedInfo?.formattedSize ?: "Unknown"}\n\n")
                                        append("Note: This PDF may already be optimized or contain mostly text.")
                                    }
                                }
                                selectedFile = null
                            },
                            onFailure = { error ->
                                resultSuccess = false
                                resultMessage = error.message ?: "Compression failed"
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
    
    // Function to compress with default location
    fun compressWithDefaultLocation() {
        scope.launch {
            isProcessing = true
            progress = 0f
            val originalFile = selectedFile!!
            
            val result = withContext(Dispatchers.IO) {
                try {
                    val fileName = FileManager.generateOutputFileName("compressed")
                    val outputResult = OutputFolderManager.createOutputStream(context, fileName)
                    
                    if (outputResult != null) {
                        val compressResult = pdfCompressor.compressPdf(
                            context = context,
                            inputUri = originalFile.uri,
                            outputStream = outputResult.outputStream,
                            level = compressionLevel,
                            onProgress = { progress = it }
                        )
                        
                        outputResult.outputStream.close()
                        
                        compressResult.fold(
                            onSuccess = { cResult ->
                                val compressedSize = outputResult.outputFile.file.length()
                                val originalBytes = originalFile.size
                                val savedBytes = originalBytes - compressedSize
                                val savedPercent = if (originalBytes > 0) {
                                    (savedBytes.toFloat() / originalBytes * 100).toInt()
                                } else 0
                                
                                val message = buildString {
                                    if (savedBytes > 0) {
                                        append("Compression successful!\n\n")
                                        append("Before: ${originalFile.formattedSize}\n")
                                        append("After: ${FileManager.formatFileSize(compressedSize)}\n")
                                        append("Saved: ${FileManager.formatFileSize(savedBytes)} ($savedPercent%)\n\n")
                                    } else {
                                        append("Compressed PDF saved.\n\n")
                                        append("Before: ${originalFile.formattedSize}\n")
                                        append("After: ${FileManager.formatFileSize(compressedSize)}\n\n")
                                        append("Note: This PDF may already be optimized.\n\n")
                                    }
                                    append("Saved to: ${OutputFolderManager.getOutputFolderPath(context)}/${outputResult.outputFile.fileName}")
                                }
                                Triple(true, message, outputResult.outputFile.contentUri)
                            },
                            onFailure = { error ->
                                outputResult.outputFile.file.delete()
                                Triple(false, error.message ?: "Compression failed", null)
                            }
                        )
                    } else {
                        Triple(false, "Cannot create output file", null)
                    }
                } catch (e: Exception) {
                    Triple(false, e.message ?: "Compression failed", null)
                }
            }
            
            resultSuccess = result.first
            resultMessage = result.second
            resultUri = result.third
            if (resultSuccess) {
                selectedFile = null
            }
            isProcessing = false
            showResult = true
        }
    }
    
    Scaffold(
        topBar = {
            ToolTopBar(
                title = "Compress PDF",
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
                if (selectedFile == null) {
                    EmptyState(
                        icon = Icons.Default.Compress,
                        title = "No PDF Selected",
                        subtitle = "Select a PDF file to reduce its file size",
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
                        // Selected file info
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
                                        imageVector = Icons.Default.PictureAsPdf,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = selectedFile!!.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        Text(
                                            text = "Original size: ${selectedFile!!.formattedSize}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                        )
                                    }
                                    IconButton(onClick = { selectedFile = null }) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Remove",
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }
                        }
                        
                        // Compression level selection
                        item {
                            Text(
                                text = "Compression Level",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        item {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CompressionLevelOption(
                                    level = CompressionLevel.LOW,
                                    title = "Low",
                                    description = "Best quality, minor size reduction",
                                    icon = Icons.Default.HighQuality,
                                    estimatedReduction = "~20%",
                                    isSelected = compressionLevel == CompressionLevel.LOW,
                                    onClick = { compressionLevel = CompressionLevel.LOW }
                                )
                                
                                CompressionLevelOption(
                                    level = CompressionLevel.MEDIUM,
                                    title = "Medium (Recommended)",
                                    description = "Good balance of quality and size",
                                    icon = Icons.Default.Balance,
                                    estimatedReduction = "~45%",
                                    isSelected = compressionLevel == CompressionLevel.MEDIUM,
                                    onClick = { compressionLevel = CompressionLevel.MEDIUM }
                                )
                                
                                CompressionLevelOption(
                                    level = CompressionLevel.HIGH,
                                    title = "High",
                                    description = "Smaller file, reduced quality",
                                    icon = Icons.Default.Compress,
                                    estimatedReduction = "~65%",
                                    isSelected = compressionLevel == CompressionLevel.HIGH,
                                    onClick = { compressionLevel = CompressionLevel.HIGH }
                                )
                                
                                CompressionLevelOption(
                                    level = CompressionLevel.MAXIMUM,
                                    title = "Maximum",
                                    description = "Smallest file, lowest quality",
                                    icon = Icons.Default.DataSaverOn,
                                    estimatedReduction = "~75%",
                                    isSelected = compressionLevel == CompressionLevel.MAXIMUM,
                                    onClick = { compressionLevel = CompressionLevel.MAXIMUM }
                                )
                            }
                        }
                        
                        // Estimated result
                        item {
                            val estimatedSize = selectedFile?.let { file ->
                                pdfCompressor.estimateCompressedSize(file.size, compressionLevel)
                            } ?: 0L
                            
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
                                        imageVector = Icons.Default.Speed,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "Estimated Result",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer
                                        )
                                        Text(
                                            text = "File size: ~${FileManager.formatFileSize(estimatedSize)}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
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
                                    message = "Compressing PDF..."
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
                    if (selectedFile == null) {
                        ActionButton(
                            text = "Select PDF",
                            onClick = {
                                pickPdfLauncher.launch(arrayOf("application/pdf"))
                            },
                            icon = Icons.Default.FolderOpen
                        )
                    } else {
                        // Save location option
                        SaveLocationSelector(
                            useCustomLocation = useCustomLocation,
                            onUseCustomLocationChange = { useCustomLocation = it }
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        ActionButton(
                            text = "Compress PDF",
                            onClick = {
                                if (useCustomLocation) {
                                    val fileName = FileManager.generateOutputFileName("compressed")
                                    savePdfLauncher.launch(fileName)
                                } else {
                                    compressWithDefaultLocation()
                                }
                            },
                            isLoading = isProcessing,
                            icon = Icons.Default.Compress
                        )
                    }
                }
            }
        }
    }
    
    // Result dialog
    if (showResult) {
        ResultDialog(
            isSuccess = resultSuccess,
            title = if (resultSuccess) "Compression Complete" else "Compression Failed",
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompressionLevelOption(
    level: CompressionLevel,
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    estimatedReduction: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = isSelected,
                onClick = onClick
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Surface(
                shape = MaterialTheme.shapes.small,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                }
            ) {
                Text(
                    text = estimatedReduction,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}
