package com.yourname.pdftoolkit.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yourname.pdftoolkit.domain.operations.*
import com.yourname.pdftoolkit.util.FileOpener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for Watermark Screen.
 */
class WatermarkViewModel : ViewModel() {
    private val _state = MutableStateFlow(WatermarkUiState())
    val state: StateFlow<WatermarkUiState> = _state.asStateFlow()
    
    fun setSourcePdf(uri: Uri, name: String) {
        _state.value = _state.value.copy(sourceUri = uri, sourceName = name)
    }
    
    fun setWatermarkImage(uri: Uri) {
        _state.value = _state.value.copy(watermarkImageUri = uri)
    }
    
    fun updateWatermarkText(text: String) {
        _state.value = _state.value.copy(watermarkText = text)
    }
    
    fun setWatermarkType(isText: Boolean) {
        _state.value = _state.value.copy(isTextWatermark = isText)
    }
    
    fun setPosition(position: WatermarkPosition) {
        _state.value = _state.value.copy(position = position)
    }
    
    fun setOpacity(opacity: Float) {
        _state.value = _state.value.copy(opacity = opacity)
    }
    
    fun setFontSize(size: Float) {
        _state.value = _state.value.copy(fontSize = size)
    }
    
    fun setRotation(rotation: Float) {
        _state.value = _state.value.copy(rotation = rotation)
    }
    
    fun setColor(color: Color) {
        _state.value = _state.value.copy(color = color)
    }
    
    fun addWatermark(
        context: android.content.Context,
        outputUri: Uri
    ) {
        val currentState = _state.value
        val sourceUri = currentState.sourceUri ?: return
        
        viewModelScope.launch {
            _state.value = _state.value.copy(isProcessing = true, progress = 0)
            
            val watermarker = PdfWatermarker()
            
            val watermarkType = if (currentState.isTextWatermark) {
                WatermarkType.Text(
                    content = currentState.watermarkText,
                    fontSize = currentState.fontSize,
                    rotation = currentState.rotation
                )
            } else {
                currentState.watermarkImageUri?.let { imgUri ->
                    WatermarkType.Image(
                        imageUri = imgUri,
                        width = 150f,
                        height = 150f,
                        rotation = currentState.rotation
                    )
                } ?: WatermarkType.Text(currentState.watermarkText)
            }
            
            val config = WatermarkConfig(
                type = watermarkType,
                position = currentState.position,
                opacity = currentState.opacity,
                applyToAllPages = true
            )
            
            val result = watermarker.addWatermark(
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
                pagesProcessed = result.pagesProcessed,
                resultUri = if (result.success) outputUri else null
            )
        }
    }
    
    fun reset() {
        _state.value = WatermarkUiState()
    }
}

data class WatermarkUiState(
    val sourceUri: Uri? = null,
    val sourceName: String = "",
    val isTextWatermark: Boolean = true,
    val watermarkText: String = "CONFIDENTIAL",
    val watermarkImageUri: Uri? = null,
    val position: WatermarkPosition = WatermarkPosition.CENTER,
    val opacity: Float = 0.3f,
    val fontSize: Float = 48f,
    val rotation: Float = 45f,
    val color: Color = Color.Gray,
    val isProcessing: Boolean = false,
    val progress: Int = 0,
    val isComplete: Boolean = false,
    val error: String? = null,
    val pagesProcessed: Int = 0,
    val resultUri: Uri? = null
)

/**
 * Watermark Screen - Add text or image watermarks to PDF documents.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatermarkScreen(
    onNavigateBack: () -> Unit,
    viewModel: WatermarkViewModel = viewModel()
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
    
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.setWatermarkImage(it) }
    }
    
    val saveDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        uri?.let { viewModel.addWatermark(context, it) }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add Watermark") },
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
            
            // Watermark Type Selection
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
                        text = "Watermark Type",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = state.isTextWatermark,
                            onClick = { viewModel.setWatermarkType(true) },
                            label = { Text("Text") },
                            leadingIcon = {
                                Icon(Icons.Default.TextFields, contentDescription = null, modifier = Modifier.size(18.dp))
                            },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = !state.isTextWatermark,
                            onClick = { viewModel.setWatermarkType(false) },
                            label = { Text("Image") },
                            leadingIcon = {
                                Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    
                    // Text Watermark Options
                    AnimatedVisibility(
                        visible = state.isTextWatermark,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(
                                value = state.watermarkText,
                                onValueChange = { viewModel.updateWatermarkText(it) },
                                label = { Text("Watermark Text") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            
                            Text("Font Size: ${state.fontSize.toInt()}", style = MaterialTheme.typography.bodyMedium)
                            Slider(
                                value = state.fontSize,
                                onValueChange = { viewModel.setFontSize(it) },
                                valueRange = 12f..120f
                            )
                        }
                    }
                    
                    // Image Watermark Options
                    AnimatedVisibility(
                        visible = !state.isTextWatermark,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (state.watermarkImageUri != null) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Image, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Image selected", modifier = Modifier.weight(1f))
                                    TextButton(onClick = { imagePickerLauncher.launch(arrayOf("image/*")) }) {
                                        Text("Change")
                                    }
                                }
                            } else {
                                OutlinedButton(
                                    onClick = { imagePickerLauncher.launch(arrayOf("image/*")) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Image, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Select Image")
                                }
                            }
                        }
                    }
                }
            }
            
            // Position Selection
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
                        text = "Position",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(WatermarkPosition.entries) { position ->
                            FilterChip(
                                selected = state.position == position,
                                onClick = { viewModel.setPosition(position) },
                                label = { Text(position.name.replace("_", " ")) }
                            )
                        }
                    }
                }
            }
            
            // Appearance Settings
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
                        text = "Appearance",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    
                    Text("Opacity: ${(state.opacity * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = state.opacity,
                        onValueChange = { viewModel.setOpacity(it) },
                        valueRange = 0.1f..1f
                    )
                    
                    Text("Rotation: ${state.rotation.toInt()}°", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = state.rotation,
                        onValueChange = { viewModel.setRotation(it) },
                        valueRange = 0f..360f
                    )
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
                        Text("Adding watermark... ${state.progress}%")
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
                                "Watermark Added!",
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
            
            // Add Watermark Button
            Button(
                onClick = {
                    val fileName = "watermarked_${System.currentTimeMillis()}.pdf"
                    saveDocumentLauncher.launch(fileName)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = state.sourceUri != null && 
                         (state.isTextWatermark && state.watermarkText.isNotBlank() || 
                          !state.isTextWatermark && state.watermarkImageUri != null) &&
                         !state.isProcessing
            ) {
                Icon(Icons.Default.WaterDrop, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add Watermark")
            }
            
            // Reset Button
            if (state.isComplete) {
                OutlinedButton(
                    onClick = { viewModel.reset() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Watermark Another PDF")
                }
            }
        }
    }
}
