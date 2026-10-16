#Sistema de Processamento de Notas Fiscais
API para recebimento, processamento e consulta de NF-e (XML), com fluxo assíncrono na AWS, autenticação JWT e isolamento por tenant (CNPJ). Nesta versão o ambiente AWS é emulado localmente com LocalStack; o deploy na AWS real está listado em Melhorias futuras.
O que o sistema faz
1. O cliente autenticado pede uma presigned URL de upload (POST /notas/upload-url).
2. O cliente envia o XML da NF-e direto ao S3, sem passar pela API.
3. O S3 notifica uma fila SQS (com DLQ para falhas).
4. Um listener (@SqsListener) baixa o XML, faz o parse seguro e grava a nota no DynamoDB, de forma idempotente.
5. O cliente consulta o status e os dados da nota (GET /notas/{chave}), sempre restrito ao próprio tenant.
Arquitetura
Monolito modular em Spring Boot, com fronteiras claras entre módulos. A decisão de não usar microserviços está justificada na seção Decisões de projeto.
Cliente --JWT--> API (Spring Boot)
  |  POST /notas/upload-url  -> gera presigned URL (tenant vem do token)
  |  GET  /notas/{chave}     -> consulta no DynamoDB (filtrada pelo tenant)
  |
  +--PUT XML (presigned URL)--> S3 (entrada/{tenant}/{uuid}.xml)
                                  |  evento ObjectCreated
                                  v
                                SQS (notas-queue) --3 falhas--> DLQ (notas-dlq)
                                  |
                                  v
                         Listener (modulo processamento)
                           parse XML (XXE bloqueado) -> DynamoDB (PutItem condicional)
Estrutura de módulos
notas/src/main/java/com/sistema_processamentoNFiscal/notas/
  ingestao/        controller + service (presigned URL)
  processamento/   listener SQS, parser NF-e, service, model
  consulta/        controller + service (leitura)
  infra/security/  SecurityConfig (JWT), resolução do tenant
Modelo de dados (DynamoDB, tabela notas)
Atributo
Exemplo
Uso
PK
TENANT#12345678000190
partição por tenant (isolamento)
SK
NOTA#<chave de acesso de 44 dígitos>
identifica a nota e garante idempotência
status
PROCESSADA
situação do processamento
emitenteCnpj, valorTotal, dataEmissao

dados extraídos do XML
s3Key
entrada/{tenant}/{uuid}.xml
arquivo original
schemaVersion
1
evolução do modelo (DynamoDB não tem migrations)
criadoEm

auditoria
Stack utilizada
Aplicação
• Java (alvo: 21) e Spring Boot 3.3.x
• Spring Web, Validation, Actuator
• Spring Security com OAuth2 Resource Server (JWT)
• Spring Cloud AWS 3.2.x (S3, SQS, DynamoDB) e AWS SDK v2
• Maven
AWS (emulada com LocalStack)
• S3, SQS (com DLQ) e DynamoDB
Autenticação em desenvolvimento
• mock-oauth2-server (emite JWT com o claim custom:tenantId); em produção o emissor seria o Cognito
Testes
• JUnit 5, Spring Test e Spring Security Test
• Testcontainers (LocalStack e mock-oauth2-server) e Awaitility
Infra local
• Docker e Docker Compose
Segurança
• JWT validado pelo Spring Security (Resource Server); o claim custom:tenantId identifica o tenant e é validado (14 dígitos).
• Isolamento por tenant: toda consulta usa a PK do tenant do token; o upload grava em entrada/{tenant}/, também vindo do token.
• Nota de outro tenant retorna 404, para não revelar que ela existe.
• Parser seguro (XXE): DOCTYPE e entidades externas bloqueados.
• Sem fallbacks silenciosos: XML com campo obrigatório ausente gera exceção, a mensagem é reentregue e, após 3 tentativas, vai para a DLQ.
• Idempotência: PutItem com attribute_not_exists(SK).
• Actuator exposto apenas em /actuator/health.
Evidências (testes manuais com token real e testes automatizados)
Cenário
Resultado
Requisição sem token
401
Token sem claim de tenant
403
Upload com token do tenant A / B
key com o CNPJ do próprio token
Tenant A consulta a própria nota
200
Tenant B consulta nota do tenant A
404
Chave de acesso com formato inválido
400
XML com DOCTYPE (XXE)
rejeitado
XML inválido
nada é gravado; mensagem vai para a DLQ
Mesma nota enviada duas vezes
um único item
Durante o desenvolvimento, uma revisão com grep encontrou um tenant fixo no UploadController, que permitiria gravar arquivos do tenant B na pasta do tenant A. Foi corrigido e protegido por teste de regressão.
Como rodar localmente
Pré-requisitos: JDK 21, Docker e Docker Compose.
1. Suba as dependências:
docker compose up -d
O LocalStack cria bucket, filas, DLQ, tabela e a notificação S3 → SQS via localstack-init/init.sh. O mock-oauth2-server usa mock-oauth/config.json, que define dois tenants fictícios (client_id teste e teste2).
2. Rode a aplicação:
cd notas
./mvnw spring-boot:run
3. Gere um token (Git Bash):
TOKEN_A=$(curl -s -X POST http://localhost:9000/default/token \
  -d "grant_type=client_credentials" -d "client_id=teste" \
  -d "client_secret=segredo" -d "scope=openid" \
  | grep -o '"access_token" *: *"[^"]*"' | cut -d'"' -f4)
