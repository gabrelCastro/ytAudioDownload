# YT Audio Downloader

Aplicativo desktop (Java + JavaFX) para baixar vídeos ou playlists inteiras do
YouTube já convertidos em áudio (MP3, M4A, Opus, FLAC ou WAV). Uso pessoal, local.

## Como funciona

O app é uma interface gráfica sobre o [`yt-dlp`](https://github.com/yt-dlp/yt-dlp)
(motor de download) e o `ffmpeg` (conversão de áudio). Ele não reimplementa o
download do YouTube — apenas chama esses dois programas e mostra progresso,
fila e seleção numa interface bonita.

## Pré-requisitos

- Java 21 (já detectado no seu ambiente)
- Maven (já detectado)
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

## Rodando o app

```bash
mvn javafx:run
```

## Usando

1. Cole a URL de um vídeo **ou** de uma playlist do YouTube e clique em "Buscar".
2. A lista de vídeos aparece na tabela, todos marcados por padrão — desmarque
   os que não quiser.
3. Escolha a pasta de saída, o formato de áudio e a qualidade no rodapé.
4. Clique em "Baixar selecionados". Cada linha mostra o progresso individual;
   a barra de baixo mostra o progresso geral.
5. O ícone de status no canto superior direito avisa se `yt-dlp` ou `ffmpeg`
   não foram encontrados; clique em "⚙ Configurações" para apontar o caminho
   manualmente ou ajustar quantos downloads simultâneos rodar.

Os arquivos vão por padrão para `~/Musica/YT-Audio`.

## Gerar um .jar único (executável com `java -jar`)

```bash
mvn -q clean package
```

Gera `target/yt-audio-downloader-1.0.0.jar` — um jar único e independente,
com o JavaFX já embutido (build fixado para Linux, já que é uso pessoal neste
computador). Depois de gerado, é só rodar:

```bash
java -jar target/yt-audio-downloader-1.0.0.jar
```

Não precisa mais do Maven nem do código-fonte para executar — só do jar e do
Java instalado. Se quiser um atalho de duplo-clique, crie um `.desktop`
apontando para esse comando.
