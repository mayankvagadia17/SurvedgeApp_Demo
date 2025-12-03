package com.nexova.survedgeapp.data.model

/**
 * Represents a survey code for categorizing points and lines
 */
data class SurveyCode(
    val id: String,
    val name: String,
    val type: CodeType
) {
    enum class CodeType {
        POINT,
        LINE
    }
}

