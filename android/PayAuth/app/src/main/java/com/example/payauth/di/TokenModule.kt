package com.example.payauth.di

import com.example.payauth.auth.TokenManager
import com.example.payauth.auth.TokenStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class TokenModule {
    @Binds
    @Singleton
    abstract fun bindTokenStore(impl: TokenManager): TokenStore
}