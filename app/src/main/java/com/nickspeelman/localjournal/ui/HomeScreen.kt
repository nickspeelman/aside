package com.nickspeelman.localjournal.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun HomeScreen(summary: MoodSummary) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Mood Home",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 24.dp)
        )
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = "Average Mood", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "%.1f".format(summary.averageRating),
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(text = "out of 5", style = MaterialTheme.typography.bodySmall)
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "Top Hashtags",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.align(Alignment.Start).padding(bottom = 8.dp)
        )
        
        if (summary.topHashtags.isEmpty()) {
            Text(text = "No hashtags recorded yet.", style = MaterialTheme.typography.bodyMedium)
        } else {
            summary.topHashtags.forEach { (tag, count) ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = tag, style = MaterialTheme.typography.bodyLarge)
                    Text(text = "$count entries", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
