// Carrega o access token do Mercado Pago de .env (não versionado) — ver
// MERCADOPAGO_ACCESS_TOKEN mais abaixo.
require("dotenv").config();

const { onSchedule } = require("firebase-functions/v2/scheduler");
const { onCall, onRequest, HttpsError } = require("firebase-functions/v2/https");
const { onDocumentCreated, onDocumentUpdated, onDocumentWritten, onDocumentDeleted } = require("firebase-functions/v2/firestore");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore, Timestamp, FieldValue } = require("firebase-admin/firestore");
const { getStorage } = require("firebase-admin/storage");
const { getAuth } = require("firebase-admin/auth");
const { getMessaging } = require("firebase-admin/messaging");
const mercadopago = require("mercadopago");
const nodemailer = require("nodemailer");
const logger = require("firebase-functions/logger");

initializeApp();

const MERCADO_PAGO_ACCESS_TOKEN = process.env.MERCADOPAGO_ACCESS_TOKEN || "";
if (!MERCADO_PAGO_ACCESS_TOKEN) {
  logger.warn(
      "MERCADOPAGO_ACCESS_TOKEN não configurado — funções de pagamento vão " +
      "recusar chamadas até isso ser definido em functions/.env (não " +
      "versionado). Conta do Mercado Pago é separada da usada no Match.",
  );
} else {
  mercadopago.configure({ access_token: MERCADO_PAGO_ACCESS_TOKEN });
}

// E-mail pros prestadores (aviso de fim do plano) — Gmail com senha de app,
// mesmo esquema do Caronas: SOS_EMAIL_USER / SOS_EMAIL_PASSWORD em
// functions/.env (não versionado). Sem isso o e-mail não sai (só o push) e
// fica registrado no log.
const SOS_EMAIL_USER = process.env.SOS_EMAIL_USER || "";
const SOS_EMAIL_PASSWORD = process.env.SOS_EMAIL_PASSWORD || "";
const transporteEmail = SOS_EMAIL_USER && SOS_EMAIL_PASSWORD ?
  nodemailer.createTransport({ service: "gmail", auth: { user: SOS_EMAIL_USER, pass: SOS_EMAIL_PASSWORD } }) :
  null;
if (!transporteEmail) {
  logger.warn("SOS_EMAIL_USER/SOS_EMAIL_PASSWORD não configurados — avisos de fim de plano vão só por push.");
}

async function isAdmin(uid) {
  const doc = await getFirestore().collection("admins").doc(uid).get();
  return doc.exists;
}

// Gatilhos do Firestore precisam rodar na mesma região do banco
// (southamerica-east1). As callables continuam no padrão (us-central1), que
// é onde o app (FirebaseFunctions.getInstance()) e o webhook do Mercado Pago
// procuram.
const REGIAO_FIRESTORE = "southamerica-east1";

const SEIS_MESES_EM_MS = 1000 * 60 * 60 * 24 * 30 * 6;
const TAMANHO_LOTE = 400;

// Roda todos os dias e apaga mensagens de chat (solicitacoes/{id}/mensagens)
// com mais de 6 meses, junto com as fotos anexadas no Storage.
exports.limparMensagensAntigas = onSchedule("every 24 hours", async () => {
  const db = getFirestore();
  const bucket = getStorage().bucket();
  const limite = Timestamp.fromMillis(Date.now() - SEIS_MESES_EM_MS);

  const snapshot = await db
      .collectionGroup("mensagens")
      .where("timestamp", "<", limite)
      .get();

  if (snapshot.empty) {
    logger.info("Nenhuma mensagem com mais de 6 meses para remover.");
    return;
  }

  let lote = db.batch();
  let contador = 0;

  for (const doc of snapshot.docs) {
    const imagemUrl = doc.get("imagemUrl");
    if (imagemUrl) {
      await apagarImagemDoStorage(bucket, imagemUrl);
    }
    lote.delete(doc.ref);
    contador++;

    if (contador % TAMANHO_LOTE === 0) {
      await lote.commit();
      lote = db.batch();
    }
  }

  if (contador % TAMANHO_LOTE !== 0) {
    await lote.commit();
  }

  logger.info(`Removidas ${contador} mensagens com mais de 6 meses.`);
});

async function apagarImagemDoStorage(bucket, url) {
  try {
    const caminho = decodeURIComponent(
        new URL(url).pathname.split("/o/")[1].split("?")[0],
    );
    await bucket.file(caminho).delete({ ignoreNotFound: true });
  } catch (erro) {
    logger.warn("Não foi possível apagar imagem do Storage:", erro.message);
  }
}

// Provisiona a ÚNICA conta de admin real (Firebase Auth por e-mail/senha +
// documento em admins/{uid}, usado pelas firestore.rules pra reconhecer
// quem é admin de verdade). Antes, o AdminActivity só comparava
// usuário/senha fixos no código do app e depois logava anonimamente — uma
// sessão assim é indistinguível de qualquer outro usuário anônimo nas
// regras de segurança, então não dava pra escrever uma regra "ehAdmin()"
// confiável.
//
// Só funciona UMA vez: se já existir qualquer documento em admins/, recusa.
// Depois de rodada uma vez com sucesso, essa function fica permanentemente
// inerte (pode deixar deployada sem risco) — criar outro admin, se um dia
// precisar, é uma decisão consciente que merece ser feita à mão (Firebase
// Console) ou com uma function nova, não por aqui.
exports.provisionarAdminInicial = onCall(async (request) => {
  const db = getFirestore();
  const auth = getAuth();

  const jaExisteAdmin = await db.collection("admins").limit(1).get();
  if (!jaExisteAdmin.empty) {
    throw new HttpsError(
        "failed-precondition",
        "Já existe um admin provisionado — esta function só roda uma vez.",
    );
  }

  const email = request.data && request.data.email;
  const senha = request.data && request.data.senha;
  if (!email || !senha || senha.length < 6) {
    throw new HttpsError(
        "invalid-argument",
        "Informe email e senha (mínimo 6 caracteres).",
    );
  }

  const usuario = await auth.createUser({ email, password: senha });

  await db.collection("admins").doc(usuario.uid).set({
    email,
    criadoEm: Timestamp.now(),
  });

  logger.info(`Admin inicial provisionado: ${usuario.uid} (${email})`);
  return { uid: usuario.uid };
});

// ============================================================
// Assinatura de prestadores (Mercado Pago) — 60 dias grátis a partir do
// cadastro, depois um dos planos (Trimestral R$79,90 / 90 dias ou Semestral
// R$129,90 / 180 dias — ver PLANOS) pra manter o
// cadastro visível na busca do motorista (SocorroFragment filtra por
// "ativo"==true). Espelha o desenho do Match (createPaymentPreference/
// paymentWebhook/checkPaymentStatus). Vencer sem pagar desativa a listagem
// (avisos com 5 e 2 dias antes — avisarVencimentoPrestadores); 6 meses
// inativo remove o cadastro (removerPrestadoresInativos). O período grátis
// vale uma vez por CPF/CNPJ (documentosPrestador).
// ============================================================

