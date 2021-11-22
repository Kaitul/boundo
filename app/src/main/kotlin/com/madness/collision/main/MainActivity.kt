/*
 * Copyright 2021 Clifford Liu
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.madness.collision.main

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.view.*
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.widget.Toolbar
import androidx.core.app.NotificationManagerCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.madness.collision.Democratic
import com.madness.collision.R
import com.madness.collision.base.BaseActivity
import com.madness.collision.databinding.ActivityMainBinding
import com.madness.collision.diy.WindowInsets
import com.madness.collision.main.updates.UpdatesFragment
import com.madness.collision.misc.MiscMain
import com.madness.collision.unit.Unit
import com.madness.collision.unit.api_viewing.AccessAV
import com.madness.collision.util.*
import com.madness.collision.util.controller.systemUi
import com.madness.collision.util.notice.ToastUtils
import kotlinx.coroutines.*
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

class MainActivity : BaseActivity(), Toolbar.OnMenuItemClickListener {
    companion object {
        /**
         * the activity to launch
         */
        const val LAUNCH_ACTIVITY = "launchActivity"
        /**
         * the target(fragment) to launch
         */
        const val LAUNCH_ITEM = "launchItem"
        const val LAUNCH_ITEM_ARGS = "launchItemArgs"

        const val ACTION_RECREATE = "mainRecreate"
        /**
         * update exterior after background has changed
         */
        const val ACTION_EXTERIOR = "mainExterior"
        const val ACTION_EXTERIOR_THEME = "mainExteriorTheme"

        private const val STATE_KEY_BACK = "Back"

        // ui appearance data
        private var themeId = P.SETTINGS_THEME_NONE

        fun forItem(name: String, args: Bundle? = null): Bundle {
            val extras = Bundle()
            extras.putString(LAUNCH_ITEM, name)
            if (args != null) extras.putParcelable(LAUNCH_ITEM_ARGS, args)
            return extras
        }
    }

    // session data
    private var launchItem: String? = null
    private var mBackStack: ArrayDeque<BackwardOperation> = ArrayDeque()
    private val viewModel: MainViewModel by viewModels()
    // ui appearance data
    private var primaryStatusBarConfig: SystemBarConfig? = null
    private var primaryNavBarConfig: SystemBarConfig? = null
    private var isToolbarInflated = false
    private var colorIcon = 0
    // views
    private lateinit var navHostFragment: NavHostFragment
    private lateinit var viewBinding: ActivityMainBinding
    private var lastBackFragment: WeakReference<Fragment> = WeakReference(null)
    // android
    private lateinit var mContext: Context
    private lateinit var mWindow: Window

    private val background: View get() = viewBinding.mainContainer
    private val navController: NavController
        get() = navHostFragment.navController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val context = this
        mContext = context
        val prefSettings = getSharedPreferences(P.PREF_SETTINGS, Context.MODE_PRIVATE)

        themeId = loadThemeId(context, prefSettings)
        // this needs to be invoked before update fragments are rendered
        viewModel.updateTimestamp()

        mWindow = window
        launchItem = intent?.getStringExtra(LAUNCH_ITEM)

        inflateLayout(context)

        lifecycleScope.launch(Dispatchers.Default) {
            initApplication(context, prefSettings)
            MiscMain.ensureUpdate(context, prefSettings)
            Unit.loadUnitClasses(context)

            withContext(Dispatchers.Main) {
                setupLayout(context, prefSettings)
            }

            checkMisc(context, prefSettings)
            checkTarget(context)
        }
    }

    private fun loadThemeId(context: Context, prefSettings: SharedPreferences) : Int {
        val app = mainApplication
        val globalValue = app.globalValue
        return if (globalValue is Pair<*, *> && globalValue.first as String == ACTION_EXTERIOR_THEME) {
            app.globalValue = null
            val themeId = globalValue.second as Int
            if (themeId != R.style.LaunchScreen) setTheme(themeId)
            ThemeUtil.updateIsDarkTheme(context, ThemeUtil.getIsDarkTheme(context))
            themeId
        } else {
            ThemeUtil.updateTheme(this, prefSettings)
        }
    }

    private suspend fun initApplication(context: Context, prefSettings: SharedPreferences) {
        val app = mainApplication
        if (!app.dead) return
        app.debug = prefSettings.getBoolean(P.ADVANCED, false)
        app.statusBarHeight = getStatusBarHeight(context)
        if (!app.isDarkTheme) MiscMain.updateExteriorBackgrounds(context)
        app.dead = false
    }

    private fun getStatusBarHeight(context: Context): Int {
        val result = AtomicInteger()
        val resourceId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) result.set(context.resources.getDimensionPixelSize(resourceId))
        if (result.get() == 0) result.set(X.size(context, 30f, X.DP).toInt())
        return result.get()
    }

    private fun inflateLayout(context: Context) {
        systemUi { fullscreen() }

        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        navHostFragment = supportFragmentManager.findFragmentById(R.id.mainNavHost) as NavHostFragment
    }

    private fun setupLayout(context: Context, prefSettings: SharedPreferences) {
        setupNav()
        setupViewModel(context, prefSettings)
        // invocation will clear stack
        setupUi(context)
        viewModel.background.observe(this) {
            applyExterior()
            if (isToolbarInflated || !mainApplication.exterior) applyColor()
        }
    }

    private fun setupNav() {
        val graph = navController.navInflater.inflate(R.navigation.nav_main).apply {
            startDestination = R.id.mainFragment
        }
        val startArgs = if (launchItem != null) Bundle().apply {
            putInt(UpdatesFragment.ARG_MODE, UpdatesFragment.MODE_NO_UPDATES)
        } else null
        navController.setGraph(graph, startArgs)
    }

    private fun setupViewModel(context: Context, prefSettings: SharedPreferences) {
        viewModel.democratic.observe(this) {
            clearDemocratic()
            viewBinding.mainTB.tag = viewBinding.mainToolbarDivider
            it.createOptions(context, viewBinding.mainTB, colorIcon)
        }
        viewModel.unit.observe(this) {
            it ?: return@observe
            val (unitFragment, flags) = it
            // use the third flag to indicate false trigger
            if (flags.size >= 3 && flags[2]) return@observe
            val opFlags = if (flags.size >= 3) flags.dropLast(1).toBooleanArray() else flags
            val navFm = navHostFragment.childFragmentManager
            navFm.fragments.find { f -> f.isVisible }?.also { topFragment ->
                if (topFragment === unitFragment) return@also
                if (topFragment !is TaggedFragment) return@also
                if (unitFragment !is TaggedFragment) return@also
                if (topFragment.uid == unitFragment.uid) return@also
                val operation = BackwardOperation(unitFragment, topFragment, opFlags)
                mBackStack.addLast(operation)
                onBackPressedDispatcher.addCallback(operation.makeCallback())
                navFm.beginTransaction().animNew.run {
                    hide(topFragment)
                    if (unitFragment.isAdded) {
                        show(unitFragment)
                    } else {
                        add(navHostFragment.view?.id ?: 0, unitFragment, unitFragment.uid)
                    }
                    commit()
                }
            }
        }
        viewModel.popUpBackStackFun = { isFromNav, _ ->
            when {
                onBackPressedDispatcher.hasEnabledCallbacks() -> onBackPressedDispatcher.onBackPressed()
                isFromNav -> navController.popBackStack()
                else -> supportFragmentManager.popBackStack()
            }
        }
        viewModel.action.observe(this) {
            when (it.first) {
                "" -> return@observe
                ACTION_EXTERIOR -> updateExterior()
                ACTION_RECREATE -> recreate()
                ACTION_EXTERIOR_THEME -> {
                    val newThemeId = ThemeUtil.updateTheme(context, prefSettings, false)
                    if (themeId != newThemeId) {
                        mainApplication.globalValue = ACTION_EXTERIOR_THEME to newThemeId
                        recreate()
                    }
                }
            }
            viewModel.action.value = "" to null
        }
        viewModel.insetTop.observe(this) {
            viewBinding.mainTB.apply {
                alterPadding(top = it)
                measure()
                viewModel.contentWidthTop.value = measuredHeight
            }
        }
        viewModel.insetBottom.observe(this) {
            viewModel.contentWidthBottom.value = it
        }
    }

    private fun setupUi(context: Context) {
        colorIcon = ThemeUtil.getColor(context, R.attr.colorIcon)

        navController.addOnDestinationChangedListener { _, _, _ ->
            viewModel.removeCurrentUnit()
            clearBackPressedCallback()
        }

        viewBinding.mainTB.setOnMenuItemClickListener(this)

        checkTargetItem()

        background.setOnApplyWindowInsetsListener { v, insets ->
            val isRtl = if (v.isLayoutDirectionResolved) v.layoutDirection == View.LAYOUT_DIRECTION_RTL else false
            consumeInsets(WindowInsets(insets, isRtl))
            insets
        }

        lifecycleScope.launch(Dispatchers.Default) {
            while (true) {
                isToolbarInflated = viewBinding.mainTB.width > 0 && viewBinding.mainTB.height > 0
                if (isToolbarInflated) {
                    if (!mainApplication.dead) withContext(Dispatchers.Main) {
                        initExterior()
                    }
                    break
                }
                delay(150)
            }
        }
    }

    private fun checkTargetItem() {
        val launchItemName = launchItem
        if (launchItemName.isNullOrEmpty()) return
        val itemArgs = intent?.getBundleExtra(LAUNCH_ITEM_ARGS)
        val hasArgs = itemArgs != null
        lifecycleScope.launch(Dispatchers.Default) {
            // wait for Unit init
            delay(200)
            launchItem = null
            val itemUnitDesc = Unit.getDescription(launchItemName) ?: return@launch
            withContext(Dispatchers.Main) {
                if (hasArgs) {
                    viewModel.displayUnit(itemUnitDesc.unitName, false, true, itemArgs)
                } else {
                    viewModel.displayUnit(itemUnitDesc.unitName, shouldExitAppAfterBack = true)
                }
            }
        }
    }

    /**
     * update notification availability, notification channels and check app update
     */
    private suspend fun checkMisc(context: Context, prefSettings: SharedPreferences) {
        val app = mainApplication
        // enable notification
        app.notificationAvailable = NotificationManagerCompat.from(context).areNotificationsEnabled()
        if (!app.notificationAvailable && Random(System.currentTimeMillis()).nextInt(10) == 0) {
            withContext(Dispatchers.Main) {
                ToastUtils.popRequestNotification(this@MainActivity)
            }
        }
        MiscMain.registerNotificationChannels(context, prefSettings)

//        prHandler = PermissionRequestHandler(this)
//        SettingsFunc.check4Update(this, null, prHandler)
    }

    private suspend fun checkTarget(context: Context) {
        val activityName = intent.getStringExtra(LAUNCH_ACTIVITY) ?: ""
        if (activityName.isEmpty()) return
        val target = try {
            Class.forName(activityName)
        } catch (e: ClassNotFoundException) {
            e.printStackTrace()
            return
        }
        val startIntent = Intent(context, target)
        startIntent.putExtras(intent)
        delay(100)
        withContext(Dispatchers.Main) {
            startActivity(startIntent)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mBackStack.toTypedArray().let {
            outState.putParcelableArray(STATE_KEY_BACK, it)
        }
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        val callbacks = savedInstanceState.getParcelableArray(STATE_KEY_BACK) ?: return
        callbacks.mapNotNull {
            if (it is BackwardOperation) it else null
        }.let { mBackStack = ArrayDeque(it) }
        lifecycleScope.launch(Dispatchers.Default) {
            // wait until fragments are in place
            delay(500)
            setupOnBackPressedDispatcher()
        }
    }

    override fun onDestroy() {
        val context = mContext
        mBackStack.forEach {
            it.cachedCallback?.remove()
            it.cachedCallback = null
        }
        AccessAV.clearContext()
        AccessAV.clearTags()
        AccessAV.clearSeals()
        MiscMain.clearCache(context)
        super.onDestroy()
    }

    private fun setupOnBackPressedDispatcher() {
        if (mBackStack.isEmpty()) return
        val last = mBackStack.last()
        mBackStack.forEach {
            val callback = it.makeCallback(it === last)
            onBackPressedDispatcher.addCallback(callback)
        }
    }

    private fun BackwardOperation.makeCallback(isForwardTheTop: Boolean = false): OnBackPressedCallback {
        val shouldExitAppAfterBack = operationFlags[1]
        val navFm = navHostFragment.childFragmentManager
        tryToEnsure(navFm)
        val forwardFragment = when {
            isForwardTheTop -> navFm.fragments.find { f -> f.isVisible }
            forwardPage.hasRef -> forwardPage.fragment
            else -> null
        }
        val backwardFragment = backwardPage.fragment
        cachedCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                minusBackPressedCallback(this)
                if (shouldExitAppAfterBack) {
                    finish()
                    return
                }
                navFm.beginTransaction().animRetreat.run {
                    if (forwardFragment != null) {
                        remove(forwardFragment)
                    }
                    if (backwardFragment != null) {
                        if (backwardFragment.isAdded) {
                            show(backwardFragment)
                        } else {
                            add(navHostFragment.view?.id
                                    ?: 0, backwardFragment, backwardFragment.uid)
                        }
                    }
                    commitNow()
                }
                if (backwardFragment is Democratic) {
                    backwardFragment.democratize(viewModel)
                }
                // make unit value matches reality
                val isMoreUnit = !mBackStack.isEmpty()
                if (backwardFragment != null) {
                    viewModel.unit.value = if (isMoreUnit) backwardFragment to booleanArrayOf(false, false, true) else null
                }
                // fix extra fragment
                if (!isMoreUnit) {
                    navFm.fragments.find {
                        it.isAdded && it !== backwardFragment
                    }?.let {
                        lastBackFragment = WeakReference(backwardFragment)
                    }
                }
            }
        }
        return cachedCallback!!
    }

    private fun minusBackPressedCallback(callback: OnBackPressedCallback) {
        if (mBackStack.isEmpty()) return
        if (mBackStack.last().cachedCallback === callback) {
            mBackStack.removeLast().cachedCallback?.remove()
        }
    }

    private fun clearBackPressedCallback() {
        // fix the last fragment not hidden by nav controller
        lastBackFragment.get()?.let {
            lastBackFragment = WeakReference(null)
            if (it.isAdded && it.isVisible) {
                val navFm = navHostFragment.childFragmentManager
                navFm.beginTransaction().hide(it).commitNowAllowingStateLoss()
            }
        }
        if (mBackStack.isEmpty()) return
        mBackStack.forEach {
            it.cachedCallback?.remove()
        }
        mBackStack.clear()
    }

    private fun clearDemocratic() {
        // Low profile mode is used in ApiDecentFragment. Deprecated since Android 11.
        if (X.belowOff(X.R)) disableLowProfileModeLegacy(window)
        viewBinding.mainTB.run {
            isVisible = true
            menu.clear()
            navigationIcon = null
            title = null
            setNavigationOnClickListener(null)
            setOnClickListener(null)
        }
        viewBinding.mainToolbarDivider.isVisible = true
        primaryStatusBarConfig?.let {
            SystemUtil.applyStatusBarConfig(mContext, mWindow, it)
        }
        primaryNavBarConfig?.let {
            SystemUtil.applyNavBarConfig(mContext, mWindow, it)
        }
    }

    @Suppress("deprecation")
    private fun disableLowProfileModeLegacy(window: Window) {
        val decorView = window.decorView
        decorView.systemUiVisibility = decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_LOW_PROFILE.inv()
    }

    private val FragmentTransaction.animNew: FragmentTransaction
        get() = disallowAddToBackStack().setCustomAnimations(
                R.animator.nav_default_enter_anim, R.animator.nav_default_exit_anim,
                R.animator.nav_default_pop_enter_anim, R.animator.nav_default_pop_exit_anim
        )

    private val FragmentTransaction.animRetreat: FragmentTransaction
        get() = setCustomAnimations(
                R.animator.nav_default_pop_enter_anim, R.animator.nav_default_pop_exit_anim,
                R.animator.nav_default_pop_enter_anim, R.animator.nav_default_pop_exit_anim
        )

    override fun onMenuItemClick(item: MenuItem?): Boolean {
        item ?: return false
        return try {
            viewModel.democratic.value?.selectOption(item) ?: false
        } catch (e: Exception) {
            e.printStackTrace()
            notifyBriefly(R.string.text_error)
            false
        }
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (SystemUtil.isSystemTvUi(this).not()) return super.onKeyUp(keyCode, event)
        val navKeyCode = SystemUtil.unifyTvNavKeyCode(keyCode)
        when(navKeyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> kotlin.Unit
            KeyEvent.KEYCODE_DPAD_DOWN -> kotlin.Unit
            KeyEvent.KEYCODE_DPAD_LEFT -> kotlin.Unit
            KeyEvent.KEYCODE_DPAD_RIGHT -> kotlin.Unit
            KeyEvent.KEYCODE_ENTER -> kotlin.Unit
            KeyEvent.KEYCODE_BACK -> kotlin.Unit
            KeyEvent.KEYCODE_HOME -> kotlin.Unit
            KeyEvent.KEYCODE_MENU -> kotlin.Unit
        }
        return super.onKeyUp(navKeyCode, event)
    }

    private fun consumeInsets(insets: WindowInsets) {
        val app = mainApplication
        app.insetTop = insets.top
        app.insetBottom = insets.bottom
        app.insetStart = insets.start
        app.insetEnd = insets.end
        viewModel.insetTop.value = app.insetTop
        viewModel.insetBottom.value = app.insetBottom
        viewModel.insetStart.value = app.insetStart
        viewModel.insetEnd.value = app.insetEnd
    }

    private fun applyColor() {
        lifecycleScope.launch(Dispatchers.Default) {
            val colorFore: Int
            val colorBack: Int
            val isDarkStatus: Boolean
            val background = background
            if (mainApplication.exterior) {
                val offsetX = viewModel.insetStart.value ?: 0
                val colors = GraphicsUtil.matchBackgroundColor(viewBinding.mainTB, background, offsetX) // extremely heavy
                colorFore = colors[0]
                colorBack = colors[1]
                val backColor = colorBack and X.getColor(mContext, R.color.exteriorTransparencyColor)
                setToolbarBackColor(backColor)
                isDarkStatus = colorFore == Color.BLACK
                withContext(Dispatchers.Main) {
                    mWindow.let {
                        primaryStatusBarConfig = SystemBarConfig(isDarkStatus, isTransparentBar = true)
                        SystemUtil.applyStatusBarConfig(mContext, it, primaryStatusBarConfig!!)
                        val isTransparentNav = mainApplication.exterior || (viewModel.insetBottom.value ?: 0) < X.size(mContext, 15f, X.DP)
                        primaryNavBarConfig = SystemBarConfig(isDarkStatus, isTransparentBar = isTransparentNav)
                        SystemUtil.applyNavBarConfig(mContext, it, primaryNavBarConfig!!)
                    }
                }
            } else {
                colorBack = ThemeUtil.getColor(mContext, R.attr.colorTb)
                setToolbarBackColor(colorBack)
                withContext(Dispatchers.Main) {
                    mWindow.also {
                        val configs = SystemUtil.applyDefaultSystemUiVisibility(mContext, it, viewModel.insetBottom.value ?: 0)
                        primaryStatusBarConfig = configs.first
                        primaryNavBarConfig = configs.second
                    }
                }
            }
        }
    }

    private suspend fun setToolbarBackColor(color: Int) {
        withContext(Dispatchers.Main) {
            viewBinding.mainTB.setBackgroundColor(color)
        }
    }

    private fun applyExterior() {
        val app = mainApplication
        if (!app.exterior) return
        if (app.background == null) {
            background.background = null
            return
        }
        val back = mainApplication.background ?: return
        val size = SystemUtil.getRuntimeWindowSize(mContext)
        val bitmap = BackgroundUtil.getBackground(back, size.x, size.y)
        background.background = BitmapDrawable(mContext.resources, bitmap)
    }

    private fun initExterior() {
//        if (viewModel.background.value == mainApplication.background) return
        if (!isToolbarInflated) return
        viewModel.background.value = mainApplication.background
    }

    /**
     * for app background changing
     */
    private fun updateExterior() {
        mWindow.let {
            primaryNavBarConfig = SystemBarConfig(mainApplication.isPaleTheme, isTransparentBar = mainApplication.exterior)
            SystemUtil.applyNavBarConfig(mContext, it, primaryNavBarConfig!!)
        }
        if (!mainApplication.exterior) background.background = null
        initExterior()
    }
}
