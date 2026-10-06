import { firebaseConfig } from "./firebase-config.js";
import { initializeApp } from "https://www.gstatic.com/firebasejs/10.12.5/firebase-app.js";
import {
  getAuth,
  onAuthStateChanged,
  signInWithEmailAndPassword,
  sendEmailVerification,
  sendPasswordResetEmail,
  signOut,
} from "https://www.gstatic.com/firebasejs/10.12.5/firebase-auth.js";
import {
  getFirestore,
  collection,
  getDocs,
  getDoc,
  doc,
  query,
  where,
  orderBy,
  onSnapshot,
  deleteDoc,
  updateDoc,
  serverTimestamp,
  setDoc,
  addDoc,
  writeBatch,
  increment,
} from "https://www.gstatic.com/firebasejs/10.12.5/firebase-firestore.js";
import { getFunctions, httpsCallable } from "https://www.gstatic.com/firebasejs/10.12.5/firebase-functions.js";
import {
  getStorage,
  ref,
  deleteObject,
} from "https://www.gstatic.com/firebasejs/10.12.5/firebase-storage.js";

// Painel Web SOS Estrada — mesmo formato do painel do Caronas: topo com
// foto/nome/papel de quem está logado, abas com contador e tabelas.
// Admin = e-mail validado + cadastro em admins/{uid}, feito pelo app admin
// ("Criar conta", autorizado pela senha do administrador master). Quem
// garante o acesso de verdade são as regras do Firestore (ehAdmin()).

const app = initializeApp(firebaseConfig);
const auth = getAuth(app);
// E-mails de verificação e de redefinição de senha em português
auth.languageCode = "pt-BR";
const db = getFirestore(app);
const storage = getStorage(app);
const functions = getFunctions(app);

// Silhueta cinza pra quem ainda não tem foto.
const FOTO_PADRAO = "data:image/svg+xml;utf8," + encodeURIComponent(
  '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 48 48"><rect width="48" height="48" fill="#E0E0E0"/>' +
  '<circle cx="24" cy="19" r="9" fill="#BDBDBD"/><path d="M8 44c2-10 9-14 16-14s14 4 16 14z" fill="#BDBDBD"/></svg>'
);

let chatUnsubscribe = null;

// Colaborador (admins/{uid}.role == "colaborador") só vê as abas liberadas
// pelo admin master no cadastro (permissoes). As regras do Firestore
// (podeAdmin) aplicam o mesmo — aqui é só pra esconder o que não pode.
let adminAtual = null;
function pode(secao) {
  // null = aberta pra todo admin e colaborador (Chat Admin).
  if (secao === null || !adminAtual || adminAtual.role !== "colaborador") return true;
  return !!(adminAtual.permissoes && adminAtual.permissoes[secao]);
}
// Aba "mensagens" lista as conversas a partir das solicitações.
const PERMISSAO_DA_ABA = { motoristas: "motoristas", prestadores: "prestadores", solicitacoes: "solicitacoes", mensagens: "mensagens", reclamacoes: "mensagens", chatAdmin: null, financeiro: "financeiro" };

async function dadosAdmin(user) {
  if (!user || user.isAnonymous || !user.emailVerified) return null;
  try {
    const snap = await getDoc(doc(db, "admins", user.uid));
    return snap.exists() ? snap.data() : null;
  } catch (_) {
    return null;
  }
}

// ---------- Telas ----------

function mostrarLogin() {
  pararChat();
  pararChatAdmin(true);
  adminAtual = null;
  document.getElementById("painel").style.display = "none";
  document.getElementById("login").style.display = "flex";
}

function mostrarPainel(admin) {
  document.getElementById("login").style.display = "none";
  document.getElementById("painel").style.display = "block";
  // Quem está logado: foto, nome e papel (colaborador ainda não existe no
  // SOS Estrada — quando existir, virá em admins/{uid}.role).
  document.getElementById("adminFoto").src = admin.foto ? srcFoto(admin.foto) : FOTO_PADRAO;
  document.getElementById("adminNome").textContent =
    [admin.nome, admin.sobrenome].filter(Boolean).join(" ") || admin.email || "";
  const papel = document.getElementById("adminPapel");
  const colaborador = admin.role === "colaborador";
  papel.textContent = colaborador ? "Colaborador" : "Administrador";
  papel.classList.toggle("colaborador", colaborador);
  adminAtual = admin;
  document.querySelectorAll(".aba-botao").forEach((b) => {
    b.style.display = pode(PERMISSAO_DA_ABA[b.dataset.aba]) ? "" : "none";
  });
  const primeira = Object.keys(PERMISSAO_DA_ABA).find((aba) => pode(PERMISSAO_DA_ABA[aba]));
  if (primeira) mostrarAba(primeira);
  atualizarContadores();
  iniciarBadgeChatAdmin();
}

function mostrarAba(nome) {
  if (nome !== "mensagens") pararChat();
  if (nome !== "chatAdmin") pararChatAdmin(false);
  document.querySelectorAll(".aba-botao").forEach((b) => b.classList.toggle("ativa", b.dataset.aba === nome));
  document.querySelectorAll(".aba-conteudo").forEach((c) => c.classList.remove("ativa"));
  document.getElementById("aba" + nome.charAt(0).toUpperCase() + nome.slice(1)).classList.add("ativa");
  ({ motoristas: carregarMotoristas, prestadores: carregarPrestadores, solicitacoes: carregarSolicitacoes, mensagens: carregarConversas, reclamacoes: carregarReclamacoes, chatAdmin: carregarChatAdmin, financeiro: carregarFinanceiro })[nome]();
}

document.querySelectorAll(".aba-botao").forEach((b) => b.addEventListener("click", () => mostrarAba(b.dataset.aba)));

// ---------- Termos de uso e privacidade ----------

// Mesmo aceite do app admin (ITermosRepository.kt): fica em admins/{uid}
// (termosVersao/termosAceitosEm), separado do aceite como motorista ou
// prestador da mesma conta. Sem aceitar, não entra no painel.
const VERSAO_TERMOS = "1";

