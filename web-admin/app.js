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

// Mesma lista de ehAdmin() em firestore.rules e do AdminRepository.EMAILS_ADMIN
// no app — mudar nos três juntos. A conta é criada pelo app admin ("Criar conta").
const EMAILS_ADMIN = ["claudecirwitkoski@gmail.com"];

function ehAdmin(user) {
  return !!user && user.emailVerified && EMAILS_ADMIN.includes((user.email || "").toLowerCase());
}

const app = initializeApp(firebaseConfig);
const auth = getAuth(app);
const db = getFirestore(app);
const storage = getStorage(app);

let chatUnsubscribe = null;

function mostrarView(id) {
  document.querySelectorAll(".view").forEach((v) => v.classList.add("hidden"));
  document.getElementById(id).classList.remove("hidden");
  if (id !== "view-chat" && chatUnsubscribe) {
    chatUnsubscribe();
    chatUnsubscribe = null;
  }
}

document.querySelectorAll("[data-voltar]").forEach((el) => {
  el.addEventListener("click", () => mostrarView(el.dataset.voltar));
});

// ---------- Login ----------

// Só abre o painel pra admin com e-mail validado (sessões anônimas das
// versões antigas caem no login).
onAuthStateChanged(auth, (user) => {
  if (user && !ehAdmin(user)) {
    signOut(auth);
    return;
  }
  mostrarView(user ? "view-panel" : "view-login");
});

document.getElementById("btnEntrar").addEventListener("click", async () => {
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
    if (!ehAdmin(user)) {
      erroEl.textContent = "❌ Esta conta não tem acesso ao painel administrativo.";
      await signOut(auth);
    }
  } catch (e) {
    erroEl.textContent = "❌ E-mail ou senha incorretos.";
  }
});

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
});

// ---------- Navegação do painel ----------

document.getElementById("btnMotoristas").addEventListener("click", () => {
  mostrarView("view-motoristas");
  carregarMotoristas();
});

document.getElementById("btnPrestadores").addEventListener("click", () => {
  mostrarView("view-prestadores");
  carregarPrestadores();
});

document.getElementById("btnVerSolicitacoes").addEventListener("click", () => {
  mostrarView("view-solicitacoes");
  carregarSolicitacoes();
});

document.getElementById("btnVerMensagens").addEventListener("click", () => {
  mostrarView("view-mensagens");
  carregarConversas();
});

document.getElementById("btnVoltarChat").addEventListener("click", () => {
  mostrarView("view-mensagens");
});

// ---------- Motoristas ----------

async function carregarMotoristas() {
  const loading = document.getElementById("motoristasLoading");
  const empty = document.getElementById("motoristasEmpty");
  const lista = document.getElementById("motoristasLista");
  loading.classList.remove("hidden");
  empty.classList.add("hidden");
  lista.innerHTML = "";

  try {
    const snap = await getDocs(collection(db, "motoristas"));
    loading.classList.add("hidden");
    if (snap.empty) {
      empty.classList.remove("hidden");
      return;
    }
    snap.forEach((docSnap) => {
      const m = docSnap.data();
      const card = document.createElement("div");
      card.className = "card";
      card.innerHTML = `
        <p class="nome">${escapeHtml(m.nome)}</p>
        <p>Telefone: ${escapeHtml(m.telefone)}</p>
        <p>E-mail: ${escapeHtml(m.email)}</p>
        <p>Veículo: ${escapeHtml(m.veiculo)}</p>
        <p>Placa: ${escapeHtml(m.placa)}</p>
        <p>Cor: ${escapeHtml(m.cor)}</p>
        <p>ID: ${escapeHtml(m.uid)}</p>
      `;
      lista.appendChild(card);
    });
  } catch (e) {
    loading.classList.add("hidden");
    empty.textContent = "Erro ao carregar motoristas: " + e.message;
    empty.classList.remove("hidden");
  }
}

// ---------- Prestadores ----------

function enderecoCompletoPrestador(p) {
  let sb = "";
  if (p.rua) sb += p.rua;
  if (p.numero) sb += ", " + p.numero;
  if (p.bairro) sb += " - " + p.bairro;
  if (p.cidade) sb += ", " + p.cidade;
  if (p.estado) sb += " - " + p.estado;
  if (p.pais) sb += ", " + p.pais;
  if (p.complemento) sb += " (" + p.complemento + ")";

  if (sb.length === 0) {
    if (p.endereco) return p.endereco;
    if (p.localizacao) return p.localizacao;
    return "Endereço não informado";
  }
  return sb;
}

