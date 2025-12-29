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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yourname.pdftoolkit.domain.operations.FlattenConfig
import com.yourname.pdftoolkit.domain.operations.PdfFlattener
import com.yourname.pdftoolkit.util.FileOpener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for Flatten Screen.
 */
class FlattenViewModel : ViewModel() {
    private val _state = MutableStateFlow(FlattenUiState())
    val state: StateFlow<FlattenUiState> = _state.asStateFlow()
    
    fun setSourcePdf(uri: Uri, name: String) {
        _state.value = _state.value.copy(sourceUri = uri, sourceName = name)
    }
    
    fun toggleFlattenAnnotations() {
        _state.value = _state.value.copy(
            flattenAnnotations = !_state.value.flattenAnnotations
        )
    }
    
    fun toggleFlattenForms() {
        _state.value = _state.value.copy(
            flattenForms = !_state.value.flattenForms
        )
    }
    
    fun toggleRemoveJavaScript() {
        _state.value = _state.value.copy(
            removeJavaScript = !_state.value.removeJavaScript
        )
    }
    
    fun toggleRemoveEmbeddedFiles() {
        _state.value = _state.value.copy(
            removeEmbeddedFiles = !_state.value.removeEmbeddedFiles
        )
    }
    
    fun flattenPdf(
        context: android.content.Context,
        outputUri: Uri
    ) {
        val sourceUri = _state.value.sourceUri ?: return
        
        viewModelScope.launch {
            _state.value = _state.value.copy(isProcessing = true, progress = 0, error = null)
            
            val flattener = PdfFlattener()
            val config = FlattenConfig(
                flattenAnnotations = _state.value.flattenAnnotations,
                flattenForms = _state.value.flattenForms,
                removeJavaScript = _state.value.removeJavaScript,
                removeEmbeddedFiles = _state.value.removeEmbeddedFiles
            )
            
            val result = flattener.flattenPdf(
                context = context,
                inputUri = sourceUri,
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
                annotationsFlattened = result.annotationsFlattened,
                formsFlattened = result.formsFlattened,
                resultUri = if (result.success) outputUri else null
            )
        }
    }
    
    fun reset() {
        _state.value = FlattenUiState()
    }
}

data class FlattenUiState(
    val sourceUri: Uri? = null,
    val sourceName: String = "",
    val flattenAnnotations: Boolean = true,
    val flattenForms: Boolean = true,
    val removeJavaScript: Boolean = true,
    val removeEmbeddedFiles: Boolean = false,
    val isProcessing: Boolean = false,
    val progress: Int = 0,
    val isComplete: Boolean = false,
    val error: String? = null,
    val annotationsFlattened: Int = 0,
    val formsFlattened: Int = 0,
    val resultUri: Uri? = null
)

/**
 * Flatten Screen - Flatten PDF annotations, forms, and remove interactive elements.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlattenScreen(
    onNavigateBack: () -> Unit,
    viewModel: FlattenViewModel = viewModel()
) {
    val context = LocalContext.current
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
        uri?.let { viewModel.flattenPdf(context, it) }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Flatten PDF") },
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
                        text = "Flattening merges annotations and form fields into the page content, making them non-editable. This is useful for sharing documents where you want to preserve the final appearance.",
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
            
            // Flatten Options
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Flatten Options",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    
                    // Annotations
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Flatten Annotations",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                "Merge highlights, notes, stamps into page",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = state.flattenAnnotations,
                            onCheckedChange = { viewModel.toggleFlattenAnnotations() }
                        )
                    }
                    
                    Divider()
                    
                    // Forms
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Flatten Forms",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                "Convert form fields to static text",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = state.flattenForms,
                            onCheckedChange = { viewModel.toggleFlattenForms() }
                        )
                    }
                    
                    Divider()
                    
                    // JavaScript
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Remove JavaScript",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                "Remove interactive scripts",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = state.removeJavaScript,
                            onCheckedChange = { viewModel.toggleRemoveJavaScript() }
                        )
                    }
                    
                    Divider()
                    
                    // Embedded Files
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Remove Embedded Files",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                "Remove attached files from PDF",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = state.removeEmbeddedFiles,
                            onCheckedChange = { viewModel.toggleRemoveEmbeddedFiles() }
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
                        Text("Flattening PDF... ${state.progress}%")
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
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "PDF Flattened!",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (state.annotationsFlattened > 0 || state.formsFlattened > 0) {
                                Text(
                                    "${state.annotationsFlattened} annotations, ${state.formsFlattened} form fields processed",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
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
            
            // Flatten Button
            Button(
                onClick = {
                    val fileName = "flattened_${System.currentTimeMillis()}.pdf"
                    saveDocumentLauncher.launch(fileName)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = state.sourceUri != null && !state.isProcessing
            ) {
                Icon(Icons.Default.Layers, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Flatten PDF")
            }
            
            // Reset Button
            if (state.isComplete) {
                OutlinedButton(
                    onClick = { viewModel.reset() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Flatten Another PDF")
                }
            }
        }
    }
}
