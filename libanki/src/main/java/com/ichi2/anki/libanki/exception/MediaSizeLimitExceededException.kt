/*
 *  Copyright (c) 2025
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 */
package com.ichi2.anki.libanki.exception

/**
 * Thrown when an attempted media file exceeds the allowed size for syncing with AnkiWeb.
 * Carries details about the offending file and the maximum allowed size.
 */
class MediaSizeLimitExceededException(
    val fileName: String,
    val fileSize: Long,
    val maxAllowedBytes: Long,
) : Exception("MEDIA_SIZE_LIMIT_EXCEEDED: $fileName|$fileSize|$maxAllowedBytes")
