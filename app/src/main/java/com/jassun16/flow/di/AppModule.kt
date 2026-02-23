package com.jassun16.flow.di

import android.content.Context
import com.jassun16.flow.data.db.AppDatabase
import com.jassun16.flow.data.network.ReadabilityFetcher
import com.jassun16.flow.data.network.RssParser
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)  // these objects live as long as the app lives
object AppModule {

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context  // Hilt auto-provides app context
    ): AppDatabase {
        return AppDatabase.getDatabase(context)
    }

    @Provides
    @Singleton
    fun provideRssParser(): RssParser {
        return RssParser()
    }

    @Provides
    @Singleton
    fun provideReadabilityFetcher(): ReadabilityFetcher {
        return ReadabilityFetcher()
    }
}
