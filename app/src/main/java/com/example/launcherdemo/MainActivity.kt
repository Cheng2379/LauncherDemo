package com.example.launcherdemo

import android.Manifest
import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.example.launcherdemo.adapter.RecyclerViewAdapter
import com.example.launcherdemo.bean.AppInfoBean
import kotlinx.coroutines.launch
import androidx.core.net.toUri
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.example.launcherdemo.databinding.ActivityMainBinding
import com.example.launcherdemo.util.AppRepository
import com.example.launcherdemo.util.Logger
import com.example.launcherdemo.util.PermissionUtil
import com.example.launcherdemo.util.WallpaperUtil
import com.example.launcherdemo.util.launchApp
import com.example.launcherdemo.util.showToast
import com.example.launcherdemo.view.MusicCardView
import com.example.launcherdemo.vm.MainViewModel
import com.example.launcherdemo.vm.MusicCardViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

class MainActivity : AppCompatActivity() {
    private lateinit var mainBinding: ActivityMainBinding
    private val mainViewModel: MainViewModel by viewModels()
    private val musicViewModel: MusicCardViewModel by viewModels()

    private val mRv: RecyclerView by lazy { mainBinding.rvMain }
    private var mRvAdapter: RecyclerViewAdapter<AppInfoBean>? = null
    private val mMusicCardView: MusicCardView by lazy { mainBinding.musicCardView }

    private var pendingUninstallPackage: String? = null
    private var pendingUninstallAppName: String? = null
    private var uninstallResultReceiver: BroadcastReceiver? = null
    private lateinit var uninstallLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        mainBinding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        mainBinding.lifecycleOwner = this

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        setupPermissionLauncher()

        initView()
        setupObservers()
        setupUninstallLogic()
    }

    private fun initView() {
        // 设置背景为系统壁纸
        mainBinding.main.background = WallpaperUtil.getWallpaperDrawable()

        //WallpaperUtil.setWallpaper(R.drawable.bg_2)

        mRvAdapter = RecyclerViewAdapter(
            this,
            ArrayList(),
            R.layout.item_app
        ) { viewHolder, position ->
            mRvAdapter?.dataList?.getOrNull(position)?.let { appInfo ->
                val view = viewHolder.itemView

                view.setOnClickListener {
                    launchApp(this, appInfo.packageName)
                }
                view.setOnLongClickListener {
                    showAppOptions(view, appInfo)
                    true
                }
            }
        }
        mRv.adapter = mRvAdapter
    }

    private fun setupObservers() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                mainViewModel.apps.collectLatest { apps ->
                    mRvAdapter?.updateAll(ArrayList(apps))
                }
            }
        }
    }

    /**
     * 卸载应用
     */
    private fun setupUninstallLogic() {
        // 卸载回调监听
        uninstallLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                pendingUninstallPackage?.let { target ->
                    lifecycleScope.launch {
                        delay(100)
                        if (AppRepository.isAppInstalled(this@MainActivity, target)) {
                            Toast.makeText(this@MainActivity, "已取消卸载", Toast.LENGTH_SHORT)
                                .show()
                        }
                        pendingUninstallPackage = null
                        pendingUninstallAppName = null
                    }
                }
            }
        setUninstallResultReceiver()
    }

    /**
     * 设置权限
     */
    private fun setupPermissionLauncher() {
        PermissionUtil.registerMultiplePermissionsLauncher(this) { permissions ->
            if (permissions[Manifest.permission.READ_EXTERNAL_STORAGE] == false) {
                mMusicCardView.setOnClickListener {
                    // 点击时判断并请求权限
                    requestNotificationPermission()
                }
            }
        }.launch(
            // 运行时权限列表
            arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        )
    }

    /**
     * 检查并请求通知栏监听权限
     */
    private fun requestNotificationPermission() {
        if (PermissionUtil.isNotificationServiceEnabled(contentResolver, packageName)) {
            mMusicCardView.initViewModel()
        } else {
            PermissionUtil.requestNotificationPermission(this)
        }
    }

    private fun showAppOptions(view: View, appInfoBean: AppInfoBean) {
        PopupMenu(this, view).apply {
            menuInflater.inflate(R.menu.menu_app_options, menu)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_info -> {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = "package:${appInfoBean.packageName}".toUri()
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        this@MainActivity.startActivity(intent)
                        true
                    }

                    R.id.action_uninstall -> {
                        // 常规卸载方法
                        pendingUninstallPackage = appInfoBean.packageName
                        pendingUninstallAppName = appInfoBean.appName
                        val intent =
                            Intent(
                                Intent.ACTION_DELETE,
                                "package:${appInfoBean.packageName}".toUri()
                            )
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
                                    "${appName}卸载完成".showToast()
                                }
                            }
                            // 刷新应用列表
                            mainViewModel.loadApplicationInfoList()
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