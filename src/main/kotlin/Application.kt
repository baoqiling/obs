package com.example

import com.example.config.ScheduledTasks
import com.example.config.configureUpdate
import io.ktor.server.application.*

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    // 启动定时任务
    ScheduledTasks.startTasks()
    configureRouting()
    configureUpdate()
}
