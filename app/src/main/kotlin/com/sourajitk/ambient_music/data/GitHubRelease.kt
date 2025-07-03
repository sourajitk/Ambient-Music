// SPDX-License-Identifier: MIT
// Copyright (c) 2025 Sourajit Karmakar

package com.sourajitk.ambient_music.data

import kotlinx.serialization.Serializable

@Serializable data class GitHubRelease(val tag_name: String, val html_url: String)
