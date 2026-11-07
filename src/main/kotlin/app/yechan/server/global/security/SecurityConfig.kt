package app.yechan.server.global.security

import app.yechan.server.global.config.CorsProperties
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration(proxyBeanMethods = false)
class SecurityConfig(
    private val corsProperties: CorsProperties,
    private val problemDetailSecurityHandler: ProblemDetailSecurityHandler,
    private val authenticators: ObjectProvider<RequestAuthenticator>,
) {
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain =
        http
            // CSRF is covered by SameSite=Strict session cookies and the CORS origin allowlist.
            .csrf { it.disable() }
            .cors { }
            .httpBasic { it.disable() }
            .formLogin { it.disable() }
            .logout { it.disable() }
            .requestCache { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .addFilterBefore(
                RequestAuthenticationFilter(authenticators.orderedStream().toList()),
                AnonymousAuthenticationFilter::class.java,
            ).exceptionHandling {
                it.authenticationEntryPoint(problemDetailSecurityHandler)
                it.accessDeniedHandler(problemDetailSecurityHandler)
            }.authorizeHttpRequests {
                it.requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                it.requestMatchers(HttpMethod.GET, "/api/v1/public/**").permitAll()
                it.requestMatchers(HttpMethod.GET, "$AUTH/onboarding").permitAll()
                it
                    .requestMatchers(
                        HttpMethod.POST,
                        "$AUTH/onboarding",
                        "$AUTH/onboarding/options",
                        "$AUTH/onboarding/password",
                        "$AUTH/sign-in/password",
                        "$AUTH/sign-in",
                        "$AUTH/sign-in/options",
                        "$AUTH/sign-out",
                    ).permitAll()
                it.requestMatchers("/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**").permitAll()
                it.requestMatchers("/error").permitAll()
                it.anyRequest().hasRole(ADMINISTRATOR_ROLE)
            }.build()

    // www and api share a site, so www must never be allowed to call the studio API with the session cookie.
    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val public =
            CorsConfiguration().apply {
                allowedOrigins = corsProperties.publicOrigins
                allowedMethods = listOf("GET", "HEAD", "OPTIONS")
                allowedHeaders = listOf("Accept-Language")
                allowCredentials = false
                maxAge = 3600
            }
        val studio =
            CorsConfiguration().apply {
                allowedOrigins = corsProperties.studioOrigins
                allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                allowedHeaders = listOf("Content-Type", "Accept-Language", SETUP_TOKEN_HEADER)
                allowCredentials = true
                maxAge = 3600
            }
        return UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration("/api/v1/public/**", public)
            registerCorsConfiguration("/api/v1/studio/**", studio)
        }
    }

    companion object {
        const val ADMINISTRATOR_ROLE = "ADMINISTRATOR"
        const val SETUP_TOKEN_HEADER = "X-Setup-Token"
        private const val AUTH = "/api/v1/studio/auth"
    }
}
