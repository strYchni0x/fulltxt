package me.fulltxt.app.data.cloud

import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

class RateLimitMiddleware @Inject constructor() : Interceptor {

    companion object {
        private const val MAX_RETRIES = 5
        private const val INITIAL_BACKOFF_MS = 1_000L
        private const val MAX_BACKOFF_MS = 60_000L
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        var response = chain.proceed(chain.request())
        var retries = 0
        var backoffMs = INITIAL_BACKOFF_MS

        while (response.code == 429 && retries < MAX_RETRIES) {
            response.close()
            Thread.sleep(backoffMs)
            backoffMs = minOf(backoffMs * 2, MAX_BACKOFF_MS)
            retries++
            response = chain.proceed(chain.request())
        }

        return response
    }
}
