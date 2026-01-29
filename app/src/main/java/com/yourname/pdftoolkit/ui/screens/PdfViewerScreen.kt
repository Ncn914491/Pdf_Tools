package com.yourname.pdftoolkit.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.RectF
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import com.yourname.pdftoolkit.data.SafUriManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * PDF Viewer Screen with annotation support.
 * Supports zoom, scroll, page navigation, highlighting, and marking.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(
    pdfUri: Uri?,
    pdfName: String = "PDF Document",
    onNavigateBack: () -> Unit,
    onNavigateToTool: ((String, Uri?, String?) -> Unit)? = null,
    viewModel: PdfViewerViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // ViewModel state
    val uiState by viewModel.uiState.collectAsState()
    val toolState by viewModel.toolState.collectAsState()
    val selectedAnnotationTool by viewModel.selectedAnnotationTool.collectAsState()
    val selectedColor by viewModel.selectedColor.collectAsState()
    val annotations by viewModel.annotations.collectAsState()

    // Local UI state
    var currentPage by remember { mutableIntStateOf(1) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var showControls by remember { mutableStateOf(true) }
    var showPageSelector by remember { mutableStateOf(false) }
    
    // Password state
    var showPasswordDialog by remember { mutableStateOf(false) }
    var isPasswordError by remember { mutableStateOf(false) }
    var pdfLoadTrigger by remember { mutableStateOf(0) } // To force reload
    
    // Annotation drawing state (transient)
    var currentStroke by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var currentDrawingPageIndex by remember { mutableIntStateOf(-1) }
    var showColorPicker by remember { mutableStateOf(false) }
    
    // Search state
    var isSearchMode by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var extractedText by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
    var searchResults by remember { mutableStateOf<List<Pair<Int, Int>>>(emptyList()) } // (pageIndex, position)
    var currentSearchResultIndex by remember { mutableIntStateOf(0) }
    
    // Save state
    var isSaving by remember { mutableStateOf(false) }
    var saveSuccess by remember { mutableStateOf<Boolean?>(null) }
    
    // Save document launcher
    val saveDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        uri?.let { outputUri ->
            if (pdfUri != null && annotations.isNotEmpty() && uiState is PdfViewerUiState.Loaded) {
                val totalPages = (uiState as PdfViewerUiState.Loaded).totalPages
                scope.launch {
                    isSaving = true
                    saveSuccess = null
                    try {
                        val success = saveAnnotatedPdf(
                            context = context,
                            outputUri = outputUri,
                            viewModel = viewModel,
                            totalPages = totalPages,
                            annotations = annotations
                        )
                        saveSuccess = success
                        if (success) {
                            SafUriManager.addRecentFile(context, outputUri)
                            Toast.makeText(context, "Annotations saved successfully!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Failed to save annotations", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Log.e("PdfViewerScreen", "Error saving annotations: ${e.message}", e)
                        saveSuccess = false
                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    } finally {
                        isSaving = false
                    }
                }
            }
        }
    }
    
    val listState = rememberLazyListState()
    
    // Track visible page based on scroll position
    LaunchedEffect(listState.firstVisibleItemIndex) {
        currentPage = listState.firstVisibleItemIndex + 1
    }
    
    // Sync tool state with local search mode
    // If toolState becomes Search, enable isSearchMode. If it becomes something else, disable it.
    // However, isSearchMode is local. Let's sync one way: ViewModel -> Local
    LaunchedEffect(toolState) {
        isSearchMode = toolState is PdfTool.Search
    }

    // Load PDF when screen opens or password/trigger changes
    LaunchedEffect(pdfUri, pdfLoadTrigger) {
        if (pdfUri != null) {
            // Check URI permissions first
            if (!SafUriManager.canAccessUri(context, pdfUri)) {
                try {
                    context.contentResolver.takePersistableUriPermission(
                        pdfUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (e: Exception) {
                    Log.w("PdfViewerScreen", "Failed to take persistable permission: ${e.message}")
                }
            }
            
            // Password handling is done via viewModel.loadPdf parameter.
            // But we need to ask for password if load fails.
            // Current ViewModel implementation sets Error state.
            // We should modify ViewModel to have a specific PasswordRequired state or handle generic error.
            // For now, if Error state contains "password", show dialog.

            // Initial load (empty password)
             viewModel.loadPdf(context, pdfUri, "")
        }
    }
    
    // Handle UI State
    val isLoading = uiState is PdfViewerUiState.Loading
    val errorMessage = (uiState as? PdfViewerUiState.Error)?.message
    val totalPages = (uiState as? PdfViewerUiState.Loaded)?.totalPages ?: 0

    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
             val isPasswordIssue = errorMessage.contains("password", ignoreCase = true) ||
                                     errorMessage.contains("encrypted", ignoreCase = true)
             if (isPasswordIssue) {
                 showPasswordDialog = true
                 isPasswordError = true // Assume error if we are here (simplification)
             }
        }
    }
    
    // Extract text from PDF for search (when PDF loads)
    LaunchedEffect(pdfUri, totalPages) {
        if (pdfUri != null && totalPages > 0 && extractedText.isEmpty()) {
            scope.launch {
                val textMap = extractPdfText(context, pdfUri, totalPages)
                extractedText = textMap
            }
        }
    }
    
    // Perform search when query changes
    LaunchedEffect(searchQuery, extractedText) {
        if (searchQuery.length >= 2) {
            val results = mutableListOf<Pair<Int, Int>>()
            val query = searchQuery.lowercase()
            extractedText.forEach { (pageIndex, text) ->
                var pos = 0
                val lowerText = text.lowercase()
                while (true) {
                    val found = lowerText.indexOf(query, pos)
                    if (found == -1) break
                    results.add(pageIndex to found)
                    pos = found + 1
                }
            }
            searchResults = results
            currentSearchResultIndex = 0
            
            // Navigate to first result
            if (results.isNotEmpty()) {
                listState.animateScrollToItem(results[0].first)
            }
        } else {
            searchResults = emptyList()
        }
    }
    
    Scaffold(
        topBar = {
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically()
            ) {
                if (toolState is PdfTool.Search) {
                    // Search mode top bar
                    TopAppBar(
                        title = {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("Search in PDF...") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color.Transparent,
                                    unfocusedBorderColor = Color.Transparent
                                ),
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        Text(
                                            text = if (searchResults.isNotEmpty()) 
                                                "${currentSearchResultIndex + 1}/${searchResults.size}" 
                                            else "No matches",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (searchResults.isNotEmpty()) 
                                                MaterialTheme.colorScheme.primary 
                                            else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = { 
                                viewModel.setTool(PdfTool.None)
                                searchQuery = ""
                            }) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Close search")
                            }
                        },
                        actions = {
                            // Navigate search results
                            if (searchResults.isNotEmpty()) {
                                IconButton(onClick = {
                                    if (currentSearchResultIndex > 0) {
                                        currentSearchResultIndex--
                                        scope.launch {
                                            listState.animateScrollToItem(searchResults[currentSearchResultIndex].first)
                                        }
                                    }
                                }) {
                                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Previous")
                                }
                                IconButton(onClick = {
                                    if (currentSearchResultIndex < searchResults.size - 1) {
                                        currentSearchResultIndex++
                                        scope.launch {
                                            listState.animateScrollToItem(searchResults[currentSearchResultIndex].first)
                                        }
                                    }
                                }) {
                                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Next")
                                }
                            }
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear search")
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                } else {
                    // Normal top bar
                    TopAppBar(
                        title = {
                            Column {
                                Text(
                                    text = pdfName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )
                                if (totalPages > 0) {
                                    Text(
                                        text = "Page $currentPage of $totalPages",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = onNavigateBack) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                            }
                        },
                        actions = {
                            // Search button
                            IconButton(onClick = { viewModel.setTool(PdfTool.Search) }) {
                                Icon(Icons.Default.Search, contentDescription = "Search")
                            }
                            
                            val isEditMode = toolState is PdfTool.Edit

                            // Save annotations button (only in edit mode with annotations)
                            if (isEditMode && annotations.isNotEmpty()) {
                                if (isSaving) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    IconButton(
                                        onClick = {
                                            val fileName = "annotated_${pdfName}_${System.currentTimeMillis()}.pdf"
                                            saveDocumentLauncher.launch(fileName)
                                        }
                                    ) {
                                        Icon(
                                            Icons.Default.Save,
                                            contentDescription = "Save Annotations",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                            
                            // Edit/Annotate toggle
                            IconButton(
                                onClick = { 
                                    if (isEditMode) {
                                        viewModel.setTool(PdfTool.None)
                                    } else {
                                        viewModel.setTool(PdfTool.Edit)
                                    }
                                }
                            ) {
                                Icon(
                                    if (isEditMode) Icons.Default.Check else Icons.Default.Edit,
                                    contentDescription = if (isEditMode) "Done Editing" else "Edit",
                                    tint = if (isEditMode) MaterialTheme.colorScheme.primary else LocalContentColor.current
                                )
                            }
                            
                            // Zoom controls
                            IconButton(onClick = { scale = (scale * 1.25f).coerceAtMost(3f) }) {
                                Icon(Icons.Default.ZoomIn, contentDescription = "Zoom In")
                            }
                            IconButton(onClick = { scale = (scale / 1.25f).coerceAtLeast(0.5f) }) {
                                Icon(Icons.Default.ZoomOut, contentDescription = "Zoom Out")
                            }
                        
                        // More options menu
                        var showMenu by remember { mutableStateOf(false) }
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More Options")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            if (pdfUri != null) {
                                DropdownMenuItem(
                                    text = { Text("Share") },
                                    leadingIcon = { Icon(Icons.Default.Share, null) },
                                    onClick = {
                                        showMenu = false
                                        sharePdf(context, pdfUri)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Open with...") },
                                    leadingIcon = { Icon(Icons.Default.OpenInNew, null) },
                                    onClick = {
                                        showMenu = false
                                        openWithExternalApp(context, pdfUri)
                                    }
                                )
                                Divider()
                            }
                            if (totalPages > 1) {
                                DropdownMenuItem(
                                    text = { Text("Go to Page") },
                                    leadingIcon = { Icon(Icons.Default.ViewList, null) },
                                    onClick = {
                                        showMenu = false
                                        showPageSelector = true
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Reset Zoom") },
                                leadingIcon = { Icon(Icons.Default.FitScreen, null) },
                                onClick = {
                                    showMenu = false
                                    scale = 1f
                                    offsetX = 0f
                                    offsetY = 0f
                                }
                            )
                            if (annotations.isNotEmpty()) {
                                Divider()
                                DropdownMenuItem(
                                    text = { Text("Clear All Annotations") },
                                    leadingIcon = { Icon(Icons.Default.ClearAll, null) },
                                    onClick = {
                                        showMenu = false
                                        viewModel.clearAnnotations()
                                    }
                                )
                            }
                            Divider()
                            // Tools navigation
                            DropdownMenuItem(
                                text = { Text("Compress this PDF") },
                                leadingIcon = { Icon(Icons.Default.Compress, null) },
                                onClick = {
                                    showMenu = false
                                    onNavigateToTool?.invoke("compress", pdfUri, pdfName)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Add Watermark") },
                                leadingIcon = { Icon(Icons.Default.WaterDrop, null) },
                                onClick = {
                                    showMenu = false
                                    onNavigateToTool?.invoke("watermark", pdfUri, pdfName)
                                }
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                    )
                )
                } // end else (normal top bar)
            }
        },
        bottomBar = {
            Column {
                val isEditMode = toolState is PdfTool.Edit

                // Annotation toolbar
                AnimatedVisibility(
                    visible = isEditMode && showControls,
                    enter = fadeIn() + slideInVertically { it },
                    exit = fadeOut() + slideOutVertically { it }
                ) {
                    AnnotationToolbar(
                        selectedTool = selectedAnnotationTool,
                        selectedColor = selectedColor,
                        onToolSelected = { viewModel.setAnnotationTool(it) },
                        onColorPickerClick = { showColorPicker = true },
                        onUndoClick = { viewModel.undoAnnotation() },
                        canUndo = annotations.isNotEmpty()
                    )
                }
                
                // Page navigation bar
                AnimatedVisibility(
                    visible = showControls && totalPages > 1 && !isEditMode,
                    enter = fadeIn() + slideInVertically { it },
                    exit = fadeOut() + slideOutVertically { it }
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    scope.launch { listState.animateScrollToItem(0) }
                                },
                                enabled = currentPage > 1
                            ) {
                                Icon(Icons.Default.FirstPage, contentDescription = "First Page")
                            }
                            
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        listState.animateScrollToItem((currentPage - 2).coerceAtLeast(0))
                                    }
                                },
                                enabled = currentPage > 1
                            ) {
                                Icon(Icons.Default.ChevronLeft, contentDescription = "Previous Page")
                            }
                            
                            Spacer(modifier = Modifier.width(16.dp))
                            
                            Text(
                                text = "$currentPage / $totalPages",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium
                            )
                            
                            Spacer(modifier = Modifier.width(16.dp))
                            
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        listState.animateScrollToItem(currentPage.coerceAtMost(totalPages - 1))
                                    }
                                },
                                enabled = currentPage < totalPages
                            ) {
                                Icon(Icons.Default.ChevronRight, contentDescription = "Next Page")
                            }
                            
                            IconButton(
                                onClick = {
                                    scope.launch { listState.animateScrollToItem(totalPages - 1) }
                                },
                                enabled = currentPage < totalPages
                            ) {
                                Icon(Icons.Default.LastPage, contentDescription = "Last Page")
                            }
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = !(toolState is PdfTool.Edit)
                ) {
                    showControls = !showControls
                }
        ) {
            when (uiState) {
                is PdfViewerUiState.Loading -> {
                    LoadingState()
                }
                
                is PdfViewerUiState.Error -> {
                    // Handled by side effect, but show basic error here if not password
                    if (isPasswordError) {
                        // Password dialog will show
                        LoadingState() // Keep showing loading/clean state behind dialog
                    } else {
                        ErrorState(
                            message = (uiState as PdfViewerUiState.Error).message,
                            onGoBack = onNavigateBack
                        )
                    }
                }
                
                is PdfViewerUiState.Loaded -> {
                    val isEditMode = toolState is PdfTool.Edit
                    PdfPagesContent(
                        totalPages = totalPages,
                        loadPage = { viewModel.loadPage(it) },
                        scale = scale,
                        onScaleChange = { scale = it },
                        offsetX = offsetX,
                        offsetY = offsetY,
                        onOffsetChange = { x, y ->
                            offsetX = x
                            offsetY = y
                        },
                        listState = listState,
                        isEditMode = isEditMode,
                        selectedTool = selectedAnnotationTool,
                        selectedColor = selectedColor,
                        annotations = annotations,
                        currentStroke = currentStroke,
                        onCurrentStrokeChange = { currentStroke = it },
                        onAddAnnotation = { stroke ->
                            viewModel.addAnnotation(stroke)
                            currentStroke = emptyList()
                        },
                        currentDrawingPageIndex = currentDrawingPageIndex,
                        onDrawingPageIndexChange = { currentDrawingPageIndex = it },
                        // Pass search params
                        isSearchMode = isSearchMode,
                        searchQuery = searchQuery,
                        searchResults = searchResults,
                        currentSearchResultIndex = currentSearchResultIndex,
                        pdfUri = pdfUri
                    )
                }

                PdfViewerUiState.Idle -> {
                    // Initial state
                }
            }
        }
    }
    
    // Page selector dialog
    if (showPageSelector) {
        PageSelectorDialog(
            currentPage = currentPage,
            totalPages = totalPages,
            onPageSelected = { page ->
                scope.launch { listState.animateScrollToItem(page - 1) }
                showPageSelector = false
            },
            onDismiss = { showPageSelector = false }
        )
    }
    
    // Color picker dialog
    if (showColorPicker) {
        ColorPickerDialog(
            selectedColor = selectedColor,
            onColorSelected = { 
                viewModel.setColor(it)
                showColorPicker = false
            },
            onDismiss = { showColorPicker = false }
        )
    }
    
    // Password dialog
    if (showPasswordDialog) {
        PasswordDialog(
            onConfirm = { input ->
                showPasswordDialog = false
                if (pdfUri != null) {
                    viewModel.loadPdf(context, pdfUri, input)
                }
            },
            onDismiss = { 
                showPasswordDialog = false
                onNavigateBack() // Close viewer if cancelled
            },
            isError = isPasswordError
        )
    }
}

// ... AnnotationToolbar ... (kept same)
// ... ToolButton ... (kept same)
// ... ColorPickerDialog ... (kept same)
// ... ColorOption ... (kept same)
// ... LoadingState ... (kept same)
// ... ErrorState ... (kept same)
// ... PageSelectorDialog ... (kept same)
// ... sharePdf ... (kept same)
// ... openWithExternalApp ... (kept same)

@Composable
private fun AnnotationToolbar(
    selectedTool: AnnotationTool,
    selectedColor: Color,
    onToolSelected: (AnnotationTool) -> Unit,
    onColorPickerClick: () -> Unit,
    onUndoClick: () -> Unit,
    canUndo: Boolean
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Highlighter tool
            ToolButton(
                icon = Icons.Default.Highlight,
                label = "Highlight",
                isSelected = selectedTool == AnnotationTool.HIGHLIGHTER,
                onClick = { onToolSelected(AnnotationTool.HIGHLIGHTER) }
            )
            
            // Marker tool
            ToolButton(
                icon = Icons.Default.Gesture,
                label = "Marker",
                isSelected = selectedTool == AnnotationTool.MARKER,
                onClick = { onToolSelected(AnnotationTool.MARKER) }
            )
            
            // Underline tool
            ToolButton(
                icon = Icons.Default.FormatUnderlined,
                label = "Underline",
                isSelected = selectedTool == AnnotationTool.UNDERLINE,
                onClick = { onToolSelected(AnnotationTool.UNDERLINE) }
            )
            
            // Color picker
            IconButton(onClick = onColorPickerClick) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(selectedColor)
                        .padding(2.dp)
                )
            }
            
            // Undo button
            IconButton(
                onClick = onUndoClick,
                enabled = canUndo
            ) {
                Icon(
                    Icons.Default.Undo,
                    contentDescription = "Undo",
                    tint = if (canUndo) LocalContentColor.current else LocalContentColor.current.copy(alpha = 0.38f)
                )
            }
        }
    }
}

