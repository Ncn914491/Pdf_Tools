package com.yourname.pdftoolkit.ui.screens

import android.graphics.Color
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
 * ViewModel for Annotation Screen.
 */
class AnnotationViewModel : ViewModel() {
    private val _state = MutableStateFlow(AnnotationUiState())
    val state: StateFlow<AnnotationUiState> = _state.asStateFlow()
    
    fun setSourcePdf(uri: Uri, name: String) {
        _state.value = _state.value.copy(sourceUri = uri, sourceName = name)
    }
    
    fun setAnnotationType(type: AnnotationTypeOption) {
        _state.value = _state.value.copy(selectedType = type)
    }
    
    fun setPageIndex(index: Int) {
        _state.value = _state.value.copy(pageIndex = index)
    }
    
    fun setAnnotationText(text: String) {
        _state.value = _state.value.copy(annotationText = text)
    }
    
    fun setColor(color: Int) {
        _state.value = _state.value.copy(selectedColor = color)
    }
    
    fun setStampType(type: StampType) {
        _state.value = _state.value.copy(stampType = type)
    }
    
    fun setPosition(x: Float, y: Float) {
        _state.value = _state.value.copy(positionX = x, positionY = y)
    }
    
    fun setSize(width: Float, height: Float) {
        _state.value = _state.value.copy(width = width, height = height)
    }
    
    fun addAnnotation() {
        val currentState = _state.value
        val annotation = createAnnotation(currentState) ?: return
        
        val updatedAnnotations = _state.value.annotations + annotation
        _state.value = _state.value.copy(annotations = updatedAnnotations)
    }
    
    fun removeAnnotation(index: Int) {
        val updatedAnnotations = _state.value.annotations.toMutableList()
        if (index in updatedAnnotations.indices) {
            updatedAnnotations.removeAt(index)
            _state.value = _state.value.copy(annotations = updatedAnnotations)
        }
    }
    
    fun clearAnnotations() {
        _state.value = _state.value.copy(annotations = emptyList())
    }
    
    private fun createAnnotation(state: AnnotationUiState): PdfAnnotation? {
        return when (state.selectedType) {
            AnnotationTypeOption.HIGHLIGHT -> {
                PdfAnnotation.Highlight(
                    pageIndex = state.pageIndex,
                    rect = AnnotationRect(state.positionX, state.positionY, state.width, state.height),
                    color = state.selectedColor,
                    opacity = 0.4f
                )
            }
            AnnotationTypeOption.STICKY_NOTE -> {
                PdfAnnotation.StickyNote(
                    pageIndex = state.pageIndex,
                    x = state.positionX,
                    y = state.positionY,
                    text = state.annotationText,
                    color = state.selectedColor,
                    width = state.width,
                    height = state.height
                )
            }
            AnnotationTypeOption.TEXT_BOX -> {
                PdfAnnotation.TextBox(
                    pageIndex = state.pageIndex,
                    rect = AnnotationRect(state.positionX, state.positionY, state.width, state.height),
                    text = state.annotationText,
                    fontSize = 12f,
                    textColor = Color.BLACK,
                    backgroundColor = null,
                    borderColor = state.selectedColor
                )
            }
            AnnotationTypeOption.STAMP -> {
                PdfAnnotation.Stamp(
                    pageIndex = state.pageIndex,
                    stampType = state.stampType,
                    x = state.positionX,
                    y = state.positionY,
                    width = state.width,
                    height = state.height
                )
            }
            AnnotationTypeOption.RECTANGLE -> {
                PdfAnnotation.Shape(
                    pageIndex = state.pageIndex,
                    shapeType = ShapeType.RECTANGLE,
                    rect = AnnotationRect(state.positionX, state.positionY, state.width, state.height),
                    strokeColor = state.selectedColor,
                    fillColor = null,
                    strokeWidth = 2f
                )
            }
            AnnotationTypeOption.CIRCLE -> {
                PdfAnnotation.Shape(
                    pageIndex = state.pageIndex,
                    shapeType = ShapeType.CIRCLE,
                    rect = AnnotationRect(state.positionX, state.positionY, state.width, state.height),
                    strokeColor = state.selectedColor,
                    fillColor = null,
                    strokeWidth = 2f
                )
            }
            AnnotationTypeOption.ARROW -> {
                PdfAnnotation.Shape(
                    pageIndex = state.pageIndex,
                    shapeType = ShapeType.ARROW,
                    rect = AnnotationRect(state.positionX, state.positionY, state.width, state.height),
                    strokeColor = state.selectedColor,
                    strokeWidth = 2f
                )
            }
            AnnotationTypeOption.UNDERLINE -> {
                PdfAnnotation.Underline(
                    pageIndex = state.pageIndex,
                    startX = state.positionX,
                    startY = state.positionY,
                    endX = state.positionX + state.width,
                    endY = state.positionY,
                    color = state.selectedColor,
                    strokeWidth = 2f
                )
            }
            AnnotationTypeOption.STRIKETHROUGH -> {
                PdfAnnotation.Strikethrough(
                    pageIndex = state.pageIndex,
                    startX = state.positionX,
                    startY = state.positionY,
                    endX = state.positionX + state.width,
                    endY = state.positionY,
                    color = state.selectedColor,
                    strokeWidth = 2f
                )
            }
        }
    }
    
