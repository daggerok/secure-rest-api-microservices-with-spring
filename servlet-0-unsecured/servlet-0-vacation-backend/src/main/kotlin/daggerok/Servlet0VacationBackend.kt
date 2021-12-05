package daggerok

import daggerok.Status.APPROVED
import daggerok.Status.CREATED
import daggerok.Status.DECLINED
import java.time.LocalDate
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType.IDENTITY
import javax.persistence.Id
import javax.persistence.Table
import mu.KLogging
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.core.NestedExceptionUtils
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.repository.query.Param
import org.springframework.http.HttpStatus
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

@SpringBootApplication
class Servlet0VacationBackend

fun main(args: Array<String>) {
    runApplication<Servlet0VacationBackend>(*args)
}

enum class Status {
    CREATED, DECLINED, APPROVED
}

@Entity
@Table(name = "vacations")
data class Vacation(

    @Id
    @GeneratedValue(strategy = IDENTITY)
    val id: Long? = null,
    val username: String = "",
    val dateFrom: LocalDate = LocalDate.now(),
    val dateTo: LocalDate = LocalDate.now(),
    val hours: Long = 0,
    val status: Status = CREATED,
)

interface Vacations : JpaRepository<Vacation, Long> {
    fun findAllByUsernameContainsIgnoreCaseOrderByIdAsc(@Param("username") username: String): Iterable<Vacation>
}

@RestController
class VacationsResource(private val vacations: Vacations) {

    @PostMapping("/vacations")
    @ResponseStatus(HttpStatus.CREATED)
    fun requestVacation(@RequestBody vacation: Vacation) =
        vacation.apply { if (status != CREATED) throw RuntimeException("Request error: not allowed status") }
            .let { vacations.save(it) }

    @GetMapping("/vacations")
    fun searchVacations(@RequestParam("username") usernames: List<String>?): Iterable<Vacation> =
        usernames?.filter { it.isNotBlank() }
            ?.flatMap { vacations.findAllByUsernameContainsIgnoreCaseOrderByIdAsc(it) }
            ?: vacations.findAll()

    @GetMapping("/vacations/{id}")
    fun getVacation(@PathVariable id: Long): ResponseEntity<Vacation> =
        vacations.findById(id)
            .map { ResponseEntity.ok(it) }
            .orElseGet { ResponseEntity.notFound().build() }

    @PutMapping("/vacations/{id}")
    fun approveVacation(@PathVariable id: Long) =
        getVacation(id).body
            ?.apply { if (status == APPROVED) throw RuntimeException("Approval error: Vacation($id) already approved") }
            ?.copy(status = APPROVED)
            ?.let { vacations.save(it) }
            ?: throw RuntimeException("Approval error: Vacation($id) not found")

    @DeleteMapping("/vacations/{id}")
    fun declineVacation(@PathVariable id: Long) =
        getVacation(id).body
            ?.apply { if (status == DECLINED) throw RuntimeException("Decline error: Vacation($id) already declined") }
            ?.copy(status = DECLINED)
            ?.let { vacations.save(it) }
            ?: throw RuntimeException("Decline error: Vacation($id) not found")

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
