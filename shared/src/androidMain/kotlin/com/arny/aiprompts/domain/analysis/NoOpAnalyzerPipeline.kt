package com.arny.aiprompts.domain.analysis

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * No-op implementation of [IAnalyzerPipeline] for Android platform.
 * Analyzer pipeline functionality is not supported on Android.
 */
class NoOpAnalyzerPipeline : IAnalyzerPipeline {

    override fun runPipelineFlow(): Flow<AnalyzerPipelineProgress> {
        return flow {
            emit(AnalyzerPipelineProgress.Error("Analyzer is not supported on Android"))
        }
    }

    override fun getStats(): AnalyzerStats {
        return AnalyzerStats(
            scrapedPages = 0,
            processedPrompts = 0,
            exportedPrompts = 0,
            exportedCategories = 0,
            outputDir = ""
        )
    }
}
