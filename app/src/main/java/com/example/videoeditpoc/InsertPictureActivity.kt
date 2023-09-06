package com.example.videoeditpoc

import android.Manifest
import android.app.Activity
import android.app.ProgressDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.OverlaySettings
import androidx.media3.effect.TextureOverlay
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.TransformationRequest
import androidx.media3.transformer.Transformer
import com.example.videoeditpoc.databinding.ActivityInsertPictureBinding
import com.google.common.collect.ImmutableList
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.math.roundToInt


@UnstableApi
class InsertPictureActivity : AppCompatActivity() {
    lateinit var binding: ActivityInsertPictureBinding
    private var input_video_uri: String? = null
    private var imageUri: Uri? = null
    private var outputFilePath: String? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityInsertPictureBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.selectVideoBtn.setOnClickListener {
            selectVideoLauncher.launch("video/*")
        }

        binding.insertEmojiActBtn.setOnClickListener {
            startActivity(Intent(this, InsertEmojiActivity::class.java))
        }

        binding.insertPictureBtn.setOnClickListener {

            if (input_video_uri == null) {
                Toast.makeText(this, "Select video", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(
                    this, Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                insertImage()
            } else {
                ActivityCompat.requestPermissions(
                    this, arrayOf(
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ), 1
                )
            }
        }

    }

    private var imageLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                imageUri = result.data!!.data
                Log.d("imageUri", "imageUri: $imageUri")
                val bitmap: Bitmap = if (Build.VERSION.SDK_INT >= 29) {
                    // To handle deprecation use
                    ImageDecoder.decodeBitmap(
                        ImageDecoder.createSource(
                            contentResolver,
                            imageUri!!
                        )
                    )
                } else {
                    // Use older version
                    MediaStore.Images.Media.getBitmap(contentResolver, imageUri)
                }

                Toast.makeText(this, "bitmap: $bitmap", Toast.LENGTH_LONG).show()

                val scaledBitmap = scaleDownImage(bitmap, maxImageHeight = 96f)
                Toast.makeText(this, "scaledBitmap:$scaledBitmap", Toast.LENGTH_LONG).show()
                val resultantBitmapFilePath = saveBitmap(scaledBitmap)
                Toast.makeText(this, "path: $resultantBitmapFilePath", Toast.LENGTH_LONG).show()
                val mediaItem = MediaItem.Builder().setUri(input_video_uri).build()
                createTransformation(mediaItem, resultantBitmapFilePath)
            }
        }

    private fun encodeImage(bm: Bitmap): String? {
        val baos = ByteArrayOutputStream()
        bm.compress(Bitmap.CompressFormat.PNG, 100, baos)
        val b = baos.toByteArray()
        return Base64.encodeToString(b, Base64.DEFAULT)
    }

    private fun scaleDownImage(
        realImage: Bitmap, maxImageHeight: Float
    ): Bitmap {
        return if (realImage.height <= maxImageHeight) {
            realImage
        } else {

            val ratio = Math.min(
                maxImageHeight / realImage.width,
                maxImageHeight / realImage.height
            )
            val width = (ratio * realImage.width).roundToInt()
            val height = (ratio * realImage.height).roundToInt()

            Bitmap.createScaledBitmap(
                realImage, width,
                height, true
            )
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

                            insertImage()
                            Toast.makeText(this, "Permission Granted", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(this, "Permission denied", Toast.LENGTH_LONG).show()
                        }
                    } else {

                        if (input_video_uri != null) {
                            insertImage()
                        } else {
                            Toast.makeText(
                                this@InsertPictureActivity, "Please upload video", Toast.LENGTH_LONG
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

    private val selectVideoLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) {
            it?.let {
                input_video_uri = it.toString()
                Toast.makeText(
                    this,
                    "video loaded successfully: $input_video_uri",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }


    private fun insertImage() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        imageLauncher.launch(intent)
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

    private fun saveBitmap(bitmap: Bitmap): String? {
        val currentTimeMillis = System.currentTimeMillis()
        val today = Date(currentTimeMillis)
        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss")
        val fileName: String = "bitmap_" + dateFormat.format(today) + ".png"
        val bitmapDirectory =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).absoluteFile
        if (!bitmapDirectory.exists()) {
            bitmapDirectory.mkdir()
        }
        val file = File(bitmapDirectory, fileName)
        file.createNewFile()
        println("No bitmap file found bitmap file created ${file.absolutePath}")

        try {
            val out = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.flush()
            out.close()
            Toast.makeText(this, "file with bitmap saved", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return file.absolutePath
    }

    private fun createVideoEffects(imageFilePath: String?): ImmutableList<Effect> {
        val effects = ImmutableList.Builder<Effect>()
        val overlayEffect: OverlayEffect = createOverlayEffect(imageFilePath)!!
        effects.add(overlayEffect)
        return effects.build()
    }

    private fun createOverlayEffect(imageFilePath: String?): OverlayEffect? {
        val overLaysBuilder: ImmutableList.Builder<TextureOverlay> = ImmutableList.builder()
        val overlaySettings = OverlaySettings.Builder().build()
        val imageFilePathUri = Uri.fromFile(File(imageFilePath!!))
        val imageOverlay =
            BitmapOverlay.createStaticBitmapOverlay(this, imageFilePathUri, overlaySettings)
        overLaysBuilder.add(imageOverlay)


        val overlays: ImmutableList<TextureOverlay> = overLaysBuilder.build()
        return if (overlays.isEmpty()) null else OverlayEffect(overlays)
    }

    private fun createTransformation(mediaItem: MediaItem, imageFilePath: String?) {

        val inputEditedMediaItem = EditedMediaItem.Builder(mediaItem).setEffects(
            Effects(listOf(), createVideoEffects(imageFilePath))
        ).build()
        val transformer = transformerBuilder()
        outputFilePath = getOutputFilePath()
        Log.d("ita", "createTransformation:$outputFilePath")
        transformer.start(inputEditedMediaItem, outputFilePath!!)
    }

    private fun transformerBuilder(): Transformer {


        val progressDialog = ProgressDialog(this@InsertPictureActivity)
        progressDialog.setCancelable(false)
        progressDialog.setMessage("Applying Filter..")
        progressDialog.setCanceledOnTouchOutside(false)
        progressDialog.show()
        val request = TransformationRequest.Builder().setVideoMimeType(MimeTypes.VIDEO_H264)
            .setAudioMimeType(MimeTypes.AUDIO_AAC).build()
        val transformerListener: Transformer.Listener = object : Transformer.Listener {
            override fun onCompleted(composition: Composition, result: ExportResult) {
                Log.d("vcas", "success")

                progressDialog.dismiss()
                Toast.makeText(this@InsertPictureActivity, "Filter Applied", Toast.LENGTH_SHORT)
                    .show()
            }

            override fun onError(
                composition: Composition, result: ExportResult, exception: ExportException
            ) {
                Log.d("vcae", "fail")
                progressDialog.dismiss()
                Toast.makeText(
                    this@InsertPictureActivity,
                    "Something went wrong",
                    Toast.LENGTH_SHORT
                )
                    .show()
            }
        }
        return Transformer.Builder(this).setTransformationRequest(request)
            .addListener(transformerListener).build()
    }
}