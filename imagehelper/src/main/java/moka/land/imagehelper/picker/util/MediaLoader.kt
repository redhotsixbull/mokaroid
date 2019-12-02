package moka.land.imagehelper.picker.util

import android.annotation.SuppressLint
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import moka.land.imagehelper.picker.conf.MediaType
import moka.land.imagehelper.picker.model.Album
import moka.land.imagehelper.picker.model.Media
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object MediaLoader {

    private const val INDEX_MEDIA_URI = MediaStore.MediaColumns.DATA
    private const val INDEX_DATE_ADDED = MediaStore.MediaColumns.DATE_ADDED
    private const val INDEX_BUCKET_NAME = MediaStore.Images.Media.BUCKET_DISPLAY_NAME

    @SuppressLint("Recycle")
    internal suspend fun get(context: Context, mediaType: MediaType): List<Album> {
        return withContext<List<Album>>(Dispatchers.IO) {
            val uri = when (mediaType) {
                MediaType.IMAGE_ONLY -> {
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                }
                MediaType.VIDEO_ONLY -> {
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                }
                else -> {
                    Uri.EMPTY
                }
            }
            val projection = arrayOf(INDEX_MEDIA_URI, INDEX_BUCKET_NAME, INDEX_DATE_ADDED)
            val selection = MediaStore.Images.Media.SIZE + " > 0"
            val order = "$INDEX_DATE_ADDED DESC"

            val cursor = context.contentResolver.query(uri, projection, selection, null, order)
                ?: return@withContext emptyList()

            Log.wtf("aaaa", "cursor: ${cursor.count}")

            val mediaList = generateSequence { if (cursor.moveToNext()) cursor else null }
                .map { getImage(it) }
                .filterNotNull()
                .toList()

            Log.wtf("aaaa", "mediaList: ${mediaList}")
            Log.wtf("aaaa", "uri: ${uri}, projection: ${projection}, selection: ${selection}, sortOrder: $order")

            val albumList = mediaList
                .asSequence()
                .groupBy { it.album }
                .toSortedMap(Comparator { albumName1: String, albumName2: String ->
                    Log.wtf("MediaLoader", "albumName1: ${albumName1}, albumName2: ${albumName2}")
                    if (albumName2 == "Camera") 1 else albumName1.compareTo(albumName2, true)
                })
                .map { getAlbum(it) }
                .toList()

            cursor.close()
            return@withContext albumList.toMutableList().apply {
                add(0, Album("전체", Media(Uri.EMPTY, "전체", 0).uri, mediaList))
            }
        }
    }

    private fun getAlbum(data: Map.Entry<String, List<Media>>): Album {
        return Album(data.key, data.value[0].uri, data.value)
    }

    private fun getImage(cursor: Cursor): Media? {
        return try {
            cursor.run {
                val folderName = getString(getColumnIndex(INDEX_BUCKET_NAME))
                val mediaPath = getString(getColumnIndex(INDEX_MEDIA_URI))
                val datedAddedSecond = getLong(getColumnIndex(INDEX_DATE_ADDED))
                val mediaUri: Uri = Uri.fromFile(File(mediaPath))
                Media(mediaUri, folderName, datedAddedSecond)
            }
        }
        catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

}