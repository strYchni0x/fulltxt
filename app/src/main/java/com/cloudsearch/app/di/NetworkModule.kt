package me.fulltxt.app.di

import me.fulltxt.app.data.cloud.RateLimitMiddleware
import me.fulltxt.app.data.cloud.dropbox.DropboxApiService
import me.fulltxt.app.data.cloud.onedrive.GraphApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Named
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
            .followRedirects(true)
            .build()

    @Provides
    @Singleton
    fun provideGraphRetrofit(okHttpClient: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl("https://graph.microsoft.com/v1.0/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    @Provides
    @Singleton
    fun provideGraphApiService(retrofit: Retrofit): GraphApiService =
        retrofit.create(GraphApiService::class.java)

    @Provides
    @Singleton
    @Named("dropbox")
    fun provideDropboxRetrofit(okHttpClient: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl("https://api.dropboxapi.com/2/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    @Provides
    @Singleton
    fun provideDropboxApiService(@Named("dropbox") retrofit: Retrofit): DropboxApiService =
        retrofit.create(DropboxApiService::class.java)
}
