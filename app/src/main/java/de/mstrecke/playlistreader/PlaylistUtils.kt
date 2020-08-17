package de.mstrecke.playlistreader

import android.content.ContentResolver
import android.content.ContentValues
import android.provider.MediaStore
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI
import java.io.PrintWriter

/**
 * get playlist ID for playlist name
 *
 * @param context the context
 * @param name name of playlist
 * @return Int or null on error
 */
fun getPlaylistID(context: Context, name: String?): Int? {
    if (name == null || name.length == 0) return null

    var id: Int? = null

    try {

        val cursor: Cursor? = context.getContentResolver().query(
            EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Audio.Playlists._ID),
            MediaStore.Audio.PlaylistsColumns.NAME + "=?", arrayOf(name),
            null
        )

        if (cursor != null && cursor.getCount() >= 1) {
            if (cursor.moveToFirst()) {
                id = cursor.getInt(cursor.getColumnIndex(MediaStore.Audio.Playlists._ID))
            }
        }
        cursor?.close()
    } catch (e: SecurityException) {
        // This shouldn't happen as the app should have requested these permission beforehand
        e.printStackTrace()
    }
    return id
}

/**
 * create a new playlist with playlist name
 *
 * @param context context
 * @param name playlist name
 * @return ID of new playlist or null on error
 */
fun createPlaylist(context: Context, name: String?): Int? {
    if (name == null || name.length == 0) return null

    var id = getPlaylistID(context, name)

    if (id == null) {  // does not exist yet
        try {
            // Store name in MediaStore
            val values = ContentValues(1)
            values.put(MediaStore.Audio.PlaylistsColumns.NAME, name)
            val uri: Uri? = context.getContentResolver().insert(
                EXTERNAL_CONTENT_URI,
                values
            )
            if (uri != null) {
                // Necessary because somehow the MediaStoreObserver is not notified when adding a playlist
                context.getContentResolver().notifyChange(Uri.parse("content://media"), null)
                id = uri.getLastPathSegment()!!.toInt()
            }
        } catch (e: SecurityException) {
            // This shouldn't happen as the app should have requested these permission beforehand
            e.printStackTrace()
        }
    }
    return id
}

/**
 * search paths in MediaStore database
 *
 * @param context context
 * @param paths list of paths
 * @return list of Longs (may be empty)
 *
 * @todo MediaStore.Audio.Media.DATA is marked deprecated.  What will be next?
 */
fun searchPathsInDatabase(context: Context, paths:List<String>) : List<Long> {
    val musicUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

    // invariable parts of the query
    val selectWhat: Array<String> = arrayOf( MediaStore.Audio.Media._ID )
    val where = MediaStore.Audio.Media.IS_MUSIC + "!=0 AND " + MediaStore.Audio.Media.DATA + "=?"

    return paths.map {
        // variable part of the query
        val where_params: Array<String> = arrayOf(it)

        // do the query
        val songCursor = context.contentResolver.query(
            musicUri,
            selectWhat,
            where,
            where_params,
            null
        )

        if (songCursor == null) {
            null
        } else  {
            if (songCursor.moveToFirst())
                songCursor.getLong(0)
            else {
                null
            }
        }
    } .filterNotNull()   // filter unsuccessful lookups
}

/**
 * iter over all audio files and write the absolute filepaths to disc
 *
 * @param context context
 * @param out PrintWriter of the output file
 */
fun dump_all_music_entries(context: Context, out:PrintWriter) {
    val musicUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    val songCursor = context.contentResolver.query(
        musicUri,
        arrayOf( MediaStore.Audio.Media.DATA),
        MediaStore.Audio.Media.IS_MUSIC + "!=0",
        null,
        MediaStore.Audio.Media.DATA + " ASC"
    )
    if (songCursor != null) {
        if (songCursor.moveToFirst()) {
            do {
                out.println(songCursor.getString(0))
            } while ( songCursor.moveToNext())
        }
    }
}

/**
 * add songs to playlist
 *
 * @param context context
 * @param playlistName name of the new (!) playlist
 * @param paths List of strings with paths to songs
 * @return number of inserted items
 */
fun add_songs(context: Context, playlistName:String, paths:List<String>): Int {
    val pathIDs = searchPathsInDatabase(context, paths)

    if (pathIDs.size == 0) {
        return 0
    }

    val playlist_id = createPlaylist(context, playlistName)  // null = error, e.g. playlist exists
    if (playlist_id == null) {
        return 0
    } else {
        // todo: source of volumeName literal "external"?
        val uri = MediaStore.Audio.Playlists.Members.getContentUri("external", playlist_id.toLong())
        val resolver: ContentResolver = context.getContentResolver()

        var cnt: Int = 0
        val contentValues = Array(pathIDs.size) { ContentValues() }
        pathIDs.forEach(
            {
                contentValues[cnt].put(MediaStore.Audio.Playlists.Members.PLAY_ORDER, cnt + 1)
                contentValues[cnt].put(MediaStore.Audio.Playlists.Members.AUDIO_ID, it)
                cnt += 1
            }
        )
        return resolver.bulkInsert(uri, contentValues)  // returns number of inserted entries
    }
}
