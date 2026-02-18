package com.kmz.miniserver

import java.net.Inet4Address
import java.net.NetworkInterface

object NetworkUtils {
    fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            var fallback: String? = null
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (address is Inet4Address && !address.isLoopbackAddress) {
                        val host = address.hostAddress ?: continue
                        if (host.startsWith("192.168.") ||
                                        host.startsWith("10.") ||
                                        host.startsWith("172.")
                        ) {
                            return host
                        }
                        if (fallback == null) fallback = host
                    }
                }
            }
            return fallback
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }
}
