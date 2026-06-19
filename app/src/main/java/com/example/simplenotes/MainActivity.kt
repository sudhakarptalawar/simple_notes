package com.example.simplenotes

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.simplenotes.ui.theme.SimpleNotesTheme
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SimpleNotesTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        NotesScreen()
                    }
                }
            }
        }
    }
}

// Storage helpers
fun saveNotesToFile(context: Context, notes: List<String>) {
    val file = File(context.filesDir, "saved_notes.txt")
    file.writeText(notes.joinToString("\n"))
}

fun loadNotesFromFile(context: Context): List<String> {
    val file = File(context.filesDir, "saved_notes.txt")
    return if (file.exists()) {
        val content = file.readText()
        if (content.isBlank()) emptyList() else content.split("\n")
    } else {
        emptyList()
    }
}

@Composable
fun NotesScreen() {
    val context = LocalContext.current
    var noteText by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }

    val notesList = remember { mutableStateListOf<String>().apply {
        addAll(loadNotesFromFile(context))
    } }

    val filteredNotes = notesList.filter {
        it.contains(searchQuery, ignoreCase = true)
    }

    // A list of lovely material pastel colors for our cards
    val pastelColors = listOf(
        Color(0xFFE3F2FD), // Light Blue
        Color(0xFFE8F5E9), // Light Green
        Color(0xFFFFFDE7), // Light Yellow
        Color(0xFFFCE4EC), // Light Pink
        Color(0xFFF3E5F5), // Light Purple
        Color(0xFFFFF3E0)  // Light Orange
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "My Notes App",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("🔍 Search notes...") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Input Field
        OutlinedTextField(
            value = noteText,
            onValueChange = { noteText = it },
            label = { Text("Enter your note here...") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Save Button
        Button(
            onClick = {
                if (noteText.isNotBlank()) {
                    val timeStamp = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
                    val noteWithTimestamp = "$noteText | Saved at $timeStamp"

                    notesList.add(noteWithTimestamp)
                    saveNotesToFile(context, notesList)
                    noteText = ""
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save Note")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Header Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Saved Notes:",
                style = MaterialTheme.typography.titleMedium
            )

            if (notesList.isNotEmpty()) {
                TextButton(onClick = {
                    notesList.clear()
                    saveNotesToFile(context, notesList)
                }) {
                    Text("Clear All", color = MaterialTheme.colorScheme.error)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Dynamic State
        if (filteredNotes.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (searchQuery.isEmpty()) "📝 No notes saved yet!" else "🔍 No matching notes found!",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f)
            ) {
                items(filteredNotes) { noteData ->
                    val parts = noteData.split(" | ")
                    val noteContent = parts.getOrNull(0) ?: ""
                    val noteTime = parts.getOrNull(1) ?: ""

                    // Deterministically pick a color based on the note text string hash
                    // This ensures a note keeps its color even when you close and reopen the app!
                    val colorIndex = abs(noteData.hashCode()) % pastelColors.size
                    val cardBackgroundColor = pastelColors[colorIndex]

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        // Here we apply our new custom color background!
                        colors = CardDefaults.cardColors(
                            containerColor = cardBackgroundColor
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = noteContent,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Color.Black // Explicit contrast against light pastels
                                )
                                if (noteTime.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = noteTime,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.DarkGray
                                    )
                                }
                            }

                            IconButton(onClick = {
                                notesList.remove(noteData)
                                saveNotesToFile(context, notesList)
                            }) {
                                Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = "Delete Note",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}