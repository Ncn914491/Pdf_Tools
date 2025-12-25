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
import com.yourname.pdftoolkit.domain.operations.EditableMetadata
import com.yourname.pdftoolkit.domain.operations.PdfMetadata
import com.yourname.pdftoolkit.domain.operations.PdfMetadataManager
import com.yourname.pdftoolkit.ui.components.*
import kotlinx.coroutines.launch

/**
 * Screen for viewing and editing PDF metadata.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetadataScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val metadataManager = remember { PdfMetadataManager() }
    
    // State
    var selectedFile by remember { mutableStateOf<PdfFileInfo?>(null) }
    var metadata by remember { mutableStateOf<PdfMetadata?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var isEditing by remember { mutableStateOf(false) }
    
    // Editable fields
    var editTitle by remember { mutableStateOf("") }
    var editAuthor by remember { mutableStateOf("") }
    var editSubject by remember { mutableStateOf("") }
    var editKeywords by remember { mutableStateOf("") }
    
    var isProcessing by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var showResult by remember { mutableStateOf(false) }
    var resultSuccess by remember { mutableStateOf(false) }
    var resultMessage by remember { mutableStateOf("") }
    
    // File picker launcher
    val pickPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            val fileInfo = FileManager.getFileInfo(context, uri)
            selectedFile = fileInfo
            isEditing = false
            
            scope.launch {
                isLoading = true
                val result = metadataManager.readMetadata(context, uri)
                result.fold(
                    onSuccess = { meta ->
                        metadata = meta
                        editTitle = meta.title ?: ""
                        editAuthor = meta.author ?: ""
                        editSubject = meta.subject ?: ""
                        editKeywords = meta.keywords ?: ""
                    },
                    onFailure = {
                        metadata = null
                    }
                )
                isLoading = false
            }
        }
    }
    
    // Save file launcher
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
                        val editedMetadata = EditableMetadata(
                            title = editTitle.takeIf { it.isNotBlank() },
                            author = editAuthor.takeIf { it.isNotBlank() },
                            subject = editSubject.takeIf { it.isNotBlank() },
                            keywords = editKeywords.takeIf { it.isNotBlank() }
                        )
                        
                        val result = metadataManager.updateMetadata(
                            context = context,
                            inputUri = file.uri,
                            outputStream = outputStream,
                            metadata = editedMetadata,
                            onProgress = { progress = it }
                        )
                        
                        outputStream.close()
                        
                        result.fold(
                            onSuccess = {
                                resultSuccess = true
                                resultMessage = "Metadata updated successfully"
                                isEditing = false
                            },
                            onFailure = { error ->
                                resultSuccess = false
                                resultMessage = error.message ?: "Update failed"
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
    
    Scaffold(
        topBar = {
            ToolTopBar(
                title = "View Metadata",
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
                when {
                    selectedFile == null -> {
                        EmptyState(
                            icon = Icons.Default.Info,
                            title = "No PDF Selected",
                            subtitle = "Select a PDF file to view its metadata",
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    isLoading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    metadata != null -> {
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
                                                text = "${metadata!!.pageCount} pages • ${selectedFile!!.formattedSize}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                            )
                                        }
                                        IconButton(onClick = { 
                                            selectedFile = null
                                            metadata = null
                                        }) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Remove",
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    }
                                }
                            }
                            
                            // Document Info section
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Document Information",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    
                                    TextButton(
                                        onClick = { isEditing = !isEditing }
                                    ) {
                                        Icon(
                                            imageVector = if (isEditing) Icons.Default.Close else Icons.Default.Edit,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(if (isEditing) "Cancel" else "Edit")
                                    }
                                }
                            }
                            
                            // Editable metadata fields
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
                                        verticalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        if (isEditing) {
                                            OutlinedTextField(
                                                value = editTitle,
                                                onValueChange = { editTitle = it },
                                                label = { Text("Title") },
                                                modifier = Modifier.fillMaxWidth(),
                                                singleLine = true
                                            )
                                            
                                            OutlinedTextField(
                                                value = editAuthor,
                                                onValueChange = { editAuthor = it },
                                                label = { Text("Author") },
                                                modifier = Modifier.fillMaxWidth(),
                                                singleLine = true
                                            )
                                            
                                            OutlinedTextField(
                                                value = editSubject,
                                                onValueChange = { editSubject = it },
                                                label = { Text("Subject") },
                                                modifier = Modifier.fillMaxWidth(),
                                                singleLine = true
                                            )
                                            
                                            OutlinedTextField(
                                                value = editKeywords,
                                                onValueChange = { editKeywords = it },
                                                label = { Text("Keywords") },
                                                placeholder = { Text("Comma-separated keywords") },
                                                modifier = Modifier.fillMaxWidth(),
                                                singleLine = true
                                            )
                                        } else {
                                            MetadataRow(
                                                label = "Title",
                                                value = metadata!!.title ?: "Not set"
                                            )
                                            MetadataRow(
                                                label = "Author",
                                                value = metadata!!.author ?: "Not set"
                                            )
                                            MetadataRow(
                                                label = "Subject",
                                                value = metadata!!.subject ?: "Not set"
                                            )
                                            MetadataRow(
                                                label = "Keywords",
                                                value = metadata!!.keywords ?: "Not set"
                                            )
                                        }
                                    }
                                }
                            }
                            
                            // Read-only properties section
                            item {
                                Text(
                                    text = "Document Properties",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            
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
                                        MetadataRow(
                                            label = "Creator",
                                            value = metadata!!.creator ?: "Unknown"
                                        )
                                        MetadataRow(
                                            label = "Producer",
                                            value = metadata!!.producer ?: "Unknown"
                                        )
                                        MetadataRow(
                                            label = "Created",
                                            value = metadata!!.creationDate ?: "Unknown"
                                        )
                                        MetadataRow(
                                            label = "Modified",
                                            value = metadata!!.modificationDate ?: "Unknown"
                                        )
                                        MetadataRow(
                                            label = "PDF Version",
                                            value = metadata!!.pdfVersion ?: "Unknown"
                                        )
                                        MetadataRow(
                                            label = "Encrypted",
                                            value = if (metadata!!.isEncrypted) "Yes" else "No"
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
                                    message = "Updating metadata..."
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
                    when {
                        selectedFile == null -> {
                            ActionButton(
                                text = "Select PDF",
                                onClick = {
                                    pickPdfLauncher.launch(arrayOf("application/pdf"))
                                },
                                icon = Icons.Default.FolderOpen
                            )
                        }
                        isEditing -> {
                            ActionButton(
                                text = "Save Changes",
                                onClick = {
                                    val fileName = FileManager.generateOutputFileName("updated")
                                    savePdfLauncher.launch(fileName)
                                },
                                isLoading = isProcessing,
                                icon = Icons.Default.Save
                            )
                        }
                        else -> {
                            OutlinedButton(
                                onClick = {
                                    pickPdfLauncher.launch(arrayOf("application/pdf"))
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                            ) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Select Another PDF")
                            }
                        }
                    }
                }
            }
        }
    }
    
    // Result dialog
    if (showResult) {
        ResultDialog(
            isSuccess = resultSuccess,
            title = if (resultSuccess) "Update Complete" else "Update Failed",
            message = resultMessage,
            onDismiss = { showResult = false }
        )
    }
}

@Composable
private fun MetadataRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(0.6f)
        )
    }
}
