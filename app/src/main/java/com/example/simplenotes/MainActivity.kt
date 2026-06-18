package com.example.simplenotes

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
import androidx.compose.ui.unit.dp
import com.example.simplenotes.ui.theme.SimpleNotesTheme

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

@Composable
fun NotesScreen() {
    var noteText by remember { mutableStateOf("") }
    val notesList = remember { mutableStateListOf<String>() }

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

        // Input Field for typing notes
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
                    notesList.add(noteText)
                    noteText = ""
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save Note")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Header Row with "Saved Notes" and the new "Clear All" Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Saved Notes:",
                style = MaterialTheme.typography.titleMedium
            )

            // Only show "Clear All" if there are actually notes in the list
            if (notesList.isNotEmpty()) {
                TextButton(onClick = { notesList.clear() }) {
                    Text("Clear All", color = MaterialTheme.colorScheme.error)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Dynamic State: Show placeholder text if empty, otherwise show scrollable list
        if (notesList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "📝 No notes saved yet!",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            // Scrollable List to display all notes
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f)
            ) {
                items(notesList) { note ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = note,
                                modifier = Modifier.weight(1f)
                            )

                            IconButton(onClick = { notesList.remove(note) }) {
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