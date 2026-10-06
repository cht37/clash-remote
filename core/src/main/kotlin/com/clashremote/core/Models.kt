package com.clashremote.core

import kotlinx.serialization.Serializable

@Serializable data class RuntimeConfig(val mode: String)
@Serializable data class ProxySnapshot(val proxies: Map<String, ProxyInfo>)
@Serializable data class DelayRecord(val delay: Int = 0, val time: String = "")
@Serializable data class ProxyInfo(
    val name: String = "", val type: String = "",
    val all: List<String>? = null, val now: String = "",
    val history: List<DelayRecord> = emptyList(), val hidden: Boolean = false,
)
@Serializable data class Traffic(val up: Long = 0, val down: Long = 0)
@Serializable data class ConnectionMetadata(
    val host: String = "", val sourceIP: String = "", val sourcePort: String = "",
    val destinationIP: String = "", val destinationPort: String = "",
    val network: String = "", val type: String = "", val process: String = "",
)
@Serializable data class ConnectionInfo(
    val id: String, val metadata: ConnectionMetadata = ConnectionMetadata(),
    val upload: Long = 0, val download: Long = 0, val chains: List<String> = emptyList(),
    val rule: String = "", val rulePayload: String = "", val start: String = "",
) {
    val destination: String get() = metadata.host.ifEmpty { metadata.destinationIP.ifEmpty { "未知目标" } }
}
@Serializable data class ConnectionSnapshot(
    val uploadTotal: Long = 0, val downloadTotal: Long = 0,
    val connections: List<ConnectionInfo> = emptyList(),
)
enum class ConnectionStatus { DISCONNECTED, CONNECTING, ONLINE, OFFLINE, PAUSED }
data class RemoteState(
    val profile: RouterProfile? = null, val status: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val version: String = "", val mode: String = "rule", val traffic: Traffic = Traffic(),
    val trafficHistory: List<Traffic> = emptyList(), val snapshot: ConnectionSnapshot = ConnectionSnapshot(),
    val proxies: Map<String, ProxyInfo> = emptyMap(), val delays: Map<String, Int> = emptyMap(),
    val testing: Set<String> = emptySet(), val busy: Boolean = false, val error: String? = null,
)
