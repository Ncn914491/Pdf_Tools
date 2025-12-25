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
import com.yourname.pdftoolkit.ui.screens.ExtractScreen
import com.yourname.pdftoolkit.ui.screens.RotateScreen
import com.yourname.pdftoolkit.ui.screens.SecurityScreen
import com.yourname.pdftoolkit.ui.screens.MetadataScreen

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
    }
}
