package com.fitbuds.app.bluetooth

data class PairedDeviceInfo(
    val name: String,
    val address: String,
    val isFitBuds: Boolean,
    val isConnected: Boolean,
)
