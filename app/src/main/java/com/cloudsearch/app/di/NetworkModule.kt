package me.fulltxt.app.di

import me.fulltxt.app.data.cloud.RateLimitMiddleware
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideRateLimitMiddleware(): RateLimitMiddleware = RateLimitMiddleware()

    @Provides
    @Singleton
    fun provideOkHttpClient(rateLimitMiddleware: RateLimitMiddleware): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(rateLimitMiddleware)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
            .build()
}
