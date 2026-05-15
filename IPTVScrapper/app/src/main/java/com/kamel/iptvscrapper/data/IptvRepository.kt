package com.kamel.iptvscrapper.data

import com.kamel.iptvscrapper.data.local.LinkDao
import com.kamel.iptvscrapper.data.local.entities.LinkEntity
import com.kamel.iptvscrapper.data.scraper.IptvScraper
import com.kamel.iptvscrapper.data.tester.IptvTester
import kotlinx.coroutines.flow.Flow

class IptvRepository(
    private val linkDao: LinkDao,
    private val scraper: IptvScraper,
    private val tester: IptvTester
) {
    val allLinks: Flow<List<LinkEntity>> = linkDao.getAllLinks()

    suspend fun scrapeAndSave(): Int = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val newLinks = scraper.scrapeLatest()
        val uniqueLinks = mutableListOf<LinkEntity>()
        for (link in newLinks) {
            if (linkDao.findDuplicate(link.url, link.username, link.mac) == null) {
                uniqueLinks.add(link)
            }
        }
        linkDao.insertLinks(uniqueLinks)
        uniqueLinks.size
    }

    suspend fun testLink(link: LinkEntity) {
        val updatedLink = tester.testLink(link)
        linkDao.updateLink(updatedLink)
    }
    
    suspend fun clearAll() = linkDao.deleteAll()

    suspend fun importFromText(text: String): Int = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val newLinks = scraper.parseText(text, "Manual Input")
        val uniqueLinks = mutableListOf<LinkEntity>()
        for (link in newLinks) {
            if (linkDao.findDuplicate(link.url, link.username, link.mac) == null) {
                uniqueLinks.add(link)
            }
        }
        linkDao.insertLinks(uniqueLinks)
        uniqueLinks.size
    }
}