const TRIAL_DIAS = 60;
const DIA_MS = 1000 * 60 * 60 * 24;
// Mesmos valores/prazos mostrados no app (AssinaturaActivity). O app manda
// só a chave do plano; valor e prazo sempre saem daqui (o cliente não
// escolhe preço).
const PLANOS = {
  trimestral: { nome: "Trimestral", valor: 79.90, diasValidade: 90 },
  semestral: { nome: "Semestral", valor: 129.90, diasValidade: 180 },
};
const PLANO_PADRAO = "trimestral";

// ============================================================
// Google Play Billing (igual Caronas): os planos também são vendidos como
// produtos avulsos no Google Play, e com o "faturamento alternativo"
// (User Choice) o prestador escolhe Google Play ou Mercado Pago. O app só
// manda o produto/token; preço e prazo saem de PLANOS.
//  - confirmarCompraGooglePlayPrestador: confere a compra na API do Google,
//    concede o plano e CONSOME (pra poder comprar de novo na renovação).
//  - Pagamento pelo Mercado Pago escolhido na tela do Google: o webhook
//    reporta a transação externa ao Google (reportarTransacaoExterna).
// Precisa em functions/.env: GOOGLE_PLAY_PACKAGE_NAME e
// GOOGLE_PLAY_SERVICE_ACCOUNT_JSON (a mesma conta de serviço do Caronas,
// com acesso ao app SOS Estrada no Play Console).
// ============================================================
const PRODUTOS_GOOGLE_PLAY = {
  plano_prestador_trimestral: "trimestral",
  plano_prestador_semestral: "semestral",
};
const GOOGLE_PLAY_PACKAGE_NAME = process.env.GOOGLE_PLAY_PACKAGE_NAME || "";
const GOOGLE_PLAY_SERVICE_ACCOUNT_JSON = process.env.GOOGLE_PLAY_SERVICE_ACCOUNT_JSON || "";
let androidPublisherCliente = null;

function obterAndroidPublisher() {
  if (androidPublisherCliente) return androidPublisherCliente;
  if (!GOOGLE_PLAY_PACKAGE_NAME || !GOOGLE_PLAY_SERVICE_ACCOUNT_JSON) return null;
  const { google } = require("googleapis");
  const auth = new google.auth.GoogleAuth({
    credentials: JSON.parse(GOOGLE_PLAY_SERVICE_ACCOUNT_JSON),
    scopes: ["https://www.googleapis.com/auth/androidpublisher"],
  });
  androidPublisherCliente = google.androidpublisher({ version: "v3", auth });
  return androidPublisherCliente;
}

async function reportarTransacaoExterna({ externalTransactionToken, valor, paymentId }) {
  const androidpublisher = obterAndroidPublisher();
  if (!androidpublisher) {
    logger.warn("Google Play não configurado — transação externa não reportada:", paymentId);
    return;
  }
  const externalTransactionId = `mp_${paymentId}`;
  try {
    await androidpublisher.externaltransactions.createexternaltransaction({
      parent: `applications/${GOOGLE_PLAY_PACKAGE_NAME}`,
      externalTransactionId,
      requestBody: {
        originalPreTaxAmount: { priceMicros: String(Math.round(valor * 1e6)), currency: "BRL" },
        originalTaxAmount: { priceMicros: "0", currency: "BRL" },
        transactionTime: new Date().toISOString(),
        userTaxAddress: { regionCode: "BR" },
        oneTimeTransaction: { externalTransactionToken },
      },
    });
    logger.info("Transação externa reportada ao Google Play:", externalTransactionId);
  } catch (e) {
    logger.error("Falha ao reportar transação externa ao Google Play:", externalTransactionId, e.message);
  }
}

// Ativa/renova o plano (renovação antes de vencer soma o que restava) e
// registra no histórico (Financeiro). referencia: { mercadoPagoPaymentId }
// ou { googlePlayPurchaseToken, googlePlayOrderId }.
async function concederAssinaturaPrestador(prestadorId, chavePlano, valor, referencia) {
  const db = getFirestore();
  const plano = PLANOS[chavePlano] || PLANOS[PLANO_PADRAO];
  const agora = Date.now();
  const atual = await db.collection("prestadores").doc(prestadorId).get();
  const expiraAtual = atual.exists && atual.get("assinaturaStatus") === "ativa" && atual.get("assinaturaExpiraEm") ?
    atual.get("assinaturaExpiraEm").toMillis() : 0;
  const expiraEm = Math.max(agora, expiraAtual) + plano.diasValidade * DIA_MS;

  await db.collection("prestadores").doc(prestadorId).update({
    ativo: true,
    assinaturaStatus: "ativa",
    assinaturaExpiraEm: Timestamp.fromMillis(expiraEm),
    assinaturaUltimoPagamento: Timestamp.fromMillis(agora),
    // "Plano pago 00/90 dias" no painel: o total inclui dias que sobraram
    // de antes (renovação antecipada soma).
    assinaturaDiasTotal: Math.round((expiraEm - agora) / DIA_MS),
    inativoDesde: FieldValue.delete(),
    avisosVencimento: FieldValue.delete(),
  });
  logger.info(`Assinatura ativada para prestador ${prestadorId}, expira em ${new Date(expiraEm).toISOString()}`);

  try {
    const dados = (await db.collection("prestadores").doc(prestadorId).get()).data() || {};
    await db.collection("pagamentos").add({
      prestadorId,
      prestadorNome: dados.nome || "",
      prestadorEmail: dados.email || "",
      valor,
      plano: chavePlano,
      diasValidade: plano.diasValidade,
      dataCompra: agora,
      expiraEm,
      ...referencia,
    });
  } catch (histError) {
    logger.error("Erro ao registrar histórico de pagamento:", histError);
  }
}

// CPF (11 dígitos) ou CNPJ (14) com os dígitos verificadores conferidos —
// mesma conta do app (DocumentoUtil).
function somenteDigitos(valor) {
  return String(valor || "").replace(/\D/g, "");
}

function cpfValido(d) {
  if (d.length !== 11 || /^(\d)\1+$/.test(d)) return false;
  for (const tamanho of [9, 10]) {
    let soma = 0;
    for (let i = 0; i < tamanho; i++) soma += Number(d[i]) * (tamanho + 1 - i);
    if (((soma * 10) % 11) % 10 !== Number(d[tamanho])) return false;
  }
  return true;
}

function cnpjValido(d) {
  if (d.length !== 14 || /^(\d)\1+$/.test(d)) return false;
  const pesos = [6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2];
  for (const tamanho of [12, 13]) {
    const p = pesos.slice(pesos.length - tamanho);
    let soma = 0;
    for (let i = 0; i < tamanho; i++) soma += Number(d[i]) * p[i];
    const resto = soma % 11;
    if ((resto < 2 ? 0 : 11 - resto) !== Number(d[tamanho])) return false;
  }
  return true;
}

function documentoValido(d) {
  return d.length === 11 ? cpfValido(d) : cnpjValido(d);
}

// Só os 3 últimos dígitos no log (CPF é dado pessoal).
function documentoNoLog(d) {
  return `***${String(d).slice(-3)}`;
}

