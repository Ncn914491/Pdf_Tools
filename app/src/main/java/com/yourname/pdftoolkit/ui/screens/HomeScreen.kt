package com.yourname.pdftoolkit.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yourname.pdftoolkit.ui.navigation.Screen

/**
 * Home screen displaying all available PDF tools as cards.
 * Each card navigates to its respective tool screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToFeature: (Screen) -> Unit
) {
    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = "PDF Toolkit",
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            itemsIndexed(
                items = pdfFeatures,
                key = { _, feature -> feature.title }
            ) { index, feature ->
                var isVisible by remember { mutableStateOf(false) }
                
                LaunchedEffect(Unit) {
                    kotlinx.coroutines.delay(index * 50L)
                    isVisible = true
                }
                
                val scale by animateFloatAsState(
                    targetValue = if (isVisible) 1f else 0.8f,
                    animationSpec = tween(durationMillis = 300),
                    label = "card_scale"
                )
                
                FeatureCard(
                    feature = feature,
                    onClick = {
                        val screen = Screen.fromFeatureTitle(feature.title)
                        onNavigateToFeature(screen)
                    },
                    modifier = Modifier.scale(scale)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeatureCard(
    feature: PdfFeature,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = feature.icon,
                    contentDescription = feature.title,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier
                        .padding(12.dp)
                        .size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = feature.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = feature.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Navigate",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Data class representing a PDF tool feature.
 */
data class PdfFeature(
    val title: String,
    val description: String,
    val icon: ImageVector
)

/**
 * List of all available PDF tools.
 */
val pdfFeatures = listOf(
    PdfFeature(
        title = "Merge PDFs",
        description = "Combine multiple PDF files into one",
        icon = Icons.Default.MergeType
    ),
    PdfFeature(
        title = "Split PDF",
        description = "Split a PDF into multiple files",
        icon = Icons.Default.CallSplit
    ),
    PdfFeature(
        title = "Compress PDF",
        description = "Reduce PDF file size",
        icon = Icons.Default.Compress
    ),
    PdfFeature(
        title = "Convert Images",
        description = "Convert images to PDF",
        icon = Icons.Default.Image
    ),
    PdfFeature(
        title = "Extract Pages",
        description = "Extract specific pages from PDF",
        icon = Icons.Default.ContentCopy
    ),
    PdfFeature(
        title = "Rotate Pages",
        description = "Rotate PDF pages",
        icon = Icons.Default.RotateRight
    ),
    PdfFeature(
        title = "Add Security",
        description = "Password protect your PDFs",
        icon = Icons.Default.Security
    ),
    PdfFeature(
        title = "View Metadata",
        description = "View and edit PDF properties",
        icon = Icons.Default.Info
    )
)
