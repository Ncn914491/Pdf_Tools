package com.yourname.pdftoolkit.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.yourname.pdftoolkit.domain.operations.PageNumberFormat
import com.yourname.pdftoolkit.domain.operations.PageNumberOptions
import com.yourname.pdftoolkit.domain.operations.PageNumberPosition
import com.yourname.pdftoolkit.domain.operations.PdfPageNumberer
import com.yourname.pdftoolkit.ui.components.*
import com.yourname.pdftoolkit.util.FileOpener
import com.yourname.pdftoolkit.util.OutputFolderManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Screen for adding page numbers to PDFs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageNumberScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pageNumberer = remember { PdfPageNumberer() }
    
    // State
    var selectedFile by remember { mutableStateOf<PdfFileInfo?>(null) }
    var position by remember { mutableStateOf(PageNumberPosition.BOTTOM_RIGHT) }
    var format by remember { mutableStateOf(PageNumberFormat.WITH_TOTAL) }
    var fontSize by remember { mutableStateOf(10f) }
    var startPage by remember { mutableStateOf(1) }
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
    val saveFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        uri?.let { saveUri ->
            resultUri = saveUri
            val file = selectedFile ?: return@let
            
            scope.launch {
                isProcessing = true
                progress = 0f
                
                val options = PageNumberOptions(
                    position = position,
                    format = format,
                    fontSize = fontSize,
                    startPage = startPage
                )
                
                context.contentResolver.openOutputStream(saveUri)?.use { outputStream ->
                    val result = pageNumberer.addPageNumbers(
                        context = context,
                        inputUri = file.uri,
                        outputStream = outputStream,
                        options = options,
                        onProgress = { progress = it }
                    )
                    
                    result.fold(
                        onSuccess = { count ->
                            resultSuccess = true
                            resultMessage = "Successfully added page numbers to $count pages"
                            selectedFile = null
                        },
                        onFailure = { error ->
                            resultSuccess = false
                            resultMessage = error.message ?: "Failed to add page numbers"
                        }
                    )
                } ?: run {
                    resultSuccess = false
                    resultMessage = "Cannot create output file"
                }
                
                isProcessing = false
                showResult = true
            }
        }
    }
    
    // Function to add page numbers with default location
    fun addNumbersWithDefaultLocation() {
        scope.launch {
            isProcessing = true
            progress = 0f
            
            val result = withContext(Dispatchers.IO) {
                try {
                    val file = selectedFile!!
                    val baseName = file.name.removeSuffix(".pdf")
                    val fileName = "${baseName}_numbered.pdf"
                    val outputResult = OutputFolderManager.createOutputStream(context, fileName)
                    
                    if (outputResult != null) {
                        val options = PageNumberOptions(
                            position = position,
                            format = format,
                            fontSize = fontSize,
                            startPage = startPage
                        )
                        
                        val pageResult = pageNumberer.addPageNumbers(
                            context = context,
                            inputUri = file.uri,
                            outputStream = outputResult.outputStream,
                            options = options,
                            onProgress = { progress = it }
                        )
                        
                        outputResult.outputStream.close()
                        
                        pageResult.fold(
                            onSuccess = { count ->
                                Triple(true, "Successfully added page numbers to $count pages\n\nSaved to: ${OutputFolderManager.getOutputFolderPath(context)}/${outputResult.outputFile.fileName}", outputResult.outputFile.contentUri)
                            },
                            onFailure = { error ->
                                outputResult.outputFile.file.delete()
                                Triple(false, error.message ?: "Failed to add page numbers", null)
                            }
                        )
                    } else {
                        Triple(false, "Cannot create output file", null)
                    }
                } catch (e: Exception) {
                    Triple(false, e.message ?: "Failed to add page numbers", null)
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
                title = "Page Numbers",
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
                        icon = Icons.Default.FormatListNumbered,
                        title = "No PDF Selected",
                        subtitle = "Select a PDF to add page numbers",
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
                            FileItemCard(
                                fileName = selectedFile!!.name,
                                fileSize = selectedFile!!.formattedSize,
                                onRemove = { selectedFile = null }
                            )
                        }
                        
                        // Position selection
                        item {
                            Text(
                                text = "Position",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                PageNumberPosition.entries.take(3).forEach { pos ->
                                    FilterChip(
                                        selected = position == pos,
                                        onClick = { position = pos },
                                        label = { 
                                            Text(
                                                pos.name.replace("_", " ").lowercase()
                                                    .replaceFirstChar { it.uppercase() },
                                                style = MaterialTheme.typography.labelSmall
                                            ) 
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                        
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                PageNumberPosition.entries.drop(3).forEach { pos ->
                                    FilterChip(
                                        selected = position == pos,
                                        onClick = { position = pos },
                                        label = { 
                                            Text(
                                                pos.name.replace("_", " ").lowercase()
                                                    .replaceFirstChar { it.uppercase() },
                                                style = MaterialTheme.typography.labelSmall
                                            ) 
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                        
                        // Format selection
                        item {
                            Text(
                                text = "Format",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        item {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                PageNumberFormat.entries.forEach { fmt ->
                                    val example = when (fmt) {
                                        PageNumberFormat.SIMPLE -> "1, 2, 3..."
                                        PageNumberFormat.WITH_TOTAL -> "1 of 5, 2 of 5..."
                                        PageNumberFormat.PREFIXED -> "Page 1, Page 2..."
                                        PageNumberFormat.PREFIXED_WITH_TOTAL -> "Page 1 of 5..."
                                    }
                                    
                                    FilterChip(
                                        selected = format == fmt,
                                        onClick = { format = fmt },
                                        label = { Text(example) },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                        
                        // Font size slider
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
                                            text = "Font Size",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = "${fontSize.toInt()} pt",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    
                                    Spacer(modifier = Modifier.height(8.dp))
                                    
                                    Slider(
                                        value = fontSize,
                                        onValueChange = { fontSize = it },
                                        valueRange = 8f..24f,
                                        steps = 7
                                    )
                                }
                            }
                        }
                        
                        // Start page
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Start numbering from page",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Medium
                                    )
                                    
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        IconButton(
                                            onClick = { if (startPage > 1) startPage-- }
                                        ) {
                                            Icon(Icons.Default.Remove, "Decrease")
                                        }
                                        Text(
                                            text = "$startPage",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        IconButton(
                                            onClick = { startPage++ }
                                        ) {
                                            Icon(Icons.Default.Add, "Increase")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                
                // Progress overlay
                if (isProcessing) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp)
                            .align(Alignment.Center)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            OperationProgress(
                                progress = progress,
                                message = "Adding page numbers..."
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
                            text = "Add Page Numbers",
                            onClick = {
                                if (useCustomLocation) {
                                    val baseName = selectedFile!!.name.removeSuffix(".pdf")
                                    saveFileLauncher.launch("${baseName}_numbered.pdf")
                                } else {
                                    addNumbersWithDefaultLocation()
                                }
                            },
                            isLoading = isProcessing,
                            icon = Icons.Default.FormatListNumbered
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
            title = if (resultSuccess) "Success" else "Error",
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
