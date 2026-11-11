package com.dj.dot.data

import org.junit.Assert.assertEquals
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class BackupRulesTest {
    private fun rules(name: String): Element = DocumentBuilderFactory.newInstance()
        .newDocumentBuilder().parse(File("src/main/res/xml/$name")).documentElement

    private fun assertPortableOnly(parent: Element, encrypted: Boolean = false) {
        val includes = parent.getElementsByTagName("include")
        assertEquals(1, includes.length)
        val include = includes.item(0) as Element
        assertEquals("sharedpref", include.getAttribute("domain"))
        assertEquals("dot_portable.xml", include.getAttribute("path"))
        assertEquals(if (encrypted) "clientSideEncryption" else "", include.getAttribute("requireFlags"))
    }

    @Test fun android10And11BackUpOnlyEncryptedPortableSettings() {
        val legacy = rules("backup_rules.xml")
        assertEquals("full-backup-content", legacy.tagName)
        assertPortableOnly(legacy, encrypted = true)
    }

    @Test fun android12AndLaterNeverTransferMediaStoreIdsOrHistory() {
        val modern = rules("data_extraction_rules.xml")
        assertEquals("data-extraction-rules", modern.tagName)

        val cloud = modern.getElementsByTagName("cloud-backup")
        assertEquals(1, cloud.length)
        val encryptedCloud = cloud.item(0) as Element
        assertEquals("true", encryptedCloud.getAttribute("disableIfNoEncryptionCapabilities"))
        assertPortableOnly(encryptedCloud)

        val transfer = modern.getElementsByTagName("device-transfer")
        assertEquals(1, transfer.length)
        assertPortableOnly(transfer.item(0) as Element)
    }
}
