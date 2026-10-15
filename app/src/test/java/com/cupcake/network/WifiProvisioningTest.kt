package com.cupcake.network

import com.cupcake.protocol.CommandResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WifiProvisioningTest {

    // --- Scan parsing -----------------------------------------------------

    @Test
    fun `parses scan entries with rssi and security`() {
        val raw = """[{"ssid":"TP-Link_2F3D","rssi":-42,"secure":true},
                      {"ssid":"OpenNet","rssi":-70,"secure":false}]"""
        val networks = WifiProvisioner.parseScan(raw)

        assertEquals(2, networks.size)
        assertEquals("TP-Link_2F3D", networks[0].ssid)
        assertEquals(-42, networks[0].rssi)
        assertTrue(networks[0].secure)
        assertEquals("OpenNet", networks[1].ssid)
        assertEquals(false, networks[1].secure)
    }

    @Test
    fun `skips entries without an ssid`() {
        val raw = """[{"rssi":-50,"secure":false},{"ssid":"Home","rssi":-30}]"""
        val networks = WifiProvisioner.parseScan(raw)
        assertEquals(1, networks.size)
        assertEquals("Home", networks[0].ssid)
    }

    @Test
    fun `returns empty list for malformed scan payload`() {
        assertTrue(WifiProvisioner.parseScan("not-json").isEmpty())
    }

    // --- JSON command responses ------------------------------------------

    @Test
    fun `decodes json response with stringified data values`() {
        val payload = """{"type":"resp","id":"abc","status":"ok","data":{"state":"connected","rssi":-55,"ip":"192.168.0.9"}}"""
        val decoded = ProtocolDecoder.decode(payload.toByteArray())

        assertNotNull(decoded)
        val response = decoded as CommandResponse
        assertEquals("abc", response.id)
        assertEquals("ok", response.status)
        assertEquals("connected", response.data?.get("state"))
        assertEquals("-55", response.data?.get("rssi"))
        assertEquals("192.168.0.9", response.data?.get("ip"))
    }

    @Test
    fun `decodes json response carrying a networks string`() {
        val payload = """{"type":"resp","id":"1","status":"ok","data":{"networks":"[{\"ssid\":\"A\",\"rssi\":-1,\"secure\":true}]"}}"""
        val decoded = ProtocolDecoder.decode(payload.toByteArray()) as CommandResponse
        val networks = WifiProvisioner.parseScan(decoded.data?.get("networks")!!)
        assertEquals("A", networks.single().ssid)
    }

    @Test
    fun `decodes error status response`() {
        val payload = """{"type":"resp","id":"9","status":"error","data":{"message":"Missing ssid"}}"""
        val decoded = ProtocolDecoder.decode(payload.toByteArray()) as CommandResponse
        assertEquals("error", decoded.status)
        assertEquals("Missing ssid", decoded.data?.get("message"))
    }

    // --- Command builders -------------------------------------------------

    @Test
    fun `builds wifi connect command with credentials`() {
        val command = ProtocolDecoder.Commands.wifiConnect("MyWifi", "secret")
        assertEquals("wifi", command.type)
        assertEquals("connect", command.action)
        assertEquals("MyWifi", command.params["ssid"])
        assertEquals("secret", command.params["password"])
    }

    @Test
    fun `builds wifi scan status and forget commands`() {
        assertEquals("scan", ProtocolDecoder.Commands.wifiScan().action)
        assertEquals("status", ProtocolDecoder.Commands.wifiStatus().action)
        assertEquals("forget", ProtocolDecoder.Commands.wifiForget().action)
    }
}
