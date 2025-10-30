package com.example.thelastone.data.mapper

import com.example.thelastone.data.model.QuestionV2Dto
import com.example.thelastone.data.model.*



object QuestionMapper {
    fun fromV2(dto: QuestionV2Dto): SingleChoiceQuestion {
        val id = dto.questionId ?: "q_${dto.text.hashCode()}"

        // 💡 關鍵修正：從 List<TextEntryDto> 中取出真正的題目文字
        // 這裡假設第一個元素的 'question' 欄位就是題目。
        val questionText = dto.text.firstOrNull()?.question
            ?: "【錯誤：無法解析題目文字】"

        val opts = dto.options
            .sortedBy { it.choice }
            .map { ChoiceOption(it.choice, it.label ?: it.value ?: it.choice, it.value, it.key) }

        return SingleChoiceQuestion(
            id = id,
            text = questionText, // 使用取出的題目文字
            options = opts
        )
    }

    fun fromLegacy(dto: LegacyQuestionDto): SingleChoiceQuestion? {
        val qText = dto.question ?: return null
        val qId = dto.questionId ?: "legacy_${qText.hashCode()}"
        val opts = dto.choices.orEmpty()
            .sortedBy { it.id }
            .mapNotNull {
                val c = it.id ?: return@mapNotNull null
                ChoiceOption(c, it.text ?: it.value ?: c, it.value)
            }
        if (opts.isEmpty()) return null
        return SingleChoiceQuestion(id = qId, text = qText, options = opts)
    }
}