package com.memora.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Night = Color(0xFF080B16)
private val Panel = Color(0xFF11172A)
private val Purple = Color(0xFF8B5CF6)
private val Magenta = Color(0xFFE14BCE)
private val SoftText = Color(0xFF9CA8C7)
private val White = Color(0xFFF5F3FF)

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
    var scanning by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(0) }
    var scanResult by remember { mutableStateOf<StorageScanResult?>(null) }
    var scanMessage by remember { mutableStateOf<String?>(null) }\n    var treeResult by remember { mutableStateOf<TreeScanResult?>(null) }\n    var treeScanning by remember { mutableStateOf(false) }\n    var treeMessage by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val runScan: () -> Unit = {
        scanning = true
        scanMessage = null
        scope.launch {
            try {
                scanResult = StorageScanner.scan(context)
                scanMessage = "اسکن با موفقیت انجام شد"
            } catch (e: Exception) {
                scanMessage = "خطا در اسکن: ${e.localizedMessage ?: "دسترسی ممکن نیست"}"
            } finally {
                scanning = false
            }
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.all { it }) runScan()
        else scanMessage = "برای شمارش عکس‌ها، ویدئوها و موسیقی باید مجوز دسترسی را بدهی."
    }
    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                treeScanning = true
                treeMessage = null
                scope.launch {
                    try {
                        treeResult = TreeStorageScanner.scan(context, uri)
                        treeMessage = "اسکن پوشه با موفقیت تمام شد"
                    } catch (e: Exception) {
                        treeMessage = "خطا: ${e.localizedMessage ?: "دسترسی ممکن نیست"}"
                    } finally {
                        treeScanning = false
                    }
                }
            } catch (e: Exception) {
                treeMessage = "امکان دریافت مجوز پوشه وجود نداشت"
            }
        }
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Night,
            surface = Panel,
            primary = Purple,
            secondary = Magenta,
            onBackground = White,
            onSurface = White
        )
    ) {
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color(0xFF10152A), Night, Color(0xFF090B13)))
            )
        ) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)
            ) {
                Spacer(Modifier.height(24.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(46.dp).background(
                            Brush.linearGradient(listOf(Purple, Magenta)), RoundedCornerShape(15.dp)
                        ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Storage, null, tint = White, modifier = Modifier.size(26.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("MEMORA", color = White, fontSize = 22.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                        Text("مدیریت هوشمند حافظه", color = SoftText, fontSize = 12.sp)
                    }
                    IconButton(onClick = { }) {
                        Icon(Icons.Rounded.Settings, "تنظیمات", tint = SoftText)
                    }
                }

                Spacer(Modifier.height(28.dp))
                Text("سلام! 👋", color = White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                Text("وضعیت فضای ذخیره‌سازی دستگاهت", color = SoftText, fontSize = 14.sp)
                Spacer(Modifier.height(18.dp))

                Card(
                    colors = CardDefaults.cardColors(containerColor = Panel.copy(alpha = .96f)),
                    shape = RoundedCornerShape(26.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("حافظه داخلی", color = SoftText, fontSize = 13.sp)
                                Spacer(Modifier.height(5.dp))
                                Text(if (scanResult == null) "آماده اسکن" else "اسکن انجام شد", color = White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                            }
                            Box(
                                Modifier.size(68.dp).background(
                                    Brush.linearGradient(listOf(Purple.copy(alpha = .25f), Magenta.copy(alpha = .18f))),
                                    CircleShape
                                ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Rounded.DonutLarge, null, tint = Magenta, modifier = Modifier.size(36.dp))
                            }
                        }
                        Spacer(Modifier.height(18.dp))
                        if (scanning) {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth().height(9.dp),
                                color = Purple,
                                trackColor = Color(0xFF28304A)
                            )
                        } else {
                            LinearProgressIndicator(
                                progress = { if (scanResult != null) 1f else 0f },
                                modifier = Modifier.fillMaxWidth().height(9.dp),
                                color = Purple,
                                trackColor = Color(0xFF28304A)
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(scanResult?.let { "فضای اشغال‌شده: ${StorageScanner.formatBytes(it.usedBytes)}" } ?: "پس از اسکن، میزان مصرف نمایش داده می‌شود", color = SoftText, fontSize = 11.sp)
                            Text(scanResult?.let { "${it.indexedFileCount} فایل" } ?: "--", color = White, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(20.dp))
                        Button(
                            enabled = !scanning,
                            onClick = {
                                if (StorageScanner.hasRequiredPermissions(context)) {
                                    runScan()
                                } else {
                                    val permissions = StorageScanner.requiredPermissions()
                                    if (permissions.isEmpty()) runScan() else permissionLauncher.launch(permissions)
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Purple)
                        ) {
                            Icon(if (scanning) Icons.Rounded.Pause else Icons.Rounded.Search, null)
                            Spacer(Modifier.width(9.dp))
                            Text(if (scanning) "در حال اسکن..." else if (scanResult != null) "اسکن دوباره" else "شروع اسکن حافظه", fontWeight = FontWeight.Bold)
                        }
                        if (scanning || scanMessage != null) {
                            Spacer(Modifier.height(10.dp))
                            Text(if (scanning) "در حال خواندن فایل‌های فهرست‌شده در حافظه..." else scanMessage.orEmpty(), color = SoftText, fontSize = 11.sp)
                        }
                        scanResult?.let { result ->
                            Spacer(Modifier.height(8.dp))
                            Text("حافظه کل: ${StorageScanner.formatBytes(result.totalBytes)}  •  آزاد: ${StorageScanner.formatBytes(result.freeBytes)}", color = SoftText, fontSize = 11.sp)
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = Panel),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("اسکن پوشه یا کارت حافظه", color = White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(Modifier.height(5.dp))
                        Text("یک پوشه از حافظه داخلی یا microSD انتخاب کن؛ دسترسی آن برای اسکن‌های بعدی حفظ می‌شود.", color = SoftText, fontSize = 11.sp)
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = { folderPicker.launch(null) },
                            enabled = !treeScanning,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Rounded.FolderOpen, null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (treeScanning) "در حال اسکن پوشه..." else "انتخاب پوشه / کارت حافظه")
                        }
                        treeMessage?.let {
                            Spacer(Modifier.height(6.dp))
                            Text(it, color = SoftText, fontSize = 11.sp)
                        }
                        treeResult?.let { tree ->
                            Spacer(Modifier.height(10.dp))
                            Text(tree.rootName, color = White, fontWeight = FontWeight.SemiBold)
                            Text("${tree.fileCount} فایل • ${tree.folderCount} پوشه • ${StorageScanner.formatBytes(tree.totalBytes)}", color = SoftText, fontSize = 12.sp)
                            Spacer(Modifier.height(8.dp))
                            Text("بزرگ‌ترین فایل‌های این پوشه", color = Purple, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            tree.largestFiles.take(5).forEach { file ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                    Text(file.name, color = White, fontSize = 11.sp, modifier = Modifier.weight(1f), maxLines = 1)
                                    Text(StorageScanner.formatBytes(file.size), color = SoftText, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("دسته‌بندی فایل‌ها", color = White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Text("پس از اسکن", color = Purple, fontSize = 12.sp)
                }
                Spacer(Modifier.height(12.dp))
                val result = scanResult
                val categories = listOf(
                    Triple("تصاویر", Icons.Rounded.Image, result?.let { "${it.images.count} فایل • ${StorageScanner.formatBytes(it.images.bytes)}" } ?: "پس از اسکن"),
                    Triple("ویدئوها", Icons.Rounded.VideoLibrary, result?.let { "${it.videos.count} فایل • ${StorageScanner.formatBytes(it.videos.bytes)}" } ?: "پس از اسکن"),
                    Triple("موسیقی", Icons.Rounded.MusicNote, result?.let { "${it.audio.count} فایل • ${StorageScanner.formatBytes(it.audio.bytes)}" } ?: "پس از اسکن"),
                    Triple("اسناد", Icons.Rounded.Description, result?.let { "${it.documents.count} فایل • ${StorageScanner.formatBytes(it.documents.bytes)}" } ?: "پس از اسکن"),
                    Triple("فایل‌های فشرده", Icons.Rounded.FolderZip, result?.let { "${it.archives.count} فایل • ${StorageScanner.formatBytes(it.archives.bytes)}" } ?: "پس از اسکن"),
                    Triple("نصب‌کننده‌ها", Icons.Rounded.Android, result?.let { "${it.apks.count} فایل • ${StorageScanner.formatBytes(it.apks.bytes)}" } ?: "پس از اسکن"),
                    Triple("سایر فایل‌ها", Icons.Rounded.InsertDriveFile, result?.let { "${it.other.count} فایل • ${StorageScanner.formatBytes(it.other.bytes)}" } ?: "پس از اسکن")
                )
                categories.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        pair.forEach { item ->
                            Card(
                                modifier = Modifier.weight(1f).padding(bottom = 10.dp),
                                colors = CardDefaults.cardColors(containerColor = Panel),
                                shape = RoundedCornerShape(18.dp)
                            ) {
                                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(item.second, null, tint = Purple, modifier = Modifier.size(25.dp))
                                    Spacer(Modifier.width(10.dp))
                                    Column {
                                        Text(item.first, color = White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                        Text(item.third, color = SoftText, fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }

                result?.let { scanned ->
                    Spacer(Modifier.height(8.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Panel),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text("بزرگ‌ترین فایل‌های پیدا شده", color = White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(10.dp))
                            if (scanned.largestFiles.isEmpty()) {
                                Text("فایلی در فهرست رسانه‌ای دستگاه پیدا نشد.", color = SoftText, fontSize = 12.sp)
                            } else {
                                scanned.largestFiles.take(10).forEachIndexed { index, file ->
                                    Row(
                                        Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("${index + 1}.", color = Purple, fontSize = 12.sp)
                                        Spacer(Modifier.width(8.dp))
                                        Text(file.name, color = White, fontSize = 12.sp, modifier = Modifier.weight(1f), maxLines = 1)
                                        Text(StorageScanner.formatBytes(file.size), color = SoftText, fontSize = 11.sp)
                                    }
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Text("تعداد کل موارد فهرست‌شده: ${scanned.indexedFileCount}", color = SoftText, fontSize = 11.sp)
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF17152B)),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.AutoAwesome, null, tint = Magenta, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("پاک‌سازی هوشمند", color = White, fontWeight = FontWeight.Bold)
                            Text("نمایش فایل‌های بزرگ در گزارش اسکن؛ حذف امن در نسخه بعد", color = SoftText, fontSize = 11.sp)
                        }
                        Spacer(Modifier.weight(1f))
                        Icon(Icons.Rounded.ChevronLeft, null, tint = SoftText)
                    }
                }
                Spacer(Modifier.height(24.dp))
                NavigationBar(containerColor = Color(0xFF0D1222)) {
                    NavigationBarItem(
                        selected = selectedTab == 0, onClick = { selectedTab = 0 },
                        icon = { Icon(Icons.Rounded.Dashboard, null) }, label = { Text("خانه") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1, onClick = { selectedTab = 1 },
                        icon = { Icon(Icons.Rounded.Folder, null) }, label = { Text("فایل‌ها") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 2, onClick = { selectedTab = 2 },
                        icon = { Icon(Icons.Rounded.CleaningServices, null) }, label = { Text("پاک‌سازی") }
                    )
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}
