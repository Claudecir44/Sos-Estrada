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
} from "https://www.gstatic.com/firebasejs/10.12.5/firebase-firestore.js";
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
  if (!adminAtual || adminAtual.role !== "colaborador") return true;
  return !!(adminAtual.permissoes && adminAtual.permissoes[secao]);
}
// Aba "mensagens" lista as conversas a partir das solicitações.
const PERMISSAO_DA_ABA = { motoristas: "motoristas", prestadores: "prestadores", solicitacoes: "solicitacoes", mensagens: "mensagens" };

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
}

function mostrarAba(nome) {
  if (nome !== "mensagens") pararChat();
  document.querySelectorAll(".aba-botao").forEach((b) => b.classList.toggle("ativa", b.dataset.aba === nome));
  document.querySelectorAll(".aba-conteudo").forEach((c) => c.classList.remove("ativa"));
  document.getElementById("aba" + nome.charAt(0).toUpperCase() + nome.slice(1)).classList.add("ativa");
  ({ motoristas: carregarMotoristas, prestadores: carregarPrestadores, solicitacoes: carregarSolicitacoes, mensagens: carregarConversas })[nome]();
}

document.querySelectorAll(".aba-botao").forEach((b) => b.addEventListener("click", () => mostrarAba(b.dataset.aba)));

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
  mostrarPainel(admin);
});

document.getElementById("btnEntrar").addEventListener("click", entrar);
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
    mostrarPainel(admin);
  } catch (e) {
    erroEl.textContent = "❌ E-mail ou senha incorretos.";
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
      : p.ativo === false ? '<span class="badge cancelado">Fora da busca</span>'
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
