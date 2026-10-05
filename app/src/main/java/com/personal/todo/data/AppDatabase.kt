package com.personal.todo.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

val DEFAULT_CATEGORIES = listOf("Personal", "Work", "Important", "Shopping", "Finance", "Other")

@Database(
    entities = [TaskEntity::class, SubtaskEntity::class, CategoryEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun subtaskDao(): SubtaskDao
    abstract fun categoryDao(): CategoryDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "todo.db")
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        val now = System.currentTimeMillis()
                        DEFAULT_CATEGORIES.forEach {
                            db.execSQL("INSERT INTO categories (name, createdAt) VALUES (?, ?)", arrayOf<Any>(it, now))
                        }
                    }
                })
                .build()
    }
}
