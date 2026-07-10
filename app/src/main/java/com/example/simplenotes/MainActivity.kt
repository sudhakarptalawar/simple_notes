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
    var isDeleted: Boolean = false,
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
                val pagerState = rememberPagerState(pageCount = { 2 }, initialPage = 0)

                var viewingNote by remember { mutableStateOf<Note?>(null) }
                var noteToEdit by remember { mutableStateOf<Note?>(null) }
                var currentFolder by remember { mutableStateOf("ALL_NOTES") }

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

                            NavigationDrawerItem(icon = { Icon(Icons.Filled.Star, "Starred") }, label = { Text("Starred") }, selected = currentFolder == "STARRED", onClick = { Toast.makeText(this@MainActivity, "Coming Soon!", Toast.LENGTH_SHORT).show() })

                            NavigationDrawerItem(
                                icon = { Icon(Icons.Filled.Delete, "Bin") },
                                label = { Text("Bin") },
                                selected = currentFolder == "BIN",
                                onClick = {
                                    currentFolder = "BIN"
                                    scope.launch { drawerState.close() }
                                }
                            )

                            NavigationDrawerItem(icon = { Icon(Icons.Filled.Lock, "Locked Folder") }, label = { Text("Locked Folder") }, selected = false, onClick = { Toast.makeText(this@MainActivity, "Coming Soon!", Toast.LENGTH_SHORT).show() })
                            NavigationDrawerItem(icon = { Icon(Icons.Filled.Cloud, "Cloud") }, label = { Text("Cloud") }, selected = false, onClick = { })

                            NavigationDrawerItem(
                                icon = { Icon(Icons.Filled.List, "All Notes") },
                                label = { Text("All Notes") },
                                selected = currentFolder == "ALL_NOTES",
                                onClick = {
                                    currentFolder = "ALL_NOTES"
                                    scope.launch { drawerState.close(); pagerState.animateScrollToPage(1) }
                                }
                            )

                            NavigationDrawerItem(icon = { Icon(Icons.Filled.Info, "Help") }, label = { Text("Help and feedback") }, selected = false, onClick = { })

                            Spacer(modifier = Modifier.weight(1f))
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            Text("v2.5.1 • Designed by Sudhakar", style = MaterialTheme.typography.labelMedium, color = Color.Gray, modifier = Modifier.padding(16.dp))
                        }
                    }
                ) {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        bottomBar = {
                            // NEW: Only show the bottom bar if we are in ALL_NOTES!
                            if (viewingNote == null && currentFolder == "ALL_NOTES") {
                                NavigationBar(containerColor = Color.White, tonalElevation = 8.dp) {
                                    NavigationBarItem(
                                        icon = { Icon(Icons.Filled.Create, "Write") },
                                        label = { Text("Write") },
                                        selected = pagerState.currentPage == 0,
                                        onClick = { scope.launch { pagerState.animateScrollToPage(0) } }
                                    )
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
                            if (pagerState.currentPage == 1 && viewingNote == null && currentFolder == "ALL_NOTES") {
                                ExtendedFloatingActionButton(
                                    onClick = { noteToEdit = null; scope.launch { pagerState.animateScrollToPage(0) } },
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
                                    onActionComplete = { viewingNote = null }
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
                                            Text(
                                                text = if (noteToEdit != null) "Editing Note..."
                                                else if (currentFolder == "BIN") "Bin"
                                                else "Hi Sudhakar",
                                                style = MaterialTheme.typography.titleMedium
                                            )
                                        }
                                        IconButton(onClick = { /* Settings */ }) { Icon(Icons.Filled.Settings, "Settings", tint = Color.Gray) }
                                    }

                                    // NEW: If we are in the Bin, completely disable the Swiping Pager
                                    // and ONLY show the SavedNotesScreen!
                                    if (currentFolder == "ALL_NOTES") {
                                        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                                            when (page) {
                                                0 -> CreateNoteScreen(
                                                    incomingNote = noteToEdit,
                                                    onNoteSaved = {
                                                        noteToEdit = null
                                                        scope.launch { pagerState.animateScrollToPage(1) }
                                                    }
                                                )
                                                1 -> SavedNotesScreen(
                                                    currentPage = pagerState.currentPage,
                                                    currentFolder = currentFolder,
                                                    onNoteClick = { clickedNote -> viewingNote = clickedNote }
                                                )
                                            }
                                        }
                                    } else {
                                        // Lock the view to just the list when in Bin or other folders
                                        SavedNotesScreen(
                                            currentPage = 1,
                                            currentFolder = currentFolder,
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

// ==========================================
// PAGE 0: CREATE / EDIT SCREEN
// ==========================================
@Composable
fun CreateNoteScreen(incomingNote: Note?, onNoteSaved: () -> Unit) {
    val context = LocalContext.current
    var currentNoteId by remember { mutableStateOf(UUID.randomUUID().toString()) }
    var title by remember { mutableStateOf("") }
    val blocks = remember { mutableStateListOf<NoteBlock>(NoteBlock("TEXT", "")) }
    var isDeletedFlag by remember { mutableStateOf(false) }

    LaunchedEffect(incomingNote) {
        if (incomingNote != null) {
            currentNoteId = incomingNote.id; title = incomingNote.title; isDeletedFlag = incomingNote.isDeleted
            blocks.clear()
            incomingNote.blocks.forEach { blocks.add(NoteBlock(it.type, it.content)) }
        } else {
            currentNoteId = UUID.randomUUID().toString(); title = ""; isDeletedFlag = false
            blocks.clear(); blocks.add(NoteBlock("TEXT", ""))
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
                            val noteToSave = Note(id = currentNoteId, title = title, isDeleted = isDeletedFlag, blocks = blocks.toMutableList())
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
// PAGE 1: SAVED NOTES SCREEN
// ==========================================
@Composable
fun SavedNotesScreen(currentPage: Int, currentFolder: String, onNoteClick: (Note) -> Unit) {
    val context = LocalContext.current
    val sharedPreferences = context.getSharedPreferences("NotesVault", Context.MODE_PRIVATE)
    var savedNotes by remember { mutableStateOf<List<Note>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(currentPage, currentFolder) {
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

            val filteredNotes = savedNotes.filter { note ->
                val matchesSearch = note.title.contains(searchQuery, ignoreCase = true) || note.blocks.any { it.type == "TEXT" && it.content.contains(searchQuery, ignoreCase = true) }
                val matchesFolder = if (currentFolder == "BIN") note.isDeleted else !note.isDeleted
                matchesSearch && matchesFolder
            }

            if (filteredNotes.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(if (currentFolder == "BIN") "Bin is empty." else "No saved notes found.", color = Color.Gray)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(filteredNotes) { note ->
                        val previewText = note.blocks.firstOrNull { it.type == "TEXT" && it.content.isNotBlank() }?.content ?: "No text preview..."
                        val dateString = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(note.timestamp))

                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable { onNoteClick(note) },
                            colors = CardDefaults.cardColors(containerColor = if (note.isDeleted) Color(0xFFFFEBEE) else Color.White),
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
// VIEW NOTE SCREEN
// ==========================================
@Composable
fun ViewNoteScreen(note: Note, onBack: () -> Unit, onEdit: () -> Unit, onActionComplete: () -> Unit) {
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

                    if (note.isDeleted) {
                        DropdownMenuItem(text = { Text("Restore Note", color = Color(0xFF4CAF50)) }, onClick = {
                            showMenu = false
                            note.isDeleted = false
                            context.getSharedPreferences("NotesVault", Context.MODE_PRIVATE).edit().putString(note.id, Gson().toJson(note)).apply()
                            Toast.makeText(context, "Note Restored!", Toast.LENGTH_SHORT).show()
                            onActionComplete()
                        })
                        DropdownMenuItem(text = { Text("Delete Forever", color = Color.Red) }, onClick = {
                            showMenu = false
                            context.getSharedPreferences("NotesVault", Context.MODE_PRIVATE).edit().remove(note.id).apply()
                            Toast.makeText(context, "Permanently Deleted", Toast.LENGTH_SHORT).show()
                            onActionComplete()
                        })
                    } else {
                        DropdownMenuItem(text = { Text("Edit") }, onClick = { showMenu = false; onEdit() })
                        DropdownMenuItem(text = { Text("Share") }, onClick = {
                            showMenu = false
                            val textToShare = note.blocks.filter { it.type == "TEXT" }.joinToString("\n\n") { it.content }
                            context.startActivity(Intent.createChooser(Intent().apply { action = Intent.ACTION_SEND; putExtra(Intent.EXTRA_TEXT, "${note.title}\n\n$textToShare"); type = "text/plain" }, "Share note via..."))
                        })
                        DropdownMenuItem(text = { Text("Move to Bin", color = Color.Red) }, onClick = {
                            showMenu = false
                            note.isDeleted = true
                            context.getSharedPreferences("NotesVault", Context.MODE_PRIVATE).edit().putString(note.id, Gson().toJson(note)).apply()
                            Toast.makeText(context, "Moved to Bin", Toast.LENGTH_SHORT).show()
                            onActionComplete()
                        })
                    }
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