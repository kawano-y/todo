package com.kawano.todo.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [TaskEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class TodoDatabase : RoomDatabase() {

    abstract fun taskDao(): TaskDao

    companion object {
        const val DATABASE_NAME = "todo.db"

        /** アプリ本体用の DB を生成する。AppContainer で 1 度だけ呼び、シングルトンとして保持すること。 */
        fun create(context: Context): TodoDatabase =
            Room.databaseBuilder(context.applicationContext, TodoDatabase::class.java, DATABASE_NAME)
                .build()
    }
}
