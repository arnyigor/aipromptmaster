package com.arny.aipromptmaster.presentation.ui.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.arny.aipromptmaster.domain.models.FileAttachment
import com.arny.aipromptmaster.presentation.databinding.FragmentFileViewerBinding
import com.arny.aipromptmaster.domain.utils.FileUtils
import io.noties.markwon.Markwon
import kotlinx.coroutines.launch

class FileViewerFragment : Fragment() {
    private var _binding: FragmentFileViewerBinding? = null
    private val binding get() = _binding!!

    private val args: FileViewerFragmentArgs by navArgs()
    private val viewModel: FileViewerViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFileViewerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        loadAndDisplayFile(args.fileId)
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
        binding.toolbar.title = "Загрузка..."
    }

    private fun loadAndDisplayFile(fileId: String) {
        lifecycleScope.launch {
            viewModel.loadFile(fileId).collect { fileAttachment ->
                fileAttachment?.let {
                    setupToolbarWithFile(it)
                    displayFileContent(it)
                }
            }
        }
    }

    private fun setupToolbarWithFile(fileAttachment: FileAttachment) {
        binding.toolbar.title = fileAttachment.fileName
    }

    private fun displayFileContent(fileAttachment: FileAttachment) {
        with(binding) {
            // Показываем информацию о файле
            tvFileName.text = fileAttachment.fileName
            tvFileSize.text = FileUtils.formatFileSize(fileAttachment.fileSize)
            tvFileType.text = fileAttachment.fileExtension.uppercase()

            // Определяем тип контента и отображаем соответственно
            when (fileAttachment.fileExtension.lowercase()) {
                "md" -> {
                    // Для markdown файлов используем Markwon
                    tvContent.isVisible = true
                    webView.isVisible = false
                    Markwon.create(requireContext()).setMarkdown(tvContent, fileAttachment.originalContent)
                }
                "json" -> {
                    // Для JSON файлов показываем как форматированный текст
                    tvContent.isVisible = true
                    webView.isVisible = false
                    tvContent.text = formatJson(fileAttachment.originalContent)
                }
                "xml", "html", "css", "js", "ts", "py", "java", "kt", "php", "rb", "go", "rs", "swift", "sh" -> {
                    // Для кода показываем с подсветкой синтаксиса через WebView
                    tvContent.isVisible = false
                    webView.isVisible = true
                    showCodeInWebView(fileAttachment)
                }
                else -> {
                    // Для остальных текстовых файлов показываем как обычный текст
                    tvContent.isVisible = true
                    webView.isVisible = false
                    tvContent.text = fileAttachment.originalContent
                }
            }
        }
    }

    private fun formatJson(json: String): String {
        return try {
            // Здесь можно добавить форматирование JSON
            json
        } catch (e: Exception) {
            json
        }
    }

    private fun showCodeInWebView(fileAttachment: FileAttachment) {
        val html = generateCodeHtml(fileAttachment)
        binding.webView.settings.javaScriptEnabled = true
        binding.webView.loadData(html, "text/html", "UTF-8")
    }

    private fun generateCodeHtml(fileAttachment: FileAttachment): String {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <style>
                    body {
                        font-family: 'Courier New', monospace;
                        background-color: #1e1e1e;
                        color: #d4d4d4;
                        padding: 16px;
                        margin: 0;
                        white-space: pre-wrap;
                        font-size: 14px;
                        line-height: 1.5;
                    }
                </style>
            </head>
            <body>
                ${escapeHtml(fileAttachment.originalContent)}
            </body>
            </html>
        """.trimIndent()
    }

    private fun escapeHtml(text: String): String {
        return text.replace("&", "&")
                  .replace("<", "<")
                  .replace(">", ">")
                  .replace("\"", "\"")
                  .replace("'", "'")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}