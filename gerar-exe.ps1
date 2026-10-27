# Gera o programa executável (.exe) do JY Bank, sem precisar do Maven.
# Requisitos: JDK 17 ou mais novo (o mesmo que você usa no VS Code) e internet só na primeira vez (para baixar o H2).
# Uso: dê dois cliques em gerar-exe.bat  (ou rode este arquivo no PowerShell).

$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

function Achar-Ferramenta($nome) {
    $cmd = Get-Command $nome -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    if ($env:JAVA_HOME) {
        $caminho = Join-Path $env:JAVA_HOME "bin\$nome.exe"
        if (Test-Path $caminho) { return $caminho }
    }
    # procura nos JDKs instalados nos lugares mais comuns
    $pastas = @("$env:ProgramFiles\Java", "$env:ProgramFiles\Eclipse Adoptium", "$env:ProgramFiles\Microsoft",
                "$env:ProgramFiles\Amazon Corretto", "$env:ProgramFiles\Zulu", "$env:LOCALAPPDATA\Programs")
    foreach ($pasta in $pastas) {
        if (Test-Path $pasta) {
            $achou = Get-ChildItem $pasta -Recurse -Filter "$nome.exe" -ErrorAction SilentlyContinue | Select-Object -First 1
            if ($achou) { return $achou.FullName }
        }
    }
    return $null
}

$javac    = Achar-Ferramenta 'javac'
$jar      = Achar-Ferramenta 'jar'
$jpackage = Achar-Ferramenta 'jpackage'
if (-not ($javac -and $jar -and $jpackage)) {
    Write-Host "Não encontrei um JDK completo (javac, jar e jpackage)." -ForegroundColor Red
    Write-Host "Instale o JDK 17 ou mais novo e tente de novo. Se já instalou, defina a variável JAVA_HOME apontando para a pasta do JDK."
    exit 1
}
Write-Host "JDK encontrado: $(Split-Path (Split-Path $javac))"

# --- 1. biblioteca do banco de dados (H2)
New-Item lib -ItemType Directory -Force | Out-Null
$h2 = Get-ChildItem lib -Filter 'h2-*.jar' -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $h2) {
    # tenta reaproveitar o que o Maven/VS Code já baixou
    $m2 = Join-Path $env:USERPROFILE '.m2\repository\com\h2database\h2'
    if (Test-Path $m2) {
        $emCache = Get-ChildItem $m2 -Recurse -Filter 'h2-*.jar' | Where-Object { $_.Name -notmatch 'sources|javadoc' } | Select-Object -First 1
        if ($emCache) { Copy-Item $emCache.FullName lib\; }
    }
    $h2 = Get-ChildItem lib -Filter 'h2-*.jar' -ErrorAction SilentlyContinue | Select-Object -First 1
}
if (-not $h2) {
    Write-Host "Baixando o H2 (banco de dados)..."
    Invoke-WebRequest -Uri 'https://repo1.maven.org/maven2/com/h2database/h2/2.2.224/h2-2.2.224.jar' -OutFile 'lib\h2-2.2.224.jar'
    $h2 = Get-Item 'lib\h2-2.2.224.jar'
}
Write-Host "Banco de dados: $($h2.Name)"

# --- 2. compilar
if (Test-Path build) { Remove-Item build -Recurse -Force }
New-Item build\classes -ItemType Directory | Out-Null
New-Item build\input -ItemType Directory | Out-Null
Get-ChildItem src\main\java -Recurse -Filter *.java |
    ForEach-Object { '"' + ($_.FullName -replace '\\', '/') + '"' } |
    Set-Content build\fontes.txt -Encoding ascii
Write-Host "Compilando..."
& $javac -encoding UTF-8 -d build\classes -cp $h2.FullName "@build\fontes.txt"
if ($LASTEXITCODE -ne 0) { Write-Host "Erro de compilação." -ForegroundColor Red; exit 1 }
Copy-Item src\main\resources\schema.sql build\classes\

# --- 3. .jar (o H2 fica ao lado, ligado pelo Class-Path)
Set-Content build\MANIFEST.MF -Value "Main-Class: app.Main`r`nClass-Path: $($h2.Name)`r`n" -Encoding ascii
& $jar --create --file build\input\sistema-bancario.jar --manifest build\MANIFEST.MF -C build\classes .
Copy-Item $h2.FullName build\input\
if ($LASTEXITCODE -ne 0) { Write-Host "Erro ao criar o .jar." -ForegroundColor Red; exit 1 }

# --- 4. .exe (jpackage, já vem no JDK; o JRE é embutido, então o .exe roda em PCs sem Java instalado)
if (Test-Path dist) { Remove-Item dist -Recurse -Force }
Write-Host "Gerando o executável..."
$parametros = @('--type', 'app-image', '--name', 'JYBank', '--app-version', '1.0', '--vendor', 'JY Bank',
    '--description', 'JY Bank - Sistema Bancário', '--input', 'build\input', '--main-jar', 'sistema-bancario.jar',
    '--main-class', 'app.Main', '--java-options', '-Dfile.encoding=UTF-8', '--dest', 'dist')
if (Test-Path 'assets\icone.ico') { $parametros += @('--icon', 'assets\icone.ico') }  # ícone da marca no .exe
& $jpackage @parametros
if ($LASTEXITCODE -ne 0) { Write-Host "Erro no jpackage." -ForegroundColor Red; exit 1 }

Write-Host ""
Write-Host "PRONTO!" -ForegroundColor Green
Write-Host "Executável: $PSScriptRoot\dist\JYBank\JYBank.exe"
Write-Host "Copie a pasta inteira dist\JYBank para qualquer computador com Windows e dê dois cliques no .exe."
Write-Host "Os dados (contas, extratos, log) ficam em: $env:USERPROFILE\SistemaBancario"
