package com.monitorung.app.data.push

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class MqttSignatureTest {
    @Test
    fun signatureMatchesBackendAndBindsTopicPayloadAndTime() {
        val topic = "students/student-one/logs"
        val payload = "{\"total_screen_time_minutes\":10}"
        val token = "test-session-secret"
        val signature = mqttSignature(topic, payload, 1700000000L, token)
        assertEquals("946031d167e9a0c1a91817ca33f037c786dcf1632671570c853b8f29163bfbf6", signature)
        assertNotEquals(signature, mqttSignature("students/student-two/logs", payload, 1700000000L, token))
        assertNotEquals(signature, mqttSignature(topic, "{}", 1700000000L, token))
        assertNotEquals(signature, mqttSignature(topic, payload, 1700000001L, token))
        assertNotEquals(signature, mqttSignature(topic, payload, 1700000000L, "another-session"))
    }
}
