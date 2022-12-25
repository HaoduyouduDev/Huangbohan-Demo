package com.huangbohan.sortrubbish

import android.content.Context
import android.graphics.Color
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import com.huangbohan.sortrubbish.databinding.ActivitySortImageBinding
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import java.io.File
import java.io.IOException

class SortImage : AppCompatActivity() {
    private val binging by lazy { ActivitySortImageBinding.inflate(layoutInflater) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binging.root)
        setSupportActionBar(binging.toorbar)

        runOnUiThread {
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
            val request: Request = Request.Builder().url("http://" + getSharedPreferences("data", Context.MODE_PRIVATE).getString("host", "") + "/sort_rubbish").post(requestBody).build()
            //4.创建一个call对象,参数就是Request请求对象
            val call: Call = okHttpClient.newCall(request)
            //5.请求加入调度,重写回调方法
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e("MainActivity", e.toString())
                    runOnUiThread {
                        Toast.makeText(this@SortImage, "Failure to get data!", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    runOnUiThread {
                        Toast.makeText(this@SortImage, "Successful to get data!", Toast.LENGTH_SHORT).show()
                        binging.imageView.setImageURI(Uri.fromFile(File(cacheDir, "temp.jpg")));
                        binging.loadview.visibility = View.GONE
                        response.body?.string().let {
                            binging.resultText.text = it.toString()
                        }
                    }
                }
            })
        }
    }
}