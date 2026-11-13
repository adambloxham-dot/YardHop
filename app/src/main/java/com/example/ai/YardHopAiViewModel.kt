package com.example.ai

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.DEFAULT_SALE_CATEGORIES
import com.example.data.model.YardSale
import com.example.ui.viewmodel.SaleWithDistance
import com.example.util.PhotoHelper
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.util.Locale

data class ListingDraftResult(
    val title: String,
    val description: String,
    val priceRangeLabel: String,
    val categories: List<String>
)

sealed interface AiDraftUiState {
    data object Idle : AiDraftUiState
    data object Loading : AiDraftUiState
    data class Success(val draft: ListingDraftResult) : AiDraftUiState
    data class Error(val message: String) : AiDraftUiState
}

sealed interface AiStreamUiState {
    data object Idle : AiStreamUiState
    data object Loading : AiStreamUiState
    data class Streaming(val text: String) : AiStreamUiState
    data class Success(val text: String) : AiStreamUiState
    data class Error(val message: String) : AiStreamUiState
}

data class TreasureMatchItem(
    val saleId: String,
    val title: String,
    val reason: String,
    val distanceMiles: Double
)

data class TreasureMatchResult(
    val summary: String,
    val matches: List<TreasureMatchItem>
)

sealed interface AiTreasureMatchUiState {
    data object Idle : AiTreasureMatchUiState
    data object Loading : AiTreasureMatchUiState
    data class Success(val result: TreasureMatchResult) : AiTreasureMatchUiState
    data class Error(val message: String) : AiTreasureMatchUiState
}

class YardHopAiViewModel : ViewModel() {

    private val listingDraftSchema = Schema.obj(
        properties = mapOf(
            "title" to Schema.string(description = "Catchy, concise yard sale headline under 80 characters"),
            "description" to Schema.string(description = "Inviting 2-4 sentence yard sale description listing specific visible items and shopper details"),
            "priceRangeLabel" to Schema.string(description = "Typical yard sale price range like $2 - $45"),
            "categories" to Schema.array(
                items = Schema.enumeration(
                    values = DEFAULT_SALE_CATEGORIES,
                    description = "Allowed yard sale category"
                ),
                description = "Up to 4 matching categories"
            )
        )
    )

