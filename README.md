# YT Audio Downloader

Aplicativo desktop (Java + JavaFX) para baixar vídeos ou playlists inteiras do
YouTube já convertidos em áudio (MP3, M4A, Opus, FLAC ou WAV). Uso pessoal, local.

## Funcionalidades

- Cola uma ou várias URLs de uma vez (vídeo ou playlist), uma por linha.
- Também aceita **texto de busca** em vez de URL — digite um termo e ele busca
  os 8 primeiros resultados do YouTube automaticamente.
- Mostra **miniatura**, título, duração, progresso e status de cada vídeo numa
  tabela; escolha manualmente o que baixar (nada vem marcado por padrão).
- Download em paralelo (quantidade configurável) com progresso individual e geral.
- Escolha de pasta de saída, formato de áudio e qualidade.
- Autenticação por cookies para vídeos que exigem login (idade, "sign in to
  confirm you're not a bot"), incluindo um **login com Google embutido no app**.
- Build de **arquivo único para Windows**: um `.bat` que já é o próprio jar
  (com yt-dlp/ffmpeg embutidos), sem precisar instalar nada além do Java.

## Como funciona

O app é uma interface gráfica sobre o [`yt-dlp`](https://github.com/yt-dlp/yt-dlp)
(motor de extração) e o `ffmpeg` (conversão de áudio). Ele não reimplementa o
download do YouTube — apenas chama esses dois programas via `ProcessBuilder`,
parseia a saída e mostra progresso, fila e seleção numa interface bonita.

## Pré-requisitos (para rodar em modo desenvolvimento neste computador)

- Java 21
- Maven
- `yt-dlp` e `ffmpeg` instalados

### Instalar as dependências (uma vez só)

```bash
sudo apt-get update
sudo apt-get install -y ffmpeg pipx
pipx install yt-dlp
pipx ensurepath
```

Depois de instalar, feche e abra o terminal (ou rode `source ~/.bashrc`) para o
`PATH` ser atualizado. O app também procura automaticamente em
`~/.local/bin`, `/usr/local/bin` e `/usr/bin`, então normalmente funciona sem
precisar configurar nada.

Para atualizar o yt-dlp depois (o YouTube muda com frequência e o yt-dlp
precisa ser atualizado de tempos em tempos):

```bash
pipx upgrade yt-dlp
```

## Rodando em modo desenvolvimento

```bash
mvn javafx:run
```

## Usando

1. Cole uma ou mais URLs (vídeo ou playlist) **ou** digite termos de busca —
   um por linha — e clique em "🔍 Buscar" (ou Ctrl+Enter).
2. A lista de vídeos aparece na tabela com miniatura, título e duração — tudo
   desmarcado por padrão. Marque o que quiser baixar (ou "Selecionar todos").
3. Escolha a pasta de saída, o formato de áudio e a qualidade no rodapé.
4. Clique em "⬇ Baixar selecionados". Cada linha mostra o progresso individual;
   a barra de baixo mostra o progresso geral. O painel "Logs" (colapsável)
   mostra a saída bruta do yt-dlp, útil para diagnosticar falhas.
5. O chip no canto superior direito avisa se `yt-dlp` ou `ffmpeg` não foram
   encontrados; clique em "⚙ Configurações" para apontar o caminho manualmente
   ou ajustar quantos downloads simultâneos rodar.

Os arquivos vão por padrão para `~/Musica/YT-Audio`.

## Vídeos que pedem login ("Sign in to confirm you're not a bot")

Alguns vídeos (conteúdo restrito, ou aleatoriamente marcados pelo YouTube) só
extraem com uma sessão autenticada. O app já tenta contornar isso sozinho: por
padrão, cada download primeiro tenta **sem** cookies usando o cliente
`android` do yt-dlp, que na prática consegue extrair a maioria dos vídeos sem
precisar de login. Só se essa tentativa falhar é que ele cai automaticamente
para os cookies configurados (se houver).

Se um vídeo específico continuar falhando, configure cookies em
⚙ Configurações:

- **Entrar com Google** — abre uma janela de login dentro do próprio app
  (navegador embutido) e gera o arquivo de cookies sozinho. Experimental: o
  Google às vezes bloqueia login em navegador embutido.
- **Arquivo cookies.txt** — alternativa manual e mais confiável: exporte os
  cookies de um navegador de verdade (extensão "Get cookies.txt LOCALLY") com
  o YouTube logado, e aponte o arquivo aqui. Tem prioridade sobre a opção de
  navegador abaixo.
- **Cookies do navegador** — pede ao yt-dlp para ler os cookies direto de um
  navegador instalado (chrome/firefox/etc). Só funciona se esse navegador
  estiver instalado **no mesmo sistema operacional onde o yt-dlp roda** — ou
  seja, sob WSL, só funciona com um navegador instalado dentro do WSL, não com
  o Chrome/Edge do Windows.

## Gerar um .jar único para este computador (Linux/WSL)

```bash
mvn -q clean package
```

Gera `target/yt-audio-downloader-1.0.0.jar` — um jar único e independente, com
o JavaFX já embutido (build fixado para Linux). Depois de gerado, é só rodar:

```bash
java -jar target/yt-audio-downloader-1.0.0.jar
```

Não precisa mais do Maven nem do código-fonte para executar — só do jar e do
Java instalado.

## Gerar o arquivo único para Windows

O JavaFX distribui binários nativos separados por sistema operacional. Para
gerar um jar com os nativos do Windows (mesmo compilando aqui no Linux/WSL):

```bash
mvn clean package -Djavafx.platform=win
```

Esse jar sozinho já roda em qualquer Windows com Java instalado
(`java -jar target/yt-audio-downloader-1.0.0.jar`). Mas o projeto vai além:
`windows/build-single-file.sh` embute também `yt-dlp.exe`, `ffmpeg.exe` e
`ffprobe.exe` dentro do próprio jar (extraídos automaticamente na primeira
execução) e gera um único arquivo `.bat` que **é o próprio jar** — um "polyglot"
que roda tanto como script batch (duplo-clique) quanto como jar (`java -jar`),
técnica igual à dos jars "fully executable" do Spring Boot: um cabeçalho de
script pode ser concatenado antes dos bytes de um zip sem quebrá-lo, porque
`java.util.zip` localiza o *central directory* a partir do fim do arquivo,
tolerando qualquer prefixo.

Para gerar:

1. Baixe os três binários do Windows (não ficam no git — veja
   `native-bin/win/README.md` para os links) e coloque em `native-bin/win/`.
2. Rode:
   ```bash
   ./windows/build-single-file.sh
   ```
3. Isso gera `YT-Audio-Downloader.bat` na raiz do projeto — copie esse único
   arquivo para o Windows. Ele detecta se há Java instalado; se não houver,
   baixa e instala um Java portátil sozinho (Eclipse Temurin 21) na primeira
   execução, sem precisar de instalação manual.

No Windows, ainda é necessário ter o próprio **Java** instalado (ou deixar o
`.bat` baixar um Java portátil na primeira vez) — isso não dá para embutir da
mesma forma que yt-dlp/ffmpeg.

## Solução de problemas

- **"Sign in to confirm you're not a bot" / "The page needs to be reloaded"**:
  já mitigado automaticamente (veja a seção acima). Se persistir mesmo com
  cookies configurados, o vídeo pode genuinamente exigir uma conta com acesso
  (idade, membros, etc).
- **Qualidade do áudio**: quando a extração cai no cliente `android` (o
  caminho sem cookies), a única fonte disponível costuma ser um formato único
  a ~360p com áudio AAC ~128kbps. Isso não afeta o resultado final além da
  qualidade da fonte — como só ficamos com o áudio, a resolução de vídeo é
  irrelevante, mas não dá para forçar uma qualidade de áudio maior que a
  fonte disponível para aquele vídeo específico.
- **ffmpeg parece "faltando" mas está instalado**: o app testa tanto `-version`
  quanto `--version`, já que os dois programas usam convenções diferentes.

## Estrutura do projeto

```
src/main/java/com/gabriel/ytaudio/
├── App.java                    # entry point (JavaFX Application)
├── Launcher.java                # shim para permitir `java -jar` sem module-path
├── model/VideoItem.java         # linha da tabela (bean observável)
├── service/
│   ├── AppSettings.java         # preferências persistidas (Preferences API)
│   ├── BundledTools.java        # extrai yt-dlp/ffmpeg embutidos no jar (build Windows)
│   ├── CookieExporter.java      # exporta cookies do WebView para cookies.txt
│   ├── ToolLocator.java         # localiza yt-dlp/ffmpeg no sistema
│   └── YtDlpService.java        # monta e executa os comandos do yt-dlp
└── ui/
    ├── MainView.java             # janela principal
    └── GoogleLoginDialog.java    # login com Google via WebView

windows/
├── bat-header.bat                # cabeçalho batch usado no build single-file
└── build-single-file.sh          # gera o YT-Audio-Downloader.bat

native-bin/win/                   # binários Windows (gitignored, ver README lá dentro)
```
