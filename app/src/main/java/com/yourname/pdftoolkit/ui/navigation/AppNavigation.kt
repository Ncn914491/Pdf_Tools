package com.yourname.pdftoolkit.ui.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.yourname.pdftoolkit.ui.screens.*

/**
 * Main navigation graph for the PDF Toolkit app.
 * Defines all navigation destinations and their composables.
 */
@Composable
fun AppNavigation(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    startDestination: String = Screen.Home.route,
    initialPdfUri: Uri? = null,
    initialPdfName: String? = null,
    initialDocumentUri: Uri? = null,
    initialDocumentName: String? = null
) {
    val actualStartDestination = when {
        initialPdfUri != null -> "pdf_viewer_direct"
        initialDocumentUri != null -> "document_viewer_direct"
        else -> startDestination
    }
    
    NavHost(
        navController = navController,
        startDestination = actualStartDestination,
        modifier = modifier
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToFeature = { screen ->
                    navController.navigate(screen.route)
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                },
                onOpenPdfViewer = { uri, name ->
                    val encodedUri = Uri.encode(uri.toString())
                    val encodedName = Uri.encode(name)
                    navController.navigate(Screen.PdfViewer.createRoute(encodedUri, encodedName))
                },
                onOpenDocumentViewer = { uri, name ->
                    val encodedUri = Uri.encode(uri.toString())
                    val encodedName = Uri.encode(name)
                    navController.navigate(Screen.DocumentViewer.createRoute(encodedUri, encodedName))
                }
            )
        }
        
        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        // PDF Viewer with URI parameters
        composable(
            route = Screen.PdfViewer.route,
            arguments = listOf(
                navArgument("uri") { 
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("name") { 
                    type = NavType.StringType
                    defaultValue = "PDF Document"
                }
            )
        ) { backStackEntry ->
            val uriString = backStackEntry.arguments?.getString("uri") ?: ""
            val name = backStackEntry.arguments?.getString("name") ?: "PDF Document"
            val uri = if (uriString.isNotEmpty()) Uri.parse(Uri.decode(uriString)) else null
            
            PdfViewerScreen(
                pdfUri = uri,
                pdfName = Uri.decode(name),
                onNavigateBack = { navController.popBackStack() },
                onNavigateToTool = { tool ->
                    when (tool) {
                        "compress" -> navController.navigate(Screen.Compress.route)
                        "watermark" -> navController.navigate(Screen.Watermark.route)
                        else -> {}
                    }
                }
            )
        }
        
        // Direct PDF viewer for intent handling
        composable("pdf_viewer_direct") {
            PdfViewerScreen(
                pdfUri = initialPdfUri,
                pdfName = initialPdfName ?: "PDF Document",
                onNavigateBack = { 
                    navController.navigate(Screen.Home.route) {
                        popUpTo("pdf_viewer_direct") { inclusive = true }
                    }
                },
                onNavigateToTool = { tool ->
                    when (tool) {
                        "compress" -> navController.navigate(Screen.Compress.route)
                        "watermark" -> navController.navigate(Screen.Watermark.route)
                        else -> {}
                    }
                }
            )
        }
        
        composable(Screen.Merge.route) {
            MergeScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        composable(Screen.Split.route) {
            SplitScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        composable(Screen.Compress.route) {
            CompressScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        composable(Screen.Convert.route) {
            ConvertScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        composable(Screen.PdfToImage.route) {
            PdfToImageScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        composable(Screen.Extract.route) {
            ExtractScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        composable(Screen.Rotate.route) {
            RotateScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        composable(Screen.Security.route) {
            SecurityScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        composable(Screen.Metadata.route) {
            MetadataScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        composable(Screen.PageNumber.route) {
            PageNumberScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        composable(Screen.Organize.route) {
            OrganizeScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        composable(Screen.Unlock.route) {
            UnlockScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        composable(Screen.Repair.route) {
            RepairScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        composable(Screen.HtmlToPdf.route) {
            HtmlToPdfScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        composable(Screen.ExtractText.route) {
            ExtractTextScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        // New Feature Screens
        composable(Screen.Watermark.route) {
            WatermarkScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        composable(Screen.Flatten.route) {
            FlattenScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        composable(Screen.SignPdf.route) {
            SignPdfScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        composable(Screen.FillForms.route) {
            FillFormsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        composable(Screen.Annotate.route) {
            AnnotationScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        composable(Screen.ScanToPdf.route) {
            ScanToPdfScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        composable(Screen.Ocr.route) {
            OcrScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        // Document Viewer for Office files (DOCX, XLSX, PPTX)
        composable(
            route = Screen.DocumentViewer.route,
            arguments = listOf(
                navArgument("uri") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("name") {
                    type = NavType.StringType
                    defaultValue = "Document"
                }
            )
        ) { backStackEntry ->
            val uriString = backStackEntry.arguments?.getString("uri") ?: ""
            val name = backStackEntry.arguments?.getString("name") ?: "Document"
            val uri = if (uriString.isNotEmpty()) Uri.parse(Uri.decode(uriString)) else null
            
            DocumentViewerScreen(
                documentUri = uri,
                documentName = Uri.decode(name),
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        // Direct document viewer for intent handling
        composable("document_viewer_direct") {
            DocumentViewerScreen(
                documentUri = initialDocumentUri,
                documentName = initialDocumentName ?: "Document",
                onNavigateBack = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo("document_viewer_direct") { inclusive = true }
                    }
                }
            )
        }
    }
}
