package com.example.kanjipractice.testing

import com.example.kanjipractice.data.DeckInitializer
import com.example.kanjipractice.data.db.CardEntity
import com.example.kanjipractice.data.db.ReviewLogEntity
import com.example.kanjipractice.domain.deck.DeckCatalog
import com.example.kanjipractice.domain.model.CardState
import com.example.kanjipractice.domain.model.Stroke
import com.example.kanjipractice.domain.recognition.ModelState
import com.example.kanjipractice.domain.recognition.RecognitionService
import com.example.kanjipractice.domain.repository.CardRepository
import com.example.kanjipractice.domain.repository.ReviewLogRepository
import com.example.kanjipractice.domain.settings.StudySettings
import com.example.kanjipractice.domain.settings.StudySettingsRepository
import com.example.kanjipractice.domain.stroke.StrokeDiagram
import com.example.kanjipractice.domain.stroke.StrokeDiagramProvider
import com.example.kanjipractice.domain.stroke.SvgPathCommand
import com.example.fsrs.Rating
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import java.time.LocalDateTime

/**
 * In-memory stand-ins for everything the ViewModels talk to.
 *
 * They are backed by StateFlows rather than snapshots so that a write made by the
 * code under test is visible to the next read -- that is what lets a test assert
 * "reviewing this card made the deck count go down".
 */
class FakeCardRepository(cards: List<CardEntity> = emptyList()) : CardRepository {

    val cards = MutableStateFlow(cards)
    val updates = mutableListOf<CardEntity>()

    private fun isNew(card: CardEntity) = card.state == CardState.NEW

    private fun CardEntity.isIn(deckIds: Set<String>) = deckId != null && deckId in deckIds

    override fun observeDueReviews(
        before: LocalDateTime,
        deckIds: Set<String>,
    ): Flow<List<CardEntity>> =
        cards.map { list ->
            list.filter { !isNew(it) && it.due.isBefore(before) && it.isIn(deckIds) }
                .sortedBy { it.due }
        }

    override fun observeDueReviewCount(
        before: LocalDateTime,
        deckIds: Set<String>,
    ): Flow<Int> =
        cards.map { list ->
            list.count { !isNew(it) && it.due.isBefore(before) && it.isIn(deckIds) }
        }

    override suspend fun nextNewCards(limit: Int, deckIds: Set<String>): List<CardEntity> =
        cards.value
            .filter { isNew(it) && it.isIn(deckIds) }
            .sortedWith(compareBy({ it.deckSortKey ?: Int.MAX_VALUE }, { it.id }))
            .take(limit.coerceAtLeast(0))

    override fun observeNewCount(deckIds: Set<String>): Flow<Int> =
        cards.map { list -> list.count { isNew(it) && it.isIn(deckIds) } }

    override fun observeTotalCount(deckIds: Set<String>): Flow<Int> =
        cards.map { list -> list.count { it.isIn(deckIds) } }

    override fun observeAll(deckIds: Set<String>): Flow<List<CardEntity>> =
        cards.map { list -> list.filter { it.isIn(deckIds) } }

    override fun observeDeckCounts(): Flow<Map<String, Int>> =
        cards.map { list ->
            list.mapNotNull { it.deckId }.groupingBy { it }.eachCount()
        }

    override suspend fun getById(id: Long): CardEntity? = cards.value.firstOrNull { it.id == id }

    override suspend fun update(card: CardEntity) {
        updates += card
        cards.value = cards.value.map { if (it.id == card.id) card else it }
    }

    override suspend fun count(): Int = cards.value.size
}

class FakeReviewLogRepository : ReviewLogRepository {

    val logs = MutableStateFlow<List<ReviewLogEntity>>(emptyList())
    private var nextId = 1L

    /** Newest first, like the real DAO. */
    val entries: List<ReviewLogEntity> get() = logs.value

    override suspend fun log(
        cardId: Long,
        rating: Rating,
        usedIDontKnow: Boolean,
        retryCount: Int,
        reviewedAt: LocalDateTime,
    ): Long {
        val id = nextId++
        logs.value = logs.value + ReviewLogEntity(
            id = id,
            cardId = cardId,
            rating = rating.value,
            usedIDontKnow = usedIDontKnow,
            retryCount = retryCount,
            reviewedAt = reviewedAt,
        )
        return id
    }

