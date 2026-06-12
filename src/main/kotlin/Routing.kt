package com.example

import com.obs.services.ObsClient
import com.obs.services.model.GetObjectRequest
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.ktor.utils.io.core.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.time.Duration
import java.util.*
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import kotlin.collections.set
import java.util.Base64

// 定义访问令牌响应的数据结构
@Serializable
data class AccessTokenResponse(
    @SerialName("access_token")
    val access_token: String,
    @SerialName("token_type")
    val token_type: String,
    @SerialName("expires_in")
    val expires_in: Int,
    @SerialName("scope")
    val scope: String
)

// 定义会话数据类
data class UserSession(val accessToken: String, val code: String, val state: String? = null,val redirectState: String? = null) : Principal

// 定义全局的 urlProvider 函数
fun getCallbackUrl(): String = "http://build.bhxz.host:18998/package/auth"

fun Application.configureRouting() {

    // 安装 Sessions 插件
    install(Sessions) {
        cookie<UserSession>("USER_SESSION") {
            cookie.path = "/"
            cookie.maxAgeInSeconds = Duration.ofMinutes(30).seconds
            cookie.extensions["SameSite"] = "Lax"
//            serializer = KotlinxSerializer(Json)
        }
    }

    val httpClient = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true  // 忽略未知字段
            })
        }
    }

    val oauthSettings = OAuthServerSettings.OAuth2ServerSettings(
        name = "obs-auth",
        authorizeUrl = "https://auth.bhxz.host/api/v1/oauth2/authorize",
        accessTokenUrl = "https://auth.bhxz.host/api/v1/oauth2/token",
        clientId = "mDKHz21veM22y63vdPhNTdVElYy7nywN",
        clientSecret = "3xK1j6ZG2yqrz1gcPqdAt5Cwb29uyYO0OQDYPA6WoDWpFJE3YZ9x3OCB7nPXJwEo",
        defaultScopes = listOf("get_user_info")
    )

    install(Authentication) {
//        oauth("obs-auth") {
//            urlProvider = { getCallbackUrl() } // 使用定义的函数获取回调URL
//            providerLookup = { oauthSettings }
//            client = httpClient
//        }
        // 会话认证策略（用于后续接口验证）
        session<UserSession>("obs-auth-session") {
            validate { session ->
                if (session.accessToken.isNotEmpty()) {
                    session
                } else {
                    null
                }
            }
            challenge {
                val stateFromQuery = call.request.queryParameters["state"] ?: ""
                //val state = call.request.queryParameters["state"]
                println("Challenge: state from query = $stateFromQuery")
                call.sessions.set(UserSession(accessToken = "", code = "", state = stateFromQuery,redirectState = stateFromQuery))
                // 构建授权 URL 并附加 state
                val authorizeUrl = buildString {
                    append(oauthSettings.authorizeUrl)
                    append("?response_type=code")
                    append("&client_id=${oauthSettings.clientId}")
                    append("&redirect_uri=${getCallbackUrl()}")
                    append("&state=${stateFromQuery}")  // 手动附加 state 参数
                    append("&scope=${oauthSettings.defaultScopes.joinToString(" ")}")
                }
                println("authorizeUrl: $stateFromQuery")
                println("Redirecting to: $authorizeUrl")
                call.respondRedirect(authorizeUrl)
            }
        }

    }

    routing {

        get("/") {
            call.respondText("安装包下载服务！")
        }


        get("/package/auth") {
            val state = call.request.queryParameters["state"] ?: run {
                call.respond(HttpStatusCode.BadRequest, "Missing state")
                return@get
            }
            val code = call.request.queryParameters["code"] ?: run {
                call.respond(HttpStatusCode.BadRequest, "Missing code")
                return@get
            }

            if (code != null) {
                try {
                    val formData = Parameters.build {
                        append("code", code)
                        append("client_id", oauthSettings.clientId)
                        append("client_secret", oauthSettings.clientSecret)
                        append("redirect_uri", getCallbackUrl())
                        append("grant_type", "authorization_code")
                    }
                    val response = httpClient.post(oauthSettings.accessTokenUrl) {
                        setBody(FormDataContent(formData))
                        contentType(ContentType.Application.FormUrlEncoded)
                    }
                    if (response.status == HttpStatusCode.OK) {
                        val accessTokenResponse = Json.decodeFromString<AccessTokenResponse>(response.bodyAsText())
                        val accessToken = accessTokenResponse.access_token
                        call.sessions.set(call.sessions.get<UserSession>()?.copy(accessToken = accessToken) ?: UserSession(accessToken, code, state))
                        println(UserSession(accessTokenResponse.access_token, code, state))
                        // 可以在这里存储访问令牌，并重定向到受保护资源
                        if (state != null) {
                            call.respondRedirect("/package/download?id=$state")
                        } else {
                            call.respond(HttpStatusCode.BadRequest, "未找到下载id")
                        }
                    } else {
                        call.respond(HttpStatusCode.InternalServerError, "获取访问令牌失败: ${response.status}")
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, "获取访问令牌时出错: ${e.message}")
                }
            } else {
                call.respond(HttpStatusCode.BadRequest, "缺少 code 参数")
            }
        }

        authenticate("obs-auth-session") {

            get("/package/download") {
                val accessToken = call.sessions.get<UserSession>()?.accessToken
                // 从会话中获取访问令牌
                if (accessToken.isNullOrBlank()) {
                    call.respond(HttpStatusCode.Unauthorized, "未获取到有效的访问令牌")
                    return@get
                }
                // 验证 accessToken
                val userInfoResponse = httpClient.get("https://auth.bhxz.host/api/v1/oauth2/userinfo") {
                    headers {
                        append(HttpHeaders.Authorization, "Bearer $accessToken")
                    }
                }
                if (userInfoResponse.status != HttpStatusCode.OK) {
                    call.respond(HttpStatusCode.Unauthorized, "访问令牌已过期或无效")
                    return@get
                }

                println("获取到的访问令牌: ${accessToken}")

                // 从查询参数中获取 OBS 桶名和对象键
                // 优先从URL参数获取state，其次从会话获取
                val idFromQuery = call.request.queryParameters["state"]
                val idDecoder = idFromQuery ?: call.sessions.get<UserSession>()?.state
                if (idDecoder == null) {
                    call.respond(HttpStatusCode.BadRequest, "缺少 id 参数")
                    return@get
                }
                val id = String(Base64.getDecoder().decode(idDecoder), Charsets.UTF_8)
                println("idDecoder: $id")
                val decrypt = decrypt(id, AES_KEY)
                println("decrypt: $decrypt")
                val parts = decrypt.split("=", limit = 2)
                if (parts.size < 2) {
                    call.respond(HttpStatusCode.BadRequest, "Invalid 'id' format, expected 'bucketName=objectKey'")
                    return@get
                }
                val bucketName = parts[0]
                val objectKey = parts[1]

                // 检查参数是否为空
                if (bucketName.isNullOrBlank() || objectKey.isNullOrBlank()) {
                    call.respond(HttpStatusCode.BadRequest, "Missing bucketName or objectKey parameters")
                    return@get
                }

                try {
                    // 配置华为 OBS 客户端
                    val endPoint = "obs.cn-north-4.myhuaweicloud.com"
                    val accessKey = "PMGE9EWWDN7DRST1NLZC"
                    val secretKey = "ZR2ZPglfV5AdKL14lTTUfDzADBJ5yUEdsfgc3XFG"
                    val obsClient = ObsClient(accessKey, secretKey, endPoint)

                    // 创建获取对象的请求
                    val getObjectRequest = GetObjectRequest(bucketName, objectKey)
                    val obsObject = obsClient.getObject(getObjectRequest)
                    val inputStream: InputStream = obsObject.objectContent

                    // 提取文件名
                    val fileName = objectKey.substringAfterLast('/')

                    // 设置响应头
                    call.response.headers.append(HttpHeaders.ContentDisposition, "attachment; filename=\"$fileName\"")
                    call.response.status(HttpStatusCode.OK)

                    // 将 OBS 对象内容作为响应返回
                    call.respondOutputStream(contentType = ContentType.Application.OctetStream) {
                        inputStream.use { input ->
                            input.copyTo(this)
                        }
                    }
                    // 关闭 OBS 客户端
                    obsClient.close()
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, "Error downloading object: ${e.message}")
                }
            }
        }
    }
}

