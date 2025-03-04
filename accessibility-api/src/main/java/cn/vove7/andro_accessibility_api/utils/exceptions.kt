package cn.vove7.andro_accessibility_api.utils

import android.accessibilityservice.GestureDescription
import cn.vove7.andro_accessibility_api.AccessibilityApi
import cn.vove7.andro_accessibility_api.viewfinder.ViewFinder

/**
 * # exceptions
 * 异常类合集
 * @author Administrator
 * 2018/12/20
 */

/**
 * 视图搜索失败异常
 */
class ViewNodeNotFoundException : Exception {
    constructor(finder: ViewFinder<*>)
        : super("ViewNodeNotFound: ${finder.finderInfo()}")

    constructor(msg: String) : super(msg)
}

/**
 * 无障碍服务未运行异常
 * @constructor
 */
open class NeedAccessibilityException(name: String?) : RuntimeException("无障碍服务未运行: $name")

class NeedBaseAccessibilityException :
    NeedAccessibilityException(AccessibilityApi.BASE_SERVICE_CLS.name)

class NeedGestureAccessibilityException :
    NeedAccessibilityException(AccessibilityApi.GESTURE_SERVICE_CLS.name)

class GestureCanceledException(
    val gestureDescription: GestureDescription
) : RuntimeException()
