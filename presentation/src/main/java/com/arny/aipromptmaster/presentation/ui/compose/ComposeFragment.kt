package com.arny.aipromptmaster.presentation.ui.compose

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.arny.aipromptmaster.presentation.ui.compose.screens.*
import com.arny.aipromptmaster.presentation.ui.compose.theme.AIPromptMasterTheme
import dagger.android.support.AndroidSupportInjection

/**
 * Универсальный фрагмент для всех Compose экранов
 * Позволяет использовать Compose с существующей навигацией Navigation Component
 */
class ComposeFragment : Fragment() {

    private val args: ComposeFragmentArgs by navArgs()

    override fun onAttach(context: android.content.Context) {
        AndroidSupportInjection.inject(this)
        super.onAttach(context)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                AIPromptMasterTheme {
                    when (args.screenType) {
                        "prompts" -> {
                            PromptsScreen(navController = findNavController())
                        }
                        "chat" -> {
                            ChatScreen(
                                navController = findNavController(),
                                chatId = args.chatId
                            )
                        }
                        "settings" -> {
                            SettingsScreen(navController = findNavController())
                        }
                        "promptView" -> {
                            PromptViewScreen(
                                navController = findNavController(),
                                promptId = args.promptId ?: ""
                            )
                        }
                        "editPrompt" -> {
                            EditPromptScreen(
                                navController = findNavController(),
                                promptId = args.promptId
                            )
                        }
                        "chatHistory" -> {
                            ChatHistoryScreen(navController = findNavController())
                        }
                        "models" -> {
                            ModelsScreen(navController = findNavController())
                        }
                        else -> {
                            // Fallback на главный экран
                            PromptsScreen(navController = findNavController())
                        }
                    }
                }
            }
        }
    }
}