// AES 加密密钥（16 字节）
private const val AES_KEY = "bqiling@bhxz.net"

// AES 加密方法
fun encrypt(plainText: String, key: String): String {
    val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
    val secretKey = SecretKeySpec(key.toByteArray(), "AES")
    cipher.init(Cipher.ENCRYPT_MODE, secretKey)
    val encryptedBytes = cipher.doFinal(plainText.toByteArray())
    return Base64.getEncoder().encodeToString(encryptedBytes)
}

// AES 解密方法
fun decrypt(cipherText: String, key: String): String {
    val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
    val secretKey = SecretKeySpec(key.toByteArray(), "AES")
    cipher.init(Cipher.DECRYPT_MODE, secretKey)
    val decodedBytes = Base64.getDecoder().decode(cipherText)
    val decryptedBytes = cipher.doFinal(decodedBytes)
    return String(decryptedBytes)
}

fun main() {

//    lib13()
//    subway61021()

//    bhapi()
//    docker()
//    lib14()

    lib16()
//    lib6016()


}
fun demo() {
    var aarch64_71113 = encrypt("releases.bhxz=center7/7.1.1.12/center7113-arm64-20251010.tar.gz", AES_KEY)
    println("aarch64_71113:"+ base64Encode(aarch64_71113))

    var x86_71113 = encrypt("releases.bhxz=center7/7.1.1.12/center7113-x86-64-20251010.tar.gz", AES_KEY)
    println("x86_71113:"+ base64Encode(x86_71113))

    var BSServerSetup = encrypt("releases.bhxz=center7/7.1.1.12/BSServerSetup.exe", AES_KEY)
    println("BSServerSetup:"+ base64Encode(BSServerSetup))

    var sh = encrypt("releases.bhxz=center7/7.1.1.12/install.sh", AES_KEY)
    println("sh:"+ base64Encode(sh))

    var navicat = encrypt("releases.bhxz=center7/7.1.1.12/navicat17_premium.zip", AES_KEY)
    println("demo navicat:"+ base64Encode(navicat))
}
fun base64Encode(input: String): String {
    val bytes = input.toByteArray()
    return Base64.getEncoder().encodeToString(bytes)
}
fun lib6016(){
    //     releases.bhxz/center6/6.0.1.5/
    var BSServerSetup = encrypt("releases.bhxz=center6/6.0.1.6/BSServerSetup.exe", AES_KEY)
    println("lib6_BSServerSetup:"+ base64Encode(BSServerSetup))
}

