package com.tunely.app.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "library_tracks")
data class LibraryTrack(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val album: String?,
    val durationMs: Long,
    val artworkUrl: String?,
    val source: String = SourceIds.YOUTUBE,
    val sourceUrl: String? = null,
    val streamUrl: String? = null,
    val live: Boolean = false,
    val addedAt: Long = System.currentTimeMillis()
) {
    fun toTrack() = Track(
        id = id, title = title, artist = artist, album = album, durationMs = durationMs,
        artworkUrl = artworkUrl, source = SourceIds.normalize(source), sourceUrl = sourceUrl,
        streamUrl = streamUrl, live = live
    )

    companion object {
        fun from(t: Track) = LibraryTrack(
            id = t.id, title = t.title, artist = t.artist, album = t.album,
            durationMs = t.durationMs, artworkUrl = t.artworkUrl,
            source = SourceIds.normalize(t.source), sourceUrl = t.sourceUrl,
            streamUrl = t.streamUrl, live = t.live
        )
    }
}

@Entity(tableName = "history")
data class HistoryEntry(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val trackId: String,
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    val source: String = SourceIds.YOUTUBE,
    val sourceUrl: String? = null,
    val streamUrl: String? = null,
    val durationMs: Long = 0,
    val album: String? = null,
    val genre: String? = null,
    val live: Boolean = false,
    val playedAt: Long = System.currentTimeMillis()
) {
    fun toTrack() = Track(
        id = trackId, title = title, artist = artist, album = album, durationMs = durationMs,
        artworkUrl = artworkUrl, source = SourceIds.normalize(source),
        sourceUrl = sourceUrl, streamUrl = streamUrl, genre = genre, live = live
    )

    companion object {
        fun from(t: Track) = HistoryEntry(
            trackId = t.id, title = t.title, artist = t.artist, artworkUrl = t.artworkUrl,
            source = SourceIds.normalize(t.source), sourceUrl = t.sourceUrl,
            streamUrl = t.streamUrl, durationMs = t.durationMs, album = t.album,
            genre = t.genre, live = t.live
        )
    }
}

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "playlist_tracks", primaryKeys = ["playlistId", "trackId"])
data class PlaylistTrack(
    val playlistId: Long,
    val trackId: String,
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    val position: Int,
    val album: String? = null,
    val durationMs: Long = 0,
    val source: String = SourceIds.YOUTUBE,
    val sourceUrl: String? = null,
    val streamUrl: String? = null,
    val live: Boolean = false
) {
    fun toTrack() = Track(
        id = trackId, title = title, artist = artist, album = album, durationMs = durationMs,
        artworkUrl = artworkUrl, source = SourceIds.normalize(source),
        sourceUrl = sourceUrl, streamUrl = streamUrl, live = live
    )

    companion object {
        fun from(playlistId: Long, t: Track, position: Int) = PlaylistTrack(
            playlistId = playlistId, trackId = t.id, title = t.title, artist = t.artist,
            artworkUrl = t.artworkUrl, position = position, album = t.album,
            durationMs = t.durationMs, source = SourceIds.normalize(t.source),
            sourceUrl = t.sourceUrl, streamUrl = t.streamUrl, live = t.live
        )
    }
}

@Dao
interface LibraryDao {
    @Query("SELECT * FROM library_tracks ORDER BY addedAt DESC")
    fun library(): Flow<List<LibraryTrack>>

