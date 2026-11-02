package io.github.nihil.orca

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import java.util.UUID

enum class TransferState { PREPARING, QUEUED, DOWNLOADING, FINISHING, COMPLETED, FAILED, INTERRUPTED, CANCELLED }
@Entity(tableName = "downloads")
data class DownloadItem(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val url: String, val title: String = "Preparing download", val thumbnail: String = "",
    val duration: Long = 0, val audio: Boolean = false, val height: Int = 0,
    val created: Long = System.currentTimeMillis(), val state: String = TransferState.QUEUED.name,
    val progress: Float = -1f, val uri: String = "", val staging: String = "", val bytes: Long = 0,
    val mime: String = "", val error: String = "", val details: String = ""
)
@Dao
interface DownloadsDao {
    @Query("SELECT * FROM downloads ORDER BY created DESC") fun observe(): Flow<List<DownloadItem>>
    @Query("SELECT * FROM downloads WHERE id = :id") suspend fun get(id: String): DownloadItem?
    @Query("SELECT * FROM downloads") suspend fun all(): List<DownloadItem>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(item: DownloadItem)
    @Query("UPDATE downloads SET state = :state, progress = :progress WHERE id = :id AND state NOT IN ('CANCELLED','INTERRUPTED')") suspend fun progress(id: String, state: String, progress: Float)
    @Query("UPDATE downloads SET state = 'INTERRUPTED', error = 'Interrupted. Tap Resume to continue.' WHERE state IN ('QUEUED','PREPARING','DOWNLOADING','FINISHING')") suspend fun reconcile()
    @Query("UPDATE downloads SET state = 'INTERRUPTED', error = 'Android stopped the queue. Tap Resume to continue.' WHERE state = 'QUEUED'") suspend fun interruptQueued()
    @Query("DELETE FROM downloads WHERE id = :id") suspend fun delete(id: String)
}
@Database(entities = [DownloadItem::class], version = 1, exportSchema = false)
abstract class OrcaDatabase : RoomDatabase() { abstract fun downloads(): DownloadsDao }
private val Context.preferences by preferencesDataStore("settings")
class Preferences(private val context: Context) {
    private val askKey = booleanPreferencesKey("ask_before_download")
    val ask: Flow<Boolean> = context.preferences.data.map { it[askKey] ?: false }
    suspend fun setAsk(value: Boolean) { context.preferences.edit { it[askKey] = value } }
}

class MediaPublisher(private val context: Context) {
    private val resolver get() = context.contentResolver
    fun exists(uri: String): Boolean = runCatching { resolver.openFileDescriptor(Uri.parse(uri), "r")?.use { true } ?: false }.getOrDefault(false)
    fun create(item: DownloadItem, file: File): Pair<String, String> {
        val extension = file.extension.lowercase()
        val mime = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            ?: if (item.audio) "audio/$extension" else "video/$extension"
        val collection = if (item.audio) MediaStore.Audio.Media.EXTERNAL_CONTENT_URI else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val name = safeFileTitle(item.title)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "$name-${item.id}.$extension")
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, if (item.audio) "${Environment.DIRECTORY_MUSIC}/Orca" else "${Environment.DIRECTORY_MOVIES}/Orca")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        // On process death between insertion and Room commit, recover the deterministic name.
        val existing = resolver.query(collection, arrayOf(MediaStore.MediaColumns._ID), "${MediaStore.MediaColumns.DISPLAY_NAME} = ?", arrayOf("$name-${item.id}.$extension"), null)?.use { cursor ->
            if (cursor.moveToFirst()) android.content.ContentUris.withAppendedId(collection, cursor.getLong(0)) else null
        }
        return (existing ?: requireNotNull(resolver.insert(collection, values))).toString() to mime
    }
    fun publish(uri: String, file: File) {
        resolver.openOutputStream(Uri.parse(uri), "wt").use { output -> requireNotNull(output); file.inputStream().use { it.copyTo(output) } }
        check(resolver.update(Uri.parse(uri), ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null) == 1) { "Could not publish the file. Retry to finish saving." }
    }
    fun delete(uri: String) { if (uri.isNotBlank()) resolver.delete(Uri.parse(uri), null, null) }
    fun deleteRequest(item: DownloadItem) {
        val collection = if (item.audio) MediaStore.Audio.Media.EXTERNAL_CONTENT_URI else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        resolver.delete(collection, "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?", arrayOf("%-${item.id}.%"))
    }
}

fun safeFileTitle(title: String): String {
    val cleaned = title.replace(Regex("[\\p{Cntrl}/\\\\:*?\"<>|]"), "_").ifBlank { "Orca" }
    return cleaned.substring(0, cleaned.offsetByCodePoints(0, minOf(48, cleaned.codePointCount(0, cleaned.length))))
}
