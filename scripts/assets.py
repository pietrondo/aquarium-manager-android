"""Paid PixelLab generation (explicit opt-in), and offline Android PNG sizing."""
import argparse
import base64
import binascii
import io
import json
import os
from pathlib import Path
import re
import sys
import urllib.error
import urllib.request
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
ENDPOINT = 'https://api.pixellab.ai/v2/create-image-pixflux'
UI_SIZES = dict(zip(['mdpi','hdpi','xhdpi','xxhdpi','xxxhdpi'], [24,36,48,72,96]))
LAUNCHER_SIZES = dict(zip(UI_SIZES, [48,72,96,144,192]))
STYLE_PATH = ROOT / 'assets' / 'style.txt'


class RejectRedirects(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        # Credentials must never follow redirects, even to another HTTPS origin.
        return None


def validate(records):
    if not isinstance(records, list) or not records:
        raise ValueError('Manifest must be a nonempty JSON array')
    seen = set()
    for record in records:
        if not isinstance(record, dict):
            raise ValueError('Each asset must be an object')
        name = record.get('name', '')
        if not isinstance(name, str) or not re.fullmatch(r'[a-z][a-z0-9_]*', name) or name in seen:
            raise ValueError('Asset names must be unique safe Android resource identifiers')
        if record.get('kind') not in ('launcher', 'ui') or not isinstance(record.get('description'), str) or not record['description'].strip():
            raise ValueError('Asset requires kind ui/launcher and nonempty description')
        seen.add(name)
    return records


def request_image(key, description):
    # Verified synchronous v2 OpenAPI: this endpoint returns image, not a job ID.
    data = json.dumps({'description': STYLE_PATH.read_text(encoding='utf-8-sig').strip() + ' ' + description,
                       'image_size': {'width':48,'height':48}, 'no_background':True}).encode()
    req = urllib.request.Request(ENDPOINT, data=data, headers={
        'Authorization':'Bearer '+key, 'Content-Type':'application/json'}, method='POST')
    try:
        opener = urllib.request.build_opener(RejectRedirects())
        with opener.open(req, timeout=90) as response:
            raw = response.read(8 * 1024 * 1024 + 1)
        if len(raw) > 8 * 1024 * 1024:
            raise ValueError('API response exceeds 8 MiB limit')
        encoded = json.loads(raw)['image']['base64']
        if encoded.startswith('data:image/png;base64,'):
            encoded = encoded.split(',', 1)[1]
        png = base64.b64decode(encoded, validate=True)
        with Image.open(io.BytesIO(png)) as image:
            if image.format != 'PNG' or image.size != (48,48):
                raise ValueError('API must return a 48x48 PNG')
            return image.convert('RGBA')
    except urllib.error.HTTPError as exc:
        # Never print headers, credentials or server response bodies.
        raise RuntimeError(f'PixelLab HTTP {exc.code}; no automatic retry (duplicate billing risk)') from None
    except urllib.error.URLError:
        raise RuntimeError('PixelLab network failure; outcome may be billed; inspect before retry') from None
    except (KeyError, TypeError, binascii.Error, json.JSONDecodeError) as exc:
        raise ValueError('Malformed PixelLab image response') from None


def generate(records, sources, enabled):
    validate(records)
    for record in records:
        path = sources / (record['name']+'.png')
        if path.exists():
            print('cached:', record['name'])
            continue
        if not enabled:
            print('dry-run:', record['name'], '48x48', record['description'])
            continue
        key = os.environ.get('PIXELLAB_API_KEY', '').strip()
        if not key:
            raise ValueError('Set PIXELLAB_API_KEY in the development environment only')
        image = request_image(key, record['description'])
        sources.mkdir(parents=True, exist_ok=True)
        # Exclusive create protects cached originals; partial files removed on save failure.
        with path.open('xb') as stream:
            try:
                image.save(stream, format='PNG')
            except Exception:
                stream.close(); path.unlink(missing_ok=True)
                raise
        print('saved:', record['name'])


def resize(records, sources, output):
    validate(records)
    # Validate the whole batch before producing any resource.
    images = []
    for record in records:
        with Image.open(sources / (record['name']+'.png')) as image:
            if image.format != 'PNG' or image.size != (48,48):
                raise ValueError('Sources must be square 48x48 PNG originals')
            images.append((record, image.convert('RGBA')))
    for record, image in images:
        launcher = record['kind'] == 'launcher'
        base = image if launcher else image.resize((24,24), Image.Resampling.NEAREST)
        for density, size in (LAUNCHER_SIZES if launcher else UI_SIZES).items():
            folder = output / (('mipmap-' if launcher else 'drawable-')+density)
            folder.mkdir(parents=True, exist_ok=True)
            base.resize((size,size),Image.Resampling.NEAREST).save(folder/(record['name']+'.png'))
        if launcher:
            folder=output/'store'; folder.mkdir(parents=True,exist_ok=True)
            image.resize((512,512),Image.Resampling.NEAREST).save(folder/(record['name']+'.png'))


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action',choices=['generate','resize'])
    parser.add_argument('--manifest',type=Path,default=ROOT/'assets'/'manifest.json')
    parser.add_argument('--sources',type=Path,default=ROOT/'assets'/'originals')
    parser.add_argument('--output',type=Path,default=ROOT/'generated'/'android-res')
    mode=parser.add_mutually_exclusive_group()
    mode.add_argument('--generate',action='store_true',help='Explicitly consent to paid API requests')
    mode.add_argument('--dry-run',action='store_true',help='Default: print only, no key or network')
    args=parser.parse_args()
    try:
        records=validate(json.loads(args.manifest.read_text(encoding='utf-8-sig')))
        if args.action=='generate': generate(records,args.sources,args.generate)
        else:
            if args.generate or args.dry_run: parser.error('generation flags only apply to generate')
            resize(records,args.sources,args.output)
    except (OSError, ValueError, RuntimeError) as exc:
        print('Error:', str(exc),file=sys.stderr)
        return 1
    return 0

if __name__=='__main__': sys.exit(main())
