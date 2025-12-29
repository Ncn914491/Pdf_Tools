package com.yourname.pdftoolkit.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.yourname.pdftoolkit.util.OutputFolderManager
import com.yourname.pdftoolkit.util.OutputFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream

/**
 * Composable that provides save location selection UI and handles file creation.
 * 
 * @param useCustomLocation Whether to use custom location (checkbox state)
 * @param onUseCustomLocationChange Callback when checkbox changes
 * @param modifier Optional modifier
 */
@Composable
fun SaveLocationSelector(
    useCustomLocation: Boolean,
    onUseCustomLocationChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = useCustomLocation,
                onCheckedChange = onUseCustomLocationChange
            )
            Text(
                text = "Choose custom save location",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        if (!useCustomLocation) {
            Text(
                text = "Default: ${OutputFolderManager.getOutputFolderPath(context)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 48.dp)
            )
        }
    }
}

/**
 * Result of a save operation.
 */
sealed class SaveResult {
    data class Success(
        val uri: Uri,
        val fileName: String,
        val location: String
    ) : SaveResult()
    
    data class Error(val message: String) : SaveResult()
}

/**
 * Helper object for saving files with default or custom location.
 */
object SaveHelper {
    
    /**
     * Save to default app folder.
     * Returns the output file info on success.
     */
    suspend fun saveToDefaultFolder(
        context: android.content.Context,
        fileName: String,
        writeContent: suspend (OutputStream) -> Result<Unit>
    ): SaveResult {
        return withContext(Dispatchers.IO) {
            try {
                val outputResult = OutputFolderManager.createOutputStream(context, fileName)
                
                if (outputResult == null) {
                    return@withContext SaveResult.Error("Cannot create output file")
                }
                
                val result = writeContent(outputResult.outputStream)
                outputResult.outputStream.close()
                
                result.fold(
                    onSuccess = {
                        SaveResult.Success(
                            uri = outputResult.outputFile.contentUri,
                            fileName = outputResult.outputFile.fileName,
                            location = OutputFolderManager.getOutputFolderPath(context)
                        )
                    },
                    onFailure = { error ->
                        outputResult.outputFile.file.delete()
                        SaveResult.Error(error.message ?: "Operation failed")
                    }
                )
            } catch (e: Exception) {
                SaveResult.Error(e.message ?: "Operation failed")
            }
        }
    }
    
    /**
     * Save to custom location using SAF.
     */
    suspend fun saveToCustomLocation(
        context: android.content.Context,
        outputUri: Uri,
        writeContent: suspend (OutputStream) -> Result<Unit>
    ): SaveResult {
        return withContext(Dispatchers.IO) {
            try {
                val outputStream = context.contentResolver.openOutputStream(outputUri)
                
                if (outputStream == null) {
                    return@withContext SaveResult.Error("Cannot create output file")
                }
                
                val result = writeContent(outputStream)
                outputStream.close()
                
                result.fold(
                    onSuccess = {
                        SaveResult.Success(
                            uri = outputUri,
                            fileName = getFileName(context, outputUri),
                            location = "Selected location"
                        )
                    },
                    onFailure = { error ->
                        SaveResult.Error(error.message ?: "Operation failed")
                    }
                )
            } catch (e: Exception) {
                SaveResult.Error(e.message ?: "Operation failed")
            }
        }
    }
    
    private fun getFileName(context: android.content.Context, uri: Uri): String {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0) cursor.getString(nameIndex) else "output.pdf"
                } else "output.pdf"
            } ?: "output.pdf"
        } catch (e: Exception) {
            "output.pdf"
        }
    }
}
