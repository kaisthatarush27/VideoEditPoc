package com.example.videoeditpoc

import android.Manifest
import android.app.ProgressDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.exoplayer.ExoPlayer
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.example.videoeditpoc.databinding.ActivityInsertGraphicBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date


class InsertGraphicActivity : AppCompatActivity() {
    lateinit var binding: ActivityInsertGraphicBinding
    private var input_video_uri_ffmpeg: String? = null
    private var input_video_uri_media: String? = null
    val handler = Handler(Looper.getMainLooper())
    private var gifUri: Uri? = null
    var filePath: String? = null
    lateinit var exoPlayer: ExoPlayer
    private var outputFilePath: String? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityInsertGraphicBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.selectVideoBtn.setOnClickListener {
            handler.removeCallbacksAndMessages(null)
            selectVideoLauncherUsingFfmpeg.launch("video/*")
        }


        binding.insertGraphicBtn.setOnClickListener {
            if (input_video_uri_ffmpeg == null) {
                Toast.makeText(this, "Select video", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(
                    this, Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                insertGif()
            } else {
                ActivityCompat.requestPermissions(
                    this, arrayOf(
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ), 1
                )
            }
        }

        binding.insertPictureActivityBtn.setOnClickListener {
            startActivity(Intent(this, InsertPictureActivity::class.java))
        }

    }


    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            1 -> {
                if (grantResults.isNotEmpty()) {
                    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R) {
                        var isPermissionsGranted = false
                        if (grantResults[0] == PackageManager.PERMISSION_GRANTED && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                            isPermissionsGranted = true
                        }

                        if (isPermissionsGranted) {

                            insertGif()
                            Toast.makeText(this, "Permission Granted", Toast.LENGTH_LONG).show()
                        } else Toast.makeText(this, "Permission denied", Toast.LENGTH_LONG).show()
                    } else {

                        if (input_video_uri_ffmpeg != null) {
                            insertGif()
                        } else {
                            Toast.makeText(
                                this@InsertGraphicActivity, "Please upload video", Toast.LENGTH_LONG
                            ).show()
                            return
                        }
                    }
                } else {
                    Toast.makeText(this, "Permission denied", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun insertGif() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/gif"
        gifLauncher.launch(intent)
    }

    private fun getTime(seconds: Int): String {
        val hr = seconds / 3600
        val rem = seconds % 3600
        val mn = rem / 60
        val sec = rem % 60
        return String.format("%02d", hr) + ":" + String.format(
            "%02d", mn
        ) + ":" + String.format("%02d", sec)
    }

    private fun executeFfmpegCommand(exe: String, filePath: String) {

        //creating the progress dialog
        val progressDialog = ProgressDialog(this@InsertGraphicActivity)
        progressDialog.setCancelable(false)
        progressDialog.setCanceledOnTouchOutside(false)
        progressDialog.show()

        /*
            Here, we have used he Async task to execute our query because if we use the regular method the progress dialog
            won't be visible. This happens because the regular method and progress dialog uses the same thread to execute
            and as a result only one is a allowed to work at a time.
            By using we Async task we create a different thread which resolves the issue.
         */
        FFmpegKit.executeAsync(exe, { session ->
            val returnCode = session.returnCode
            lifecycleScope.launch(Dispatchers.Main) {
                if (returnCode.isValueSuccess) {
                    //after successful execution of ffmpeg command,
                    //again set up the video Uri in VideoView
                    Log.d("ffmpeg", "execFilePath: $filePath")
                    //change the video_url to filePath, so that we could do more manipulations in the
                    //resultant video. By this we can apply as many effects as we want in a single video.
                    //Actually there are multiple videos being formed in storage but while using app it
                    //feels like we are doing manipulations in only one video
                    outputFilePath = filePath
                    Log.d("ffmpeg", "execInputVideoUri: $input_video_uri_ffmpeg")
                    //play the result video in VideoView
                    progressDialog.dismiss()
                    Toast.makeText(this@InsertGraphicActivity, "Filter Applied", Toast.LENGTH_SHORT)
                        .show()
                } else {
                    progressDialog.dismiss()
                    Log.d("TAG", session.allLogsAsString)
                    Toast.makeText(
                        this@InsertGraphicActivity, "Something Went Wrong!", Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }, { log ->
            lifecycleScope.launch(Dispatchers.Main) {
                progressDialog.setMessage("Applying Filter..${log.message}")
            }
        }) { statistics -> Log.d("STATS", statistics.toString()) }
    }

    private var gifLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->

            if (result.resultCode != RESULT_OK) {
                Toast.makeText(this, "Select a video", Toast.LENGTH_SHORT).show()
                return@registerForActivityResult
            }
            if (result.resultCode == RESULT_OK) {
                gifUri = result.data!!.data
                Log.d("gifuri", "gifUri: $gifUri")
                if (gifUri != null && "content" == gifUri!!.scheme) {
                    val cursor = this.contentResolver.query(
                        gifUri!!, arrayOf(MediaStore.Images.ImageColumns.DATA), null, null, null
                    )
                    cursor!!.moveToFirst()
                    filePath = cursor.getString(0)
                    Log.d("filePathScheme", "filePath: $filePath")
                    cursor.close()
                } else {
                    filePath = gifUri!!.path
                    Log.d("filePathwoScheme", "filePathwosc: $filePath ")
                }
                Log.d("", "Chosen path = $filePath")
                Log.d("gifma", "gif : $gifUri")
//                val folder = cacheDir
//                val file = File(folder, System.currentTimeMillis().toString() + ".mp4")

                outputFilePath = getOutputFilePath()
                val command =
                    "-y -i $input_video_uri_ffmpeg -stream_loop -1 -i ${Uri.fromFile(File(filePath!!))} -filter_complex [0]overlay=x=0:y=0:shortest=1[out] -map [out] -map 0:a? $outputFilePath"
                executeFfmpegCommand(command, outputFilePath!!)
            }
        }


    private val selectVideoLauncherUsingFfmpeg =
        registerForActivityResult(ActivityResultContracts.GetContent()) {
            it?.let {
                input_video_uri_ffmpeg = FFmpegKitConfig.getSafParameterForRead(this, it)
                Toast.makeText(
                    this, "video loaded successfully: $input_video_uri_ffmpeg", Toast.LENGTH_SHORT
                ).show()
            }
        }

    @Throws(IOException::class)
    private fun createExternalCacheFile(fileName: String): File {
        val file = File(externalCacheDir, fileName)
        check(!(file.exists() && !file.delete())) { "Could not delete the previous export output file" }
        check(file.createNewFile()) { "Could not create the export output file" }
        return file
    }

    private fun getOutputFilePath(): String? {

        val currentTimeMillis = System.currentTimeMillis()
        val today = Date(currentTimeMillis)
        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss")
        val fileName: String = "media3_" + dateFormat.format(today) + ".mp4"

        val documentsDirectory =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES).absoluteFile
        Log.d("itadocdir", "getDocDirName:$documentsDirectory")
        val mediaThreeDirectory = File(documentsDirectory, "Media3")
        if (!mediaThreeDirectory.exists()) {
            mediaThreeDirectory.mkdir()
        }
        val file = File(mediaThreeDirectory, fileName)


        file.createNewFile()
        println("No file found file created ${file.absolutePath}")


        return file.absolutePath
    }

}