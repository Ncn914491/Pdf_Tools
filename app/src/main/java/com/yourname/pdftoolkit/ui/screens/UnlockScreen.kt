package com.yourname.pdftoolkit.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.yourname.pdftoolkit.data.FileManager
import com.yourname.pdftoolkit.data.PdfFileInfo
import com.yourname.pdftoolkit.domain.operations.PdfUnlocker
import com.yourname.pdftoolkit.domain.operations.UnlockError
import com.yourname.pdftoolkit.ui.components.*
import com.yourname.pdftoolkit.util.FileOpener
import com.yourname.pdftoolkit.util.OutputFolderManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Screen for unlocking password-protected PDFs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnlockScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val unlocker = remember { PdfUnlocker() }
    
    // State
    var selectedFile by remember { mutableStateOf<PdfFileInfo?>(null) }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isEncrypted by remember { mutableStateOf<Boolean?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var showResult by remember { mutableStateOf(false) }
    var resultSuccess by remember { mutableStateOf(false) }
    var resultMessage by remember { mutableStateOf("") }
    var resultUri by remember { mutableStateOf<Uri?>(null) }
    var useCustomLocation by remember { mutableStateOf(false) }
    
    // File picker launcher
    val pickPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            selectedFile = FileManager.getFileInfo(context, uri)
            password = ""
            isEncrypted = null
            
            // Check if file is encrypted
            scope.launch {
                isEncrypted = unlocker.isEncrypted(context, uri)
            }
        }
    }
    
    // Save file launcher (for custom location)
    val saveFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        uri?.let { saveUri ->
            val file = selectedFile ?: return@let
            
            scope.launch {
                isProcessing = true
                progress = 0f
                
                context.contentResolver.openOutputStream(saveUri)?.use { outputStream ->
                    val result = unlocker.unlockPdf(
                        context = context,
                        inputUri = file.uri,
                        outputStream = outputStream,
                        password = password,
                        onProgress = { progress = it }
                    )
                    
                    result.fold(
                        onSuccess = { unlockResult ->
                            resultSuccess = true
                            resultUri = saveUri
                            resultMessage = buildString {
                                append("PDF unlocked successfully!")
                                unlockResult.originalPermissions?.let { perms ->
                                    append("\n\nOriginal restrictions:")
                                    if (!perms.canPrint) append("\n• Printing was disabled")
                                    if (!perms.canCopy) append("\n• Copying was disabled")
                                    if (!perms.canModify) append("\n• Editing was disabled")
                                    if (!perms.canAnnotate) append("\n• Annotations were disabled")
                                }
                            }
                            selectedFile = null
                            password = ""
                        },
                        onFailure = { error ->
                            resultSuccess = false
                            resultMessage = when (error) {
                                is UnlockError.IncorrectPassword -> "Incorrect password. Please try again."
                                is UnlockError.NotEncrypted -> "This PDF is not encrypted."
                                is UnlockError.FileNotFound -> "Cannot open the file."
                                is UnlockError.EmptyDocument -> "The PDF has no pages."
                                is UnlockError.GenericError -> error.message
                                else -> error.message ?: "Failed to unlock PDF"
                            }
                        }
                    )
                } ?: run {
                    resultSuccess = false
                    resultMessage = "Cannot create output file"
                }
                
                isProcessing = false
                showResult = true
            }
        }
    }
    
    // Function to unlock with default location
    fun unlockWithDefaultLocation() {
        scope.launch {
            isProcessing = true
            progress = 0f
            
            val result = withContext(Dispatchers.IO) {
                try {
                    val file = selectedFile!!
                    val baseName = file.name.removeSuffix(".pdf")
                    val fileName = "${baseName}_unlocked.pdf"
                    val outputResult = OutputFolderManager.createOutputStream(context, fileName)
                    
                    if (outputResult != null) {
                        val unlockResult = unlocker.unlockPdf(
                            context = context,
                            inputUri = file.uri,
                            outputStream = outputResult.outputStream,
                            password = password,
                            onProgress = { progress = it }
                        )
                        
                        outputResult.outputStream.close()
                        
                        unlockResult.fold(
                            onSuccess = { result ->
                                val message = buildString {
                                    append("PDF unlocked successfully!\n\nSaved to: ${OutputFolderManager.getOutputFolderPath(context)}/${outputResult.outputFile.fileName}")
                                    result.originalPermissions?.let { perms ->
                                        append("\n\nOriginal restrictions:")
                                        if (!perms.canPrint) append("\n• Printing was disabled")
                                        if (!perms.canCopy) append("\n• Copying was disabled")
                                        if (!perms.canModify) append("\n• Editing was disabled")
                                        if (!perms.canAnnotate) append("\n• Annotations were disabled")
                                    }
                                }
                                Triple(true, message, outputResult.outputFile.contentUri)
                            },
                            onFailure = { error ->
                                outputResult.outputFile.file.delete()
                                val errorMsg = when (error) {
                                    is UnlockError.IncorrectPassword -> "Incorrect password. Please try again."
                                    is UnlockError.NotEncrypted -> "This PDF is not encrypted."
                                    is UnlockError.FileNotFound -> "Cannot open the file."
                                    is UnlockError.EmptyDocument -> "The PDF has no pages."
                                    is UnlockError.GenericError -> error.message
                                    else -> error.message ?: "Failed to unlock PDF"
                                }
                                Triple(false, errorMsg, null)
                            }
                        )
                    } else {
                        Triple(false, "Cannot create output file", null)
                    }
                } catch (e: Exception) {
                    Triple(false, e.message ?: "Failed to unlock PDF", null)
                }
            }
            
            resultSuccess = result.first
            resultMessage = result.second
            resultUri = result.third
            if (resultSuccess) {
                selectedFile = null
                password = ""
            }
            isProcessing = false
            showResult = true
        }
    }
    
    Scaffold(
        topBar = {
            ToolTopBar(
                title = "Unlock PDF",
                onNavigateBack = onNavigateBack
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Content area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (selectedFile == null) {
                    EmptyState(
                        icon = Icons.Default.LockOpen,
                        title = "No PDF Selected",
                        subtitle = "Select a password-protected PDF to unlock",
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(vertical = 16.dp)
                    ) {
                        // Selected file info
                        item {
                            FileItemCard(
                                fileName = selectedFile!!.name,
                                fileSize = selectedFile!!.formattedSize,
                                onRemove = { 
                                    selectedFile = null
                                    password = ""
                                    isEncrypted = null
                                }
                            )
                        }
                        
                        // Encryption status
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = when (isEncrypted) {
                                        true -> MaterialTheme.colorScheme.errorContainer
                                        false -> MaterialTheme.colorScheme.tertiaryContainer
                                        null -> MaterialTheme.colorScheme.surfaceVariant
                                    }
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = when (isEncrypted) {
                                            true -> Icons.Default.Lock
                                            false -> Icons.Default.LockOpen
                                            null -> Icons.Default.HourglassEmpty
                                        },
                                        contentDescription = null
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = when (isEncrypted) {
                                                true -> "PDF is Password Protected"
                                                false -> "PDF is Not Encrypted"
                                                null -> "Checking encryption status..."
                                            },
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                        if (isEncrypted == false) {
                                            Text(
                                                text = "This file does not require unlocking",
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        
                        // Password input (only show if encrypted)
                        if (isEncrypted == true) {
                            item {
                                Text(
                                    text = "Enter Password",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            
                            item {
                                OutlinedTextField(
                                    value = password,
                                    onValueChange = { password = it },
                                    label = { Text("Password") },
                                    placeholder = { Text("Enter PDF password") },
                                    singleLine = true,
                                    visualTransformation = if (passwordVisible) {
                                        VisualTransformation.None
                                    } else {
                                        PasswordVisualTransformation()
                                    },
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Password
                                    ),
                                    trailingIcon = {
                                        IconButton(
                                            onClick = { passwordVisible = !passwordVisible }
                                        ) {
                                            Icon(
                                                imageVector = if (passwordVisible) {
                                                    Icons.Default.VisibilityOff
                                                } else {
                                                    Icons.Default.Visibility
                                                },
                                                contentDescription = if (passwordVisible) {
                                                    "Hide password"
                                                } else {
                                                    "Show password"
                                                }
                                            )
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Info,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onTertiaryContainer
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = "The unlocked PDF will be saved as a new file without any password protection.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                
                // Progress overlay
                if (isProcessing) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp)
                            .align(Alignment.Center)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            OperationProgress(
                                progress = progress,
                                message = "Unlocking PDF..."
                            )
                        }
                    }
                }
            }
            
            // Bottom action area
            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 3.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    if (selectedFile == null) {
                        ActionButton(
                            text = "Select PDF",
                            onClick = {
                                pickPdfLauncher.launch(arrayOf("application/pdf"))
                            },
                            icon = Icons.Default.FolderOpen
                        )
                    } else {
                        // Save location option
                        SaveLocationSelector(
                            useCustomLocation = useCustomLocation,
                            onUseCustomLocationChange = { useCustomLocation = it }
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        ActionButton(
                            text = "Unlock PDF",
                            onClick = {
                                if (useCustomLocation) {
                                    val baseName = selectedFile!!.name.removeSuffix(".pdf")
                                    saveFileLauncher.launch("${baseName}_unlocked.pdf")
                                } else {
                                    unlockWithDefaultLocation()
                                }
                            },
                            enabled = isEncrypted == true && password.isNotEmpty(),
                            isLoading = isProcessing,
                            icon = Icons.Default.LockOpen
                        )
                    }
                }
            }
        }
    }
    
    // Result dialog with View option
    if (showResult) {
        ResultDialog(
            isSuccess = resultSuccess,
            title = if (resultSuccess) "PDF Unlocked" else "Unlock Failed",
            message = resultMessage,
            onDismiss = { 
                showResult = false
                resultUri = null
            },
            onAction = resultUri?.let { uri ->
                { FileOpener.openPdf(context, uri) }
            },
            actionText = "Open PDF"
        )
    }
}
