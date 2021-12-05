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
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.client.exchange
import org.springframework.boot.test.web.client.getForEntity
import org.springframework.boot.test.web.client.postForEntity
import org.springframework.boot.web.server.LocalServerPort
import org.springframework.http.HttpMethod.DELETE
import org.springframework.http.HttpMethod.PUT
import org.springframework.http.HttpStatus
import org.springframework.transaction.support.TransactionTemplate

@TestInstance(PER_CLASS)
@SpringBootTest(webEnvironment = RANDOM_PORT)
@Suppress("SpringJavaInjectionPointsAutowiringInspection")
class Servlet0VacationBackendTests @Autowired constructor(
    val transactionTemplate: TransactionTemplate,
    val restClient: TestRestTemplate,
    @LocalServerPort port: Int,
    val vacations: Vacations,
) {

    val baseUrl = "http://127.0.0.1:$port"

    @BeforeEach
    fun setUp() = vacations.deleteAllInBatch()

    @Test
    fun `should request vacation`() {
        // given
        val request = vacationWith(username = "daggerok")
        // when
        val responseEntity = restClient.postForEntity<Vacation>("$baseUrl/vacations", request)
        logger.info { responseEntity }
        // then
        assertThat(responseEntity.statusCode).isEqualTo(HttpStatus.CREATED)
        // and
        val vacation = responseEntity.body
        assertThat(vacation).isNotNull
        assertThat(vacation?.username).isEqualTo("daggerok")
    }

    @Test
    fun `should not request vacation with status != CREATED`() {
        // given
        val request = vacationWith(username = "daggerok", status = APPROVED)
        // when
        val responseEntity = restClient.postForEntity<Map<String, Any>>("$baseUrl/vacations", request)
        logger.info { responseEntity }
        // then
        assertThat(responseEntity.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        // and
        val error = responseEntity.body
        assertThat(error).isNotNull
        assertThat(error?.containsKey("error")).isTrue
        assertThat(error?.get("error").toString()).isEqualTo("Request error: not allowed status")
    }

    @Test
    fun `should search vacations`() {
        // given
        listOf("ololo", "trololo", "nonono")
            .map { vacationWith(username = it) }
            .forEach { v ->
                transactionTemplate.execute {
                    vacations.save(v)
                }
            }
        // when
        val responseEntity = restClient.getForEntity<Iterable<Vacation>>("$baseUrl/vacations?username=ololo")
        logger.info { responseEntity }
        // then
        assertThat(responseEntity.statusCode).isEqualTo(HttpStatus.OK)
        // and
        val body = responseEntity.body
        assertThat(body).isNotNull
        assertThat(body).hasSize(2)
    }

    @Test
    fun `should get vacation`() {
        // given
        val id = listOf("ololo", "trololo", "nonono")
            .map { vacationWith(username = it) }.map { v ->
                transactionTemplate.execute {
                    vacations.save(v)
                }
            }
            .last()?.id ?: fail("id is required")
        // when
        val responseEntity = restClient.getForEntity<Vacation>("$baseUrl/vacations/{id}", id)
        logger.info { responseEntity }
        // then
        assertThat(responseEntity.statusCode).isEqualTo(HttpStatus.OK)
        // and
        val body = responseEntity.body
        assertThat(body).isNotNull
        assertThat(body?.username).isEqualTo("nonono")
    }

    @Test
    fun `should approve vacation`() {
        // given
        val id = transactionTemplate
            .execute {
                vacations.save(
                    vacationWith(
                        username = "daggerok",
                        status = CREATED,
                    )
                )
            }
            ?.id ?: fail("id is required")
        // when
        val responseEntity = restClient.exchange<Vacation>("$baseUrl/vacations/{id}", PUT, null, id)
        logger.info { responseEntity }
        // then
        assertThat(responseEntity.statusCode).isEqualTo(HttpStatus.OK)
        // and
        val body = responseEntity.body
        assertThat(body).isNotNull
        assertThat(body?.status).isEqualTo(Status.APPROVED)
    }

    @Test
    fun `should decline vacation`() {
        // given
        val id = transactionTemplate
            .execute {
                vacations.save(
                    vacationWith(
                        username = "daggerok",
                        status = CREATED,
                    )
                )
            }
            ?.id ?: fail("id is required")
        // when
        val responseEntity = restClient.exchange<Vacation>("$baseUrl/vacations/{id}", DELETE, null, id)
        logger.info { responseEntity }
        // then
        assertThat(responseEntity.statusCode).isEqualTo(HttpStatus.OK)
        // and
        val body = responseEntity.body
        assertThat(body).isNotNull
        assertThat(body?.status).isEqualTo(Status.DECLINED)
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
