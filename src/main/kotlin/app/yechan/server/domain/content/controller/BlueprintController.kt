package app.yechan.server.domain.content.controller

import app.yechan.server.domain.content.dto.BlueprintResponse
import app.yechan.server.domain.content.dto.CreateBlueprintRequest
import app.yechan.server.domain.content.dto.UpdateBlueprintRequest
import app.yechan.server.domain.content.service.Blueprints
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.net.URI

@RestController
@RequestMapping("/api/v1/studio/blueprints")
class BlueprintController(
    private val blueprints: Blueprints,
) {
    @GetMapping
    fun list(): List<BlueprintResponse> = blueprints.list()

    @GetMapping("/{key}")
    fun get(
        @PathVariable key: String,
    ): BlueprintResponse = blueprints.get(key)

    @PostMapping
    fun create(
        @Valid @RequestBody request: CreateBlueprintRequest,
    ): ResponseEntity<BlueprintResponse> {
        val created = blueprints.create(request)
        return ResponseEntity.created(URI.create("/api/v1/studio/blueprints/${created.key}")).body(created)
    }

    @PutMapping("/{key}")
    fun update(
        @PathVariable key: String,
        @Valid @RequestBody request: UpdateBlueprintRequest,
    ): BlueprintResponse = blueprints.update(key, request)

    @DeleteMapping("/{key}")
    fun delete(
        @PathVariable key: String,
    ): ResponseEntity<Unit> {
        blueprints.delete(key)
        return ResponseEntity.noContent().build()
    }
}