function exigirTermos(user, admin) {
  if (admin.termosVersao === VERSAO_TERMOS) return mostrarPainel(admin);
  document.getElementById("login").style.display = "none";
  const modal = document.getElementById("modalTermos");
  const caixa = document.getElementById("cbAceitarTermos");
  const aceitar = document.getElementById("btnAceitarTermos");
  const erro = document.getElementById("termosErro");
  caixa.checked = false;
  aceitar.disabled = true;
  erro.textContent = "";
  modal.style.display = "flex";
  caixa.onchange = () => { aceitar.disabled = !caixa.checked; };
  aceitar.onclick = async () => {
    aceitar.disabled = true;
    try {
      await updateDoc(doc(db, "admins", user.uid), { termosVersao: VERSAO_TERMOS, termosAceitosEm: serverTimestamp() });
      modal.style.display = "none";
      mostrarPainel({ ...admin, termosVersao: VERSAO_TERMOS });
    } catch (e) {
      erro.textContent = "Não foi possível registrar o aceite: " + e.message;
      aceitar.disabled = !caixa.checked;
    }
  };
  document.getElementById("btnRecusarTermos").onclick = async () => {
    modal.style.display = "none";
    await signOut(auth);
  };
}

// ---------- Login ----------

// Durante o clique em "Entrar" quem decide é o próprio login (que ainda
// precisa da sessão aberta pra reenviar a verificação).
let entrando = false;

onAuthStateChanged(auth, async (user) => {
  if (entrando) return;
  if (!user) return mostrarLogin();
  const admin = await dadosAdmin(user);
  if (!admin) {
    await signOut(auth);
    return;
  }
  exigirTermos(user, admin);
});

document.getElementById("btnEntrar").addEventListener("click", entrar);

// Olho do campo de senha: mostra/esconde o que foi digitado (ajuda a ver se
// o navegador preencheu uma senha antiga salva).
document.getElementById("btnVerSenha").addEventListener("click", () => {
  const campo = document.getElementById("loginSenha");
  const mostrar = campo.type === "password";
  campo.type = mostrar ? "text" : "password";
  const botao = document.getElementById("btnVerSenha");
  botao.classList.toggle("ativo", mostrar);
  botao.title = mostrar ? "Esconder senha" : "Mostrar senha";
});

// Mensagem de erro com o motivo real (antes, qualquer falha virava
// "e-mail ou senha incorretos").
function mensagemErroLogin(e) {
  switch (e && e.code) {
    case "auth/invalid-credential":
    case "auth/wrong-password":
    case "auth/user-not-found":
    case "auth/invalid-email":
      return "❌ E-mail ou senha incorretos. Confira a senha no 👁 (o navegador pode ter preenchido uma senha antiga).";
    case "auth/too-many-requests":
      return "⏳ Muitas tentativas seguidas. Aguarde alguns minutos ou use \"Esqueci minha senha\".";
    case "auth/network-request-failed":
      return "📶 Sem conexão com o servidor. Verifique a internet e tente de novo.";
    case "auth/user-disabled":
      return "🚫 Esta conta foi desativada.";
    default:
      return "❌ Não foi possível entrar (" + ((e && (e.code || e.message)) || "erro desconhecido") + ").";
  }
}
document.getElementById("loginSenha").addEventListener("keydown", (e) => { if (e.key === "Enter") entrar(); });

async function entrar() {
  const email = document.getElementById("loginEmail").value.trim();
  const senha = document.getElementById("loginSenha").value;
  const erroEl = document.getElementById("loginErro");
  erroEl.textContent = "";

  if (!email || !senha) {
    erroEl.textContent = "Preencha todos os campos.";
    return;
  }
  if (senha.length < 6 || senha.length > 10) {
    erroEl.textContent = "A senha deve ter de 6 a 10 caracteres.";
    return;
  }

  entrando = true;
  try {
    const { user } = await signInWithEmailAndPassword(auth, email, senha);
    // reload(): a validação do e-mail acontece fora daqui, no link recebido.
    await user.reload();
    if (!user.emailVerified) {
      try {
        await sendEmailVerification(user);
        erroEl.textContent = "📧 Valide seu e-mail para entrar. Reenviamos o e-mail de verificação — confira também o spam.";
      } catch (_) {
        erroEl.textContent = "📧 Valide seu e-mail para entrar. Confira a caixa de entrada e o spam do e-mail já enviado.";
      }
      await signOut(auth);
      return;
    }
    const admin = await dadosAdmin(user);
    if (!admin) {
      erroEl.textContent = "❌ Esta conta não tem acesso ao painel administrativo.";
      await signOut(auth);
      return;
    }
    exigirTermos(user, admin);
  } catch (e) {
    erroEl.textContent = mensagemErroLogin(e);
  } finally {
    entrando = false;
  }
}

document.getElementById("btnEsqueciSenha").addEventListener("click", async (evento) => {
  evento.preventDefault();
  const email = document.getElementById("loginEmail").value.trim();
  const erroEl = document.getElementById("loginErro");
  if (!email) {
    erroEl.textContent = "Digite seu e-mail para redefinir a senha.";
    return;
  }
  try {
    await sendPasswordResetEmail(auth, email);
    erroEl.textContent = "📧 Enviamos um link para redefinir sua senha. Confira também o spam.";
  } catch (e) {
    erroEl.textContent = "❌ Não foi possível enviar: " + e.message;
  }
});

document.getElementById("btnSair").addEventListener("click", async () => {
  await signOut(auth);
  document.getElementById("loginEmail").value = "";
  document.getElementById("loginSenha").value = "";
});

// ---------- Contadores das abas ----------

async function atualizarContadores() {
  const contar = async (colecao, id) => {
    try {
      const snap = await getDocs(collection(db, colecao));
      document.getElementById(id).textContent = snap.size || "";
    } catch (_) { /* sem permissão ou offline: fica sem número */ }
  };
  if (pode("motoristas")) contar("motoristas", "contMotoristas");
  if (pode("prestadores")) contar("prestadores", "contPrestadores");
  if (pode("solicitacoes")) contar("solicitacoes", "contSolicitacoes");
  if (pode("mensagens")) contarReclamacoesNovas();
}

// ---------- Motoristas ----------

async function carregarMotoristas() {
  const corpo = document.getElementById("corpoMotoristas");
  const vazio = document.getElementById("vazioMotoristas");
  await carregarTabela(corpo, vazio, "motoristas", (m) => `
    <td><img class="foto-linha" src="${escapeHtml(m.foto ? srcFoto(m.foto) : FOTO_PADRAO)}" alt=""></td>
    <td><b>${escapeHtml(m.nome)}</b></td>
    <td>${escapeHtml(m.email)}</td>
    <td>${escapeHtml(m.telefone)}</td>
    <td>${escapeHtml([m.veiculo, m.cor].filter(Boolean).join(" • "))}</td>
    <td>${escapeHtml(m.placa)}</td>
    <td>${m.bloqueado ? '<span class="badge bloqueado">Bloqueado</span>' : '<span class="badge ativo">Ativo</span>'}</td>
  `, (a, b) => (a.nome || "").localeCompare(b.nome || ""));
}

