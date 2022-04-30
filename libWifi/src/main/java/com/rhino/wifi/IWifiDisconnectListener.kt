package com.rhino.wifi

/**
 * @author LuoLin
 * @since Create on 2022/4/22.
 */
interface IWifiDisconnectListener {
    /**
     * 断开成功
     */
    fun onDisconnectSuccess()

    /**
     * 断开失败
     */
    fun onDisconnectFail(errorMsg: String?)
}