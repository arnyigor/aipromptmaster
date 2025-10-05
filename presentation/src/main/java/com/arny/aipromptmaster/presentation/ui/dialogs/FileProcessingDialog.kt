package com.arny.aipromptmaster.presentation.ui.dialogs

import android.content.Context
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.FragmentManager
import com.arny.aipromptmaster.domain.services.FileProcessingResult
import com.arny.aipromptmaster.presentation.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class FileProcessingDialog(
    context: Context,
    private val fileName: String,
    private val onCancel: () -> Unit
) : AlertDialog(context) {

    private var progressBar: ProgressBar? = null
    private var progressText: TextView? = null
    private var fileNameText: TextView? = null
    private var cancelButton: Button? = null
    private var processingJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.dialog_file_processing)

        window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setBackgroundDrawableResource(android.R.color.transparent)
        }

        progressBar = findViewById(R.id.progressBar)
        progressText = findViewById(R.id.tvProgress)
        fileNameText = findViewById(R.id.tvFileName)
        cancelButton = findViewById(R.id.btnCancel)

        fileNameText?.text = fileName
        progressText?.text = "Подготовка..."

        cancelButton?.setOnClickListener {
            processingJob?.cancel()
            onCancel()
            dismiss()
        }

        setCancelable(false)
        setCanceledOnTouchOutside(false)
    }

    fun updateProgress(progress: Int, bytesRead: Long, totalBytes: Long) {
        progressBar?.progress = progress
        progressText?.text = when {
            totalBytes > 0 -> {
                val mbRead = bytesRead / (1024.0 * 1024.0)
                val mbTotal = totalBytes / (1024.0 * 1024.0)
                "$progress% (${String.format("%.1f", mbRead)} MB / ${String.format("%.1f", mbTotal)} MB)"
            }
            else -> "$progress%"
        }
    }

    fun setProcessingJob(job: Job) {
        processingJob = job
    }

    override fun dismiss() {
        processingJob?.cancel()
        super.dismiss()
    }
}

/**
 * ✅ Extension функция с правильным доступом к контексту
 */
fun FragmentManager.showFileProcessingDialog(
    fileName: String,
    processingFlow: Flow<FileProcessingResult>,
    onComplete: (FileProcessingResult.Complete) -> Unit,
    onError: (String) -> Unit
): FileProcessingDialog {

    // ✅ ИСПРАВЛЕНО: Получаем context из Fragment вместо FragmentManager
    val fragment = fragments.firstOrNull()
        ?: throw IllegalStateException("No fragments attached to FragmentManager")

    val context = fragment.requireContext()

    val dialog = FileProcessingDialog(
        context = context,
        fileName = fileName,
        onCancel = { onError("Операция отменена пользователем") }
    )

    val job = CoroutineScope(Dispatchers.Main).launch {
        processingFlow.collect { result ->
            when (result) {
                is FileProcessingResult.Started -> {
                    dialog.show()
                }
                is FileProcessingResult.Progress -> {
                    dialog.updateProgress(
                        progress = result.progress,
                        bytesRead = result.bytesRead,
                        totalBytes = result.totalBytes
                    )
                }
                is FileProcessingResult.Complete -> {
                    dialog.dismiss()
                    onComplete(result)
                }
                is FileProcessingResult.Error -> {
                    dialog.dismiss()
                    onError(result.message)
                }
            }
        }
    }

    dialog.setProcessingJob(job)
    return dialog
}
