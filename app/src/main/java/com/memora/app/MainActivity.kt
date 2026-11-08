package com.memora.app

import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.StatFs
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first

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
    var scanProgress by remember { mutableFloatStateOf(0f) }
    var scanProgressText by remember { mutableStateOf("") }
    var scanPaused by remember { mutableStateOf(false) }
    var loadedScanAt by remember { mutableLongStateOf(0L) }

    var message by remember { mutableStateOf("برای شروع، حافظه داخلی یا کارت حافظه را انتخاب کن.") }
    var filter by remember { mutableStateOf("همه") }
    var currentFolderPath by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf<Uri>()) }
    var sortLargeFirst by remember { mutableStateOf(true) }
    var dialog by remember { mutableStateOf("") }
    var nameInput by remember { mutableStateOf("") }
    var targetUri by remember { mutableStateOf<Uri?>(null) }
    var moveMode by remember { mutableStateOf(true) }
    var pendingEntry by remember { mutableStateOf<ManagedEntry?>(null) }
    var pendingTransferEntries by remember { mutableStateOf<List<ManagedEntry>>(emptyList()) }
    var duplicateGroups by remember { mutableStateOf<List<List<ManagedEntry>>>(emptyList()) }
    var sameNameGroups by remember { mutableStateOf<List<List<ManagedEntry>>>(emptyList()) }
    var sameNameBusy by remember { mutableStateOf(false) }
    var sameNameChecked by remember { mutableStateOf(false) }
    var similarGroups by remember { mutableStateOf<List<List<ManagedEntry>>>(emptyList()) }
    var similarBusy by remember { mutableStateOf(false) }
    var similarChecked by remember { mutableStateOf(false) }
    var duplicateBusy by remember { mutableStateOf(false) }
    var duplicateChecked by remember { mutableStateOf(false) }
    var cleanSelection by remember { mutableStateOf(setOf<Uri>()) }

    fun refresh(requestedUri: Uri? = treeUri, fullScan: Boolean = false) {
        val uri = requestedUri ?: return
        if (busy) return
        busy = true
        scanPaused = false
        scanProgress = 0f
        scanProgressText = "آماده‌سازی اسکن در پس‌زمینه..."
        message = "اسکن حتی با خاموش‌شدن صفحه ادامه پیدا می‌کند."
        try {
            MemoryScanService.command(context, MemoryScanService.ACTION_START, uri, fullScan)
        } catch (e: Exception) {
            busy = false
            message = "شروع سرویس اسکن ممکن نشد: ${e.localizedMessage ?: "خطای نامشخص"}"
        }
    }

    LaunchedEffect(Unit) {
        val saved = context.getSharedPreferences("memora_settings", android.content.Context.MODE_PRIVATE)
            .getString("last_tree_uri", null)
        if (saved != null) {
            val savedUri = Uri.parse(saved)
            treeUri = savedUri
            tree = ScanSnapshotStore.load(context)
            val scanPrefs = context.getSharedPreferences(MemoryScanService.PREFS, android.content.Context.MODE_PRIVATE)
            if (scanPrefs.getBoolean("busy", false)) {
                busy = true
                scanPaused = scanPrefs.getBoolean("paused", false)
                scanProgress = scanPrefs.getFloat("progress", 0f)
                scanProgressText = scanPrefs.getString("progressText", "اسکن در حال اجراست") ?: "اسکن در حال اجراست"
            } else if (tree == null) refresh(savedUri)
        }
    }

    LaunchedEffect(Unit) {
        val scanPrefs = context.getSharedPreferences(MemoryScanService.PREFS, android.content.Context.MODE_PRIVATE)
        while (true) {
            val status = scanPrefs.getString("status", "")
            busy = scanPrefs.getBoolean("busy", false)
            scanPaused = scanPrefs.getBoolean("paused", false)
            scanProgress = scanPrefs.getFloat("progress", 0f)
            scanProgressText = scanPrefs.getString("progressText", scanProgressText) ?: scanProgressText
            if (status == "completed") {
                val finishedAt = scanPrefs.getLong("finishedAt", 0L)
                if (finishedAt > loadedScanAt) {
                    val restored = withContext(Dispatchers.IO) { ScanSnapshotStore.load(context) }
                    if (restored != null) {
                    tree = restored
                    loadedScanAt = finishedAt
                    duplicateGroups = emptyList(); duplicateChecked = false
                    similarGroups = emptyList(); similarChecked = false
                    sameNameGroups = emptyList(); sameNameChecked = false
                    cleanSelection = emptySet(); selected = emptySet()
                    message = "اسکن کامل شد • ${restored.files.size} فایل"
                    }
                }
            } else if (status == "error") {
                message = "خطا در اسکن: ${scanPrefs.getString("error", "دسترسی ممکن نیست")}"
            } else if (status == "cancelled") {
                message = "اسکن متوقف شد."
            }
            kotlinx.coroutines.delay(700)
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
                context.getSharedPreferences("memora_settings", android.content.Context.MODE_PRIVATE).edit().putString("last_tree_uri", uri.toString()).apply()
                currentFolderPath = ""
                tree = null
                duplicateGroups = emptyList()
                duplicateChecked = false
                cleanSelection = emptySet()
                selected = emptySet()
                refresh(uri, fullScan = true)
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
                    tree = tree, busy = busy, message = message, scanProgress = scanProgress, scanProgressText = scanProgressText, scanPaused = scanPaused,
                    onPause = { MemoryScanService.command(context, if (scanPaused) MemoryScanService.ACTION_RESUME else MemoryScanService.ACTION_PAUSE) },
                    onCancelScan = { MemoryScanService.command(context, MemoryScanService.ACTION_CANCEL) },
                    onPick = { rootPicker.launch(null) }, onRefresh = { refresh() }, onFullRefresh = { refresh(fullScan = true) },
                    onFiles = { filter = "همه"; currentFolderPath = ""; tab = 1 },
                    onCategory = { filter = it; currentFolderPath = ""; tab = 1 },
                    onClean = { tab = 2 },
                    onApps = { tab = 3 }
                )
                1 -> FilesScreen(
                    tree = tree, busy = busy, message = message, filter = filter, query = query,
                    selected = selected, sortLargeFirst = sortLargeFirst, currentFolderPath = currentFolderPath,
                    onPick = { rootPicker.launch(null) }, onRefresh = { refresh() },
                    onFilter = { filter = it }, onQuery = { query = it },
                    onBackFolder = { currentFolderPath = currentFolderPath.substringBeforeLast("/", "") },
                    onSort = { sortLargeFirst = !sortLargeFirst },
                    onToggle = { uri -> selected = if (uri in selected) selected - uri else selected + uri },
                    onSelectAll = { items -> selected = if (selected.size == items.size) emptySet() else items.map { it.uri }.toSet() },
                    onCreateFolder = { nameInput = ""; dialog = "createFolder" },
                    onOpen = { item ->
                        if (item.isDirectory) { currentFolderPath = item.relativePath; selected = emptySet() } else try {
                            val viewIntent = Intent(Intent.ACTION_VIEW).setDataAndType(item.uri, item.mimeType ?: "*/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            context.startActivity(Intent.createChooser(viewIntent, "باز کردن فایل"))
                        } catch (_: Exception) { message = "برنامه‌ای برای باز کردن این نوع فایل پیدا نشد." }
                    },
                    onRename = { item -> pendingEntry = item; nameInput = item.name; dialog = "rename" },
                    onDelete = { item -> pendingEntry = item; dialog = "deleteOne" },
                    onMove = {
                        val chosen = selected.mapNotNull { uri -> tree?.entries?.firstOrNull { it.uri == uri } }
                        val topLevel = chosen.filterNot { entry -> chosen.any { parent -> parent.isDirectory && entry.relativePath.startsWith(parent.relativePath + "/") } }
                        if (topLevel.isNotEmpty()) { pendingTransferEntries = topLevel; selected = topLevel.map { it.uri }.toSet(); moveMode = true; destinationPicker.launch(null) }
                        else message = "ابتدا فایل یا پوشه‌ای را برای انتقال انتخاب کن."
                    },
                    onCopy = {
                        val chosen = selected.mapNotNull { uri -> tree?.entries?.firstOrNull { it.uri == uri } }
                        val topLevel = chosen.filterNot { entry -> chosen.any { parent -> parent.isDirectory && entry.relativePath.startsWith(parent.relativePath + "/") } }
                        if (topLevel.isNotEmpty()) { pendingTransferEntries = topLevel; selected = topLevel.map { it.uri }.toSet(); moveMode = false; destinationPicker.launch(null) }
                        else message = "ابتدا فایل یا پوشه‌ای را برای کپی انتخاب کن."
                    },
                    onDeleteSelected = { dialog = "deleteSelected" },
                    onOpenClean = { tab = 2 }
                )
                2 -> CleanupScreen(
                    tree = tree, duplicateGroups = duplicateGroups, similarGroups = similarGroups, sameNameGroups = sameNameGroups, busy = busy,
                    duplicateBusy = duplicateBusy, duplicateChecked = duplicateChecked, similarBusy = similarBusy, similarChecked = similarChecked,
                    sameNameBusy = sameNameBusy, sameNameChecked = sameNameChecked,
                    selection = cleanSelection,
                    onPick = { rootPicker.launch(null) }, onRefresh = { refresh() },
                    onFindSameNames = {
                        sameNameBusy = true
                        scope.launch {
                            try {
                                sameNameGroups = TreeStorageManager.findSameNames(tree?.files.orEmpty())
                                sameNameChecked = true
                            } catch (e: Exception) { message = "خطا در بررسی فایل‌های هم‌نام: ${e.localizedMessage}" }
                            finally { sameNameBusy = false }
                        }
                    },
                    onFindSimilar = {
                        similarBusy = true
                        scope.launch {
                            try {
                                similarGroups = TreeStorageManager.findSimilarNames(tree?.files.orEmpty())
                                similarChecked = true
                            } catch (e: Exception) { message = "خطا در بررسی نام‌های مشابه: ${e.localizedMessage}" }
                            finally { similarBusy = false }
                        }
                    },
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
                    onToggleAll = { items ->
                        val uris = items.map { it.uri }.toSet()
                        cleanSelection = if (uris.isNotEmpty() && cleanSelection.containsAll(uris)) cleanSelection - uris else cleanSelection + uris
                    },
                    onOpen = { item ->
                        try {
                            val viewIntent = Intent(Intent.ACTION_VIEW)
                                .setDataAndType(item.uri, item.mimeType ?: "*/*")
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            context.startActivity(Intent.createChooser(viewIntent, "باز کردن فایل"))
                        } catch (_: Exception) {
                            message = "برنامه‌ای برای باز کردن این فایل پیدا نشد."
                        }
                    },
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
                    val ok = TreeStorageManager.createFolderInPath(context, uri, currentFolderPath, nameInput.trim())
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
            "deleteOne" -> ConfirmDialog("حذف مورد", if (pendingEntry?.isDirectory == true) "پوشه «${pendingEntry?.name}» همراه با تمام محتویاتش حذف شود؟ این کار ممکن است قابل بازگشت نباشد." else "«${pendingEntry?.name}» حذف شود؟ این کار ممکن است قابل بازگشت نباشد.", {
                val item = pendingEntry
                if (item != null) scope.launch {
                    val ok = if (item.isDirectory) {
                        val descendants = tree?.entries.orEmpty()
                            .filter { it.relativePath.startsWith(item.relativePath + "/") }
                            .sortedByDescending { it.relativePath.split("/").size }
                            .map { it.uri }
                        if (descendants.isNotEmpty()) TreeStorageManager.deleteMany(context, descendants)
                        TreeStorageManager.delete(context, item.uri)
                    } else TreeStorageManager.delete(context, item.uri)
                    message = if (ok) "مورد حذف شد" else "حذف ممکن نشد؛ ممکن است دسترسی پوشه محدود باشد"
                    dialog = ""
                    refresh()
                } else dialog = ""
            }) { dialog = "" }
            "deleteSelected" -> ConfirmDialog("حذف موارد انتخاب‌شده", if (selected.any { uri -> tree?.entries?.any { it.uri == uri && it.isDirectory } == true }) "تعداد ${selected.size} مورد انتخاب شده. محتویات پوشه‌های انتخاب‌شده نیز حذف خواهد شد. ادامه می‌دهی؟" else "تعداد ${selected.size} مورد برای حذف انتخاب شده. ادامه می‌دهی؟", {
                scope.launch {
                    val currentTree = tree
                    val result = if (currentTree != null) TreeStorageManager.deleteSelection(context, currentTree, selected.toList()) else 0 to selected.size
                    message = "${result.first} مورد حذف شد؛ ${result.second} مورد ناموفق بود"
                    dialog = ""
                    refresh()
                }
            }) { dialog = "" }
            "deleteClean" -> ConfirmDialog("حذف موارد پاک‌سازی", "تعداد ${cleanSelection.size} مورد انتخاب‌شده (شامل محتویات پوشه‌های انتخابی) حذف شود؟", {
                scope.launch {
                    val currentTree = tree
                    val result = if (currentTree != null) TreeStorageManager.deleteSelection(context, currentTree, cleanSelection.toList()) else 0 to cleanSelection.size
                    message = "${result.first} مورد حذف شد؛ ${result.second} مورد ناموفق بود"
                    cleanSelection = emptySet()
                    dialog = ""
                    refresh()
                }
            }) { dialog = "" }
            "moveConfirm" -> ConfirmDialog("انتقال فایل‌ها", if (moveMode) "تعداد ${pendingTransferEntries.size} مورد (فایل/پوشه) به مقصد منتقل می‌شود." else "تعداد ${pendingTransferEntries.size} مورد (فایل/پوشه) در مقصد کپی می‌شود. اصل موارد باقی می‌ماند.", {
                val dest = targetUri
                if (dest != null) scope.launch {
                    var ok = 0
                    var failed = 0
                    val sourceRoot = treeUri
                    if (sourceRoot == dest) {
                        message = "برای جلوگیری از کپی تکراری، مقصد باید پوشه دیگری باشد."
                        dialog = ""
                        return@launch
                    }
                    pendingTransferEntries.forEach { entry ->
                        try {
                            if (sourceRoot != null && TreeStorageManager.copyOrMoveEntry(context, sourceRoot, entry, dest, moveMode)) ok++ else failed++
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
    scanProgress: Float, scanProgressText: String, scanPaused: Boolean,
    onPause: () -> Unit, onCancelScan: () -> Unit,
    onPick: () -> Unit, onRefresh: () -> Unit, onFullRefresh: () -> Unit, onFiles: () -> Unit,
    onCategory: (String) -> Unit, onClean: () -> Unit, onApps: () -> Unit
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text("داشبورد حافظه", color = White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("آمار واقعی پوشه انتخاب‌شده", color = SoftText, fontSize = 12.sp)
        val primaryStats = remember {
            runCatching {
                val stat = StatFs(Environment.getExternalStorageDirectory().absolutePath)
                (stat.blockCountLong * stat.blockSizeLong) to (stat.availableBlocksLong * stat.blockSizeLong)
            }.getOrNull()
        }
        primaryStats?.let { stats ->
            Text("حافظه داخلی دستگاه • کل ${StorageScanner.formatBytes(stats.first)} • مصرف‌شده ${StorageScanner.formatBytes((stats.first - stats.second).coerceAtLeast(0L))} • آزاد ${StorageScanner.formatBytes(stats.second)}", color = SoftText, fontSize = 10.sp)
        }
        Spacer(Modifier.height(12.dp))
        Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text(tree?.rootName ?: "هنوز حافظه‌ای انتخاب نشده", color = White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(if (tree == null) "برای اسکن کامل محدوده مجاز، حافظه داخلی یا microSD را انتخاب کن." else "${tree.files.size} فایل • ${tree.folders.size} پوشه • ${StorageScanner.formatBytes(tree.totalBytes)}", color = SoftText)
                Spacer(Modifier.height(12.dp))
                Button(onClick = onPick, modifier = Modifier.fillMaxWidth(), enabled = !busy) { Text(if (busy) "در حال اسکن..." else "انتخاب حافظه / کارت SD") }
                OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth(), enabled = tree != null && !busy) { Text("اسکن هوشمند (فقط تغییرات)") }
                TextButton(onClick = onFullRefresh, modifier = Modifier.fillMaxWidth(), enabled = tree != null && !busy) { Text("اسکن کامل از ابتدا") }
                if (busy) {
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(progress = { scanProgress }, modifier = Modifier.fillMaxWidth(), color = Purple)
                    Text(scanProgressText, color = SoftText, fontSize = 11.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onPause, modifier = Modifier.weight(1f)) { Text(if (scanPaused) "ادامه اسکن" else "مکث اسکن") }
                        TextButton(onClick = onCancelScan) { Text("توقف", color = Danger) }
                    }
                }
                Text(message, color = SoftText, fontSize = 11.sp)
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("دسته‌بندی فایل‌ها", color = White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        val files = tree?.files.orEmpty()
        fun filesFor(category: String): List<ManagedEntry> = files.filter { entry ->
            val ext = entry.name.substringAfterLast('.', "").lowercase()
            when (category) {
                "همه" -> true
                "تصاویر" -> entry.mimeType?.startsWith("image/") == true
                "ویدئوها" -> entry.mimeType?.startsWith("video/") == true
                "موسیقی" -> entry.mimeType?.startsWith("audio/") == true
                "اسناد" -> ext in setOf("pdf","txt","doc","docx","xls","xlsx","ppt","pptx","odt","rtf")
                "فشرده" -> ext in setOf("zip","rar","7z","tar","gz")
                "APK" -> ext == "apk"
                else -> entry.mimeType?.startsWith("image/") != true && entry.mimeType?.startsWith("video/") != true && entry.mimeType?.startsWith("audio/") != true && ext !in setOf("pdf","txt","doc","docx","xls","xlsx","ppt","pptx","odt","rtf","zip","rar","7z","tar","gz","apk")
            }
        }
        val cats = listOf("همه", "تصاویر", "ویدئوها", "موسیقی", "اسناد", "فشرده", "APK", "سایر")
        cats.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { cat ->
                    val matches = filesFor(cat)
                    val size = matches.sumOf { it.size }
                    Card(Modifier.weight(1f).padding(vertical = 4.dp).clickable { onCategory(cat) }, colors = CardDefaults.cardColors(containerColor = Panel)) {
                        Column(Modifier.padding(13.dp)) {
                            Text(cat, color = White, fontWeight = FontWeight.SemiBold)
                            Text("${matches.size} فایل • ${StorageScanner.formatBytes(size)}", color = SoftText, fontSize = 10.sp)
                        }
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
    selected: Set<Uri>, sortLargeFirst: Boolean, currentFolderPath: String,
    onPick: () -> Unit, onRefresh: () -> Unit, onFilter: (String) -> Unit, onQuery: (String) -> Unit, onBackFolder: () -> Unit,
    onSort: () -> Unit, onToggle: (Uri) -> Unit, onSelectAll: (List<ManagedEntry>) -> Unit,
    onCreateFolder: () -> Unit, onOpen: (ManagedEntry) -> Unit, onRename: (ManagedEntry) -> Unit, onDelete: (ManagedEntry) -> Unit,
    onMove: () -> Unit, onCopy: () -> Unit, onDeleteSelected: () -> Unit, onOpenClean: () -> Unit
) {
    val filtered = tree?.entries.orEmpty().filter { entry ->
        val inCurrentFolder = if (query.isNotBlank()) true else entry.relativePath.substringBeforeLast("/", "") == currentFolderPath
        val categoryOk = when (filter) {
            "تصاویر" -> entry.mimeType?.startsWith("image/") == true
            "ویدئوها" -> entry.mimeType?.startsWith("video/") == true
            "موسیقی" -> entry.mimeType?.startsWith("audio/") == true
            "اسناد" -> entry.name.substringAfterLast('.', "").lowercase() in setOf("pdf","txt","doc","docx","xls","xlsx","ppt","pptx","odt","rtf")
            "فشرده" -> entry.name.substringAfterLast('.', "").lowercase() in setOf("zip","rar","7z","tar","gz")
            "APK" -> entry.name.endsWith(".apk", true)
            "سایر" -> entry.mimeType?.startsWith("image/") != true && entry.mimeType?.startsWith("video/") != true && entry.mimeType?.startsWith("audio/") != true && entry.name.substringAfterLast('.', "").lowercase() !in setOf("pdf","txt","doc","docx","xls","xlsx","ppt","pptx","odt","rtf","zip","rar","7z","tar","gz","apk")
            else -> true
        }
        inCurrentFolder && categoryOk && entry.name.contains(query, true)
    }.let { if (sortLargeFirst) it.sortedWith(compareByDescending<ManagedEntry> { e -> e.isDirectory }.thenByDescending { e -> e.size }) else it.sortedBy { e -> e.name.lowercase() } }

    Column(Modifier.fillMaxSize()) {
        Text("مدیریت فایل‌ها", color = White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
        if (currentFolderPath.isNotBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBackFolder) { Text("← بازگشت از: $currentFolderPath", color = SoftText) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onPick, modifier = Modifier.weight(1f), enabled = !busy) { Text("انتخاب حافظه") }
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
            TextButton(onClick = { onSelectAll(filtered) }, enabled = !busy) { Text(if (selected.size == filtered.size && filtered.isNotEmpty()) "لغو انتخاب" else "انتخاب همه") }
            TextButton(onClick = onCreateFolder, enabled = !busy) { Text("پوشه جدید") }
        }
        if (selected.isNotEmpty()) {
            Text("انتخاب‌شده: ${selected.size}", color = Purple)
            Row {
                TextButton(onClick = onMove, enabled = !busy) { Text("انتقال") }
                TextButton(onClick = onDeleteSelected, enabled = !busy) { Text("حذف گروهی", color = Danger) }
            }
        }
        Text(message, color = SoftText, fontSize = 10.sp)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            filtered.forEach { item ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = item.uri in selected, onCheckedChange = { onToggle(item.uri) }, enabled = !busy)
                    Text(if (item.isDirectory) "📁" else "📄", modifier = Modifier.padding(end = 7.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.name, color = White, fontSize = 12.sp, maxLines = 1, modifier = Modifier.clickable(enabled = !busy) { onOpen(item) })
                        Text(if (item.isDirectory) item.relativePath else "${StorageScanner.formatBytes(item.size)} • ${item.relativePath}", color = SoftText, fontSize = 10.sp, maxLines = 1)
                    }
                    IconButton(onClick = { onRename(item) }, enabled = !busy) { Icon(Icons.Rounded.Edit, "تغییر نام", tint = Purple) }
                    IconButton(onClick = { onDelete(item) }, enabled = !busy) { Icon(Icons.Rounded.Delete, "حذف", tint = Danger) }
                }
                Divider(color = Color(0xFF252C43))
            }
            if (tree == null) Text("ابتدا یک پوشه یا کارت حافظه انتخاب کن.", color = SoftText, modifier = Modifier.padding(16.dp))
        }
        TextButton(onClick = onOpenClean, modifier = Modifier.fillMaxWidth()) { Text("رفتن به پاک‌سازی هوشمند") }
    }
}


private data class CleanupData(
    val large: List<ManagedEntry> = emptyList(),
    val largeFolders: List<ManagedEntry> = emptyList(),
    val empty: List<ManagedEntry> = emptyList(),
    val tempFiles: List<ManagedEntry> = emptyList(),
    val oldFiles: List<ManagedEntry> = emptyList()
)

@Composable
private fun CleanupScreen(
    tree: ManagedTree?, duplicateGroups: List<List<ManagedEntry>>, similarGroups: List<List<ManagedEntry>>, sameNameGroups: List<List<ManagedEntry>>, busy: Boolean,
    duplicateBusy: Boolean, duplicateChecked: Boolean, similarBusy: Boolean, similarChecked: Boolean,
    sameNameBusy: Boolean, sameNameChecked: Boolean, selection: Set<Uri>,
    onPick: () -> Unit, onRefresh: () -> Unit, onFindDuplicates: () -> Unit, onFindSimilar: () -> Unit, onFindSameNames: () -> Unit,
    onToggle: (Uri) -> Unit, onToggleAll: (List<ManagedEntry>) -> Unit, onOpen: (ManagedEntry) -> Unit, onDeleteSelected: () -> Unit, onApps: () -> Unit
) {
    // Build cleanup categories away from the UI thread and only when the scan tree changes.
    val cleanupData by produceState(CleanupData(), tree) {
        value = withContext(Dispatchers.Default) {
            val entries = tree?.entries.orEmpty()
            val files = entries.filter { !it.isDirectory }
            val folders = entries.filter { it.isDirectory }
            val cutoff = System.currentTimeMillis() - 365L * 24L * 60L * 60L * 1000L
            CleanupData(
                large = files.sortedByDescending { it.size }.take(30),
                largeFolders = folders.asSequence().filter { it.size > 0L }.sortedByDescending { it.size }.take(20).toList(),
                empty = tree?.emptyFolders.orEmpty(),
                tempFiles = files.filter { it.name.substringAfterLast('.', "").lowercase() in setOf("tmp", "temp", "log", "cache", "bak", "old") },
                oldFiles = files.filter { it.modified > 0L && it.modified < cutoff }
            )
        }
    }
    val large = cleanupData.large
    val largeFolders = cleanupData.largeFolders
    val empty = cleanupData.empty
    val duplicateItems = remember(duplicateGroups) { duplicateGroups.flatten() }
    val sameNameItems = remember(sameNameGroups) { sameNameGroups.flatten() }
    val similarItems = remember(similarGroups) { similarGroups.flatten() }
    val tempFiles = cleanupData.tempFiles
    val oldFiles = cleanupData.oldFiles
    val sections = listOf("پوشه‌های خالی", "فایل‌های تکراری", "فایل‌های هم‌نام", "نام‌های مشابه", "فایل‌های موقت", "فایل‌های قدیمی", "فایل‌های حجیم", "پوشه‌های حجیم")
    var activeSection by remember { mutableStateOf("پوشه‌های خالی") }
    var shownCount by remember(activeSection) { mutableIntStateOf(80) }
    val items = when (activeSection) {
        "پوشه‌های خالی" -> empty
        "فایل‌های تکراری" -> duplicateItems
        "فایل‌های هم‌نام" -> sameNameItems
        "نام‌های مشابه" -> similarItems
        "فایل‌های موقت" -> tempFiles
        "فایل‌های قدیمی" -> oldFiles
        "فایل‌های حجیم" -> large
        else -> largeFolders
    }
    val selectedEntries = remember(tree, selection) { tree?.entries.orEmpty().filter { it.uri in selection } }
    val selectedTopLevel = selectedEntries.filterNot { entry ->
        selectedEntries.any { parent -> parent.isDirectory && entry.relativePath.startsWith(parent.relativePath + "/") }
    }
    val selectedBytes = selectedTopLevel.sumOf { it.size }
    val allSelected = items.isNotEmpty() && items.all { it.uri in selection }
    val sectionBusy = busy || duplicateBusy || similarBusy || sameNameBusy
    Column(Modifier.fillMaxSize()) {
        Text("پاک‌سازی هوشمند", color = White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
        Text("هر دسته جداگانه نمایش داده می‌شود؛ برای جلوگیری از هنگ، حداکثر ۸۰ مورد در هر مرحله بارگذاری می‌شود.", color = SoftText, fontSize = 11.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onPick, modifier = Modifier.weight(1f), enabled = !busy) { Text("انتخاب حافظه") }
            OutlinedButton(onClick = onRefresh, enabled = tree != null && !busy) { Text("اسکن دوباره") }
        }
        Text("دسته‌بندی پاک‌سازی", color = White, fontWeight = FontWeight.Bold)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            sections.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
                    pair.forEach { section ->
                        val count = when (section) {
                            "پوشه‌های خالی" -> empty.size
                            "فایل‌های تکراری" -> if (duplicateChecked) duplicateItems.size else null
                            "فایل‌های هم‌نام" -> if (sameNameChecked) sameNameItems.size else null
                            "نام‌های مشابه" -> if (similarChecked) similarItems.size else null
                            "فایل‌های موقت" -> tempFiles.size
                            "فایل‌های قدیمی" -> oldFiles.size
                            "فایل‌های حجیم" -> large.size
                            else -> largeFolders.size
                        }
                        Card(
                            Modifier.weight(1f).padding(vertical = 3.dp).clickable { activeSection = section },
                            colors = CardDefaults.cardColors(containerColor = if (activeSection == section) Color(0xFF302451) else Panel),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(section, color = White, fontSize = 11.sp, modifier = Modifier.weight(1f))
                                Text(count?.toString() ?: "—", color = Purple, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(8.dp))
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(14.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(activeSection, color = White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Checkbox(checked = allSelected, onCheckedChange = { onToggleAll(items) }, enabled = items.isNotEmpty() && !sectionBusy)
                        Text("انتخاب همه", color = SoftText, fontSize = 10.sp)
                    }
                    when (activeSection) {
                        "فایل‌های تکراری" -> {
                            Text("تطبیق محتوای واقعی با SHA-256", color = SoftText, fontSize = 10.sp)
                            OutlinedButton(onClick = onFindDuplicates, enabled = tree != null && !sectionBusy, modifier = Modifier.fillMaxWidth()) {
                                Text(if (duplicateBusy) "در حال بررسی..." else if (duplicateChecked) "بررسی دوباره تکراری‌ها" else "شروع بررسی تکراری‌ها")
                            }
                        }
                        "فایل‌های هم‌نام" -> OutlinedButton(onClick = onFindSameNames, enabled = tree != null && !sectionBusy, modifier = Modifier.fillMaxWidth()) {
                            Text(if (sameNameBusy) "در حال بررسی..." else "بررسی فایل‌های هم‌نام")
                        }
                        "نام‌های مشابه" -> {
                            OutlinedButton(onClick = onFindSimilar, enabled = tree != null && !sectionBusy, modifier = Modifier.fillMaxWidth()) {
                                Text(if (similarBusy) "در حال بررسی..." else "بررسی نام‌های مشابه")
                            }
                            Text("نام‌های مشابه لزوماً فایل تکراری نیستند؛ قبل از حذف بررسی کن.", color = SoftText, fontSize = 10.sp)
                        }
                        "پوشه‌های خالی" -> Text("فقط پوشه‌هایی که هنگام اسکن خالی تشخیص داده شده‌اند.", color = SoftText, fontSize = 10.sp)
                        "فایل‌های موقت" -> Text("پسوندهای موقت، گزارش، کش و پشتیبان؛ قبل از حذف بررسی کن.", color = SoftText, fontSize = 10.sp)
                        "فایل‌های قدیمی" -> Text("فایل‌هایی که بیش از یک سال از آخرین تغییرشان گذشته است.", color = SoftText, fontSize = 10.sp)
                        else -> Text("موارد بر اساس حجم مرتب شده‌اند.", color = SoftText, fontSize = 10.sp)
                    }
                    Text("${items.size} مورد", color = Purple, fontSize = 11.sp)
                    if (items.isEmpty()) {
                        Text(if (tree == null) "ابتدا حافظه را اسکن کن." else "موردی پیدا نشد یا هنوز بررسی این بخش اجرا نشده است.", color = SoftText, fontSize = 11.sp)
                    } else {
                        items.take(shownCount).forEach { CleanupItem(it, selection, onToggle, onOpen, enabled = !sectionBusy) }
                        if (items.size > shownCount) {
                            OutlinedButton(onClick = { shownCount += 80 }, modifier = Modifier.fillMaxWidth()) {
                                Text("نمایش ${minOf(80, items.size - shownCount)} مورد بعدی")
                            }
                        }
                    }
                }
            }
            if (selection.isNotEmpty()) {
                Text("انتخاب‌شده: ${selection.size} مورد • فضای قابل آزادسازی: ${StorageScanner.formatBytes(selectedBytes)}", color = Purple, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Button(onClick = onDeleteSelected, enabled = !sectionBusy, colors = ButtonDefaults.buttonColors(containerColor = Danger), modifier = Modifier.fillMaxWidth()) {
                    Text("حذف ${selection.size} مورد انتخاب‌شده")
                }
            }
            ActionCard("کش و داده برنامه‌ها", "حذف مستقیم داده برنامه‌های دیگر محدودیت اندروید دارد.", onApps)
        }
    }
}

@Composable
private fun CleanupItem(item: ManagedEntry, selection: Set<Uri>, onToggle: (Uri) -> Unit, onOpen: (ManagedEntry) -> Unit, enabled: Boolean = true) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = item.uri in selection, onCheckedChange = { onToggle(item.uri) }, enabled = enabled)
        Column(Modifier.weight(1f)) {
            Text(item.name, color = White, fontSize = 11.sp, maxLines = 1)
            Text(if (item.isDirectory) "پوشه" else StorageScanner.formatBytes(item.size), color = SoftText, fontSize = 10.sp)
        }
        if (!item.isDirectory) TextButton(onClick = { onOpen(item) }, enabled = enabled) { Text("بازکردن", color = Purple, fontSize = 11.sp) }
    }
}

@Composable
private fun AppsScreen(onAppSettings: () -> Unit, onStorageSettings: () -> Unit, onPermissions: () -> Unit) {
    val context = LocalContext.current
    var appQuery by remember { mutableStateOf("") }
    var appListRevision by remember { mutableIntStateOf(0) }
    val apps = remember(appListRevision) {
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        context.packageManager.queryIntentActivities(launcherIntent, 0)
            .mapNotNull { it.activityInfo?.applicationInfo }
            .distinctBy { it.packageName }
            .filter { it.packageName != context.packageName }
            .sortedBy { it.loadLabel(context.packageManager).toString().lowercase() }
    }
    val visibleApps = apps.filter {
        it.loadLabel(context.packageManager).toString().contains(appQuery, true) ||
            it.packageName.contains(appQuery, true)
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text("مدیریت برنامه‌ها", color = White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
        Text("${apps.size} برنامه قابل مشاهده در فهرست برنامه‌های اجراشدنی", color = SoftText, fontSize = 11.sp)
        OutlinedTextField(value = appQuery, onValueChange = { appQuery = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("جستجوی برنامه") })
        TextButton(onClick = { appListRevision++ }) { Text("بروزرسانی فهرست برنامه‌ها") }
        ActionCard("مدیریت همه برنامه‌ها", "مشاهده جزئیات، مجوزها و حذف از تنظیمات اندروید", onAppSettings)
        ActionCard("فضای ذخیره‌سازی دستگاه", "بررسی مصرف حافظه و ابزار آزادسازی فضای اندروید", onStorageSettings)
        ActionCard("مجوزهای MEMORA", "مدیریت دسترسی‌های همین برنامه", onPermissions)
        Spacer(Modifier.height(8.dp))
        visibleApps.forEach { app ->
            val label = app.loadLabel(context.packageManager).toString()
            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(14.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text(label, color = White, fontWeight = FontWeight.SemiBold)
                    Text(app.packageName, color = SoftText, fontSize = 10.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = {
                            val launch = context.packageManager.getLaunchIntentForPackage(app.packageName)
                            if (launch != null) context.startActivity(launch)
                        }) { Text("اجرا") }
                        TextButton(onClick = {
                            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}")))
                        }) { Text("اطلاعات و کش") }
                        TextButton(onClick = {
                            context.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}")))
                        }) { Text("حذف", color = Danger) }
                    }
                }
            }
        }
        Text("محدودیت اندروید: پاک‌کردن مستقیم کش یا داده خصوصی برنامه‌های دیگر در اختیار MEMORA نیست؛ دکمه «اطلاعات و کش» صفحه رسمی همان برنامه را باز می‌کند. حذف نیز با تأیید خود اندروید انجام می‌شود.", color = SoftText, fontSize = 11.sp)
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
