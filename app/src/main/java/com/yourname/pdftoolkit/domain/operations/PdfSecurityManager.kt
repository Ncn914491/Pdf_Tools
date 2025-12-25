package com.yourname.pdftoolkit.domain.operations

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream

/**
 * Security options for PDF protection.
 */
data class PdfSecurityOptions(
    val ownerPassword: String,
    val userPassword: String,
    val allowPrinting: Boolean = true,
    val allowCopying: Boolean = false,
    val allowModifying: Boolean = false,
    val allowAnnotations: Boolean = false,
    val keyLength: Int = 128 // 40 or 128 bits
)

/**
 * Handles PDF security operations (encryption/decryption).
 */
class PdfSecurityManager {
    
    /**
     * Add password protection to a PDF.
     * 
     * @param context Android context
     * @param inputUri URI of the PDF to protect
     * @param outputStream Output stream for the protected PDF
     * @param options Security options including passwords and permissions
     * @param onProgress Progress callback (0.0 to 1.0)
     * @return Result indicating success or failure
     */
    suspend fun encryptPdf(
        context: Context,
        inputUri: Uri,
        outputStream: OutputStream,
        options: PdfSecurityOptions,
        onProgress: (Float) -> Unit = {}
    ): Result<Unit> = withContext(Dispatchers.IO) {
        var document: PDDocument? = null
        
        try {
            onProgress(0.1f)
            
            val inputStream = context.contentResolver.openInputStream(inputUri)
                ?: return@withContext Result.failure(
                    IllegalStateException("Cannot open input file")
                )
            
            document = PDDocument.load(inputStream)
            
            onProgress(0.3f)
            
            // Set up access permissions
            val accessPermission = AccessPermission().apply {
                setCanPrint(options.allowPrinting)
                setCanExtractContent(options.allowCopying)
                setCanModify(options.allowModifying)
                setCanModifyAnnotations(options.allowAnnotations)
            }
            
            onProgress(0.5f)
            
            // Create protection policy
            val protectionPolicy = StandardProtectionPolicy(
                options.ownerPassword,
                options.userPassword,
                accessPermission
            ).apply {
                encryptionKeyLength = options.keyLength
            }
            
            // Apply protection
            document.protect(protectionPolicy)
            
            onProgress(0.8f)
            
            // Save encrypted document
            document.save(outputStream)
            
            onProgress(1.0f)
            
            Result.success(Unit)
            
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            document?.close()
        }
    }
    
    /**
     * Remove password protection from a PDF.
     * Requires the correct password to decrypt.
     * 
     * @param context Android context
     * @param inputUri URI of the encrypted PDF
     * @param outputStream Output stream for the decrypted PDF
     * @param password Password to unlock the PDF
     * @param onProgress Progress callback (0.0 to 1.0)
     * @return Result indicating success or failure
     */
    suspend fun decryptPdf(
        context: Context,
        inputUri: Uri,
        outputStream: OutputStream,
        password: String,
        onProgress: (Float) -> Unit = {}
    ): Result<Unit> = withContext(Dispatchers.IO) {
        var document: PDDocument? = null
        
        try {
            onProgress(0.1f)
            
            val inputStream = context.contentResolver.openInputStream(inputUri)
                ?: return@withContext Result.failure(
                    IllegalStateException("Cannot open input file")
                )
            
            // Load with password
            document = PDDocument.load(inputStream, password)
            
            onProgress(0.4f)
            
            if (!document.isEncrypted) {
                return@withContext Result.failure(
                    IllegalStateException("PDF is not encrypted")
                )
            }
            
            // Remove encryption by setting all permissions
            document.isAllSecurityToBeRemoved = true
            
            onProgress(0.7f)
            
            // Save decrypted document
            document.save(outputStream)
            
            onProgress(1.0f)
            
            Result.success(Unit)
            
        } catch (e: Exception) {
            when {
                e.message?.contains("password", ignoreCase = true) == true ->
                    Result.failure(IllegalArgumentException("Incorrect password"))
                else -> Result.failure(e)
            }
        } finally {
            document?.close()
        }
    }
    
    /**
     * Check if a PDF is encrypted.
     */
    suspend fun isEncrypted(
        context: Context,
        uri: Uri
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                PDDocument.load(inputStream).use { document ->
                    document.isEncrypted
                }
            } ?: false
        } catch (e: Exception) {
            // If loading fails with a password error, it's likely encrypted
            e.message?.contains("password", ignoreCase = true) == true
        }
    }
    
    /**
     * Validate a password against an encrypted PDF.
     */
    suspend fun validatePassword(
        context: Context,
        uri: Uri,
        password: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                PDDocument.load(inputStream, password).use { _ ->
                    true // Password is correct
                }
            } ?: false
        } catch (e: Exception) {
            false // Password is incorrect or other error
        }
    }
}
