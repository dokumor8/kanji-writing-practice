package com.example.kanjipractice.di

import android.content.Context
import androidx.room.Room
import com.example.fsrs.Fsrs
import com.example.kanjipractice.data.DataStoreStudySettingsRepository
import com.example.kanjipractice.data.DeckInitializer
import com.example.kanjipractice.data.DeckSeeder
import com.example.kanjipractice.data.DefaultCardRepository
import com.example.kanjipractice.data.DefaultReviewLogRepository
import com.example.kanjipractice.data.db.CardDao
import com.example.kanjipractice.data.db.KanjiDatabase
import com.example.kanjipractice.data.db.ReviewLogDao
import com.example.kanjipractice.domain.recognition.MlKitRecognitionService
import com.example.kanjipractice.domain.recognition.RecognitionService
import com.example.kanjipractice.domain.repository.CardRepository
import com.example.kanjipractice.domain.repository.ReviewLogRepository
import com.example.kanjipractice.domain.settings.StudySettingsRepository
import com.example.kanjipractice.domain.stroke.StrokeDataService
import com.example.kanjipractice.domain.stroke.StrokeDiagramProvider
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import java.time.ZoneId
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

    /**
     * The zone the *user* lives in, which is what decides when one study day
     * ends. Timestamps themselves are stored as UTC wall time.
     */
    @Provides
    @Singleton
    fun provideZoneId(): ZoneId = ZoneId.systemDefault()

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
    abstract fun bindDeckInitializer(impl: DeckSeeder): DeckInitializer

    @Binds
    @Singleton
    abstract fun bindCardRepository(impl: DefaultCardRepository): CardRepository

    @Binds
    @Singleton
    abstract fun bindStudySettingsRepository(
        impl: DataStoreStudySettingsRepository,
    ): StudySettingsRepository

    @Binds
    @Singleton
    abstract fun bindReviewLogRepository(impl: DefaultReviewLogRepository): ReviewLogRepository
}
