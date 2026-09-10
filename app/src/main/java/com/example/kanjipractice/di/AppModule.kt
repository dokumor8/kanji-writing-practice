package com.example.kanjipractice.di

import android.content.Context
import androidx.room.Room
import com.example.fsrs.Fsrs
import com.example.kanjipractice.data.DefaultCardRepository
import com.example.kanjipractice.data.DefaultReviewLogRepository
import com.example.kanjipractice.data.db.CardDao
import com.example.kanjipractice.data.db.KanjiDatabase
import com.example.kanjipractice.data.db.ReviewLogDao
import com.example.kanjipractice.domain.recognition.MlKitRecognitionService
import com.example.kanjipractice.domain.recognition.RecognitionService
import com.example.kanjipractice.domain.repository.CardRepository
import com.example.kanjipractice.domain.repository.ReviewLogRepository
import com.example.kanjipractice.domain.stroke.StrokeDataService
import com.example.kanjipractice.domain.stroke.StrokeDiagramProvider
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /**
     * UTC, deliberately.
     *
     * Every [java.time.LocalDateTime] persisted by the app is UTC wall time (see
     * Converters), so "now" must be UTC too. Mixing a local-zone clock with
     * UTC-stored instants would shift due dates by the time-zone offset.
     */
    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemUTC()

    @Provides
    @Singleton
    fun provideFsrs(): Fsrs = Fsrs()

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): KanjiDatabase =
        Room.databaseBuilder(context, KanjiDatabase::class.java, KanjiDatabase.NAME)
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideCardDao(database: KanjiDatabase): CardDao = database.cardDao()

    @Provides
    fun provideReviewLogDao(database: KanjiDatabase): ReviewLogDao = database.reviewLogDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BindingsModule {

    @Binds
    @Singleton
    abstract fun bindRecognitionService(impl: MlKitRecognitionService): RecognitionService

    @Binds
    @Singleton
    abstract fun bindStrokeDiagramProvider(impl: StrokeDataService): StrokeDiagramProvider

    @Binds
    @Singleton
    abstract fun bindCardRepository(impl: DefaultCardRepository): CardRepository

    @Binds
    @Singleton
    abstract fun bindReviewLogRepository(impl: DefaultReviewLogRepository): ReviewLogRepository
}
