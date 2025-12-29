package com.yourname.pdftoolkit.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yourname.pdftoolkit.domain.operations.PdfOcrProcessor
import com.yourname.pdftoolkit.util.FileOpener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for OCR Screen.
 */
class OcrViewModel : ViewModel() {
    private val _state = MutableStateFlow(OcrUiState())
    val state: StateFlow<OcrUiState> = _state.asStateFlow()
    
    private var ocrProcessor: PdfOcrProcessor? = null
    
    fun setSourcePdf(uri: Uri, name: String) {
        _state.value = _state.value.copy(sourceUri = uri, sourceName = name)
    }
    
    fun setMode(mode: OcrMode) {
        _state.value = _state.value.copy(mode = mode)
    }
    
    fun extractText(context: android.content.Context) {
        val sourceUri = _state.value.sourceUri ?: return
        
        viewModelScope.launch {
            _state.value = _state.value.copy(isProcessing = true, progress = 0, error = null)
            
            ocrProcessor = PdfOcrProcessor(context)
            
            val result = ocrProcessor?.extractTextWithOcr(
                pdfUri = sourceUri,
                progressCallback = { progress ->
                    _state.value = _state.value.copy(progress = progress)
                }
            )
            
            _state.value = _state.value.copy(
                isProcessing = false,
                isComplete = result?.success == true,
                error = result?.errorMessage,
                extractedText = result?.fullText ?: "",
                pagesProcessed = result?.pages?.size ?: 0
            )
        }
    }
    
    fun makeSearchable(
        context: android.content.Context,
        outputUri: Uri
    ) {
        val sourceUri = _state.value.sourceUri ?: return
        
        viewModelScope.launch {
            _state.value = _state.value.copy(isProcessing = true, progress = 0, error = null)
            
            ocrProcessor = PdfOcrProcessor(context)
            
            val result = ocrProcessor?.makeSearchable(
                inputUri = sourceUri,
                outputUri = outputUri,
                progressCallback = { progress ->
                    _state.value = _state.value.copy(progress = progress)
                }
            )
            
            _state.value = _state.value.copy(
                isProcessing = false,
                isComplete = result?.success == true,
                error = result?.errorMessage,
                pagesProcessed = result?.pagesProcessed ?: 0,
                resultUri = if (result?.success == true) outputUri else null
            )
        }
    }
    
    fun reset() {
        ocrProcessor?.close()
        ocrProcessor = null
        _state.value = OcrUiState()
    }
    
    override fun onCleared() {
        super.onCleared()
        ocrProcessor?.close()
    }
}

enum class OcrMode {
    EXTRACT_TEXT,
    MAKE_SEARCHABLE
}

data class OcrUiState(
    val sourceUri: Uri? = null,
    val sourceName: String = "",
    val mode: OcrMode = OcrMode.EXTRACT_TEXT,
    val isProcessing: Boolean = false,
    val progress: Int = 0,
    val isComplete: Boolean = false,
    val error: String? = null,
    val extractedText: String = "",
    val pagesProcessed: Int = 0,
    val resultUri: Uri? = null
)