// documentosPrestador/{cpf ou cnpj}: quem já usou o período grátis. Fica
// guardado pra sempre (mesmo depois que o cadastro é removido por
// inatividade) — o mesmo CPF/CNPJ não ganha os 60 dias grátis de novo, só
// ativa com plano pago. Devolve true se ESTE cadastro ganha o período grátis.
async function registrarDocumento(uid, documento) {
  const d = somenteDigitos(documento);
  if (!documentoValido(d)) {
    logger.warn(`Prestador ${uid} sem CPF/CNPJ válido — começa sem período grátis.`);
    return false;
  }
  const ref = getFirestore().collection("documentosPrestador").doc(d);
  try {
    await ref.create({ uid, usouTrialEm: Timestamp.now(), historicoUids: [uid] });
    return true;
  } catch (erro) {
    if (erro.code !== 6) throw erro; // 6 = ALREADY_EXISTS
    const registro = await ref.get();
    // Gatilho repetido pro mesmo cadastro: o período grátis já era dele.
    if (registro.get("uid") === uid && (registro.get("historicoUids") || []).length === 1) return true;
    await ref.update({ uid, historicoUids: FieldValue.arrayUnion(uid), atualizadoEm: Timestamp.now() });
    logger.info(`Documento ${documentoNoLog(d)} já usou o período grátis — ${uid} começa sem trial.`);
    return false;
  }
}

// Cadastro novo (CadastroPrestadorActivity), logo depois de criar a conta:
// "livre" (ganha o período grátis), "sem_trial" (CPF/CNPJ já usou os 60
// dias; o cadastro nasce inativo até pagar) ou "em_uso" (outro cadastro de
// prestador que ainda existe tem esse CPF/CNPJ — o app desfaz a conta nova).
exports.verificarDocumentoPrestador = onCall(async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Usuário não autenticado.");
  }
  const d = somenteDigitos(request.data && request.data.documento);
  if (!documentoValido(d)) {
    throw new HttpsError("invalid-argument", "CPF ou CNPJ inválido.");
  }
  const db = getFirestore();
  const registro = await db.collection("documentosPrestador").doc(d).get();
  if (!registro.exists) return { situacao: "livre" };
  const dono = registro.get("uid");
  if (dono === request.auth.uid) return { situacao: "livre" };
  if (dono && (await db.collection("prestadores").doc(dono).get()).exists) return { situacao: "em_uso" };
  return { situacao: "sem_trial" };
});

// Campos que só esta Cloud Function (Admin SDK, ignora firestore.rules)
// pode escrever — o cliente nunca grava "ativo" nem os campos de
// assinatura diretamente (ver firestore.rules, camposDoServidorPrestador).
exports.aoRegistrarPrestador = onDocumentCreated({ document: "prestadores/{uid}", region: REGIAO_FIRESTORE }, async (event) => {
  const snap = event.data;
  if (!snap) return;
  const dados = snap.data();
  // Idempotência básica: se por algum motivo o documento já nasceu com
  // esses campos (não deveria, as regras bloqueiam), não sobrescreve.
  if (dados.dataCadastro && dados.assinaturaStatus) return;

  const agora = Timestamp.now();
  if (await registrarDocumento(event.params.uid, dados.documento)) {
    await snap.ref.update({
      dataCadastro: dados.dataCadastro || agora,
      ativo: true,
      assinaturaStatus: "trial",
      assinaturaExpiraEm: null,
      assinaturaUltimoPagamento: null,
    });
    logger.info(`Trial de ${TRIAL_DIAS} dias iniciado para prestador ${event.params.uid}`);
    return;
  }
  // CPF/CNPJ que já usou o período grátis: nasce inativo, só ativa pagando.
  await snap.ref.update({
    dataCadastro: dados.dataCadastro || agora,
    ativo: false,
    assinaturaStatus: "expirada",
    trialNegado: true,
    inativoDesde: agora,
    assinaturaExpiraEm: null,
    assinaturaUltimoPagamento: null,
  });
});

// Cadastro antigo (anterior ao CPF/CNPJ obrigatório) que informou o
// documento agora no Meu Perfil: entra no registro de quem já usou o
// período grátis (o dele já foi usado/está em uso).
exports.registrarDocumentoPrestador = onDocumentUpdated(
    { document: "prestadores/{uid}", region: REGIAO_FIRESTORE },
    async (event) => {
      if (event.data.before.get("documento") || !event.data.after.get("documento")) return;
      const d = somenteDigitos(event.data.after.get("documento"));
      if (!documentoValido(d)) return;
      const uid = event.params.uid;
      const ref = getFirestore().collection("documentosPrestador").doc(d);
      try {
        await ref.create({ uid, usouTrialEm: event.data.after.get("dataCadastro") || Timestamp.now(), historicoUids: [uid] });
      } catch (erro) {
        if (erro.code !== 6) throw erro;
        await ref.update({ historicoUids: FieldValue.arrayUnion(uid) });
      }
    },
);

// Backfill pra prestadores cadastrados ANTES do módulo de assinatura
// existir — sem os campos novos, o filtro whereEqualTo("ativo", true) do
// SocorroFragment os deixaria invisíveis pra sempre. dataCadastro real
// desses cadastros antigos não é conhecida, então usa a data de hoje como
// início do trial (mais generoso que assumir que o trial já venceu).
// Idempotente e de baixo risco (só preenche o que está faltando, nunca
// sobrescreve) — por isso só exige estar autenticado, sem checagem de
// admin: rodar de novo por engano não tem efeito colateral.
exports.migrarAssinaturaPrestadores = onCall(async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Usuário não autenticado.");
  }
  // Só admin: antes qualquer usuário logado conseguia disparar.
  if (!(await isAdmin(request.auth.uid))) {
    throw new HttpsError("permission-denied", "Apenas administradores.");
  }

  const db = getFirestore();
  const snapshot = await db.collection("prestadores").get();

  let migrados = 0;
  let lote = db.batch();
  let naLote = 0;

  for (const doc of snapshot.docs) {
    const dados = doc.data();
    if (dados.assinaturaStatus) continue; // já migrado

    lote.update(doc.ref, {
      dataCadastro: dados.dataCadastro || Timestamp.now(),
      ativo: true,
      assinaturaStatus: "trial",
      assinaturaExpiraEm: null,
      assinaturaUltimoPagamento: null,
    });
    migrados++;
    naLote++;

    if (naLote >= 400) {
      await lote.commit();
      lote = db.batch();
      naLote = 0;
    }
  }
  if (naLote > 0) await lote.commit();

  logger.info(`migrarAssinaturaPrestadores: ${migrados} prestador(es) migrado(s) para trial.`);
  return { migrados };
});

