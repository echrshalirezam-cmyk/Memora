package com.memora.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Night = Color(0xFF080B16)
private val Panel = Color(0xFF11172A)
private val Purple = Color(0xFF8B5CF6)
private val Magenta = Color(0xFFE14BCE)
private val SoftText = Color(0xFF9CA8C7)
private val White = Color(0xFFF5F3FF)
private val Danger = Color(0xFFFF7188)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(8, 11, 22)
        window.navigationBarColor = android.graphics.Color.rgb(8, 11, 22)
        setContent { MemoraApp() }
    }
}

@Composable
private fun MemoraApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableIntStateOf(0) }
    var treeUri by remember { mutableStateOf<Uri?>(null) }
    var tree by remember { mutableStateOf<ManagedTree?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("برای شروع، حافظه داخلی یا کارت حافظه را انتخاب کن.") }
    var filter by remember { mutableStateOf("همه") }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf<Uri>()) }
    var sortLargeFirst by remember { mutableStateOf(true) }
    var dialog by remember { mutableStateOf("") }
    var nameInput by remember { mutableStateOf("") }
    var targetUri by remember { mutableStateOf<Uri?>(null) }
    var moveMode by remember { mutableStateOf(true) }
    var pendingEntry by remember { mutableStateOf<ManagedEntry?>(null) }
    var duplicateGroups by remember { mutableStateOf<List<List<ManagedEntry>>>(emptyList()) }
    var duplicateBusy by remember { mutableStateOf(false) }
    var duplicateChecked by remember { mutableStateOf(false) }
    var cleanSelection by remember { mutableStateOf(setOf<Uri>()) }

    fun refresh() {
        val uri = treeUri ?: return
        busy = true
        scope.launch {
            try {
                tree = TreeStorageManager.scan(context, uri)
                message = "اسکن کامل شد • ${tree?.files?.size ?: 0} فایل"
                selected = emptySet()
            } catch (e: Exception) {
                message = "خطا در اسکن: ${e.localizedMessage ?: "دسترسی ممکن نیست"}"
            } finally { busy = false }
        }
    }

    val rootPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                treeUri = uri
                tree = null
                selected = emptySet()
                refresh()
            } catch (e: Exception) {
                message = "مجوز پوشه دریافت نشد: ${e.localizedMessage ?: ""}"
            }
        }
    }
    val destinationPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                targetUri = uri
                dialog = "moveConfirm"
            } catch (e: Exception) { message = "دسترسی مقصد دریافت نشد" }
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(
        background = Night, surface = Panel, primary = Purple, secondary = Magenta,
        onBackground = White, onSurface = White
    )) {
        Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF10152A), Night))).padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("MEMORA", color = White, fontSize = 22.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                    Text("مدیریت کامل حافظه", color = SoftText, fontSize = 12.sp)
                }
                IconButton(onClick = { dialog = "settings" }) { Icon(Icons.Rounded.Settings, "تنظیمات", tint = SoftText) }
            }
            Column(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                0 -> HomeScreen(
                    tree = tree, busy = busy, message = message,
                    onPick = { rootPicker.launch(null) }, onRefresh = { refresh() },
                    onFiles = { filter = "همه"; tab = 1 },
                    onCategory = { filter = it; tab = 1 },
                    onClean = { tab = 2 },
                    onApps = { tab = 3 }
                )
                1 -> FilesScreen(
                    tree = tree, busy = busy, message = message, filter = filter, query = query,
                    selected = selected, sortLargeFirst = sortLargeFirst,
                    onPick = { rootPicker.launch(null) }, onRefresh = { refresh() },
                    onFilter = { filter = it }, onQuery = { query = it },
                    onSort = { sortLargeFirst = !sortLargeFirst },
                    onToggle = { uri -> selected = if (uri in selected) selected - uri else selected + uri },
                    onSelectAll = { items -> selected = if (selected.size == items.size) emptySet() else items.map { it.uri }.toSet() },
                    onCreateFolder = { nameInput = ""; dialog = "createFolder" },
                    onRename = { item -> pendingEntry = item; nameInput = item.name; dialog = "rename" },
                    onDelete = { item -> pendingEntry = item; dialog = "deleteOne" },
                    onMove = {
                        val fileUris = selected.filter { uri -> tree?.files?.any { it.uri == uri } == true }
                        if (fileUris.isNotEmpty()) { selected = fileUris.toSet(); moveMode = true; destinationPicker.launch(null) }
                        else message = "برای انتقال، ابتدا فایل‌ها را انتخاب کن."
                    },
                    onCopy = {
                        val fileUris = selected.filter { uri -> tree?.files?.any { it.uri == uri } == true }
                        if (fileUris.isNotEmpty()) { selected = fileUris.toSet(); moveMode = false; destinationPicker.launch(null) }
                        else message = "برای کپی، ابتدا فایل‌ها را انتخاب کن."
                    },
                    onDeleteSelected = { dialog = "deleteSelected" },
                    onOpenClean = { tab = 2 }
                )
                2 -> CleanupScreen(
                    tree = tree, duplicateGroups = duplicateGroups, busy = busy,
                    duplicateBusy = duplicateBusy, duplicateChecked = duplicateChecked,
                    selection = cleanSelection,
                    onPick = { rootPicker.launch(null) }, onRefresh = { refresh() },
                    onFindDuplicates = {
                        val files = tree?.files.orEmpty()
                        duplicateBusy = true
                        scope.launch {
                            try {
                                duplicateGroups = TreeStorageManager.findDuplicates(context, files)
                                duplicateChecked = true
                                message = "بررسی تکراری‌ها تمام شد"
                            } catch (e: Exception) { message = "خطا در بررسی تکراری‌ها: ${e.localizedMessage}" }
                            finally { duplicateBusy = false }
                        }
                    },
                    onToggle = { uri -> cleanSelection = if (uri in cleanSelection) cleanSelection - uri else cleanSelection + uri },
                    onDeleteSelected = { dialog = "deleteClean" },
                    onApps = { tab = 3 }
                )
                else -> AppsScreen(
                    onAppSettings = { context.startActivity(Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS)) },
                    onStorageSettings = {
                        try { context.startActivity(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)) }
                        catch (_: Exception) { context.startActivity(Intent(Settings.ACTION_SETTINGS)) }
                    },
                    onPermissions = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }
                )
            }
            }
            NavigationBar(containerColor = Color(0xFF0D1222)) {
                NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, icon = { Icon(Icons.Rounded.Dashboard, null) }, label = { Text("خانه") })
                NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, icon = { Icon(Icons.Rounded.Folder, null) }, label = { Text("فایل‌ها") })
                NavigationBarItem(selected = tab == 2, onClick = { tab = 2 }, icon = { Icon(Icons.Rounded.CleaningServices, null) }, label = { Text("پاک‌سازی") })
                NavigationBarItem(selected = tab == 3, onClick = { tab = 3 }, icon = { Icon(Icons.Rounded.Apps, null) }, label = { Text("برنامه‌ها") })
            }
        }

        when (dialog) {
            "settings" -> AlertDialog(
                onDismissRequest = { dialog = "" },
                title = { Text("تنظیمات MEMORA") },
                text = { Text("برای دسترسی کامل به فایل‌ها، پوشه‌ها را از انتخاب‌گر رسمی اندروید اضافه کن. حذف فایل‌ها فقط پس از تأیید انجام می‌شود. پاک‌سازی داده و کش برنامه‌های دیگر فقط از تنظیمات خود اندروید ممکن است.") },
                confirmButton = { TextButton(onClick = { dialog = ""; context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }) { Text("مجوزهای برنامه") } },
                dismissButton = { TextButton(onClick = { dialog = "" }) { Text("بستن") } }
            )
            "createFolder" -> TextEntryDialog("ساخت پوشه جدید", nameInput, { nameInput = it }, {
                val uri = treeUri
                if (uri != null) scope.launch {
                    val ok = TreeStorageManager.createFolder(context, uri, nameInput.trim())
                    message = if (ok) "پوشه ساخته شد" else "ساخت پوشه ممکن نشد"
                    dialog = ""
                    refresh()
                } else dialog = ""
            }) { dialog = "" }
            "rename" -> TextEntryDialog("تغییر نام", nameInput, { nameInput = it }, {
                val item = pendingEntry
                if (item != null) scope.launch {
                    val ok = TreeStorageManager.rename(context, item.uri, nameInput.trim())
                    message = if (ok) "نام تغییر کرد" else "تغییر نام انجام نشد"
                    dialog = ""
                    refresh()
                } else dialog = ""
            }) { dialog = "" }
            "deleteOne" -> ConfirmDialog("حذف فایل", "«${pendingEntry?.name}» حذف شود؟ این کار ممکن است قابل بازگشت نباشد.", {
                val item = pendingEntry
                if (item != null) scope.launch {
                    val ok = TreeStorageManager.delete(context, item.uri)
                    message = if (ok) "فایل حذف شد" else "حذف فایل ممکن نشد"
                    dialog = ""
                    refresh()
                } else dialog = ""
            }) { dialog = "" }
            "deleteSelected" -> ConfirmDialog("حذف موارد انتخاب‌شده", "تعداد ${selected.size} مورد برای حذف انتخاب شده. ادامه می‌دهی؟", {
                scope.launch {
                    val ordered = selected.sortedByDescending { uri -> tree?.entries?.firstOrNull { it.uri == uri }?.relativePath?.split("/")?.size ?: 0 }
                    val result = TreeStorageManager.deleteMany(context, ordered)
                    message = "${result.first} مورد حذف شد؛ ${result.second} مورد ناموفق بود"
                    dialog = ""
                    refresh()
                }
            }) { dialog = "" }
            "deleteClean" -> ConfirmDialog("حذف موارد پاک‌سازی", "تعداد ${cleanSelection.size} فایل انتخاب‌شده حذف شود؟", {
                scope.launch {
                    val ordered = cleanSelection.sortedByDescending { uri -> tree?.entries?.firstOrNull { it.uri == uri }?.relativePath?.split("/")?.size ?: 0 }
                    val result = TreeStorageManager.deleteMany(context, ordered)
                    message = "${result.first} مورد حذف شد؛ ${result.second} مورد ناموفق بود"
                    cleanSelection = emptySet()
                    dialog = ""
                    refresh()
                }
            }) { dialog = "" }
            "moveConfirm" -> ConfirmDialog("انتقال فایل‌ها", if (moveMode) "تعداد ${selected.size} فایل به پوشه مقصد منتقل می‌شود؛ پس از کپی موفق از مبدأ حذف خواهد شد." else "تعداد ${selected.size} فایل در پوشه مقصد کپی می‌شود. فایل اصلی باقی می‌ماند.", {
                val dest = targetUri
                if (dest != null) scope.launch {
                    var ok = 0
                    var failed = 0
                    selected.forEach { uri ->
                        try {
                            if (TreeStorageManager.copyOrMove(context, uri, dest, moveMode)) ok++ else failed++
                        } catch (_: Exception) { failed++ }
                    }
                    message = "$ok فایل منتقل شد؛ $failed مورد ناموفق بود"
                    dialog = ""
                    refresh()
                } else dialog = ""
            }) { dialog = "" }
        }
    }
}

