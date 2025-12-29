package com.yourname.pdftoolkit.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yourname.pdftoolkit.ui.navigation.Screen

/**
 * Category tabs for organizing PDF tools.
 */
enum class ToolCategory(val title: String, val icon: ImageVector) {
    ORGANIZE("Organize", Icons.Default.Folder),
    CONVERT("Convert", Icons.Default.Transform),
    MARKUP("Markup", Icons.Default.Draw),
    SECURITY("Security", Icons.Default.Lock),
    OPTIMIZE("Optimize", Icons.Default.Speed)
}

// Supported file types
private val pdfMimeTypes = arrayOf("application/pdf")
private val officeMimeTypes = arrayOf(
    "application/pdf",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document", // docx
    "application/msword", // doc
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", // xlsx
    "application/vnd.ms-excel", // xls
    "application/vnd.openxmlformats-officedocument.presentationml.presentation", // pptx
    "application/vnd.ms-powerpoint" // ppt
)

/**
 * Home screen displaying all available PDF tools organized by category.
 * Uses tabs for category navigation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToFeature: (Screen) -> Unit,
    onNavigateToSettings: () -> Unit = {},
    onOpenPdfViewer: (Uri, String) -> Unit = { _, _ -> },
    onOpenDocumentViewer: (Uri, String) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    var selectedCategory by remember { mutableStateOf(ToolCategory.ORGANIZE) }
    
    // PDF file picker
    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        // uri is null when user cancels - just do nothing
        uri?.let {
            val cursor = context.contentResolver.query(it, null, null, null, null)
            var name = "PDF Document"
            cursor?.use { c ->
                if (c.moveToFirst()) {
                    val nameIndex = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0) {
                        name = c.getString(nameIndex)?.substringBeforeLast('.') ?: name
                    }
                }
            }
            onOpenPdfViewer(it, name)
        }
    }
    
    // Document file picker (All supported formats)
    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        // uri is null when user cancels - just do nothing (handles cancel issue)
        uri?.let {
            val mimeType = context.contentResolver.getType(it)
            val cursor = context.contentResolver.query(it, null, null, null, null)
            var name = "Document"
            cursor?.use { c ->
                if (c.moveToFirst()) {
                    val nameIndex = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0) {
                        name = c.getString(nameIndex)?.substringBeforeLast('.') ?: name
                    }
                }
            }
            
            // Route to appropriate viewer based on MIME type
            if (mimeType == "application/pdf") {
                onOpenPdfViewer(it, name)
            } else {
                onOpenDocumentViewer(it, name)
            }
        }
    }
    
    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = "PDF Toolkit",
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    // Open Document button (all formats)
                    IconButton(
                        onClick = {
                            documentPickerLauncher.launch(officeMimeTypes)
                        }
                    ) {
                        Icon(
                            Icons.Default.FolderOpen,
                            contentDescription = "Open Document"
                        )
                    }
                    // Settings button
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Settings"
                        )
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    documentPickerLauncher.launch(officeMimeTypes)
                },
                icon = { Icon(Icons.Default.FileOpen, contentDescription = null) },
                text = { Text("Open Document") },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Category tabs
            ScrollableTabRow(
                selectedTabIndex = ToolCategory.entries.indexOf(selectedCategory),
                edgePadding = 16.dp,
                containerColor = MaterialTheme.colorScheme.surface,
                divider = {}
            ) {
                ToolCategory.entries.forEach { category ->
                    Tab(
                        selected = selectedCategory == category,
                        onClick = { selectedCategory = category },
                        text = { Text(category.title) },
                        icon = {
                            Icon(
                                imageVector = category.icon,
                                contentDescription = category.title,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    )
                }
            }
            
            // Content
            val features = getFeaturesByCategory(selectedCategory)
            
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 16.dp, horizontal = 0.dp)
            ) {
                itemsIndexed(
                    items = features,
                    key = { _, feature -> feature.title }
                ) { index, feature ->
                    var isVisible by remember { mutableStateOf(false) }
                    
                    LaunchedEffect(selectedCategory) {
                        isVisible = false
                        kotlinx.coroutines.delay(index * 50L)
                        isVisible = true
                    }
                    
                    val scale by animateFloatAsState(
                        targetValue = if (isVisible) 1f else 0.8f,
                        animationSpec = tween(durationMillis = 300),
                        label = "card_scale"
                    )
                    
                    FeatureCard(
                        feature = feature,
                        onClick = {
                            val screen = Screen.fromFeatureTitle(feature.title)
                            onNavigateToFeature(screen)
                        },
                        modifier = Modifier.scale(scale)
                    )
                }
                
                // Bottom spacing for FAB
                item {
                    Spacer(modifier = Modifier.height(72.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeatureCard(
    feature: PdfFeature,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = feature.icon,
                    contentDescription = feature.title,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier
                        .padding(12.dp)
                        .size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = feature.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = feature.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Navigate",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Data class representing a PDF tool feature.
 */
