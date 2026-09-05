package com.cortextransl.translateonscreen.data.model

sealed class PipelineResult {
    data class Success(
        val blocks: List<OverlayBlock>,
        val sourceLanguage: String,
        val targetLanguage: String
    ) : PipelineResult()

    data object NoTextFound : PipelineResult()

    data class Error(val messageRes: Int, val detail: String? = null) : PipelineResult()
}