exports.criarPreferenciaPagamentoPrestador = onCall(async (request) => {
  if (!MERCADO_PAGO_ACCESS_TOKEN) {
    throw new HttpsError("failed-precondition", "Mercado Pago não configurado.");
  }
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Usuário não autenticado.");
  }

  const prestadorId = request.auth.uid;
  const db = getFirestore();
  const prestadorDoc = await db.collection("prestadores").doc(prestadorId).get();
  if (!prestadorDoc.exists) {
    throw new HttpsError("not-found", "Cadastro de prestador não encontrado.");
  }
  const prestador = prestadorDoc.data();
  const chavePlano = (request.data && request.data.plano) || PLANO_PADRAO;
  const plano = PLANOS[chavePlano];
  if (!plano) {
    throw new HttpsError("invalid-argument", "Plano inválido.");
  }

  try {
    const preference = {
      items: [{
        id: `plano_${chavePlano}_prestador`,
        title: `Plano ${plano.nome} - SOS Estrada Prestador`,
        description: `Assinatura ${plano.nome.toLowerCase()} (${plano.diasValidade} dias) para manter o cadastro ativo no SOS Estrada`,
        quantity: 1,
        currency_id: "BRL",
        unit_price: plano.valor,
      }],
      payer: {
        email: prestador.email || `${prestadorId}@sosestrada.app`,
        name: prestador.nome || "Prestador",
      },
      external_reference: `${prestadorId}_${Date.now()}`,
      back_urls: {
        success: "sosestrada://payment_success",
        failure: "sosestrada://payment_failure",
        pending: "sosestrada://payment_pending",
      },
      auto_return: "approved",
      notification_url: `https://us-central1-${process.env.GCLOUD_PROJECT}.cloudfunctions.net/paymentWebhookPrestador`,
      metadata: {
        prestadorId,
        plano: chavePlano,
        diasValidade: plano.diasValidade,
        // Token da tela de escolha do Google (User Choice): o webhook reporta
        // a transação externa ao Google Play com ele.
        externalTransactionToken: (request.data && request.data.externalTransactionToken) || null,
      },
      statement_descriptor: "SOS ESTRADA",
    };

    logger.info(`Criando preferência de pagamento para prestador ${prestadorId}`);
    const response = await mercadopago.preferences.create(preference);

    return {
      preferenceId: response.body.id,
      initPoint: response.body.init_point,
    };
  } catch (error) {
    logger.error("Erro ao criar preferência:", error);
    throw new HttpsError("internal", "Erro ao criar pagamento: " + error.message);
  }
});

exports.paymentWebhookPrestador = onRequest(async (req, res) => {
  if (!MERCADO_PAGO_ACCESS_TOKEN) {
    logger.error("Token do Mercado Pago não configurado.");
    res.status(500).send("Mercado Pago não configurado.");
    return;
  }
  if (req.method !== "POST") {
    res.sendStatus(405);
    return;
  }

  try {
    const { id, topic } = req.query;
    if (topic !== "payment" || !id) {
      res.sendStatus(200);
      return;
    }

    const paymentResponse = await mercadopago.payment.findById(id);
    const payment = paymentResponse.body;

    let prestadorId = null;
    if (payment.metadata && payment.metadata.prestadorId) {
      prestadorId = payment.metadata.prestadorId;
    } else if (payment.external_reference) {
      prestadorId = payment.external_reference.split("_")[0];
    }

    if (!prestadorId) {
      logger.warn("Webhook sem prestadorId identificável.");
      res.sendStatus(200);
      return;
    }

    if (payment.status === "approved") {
      const db = getFirestore();

      // Idempotência: o MP reenvia a mesma notificação em retries — sem
      // isso, cada reenvio do mesmo payment.id renovaria a assinatura do
      // zero de novo. create() falha atomicamente se o doc já existir.
      const idempotenciaRef = db.collection("pagamentosProcessados").doc(String(payment.id));
      try {
        await idempotenciaRef.create({ prestadorId, processadoEm: Date.now() });
      } catch (idempotenciaError) {
        if (idempotenciaError.code === 6) { // ALREADY_EXISTS
          logger.info(`Pagamento ${payment.id} já processado — notificação repetida ignorada.`);
          res.sendStatus(200);
          return;
        }
        throw idempotenciaError;
      }

      const chavePlano = (payment.metadata && payment.metadata.plano) || PLANO_PADRAO;
      const valor = payment.transaction_amount || 0;
      await concederAssinaturaPrestador(prestadorId, PLANOS[chavePlano] ? chavePlano : PLANO_PADRAO, valor, { mercadoPagoPaymentId: payment.id });

      // Pago fora do Google Play pela escolha do usuário: reporta ao Google.
      const tokenExterno = payment.metadata && (payment.metadata.external_transaction_token || payment.metadata.externalTransactionToken);
      if (tokenExterno) {
        await reportarTransacaoExterna({ externalTransactionToken: tokenExterno, valor, paymentId: payment.id });
      }
    } else {
      logger.info(`Pagamento ${payment.id} com status ${payment.status} para prestador ${prestadorId} — nenhuma ação.`);
    }

    res.sendStatus(200);
  } catch (error) {
    logger.error("Erro no webhook de pagamento:", error);
    res.status(500).send("Erro interno: " + error.message);
  }
});

exports.checkPaymentStatus = onCall(async (request) => {
  if (!MERCADO_PAGO_ACCESS_TOKEN) {
    throw new HttpsError("failed-precondition", "Mercado Pago não configurado.");
  }
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Usuário não autenticado.");
  }

  const { paymentId } = request.data || {};
  if (!paymentId) {
    throw new HttpsError("invalid-argument", "paymentId é obrigatório.");
  }

  try {
    const paymentResponse = await mercadopago.payment.findById(paymentId);
    const payment = paymentResponse.body;

    const donoId = (payment.metadata && payment.metadata.prestadorId) ||
      (payment.external_reference && payment.external_reference.split("_")[0]);
    if (donoId !== request.auth.uid && !(await isAdmin(request.auth.uid))) {
      throw new HttpsError("permission-denied", "Você não tem acesso a este pagamento.");
    }

    return {
      id: payment.id,
      status: payment.status,
      statusDetail: payment.status_detail,
      transactionAmount: payment.transaction_amount,
    };
  } catch (error) {
    if (error instanceof HttpsError) throw error;
    logger.error("Erro ao verificar pagamento:", error);
    throw new HttpsError("internal", "Erro ao verificar pagamento.");
  }
});

// Roda todo dia: desativa (não apaga) prestadores cujo período grátis de
// 60 dias venceu sem assinatura, ou cuja assinatura venceu sem renovação.
// Assinatura ATIVA e ainda dentro da validade nunca é tocada aqui.
exports.expirarAssinaturasPrestadores = onSchedule("every day 04:00", async () => {
  const db = getFirestore();
  const agora = Date.now();
  const snapshot = await db.collection("prestadores").where("ativo", "==", true).get();

  let desativados = 0;
  for (const doc of snapshot.docs) {
    const dados = doc.data();

    if (dados.assinaturaStatus === "ativa") {
      const expiraEm = dados.assinaturaExpiraEm ? dados.assinaturaExpiraEm.toMillis() : 0;
      if (expiraEm && expiraEm > agora) continue; // assinatura em dia
      await doc.ref.update({ ativo: false, assinaturaStatus: "expirada", inativoDesde: Timestamp.now() });
      desativados++;
      continue;
    }

    // status "trial" (ou ausente, cadastro pré-migração já com ativo=true)
    const dataCadastro = dados.dataCadastro ? dados.dataCadastro.toMillis() : null;
    if (!dataCadastro) continue; // sem dado confiável, não mexe
    const diasDesdeCadastro = Math.floor((agora - dataCadastro) / DIA_MS);
    if (diasDesdeCadastro < TRIAL_DIAS) continue; // ainda no período grátis

    await doc.ref.update({ ativo: false, assinaturaStatus: "expirada", inativoDesde: Timestamp.now() });
    desativados++;
  }

  logger.info(`expirarAssinaturasPrestadores: ${desativados} prestador(es) desativado(s).`);
});

