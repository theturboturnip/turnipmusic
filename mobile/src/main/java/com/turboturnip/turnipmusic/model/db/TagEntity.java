package com.turboturnip.turnipmusic.model.db;

import android.arch.persistence.room.ColumnInfo;
import android.arch.persistence.room.Entity;
import android.arch.persistence.room.Ignore;
import android.arch.persistence.room.PrimaryKey;
import android.support.annotation.NonNull;

import java.util.UUID;

@Entity(tableName = DBConstants.TAG_TABLE)
public class TagEntity {
	@PrimaryKey
	public final int id;
	public final int parentTableId;
	public final String name;

	@Ignore
	public TagEntity(String name){
		this.id = name.hashCode();
		this.name = name;
		this.parentTableId = -1;
	}
	@Ignore
	public TagEntity(String name, TagEntity parent){
		this.id = UUID.randomUUID().hashCode();
		this.name = name;
		this.parentTableId = parent.id;
	}

	public TagEntity(int id, int parentTableId, String name){
		this.id = id;
		this.parentTableId = parentTableId;
		this.name = name;
	}
}
