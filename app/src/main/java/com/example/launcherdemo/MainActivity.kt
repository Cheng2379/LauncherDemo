package com.example.launcherdemo

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.example.launcherdemo.adapter.RecyclerViewAdapter
import com.example.launcherdemo.bean.AppInfo
import com.example.launcherdemo.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    private val mRv: RecyclerView by lazy { findViewById(R.id.rv_main) }
    private var mRvAdapter: RecyclerViewAdapter<AppInfo>? = null
    private var appInfoList = ArrayList<AppInfo>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        initView()
        loadApplicationInfoList()
    }

    private fun initView() {
        mRvAdapter = RecyclerViewAdapter(
            this,
            appInfoList,
            R.layout.item_app
        ) { viewHolder, position ->
            val appInfo = appInfoList[position]

            val view = viewHolder.itemView
            val iconView = view.findViewById<ImageView>(R.id.app_icon)
            val appNameView = view.findViewById<TextView>(R.id.app_name)

            iconView.apply {
                background =
                    ContextCompat.getDrawable(context, R.drawable.shape_img_selector)
                clipToOutline = true
                setImageDrawable(appInfo.icon)
            }
            appNameView.text = appInfo.appName

            view.setOnClickListener {
                launchApp(this, appInfo.packageName)
            }
        }
        mRv.adapter = mRvAdapter
    }

    private suspend fun getResolveInfoList(): List<AppInfo> = withContext(Dispatchers.IO) {
        val mainIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        return@withContext mutableListOf<AppInfo>().apply {
            packageManager.queryIntentActivities(mainIntent, PackageManager.GET_META_DATA)
                .mapNotNull { resolveInfo ->
                    resolveInfo.activityInfo?.packageName
                        .takeIf {
                            !it.equals("com.example.launcherdemo")
                        }
                        ?.let {
                            add(
                                AppInfo(
                                    resolveInfo.activityInfo.packageName,
                                    resolveInfo.activityInfo.name,
                                    resolveInfo.loadLabel(packageManager).toString(),
                                    resolveInfo.loadIcon(packageManager)
                                )
                            )
                        }
                }
        }
    }

    private fun loadApplicationInfoList() {
        lifecycleScope.launch(Dispatchers.IO) {
            appInfoList = getResolveInfoList() as ArrayList<AppInfo>
            appInfoList.sortWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.appName })
            withContext(Dispatchers.Main) {
                mRvAdapter?.updateAll(appInfoList)
            }
        }
    }

    /**
     * 启动App
     */
    private fun launchApp(context: Context, packageName: String) {
        // 跳转方式一
        /*val intent = Intent()
        intent.setComponent(
            ComponentName(
                appInfo.packageName,
                appInfo.mainActivity
            )
        )
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        this.startActivity(intent)*/
        // 跳转方式二, 稳定写法
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
        intent?.let {
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(context, "无法启动应用", Toast.LENGTH_SHORT).show()
            }
        } ?: {
            Toast.makeText(context, "无法启动应用: 未找到启动意图", Toast.LENGTH_SHORT).show()
        }
    }


}