// Fim do plano atual (período grátis ou pago) e dias que faltam — mesmas
// contas do app (StatusAssinatura.resumo), arredondando pra cima.
function fimDoPlano(dados) {
  if (dados.assinaturaStatus === "ativa") return dados.assinaturaExpiraEm ? dados.assinaturaExpiraEm.toMillis() : null;
  if (dados.assinaturaStatus === "trial" && dados.dataCadastro) return dados.dataCadastro.toMillis() + TRIAL_DIAS * DIA_MS;
  return null;
}

async function enviarEmailVencimento(email, nome, pago, restam, fim) {
  if (!transporteEmail || !email) return false;
  const plano = pago ? "plano pago" : "plano free (período grátis de 60 dias)";
  const data = new Date(fim).toLocaleDateString("pt-BR", { timeZone: "America/Sao_Paulo" });
  const dias = restam === 1 ? "1 dia" : `${restam} dias`;
  try {
    await transporteEmail.sendMail({
      from: `"SOS Estrada" <${SOS_EMAIL_USER}>`,
      to: email,
      subject: `SOS Estrada: seu plano termina em ${dias}`,
      html: `<p>Olá, ${primeiroNome(nome, "prestador")}!</p>
<p>Seu <b>${plano}</b> do SOS Estrada termina em <b>${dias}</b>, no dia <b>${data}</b>.</p>
<p>Depois disso seu cadastro fica <b>inativo</b> e deixa de aparecer para os motoristas.</p>
<p>Para continuar aparecendo, abra o app SOS Estrada → ⚙️ Configurações → <b>Assinatura</b> e escolha um plano:
Trimestral (R$ 79,90 / 90 dias) ou Semestral (R$ 129,90 / 180 dias).</p>
<p>Cadastros que ficam 6 meses inativos sem renovar são removidos automaticamente.</p>
<p>Equipe SOS Estrada</p>`,
    });
    return true;
  } catch (erro) {
    logger.warn(`Erro ao enviar e-mail de vencimento para ${email}:`, erro.message);
    return false;
  }
}

// Todo dia às 9h: avisa (e-mail + push) quem está a 5 e a 2 dias do fim do
// plano, grátis ou pago. avisosVencimento guarda pra qual fim de plano cada
// aviso já saiu — renovar muda o fim e zera os avisos.
exports.avisarVencimentoPrestadores = onSchedule(
    { schedule: "every day 09:00", timeZone: "America/Sao_Paulo" },
    async () => {
      const agora = Date.now();
      const snapshot = await getFirestore().collection("prestadores").where("ativo", "==", true).get();
      let avisados = 0;
      for (const doc of snapshot.docs) {
        const dados = doc.data();
        if (dados.bloqueado === true) continue;
        const fim = fimDoPlano(dados);
        if (!fim) continue;
        const restam = Math.ceil((fim - agora) / DIA_MS);
        if (restam <= 0 || restam > 5) continue;
        const marco = restam <= 2 ? "2" : "5";
        if ((dados.avisosVencimento || {})[marco] === fim) continue;

        const pago = dados.assinaturaStatus === "ativa";
        const dias = restam === 1 ? "1 dia" : `${restam} dias`;
        const email = await getAuth().getUser(doc.id).then((u) => u.email).catch(() => null) || dados.email;
        const porEmail = await enviarEmailVencimento(email, dados.nome, pago, restam, fim);
        const porPush = await enviarPush(doc.id, null, "assinatura", "prestador",
            `Seu ${pago ? "plano pago" : "plano free"} termina em ${dias}. Renove em Configurações → Assinatura.`, `plano-${marco}`);
        if (porEmail || porPush) {
          await doc.ref.update({ [`avisosVencimento.${marco}`]: fim });
          avisados++;
        }
      }
      logger.info(`avisarVencimentoPrestadores: ${avisados} aviso(s) enviado(s).`);
    },
);

// Todo dia: cadastro inativo (plano vencido sem renovar) há 6 meses sai do
// sistema. Os dados vão pra prestadoresRemovidos/{uid} e o CPF/CNPJ continua
// em documentosPrestador — se a pessoa se cadastrar de novo, não ganha outro
// período grátis. Bloqueado pelo admin nunca é removido aqui.
exports.removerPrestadoresInativos = onSchedule("every day 05:00", async () => {
  const db = getFirestore();
  const limite = Date.now() - SEIS_MESES_EM_MS;
  const snapshot = await db.collection("prestadores").where("ativo", "==", false).get();
  let removidos = 0;
  for (const doc of snapshot.docs) {
    const dados = doc.data();
    if (dados.bloqueado === true) continue;
    // Inativo de antes deste controle: começa a contar os 6 meses agora.
    if (!dados.inativoDesde) {
      await doc.ref.update({ inativoDesde: Timestamp.now() });
      continue;
    }
    if (dados.inativoDesde.toMillis() > limite) continue;

    const uid = doc.id;
    await db.collection("prestadoresRemovidos").doc(uid).set({
      ...dados, removidoEm: Timestamp.now(), motivo: "inativo_6_meses",
    });
    await doc.ref.delete();
    await db.collection("fcmTokens").doc(uid).delete();
    // Sem a conta, o e-mail fica livre pra um cadastro novo. Quem também é
    // motorista (conta antiga com os dois perfis) mantém a conta.
    if (!(await db.collection("motoristas").doc(uid).get()).exists) {
      await getAuth().deleteUser(uid).catch((erro) => {
        if (erro.code !== "auth/user-not-found") logger.warn(`Erro ao apagar a conta ${uid}:`, erro.message);
      });
    }
    removidos++;
  }
  logger.info(`removerPrestadoresInativos: ${removidos} cadastro(s) removido(s).`);
});

// ============================================================
// Notificações push (SosFirebaseMessagingService no app). Sempre só "data"
// (nunca um bloco "notification"): o app decide título e tela pelo "tipo".
// O token de cada conta fica em fcmTokens/{uid} (NotificacaoRepository).
// Nunca manda o texto da mensagem do chat — a notificação aparece na tela
// de bloqueio.
// ============================================================

function primeiroNome(nome, padrao) {
  return (nome || "").trim().split(/\s+/)[0] || padrao;
}

async function tokenDe(uid) {
  if (!uid) return null;
  const snap = await getFirestore().collection("fcmTokens").doc(uid).get();
  return snap.exists ? snap.get("token") : null;
}

// remetenteUid: quem causou o aviso. Se o celular registrado pra quem
// recebe for o MESMO de quem enviou (as duas contas usadas no mesmo
// aparelho), não manda — senão a pessoa recebia o aviso da própria ação
// no próprio celular. Vale pra todos os tipos de push.
async function enviarPush(uid, remetenteUid, tipo, destino, corpo, id) {
  if (!uid) return;
  const [token, tokenRemetente] = await Promise.all([tokenDe(uid), tokenDe(remetenteUid)]);
  if (!token) {
    logger.info(`push ${tipo}: ${uid} sem celular registrado.`);
    return;
  }
  if (token === tokenRemetente) {
    logger.info(`push ${tipo}: não enviado — ${uid} e o remetente ${remetenteUid} estão no mesmo celular.`);
    return;
  }
  try {
    await getMessaging().send({
      token,
      data: { tipo, destino, corpo, id, destinatarioUid: uid },
      android: { priority: "high" },
    });
    logger.info(`push ${tipo}: enviado para ${uid}.`);
    return true;
  } catch (erro) {
    logger.warn(`Erro ao enviar push (${tipo}) para ${uid}:`, erro.message);
    // App desinstalado ou dados limpos: token morto, não tenta mais.
    if (erro.code === "messaging/registration-token-not-registered") {
      await getFirestore().collection("fcmTokens").doc(uid).delete();
    }
  }
}

