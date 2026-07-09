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

### Preview em tempo real

O `EditorScreen` aplica o shader direto na composição via
`Modifier.graphicsLayer { renderEffect = RenderEffect.createRuntimeShaderEffect(...) }`
— slider mexeu, GPU re-renderiza o frame. Segurar o dedo na foto mostra a
original.

### Export (`export/Exporter.kt`)

1. Decodifica a foto na resolução do preset (ex.: 1600px = digicam raiz)
2. Roda o shader offscreen na GPU (`HardwareRenderer` + `ImageReader`,
   porque RuntimeShader não roda em Canvas de software)
3. Desenha o **timestamp laranja 7-segmentos** por cima
   (`TimestampRenderer` — desenhado com paths, sem fonte)
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

- Sliders individuais por efeito (os uniforms já existem, é só ligar UI)
- LUTs 3D reais de câmeras específicas (Sony Cyber-shot, Canon IXUS)
- Grain animado / export de vídeo curto
- Datas fake no timestamp ("modo 2003")
- Filtro no preview da câmera (RenderEffect no PreviewView)
