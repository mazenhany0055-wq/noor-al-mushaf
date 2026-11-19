package com.example.data.repository

import com.example.data.catalog.AdhkarCatalog
import com.example.data.local.TasbihDao
import com.example.data.local.TasbihItemEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class TasbihRepository(private val tasbihDao: TasbihDao) {

    val allTasbihItems: Flow<List<TasbihItemEntity>> = tasbihDao.observeAllTasbih()
    val totalLifetimeTasbih: Flow<Long> = tasbihDao.observeTotalLifetimeTasbih()

    suspend fun ensureSeeded() = withContext(Dispatchers.IO) {
        if (tasbihDao.countItems() == 0) {
            val defaults = AdhkarCatalog.defaultTasbihPresets.mapIndexed { index, (phrase, target) ->
                TasbihItemEntity(
                    phrase = phrase,
                    targetCount = target,
                    currentCount = 0,
                    roundsCompleted = 0,
                    totalLifetimeCount = 0L,
                    isCustom = false,
                    orderIndex = index
                )
            }
            tasbihDao.insertAll(defaults)
        }
    }

    suspend fun increment(item: TasbihItemEntity): Boolean = withContext(Dispatchers.IO) {
        val nextCount = item.currentCount + 1
        val completedRound = item.targetCount > 0 && nextCount >= item.targetCount
        val updated = if (completedRound) {
            item.copy(
                currentCount = 0,
                roundsCompleted = item.roundsCompleted + 1,
                totalLifetimeCount = item.totalLifetimeCount + 1
            )
        } else {
            item.copy(
                currentCount = nextCount,
                totalLifetimeCount = item.totalLifetimeCount + 1
            )
        }
        tasbihDao.update(updated)
        completedRound
    }

    suspend fun resetCurrent(item: TasbihItemEntity) = withContext(Dispatchers.IO) {
        tasbihDao.update(item.copy(currentCount = 0))
    }

    suspend fun updateTarget(item: TasbihItemEntity, newTarget: Int) = withContext(Dispatchers.IO) {
        val safeCount = if (newTarget > 0 && item.currentCount >= newTarget) 0 else item.currentCount
        tasbihDao.update(item.copy(targetCount = newTarget, currentCount = safeCount))
    }

    suspend fun addCustom(phrase: String, target: Int): Long = withContext(Dispatchers.IO) {
        tasbihDao.insert(
            TasbihItemEntity(
                phrase = phrase.trim(),
                targetCount = target,
                isCustom = true,
                orderIndex = 100
            )
        )
    }

    suspend fun deleteCustom(id: Int) = withContext(Dispatchers.IO) {
        tasbihDao.deleteCustomById(id)
    }
}
