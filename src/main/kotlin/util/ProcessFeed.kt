package com.example.util

import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileOutputStream
import java.lang.management.ManagementFactory
import java.nio.charset.StandardCharsets

/***
 * @ClassName ProcessFeed
 * @author baoqiling
 * @version 1.0.0
 * @Description
 * @createTime 2025/6/17 14:22
 */
object ProcessFeed {
    private val log = LoggerFactory.getLogger(ProcessFeed::class.java)

    /**
     * 将当前进程PID写入文件
     * @param fileName 文件名
     * @return 操作是否成功
     */
    fun feed(fileName: String): Boolean {
        if (fileName.isNullOrEmpty()) {
            log.warn("文件名为空，无法写入")
            return false
        }

        return try {
            val file = File(fileName)
            if (!file.exists()) {
                file.createNewFile()
            }

            // 获取当前进程PID
            val name = ManagementFactory.getRuntimeMXBean().name
            val pid = name.split("@")[0]

            // 写入PID到文件
            FileOutputStream(file).use { outputStream ->
                outputStream.write(pid.toByteArray(StandardCharsets.UTF_8))
            }

            // 更新文件最后修改时间
            file.setLastModified(System.currentTimeMillis())
            true
        } catch (e: Exception) {
            log.error("写入PID到文件失败", e)
            false
        }
    }
}