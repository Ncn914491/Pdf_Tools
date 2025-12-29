package com.yourname.pdftoolkit.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.yourname.pdftoolkit.util.CacheManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Comprehensive Settings Screen with organized sections.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var cacheSize by remember { mutableStateOf("Calculating...") }
    var isClearing by remember { mutableStateOf(false) }
    var showClearCacheDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showFeatureRequestDialog by remember { mutableStateOf(false) }
    
    // Calculate cache size on screen load
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            cacheSize = CacheManager.getFormattedCacheSize(context)
        }
    }
    
    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            // Storage Section
            item {
                SettingsSectionHeader(title = "Storage")
            }
            
            item {
                SettingsItem(
                    title = "Cache Size",
                    subtitle = cacheSize,
                    icon = Icons.Default.Storage,
                    onClick = { showClearCacheDialog = true }
                ) {
                    if (isClearing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        TextButton(onClick = { showClearCacheDialog = true }) {
                            Text("Clear")
                        }
                    }
                }
            }
            
            // Support Section
            item {
                SettingsSectionHeader(title = "Support")
            }
            
            item {
                SettingsItem(
                    title = "Request a Feature",
                    subtitle = "Share your ideas to improve the app",
                    icon = Icons.Default.Lightbulb,
                    onClick = { showFeatureRequestDialog = true }
                )
            }
            
            item {
                SettingsItem(
                    title = "Report a Bug",
                    subtitle = "Help us fix issues",
                    icon = Icons.Default.BugReport,
                    onClick = {
                        sendBugReport(context)
                    }
                )
            }
            
            item {
                SettingsItem(
                    title = "Rate the App",
                    subtitle = "Love the app? Rate us on Play Store",
                    icon = Icons.Default.Star,
                    onClick = {
                        openPlayStore(context)
                    }
                )
            }
            
            // About Section
            item {
                SettingsSectionHeader(title = "About")
            }
            
            item {
                SettingsItem(
                    title = "Version",
                    subtitle = "1.2.2 (Build 7)",
                    icon = Icons.Default.Info,
                    onClick = { showAboutDialog = true }
                )
            }
            
            item {
                SettingsItem(
                    title = "Privacy Policy",
                    subtitle = "View our privacy policy",
                    icon = Icons.Default.PrivacyTip,
                    onClick = {
                        openPrivacyPolicy(context)
                    }
                )
            }
            
            item {
                SettingsItem(
                    title = "Open Source Licenses",
                    subtitle = "View third-party licenses",
                    icon = Icons.Default.Description,
                    onClick = {
                        // TODO: Open licenses activity
                        Toast.makeText(context, "Licenses: PdfBox-Android (Apache 2.0), ML Kit (Apache 2.0)", Toast.LENGTH_LONG).show()
                    }
                )
            }
            
            // App Info Footer
            item {
                Spacer(modifier = Modifier.height(32.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(64.dp)
                    ) {
                        Icon(
                            Icons.Default.PictureAsPdf,
                            contentDescription = null,
                            modifier = Modifier
                                .padding(16.dp)
                                .size(32.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "PDF Toolkit",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Made with ❤️ for productivity",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "© 2024 PDF Toolkit",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
    
    // Clear Cache Dialog
    if (showClearCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearCacheDialog = false },
            icon = { Icon(Icons.Default.DeleteSweep, contentDescription = null) },
            title = { Text("Clear Cache?") },
            text = {
                Text("This will delete temporary files and cached data. Your saved PDFs will not be affected.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        isClearing = true
                        showClearCacheDialog = false
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                CacheManager.clearAllCache(context)
                            }
                            cacheSize = CacheManager.getFormattedCacheSize(context)
                            isClearing = false
                            Toast.makeText(context, "Cache cleared successfully", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    // About Dialog
    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            icon = { Icon(Icons.Default.Info, contentDescription = null) },
            title = { Text("About PDF Toolkit") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("PDF Toolkit is a powerful, offline PDF tool that helps you manage PDF documents quickly and efficiently.")
                    
                    Divider()
                    
                    Text(
                        text = "Features:",
                        fontWeight = FontWeight.SemiBold
                    )
                    Text("• Merge, Split, Compress PDFs")
                    Text("• Convert to/from images")
                    Text("• Add watermarks & signatures")
                    Text("• OCR & Scan to PDF")
                    Text("• Secure & encrypt PDFs")
                    
                    Divider()
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Version", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("1.2.2")
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Build", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("7")
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Made with", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Kotlin & Jetpack Compose")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
    
    // Feature Request Dialog
    if (showFeatureRequestDialog) {
        FeatureRequestDialog(
            onDismiss = { showFeatureRequestDialog = false },
            onSubmit = { featureText ->
                sendFeatureRequest(context, featureText)
                showFeatureRequestDialog = false
            }
        )
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp)
    )
}

@Composable
private fun SettingsItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    trailing: @Composable (() -> Unit)? = null
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    modifier = Modifier
                        .padding(8.dp)
                        .size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            if (trailing != null) {
                trailing()
            } else {
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeatureRequestDialog(
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit
) {
    var featureText by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Feature") }
    var showCategoryMenu by remember { mutableStateOf(false) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Lightbulb, contentDescription = null) },
        title = { Text("Request a Feature") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Share your idea to help us improve PDF Toolkit!")
                
                // Category selector
                ExposedDropdownMenuBox(
                    expanded = showCategoryMenu,
                    onExpandedChange = { showCategoryMenu = it }
                ) {
                    OutlinedTextField(
                        value = category,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = showCategoryMenu)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = showCategoryMenu,
                        onDismissRequest = { showCategoryMenu = false }
                    ) {
                        listOf("Feature", "Improvement", "UI/UX", "Other").forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    category = option
                                    showCategoryMenu = false
                                }
                            )
                        }
                    }
                }
                
                OutlinedTextField(
                    value = featureText,
                    onValueChange = { featureText = it },
                    label = { Text("Describe your idea") },
                    placeholder = { Text("What feature would you like to see?") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp),
                    maxLines = 6
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit("[$category] $featureText") },
                enabled = featureText.isNotBlank()
            ) {
                Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Submit")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// Helper functions for intents
private fun sendFeatureRequest(context: Context, featureText: String) {
    val deviceInfo = """
        
        ---
        Device: ${Build.MANUFACTURER} ${Build.MODEL}
        Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})
        App Version: 1.2.2 (7)
    """.trimIndent()
    
    val intent = Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.parse("mailto:")
        putExtra(Intent.EXTRA_EMAIL, arrayOf("feedback.pdftoolkit@gmail.com"))
        putExtra(Intent.EXTRA_SUBJECT, "[Feature Request] PDF Toolkit")
        putExtra(Intent.EXTRA_TEXT, "$featureText\n$deviceInfo")
    }
    
    if (intent.resolveActivity(context.packageManager) != null) {
        context.startActivity(Intent.createChooser(intent, "Send Feature Request"))
    } else {
        Toast.makeText(context, "No email app found. Please install an email app.", Toast.LENGTH_LONG).show()
    }
}

private fun sendBugReport(context: Context) {
    val deviceInfo = """
        Bug Description:
        [Please describe the issue you encountered]
        
        Steps to Reproduce:
        1. 
        2. 
        3. 
        
        Expected Behavior:
        [What did you expect to happen?]
        
        Actual Behavior:
        [What actually happened?]
        
        ---
        Device: ${Build.MANUFACTURER} ${Build.MODEL}
        Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})
        App Version: 1.2.2 (7)
    """.trimIndent()
    
    val intent = Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.parse("mailto:")
        putExtra(Intent.EXTRA_EMAIL, arrayOf("feedback.pdftoolkit@gmail.com"))
        putExtra(Intent.EXTRA_SUBJECT, "[Bug Report] PDF Toolkit")
        putExtra(Intent.EXTRA_TEXT, deviceInfo)
    }
    
    if (intent.resolveActivity(context.packageManager) != null) {
        context.startActivity(Intent.createChooser(intent, "Send Bug Report"))
    } else {
        Toast.makeText(context, "No email app found", Toast.LENGTH_LONG).show()
    }
}

private fun openPlayStore(context: Context) {
    try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.yourname.pdftoolkit"))
        )
    } catch (e: Exception) {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.yourname.pdftoolkit"))
        )
    }
}

private fun openPrivacyPolicy(context: Context) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://ncn914491.github.io/Pdf_Tools/"))
    context.startActivity(intent)
}
