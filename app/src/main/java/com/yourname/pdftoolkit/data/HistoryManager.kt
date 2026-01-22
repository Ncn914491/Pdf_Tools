package com.yourname.pdftoolkit.data

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Represents a single history entry for an operation.
 */
data class HistoryEntry(
    val id: String = UUID.randomUUID().toString(),
    val operationType: OperationType,
    val operationName: String,
    val timestamp: Long = System.currentTimeMillis(),
    val inputFileName: String?,
    val outputFileUri: String?,
    val outputFileName: String?,
    val status: OperationStatus = OperationStatus.SUCCESS,
    val details: String? = null
) {
    val formattedTimestamp: String
        get() {
            val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
            return sdf.format(Date(timestamp))
        }
    
    val relativeTime: String
        get() {
            val now = System.currentTimeMillis()
            val diff = now - timestamp
            return when {
                diff < 60_000 -> "Just now"
                diff < 3600_000 -> "${diff / 60_000}m ago"
                diff < 86400_000 -> "${diff / 3600_000}h ago"
                diff < 604800_000 -> "${diff / 86400_000}d ago"
                else -> formattedTimestamp
            }
        }
}

/**
 * Types of operations tracked in history.
 */
enum class OperationType(val displayName: String, val icon: String) {
    MERGE("Merge PDFs", "merge_type"),
    SPLIT("Split PDF", "call_split"),
    COMPRESS("Compress PDF", "compress"),
    CONVERT("Images to PDF", "transform"),
    PDF_TO_IMAGE("PDF to Images", "image"),
    EXTRACT("Extract Pages", "content_cut"),
    ROTATE("Rotate PDF", "rotate_right"),
    ADD_PASSWORD("Add Password", "lock"),
    REMOVE_PASSWORD("Remove Password", "lock_open"),
    METADATA("Edit Metadata", "info"),
    PAGE_NUMBER("Add Page Numbers", "format_list_numbered"),
    ORGANIZE("Organize Pages", "reorder"),
    UNLOCK("Unlock PDF", "lock_open"),
    REPAIR("Repair PDF", "build"),
    HTML_TO_PDF("HTML to PDF", "code"),
    EXTRACT_TEXT("Extract Text", "text_snippet"),
    WATERMARK("Add Watermark", "water_drop"),
    FLATTEN("Flatten PDF", "layers_clear"),
    SIGN("Sign PDF", "draw"),
    FILL_FORMS("Fill Forms", "edit_note"),
    ANNOTATE("Annotate PDF", "edit"),
    SCAN_TO_PDF("Scan to PDF", "document_scanner"),
    OCR("OCR PDF", "text_recognition"),
    IMAGE_TOOLS("Image Tools", "photo"),
    OPEN_PDF("Open PDF", "picture_as_pdf"),
    OTHER("Other", "more_horiz")
}

/**
 * Status of an operation.
 */
enum class OperationStatus {
    SUCCESS,
    FAILED,
    CANCELLED
}

/**
 * Manages operation history storage and retrieval.
 * Uses SharedPreferences for lightweight persistence.
 */
object HistoryManager {
    private const val PREFS_NAME = "pdf_toolkit_history"
    private const val KEY_HISTORY = "operation_history"
    private const val MAX_HISTORY_ENTRIES = 100
    
    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    /**
     * Add a new entry to history.
     */
    suspend fun addEntry(context: Context, entry: HistoryEntry) = withContext(Dispatchers.IO) {
        val history = getHistory(context).toMutableList()
        history.add(0, entry) // Add at beginning (newest first)
        
        // Trim if necessary
        while (history.size > MAX_HISTORY_ENTRIES) {
            history.removeAt(history.lastIndex)
        }
        
        saveHistory(context, history)
    }
    
