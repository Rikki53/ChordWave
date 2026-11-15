// EqualizerViewModel — the equalizer screen's AutoEq half, in the shape
// of Mochy's viewmodels/AudioLabViewModel.kt AutoEq section (GPL-3.0)
// (index / query / results / measurement / busy / error / openAutoEq /
// loadIndex / select / tune / setCorrectionEnabled / clearCorrection),
// rewired onto ChordWave's real engine.
//
// THE ONE BIG DIFFERENCE: Mochy's correction sits UNDER the user's own
// curve (both run in its processor). ChordWave's engine has a single
// curve, so here the correction BECOMES the manual curve when applied:
// the fitted filters are sampled at EqLayout.MANUAL_BANDS_HZ and written
// with AppSettings.setEqualizerBands in MANUAL mode. The bands that were
// there before are remembered (persisted) and restored when the
// correction is switched off or removed, so nothing is lost.
//
// The correction itself (model + tuning + filters) is persisted as JSON
// in a private SharedPreferences file ("mochy_equalizer") — AppSettings
// is the engine's store and is never touched for mochy-layer state. On
// restore, an enabled flag is only trusted when the engine's bands still
// match the sampled correction: if the user changed the curve elsewhere
// (Classic screen, a preset, the tone pad) the toggle must not claim the
// correction is playing when it is not.
//
// The fitter's preamp is stored but never applied: ChordWave sets
// headroom automatically (EqCurve.preampDb takes back half of a curve's
// peak boost; PeakLimiter catches the rest).
//
// The AudioLabViewModel (5F) still owns the eq/presets display state;
// this VM owns only the AutoEq tab + the correction.
package com.music.bitchord.ui.mochy.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.bitchord.R
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.EqualizerMode
import com.music.bitchord.playback.EqLayout
import com.music.bitchord.ui.mochy.eq.audio.ResponseCurve
import com.music.bitchord.ui.mochy.eq.autoeq.AutoEqCorrection
import com.music.bitchord.ui.mochy.eq.autoeq.AutoEqEntry
import com.music.bitchord.ui.mochy.eq.autoeq.AutoEqFitter
import com.music.bitchord.ui.mochy.eq.autoeq.AutoEqMeasurement
import com.music.bitchord.ui.mochy.eq.autoeq.AutoEqModel
import com.music.bitchord.ui.mochy.eq.autoeq.AutoEqRepository
import com.music.bitchord.ui.mochy.eq.autoeq.AutoEqTuning
import com.music.bitchord.ui.mochy.eq.autoeq.toEntry
import com.music.bitchord.ui.mochy.eq.data.ParametricEQBand
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import kotlin.math.abs

sealed interface AutoEqIndex {
    data object Loading : AutoEqIndex
    data class Failed(val message: String) : AutoEqIndex
    data class Ready(val models: List<AutoEqModel>) : AutoEqIndex
}

