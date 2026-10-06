package com.tunely.app.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "library_tracks")
data class LibraryTrack(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val album: String?,
    val durationMs: Long,
    val artworkUrl: String?,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "history")
data class HistoryEntry(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val trackId: String,
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    val playedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String
)

@Entity(tableName = "playlist_tracks", primaryKeys = ["playlistId", "trackId"])
data class PlaylistTrack(
    val playlistId: Long,
    val trackId: String,
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    val position: Int
)

@Dao
interface LibraryDao {
    @Query("SELECT * FROM library_tracks ORDER BY addedAt DESC")
    fun library(): Flow<List<LibraryTrack>>

    @Query("SELECT EXISTS(SELECT 1 FROM library_tracks WHERE id = :id)")
    fun isInLibrary(id: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(track: LibraryTrack)

    @Query("DELETE FROM library_tracks WHERE id = :id")
    suspend fun remove(id: String)

    @Insert suspend fun addHistory(entry: HistoryEntry)

    @Query("SELECT * FROM history ORDER BY playedAt DESC LIMIT 50")
    fun recentlyPlayed(): Flow<List<HistoryEntry>>

    @Insert suspend fun createPlaylist(p: PlaylistEntity): Long

    @Query("SELECT * FROM playlists ORDER BY name")
    fun playlists(): Flow<List<PlaylistEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addToPlaylist(t: PlaylistTrack)

    @Query("SELECT * FROM playlist_tracks WHERE playlistId = :id ORDER BY position")
    fun playlistTracks(id: Long): Flow<List<PlaylistTrack>>
}

@Database(
    entities = [LibraryTrack::class, HistoryEntry::class, PlaylistEntity::class, PlaylistTrack::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): LibraryDao

    companion object {
        fun create(ctx: Context) =
            Room.databaseBuilder(ctx, AppDatabase::class.java, "tunely.db").build()
    }
}
