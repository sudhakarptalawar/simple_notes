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
                // --- THE NEW NAVIGATION DRAWER ---
                val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                val scope = rememberCoroutineScope()

                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        ModalDrawerSheet(
                            modifier = Modifier.width(280.dp),
                            drawerContainerColor = Color.White
                        ) {
                            Spacer(modifier = Modifier.height(32.dp))
                            // Drawer Header
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.Person, contentDescription = "Profile", modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("Hi Sudhakar", style = MaterialTheme.typography.titleLarge)
                            }
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

                            // Drawer Menu Items
                            NavigationDrawerItem(icon = { Icon(Icons.Filled.Star, "Starred") }, label = { Text("Starred") }, selected = false, onClick = { /* TODO */ })
                            NavigationDrawerItem(icon = { Icon(Icons.Filled.Delete, "Bin") }, label = { Text("Bin") }, selected = false, onClick = { /* TODO */ })
                            NavigationDrawerItem(icon = { Icon(Icons.Filled.Lock, "Locked Folder") }, label = { Text("Locked Folder") }, selected = false, onClick = { /* TODO */ })
                            NavigationDrawerItem(icon = { Icon(Icons.Filled.Cloud, "Cloud") }, label = { Text("Cloud") }, selected = false, onClick = { /* TODO */ })
                            NavigationDrawerItem(icon = { Icon(Icons.Filled.List, "All Notes") }, label = { Text("All Notes") }, selected = true, onClick = { scope.launch { drawerState.close() } })
                            NavigationDrawerItem(icon = { Icon(Icons.Filled.Info, "Help") }, label = { Text("Help and feedback") }, selected = false, onClick = { /* TODO */ })

                            // Pushes the signature to the very bottom!
                            Spacer(modifier = Modifier.weight(1f))

                            // The Signature
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            Text(
                                text = "v2.2.0 • Designed by Sudhakar",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.Gray,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                ) {
                    // MAIN APP CONTENT INSIDE DRAWER
                    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                        Box(modifier = Modifier.padding(innerPadding)) {
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
                                    onNoteClick = { clickedNote -> viewingNote = clickedNote },
                                    onOpenMenu = { scope.launch { drawerState.open() } } // Triggers the Drawer!
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainAppPager(noteToEdit: Note?, onClearEdit: () -> Unit, onNoteClick: (Note) -> Unit, onOpenMenu: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { 2 })
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(noteToEdit) {
        if (noteToEdit != null) {
            pagerState.animateScrollToPage(0)
        }
    }

    HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
        when (page) {
            0 -> CreateNoteScreen(
                incomingNote = noteToEdit,
                onNoteSaved = { onClearEdit() },
                onOpenMenu = onOpenMenu
            )
            1 -> SavedNotesScreen(
                currentPage = pagerState.currentPage,
                onNoteClick = onNoteClick,
                onNewNoteClick = {
                    onClearEdit()
                    coroutineScope.launch { pagerState.animateScrollToPage(0) }
                },
                onOpenMenu = onOpenMenu
            )
        }
    }
}

// ==========================================
// VIEW NOTE SCREEN (Unchanged)
// ==========================================
@Composable
fun ViewNoteScreen(note: Note, onBack: () -> Unit, onEdit: () -> Unit, onDelete: (Note) -> Unit) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFFFFFDE7)).padding(16.dp).padding(top = 24.dp)) {
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

// ==========================================
// PAGE 1: CREATE / EDIT SCREEN (Updated Buttons)
// ==========================================
@Composable
fun CreateNoteScreen(incomingNote: Note?, onNoteSaved: () -> Unit, onOpenMenu: () -> Unit) {
    val context = LocalContext.current
    var currentNoteId by remember { mutableStateOf(UUID.randomUUID().toString()) }
    var title by remember { mutableStateOf("") }
    val blocks = remember { mutableStateListOf<NoteBlock>(NoteBlock("TEXT", "")) }

    LaunchedEffect(incomingNote) {
        if (incomingNote != null) {
            currentNoteId = incomingNote.id
            title = incomingNote.title
            blocks.clear()
            incomingNote.blocks.forEach { blocks.add(NoteBlock(it.type, it.content)) }
        } else {
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
        // --- TOP HEADER ---
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Click the profile to open the side menu!
                IconButton(onClick = onOpenMenu) {
                    Icon(Icons.Filled.Menu, "Menu", modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (incomingNote != null) "Editing Note..." else "Hi Sudhakar", style = MaterialTheme.typography.titleMedium)
            }
            IconButton(onClick = { /* Settings */ }) { Icon(Icons.Filled.Settings, "Settings", tint = Color.Gray) }
        }

        OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Enter Title") }, modifier = Modifier.fillMaxWidth(), singleLine = true, textStyle = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(12.dp))

        // --- EDITOR AREA ---
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

        // --- NEW UNIFIED ACTION BAR ---
        Surface(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            color = MaterialTheme.colorScheme.background
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // LEFT SIDE: Other Tools
                IconButton(onClick = { Toast.makeText(context, "Tools coming soon!", Toast.LENGTH_SHORT).show() }) {
                    Icon(Icons.Filled.Build, contentDescription = "Other Tools", tint = MaterialTheme.colorScheme.primary)
                }

                // RIGHT SIDE: Add Image next to Save
                Row {
                    Button(
                        onClick = { imagePickerLauncher.launch(arrayOf("image/*")) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Icon(Icons.Filled.Add, "Add Image")
                        Spacer(Modifier.width(4.dp))
                        Text("Image")
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            val noteToSave = Note(id = currentNoteId, title = title, blocks = blocks.toMutableList())
                            context.getSharedPreferences("NotesVault", Context.MODE_PRIVATE).edit().putString(noteToSave.id, Gson().toJson(noteToSave)).apply()

                            title = ""
                            blocks.clear()
                            blocks.add(NoteBlock("TEXT", ""))
                            currentNoteId = UUID.randomUUID().toString()
                            onNoteSaved()
                            Toast.makeText(context, "Note Saved! Swipe Right to view.", Toast.LENGTH_SHORT).show()
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
// PAGE 2: SAVED NOTES SCREEN (Fixed Top Bar)
// ==========================================
@Composable
fun SavedNotesScreen(currentPage: Int, onNoteClick: (Note) -> Unit, onNewNoteClick: () -> Unit, onOpenMenu: () -> Unit) {
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
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues).background(Color(0xFFE3F2FD)).padding(top = 24.dp)) {

            // --- THE FIXED HEADER (Like WhatsApp) ---
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFFE3F2FD),
                shadowElevation = 2.dp // Gives a tiny shadow so you know it's fixed
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Click the profile menu to open Drawer
                            IconButton(onClick = onOpenMenu) {
                                Icon(Icons.Filled.Menu, "Menu", modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Hi Sudhakar", style = MaterialTheme.typography.titleMedium)
                        }
                    }

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("🔍 Search notes...") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = Color.White, focusedContainerColor = Color.White)
                    )
                }
            }

            // --- THE SCROLLABLE LIST ---
            val filteredNotes = savedNotes.filter { note -> note.title.contains(searchQuery, ignoreCase = true) || note.blocks.any { it.type == "TEXT" && it.content.contains(searchQuery, ignoreCase = true) } }

            if (filteredNotes.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Text("No saved notes found.", color = Color.Gray) }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp) // Pads the scrolling list
                ) {
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
