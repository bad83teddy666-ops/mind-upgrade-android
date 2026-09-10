"""Bundle a pinned offline acoustic model; never download models on the phone."""
import hashlib
from pathlib import Path
import urllib.request
import zipfile
import shutil

root = Path(__file__).resolve().parents[1]
archive = root / 'build' / 'bibi-model.zip'
archive.parent.mkdir(exist_ok=True)
expected = '30f26242c4eb449f948e42cb302dd7a686cb29a3423a8367f99ff41780942498'
if not archive.exists():
    urllib.request.urlretrieve('https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip', archive)
if hashlib.sha256(archive.read_bytes()).hexdigest() != expected:
    raise RuntimeError('Unexpected Bibi model checksum')
target = root / 'app/src/main/assets/bibi-model'
target.mkdir(parents=True, exist_ok=True)
with zipfile.ZipFile(archive) as z:
    for info in z.infolist():
        relative = Path(info.filename).relative_to('vosk-model-small-en-us-0.15')
        if '..' in relative.parts or relative.is_absolute():
            raise RuntimeError('Invalid archive path')
        if info.is_dir():
            (target / relative).mkdir(parents=True, exist_ok=True)
        else:
            destination = target / relative
            destination.parent.mkdir(parents=True, exist_ok=True)
            with z.open(info) as source, destination.open('wb') as output:
                shutil.copyfileobj(source, output)
print('Verified and bundled Bibi offline model')
