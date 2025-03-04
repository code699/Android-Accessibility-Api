package cn.vove7.andro_accessibility_api

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import cn.vove7.andro_accessibility_api.api.BaseServiceApi
import cn.vove7.andro_accessibility_api.utils.NeedAccessibilityException
import cn.vove7.andro_accessibility_api.utils.jumpAccessibilityServiceSettings
import cn.vove7.andro_accessibility_api.utils.whileWaitTime
import cn.vove7.andro_accessibility_api.viewnode.ViewNode
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.lang.Thread.sleep
import kotlin.math.min

/**
 *
 *
 * Created by Vove on 2018/6/18
 */
@Suppress("MemberVisibilityCanBePrivate")
abstract class AccessibilityApi : AccessibilityService(), BaseServiceApi {
    //implement of BaseServiceApi
    override val _baseService: AccessibilityService get() = this

    abstract val enableListenAppScope: Boolean

    var currentScope: AppScope? = null
        private set

    //activity or dialog
    var currentPage: String? = null
        private set
        get() = currentScope?.packageName

    override fun onCreate() {
        super.onCreate()
        if (this::class.java == BASE_SERVICE_CLS) {
            baseService = this
        }
        if (isEnableGestureService() && this::class.java == GESTURE_SERVICE_CLS) {
            gestureService = this
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (this::class.java == BASE_SERVICE_CLS) {
            baseService = null
        }
        if (isEnableGestureService() && this::class.java == GESTURE_SERVICE_CLS) {
            gestureService = null
        }
    }

    /**
     * ViewNode with rootInActiveWindow
     */
    val activeWinNode: ViewNode? get() = ViewNode.activeWinNode()

    //适应 多窗口 分屏
    val rootNodeOfAllWindows get() = ViewNode.getRoot()

    override fun getRootInActiveWindow(): AccessibilityNodeInfo? {
        return try {
            super.getRootInActiveWindow()
        } catch (e: Throwable) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 更新当前[currentScope]
     * @param pkg String
     * @param pageName String Activity or Dialog
     */
    private fun updateCurrentApp(pkg: String, pageName: String) {
        if (currentScope?.packageName == pkg && pageName == currentScope?.pageName) {
            return
        }
        if (
            pageName.startsWith("android.widget") ||
            pageName.startsWith("android.view") ||
            pageName.startsWith("android.inputmethodservice") ||
            pageIsView(pageName)
        ) {
            return
        }

        currentScope = currentScope?.also {
            it.pageName = pageName
            it.packageName = pkg
        } ?: AppScope(pkg, pageName)

        onPageUpdate(currentScope!!)
    }

    /**
     * Activity or Dialog update
     * @param currentScope AppScope
     */
    open fun onPageUpdate(currentScope: AppScope) {}

    private fun pageIsView(pageName: String): Boolean = try {
        View::class.java.isAssignableFrom(Class.forName(pageName))
    } catch (e: ClassNotFoundException) {
        false
    }

    /**
     * @param event AccessibilityEvent?
     */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!enableListenAppScope) return
        event ?: return

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            //界面切换
            val classNameStr = event.className
            val pkg = event.packageName as String?
            if (!classNameStr.isNullOrBlank() && pkg != null) {
                GlobalScope.launch {
                    updateCurrentApp(pkg, classNameStr.toString())
                }
            }
        }
    }

    override fun onInterrupt() {
    }

    companion object {
        lateinit var BASE_SERVICE_CLS: Class<*>
        lateinit var GESTURE_SERVICE_CLS: Class<*>

        @SuppressLint("StaticFieldLeak")
        private var appCtx_: Context? = null
        val appCtx
            get() = appCtx_ ?: throw NullPointerException(
                "please call AccessibilityApi.init(...) in Application.onCreate()")

        fun init(
            ctx: Context,
            baseServiceCls: Class<*>,
            gestureServiceCls: Class<*> = baseServiceCls
        ) {
            appCtx_ = ctx.applicationContext
            BASE_SERVICE_CLS = baseServiceCls
            GESTURE_SERVICE_CLS = gestureServiceCls
        }

        private fun isEnableGestureService() = ::GESTURE_SERVICE_CLS.isInitialized

        //无障碍基础服务
        @SuppressLint("StaticFieldLeak")
        var baseService: AccessibilityApi? = null

        val requireBase: AccessibilityApi
            get() = run {
                requireBaseAccessibility(false)
                baseService!!
            }

        //无障碍高级服务 执行手势等操作
        /**
         * GestureService base on AccessibilityApi
         */
        @SuppressLint("StaticFieldLeak")
        var gestureService: AccessibilityService? = null

        val requireGesture: AccessibilityService
            get() = run {
                requireGestureAccessibility(false)
                gestureService!!
            }

        // currentAppScope
        val currentScope get() = baseService?.currentScope

        // Service is enable
        val isBaseServiceEnable: Boolean
            get() = (baseService != null)

        val isGestureServiceEnable: Boolean get() = gestureService != null

        /**
         * 等待无障碍开启，最长等待30s
         * @param waitMillis Long
         * @return Boolean true 开启成功 ; false 超时
         * @throws NeedAccessibilityException
         */
        @JvmOverloads
        @JvmStatic
        @Throws(NeedAccessibilityException::class)
        suspend fun waitAccessibility(waitMillis: Long = 30000, cls: Class<*>): Boolean {

            val se = if (cls == BASE_SERVICE_CLS) isBaseServiceEnable
            else isGestureServiceEnable

            if (se) return true
            else jumpAccessibilityServiceSettings(cls)

            return whileWaitTime(min(30000, waitMillis)) {
                if (isBaseServiceEnable) true
                else {
                    sleep(500)
                    null
                }
            } ?: throw NeedAccessibilityException(cls.name)
        }

        //声明 需要基础无障碍权限
        fun requireBaseAccessibility(autoJump: Boolean = false) {
            if (!isBaseServiceEnable) {
                if (autoJump) jumpAccessibilityServiceSettings(BASE_SERVICE_CLS)
                throw NeedAccessibilityException(BASE_SERVICE_CLS.name)
            }
        }

        //声明 需要手势无障碍权限
        fun requireGestureAccessibility(autoJump: Boolean = false) {
            if (!isGestureServiceEnable) {
                if (autoJump) jumpAccessibilityServiceSettings(GESTURE_SERVICE_CLS)
                throw NeedAccessibilityException(GESTURE_SERVICE_CLS.name)
            }
        }

    }

}