// Um celular = uma conta. Quando o aparelho é registrado pra uma conta
// (NotificacaoRepository.registrarToken), sai de qualquer outra que ainda o
// tenha — senão, quem testou como prestador e depois entrou como motorista
// no mesmo celular continuaria recebendo os avisos do prestador. (O app
// também apaga o registro no "Sair", mas isso cobre quem só trocou de conta
// sem sair, ou reinstalou o app.)
exports.tokenUnicoPorAparelho = onDocumentWritten(
    { document: "fcmTokens/{uid}", region: REGIAO_FIRESTORE },
    async (event) => {
      const token = event.data?.after?.get("token");
      if (!token) return;
      const iguais = await getFirestore().collection("fcmTokens").where("token", "==", token).get();
      const lote = getFirestore().batch();
      let removidos = 0;
      iguais.forEach((doc) => {
        if (doc.id !== event.params.uid) {
          lote.delete(doc.ref);
          removidos++;
        }
      });
      if (removidos > 0) {
        await lote.commit();
        logger.info(`tokenUnicoPorAparelho: aparelho saiu de ${removidos} outra(s) conta(s).`);
      }
    },
);

// Motorista pediu socorro -> "Solicitação Sos Estrada" pro prestador.
exports.notificarNovaSolicitacao = onDocumentCreated(
    { document: "solicitacoes/{id}", region: REGIAO_FIRESTORE },
    async (event) => {
      const s = event.data?.data();
      if (!s) return;
      const nome = primeiroNome(s.motoristaNome, "Um motorista");
      await enviarPush(s.prestadorUid, s.motoristaUid, "novaSolicitacao", "prestador",
          `${nome} precisa de socorro. Toque para ver a solicitação.`, event.params.id);
    },
);

// Prestador aceitou ou recusou -> "Prestador respondeu" pro motorista. Só
// na mudança de pendente para aceito/recusado (não em qualquer update).
exports.notificarRespostaPrestador = onDocumentUpdated(
    { document: "solicitacoes/{id}", region: REGIAO_FIRESTORE },
    async (event) => {
      const antes = event.data?.before?.data();
      const depois = event.data?.after?.data();
      if (!antes || !depois || antes.status === depois.status) return;
      if (depois.status !== "aceito" && depois.status !== "recusado") return;
      const nome = depois.prestadorNome || "O prestador";
      const corpo = depois.status === "aceito" ?
        `${nome} aceitou sua solicitação e está a caminho.` :
        `${nome} recusou sua solicitação. Procure outro prestador.`;
      await enviarPush(depois.motoristaUid, depois.prestadorUid, "respostaPrestador", "motorista", corpo, event.params.id);
    },
);

// Mensagem nova no chat -> "Mensagem Sos Estrada" pro outro lado. Mensagem
// automática do app (aviso de aceite) não gera push: o motorista já recebe
// o "Prestador respondeu" na mesma hora.
exports.notificarMensagemSos = onDocumentCreated(
    { document: "solicitacoes/{solicitacaoId}/mensagens/{mensagemId}", region: REGIAO_FIRESTORE },
    async (event) => {
      const m = event.data?.data();
      if (!m || m.automatica === true) return;
      const solicitacao = await getFirestore().collection("solicitacoes").doc(event.params.solicitacaoId).get();
      if (!solicitacao.exists) return;
      const s = solicitacao.data();
      const doMotorista = m.remetenteTipo === "motorista";
      const destinatario = doMotorista ? s.prestadorUid : s.motoristaUid;
      const remetente = doMotorista ? primeiroNome(s.motoristaNome, "O motorista") : (s.prestadorNome || "O prestador");
      await enviarPush(destinatario, m.remetenteUid, "mensagemSos", doMotorista ? "prestador" : "motorista",
          `Nova mensagem de ${remetente}.`, event.params.solicitacaoId);
    },
);

// Motorista tocou em "Enviar Minha Localização" (só com a solicitação
// aceita) -> "Localização Sos Estrada" pro prestador. Dispara a cada envio
// novo (o horário do servidor muda), inclusive reenvios.
exports.notificarLocalizacaoMotorista = onDocumentUpdated(
    { document: "solicitacoes/{id}", region: REGIAO_FIRESTORE },
    async (event) => {
      const antes = event.data?.before?.data();
      const depois = event.data?.after?.data();
      if (!antes || !depois || !depois.localizacaoCompartilhadaEm) return;
      const emAntes = antes.localizacaoCompartilhadaEm ? antes.localizacaoCompartilhadaEm.toMillis() : 0;
      if (depois.localizacaoCompartilhadaEm.toMillis() === emAntes) return;
      const nome = primeiroNome(depois.motoristaNome, "O motorista");
      await enviarPush(depois.prestadorUid, depois.motoristaUid, "localizacaoMotorista", "prestador",
          `${nome} enviou a localização atual. Toque para ver no mapa.`, event.params.id);
    },
);

// ============================================================
// E-mail de verificação próprio do SOS Estrada (mesmo modelo do Caronas).
//
// O link vem de getAuth().generateEmailVerificationLink — o mesmo link
// oficial do Firebase, só que o e-mail em volta é nosso, em português e com
// passo a passo. Cada link novo invalida o anterior; por isso o login só
// pede outro e-mail se o último tiver mais de 1 hora (controle aqui no
// servidor, em controleVerificacaoEmail/{uid}, que não se perde ao
// reinstalar o app). Sem SOS_EMAIL_USER/SOS_EMAIL_PASSWORD devolve
// usarPadrao e o app manda o e-mail padrão do Firebase.
// ============================================================
const NOME_APP = "SOS Estrada";
const UMA_HORA_MS = 60 * 60 * 1000;

function escaparHtml(valor) {
  return String(valor || "")
      .replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;").replace(/'/g, "&#39;");
}

async function primeiroNomeDaConta(uid, nomeInformado) {
  let nome = String(nomeInformado || "").trim();
  if (!nome) {
    const db = getFirestore();
    const [mot, pre, adm] = await Promise.all([
      db.collection("motoristas").doc(uid).get(),
      db.collection("prestadores").doc(uid).get(),
      db.collection("admins").doc(uid).get(),
    ]);
    nome = (mot.exists && mot.get("nome")) || (pre.exists && pre.get("nome")) || (adm.exists && adm.get("nome")) || "";
  }
  return String(nome).split(/\s+/)[0] || "";
}

