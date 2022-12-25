package com.huangbohan.sortrubbish

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.TextView
import android.widget.TextView.OnEditorActionListener
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.huangbohan.sortrubbish.databinding.ActivitySortTextBinding
import com.iflytek.cloud.*
import com.lxj.xpopup.XPopup
import com.lxj.xpopup.core.CenterPopupView
import okhttp3.*
import org.json.JSONObject
import org.json.JSONTokener
import java.io.IOException
import kotlin.concurrent.thread


class SortText : AppCompatActivity() {

    private val binging by lazy { ActivitySortTextBinding.inflate(layoutInflater) }
    private lateinit var mIat: SpeechRecognizer
    private var lastSearchTime: Long = System.currentTimeMillis()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binging.root)


        binging.root.viewTreeObserver.addOnGlobalLayoutListener(OnGlobalLayoutListener {
            val height: Int = binging.root.rootView.height - binging.root.height
            if (height > 100) {
                binging.toorbar.visibility = View.GONE
            } else {
                binging.toorbar.visibility = View.VISIBLE
            }
        })

        binging.searchEdittext.setOnEditorActionListener(OnEditorActionListener { v, actionId, event ->
            if (event?.keyCode == KeyEvent.KEYCODE_ENTER || actionId == EditorInfo.IME_ACTION_SEARCH) {
                try {
                    (v.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                        .hideSoftInputFromWindow(
                            this@SortText
                                .currentFocus
                                !!.windowToken,
                            InputMethodManager.HIDE_NOT_ALWAYS
                        )
                } catch (e:NullPointerException) {
                    e.printStackTrace()
                }

                search(v.text.toString())
                true
            }else {
                false
            }
        })



        SpeechUtility.createUtility(this, SpeechConstant.APPID +"=fa44fac1");

        initPermission()

        binging.record.setOnClickListener {
            XPopup.Builder(this)
                .asCustom(CustomPopup(this))
                .show()
        }
    }

    /**
     * android 6.0 以上需要动态申请权限
     */
    private fun initPermission() {
        val permissions = arrayOf<String>(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.INTERNET,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
        val toApplyList = ArrayList<String>()
        for (perm in permissions) {
            if (PackageManager.PERMISSION_GRANTED != ContextCompat.checkSelfPermission(this, perm)) {
                toApplyList.add(perm)
            }
        }
        val tmpList = arrayOfNulls<String>(toApplyList.size)
        if (toApplyList.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, toApplyList.toArray(tmpList), 123)
        }
    }

    /**
     * 权限申请回调，可以作进一步处理
     *
     * @param requestCode
     * @param permissions
     * @param grantResults
     */
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

    }

    private fun parseIatResult(json: String?): String {
        val ret = StringBuffer()
        try {
            val tokener = JSONTokener(json)
            val joResult = JSONObject(tokener)
            val words = joResult.getJSONArray("ws")
            for (i in 0 until words.length()) {
                // 转写结果词，默认使用第一个结果
                val items = words.getJSONObject(i).getJSONArray("cw")
                val obj = items.getJSONObject(0)
                ret.append(obj.getString("w"))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return ret.toString()
    }

    private fun search(text: String) {
        if (System.currentTimeMillis() - lastSearchTime < 1000L) {
            return
        }
        lastSearchTime = System.currentTimeMillis()

        binging.progress.visibility = View.INVISIBLE

        thread {
            val okHttpClient = OkHttpClient()
            val request: Request = Request.Builder() //这里我们采用建造者模式和链式调用指明是进行Get请求,
                //并传入Get请求的地址
                .get()
                .url("http://" + getSharedPreferences("data", Context.MODE_PRIVATE).getString("host", "") + "/search" + "?name=${text}")
                .build()
            val call: Call = okHttpClient.newCall(request)
            //异步调用
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {  //未收到服务器返回的结果
                    runOnUiThread {
                        Toast.makeText(this@SortText, "get failed", Toast.LENGTH_SHORT).show()
                    }
                }
                override fun onResponse(call: Call, response: Response) { //从onResponse中获得了返回的json数据
                    val res = response.body?.string()
                    runOnUiThread {
                        if (res != null) {
                            XPopup.Builder(this@SortText).asConfirm(
                                "Type", res
                            ) {}.show()
                        }else {
                            Toast.makeText(this@SortText, "fail", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            })
        }
    }

    inner class CustomPopup  //注意：自定义弹窗本质是一个自定义View，但是只需重写一个参数的构造，其他的不要重写，所有的自定义弹窗都是这样。
        (context: Context) : CenterPopupView(context) {
        // 返回自定义弹窗的布局
        override fun getImplLayoutId(): Int {
            return R.layout.dialog_record
        }

        // 执行初始化操作，比如：findView，设置点击，或者任何你弹窗内的业务逻辑
        override fun onCreate() {
            super.onCreate()
            val mIat = SpeechRecognizer.createRecognizer(this@SortText, InitListener { code ->
                Log.d("SortTextActivity", "SpeechRecognizer init() code = $code")
                if (code != ErrorCode.SUCCESS) {
                    Toast.makeText(this@SortText, "初始化失败，错误码：$code", Toast.LENGTH_SHORT).show()
                }
            });

            //设置语法ID和 SUBJECT 为空，以免因之前有语法调用而设置了此参数；或直接清空所有参数，具体可参考 DEMO 的示例。
            mIat.setParameter( SpeechConstant.CLOUD_GRAMMAR, null );
            mIat.setParameter( SpeechConstant.SUBJECT, null );
            //设置返回结果格式，目前支持json,xml以及plain 三种格式，其中plain为纯听写文本内容
            mIat.setParameter(SpeechConstant.RESULT_TYPE, "plain");
            //此处engineType为“cloud”
            mIat.setParameter( SpeechConstant.ENGINE_TYPE, "cloud" );
            //设置语音输入语言，zh_cn为简体中文
            mIat.setParameter(SpeechConstant.LANGUAGE, "zh_cn");
            //设置结果返回语言
            mIat.setParameter(SpeechConstant.ACCENT, "mandarin");
            // 设置语音前端点:静音超时时间，单位ms，即用户多长时间不说话则当做超时处理
            // 取值范围{1000～10000}
            mIat.setParameter(SpeechConstant.VAD_BOS, "4000");
            //设置语音后端点:后端点静音检测时间，单位ms，即用户停止说话多长时间内即认为不再输入，
            //自动停止录音，范围{0~10000}
            mIat.setParameter(SpeechConstant.VAD_EOS, "2000");
            //设置标点符号,设置为"0"返回结果无标点,设置为"1"返回结果有标点
            mIat.setParameter(SpeechConstant.ASR_PTT, "0");

            //开始识别，并设置监听器
            mIat.startListening(object : RecognizerListener{
                override fun onVolumeChanged(p0: Int, p1: ByteArray?) {

                }

                override fun onBeginOfSpeech() {
                    Log.d("SortText", "startSpeech")
                }

                override fun onEndOfSpeech() {
                    Log.d("SortText", "endSpeech")
                    dismiss()
                }

                @SuppressLint("SetTextI18n")
                override fun onResult(p0: RecognizerResult?, p1: Boolean) {
                    if (!p0?.resultString.isNullOrEmpty()) {
                        p0?.resultString.let {
                            Log.d("XFResult", "result:$it")
                            // Log.d("XFResult", parseIatResult(it))
                            if (this@CustomPopup.findViewById<TextView>(R.id.text).text.toString() == "Listening...") this@CustomPopup.findViewById<TextView>(R.id.text).text = ""
                            this@CustomPopup.findViewById<TextView>(R.id.text).text = this@CustomPopup.findViewById<TextView>(R.id.text).text.toString() + it
                            binging.searchEdittext.setText(binging.searchEdittext.text.toString() + it)
                        }
                    }
                }

                override fun onError(p0: SpeechError?) {
                    Log.d("SortText", "onError")
                    p0?.printStackTrace()
                }

                override fun onEvent(p0: Int, p1: Int, p2: Int, p3: Bundle?) {

                }

            });
            findViewById<Button>(R.id.button).setOnClickListener {
                dismiss()
            }
        }
    }
}