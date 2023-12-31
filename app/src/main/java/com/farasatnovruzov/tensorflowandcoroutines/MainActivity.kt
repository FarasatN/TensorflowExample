package com.farasatnovruzov.tensorflowandcoroutines

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.Instrumentation
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.farasatnovruzov.tensorflowandcoroutines.databinding.ActivityMainBinding
import com.farasatnovruzov.tensorflowandcoroutines.ml.InsectsModel
import org.tensorflow.lite.support.image.TensorImage
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val GALLERY_REQUEST_CODE = 1

    @SuppressLint("IntentReset")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)


        binding.btnCaptureImage.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this,android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED){
                takePicturePreview.launch(null)
            }else{
                requestPermission.launch(android.Manifest.permission.CAMERA)
            }
        }

        binding.btnLoadImage.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this,android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED){
                val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                intent.type = "image/*"
                val mimeTypes = arrayOf("image/jpeg","image/jpg","image/png")
                intent.putExtra(Intent.EXTRA_MIME_TYPES,mimeTypes)
                intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                onResult.launch(intent)
            }else{
                requestPermission.launch(android.Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        //to redirect user to google search for the scientific name
        binding.textView.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW,Uri.parse("https://www.google.com/search?q=${binding.textView.text}"))
            startActivity(intent)
        }

        //to download image when longPress on ImageView
        binding.imageView.setOnClickListener {
            requestPermissionLauncher.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
            return@setOnClickListener
        }
    }

    //request camera permission
    private val requestPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()){granted ->
        if(granted){
            takePicturePreview.launch(null)
        }else{
            Toast.makeText(this,"Permission Denied !! Try again ", Toast.LENGTH_SHORT).show()
        }
    }

    //launch camera and take picture
    private val takePicturePreview = registerForActivityResult(ActivityResultContracts.TakePicturePreview()){bitmap ->
        if (bitmap != null){
            binding.imageView.setImageBitmap(bitmap)
            outputGenerator(bitmap)
        }
    }

    //to get image from gallery
    private val onResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()){result ->
        Log.v("TAG","This is result: ${result.data} ${result.resultCode}")
        onResultReceived(GALLERY_REQUEST_CODE,result = result)
    }

    private fun onResultReceived(requestCode: Int, result: ActivityResult?){
        when(requestCode){
            GALLERY_REQUEST_CODE -> {
                if (result?.resultCode == Activity.RESULT_OK){
                    result.data?.data?.let{uri ->
                        Log.v("TAG","onResultReceived: $uri")
                        val bitmap = BitmapFactory.decodeStream(contentResolver.openInputStream(uri))
                        binding.imageView.setImageBitmap(bitmap)
                        outputGenerator(bitmap)
                    }
                }else{
                    Log.v("TAG","onActivityResult: error in selecting image")
                }
            }
        }
    }

    private fun outputGenerator(bitmap: Bitmap){
        //declearing tensorflow lite model variable
        val insectsModel = InsectsModel.newInstance(this)
        //converting bitmap into tensor flow image
        val newBitmap = bitmap.copy(Bitmap.Config.ARGB_8888,true)
        // Creates inputs for reference.
        val tfImage = TensorImage.fromBitmap(bitmap)
        //process the image using trained model and sort it in descending order

        // Runs model inference and gets result.
        val outputs = insectsModel.process(tfImage)
            .probabilityAsCategoryList.apply {
                sortByDescending { it.score }
            }
        //getting result having high probability
        val highProbabilityOutput = outputs[0]
        //setting output text
        binding.textView.text = highProbabilityOutput.label
        Log.v("TAG","outputGenerator: $highProbabilityOutput")
        // Releases model resources if no longer used.
//        insectsModel.close()
    }


    //to download image to device
    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()){isGranted:Boolean->
        if (isGranted){
            AlertDialog.Builder(this).setTitle("Download Image? ")
                .setMessage("Do you want to download this image to your device?")
                .setPositiveButton("Yes"){_,_ ->
                    val drawable:BitmapDrawable = binding.imageView.drawable as BitmapDrawable
                    val bitmap = drawable.bitmap
                    downloadImage(bitmap)
                }
                .setNegativeButton("No"){dialog,_ ->
                    dialog.dismiss()
                }
                .show()
        }else{
            Toast.makeText(this,"Please allow permissin to download image",Toast.LENGTH_LONG).show()
        }
    }

    //fun that takes a bitmap and store to user's device
    private fun downloadImage(mBitmap: Bitmap):Uri?{
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME,"Insect_Images"+System.currentTimeMillis()/1000)
            put(MediaStore.Images.Media.MIME_TYPE,"image/png")
        }

        val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        if (uri!=null){
            contentResolver.insert(uri,contentValues)?.also {
                contentResolver.openOutputStream(it).use { outPutStream ->
                    if (!mBitmap.compress(Bitmap.CompressFormat.PNG,100,outPutStream)){
                        throw IOException("Couldn't save the bitmap")
                    }else{
                        Toast.makeText(applicationContext,"Image Saved",Toast.LENGTH_LONG).show()
                    }
                }
                return it
            }
        }
        return null
    }
}