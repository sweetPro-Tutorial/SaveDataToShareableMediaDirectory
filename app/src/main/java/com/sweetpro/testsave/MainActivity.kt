package com.sweetpro.testsave

import android.content.ContentValues
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.material.snackbar.Snackbar
import com.sweetpro.testsave.databinding.ActivityMainBinding
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.lang.Exception
import java.util.*

class MainActivity : AppCompatActivity() {
    private final val TAG = "MainActivity"

    var publicImageUriString = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // for supporting Android 6+
        resultLauncher.launch(neededRuntimePermissions)

        binding.loadLocalButton.setOnClickListener { loadRawImage() }
        binding.savePublicButton.setOnClickListener { saveToPublicImage() }
        binding.loadPublicButton.setOnClickListener { loadPublicImage() }
    }

    private fun loadRawImage() {
        val imageStream = resources.openRawResource(R.raw.local_image)
        val bitmap = BitmapFactory.decodeStream(imageStream)
        binding.imageLocal.setImageBitmap(bitmap)
    }

    private fun saveToPublicImage() {
        // make empty target file on shareable media storage using MediaStore API contentResolver
        val targetFilename = "publicImage_${Date().time}.jsp"
        val targetUri: Uri? = makeEmptyTargetFile(targetFilename)
        if (targetUri == null) { return }

        publicImageUriString = targetUri.toString()
        Snackbar.make(binding.root, "content uri=${publicImageUriString}", Snackbar.LENGTH_SHORT).show()

        // make copycat to the target file
        val inputStream: InputStream = resources.openRawResource(R.raw.local_image)
        makeCopycat(inputStream, targetUri)
    }

    private fun makeEmptyTargetFile(targetFilename: String): Uri? {
        Log.d(TAG, "makeEmptyTargetFile: targetFilename=${targetFilename}")

        // make empty target file on public storage
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return makeEmptyTargetFile_on10plus(targetFilename)  // using relative path
        } else {
            return makeEmptyTargetFile_below10(targetFilename)   // using absolute pathname
        }
    }

    private fun makeEmptyTargetFile_on10plus(targetFilename: String): Uri? {
        val relativePath = Environment.DIRECTORY_PICTURES + File.separatorChar + getString(R.string.app_name)
        Log.d(TAG, "makeEmptyTargetFile_on10plus: relativePath=${relativePath}")

        val contentValues = ContentValues()
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, targetFilename)
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)  // <<< see here
        
        val contentUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        return contentUri
    }

    private fun makeEmptyTargetFile_below10(targetFilename: String): Uri? {
        val publicDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val absolutePathname = "${publicDirectory.absolutePath}/${targetFilename}"
        Log.d(TAG, "makeEmptyTargetFile_below10: absolutePathname=${absolutePathname}")

        val contentValues = ContentValues()
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, targetFilename)
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        contentValues.put(MediaStore.MediaColumns.DATA, absolutePathname)  // <<< see here

        val contentUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        return contentUri
    }

    private fun makeCopycat(inputStream: InputStream, targetUri: Uri) {
        // can not directly convert uri to file on Android 10,
        // so need to use contentResolver
        val pfd = contentResolver.openFileDescriptor(targetUri, "w")
        try {
            pfd?.use {
                val target = FileOutputStream(pfd.fileDescriptor)
                val buffer = ByteArray(4096)
                var length: Int
                while ( (inputStream.read(buffer).also { length = it }) > 0 ) {
                    target.write(buffer, 0, length)
                }
                target.flush()
            }
        } catch (e: Exception) {
            Log.d(TAG, "makeCopycat: ${e.message}")
        }
    }

    private fun loadPublicImage() {
        if (publicImageUriString.isEmpty()) { return }

        binding.imagePublic.setImageURI(Uri.parse(publicImageUriString))
    }

    //
    // routine: view binding and requesting runtime permission(s)
    //
    // region:- Permission:
    // for requesting runtime permission(s) using new API
    private val neededRuntimePermissions = arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
    private val resultLauncher =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
                permissions.entries.forEach {
                    Log.d(TAG, ": registerForActivityResult: ${it.key}=${it.value}")

                    // if any permission is not granted...
                    if (! it.value) {
                        // do anything if needed: ex) display about limitation
                        Snackbar.make(binding.root, R.string.permissions_request, Snackbar.LENGTH_SHORT).show()
                    }
                }
            }
    // endregion----

    // region:- View Binding
    lateinit var binding: ActivityMainBinding
    // endregion----
}