4. Peça a URL de upload e envie o XML:
URL=$(curl -s -X POST http://localhost:8080/notas/upload-url \
  -H "Authorization: Bearer $TOKEN_A" | grep -o '"url":"[^"]*"' | cut -d'"' -f4)

curl -X PUT -H "Content-Type: application/xml" \
  --data-binary @src/test/resources/nfe-exemplo.xml "$URL"
5. Consulte a nota (substitua pela chave de 44 dígitos):
curl -i http://localhost:8080/notas/<CHAVE> -H "Authorization: Bearer $TOKEN_A"
Testes
cd notas
./mvnw clean verify
Os testes de integração sobem LocalStack e mock-oauth2-server via Testcontainers, então o Docker precisa estar em execução.
Decisões de projeto
• Monolito modular em vez de microserviços: o escopo não tem times independentes nem necessidade de escala separada por módulo. Menos pipelines, menos infraestrutura e testes mais simples. O módulo de processamento é o primeiro candidato a extração, caso a carga justifique.
• DynamoDB sem Flyway: não há schema relacional. A estrutura da tabela é definida por infraestrutura como código (previsto) e a evolução do modelo, pelo campo schemaVersion. Um serviço futuro de relatórios poderia usar PostgreSQL com Flyway.
• Upload por presigned URL: evita o limite de payload do API Gateway e tira o tráfego de arquivos da aplicação.
• Fila entre S3 e processamento: absorve picos e permite reprocessar falhas pela DLQ.
• 404 para nota de outro tenant: não confirma a existência do recurso.
• Sem fallbacks no parser: dado fiscal incompleto nunca é gravado com valor padrão.
Limitações atuais
• Roda apenas localmente; não foi feito deploy na AWS real.
• Apenas XML de NF-e; PDF e imagem não são processados.
• A listagem de notas (GET /notas) e os filtros por status e período dependem de GSIs ainda não criados.
• Sem validação do status da nota na SEFAZ.
• A identidade em produção (Cognito) ainda não foi configurada.
Melhorias futuras
Entrega e operação
• Dockerfile multi-stage (build com Maven, runtime com JRE 21, usuário não root) e serviço da aplicação no Docker Compose.
• Infraestrutura como código com AWS CDK ou Terraform: bucket, filas, tabela, Cognito, ECS Fargate e API Gateway.
• CI/CD com GitHub Actions: build, testes, scan de dependências e imagem (Trivy, Dependabot, gitleaks) e deploy automático.
• Padronizar o projeto em Java 21 (hoje o ambiente de desenvolvimento usa uma versão mais nova).
Funcionalidades
• GSIs no DynamoDB para listar e filtrar por status e período, com paginação por cursor.
• Suporte a PDF e imagem com Amazon Textract.
• Validação do status da nota na SEFAZ.
• Notificações de conclusão do processamento (SNS ou EventBridge, ou WebSocket).
• Registro de notas com falha (status=ERRO) para consulta pelo cliente, além da DLQ.
Segurança
• Cognito como emissor de JWT, com tokens de curta duração.
• API Gateway com WAF, throttling e validação de requisição.
• KMS (chaves próprias) em S3, SQS e DynamoDB; Object Lock e lifecycle no S3 para a retenção fiscal (5 anos ou mais).
• Privilégio mínimo no IAM, subnets privadas e VPC endpoints.
• Mascaramento de CNPJ e CPF nos logs, e trilha de auditoria (CloudTrail) para a LGPD.
Observabilidade
• Logs estruturados em JSON, métricas com Micrometer/CloudWatch, tracing com X-Ray ou OpenTelemetry e alarme na DLQ.
Qualidade
• Testes de carga (k6 ou Gatling).
• Spring Modulith para validar as fronteiras entre módulos.
• ADRs documentando as decisões de arquitetura e OpenAPI/Swagger.
