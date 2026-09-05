package com.cortextransl.translateonscreen.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Application-level Hilt bindings. Repositories use @Inject constructors.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule
