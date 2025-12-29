package com.yourname.pdftoolkit.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
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
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.rendering.PDFRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Annotation tools available in the PDF viewer.
 */
enum class AnnotationTool(val displayName: String) {
    NONE("Select"),
    HIGHLIGHTER("Highlighter"),
    MARKER("Marker"),
    UNDERLINE("Underline")
}

/**
 * Represents a single annotation stroke.
 */
data class AnnotationStroke(
    val pageIndex: Int,
    val tool: AnnotationTool,
    val color: Color,
    val points: List<Offset>,
    val strokeWidth: Float
)

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
    onNavigateToTool: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var totalPages by remember { mutableIntStateOf(0) }
    var currentPage by remember { mutableIntStateOf(1) }
    var pageImages by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var scale by remember { mutableFloatStateOf(1f) }
    var showControls by remember { mutableStateOf(true) }
    var showPageSelector by remember { mutableStateOf(false) }
    
    // Annotation state
    var isEditMode by remember { mutableStateOf(false) }
    var selectedTool by remember { mutableStateOf(AnnotationTool.NONE) }
    var selectedColor by remember { mutableStateOf(Color.Yellow.copy(alpha = 0.5f)) }
    var annotations by remember { mutableStateOf<List<AnnotationStroke>>(emptyList()) }
    var currentStroke by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var showColorPicker by remember { mutableStateOf(false) }
    
    val listState = rememberLazyListState()
    
    // Track visible page based on scroll position
    LaunchedEffect(listState.firstVisibleItemIndex) {
        currentPage = listState.firstVisibleItemIndex + 1
    }
    
    // Load PDF when screen opens
    LaunchedEffect(pdfUri) {
        if (pdfUri == null) {
            errorMessage = "No PDF file provided"
            isLoading = false
            return@LaunchedEffect
        }
        
        isLoading = true
        errorMessage = null
        
        scope.launch {
            try {
                val (pages, images) = loadPdfPages(context, pdfUri)
                totalPages = pages
                pageImages = images
                isLoading = false
            } catch (e: Exception) {
                errorMessage = "Failed to load PDF: ${e.localizedMessage}"
                isLoading = false
            }
        }
    }
    
    DisposableEffect(Unit) {
        onDispose {
            pageImages.forEach { it.recycle() }
        }
    }
    
    Scaffold(
        topBar = {
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically()
            ) {
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
                        // Edit/Annotate toggle
                        IconButton(
                            onClick = { 
                                isEditMode = !isEditMode
                                if (isEditMode) {
                                    selectedTool = AnnotationTool.HIGHLIGHTER
                                } else {
                                    selectedTool = AnnotationTool.NONE
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
                                }
                            )
                            if (annotations.isNotEmpty()) {
                                Divider()
                                DropdownMenuItem(
                                    text = { Text("Clear All Annotations") },
                                    leadingIcon = { Icon(Icons.Default.ClearAll, null) },
                                    onClick = {
                                        showMenu = false
                                        annotations = emptyList()
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
                                    onNavigateToTool?.invoke("compress")
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Add Watermark") },
                                leadingIcon = { Icon(Icons.Default.WaterDrop, null) },
                                onClick = {
                                    showMenu = false
                                    onNavigateToTool?.invoke("watermark")
                                }
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                    )
                )
            }
        },
        bottomBar = {
            Column {
                // Annotation toolbar
                AnimatedVisibility(
                    visible = isEditMode && showControls,
                    enter = fadeIn() + slideInVertically { it },
                    exit = fadeOut() + slideOutVertically { it }
                ) {
                    AnnotationToolbar(
                        selectedTool = selectedTool,
                        selectedColor = selectedColor,
                        onToolSelected = { selectedTool = it },
                        onColorPickerClick = { showColorPicker = true },
                        onUndoClick = {
                            if (annotations.isNotEmpty()) {
                                annotations = annotations.dropLast(1)
                            }
                        },
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
                    enabled = !isEditMode
                ) {
                    showControls = !showControls
                }
        ) {
            when {
                isLoading -> {
                    LoadingState()
                }
                
                errorMessage != null -> {
                    ErrorState(
                        message = errorMessage!!,
                        onGoBack = onNavigateBack
                    )
                }
                
                pageImages.isNotEmpty() -> {
                    PdfPagesContent(
                        pageImages = pageImages,
                        scale = scale,
                        onScaleChange = { scale = it },
                        listState = listState,
                        isEditMode = isEditMode,
                        selectedTool = selectedTool,
                        selectedColor = selectedColor,
                        annotations = annotations,
                        currentStroke = currentStroke,
                        onCurrentStrokeChange = { currentStroke = it },
                        onAddAnnotation = { stroke ->
                            annotations = annotations + stroke
                            currentStroke = emptyList()
                        }
                    )
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
                selectedColor = it
                showColorPicker = false
            },
            onDismiss = { showColorPicker = false }
        )
    }
}

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
    pageImages: List<Bitmap>,
    scale: Float,
    onScaleChange: (Float) -> Unit,
    listState: LazyListState,
    isEditMode: Boolean,
    selectedTool: AnnotationTool,
    selectedColor: Color,
    annotations: List<AnnotationStroke>,
    currentStroke: List<Offset>,
    onCurrentStrokeChange: (List<Offset>) -> Unit,
    onAddAnnotation: (AnnotationStroke) -> Unit
) {
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (!isEditMode) {
                    Modifier.pointerInput(Unit) {
                        detectTransformGestures { _, _, zoom, _ ->
                            val newScale = (scale * zoom).coerceIn(0.5f, 3f)
                            onScaleChange(newScale)
                        }
                    }
                } else Modifier
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        items(pageImages.size) { index ->
            PdfPageWithAnnotations(
                bitmap = pageImages[index],
                pageIndex = index,
                scale = scale,
                isEditMode = isEditMode,
                selectedTool = selectedTool,
                selectedColor = selectedColor,
                annotations = annotations.filter { it.pageIndex == index },
                currentStroke = if (listState.firstVisibleItemIndex == index) currentStroke else emptyList(),
                onCurrentStrokeChange = onCurrentStrokeChange,
                onAddAnnotation = onAddAnnotation
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
    bitmap: Bitmap,
    pageIndex: Int,
    scale: Float,
    isEditMode: Boolean,
    selectedTool: AnnotationTool,
    selectedColor: Color,
    annotations: List<AnnotationStroke>,
    currentStroke: List<Offset>,
    onCurrentStrokeChange: (List<Offset>) -> Unit,
    onAddAnnotation: (AnnotationStroke) -> Unit
) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        shape = MaterialTheme.shapes.small,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { size = it }
        ) {
            // PDF page image
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Page ${pageIndex + 1}",
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small),
                contentScale = ContentScale.FillWidth
            )
            
            // Annotation overlay
            if (size != IntSize.Zero) {
                Canvas(
                    modifier = Modifier
                        .matchParentSize()
                        .then(
                            if (isEditMode && selectedTool != AnnotationTool.NONE) {
                                Modifier.pointerInput(selectedTool, selectedColor) {
                                    detectDragGestures(
                                        onDragStart = { offset ->
                                            onCurrentStrokeChange(listOf(offset))
                                        },
                                        onDrag = { change, _ ->
                                            onCurrentStrokeChange(currentStroke + change.position)
                                        },
                                        onDragEnd = {
                                            if (currentStroke.size > 1) {
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
                                                        points = currentStroke,
                                                        strokeWidth = strokeWidth
                                                    )
                                                )
                                            }
                                        }
                                    )
                                }
                            } else Modifier
                        )
                ) {
                    // Draw saved annotations
                    annotations.forEach { stroke ->
                        if (stroke.points.size > 1) {
                            val path = androidx.compose.ui.graphics.Path().apply {
                                moveTo(stroke.points.first().x, stroke.points.first().y)
                                stroke.points.drop(1).forEach { point ->
                                    lineTo(point.x, point.y)
                                }
                            }
                            drawPath(
                                path = path,
                                color = stroke.color,
                                style = androidx.compose.ui.graphics.drawscope.Stroke(
                                    width = stroke.strokeWidth,
                                    cap = StrokeCap.Round
                                )
                            )
                        }
                    }
                    
                    // Draw current stroke being drawn
                    if (currentStroke.size > 1) {
                        val path = androidx.compose.ui.graphics.Path().apply {
                            moveTo(currentStroke.first().x, currentStroke.first().y)
                            currentStroke.drop(1).forEach { point ->
                                lineTo(point.x, point.y)
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
    var inputPage by remember { mutableStateOf(currentPage.toString()) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Go to Page") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("Enter a page number (1 - $totalPages)")
                OutlinedTextField(
                    value = inputPage,
                    onValueChange = { inputPage = it.filter { char -> char.isDigit() } },
                    label = { Text("Page Number") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val page = inputPage.toIntOrNull()?.coerceIn(1, totalPages) ?: currentPage
                    onPageSelected(page)
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

// Helper functions
private fun sharePdf(context: Context, pdfUri: Uri) {
    try {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, pdfUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share PDF"))
    } catch (e: Exception) {
        Toast.makeText(context, "Unable to share PDF: ${e.message}", Toast.LENGTH_SHORT).show()
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
 * Load PDF pages as bitmaps for display.
 */
private suspend fun loadPdfPages(
    context: Context,
    pdfUri: Uri
): Pair<Int, List<Bitmap>> = withContext(Dispatchers.IO) {
    if (!PDFBoxResourceLoader.isReady()) {
        PDFBoxResourceLoader.init(context.applicationContext)
    }
    
    var document: PDDocument? = null
    try {
        val inputStream = context.contentResolver.openInputStream(pdfUri)
            ?: throw IOException("Cannot open PDF file")
        
        document = PDDocument.load(inputStream)
        val totalPages = document.numberOfPages
        val renderer = PDFRenderer(document)
        
        val dpi = 150f
        val images = mutableListOf<Bitmap>()
        
        for (i in 0 until totalPages.coerceAtMost(50)) {
            // PdfBox-Android renderImage takes scale factor, not DPI
            val bitmap = renderer.renderImage(i, dpi / 72f)
            images.add(bitmap)
        }
        
        Pair(totalPages, images)
    } finally {
        document?.close()
    }
}
