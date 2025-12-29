package com.yourname.pdftoolkit.ui.screens

import android.net.Uri
import android.webkit.URLUtil
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
import androidx.compose.ui.unit.dp
import com.yourname.pdftoolkit.domain.operations.HtmlToPdfConverter
import com.yourname.pdftoolkit.ui.components.*
import com.yourname.pdftoolkit.util.FileOpener
import com.yourname.pdftoolkit.util.OutputFolderManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Screen for converting HTML/URLs to PDF.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HtmlToPdfScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val converter = remember { HtmlToPdfConverter() }
    
    // State
    var inputMode by remember { mutableStateOf(InputMode.URL) }
    var urlInput by remember { mutableStateOf("") }
    var htmlInput by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var showResult by remember { mutableStateOf(false) }
    var resultSuccess by remember { mutableStateOf(false) }
    var resultMessage by remember { mutableStateOf("") }
    var useCustomLocation by remember { mutableStateOf(false) }
    var resultUri by remember { mutableStateOf<Uri?>(null) }
    
    val isInputValid = when (inputMode) {
        InputMode.URL -> urlInput.isNotBlank() && 
            (urlInput.startsWith("http://") || urlInput.startsWith("https://"))
        InputMode.HTML -> htmlInput.isNotBlank()
    }
    
    // Save file launcher (for custom location)
    val saveFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        uri?.let { saveUri ->
            scope.launch {
                isProcessing = true
                progress = 0f
                
                context.contentResolver.openOutputStream(saveUri)?.use { outputStream ->
                    val result = when (inputMode) {
                        InputMode.URL -> converter.convertUrlToPdf(
                            context = context,
                            url = urlInput,
                            outputStream = outputStream,
                            onProgress = { progress = it }
                        )
                        InputMode.HTML -> converter.convertHtmlToPdf(
                            context = context,
                            htmlContent = htmlInput,
                            outputStream = outputStream,
                            onProgress = { progress = it }
                        )
                    }
                    
                    result.fold(
                        onSuccess = { conversionResult ->
                            resultSuccess = true
                            resultUri = saveUri
                            resultMessage = "PDF created successfully!\n\nPages: ${conversionResult.pageCount}"
                            urlInput = ""
                            htmlInput = ""
                        },
                        onFailure = { error ->
                            resultSuccess = false
                            resultMessage = error.message ?: "Conversion failed"
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
    
    // Function to convert with default location
    fun convertWithDefaultLocation() {
        scope.launch {
            isProcessing = true
            progress = 0f
            
            val result = withContext(Dispatchers.IO) {
                try {
                    val fileName = when (inputMode) {
                        InputMode.URL -> {
                            val domain = try {
                                java.net.URL(urlInput).host.replace("www.", "")
                            } catch (e: Exception) {
                                "webpage"
                            }
                            "${domain}_${System.currentTimeMillis()}.pdf"
                        }
                        InputMode.HTML -> "html_document_${System.currentTimeMillis()}.pdf"
                    }
                    val outputResult = OutputFolderManager.createOutputStream(context, fileName)
                    
                    if (outputResult != null) {
                        val convResult = when (inputMode) {
                            InputMode.URL -> converter.convertUrlToPdf(
                                context = context,
                                url = urlInput,
                                outputStream = outputResult.outputStream,
                                onProgress = { progress = it }
                            )
                            InputMode.HTML -> converter.convertHtmlToPdf(
                                context = context,
                                htmlContent = htmlInput,
                                outputStream = outputResult.outputStream,
                                onProgress = { progress = it }
                            )
                        }
                        
                        outputResult.outputStream.close()
                        
                        convResult.fold(
                            onSuccess = { cResult ->
                                Triple(true, "PDF created successfully!\n\nPages: ${cResult.pageCount}\n\nSaved to: ${OutputFolderManager.getOutputFolderPath(context)}/${outputResult.outputFile.fileName}", outputResult.outputFile.contentUri)
                            },
                            onFailure = { error ->
                                outputResult.outputFile.file.delete()
                                Triple(false, error.message ?: "Conversion failed", null)
                            }
                        )
                    } else {
                        Triple(false, "Cannot create output file", null)
                    }
                } catch (e: Exception) {
                    Triple(false, e.message ?: "Conversion failed", null)
                }
            }
            
            resultSuccess = result.first
            resultMessage = result.second
            resultUri = result.third
            if (resultSuccess) {
                urlInput = ""
                htmlInput = ""
            }
            isProcessing = false
            showResult = true
        }
    }
    
    Scaffold(
        topBar = {
            ToolTopBar(
                title = "HTML to PDF",
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
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(vertical = 16.dp)
                ) {
                    // Mode selection
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = inputMode == InputMode.URL,
                                onClick = { inputMode = InputMode.URL },
                                label = { Text("From URL") },
                                leadingIcon = if (inputMode == InputMode.URL) {
                                    { Icon(Icons.Default.Link, null, Modifier.size(18.dp)) }
                                } else null,
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = inputMode == InputMode.HTML,
                                onClick = { inputMode = InputMode.HTML },
                                label = { Text("From HTML") },
                                leadingIcon = if (inputMode == InputMode.HTML) {
                                    { Icon(Icons.Default.Code, null, Modifier.size(18.dp)) }
                                } else null,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    
                    // Input field
                    item {
                        when (inputMode) {
                            InputMode.URL -> {
                                OutlinedTextField(
                                    value = urlInput,
                                    onValueChange = { urlInput = it },
                                    label = { Text("Website URL") },
                                    placeholder = { Text("https://example.com") },
                                    singleLine = true,
                                    leadingIcon = {
                                        Icon(Icons.Default.Language, null)
                                    },
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Uri
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            InputMode.HTML -> {
                                OutlinedTextField(
                                    value = htmlInput,
                                    onValueChange = { htmlInput = it },
                                    label = { Text("HTML Content") },
                                    placeholder = { Text("<html>\n  <body>\n    <h1>Hello</h1>\n  </body>\n</html>") },
                                    minLines = 8,
                                    maxLines = 15,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                    
                    // Quick templates for HTML mode
                    if (inputMode == InputMode.HTML) {
                        item {
                            Text(
                                text = "Quick Templates",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        item {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                AssistChip(
                                    onClick = {
                                        htmlInput = """
<!DOCTYPE html>
<html>
<head>
    <title>My Document</title>
    <style>
        body { font-family: Arial, sans-serif; padding: 20px; }
        h1 { color: #333; }
    </style>
</head>
<body>
    <h1>Document Title</h1>
    <p>Your content here...</p>
</body>
</html>
                                        """.trimIndent()
                                    },
                                    label = { Text("Basic") }
                                )
                                AssistChip(
                                    onClick = {
                                        htmlInput = """
<!DOCTYPE html>
<html>
<head>
    <title>Invoice</title>
    <style>
        body { font-family: Arial; padding: 20px; }
        .header { text-align: center; margin-bottom: 30px; }
        table { width: 100%; border-collapse: collapse; }
        th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }
        th { background: #f4f4f4; }
        .total { font-weight: bold; text-align: right; margin-top: 20px; }
    </style>
</head>
<body>
    <div class="header">
        <h1>INVOICE</h1>
        <p>Invoice #: 001</p>
        <p>Date: ${java.time.LocalDate.now()}</p>
    </div>
    <table>
        <tr><th>Item</th><th>Qty</th><th>Price</th></tr>
        <tr><td>Service</td><td>1</td><td>₹100.00</td></tr>
    </table>
    <p class="total">Total: ₹100.00</p>
</body>
</html>
                                        """.trimIndent()
                                    },
                                    label = { Text("Invoice") }
                                )
                            }
                        }
                    }
                    
                    // Info card
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
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = when (inputMode) {
                                        InputMode.URL -> "Enter a website URL to convert to PDF. The page will be rendered as it appears in a browser."
                                        InputMode.HTML -> "Enter HTML code to convert to a PDF document. CSS styles are supported."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
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
                                message = if (inputMode == InputMode.URL) 
                                    "Loading webpage..." 
                                else 
                                    "Converting HTML..."
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
                    // Save location option
                    SaveLocationSelector(
                        useCustomLocation = useCustomLocation,
                        onUseCustomLocationChange = { useCustomLocation = it }
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    ActionButton(
                        text = "Convert to PDF",
                        onClick = {
                            if (useCustomLocation) {
                                val fileName = when (inputMode) {
                                    InputMode.URL -> {
                                        val domain = try {
                                            java.net.URL(urlInput).host.replace("www.", "")
                                        } catch (e: Exception) {
                                            "webpage"
                                        }
                                        "${domain}_${System.currentTimeMillis()}.pdf"
                                    }
                                    InputMode.HTML -> "html_document_${System.currentTimeMillis()}.pdf"
                                }
                                saveFileLauncher.launch(fileName)
                            } else {
                                convertWithDefaultLocation()
                            }
                        },
                        enabled = isInputValid,
                        isLoading = isProcessing,
                        icon = Icons.Default.PictureAsPdf
                    )
                }
            }
        }
    }
    
    // Result dialog with View option
    if (showResult) {
        ResultDialog(
            isSuccess = resultSuccess,
            title = if (resultSuccess) "Conversion Complete" else "Conversion Failed",
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

private enum class InputMode {
    URL,
    HTML
}
