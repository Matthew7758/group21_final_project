package com.group21.android.mp

import android.Manifest
import android.accounts.AccountManager.VISIBILITY_VISIBLE
import android.app.DownloadManager
import android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Environment.*
import android.os.Handler
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import android.widget.Toast.LENGTH_SHORT
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.internal.ContextUtils.getActivity
import com.google.gson.Gson
import java.io.File
import java.util.concurrent.TimeUnit


private const val TAG = "MainActivity"

class MainActivity : AppCompatActivity() {
    private lateinit var songRecyclerView: RecyclerView
    private var adapter: SongAdapter? = SongAdapter(emptyList())
    private var songList: List<Song>? = null
    private lateinit var downloadButton: Button

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        songRecyclerView = this.findViewById(R.id.musicList) as RecyclerView
        songRecyclerView.layoutManager = LinearLayoutManager(applicationContext)
        songRecyclerView.adapter = adapter
        downloadButton = this.findViewById(R.id.downloadButton)

        downloadButton.setOnClickListener {
            downloadFile()
        }
        Log.d(TAG,"${getExternalStoragePublicDirectory(DIRECTORY_MUSIC)}/test.mp3")
        val file2 = File(Environment.getExternalStoragePublicDirectory(DIRECTORY_MUSIC),"test.mp3")
        if(file2.exists()) {
            Log.d(TAG,"test.mp3 EXISTS")
            downloadButton.isClickable = false
            downloadButton.isEnabled = false
        }
        checkPerms()
        getAllAudioFromDevice(applicationContext)
        //Toast.makeText(applicationContext, "Permissions granted!", LENGTH_SHORT).show()
        val filename = "save.txt"
        val file = File(applicationContext.filesDir, filename)
        if (file.exists()) {
            //Log.d(TAG, "FILE EXISTS")
            val fileContent =
                applicationContext.openFileInput(filename).bufferedReader().useLines { lines ->
                    lines.fold("") { some, text ->
                        "$some$text"
                    }
                }
            //Log.d(TAG, "FILE CONTENTS = $fileContent")
            //Toast.makeText(applicationContext,"FILE CONTENTS = $fileContent", LENGTH_SHORT).show()
            val songFile = File(fileContent)
            if (songFile.exists()) {
                //Toast.makeText(applicationContext,"SONG $fileContent FOUND!", LENGTH_SHORT).show()
                //Log.d(TAG, "SONG FILE $fileContent EXISTS")
                val intent = Intent(applicationContext, PlayActivity::class.java)
                val gson: Gson = Gson()
                val jsonString = gson.toJson(songList)
                val song: Song? = songList!!.find { it.path == fileContent }

                intent.putExtra("songList", jsonString)
                intent.putExtra("songName", song!!.name)
                intent.putExtra("position", songList!!.indexOf(song))
                startActivity(intent)
            }
        }
        displaySongs(songList)
    }

    private fun downloadFile() {
        val uri = "https://file-examples-com.github.io/uploads/2017/11/file_example_MP3_5MG.mp3".toUri()
        val request: DownloadManager.Request = DownloadManager.Request(uri)
        request.addRequestHeader("Accept", "application/pdf");
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_MUSIC, "test.mp3");
        request.setNotificationVisibility(VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        val downloadManager: DownloadManager = applicationContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        downloadManager.enqueue(request)
        Toast.makeText(applicationContext,"DOWNLOAD FILE CALLED", LENGTH_SHORT).show()
        downloadButton.isEnabled = false
        downloadButton.isClickable = false

        Handler().postDelayed(this::displayDownloadMessage, 2000)
    }
    private fun displayDownloadMessage() {
        Toast.makeText(applicationContext,"Please restart device to allow MediaStore to refresh.", LENGTH_SHORT).show()
    }

    private fun displaySongs(songList: List<Song>?) {
        adapter = SongAdapter(songList)
        songRecyclerView.adapter = adapter
    }

    private inner class SongHolder(view: View) : RecyclerView.ViewHolder(view),
        View.OnClickListener {
        private lateinit var song: Song
        val songImage: ImageView = itemView.findViewById(R.id.songImage)
        val songName: TextView = itemView.findViewById(R.id.songName)
        val songDuration: TextView = itemView.findViewById(R.id.songDuration)

        init {
            itemView.setOnClickListener(this)
        }

        fun bind(song: Song) {
            this.song = song
            songName.isSelected = true
            songName.text = song.name
            val millis = song.duration.toLong()
            songDuration.text = String.format(
                "%02d:%02d:%02d",
                TimeUnit.MILLISECONDS.toHours(millis),
                TimeUnit.MILLISECONDS.toMinutes(millis) -
                        TimeUnit.HOURS.toMinutes(TimeUnit.MILLISECONDS.toHours(millis)), // The change is in this line
                TimeUnit.MILLISECONDS.toSeconds(millis) -
                        TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(millis))
            )
            val art: Bitmap? = getAlbumImage(song.path)
            if (art !== null)
                songImage.setImageBitmap(art)

        }

        override fun onClick(v: View?) {
            val intent = Intent(applicationContext, PlayActivity::class.java)
            val gson: Gson = Gson()
            val jsonString = gson.toJson(songList)

            intent.putExtra("songList", jsonString)
            intent.putExtra("songName", song.name)
            intent.putExtra("position", songList!!.indexOf(song))
            startActivity(intent)
        }
    }

    fun getAlbumImage(path: String): Bitmap? {
        val mmr = MediaMetadataRetriever()
        mmr.setDataSource(path)
        val data = mmr.embeddedPicture
        return if (data != null) BitmapFactory.decodeByteArray(data, 0, data.size) else null
    }

    private inner class SongAdapter(var songs: List<Song>?) : RecyclerView.Adapter<SongHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongHolder {
            val view = layoutInflater.inflate(R.layout.song_item, parent, false)
            return SongHolder(view)
        }

        override fun onBindViewHolder(holder: SongHolder, position: Int) {
            val song = songs?.get(position)
            if (song != null) {
                holder.bind(song)
            }
        }

        override fun getItemCount(): Int {
            return songs!!.size
        }

    }

    private fun getAllAudioFromDevice(context: Context) {
        val tempAudioList: MutableList<Song> = ArrayList()
        val uri: Uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.AudioColumns.DATA,
            MediaStore.Audio.AudioColumns.TITLE,
            MediaStore.Audio.AudioColumns.ALBUM,
            MediaStore.Audio.ArtistColumns.ARTIST,
            MediaStore.Audio.AudioColumns.DURATION
        )
        // if want fetch all files
        val mimeType1 = MimeTypeMap.getSingleton().getMimeTypeFromExtension("mp3")
        val mimeType2 = MimeTypeMap.getSingleton().getMimeTypeFromExtension("m4a")
        val mimeType3 = MimeTypeMap.getSingleton().getMimeTypeFromExtension("flac")
        val mimeType4 = MimeTypeMap.getSingleton().getMimeTypeFromExtension("aac")
        val mimeType5 = MimeTypeMap.getSingleton().getMimeTypeFromExtension("wav")
        val mimeType6 = MimeTypeMap.getSingleton().getMimeTypeFromExtension("ogg")
        val selectionMimeType =
            MediaStore.Audio.AudioColumns.MIME_TYPE + "=? OR " + MediaStore.Audio.AudioColumns.MIME_TYPE + "=?" + "=? OR " + MediaStore.Audio.AudioColumns.MIME_TYPE + "=?" + "=? OR " + MediaStore.Audio.AudioColumns.MIME_TYPE + "=?" + "=? OR " + MediaStore.Audio.AudioColumns.MIME_TYPE + "=?" + "=? OR " + MediaStore.Audio.AudioColumns.MIME_TYPE + "=?"
        val selectionArgsMp3 =
            arrayOf(mimeType1, mimeType2, mimeType3, mimeType4, mimeType5, mimeType6)


        val cursor: Cursor? = context.contentResolver.query(
            uri,
            projection,
            selectionMimeType,
            selectionArgsMp3,
            null
        )
        if (cursor != null) {
            while (cursor.moveToNext()) {
                val audioModel = Song()
                val path: String = cursor.getString(0)
                //Log.d(TAG, path)
                val name: String = cursor.getString(1)
                val album: String = cursor.getString(2)
                val artist: String = cursor.getString(3)
                val duration: String = cursor.getString(4)
                audioModel.name = name
                audioModel.album = album
                audioModel.artist = artist
                audioModel.path = path
                audioModel.duration = duration
                //Log.d("$TAG Name :$name", " Album :$album")
                //Log.d("$TAG Path :$path", " Artist :$artist")
                //Log.d(TAG, duration)
                //Log.d(TAG, path)
                tempAudioList.add(audioModel)
            }
            cursor.close()
        }
        songList = tempAudioList
        return
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun checkPerms() {
        when {
            ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.MANAGE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.INTERNET
            ) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED -> {
                return
            }
            shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE) -> {
                Toast.makeText(applicationContext, "Permissions required!", LENGTH_SHORT).show()
            }
            else -> {
                requestPermissions(
                    arrayOf(
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.RECORD_AUDIO,
                        Manifest.permission.MANAGE_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.INTERNET,
                    ), 69
                )
            }
        }
    }

}