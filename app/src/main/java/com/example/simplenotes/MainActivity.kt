package com.example.simplenotes

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
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
            var isDarkMode by remember { mutableStateOf(false) }

            SimpleNotesTheme(darkTheme = isDarkMode) {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        NotesScreen(isDarkMode = isDarkMode, onThemeToggle = { isDarkMode = it })
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
fun NotesScreen(isDarkMode: Boolean, onThemeToggle: (Boolean) -> Unit) {
    val context = LocalContext.current
    var noteText by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var editingNoteData by remember { mutableStateOf<String?>(null) }
    var attachedImageUri by remember { mutableStateOf("") }

    // Vault State Variables
    var isVaultOpen by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }
    var savedPin by remember { mutableStateOf(loadPinFromFile(context)) }
    var pinErrorMessage by remember { mutableStateOf(false) }

    // Image Picker Launcher requesting persistent permissions
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri: Uri? ->
            uri?.let {
                context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                attachedImageUri = it.toString()
            }
        }
    )

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
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
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

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { onThemeToggle(!isDarkMode) },
                    modifier = Modifier.padding(end = 4.dp)
                ) {
                    Text(text = if (isDarkMode) "🌙" else "☀️", style = MaterialTheme.typography.headlineSmall)
                }

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
        }

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

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = noteText,
                onValueChange = { noteText = it },
                label = { Text(if (editingNoteData != null) "Edit your note..." else "Enter your note here...") },
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { imagePickerLauncher.launch(arrayOf("image/*")) }) {
                Icon(Icons.Filled.AddCircle, contentDescription = "Attach Image", tint = MaterialTheme.colorScheme.primary)
            }
        }

        if (attachedImageUri.isNotEmpty()) {
            Text("📷 Image attached and ready to save!", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.align(Alignment.Start).padding(start = 8.dp, top = 4.dp))
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                if (noteText.isNotBlank() || attachedImageUri.isNotBlank()) {
                    val timeStamp = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
                    val noteWithTimestamp = "$noteText | Saved at $timeStamp | $attachedImageUri"

                    if (editingNoteData != null) {
                        val index = activeNotesList.indexOf(editingNoteData)
                        if (index != -1) activeNotesList[index] = noteWithTimestamp
                        editingNoteData = null
                    } else {
                        activeNotesList.add(noteWithTimestamp)
                    }

                    saveNotesToFile(context, activeNotesList, activeFileName)
                    noteText = ""
                    attachedImageUri = ""
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
            TextButton(onClick = {
                editingNoteData = null
                noteText = ""
                attachedImageUri = ""
            }) {
                Text("Cancel Edit", color = MaterialTheme.colorScheme.secondary)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Saved Notes:", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
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
                    val noteImage = parts.getOrNull(2) ?: ""

                    val colorIndex = abs(noteData.hashCode()) % pastelColors.size

                    val cardBackgroundColor = if (isDarkMode) Color(0xFF2C2C2C) else pastelColors[colorIndex]
                    val cardTextColor = if (isDarkMode) Color.White else Color.Black
                    val timeTextColor = if (isDarkMode) Color.LightGray else Color.DarkGray

                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = cardBackgroundColor)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    if (noteContent.isNotEmpty()) {
                                        Text(
                                            text = noteContent,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = cardTextColor
                                        )
                                    }
                                    if (noteTime.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = noteTime,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = timeTextColor
                                        )
                                    }
                                }

                                Row {
                                    IconButton(onClick = {
                                        editingNoteData = noteData
                                        noteText = noteContent
                                        attachedImageUri = noteImage
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

                            // UPDATED LOGIC: No Composables inside the try-catch block!
                            if (noteImage.isNotEmpty()) {
                                // 1. Attempt to decode the image safely first
                                val bitmap = try {
                                    val uri = Uri.parse(noteImage)
                                    val inputStream = context.contentResolver.openInputStream(uri)
                                    BitmapFactory.decodeStream(inputStream)
                                } catch (e: Exception) {
                                    null
                                }

                                // 2. Display the UI based on the result
                                if (bitmap != null) {
                                    Image(
                                        bitmap = bitmap.asImageBitmap(),
                                        contentDescription = "Attached Note Image",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(200.dp)
                                            .padding(top = 12.dp)
                                            .clip(RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Text(
                                        text = "⚠️ Image removed or unavailable",
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(top = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}