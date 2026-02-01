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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
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
    val searchState by viewModel.searchState.collectAsState()
    val saveState by viewModel.saveState.collectAsState()
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

    // Save document launcher
    val saveDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        uri?.let { outputUri ->
            viewModel.saveAnnotations(context, outputUri)
        }
    }
    
    val listState = rememberLazyListState()
    
    // Track visible page based on scroll position
    LaunchedEffect(listState.firstVisibleItemIndex) {
        currentPage = listState.firstVisibleItemIndex + 1
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
    
    // Handle Save State
    LaunchedEffect(saveState) {
        when (saveState) {
            is SaveState.Success -> {
                Toast.makeText(context, "Annotations saved successfully!", Toast.LENGTH_SHORT).show()
                viewModel.resetSaveState()
            }
            is SaveState.Error -> {
                Toast.makeText(context, (saveState as SaveState.Error).message, Toast.LENGTH_SHORT).show()
                viewModel.resetSaveState()
            }
            else -> {}
        }
    }

    // Navigate to search result
    LaunchedEffect(searchState.currentResultIndex, searchState.results) {
        if (searchState.results.isNotEmpty()) {
            val result = searchState.results.getOrNull(searchState.currentResultIndex)
            if (result != null) {
                listState.animateScrollToItem(result.first)
            }
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
                                value = searchState.query,
                                onValueChange = { viewModel.performSearch(it) },
                                placeholder = { Text("Search in PDF...") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color.Transparent,
                                    unfocusedBorderColor = Color.Transparent
                                ),
                                trailingIcon = {
                                    if (searchState.isSearching) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp
                                        )
                                    } else if (searchState.query.isNotEmpty()) {
                                        Text(
                                            text = if (searchState.results.isNotEmpty())
                                                "${searchState.currentResultIndex + 1}/${searchState.results.size}"
                                            else "No matches",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (searchState.results.isNotEmpty())
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
                                viewModel.clearSearch()
                            }) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Close search")
                            }
                        },
                        actions = {
                            // Navigate search results
                            if (searchState.results.isNotEmpty()) {
                                IconButton(onClick = { viewModel.previousSearchResult() }) {
                                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Previous")
                                }
                                IconButton(onClick = { viewModel.nextSearchResult() }) {
                                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Next")
                                }
                            }
                            if (searchState.query.isNotEmpty()) {
                                IconButton(onClick = { viewModel.clearSearch() }) {
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
                                if (saveState is SaveState.Saving) {
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
            val pageResults = if (toolState is PdfTool.Search) searchState.results.filter { it.first == index } else emptyList()
            val currentGlobalResult = searchState.results.getOrNull(searchState.currentResultIndex)
            val currentMatchIndexOnPage = if (toolState is PdfTool.Search && currentGlobalResult != null && currentGlobalResult.first == index) {
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
                // Pass viewModel to get highlights instead of local logic
                viewModel = viewModel,
                searchState = searchState,
                currentMatchIndexOnPage = currentMatchIndexOnPage
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
    viewModel: PdfViewerViewModel,
    searchState: SearchState,
    currentMatchIndexOnPage: Int
) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    
    // Load bitmap lazily
    val bitmap by produceState<Bitmap?>(initialValue = null, key1 = pageIndex) {
        value = loadPage(pageIndex)
    }

    // Asynchronously load search highlights
    val searchHighlights by produceState<List<List<RectF>>>(
        initialValue = emptyList(),
        key1 = searchState.isSearching,
        key2 = searchState.query,
        key3 = pageIndex
    ) {
        if (searchState.query.length >= 2) {
            value = viewModel.getSearchHighlights(pageIndex, searchState.query)
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
            if (searchState.query.isNotEmpty() && searchHighlights.isNotEmpty() && bitmap != null) {
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


@Composable
private fun PageSelectorDialog(
    currentPage: Int,
    totalPages: Int,
    onPageSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var pageInput by remember { mutableStateOf(currentPage.toString()) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Go to Page") },
        text = {
            Column {
                Text(
                    text = "Enter page number (1-$totalPages)",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = pageInput,
                    onValueChange = { pageInput = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val page = pageInput.toIntOrNull()
                    if (page != null && page in 1..totalPages) {
                        onPageSelected(page)
                    }
                }
            ) {
                Text("Go")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun PasswordDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    isError: Boolean = false
) {
    var password by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Password Required") },
        text = {
            Column {
                if (isError) {
                    Text(
                        text = "Incorrect password. Please try again.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Text(
                    text = "This PDF is password protected. Please enter the password to open it.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(password) },
                enabled = password.isNotEmpty()
            ) {
                Text("Open")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun sharePdf(context: Context, pdfUri: Uri) {
    try {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, pdfUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(shareIntent, "Share PDF")
        context.startActivity(chooser)
    } catch (e: Exception) {
        Toast.makeText(context, "Unable to share PDF", Toast.LENGTH_SHORT).show()
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
