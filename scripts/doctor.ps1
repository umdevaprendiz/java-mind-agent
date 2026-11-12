<#
.SYNOPSIS
  Valida o ambiente local do Java Mind Agent.

.DESCRIPTION
  Confere JDK, Docker, banco com pgvector, Ollama e memória RAM.
  Termina com código 1 se algum item estiver em ERRO (avisos não falham).

.EXAMPLE
  .\scripts\doctor.ps1

.EXAMPLE
  .\scripts\doctor.ps1 -MinFreeRamGb 8
#>
[CmdletBinding()]
param(
    # RAM livre abaixo disso gera aviso (modelos de 7B+ precisam de folga).
    [int] $MinFreeRamGb = 6
)

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$root = Split-Path -Parent $PSScriptRoot
$script:erros = 0
$script:avisos = 0

function Write-Check {
    param(
        [Parameter(Mandatory)] [ValidateSet('OK', 'AVISO', 'ERRO')] [string] $Status,
        [Parameter(Mandatory)] [string] $Mensagem,
        [string] $Dica
    )
    $cor = @{ OK = 'Green'; AVISO = 'Yellow'; ERRO = 'Red' }[$Status]
    Write-Host ('[{0,-5}] ' -f $Status) -ForegroundColor $cor -NoNewline
    Write-Host $Mensagem
    if ($Dica -and $Status -ne 'OK') {
        Write-Host ('        -> ' + $Dica) -ForegroundColor DarkGray
    }
    if ($Status -eq 'ERRO') { $script:erros++ }
    elseif ($Status -eq 'AVISO') { $script:avisos++ }
}

# Executa um programa nativo sem que o stderr vire exceção (Windows PowerShell 5.1).
function Invoke-Native {
    param(
        [Parameter(Mandatory)] [string] $File,
        [string[]] $Arguments = @()
    )
    $anterior = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $saida = & $File @Arguments 2>&1 | ForEach-Object { $_.ToString() }
        return [pscustomobject]@{ Code = $LASTEXITCODE; Output = (($saida -join "`n").Trim()) }
    }
    catch {
        return [pscustomobject]@{ Code = -1; Output = $_.Exception.Message }
    }
    finally {
        $ErrorActionPreference = $anterior
    }
}

function Read-DotEnv {
    param([string] $Path)
    $mapa = @{}
    foreach ($linha in Get-Content -LiteralPath $Path -Encoding UTF8) {
        if ($linha -match '^\s*#' -or $linha -notmatch '=') { continue }
        $par = $linha -split '=', 2
        $mapa[$par[0].Trim()] = $par[1].Trim().Trim('"').Trim("'")
    }
    return $mapa
}

Write-Host ''
Write-Host 'Java Mind Agent - diagnóstico do ambiente' -ForegroundColor Cyan
Write-Host ''

# --- JDK -------------------------------------------------------------------
$javaExigido = 27
$pom = Join-Path $root 'pom.xml'
if (Test-Path -LiteralPath $pom) {
    $m = [regex]::Match((Get-Content -LiteralPath $pom -Raw), '<java\.version>(\d+)</java\.version>')
    if ($m.Success) { $javaExigido = [int] $m.Groups[1].Value }
}
$java = Invoke-Native -File 'java' -Arguments @('-version')
if ($java.Code -ne 0) {
    Write-Check ERRO 'JDK não encontrado no PATH.' "Instale o JDK $javaExigido e confira o PATH."
}
else {
    $v = [regex]::Match($java.Output, 'version "(\d+)(?:\.(\d+))?')
    $maior = [int] $v.Groups[1].Value
    if ($maior -eq 1) { $maior = [int] $v.Groups[2].Value }
    if ($maior -ge $javaExigido) { Write-Check OK "JDK $maior (o projeto exige $javaExigido)." }
    else { Write-Check ERRO "JDK $maior encontrado, mas o projeto exige $javaExigido." "Instale o JDK $javaExigido." }
}

# --- Docker ----------------------------------------------------------------
$dockerOk = $false
$docker = Invoke-Native -File 'docker' -Arguments @('info', '--format', '{{.ServerVersion}}')
if ($docker.Code -eq 0 -and $docker.Output) {
    Write-Check OK "Docker em execução (engine $($docker.Output))."
    $dockerOk = $true
}
else {
    Write-Check ERRO 'Docker não está acessível.' 'Abra o Docker Desktop e aguarde o engine iniciar.'
}

# --- Banco (Postgres + pgvector) ---------------------------------------------
$envFile = Join-Path $root '.env'
$cfg = @{}
if (Test-Path -LiteralPath $envFile) {
    $cfg = Read-DotEnv -Path $envFile
    Write-Check OK 'Arquivo .env encontrado.'
}
else {
    Write-Check ERRO 'Arquivo .env não encontrado.' 'Copie .env.example para .env e defina POSTGRES_PASSWORD.'
}
$dbUser = if ($cfg['POSTGRES_USER']) { $cfg['POSTGRES_USER'] } else { 'javamind' }
$dbName = if ($cfg['POSTGRES_DB']) { $cfg['POSTGRES_DB'] } else { 'javamind' }

