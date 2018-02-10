package com.turboturnip.turnipmusic.model.db;

import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.OnConflictStrategy;
import android.arch.persistence.room.Query;

import java.util.List;

@Dao
public interface SongEntityDao {
	@Query("SELECT * FROM " + DBConstants.SONG_TABLE + " WHERE id=:id")
	SongEntity getSong(int id);
	@Query("SELECT * FROM " + DBConstants.SONG_TABLE + " WHERE mediaId=:mediaId")
	SongEntity getSongByMediaId(String mediaId);
	@Query("SELECT * FROM " + DBConstants.SONG_TABLE + " WHERE name=:name")
	SongEntity getSongByName(String name);
	@Query("SELECT * FROM " + DBConstants.SONG_TABLE + " WHERE albumId=:albumId ORDER BY albumIndex")
	List<SongEntity> getSongsInAlbum(int albumId);

	@Insert(onConflict = OnConflictStrategy.REPLACE)
	void insertSong(SongEntity song);

	@Query("DELETE FROM " + DBConstants.SONG_TABLE)
	void clearDatabase();
}