    private val treasureMatchSchema = Schema.obj(
        properties = mapOf(
            "summary" to Schema.string(description = "Friendly 1-2 sentence overview of what matches near the shopper"),
            "matches" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "saleId" to Schema.string(description = "Exact ID of the matching YardSale"),
                        "reason" to Schema.string(description = "Short explanation of why this sale fits what the shopper wants")
                    )
                ),
                description = "Top matching sales ordered by relevance"
            )
        )
    )

    private val draftModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_NAME,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = listingDraftSchema
            }
        )
    }

    private val scoutModel by lazy {
        Firebase.ai.generativeModel(modelName = MODEL_NAME)
    }

    private val treasureFinderModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_NAME,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = treasureMatchSchema
            }
        )
    }

    private val _draftState = MutableStateFlow<AiDraftUiState>(AiDraftUiState.Idle)
    val draftState: StateFlow<AiDraftUiState> = _draftState.asStateFlow()

    private val _scoutState = MutableStateFlow<AiStreamUiState>(AiStreamUiState.Idle)
    val scoutState: StateFlow<AiStreamUiState> = _scoutState.asStateFlow()

    private val _treasureMatchState = MutableStateFlow<AiTreasureMatchUiState>(AiTreasureMatchUiState.Idle)
    val treasureMatchState: StateFlow<AiTreasureMatchUiState> = _treasureMatchState.asStateFlow()

    private var scoutJob: Job? = null
    private var lastScoutSaleId: String? = null

    fun resetDraftState() {
        _draftState.value = AiDraftUiState.Idle
    }

    fun resetTreasureMatchState() {
        _treasureMatchState.value = AiTreasureMatchUiState.Idle
    }

    fun ensureScoutContext(saleId: String) {
        if (lastScoutSaleId != saleId) {
            scoutJob?.cancel()
            lastScoutSaleId = saleId
            _scoutState.value = AiStreamUiState.Idle
        }
    }

    fun draftListingFromPhotosAndNotes(
        photoDataList: List<String>,
        existingTitle: String,
        existingNotes: String,
        neighborhood: String
    ) {
        if (photoDataList.isEmpty() && existingTitle.isBlank() && existingNotes.isBlank()) {
            _draftState.value = AiDraftUiState.Error("Add at least one photo or a few item notes first.")
            return
        }

        viewModelScope.launch {
            _draftState.value = AiDraftUiState.Loading
            val bitmaps = mutableListOf<Bitmap>()
            try {
                withContext(Dispatchers.IO) {
                    photoDataList.take(3).forEach { dataUri ->
                        PhotoHelper.decodeDataUriToBitmap(dataUri)?.let { bitmaps.add(it) }
                    }
                }

                val promptText = buildString {
                    appendLine("You are helping a neighbor post a yard or garage sale on YardHop.")
                    appendLine("Inspect any provided photos and seller notes to draft a friendly, accurate listing.")
                    if (existingTitle.isNotBlank()) appendLine("Seller draft headline: $existingTitle")
                    if (existingNotes.isNotBlank()) appendLine("Seller notes: $existingNotes")
                    if (neighborhood.isNotBlank()) appendLine("Neighborhood: $neighborhood")
                    appendLine("Allowed categories: ${DEFAULT_SALE_CATEGORIES.joinToString(", ")}")
                    appendLine("Only describe items visible in the photos or mentioned in the notes.")
                }

                val requestContent = content {
                    bitmaps.forEach { image(it) }
                    text(promptText)
                }

                val response = withTimeout(TIMEOUT_MILLIS) {
                    draftModel.generateContent(requestContent)
                }

                val jsonText = response.text
                if (jsonText.isNullOrBlank()) {
                    _draftState.value = AiDraftUiState.Error("Gemini returned an empty response.")
                    return@launch
                }

                val json = JSONObject(jsonText)
                val title = json.optString("title").trim().take(100)
                val description = json.optString("description").trim().take(1000)
                val priceRange = json.optString("priceRangeLabel").trim().take(50)
                val catsArray = json.optJSONArray("categories")
                val parsedCategories = buildList {
                    if (catsArray != null) {
                        for (i in 0 until catsArray.length()) {
                            val candidate = catsArray.optString(i)
                            val matched = DEFAULT_SALE_CATEGORIES.firstOrNull {
                                it.equals(candidate, ignoreCase = true)
                            }
                            if (matched != null && matched !in this) {
                                add(matched)
                            }
                        }
                    }
                }.take(4)

                if (title.isBlank() || description.isBlank()) {
                    _draftState.value = AiDraftUiState.Error("Could not extract listing details. Please try again.")
                } else {
                    _draftState.value = AiDraftUiState.Success(
                        ListingDraftResult(
                            title = title,
                            description = description,
                            priceRangeLabel = priceRange.ifBlank { "$2 - $50" },
                            categories = parsedCategories
                        )
                    )
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Listing auto-draft timed out", e)
                _draftState.value = AiDraftUiState.Error("Gemini timed out after 60 seconds. Please try again.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Listing auto-draft failed", e)
                _draftState.value = AiDraftUiState.Error(
                    e.localizedMessage ?: "Failed to draft listing with Gemini."
                )
            } finally {
                bitmaps.forEach { if (!it.isRecycled) it.recycle() }
            }
        }
    }

    fun askSaleScout(sale: YardSale, distanceMiles: Double, question: String) {
        val trimmedQuestion = question.trim()
        if (trimmedQuestion.isBlank()) return

        lastScoutSaleId = sale.id
        scoutJob?.cancel()
        scoutJob = viewModelScope.launch {
            _scoutState.value = AiStreamUiState.Loading
            val accumulated = StringBuilder()
            val bitmaps = mutableListOf<Bitmap>()
            try {
                withContext(Dispatchers.IO) {
                    sale.photoDataList.take(3).forEach { dataUri ->
                        PhotoHelper.decodeDataUriToBitmap(dataUri)?.let { bitmaps.add(it) }
                    }
                }

                val contextPrompt = buildString {
                    appendLine("You are YardHop's helpful neighborhood yard sale scout advising a local shopper.")
                    appendLine("Keep your answer practical, concise (3-5 bullet points or short paragraphs), and grounded in the listing details and photos.")
                    appendLine("Sale Title: ${sale.title}")
                    appendLine("Neighborhood / Address: ${sale.neighborhood.ifBlank { sale.addressLabel }} (${String.format(Locale.US, "%.1f", distanceMiles)} mi away)")
                    appendLine("Schedule: ${sale.dateLabel} · ${sale.hoursLabel}")
                    if (sale.priceRangeLabel.isNotBlank()) appendLine("Price Range: ${sale.priceRangeLabel}")
                    if (sale.categories.isNotEmpty()) appendLine("Categories: ${sale.categories.joinToString(", ")}")
                    appendLine("Description: ${sale.description}")
                    appendLine("Shopper question: $trimmedQuestion")
                }

                val requestContent = content {
                    bitmaps.forEach { image(it) }
                    text(contextPrompt)
                }

                withTimeout(TIMEOUT_MILLIS) {
                    scoutModel.generateContentStream(requestContent).collect { chunk ->
                        val chunkText = chunk.text.orEmpty()
                        if (chunkText.isNotEmpty()) {
                            accumulated.append(chunkText)
                            _scoutState.value = AiStreamUiState.Streaming(accumulated.toString())
                        }
                    }
                }

                if (accumulated.isNotBlank()) {
                    _scoutState.value = AiStreamUiState.Success(accumulated.toString().trim())
                } else {
                    _scoutState.value = AiStreamUiState.Error("Gemini returned an empty response.")
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Sale scout timed out", e)
                _scoutState.value = AiStreamUiState.Error("Request timed out after 60 seconds. Please try again.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Sale scout failed", e)
                _scoutState.value = AiStreamUiState.Error(
                    e.localizedMessage ?: "Could not reach Gemini AI Scout."
                )
            } finally {
                bitmaps.forEach { if (!it.isRecycled) it.recycle() }
            }
        }
    }

    fun findTreasureMatches(
        shopperWish: String,
        availableSales: List<SaleWithDistance>
    ) {
        val trimmedWish = shopperWish.trim()
        if (trimmedWish.isBlank()) {
            _treasureMatchState.value = AiTreasureMatchUiState.Error("Tell Gemini what items or deals you are hunting for.")
            return
        }
        if (availableSales.isEmpty()) {
            _treasureMatchState.value = AiTreasureMatchUiState.Error("There are no active yard sales to search right now.")
            return
        }

        viewModelScope.launch {
            _treasureMatchState.value = AiTreasureMatchUiState.Loading
            try {
                val salesById = availableSales.associateBy { it.sale.id }
                val catalogText = availableSales.take(20).joinToString("\n") { item ->
                    val s = item.sale
                    val cats = if (s.categories.isEmpty()) "General" else s.categories.joinToString(", ")
                    "- id=${s.id} | title=${s.title} | dist=${String.format(Locale.US, "%.1f", item.distanceMiles)}mi | price=${s.priceRangeLabel.ifBlank { "N/A" }} | categories=$cats | desc=${s.description.take(180)}"
                }

                val prompt = buildString {
                    appendLine("You are YardHop's Treasure Finder. Match the shopper's wish against these active yard sales:")
                    appendLine("Shopper wish: $trimmedWish")
                    appendLine("Active yard sales:")
                    appendLine(catalogText)
                    appendLine("Return up to 5 best matching saleId values from the list above with short, helpful reasons.")
                }

                val response = withTimeout(TIMEOUT_MILLIS) {
                    treasureFinderModel.generateContent(prompt)
                }

                val jsonText = response.text
                if (jsonText.isNullOrBlank()) {
                    _treasureMatchState.value = AiTreasureMatchUiState.Error("Gemini returned an empty response.")
                    return@launch
                }

                val json = JSONObject(jsonText)
                val summary = json.optString("summary").trim()
                val matchesArray = json.optJSONArray("matches")
                val resolvedMatches = buildList<TreasureMatchItem> {
                    if (matchesArray != null) {
                        for (i in 0 until matchesArray.length()) {
                            val obj = matchesArray.optJSONObject(i) ?: continue
                            val saleId = obj.optString("saleId")
                            val reason = obj.optString("reason").trim()
                            val matchedSale = salesById[saleId] ?: continue
                            if (none { it.saleId == saleId }) {
                                add(
                                    TreasureMatchItem(
                                        saleId = saleId,
                                        title = matchedSale.sale.title,
                                        reason = reason.ifBlank { "Matches your search" },
                                        distanceMiles = matchedSale.distanceMiles
                                    )
                                )
                            }
                        }
                    }
                }

                _treasureMatchState.value = AiTreasureMatchUiState.Success(
                    TreasureMatchResult(
                        summary = summary.ifBlank { "Here are the closest matching sales near you." },
                        matches = resolvedMatches
                    )
                )
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Treasure finder timed out", e)
                _treasureMatchState.value = AiTreasureMatchUiState.Error("Search timed out after 60 seconds. Please try again.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Treasure finder failed", e)
                _treasureMatchState.value = AiTreasureMatchUiState.Error(
                    e.localizedMessage ?: "Treasure Finder failed."
                )
            }
        }
    }

    private companion object {
        const val TAG = "YardHopAiVM"
        const val MODEL_NAME = "gemini-3.8-flash"
        const val TIMEOUT_MILLIS = 60_000L
    }
}
