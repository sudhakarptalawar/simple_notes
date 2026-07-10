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
import androidx.compose.material.icons.filled.*
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

// --- DATA ARCHITECTURE ---
data class NoteBlock(val type: String, var content: String)

data class Note(
    val id: String = UUID.randomUUID().toString(),
    var title: String = "",
    var timestamp: Long = System.currentTimeMillis(),
    var isPinned: Boolean = false,
    var blocks: MutableList<NoteBlock> = mutableListOf(NoteBlock(type = "TEXT", content = ""))
)
// -------------------------

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalFoundationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SimpleNotesTheme(darkTheme = false) {
                val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                val scope = rememberCoroutineScope()

                // Fixed: Starts on Page 0 (The Editor/Write screen) exactly as it originally did
                val pagerState = rememberPagerState(pageCount = { 2 }, initialPage = 0)

                var viewingNote by remember { mutableStateOf<Note?>(null) }
                var noteToEdit by remember { mutableStateOf<Note?>(null) }

                LaunchedEffect(noteToEdit) {
                    if (noteToEdit != null) pagerState.animateScrollToPage(0)
                }

                ModalNavigationDrawer(
                    drawerState = drawerState,
                    gesturesEnabled = true,
                    drawerContent = {
                        ModalDrawerSheet(modifier = Modifier.width(280.dp), drawerContainerColor = Color.White) {
                            Spacer(modifier = Modifier.height(32.dp))
                            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Person, "Profile", modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("Hi Sudhakar", style = MaterialTheme.typography.titleLarge)
                            }
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

                            NavigationDrawerItem(icon = { Icon(Icons.Filled.Star, "Starred") }, label = { Text("Starred") }, selected = false, onClick = { })
                            NavigationDrawerItem(icon = { Icon(Icons.Filled.Delete, "Bin") }, label = { Text("Bin") }, selected = false, onClick = { })
                            NavigationDrawerItem(icon = { Icon(Icons.Filled.Lock, "Locked Folder") }, label = { Text("Locked Folder") }, selected = false, onClick = { })
                            NavigationDrawerItem(icon = { Icon(Icons.Filled.Cloud, "Cloud") }, label = { Text("Cloud") }, selected = false, onClick = { })
                            NavigationDrawerItem(icon = { Icon(Icons.Filled.List, "All Notes") }, label = { Text("All Notes") }, selected = true, onClick = { scope.launch { drawerState.close() } })
                            NavigationDrawerItem(icon = { Icon(Icons.Filled.Info, "Help") }, label = { Text("Help and feedback") }, selected = false, onClick = { })

                            Spacer(modifier = Modifier.weight(1f))
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            Text("v2.4.1 • Designed by Sudhakar", style = MaterialTheme.typography.labelMedium, color = Color.Gray, modifier = Modifier.padding(16.dp))
                        }
                    }
                ) {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        bottomBar = {
                            if (viewingNote == null) {
                                NavigationBar(containerColor = Color.White, tonalElevation = 8.dp) {
                                    // FIXED ORDER: Left button is Page 0 (Write)
                                    NavigationBarItem(
                                        icon = { Icon(Icons.Filled.Create, "Write") },
                                        label = { Text("Write") },
                                        selected = pagerState.currentPage == 0,
                                        onClick = { scope.launch { pagerState.animateScrollToPage(0) } }
                                    )
                                    // FIXED ORDER: Right button is Page 1 (Notes)
                                    NavigationBarItem(
                                        icon = { Icon(Icons.Filled.List, "Notes") },
                                        label = { Text("Notes") },
                                        selected = pagerState.currentPage == 1,
                                        onClick = { scope.launch { pagerState.animateScrollToPage(1) } }
                                    )
                                }
                            }
                        },
                        floatingActionButton = {
                            // "Compose" FAB only shows up when you are looking at your Notes list (Page 1)
                            if (pagerState.currentPage == 1 && viewingNote == null) {
                                ExtendedFloatingActionButton(
                                    onClick = {
                                        noteToEdit = null
                                        scope.launch { pagerState.animateScrollToPage(0) }
                                    },
                                    icon = { Icon(Icons.Filled.Create, "Compose") },
                                    text = { Text("Compose") },
                                    containerColor = Color(0xFFFFE0B2),
                                    contentColor = Color(0xFF5D4037)
                                )
                            }
                        }
                    ) { innerPadding ->
                        Box(modifier = Modifier.padding(innerPadding).fillMaxSize().background(MaterialTheme.colorScheme.background)) {

                            if (viewingNote != null) {
                                ViewNoteScreen(
                                    note = viewingNote!!,
                                    onBack = { viewingNote = null },
                                    onEdit = { noteToEdit = viewingNote; viewingNote = null },
                                    onDelete = { note ->
                                        getSharedPreferences("NotesVault", Context.MODE_PRIVATE).edit().remove(note.id).apply()
                                        Toast.makeText(this@MainActivity, "Note Deleted", Toast.LENGTH_SHORT).show()
                                        viewingNote = null
                                    }
                                )
                            } else {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                                Icon(Icons.Filled.Menu, "Menu", modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(if (noteToEdit != null) "Editing Note..." else "Hi Sudhakar", style = MaterialTheme.typography.titleMedium)
                                        }
                                        IconButton(onClick = { /* Settings */ }) {
                                            Icon(Icons.Filled.Settings, "Settings", tint = Color.Gray)
                                        }
                                    }

                                    HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                                        when (page) {
                                            // Page 0: The Editor (Write)
                                            0 -> CreateNoteScreen(
                                                incomingNote = noteToEdit,
                                                onNoteSaved = {
                                                    noteToEdit = null
                                                    scope.launch { pagerState.animateScrollToPage(1) }
                                                }
                                            )
                                            // Page 1: The Saved Notes List (Notes)
                                            1 -> SavedNotesScreen(
                                                currentPage = pagerState.currentPage,
                                                onNoteClick = { clickedNote -> viewingNote = clickedNote }
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
    }
}

// ==========================================
// PAGE 0: CREATE / EDIT SCREEN (Write)
// ==========================================
@Composable
fun CreateNoteScreen(incomingNote: Note?, onNoteSaved: () -> Unit) {
    val context = LocalContext.current
    var currentNoteId by remember { mutableStateOf(UUID.randomUUID().toString()) }
    var title by remember { mutableStateOf("") }
    val blocks = remember { mutableStateListOf<NoteBlock>(NoteBlock("TEXT", "")) }

    LaunchedEffect(incomingNote) {
        if (incomingNote != null) {
            currentNoteId = incomingNote.id; title = incomingNote.title; blocks.clear()
            incomingNote.blocks.forEach { blocks.add(NoteBlock(it.type, it.content)) }
        } else {
            currentNoteId = UUID.randomUUID().toString(); title = ""; blocks.clear(); blocks.add(NoteBlock("TEXT", ""))
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            blocks.add(NoteBlock("IMAGE", it.toString())); blocks.add(NoteBlock("TEXT", ""))
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Enter Title") }, modifier = Modifier.fillMaxWidth(), singleLine = true, textStyle = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
            itemsIndexed(blocks) { index, block ->
                if (block.type == "TEXT") {
                    OutlinedTextField(value = block.content, onValueChange = { newText -> blocks[index] = blocks[index].copy(content = newText) }, placeholder = { Text(if (index == 0) "Start notes writing from here..." else "Again continue...") }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
                } else if (block.type == "IMAGE") {
                    val bitmap = try { BitmapFactory.decodeStream(context.contentResolver.openInputStream(Uri.parse(block.content))) } catch (e: Exception) { null }
                    if (bitmap != null) { Image(bitmap = bitmap.asImageBitmap(), contentDescription = "Attached Image", modifier = Modifier.fillMaxWidth().height(200.dp).padding(vertical = 8.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop) }
                }
            }
        }

        Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.background) {
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { Toast.makeText(context, "Tools coming soon!", Toast.LENGTH_SHORT).show() }) {
                    Icon(Icons.Filled.Build, "Other Tools", tint = MaterialTheme.colorScheme.primary)
                }
                Row {
                    Button(onClick = { imagePickerLauncher.launch(arrayOf("image/*")) }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)) {
                        Icon(Icons.Filled.Add, "Add Image")
                        Spacer(Modifier.width(4.dp))
                        Text("Image")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val noteToSave = Note(id = currentNoteId, title = title, blocks = blocks.toMutableList())
                            context.getSharedPreferences("NotesVault", Context.MODE_PRIVATE).edit().putString(noteToSave.id, Gson().toJson(noteToSave)).apply()
                            title = ""; blocks.clear(); blocks.add(NoteBlock("TEXT", "")); currentNoteId = UUID.randomUUID().toString()
                            onNoteSaved(); Toast.makeText(context, "Note Saved!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                    ) {
                        Icon(Icons.Filled.Check, "Save Note")
                        Spacer(Modifier.width(4.dp))
                        Text(if (incomingNote != null) "Update" else "Save")
                    }
                }
            }
        }
    }
}

// ==========================================
// PAGE 1: SAVED NOTES SCREEN (Notes)
// ==========================================
@Composable
fun SavedNotesScreen(currentPage: Int, onNoteClick: (Note) -> Unit) {
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

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("🔍 Search notes...") },
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = Color.White, focusedContainerColor = Color.White)
            )

            val filteredNotes = savedNotes.filter { note -> note.title.contains(searchQuery, ignoreCase = true) || note.blocks.any { it.type == "TEXT" && it.content.contains(searchQuery, ignoreCase = true) } }

            if (filteredNotes.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Text("No saved notes found.", color = Color.Gray) }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(filteredNotes) { note ->
                        val previewText = note.blocks.firstOrNull { it.type == "TEXT" && it.content.isNotBlank() }?.content ?: "No text preview..."
                        val dateString = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(note.timestamp))

                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable { onNoteClick(note) },
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(if (note.title.isNotBlank()) note.title else "Untitled Note", style = MaterialTheme.typography.titleMedium, color = Color.Black)
                                    Text(dateString, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(previewText, style = MaterialTheme.typography.bodyMedium, color = Color.DarkGray, maxLines = 2)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// VIEW NOTE SCREEN (Reading Overlay)
// ==========================================
@Composable
fun ViewNoteScreen(note: Note, onBack: () -> Unit, onEdit: () -> Unit, onDelete: (Note) -> Unit) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFFFFFDE7)).padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back") }
                Icon(Icons.Filled.Person, "Profile", tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (note.title.isNotBlank()) note.title else "Untitled Note", style = MaterialTheme.typography.titleLarge)
            }
            Box {
                IconButton(onClick = { showMenu = true }) { Icon(Icons.Filled.MoreVert, "Options") }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(text = { Text("Edit") }, onClick = { showMenu = false; onEdit() })
                    DropdownMenuItem(text = { Text("Share") }, onClick = {
                        showMenu = false
                        val textToShare = note.blocks.filter { it.type == "TEXT" }.joinToString("\n\n") { it.content }
                        context.startActivity(Intent.createChooser(Intent().apply { action = Intent.ACTION_SEND; putExtra(Intent.EXTRA_TEXT, "${note.title}\n\n$textToShare"); type = "text/plain" }, "Share note via..."))
                    })
                    DropdownMenuItem(text = { Text("Delete", color = Color.Red) }, onClick = { showMenu = false; onDelete(note) })
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(note.blocks) { block ->
                if (block.type == "TEXT" && block.content.isNotBlank()) {
                    Text(text = block.content, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 6.dp))
                } else if (block.type == "IMAGE") {
                    val bitmap = try { BitmapFactory.decodeStream(context.contentResolver.openInputStream(Uri.parse(block.content))) } catch (e: Exception) { null }
                    if (bitmap != null) { Image(bitmap = bitmap.asImageBitmap(), contentDescription = "Attached Image", modifier = Modifier.fillMaxWidth().height(250.dp).padding(vertical = 12.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop) }
                }
            }
        }
    }
}