@Composable
private fun ColumnScope.HomeScreen(
    tree: ManagedTree?, busy: Boolean, message: String,
    onPick: () -> Unit, onRefresh: () -> Unit, onFiles: () -> Unit,
    onCategory: (String) -> Unit, onClean: () -> Unit, onApps: () -> Unit
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text("داشبورد حافظه", color = White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("آمار واقعی پوشه انتخاب‌شده", color = SoftText, fontSize = 12.sp)
        Spacer(Modifier.height(12.dp))
        Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text(tree?.rootName ?: "هنوز حافظه‌ای انتخاب نشده", color = White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(if (tree == null) "برای اسکن کامل محدوده مجاز، حافظه داخلی یا microSD را انتخاب کن." else "${tree.files.size} فایل • ${tree.folders.size} پوشه • ${StorageScanner.formatBytes(tree.totalBytes)}", color = SoftText)
                Spacer(Modifier.height(12.dp))
                Button(onClick = onPick, modifier = Modifier.fillMaxWidth(), enabled = !busy) { Text(if (busy) "در حال اسکن..." else "انتخاب حافظه / کارت SD") }
                OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth(), enabled = tree != null && !busy) { Text("اسکن دوباره") }
                Text(message, color = SoftText, fontSize = 11.sp)
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("دسته‌بندی فایل‌ها", color = White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        val cats = listOf("همه", "تصاویر", "ویدئوها", "موسیقی", "اسناد", "فشرده", "APK", "سایر")
        cats.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { cat ->
                    Card(Modifier.weight(1f).padding(vertical = 4.dp).clickable { onCategory(cat) }, colors = CardDefaults.cardColors(containerColor = Panel)) {
                        Text(cat, color = White, modifier = Modifier.padding(16.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        ActionCard("مدیریت فایل‌ها", "انتقال، تغییرنام، حذف، ساخت پوشه و انتخاب گروهی", onFiles)
        ActionCard("پاک‌سازی هوشمند", "فایل‌های حجیم، تکراری و پوشه‌های خالی", onClean)
        ActionCard("مدیریت برنامه‌ها", "باز کردن مدیریت برنامه‌ها و فضای اشغال‌شده اندروید", onApps)
    }
}

@Composable
private fun FilesScreen(
    tree: ManagedTree?, busy: Boolean, message: String, filter: String, query: String,
    selected: Set<Uri>, sortLargeFirst: Boolean,
    onPick: () -> Unit, onRefresh: () -> Unit, onFilter: (String) -> Unit, onQuery: (String) -> Unit,
    onSort: () -> Unit, onToggle: (Uri) -> Unit, onSelectAll: (List<ManagedEntry>) -> Unit,
    onCreateFolder: () -> Unit, onRename: (ManagedEntry) -> Unit, onDelete: (ManagedEntry) -> Unit,
    onMove: () -> Unit, onDeleteSelected: () -> Unit, onOpenClean: () -> Unit
) {
    val filtered = tree?.entries.orEmpty().filter { entry ->
        val categoryOk = when (filter) {
            "تصاویر" -> entry.mimeType?.startsWith("image/") == true
            "ویدئوها" -> entry.mimeType?.startsWith("video/") == true
            "موسیقی" -> entry.mimeType?.startsWith("audio/") == true
            "اسناد" -> entry.name.substringAfterLast('.', "").lowercase() in setOf("pdf","txt","doc","docx","xls","xlsx","ppt","pptx","odt","rtf")
            "فشرده" -> entry.name.substringAfterLast('.', "").lowercase() in setOf("zip","rar","7z","tar","gz")
            "APK" -> entry.name.endsWith(".apk", true)
            "سایر" -> entry.mimeType?.startsWith("image/") != true && entry.mimeType?.startsWith("video/") != true && entry.mimeType?.startsWith("audio/") != true
            else -> true
        }
        categoryOk && entry.name.contains(query, true)
    }.let { if (sortLargeFirst) it.sortedWith(compareByDescending<ManagedEntry> { e -> e.isDirectory }.thenByDescending { e -> e.size }) else it.sortedBy { e -> e.name.lowercase() } }

    Column(Modifier.fillMaxSize()) {
        Text("مدیریت فایل‌ها", color = White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onPick, modifier = Modifier.weight(1f)) { Text("انتخاب حافظه") }
            OutlinedButton(onClick = onRefresh, enabled = tree != null && !busy) { Text("بروزرسانی") }
        }
        OutlinedTextField(value = query, onValueChange = onQuery, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("جستجوی نام فایل یا پوشه") })
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${filtered.size} مورد", color = SoftText, modifier = Modifier.weight(1f))
            TextButton(onClick = onSort) { Text(if (sortLargeFirst) "مرتب‌سازی: حجم" else "مرتب‌سازی: نام") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.fillMaxWidth()) {
            listOf("همه","تصاویر","ویدئوها","موسیقی","اسناد","فشرده","APK").forEach { cat ->
                FilterChip(selected = filter == cat, onClick = { onFilter(cat) }, label = { Text(cat, fontSize = 10.sp) })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = { onSelectAll(filtered) }) { Text(if (selected.size == filtered.size && filtered.isNotEmpty()) "لغو انتخاب" else "انتخاب همه") }
            TextButton(onClick = onCreateFolder) { Text("پوشه جدید") }
        }
        if (selected.isNotEmpty()) {
            Text("انتخاب‌شده: ${selected.size}", color = Purple)
            Row {
                TextButton(onClick = onMove) { Text("انتقال") }
                TextButton(onClick = onDeleteSelected) { Text("حذف گروهی", color = Danger) }
            }
        }
        Text(message, color = SoftText, fontSize = 10.sp)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            filtered.forEach { item ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = item.uri in selected, onCheckedChange = { onToggle(item.uri) })
                    Text(if (item.isDirectory) "📁" else "📄", modifier = Modifier.padding(end = 7.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.name, color = White, fontSize = 12.sp, maxLines = 1)
                        Text(if (item.isDirectory) item.relativePath else "${StorageScanner.formatBytes(item.size)} • ${item.relativePath}", color = SoftText, fontSize = 10.sp, maxLines = 1)
                    }
                    if (!item.isDirectory) {
                        IconButton(onClick = { onRename(item) }) { Icon(Icons.Rounded.Edit, "تغییر نام", tint = Purple) }
                        IconButton(onClick = { onDelete(item) }) { Icon(Icons.Rounded.Delete, "حذف", tint = Danger) }
                    }
                }
                Divider(color = Color(0xFF252C43))
            }
            if (tree == null) Text("ابتدا یک پوشه یا کارت حافظه انتخاب کن.", color = SoftText, modifier = Modifier.padding(16.dp))
        }
        TextButton(onClick = onOpenClean, modifier = Modifier.fillMaxWidth()) { Text("رفتن به پاک‌سازی هوشمند") }
    }
}

