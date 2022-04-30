package com.rhino.wifi

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import androidx.core.app.ActivityCompat
import androidx.core.content.PermissionChecker
import com.rhino.log.LogUtils

/**
 * @author LuoLin
 * @since Create on 2022/4/22.
 */
class WifiManagerProxy private constructor() {

    private object SingletonHolder {
        val INSTANCE = WifiManagerProxy()
    }

    private var context: Context? = null
    private var wifiManager: WifiManager? = null
    private val wifiConnector = WifiConnector()

    /**
     * 检查权限
     */
    fun checkPermission(permissions: Array<String>): Boolean {
        for (permission in permissions) {
            if (PermissionChecker.checkSelfPermission(
                    context!!,
                    permission
                ) != PermissionChecker.PERMISSION_GRANTED
            ) {
                return false
            }
        }
        return true
    }

    /**
     * 请求权限
     */
    fun requestPermissions(activity: Activity?, permissions: Array<String>, requestCode: Int) {
        ActivityCompat.requestPermissions(activity!!, permissions, requestCode)
    }

    /**
     * 检查是否初始化
     */
    private fun checkInit() {
        requireNotNull(wifiManager) { "You must call init()  before other methods!" }
    }

    /**
     * 初始化
     */
    fun init(application: Application) {
        context = application.applicationContext
        if (wifiManager == null) {
            wifiManager = application.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wifiConnector.init(wifiManager)
        }
    }

    /**
     * 获取ssid的加密方式
     */
    fun getCipherType(ssid: String?): WifiUtils.WifiCipherType? {
        if (wifiManager == null) {
            return null
        }
        val list = getScanResults() ?: arrayListOf()
        for (scResult in list) {
            if (!TextUtils.isEmpty(scResult.SSID) && scResult.SSID == ssid) {
                return WifiUtils.getCipherType(scResult)
            }
        }
        return WifiUtils.WifiCipherType.INVALID
    }

    /**
     * 开始扫描
     */
    fun startScan() {
        checkInit()
        wifiManager!!.startScan()
    }

    /**
     * 获取扫描列表
     */
    fun getScanResults(): List<ScanResult>? {
        checkInit()
        return wifiManager!!.scanResults
    }

