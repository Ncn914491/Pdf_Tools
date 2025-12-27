package com.yourname.pdftoolkit.ui.screens

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
import com.yourname.pdftoolkit.util.CacheManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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


/**
 * Home screen displaying all available PDF tools organized by category.
 * Uses tabs for category navigation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToFeature: (Screen) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedCategory by remember { mutableStateOf(ToolCategory.ORGANIZE) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var cacheSize by remember { mutableStateOf("Calculating...") }
    var isClearing by remember { mutableStateOf(false) }
    
    // Calculate cache size when settings dialog opens
    LaunchedEffect(showSettingsDialog) {
        if (showSettingsDialog) {
            withContext(Dispatchers.IO) {
                cacheSize = CacheManager.getFormattedCacheSize(context)
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
                    IconButton(onClick = { showSettingsDialog = true }) {
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
                contentPadding = PaddingValues(vertical = 16.dp)
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
            }
        }
    }
    
    // Settings Dialog
    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            icon = {
                Icon(Icons.Default.Settings, contentDescription = null)
            },
            title = { Text("Settings") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Cache info
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "Cache Size",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                cacheSize,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                isClearing = true
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        CacheManager.clearAllCache(context)
                                    }
                                    cacheSize = CacheManager.getFormattedCacheSize(context)
                                    isClearing = false
                                }
                            },
                            enabled = !isClearing
                        ) {
                            if (isClearing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("Clear")
                            }
                        }
                    }
                    
                    Divider()
                    
                    Text(
                        "PDF Toolkit v1.2.0",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showSettingsDialog = false }) {
                    Text("Close")
                }
            }
        )
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
