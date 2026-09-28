package com.cjstudio.sosestrada

// Textos das Configurações (TextoInformativoActivity), em HTML simples
// (<b>, <br>, listas com •) pra poder ter títulos e destaques numa TextView.
// Termos de uso e privacidade são os mesmos pro motorista e pro prestador;
// as regras são uma pra cada lado. Mudou o funcionamento do app (prazo da
// assinatura, o que precisa de aceite, etc.)? Atualize aqui também.
object ConteudoSos {

    const val EMAIL_SUPORTE = "cjstudiotechnology@gmail.com"

    const val TITULO_TERMOS = "Termos de uso e privacidade"
    const val TITULO_REGRAS_MOTORISTA = "Regras e como usar o app"
    const val TITULO_REGRAS_PRESTADOR = "Regras do prestador"

    val termos = """
        <b>Última atualização: setembro de 2026</b><br><br>
        O SOS Estrada é um aplicativo da <b>CJ Studio Technology</b> que aproxima motoristas que precisam de socorro na estrada (guincho, mecânico, borracheiro, chaveiro e outros serviços) de prestadores que oferecem esses serviços. Ao criar uma conta e usar o app, você concorda com estes termos.<br><br>

        <b>1. O que o app faz — e o que não faz</b><br>
        • O SOS Estrada é uma <b>plataforma de contato</b>: mostra prestadores, permite pedir o serviço, conversar pelo chat e compartilhar a localização.<br>
        • O serviço de socorro é prestado <b>pelo prestador</b>, que é o único responsável pela execução, pelo preço combinado, pelos prazos e pelos danos que causar. A CJ Studio Technology não presta o socorro, não garante que haverá prestador disponível e não participa do pagamento do serviço entre motorista e prestador.<br>
        • Em caso de acidente com feridos ou risco à vida, ligue primeiro para <b>192 (SAMU)</b>, <b>193 (Bombeiros)</b> ou <b>190 (Polícia)</b>. O app não substitui os serviços de emergência.<br><br>

        <b>2. Conta e cadastro</b><br>
        • O cadastro exige um e-mail válido, que precisa ser confirmado pelo link enviado antes do primeiro acesso.<br>
        • Você é responsável pela veracidade dos dados (nome, telefone, veículo, placa, serviço, endereço) e por manter sua senha em segredo.<br>
        • A conta é pessoal. Não crie conta em nome de outra pessoa.<br><br>

        <b>3. Uso adequado</b><br>
        É proibido: fazer pedidos falsos ou de brincadeira; enviar mensagens ofensivas, ameaçadoras ou com conteúdo ilegal; usar os dados de outros usuários fora do atendimento; tentar burlar a segurança do app. O descumprimento pode levar ao <b>bloqueio da conta</b>, que impede novo cadastro com a mesma conta.<br><br>

        <b>4. Dados que coletamos e para quê</b><br>
        • <b>Cadastro:</b> nome, e-mail, telefone, foto; do motorista, veículo e placa; do prestador, CNPJ, serviço, preço e endereço — para identificar você e aparecer para o outro lado do atendimento.<br>
        • <b>Localização:</b> usada para calcular a distância até os prestadores e, quando <b>você</b> toca em "Enviar Minha Localização", compartilhada <b>somente</b> com o prestador que aceitou a sua solicitação. O app não rastreia você em segundo plano.<br>
        • <b>Solicitações e chat:</b> guardados para o atendimento funcionar. Mensagens do chat com mais de <b>6 meses</b> são apagadas automaticamente.<br>
        • <b>Notificações:</b> um identificador do aparelho é guardado só para enviar os avisos do app (mensagem, solicitação, resposta, localização).<br>
        • Não vendemos seus dados. Eles ficam armazenados nos serviços do Google Firebase.<br><br>

        <b>5. Quem vê seus dados</b><br>
        • O prestador vê os dados do motorista que pediu socorro a ele (nome, telefone, veículo, placa e endereço/localização do pedido).<br>
        • Os motoristas veem os dados públicos dos prestadores (nome, serviço, preço, telefone e endereço).<br>
        • A equipe administrativa do app pode ver os cadastros e as solicitações para suporte, segurança e cumprimento destes termos.<br><br>

        <b>6. Seus direitos (LGPD)</b><br>
        Você pode ver e corrigir seus dados em "Meu Perfil" e excluir seu cadastro a qualquer momento. Para outras solicitações sobre seus dados, escreva para <b>$EMAIL_SUPORTE</b>.<br><br>

        <b>7. Assinatura do prestador</b><br>
        O prestador tem um período gratuito e, depois dele, precisa de uma assinatura ativa para continuar aparecendo na busca dos motoristas. As condições ficam em Configurações → Assinatura.<br><br>

        <b>8. Alterações</b><br>
        Estes termos podem ser atualizados. Mudanças importantes serão avisadas no app. Continuar usando o app depois da mudança significa que você concorda com a nova versão.<br><br>

        <b>Contato:</b> $EMAIL_SUPORTE
    """.trimIndent()

