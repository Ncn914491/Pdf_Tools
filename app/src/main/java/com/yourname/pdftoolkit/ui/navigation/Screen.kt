package com.yourname.pdftoolkit.ui.navigation

/**
 * Sealed class representing all navigation destinations in the app.
 * Each screen has a unique route string for navigation.
 */
sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Merge : Screen("merge")
    object Split : Screen("split")
    object Compress : Screen("compress")
    object Convert : Screen("convert")
    object Extract : Screen("extract")
    object Rotate : Screen("rotate")
    object Security : Screen("security")
    object Metadata : Screen("metadata")
    
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
                "Convert Images" -> Convert
                "Extract Pages" -> Extract
                "Rotate Pages" -> Rotate
                "Add Security" -> Security
                "View Metadata" -> Metadata
                else -> Home
            }
        }
    }
}