// ---------- Prestadores ----------

// Foto nova vem como JPEG em base64 (o projeto não tem Storage); a antiga, como link.
function srcFoto(foto) {
  return String(foto).startsWith("http") ? foto : "data:image/jpeg;base64," + foto;
}

async function carregarPrestadores() {
  const corpo = document.getElementById("corpoPrestadores");
  const vazio = document.getElementById("vazioPrestadores");
  await carregarTabela(corpo, vazio, "prestadores", (p) => {
    const cidade = [p.cidade, p.estado].filter(Boolean).join(" - ") || p.endereco || p.localizacao || "—";
    const situacao = p.bloqueado ? '<span class="badge bloqueado">Bloqueado</span>'
      : p.ativo === false ? '<span class="badge cancelado">Inativo</span>'
      : '<span class="badge ativo">Ativo</span>';
    return `
      <td><img class="foto-linha" src="${escapeHtml(p.logo ? srcFoto(p.logo) : FOTO_PADRAO)}" alt=""></td>
      <td><b>${escapeHtml(p.nome)}</b><br><span style="color:#888">${escapeHtml(p.email)}</span></td>
      <td>${escapeHtml(p.servico)}</td>
      <td>${escapeHtml(p.telefone)}</td>
      <td>${escapeHtml(cidade)}</td>
      <td>${escapeHtml(p.cnpj)}</td>
      <td>${escapeHtml(p.preco)}</td>
      <td>${situacao}</td>
    `;
  }, (a, b) => (a.nome || "").localeCompare(b.nome || ""));
}

// ---------- Solicitações ----------

const NOME_STATUS = { pendente: "Pendente", aceito: "Aceita", recusado: "Recusada", cancelado: "Cancelada" };

