package com.example.simplenotes

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

// Storage helpers for Notes
fun saveNotesToFile(context: Context, notes: List<String>, fileName: String) {
    val file = File(context.filesDir, fileName)
    file.writeText(notes.joinToString("\n"))
}

fun loadNotesFromFile(context: Context, fileName: String): List<String> {
    val file = File(context.filesDir, fileName)
    return if (file.exists()) {
        val content = file.readText()
        if (content.isBlank()) emptyList() else content.split("\n")
    } else {
        emptyList()
    }
}

// Storage helpers for Custom PIN
fun savePinToFile(context: Context, pin: String) {
    val file = File(context.filesDir, "vault_pin.txt")
    file.writeText(pin)
}

fun loadPinFromFile(context: Context): String {
    val file = File(context.filesDir, "vault_pin.txt")
    return if (file.exists()) file.readText() else ""
}

@Composable
fun NotesScreen() {
    val context = LocalContext.current
    var noteText by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var editingNoteData by remember { mutableStateOf<String?>(null) }

    // Vault State Variables
    var isVaultOpen by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }
    var savedPin by remember { mutableStateOf(loadPinFromFile(context)) }
    var pinErrorMessage by remember { mutableStateOf(false) }

    // Two separate lists for public and hidden notes
    val publicNotes = remember { mutableStateListOf<String>().apply {
        addAll(loadNotesFromFile(context, "saved_notes.txt"))
    } }
    val hiddenNotes = remember { mutableStateListOf<String>().apply {
        addAll(loadNotesFromFile(context, "hidden_notes.txt"))
    } }

    val activeNotesList = if (isVaultOpen) hiddenNotes else publicNotes
    val activeFileName = if (isVaultOpen) "hidden_notes.txt" else "saved_notes.txt"

    val filteredNotes = activeNotesList.filter {
        it.contains(searchQuery, ignoreCase = true)
    }

    val pastelColors = listOf(
        Color(0xFFE3F2FD), Color(0xFFE8F5E9), Color(0xFFFFFDE7),
        Color(0xFFFCE4EC), Color(0xFFF3E5F5), Color(0xFFFFF3E0)
    )

    // Dynamic Password Entry Pop-up
    if (showPasswordDialog) {
        AlertDialog(
            onDismissRequest = {
                showPasswordDialog = false
                pinInput = ""
                pinErrorMessage = false
            },
            title = {
                Text(if (savedPin.isEmpty()) "Create a 4-Digit PIN" else "Enter Secret PIN")
            },
            text = {
                Column {
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { if (it.length <= 4) pinInput = it },
                        label = { Text("PIN") },
                        singleLine = true,
                        isError = pinErrorMessage
                    )
                    if (pinErrorMessage) {
                        Text("Incorrect PIN. Try again.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (savedPin.isEmpty()) {
                        if (pinInput.length == 4) {
                            savePinToFile(context, pinInput)
                            savedPin = pinInput
                            isVaultOpen = true
                            showPasswordDialog = false
                            pinInput = ""
                        }
                    } else {
                        if (pinInput == savedPin) {
                            isVaultOpen = true
                            showPasswordDialog = false
                            pinInput = ""
                            pinErrorMessage = false
                        } else {
                            pinErrorMessage = true
                            pinInput = ""
                        }
                    }
                }) {
                    Text(if (savedPin.isEmpty()) "Set PIN & Open" else "Unlock")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showPasswordDialog = false
                    pinInput = ""
                    pinErrorMessage = false
                }) { Text("Cancel") }
            }
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // UPDATED: Profile Header UI
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left side: Profile Icon + Greeting
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = "Profile Picture",
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(8.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Hi, Sudhakar!",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (isVaultOpen) "🔒 Secret Vault" else "My Notes",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isVaultOpen) MaterialTheme.colorScheme.primary else Color.Gray
                    )
                }
            }

            // Right side: Lock Icon
            IconButton(onClick = {
                if (isVaultOpen) {
                    isVaultOpen = false
                } else {
                    showPasswordDialog = true
                }
            }) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = "Vault Toggle",
                    tint = if (isVaultOpen) MaterialTheme.colorScheme.primary else Color.Gray
                )
            }
        }

        // Vault-only controls: Change PIN button
        if (isVaultOpen) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = {
                    savedPin = ""
                    showPasswordDialog = true
                }) {
                    Text("Change Vault PIN", color = MaterialTheme.colorScheme.secondary)
                }
            }
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("🔍 Search ${if (isVaultOpen) "hidden" else "public"} notes...") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = noteText,
            onValueChange = { noteText = it },
            label = { Text(if (editingNoteData != null) "Edit your note..." else "Enter your note here...") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                if (noteText.isNotBlank()) {
                    val timeStamp = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
                    val noteWithTimestamp = "$noteText | Saved at $timeStamp"

                    if (editingNoteData != null) {
                        val index = activeNotesList.indexOf(editingNoteData)
                        if (index != -1) activeNotesList[index] = noteWithTimestamp
                        editingNoteData = null
                    } else {
                        activeNotesList.add(noteWithTimestamp)
                    }

                    saveNotesToFile(context, activeNotesList, activeFileName)
                    noteText = ""
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isVaultOpen) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
            )
        ) {
            Text(if (editingNoteData != null) "Update Note" else "Save Note")
        }

        if (editingNoteData != null) {
            TextButton(onClick = { editingNoteData = null; noteText = "" }) {
                Text("Cancel Edit", color = MaterialTheme.colorScheme.secondary)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Saved Notes:", style = MaterialTheme.typography.titleMedium)
            if (activeNotesList.isNotEmpty()) {
                TextButton(onClick = {
                    activeNotesList.clear()
                    saveNotesToFile(context, activeNotesList, activeFileName)
                }) {
                    Text("Clear All", color = MaterialTheme.colorScheme.error)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (filteredNotes.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (searchQuery.isEmpty()) "📝 No notes saved here yet!" else "🔍 No matching notes found!",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                items(filteredNotes) { noteData ->
                    val parts = noteData.split(" | ")
                    val noteContent = parts.getOrNull(0) ?: ""
                    val noteTime = parts.getOrNull(1) ?: ""

                    val colorIndex = abs(noteData.hashCode()) % pastelColors.size
                    val cardBackgroundColor = pastelColors[colorIndex]

                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = cardBackgroundColor)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = noteContent,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Color.Black
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

                            Row {
                                IconButton(onClick = {
                                    editingNoteData = noteData
                                    noteText = noteContent
                                }) {
                                    Icon(Icons.Filled.Edit, "Edit Note", tint = MaterialTheme.colorScheme.secondary)
                                }

                                IconButton(onClick = {
                                    val sendIntent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(Intent.EXTRA_TEXT, noteContent)
                                        type = "text/plain"
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, "Share note via..."))
                                }) {
                                    Icon(Icons.Filled.Share, "Share Note", tint = MaterialTheme.colorScheme.primary)
                                }

                                IconButton(onClick = {
                                    activeNotesList.remove(noteData)
                                    saveNotesToFile(context, activeNotesList, activeFileName)
                                }) {
                                    Icon(Icons.Filled.Delete, "Delete Note", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}