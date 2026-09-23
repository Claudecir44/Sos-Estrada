// Carrega o access token do Mercado Pago de .env (não versionado) — ver
// MERCADOPAGO_ACCESS_TOKEN mais abaixo.
require("dotenv").config();

const { onSchedule } = require("firebase-functions/v2/scheduler");
const { onCall, onRequest, HttpsError } = require("firebase-functions/v2/https");
const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore, Timestamp, FieldValue } = require("firebase-admin/firestore");
const { getStorage } = require("firebase-admin/storage");
const { getAuth } = require("firebase-admin/auth");
const mercadopago = require("mercadopago");
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

async function isAdmin(uid) {
  const doc = await getFirestore().collection("admins").doc(uid).get();
  return doc.exists;
}

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
// Assinatura de prestadores (Mercado Pago) — 180 dias grátis a partir do
// cadastro, depois um único plano anual (R$49,90 / 365 dias) pra manter o
// cadastro visível na busca do motorista (SocorroActivity filtra por
// "ativo"==true). Espelha o desenho do Match (createPaymentPreference/
// paymentWebhook/checkPaymentStatus), simplificado: aqui é só um plano, e
// vencer sem pagar apenas desativa a listagem — nunca apaga a conta.
// ============================================================

const TRIAL_DIAS = 180;
const DIA_MS = 1000 * 60 * 60 * 24;
const PLANO_ANUAL = { valor: 49.90, diasValidade: 365 };

// Campos que só esta Cloud Function (Admin SDK, ignora firestore.rules)
// pode escrever — o cliente nunca grava "ativo" nem os campos de
// assinatura diretamente (ver firestore.rules, camposDoServidorPrestador).
exports.aoRegistrarPrestador = onDocumentCreated("prestadores/{uid}", async (event) => {
  const snap = event.data;
  if (!snap) return;
  const dados = snap.data();
  // Idempotência básica: se por algum motivo o documento já nasceu com
  // esses campos (não deveria, as regras bloqueiam), não sobrescreve.
  if (dados.dataCadastro && dados.assinaturaStatus) return;

  await snap.ref.update({
    dataCadastro: dados.dataCadastro || Timestamp.now(),
    ativo: true,
    assinaturaStatus: "trial",
    assinaturaExpiraEm: null,
    assinaturaUltimoPagamento: null,
  });
  logger.info(`Trial de ${TRIAL_DIAS} dias iniciado para prestador ${event.params.uid}`);
});

// Backfill pra prestadores cadastrados ANTES do módulo de assinatura
// existir — sem os campos novos, o filtro whereEqualTo("ativo", true) do
// SocorroActivity os deixaria invisíveis pra sempre. dataCadastro real
// desses cadastros antigos não é conhecida, então usa a data de hoje como
// início do trial (mais generoso que assumir que o trial já venceu).
// Idempotente e de baixo risco (só preenche o que está faltando, nunca
// sobrescreve) — por isso só exige estar autenticado, sem checagem de
// admin: rodar de novo por engano não tem efeito colateral.
exports.migrarAssinaturaPrestadores = onCall(async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Usuário não autenticado.");
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

  try {
    const preference = {
      items: [{
        id: "plano_anual_prestador",
        title: "Plano Anual - SOS Estrada Prestador",
        description: "Assinatura anual para manter o cadastro ativo no SOS Estrada",
        quantity: 1,
        currency_id: "BRL",
        unit_price: PLANO_ANUAL.valor,
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
        diasValidade: PLANO_ANUAL.diasValidade,
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

      const diasValidade = (payment.metadata && payment.metadata.diasValidade) || PLANO_ANUAL.diasValidade;
      const agora = Date.now();
      const expiraEm = agora + diasValidade * DIA_MS;

      await db.collection("prestadores").doc(prestadorId).update({
        ativo: true,
        assinaturaStatus: "ativa",
        assinaturaExpiraEm: Timestamp.fromMillis(expiraEm),
        assinaturaUltimoPagamento: Timestamp.fromMillis(agora),
      });

      logger.info(`Assinatura ativada para prestador ${prestadorId}, expira em ${new Date(expiraEm).toISOString()}`);

      try {
        const prestadorDoc = await db.collection("prestadores").doc(prestadorId).get();
        const prestadorData = prestadorDoc.exists ? prestadorDoc.data() : {};
        await db.collection("pagamentos").add({
          prestadorId,
          prestadorNome: prestadorData.nome || "",
          prestadorEmail: prestadorData.email || "",
          valor: payment.transaction_amount || 0,
          diasValidade,
          dataCompra: agora,
          expiraEm,
          mercadoPagoPaymentId: payment.id,
        });
      } catch (histError) {
        logger.error("Erro ao registrar histórico de pagamento:", histError);
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

// Roda todo dia: desativa (não apaga) prestadores cujo trial de 180 dias
// venceu sem assinatura, ou cuja assinatura anual venceu sem renovação.
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
      await doc.ref.update({ ativo: false, assinaturaStatus: "expirada" });
      desativados++;
      continue;
    }

    // status "trial" (ou ausente, cadastro pré-migração já com ativo=true)
    const dataCadastro = dados.dataCadastro ? dados.dataCadastro.toMillis() : null;
    if (!dataCadastro) continue; // sem dado confiável, não mexe
    const diasDesdeCadastro = Math.floor((agora - dataCadastro) / DIA_MS);
    if (diasDesdeCadastro < TRIAL_DIAS) continue; // ainda no período grátis

    await doc.ref.update({ ativo: false, assinaturaStatus: "expirada" });
    desativados++;
  }

  logger.info(`expirarAssinaturasPrestadores: ${desativados} prestador(es) desativado(s).`);
});
