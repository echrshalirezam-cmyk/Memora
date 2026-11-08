package com.memora.app
import android.app.*
import android.content.*
import android.net.Uri
import android.os.*
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first

class MemoryScanService : Service() {
 private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
 private val paused = MutableStateFlow(false)
 private var job: Job? = null
 private var wake: PowerManager.WakeLock? = null
 private val prefs by lazy { getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
 override fun onCreate() { super.onCreate(); if (Build.VERSION.SDK_INT >= 26) getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL, "اسکن حافظه", NotificationManager.IMPORTANCE_LOW)) }
 override fun onStartCommand(i: Intent?, flags: Int, id: Int): Int {
  when(i?.action) {
   ACTION_PAUSE -> { paused.value=true; prefs.edit().putBoolean("paused",true).apply(); notifyScan("اسکن مکث شده است") }
   ACTION_RESUME -> { paused.value=false; prefs.edit().putBoolean("paused",false).apply(); notifyScan("اسکن ادامه دارد") }
   ACTION_CANCEL -> { job?.cancel(); stopSelf() }
   ACTION_START -> if(job?.isActive != true) i.getStringExtra(EXTRA_URI)?.let { startScan(Uri.parse(it)) }
  }
  return START_NOT_STICKY
 }
 private fun startScan(uri: Uri) {
  startForeground(NOTIFY_ID, notification("آماده‌سازی اسکن"))
  prefs.edit().putBoolean("busy",true).putBoolean("paused",false).putFloat("progress",0f).putString("progressText","آماده‌سازی اسکن...").putString("status","running").apply()
  paused.value=false
  wake=(getSystemService(POWER_SERVICE) as PowerManager).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, packageName+":MemoryScan").apply { setReferenceCounted(false); acquire(6*60*60*1000L) }
  job=scope.launch {
   try {
    val result=TreeStorageManager.scan(applicationContext,uri,
     pauseCheck={ paused.first { p -> !p } },
     onProgress={ done,total ->
      val pct=if(total>0) done.toFloat()/total else 0f
      val label=if(total>0) "بررسی "+done+" از "+total+" مورد • "+(pct*100).toInt()+"٪" else "پیمایش پوشه‌ها • "+done+" مورد"
      prefs.edit().putFloat("progress",pct).putString("progressText",label).apply()
      notifyScan(label)
     })
    ScanSnapshotStore.save(applicationContext,result)
    prefs.edit().putBoolean("busy",false).putBoolean("paused",false).putFloat("progress",1f).putString("progressText","اسکن کامل شد").putString("status","completed").putInt("fileCount",result.files.size).apply()
    notifyScan("اسکن کامل شد • "+result.files.size+" فایل")
   } catch(e: CancellationException) {
    prefs.edit().putBoolean("busy",false).putBoolean("paused",false).putString("status","cancelled").apply(); notifyScan("اسکن متوقف شد")
   } catch(e: Exception) {
    prefs.edit().putBoolean("busy",false).putBoolean("paused",false).putString("status","error").putString("error",e.localizedMessage ?: "خطای نامشخص").apply(); notifyScan("خطا در اسکن")
   } finally {
    wake?.let { if(it.isHeld) it.release() }; wake=null; job=null
    if(Build.VERSION.SDK_INT>=24) stopForeground(STOP_FOREGROUND_DETACH) else @Suppress("DEPRECATION") stopForeground(false)
    stopSelf()
   }
  }
 }
 private fun pending(action:String, code:Int)=PendingIntent.getService(this,code,Intent(this,MemoryScanService::class.java).setAction(action),PendingIntent.FLAG_UPDATE_CURRENT or if(Build.VERSION.SDK_INT>=23) PendingIntent.FLAG_IMMUTABLE else 0)
 private fun notification(msg:String): Notification {
  val isPaused=paused.value
  return NotificationCompat.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.stat_sys_download)
   .setContentTitle("MEMORA • اسکن حافظه").setContentText(msg).setOngoing(true).setOnlyAlertOnce(true)
   .addAction(0,if(isPaused) "ادامه" else "مکث",pending(if(isPaused) ACTION_RESUME else ACTION_PAUSE,2))
   .addAction(0,"توقف",pending(ACTION_CANCEL,3)).build()
 }
 private fun notifyScan(msg:String) { getSystemService(NotificationManager::class.java).notify(NOTIFY_ID,notification(msg)) }
 override fun onBind(intent:Intent?):IBinder?=null
 override fun onDestroy() { wake?.let { if(it.isHeld) it.release() }; scope.cancel(); super.onDestroy() }
 companion object {
  const val PREFS="memora_scan"; const val CHANNEL="memora_scan_channel"; const val NOTIFY_ID=731
  const val ACTION_START="com.memora.app.scan.START"; const val ACTION_PAUSE="com.memora.app.scan.PAUSE"
  const val ACTION_RESUME="com.memora.app.scan.RESUME"; const val ACTION_CANCEL="com.memora.app.scan.CANCEL"; const val EXTRA_URI="tree_uri"
  fun command(context:Context,action:String,uri:Uri?=null) {
   val i=Intent(context,MemoryScanService::class.java).setAction(action); if(uri!=null)i.putExtra(EXTRA_URI,uri.toString())
   if(Build.VERSION.SDK_INT>=26 && action==ACTION_START) context.startForegroundService(i) else context.startService(i)
  }
 }
}