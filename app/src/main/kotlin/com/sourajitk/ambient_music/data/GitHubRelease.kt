// SPDX-License-Identifier: MIT
// Copyright (c) 2025-2026 Sourajit Karmakar

package com.sourajitk.ambient_music.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GitHubRelease(
    // Map JSON key "tag_name" & "html_url" to the Kotlin props and adhere to camelCase.
    @SerialName("tag_name") val tagName: String,
    @SerialName("html_url") val htmlUrl: String,
)
