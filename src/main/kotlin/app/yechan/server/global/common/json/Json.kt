package app.yechan.server.global.common.json

import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule

object Json {
    val mapper: JsonMapper = JsonMapper.builder().addModule(KotlinModule.Builder().build()).build()
}
