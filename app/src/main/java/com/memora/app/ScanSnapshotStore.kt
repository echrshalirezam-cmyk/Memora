package com.memora.app
import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
object ScanSnapshotStore {
 private const val FILE="memora_scan_snapshot.json"
 fun save(context:Context,tree:ManagedTree) {
  val root=JSONObject().put("rootName",tree.rootName).put("totalBytes",tree.totalBytes)
  fun array(items:List<ManagedEntry>):JSONArray { val a=JSONArray(); items.forEach { e -> a.put(JSONObject().put("name",e.name).put("size",e.size).put("directory",e.isDirectory).put("uri",e.uri.toString()).put("mime",e.mimeType).put("modified",e.modified).put("path",e.relativePath)) }; return a }
  root.put("entries",array(tree.entries)).put("emptyFolders",array(tree.emptyFolders))
  File(context.filesDir,FILE).writeText(root.toString())
 }
 fun load(context:Context):ManagedTree?=try {
  val root=JSONObject(File(context.filesDir,FILE).readText())
  fun read(a:JSONArray):List<ManagedEntry>=(0 until a.length()).map { n -> val e=a.getJSONObject(n); ManagedEntry(e.getString("name"),e.getLong("size"),e.getBoolean("directory"),Uri.parse(e.getString("uri")),e.optString("mime").takeIf { it.isNotBlank() && it!="null" },e.getLong("modified"),e.getString("path")) }
  val entries=read(root.getJSONArray("entries"))
  ManagedTree(root.getString("rootName"),entries,read(root.getJSONArray("emptyFolders")),root.getLong("totalBytes"))
 } catch(_:Exception) { null }
}