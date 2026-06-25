package com.example.personalmemories.media

import com.example.personalmemories.data.MediaItemEntity
import kotlin.random.Random

object ShuffleEngine {
    fun newRound(items: List<MediaItemEntity>, previousKey: String? = null): List<String> {
        val keys = items.map { it.mediaKey }.toMutableList()
        for (i in keys.lastIndex downTo 1) {
            val j = Random.nextInt(i + 1)
            val tmp = keys[i]
            keys[i] = keys[j]
            keys[j] = tmp
        }
        if (keys.size >= 3 && previousKey != null && keys.firstOrNull() == previousKey) {
            val swapIndex = keys.indexOfFirst { it != previousKey }.takeIf { it > 0 } ?: 1
            val tmp = keys[0]
            keys[0] = keys[swapIndex]
            keys[swapIndex] = tmp
        }
        return keys
    }
}
