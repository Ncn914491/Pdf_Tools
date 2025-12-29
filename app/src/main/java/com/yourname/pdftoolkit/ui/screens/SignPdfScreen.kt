package com.yourname.pdftoolkit.ui.screens

import android.graphics.Color
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
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
 * ViewModel for Sign PDF Screen.
 */
class SignPdfViewModel : ViewModel() {
    private val _state = MutableStateFlow(SignPdfUiState())
    val state: StateFlow<SignPdfUiState> = _state.asStateFlow()
    
    fun setSourcePdf(uri: Uri, name: String) {
        _state.value = _state.value.copy(sourceUri = uri, sourceName = name)
    }
    
    fun addPathPoint(x: Float, y: Float) {
        val currentPaths = _state.value.signaturePaths.toMutableList()
        val currentPath = _state.value.currentPath.toMutableList()
        currentPath.add(SignaturePoint(x, y))
        _state.value = _state.value.copy(currentPath = currentPath)
    }
    
    fun finishCurrentPath() {
        if (_state.value.currentPath.isNotEmpty()) {
            val currentPaths = _state.value.signaturePaths.toMutableList()
            currentPaths.add(SignaturePath(_state.value.currentPath))
            _state.value = _state.value.copy(
                signaturePaths = currentPaths,
                currentPath = emptyList()
            )
        }
    }
    
    fun clearSignature() {
        _state.value = _state.value.copy(
            signaturePaths = emptyList(),
            currentPath = emptyList()
        )
    }
    
    fun setPageIndex(index: Int) {
        _state.value = _state.value.copy(pageIndex = index)
    }
    
    fun setSignaturePosition(x: Float, y: Float) {
        _state.value = _state.value.copy(signatureX = x, signatureY = y)
    }
    
    fun setSignatureSize(width: Float, height: Float) {
        _state.value = _state.value.copy(signatureWidth = width, signatureHeight = height)
    }
    
    fun toggleAddDate() {
        _state.value = _state.value.copy(addDate = !_state.value.addDate)
    }
    
    fun toggleAddName() {
        _state.value = _state.value.copy(addName = !_state.value.addName)
    }
    
    fun setName(name: String) {
        _state.value = _state.value.copy(signerName = name)
    }
    
    fun signPdf(
        context: android.content.Context,
        outputUri: Uri
    ) {
        val currentState = _state.value
        val sourceUri = currentState.sourceUri ?: return
        
        if (currentState.signaturePaths.isEmpty()) return
        
        viewModelScope.launch {
            _state.value = _state.value.copy(isProcessing = true, progress = 0, error = null)
            
            val signer = PdfSigner(context)
            
            val signatureData = SignatureData(
                paths = currentState.signaturePaths,
                strokeWidth = 3f,
                strokeColor = Color.BLACK
            )
            
            val placement = SignaturePlacement(
                pageIndex = currentState.pageIndex,
                x = currentState.signatureX,
                y = currentState.signatureY,
                width = currentState.signatureWidth,
                height = currentState.signatureHeight
            )
            
            val extras = SignatureExtras(
                addDate = currentState.addDate,
                addName = currentState.addName,
                name = currentState.signerName
            )
            
            val result = signer.addSignature(
                inputUri = sourceUri,
                outputUri = outputUri,
                signatureData = signatureData,
                placement = placement,
                extras = extras,
                progressCallback = { progress ->
                    _state.value = _state.value.copy(progress = progress)
                }
            )
            
            _state.value = _state.value.copy(
                isProcessing = false,
                isComplete = result.success,
                error = result.errorMessage,
                resultUri = if (result.success) outputUri else null
            )
        }
    }
    
    fun reset() {
        _state.value = SignPdfUiState()
    }
}

data class SignPdfUiState(
    val sourceUri: Uri? = null,
    val sourceName: String = "",
    val signaturePaths: List<SignaturePath> = emptyList(),
    val currentPath: List<SignaturePoint> = emptyList(),
    val pageIndex: Int = 0,
    val signatureX: Float = 50f,
    val signatureY: Float = 50f,
    val signatureWidth: Float = 200f,
    val signatureHeight: Float = 100f,
    val addDate: Boolean = true,
    val addName: Boolean = false,
    val signerName: String = "",
    val isProcessing: Boolean = false,
    val progress: Int = 0,
    val isComplete: Boolean = false,
    val error: String? = null,
    val resultUri: Uri? = null
)

