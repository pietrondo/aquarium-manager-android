# PixelLab artwork tooling

## Verified contract

Verified on 2026-10-18 against [official v2 OpenAPI](https://api.pixellab.ai/v2/openapi.json), [API docs](https://api.pixellab.ai/v2/docs) and the [official Python SDK](https://github.com/pixellab-code/pixellab-python/blob/master/pixellab/generate_image_pixflux.py). Context7 resolved `/pixellab-code/pixellab-python` and confirmed `Client(secret=...).generate_image_pixflux(description=..., image_size={width,height})` and `response.image.pil_image()`. The older SDK uses v1 internally; this tool instead uses the directly verified **synchronous v2** `POST https://api.pixellab.ai/v2/create-image-pixflux` with Bearer auth, `description`, `image_size`, `no_background`. Response: `image.base64` PNG bytes (raw Base64 or PNG data URI). This endpoint returns an image, not an async job; no speculative polling protocol is implemented.

Official endpoint description supports a minimum area of 32x32 and maximum 400x400. We request **48x48** for both launcher and UI originals, never unsupported 24x24 generation. The service may change; reverify contract before future paid generation. No paid request was executed for this delivery.

## Commands and resource outputs

`python scripts/assets.py generate` is dry-run. Only `--generate` enables paid calls. `--manifest`, `--sources` and `--output` override paths. The data-driven manifest controls names/kinds/subjects; shared style directs crisp flat silhouettes in navy, teal, aqua and coral without text. These are candidate prompts, not final design approval. Originals are exclusively created under `assets/originals/`; cache reuse never spends credits. Remove/move an original deliberately only after deciding to regenerate it.

`python scripts/assets.py resize` is entirely local, uses Pillow nearest-neighbor and validates every source before writing. Outputs default to `generated/android-res/`:

| Resource | mdpi | hdpi | xhdpi | xxhdpi | xxxhdpi |
|---|---:|---:|---:|---:|---:|
| UI PNG in drawable-* | 24 | 36 | 48 | 72 | 96 |
| Legacy launcher PNG in mipmap-* | 48 | 72 | 96 | 144 | 192 |

A launcher also gets `store/<name>.png` 512x512, plus `mipmap-*<name>_foreground.png` on a 108 dp adaptive canvas with the art at 48 dp. `resize` first trims each original's transparent padding and fits the art into the target box, so an icon uses the whole 24 dp instead of a small subject in an empty canvas. UI originals are reduced once from 48 to a 24-pixel master, then resized from that master; xhdpi/xxhdpi/xxxhdpi use integer 2x/3x/4x enlargement. Android's hdpi is inherently 1.5x; nearest-neighbor preserves palette but produces uneven pixel blocks. Launcher hdpi, the 512 store fit and the 48 dp adaptive art are also noninteger ratios. Inspect these individually; do not claim every density is an integer upscale. Export does not anti-alias or invent colors. Manual pixel cleanup at 24px may be necessary before acceptance.

**Modern launcher work remains open in Beads:** Android adaptive foreground/background XML with safe-zone composition, monochrome/themed icon (Android 13+), and circle/squircle/rounded-square masking QA. Legacy mipmaps alone are NOT complete modern launcher coverage. Generated multi-color UI PNGs also require deliberate Compose tint handling and native-size legibility/contrast QA; do not use launcher mipmaps for navigation icons.

## Failure and secret handling

All HTTP redirects are rejected: credentials are never forwarded cross-origin or downgraded to HTTP. Redirects return a sanitized HTTP-status error. One bounded 90-second HTTP operation per uncached asset, 8 MiB response ceiling, validated PNG dimensions, no automatic retries (avoid double charges). HTTP status is shown without response bodies/headers or keys. Partial source writes are removed on failure. Outbound requests occur only in the development script with an explicit flag. A timeout has uncertain billing outcome; inspect the account before retrying. Generated originals are saved as PNG before resizing so no API calls are needed for density changes.

Mock tests cover exact endpoint/payload/timeout and Base64 response, HTTP failures, malformed response, dry-run, cache, path validation, aspect/size rejection and density outputs. Real service availability and visual suitability are unverified. See verification evidence.
