package com.turboturnip.turnipmusic.model;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;

import com.turboturnip.turnipmusic.model.db.SongTagDao;
import com.turboturnip.turnipmusic.model.db.SongTags;
import com.turboturnip.turnipmusic.utils.LogHelper;

import java.util.ArrayList;
import java.util.Iterator;

/**
 * Utility Class that gets music from the device using MediaSource
 */

public class DeviceMusicSource implements MusicProviderSource {

	private static final String TAG = LogHelper.makeLogTag(DeviceMusicSource.class);

	// TODO: Use the projections.
	private static String[] musicProjection = null;
	private static String[] genresProjection = null;
	private static String[] albumProjection = null;

	@Override
	public Iterator<Song> iterator(Context context, SongTagDao songTagDao) {
		try {
			ArrayList<Song> tracks = new ArrayList<>();
			Cursor musicCursor = context.getContentResolver().query(
					MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, musicProjection, null, null,
					null);


			if (musicCursor == null) {
				LogHelper.e(TAG, "Failed to retrieve music: Query Failed");
				return tracks.iterator();
			} else if (!musicCursor.moveToFirst()) {
				LogHelper.e(TAG, "No music found on the device!.");
				return tracks.iterator();
			}
			LogHelper.i(TAG, "Listing...");
			// retrieve the indices of the columns where the ID, title, etc. of the song are
			int titleColumn = musicCursor.getColumnIndex(MediaStore.Audio.Media.TITLE);
			int idColumn = musicCursor.getColumnIndex(MediaStore.Audio.Media._ID);
			int filePathColumn = musicCursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA);
			int albumFromMusicColumn = musicCursor.getColumnIndex(MediaStore.Audio.Media.ALBUM);
			int artistColumn = musicCursor.getColumnIndex(MediaStore.Audio.Media.ARTIST);
			int albumIDColumn = musicCursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID);
			int durationColumn = musicCursor.getColumnIndex(MediaStore.Audio.Media.DURATION);
			int trackNumberColumn = musicCursor.getColumnIndex(MediaStore.Audio.Media.TRACK);

			int isMusicColumn = musicCursor.getColumnIndex(MediaStore.Audio.Media.IS_MUSIC);

			Cursor albumCursor = context.getContentResolver().query(
					MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, albumProjection, null, null,
					null);
			if (albumCursor == null){
				LogHelper.e(TAG, "Failed to get albums");
				return tracks.iterator();
			}
			int albumNameColumn = albumCursor.getColumnIndex(MediaStore.Audio.Albums.ALBUM);
			int albumArtColumn = albumCursor.getColumnIndex(MediaStore.Audio.Albums.ALBUM_ART);
			int albumTotalSongsColumn = albumCursor.getColumnIndex(MediaStore.Audio.Albums.NUMBER_OF_SONGS);
			Cursor genresCursor;

			// add each song to mItems
			do {
				if (musicCursor.getInt(isMusicColumn) == 0) continue;

				String title = musicCursor.getString(titleColumn);
				String id = musicCursor.getString(idColumn);
				String filePath = musicCursor.getString(filePathColumn);
				String artist = musicCursor.getString(artistColumn);
				Long duration = musicCursor.getLong(durationColumn);
				Long trackNumber = musicCursor.getLong(trackNumberColumn);

				String genre = "";
				{
					Uri genreUri = MediaStore.Audio.Genres.getContentUriForAudioId("external", Integer.parseInt(id));
					genresCursor = context.getContentResolver().query(genreUri,
							genresProjection, null, null, null);
					int genreColumn = genresCursor.getColumnIndexOrThrow(MediaStore.Audio.Genres.NAME);
					if (genresCursor.moveToFirst()) {
						genre = genresCursor.getString(genreColumn);
						LogHelper.i("Found Genre: ", genre);
					}
					genresCursor.close();
				}

				int albumID = musicCursor.getInt(albumIDColumn);
				String album = "";
				String iconUrl = "";
				Long totalTrackCount = 0L;
				if (albumCursor.moveToPosition(albumID)){
					album = albumCursor.getString(albumNameColumn);
					iconUrl = albumCursor.getString(albumArtColumn);
					totalTrackCount = albumCursor.getLong(albumTotalSongsColumn);
				}else {
					album = musicCursor.getString(albumFromMusicColumn);
				}

				SongTags tags = songTagDao.getTags(id);
				if (tags == null){
					tags = new SongTags(id);
					songTagDao.insertTags(tags);
					LogHelper.i(TAG, "Song ", id, " wasn't in the database, adding an entry");
				}else{
					LogHelper.i(TAG, "Song ", id, " was in the database!");
				}

				tracks.add(new Song(title, id, filePath, album, artist, duration, genre, iconUrl, trackNumber, totalTrackCount, tags));
			} while (musicCursor.moveToNext());

			albumCursor.close();
			musicCursor.close();

			LogHelper.i(TAG, "Collected ", tracks.size(), " total songs");
			return tracks.iterator();
		} catch (Exception e) {
			LogHelper.e(TAG, e, "Could not retrieve music list");
			throw new RuntimeException("Could not retrieve music list", e);
		}
	}
}