@HiltViewModel
class EqualizerViewModel @Inject constructor(
    private val autoEq: AutoEqRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    // ---- the database ------------------------------------------------------

    private val _index = MutableStateFlow<AutoEqIndex>(AutoEqIndex.Loading)
    val index: StateFlow<AutoEqIndex> = _index.asStateFlow()

    val query = MutableStateFlow("")

    @OptIn(FlowPreview::class)
    val results: StateFlow<List<AutoEqModel>> = combine(_index, query.debounce(120)) { idx, q ->
        if (idx is AutoEqIndex.Ready) AutoEqRepository.search(idx.models, q) else emptyList()
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** the picked headphone's measurement, for the graph */
    private val _measurement = MutableStateFlow<AutoEqMeasurement?>(null)
    val measurement: StateFlow<AutoEqMeasurement?> = _measurement.asStateFlow()

    /** an entry being fetched or a fit being worked out */
    private val _busy = MutableStateFlow<String?>(null)
    val busy: StateFlow<String?> = _busy.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var indexJob: Job? = null
    private var fitJob: Job? = null

    // ---- the correction (persisted) ----------------------------------------

    private val prefs by lazy {
        context.getSharedPreferences(PrefsName, Context.MODE_PRIVATE)
    }

    private val _correction = MutableStateFlow<AutoEqCorrection?>(null)
    val correction: StateFlow<AutoEqCorrection?> = _correction.asStateFlow()

    private val _correctionEnabled = MutableStateFlow(false)
    val correctionEnabled: StateFlow<Boolean> = _correctionEnabled.asStateFlow()

    /** the manual bands the correction replaced, so switching it off restores them */
    private var prevBands: List<Float>? = null

    init {
        restoreCorrection()
    }

    private fun restoreCorrection() {
        val stored = prefs.getString(KeyCorrection, null) ?: return
        val restored = runCatching { json.decodeFromString<AutoEqCorrection>(stored) }
            .onFailure { Timber.tag(Tag).w(it, "Stored AutoEq correction unreadable; dropping it") }
            .getOrNull() ?: return
        prevBands = prefs.getString(KeyPrevBands, null)
            ?.split(",")?.mapNotNull { it.trim().toFloatOrNull() }
            ?.takeIf { it.size == EqLayout.MANUAL_COUNT }
        val wasEnabled = prefs.getBoolean(KeyEnabled, false)
        _correction.value = restored
        // only trust the enabled flag if the engine still plays this correction
        _correctionEnabled.value = wasEnabled && AppSettings.equalizerMode.value == EqualizerMode.MANUAL &&
            bandsMatch(AppSettings.equalizerBands.value, sampleCorrectionBands(restored.filters))
        if (wasEnabled && !_correctionEnabled.value) persist()
    }

    private fun persist() {
        val editor = prefs.edit()
        val current = _correction.value
        if (current == null) {
            editor.remove(KeyCorrection).remove(KeyEnabled).remove(KeyPrevBands)
        } else {
            editor.putString(KeyCorrection, json.encodeToString(current))
            editor.putBoolean(KeyEnabled, _correctionEnabled.value)
            val prev = prevBands
            if (prev != null) editor.putString(KeyPrevBands, prev.joinToString(","))
            else editor.remove(KeyPrevBands)
        }
        editor.apply()
    }

    // ---- opening / loading -------------------------------------------------

    /** the AutoEq tab opened: the list, and the current headphone's curve for the graph */
    fun openAutoEq() {
        if (_index.value !is AutoEqIndex.Ready && indexJob?.isActive != true) loadIndex(refresh = false)
        val current = _correction.value
        if (current != null && _measurement.value?.entry?.path != current.path) {
            viewModelScope.launch {
                runCatching { autoEq.measurement(current.toEntry()) }.onSuccess { _measurement.value = it }
            }
        }
    }

    fun loadIndex(refresh: Boolean) {
        indexJob?.cancel()
        indexJob = viewModelScope.launch {
            if (_index.value !is AutoEqIndex.Ready) _index.value = AutoEqIndex.Loading
            _index.value = runCatching { AutoEqIndex.Ready(autoEq.models(refresh)) }
                .getOrElse {
                    Timber.tag(Tag).w(it, "AutoEq index load failed")
                    AutoEqIndex.Failed(context.getString(R.string.mochy_eq_autoeq_db_unreachable))
                }
        }
    }

    // ---- picking / tuning / clearing ---------------------------------------

    fun select(entry: AutoEqEntry) {
        fitJob?.cancel()
        fitJob = viewModelScope.launch {
            _busy.value = entry.path
            _error.value = null
            runCatching {
                val m = autoEq.measurement(entry)
                val tuning = AutoEqTuning()
                val fit = withContext(Dispatchers.Default) { AutoEqFitter.correction(m, tuning) }
                _measurement.value = m
                // remember the curve being replaced — but only when the
                // bands on screen are the user's own, not an old correction
                if (_correction.value == null || !_correctionEnabled.value) {
                    prevBands = AppSettings.equalizerBands.value
                }
                val correction = AutoEqCorrection(
                    model = entry.name,
                    source = entry.source,
                    rig = entry.rig,
                    path = entry.path,
                    form = entry.form,
                    tuning = tuning,
                    filters = fit.bands,
                    preamp = fit.preamp,
                )
                _correction.value = correction
                _correctionEnabled.value = true
                persist()
                applyCorrection(correction)
                // Mochy parity: picking a headphone turns the equalizer on
                AppSettings.setEqualizerEnabled(true)
            }.onFailure {
                Timber.tag(Tag).w(it, "AutoEq measurement load failed for ${entry.name}")
                _error.value = context.getString(R.string.mochy_eq_autoeq_load_failed, entry.name)
            }
            _busy.value = null
        }
    }

    fun tune(transform: (AutoEqTuning) -> AutoEqTuning) {
        val current = _correction.value ?: return
        val m = _measurement.value ?: return
        val tuning = transform(current.tuning)
        // the sliders move first; the fit follows as soon as the finger rests
        _correction.value = current.copy(tuning = tuning)
        persist()
        fitJob?.cancel()
        fitJob = viewModelScope.launch {
            kotlinx.coroutines.delay(140)
            _busy.value = "fit"
            val fit = withContext(Dispatchers.Default) { AutoEqFitter.correction(m, tuning) }
            val latest = _correction.value
            if (latest != null && latest.path == current.path) {
                val refit = latest.copy(tuning = tuning, filters = fit.bands, preamp = fit.preamp)
                _correction.value = refit
                persist()
                if (_correctionEnabled.value) applyCorrection(refit)
            }
            _busy.value = null
        }
    }

    fun setCorrectionEnabled(on: Boolean) {
        val current = _correction.value ?: return
        if (on == _correctionEnabled.value) return
        if (on) {
            if (prevBands == null) prevBands = AppSettings.equalizerBands.value
            _correctionEnabled.value = true
            persist()
            applyCorrection(current)
            // Mochy parity: enabling the correction turns the equalizer on
            AppSettings.setEqualizerEnabled(true)
        } else {
            _correctionEnabled.value = false
            persist()
            restorePrevBands()
        }
    }

    fun clearCorrection() {
        if (_correctionEnabled.value) restorePrevBands()
        _measurement.value = null
        _correction.value = null
        _correctionEnabled.value = false
        prevBands = null
        persist()
    }

    // ---- driving the real engine -------------------------------------------

    /** Writes the sampled correction to the engine, in MANUAL mode. */
    private fun applyCorrection(correction: AutoEqCorrection) {
        AppSettings.setEqualizerMode(EqualizerMode.MANUAL)
        AppSettings.setEqualizerBands(sampleCorrectionBands(correction.filters))
    }

    /** Puts back the bands the correction replaced (stays in MANUAL mode). */
    private fun restorePrevBands() {
        val prev = prevBands ?: return
        AppSettings.setEqualizerMode(EqualizerMode.MANUAL)
        AppSettings.setEqualizerBands(prev)
    }

    companion object {
        private const val Tag = "EqualizerViewModel"
        private const val PrefsName = "mochy_equalizer"
        private const val KeyCorrection = "autoeq_correction"
        private const val KeyEnabled = "autoeq_enabled"
        private const val KeyPrevBands = "autoeq_prev_bands"

        private val json = Json { ignoreUnknownKeys = true }

        /** The fitted curve's gain at each manual band centre, rounded to
         *  0.1 dB and clamped to the sliders' ±12 dB. The fitter's own
         *  bands stay within ±12 dB, but neighbours overlap, so the summed
         *  response at a centre can overshoot. */
        fun sampleCorrectionBands(filters: List<ParametricEQBand>): List<Float> {
            val centres = DoubleArray(EqLayout.MANUAL_COUNT) {
                EqLayout.MANUAL_BANDS_HZ[it].toDouble()
            }
            val response = ResponseCurve(centres).response(filters)
            return response.map {
                (kotlin.math.round(it * 10) / 10).toFloat()
                    .coerceIn(-EqLayout.MANUAL_RANGE_DB, EqLayout.MANUAL_RANGE_DB)
            }
        }

        private fun bandsMatch(a: List<Float>, b: List<Float>): Boolean =
            a.size == b.size && a.indices.all { abs(a[it] - b[it]) < 0.05f }
    }
}
