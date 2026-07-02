package com.breakfree.app.data.repository

import com.breakfree.app.data.db.dao.BlockedAppDao
import com.breakfree.app.data.db.dao.BlockedDomainDao
import com.breakfree.app.data.db.entities.BlockedApp
import com.breakfree.app.data.db.entities.BlockedDomain
import kotlinx.coroutines.flow.Flow

class BlockingRepository(
    private val appDao: BlockedAppDao,
    private val domainDao: BlockedDomainDao
) {
    fun observeBlockedApps(): Flow<List<BlockedApp>> = appDao.observeAll()
    fun observeBlockedDomains(): Flow<List<BlockedDomain>> = domainDao.observeAll()

    suspend fun blockedPackageNames(): Set<String> = appDao.getAllPackageNames().toSet()
    suspend fun blockedDomains(): Set<String> = domainDao.getAllDomains().toSet()

    suspend fun addApp(packageName: String, appName: String) =
        appDao.insert(BlockedApp(packageName, appName))

    suspend fun removeApp(packageName: String) = appDao.deleteByPackageName(packageName)

    suspend fun addDomain(domain: String) =
        domainDao.insert(BlockedDomain(domain.lowercase().trim()))

    suspend fun removeDomain(domain: String) = domainDao.deleteByDomain(domain)
}