    fun applyAnnotations(
        context: android.content.Context,
        outputUri: Uri
    ) {
        val sourceUri = _state.value.sourceUri ?: return
        if (_state.value.annotations.isEmpty()) return
        
        viewModelScope.launch {
            _state.value = _state.value.copy(isProcessing = true, progress = 0, error = null)
            
            val annotator = PdfAnnotator()
            
            val result = annotator.addAnnotations(
                context = context,
                inputUri = sourceUri,
                outputUri = outputUri,
                annotations = _state.value.annotations,
                progressCallback = { progress ->
                    _state.value = _state.value.copy(progress = progress)
                }
            )
            
            _state.value = _state.value.copy(
                isProcessing = false,
                isComplete = result.success,
                error = result.errorMessage,
                annotationsAdded = result.annotationsAdded,
                resultUri = if (result.success) outputUri else null
            )
        }
    }
    
    fun reset() {
        _state.value = AnnotationUiState()
    }
}

enum class AnnotationTypeOption(val displayName: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    HIGHLIGHT("Highlight", Icons.Default.Highlight),
    UNDERLINE("Underline", Icons.Default.FormatUnderlined),
    STRIKETHROUGH("Strikethrough", Icons.Default.StrikethroughS),
    STICKY_NOTE("Sticky Note", Icons.Default.Note),
    TEXT_BOX("Text Box", Icons.Default.TextFields),
    RECTANGLE("Rectangle", Icons.Default.Rectangle),
    CIRCLE("Circle", Icons.Default.Circle),
    ARROW("Arrow", Icons.Default.ArrowRightAlt),
    STAMP("Stamp", Icons.Default.Verified)
}

data class AnnotationUiState(
    val sourceUri: Uri? = null,
    val sourceName: String = "",
    val selectedType: AnnotationTypeOption = AnnotationTypeOption.HIGHLIGHT,
    val pageIndex: Int = 0,
    val annotationText: String = "",
    val selectedColor: Int = Color.YELLOW,
    val stampType: StampType = StampType.APPROVED,
    val positionX: Float = 100f,
    val positionY: Float = 700f,
    val width: Float = 200f,
    val height: Float = 30f,
    val annotations: List<PdfAnnotation> = emptyList(),
    val isProcessing: Boolean = false,
    val progress: Int = 0,
    val isComplete: Boolean = false,
    val error: String? = null,
    val annotationsAdded: Int = 0,
    val resultUri: Uri? = null
)

