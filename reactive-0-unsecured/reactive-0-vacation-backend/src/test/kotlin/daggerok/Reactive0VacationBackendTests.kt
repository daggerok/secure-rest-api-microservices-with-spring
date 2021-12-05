package daggerok

import daggerok.Status.APPROVED
import daggerok.Status.CREATED
import java.time.LocalDate
import mu.KLogging
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.junit.jupiter.api.fail
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.DEFINED_PORT
import reactor.core.publisher.Flux
import reactor.test.StepVerifier

@TestInstance(PER_CLASS)
@SpringBootTest(
    webEnvironment = DEFINED_PORT,
    properties = [
        "server.port=7003",
        "vacation-server.port=\${server.port}",
    ]
)
@Suppress("SpringJavaInjectionPointsAutowiringInspection")
class Reactive0VacationBackendTests @Autowired constructor(val restClient: VacationClient, val vacations: Vacations) {

    @BeforeEach
    fun setUp() {
        StepVerifier.create(vacations.deleteAll())
            .verifyComplete()
    }

    @Test
    fun `should request vacation`() {
        // given
        val vacation = vacationWith(username = "daggerok")
        // when
        val mono = restClient.requestVacation(vacation)
        // then
        StepVerifier.create(mono)
            .consumeNextWith {
                assertThat(it).isNotNull
                assertThat(it.username).isEqualTo("daggerok")
            }
            .verifyComplete()
    }

    @Test
    fun `should not request vacation with status != CREATED`() {
        // given
        val vacation = vacationWith(status = APPROVED)
        // when
        val mono = restClient.requestVacation(vacation)
        // then
        StepVerifier.create(mono)
            .consumeErrorWith {
                val message = it.message
                logger.info { message }
                assertThat(message).isEqualTo("Request error: not allowed status")
            }
            .verify()
    }

    @Test
    fun `should search vacations`() {
        // given
        Flux.just("ololo", "trololo", "nonono")
            .map { vacationWith(username = it) }
            .flatMap { vacations.save(it) }
            .subscribe { logger.info { "created: $it" } }
        // when
        val flux = restClient.searchVacations("ololo")
        // then
        StepVerifier.create(flux)
            .consumeNextWith {
                logger.info { "received 1st: $it" }
                assertThat(it.username).isEqualTo("ololo")
            }
            .consumeNextWith {
                logger.info { "received 2nd: $it" }
                assertThat(it.username).isEqualTo("trololo")
            }
            .verifyComplete()
    }

    @Test
    fun `should get vacation`() {
        // given
        Flux.just("ololo", "trololo", "nonono")
            .map { vacationWith(username = it) }
            .flatMap { vacations.save(it) }
            .last()
            .subscribe {
                logger.info { "created: $it" }
                val id = it.id ?: fail("id is null")
                // when
                val mono = restClient.getVacation(id)
                // then
                StepVerifier.create(mono)
                    .consumeNextWith {
                        logger.info { it }
                        assertThat(it.username).isEqualTo("nonono")
                    }
                    .verifyComplete()
            }
    }

    @Test
    fun `should approve vacation`() {
        val vacation = vacationWith(username = "daggerok", status = CREATED)
        // given
        vacations.save(vacation)
            .subscribe {
                logger.info { "saved: $it" }
                val id = it.id ?: fail("id may not be null")
                // when
                val mono = restClient.approveVacation(id)
                // then
                StepVerifier.create(mono)
                    .consumeNextWith {
                        logger.info { "received: $it" }
                        assertThat(it.status).isEqualTo(APPROVED)
                    }
            }
    }

    @Test
    fun `should decline vacation`() {
        // given
        val vacation = vacationWith(username = "daggerok", status = CREATED)
        vacations.save(vacation)
            .subscribe {
                logger.info { "saved: $it" }
                val id = it.id ?: fail("id may not be null")
                // when
                val mono = restClient.declineVacation(id)
                // then
                StepVerifier.create(mono)
                    .consumeNextWith {
                        logger.info { "received: $it" }
                        assertThat(it.status).isEqualTo(Status.DECLINED)
                    }
            }
    }

    companion object : KLogging() {
        fun vacationWith(
            id: Long? = null,
            username: String = "daggerok",
            dateFrom: LocalDate = LocalDate.now(),
            dateTo: LocalDate = LocalDate.now(),
            hours: Long = 8,
            status: Status = CREATED
        ) = Vacation(id, username, dateFrom, dateTo, hours, status)
    }
}
