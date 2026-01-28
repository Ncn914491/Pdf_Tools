package com.yourname.pdftoolkit.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface RecentFilesDao {
    @Query("SELECT * FROM recent_files ORDER BY lastAccessed DESC")
    suspend fun getAll(): List<RecentFileEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(file: RecentFileEntity)

    // For bulk migration
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(files: List<RecentFileEntity>)

    @Query("DELETE FROM recent_files WHERE uriString = :uriString")
    suspend fun deleteByUri(uriString: String)

    @Query("DELETE FROM recent_files")
    suspend fun clearAll()

    @Query("SELECT count(*) FROM recent_files")
    suspend fun getCount(): Int

    @Query("DELETE FROM recent_files WHERE uriString NOT IN (SELECT uriString FROM recent_files ORDER BY lastAccessed DESC LIMIT :limit)")
    suspend fun prune(limit: Int)

    @Query("UPDATE recent_files SET lastAccessed = :time WHERE uriString = :uriString")
    suspend fun updateLastAccessed(uriString: String, time: Long)
}
