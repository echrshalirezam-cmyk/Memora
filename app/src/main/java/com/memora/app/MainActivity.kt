package com.memora.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
                                Text("آماده اسکن", color = White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
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
                        LinearProgressIndicator(
                            progress = { 0f },
                            modifier = Modifier.fillMaxWidth().height(9.dp),
                            color = Purple,
                            trackColor = Color(0xFF28304A)
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("پس از اسکن، میزان مصرف نمایش داده می‌شود", color = SoftText, fontSize = 11.sp)
                            Text("--", color = White, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(20.dp))
                        Button(
                            onClick = { scanning = !scanning },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Purple)
                        ) {
                            Icon(if (scanning) Icons.Rounded.Pause else Icons.Rounded.Radar, null)
                            Spacer(Modifier.width(9.dp))
                            Text(if (scanning) "توقف اسکن" else "شروع اسکن حافظه", fontWeight = FontWeight.Bold)
                        }
                        if (scanning) {
                            Spacer(Modifier.height(10.dp))
                            Text("زیرساخت اسکن در مرحله بعدی فعال می‌شود.", color = SoftText, fontSize = 11.sp)
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
                val categories = listOf(
                    Triple("تصاویر", Icons.Rounded.Image, "۰ فایل"),
                    Triple("ویدئوها", Icons.Rounded.VideoLibrary, "۰ فایل"),
                    Triple("موسیقی", Icons.Rounded.MusicNote, "۰ فایل"),
                    Triple("اسناد", Icons.Rounded.Description, "۰ فایل"),
                    Triple("فایل‌های فشرده", Icons.Rounded.FolderZip, "۰ فایل"),
                    Triple("برنامه‌ها", Icons.Rounded.Android, "۰ برنامه")
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
                            Text("یافتن فایل‌های حجیم، تکراری و قدیمی", color = SoftText, fontSize = 11.sp)
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
