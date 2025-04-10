package com.example.launcherdemo.util

/**
 *
 * @author cheng
 * @since 2025/4/7
 */
interface PermissionCallbackListener {

    fun onGrant()

    fun onDeny(permission: List<String>)
}