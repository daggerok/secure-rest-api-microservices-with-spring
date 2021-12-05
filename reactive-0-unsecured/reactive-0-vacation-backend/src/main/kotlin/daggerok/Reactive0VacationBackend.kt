package daggerok

import daggerok.Status.APPROVED
import daggerok.Status.CREATED
import daggerok.Status.DECLINED
import java.time.LocalDate
import mu.KLogging
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.core.NestedExceptionUtils
import org.springframework.data.annotation.Id
import org.springframework.data.r2dbc.repository.R2dbcRepository
import org.springframework.data.relational.core.mapping.Table
import org.springframework.data.repository.query.Param
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatus.ACCEPTED
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@SpringBootApplication
class Reactive0VacationBackend

fun main(args: Array<String>) {
    runApplication<Reactive0VacationBackend>(*args)
}

enum class Status {
    CREATED, DECLINED, APPROVED
}

@Table("vacations")
data class Vacation(

    @Id
    val id: Long? = null,
    val username: String = "",
    val dateFrom: LocalDate = LocalDate.now(),
    val dateTo: LocalDate = LocalDate.now(),
    val hours: Long = 0,
    val status: Status = CREATED,
)

interface Vacations : R2dbcRepository<Vacation, Long> {
    fun findAllByUsernameContainsIgnoreCaseOrderByIdAsc(@Param("username") username: String): Flux<Vacation>
}

@RestController
class VacationsResource(private val vacations: Vacations) {

    @PostMapping("/vacations")
    @ResponseStatus(HttpStatus.CREATED)
    fun requestVacation(@RequestBody vacation: Vacation) =
        Mono.justOrEmpty(vacation)
            .handle<Vacation> { value, sick ->
                if (value.status != CREATED) sick.error(RuntimeException("Request error: not allowed status"))
                else sick.next(value)
            }
            .flatMap { vacations.save(it) }

    @GetMapping("/vacations")
    fun searchVacations(@RequestParam("username") usernames: List<String>?): Flux<Vacation> =
        Flux.fromIterable(usernames?.filter { it.isNotBlank() } ?: listOf())
            .switchIfEmpty(Mono.just(""))
            .flatMap { vacations.findAllByUsernameContainsIgnoreCaseOrderByIdAsc(it) }

    @GetMapping("/vacations/{id}")
    fun getVacation(@PathVariable id: Long) =
        vacations.findById(id)
            .map { ResponseEntity.ok(it) }
            .switchIfEmpty(Mono.just(ResponseEntity.notFound().build()))

    @ResponseStatus(ACCEPTED)
    @PutMapping("/vacations/{id}")
    fun approveVacation(@PathVariable id: Long) =
        vacations.findById(id)
            .handle<Vacation> { v, sink ->
                if (v.status == APPROVED) sink.error(RuntimeException("Approval error: Vacation($id) already approved"))
                else sink.next(v)
            }
            .map { it.copy(status = APPROVED) }
            .flatMap { vacations.save(it) }
            .switchIfEmpty(Mono.error(RuntimeException("Approval error: Vacation($id) not found")))

    @ResponseStatus(ACCEPTED)
    @DeleteMapping("/vacations/{id}")
    fun declineVacation(@PathVariable id: Long) =
        vacations.findById(id)
            .handle<Vacation> { v, sink ->
                if (v.status == DECLINED) sink.error(RuntimeException("Decline error: Vacation($id) already declined"))
                else sink.next(v)
            }
            .map { it.copy(status = DECLINED) }
            .flatMap { vacations.save(it) }
            .switchIfEmpty(Mono.error(RuntimeException("Decline error: Vacation($id) not found")))

    @ExceptionHandler
    fun handleException(e: Throwable) =
        (NestedExceptionUtils.getMostSpecificCause(e).message ?: "Unknown error").let {
            logger.warn/* (e) */ { it }
            ResponseEntity.badRequest().body(
                mapOf("error" to it)
            )
        }

    private companion object : KLogging()
}