/**
 * OCR Screen - Extract text from scanned PDFs using ML Kit.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrScreen(
    onNavigateBack: () -> Unit,
    viewModel: OcrViewModel = viewModel()
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val state by viewModel.state.collectAsState()
    
    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            val name = context.contentResolver.query(it, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                cursor.moveToFirst()
                cursor.getString(nameIndex)
            } ?: "Selected PDF"
            viewModel.setSourcePdf(it, name)
        }
    }
    
    val saveDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        uri?.let { viewModel.makeSearchable(context, it) }
    }
    
    val saveTextLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        uri?.let { outputUri ->
            context.contentResolver.openOutputStream(outputUri)?.use { stream ->
                stream.write(state.extractedText.toByteArray())
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("OCR - Text Recognition") },
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
            // Info Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "OCR uses AI to recognize text in scanned PDFs and images. This works best with clear, high-resolution scans.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            
            // Source PDF Selection
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
                        text = "Source PDF",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    
                    if (state.sourceUri != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.PictureAsPdf,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = state.sourceName,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            IconButton(onClick = { pdfPickerLauncher.launch(arrayOf("application/pdf")) }) {
                                Icon(Icons.Default.Edit, contentDescription = "Change")
                            }
                        }
                    } else {
                        OutlinedButton(
                            onClick = { pdfPickerLauncher.launch(arrayOf("application/pdf")) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.FileOpen, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Select PDF")
                        }
                    }
                }
            }
            
            // Mode Selection
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
                        text = "OCR Mode",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    
                    // Extract Text Mode
                    Card(
                        onClick = { viewModel.setMode(OcrMode.EXTRACT_TEXT) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (state.mode == OcrMode.EXTRACT_TEXT)
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = state.mode == OcrMode.EXTRACT_TEXT,
                                onClick = { viewModel.setMode(OcrMode.EXTRACT_TEXT) }
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    "Extract Text",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "Extract all text content to a text file",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    
                    // Make Searchable Mode
                    Card(
                        onClick = { viewModel.setMode(OcrMode.MAKE_SEARCHABLE) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (state.mode == OcrMode.MAKE_SEARCHABLE)
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = state.mode == OcrMode.MAKE_SEARCHABLE,
                                onClick = { viewModel.setMode(OcrMode.MAKE_SEARCHABLE) }
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    "Make Searchable",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "Add invisible text layer for search/copy",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
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
                        Text("Processing with OCR... ${state.progress}%")
                        Text(
                            "This may take a while for large documents",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        LinearProgressIndicator(
                            progress = state.progress / 100f,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
            
            // Extracted Text Result
            AnimatedVisibility(
                visible = state.isComplete && 
                         !state.isProcessing && 
                         state.mode == OcrMode.EXTRACT_TEXT &&
                         state.extractedText.isNotEmpty()
            ) {
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
                                text = "Extracted Text",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Row {
                                IconButton(onClick = {
                                    clipboardManager.setText(AnnotatedString(state.extractedText))
                                }) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                                }
                                IconButton(onClick = {
                                    saveTextLauncher.launch("extracted_text_${System.currentTimeMillis()}.txt")
                                }) {
                                    Icon(Icons.Default.Save, contentDescription = "Save")
                                }
                            }
                        }
                        
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 300.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Text(
                                text = state.extractedText.take(2000) + 
                                       if (state.extractedText.length > 2000) "..." else "",
                                modifier = Modifier
                                    .padding(12.dp)
                                    .verticalScroll(rememberScrollState()),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        
                        Text(
                            text = "${state.extractedText.length} characters extracted from ${state.pagesProcessed} pages",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            // Success State (Make Searchable)
            AnimatedVisibility(
                visible = state.isComplete && 
                         !state.isProcessing && 
                         state.mode == OcrMode.MAKE_SEARCHABLE
            ) {
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
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "PDF Made Searchable!",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "${state.pagesProcessed} pages processed",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        state.resultUri?.let { uri ->
                            FilledTonalButton(
                                onClick = { FileOpener.openPdf(context, uri) }
                            ) {
                                Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Open")
                            }
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
            
            // Action Button
            Button(
                onClick = {
                    when (state.mode) {
                        OcrMode.EXTRACT_TEXT -> viewModel.extractText(context)
                        OcrMode.MAKE_SEARCHABLE -> {
                            val fileName = "searchable_${System.currentTimeMillis()}.pdf"
                            saveDocumentLauncher.launch(fileName)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = state.sourceUri != null && !state.isProcessing
            ) {
                Icon(
                    if (state.mode == OcrMode.EXTRACT_TEXT) Icons.Default.TextFields else Icons.Default.Search,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (state.mode == OcrMode.EXTRACT_TEXT) "Extract Text" else "Make Searchable"
                )
            }
            
            // Reset Button
            if (state.isComplete) {
                OutlinedButton(
                    onClick = { viewModel.reset() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Process Another PDF")
                }
            }
        }
    }
}
