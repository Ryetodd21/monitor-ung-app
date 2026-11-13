package com.monitorung.app.data

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Test
import org.w3c.dom.Element

class BackupRulesTest {
    @Test fun cloudAndDeviceTransferExcludeAllPrivateStorage() {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File("src/main/res/xml/data_extraction_rules.xml"))
        val domains = setOf("root", "file", "database", "sharedpref", "external",
            "device_root", "device_file", "device_database", "device_sharedpref")
        for (mode in listOf("cloud-backup", "device-transfer")) {
            val rules = (document.getElementsByTagName(mode).item(0) as Element)
                .getElementsByTagName("exclude")
            val actual = (0 until rules.length).map { index ->
                val rule = rules.item(index) as Element
                assertEquals(".", rule.getAttribute("path"))
                rule.getAttribute("domain")
            }.toSet()
            assertEquals(domains, actual)
        }
    }
}
