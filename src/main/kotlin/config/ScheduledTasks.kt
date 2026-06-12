package com.example.config

import com.example.util.ProcessFeed
import kotlinx.coroutines.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/***
 * @ClassName ScheduledTasks
 * @author baoqiling
 * @version 1.0.0
 * @Description
 * @createTime 2025/6/17 14:16
 */

object ScheduledTasks {
    private val scope = CoroutineScope(Dispatchers.IO)

    fun startTasks() {
        // 启动定时任务
        startHourlyTask()
        startDailyTask()
    }

    private fun startHourlyTask() {
        scope.launch {
            while (isActive) {
                try {
                    // 执行任务
                    println("每小时任务执行: ${LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)}")
                    ProcessFeed.feed("obs.txt")
                    // 延迟1 min
                    delay(1000 * 60)
                } catch (e: Exception) {
                    println("定时任务出错: ${e.message}")
                }
            }
        }
    }

    private fun startDailyTask() {
        scope.launch {
            while (isActive) {
                // 计算到明天凌晨的延迟时间
                val now = LocalDateTime.now()
                val tomorrowMidnight = now.withHour(0).withMinute(0).withSecond(0).plusDays(1)
                val delayMillis = java.time.Duration.between(now, tomorrowMidnight).toMillis()

                // 等待到明天凌晨
                delay(delayMillis)

                // 执行任务
                println("每日任务执行: ${LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)}")

                // 之后每天执行一次
                delay(1000 * 60 * 60 * 24)
            }
        }
    }
}