package com.example.launcherdemo.util

import android.util.Log

/**
 *
 * @author Cheng
 * @since 2025/1/9
 */
object Logger {
    private const val TAG = "Launcher"
    const val MIN_STACK_OFFSET = 2
    const val VERBOSE = 2
    const val DEBUG = 3
    const val INFO = 4
    const val WARN = 5
    const val ERROR = 6
    const val NOTHING = Int.MAX_VALUE
    private var mLogLevel = VERBOSE
    private var mStackDeep = 1

    fun init(level: Int = VERBOSE) {
        mLogLevel = level
    }

    /**
     * 日志深度
     */
    fun setLogDeep(deep: Int) {
        mStackDeep = if (deep >= 7) 7 else deep
    }

    fun v(msg: String) = v(TAG, msg)

    fun d(msg: String) = d(TAG, msg)

    fun i(msg: String) = i(TAG, msg)

    fun w(msg: String) = w(TAG, msg)

    fun e(msg: String) = e(TAG, msg)

    fun e(msg: String, e: Throwable) = e(TAG, msg, e)

    fun v(tag: String, msg: String) {
        if (mLogLevel <= VERBOSE) {
            Log.v(tag, formatMSG(msg))
        }
    }

    fun d(tag: String, msg: String) {
        if (mLogLevel <= DEBUG) {
            Log.d(tag, formatMSG(msg))
        }
    }

    fun i(tag: String, msg: String) {
        if (mLogLevel <= INFO) {
            Log.i(tag, formatMSG(msg))
        }
    }

    fun w(tag: String, msg: String) {
        if (mLogLevel <= WARN) {
            Log.w(tag, formatMSG(msg))
        }
    }

    fun e(tag: String, msg: String) {
        if (mLogLevel <= ERROR) {
            Log.e(tag, formatMSG(msg))
        }
    }

    fun e(tag: String, msg: String, e: Throwable) {
        if (mLogLevel <= ERROR) {
            Log.e(tag, formatMSG(msg), e)
        }
    }

    private fun formatMSG(msg: String): String {
        val stackTrace = Exception().stackTrace
        val index = getStackOffSet(stackTrace)
        if (index == -1) {
            return "[Get LogInfo Error]"
        }
        val element = stackTrace[index]
        val className = element.fileName ?: "UnKnown"
        return "[($className:${element.lineNumber})#${element.methodName}] $msg"
    }

    private fun getStackOffSet(stackTrace: Array<StackTraceElement>?): Int {
        stackTrace?.let {
            for (index in MIN_STACK_OFFSET until it.size) {
                if (!it[index].className.equals(Logger.javaClass.name)) {
                    return index
                }
            }
        }
        return -1
    }
}