    val regrasMotorista = """
        <b>Como pedir socorro</b><br>
        1. No painel, toque em <b>Preciso de socorro</b>. A lista de prestadores abre logo abaixo, com a distância até cada um (se o GPS estiver ligado e a permissão de localização for dada).<br>
        2. Use a busca para filtrar por serviço (guincho, mecânico, borracheiro…), nome ou cidade.<br>
        3. No prestador escolhido, toque em <b>🚨 Solicitar serviço</b>. Ele recebe um aviso na hora.<br>
        4. Acompanhe o status no próprio cartão: <b>aguardando</b>, <b>aceito</b> (o prestador está a caminho) ou <b>recusado</b> (procure outro).<br><br>

        <b>Botões do cartão do prestador</b><br>
        • <b>📞 Chamar:</b> liga para o prestador.<br>
        • <b>📍 Localização do Prestador:</b> abre o endereço dele no mapa.<br>
        • <b>💬 Mensagem:</b> chat com o prestador (aparece depois que você faz a solicitação). A bolinha vermelha mostra quantas mensagens você ainda não leu.<br>
        • <b>📡 Enviar Minha Localização:</b> só funciona <b>depois que o prestador aceitar</b>. Envia o ponto exato onde você está agora, para ele chegar mais rápido. Pode enviar de novo se mudar de lugar.<br><br>

        <b>Avisos</b><br>
        Você recebe notificação quando o prestador <b>responder</b> (aceitar ou recusar) e quando chegar <b>mensagem</b>. A bolinha em "Preciso de socorro" e o número no ícone do app mostram o que ainda não foi visto. Permita as notificações quando o app pedir.<br><br>

        <b>Cancelar uma solicitação</b><br>
        Segure o cartão do prestador e confirme com a sua senha. Atenção: o prestador pode já estar a caminho — combine com ele pelo chat antes de cancelar. Cancelamentos sem motivo repetidos podem levar ao bloqueio da conta.<br>
        Uma solicitação cancelada pode ser excluída segurando o cartão de novo.<br><br>

        <b>Regras de uso</b><br>
        • Só peça socorro quando realmente precisar. Pedidos falsos são proibidos.<br>
        • Combine preço e forma de pagamento <b>diretamente com o prestador</b>, antes do serviço. O pagamento não passa pelo app.<br>
        • Mantenha seus dados atualizados em <b>Meu Perfil</b> (telefone, veículo e placa) — é o que o prestador usa para encontrar você.<br>
        • Trate o prestador com respeito no chat e no atendimento.<br>
        • Em emergência com feridos: <b>192</b> (SAMU), <b>193</b> (Bombeiros) ou <b>190</b> (Polícia) primeiro.<br><br>

        <b>Dúvidas ou problemas:</b> ${EMAIL_SUPORTE}
    """.trimIndent()

    val regrasPrestador = """
        <b>Como atender</b><br>
        1. No painel, toque em <b>Atender solicitações</b>. Os pedidos recebidos abrem logo abaixo, do mais novo para o mais antigo.<br>
        2. Em cada pedido você vê nome, telefone, veículo, placa e o endereço de onde o motorista pediu.<br>
        3. Toque em <b>✅ Aceitar</b> se você vai atender — o motorista é avisado e recebe uma mensagem automática de que você está a caminho. Se não puder, toque em <b>❌ Recusar</b>, para ele procurar outro prestador rapidamente.<br><br>

        <b>Botões do pedido</b><br>
        • <b>💬 Mensagem:</b> chat com o motorista. A bolinha vermelha mostra as mensagens não lidas.<br>
        • <b>📍 Localização do Motorista:</b> fica cinza até o motorista enviar a localização atual (ele só pode enviar depois que você aceitar). Quando ele envia, você recebe um aviso, o botão mostra a hora do envio e abre o ponto exato no mapa.<br><br>

        <b>Avisos</b><br>
        Você recebe notificação de <b>nova solicitação</b>, <b>mensagem</b> e <b>localização enviada</b>. A bolinha em "Atender solicitações" e o número no ícone do app mostram o que ainda não foi visto. Permita as notificações quando o app pedir — responder rápido faz diferença para quem está parado na estrada.<br><br>

        <b>Regras do prestador</b><br>
        • Responda os pedidos o quanto antes: aceite só se realmente puder ir; se não puder, recuse.<br>
        • Depois de aceitar, vá ao local ou avise pelo chat se houver qualquer atraso ou imprevisto.<br>
        • Informe o preço <b>antes</b> de executar o serviço e cumpra o valor combinado. O pagamento é combinado diretamente com o motorista.<br>
        • Mantenha seu cadastro correto em <b>Meu Perfil</b> (serviço, preço, telefone e endereço) — é o que os motoristas veem na busca.<br>
        • Use os dados do motorista somente para o atendimento.<br>
        • Você é o responsável pela execução do serviço e por cumprir as leis e normas da sua atividade.<br>
        • Atendimento desrespeitoso, cobranças abusivas ou pedidos aceitos e não atendidos podem levar ao <b>bloqueio</b> da conta.<br><br>

        <b>Assinatura</b><br>
        Seu cadastro tem um período gratuito. Depois dele, é preciso manter a assinatura ativa para continuar aparecendo na busca dos motoristas. Veja o prazo e o plano em <b>Configurações → Assinatura</b>.<br><br>

        <b>Dúvidas ou problemas:</b> ${EMAIL_SUPORTE}
    """.trimIndent()
}
