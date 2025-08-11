package com.arny.aiprompts.presentation.ui

import androidx.compose.runtime.Composable
import com.arny.aiprompts.presentation.navigation.RootComponent

// "Обещаем", что на каждой платформе будет Composable-функция RootContent
@Composable
expect fun RootContent(component: RootComponent)