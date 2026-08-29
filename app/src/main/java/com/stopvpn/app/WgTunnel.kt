package com.stopvpn.app

import com.wireguard.android.backend.Tunnel

class WgTunnel(
    private val name: String,
    private val onStateChange: (Tunnel.State) -> Unit
) : Tunnel {

    override fun getName(): String = name

    override fun onStateChange(newState: Tunnel.State) {
        onStateChange.invoke(newState)
    }
}
