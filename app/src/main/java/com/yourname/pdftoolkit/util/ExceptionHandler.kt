package com.yourname.pdftoolkit.util

import android.content.Context
import android.widget.Toast
import java.io.FileNotFoundException
import java.io.IOException
import java.security.Permission

/**
 * Utility object for handling common exceptions in PDF operations.
 * Provides user-friendly error messages and centralized error handling.
 */
object ExceptionHandler {

    /**
     * Get a user-friendly error message for common exceptions.
     */
    fun getErrorMessage(exception: Throwable): String {
        return when (exception) {
            is FileNotFoundException -> "File not found. It may have been moved or deleted."
            is SecurityException -> "Permission denied. Please grant access to the file."
            is IOException -> {
                when {
                    exception.message?.contains("Permission denied", ignoreCase = true) == true ->
                        "Permission denied. Please grant storage access."
                    exception.message?.contains("No space", ignoreCase = true) == true ->
                        "Not enough storage space. Please free up some space."
                    exception.message?.contains("Read-only", ignoreCase = true) == true ->
                        "Cannot write to this location. Please choose a different folder."
                    else -> "I/O error: ${exception.message ?: "Unknown file operation error"}"
                }
            }
            is OutOfMemoryError -> "Not enough memory to process the file. Try with a smaller PDF."
            is IllegalArgumentException -> {
                when {
                    exception.message?.contains("password", ignoreCase = true) == true ->
                        "Invalid password. Please enter the correct password."
                    exception.message?.contains("encrypted", ignoreCase = true) == true ->
                        "This PDF is encrypted. Please provide the password."
                    exception.message?.contains("corrupted", ignoreCase = true) == true ->
                        "The PDF file appears to be corrupted."
                    else -> exception.message ?: "Invalid input provided."
                }
            }
            is UnsupportedOperationException -> "This operation is not supported for this file type."
            is IllegalStateException -> {
                when {
                    exception.message?.contains("closed", ignoreCase = true) == true ->
                        "File was closed unexpectedly. Please try again."
                    else -> exception.message ?: "Invalid state. Please try again."
                }
            }
            else -> exception.message ?: "An unexpected error occurred. Please try again."
        }
    }

    /**
     * Handle an exception and show a toast message.
     */
    fun handleWithToast(context: Context, exception: Throwable) {
        val message = getErrorMessage(exception)
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    /**
     * Check if the exception is recoverable (user can try again).
     */
    fun isRecoverable(exception: Throwable): Boolean {
        return when (exception) {
            is SecurityException -> true // User can grant permission
            is FileNotFoundException -> false // File is gone
            is OutOfMemoryError -> false // Need smaller file
            is IOException -> {
                // Some IO exceptions are recoverable
                exception.message?.contains("Permission denied", ignoreCase = true) == true ||
                exception.message?.contains("No space", ignoreCase = true) == true
            }
            else -> true
        }
    }

    /**
     * Get a suggested action for the user based on the exception.
     */
    fun getSuggestedAction(exception: Throwable): String? {
        return when (exception) {
            is SecurityException -> "Try selecting the file again and grant access."
            is FileNotFoundException -> "Please select a different file."
            is OutOfMemoryError -> "Try with a smaller PDF or close other apps."
            is IOException -> {
                when {
                    exception.message?.contains("No space", ignoreCase = true) == true ->
                        "Free up storage space and try again."
                    exception.message?.contains("Permission denied", ignoreCase = true) == true ->
                        "Check app permissions in Settings."
                    else -> null
                }
            }
            is IllegalArgumentException -> {
                when {
                    exception.message?.contains("password", ignoreCase = true) == true ->
                        "Enter the correct password and try again."
                    else -> null
                }
            }
            else -> null
        }
    }

    /**
     * Check if the file type is supported.
     */
    fun isFiletypeSupported(mimeType: String?, fileName: String?): Boolean {
        if (mimeType == "application/pdf") return true
        if (fileName?.endsWith(".pdf", ignoreCase = true) == true) return true
        return false
    }

    /**
     * Get error message for unsupported file type.
     */
    fun getUnsupportedFileTypeMessage(fileName: String?): String {
        return "Unsupported file type. Please select a PDF file.${
            fileName?.let { "\n\nSelected: $it" } ?: ""
        }"
    }
}

/**
 * Extension function to safely execute a block and handle exceptions.
 */
inline fun <T> safeExecute(
    block: () -> T,
    onError: (Throwable) -> T
): T {
    return try {
        block()
    } catch (e: Exception) {
        onError(e)
    }
}

/**
 * Extension function to safely execute a suspend block and handle exceptions.
 */
suspend inline fun <T> safeSuspendExecute(
    crossinline block: suspend () -> T,
    crossinline onError: (Throwable) -> T
): T {
    return try {
        block()
    } catch (e: Exception) {
        onError(e)
    }
}
