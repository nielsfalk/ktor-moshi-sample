package de.nielsfalk.playground.ktor_moshi

import com.squareup.moshi.Rfc3339DateJsonAdapter
import org.jetbrains.ktor.application.install
import org.jetbrains.ktor.features.CallLogging
import org.jetbrains.ktor.features.Compression
import org.jetbrains.ktor.features.DefaultHeaders
import org.jetbrains.ktor.host.embeddedServer
import org.jetbrains.ktor.http.HttpStatusCode
import org.jetbrains.ktor.netty.Netty
import org.jetbrains.ktor.response.respond
import org.jetbrains.ktor.routing.get
import org.jetbrains.ktor.routing.routing
import java.util.*

data class Model(val name: String, val items: List<Item>)
data class Item(val key: String, val value: String)

/*
         > curl -v --compress --header "Accept: application/json" http://localhost:8080/v1
         {"name":"root","items":[{"key":"A","value":"Apache"},{"key":"B","value":"Bing"}]}

         > curl -v --compress --header "Accept: application/json" http://localhost:8080/v1/item/A
         {"key":"A","value":"Apache"}
     */

fun main(args: Array<String>) {
    val server = embeddedServer(Netty, 8080) {
        install(DefaultHeaders)
        install(Compression)
        install(CallLogging)
        install(MoshiSupport) {
            add(Date::class.java, Rfc3339DateJsonAdapter().nullSafe())
        }
        val model = Model("root", listOf(Item("A", "Apache"), Item("B", "Bing")))
        routing {
            get("/v1") {
                call.respond(model)
            }
            get("/v1/item/{key}") {
                val item = model.items.firstOrNull { it.key == call.parameters["key"] }
                call.respond(item ?: HttpStatusCode.NotFound)
            }
        }
    }
    server.start(wait = true)
}