    @Query("SELECT EXISTS(SELECT 1 FROM library_tracks WHERE id = :id AND source = :source)")
    fun isInLibrary(id: String, source: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(track: LibraryTrack)

    @Query("DELETE FROM library_tracks WHERE id = :id AND source = :source")
    suspend fun remove(id: String, source: String)

    @Insert
    suspend fun addHistory(entry: HistoryEntry)

    @Query("DELETE FROM history WHERE trackId = :trackId AND source = :source")
    suspend fun forgetPlays(trackId: String, source: String)

    /**
     * History shows each song once, at its most recent play. Replays move the
     * entry to the top instead of piling up duplicates.
     */
    @Transaction
    suspend fun recordPlay(entry: HistoryEntry) {
        forgetPlays(entry.trackId, entry.source)
        addHistory(entry)
        trimHistory()
    }

    @Query(
        "DELETE FROM history WHERE rowId NOT IN " +
            "(SELECT rowId FROM history ORDER BY playedAt DESC LIMIT 400)"
    )
    suspend fun trimHistory()

    @Query("SELECT * FROM history ORDER BY playedAt DESC LIMIT 60")
    fun recentlyPlayed(): Flow<List<HistoryEntry>>

    @Query("SELECT artist FROM history GROUP BY artist ORDER BY MAX(playedAt) DESC LIMIT 8")
    fun topArtists(): Flow<List<String>>

    @Query(
        "SELECT genre FROM history WHERE genre IS NOT NULL AND genre != '' " +
            "GROUP BY genre ORDER BY MAX(playedAt) DESC LIMIT 8"
    )
    fun topGenres(): Flow<List<String>>

    @Insert
    suspend fun createPlaylist(p: PlaylistEntity): Long

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylist(id: Long)

    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun playlists(): Flow<List<PlaylistEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addToPlaylist(t: PlaylistTrack)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun removeFromPlaylist(playlistId: Long, trackId: String)

    @Query("SELECT * FROM playlist_tracks WHERE playlistId = :id ORDER BY position")
    fun playlistTracks(id: Long): Flow<List<PlaylistTrack>>

    @Query("SELECT EXISTS(SELECT 1 FROM playlist_tracks WHERE playlistId = :id AND trackId = :trackId)")
    suspend fun playlistContains(id: Long, trackId: String): Boolean

    @Query("UPDATE playlist_tracks SET position = :position WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun reposition(playlistId: Long, trackId: String, position: Int)
}

@Database(
    entities = [LibraryTrack::class, HistoryEntry::class, PlaylistEntity::class, PlaylistTrack::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): LibraryDao

    companion object {
        /**
         * v1 stored tracks without their provider, so every saved SoundCloud or
         * Audius track tried to play through YouTube. v2 keeps the full source
         * information instead of throwing the library away.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE library_tracks ADD COLUMN source TEXT NOT NULL DEFAULT 'youtube'")
                db.execSQL("ALTER TABLE library_tracks ADD COLUMN sourceUrl TEXT")
                db.execSQL("ALTER TABLE library_tracks ADD COLUMN streamUrl TEXT")
                db.execSQL("ALTER TABLE library_tracks ADD COLUMN live INTEGER NOT NULL DEFAULT 0")

                db.execSQL("ALTER TABLE history ADD COLUMN source TEXT NOT NULL DEFAULT 'youtube'")
                db.execSQL("ALTER TABLE history ADD COLUMN sourceUrl TEXT")
                db.execSQL("ALTER TABLE history ADD COLUMN streamUrl TEXT")
                db.execSQL("ALTER TABLE history ADD COLUMN durationMs INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE history ADD COLUMN album TEXT")
                db.execSQL("ALTER TABLE history ADD COLUMN live INTEGER NOT NULL DEFAULT 0")

                db.execSQL("ALTER TABLE playlist_tracks ADD COLUMN album TEXT")
                db.execSQL("ALTER TABLE playlist_tracks ADD COLUMN durationMs INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE playlist_tracks ADD COLUMN source TEXT NOT NULL DEFAULT 'youtube'")
                db.execSQL("ALTER TABLE playlist_tracks ADD COLUMN sourceUrl TEXT")
                db.execSQL("ALTER TABLE playlist_tracks ADD COLUMN streamUrl TEXT")
                db.execSQL("ALTER TABLE playlist_tracks ADD COLUMN live INTEGER NOT NULL DEFAULT 0")

                db.execSQL("ALTER TABLE playlists ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * v3: history keeps one row per song (replays move it to the top
         * instead of stacking duplicates) and remembers the genre so the
         * recommendation engine can build mood/category profiles.
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE history ADD COLUMN genre TEXT")
                // Collapse existing duplicates, keeping the most recent play.
                db.execSQL(
                    "DELETE FROM history WHERE rowId NOT IN " +
                        "(SELECT MAX(rowId) FROM history GROUP BY trackId, source)"
                )
            }
        }

        fun create(ctx: Context) =
            Room.databaseBuilder(ctx, AppDatabase::class.java, "tunely.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
    }
}
