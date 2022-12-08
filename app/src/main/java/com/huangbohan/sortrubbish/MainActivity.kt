package com.huangbohan.sortrubbish

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.huangbohan.sortrubbish.databinding.ActivityMainBinding
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.concurrent.thread


class MainActivity : AppCompatActivity() {
    val takePhoto = 1
    val fromAlbum = 2
    lateinit var imageUri: Uri
    lateinit var outputImage: File

    private val binging by lazy { ActivityMainBinding.inflate(layoutInflater) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binging.root)
        binging.takePhotoBtn.setOnClickListener {
            // 创建File对象，用于存储拍照后的图片
            outputImage = File(externalCacheDir, "output_image.jpg")
            if (outputImage.exists()) {
                outputImage.delete()
            }
            outputImage.createNewFile()
            imageUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                FileProvider.getUriForFile(this, "com.huangbohan.sortrubbish.fileprovider", outputImage);
            } else {
                Uri.fromFile(outputImage);
            }
            // 启动相机程序
            val intent = Intent("android.media.action.IMAGE_CAPTURE")
            intent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri)
            startActivityForResult(intent, takePhoto)
        }
        binging.fromAlbumBtn.setOnClickListener {
            // 打开文件选择器
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            // 指定只显示照片
            intent.type = "image/*"
            startActivityForResult(intent, fromAlbum)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            takePhoto -> {
                if (resultCode == Activity.RESULT_OK) {
                    // 将拍摄的照片显示出来
                    val bitmap = BitmapFactory.decodeStream(contentResolver.openInputStream(imageUri))
                    setImage(rotateIfRequired(bitmap))
                }
            }
            fromAlbum -> {
                if (resultCode == Activity.RESULT_OK && data != null) {
                    data.data?.let { uri ->
                        // 将选择的照片显示
                        val bitmap = getBitmapFromUri(uri)
                        if (bitmap != null) {
                            setImage(bitmap)
                        }
                    }
                }
            }
        }
    }

    private fun setImage(bitmap: Bitmap) {
        thread {
            saveBitmap("temp.jpg", bitmap)
            runOnUiThread {
                binging.imageView.setImageBitmap(BitmapFactory.decodeFile(cacheDir!!.absolutePath + "/temp.jpg"))
                //1.创建OkHttpClient对象
                val okHttpClient = OkHttpClient()
                //上传的图片
                val file = File(cacheDir!!.absolutePath, "temp.jpg")
                //2.通过new MultipartBody build() 创建requestBody对象，
                val requestBody: RequestBody = MultipartBody.Builder() //设置类型是表单
                    .setType(MultipartBody.FORM) //添加数据
                    .addFormDataPart(
                        "image", "post.jpg",
                        RequestBody.create("image/jpeg".toMediaTypeOrNull(), file)
                    )
                    .build()
                //3.创建Request对象，设置URL地址，将RequestBody作为post方法的参数传入
                val request: Request = Request.Builder().url("http://" + binging.editText.text.toString() + "/sort_rubbish").post(requestBody).build()
                //4.创建一个call对象,参数就是Request请求对象
                val call: Call = okHttpClient.newCall(request)
                //5.请求加入调度,重写回调方法
                call.enqueue(object : Callback{
                    override fun onFailure(call: Call, e: IOException) {
                        Log.e("MainActivity", e.toString())
                        runOnUiThread {
                            Toast.makeText(this@MainActivity, "Failure to get data!", Toast.LENGTH_SHORT).show()
                        }
                    }

                    override fun onResponse(call: Call, response: Response) {
                        runOnUiThread {
                            Toast.makeText(this@MainActivity, "Successful to get data!", Toast.LENGTH_SHORT).show()
                            response.body?.string().let {
                                Toast.makeText(this@MainActivity, it, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                })
            }
        }
    }

    private fun getBitmapFromUri(uri: Uri) = contentResolver.openFileDescriptor(uri, "r")?.use {
        BitmapFactory.decodeFileDescriptor(it.fileDescriptor)
    }

    private fun rotateIfRequired(bitmap: Bitmap): Bitmap {
        val exif = ExifInterface(outputImage.path)
        val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        return when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> rotateBitmap(bitmap, 90)
            ExifInterface.ORIENTATION_ROTATE_180 -> rotateBitmap(bitmap, 180)
            ExifInterface.ORIENTATION_ROTATE_270 -> rotateBitmap(bitmap, 270)
            else -> bitmap
        }
    }

    private fun rotateBitmap(bitmap: Bitmap, degree: Int): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(degree.toFloat())
        val rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        bitmap.recycle()
        return rotatedBitmap
    }

    fun saveBitmap(name: String?, bm: Bitmap) {
        Log.d("Save Bitmap", "Ready to save picture")
        //指定我们想要存储文件的地址
        val TargetPath: String = getCacheDir()!!.absolutePath + "/"
        Log.d("Save Bitmap", "Save Path=$TargetPath")
        //判断指定文件夹的路径是否存在
        if (!File(TargetPath).exists()) {
            Log.d("Save Bitmap", "TargetPath isn't exist")
        } else {
            //如果指定文件夹创建成功，那么我们则需要进行图片存储操作
            val saveFile = File(TargetPath, name)
            try {
                val saveImgOut = FileOutputStream(saveFile)
                // compress - 压缩的意思
                bm.compress(Bitmap.CompressFormat.JPEG, 80, saveImgOut)
                //存储完成后需要清除相关的进程
                saveImgOut.flush()
                saveImgOut.close()
                Log.d("Save Bitmap", "The picture is save to your phone!")
            } catch (ex: IOException) {
                ex.printStackTrace()
            }
        }
    }
}