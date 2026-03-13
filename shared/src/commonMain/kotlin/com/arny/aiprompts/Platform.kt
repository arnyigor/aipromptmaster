package com.arny.aiprompts

import com.arny.aiprompts.data.model.Platform
import kotlinx.serialization.Serializable
import java.io.File

/**
 * Returns the current platform.
 */
expect fun getPlatform(): Platform

/**
 * Returns the cache directory for the current platform.
 */
expect fun getCacheDir(): File
