package com.stopvpn.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import android.widget.Toast
import com.wireguard.android.backend.Backend
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import kotlinx.coroutines.*
import java.io.ByteArrayInputStream
import java.net.InetAddress

class VpnManager(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var backend: Backend? = null
    private var tunnel: WgTunnel? = null
    private var currentConfig: Config? = null
    private val futureBackend = CompletableDeferred<Backend>()
    private var currentServer: ServerInfo? = null

    var onStatusChanged: ((VpnStatus) -> Unit)? = null
    var onServerChanged: ((ServerInfo?) -> Unit)? = null

    companion object {
        private const val TAG = "StopVpnManager"
        private var globalStatus: VpnStatus = VpnStatus.DISCONNECTED
    }

    init {
        scope.launch(Dispatchers.IO) {
            try {
                backend = GoBackend(context)
                futureBackend.complete(backend!!)
                Log.i(TAG, "WireGuard backend initialized (ver 1.0.20230706)")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize backend: ${e.message}", e)
                futureBackend.completeExceptionally(e)
                showToast("Ошибка инициализации WireGuard: ${e.message}")
            }
        }
    }

    fun prepareVpn(activity: Activity): Boolean {
        val intent = GoBackend.VpnService.prepare(activity)
        return intent == null
    }

    fun getPrepareIntent(activity: Activity): android.content.Intent? {
        return GoBackend.VpnService.prepare(activity)
    }

    fun connect(server: ServerInfo) {
        scope.launch(Dispatchers.IO) {
            try {
                if (!validateKeys(server)) {
                    updateStatus(VpnStatus.DISCONNECTED)
                    return@launch
                }

                updateStatus(VpnStatus.CONNECTING)
                currentServer = server
                onServerChanged?.invoke(server)

                startVpnService(server.name)

                val config = buildConfig(server)
                currentConfig = config

                val tunnelName = "stopvpn_${server.id}"
                tunnel = WgTunnel(tunnelName) { state ->
                    scope.launch(Dispatchers.Main) {
                        Log.i(TAG, "Tunnel state changed: $state")
                        when (state) {
                            Tunnel.State.UP -> {
                                updateStatus(VpnStatus.CONNECTED)
                                testTunnelConnectivity()
                            }
                            Tunnel.State.DOWN -> updateStatus(VpnStatus.DISCONNECTED)
                            else -> updateStatus(VpnStatus.DISCONNECTED)
                        }
                    }
                }

                futureBackend.await().setState(tunnel!!, Tunnel.State.UP, config)
                Log.i(TAG, "Connected to ${server.name}")

            } catch (e: Exception) {
                Log.e(TAG, "Connection failed: ${e.message}", e)
                showToast("Ошибка подключения: ${e.message}")
                updateStatus(VpnStatus.ERROR)
                stopVpnService()
            }
        }
    }

    fun disconnect() {
        scope.launch(Dispatchers.IO) {
            try {
                updateStatus(VpnStatus.DISCONNECTING)
                tunnel?.let { t ->
                    futureBackend.await().setState(t, Tunnel.State.DOWN, currentConfig)
                }
                stopVpnService()
                updateStatus(VpnStatus.DISCONNECTED)
                currentServer = null
                onServerChanged?.invoke(null)
                Log.i(TAG, "Disconnected")
            } catch (e: Exception) {
                Log.e(TAG, "Disconnect failed: ${e.message}", e)
                showToast("Ошибка отключения: ${e.message}")
                updateStatus(VpnStatus.ERROR)
            }
        }
    }

    fun switchServer(newServer: ServerInfo) {
        scope.launch(Dispatchers.IO) {
            try {
                updateStatus(VpnStatus.SWITCHING)
                tunnel?.let { t ->
                    futureBackend.await().setState(t, Tunnel.State.DOWN, currentConfig)
                }
                delay(500)
                connect(newServer)
            } catch (e: Exception) {
                Log.e(TAG, "Switch failed: ${e.message}", e)
                showToast("Ошибка смены сервера: ${e.message}")
                updateStatus(VpnStatus.ERROR)
            }
        }
    }

    fun getStatus(): VpnStatus = globalStatus
