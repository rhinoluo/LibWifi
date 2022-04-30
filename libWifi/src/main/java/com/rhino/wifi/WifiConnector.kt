package com.rhino.wifi

import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.text.TextUtils
import com.rhino.log.LogUtils

/**
 * @author LuoLin
 * @since Create on 2022/4/22.
 */
internal class WifiConnector {
    private var wifiConnectListener: IWifiConnectListener? = null
    private var wifiManager: WifiManager? = null
    private val handler: Handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            checkInit()
            if (msg.what == 0) {
                wifiConnectListener!!.onConnectSuccess()
            } else {
                wifiConnectListener!!.onConnectFail(msg.what, msg.obj as String)
            }
        }
    }

    /**
     * 初始化
     */
    fun init(wifiManager: WifiManager?) {
        requireNotNull(wifiManager) { "WifiConnector wifiManager cant be null!" }
        this.wifiManager = wifiManager
    }

    /**
     * 检查是否初始化
     */
    private fun checkInit() {
        requireNotNull(wifiManager) { "You must call init()  before other methods!" }
        requireNotNull(wifiConnectListener) { "IWifiConnectListener cant be null!" }
    }

    /**
     * 子线程要向UI发送连接的消息
     */
    fun sendHandleMsg(errorCode: Int, errorMsg: String?) {
        val msg = Message()
        msg.obj = errorMsg
        msg.what = errorCode
        handler.sendMessage(msg)
    }

    /**
     * 提供一个外部接口，传入要连接的无线网
     */
    fun connect(
        ssid: String,
        password: String?,
        listener: IWifiConnectListener,
        type: WifiUtils.WifiCipherType?
    ) {
        wifiConnectListener = listener
        val thread = Thread(ConnectRunnable(ssid, password, type))
        thread.start()
    }

    internal inner class ConnectRunnable(
        private val ssid: String,
        private val password: String?,
        private val type: WifiUtils.WifiCipherType?
    ) : Runnable {
        override fun run() {
            checkInit()
            try {
                // 如果之前没打开wifi,就去打开  确保wifi开关开了
                WifiManagerProxy.get().openWifi()
                //开启wifi需要等系统wifi刷新1秒的时间
                Thread.sleep(1000)

                // 如果wifi没开启的话就提示错误
                if (!WifiManagerProxy.get().isWifiEnabled()) {
                    sendHandleMsg(IWifiConnectListener.WIFI_DISABLE, "WIFI未开启")
                    return
                }
                // 开启wifi之后开始扫描附近的wifi列表
                WifiManagerProxy.get().startScan()
                // 延迟1秒获取扫码列表
                Thread.sleep(1000)
                var hasSsIdWifi = false
                val scanResults = WifiManagerProxy.get().getScanResults()
                if (scanResults != null) {
                    for (i in scanResults.indices) {
                        val scanResult = scanResults[i]
                        if (TextUtils.equals(scanResult.SSID, ssid)) {
                            hasSsIdWifi = true
                            break
                        }
                    }
                }
                // 如果就没这个wifi的话直接返回
                if (!hasSsIdWifi) {
                    sendHandleMsg(IWifiConnectListener.WIFI_NOT_FOUND, "未找到$ssid，请稍后重试")
                    return
                }

                // 开始连接
                if (WifiManagerProxy.get().connectWifi(ssid, password, type)) {
                    sendHandleMsg(IWifiConnectListener.WIFI_CONNECT_SUCCESS, "连接成功")
                } else {
                    sendHandleMsg(IWifiConnectListener.WIFI_CONNECT_FAILED, "连接失败")
                }
            } catch (e: Exception) {
                LogUtils.e(e.toString())
                sendHandleMsg(IWifiConnectListener.WIFI_CONNECT_FAILED, "连接失败")
            }
        }
    }
}