    override suspend fun delete(id: Long) {
        logs.value = logs.value.filterNot { it.id == id }
    }

    override fun observeRecent(limit: Int): Flow<List<ReviewLogEntity>> =
        logs.map { list -> list.sortedByDescending { it.reviewedAt }.take(limit) }

    override fun observeIntroducedSince(since: LocalDateTime): Flow<Int> =
        logs.map { list ->
            list.groupBy { it.cardId }
                .values
                .count { entries -> entries.minOf { it.reviewedAt } >= since }
        }
}

class FakeStudySettingsRepository(
    initialLimit: Int = StudySettings.DEFAULT_DAILY_NEW_LIMIT,
    initialDecks: Set<String> = DeckCatalog.DEFAULT_SELECTED,
    initialDeckDataVersion: Int = DeckCatalog.DATA_VERSION,
) : StudySettingsRepository {

    val limit = MutableStateFlow(initialLimit)
    val deckIds = MutableStateFlow(initialDecks)
    val deckDataVersion = MutableStateFlow(initialDeckDataVersion)
    val acceptedCandidates = MutableStateFlow(StudySettings.DEFAULT_ACCEPTED_CANDIDATES)
    val similarityPercent = MutableStateFlow(StudySettings.DEFAULT_SIMILARITY_PERCENT)

    override fun observeDailyNewLimit(): Flow<Int> = limit

    override suspend fun setDailyNewLimit(limit: Int) {
        this.limit.value = limit
    }

    override fun observeAcceptedCandidates(): Flow<Int> = acceptedCandidates

    override suspend fun setAcceptedCandidates(count: Int) {
        acceptedCandidates.value = count
    }

    override fun observeSimilarityThresholdPercent(): Flow<Int> = similarityPercent

    override suspend fun setSimilarityThresholdPercent(percent: Int) {
        similarityPercent.value = percent
    }

    override fun observeSelectedDeckIds(): Flow<Set<String>> = deckIds

    override suspend fun setSelectedDeckIds(ids: Set<String>) {
        deckIds.value = ids
    }

    override fun observeDeckDataVersion(): Flow<Int> = deckDataVersion

    override suspend fun setDeckDataVersion(version: Int) {
        deckDataVersion.value = version
    }
}

class FakeRecognitionService : RecognitionService {

    private val state = MutableStateFlow<ModelState>(ModelState.Ready)
    override val modelState: StateFlow<ModelState> = state

    var candidates: List<String> = emptyList()
    var failure: Exception? = null
    var prepareCount = 0
    var refreshCount = 0
    var reinstallCount = 0

    fun setModelState(value: ModelState) {
        state.value = value
    }

    override suspend fun refreshModelState() {
        refreshCount++
        failure?.let { throw it }
    }

    override suspend fun prepare() {
        prepareCount++
        failure?.let { throw it }
    }

    override suspend fun reinstallModel() {
        reinstallCount++
        failure?.let { throw it }
    }

    override suspend fun recognize(strokes: List<Stroke>): List<String> {
        failure?.let { throw it }
        return candidates
    }
}

class FakeStrokeDiagramProvider : StrokeDiagramProvider {
    override suspend fun diagramFor(character: String): StrokeDiagram = DIAGRAM

    companion object {
        val DIAGRAM = StrokeDiagram(
            viewBoxWidth = 109f,
            viewBoxHeight = 109f,
            strokes = listOf(
                listOf(SvgPathCommand.MoveTo(0f, 0f), SvgPathCommand.LineTo(10f, 10f)),
            ),
            numbers = emptyList(),
        )
    }
}

/**
 * @param onSeed what "seeding" does to the fake database, so a test can reproduce
 *   the real ordering where the deck lands *after* the screen has loaded.
 */
class FakeDeckInitializer(private val onSeed: suspend () -> Unit = {}) : DeckInitializer {
    var seedCount = 0
        private set

    override suspend fun syncDecks(): Int {
        seedCount++
        onSeed()
        return 0
    }
}
