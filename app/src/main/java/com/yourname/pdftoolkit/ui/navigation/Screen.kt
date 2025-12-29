package com.yourname.pdftoolkit.ui.navigation

/**
 * Sealed class representing all navigation destinations in the app.
 * Each screen has a unique route string for navigation.
 */
sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Settings : Screen("settings")
    object PdfViewer : Screen("pdf_viewer?uri={uri}&name={name}") {
        fun createRoute(uri: String, name: String): String {
            return "pdf_viewer?uri=$uri&name=$name"
        }
    }
    object Merge : Screen("merge")
    object Split : Screen("split")
    object Compress : Screen("compress")
    object Convert : Screen("convert")
    object PdfToImage : Screen("pdf_to_image")
    object Extract : Screen("extract")
    object Rotate : Screen("rotate")
    object Security : Screen("security")
    object Metadata : Screen("metadata")
    object PageNumber : Screen("page_number")
    object Organize : Screen("organize")
    object Unlock : Screen("unlock")
    object Repair : Screen("repair")
    object HtmlToPdf : Screen("html_to_pdf")
    object ExtractText : Screen("extract_text")
    
    // New Feature Screens
    object Watermark : Screen("watermark")
    object Flatten : Screen("flatten")
    object SignPdf : Screen("sign_pdf")
    object FillForms : Screen("fill_forms")
    object Annotate : Screen("annotate")
    object ScanToPdf : Screen("scan_to_pdf")
    object Ocr : Screen("ocr")
    
    // Document Viewer for Office files
    object DocumentViewer : Screen("document_viewer?uri={uri}&name={name}") {
        fun createRoute(uri: String, name: String): String {
            return "document_viewer?uri=$uri&name=$name"
        }
    }
    
    companion object {
        /**
         * Returns the Screen object for a given feature title.
         * Used to navigate from HomeScreen feature cards.
         */
        fun fromFeatureTitle(title: String): Screen {
            return when (title) {
                "Merge PDFs" -> Merge
                "Split PDF" -> Split
                "Compress PDF" -> Compress
                "Images to PDF" -> Convert
                "PDF to Images" -> PdfToImage
                "Extract Pages" -> Extract
                "Rotate Pages" -> Rotate
                "Add Security" -> Security
                "View Metadata" -> Metadata
                "Page Numbers" -> PageNumber
                "Organize Pages" -> Organize
                "Unlock PDF" -> Unlock
                "Repair PDF" -> Repair
                "HTML to PDF" -> HtmlToPdf
                "Extract Text" -> ExtractText
                // New Features
                "Add Watermark" -> Watermark
                "Flatten PDF" -> Flatten
                "Sign PDF" -> SignPdf
                "Fill Forms" -> FillForms
                "Annotate PDF" -> Annotate
                "Scan to PDF" -> ScanToPdf
                "OCR" -> Ocr
                else -> Home
            }
        }
    }
}
