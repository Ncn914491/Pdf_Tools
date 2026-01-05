package com.yourname.pdftoolkit.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yourname.pdftoolkit.data.PersistedFile
import com.yourname.pdftoolkit.data.SafUriManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * File type filter options.
 */
enum class FileFilter(val title: String, val icon: ImageVector) {
    ALL("All", Icons.Default.Folder),
    PDF("PDF", Icons.Default.PictureAsPdf)
}

/**
 * Files Screen - Content management tab.
 * Purpose: File access, NOT tools.
 * 
 * Features:
 * - Recent files list (PDF, Images)
 * - Open Document button using SAF (ACTION_OPEN_DOCUMENT)
 * - System file picker with persistent URI permissions
 * - Simple filters (PDF / Image)
 * 
 * IMPORTANT: This screen uses SafUriManager for proper SAF compliance.
 * All files are stored as URI strings, NOT file paths.
 * This ensures proper scoped storage compliance on Android 10+.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesScreen(
    onOpenPdfViewer: (Uri, String) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var selectedFilter by remember { mutableStateOf(FileFilter.ALL) }
    var recentFiles by remember { mutableStateOf<List<PersistedFile>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showClearHistoryDialog by remember { mutableStateOf(false) }
    
    // Supported MIME types for document picker (PDF only)
    val pdfMimeTypes = arrayOf("application/pdf")
    
    /**
     * Document picker using SAF (ACTION_OPEN_DOCUMENT).
     * ActivityResultContracts.OpenDocument() automatically uses ACTION_OPEN_DOCUMENT
     * with CATEGORY_OPENABLE, which is the correct way to open files on Android 10+.
     */
    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { selectedUri ->
            scope.launch {
                // Take persistable URI permission immediately
                // This is CRITICAL for reopening files later
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                val persistedFile = SafUriManager.addRecentFile(context, selectedUri, flags)
                
                if (persistedFile != null) {
                    // Update local list immediately
                    recentFiles = SafUriManager.loadRecentFiles(context)
                    
                    // Open PDF files
                    if (persistedFile.mimeType == "application/pdf") {
                        onOpenPdfViewer(selectedUri, persistedFile.name.substringBeforeLast('.'))
                    }
                } else {
                    // Fallback: try to open anyway, may fail if no permission
                    val mimeType = context.contentResolver.getType(selectedUri)
                    val name = getFileName(context, selectedUri)
                    
                    if (mimeType == "application/pdf") {
                        onOpenPdfViewer(selectedUri, name)
                    }
                }
            }
        }
    }
    
    // Load recent files from SafUriManager
    LaunchedEffect(Unit) {
        isLoading = true
        recentFiles = SafUriManager.loadRecentFiles(context)
        isLoading = false
    }
    
    // Filter files based on selection
    val filteredFiles = remember(recentFiles, selectedFilter) {
        when (selectedFilter) {
            FileFilter.ALL -> recentFiles
            FileFilter.PDF -> recentFiles.filter { it.mimeType == "application/pdf" }
        }
    }
    
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Subtitle
        Text(
            text = "Access your recent documents",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        
        // Open Document Button
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            onClick = {
                documentPickerLauncher.launch(pdfMimeTypes)
            }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.FileOpen,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Open PDF Document",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "Browse and open PDF files",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        
        // Filter tabs
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(FileFilter.entries) { filter ->
                FilterChip(
                    selected = selectedFilter == filter,
                    onClick = { selectedFilter = filter },
                    label = { Text(filter.title) },
                    leadingIcon = {
                        Icon(
                            imageVector = filter.icon,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Recent Files Section Header with Clear Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Recent Files",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            if (recentFiles.isNotEmpty()) {
                TextButton(
                    onClick = { showClearHistoryDialog = true }
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = "Clear History",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Clear")
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (filteredFiles.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No recent files",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Open a document to see it here",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredFiles, key = { it.uriString }) { file ->
                    RecentFileItem(
                        file = file,
                        onClick = {
                            scope.launch {
                                val uri = file.toUri()
                                if (uri != null) {
                                    // Update last accessed time
                                    SafUriManager.updateLastAccessed(context, file.uriString)
                                    
                                    // Open PDF files only
                                    val displayName = file.name.substringBeforeLast('.')
                                    if (file.mimeType == "application/pdf") {
                                        onOpenPdfViewer(uri, displayName)
                                    }
                                }
                            }
                        }
                    )
                }
                
                // Bottom spacing
                item {
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        }
    }
    
    // Clear History Dialog
    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            icon = { Icon(Icons.Default.DeleteSweep, contentDescription = null) },
            title = { Text("Clear Recent Files?") },
            text = {
                Text("This will remove all files from your recent history. The actual files will not be deleted.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            SafUriManager.clearAllRecentFiles(context)
                            recentFiles = emptyList()
                            showClearHistoryDialog = false
                        }
                    }
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecentFileItem(
    file: PersistedFile,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // File type icon
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = getFileIconColor(file.mimeType),
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = getFileIcon(file.mimeType),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(10.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name.substringBeforeLast('.'),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = formatFileSize(file.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatDate(file.lastAccessed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun getFileIconColor(mimeType: String): androidx.compose.ui.graphics.Color {
    return when {
        mimeType == "application/pdf" -> MaterialTheme.colorScheme.error
        mimeType.startsWith("image/") -> MaterialTheme.colorScheme.tertiary
        mimeType.contains("word") || mimeType.contains("document") -> 
            MaterialTheme.colorScheme.primary
        mimeType.contains("excel") || mimeType.contains("spreadsheet") -> 
            MaterialTheme.colorScheme.secondary
        mimeType.contains("powerpoint") || mimeType.contains("presentation") -> 
            MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.outline
    }
}

private fun getFileIcon(mimeType: String): ImageVector {
    return when {
        mimeType == "application/pdf" -> Icons.Default.PictureAsPdf
        mimeType.startsWith("image/") -> Icons.Default.Photo
        mimeType.contains("word") || mimeType.contains("document") -> Icons.Default.Description
        mimeType.contains("excel") || mimeType.contains("spreadsheet") -> Icons.Default.TableView
        mimeType.contains("powerpoint") || mimeType.contains("presentation") -> Icons.Default.Slideshow
        else -> Icons.Default.InsertDriveFile
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "${bytes / (1024 * 1024)} MB"
    }
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

private fun getFileName(context: Context, uri: Uri): String {
    var name = "Document"
    try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) {
                    name = cursor.getString(nameIndex)?.substringBeforeLast('.') ?: name
                }
            }
        }
    } catch (e: Exception) {
        name = uri.lastPathSegment ?: "Document"
    }
    return name
}
