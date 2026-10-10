package app.ownbridge.core

import java.util.Base64

data class ShadowsocksExport(val plan: ShadowsocksPlan, val secret: String) {
    init {
        require(secret.matches(Regex("[A-Za-z0-9_-]{32,128}")))
    }

    val nodeUri: String
        get() {
            val userInfo = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("aes-256-gcm:$secret".toByteArray(Charsets.UTF_8))
            return "ss://$userInfo@${plan.address}:${plan.port}#OwnBridge-SS"
        }

    val clashYaml: String
        get() = """
            proxies:
              - name: OwnBridge-SS
                type: ss
                server: ${plan.address}
                port: ${plan.port}
                cipher: aes-256-gcm
                password: $secret
                udp: true
            proxy-groups:
              - name: OwnBridge
                type: select
                proxies:
                  - OwnBridge-SS
                  - DIRECT
            rules:
              - MATCH,OwnBridge
        """.trimIndent() + "\n"
}
