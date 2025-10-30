package com.example.thelastone.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.thelastone.data.model.AgeBand
import com.example.thelastone.data.model.Trip
import com.example.thelastone.data.model.TripForm
import com.example.thelastone.data.model.TripVisibility
import com.example.thelastone.data.repo.TripRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class TripFormViewModel @Inject constructor(
    private val repo: TripRepository
) : ViewModel() {
    val styleOptions = listOf("自然探索","藝術文青","休閒放鬆","文化歷史","美食巡禮","親子友善", "購物娛樂", "社群打卡")
    val transportOptions = listOf("步行","大眾運輸", "汽車", "機車")

    data class Form(
        val locations: String = "", // ✅ [整合] 1. 加入 locations 欄位
        val name: String = "",
        val totalBudget: Int? = null,
        val startDate: String = "",
        val endDate: String = "",
        val activityStart: String? = null,
        val activityEnd: String? = null,
        val styles: List<String> = emptyList(),
        val transportPreferences: List<String> = emptyList(),
        val avgAge: AgeBand = AgeBand.IGNORE,
        val useGmapsRating: Boolean = true,
        val visibility: TripVisibility = TripVisibility.PRIVATE,
        val extraNote: String? = null,
        val aiDisclaimerChecked: Boolean = false
    )

    private val _form = MutableStateFlow(Form())
    val form: StateFlow<Form> = _form

    // ====== 更新事件 ======
    fun updateName(v: String) = _form.update { it.copy(name = v.take(50)) }

    // ✅ [整合] 2. 加入 locations 的更新函式
    fun updateLocations(v: String) = _form.update { it.copy(locations = v) }

    fun updateBudgetText(v: String) {
        val digits = v.filter { it.isDigit() }
        _form.update { it.copy(totalBudget = digits.toIntOrNull()) }
    }
    fun updateDateRange(start: String, end: String) = _form.update { it.copy(startDate = start, endDate = end) }
    fun updateActivityStart(hhmm: String?) = _form.update { it.copy(activityStart = hhmm) }
    fun updateActivityEnd(hhmm: String?) = _form.update { it.copy(activityEnd = hhmm) }
    fun toggleStyle(s: String) = _form.update {
        it.copy(styles = it.styles.toMutableList().also { list ->
            if (list.contains(s)) list.remove(s) else list.add(s)
        })
    }
    fun toggleTransport(t: String) = _form.update {
        it.copy(transportPreferences = it.transportPreferences.toMutableList().also { list ->
            if (list.contains(t)) list.remove(t) else list.add(t)
        })
    }
    fun setAvgAge(a: AgeBand) = _form.update { it.copy(avgAge = a) }
    fun setUseGmapsRating(enabled: Boolean) = _form.update { it.copy(useGmapsRating = enabled) }
    fun updateExtraNote(v: String) = _form.update { it.copy(extraNote = v.take(200)) }
    fun setAiDisclaimer(v: Boolean) = _form.update { it.copy(aiDisclaimerChecked = v) }
    fun setVisibility(v: TripVisibility) = _form.update { it.copy(visibility = v) }


    // ✅ [還原] 3. 加回您原本的 ValidationResult 定義
    data class ValidationResult(
        val ok: Boolean,
        val nameError: String? = null,
        val locationError: String? = null, // [修改] 加入 locationError
        val dateError: String? = null,
        val timeError: String? = null
    )

    private fun validate(f: Form): ValidationResult {
        if (f.name.isBlank()) return ValidationResult(false, nameError = "請輸入旅遊名稱")
        if (f.name.length > 50) return ValidationResult(false, nameError = "名稱最多 50 字")

        // ✅ [整合] 4. 加入 locations 的驗證邏輯
        if (f.locations.isBlank()) return ValidationResult(false, locationError = "請輸入旅遊地點")

        val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val s = runCatching { LocalDate.parse(f.startDate, fmt) }.getOrNull()
        val e = runCatching { LocalDate.parse(f.endDate, fmt) }.getOrNull()
        if (s == null || e == null) return ValidationResult(false, dateError = "請選擇有效日期")
        if (e.isBefore(s)) return ValidationResult(false, dateError = "結束日期需晚於開始日期")

        if ((f.activityStart != null) xor (f.activityEnd != null))
            return ValidationResult(false, timeError = "活動時間需成對輸入")
        if (f.activityStart != null && f.activityEnd != null) {
            val tfmt = DateTimeFormatter.ofPattern("HH:mm")
            val ts = runCatching { LocalTime.parse(f.activityStart, tfmt) }.getOrNull()
            val te = runCatching { LocalTime.parse(f.activityEnd, tfmt) }.getOrNull()
            if (ts == null || te == null) return ValidationResult(false, timeError = "活動時間格式錯誤")
            if (!te.isAfter(ts)) return ValidationResult(false, timeError = "結束時間需晚於開始時間")
        }

        if (!f.aiDisclaimerChecked) {
            return ValidationResult(false)
        }

        return ValidationResult(true)
    }

    // ✅ [還原] 5. 加回您原本的 PreviewUiState 和 _preview StateFlow
    sealed interface PreviewUiState {
        data object Idle : PreviewUiState
        data object Loading : PreviewUiState
        data class Data(val trip: Trip) : PreviewUiState
        data class Error(val message: String) : PreviewUiState
    }
    private val _preview = MutableStateFlow<PreviewUiState>(PreviewUiState.Idle)
    val preview: StateFlow<PreviewUiState> = _preview

    // ✅ [還原] 6. 加回您原本的 SaveUiState 和 _save StateFlow
    sealed interface SaveUiState {
        data object Idle : SaveUiState
        data object Loading : SaveUiState
        data class Success(val tripId: String) : SaveUiState
        data class Error(val message: String) : SaveUiState
    }
    private val _save = MutableStateFlow<SaveUiState>(SaveUiState.Idle)
    val save: StateFlow<SaveUiState> = _save

    fun generatePreview() = viewModelScope.launch {
        val f = _form.value
        val v = validate(f)
        if (!v.ok) {
            val errorMsg = v.nameError ?: v.locationError ?: v.dateError ?: v.timeError ?: "表單未通過驗證"
            _preview.value = PreviewUiState.Error(errorMsg)
            return@launch
        }
        _preview.value = PreviewUiState.Loading

        // ✅ [整合] 7. 呼叫 repo.createTrip 時，傳入我們新增的 locations 欄位
        runCatching {
            repo.createTrip(
                TripForm(
                    locations = f.locations, // 👈 傳入新欄位
                    name = f.name,
                    totalBudget = f.totalBudget,
                    startDate = f.startDate,
                    endDate = f.endDate,
                    activityStart = f.activityStart,
                    activityEnd = f.activityEnd,
                    transportPreferences = f.transportPreferences,
                    useGmapsRating = f.useGmapsRating,
                    styles = f.styles,
                    avgAge = f.avgAge,
                    visibility = f.visibility, // 使用 state 中的值
                    extraNote = f.extraNote,
                    aiDisclaimerChecked = f.aiDisclaimerChecked
                )
            )
        }
            .onSuccess { _preview.value = PreviewUiState.Data(it) }
            .onFailure { _preview.value = PreviewUiState.Error(it.message ?: "Preview failed") }
    }

    fun confirmSave() = viewModelScope.launch {
        val p = _preview.value as? PreviewUiState.Data ?: return@launch
        _save.value = SaveUiState.Loading
        runCatching { repo.saveTrip(p.trip) }
            .onSuccess { _save.value = SaveUiState.Success(it.id) }
            .onFailure { _save.value = SaveUiState.Error(it.message ?: "Save failed") }
    }

    fun resetSaveState() { _save.value = SaveUiState.Idle }
}

