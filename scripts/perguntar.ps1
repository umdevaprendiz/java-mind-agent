<#
.SYNOPSIS
  Faz uma pergunta ao agente e mostra a resposta chegando, com ferramentas e metricas.

.DESCRIPTION
  Chama POST /internal/agent/run (so existe no perfil dev, F15). O app precisa estar rodando
  (.\mvnw spring-boot:run) e o Ollama ligado. Ctrl+C fecha a conexao e o agente para na hora.

.EXAMPLE
  .\scripts\perguntar.ps1 "O que é um record em Java?" -Tier low

.EXAMPLE
  .\scripts\perguntar.ps1 "O que mudou no Java 17?" -Tier medium -JavaVersion 17 -MaxTokens 600

.EXAMPLE
  .\scripts\perguntar.ps1 "Explique streams" -Tier low -Raw
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory, Position = 0)] [string] $Pergunta,
    # Nivel do modelo; sem ele vale o padrao (medium).
    [ValidateSet('low', 'medium', 'strong', 'extra')] [string] $Tier,
    # Versao do Java fixada para esta pergunta.
    [int] $JavaVersion,
    [ValidateSet('pt-BR', 'en')] [string] $Idioma,
    # Limites desta pergunta (o resto vem de app.agent.limits).
    [int] $MaxSteps,
    [int] $MaxTokens,
    [string] $Url = 'http://localhost:8080',
    # Mostra os eventos SSE crus em vez do resumo legivel.
    [switch] $Raw
)

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
Add-Type -AssemblyName System.Net.Http

$corpo = @{ message = $Pergunta }
if ($Tier) { $corpo.tier = $Tier }
if ($JavaVersion) { $corpo.javaVersion = $JavaVersion }
if ($Idioma) { $corpo.language = $Idioma }
if ($MaxSteps) { $corpo.maxSteps = $MaxSteps }
if ($MaxTokens) { $corpo.maxTokens = $MaxTokens }

$cliente = [System.Net.Http.HttpClient]::new()
$cliente.Timeout = [TimeSpan]::FromMinutes(30)
$leitor = $null
try {
    $requisicao = [System.Net.Http.HttpRequestMessage]::new([System.Net.Http.HttpMethod]::Post, "$Url/internal/agent/run")
    # StringContent com UTF-8 de verdade: o curl do Windows manda acentos em outra codificacao e o servidor recusa.
    $requisicao.Content = [System.Net.Http.StringContent]::new(($corpo | ConvertTo-Json -Compress), [System.Text.Encoding]::UTF8, 'application/json')
    $requisicao.Headers.Accept.ParseAdd('text/event-stream')

    try {
        $resposta = $cliente.SendAsync($requisicao, [System.Net.Http.HttpCompletionOption]::ResponseHeadersRead).GetAwaiter().GetResult()
    }
    catch {
        Write-Host "Nao consegui falar com $Url. O app esta rodando? ($($_.Exception.InnerException.Message))" -ForegroundColor Red
        exit 1
    }
    $fluxo = $resposta.Content.ReadAsStreamAsync().GetAwaiter().GetResult()
    $leitor = [System.IO.StreamReader]::new($fluxo, [System.Text.Encoding]::UTF8)

    if (-not $resposta.IsSuccessStatusCode) {
        $problema = $leitor.ReadToEnd() | ConvertFrom-Json
        Write-Host ("Erro {0} ({1}): {2}" -f [int]$resposta.StatusCode, $problema.code, $problema.detail) -ForegroundColor Red
        if ($problema.suggestedTier) { Write-Host "  Tente -Tier $($problema.suggestedTier)" -ForegroundColor Yellow }
        exit 1
    }

    $inicio = Get-Date
    $emTexto = $false
    while ($null -ne ($linha = $leitor.ReadLine())) {
        if ($Raw) { Write-Host $linha; continue }
        if (-not $linha.StartsWith('data:')) { continue }
        $e = $linha.Substring(5) | ConvertFrom-Json

        if ($e.type -ne 'token' -and $e.type -ne 'thinking' -and $emTexto) { Write-Host ''; $emTexto = $false }
        switch ($e.type) {
            'route' {
                $ferramentas = if ($e.toolsEnabled) { 'com ferramentas' } else { 'sem ferramentas' }
                Write-Host ("[{0} · {1} · Java {2} ({3}) · {4}]" -f $e.tier, $e.model, $e.javaVersion, $e.javaVersionSource, $ferramentas) -ForegroundColor DarkGray
            }
            'status' { Write-Host ("... " + $e.message) -ForegroundColor DarkGray }
            'thinking' { Write-Host $e.text -NoNewline -ForegroundColor DarkGray; $emTexto = $true }
            'token' { Write-Host $e.text -NoNewline; $emTexto = $true }
            'tool_call' { Write-Host ("> {0} {1}" -f $e.tool, ($e.arguments | ConvertTo-Json -Compress)) -ForegroundColor Yellow }
            'tool_result' {
                $cor = if ($e.ok) { 'Yellow' } else { 'Red' }
                $estado = if ($e.ok) { 'ok' } else { 'ERRO' }
                Write-Host ("< {0} {1} ({2} ms): {3}" -f $e.tool, $estado, $e.durationMs, $e.summary) -ForegroundColor $cor
            }
            'usage' {
                $velocidade = if ($null -ne $e.tokensPerSecond) { ('{0:N1} tok/s' -f $e.tokensPerSecond) } else { '? tok/s' }
                Write-Host ("[{0} tokens lidos, {1} gerados, {2}, 1o token em {3} ms, total {4:N1} s]" -f $e.promptTokens, $e.completionTokens, $velocidade, $e.timeToFirstTokenMs, ($e.totalDurationMs / 1000)) -ForegroundColor DarkGray
            }
            'done' { Write-Host ("[fim: {0}]" -f $e.finishReason) -ForegroundColor DarkGray }
            'summary' {
                $limite = if ($e.limit) { " · limite atingido: $($e.limit)" } else { '' }
                Write-Host ("[{0} passo(s), {1} ferramenta(s){2}]" -f $e.steps, $e.toolCalls, $limite) -ForegroundColor DarkGray
            }
            'error' { Write-Host ("ERRO {0}: {1}" -f $e.code, $e.message) -ForegroundColor Red }
        }
    }
    if ($emTexto) { Write-Host '' }
}
finally {
    if ($leitor) { $leitor.Dispose() }
    $cliente.Dispose()
}