    /**
     * Record a successful operation.
     */
    suspend fun recordSuccess(
        context: Context,
        operationType: OperationType,
        inputFileName: String? = null,
        outputFileUri: Uri? = null,
        outputFileName: String? = null,
        details: String? = null
    ) {
        addEntry(context, HistoryEntry(
            operationType = operationType,
            operationName = operationType.displayName,
            inputFileName = inputFileName,
            outputFileUri = outputFileUri?.toString(),
            outputFileName = outputFileName,
            status = OperationStatus.SUCCESS,
            details = details
        ))
    }
    
    /**
     * Record a failed operation.
     */
    suspend fun recordFailure(
        context: Context,
        operationType: OperationType,
        inputFileName: String? = null,
        errorMessage: String? = null
    ) {
        addEntry(context, HistoryEntry(
            operationType = operationType,
            operationName = operationType.displayName,
            inputFileName = inputFileName,
            outputFileUri = null,
            outputFileName = null,
            status = OperationStatus.FAILED,
            details = errorMessage
        ))
    }
    
    /**
     * Get all history entries.
     */
    suspend fun getHistory(context: Context): List<HistoryEntry> = withContext(Dispatchers.IO) {
        try {
            val prefs = getPrefs(context)
            val json = prefs.getString(KEY_HISTORY, null) ?: return@withContext emptyList()
            val jsonArray = JSONArray(json)
            
            val entries = mutableListOf<HistoryEntry>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                entries.add(parseEntry(obj))
            }
            entries
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Clear all history.
     */
    suspend fun clearHistory(context: Context) = withContext(Dispatchers.IO) {
        getPrefs(context).edit().remove(KEY_HISTORY).apply()
    }
    
    /**
     * Delete a specific entry.
     */
    suspend fun deleteEntry(context: Context, entryId: String) = withContext(Dispatchers.IO) {
        val history = getHistory(context).filter { it.id != entryId }
        saveHistory(context, history)
    }
    
    /**
     * Get history filtered by operation type.
     */
    suspend fun getHistoryByType(context: Context, type: OperationType): List<HistoryEntry> {
        return getHistory(context).filter { it.operationType == type }
    }
    
    /**
     * Get recent history (last N entries).
     */
    suspend fun getRecentHistory(context: Context, limit: Int = 10): List<HistoryEntry> {
        return getHistory(context).take(limit)
    }
    
    private fun saveHistory(context: Context, history: List<HistoryEntry>) {
        val jsonArray = JSONArray()
        history.forEach { entry ->
            jsonArray.put(entryToJson(entry))
        }
        getPrefs(context).edit().putString(KEY_HISTORY, jsonArray.toString()).apply()
    }
    
    private fun entryToJson(entry: HistoryEntry): JSONObject {
        return JSONObject().apply {
            put("id", entry.id)
            put("operationType", entry.operationType.name)
            put("operationName", entry.operationName)
            put("timestamp", entry.timestamp)
            put("inputFileName", entry.inputFileName ?: JSONObject.NULL)
            put("outputFileUri", entry.outputFileUri ?: JSONObject.NULL)
            put("outputFileName", entry.outputFileName ?: JSONObject.NULL)
            put("status", entry.status.name)
            put("details", entry.details ?: JSONObject.NULL)
        }
    }
    
    private fun parseEntry(obj: JSONObject): HistoryEntry {
        return HistoryEntry(
            id = obj.optString("id", UUID.randomUUID().toString()),
            operationType = try {
                OperationType.valueOf(obj.getString("operationType"))
            } catch (e: Exception) {
                OperationType.OTHER
            },
            operationName = obj.getString("operationName"),
            timestamp = obj.getLong("timestamp"),
            inputFileName = obj.optString("inputFileName").takeIf { it.isNotEmpty() && it != "null" },
            outputFileUri = obj.optString("outputFileUri").takeIf { it.isNotEmpty() && it != "null" },
            outputFileName = obj.optString("outputFileName").takeIf { it.isNotEmpty() && it != "null" },
            status = try {
                OperationStatus.valueOf(obj.getString("status"))
            } catch (e: Exception) {
                OperationStatus.SUCCESS
            },
            details = obj.optString("details").takeIf { it.isNotEmpty() && it != "null" }
        )
    }
}