data class PdfFeature(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val category: ToolCategory
)

/**
 * Get features filtered by category.
 */
private fun getFeaturesByCategory(category: ToolCategory): List<PdfFeature> {
    return pdfFeatures.filter { it.category == category }
}

/**
 * List of all available PDF tools organized by category.
 */
val pdfFeatures = listOf(
    // ORGANIZE category
    PdfFeature(
        title = "Merge PDFs",
        description = "Combine multiple PDF files into one",
        icon = Icons.Default.MergeType,
        category = ToolCategory.ORGANIZE
    ),
    PdfFeature(
        title = "Split PDF",
        description = "Split a PDF into multiple files",
        icon = Icons.Default.CallSplit,
        category = ToolCategory.ORGANIZE
    ),
    PdfFeature(
        title = "Organize Pages",
        description = "Remove or reorder PDF pages",
        icon = Icons.Default.SwapVert,
        category = ToolCategory.ORGANIZE
    ),
    PdfFeature(
        title = "Rotate Pages",
        description = "Rotate PDF pages",
        icon = Icons.Default.RotateRight,
        category = ToolCategory.ORGANIZE
    ),
    PdfFeature(
        title = "Extract Pages",
        description = "Extract specific pages from PDF",
        icon = Icons.Default.ContentCopy,
        category = ToolCategory.ORGANIZE
    ),
    
    // CONVERT category
    PdfFeature(
        title = "Images to PDF",
        description = "Convert images to PDF",
        icon = Icons.Default.Image,
        category = ToolCategory.CONVERT
    ),
    PdfFeature(
        title = "PDF to Images",
        description = "Convert PDF pages to images",
        icon = Icons.Default.PhotoLibrary,
        category = ToolCategory.CONVERT
    ),
    PdfFeature(
        title = "HTML to PDF",
        description = "Convert webpage or HTML to PDF",
        icon = Icons.Default.Language,
        category = ToolCategory.CONVERT
    ),
    PdfFeature(
        title = "Extract Text",
        description = "Extract text content to TXT file",
        icon = Icons.Default.TextFields,
        category = ToolCategory.CONVERT
    ),
    PdfFeature(
        title = "Scan to PDF",
        description = "Scan documents with camera",
        icon = Icons.Default.CameraAlt,
        category = ToolCategory.CONVERT
    ),
    PdfFeature(
        title = "OCR",
        description = "Make scanned PDFs searchable",
        icon = Icons.Default.DocumentScanner,
        category = ToolCategory.CONVERT
    ),
    
    // MARKUP category (Sign, Annotate, Fill Forms)
    PdfFeature(
        title = "Sign PDF",
        description = "Add your signature to PDF",
        icon = Icons.Default.Draw,
        category = ToolCategory.MARKUP
    ),
    PdfFeature(
        title = "Fill Forms",
        description = "Fill PDF form fields",
        icon = Icons.Default.EditNote,
        category = ToolCategory.MARKUP
    ),
    PdfFeature(
        title = "Annotate PDF",
        description = "Add highlights, notes, stamps",
        icon = Icons.Default.Edit,
        category = ToolCategory.MARKUP
    ),
    
    // SECURITY category (focused on Lock/Unlock)
    PdfFeature(
        title = "Add Security",
        description = "Password protect your PDFs",
        icon = Icons.Default.Lock,
        category = ToolCategory.SECURITY
    ),
    PdfFeature(
        title = "Unlock PDF",
        description = "Remove password from PDFs",
        icon = Icons.Default.LockOpen,
        category = ToolCategory.SECURITY
    ),
    
    // OPTIMIZE category (includes compression, repair, metadata, watermark, flatten)
    PdfFeature(
        title = "Compress PDF",
        description = "Reduce PDF file size",
        icon = Icons.Default.Compress,
        category = ToolCategory.OPTIMIZE
    ),
    PdfFeature(
        title = "Repair PDF",
        description = "Fix corrupted PDF files",
        icon = Icons.Default.Build,
        category = ToolCategory.OPTIMIZE
    ),
    PdfFeature(
        title = "Page Numbers",
        description = "Add page numbers to your PDF",
        icon = Icons.Default.FormatListNumbered,
        category = ToolCategory.OPTIMIZE
    ),
    PdfFeature(
        title = "View Metadata",
        description = "View and edit PDF properties",
        icon = Icons.Default.Info,
        category = ToolCategory.OPTIMIZE
    ),
    PdfFeature(
        title = "Add Watermark",
        description = "Add text or image watermark",
        icon = Icons.Default.WaterDrop,
        category = ToolCategory.OPTIMIZE
    ),
    PdfFeature(
        title = "Flatten PDF",
        description = "Merge annotations to content",
        icon = Icons.Default.Layers,
        category = ToolCategory.OPTIMIZE
    )
)
