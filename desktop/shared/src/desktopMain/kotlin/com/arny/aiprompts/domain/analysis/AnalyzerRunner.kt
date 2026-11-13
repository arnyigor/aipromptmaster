package com.arny.aiprompts.domain.analysis

import com.arny.aiprompts.domain.analysis.HtmlStructureAnalyzer
import com.arny.aiprompts.domain.analysis.StructureReporter
import kotlinx.coroutines.runBlocking
import org.jsoup.Jsoup
import java.io.File

/**
 * Simple runner for HtmlStructureAnalyzer.
 * Run with: ./gradlew :desktopApp:run --args="--analyze-html <file>"
 */
object AnalyzerRunner {

    @JvmStatic
    fun main(args: Array<String>) = runBlocking {
        println("=".repeat(80))
        println("HTML STRUCTURE ANALYZER")
        println("=".repeat(80))

        val htmlFile = if (args.isNotEmpty() && args[0] != "--analyze-html") {
            File(args[0])
        } else {
            File(System.getProperty("user.home"), ".aiprompts/test_output/index_page_downloaded.html")
        }

        if (!htmlFile.exists()) {
            println("File not found: ${htmlFile.absolutePath}")
            println("\nUsage:")
            println("  java -jar app.jar --analyze-html <html-file>")
            println("\nOr download HTML first:")
            println("  ./gradlew :desktopApp:run --args=\"--download-index\"")
            return@runBlocking
        }

        println("\nAnalyzing: ${htmlFile.absolutePath}")
        val htmlContent = htmlFile.readText()
        println("File size: ${htmlContent.length} chars\n")

        // Analyze
        val analyzer = HtmlStructureAnalyzer()
        val result = analyzer.analyze(htmlContent)

        // Report
        StructureReporter.print(result)

        // Save markdown report
        val reportFile = File(htmlFile.parent, "analysis_report.md")
        reportFile.writeText(StructureReporter.toMarkdown(result))
        println("\nMarkdown report saved to: ${reportFile.absolutePath}")
    }
}
