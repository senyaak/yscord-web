package de.yscord.auth

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component

/**
 * Logs the redirect URI the app sends to Google. Google's list of registered
 * URIs can't be read through an API, so when login fails with
 * redirect_uri_mismatch, compare this line with the client in Google's console.
 */
@Component
class RedirectUriLogger(@Value("\${app.public-url}") private val publicUrl: String) : ApplicationRunner {
    override fun run(args: ApplicationArguments) {
        LoggerFactory.getLogger(javaClass)
            .info("Google OAuth redirect URI (must be registered with Google): {}/login/oauth2/code/google", publicUrl)
    }
}
