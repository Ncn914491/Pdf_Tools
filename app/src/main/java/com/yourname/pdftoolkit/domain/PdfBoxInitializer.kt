package com.yourname.pdftoolkit.domain

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Singleton object responsible for initializing PdfBox-Android.
 * Must be called before any PDF operations.
 */
object PdfBoxInitializer {
    
    @Volatile
    private var isInitialized = false
    
    /**
     * Initialize PdfBox-Android with the application context.
     * Safe to call multiple times - subsequent calls are no-ops.
     */
    suspend fun initialize(context: Context): Result<Unit> {
        if (isInitialized) return Result.success(Unit)
        
        return withContext(Dispatchers.IO) {
            try {
                PDFBoxResourceLoader.init(context.applicationContext)
                isInitialized = true
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    /**
     * Check if PdfBox is initialized.
     */
    fun isReady(): Boolean = isInitialized
    
    /**
     * Force re-initialization (for testing or recovery).
     */
    suspend fun reinitialize(context: Context): Result<Unit> {
        isInitialized = false
        return initialize(context)
    }
}
