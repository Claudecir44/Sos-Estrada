# Publica firestore.rules no projeto sos-estrada-dc55d com o hash da senha
# master no lugar do marcador __HASH_SENHA_MASTER__ (ver comentário no bloco
# de admins do firestore.rules). A senha é digitada aqui, sem aparecer na
# tela, e o hash nunca é gravado no git: o arquivo volta ao original no fim.
#
# Uso (na pasta do projeto):
#   powershell -ExecutionPolicy Bypass -File tools\publicar-regras.ps1

$ErrorActionPreference = "Stop"
$raiz = Split-Path -Parent $PSScriptRoot
$regras = Join-Path $raiz "firestore.rules"
$original = [IO.File]::ReadAllText($regras)

if (-not $original.Contains("__HASH_SENHA_MASTER__")) {
    throw "firestore.rules não tem o marcador __HASH_SENHA_MASTER__ (já foi trocado?). Rode 'git checkout -- firestore.rules' e tente de novo."
}

$segura = Read-Host "Senha master" -AsSecureString
$bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($segura)
try {
    $senha = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr)
} finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)
}
if ([string]::IsNullOrEmpty($senha)) { throw "Senha vazia." }

# Mesmo formato que as regras comparam: SHA-256 em hex MAIÚSCULO.
$sha = [Security.Cryptography.SHA256]::Create()
$hash = -join ($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($senha)) | ForEach-Object { $_.ToString("X2") })
$senha = $null

try {
    [IO.File]::WriteAllText($regras, $original.Replace("__HASH_SENHA_MASTER__", $hash))
    Push-Location $raiz
    npx --yes firebase-tools deploy --only firestore:rules --project sos-estrada-dc55d
} finally {
    Pop-Location
    [IO.File]::WriteAllText($regras, $original)
    Write-Host "firestore.rules restaurado (sem o hash)."
}