function formatarDataHora(timestamp) {
  if (!timestamp || !timestamp.toDate) return "—";
  const d = timestamp.toDate();
  const pad = (n) => String(n).padStart(2, "0");
  return `${pad(d.getDate())}/${pad(d.getMonth() + 1)}/${d.getFullYear()} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

async function carregarSolicitacoes() {
  const corpo = document.getElementById("corpoSolicitacoes");
  const vazio = document.getElementById("vazioSolicitacoes");
  await carregarTabela(corpo, vazio, "solicitacoes", (s, id) => `
    <td>${formatarDataHora(s.timestamp)}</td>
    <td><b>${escapeHtml(s.motoristaNome)}</b><br><span style="color:#888">${escapeHtml(s.motoristaTelefone)}</span></td>
    <td>${escapeHtml([s.motoristaVeiculo, s.motoristaPlaca].filter(Boolean).join(" • "))}</td>
    <td>${escapeHtml(s.prestadorNome)}</td>
    <td>${escapeHtml(s.enderecoMotorista || "—")}</td>
    <td><span class="badge ${escapeHtml(s.status)}">${escapeHtml(NOME_STATUS[s.status] || s.status)}</span></td>
    <td>${pode("mensagens") ? `<button class="btn-acao" data-chat="${escapeHtml(id)}">💬 Mensagens</button>` : ""}</td>
  `, (a, b) => tempo(b.timestamp) - tempo(a.timestamp));

  corpo.querySelectorAll("[data-chat]").forEach((botao) => botao.addEventListener("click", () => {
    const id = botao.dataset.chat;
    mostrarAba("mensagens");
    // Espera a lista de conversas montar pra marcar a certa.
    setTimeout(() => document.querySelector(`.conversa[data-id="${CSS.escape(id)}"]`)?.click(), 400);
  }));
}

function tempo(timestamp) {
  return timestamp && timestamp.toMillis ? timestamp.toMillis() : 0;
}

// ---------- Mensagens: lista de conversas + chat na mesma aba ----------

async function carregarConversas() {
  const lista = document.getElementById("listaConversas");
  const vazio = document.getElementById("vazioConversas");
  lista.innerHTML = '<div class="carregando">Carregando…</div>';
  vazio.style.display = "none";
  try {
    const snap = await getDocs(collection(db, "solicitacoes"));
    const docs = snap.docs.sort((a, b) => tempo(b.data().timestamp) - tempo(a.data().timestamp));
    lista.innerHTML = "";
    if (docs.length === 0) {
      vazio.style.display = "block";
      return;
    }
    docs.forEach((docSnap) => {
      const s = docSnap.data();
      const item = document.createElement("div");
      item.className = "conversa";
      item.dataset.id = docSnap.id;
      item.innerHTML = `
        <div class="partes">🚗 ${escapeHtml(s.motoristaNome)} ↔ 🔧 ${escapeHtml(s.prestadorNome)}</div>
        <div class="detalhe">${formatarDataHora(s.timestamp)} · <span class="badge ${escapeHtml(s.status)}">${escapeHtml(NOME_STATUS[s.status] || s.status)}</span></div>
      `;
      item.addEventListener("click", () => {
        lista.querySelectorAll(".conversa").forEach((c) => c.classList.remove("ativa"));
        item.classList.add("ativa");
        abrirChat(docSnap.id, `${s.motoristaNome} ↔ ${s.prestadorNome}`);
      });
      lista.appendChild(item);
    });
  } catch (e) {
    lista.innerHTML = "";
    vazio.textContent = "Erro ao carregar conversas: " + e.message;
    vazio.style.display = "block";
  }
}

const SEIS_MESES_EM_MS = 1000 * 60 * 60 * 24 * 30 * 6;

async function limparMensagensAntigas(solicitacaoId) {
  const limite = new Date(Date.now() - SEIS_MESES_EM_MS);
  const mensagensRef = collection(db, "solicitacoes", solicitacaoId, "mensagens");
  const snap = await getDocs(query(mensagensRef, where("timestamp", "<", limite)));
  for (const docSnap of snap.docs) {
    const data = docSnap.data();
    if (data.imagemUrl) {
      try { await deleteObject(ref(storage, data.imagemUrl)); } catch (_) { /* já removida */ }
    }
    await deleteDoc(docSnap.ref);
  }
}

function pararChat() {
  if (chatUnsubscribe) {
    chatUnsubscribe();
    chatUnsubscribe = null;
  }
}

function abrirChat(solicitacaoId, titulo) {
  pararChat();
  document.getElementById("chatTitulo").textContent = "💬 " + titulo;
  const mensagensEl = document.getElementById("chatMensagens");
  mensagensEl.innerHTML = '<div class="carregando">Carregando…</div>';

  limparMensagensAntigas(solicitacaoId).catch(() => {});

  const q = query(collection(db, "solicitacoes", solicitacaoId, "mensagens"), orderBy("timestamp", "asc"));
  chatUnsubscribe = onSnapshot(q, (snap) => {
    mensagensEl.innerHTML = "";
    if (snap.empty) {
      mensagensEl.innerHTML = '<div class="vazio">Nenhuma mensagem nesta conversa.</div>';
      return;
    }
    snap.forEach((docSnap) => {
      const m = docSnap.data();
      const doPrestador = m.remetenteTipo === "prestador";
      const bolha = document.createElement("div");
      bolha.className = "bolha " + (doPrestador ? "bolha-prestador" : "bolha-motorista");
      const hora = m.timestamp && m.timestamp.toDate
        ? m.timestamp.toDate().toLocaleString("pt-BR", { day: "2-digit", month: "2-digit", hour: "2-digit", minute: "2-digit" })
        : "";
      bolha.innerHTML = `
        <div class="remetente">${doPrestador ? "Prestador" : "Motorista"}</div>
        ${m.imagemUrl ? `<img src="${escapeHtml(m.imagemUrl)}" alt="">` : ""}
        ${m.texto ? `<div>${escapeHtml(m.texto)}</div>` : ""}
        <div class="hora">${hora}${m.lida ? " ✓✓" : " ✓"}</div>
      `;
      mensagensEl.appendChild(bolha);
    });
    mensagensEl.scrollTop = mensagensEl.scrollHeight;
  });
}

// ---------- Financeiro (mesmo do painel do Caronas) ----------
// Assinaturas dos prestadores (coleção pagamentos, gravada pelo webhook do
// Mercado Pago — paymentWebhookPrestador). Datas em milissegundos.

let pagamentosCache = [];
const MESES = ["Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho", "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro"];

function formatarReal(v) {
  return "R$ " + (v || 0).toFixed(2).replace(".", ",");
}

function formatarData(ms) {
  return ms ? new Intl.DateTimeFormat("pt-BR", { day: "2-digit", month: "2-digit", year: "numeric" }).format(new Date(ms)) : "-";
}

document.getElementById("selectPeriodoFinanceiro").addEventListener("change", () => {
  const personalizado = document.getElementById("selectPeriodoFinanceiro").value === "personalizado";
  document.getElementById("datasPersonalizadasFinanceiro").style.display = personalizado ? "inline-flex" : "none";
});
document.getElementById("btnFiltrarFinanceiro").addEventListener("click", aplicarFiltroFinanceiro);
document.getElementById("btnRelatorioFinanceiro").addEventListener("click", abrirModalRelatorio);
document.getElementById("btnCancelarRelatorio").addEventListener("click", () => {
  document.getElementById("modalRelatorio").style.display = "none";
});
document.getElementById("btnGerarRelatorio").addEventListener("click", () => {
  document.getElementById("modalRelatorio").style.display = "none";
  gerarRelatorioMensal(
    parseInt(document.getElementById("mesRelatorio").value, 10),
    parseInt(document.getElementById("anoRelatorio").value, 10)
  );
});

async function carregarFinanceiro() {
  const corpo = document.getElementById("corpoFinanceiro");
  const vazio = document.getElementById("vazioFinanceiro");
  corpo.innerHTML = '<tr><td colspan="6" class="carregando">Carregando…</td></tr>';
  vazio.style.display = "none";
  try {
    const snap = await getDocs(collection(db, "pagamentos"));
    pagamentosCache = snap.docs.map((d) => ({ id: d.id, ...d.data() }));
    aplicarFiltroFinanceiro();
  } catch (e) {
    corpo.innerHTML = "";
    vazio.textContent = "Erro ao carregar o financeiro: " + e.message;
    vazio.style.display = "block";
  }
}

function aplicarFiltroFinanceiro() {
  const periodo = document.getElementById("selectPeriodoFinanceiro").value;
  const agora = Date.now();
  let inicio = 0;
  let fim = Infinity;
  if (periodo === "personalizado") {
    const de = document.getElementById("dataInicialFinanceiro").value;
    const ate = document.getElementById("dataFinalFinanceiro").value;
    if (!de || !ate) {
      alert("Preencha as duas datas.");
      return;
    }
    inicio = new Date(de + "T00:00:00").getTime();
    fim = new Date(ate + "T23:59:59").getTime();
    if (inicio > fim) {
      alert("A data inicial é depois da final.");
      return;
    }
  } else {
    const dias = { diario: 1, semanal: 7, mensal: 30, trimestral: 90, semestral: 180, anual: 365 }[periodo];
    if (dias) inicio = agora - dias * 24 * 60 * 60 * 1000;
  }

  const filtrados = pagamentosCache
    .filter((p) => p.dataCompra && p.dataCompra >= inicio && p.dataCompra <= fim)
    .sort((a, b) => b.dataCompra - a.dataCompra);

  document.getElementById("statTotalPagamentos").textContent = String(filtrados.length);
  document.getElementById("statPrestadoresPagantes").textContent = String(new Set(filtrados.map((p) => p.prestadorId).filter(Boolean)).size);
  document.getElementById("statTotalArrecadado").textContent = formatarReal(filtrados.reduce((t, p) => t + (p.valor || 0), 0));

  const corpo = document.getElementById("corpoFinanceiro");
  const vazio = document.getElementById("vazioFinanceiro");
  vazio.textContent = "Nenhum pagamento encontrado nesse período.";
  vazio.style.display = filtrados.length ? "none" : "block";
  corpo.innerHTML = filtrados.map((p) => {
    const ativo = p.expiraEm && p.expiraEm > agora;
    return `<tr>
      <td><b>${escapeHtml(p.prestadorNome || "Prestador")}</b></td>
      <td>${escapeHtml(p.prestadorEmail || "-")}</td>
      <td>${formatarReal(p.valor)}</td>
      <td>${formatarData(p.dataCompra)}</td>
      <td>${formatarData(p.expiraEm)}</td>
      <td><span class="badge ${ativo ? "ativo" : "recusado"}">${ativo ? "Ativo" : "Expirado"}</span></td>
    </tr>`;
  }).join("");
}

function abrirModalRelatorio() {
  const hoje = new Date();
  document.getElementById("mesRelatorio").innerHTML = MESES
    .map((m, i) => `<option value="${i}" ${i === hoje.getMonth() ? "selected" : ""}>${m}</option>`).join("");
  document.getElementById("anoRelatorio").innerHTML = Array.from({ length: 6 }, (_, i) => hoje.getFullYear() - i)
    .map((a) => `<option value="${a}">${a}</option>`).join("");
  document.getElementById("modalRelatorio").style.display = "flex";
}

// Relatório mensal: página imprimível numa aba nova ("Salvar como PDF" na
// impressão do navegador) — mesmo recurso do painel do Caronas.
function gerarRelatorioMensal(mes, ano) {
  const inicio = new Date(ano, mes, 1).getTime();
  const fim = new Date(ano, mes + 1, 1).getTime();
  const linhas = pagamentosCache
    .filter((p) => p.dataCompra && p.dataCompra >= inicio && p.dataCompra < fim && (p.valor || 0) > 0)
    .sort((a, b) => a.dataCompra - b.dataCompra);
  if (linhas.length === 0) {
    alert(`Nenhum pagamento em ${MESES[mes]}/${ano}.`);
    return;
  }
  const porValor = {};
  linhas.forEach((l) => { porValor[l.valor] = (porValor[l.valor] || 0) + 1; });
  const total = linhas.reduce((t, l) => t + l.valor, 0);
  const periodo = `${MESES[mes]}/${ano}`;
  const html = `<!DOCTYPE html><html lang="pt-BR"><head><meta charset="UTF-8">
    <title>Relatório Financeiro — ${periodo}</title>
    <style>
      body { font-family: Arial, sans-serif; padding: 30px; color: #222; }
      h1 { color: #B71C1C; font-size: 20px; }
      h2 { color: #B71C1C; font-size: 14px; margin-top: 24px; border-bottom: 2px solid #B71C1C; padding-bottom: 4px; }
      p.sub { color: #666; font-size: 12px; }
      table { width: 100%; border-collapse: collapse; margin-top: 10px; font-size: 12px; }
      th, td { text-align: left; padding: 6px 8px; border-bottom: 1px solid #eee; }
      th { color: #666; font-size: 10px; text-transform: uppercase; }
      .total { text-align: right; font-weight: 700; color: #B71C1C; font-size: 14px; margin-top: 10px; }
    </style></head><body>
    <h1>Relatório Financeiro — Assinaturas de Prestadores</h1>
    <p class="sub">SOS Estrada · Período: ${periodo}</p>
    <h2>Pagamentos do período</h2>
    <table><thead><tr><th>Prestador</th><th>E-mail</th><th>Valor</th><th>Data</th><th>Vale até</th></tr></thead><tbody>
    ${linhas.map((l) => `<tr><td>${escapeHtml(l.prestadorNome || "Prestador")}</td><td>${escapeHtml(l.prestadorEmail || "-")}</td><td>${formatarReal(l.valor)}</td><td>${formatarData(l.dataCompra)}</td><td>${formatarData(l.expiraEm)}</td></tr>`).join("")}
    </tbody></table>
    <h2>Resumo por valor</h2>
    <table><thead><tr><th>Valor</th><th>Qtd</th><th>Total</th></tr></thead><tbody>
    ${Object.entries(porValor).map(([v, q]) => `<tr><td>${formatarReal(parseFloat(v))}</td><td>${q}</td><td>${formatarReal(parseFloat(v) * q)}</td></tr>`).join("")}
    </tbody></table>
    <p class="total">Total geral: ${formatarReal(total)}</p>
    <p class="sub" style="margin-top:30px;">Gerado em ${new Intl.DateTimeFormat("pt-BR", { dateStyle: "short", timeStyle: "short" }).format(new Date())}</p>
    </body></html>`;
  const aba = window.open("", "_blank");
  if (!aba) {
    alert("O navegador bloqueou a nova aba do relatório. Permita pop-ups para este site e tente de novo.");
    return;
  }
  aba.document.write(html);
  aba.document.close();
  setTimeout(() => aba.print(), 300);
}

// ---------- 📝 Reclamações (mesmo chip do app admin) ----------

const NOME_TIPO_MANIFESTACAO = { reclamacao: "😠 Reclamação", sugestao: "💡 Sugestão", denuncia: "🚩 Denúncia" };
const NOME_STATUS_MANIFESTACAO = { nova: "Nova", respondida: "Respondida", arquivada: "Arquivada" };

async function contarReclamacoesNovas() {
  try {
    const snap = await getDocs(query(collection(db, "manifestacoes"), where("status", "==", "nova")));
    document.getElementById("contReclamacoes").textContent = snap.size || "";
  } catch (_) { /* sem permissão: fica sem número */ }
}

async function carregarReclamacoes() {
  const corpo = document.getElementById("corpoReclamacoes");
  const vazio = document.getElementById("vazioReclamacoes");
  await carregarTabela(corpo, vazio, "manifestacoes", (m, id) => {
    const texto = m.tipo === "denuncia"
      ? `Denunciado: ${m.denunciadoNome || "—"}\nMotivo: ${m.motivo || "—"}\n\n${m.mensagem || ""}`
      : (m.mensagem || "(sem texto)");
    const resposta = m.resposta ? `<br><span style="color:#2E7D32">Resposta: ${escapeHtml(m.resposta)}</span>` : "";
    const arquivada = m.status === "arquivada";
    return `
      <td>${formatarDataHora(m.criadoEm)}</td>
      <td>${escapeHtml(NOME_TIPO_MANIFESTACAO[m.tipo] || "📝 Mensagem")}</td>
      <td><b>${escapeHtml(m.nome)}</b><br><span style="color:#888">${escapeHtml(m.email)}</span></td>
      <td class="msg-reclamacao">${escapeHtml(texto)}${resposta}</td>
      <td><span class="badge ${escapeHtml(m.status)}">${escapeHtml(NOME_STATUS_MANIFESTACAO[m.status] || m.status)}</span></td>
      <td>
        <button class="btn-acao" data-responder="${escapeHtml(id)}" data-email="${escapeHtml(m.email)}">✉️ Responder</button>
        <button class="btn-acao" style="background:#546E7A" data-arquivar="${escapeHtml(id)}" data-arquivada="${arquivada}">${arquivada ? "Desarquivar" : "Arquivar"}</button>
      </td>`;
  }, (a, b) => tempo(b.criadoEm) - tempo(a.criadoEm));

  corpo.querySelectorAll("[data-responder]").forEach((b) => b.addEventListener("click", async () => {
    const resposta = prompt("Resposta (vai por e-mail para " + (b.dataset.email || "quem enviou") + "):");
    if (!resposta || !resposta.trim()) return;
    try {
      const r = await httpsCallable(functions, "responderManifestacao")({ id: b.dataset.responder, resposta: resposta.trim() });
      alert(r.data && r.data.enviadoPorEmail ? "Resposta enviada por e-mail." : "Resposta gravada, mas o e-mail do servidor não está configurado.");
      carregarReclamacoes();
      contarReclamacoesNovas();
    } catch (e) {
      alert("Não foi possível responder: " + e.message);
    }
  }));
  corpo.querySelectorAll("[data-arquivar]").forEach((b) => b.addEventListener("click", async () => {
    try {
      await updateDoc(doc(db, "manifestacoes", b.dataset.arquivar), { status: b.dataset.arquivada === "true" ? "nova" : "arquivada" });
      carregarReclamacoes();
      contarReclamacoesNovas();
    } catch (e) {
      alert("Não foi possível alterar: " + e.message);
    }
  }));
}

// ---------- 💬 Chat Admin (igual ao do Caronas e ao app admin) ----------
// Conversas 1-a-1 entre admins e colaboradores em conversasAdmin, com o
// conteúdo cifrado (AES-256-GCM) com a MESMA chave do app
// (ChatAdminCryptoUtil.kt): a conversa continua de um lado pro outro.

const CHAT_ADMIN_CHAVE_BASE64 = "Ugj2YVu36MvXnPYbylsDwXzTKyXakyl+XR2iUqmFuS0=";
const CHAT_ADMIN_PREFIXO = "ENC1:";
let chaveChatAdminPromise = null;

function base64ParaBytes(b64) {
  const bin = atob(b64);
  const bytes = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
  return bytes;
}

function bytesParaBase64(bytes) {
  let bin = "";
  bytes.forEach((b) => { bin += String.fromCharCode(b); });
  return btoa(bin);
}

function chaveChatAdmin() {
  if (!chaveChatAdminPromise) {
    chaveChatAdminPromise = crypto.subtle.importKey("raw", base64ParaBytes(CHAT_ADMIN_CHAVE_BASE64), "AES-GCM", false, ["encrypt", "decrypt"]);
  }
  return chaveChatAdminPromise;
}

async function cifrarChatAdmin(texto) {
  if (!texto) return texto;
  try {
    const iv = crypto.getRandomValues(new Uint8Array(12));
    const cifrado = new Uint8Array(await crypto.subtle.encrypt({ name: "AES-GCM", iv, tagLength: 128 }, await chaveChatAdmin(), new TextEncoder().encode(texto)));
    const junto = new Uint8Array(iv.length + cifrado.length);
    junto.set(iv, 0);
    junto.set(cifrado, iv.length);
    return CHAT_ADMIN_PREFIXO + bytesParaBase64(junto);
  } catch (e) {
    console.error("Erro ao cifrar mensagem do Chat Admin", e);
    return texto;
  }
}

async function decifrarChatAdmin(armazenado) {
  if (!armazenado || !armazenado.startsWith(CHAT_ADMIN_PREFIXO)) return armazenado;
  try {
    const dados = base64ParaBytes(armazenado.slice(CHAT_ADMIN_PREFIXO.length));
    const texto = await crypto.subtle.decrypt({ name: "AES-GCM", iv: dados.slice(0, 12), tagLength: 128 }, await chaveChatAdmin(), dados.slice(12));
    return new TextDecoder().decode(texto);
  } catch (e) {
    console.error("Erro ao decifrar mensagem do Chat Admin", e);
    return armazenado;
  }
}

// Mesmo id do app: os dois uids em ordem, separados por "_".
function idConversaAdminEntre(a, b) {
  return [a, b].sort().join("_");
}

function nomeAdmin(a) {
  return [a.nome, a.sobrenome].filter(Boolean).join(" ").trim() || a.email || "Administrador";
}

let conversasAdminCache = [];
let contatosAdmin = [];
let conversaAdminAtual = null;
let unsubConversasAdmin = null;
let unsubMensagensAdmin = null;
let unsubBadgeChatAdmin = null;

// tudo=true também desliga o contador da aba (logout).
function pararChatAdmin(tudo) {
  if (unsubConversasAdmin) { unsubConversasAdmin(); unsubConversasAdmin = null; }
  if (unsubMensagensAdmin) { unsubMensagensAdmin(); unsubMensagensAdmin = null; }
  conversaAdminAtual = null;
  if (tudo && unsubBadgeChatAdmin) { unsubBadgeChatAdmin(); unsubBadgeChatAdmin = null; }
}

function naoLidasParaMim(c) {
  const eu = auth.currentUser && auth.currentUser.uid;
  return (c.admin1Id === eu ? c.naoLidas1 : c.naoLidas2) || 0;
}

// Número de não lidas na aba, em qualquer aba do painel.
function iniciarBadgeChatAdmin() {
  if (unsubBadgeChatAdmin || !auth.currentUser) return;
  unsubBadgeChatAdmin = onSnapshot(
    query(collection(db, "conversasAdmin"), where("participantes", "array-contains", auth.currentUser.uid)),
    (snap) => {
      const total = snap.docs.reduce((s, d) => s + naoLidasParaMim(d.data()), 0);
      document.getElementById("contChatAdmin").textContent = total || "";
    },
    () => {},
  );
}

async function carregarContatosAdmin() {
  const snap = await getDocs(collection(db, "admins"));
  contatosAdmin = snap.docs
    .filter((d) => d.id !== auth.currentUser.uid)
    .map((d) => ({ id: d.id, ...d.data() }))
    .sort((a, b) => nomeAdmin(a).localeCompare(nomeAdmin(b)));
}

async function carregarChatAdmin() {
  pararChatAdmin(false);
  document.getElementById("chatAdminTitulo").textContent = "Escolha uma conversa ao lado";
  document.getElementById("chatAdminMensagens").innerHTML = '<div class="vazio">As mensagens aparecem aqui.</div>';
  document.getElementById("chatAdminEnvio").style.display = "none";
  document.getElementById("novaConversaAdminBox").style.display = "none";
  const lista = document.getElementById("listaChatAdmin");
  lista.innerHTML = '<div class="carregando">Carregando…</div>';
  try { await carregarContatosAdmin(); } catch (_) { contatosAdmin = []; }
  const eu = auth.currentUser.uid;
  unsubConversasAdmin = onSnapshot(
    query(collection(db, "conversasAdmin"), where("participantes", "array-contains", eu), orderBy("ultimoTimestamp", "desc")),
    (snap) => {
      conversasAdminCache = snap.docs.map((d) => ({ id: d.id, ...d.data() }));
      desenharConversasAdmin();
    },
    (e) => { lista.innerHTML = `<div class="vazio">Erro ao carregar: ${escapeHtml(e.message)}</div>`; },
  );
}

function fotoContato(id) {
  const c = contatosAdmin.find((a) => a.id === id);
  return c && c.foto ? srcFoto(c.foto) : FOTO_PADRAO;
}

function desenharConversasAdmin() {
  const lista = document.getElementById("listaChatAdmin");
  const eu = auth.currentUser.uid;
  if (conversasAdminCache.length === 0) {
    lista.innerHTML = '<div class="vazio">Nenhuma conversa ainda.<br>Clique em "+ Nova conversa".</div>';
    return;
  }
  lista.innerHTML = "";
  conversasAdminCache.forEach((c) => {
    const outroId = c.admin1Id === eu ? c.admin2Id : c.admin1Id;
    const outroNome = (c.admin1Id === eu ? c.admin2Nome : c.admin1Nome) || "Administrador";
    const naoLidas = naoLidasParaMim(c);
    const previa = c.ultimaMensagem ? (c.ultimoRemetenteId === eu ? "Você: " : "") + c.ultimaMensagem : "Nenhuma mensagem ainda";
    const item = document.createElement("div");
    item.className = "conversa" + (conversaAdminAtual && conversaAdminAtual.id === c.id ? " ativa" : "");
    item.innerHTML = `
      <div class="conversa-admin">
        <img src="${escapeHtml(fotoContato(outroId))}" alt="">
        <div class="meio">
          <div class="partes">${escapeHtml(outroNome)}</div>
          <div class="previa">${escapeHtml(previa)}</div>
        </div>
        ${naoLidas > 0 ? `<span class="nao-lidas">${naoLidas}</span>` : ""}
        <span class="excluir" title="Excluir conversa">🗑️</span>
      </div>`;
    item.addEventListener("click", (ev) => {
      if (ev.target.classList.contains("excluir")) return excluirConversaAdmin(c, outroNome);
      abrirConversaAdmin(c);
    });
    lista.appendChild(item);
  });
}

document.getElementById("btnNovaConversaAdmin").addEventListener("click", () => {
  const box = document.getElementById("novaConversaAdminBox");
  const abrir = box.style.display === "none";
  box.style.display = abrir ? "flex" : "none";
  if (!abrir) return;
  const select = document.getElementById("selectContatoAdmin");
  select.innerHTML = contatosAdmin.length
    ? contatosAdmin.map((a) => `<option value="${escapeHtml(a.id)}">${a.role === "colaborador" ? "🤝" : "🛡️"} ${escapeHtml(nomeAdmin(a))} (${escapeHtml(a.email || "")})</option>`).join("")
    : '<option value="">Nenhum outro administrador ou colaborador</option>';
});

document.getElementById("btnIniciarConversaAdmin").addEventListener("click", async () => {
  const outroId = document.getElementById("selectContatoAdmin").value;
  if (!outroId) return;
  const outro = contatosAdmin.find((a) => a.id === outroId);
  const eu = auth.currentUser.uid;
  const id = idConversaAdminEntre(eu, outroId);
  const ref = doc(db, "conversasAdmin", id);
  try {
    const existente = await getDoc(ref);
    let conversa;
    if (existente.exists()) {
      conversa = { id, ...existente.data() };
    } else {
      const meuNome = nomeAdmin(adminAtual || {});
      const souAdmin1 = [eu, outroId].sort()[0] === eu;
      conversa = {
        participantes: [eu, outroId],
        admin1Id: souAdmin1 ? eu : outroId,
        admin1Nome: souAdmin1 ? meuNome : nomeAdmin(outro),
        admin2Id: souAdmin1 ? outroId : eu,
        admin2Nome: souAdmin1 ? nomeAdmin(outro) : meuNome,
        ultimaMensagem: "",
        ultimoTimestamp: serverTimestamp(),
        naoLidas1: 0,
        naoLidas2: 0,
      };
      await setDoc(ref, conversa);
      conversa = { id, ...conversa };
    }
    document.getElementById("novaConversaAdminBox").style.display = "none";
    abrirConversaAdmin(conversa);
  } catch (e) {
    alert("Erro ao iniciar a conversa: " + e.message);
  }
});

function abrirConversaAdmin(c) {
  if (unsubMensagensAdmin) { unsubMensagensAdmin(); unsubMensagensAdmin = null; }
  conversaAdminAtual = c;
  desenharConversasAdmin();
  const eu = auth.currentUser.uid;
  const outroId = c.admin1Id === eu ? c.admin2Id : c.admin1Id;
  const outroNome = (c.admin1Id === eu ? c.admin2Nome : c.admin1Nome) || "Administrador";
  document.getElementById("chatAdminTitulo").innerHTML =
    `<img src="${escapeHtml(fotoContato(outroId))}" alt="" style="width:26px;height:26px;border-radius:50%;object-fit:cover;vertical-align:middle;margin-right:8px;">${escapeHtml(outroNome)}`;
  document.getElementById("chatAdminEnvio").style.display = "flex";
  const caixa = document.getElementById("chatAdminMensagens");
  caixa.innerHTML = '<div class="carregando">Carregando…</div>';
  unsubMensagensAdmin = onSnapshot(
    query(collection(db, "conversasAdmin", c.id, "mensagens"), orderBy("timestamp", "asc")),
    async (snap) => {
      const mensagens = snap.docs.map((d) => ({ id: d.id, ...d.data() }));
      await desenharMensagensAdmin(mensagens);
      marcarConversaAdminComoLida(c.id, mensagens);
    },
    (e) => { caixa.innerHTML = `<div class="vazio">Erro: ${escapeHtml(e.message)}</div>`; },
  );
}

async function desenharMensagensAdmin(mensagens) {
  const caixa = document.getElementById("chatAdminMensagens");
  const eu = auth.currentUser.uid;
  if (mensagens.length === 0) {
    caixa.innerHTML = '<div class="vazio">Nenhuma mensagem ainda. Envie a primeira!</div>';
    return;
  }
  const partes = await Promise.all(mensagens.map(async (m) => {
    const minha = m.remetenteId === eu;
    const apagadaPraMim = (minha && m.deletadoParaRemetente) || (!minha && m.deletadoParaDestinatario);
    const texto = m.deletadoParaTodos ? "⚠️ Mensagem apagada"
      : apagadaPraMim ? (minha ? "Você apagou esta mensagem" : "Mensagem apagada")
        : await decifrarChatAdmin(m.conteudo);
    const hora = m.timestamp && m.timestamp.toDate
      ? m.timestamp.toDate().toLocaleString("pt-BR", { day: "2-digit", month: "2-digit", hour: "2-digit", minute: "2-digit" })
      : "";
    const podeApagar = !m.deletadoParaTodos && !apagadaPraMim;
    return `<div class="bolha ${minha ? "bolha-minha" : "bolha-outro"}${podeApagar ? "" : " apagada"}">
      ${minha ? "" : `<div class="remetente">${escapeHtml(m.remetenteNome || "Administrador")}</div>`}
      <div style="white-space:pre-wrap">${escapeHtml(texto)}</div>
      <div class="hora">${hora}${minha && podeApagar ? (m.lida ? " ✓✓" : " ✓") : ""}${podeApagar ? `<span class="apagar" data-msg="${escapeHtml(m.id)}" data-minha="${minha}" title="Apagar">🗑️</span>` : ""}</div>
    </div>`;
  }));
  caixa.innerHTML = partes.join("");
  caixa.querySelectorAll("[data-msg]").forEach((el) => el.addEventListener("click", () => apagarMensagemAdmin(el.dataset.msg, el.dataset.minha === "true")));
  caixa.scrollTop = caixa.scrollHeight;
}

async function marcarConversaAdminComoLida(conversaId, mensagens) {
  const eu = auth.currentUser.uid;
  const c = conversaAdminAtual;
  if (!c || c.id !== conversaId) return;
  const pendentes = mensagens.filter((m) => m.destinatarioId === eu && !m.lida);
  const campo = c.admin1Id === eu ? "naoLidas1" : "naoLidas2";
  try {
    if (pendentes.length) {
      const lote = writeBatch(db);
      pendentes.forEach((m) => lote.update(doc(db, "conversasAdmin", conversaId, "mensagens", m.id), { lida: true }));
      await lote.commit();
    }
    if (naoLidasParaMim(conversasAdminCache.find((x) => x.id === conversaId) || c) > 0) {
      await updateDoc(doc(db, "conversasAdmin", conversaId), { [campo]: 0 });
    }
  } catch (_) { /* tenta de novo na próxima mudança */ }
}

async function enviarMensagemAdmin() {
  const input = document.getElementById("chatAdminInput");
  const c = conversaAdminAtual;
  const texto = input.value.trim();
  if (!c || !texto) return;
  input.value = "";
  const eu = auth.currentUser.uid;
  const souAdmin1 = c.admin1Id === eu;
  try {
    await addDoc(collection(db, "conversasAdmin", c.id, "mensagens"), {
      remetenteId: eu,
      remetenteNome: nomeAdmin(adminAtual || {}),
      destinatarioId: souAdmin1 ? c.admin2Id : c.admin1Id,
      destinatarioNome: souAdmin1 ? c.admin2Nome : c.admin1Nome,
      conteudo: await cifrarChatAdmin(texto),
      tipo: "texto",
      timestamp: serverTimestamp(),
      lida: false,
      deletadoParaTodos: false,
      deletadoParaRemetente: false,
      deletadoParaDestinatario: false,
    });
    await updateDoc(doc(db, "conversasAdmin", c.id), {
      ultimaMensagem: texto.slice(0, 120),
      ultimoTimestamp: serverTimestamp(),
      ultimoRemetenteId: eu,
      [souAdmin1 ? "naoLidas2" : "naoLidas1"]: increment(1),
    });
  } catch (e) {
    input.value = texto;
    alert("Erro ao enviar: " + e.message);
  }
}

document.getElementById("btnEnviarChatAdmin").addEventListener("click", enviarMensagemAdmin);
document.getElementById("chatAdminInput").addEventListener("keydown", (e) => { if (e.key === "Enter") enviarMensagemAdmin(); });

async function apagarMensagemAdmin(msgId, minha) {
  const c = conversaAdminAtual;
  if (!c) return;
  const ref = doc(db, "conversasAdmin", c.id, "mensagens", msgId);
  try {
    if (minha && confirm("Apagar para TODOS? (Cancelar = apagar só para você)")) {
      await updateDoc(ref, { deletadoParaTodos: true, conteudo: await cifrarChatAdmin("Mensagem apagada"), tipo: "deletada" });
    } else if (minha || confirm("Apagar esta mensagem só para você?")) {
      await updateDoc(ref, { [minha ? "deletadoParaRemetente" : "deletadoParaDestinatario"]: true });
    }
  } catch (e) {
    alert("Erro ao apagar: " + e.message);
  }
}

async function excluirConversaAdmin(c, nome) {
  if (!confirm(`Excluir toda a conversa com ${nome}? As mensagens somem para os dois lados, sem volta.`)) return;
  try {
    const snap = await getDocs(collection(db, "conversasAdmin", c.id, "mensagens"));
    for (let i = 0; i < snap.docs.length; i += 400) {
      const lote = writeBatch(db);
      snap.docs.slice(i, i + 400).forEach((d) => lote.delete(d.ref));
      await lote.commit();
    }
    await deleteDoc(doc(db, "conversasAdmin", c.id));
    if (conversaAdminAtual && conversaAdminAtual.id === c.id) carregarChatAdmin();
  } catch (e) {
    alert("Erro ao excluir: " + e.message);
  }
}

// ---------- Util ----------

// Carrega uma coleção inteira numa tabela: linha(dados, id) devolve os <td>.
async function carregarTabela(corpo, vazio, colecao, linha, ordenar) {
  corpo.innerHTML = '<tr><td colspan="9" class="carregando">Carregando…</td></tr>';
  vazio.style.display = "none";
  try {
    const snap = await getDocs(collection(db, colecao));
    const itens = snap.docs.map((d) => ({ id: d.id, dados: d.data() }));
    if (ordenar) itens.sort((a, b) => ordenar(a.dados, b.dados));
    corpo.innerHTML = "";
    if (itens.length === 0) {
      vazio.style.display = "block";
      return;
    }
    itens.forEach(({ id, dados }) => {
      const tr = document.createElement("tr");
      tr.innerHTML = linha(dados, id);
      corpo.appendChild(tr);
    });
  } catch (e) {
    corpo.innerHTML = "";
    vazio.textContent = "Erro ao carregar: " + e.message;
    vazio.style.display = "block";
  }
}

function escapeHtml(valor) {
  if (valor === undefined || valor === null) return "";
  const div = document.createElement("div");
  div.textContent = String(valor);
  return div.innerHTML;
}
