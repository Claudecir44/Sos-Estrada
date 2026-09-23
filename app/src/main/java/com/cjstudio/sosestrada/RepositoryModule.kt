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

    @Binds
    @Singleton
    abstract fun bindSolicitacaoRepository(impl: SolicitacaoRepository): ISolicitacaoRepository

    @Binds
    @Singleton
    abstract fun bindChatRepository(impl: ChatRepository): IChatRepository

    @Binds
    @Singleton
    abstract fun bindLocalizacaoRepository(impl: LocalizacaoRepository): ILocalizacaoRepository

    @Binds
    @Singleton
    abstract fun bindAssinaturaRepository(impl: AssinaturaRepository): IAssinaturaRepository

    @Binds
    @Singleton
    abstract fun bindAdminRepository(impl: AdminRepository): IAdminRepository
}