/**
 * Sign PDF Screen - Add handwritten signatures to PDF documents.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignPdfScreen(
    onNavigateBack: () -> Unit,
    viewModel: SignPdfViewModel = viewModel()
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
        uri?.let { viewModel.signPdf(context, it) }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sign PDF") },
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
            
            // Signature Pad
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
                            text = "Draw Your Signature",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        TextButton(onClick = { viewModel.clearSignature() }) {
                            Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Clear")
                        }
                    }
                    
                    // Signature Canvas
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(androidx.compose.ui.graphics.Color.White)
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outline,
                                RoundedCornerShape(8.dp)
                            )
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        viewModel.addPathPoint(offset.x, offset.y)
                                    },
                                    onDrag = { change, _ ->
                                        viewModel.addPathPoint(change.position.x, change.position.y)
                                    },
                                    onDragEnd = {
                                        viewModel.finishCurrentPath()
                                    }
                                )
                            }
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            // Draw completed paths
                            for (signaturePath in state.signaturePaths) {
                                if (signaturePath.points.size > 1) {
                                    val path = Path()
                                    val firstPoint = signaturePath.points.first()
                                    path.moveTo(firstPoint.x, firstPoint.y)
                                    
                                    for (i in 1 until signaturePath.points.size) {
                                        val point = signaturePath.points[i]
                                        path.lineTo(point.x, point.y)
                                    }
                                    
                                    drawPath(
                                        path = path,
                                        color = androidx.compose.ui.graphics.Color.Black,
                                        style = Stroke(
                                            width = 3f,
                                            cap = StrokeCap.Round,
                                            join = StrokeJoin.Round
                                        )
                                    )
                                }
                            }
                            
                            // Draw current path
                            if (state.currentPath.size > 1) {
                                val path = Path()
                                val firstPoint = state.currentPath.first()
                                path.moveTo(firstPoint.x, firstPoint.y)
                                
                                for (i in 1 until state.currentPath.size) {
                                    val point = state.currentPath[i]
                                    path.lineTo(point.x, point.y)
                                }
                                
                                drawPath(
                                    path = path,
                                    color = androidx.compose.ui.graphics.Color.Black,
                                    style = Stroke(
                                        width = 3f,
                                        cap = StrokeCap.Round,
                                        join = StrokeJoin.Round
                                    )
                                )
                            }
                        }
                        
                        // Placeholder text
                        if (state.signaturePaths.isEmpty() && state.currentPath.isEmpty()) {
                            Text(
                                text = "Sign here",
                                modifier = Modifier.align(Alignment.Center),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            }
            
            // Signature Placement
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
                        text = "Placement",
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
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Description, contentDescription = null) }
                    )
                    
                    // Position
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = state.signatureX.toInt().toString(),
                            onValueChange = { value ->
                                value.toFloatOrNull()?.let { viewModel.setSignaturePosition(it, state.signatureY) }
                            },
                            label = { Text("X Position") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = state.signatureY.toInt().toString(),
                            onValueChange = { value ->
                                value.toFloatOrNull()?.let { viewModel.setSignaturePosition(state.signatureX, it) }
                            },
                            label = { Text("Y Position") },
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
                            value = state.signatureWidth.toInt().toString(),
                            onValueChange = { value ->
                                value.toFloatOrNull()?.let { viewModel.setSignatureSize(it, state.signatureHeight) }
                            },
                            label = { Text("Width") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = state.signatureHeight.toInt().toString(),
                            onValueChange = { value ->
                                value.toFloatOrNull()?.let { viewModel.setSignatureSize(state.signatureWidth, it) }
                            },
                            label = { Text("Height") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                }
            }
            
            // Additional Options
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
                        text = "Additional Info",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Add Date", modifier = Modifier.weight(1f))
                        Switch(
                            checked = state.addDate,
                            onCheckedChange = { viewModel.toggleAddDate() }
                        )
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Add Name", modifier = Modifier.weight(1f))
                        Switch(
                            checked = state.addName,
                            onCheckedChange = { viewModel.toggleAddName() }
                        )
                    }
                    
                    AnimatedVisibility(visible = state.addName) {
                        OutlinedTextField(
                            value = state.signerName,
                            onValueChange = { viewModel.setName(it) },
                            label = { Text("Your Name") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
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
                        Text("Adding signature... ${state.progress}%")
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
                        Text(
                            "PDF Signed Successfully!",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
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
            
            // Sign Button
            Button(
                onClick = {
                    val fileName = "signed_${System.currentTimeMillis()}.pdf"
                    saveDocumentLauncher.launch(fileName)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = state.sourceUri != null && 
                         state.signaturePaths.isNotEmpty() &&
                         !state.isProcessing
            ) {
                Icon(Icons.Default.Draw, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Sign PDF")
            }
            
            // Reset Button
            if (state.isComplete) {
                OutlinedButton(
                    onClick = { viewModel.reset() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sign Another PDF")
                }
            }
        }
    }
}