@Composable
private fun ToolButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .clip(CircleShape)
                .background(
                    if (isSelected) MaterialTheme.colorScheme.primaryContainer 
                    else Color.Transparent
                )
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                       else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ColorPickerDialog(
    selectedColor: Color,
    onColorSelected: (Color) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = listOf(
        Color.Yellow.copy(alpha = 0.5f) to "Yellow",
        Color.Green.copy(alpha = 0.5f) to "Green",
        Color.Cyan.copy(alpha = 0.5f) to "Cyan",
        Color.Magenta.copy(alpha = 0.5f) to "Pink",
        Color.Red.copy(alpha = 0.5f) to "Red",
        Color.Blue.copy(alpha = 0.5f) to "Blue",
        Color(0xFF614700).copy(alpha = 0.8f) to "Brown",
        Color.Black.copy(alpha = 0.8f) to "Black"
    )
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Color") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    colors.take(4).forEach { (color, name) ->
                        ColorOption(
                            color = color,
                            name = name,
                            isSelected = selectedColor == color,
                            onClick = { onColorSelected(color) }
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    colors.drop(4).forEach { (color, name) ->
                        ColorOption(
                            color = color,
                            name = name,
                            isSelected = selectedColor == color,
                            onClick = { onColorSelected(color) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun ColorOption(
    color: Color,
    name: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape),
            color = color,
            onClick = onClick,
            shape = CircleShape,
            border = if (isSelected) {
                ButtonDefaults.outlinedButtonBorder
            } else null
        ) {
            if (isSelected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "Selected",
                    modifier = Modifier.padding(12.dp),
                    tint = Color.Black.copy(alpha = 0.7f)
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun LoadingState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(48.dp),
            strokeWidth = 4.dp
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Loading PDF...",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ErrorState(
    message: String,
    onGoBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Error,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Unable to open PDF",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onGoBack) {
            Text("Go Back")
        }
    }
}

@Composable
private fun PdfPagesContent(
    totalPages: Int,
    loadPage: suspend (Int) -> Bitmap?,
    scale: Float,
    onScaleChange: (Float) -> Unit,
    offsetX: Float,
    offsetY: Float,
    onOffsetChange: (Float, Float) -> Unit,
    listState: LazyListState,
    isEditMode: Boolean,
    selectedTool: AnnotationTool,
    selectedColor: Color,
    annotations: List<AnnotationStroke>,
    currentStroke: List<Offset>,
    onCurrentStrokeChange: (List<Offset>) -> Unit,
    onAddAnnotation: (AnnotationStroke) -> Unit,
    currentDrawingPageIndex: Int,
    onDrawingPageIndexChange: (Int) -> Unit,
    // Search params
    isSearchMode: Boolean = false,
    searchQuery: String = "",
    searchResults: List<Pair<Int, Int>> = emptyList(),
    currentSearchResultIndex: Int = 0,
    pdfUri: Uri? = null
) {
    // Track container size for pan boundary calculation
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    
    LazyColumn(
        state = listState,
        userScrollEnabled = !isEditMode,
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { containerSize = it }
            .then(
                if (!isEditMode) {
                    Modifier.pointerInput(Unit) {
                        detectTransformGestures { centroid, pan, zoom, _ ->
                            val oldScale = scale
                            val newScale = (scale * zoom).coerceIn(0.5f, 3f)
                            
                            if (newScale > 1f) {
                                val zoomFactor = newScale / oldScale
                                val centerX = containerSize.width / 2f
                                val centerY = containerSize.height / 2f

                                // Calculate new offset to zoom around centroid
                                val newOffsetXCandidate = (centroid.x - centerX) * (1 - zoomFactor) + offsetX * zoomFactor + pan.x
                                val newOffsetYCandidate = (centroid.y - centerY) * (1 - zoomFactor) + offsetY * zoomFactor + pan.y

                                val maxOffsetX = (containerSize.width * (newScale - 1f)) / 2f
                                val maxOffsetY = (containerSize.height * (newScale - 1f)) / 2f
                                
                                onOffsetChange(
                                    newOffsetXCandidate.coerceIn(-maxOffsetX, maxOffsetX),
                                    newOffsetYCandidate.coerceIn(-maxOffsetY, maxOffsetY)
                                )
                            } else {
                                onOffsetChange(0f, 0f)
                            }
                            
                            onScaleChange(newScale)
                        }
                    }
                } else Modifier
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        items(totalPages) { index ->
            // Check if this page has the current result
            val pageResults = searchResults.filter { it.first == index }
            val currentGlobalResult = searchResults.getOrNull(currentSearchResultIndex)
            val currentMatchIndexOnPage = if (isSearchMode && currentGlobalResult != null && currentGlobalResult.first == index) {
                pageResults.indexOf(currentGlobalResult)
            } else {
                -1
            }
            
            PdfPageWithAnnotations(
                pageIndex = index,
                loadPage = loadPage,
                scale = scale,
                offsetX = offsetX,
                offsetY = offsetY,
                isEditMode = isEditMode,
                selectedTool = selectedTool,
                selectedColor = selectedColor,
                annotations = annotations.filter { it.pageIndex == index },
                currentStroke = if (currentDrawingPageIndex == index) currentStroke else emptyList(),
                onCurrentStrokeChange = { stroke ->
                    onDrawingPageIndexChange(index)
                    onCurrentStrokeChange(stroke)
                },
                onAddAnnotation = onAddAnnotation,
                // Search params
                isSearchMode = isSearchMode,
                searchQuery = searchQuery,
                currentMatchIndexOnPage = currentMatchIndexOnPage,
                pdfUri = pdfUri
            )
            
            Text(
                text = "Page ${index + 1}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun PdfPageWithAnnotations(
    pageIndex: Int,
    loadPage: suspend (Int) -> Bitmap?,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    isEditMode: Boolean,
    selectedTool: AnnotationTool,
    selectedColor: Color,
    annotations: List<AnnotationStroke>,
    currentStroke: List<Offset>,
    onCurrentStrokeChange: (List<Offset>) -> Unit,
    onAddAnnotation: (AnnotationStroke) -> Unit,
    // Search params
    isSearchMode: Boolean = false,
    searchQuery: String = "",
    currentMatchIndexOnPage: Int = -1,
    pdfUri: Uri? = null
) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val context = LocalContext.current
    
    // Load bitmap lazily
    val bitmap by produceState<Bitmap?>(initialValue = null, key1 = pageIndex) {
        value = loadPage(pageIndex)
    }

    // Asynchronously load search highlights
    val searchHighlights by produceState<List<List<RectF>>>(
        initialValue = emptyList(),
        key1 = isSearchMode,
        key2 = searchQuery,
        key3 = pageIndex
    ) {
        if (isSearchMode && searchQuery.length >= 2 && pdfUri != null) {
            value = getSearchHighlights(context, pdfUri, pageIndex, searchQuery)
        } else {
            value = emptyList()
        }
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offsetX
                translationY = offsetY
            },
        shape = MaterialTheme.shapes.small,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { size = it }
                .heightIn(min = 200.dp) // Minimum height for placeholder
        ) {
            if (bitmap != null) {
                // PDF page image
                Image(
                    bitmap = bitmap!!.asImageBitmap(),
                    contentDescription = "Page ${pageIndex + 1}",
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small),
                    contentScale = ContentScale.FillWidth
                )
            } else {
                // Placeholder while loading
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f / 1.414f) // Approx A4 aspect ratio
                        .background(Color.LightGray.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                }
            }
            
            // Search Highlights Overlay
            if (isSearchMode && searchHighlights.isNotEmpty() && bitmap != null) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    searchHighlights.forEachIndexed { index, rects ->
                        val color = if (index == currentMatchIndexOnPage) {
                            Color(0xFFFF8C00).copy(alpha = 0.5f) // Dark Orange for current
                        } else {
                            Color.Yellow.copy(alpha = 0.4f) // Yellow for others
                        }
                        
                        rects.forEach { rect ->
                            // Scale rect to current canvas size
                            val scaleX = size.width.toFloat() / bitmap!!.width.toFloat()
                            val scaleY = size.height.toFloat() / bitmap!!.height.toFloat()
                            
                            drawRect(
                                color = color,
                                topLeft = Offset(rect.left * scaleX, rect.top * scaleY),
                                size = androidx.compose.ui.geometry.Size(
                                    width = (rect.width()) * scaleX,
                                    height = (rect.height()) * scaleY
                                )
                            )
                        }
                    }
                }
            }
            
            // Annotation overlay
            if ((isEditMode || annotations.isNotEmpty()) && bitmap != null) {
                Canvas(
                    modifier = Modifier
                        .matchParentSize()
                        .then(
                            if (isEditMode && selectedTool != AnnotationTool.NONE) {
                                Modifier.pointerInput(isEditMode, selectedTool) {
                                    if (!isEditMode || selectedTool == AnnotationTool.NONE) return@pointerInput
                                    
                                    var localStroke = mutableListOf<Offset>()

                                    detectDragGestures(
                                        onDragStart = { offset ->
                                            localStroke = mutableListOf(offset)
                                            onCurrentStrokeChange(localStroke)
                                        },
                                        onDrag = { change, _ ->
                                            change.consume()
                                            localStroke.add(change.position)
                                            onCurrentStrokeChange(localStroke.toList())
                                        },
                                        onDragEnd = {
                                            if (localStroke.isNotEmpty()) {
                                                val strokeWidth = when (selectedTool) {
                                                    AnnotationTool.HIGHLIGHTER -> 20f
                                                    AnnotationTool.MARKER -> 8f
                                                    AnnotationTool.UNDERLINE -> 4f
                                                    else -> 8f
                                                }
                                                onAddAnnotation(
                                                    AnnotationStroke(
                                                        pageIndex = pageIndex,
                                                        tool = selectedTool,
                                                        color = selectedColor,
                                                        points = localStroke.toList(),
                                                        strokeWidth = strokeWidth
                                                    )
                                                )
                                                localStroke = mutableListOf()
                                            }
                                        }
                                    )
                                }
                            } else Modifier
                        )
                ) {
                    // Draw saved annotations
                    annotations.forEach { stroke ->
                        if (stroke.points.isNotEmpty()) {
                            val path = androidx.compose.ui.graphics.Path().apply {
                                moveTo(stroke.points.first().x, stroke.points.first().y)
                                for (i in 1 until stroke.points.size) {
                                    lineTo(stroke.points[i].x, stroke.points[i].y)
                                }
                            }
                            drawPath(
                                path = path,
                                color = stroke.color,
                                style = androidx.compose.ui.graphics.drawscope.Stroke(
                                    width = stroke.strokeWidth,
                                    cap = StrokeCap.Round,
                                    join = androidx.compose.ui.graphics.StrokeJoin.Round
                                )
                            )
                        }
                    }
                    
                    // Draw current stroke being drawn
                    if (currentStroke.isNotEmpty()) {
                        val path = androidx.compose.ui.graphics.Path().apply {
                            moveTo(currentStroke.first().x, currentStroke.first().y)
                            for (i in 1 until currentStroke.size) {
                                lineTo(currentStroke[i].x, currentStroke[i].y)
                            }
                        }
                        val strokeWidth = when (selectedTool) {
                            AnnotationTool.HIGHLIGHTER -> 20f
                            AnnotationTool.MARKER -> 8f
                            AnnotationTool.UNDERLINE -> 4f
                            else -> 8f
                        }
                        drawPath(
                            path = path,
                            color = selectedColor,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(
                                width = strokeWidth,
                                cap = StrokeCap.Round
                            )
                        )
                    }
                }
            }
        }
    }
}