fun lib12(){
    var aarch64_dm_71113 = encrypt("releases.bhxz=center7/7.1.1.12/center7112-arm64-dm-20251029.tar.gz", AES_KEY)
    println("aarch64_dm_71113:"+ base64Encode(aarch64_dm_71113))

    var aarch64_mysql_71113 = encrypt("releases.bhxz=center7/7.1.1.12/center7112-arm64-mysql-20251029.tar.gz", AES_KEY)
    println("aarch64_mysql_71113:"+ base64Encode(aarch64_mysql_71113))

    var x86_dm_71113 = encrypt("releases.bhxz=center7/7.1.1.12/center7112-x86-64-dm-20251029.tar.gz", AES_KEY)
    println("x86_dm_71113:"+ base64Encode(x86_dm_71113))

    var x86_mysql_71113 = encrypt("releases.bhxz=center7/7.1.1.12/center7112-x86-64-mysql-20251029.tar.gz", AES_KEY)
    println("x86_mysql_71113:"+ base64Encode(x86_mysql_71113))

    var BSServerSetup = encrypt("releases.bhxz=center7/7.1.1.12/BSServerSetup.exe", AES_KEY)
    println("BSServerSetup:"+ base64Encode(BSServerSetup))
}

fun lib13(){
    var aarch64_71113 = encrypt("releases.bhxz=center7/7.1.1.13/center7113-arm64-20260316.tar.gz", AES_KEY)
    println("aarch64_71113:"+ base64Encode(aarch64_71113))

    var x86_71113 = encrypt("releases.bhxz=center7/7.1.1.13/center7113-x86-64-20260316.tar.gz", AES_KEY)
    println("x86_71113:"+ base64Encode(x86_71113))

    var BSServerSetup = encrypt("releases.bhxz=center7/7.1.1.13/BSServerSetup.exe", AES_KEY)
    println("BSServerSetup:"+ base64Encode(BSServerSetup))
}

