package daggerok

import daggerok.Status.CREATED
import java.time.LocalDate
import mu.KLogging
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.body
import org.springframework.web.reactive.function.client.bodyToFlux
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono

@Configuration
@ComponentScan(basePackageClasses = [VacationClient::class])
@EnableConfigurationProperties(VacationProps::class)
class Reactive0VacationClientAutoConfiguration {

    @Bean
    fun restClient(builder: WebClient.Builder, props: VacationProps): WebClient =
        builder.baseUrl(props.baseUrl).build()
}

@ConstructorBinding
@ConfigurationProperties("vacation-server")
data class VacationProps(
    val protocol: String = "http",
    val host: String = "127.0.0.1",
    val port: Int = 8003,
    val baseUrl: String = "$protocol://$host:$port"
)

@Component
class VacationClient(private val restClient: WebClient, private val props: VacationProps) {

    fun requestVacation(vacation: Vacation) =
        restClient.post()
            .uri("${props.baseUrl}/vacations")
            .body(Mono.just(vacation))
            .retrieve()
            .handleErrors()
            .bodyToMono<Vacation>()
            .log("request-vacation")

    fun searchVacations(vararg usernames: String = arrayOf("")) =
        usernames.joinToString(prefix = "?username=", separator = "&username=") { it }
            .let {
                restClient.get().uri("${props.baseUrl}/vacations$it")
                    .retrieve()
                    .handleErrors()
                    .bodyToFlux<Vacation>()
                    .log("search-vacations")
            }

    fun getVacation(id: Long) =
        restClient.get().uri("${props.baseUrl}/vacations/{id}", id)
            .retrieve()
            .handleErrors()
            .bodyToFlux<Vacation>()
            .log("get-vacation")

    fun approveVacation(id: Long) =
        restClient.put().uri("${props.baseUrl}/vacations/{id}", id)
            .retrieve()
            .handleErrors()
            .bodyToFlux<Vacation>()
            .log("approve-vacation")

    fun declineVacation(id: Long) =
        restClient.delete().uri("${props.baseUrl}/vacations/{id}", id)
            .retrieve()
            .handleErrors()
            .bodyToFlux<Vacation>()
            .log("approve-vacation")

    private companion object : KLogging() {
        fun WebClient.ResponseSpec.handleErrors() =
            onStatus(HttpStatus::isError) {
                it.bodyToMono<Map<String, Any>>()
                    .handle { value, sink ->
                        val message = if (value.containsKey("error")) value["error"].toString() else "Unknown error"
                        sink.error(RuntimeException(message))
                    }
            }
    }
}

enum class Status {
    CREATED, DECLINED, APPROVED
}

data class Vacation(
    val id: Long? = null,
    val username: String = "",
    val dateFrom: LocalDate = LocalDate.now(),
    val dateTo: LocalDate = LocalDate.now(),
    val hours: Long = 0,
    val status: Status = CREATED,
)
