package com.cjstudio.sosestrada

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

// Ponto de entrada do Hilt (mesmo padrão do Caronas/Match): as telas recebem
// os repositórios por injeção em vez de chamar o Firebase direto.
@HiltAndroidApp
class SosEstradaApplication : Application()