async function carregarPrestadores() {
  const loading = document.getElementById("prestadoresLoading");
  const empty = document.getElementById("prestadoresEmpty");
  const lista = document.getElementById("prestadoresLista");
  loading.classList.remove("hidden");
  empty.classList.add("hidden");
  lista.innerHTML = "";

  try {
    const snap = await getDocs(collection(db, "prestadores"));
    loading.classList.add("hidden");
    if (snap.empty) {
      empty.classList.remove("hidden");
      return;
    }
    snap.forEach((docSnap) => {
      const p = docSnap.data();
      const localizacao =
        p.cidade || p.estado
          ? `${p.cidade || ""}${p.estado ? " - " + p.estado : ""}`
          : "Não informada";

      const card = document.createElement("div");
      card.className = "card";
      card.innerHTML = `
        ${p.logo ? `<img class="logo" src="${escapeHtml(p.logo)}" />` : ""}
        <p class="nome">${escapeHtml(p.nome)}</p>
        <p>CNPJ: ${escapeHtml(p.cnpj)}</p>
        <p>Telefone: ${escapeHtml(p.telefone)}</p>
        <p>E-mail: ${escapeHtml(p.email)}</p>
        <p>Serviço: ${escapeHtml(p.servico)}</p>
        <p>Endereço: ${escapeHtml(enderecoCompletoPrestador(p))}</p>
        <p>Localização: ${escapeHtml(localizacao)}</p>
        <p>Preço: ${escapeHtml(p.preco)}</p>
        <p>ID: ${escapeHtml(p.uid)}</p>
      `;
      lista.appendChild(card);
    });
  } catch (e) {
    loading.classList.add("hidden");
    empty.textContent = "Erro ao carregar prestadores: " + e.message;
    empty.classList.remove("hidden");
  }
}

// ---------- Solicitações ----------

function statusClasse(status) {
  switch (status) {
    case "pendente": return "status-pendente";
    case "aceito": return "status-aceito";
    case "recusado": return "status-recusado";
    case "cancelado": return "status-cancelado";
    default: return "";
  }
}

