package com.cjstudio.sosestrada

import com.google.firebase.firestore.Exclude

// Documento admins/{uid}: quem tem acesso ao painel administrativo. Só é
// criado com a senha do administrador master (conferida pelas regras do
// Firestore — ver firestore.rules, match /admins).
data class Admin(
    var nome: String? = null,
    var sobrenome: String? = null,
    var email: String? = null,
    var telefone: String? = null,
    var cpf: String? = null,
    // JPEG pequeno em base64 (ver FotoUtil) — não há Firebase Storage.
    var foto: String? = null,
    // null = administrador completo; "colaborador" = só as seções marcadas
    // em permissoes (definidas pelo admin master no cadastro). As regras do
    // Firestore (podeAdmin) aplicam o mesmo — isto aqui é só a tela.
    var role: String? = null,
    var permissoes: Map<String, Boolean>? = null
) {
    @get:Exclude
    val ehColaborador: Boolean
        get() = role == ROLE_COLABORADOR

    fun pode(secao: String): Boolean = !ehColaborador || permissoes?.get(secao) == true

    // Administrador master: o mesmo CPF do Caronas e do Match. Essa conta
    // nunca pode ser excluída (nem pelo "Meu Perfil", nem junto com um
    // cadastro de motorista/prestador da mesma conta, nem por outro admin):
    // sem ela o projeto fica sem ninguém com acesso total. As regras do
    // Firestore (match /admins) e as functions conferem o mesmo CPF.
    @get:Exclude
    val ehMaster: Boolean
        get() = cpf == CPF_ADMIN_MASTER

    companion object {
        const val CPF_ADMIN_MASTER = "56413025034"
        const val MENSAGEM_MASTER_NAO_EXCLUIVEL = "Esta é a conta do administrador master e não pode ser excluída."

        const val ROLE_COLABORADOR = "colaborador"

        const val PERM_MOTORISTAS = "motoristas"
        const val PERM_PRESTADORES = "prestadores"
        const val PERM_SOLICITACOES = "solicitacoes"
        const val PERM_MENSAGENS = "mensagens"
        const val PERM_ACOES = "acoes"
        const val PERM_FINANCEIRO = "financeiro"

        // Ordem e textos das caixinhas no cadastro do colaborador (e no painel web).
        val PERMISSOES = listOf(
            PERM_MOTORISTAS to "Ver motoristas",
            PERM_PRESTADORES to "Ver prestadores",
            PERM_SOLICITACOES to "Ver solicitações",
            PERM_MENSAGENS to "Ver mensagens (conversas)",
            PERM_FINANCEIRO to "Ver financeiro (pagamentos e relatórios)",
            PERM_ACOES to "Editar, bloquear e excluir cadastros (pede a senha master)"
        )
    }
}
