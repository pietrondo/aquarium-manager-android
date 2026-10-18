import base64
import importlib.util
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
from PIL import Image

SCRIPT = Path(__file__).resolve().parents[1] / 'scripts' / 'assets.py'
spec = importlib.util.spec_from_file_location('assets_tool', SCRIPT)
a = importlib.util.module_from_spec(spec)
if SCRIPT.exists():
    spec.loader.exec_module(a)

class AssetTests(unittest.TestCase):
    def test_implementation_exists(self):
        self.assertTrue(SCRIPT.exists(), 'asset tool is not implemented')

    @unittest.skipUnless(SCRIPT.exists(), 'implementation not yet written')
    def test_manifest_rejects_traversal_and_duplicate(self):
        for records in [[{'name':'../x','kind':'ui','description':'fish'}],
                        [{'name':'x','kind':'ui','description':'fish'}]*2]:
            with self.assertRaises(ValueError): a.validate(records)

    @unittest.skipUnless(SCRIPT.exists(), 'implementation not yet written')
    def test_dry_run_never_calls_api_or_writes(self):
        with tempfile.TemporaryDirectory() as tmp, patch.object(a, 'request_image') as req:
            a.generate([{'name':'fish','kind':'ui','description':'fish'}], Path(tmp), False)
            req.assert_not_called()
            self.assertEqual(list(Path(tmp).iterdir()), [])

    @unittest.skipUnless(SCRIPT.exists(), 'implementation not yet written')
    def test_mock_generation_and_cache(self):
        with tempfile.TemporaryDirectory() as tmp, patch.object(a, 'request_image', return_value=Image.new('RGBA',(48,48))) as req:
            records=[{'name':'fish','kind':'ui','description':'fish'}]
            with patch.dict('os.environ', {'PIXELLAB_API_KEY':'test-secret'}):
                a.generate(records, Path(tmp), True)
                a.generate(records, Path(tmp), True)
            req.assert_called_once()
            self.assertTrue((Path(tmp)/'fish.png').exists())

    @unittest.skipUnless(SCRIPT.exists(), 'implementation not yet written')
    def test_sizes_and_nearest_palette(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp); src=root/'src'; src.mkdir()
            for name in ['app_icon','fish']:
                im=Image.new('RGBA',(48,48),'#0b2030'); im.putpixel((20,20),(255,100,90,255)); im.save(src/f'{name}.png')
            a.resize([{'name':'app_icon','kind':'launcher','description':'tank'}, {'name':'fish','kind':'ui','description':'fish'}],src,root/'out')
            for density,size in a.UI_SIZES.items():
                with Image.open(root/'out'/f'drawable-{density}'/'fish.png') as im:
                    self.assertEqual(im.size,(size,size)); self.assertLessEqual(len(im.getcolors()),2)
            for density,size in a.LAUNCHER_SIZES.items():
                with Image.open(root/'out'/f'mipmap-{density}'/'app_icon.png') as im: self.assertEqual(im.size,(size,size))
            with Image.open(root/'out'/'store'/'app_icon.png') as im: self.assertEqual(im.size,(512,512))

    @unittest.skipUnless(SCRIPT.exists(), 'implementation not yet written')
    def test_wrong_aspect_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            src=Path(tmp); Image.new('RGBA',(48,32)).save(src/'fish.png')
            with self.assertRaises(ValueError): a.resize([{'name':'fish','kind':'ui','description':'fish'}],src,src/'out')

    @unittest.skipUnless(SCRIPT.exists(), 'implementation not yet written')
    def test_exact_request_response_and_timeout(self):
        buf=io.BytesIO(); Image.new('RGBA',(48,48)).save(buf,format='PNG')
        payload=json.dumps({'image':{'type':'base64','base64':'data:image/png;base64,'+base64.b64encode(buf.getvalue()).decode()}}).encode()
        with patch.object(a.urllib.request.OpenerDirector, 'open') as op:
            op.return_value.__enter__.return_value.read.return_value=payload
            self.assertEqual(a.request_image('key','tank').size,(48,48))
            req=op.call_args.args[0]
            self.assertEqual(req.full_url,'https://api.pixellab.ai/v2/create-image-pixflux')
            self.assertEqual(json.loads(req.data)['image_size'],{'width':48,'height':48})
            self.assertEqual(op.call_args.kwargs['timeout'],90)

    @unittest.skipUnless(SCRIPT.exists(), 'implementation not yet written')
    def test_http_and_malformed_response_errors(self):
        import urllib.error
        with patch.object(a.urllib.request.OpenerDirector,'open',side_effect=urllib.error.HTTPError('url',402,'credits',{},None)):
            with self.assertRaisesRegex(RuntimeError,'402'): a.request_image('key','tank')
        with patch.object(a.urllib.request.OpenerDirector,'open') as op:
            op.return_value.__enter__.return_value.read.return_value=b'{"image":{}}'
            with self.assertRaises(ValueError): a.request_image('key','tank')

    def test_redirects_never_forward_credentials_or_follow_downgrades(self):
        from email.message import Message
        from urllib.response import addinfourl
        for target in ['https://other.invalid/collect', 'http://other.invalid/collect']:
            with self.subTest(target=target):
                sent = []
                def transport(handler, request):
                    sent.append(request)
                    headers = Message()
                    headers['Location'] = target
                    response = addinfourl(io.BytesIO(b'{}'), headers,
                                          request.full_url, 302 if len(sent) == 1 else 200)
                    response.msg = 'Found' if len(sent) == 1 else 'OK'
                    return response
                with patch.object(a.urllib.request.HTTPSHandler, 'https_open', transport), \
                     patch.object(a.urllib.request.HTTPHandler, 'http_open', transport):
                    error = None
                    try:
                        a.request_image('redirect-test-secret', 'tank')
                    except (RuntimeError, ValueError) as exc:
                        error = exc
                self.assertEqual(len(sent), 1, 'Redirect must never reach another transport')
                self.assertEqual(sent[0].full_url, a.ENDPOINT)
                self.assertIsInstance(error, RuntimeError)
                self.assertIn('302', str(error))
                self.assertNotIn('redirect-test-secret', str(error))
                self.assertNotIn('other.invalid', str(error))

if __name__ == '__main__': unittest.main()
