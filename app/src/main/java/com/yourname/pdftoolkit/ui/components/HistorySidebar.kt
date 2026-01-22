package com.yourname.pdftoolkit.ui.components

import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.yourname.pdftoolkit.data.HistoryEntry
import com.yourname.pdftoolkit.data.HistoryManager
import com.yourname.pdftoolkit.data.OperationStatus
import com.yourname.pdftoolkit.data.OperationType
import com.yourname.pdftoolkit.util.FileOpener
import kotlinx.coroutines.launch

/**
 * Get icon for operation type.
 */
@Composable
fun getOperationIcon(operationType: OperationType): ImageVector {
    return when (operationType) {
        OperationType.MERGE -> Icons.Default.MergeType
        OperationType.SPLIT -> Icons.Default.CallSplit
        OperationType.COMPRESS -> Icons.Default.Compress
        OperationType.CONVERT -> Icons.Default.Transform
        OperationType.PDF_TO_IMAGE -> Icons.Default.Image
        OperationType.EXTRACT -> Icons.Default.ContentCut
        OperationType.ROTATE -> Icons.Default.RotateRight
        OperationType.ADD_PASSWORD -> Icons.Default.Lock
        OperationType.REMOVE_PASSWORD -> Icons.Default.LockOpen
        OperationType.METADATA -> Icons.Default.Info
        OperationType.PAGE_NUMBER -> Icons.Default.FormatListNumbered
        OperationType.ORGANIZE -> Icons.Default.Reorder
        OperationType.UNLOCK -> Icons.Default.LockOpen
        OperationType.REPAIR -> Icons.Default.Build
        OperationType.HTML_TO_PDF -> Icons.Default.Code
        OperationType.EXTRACT_TEXT -> Icons.Default.TextSnippet
        OperationType.WATERMARK -> Icons.Default.WaterDrop
        OperationType.FLATTEN -> Icons.Default.LayersClear
        OperationType.SIGN -> Icons.Default.Draw
        OperationType.FILL_FORMS -> Icons.Default.EditNote
        OperationType.ANNOTATE -> Icons.Default.Edit
        OperationType.SCAN_TO_PDF -> Icons.Default.DocumentScanner
        OperationType.OCR -> Icons.Default.TextFields
        OperationType.IMAGE_TOOLS -> Icons.Default.Photo
        OperationType.OPEN_PDF -> Icons.Default.PictureAsPdf
        OperationType.OTHER -> Icons.Default.MoreHoriz
    }
}

/**
 * Hamburger menu button to toggle history sidebar.
 */
@Composable
fun HistoryMenuButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
    ) {
        Icon(
            imageVector = Icons.Default.Menu,
            contentDescription = "Open History",
            tint = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * History sidebar component.
 * Can be minimized/expanded with an animation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistorySidebar(
    isOpen: Boolean,
    onClose: () -> Unit,
    onOpenFile: (Uri) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var history by remember { mutableStateOf<List<HistoryEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showClearConfirmation by remember { mutableStateOf(false) }
    
    // Load history when sidebar opens
    LaunchedEffect(isOpen) {
        if (isOpen) {
            isLoading = true
            history = HistoryManager.getHistory(context)
            isLoading = false
        }
    }
    
    // Animated visibility for the sidebar
    AnimatedVisibility(
        visible = isOpen,
        enter = slideInHorizontally(
            initialOffsetX = { -it },
            animationSpec = tween(300)
        ) + fadeIn(animationSpec = tween(300)),
        exit = slideOutHorizontally(
            targetOffsetX = { -it },
            animationSpec = tween(300)
        ) + fadeOut(animationSpec = tween(300)),
        modifier = modifier.zIndex(10f)
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            // Sidebar content
            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(300.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Header
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "History",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            
                            IconButton(onClick = onClose) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                    
                    // Actions bar
                    if (history.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = { showClearConfirmation = true },
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Icon(
                                    Icons.Default.DeleteSweep,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Clear All")
                            }
                        }
                    }
                    
                    // Content
                    when {
                        isLoading -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                        
                        history.isEmpty() -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.HistoryToggleOff,
                                        contentDescription = null,
                                        modifier = Modifier.size(48.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = "No history yet",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "Your operations will appear here",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                        
                        else -> {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(history, key = { it.id }) { entry ->
                                    HistoryItem(
                                        entry = entry,
                                        onOpenFile = { uri ->
                                            if (entry.outputFileName?.endsWith(".pdf", ignoreCase = true) == true) {
                                                onOpenFile(uri)
                                            } else {
                                                // For non-PDF files, use system open
                                                FileOpener.openWithSystemPicker(context, uri)
                                            }
                                        },
                                        onDelete = {
                                            scope.launch {
                                                HistoryManager.deleteEntry(context, entry.id)
                                                history = HistoryManager.getHistory(context)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
            
            // Click-away area to close sidebar
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable { onClose() }
            )
        }
    }
    
    // Clear confirmation dialog
    if (showClearConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearConfirmation = false },
            icon = {
                Icon(
                    Icons.Default.DeleteForever,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("Clear History?") },
            text = { Text("This will remove all history entries. This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            HistoryManager.clearHistory(context)
                            history = emptyList()
                            showClearConfirmation = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun HistoryItem(
    entry: HistoryEntry,
    onOpenFile: (Uri) -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (entry.status) {
                OperationStatus.SUCCESS -> MaterialTheme.colorScheme.surfaceVariant
                OperationStatus.FAILED -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                OperationStatus.CANCELLED -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Icon
                Surface(
                    shape = CircleShape,
                    color = when (entry.status) {
                        OperationStatus.SUCCESS -> MaterialTheme.colorScheme.primaryContainer
                        OperationStatus.FAILED -> MaterialTheme.colorScheme.errorContainer
                        OperationStatus.CANCELLED -> MaterialTheme.colorScheme.surfaceVariant
                    },
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            imageVector = getOperationIcon(entry.operationType),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = when (entry.status) {
                                OperationStatus.SUCCESS -> MaterialTheme.colorScheme.onPrimaryContainer
                                OperationStatus.FAILED -> MaterialTheme.colorScheme.onErrorContainer
                                OperationStatus.CANCELLED -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.operationName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    if (entry.outputFileName != null) {
                        Text(
                            text = entry.outputFileName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else if (entry.inputFileName != null) {
                        Text(
                            text = entry.inputFileName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    
                    Text(
                        text = entry.relativeTime,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
                
                // Menu button
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "More options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        if (entry.outputFileUri != null && entry.status == OperationStatus.SUCCESS) {
                            DropdownMenuItem(
                                text = { Text("Open File") },
                                leadingIcon = { Icon(Icons.Default.OpenInNew, null) },
                                onClick = {
                                    showMenu = false
                                    try {
                                        onOpenFile(Uri.parse(entry.outputFileUri))
                                    } catch (e: Exception) {
                                        // Handle invalid URI
                                    }
                                }
                            )
                        }
                        
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            leadingIcon = { 
                                Icon(
                                    Icons.Default.Delete, 
                                    null,
                                    tint = MaterialTheme.colorScheme.error
                                ) 
                            },
                            onClick = {
                                showMenu = false
                                onDelete()
                            }
                        )
                    }
                }
            }
            
            // Status badge for failed operations
            if (entry.status == OperationStatus.FAILED && entry.details != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = entry.details,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
