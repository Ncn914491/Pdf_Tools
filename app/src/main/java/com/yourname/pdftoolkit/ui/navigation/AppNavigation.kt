package com.yourname.pdftoolkit.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.yourname.pdftoolkit.ui.screens.HomeScreen
import com.yourname.pdftoolkit.ui.screens.MergeScreen
import com.yourname.pdftoolkit.ui.screens.SplitScreen
import com.yourname.pdftoolkit.ui.screens.CompressScreen
import com.yourname.pdftoolkit.ui.screens.ConvertScreen
import com.yourname.pdftoolkit.ui.screens.PdfToImageScreen
import com.yourname.pdftoolkit.ui.screens.ExtractScreen
import com.yourname.pdftoolkit.ui.screens.RotateScreen
import com.yourname.pdftoolkit.ui.screens.SecurityScreen
import com.yourname.pdftoolkit.ui.screens.MetadataScreen
import com.yourname.pdftoolkit.ui.screens.PageNumberScreen
import com.yourname.pdftoolkit.ui.screens.OrganizeScreen
import com.yourname.pdftoolkit.ui.screens.UnlockScreen
import com.yourname.pdftoolkit.ui.screens.RepairScreen
import com.yourname.pdftoolkit.ui.screens.HtmlToPdfScreen
import com.yourname.pdftoolkit.ui.screens.ExtractTextScreen
// New Feature Screens
import com.yourname.pdftoolkit.ui.screens.WatermarkScreen
import com.yourname.pdftoolkit.ui.screens.FlattenScreen
import com.yourname.pdftoolkit.ui.screens.SignPdfScreen
import com.yourname.pdftoolkit.ui.screens.FillFormsScreen
import com.yourname.pdftoolkit.ui.screens.AnnotationScreen
import com.yourname.pdftoolkit.ui.screens.ScanToPdfScreen
import com.yourname.pdftoolkit.ui.screens.OcrScreen

/**
 * Main navigation graph for the PDF Toolkit app.
 * Defines all navigation destinations and their composables.
 */
@Composable
fun AppNavigation(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = modifier
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToFeature = { screen ->
                    navController.navigate(screen.route)
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
    }
}
