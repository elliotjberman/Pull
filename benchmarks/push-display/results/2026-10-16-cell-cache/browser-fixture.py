"""Isolated browser verification fixture; never reads the live Pull debug directory."""
from pathlib import Path
from functools import partial
from http.server import ThreadingHTTPServer
from http import HTTPStatus
import importlib.util,json,struct,zlib,threading
ROOT=Path.cwd(); TEST=ROOT/'target/display-benchmark/round2/browser-check'
spec=importlib.util.spec_from_file_location('pull_debug_server',ROOT/'tools/push-debug-surface-server.py')
module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)

def png(rgb):
    def chunk(kind,data):return struct.pack('>I',len(data))+kind+data+struct.pack('>I',zlib.crc32(kind+data)&0xffffffff)
    pixels=b''.join(b'\0'+bytes([*rgb,255])*960 for _ in range(160))
    return b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',960,160,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(pixels))+chunk(b'IEND',b'')

def publish(revision,content,rgb=None):
    if rgb is not None:
        temp=TEST/'latest.png.tmp';temp.write_bytes(png(rgb));temp.replace(TEST/'latest.png')
    temp=TEST/'latest-frame.txt.tmp';temp.write_text(f'{revision}\t960\t160\t1\t{revision}\t0\t0\t0\t{content}\n');temp.replace(TEST/'latest-frame.txt')

if __name__=='__main__':
    TEST.mkdir(parents=True,exist_ok=True)
    (TEST/'allow-image').unlink(missing_ok=True)
    publish(1,'test-epoch-1',(20,150,100))
    (TEST/'surface-state.json').write_text(json.dumps({'connected':True,'revision':1,'lights':{},'pressed':[],'events':[]}))
    records=[];lock=threading.Lock()
    class Handler(module.PushSurfaceHandler):
        def _serve_display(self):
            success=(TEST/'allow-image').exists()
            with lock:
                records.append({'request':self.path,'status':200 if success else 503})
                temp=TEST/'requests.json.tmp';temp.write_text(json.dumps(records));temp.replace(TEST/'requests.json')
            if success:super()._serve_display()
            else:self.send_error(HTTPStatus.SERVICE_UNAVAILABLE,'Tested retry condition')
    factory=partial(Handler,directory=str(ROOT/'tools/push-debug-surface-app'),authority='127.0.0.1:8766',origin='http://127.0.0.1:8766',debug_dir=TEST,font_dir=None,lease_active=lambda:False)
    print('Isolated fixture: http://127.0.0.1:8766',flush=True)
    ThreadingHTTPServer(('127.0.0.1',8766),factory).serve_forever()
