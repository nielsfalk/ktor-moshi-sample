package de.nielsfalk.playground.ktor_moshi

import com.squareup.moshi.KotlinJsonAdapterFactory
import com.squareup.moshi.Moshi
import org.jetbrains.ktor.application.ApplicationCallPipeline
import org.jetbrains.ktor.application.ApplicationFeature
import org.jetbrains.ktor.content.FinalContent
import org.jetbrains.ktor.content.IncomingContent
import org.jetbrains.ktor.content.readText
import org.jetbrains.ktor.http.ContentType
import org.jetbrains.ktor.http.HttpStatusCode
import org.jetbrains.ktor.http.withCharset
import org.jetbrains.ktor.pipeline.PipelineContext
import org.jetbrains.ktor.request.ApplicationReceivePipeline
import org.jetbrains.ktor.request.ApplicationReceiveRequest
import org.jetbrains.ktor.request.acceptItems
import org.jetbrains.ktor.request.contentType
import org.jetbrains.ktor.response.ApplicationSendPipeline
import org.jetbrains.ktor.response.contentLength
import org.jetbrains.ktor.response.contentType
import org.jetbrains.ktor.util.AttributeKey
import org.jetbrains.ktor.util.ValuesMap

class MoshiSupport(private val moshi: Moshi, pipeline: ApplicationCallPipeline) {
    init {
        pipeline.sendPipeline.intercept(ApplicationSendPipeline.Render) {
            if (it !is FinalContent && acceptsJson()) {
                proceedWith(renderJsonContent(it))
            }
        }
        pipeline.receivePipeline.intercept(ApplicationReceivePipeline.Transform) {
            if (call.request.contentType().match(ContentType.Application.Json)) {
                val message = it.value as? IncomingContent ?: return@intercept
                proceedWith(ApplicationReceiveRequest(it.type, message.toObject(it)))
            }
        }
    }

    private suspend fun IncomingContent.toObject(it: ApplicationReceiveRequest) = moshi
            .adapter<Any>(it.type.javaObjectType)
            .fromJson(readText())!!


    private fun PipelineContext<Any>.acceptsJson() =
            call.request.acceptItems().any {
                ContentType.Application.Json.match(it.value)
            }

    private fun renderJsonContent(model: Any) =
            JsonContent(model.toJson(), HttpStatusCode.OK)

    private fun Any.toJson() = moshi.adapter(javaClass).toJson(this)

    companion object Feature : ApplicationFeature<ApplicationCallPipeline, Moshi.Builder, MoshiSupport> {
        override val key = AttributeKey<MoshiSupport>("moshi")

        override fun install(pipeline: ApplicationCallPipeline, configure: Moshi.Builder.() -> Unit): MoshiSupport {
            val moshi = Moshi.Builder().apply {
                add(KotlinJsonAdapterFactory())
                configure()
            }.build()
            return MoshiSupport(moshi, pipeline)
        }
    }
}

private val contentType = ContentType.Application.Json.withCharset(Charsets.UTF_8)

private class JsonContent(val text: String, override val status: HttpStatusCode? = null) : FinalContent.ByteArrayContent() {

    private val bytes by lazy { text.toByteArray(Charsets.UTF_8) }

    override val headers by lazy {
        ValuesMap.build(true) {
            contentType(contentType)
            contentLength(bytes.size.toLong())
        }
    }

    override fun bytes(): ByteArray = bytes
    override fun toString() = "JsonContent \"${text.take(30)}\""
}
