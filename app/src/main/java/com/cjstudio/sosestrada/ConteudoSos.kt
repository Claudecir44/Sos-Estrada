package com.cjstudio.sosestrada

// Textos das Configurações (TextoInformativoActivity), em seções: cada
// seção vira um cartão com ícone, título e itens (um parágrafo por item,
// com espaço entre eles). Itens aceitam <b>negrito</b>.
// Termos de uso e privacidade são os mesmos pro motorista e pro prestador;
// as regras são uma pra cada lado. Mudou o funcionamento do app (prazo da
// assinatura, o que precisa de aceite, etc.)? Atualize aqui também.
object ConteudoSos {

    const val EMAIL_SUPORTE = "cjstudiotechnology@gmail.com"

    const val TITULO_TERMOS = "Termos de uso e privacidade"
    const val TITULO_REGRAS_MOTORISTA = "Regras e como usar o app"
    const val TITULO_REGRAS_PRESTADOR = "Regras do prestador"

    data class Secao(val icone: String, val titulo: String, val itens: List<String>)

    // Chaves passadas pra TextoInformativoActivity.
    const val TERMOS = "termos"
    const val REGRAS_MOTORISTA = "regrasMotorista"
    const val REGRAS_PRESTADOR = "regrasPrestador"

    fun titulo(chave: String) = when (chave) {
        REGRAS_MOTORISTA -> TITULO_REGRAS_MOTORISTA
        REGRAS_PRESTADOR -> TITULO_REGRAS_PRESTADOR
        else -> TITULO_TERMOS
    }

    fun introducao(chave: String) = when (chave) {
        REGRAS_MOTORISTA -> "Tudo o que você precisa saber para pedir socorro com segurança e rapidez."
        REGRAS_PRESTADOR -> "Como atender os pedidos e o que os motoristas esperam de você."
        else -> "Última atualização: setembro de 2026. Ao criar uma conta e usar o SOS Estrada, você concorda com estes termos."
    }

    fun secoes(chave: String) = when (chave) {
        REGRAS_MOTORISTA -> regrasMotorista
        REGRAS_PRESTADOR -> regrasPrestador
        else -> termos
    }

