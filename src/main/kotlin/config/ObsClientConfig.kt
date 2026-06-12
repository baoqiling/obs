package com.example.config

import com.obs.services.ObsClient
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.io.ByteArrayInputStream
import java.io.InputStream

/***
 * @ClassName ObsClientConfig
 * @author baoqiling
 * @version 1.0.0
 * @Description
 * @createTime 2025/4/29 15:42
 */

private const val OBS_ENDPOINT = "obs.cn-north-4.myhuaweicloud.com"
private const val OBS_ACCESS_KEY = "PMGE9EWWDN7DRST1NLZC"
private const val OBS_SECRET_KEY = "ZR2ZPglfV5AdKL14lTTUfDzADBJ5yUEdsfgc3XFG"
private const val BUCKET_NAME = "releases.bhxz"

fun Application.configureUpdate() {

    routing {

    post("/package/upload") {
        val multipart = call.receiveMultipart()
        var objectKey: String? = null
        var fileBytes: ByteArray? = null
        var originalFileName: String = ""

        // 第一步：一次性缓存所有数据（避免流提前关闭）
        multipart.forEachPart { part ->
            when (part) {
                is PartData.FormItem -> {
                    if (part.name == "objectKey") {
                        objectKey = part.value.trim()
                    }
                }
                is PartData.FileItem -> {
                    // 完整读取文件字节缓存，解决0B问题
                    fileBytes = part.streamProvider().readBytes()
                    originalFileName = part.originalFileName ?: "unknown.file"
                }
                else -> {}
            }
            part.dispose()
        }

        // 参数校验
        if (objectKey.isNullOrBlank() || fileBytes == null || fileBytes!!.isEmpty()) {
            call.respond(
                HttpStatusCode.BadRequest,
                mapOf("code" to 400, "msg" to "参数缺失或文件为空")
            )
            return@post
        }

        try {
            val obsClient = ObsClient(OBS_ACCESS_KEY, OBS_SECRET_KEY, OBS_ENDPOINT)
            val inputStream: InputStream = ByteArrayInputStream(fileBytes)

            // 完整字节流上传，文件大小完全一致
            obsClient.putObject(BUCKET_NAME, objectKey, inputStream)

            inputStream.close()
            obsClient.close()

            call.respond(
                HttpStatusCode.OK,
                mapOf(
                    "code" to 200,
                    "msg" to "上传成功",
                    "bucket" to BUCKET_NAME,
                    "objectKey" to objectKey,
                    "fileName" to originalFileName,
                    "fileSize" to "${fileBytes!!.size} byte"
                )
            )

        } catch (e: Exception) {
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf("code" to 500, "msg" to "上传失败：${e.message}")
            )
        }
    }

    }

}