    /**
     * 是否已连接过该wifi
     */
    fun isExist(ssid: String): WifiConfiguration? {
        checkInit()
        if (ActivityCompat.checkSelfPermission(
                context!!,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }
        val configs = wifiManager!!.configuredNetworks
        for (config in configs) {
            if (config.SSID == "\"" + ssid + "\"") {
                return config
            }
        }
        return null
    }


    /**
     * 连接
     */
    fun connectWifi(
        ssid: String?,
        password: String?,
        type: WifiUtils.WifiCipherType? = null
    ): Boolean {
        checkInit()
        val tempConfig = WifiUtils.isExsit(wifiManager, ssid)
        //禁掉所有wifi
        for (c in wifiManager!!.configuredNetworks) {
            wifiManager!!.disableNetwork(c.networkId)
        }
        return if (tempConfig != null) {
            wifiManager!!.enableNetwork(tempConfig.networkId, true)
        } else {
            var t = type
            if (type == null) {
                t = getCipherType(ssid)
            }
            val wifiConfig = WifiUtils.createWifiInfo(ssid, password, t)
            val netID = wifiManager!!.addNetwork(wifiConfig)
            wifiManager!!.enableNetwork(netID, true)
        }
    }

    /**
     * 连接wifi
     */
    fun connectWithScan(
        ssid: String,
        password: String?,
        listener: IWifiConnectListener,
        type: WifiUtils.WifiCipherType? = null
    ) {
        LogUtils.d("ssid: $ssid, password: $password")
        checkInit()
        if (isConnected(ssid)) {
            listener.onConnectSuccess()
            return
        }
        wifiConnector.connect(ssid, password, listener, type)
    }

    /**
     * 断开链接wifi
     */
    fun disconnect(ssid: String, listener: IWifiDisconnectListener) {
        LogUtils.d("ssid: $ssid")
        checkInit()
        if (TextUtils.isEmpty(ssid)) {
            listener.onDisconnectFail("WIFI名称不能为空! ")
            return
        }
        val tmpSsid = "\"" + ssid + "\""
        val wifiInfo = wifiManager!!.connectionInfo
        if (wifiInfo != null && TextUtils.equals(tmpSsid, wifiInfo.ssid)) {
            val netId = wifiInfo.networkId
            wifiManager!!.disableNetwork(netId)
            listener.onDisconnectSuccess()
        } else {
            listener.onDisconnectFail("wifi状态异常 或者 此时就没有连接上对应的WIFI ！")
        }
    }

    /**
     * 是否连接到wifi
     */
    fun isConnected(ssid: String?): Boolean {
        checkInit()
        val tmpSsid = "\"" + ssid + "\""
        val wifiInfo = wifiManager!!.connectionInfo
        return wifiInfo != null && TextUtils.equals(tmpSsid, wifiInfo.ssid)
    }

    /**
     * 打开wifi
     */
    fun openWifi(): Boolean {
        checkInit()
        if (isWifiEnabled()) {
            return true
        }
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
//            openWifiSettingPage()
//            return false
//        }
        return wifiManager!!.setWifiEnabled(true)
    }

    /**
     * 关闭wifi
     */
    fun closeWifi(): Boolean {
        checkInit()
        if (!isWifiEnabled()) {
            return true
        }
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
//            openWifiSettingPage()
//            return false
//        }
        return wifiManager!!.setWifiEnabled(false)
    }

    /**
     * wifi是否开启
     */
    fun isWifiEnabled(): Boolean {
        checkInit()
        return wifiManager!!.isWifiEnabled
    }

    /**
     * 获取Wifi ConfiguredNetworks
     */
    val configuredNetworks: List<WifiConfiguration>?
        get() {
            checkInit()
            return if (ActivityCompat.checkSelfPermission(
                    context!!,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                null
            } else wifiManager!!.configuredNetworks
        }

    /**
     * 打开wifi设置界面
     */
    fun openWifiSettingPage() {
        checkInit()
        val intent = Intent(Settings.ACTION_WIFI_SETTINGS)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context!!.startActivity(intent)
    }

    fun getConnectedSsid(): String? {
        (context?.applicationContext?.getSystemService(Context.WIFI_SERVICE)
                as? WifiManager)?.let {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                val configuredNetworks = it.configuredNetworks
                if (configuredNetworks?.isNotEmpty() == true)
                    for (config in configuredNetworks) {
                        val ssid = config.SSID.substring(1, config.SSID.length - 1)

                        if (config.status == WifiConfiguration.Status.CURRENT) {
                            return ssid
                        }
                    }
            } else {
                val connManager = context?.applicationContext
                    ?.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                val networkInfo = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI)
                if (networkInfo?.isConnected == true) {
                    val wifiInfo = it.connectionInfo
                    val ssid = wifiInfo.ssid
                    return ssid.substring(1, ssid.length - 1)
                }
            }
        }
        return null
    }

    fun getConnectedBssid(): String? {
        (context?.applicationContext?.getSystemService(Context.WIFI_SERVICE)
                as? WifiManager)?.let {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                val configuredNetworks = it.configuredNetworks
                if (configuredNetworks?.isNotEmpty() == true)
                    for (config in configuredNetworks) {
                        val bssid = config.BSSID

                        if (config.status == WifiConfiguration.Status.CURRENT) {
                            return bssid
                        }
                    }
            } else {
                val connManager = context?.applicationContext
                    ?.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                val networkInfo = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI)
                if (networkInfo?.isConnected == true) {
                    val wifiInfo = it.connectionInfo
                    return wifiInfo.bssid
                }
            }
        }
        return null
    }

    fun isHexWepKey(wepKey: String?): Boolean {
        checkInit()
        val len = wepKey?.length
        // WEP-40, WEP-104, and some vendors using 256-bit WEP (WEP-232?)
        return if (len != 10 && len != 26 && len != 58) {
            false
        } else isHex(wepKey)
    }

    fun isHex(key: String): Boolean {
        checkInit()
        for (i in key.length - 1 downTo 0) {
            val c = key[i]
            if (!(c in '0'..'9' || c in 'A'..'F' || (c in 'a'..'f'))
            ) {
                return false
            }
        }
        return true
    }

    companion object {
        val PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.CHANGE_NETWORK_STATE
        )

        @JvmStatic
        fun get(): WifiManagerProxy {
            return SingletonHolder.INSTANCE
        }
    }
}