fun lib14(){
    var aarch64_71113 = encrypt("releases.bhxz=center7/7.1.1.14/center7114-arm64-20260430.tar.gz", AES_KEY)
    println("aarch64_71113:"+ base64Encode(aarch64_71113))

    var x86_71113 = encrypt("releases.bhxz=center7/7.1.1.14/center7114-x86-64-20260430.tar.gz", AES_KEY)
    println("x86_71113:"+ base64Encode(x86_71113))

    var BSServerSetup = encrypt("releases.bhxz=center7/7.1.1.14/BSServerSetup.exe", AES_KEY)
    println("BSServerSetup:"+ base64Encode(BSServerSetup))
}
fun lib16(){
    var aarch64_71113 = encrypt("releases.bhxz=center7/7.1.1.16/center7116-docker-arm64-20260605.tar.gz", AES_KEY)
    println("aarch64_71116:"+ base64Encode(aarch64_71113))

    var x86_71113 = encrypt("releases.bhxz=center7/7.1.1.16/center7116-docker-x86-64-20260605.tar.gz", AES_KEY)
    println("x86_71116:"+ base64Encode(x86_71113))

    var BSServerSetup = encrypt("releases.bhxz=center7/7.1.1.16/BSServerSetup.exe", AES_KEY)
    println("BSServerSetup:"+ base64Encode(BSServerSetup))

    var docker = encrypt("releases.bhxz=center7/7.1.1.16/docker-install.zip", AES_KEY)
    println("docker:"+ base64Encode(docker))
}

fun subway61021(){
    var aarch64_61021 = encrypt("releases.bhxz=subway/6.1.0.21/subway61021-arm64-20260117.tar.gz", AES_KEY)
    println("aarch64_61021:"+ base64Encode(aarch64_61021))

    var x86_61021 = encrypt("releases.bhxz=subway/6.1.0.21/subway61021-x86-64-20260117.tar.gz", AES_KEY)
    println("x86_61021:"+ base64Encode(x86_61021))

    var BSServerSetup = encrypt("releases.bhxz=subway/6.1.0.21/BSServerSetup.exe", AES_KEY)
    println("BSServerSetup:"+ base64Encode(BSServerSetup))

    var installScript = encrypt("releases.bhxz=subway/6.1.0.21/install.sh", AES_KEY)
    println("installScript:"+ base64Encode(installScript))
}

fun bhapi(){
    var bhapiWindow = encrypt("releases.bhxz=bhapi/1.7.0/bhapi_windows.zip", AES_KEY)
    println("bhapiWindow:"+ base64Encode(bhapiWindow))

    var bhapiLinux = encrypt("releases.bhxz=bhapi/1.7.0/bhapi_linux_x86.tar.gz", AES_KEY)
    println("bhapiLinux:" + base64Encode(bhapiLinux))

    var bhapiWindow1 = encrypt("releases.bhxz=bhapi/1.7.1/bhapi_windows.zip", AES_KEY)
    println("bhapiWindow1:"+ base64Encode(bhapiWindow1))
    var bhapiLinux1 = encrypt("releases.bhxz=bhapi/1.7.1/bhapi-1.7.1-linux.tar.gz", AES_KEY)
    println("bhapiLinux1:" + base64Encode(bhapiLinux1))
}

fun docker(){
    var docker_61021 = encrypt("releases.bhxz=subway/6.1.0.21/subway61021-docker-x86-20260526.tar.gz", AES_KEY)
    println("docker_61021:"+ base64Encode(docker_61021))
    var docker_7200 = encrypt("releases.bhxz=subway/6.1.0.21/BSServerSetup.exe", AES_KEY)
    println("docker_7200:"+ base64Encode(docker_7200))
}