if ($dockerOk -and (Test-Path -LiteralPath $envFile)) {
    Push-Location $root
    try {
        $id = Invoke-Native -File 'docker' -Arguments @('compose', 'ps', '-q', 'postgres')
        if ($id.Code -ne 0 -or -not $id.Output) {
            Write-Check ERRO 'Serviço postgres não está de pé.' 'Rode: docker compose up -d --wait'
        }
        else {
            $fmt = '{{.State.Status}}|{{if .State.Health}}{{.State.Health.Status}}{{else}}sem-healthcheck{{end}}'
            $st = Invoke-Native -File 'docker' -Arguments @('inspect', '-f', $fmt, $id.Output)
            $partes = $st.Output -split '\|'
            if ($partes[0] -eq 'running' -and $partes[1] -eq 'healthy') {
                $porta = Invoke-Native -File 'docker' -Arguments @('compose', 'port', 'postgres', '5432')
                Write-Check OK "Postgres saudável (host $($porta.Output))."

                $ext = Invoke-Native -File 'docker' -Arguments @(
                    'compose', 'exec', '-T', 'postgres', 'psql', '-U', $dbUser, '-d', $dbName, '-tAc',
                    "select extversion from pg_extension where extname='vector'")
                if ($ext.Code -eq 0 -and $ext.Output -match '^\d') {
                    Write-Check OK "Extensão pgvector ativa (versão $($ext.Output))."
                }
                else {
                    Write-Check ERRO 'Extensão pgvector não está ativa no banco.' `
                        'O script de init só roda com o volume vazio: docker compose down -v ; docker compose up -d --wait (apaga os dados locais).'
                }
            }
            else {
                Write-Check ERRO "Postgres não está pronto (estado: $($partes[0]), saúde: $($partes[1]))." 'Veja: docker compose logs postgres'
            }
        }
    }
    finally {
        Pop-Location
    }
}

# --- Ollama ------------------------------------------------------------------
$ollamaBase = if ($env:OLLAMA_BASE_URL) { $env:OLLAMA_BASE_URL.TrimEnd('/') } else { 'http://localhost:11434' }
try {
    $ver = Invoke-RestMethod -Uri "$ollamaBase/api/version" -TimeoutSec 3
    Write-Check OK "Ollama respondendo em $ollamaBase (versão $($ver.version))."
    try {
        $tags = Invoke-RestMethod -Uri "$ollamaBase/api/tags" -TimeoutSec 5
        $modelos = @($tags.models)
        if ($modelos.Count -eq 0) {
            Write-Check AVISO 'Nenhum modelo baixado no Ollama.' 'Os modelos dos tiers serão baixados pela F07 (ou: ollama pull <modelo>).'
        }
        else {
            $lista = ($modelos | ForEach-Object { '{0} ({1:N1} GB)' -f $_.name, ($_.size / 1GB) }) -join ', '
            Write-Check OK "Modelos instalados: $lista"
        }
    }
    catch {
        Write-Check AVISO 'Não foi possível listar os modelos do Ollama.' $_.Exception.Message
    }
}
catch {
    Write-Check ERRO "Ollama não respondeu em $ollamaBase." 'Inicie o app do Ollama ou rode "ollama serve".'
}

# --- Memória e GPU -----------------------------------------------------------
try {
    $os = Get-CimInstance -ClassName Win32_OperatingSystem
    $totalGb = $os.TotalVisibleMemorySize / 1MB
    $livreGb = $os.FreePhysicalMemory / 1MB
    $msg = 'RAM: {0:N1} GB total, {1:N1} GB livre.' -f $totalGb, $livreGb
    if ($livreGb -ge $MinFreeRamGb) { Write-Check OK $msg }
    else { Write-Check AVISO $msg "Menos de $MinFreeRamGb GB livres: feche programas antes de usar modelos médios/fortes." }

    $gpus = @(Get-CimInstance -ClassName Win32_VideoController | ForEach-Object { $_.Name })
    if ($gpus -match 'NVIDIA') { Write-Check OK ('GPU: ' + ($gpus -join ', ')) }
    else { Write-Check AVISO ('GPU sem CUDA detectada (' + ($gpus -join ', ') + '): a inferência roda na CPU.') 'Esperado nesta máquina; modelos grandes ficam lentos.' }
}
catch {
    Write-Check AVISO 'Não foi possível ler RAM/GPU.' $_.Exception.Message
}

# --- Node (necessário só a partir da F32) --------------------------------------
$node = Invoke-Native -File 'node' -Arguments @('--version')
if ($node.Code -eq 0 -and $node.Output -match '^v(\d+)') {
    if ([int] $Matches[1] -ge 20) { Write-Check OK "Node $($node.Output) (front)." }
    else { Write-Check AVISO "Node $($node.Output) é antigo para o front." 'Use Node 20 ou superior.' }
}
else {
    Write-Check AVISO 'Node não encontrado (só é preciso a partir do front, F32).' 'Instale o Node 20 ou superior.'
}

# --- Resultado -----------------------------------------------------------------
Write-Host ''
if ($script:erros -gt 0) {
    Write-Host ("Resultado: {0} erro(s), {1} aviso(s)." -f $script:erros, $script:avisos) -ForegroundColor Red
    exit 1
}
Write-Host ("Resultado: tudo OK ({0} aviso(s))." -f $script:avisos) -ForegroundColor Green
exit 0
