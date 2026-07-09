# appy2k 📸✧

Editor de fotos com estética anos 2000 — digicam, shoegaze, emo, Orkut.
Inspirado em apps como *y2k: editor de fotos anos 2000* e *Bleach Bypass*.

## Stack

- **Kotlin + Jetpack Compose** (Material 3, dark sempre)
- **CameraX** pra captura (flash ligado por padrão — flash estourado É a estética)
- **AGSL / RuntimeShader** pra todo o processamento de imagem na GPU
  - por isso o **minSdk é 33** (Android 13+)
- Sem dependências de terceiros além de AndroidX

## Arquitetura dos efeitos

Tudo acontece em um **über-shader AGSL** de passe único
(`effects/Y2kShader.kt`), parametrizado por uniforms. Cada preset
(`effects/Presets.kt`) é só um `EffectParams` — um conjunto de valores pros
uniforms + configurações do pipeline de export. Criar preset novo = copiar
um data class e ajustar números.

Efeitos implementados no shader, na ordem do pipeline:

| efeito | uniform | o que faz |
|---|---|---|
| softness | `softness` | blur de digicam (downscale/upscale fake) |
| aberração cromática | `ca` | canais R/B deslocados radialmente |
| ghosting | `ghost` | double exposure direcional (shoegaze) |
| bloom/halation | `bloom` | glow quente nos highlights (flash estourado) |
| blocos JPEG fake | `blockiness` | chroma quantizado em blocos 8×8 (só preview) |
| color science CCD | `crush`, `fade`, `colorCast`, `saturation` | pretos esmagados, cast ciano/magenta, saturação estranha |
| vinheta | `vignette` | |
| posterização + dither | `poster` | banding de foto de Orkut, ordered dither 2×2 |
| grain | `grain` | ruído de sensor pequeno, mais forte nas sombras |

Todos os tamanhos em pixel escalam pelo uniform `px` (referência: 1200px no
menor lado), então o preview em baixa resolução e o export em alta ficam
visualmente idênticos.

### LUTs 3D de câmeras (`effects/Luts.kt`)

Além do grade paramétrico, cada preset pode aplicar uma **LUT 3D** emulando
a color science de uma digicam específica: **Sony CCD** (Cyber-shot, frio,
sombras teal), **Canon IXUS** (quente, highlight magenta), **Kodak
EasyShare** (amarelão, vermelhos gritados) e **Fuji FinePix** (esverdeado,
contraste macio). As LUTs são geradas parametricamente em runtime (32³,
white balance → gamma → curva S → tint por luma → saturação) — nada de
assets binários — e viram uma textura 2D em faixa (fatias de azul lado a
lado) que o shader amostra com lookup trilinear. Dá pra trocar a câmera e a
intensidade da LUT nos ajustes finos.

### Preview em tempo real

O `EditorScreen` aplica o shader direto na composição via
`Modifier.graphicsLayer { renderEffect = RenderEffect.createRuntimeShaderEffect(...) }`
— slider mexeu, GPU re-renderiza o frame. Segurar o dedo na foto mostra a
original. Além do slider mestre ("vibe"), o painel **ajustes finos** expõe
um slider por uniform do shader + a escolha de câmera (LUT).

A **câmera também filtra ao vivo**: o `CameraScreen` aplica o mesmo shader
no `PreviewView` via `View.setRenderEffect`, com o grain animado por um
loop que troca o seed (~11fps, vibe VHS). O `PreviewView` roda em modo
`COMPATIBLE` (TextureView) porque RenderEffect não pega em SurfaceView.
O preset escolhido na câmera já abre selecionado no editor.

### Export (`export/Exporter.kt`)

1. Decodifica a foto na resolução do preset (ex.: 1600px = digicam raiz)
2. Roda o shader offscreen na GPU (`HardwareRenderer` + `ImageReader`,
   porque RuntimeShader não roda em Canvas de software)
3. Desenha o **timestamp laranja 7-segmentos** por cima
   (`TimestampRenderer` — desenhado com paths, sem fonte). A era é
   configurável: data de hoje, "modo 2003" ou "modo 1999" — presets nostálgicos
   já vêm com ano fake por padrão (Digicam ’03 carimba ’03, Orkut ’07…)
4. **Compressão JPEG real** em N gerações (`JpegCrusher`) — blocking e
   mosquito noise autênticos, tipo foto que passou por MSN → Fotolog → Orkut
5. Salva em `Pictures/appy2k` via MediaStore

## Presets

- **Digicam ’03** — o pacote completo: soft, CCD cast, timestamp, JPEG q55
- **Flash Estourado** — bloom alto, pretos esmagados, vinheta, grain
- **Shoegaze** — ghosting forte, fade, dessaturado, grain pesado
- **Orkut** — posterização + dither, JPEG q28 em 2 gerações, 1280px
- **Bleach Bypass** — dessaturado prateado, contraste alto
- **Emo Night** — aberração forte, crush, cast magenta, vinheta pesada

## Build

```bash
./gradlew assembleDebug
```

Requer JDK 17+ e Android SDK (compileSdk 35).

## Ideias pra depois

- Export de vídeo curto com grain animado
- LUTs carregadas de arquivos .cube (as atuais são paramétricas)
- Frente/verso de câmera e zoom no CameraScreen
- Salvar presets customizados do usuário
