<#
在独立、本机、临时 PostgreSQL 实例集中验证阶段二，不读取项目 .env 或连接远程数据库。
依赖 PATH 中的 PostgreSQL initdb/pg_ctl/psql、Java 25 和 Maven。
#>
$ErrorActionPreference = 'Stop'
$backendDirectory = Split-Path -Parent $PSScriptRoot
$verificationDirectory = Join-Path $backendDirectory ('target\verification-' + [guid]::NewGuid().ToString('N'))
$dataDirectory = Join-Path $verificationDirectory 'postgres'
$serverLog = Join-Path $verificationDirectory 'postgres.log'
$previousTestUrl = $env:GUIDEPINS_TEST_DB_URL
$postgresStarted = $false

# 由操作系统挑选空闲本机端口；数据库只监听 127.0.0.1。
$portProbe = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, 0)
$portProbe.Start()
$testPort = $portProbe.LocalEndpoint.Port
$portProbe.Stop()

New-Item -ItemType Directory -Path $verificationDirectory | Out-Null
Push-Location $backendDirectory
try {
    Write-Host '正在初始化独立 PostgreSQL 测试实例……'
    & initdb -D $dataDirectory -A trust -U guidepins_test_owner --no-locale --encoding=UTF8 *> (Join-Path $verificationDirectory 'initdb.log')
    if ($LASTEXITCODE -ne 0) { throw "initdb 失败，详见 $verificationDirectory" }
    & pg_ctl -D $dataDirectory -l $serverLog -o "-h 127.0.0.1 -p $testPort" -w start
    if ($LASTEXITCODE -ne 0) { throw "PostgreSQL 启动失败，详见 $serverLog" }
    $postgresStarted = $true
    & psql -X -v ON_ERROR_STOP=1 -h 127.0.0.1 -p $testPort -U guidepins_test_owner -d postgres -c 'CREATE DATABASE guidepins_backend_test' *> (Join-Path $verificationDirectory 'database.log')
    if ($LASTEXITCODE -ne 0) { throw '创建隔离测试库失败' }
    & psql -X -v ON_ERROR_STOP=1 -h 127.0.0.1 -p $testPort -U guidepins_test_owner -d guidepins_backend_test -f (Join-Path $backendDirectory 'src\test\resources\postgres\schema.sql') *> (Join-Path $verificationDirectory 'schema.log')
    if ($LASTEXITCODE -ne 0) { throw "测试结构初始化失败，详见 $verificationDirectory" }

    $env:GUIDEPINS_TEST_DB_URL = "jdbc:postgresql://127.0.0.1:$testPort/guidepins_backend_test"
    Write-Host '正在使用最小权限应用账号运行完整 Maven 验证……'
    # 仅重定向 Maven；Windows 上重定向整个脚本会等待后台 PostgreSQL 的继承管道。
    $mavenLog = Join-Path $verificationDirectory 'maven.log'
    & mvn -B verify *> $mavenLog
    $verificationExitCode = $LASTEXITCODE
    Get-Content -LiteralPath $mavenLog -Tail 50
    if ($verificationExitCode -ne 0) { throw "阶段二验证失败，详见 $mavenLog 和 target/surefire-reports" }
}
finally {
    if ($postgresStarted) {
        & pg_ctl -D $dataDirectory -m fast -w stop
    }
    $env:GUIDEPINS_TEST_DB_URL = $previousTestUrl
    Pop-Location
}
