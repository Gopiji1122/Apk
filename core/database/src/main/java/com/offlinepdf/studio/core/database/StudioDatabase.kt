package com.offlinepdf.studio.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Database(
    entities = [DocumentEntity::class, BookmarkEntity::class, ScanSessionPageEntity::class],
    version = 2,
    exportSchema = false
)
abstract class StudioDatabase : RoomDatabase() {
    abstract fun documentDao(): DocumentDao
    abstract fun scanSessionDao(): ScanSessionDao
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideStudioDatabase(@ApplicationContext context: Context): StudioDatabase =
        Room.databaseBuilder(context, StudioDatabase::class.java, "offline_pdf_studio.db")
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun provideDocumentDao(db: StudioDatabase): DocumentDao = db.documentDao()

    @Provides
    fun provideScanSessionDao(db: StudioDatabase): ScanSessionDao = db.scanSessionDao()
}