function montarEmailVerificacao(primeiroNome, email, link) {
  const saudacao = primeiroNome ? `Olá, ${escaparHtml(primeiroNome)}!` : "Olá!";
  const assunto = `Confirme seu e-mail para usar o ${NOME_APP}`;
  const html = `
<div style="background:#F2F2F7;padding:24px 12px;font-family:Arial,Helvetica,sans-serif;color:#222;">
  <div style="max-width:520px;margin:0 auto;background:#ffffff;border-radius:16px;overflow:hidden;">
    <div style="background:#0D47A1;padding:22px 24px;text-align:center;">
      <div style="font-size:26px;font-weight:bold;color:#FFB300;">SOS Estrada</div>
      <div style="font-size:13px;color:#BBDEFB;margin-top:2px;">Socorro na estrada</div>
    </div>
    <div style="padding:26px 24px 8px;">
      <h1 style="font-size:22px;color:#0D47A1;margin:0 0 10px;">${saudacao}</h1>
      <p style="font-size:16px;line-height:1.55;margin:0 0 6px;">Seu cadastro no <b>${NOME_APP}</b> está quase pronto.</p>
      <p style="font-size:16px;line-height:1.55;margin:0 0 22px;">Falta só <b>confirmar que este e-mail é seu</b>. É rápido:</p>
      <div style="text-align:center;margin:0 0 24px;">
        <a href="${link}" style="display:inline-block;background:#1565C0;color:#ffffff;text-decoration:none;font-size:18px;font-weight:bold;padding:15px 30px;border-radius:28px;">CONFIRMAR MEU E-MAIL</a>
      </div>
      <div style="background:#E3F2FD;border-radius:12px;padding:14px 16px;margin:0 0 18px;">
        <div style="font-size:16px;font-weight:bold;color:#0D47A1;margin-bottom:6px;">Como fazer</div>
        <div style="font-size:15px;line-height:1.6;color:#0D47A1;">
          <b>1.</b> Toque no botão <b>CONFIRMAR MEU E-MAIL</b> acima.<br>
          <b>2.</b> Vai abrir uma página dizendo que o seu e-mail foi verificado.<br>
          <b>3.</b> Volte ao app SOS Estrada e entre com seu e-mail e senha.
        </div>
      </div>
      <div style="background:#FFF3E0;border-radius:12px;padding:14px 16px;margin:0 0 18px;font-size:14px;line-height:1.55;color:#7A4100;">
        <b>Importante:</b> abra este link assim que puder. Se você pedir outro e-mail de confirmação, <b>este link deixa de valer</b> — use sempre o e-mail mais recente.
      </div>
      <p style="font-size:13px;line-height:1.5;color:#555;margin:0 0 6px;">O botão não funcionou? Copie o endereço abaixo e cole no navegador:</p>
      <p style="font-size:12px;line-height:1.4;word-break:break-all;margin:0 0 20px;"><a href="${link}" style="color:#1565C0;">${link}</a></p>
      <p style="font-size:13px;line-height:1.5;color:#555;margin:0 0 22px;">Não fez cadastro no SOS Estrada com <b>${escaparHtml(email)}</b>? É só ignorar este e-mail — nada será ativado.</p>
    </div>
    <div style="border-top:1px solid #E5E5EA;padding:14px 24px;font-size:12px;color:#888;text-align:center;">
      ${NOME_APP} · CJ Studio Technology<br>Este é um e-mail automático, não precisa responder.
    </div>
  </div>
</div>`;
  const texto = `${primeiroNome ? `Olá, ${primeiroNome}!` : "Olá!"}\n\n` +
    `Seu cadastro no ${NOME_APP} está quase pronto. Falta só confirmar que este e-mail é seu.\n\n` +
    `1. Abra este link: ${link}\n` +
    "2. Vai abrir uma página dizendo que o seu e-mail foi verificado.\n" +
    "3. Volte ao app SOS Estrada e entre com seu e-mail e senha.\n\n" +
    "Importante: se você pedir outro e-mail de confirmação, este link deixa de valer — use sempre o mais recente.\n\n" +
    `Não fez cadastro no SOS Estrada com ${email}? É só ignorar este e-mail.\n\n` +
    `${NOME_APP} · CJ Studio Technology`;
  return { assunto, html, texto };
}

async function msDesdeUltimoEnvioVerificacao(uid) {
  const snap = await getFirestore().collection("controleVerificacaoEmail").doc(uid).get();
  const ultimo = snap.exists ? snap.get("ultimoEnvio") : null;
  return ultimo ? Date.now() - ultimo.toMillis() : Infinity;
}

async function registrarEnvioVerificacao(uid) {
  await getFirestore().collection("controleVerificacaoEmail").doc(uid)
      .set({ ultimoEnvio: FieldValue.serverTimestamp() })
      .catch((e) => logger.warn("Não gravou controleVerificacaoEmail", e.message));
}

// origem "cadastro": logo depois de criar a conta (intervalo mínimo de 1
// min, só contra toque duplo). origem "login": conta ainda não confirmada
// tentando entrar — só manda outro se o último tiver mais de 1 hora.
// Respostas: {enviado} | {recente} | {jaVerificado} | {usarPadrao} (o app
// manda o e-mail padrão do Firebase e avisa com registrarEnvioPadrao).
exports.enviarVerificacaoEmailPropria = onCall(async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Faça login para continuar.");
  const uid = request.auth.uid;
  const dados = request.data || {};
  const origem = dados.origem === "cadastro" ? "cadastro" : "login";

  const conta = await getAuth().getUser(uid);
  if (conta.emailVerified) return { jaVerificado: true };

  const intervaloMinimo = origem === "cadastro" ? 60 * 1000 : UMA_HORA_MS;
  if (await msDesdeUltimoEnvioVerificacao(uid) < intervaloMinimo) return { recente: true };

  if (dados.registrarEnvioPadrao === true) {
    await registrarEnvioVerificacao(uid);
    return { registrado: true };
  }
  if (!transporteEmail || !conta.email) return { usarPadrao: true };

  try {
    let link = await getAuth().generateEmailVerificationLink(conta.email);
    if (!/[?&]lang=/.test(link)) link += "&lang=pt-BR";
    const primeiroNome = await primeiroNomeDaConta(uid, dados.nome);
    const { assunto, html, texto } = montarEmailVerificacao(primeiroNome, conta.email, link);
    await transporteEmail.sendMail({
      from: `"${NOME_APP}" <${SOS_EMAIL_USER}>`,
      to: conta.email,
      subject: assunto,
      html,
      text: texto,
    });
    await registrarEnvioVerificacao(uid);
    return { enviado: true };
  } catch (e) {
    logger.error("Falha no e-mail de verificação próprio — app usa o padrão", e);
    return { usarPadrao: true };
  }
});

// ============================================================
// Cadastro excluído (pelo admin no painel, ou pelo próprio usuário) ->
// apaga também o login (Firebase Auth), como no Caronas. Sem isso, a conta
// excluída pelo admin continuava existindo e o e-mail ficava preso.
// Não apaga se: a mesma conta ainda tem outro perfil (motorista, prestador
// ou admin), ou se ela está BLOQUEADA — o bloqueio (bloqueados/{uid}) é
// pelo login; apagar o login deixaria a pessoa se recadastrar.
// ============================================================
async function apagarLoginSeSobrouNada(uid) {
  const db = getFirestore();
  const [mot, pre, adm, bloq] = await Promise.all([
    db.collection("motoristas").doc(uid).get(),
    db.collection("prestadores").doc(uid).get(),
    db.collection("admins").doc(uid).get(),
    db.collection("bloqueados").doc(uid).get(),
  ]);
  if (mot.exists || pre.exists || adm.exists || bloq.exists) return;
  try {
    await getAuth().deleteUser(uid);
    logger.info(`Login ${uid} apagado junto com o cadastro.`);
  } catch (e) {
    // O próprio app já apagou (exclusão pelo usuário) — nada a fazer.
    if (e.code !== "auth/user-not-found") logger.error("Falha ao apagar login", uid, e);
  }
}

