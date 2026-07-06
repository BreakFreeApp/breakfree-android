package com.breakfree.app.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.breakfree.app.data.db.entities.BlockedDomain
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockedDomainDao {

    @Query("SELECT * FROM blocked_domains ORDER BY domain ASC")
    fun observeAll(): Flow<List<BlockedDomain>>

    @Query("SELECT domain FROM blocked_domains")
    suspend fun getAllDomains(): List<String>

    @Query("SELECT * FROM blocked_domains WHERE domain = :domain")
    suspend fun getDomain(domain: String): BlockedDomain?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(domain: BlockedDomain)

    @Delete
    suspend fun delete(domain: BlockedDomain)

    @Query("DELETE FROM blocked_domains WHERE domain = :domain")
    suspend fun deleteByDomain(domain: String)
}
