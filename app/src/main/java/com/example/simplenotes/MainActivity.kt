package com.example.simplenotes

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
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
import com.google.gson.Gson
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

// --- JSON-FRIENDLY DATA ARCHITECTURE ---
data class NoteBlock(val type: String, var content: String)

data class Note(
    val id: String = UUID.randomUUID().toString(),
    var title: String = "",
    var timestamp: Long = System.currentTimeMillis(),
    var isPinned: Boolean = false,
    var blocks: MutableList<NoteBlock> = mutableListOf(NoteBlock(type = "TEXT", content = ""))
)
// ---------------------------------------

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SimpleNotesTheme(darkTheme = false) {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        // --- NAVIGATION STATE ---
                        // This decides if we are looking at the swiping pages, or reading a specific note!
                        var viewingNote by remember { mutableStateOf<Note?>(null) }
                        var noteToEdit by remember { mutableStateOf<Note?>(null) }

                        if (viewingNote != null) {
                            ViewNoteScreen(
                                note = viewingNote!!,
                                onBack = { viewingNote = null },
                                onEdit = {
                                    noteToEdit = viewingNote
                                    viewingNote = null
                                },
                                onDelete = { note ->
                                    val prefs = getSharedPreferences("NotesVault", Context.MODE_PRIVATE)
                                    prefs.edit().remove(note.id).apply()
                                    Toast.makeText(this@MainActivity, "Note Deleted", Toast.LENGTH_SHORT).show()
                                    viewingNote = null
                                }
                            )
                        } else {
                            MainAppPager(
                                noteToEdit = noteToEdit,
                                onClearEdit = { noteToEdit = null },
                                onNoteClick = { clickedNote -> viewingNote = clickedNote }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainAppPager(noteToEdit: Note?, onClearEdit: () -> Unit, onNoteClick: (Note) -> Unit) {
    val pagerState = rememberPagerState(pageCount = { 2 })
    val coroutineScope = rememberCoroutineScope()

    // If the user clicked "Edit", slide them automatically to Page 0!
    LaunchedEffect(noteToEdit) {
        if (noteToEdit != null) {
            pagerState.animateScrollToPage(0)
        }
    }

    HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
        when (page) {
            0 -> CreateNoteScreen(
                incomingNote = noteToEdit,
                onNoteSaved = { onClearEdit() }
            )
            1 -> SavedNotesScreen(
                currentPage = pagerState.currentPage,
                onNoteClick = onNoteClick,
                onNewNoteClick = {
                    onClearEdit()
                    coroutineScope.launch { pagerState.animateScrollToPage(0) }
                }
            )
        }
    }
}

// ==========================================
// THE NEW "READING MODE" SCREEN (From your sketch!)
// ==========================================
@Composable
fun ViewNoteScreen(note: Note, onBack: () -> Unit, onEdit: () -> Unit, onDelete: (Note) -> Unit) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFFFDE7)) // Soft pastel yellow for reading mode
            .padding(16.dp)
            .padding(top = 24.dp) // Extra padding for status bar
    ) {
        // --- TOP BAR WITH 3-DOT MENU ---
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, "Back")
                }
                Icon(Icons.Filled.Person, "Profile", tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (note.title.isNotBlank()) note.title else "Untitled Note", style = MaterialTheme.typography.titleLarge)
            }

            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Filled.MoreVert, "Options")
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(text = { Text("Edit") }, onClick = { showMenu = false; onEdit() })
                    DropdownMenuItem(text = { Text("Share") }, onClick = {
                        showMenu = false
                        // Combine all text blocks to share
                        val textToShare = note.blocks.filter { it.type == "TEXT" }.joinToString("\n\n") { it.content }
                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, "${note.title}\n\n$textToShare")
                            type = "text/plain"
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "Share note via..."))
                    })
                    DropdownMenuItem(text = { Text("Delete", color = Color.Red) }, onClick = { showMenu = false; onDelete(note) })
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // --- READ-ONLY BLOCKS LIST ---
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(note.blocks) { block ->
                if (block.type == "TEXT" && block.content.isNotBlank()) {
                    Text(
                        text = block.content,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                } else if (block.type == "IMAGE") {
                    val bitmap = try {
                        val uri = Uri.parse(block.content)
                        BitmapFactory.decodeStream(context.contentResolver.openInputStream(uri))
                    } catch (e: Exception) { null }

                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Attached Image",
                            modifier = Modifier.fillMaxWidth().height(250.dp).padding(vertical = 12.dp).clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// PAGE 1: CREATE / EDIT SCREEN
// ==========================================
@Composable
fun CreateNoteScreen(incomingNote: Note?, onNoteSaved: () -> Unit) {
    val context = LocalContext.current

    // Use the incoming note's ID if we are editing, otherwise generate a new one
    var currentNoteId by remember { mutableStateOf(UUID.randomUUID().toString()) }
    var title by remember { mutableStateOf("") }
    val blocks = remember { mutableStateListOf<NoteBlock>(NoteBlock("TEXT", "")) }

    // If an incoming note arrives (User clicked Edit), load its data into the boxes!
    LaunchedEffect(incomingNote) {
        if (incomingNote != null) {
            currentNoteId = incomingNote.id
            title = incomingNote.title
            blocks.clear()
            incomingNote.blocks.forEach { blocks.add(NoteBlock(it.type, it.content)) }
        } else {
            // Reset for a brand new note
            currentNoteId = UUID.randomUUID().toString()
            title = ""
            blocks.clear()
            blocks.add(NoteBlock("TEXT", ""))
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri: Uri? ->
            uri?.let {
                context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                blocks.add(NoteBlock("IMAGE", it.toString()))
                blocks.add(NoteBlock("TEXT", ""))
            }
        }
    )

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(16.dp).padding(top = 24.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Person, "Profile", modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (incomingNote != null) "Editing Note..." else "Hi Sudhakar", style = MaterialTheme.typography.titleMedium)
            }
            IconButton(onClick = { /* Settings later */ }) {
                Icon(Icons.Filled.Settings, "Settings", tint = Color.Gray)
            }
        }

        OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Enter Title") }, modifier = Modifier.fillMaxWidth(), singleLine = true, textStyle = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
            itemsIndexed(blocks) { index, block ->
                if (block.type == "TEXT") {
                    OutlinedTextField(value = block.content, onValueChange = { newText -> blocks[index] = blocks[index].copy(content = newText) }, placeholder = { Text(if (index == 0) "Start notes writing from here..." else "Again continue...") }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
                } else if (block.type == "IMAGE") {
                    val bitmap = try { BitmapFactory.decodeStream(context.contentResolver.openInputStream(Uri.parse(block.content))) } catch (e: Exception) { null }
                    if (bitmap != null) {
                        Image(bitmap = bitmap.asImageBitmap(), contentDescription = "Attached Image", modifier = Modifier.fillMaxWidth().height(200.dp).padding(vertical = 8.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                    }
                }
            }

            item {
                Box(modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 32.dp), contentAlignment = Alignment.Center) {
                    Button(
                        onClick = {
                            val noteToSave = Note(id = currentNoteId, title = title, blocks = blocks.toMutableList())
                            val jsonString = Gson().toJson(noteToSave)
                            context.getSharedPreferences("NotesVault", Context.MODE_PRIVATE).edit().putString(noteToSave.id, jsonString).apply()

                            title = ""
                            blocks.clear()
                            blocks.add(NoteBlock("TEXT", ""))
                            currentNoteId = UUID.randomUUID().toString()
                            onNoteSaved() // Clear edit state
                            Toast.makeText(context, "Note Saved! Swipe Right to view.", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                    ) {
                        Icon(Icons.Filled.Check, "Save Note")
                        Spacer(Modifier.width(8.dp))
                        Text(if (incomingNote != null) "Update Note" else "Save Note")
                    }
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
            Button(onClick = { imagePickerLauncher.launch(arrayOf("image/*")) }) {
                Icon(Icons.Filled.Add, "Add Image")
                Spacer(Modifier.width(8.dp))
                Text("Add Image")
            }
        }
    }
}

// ==========================================
// PAGE 2: SAVED NOTES SCREEN
// ==========================================
@Composable
fun SavedNotesScreen(currentPage: Int, onNoteClick: (Note) -> Unit, onNewNoteClick: () -> Unit) {
    val context = LocalContext.current
    val sharedPreferences = context.getSharedPreferences("NotesVault", Context.MODE_PRIVATE)
    var savedNotes by remember { mutableStateOf<List<Note>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(currentPage) {
        if (currentPage == 1) {
            val notesList = mutableListOf<Note>()
            val allEntries = sharedPreferences.all
            for ((_, value) in allEntries) {
                if (value is String) {
                    try { notesList.add(Gson().fromJson(value, Note::class.java)) } catch (e: Exception) { }
                }
            }
            savedNotes = notesList.sortedByDescending { it.timestamp }
        }
    }

    Scaffold(
        floatingActionButton = { FloatingActionButton(onClick = onNewNoteClick, containerColor = MaterialTheme.colorScheme.primary) { Icon(Icons.Filled.Add, "Create New Note") } }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues).background(Color(0xFFE3F2FD)).padding(16.dp).padding(top = 24.dp)) {
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Person, "Profile", modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Hi Sudhakar", style = MaterialTheme.typography.titleMedium)
                }
            }

            OutlinedTextField(value = searchQuery, onValueChange = { searchQuery = it }, label = { Text("🔍 Search notes...") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = Color.White, focusedContainerColor = Color.White))
            Spacer(modifier = Modifier.height(16.dp))

            val filteredNotes = savedNotes.filter { note -> note.title.contains(searchQuery, ignoreCase = true) || note.blocks.any { it.type == "TEXT" && it.content.contains(searchQuery, ignoreCase = true) } }

            if (filteredNotes.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Text("No saved notes found.", color = Color.Gray) }
            } else {
                LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {

                    items(filteredNotes) { note ->
                        val previewText = note.blocks.firstOrNull { it.type == "TEXT" && it.content.isNotBlank() }?.content ?: "No text preview..."
                        val dateString = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(note.timestamp))

                        // --- THE CARDS ARE NOW CLICKABLE! ---
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .clickable { onNoteClick(note) }, // Triggers the View Screen
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(if (note.title.isNotBlank()) note.title else "Untitled Note", style = MaterialTheme.typography.titleMedium, color = Color.Black)
                                    Text(dateString, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "v2.1.0 • Designed by Sudhakar",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.LightGray,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}