package com.yourname.pdftoolkit.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.yourname.pdftoolkit.data.PersistedFile

@Entity(tableName = "recent_files")
data class RecentFileEntity(
    @PrimaryKey val uriString: String,
    val name: String,
    val mimeType: String,
    val size: Long,
    val lastAccessed: Long
) {
    fun toPersistedFile(): PersistedFile {
        return PersistedFile(uriString, name, mimeType, size, lastAccessed)
    }

    companion object {
        fun fromPersistedFile(file: PersistedFile): RecentFileEntity {
            return RecentFileEntity(
                uriString = file.uriString,
                name = file.name,
                mimeType = file.mimeType,
                size = file.size,
                lastAccessed = file.lastAccessed
            )
        }
    }
}