    private val termos = listOf(
        Secao("🛡️", "O que é o SOS Estrada", listOf(
            "Um aplicativo da <b>CJ Studio Technology</b> que aproxima motoristas que precisam de socorro na estrada — guincho, mecânico, borracheiro, chaveiro e outros — de prestadores que oferecem esses serviços."
        )),
        Secao("🤝", "O que o app faz — e o que não faz", listOf(
            "O SOS Estrada é uma <b>plataforma de contato</b>: mostra prestadores, permite pedir o serviço, conversar pelo chat e compartilhar a localização.",
            "O socorro é prestado <b>pelo prestador</b>, único responsável pela execução, pelo preço combinado, pelos prazos e por eventuais danos.",
            "A CJ Studio Technology não presta o socorro, não garante que haverá prestador disponível e não participa do pagamento entre motorista e prestador."
        )),
        Secao("🚑", "Emergências", listOf(
            "Em acidente com feridos ou risco à vida, ligue primeiro para <b>192</b> (SAMU), <b>193</b> (Bombeiros) ou <b>190</b> (Polícia). O app não substitui os serviços de emergência."
        )),
        Secao("👤", "Conta e cadastro", listOf(
            "O cadastro exige um e-mail válido, confirmado pelo link enviado antes do primeiro acesso.",
            "Você é responsável pela veracidade dos seus dados e por manter sua senha em segredo.",
            "A conta é pessoal. Não crie conta em nome de outra pessoa."
        )),
        Secao("⚖️", "Uso adequado", listOf(
            "É proibido: fazer pedidos falsos ou de brincadeira; enviar mensagens ofensivas, ameaçadoras ou ilegais; usar os dados de outros usuários fora do atendimento; tentar burlar a segurança do app.",
            "O descumprimento pode levar ao <b>bloqueio da conta</b>, que também impede um novo cadastro."
        )),
        Secao("📊", "Dados que coletamos e para quê", listOf(
            "<b>Cadastro:</b> nome, e-mail, telefone e foto; do motorista, veículo e placa; do prestador, CNPJ, serviço, preço e endereço — para identificar você e aparecer para o outro lado do atendimento.",
            "<b>Localização:</b> usada para calcular a distância até os prestadores. Só é compartilhada quando <b>você</b> toca em \"Enviar Minha Localização\", <b>somente</b> com o prestador que aceitou o seu pedido — e você pode parar de enviar quando quiser. O app não rastreia você em segundo plano.",
            "<b>Solicitações e chat:</b> guardados para o atendimento funcionar. Mensagens com mais de <b>6 meses</b> são apagadas automaticamente.",
            "<b>Notificações:</b> um identificador do aparelho é guardado só para enviar os avisos do app.",
            "Não vendemos seus dados. Eles ficam armazenados nos serviços do Google Firebase."
        )),
        Secao("👀", "Quem vê seus dados", listOf(
            "O prestador vê os dados do motorista que pediu socorro a ele: nome, telefone, veículo, placa e endereço/localização do pedido.",
            "Os motoristas veem os dados públicos dos prestadores: nome, serviço, preço, telefone e endereço.",
            "A equipe administrativa pode ver cadastros e solicitações para suporte, segurança e cumprimento destes termos."
        )),
        Secao("🔐", "Seus direitos (LGPD)", listOf(
            "Você pode ver e corrigir seus dados em <b>Meu Perfil</b> e excluir seu cadastro a qualquer momento.",
            "Para outras solicitações sobre seus dados, escreva para <b>$EMAIL_SUPORTE</b>."
        )),
        Secao("💳", "Assinatura do prestador", listOf(
            "O prestador tem um período gratuito e, depois dele, precisa de assinatura ativa para continuar aparecendo na busca. As condições ficam em Configurações → Assinatura."
        )),
        Secao("📝", "Alterações e contato", listOf(
            "Estes termos podem ser atualizados, e mudanças importantes serão avisadas no app. Continuar usando o app depois disso significa que você concorda com a nova versão.",
            "Contato: <b>$EMAIL_SUPORTE</b>"
        ))
    )

    private val regrasMotorista = listOf(
        Secao("🚨", "Como pedir socorro", listOf(
            "<b>1.</b> No painel, toque em <b>Preciso de socorro</b>. A lista de prestadores abre logo abaixo, com a distância até cada um (com o GPS ligado).",
            "<b>2.</b> Use a busca para filtrar por serviço, nome ou cidade.",
            "<b>3.</b> No prestador escolhido, toque em <b>Solicitar serviço</b>. Ele recebe um aviso na hora.",
            "<b>4.</b> Acompanhe o status no próprio cartão: <b>aguardando</b>, <b>aceito</b> (está a caminho) ou <b>recusado</b> (procure outro). A lista se atualiza sozinha."
        )),
        Secao("🔘", "Botões do cartão do prestador", listOf(
            "<b>📞 Chamar:</b> liga para o prestador.",
            "<b>📍 Localização do Prestador:</b> abre o endereço dele no mapa.",
            "<b>💬 Mensagem:</b> chat com o prestador. A bolinha vermelha mostra as mensagens que você ainda não leu."
        )),
        Secao("📡", "Enviar sua localização", listOf(
            "O botão <b>Enviar Minha Localização</b> funciona <b>depois que o prestador aceitar</b> o pedido. Ele envia o ponto exato onde você está agora, para o prestador chegar mais rápido.",
            "Mudou de lugar? Pare e envie de novo para atualizar.",
            "Depois de enviar, o botão vira <b>🛑 Parar de Enviar Localização</b>. Tocando nele, sua localização é <b>removida na hora</b> e o prestador deixa de ver onde você está."
        )),
        Secao("🔔", "Avisos", listOf(
            "Você recebe notificação quando o prestador <b>responder</b> (aceitar ou recusar) e quando chegar <b>mensagem</b>.",
            "A bolinha em <b>Preciso de socorro</b> e o número no ícone do app mostram o que ainda não foi visto. Permita as notificações quando o app pedir."
        )),
        Secao("✋", "Cancelar um pedido", listOf(
            "Segure o cartão do prestador e confirme com a sua senha.",
            "Atenção: o prestador pode já estar a caminho — combine com ele pelo chat antes de cancelar. Cancelamentos repetidos sem motivo podem levar ao bloqueio da conta.",
            "Um pedido cancelado pode ser excluído segurando o cartão de novo."
        )),
        Secao("📋", "Regras de uso", listOf(
            "Só peça socorro quando realmente precisar. Pedidos falsos são proibidos.",
            "Combine preço e forma de pagamento <b>diretamente com o prestador</b>, antes do serviço. O pagamento não passa pelo app.",
            "Mantenha telefone, veículo e placa atualizados em <b>Meu Perfil</b> — é o que o prestador usa para encontrar você.",
            "Trate o prestador com respeito no chat e no atendimento.",
            "Em emergência com feridos: <b>192</b>, <b>193</b> ou <b>190</b> primeiro."
        )),
        Secao("✉️", "Dúvidas ou problemas", listOf(
            "Escreva para <b>$EMAIL_SUPORTE</b>."
        ))
    )

