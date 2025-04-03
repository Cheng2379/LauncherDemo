package com.example.launcherdemo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.example.launcherdemo.adapter.RecyclerViewAdapter
import com.example.launcherdemo.bean.AppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.core.net.toUri
import com.example.launcherdemo.util.Logger
import kotlinx.coroutines.delay

class MainActivity : AppCompatActivity() {
    private val mRv: RecyclerView by lazy { findViewById(R.id.rv_main) }
    private var mRvAdapter: RecyclerViewAdapter<AppInfo>? = null
    private var appInfoList = ArrayList<AppInfo>()

    private var pendingUninstallPackage: String? = null
    private var pendingUninstallAppName: String? = null
    private var uninstallResultReceiver: BroadcastReceiver? = null
    private lateinit var uninstallLauncher: ActivityResultLauncher<Intent>

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
        setUninstallResultReceiver()
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
            view.setOnLongClickListener {
                showAppOptions(view, appInfo)
                true
            }
        }
        mRv.adapter = mRvAdapter

        // 卸载回调监听
        uninstallLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                pendingUninstallPackage?.let { target ->
                    lifecycleScope.launch {
                        delay(100)
                        if (isAppInstalled(target)) {
                            Toast.makeText(this@MainActivity, "已取消卸载", Toast.LENGTH_SHORT)
                                .show()
                        }
                        pendingUninstallPackage = null
                        pendingUninstallAppName = null
                    }
                }
            }
    }

    private fun showAppOptions(view: View, appInfo: AppInfo) {
        PopupMenu(this, view).apply {
            menuInflater.inflate(R.menu.menu_app_options, menu)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_info -> {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = "package:${appInfo.packageName}".toUri()
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        this@MainActivity.startActivity(intent)
                        true
                    }

                    R.id.action_uninstall -> {
                        // 常规卸载方法
                        pendingUninstallPackage = appInfo.packageName
                        pendingUninstallAppName = appInfo.appName
                        val intent =
                            Intent(Intent.ACTION_DELETE, "package:${appInfo.packageName}".toUri())
                        uninstallLauncher.launch(intent)

                        true
                    }

                    else -> false
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                setForceShowIcon(true)
            }
            show()
        }
    }

    private suspend fun getAppInfoList(): List<AppInfo> = withContext(Dispatchers.IO) {
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
            appInfoList = getAppInfoList() as ArrayList<AppInfo>
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
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        intent?.let {
            try {
                context.startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(context, "无法启动应用", Toast.LENGTH_SHORT).show()
            }
        } ?: {
            Toast.makeText(context, "无法启动应用: 未找到启动意图", Toast.LENGTH_SHORT).show()
        }
    }

    private fun isAppInstalled(packageName: String): Boolean {
        return try {
            packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * 监听卸载回调广播
     */
    private fun setUninstallResultReceiver() {
        uninstallResultReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == Intent.ACTION_PACKAGE_REMOVED) {
                    intent.data?.schemeSpecificPart?.let { removedPackageName ->
                        val appName = pendingUninstallAppName ?: pendingUninstallPackage
                        Logger.d("removedPackage: $removedPackageName")
                        Logger.d("appName: $appName")
                        val isUninstalled = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
                        if (!isUninstalled) {
                            runOnUiThread {
                                if (!isFinishing && !isDestroyed) {
                                    Toast.makeText(
                                        context,
                                        "${appName}卸载完成",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                            // 刷新应用列表
                            loadApplicationInfoList()
                            pendingUninstallPackage = null
                            pendingUninstallAppName = null
                        }
                    }
                }
            }
        }
        val filter = IntentFilter(Intent.ACTION_PACKAGE_REMOVED)
        filter.addDataScheme("package")
        registerReceiver(uninstallResultReceiver, filter)
    }


    override fun onDestroy() {
        unregisterReceiver(uninstallResultReceiver)
        super.onDestroy()
    }

}