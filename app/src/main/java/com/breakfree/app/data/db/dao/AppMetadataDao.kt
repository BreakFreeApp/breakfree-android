package com.breakfree.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.breakfree.app.data.db.entities.AppMetadata

@Dao
interface AppMetadataDao {
    @Query("SELECT * FROM app_metadata_cache")
    suspend fun getAll(): List<AppMetadata>

    @Query("SELECT * FROM app_metadata_cache WHERE packageName IN (:packageNames)")
    suspend fun getByPackageNames(packageNames: List<String>): List<AppMetadata>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(apps: List<AppMetadata>)

    @Query("UPDATE app_metadata_cache SET isFavorite = :isFavorite WHERE packageName = :packageName")
    suspend fun updateFavorite(packageName: String, isFavorite: Boolean)

    @Query("UPDATE app_metadata_cache SET isDoomscrollWhitelisted = :isWhitelisted WHERE packageName = :packageName")
    suspend fun updateDoomscrollWhitelisted(packageName: String, isWhitelisted: Boolean)

    @Query("DELETE FROM app_metadata_cache")
    suspend fun clearAll()
}