    private val regrasPrestador = listOf(
        Secao("🛠️", "Como atender", listOf(
            "<b>1.</b> No painel, toque em <b>Atender solicitações</b>. Os pedidos abrem logo abaixo, do mais novo para o mais antigo, e se atualizam sozinhos.",
            "<b>2.</b> Em cada pedido você vê nome, telefone, veículo, placa e o endereço de onde o motorista pediu.",
            "<b>3.</b> Toque em <b>✅ Aceitar</b> se você vai atender — o motorista é avisado e recebe uma mensagem pedindo a localização dele. Se não puder, toque em <b>❌ Recusar</b>, para ele procurar outro prestador rapidamente."
        )),
        Secao("🔘", "Botões do pedido", listOf(
            "<b>💬 Mensagem:</b> chat com o motorista. A bolinha vermelha mostra as mensagens não lidas.",
            "<b>📍 Localização do Motorista:</b> fica cinza até o motorista enviar a localização (ele só pode depois que você aceitar). Quando ele envia, você recebe um aviso, o botão mostra a hora do envio e abre o ponto exato no mapa.",
            "Se o motorista <b>parar de enviar</b>, o botão volta a ficar cinza na hora."
        )),
        Secao("🔔", "Avisos", listOf(
            "Você recebe notificação de <b>nova solicitação</b>, <b>mensagem</b> e <b>localização enviada</b>.",
            "A bolinha em <b>Atender solicitações</b> e o número no ícone do app mostram o que ainda não foi visto. Responder rápido faz diferença para quem está parado na estrada."
        )),
        Secao("📋", "Regras do prestador", listOf(
            "Responda os pedidos o quanto antes: aceite só se realmente puder ir; se não puder, recuse.",
            "Depois de aceitar, vá ao local ou avise pelo chat se houver atraso ou imprevisto.",
            "Informe o preço <b>antes</b> de executar o serviço e cumpra o valor combinado. O pagamento é combinado diretamente com o motorista.",
            "Mantenha serviço, preço, telefone e endereço corretos em <b>Meu Perfil</b> — é o que os motoristas veem na busca.",
            "Use os dados do motorista somente para o atendimento.",
            "Você é o responsável pela execução do serviço e por cumprir as leis e normas da sua atividade.",
            "Atendimento desrespeitoso, cobranças abusivas ou pedidos aceitos e não atendidos podem levar ao <b>bloqueio</b> da conta."
        )),
        Secao("💳", "Assinatura", listOf(
            "Seu cadastro tem um período gratuito. Depois dele, é preciso manter a assinatura ativa para continuar aparecendo na busca dos motoristas.",
            "Veja o prazo e o plano em <b>Configurações → Assinatura</b>."
        )),
        Secao("✉️", "Dúvidas ou problemas", listOf(
            "Escreva para <b>$EMAIL_SUPORTE</b>."
        ))
    )
}
