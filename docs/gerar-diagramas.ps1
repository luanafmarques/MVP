# Gera as imagens .png dos diagramas PlantUML (docs/diagramas/*.puml).
# Uso (na raiz do projeto):  powershell -ExecutionPolicy Bypass -File docs\gerar-diagramas.ps1
# Precisa de Java 11 ou mais novo. Não precisa de Graphviz (os diagramas usam o layout "smetana" do PlantUML).
$ErrorActionPreference = 'Stop'
$pasta = Join-Path $PSScriptRoot '.plantuml'
$jar = Join-Path $pasta 'plantuml.jar'
$versao = '1.2026.8'

if (-not (Test-Path $jar)) {
    New-Item -ItemType Directory -Force $pasta | Out-Null
    Write-Host "Baixando PlantUML $versao..."
    $ProgressPreference = 'SilentlyContinue'
    Invoke-WebRequest "https://repo.maven.apache.org/maven2/net/sourceforge/plantuml/plantuml/$versao/plantuml-$versao.jar" -OutFile $jar
}

$java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin\java.exe' } else { 'java' }
& $java -jar $jar -charset UTF-8 -tpng (Join-Path $PSScriptRoot 'diagramas\*.puml')
Write-Host 'Pronto: imagens geradas em docs\diagramas.'
