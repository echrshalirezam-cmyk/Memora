package com.memora.app
import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object ScanSnapshotStore {
 private const val FILE="memora_scan_snapshot.json"

 fun save(context:Context,tree:ManagedTree,treeUri:Uri) {
  val root=JSONObject().put("rootName",tree.rootName).put("rootUri",treeUri.toString()).put("totalBytes",tree.totalBytes)
  fun array(items:List<ManagedEntry>):JSONArray {
   val a=JSONArray()
   items.forEach { e ->
    a.put(JSONObject().put("name",e.name).put("size",e.size).put("directory",e.isDirectory)
     .put("uri",e.uri.toString()).put("mime",e.mimeType).put("modified",e.modified).put("path",e.relativePath))
   }
   return a
  }
  root.put("entries",array(tree.entries)).put("emptyFolders",array(tree.emptyFolders))
  val target=File(context.filesDir,FILE)
  val temp=File(context.filesDir,"$FILE.tmp")
  temp.writeText(root.toString())
  if (!temp.renameTo(target)) {
   target.writeText(temp.readText())
   temp.delete()
  }
 }

 fun load(context:Context,expectedTreeUri:Uri?=null):ManagedTree? {
  return try {
  val root=JSONObject(File(context.filesDir,FILE).readText())
  if (expectedTreeUri!=null && root.optString("rootUri")!=expectedTreeUri.toString()) return null
  fun read(a:JSONArray):List<ManagedEntry> = (0 until a.length()).map { n ->
   val e=a.getJSONObject(n)
   ManagedEntry(e.getString("name"),e.getLong("size"),e.getBoolean("directory"),Uri.parse(e.getString("uri")),
    e.optString("mime").takeIf { it.isNotBlank() && it!="null" },e.optLong("modified"),e.getString("path"))
  }
  val entries=read(root.getJSONArray("entries"))
  ManagedTree(root.getString("rootName"),entries,read(root.getJSONArray("emptyFolders")),root.getLong("totalBytes"))
 } catch(_:Exception) { null }
 }
}