exports.apagarLoginMotoristaExcluido = onDocumentDeleted(
    { document: "motoristas/{uid}", region: REGIAO_FIRESTORE },
    (event) => apagarLoginSeSobrouNada(event.params.uid),
);

exports.apagarLoginPrestadorExcluido = onDocumentDeleted(
    { document: "prestadores/{uid}", region: REGIAO_FIRESTORE },
    (event) => apagarLoginSeSobrouNada(event.params.uid),
);

// ============================================================
// Avaliação nova -> recalcula a média de quem foi avaliado em
// notasUsuarios/{uid} (o app lê pra mostrar "⭐ 4,8 (12 avaliações)" nos
// cartões sem somar tudo de novo). Recalcula do zero a cada avaliação:
// idempotente e corrige sozinho se uma avaliação for apagada pelo admin.
// ============================================================
async function recalcularNotaUsuario(uid) {
  const db = getFirestore();
  const snap = await db.collection("avaliacoes").where("avaliadoUid", "==", uid).get();
  const notas = snap.docs.map((d) => d.get("nota")).filter((n) => typeof n === "number");
  const total = notas.length;
  const media = total ? Math.round((notas.reduce((a, b) => a + b, 0) / total) * 10) / 10 : 0;
  await db.collection("notasUsuarios").doc(uid).set({ media, total, atualizadoEm: FieldValue.serverTimestamp() });
}

exports.atualizarNotaUsuario = onDocumentWritten(
    { document: "avaliacoes/{avaliacaoId}", region: REGIAO_FIRESTORE },
    async (event) => {
      const dados = event.data?.after?.data() || event.data?.before?.data();
      if (dados?.avaliadoUid) await recalcularNotaUsuario(dados.avaliadoUid);
    },
);

// ============================================================
// Resposta do admin a uma reclamação/sugestão/denúncia (chip "📝 Sugestões"
// do painel), igual ao Caronas: grava a resposta e manda por e-mail pra
// quem enviou. Admin completo ou colaborador com a permissão "mensagens".
// Sem SOS_EMAIL_USER/SOS_EMAIL_PASSWORD a resposta fica só gravada (e o
// admin é avisado).
// ============================================================
async function podeResponderManifestacoes(uid) {
  const doc = await getFirestore().collection("admins").doc(uid).get();
  if (!doc.exists) return false;
  const dados = doc.data();
  if ((dados.role || "admin") !== "colaborador") return true;
  return dados.permissoes?.mensagens === true;
}

exports.responderManifestacao = onCall(async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Faça login para continuar.");
  if (!(await podeResponderManifestacoes(request.auth.uid))) {
    throw new HttpsError("permission-denied", "Sem permissão para responder.");
  }
  const id = String(request.data?.id || "");
  const resposta = String(request.data?.resposta || "").trim();
  if (!id || !resposta) throw new HttpsError("invalid-argument", "Escreva a resposta.");
  if (resposta.length > 4000) throw new HttpsError("invalid-argument", "Resposta muito longa.");

  const ref = getFirestore().collection("manifestacoes").doc(id);
  const snap = await ref.get();
  if (!snap.exists) throw new HttpsError("not-found", "Mensagem não encontrada.");
  const m = snap.data();

  let enviadoPorEmail = false;
  if (transporteEmail && m.email) {
    const tipo = { reclamacao: "reclamação", sugestao: "sugestão", denuncia: "denúncia" }[m.tipo] || "mensagem";
    const nome = String(m.nome || "").split(/\s+/)[0];
    await transporteEmail.sendMail({
      from: `"${NOME_APP}" <${SOS_EMAIL_USER}>`,
      to: m.email,
      subject: `Resposta à sua ${tipo} — ${NOME_APP}`,
      text: `${nome ? `Olá, ${nome}!` : "Olá!"}\n\n` +
        `Recebemos sua ${tipo}:\n"${m.mensagem || ""}"\n\n` +
        `Nossa resposta:\n${resposta}\n\n` +
        `${NOME_APP} · CJ Studio Technology`,
    });
    enviadoPorEmail = true;
  }
  await ref.update({
    resposta,
    status: "respondida",
    respondidoEm: FieldValue.serverTimestamp(),
    respondidoPor: request.auth.uid,
    respostaEnviadaPorEmail: enviadoPorEmail,
  });
  return { enviadoPorEmail };
});


exports.confirmarCompraGooglePlayPrestador = onCall(async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Usuário não autenticado.");
  const prestadorId = request.auth.uid;
  const { purchaseToken, productId } = request.data || {};
  if (!purchaseToken || !productId) throw new HttpsError("invalid-argument", "Dados incompletos.");
  const chavePlano = PRODUTOS_GOOGLE_PLAY[productId];
  if (!chavePlano) throw new HttpsError("invalid-argument", "Produto desconhecido: " + productId);
  const androidpublisher = obterAndroidPublisher();
  if (!androidpublisher) {
    throw new HttpsError("failed-precondition", "Integração com o Google Play não configurada no servidor.");
  }

  let compra;
  try {
    compra = (await androidpublisher.purchases.products.get({
      packageName: GOOGLE_PLAY_PACKAGE_NAME, productId, token: purchaseToken,
    })).data;
  } catch (e) {
    logger.error("Erro ao verificar compra no Google Play:", e.message);
    throw new HttpsError("internal", "Não foi possível verificar a compra junto ao Google Play.");
  }
  // purchaseState: 0 comprado, 1 cancelado, 2 pendente (Pix/boleto).
  if (compra.purchaseState === 2) return { success: false, pendente: true };
  if (compra.purchaseState !== 0) {
    throw new HttpsError("failed-precondition", "Compra não concluída (purchaseState=" + compra.purchaseState + ").");
  }
  // A compra tem que ser desta conta (o app manda o uid como obfuscatedAccountId).
  if (compra.obfuscatedExternalAccountId && compra.obfuscatedExternalAccountId !== prestadorId) {
    throw new HttpsError("permission-denied", "Esta compra pertence a outra conta.");
  }

  // O mesmo token só concede uma vez (só depois de confirmar "comprado").
  const idempotenciaRef = getFirestore().collection("pagamentosProcessados").doc(`gp_${purchaseToken}`);
  try {
    await idempotenciaRef.create({ prestadorId, processadoEm: Date.now() });
  } catch (e) {
    if (e.code === 6) return { success: true, jaProcessado: true };
    throw e;
  }

  // CONSUMIR: o plano é comprado de novo a cada período; só reconhecido,
  // o Google dá "item já comprado" na renovação. Consumir também reconhece.
  if (compra.consumptionState !== 1) {
    try {
      await androidpublisher.purchases.products.consume({ packageName: GOOGLE_PLAY_PACKAGE_NAME, productId, token: purchaseToken });
    } catch (e) {
      logger.error("Erro ao consumir compra (plano concedido mesmo assim):", e.message);
    }
  }

  await concederAssinaturaPrestador(prestadorId, chavePlano, PLANOS[chavePlano].valor,
      { googlePlayPurchaseToken: purchaseToken, googlePlayOrderId: compra.orderId || "" });
  return { success: true };
});