/**
 * Annotation Screen - Add various annotations to PDF documents.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnnotationScreen(
    onNavigateBack: () -> Unit,
    viewModel: AnnotationViewModel = viewModel()
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    
    val colorOptions = listOf(
        Color.YELLOW to "Yellow",
        Color.RED to "Red",
        Color.GREEN to "Green",
        Color.BLUE to "Blue",
        Color.MAGENTA to "Pink",
        Color.CYAN to "Cyan",
        Color.BLACK to "Black"
    )
    
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
        uri?.let { viewModel.applyAnnotations(context, it) }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Annotate PDF") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.annotations.isNotEmpty()) {
                        Badge(
                            containerColor = MaterialTheme.colorScheme.primary
                        ) {
                            Text("${state.annotations.size}")
                        }
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
            
            // Annotation Type Selection
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
                        text = "Annotation Type",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(AnnotationTypeOption.entries) { type ->
                            FilterChip(
                                selected = state.selectedType == type,
                                onClick = { viewModel.setAnnotationType(type) },
                                label = { Text(type.displayName) },
                                leadingIcon = {
                                    Icon(
                                        type.icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            )
                        }
                    }
                }
            }
            
            // Annotation Settings
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
                    
                    // Page Selection
                    OutlinedTextField(
                        value = (state.pageIndex + 1).toString(),
                        onValueChange = { value ->
                            value.toIntOrNull()?.let { page ->
                                if (page > 0) viewModel.setPageIndex(page - 1)
                            }
                        },
                        label = { Text("Page Number") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    
                    // Text input for notes/text boxes
                    AnimatedVisibility(
                        visible = state.selectedType in listOf(
                            AnnotationTypeOption.STICKY_NOTE,
                            AnnotationTypeOption.TEXT_BOX
                        )
                    ) {
                        OutlinedTextField(
                            value = state.annotationText,
                            onValueChange = { viewModel.setAnnotationText(it) },
                            label = { Text("Text") },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 3
                        )
                    }
                    
                    // Stamp type selection
                    AnimatedVisibility(visible = state.selectedType == AnnotationTypeOption.STAMP) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Stamp Type", style = MaterialTheme.typography.bodyMedium)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(StampType.entries) { stamp ->
                                    FilterChip(
                                        selected = state.stampType == stamp,
                                        onClick = { viewModel.setStampType(stamp) },
                                        label = { Text(stamp.text) }
                                    )
                                }
                            }
                        }
                    }
                    
                    // Color selection
                    AnimatedVisibility(
                        visible = state.selectedType != AnnotationTypeOption.STAMP
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Color", style = MaterialTheme.typography.bodyMedium)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(colorOptions) { (color, name) ->
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(androidx.compose.ui.graphics.Color(color))
                                            .border(
                                                width = if (state.selectedColor == color) 3.dp else 1.dp,
                                                color = if (state.selectedColor == color)
                                                    MaterialTheme.colorScheme.primary
                                                else
                                                    MaterialTheme.colorScheme.outline,
                                                shape = CircleShape
                                            )
                                            .clickable { viewModel.setColor(color) }
                                    )
                                }
                            }
                        }
                    }
                    
                    // Position
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = state.positionX.toInt().toString(),
                            onValueChange = { value ->
                                value.toFloatOrNull()?.let { viewModel.setPosition(it, state.positionY) }
                            },
                            label = { Text("X") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = state.positionY.toInt().toString(),
                            onValueChange = { value ->
                                value.toFloatOrNull()?.let { viewModel.setPosition(state.positionX, it) }
                            },
                            label = { Text("Y") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                    
                    // Size
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = state.width.toInt().toString(),
                            onValueChange = { value ->
                                value.toFloatOrNull()?.let { viewModel.setSize(it, state.height) }
                            },
                            label = { Text("Width") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = state.height.toInt().toString(),
                            onValueChange = { value ->
                                value.toFloatOrNull()?.let { viewModel.setSize(state.width, it) }
                            },
                            label = { Text("Height") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                    
                    // Add Annotation Button
                    Button(
                        onClick = { viewModel.addAnnotation() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add Annotation")
                    }
                }
            }
            
            // Added Annotations List
            if (state.annotations.isNotEmpty()) {
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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Annotations (${state.annotations.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            TextButton(onClick = { viewModel.clearAnnotations() }) {
                                Text("Clear All")
                            }
                        }
                        
                        state.annotations.forEachIndexed { index, annotation ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${index + 1}. ${annotation::class.simpleName ?: "Annotation"} (Page ${annotation.pageIndex + 1})",
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    IconButton(onClick = { viewModel.removeAnnotation(index) }) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Remove",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
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
                        Text("Applying annotations... ${state.progress}%")
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
                                "Annotations Applied!",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "${state.annotationsAdded} annotations added",
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
            
            // Apply Annotations Button
            Button(
                onClick = {
                    val fileName = "annotated_${System.currentTimeMillis()}.pdf"
                    saveDocumentLauncher.launch(fileName)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = state.sourceUri != null && 
                         state.annotations.isNotEmpty() &&
                         !state.isProcessing
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Save Annotated PDF")
            }
            
            // Reset Button
            if (state.isComplete) {
                OutlinedButton(
                    onClick = { viewModel.reset() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Annotate Another PDF")
                }
            }
        }
    }
}