@Composable
private fun CleanupScreen(
    tree: ManagedTree?, duplicateGroups: List<List<ManagedEntry>>, busy: Boolean,
    duplicateBusy: Boolean, duplicateChecked: Boolean, selection: Set<Uri>,
    onPick: () -> Unit, onRefresh: () -> Unit, onFindDuplicates: () -> Unit,
    onToggle: (Uri) -> Unit, onDeleteSelected: () -> Unit, onApps: () -> Unit
) {
    val large = tree?.largestFiles.orEmpty()
    val empty = tree?.emptyFolders.orEmpty()
    val duplicateItems = duplicateGroups.flatten()
    val tempFiles = tree?.files.orEmpty().filter { it.name.substringAfterLast('.', "").lowercase() in setOf("tmp", "temp", "log", "cache", "bak", "old") }
    val oldFiles = tree?.files.orEmpty().filter { it.modified > 0L && System.currentTimeMillis() - it.modified > 365L * 24L * 60L * 60L * 1000L }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text("پاک‌سازی هوشمند", color = White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
        Text("هیچ موردی بدون انتخاب و تأیید تو حذف نمی‌شود.", color = SoftText, fontSize = 11.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onPick, modifier = Modifier.weight(1f)) { Text("انتخاب حافظه") }
            OutlinedButton(onClick = onRefresh, enabled = tree != null && !busy) { Text("اسکن دوباره") }
        }
        Text("فایل‌های حجیم • ${large.size} مورد شناسایی‌شده", color = White, fontWeight = FontWeight.Bold)
        large.forEach { item -> CleanupItem(item, selection, onToggle) }
        Spacer(Modifier.height(12.dp))
        Text("پوشه‌های خالی • ${empty.size} مورد پیدا شد", color = White, fontWeight = FontWeight.Bold)
        empty.forEach { folder -> CleanupItem(folder, selection, onToggle) }
        Spacer(Modifier.height(12.dp))
        Text("فایل‌های موقت و پشتیبان • ${tempFiles.size} مورد", color = White, fontWeight = FontWeight.Bold)
        tempFiles.forEach { CleanupItem(it, selection, onToggle) }
        Spacer(Modifier.height(10.dp))
        Text("فایل‌های قدیمی‌تر از یک سال • ${oldFiles.size} مورد", color = White, fontWeight = FontWeight.Bold)
        oldFiles.forEach { CleanupItem(it, selection, onToggle) }
        Spacer(Modifier.height(10.dp))
        Text("فایل‌های تکراری واقعی (بر اساس SHA-256)", color = White, fontWeight = FontWeight.Bold)
        Button(onClick = onFindDuplicates, enabled = tree != null && !duplicateBusy, modifier = Modifier.fillMaxWidth()) {
            Text(if (duplicateBusy) "در حال بررسی..." else "بررسی فایل‌های تکراری")
        }
        if (duplicateChecked) {
            Text("${duplicateGroups.size} گروه تکراری • ${duplicateItems.size} فایل", color = SoftText, fontSize = 11.sp)
            duplicateItems.forEach { CleanupItem(it, selection, onToggle) }
        }
        if (selection.isNotEmpty()) {
            Button(onClick = onDeleteSelected, colors = ButtonDefaults.buttonColors(containerColor = Danger), modifier = Modifier.fillMaxWidth()) {
                Text("حذف ${selection.size} فایل انتخاب‌شده")
            }
        }
        ActionCard("کش و داده برنامه‌ها", "اندروید اجازه حذف مستقیم داده برنامه‌های دیگر را به این اپ نمی‌دهد.", onApps)
    }
}