function formatarDataHora(timestamp) {
  if (!timestamp || !timestamp.toDate) return "não informada";
  const d = timestamp.toDate();
  const pad = (n) => String(n).padStart(2, "0");
  return `${pad(d.getDate())}/${pad(d.getMonth() + 1)}/${d.getFullYear()} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

async function carregarSolicitacoes() {
  const loading = document.getElementById("solicitacoesLoading");
  const empty = document.getElementById("solicitacoesEmpty");
  const lista = document.getElementById("solicitacoesLista");
  loading.classList.remove("hidden");
  empty.classList.add("hidden");
  lista.innerHTML = "";

  try {
    const snap = await getDocs(collection(db, "solicitacoes"));
    loading.classList.add("hidden");
    if (snap.empty) {
      empty.classList.remove("hidden");
      return;
    }
    snap.forEach((docSnap) => {
      const s = docSnap.data();
      const id = docSnap.id;
      const card = document.createElement("div");
      card.className = "card";
      card.innerHTML = `
        <p class="nome">Motorista: ${escapeHtml(s.motoristaNome)}</p>
        <p>📞 ${escapeHtml(s.motoristaTelefone)}</p>
        <p>🚗 ${escapeHtml(s.motoristaVeiculo)}</p>
        <p>🔢 ${escapeHtml(s.motoristaPlaca)}</p>
        <p>📍 ${escapeHtml(s.enderecoMotorista)}</p>
        <p class="nome">Prestador: ${escapeHtml(s.prestadorNome)}</p>
        <p class="${statusClasse(s.status)}">Status: ${escapeHtml(s.status)}</p>
        <p>Data/Hora: ${formatarDataHora(s.timestamp)}</p>
        <button class="btn btn-purple btn-ver-mensagens">💬 Ver mensagens</button>
      `;
      card.querySelector(".btn-ver-mensagens").addEventListener("click", () => {
        abrirChat(id, `${s.motoristaNome} ↔ ${s.prestadorNome}`);
      });
      lista.appendChild(card);
    });
  } catch (e) {
    loading.classList.add("hidden");
    empty.textContent = "Erro ao carregar solicitações: " + e.message;
    empty.classList.remove("hidden");
  }
}

// ---------- Conversas (Ver Mensagens) ----------

async function carregarConversas() {
  const loading = document.getElementById("mensagensLoading");
  const empty = document.getElementById("mensagensEmpty");
  const lista = document.getElementById("mensagensLista");
  loading.classList.remove("hidden");
  empty.classList.add("hidden");
  lista.innerHTML = "";

  try {
    const snap = await getDocs(collection(db, "solicitacoes"));
    loading.classList.add("hidden");
    if (snap.empty) {
      empty.classList.remove("hidden");
      return;
    }
    snap.forEach((docSnap) => {
      const s = docSnap.data();
      const id = docSnap.id;
      const card = document.createElement("div");
      card.className = "card card-clickable";
      card.innerHTML = `
        <p class="nome">🚗 Motorista: ${escapeHtml(s.motoristaNome)}</p>
        <p class="nome">🔧 Prestador: ${escapeHtml(s.prestadorNome)}</p>
        <p class="${statusClasse(s.status)}">Status: ${escapeHtml(s.status)}</p>
        <p>Data/Hora: ${formatarDataHora(s.timestamp)}</p>
      `;
      card.addEventListener("click", () => {
        abrirChat(id, `${s.motoristaNome} ↔ ${s.prestadorNome}`);
      });
      lista.appendChild(card);
    });
  } catch (e) {
    loading.classList.add("hidden");
    empty.textContent = "Erro ao carregar conversas: " + e.message;
    empty.classList.remove("hidden");
  }
}

// ---------- Chat (somente leitura) ----------

const SEIS_MESES_EM_MS = 1000 * 60 * 60 * 24 * 30 * 6;

async function limparMensagensAntigas(solicitacaoId) {
  const limite = new Date(Date.now() - SEIS_MESES_EM_MS);
  const mensagensRef = collection(db, "solicitacoes", solicitacaoId, "mensagens");
  const q = query(mensagensRef, where("timestamp", "<", limite));
  const snap = await getDocs(q);
  for (const docSnap of snap.docs) {
    const data = docSnap.data();
    if (data.imagemUrl) {
      try {
        await deleteObject(ref(storage, data.imagemUrl));
      } catch (e) {
        // imagem já pode ter sido removida
      }
    }
    await deleteDoc(docSnap.ref);
  }
}

function abrirChat(solicitacaoId, titulo) {
  mostrarView("view-chat");
  document.getElementById("chatTitulo").textContent = titulo;
  const mensagensEl = document.getElementById("chatMensagens");
  mensagensEl.innerHTML = "";

  limparMensagensAntigas(solicitacaoId);

  const mensagensRef = collection(db, "solicitacoes", solicitacaoId, "mensagens");
  const q = query(mensagensRef, orderBy("timestamp", "asc"));

  chatUnsubscribe = onSnapshot(q, (snap) => {
    mensagensEl.innerHTML = "";
    if (snap.empty) {
      mensagensEl.innerHTML = '<div class="chat-empty">Nenhuma mensagem ainda.</div>';
      return;
    }
    snap.forEach((docSnap) => {
      const m = docSnap.data();
      const bolha = document.createElement("div");
      const tipo = m.remetenteTipo === "prestador" ? "bolha-enviada" : "bolha-recebida";
      bolha.className = "bolha " + tipo;

      let html = "";
      if (tipo === "bolha-recebida") {
        html += `<div class="remetente">${m.remetenteTipo === "motorista" ? "Motorista" : "Prestador"}</div>`;
      }
      if (m.imagemUrl) {
        html += `<img src="${escapeHtml(m.imagemUrl)}" />`;
      }
      if (m.texto) {
        html += `<div>${escapeHtml(m.texto)}</div>`;
      }
      const hora = m.timestamp && m.timestamp.toDate
        ? m.timestamp.toDate().toLocaleTimeString("pt-BR", { hour: "2-digit", minute: "2-digit" })
        : "";
      html += `<div class="hora">${hora}</div>`;

      bolha.innerHTML = html;
      mensagensEl.appendChild(bolha);
    });
    mensagensEl.scrollTop = mensagensEl.scrollHeight;
  });
}

// ---------- Util ----------

function escapeHtml(valor) {
  if (valor === undefined || valor === null) return "";
  const div = document.createElement("div");
  div.textContent = String(valor);
  return div.innerHTML;
}