private suspend fun saveAnnotatedPdf(
    context: Context,
    outputUri: Uri,
    viewModel: PdfViewerViewModel,
    totalPages: Int,
    annotations: List<AnnotationStroke>
): Boolean = withContext(Dispatchers.IO) {
    try {
        if (!PDFBoxResourceLoader.isReady()) {
            PDFBoxResourceLoader.init(context.applicationContext)
        }
        
        val outputStream = context.contentResolver.openOutputStream(outputUri)
            ?: throw IOException("Could not open output stream")
        
        // Create new PDF document
        val document = PDDocument()
        
        try {
            for (pageIndex in 0 until totalPages) {
                // Fetch page from ViewModel (forces render if not cached)
                val originalBitmap = viewModel.loadPage(pageIndex) ?: continue

                val pageAnnotations = annotations.filter { it.pageIndex == pageIndex }
                
                // Create a mutable copy of the bitmap to draw annotations on
                val annotatedBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
                val canvas = android.graphics.Canvas(annotatedBitmap)
                
                // Draw annotations on the bitmap
                for (annotation in pageAnnotations) {
                    if (annotation.points.size >= 2) {
                        val paint = android.graphics.Paint().apply {
                            color = android.graphics.Color.argb(
                                (annotation.color.alpha * 255).toInt(),
                                (annotation.color.red * 255).toInt(),
                                (annotation.color.green * 255).toInt(),
                                (annotation.color.blue * 255).toInt()
                            )
                            strokeWidth = annotation.strokeWidth
                            style = android.graphics.Paint.Style.STROKE
                            strokeCap = android.graphics.Paint.Cap.ROUND
                            strokeJoin = android.graphics.Paint.Join.ROUND
                            isAntiAlias = true
                        }
                        
                        val path = android.graphics.Path()
                        path.moveTo(annotation.points[0].x, annotation.points[0].y)
                        for (i in 1 until annotation.points.size) {
                            path.lineTo(annotation.points[i].x, annotation.points[i].y)
                        }
                        canvas.drawPath(path, paint)
                    }
                }
                
                // Create PDF page with the annotated bitmap
                val width = annotatedBitmap.width.toFloat()
                val height = annotatedBitmap.height.toFloat()
                
                // Scale to reasonable PDF page size (72 DPI equivalent)
                val scaleFactor = 72f / 150f // Original render was at 150 DPI
                val pageWidth = width * scaleFactor
                val pageHeight = height * scaleFactor
                
                val page = PDPage(com.tom_roush.pdfbox.pdmodel.common.PDRectangle(pageWidth, pageHeight))
                document.addPage(page)
                
                // Create image from bitmap
                val pdImage = LosslessFactory.createFromImage(document, annotatedBitmap)
                
                // Draw image on page
                PDPageContentStream(document, page).use { contentStream ->
                    contentStream.drawImage(pdImage, 0f, 0f, pageWidth, pageHeight)
                }
                
                // Recycle the annotated bitmap (not the original!)
                annotatedBitmap.recycle()
            }
            
            // Save the document
            document.save(outputStream)
            
            true
        } finally {
            document.close()
            outputStream.close()
        }
    } catch (e: Exception) {
        Log.e("PdfViewerScreen", "Error saving annotated PDF: ${e.message}", e)
        false
    }
}

