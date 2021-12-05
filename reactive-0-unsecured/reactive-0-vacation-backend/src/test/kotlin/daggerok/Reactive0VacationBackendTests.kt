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
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.boot.web.server.LocalServerPort
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.body
import org.springframework.web.reactive.function.client.bodyToFlux
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

@TestInstance(PER_CLASS)
@SpringBootTest(webEnvironment = RANDOM_PORT)
@Suppress("SpringJavaInjectionPointsAutowiringInspection")
class Reactive0VacationBackendTests @Autowired constructor(
    @LocalServerPort port: Int,
    builder: WebClient.Builder,
    val vacations: Vacations,
) {

    val restClient = builder.build()
    val baseUrl = "http://127.0.0.1:$port"

    @BeforeEach
    fun setUp() {
        StepVerifier.create(vacations.deleteAll())
            .verifyComplete()
    }

    @Test
    fun `should request vacation`() {
        // when
        val mono = restClient.post()
            .uri("$baseUrl/vacations")
            .body(Mono.just(vacationWith(username = "daggerok")))
            .retrieve()
            .bodyToMono<Vacation>()
            .log("request vacation response")
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
        // when
        val mono = restClient.post()
            .uri("$baseUrl/vacations")
            .bodyValue(vacationWith(status = APPROVED))
            .retrieve()
            // .bodyToMono(Vacation::class.java)
            // .onErrorResume(WebClientResponseException::class.java) {
            //     if (it.rawStatusCode < 400) Mono.empty()
            //     else Mono.error(RuntimeException(it))
            // }
            .onStatus(HttpStatus::isError) {
                it.bodyToMono<Map<String, Any>>()
                    .handle<Throwable> { value, sink ->
                        val message = if (value.containsKey("error")) value["error"].toString() else "Unknown error"
                        sink.error(RuntimeException(message))
                    }
            }
            .bodyToMono<Vacation>()
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
        val flux = restClient.get()
            .uri("$baseUrl/vacations?username={username}", "ololo")
            .retrieve()
            .bodyToFlux<Vacation>()
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
                // when
                val mono = restClient.get()
                    .uri("$baseUrl/vacations/{id}", it.id)
                    .retrieve()
                    .bodyToMono<Vacation>()
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
        // given
        vacations.save(vacationWith(username = "daggerok", status = CREATED))
            .subscribe {
                logger.info { "saved: $it" }
                // when
                val mono = restClient.put().uri("$baseUrl/vacations/{id}", it.id)
                    .retrieve()
                    .bodyToMono<Vacation>()
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
        vacations.save(vacationWith(username = "daggerok", status = CREATED))
            .subscribe {
                logger.info { "saved: $it" }
                // when
                val mono = restClient.delete().uri("$baseUrl/vacations/{id}", it.id)
                    .retrieve()
                    .bodyToMono<Vacation>()
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
