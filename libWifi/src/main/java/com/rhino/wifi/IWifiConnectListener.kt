package com.rhino.wifi

/**
 * @author LuoLin
 * @since Create on 2022/4/22.
 */
interface IWifiConnectListener {
    /**
     * 连接成功
     */
    fun onConnectSuccess()

    /**
     * 连接失败
     */
    fun onConnectFail(errorCode: Int, errorMsg: String?)

    companion object {
        const val WIFI_CONNECT_SUCCESS = 0
        const val WIFI_CONNECT_FAILED = -1
        const val WIFI_DISABLE = -2
        const val WIFI_NOT_FOUND = -3
    }
}