private fun openWithExternalApp(context: Context, pdfUri: Uri) {
    try {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(pdfUri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "Open with")
        context.startActivity(chooser)
    } catch (e: Exception) {
        Toast.makeText(context, "No app found to open PDF", Toast.LENGTH_SHORT).show()
    }
}

/**
 * Copy a URI to the app's cache directory.
 * Used as a fallback when direct access to the URI fails due to permission issues.
 * 
 * NOTE: This should only be called if the URI is NOT already our app's FileProvider URI.
 */
private fun copyUriToCache(context: Context, uri: Uri): java.io.File? {
    // Don't try to copy if it's already our app's FileProvider URI
    val isOurFileProvider = uri.scheme == "content" && 
        (uri.authority == "${context.packageName}.provider" ||
         uri.authority?.startsWith("com.yourname.pdftoolkit") == true && 
         uri.authority?.endsWith(".provider") == true)
    
    if (isOurFileProvider) {
        Log.d("PdfViewerScreen", "Skipping copy for our FileProvider URI: $uri")
        return null
    }
    
    return try {
        val cacheDir = java.io.File(context.cacheDir, "pdf_viewer_cache")
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
        
        // Clean old cached files (older than 1 hour)
        val oneHourAgo = System.currentTimeMillis() - (60 * 60 * 1000)
        cacheDir.listFiles()?.forEach { file ->
            if (file.lastModified() < oneHourAgo) {
                file.delete()
            }
        }
        
        val cachedFile = java.io.File(cacheDir, "viewer_${System.currentTimeMillis()}.pdf")
        
        Log.d("PdfViewerScreen", "Copying URI to cache: $uri -> ${cachedFile.absolutePath}")
        context.contentResolver.openInputStream(uri)?.use { input ->
            cachedFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        
        if (cachedFile.exists() && cachedFile.length() > 0) {
            Log.d("PdfViewerScreen", "Successfully copied to cache, size: ${cachedFile.length()}")
            cachedFile
        } else {
            Log.w("PdfViewerScreen", "Copy failed - file empty or doesn't exist")
            null
        }
    } catch (e: SecurityException) {
        // Permission denied - can't copy
        Log.e("PdfViewerScreen", "SecurityException copying to cache: ${e.message}")
        null
    } catch (e: Exception) {
        Log.e("PdfViewerScreen", "Exception copying to cache: ${e.message}")
        null
    }
}

/**
 * Extract text from PDF for search functionality.
 */
private suspend fun extractPdfText(
    context: Context,
    pdfUri: Uri,
    totalPages: Int
): Map<Int, String> = withContext(Dispatchers.IO) {
    val textMap = mutableMapOf<Int, String>()
    
    // Only extract first 20 pages to avoid performance issues
    val maxPages = totalPages.coerceAtMost(20)
    
    var document: PDDocument? = null
    try {
        var inputStream: java.io.InputStream? = null
             
        val isOurFileProvider = pdfUri.scheme == "content" && 
            (pdfUri.authority == "${context.packageName}.provider" ||
             pdfUri.authority?.startsWith("com.yourname.pdftoolkit") == true && 
             pdfUri.authority?.endsWith(".provider") == true)
        
        if (isOurFileProvider) {
            val pathSegments = pdfUri.pathSegments
            val fileName = pathSegments.lastOrNull()
            if (fileName != null) {
                val cacheDir = java.io.File(context.cacheDir, "shared_files")
                val file = java.io.File(cacheDir, fileName)
                if (file.exists() && file.canRead()) {
                    inputStream = file.inputStream()
                }
            }
            
            if (inputStream == null) {
                try {
                    inputStream = context.contentResolver.openInputStream(pdfUri)
                } catch (e: Exception) {
                    // Ignore
                }
            }
        } else {
             inputStream = try {
                context.contentResolver.openInputStream(pdfUri)
            } catch (e: Exception) {
                null
            }
            
            if (inputStream == null) {
                val cachedFile = copyUriToCache(context, pdfUri)
                if (cachedFile != null) {
                    inputStream = cachedFile.inputStream()
                }
            }
        }
        
        if (inputStream != null) {
            document = PDDocument.load(inputStream)
            inputStream.close()
            
            val stripper = PDFTextStripper()
            
            for (i in 1..maxPages) {
                stripper.startPage = i
                stripper.endPage = i
                val text = stripper.getText(document)
                // Map is 0-indexed for list scrolling matches
                textMap[i - 1] = text
            }
        }
    } catch (e: Exception) {
        Log.e("PdfViewerScreen", "Error extracting text: ${e.message}")
    } finally {
        document?.close()
    }
    
    textMap
}

/**
 * Get the bounding boxes for search highlights on a specific page.
 * Returns a list of matches, where each match is a list of RectFs (handling line breaks).
 */
private suspend fun getSearchHighlights(
    context: Context,
    pdfUri: Uri,
    pageIndex: Int,
    query: String
): List<List<RectF>> = withContext(Dispatchers.IO) {
    if (query.length < 2) return@withContext emptyList()
    
    val allMatches = mutableListOf<List<RectF>>()
    var document: PDDocument? = null
    
    try {
        var inputStream: java.io.InputStream? = null
             
        val isOurFileProvider = pdfUri.scheme == "content" && 
            (pdfUri.authority == "${context.packageName}.provider" ||
             pdfUri.authority?.startsWith("com.yourname.pdftoolkit") == true && 
             pdfUri.authority?.endsWith(".provider") == true)
        
        if (isOurFileProvider) {
            val pathSegments = pdfUri.pathSegments
            val fileName = pathSegments.lastOrNull()
            if (fileName != null) {
                val cacheDir = java.io.File(context.cacheDir, "shared_files")
                val file = java.io.File(cacheDir, fileName)
                if (file.exists() && file.canRead()) {
                    inputStream = file.inputStream()
                }
            }
            if (inputStream == null) {
                try {
                    inputStream = context.contentResolver.openInputStream(pdfUri)
                } catch (e: Exception) {}
            }
        } else {
             inputStream = try {
                context.contentResolver.openInputStream(pdfUri)
            } catch (e: Exception) { null }
            
            if (inputStream == null) {
                val cachedFile = copyUriToCache(context, pdfUri)
                if (cachedFile != null) {
                    inputStream = cachedFile.inputStream()
                }
            }
        }
        
        if (inputStream != null) {
            document = PDDocument.load(inputStream)
            inputStream.close()
            
            val textPositions = mutableListOf<TextPosition>()
            
            val stripper = object : PDFTextStripper() {
                override fun processTextPosition(text: TextPosition) {
                    super.processTextPosition(text)
                    textPositions.add(text)
                }
            }
            
            stripper.sortByPosition = true
            stripper.startPage = pageIndex + 1
            stripper.endPage = pageIndex + 1
            
            // This populates textPositions
            val textContent = stripper.getText(document).lowercase()
            val lowerQuery = query.lowercase()
            
            val sb = StringBuilder()
            textPositions.forEach { sb.append(it.unicode) }
            val rawText = sb.toString().lowercase()
            
            var pos = 0
            while (true) {
                val found = rawText.indexOf(lowerQuery, pos)
                if (found == -1) break
                
                val matchRects = mutableListOf<RectF>()
                
                // Construct rect for this match
                for (i in found until (found + lowerQuery.length)) {
                    if (i < textPositions.size) {
                        val tp = textPositions[i]
                        val scale = 150f / 72f
                        
                        val x = tp.xDirAdj * scale
                        val y = tp.yDirAdj * scale
                        val w = tp.widthDirAdj * scale
                        val h = tp.heightDir * scale
                        
                        matchRects.add(RectF(x, y, x + w, y + h))
                    }
                }
                
                if (matchRects.isNotEmpty()) {
                    allMatches.add(matchRects)
                }
                
                pos = found + 1
            }
        }
    } catch (e: Exception) {
        Log.e("PdfViewerScreen", "Error getting highlights: ${e.message}")
    } finally {
        document?.close()
    }
    
    allMatches
}
