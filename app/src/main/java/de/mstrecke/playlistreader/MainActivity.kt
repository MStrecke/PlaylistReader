package de.mstrecke.playlistreader

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.io.InputStream
import java.io.PrintWriter

// request_codes for onActivityResult

val RCBASE = 12345
val REQUEST_CODE_PERMISSION_REQUEST = RCBASE + 1   // answer to request for write permissions
val REQUEST_CODE_READ_PLAYLIST = RCBASE + 2        // answer to button press "READ PLAYLIST"
val REQUEST_CODE_DUMP = RCBASE + 3                 // answer to button press "WRITE ENTRIES"

// val LOGTAG = "abcd"

class MainActivity : AppCompatActivity() {
    var readwrite_permission_granted = false

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        // Log.i(LOGTAG, "onAct $requestCode - $resultCode")
        when (requestCode) {

            REQUEST_CODE_PERMISSION_REQUEST -> {
                if (resultCode == Activity.RESULT_OK) {
                    readwrite_permission_granted = true
                }
            }

            REQUEST_CODE_READ_PLAYLIST -> {
                if (resultCode == Activity.RESULT_OK) {
                    data?.data?.also { uri ->
                        getContentResolver().openInputStream(uri)?.let {
                            process_playlist(this, uri, it)
                        }
                    }
                }
            }

            REQUEST_CODE_DUMP -> {
                if (resultCode == Activity.RESULT_OK) {
                    data?.data?.also { uri ->
                        getContentResolver().openOutputStream(uri)?.let {
                                val outp = PrintWriter(it)
                                dump_all_music_entries(this, outp)
                                outp.close()
                        }
                    }
                }
            }

        }
    }

    /**
     * extract playlist name from uri
     *
     * @param uri playlist uri
     * @return name of playlist or null on error
     */
    fun getPlaylistnameFromUri(uri: Uri): String? {
        // URI: content://com.android.externalstorage.documents/document/primary%3APlaylists%2F12345.m3u
        val lps = uri.getPath()
        // LPS: primary:Playlists/12345.m3u  (or null)
        if (lps == null) return null
        return File(lps!!).nameWithoutExtension
    }

    // convenience function to display results

    val ALERT_TYPE_WARNING = 2
    val ALERT_TYPE_ERROR = 1
    val ALERT_TYPE_INFO = 0

    fun display_alert(title:String, message: String, alertType:Int=ALERT_TYPE_INFO) {
        val iconid =  when (alertType) {
            ALERT_TYPE_ERROR -> android.R.drawable.ic_dialog_alert
            ALERT_TYPE_INFO -> android.R.drawable.ic_dialog_info
            else -> android.R.drawable.ic_dialog_alert
        }

        val builder = AlertDialog.Builder(this)
        builder
            .setMessage(message)
            .setTitle(title)
            .setIcon(iconid)
            .setPositiveButton(getString(R.string.dialog_neutral_button)) { dialog, which -> }
            .create()
            .show()
    }

    fun process_playlist(context: Context, playlistUri: Uri, playlistStream: InputStream) {
        val playlistName = getPlaylistnameFromUri(playlistUri)

        if (playlistName == null) {
            display_alert(getString(R.string.dialog_title_error), getString(R.string.dialog_message_playlist_name_not_found), alertType = ALERT_TYPE_ERROR)
            return
        }

        if (getPlaylistID(context, playlistName) != null) {
            display_alert(getString(R.string.dialog_title_error), getString(R.string.dialog_message_playlist_x_already_exists).format(playlistName), alertType = ALERT_TYPE_WARNING)
            return
        }
        val txt = playlistStream.readBytes().toString(Charsets.UTF_8)
        val pathList = txt.split("\n").map { it.trim() } .filter { it.isNotEmpty() && ! it.startsWith("#")}
        if (pathList.size == 0) {
            display_alert(getString(R.string.dialog_title_error), getString(R.string.dialog_message_no_valid_entries), alertType = ALERT_TYPE_ERROR)
        } else {
            val imported = add_songs(this, playlistName, pathList)
            if (imported == pathList.size) {
                display_alert(getString(R.string.dialog_title_success), getString(R.string.import_result_full).format(playlistName, imported), alertType = ALERT_TYPE_INFO)
            } else {
                if (imported == 0) {
                    display_alert(getString(R.string.dialog_title_error), getString(R.string.dialog_message_no_matching_entries), alertType = ALERT_TYPE_ERROR)
                } else {
                    display_alert(getString(R.string.dialog_title_warning), getString(R.string.import_result_part).format(
                        playlistName,
                        pathList.size,
                        imported
                    ), alertType = ALERT_TYPE_WARNING)
                }
            }
        }
    }

    /**
     * gets a SAF compatible handle via onActivityResult for writing
     *
     * @param filename base filename
     * @param mimeType mime type of file
     * @param requestCode request code to use in onActivityResult
     */
    private fun createFile(filename:String, mimeType:String, requestCode:Int) {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = mimeType
            putExtra(Intent.EXTRA_TITLE, filename)

            // Optionally, specify a URI for the directory that should be opened in
            // the system file picker before your app creates the document.
            // /!\ needs min API 26
            // putExtra(DocumentsContract.EXTRA_INITIAL_URI, pickerInitialUri)
        }
        startActivityForResult(intent, requestCode)
    }

    /**
     * gets a SAF compatible handle via onActivityResult for reading
     *
     * @param mimeType mime type of file
     * @param requestCode requestCode for onActivityResult
     */
    private fun openFile(mimeType:String, requestCode:Int) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = mimeType
        }
        startActivityForResult(intent, requestCode)
    }

    /**
     * request permissions from user
     *
     * @param context
     */
    fun requestPermissions(context: Context) {
        readwrite_permission_granted = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        // Log.i(LOGTAG, "write perm " + write_permission_granted.toString())
        if (! this.readwrite_permission_granted) {

            ActivityCompat.requestPermissions(this,
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE),
                    REQUEST_CODE_PERMISSION_REQUEST)
        }
    }

    fun checkPermissions(): Boolean {
        return readwrite_permission_granted
    }

    /**
     * function called when pressing READ PLAYLIST button
     *
     * @param view
     */
    fun start_read_file(view: View) {
        requestPermissions(view.context)
        if (checkPermissions()) {
            openFile("*/*", REQUEST_CODE_READ_PLAYLIST)
        } else {
            Toast.makeText(view.context, getString(R.string.error_missing_permissions), Toast.LENGTH_LONG).show()
        }
    }

    /**
     * function called when pressing WRITE ENTRIES button
     *
     * Uses "playlist.txt" aus default.
     * /!\ If the name already exists it will be renamed to xxx(1) xxx(2) etc.
     *
     * @param view
     */
    fun dump_all_entries(view: View) {
        requestPermissions(view.context)
        if (checkPermissions()) {
            createFile("playlist.txt", "*/", REQUEST_CODE_DUMP)
        } else {
            Toast.makeText(view.context, getString(R.string.error_missing_permissions), Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val version_tv = findViewById(R.id.version_tv) as TextView
        version_tv.text = getString(R.string.version_str).format(BuildConfig.VERSION_NAME)

        val btn = findViewById(R.id.file_btn) as Button
        val dump_btn = findViewById(R.id.dump_btn) as Button

        btn.setOnClickListener {
            start_read_file(it)
        }
        dump_btn.setOnClickListener {
            dump_all_entries(it)
        }
    }
}