@Composable
private fun CleanupItem(item: ManagedEntry, selection: Set<Uri>, onToggle: (Uri) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = item.uri in selection, onCheckedChange = { onToggle(item.uri) })
        Column(Modifier.weight(1f)) {
            Text(item.name, color = White, fontSize = 11.sp, maxLines = 1)
            Text(StorageScanner.formatBytes(item.size), color = SoftText, fontSize = 10.sp)
        }
    }
}

@Composable
private fun AppsScreen(onAppSettings: () -> Unit, onStorageSettings: () -> Unit, onPermissions: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text("مدیریت برنامه‌ها", color = White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
        Text("مدیریت نصب، حذف و پاک‌سازی کش برنامه‌ها تحت کنترل اندروید است.", color = SoftText, fontSize = 12.sp)
        ActionCard("مدیریت همه برنامه‌ها", "مشاهده، توقف یا حذف برنامه‌ها از تنظیمات سیستم", onAppSettings)
        ActionCard("فضای ذخیره‌سازی دستگاه", "بررسی مصرف حافظه و ابزار آزادسازی فضای اندروید", onStorageSettings)
        ActionCard("مجوزهای MEMORA", "مدیریت دسترسی‌های همین برنامه", onPermissions)
        Text("محدودیت مهم: اندروید به برنامه‌های عادی اجازه پاک‌کردن کش یا داده خصوصی سایر برنامه‌ها را نمی‌دهد. برای جلوگیری از حذف ناخواسته، این عملیات به صفحه رسمی سیستم هدایت می‌شود.", color = SoftText, fontSize = 12.sp)
    }
}

@Composable
private fun ActionCard(title: String, subtitle: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(vertical = 5.dp).clickable(onClick = onClick), colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(15.dp)) {
            Text(title, color = White, fontWeight = FontWeight.Bold)
            Text(subtitle, color = SoftText, fontSize = 11.sp)
        }
    }
}

@Composable
private fun TextEntryDialog(title: String, value: String, onValue: (String) -> Unit, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = {
        OutlinedTextField(value = value, onValueChange = onValue, singleLine = true, label = { Text("نام") })
    }, confirmButton = { TextButton(onClick = onConfirm) { Text("تأیید") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } })
}

@Composable
private fun ConfirmDialog(title: String, body: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(body) },
        confirmButton = { TextButton(onClick = onConfirm) { Text("تأیید", color = Danger) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } })
}
