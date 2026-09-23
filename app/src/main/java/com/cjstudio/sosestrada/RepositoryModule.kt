package com.cjstudio.sosestrada

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: AuthRepository): IAuthRepository

    @Binds
    @Singleton
    abstract fun bindMotoristaRepository(impl: MotoristaRepository): IMotoristaRepository

    @Binds
    @Singleton
    abstract fun bindPrestadorRepository(impl: PrestadorRepository): IPrestadorRepository
}
