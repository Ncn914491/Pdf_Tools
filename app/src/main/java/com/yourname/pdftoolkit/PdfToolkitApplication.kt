package com.yourname.pdftoolkit

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

/**
 * Application class for PDF Toolkit.
 * Initializes PdfBox-Android on startup.
 */
class PdfToolkitApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize PdfBox-Android
        PDFBoxResourceLoader.init(applicationContext)
    }
}