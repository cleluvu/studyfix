from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED
import hashlib
import json
import uuid

workspace = Path(__file__).resolve().parent.parent
project = workspace / 'StudyFix'
out_dir = workspace / 'deliverables'
output = out_dir / 'StudyFix_Source_De_Sua_Code_2026-11-05.zip'
if output.exists():
    output = output.with_stem(output.stem + '_' + uuid.uuid4().hex[:8])

blocked_dirs = {'build', 'build-launcher', 'dist', 'data', 'private', '.git',
                'node_modules', '__pycache__', '.cache', '.idea'}
blocked_suffixes = {'.db', '.sqlite', '.sqlite3', '.p12', '.pfx', '.jks', '.pem',
                    '.key', '.log', '.class', '.zip', '.docx', '.pptx', '.pdf'}

def allowed(relative):
    parts = relative.parts
    name = relative.name.lower()
    if any(part.lower() in blocked_dirs for part in parts):
        return False
    if name.startswith('.env') and name != '.env.example':
        return False
    if relative.suffix.lower() in blocked_suffixes or name.endswith(('-wal', '-shm')):
        return False
    if parts[0] == 'server':
        return (len(parts) == 2 and (relative.suffix.lower() in {'.ps1', '.xml', '.mf'}
                                   or name in {'.gitignore', '.env.example'})) or (
            len(parts) > 2 and parts[1] in {'src', 'lib', 'nbproject', 'test', 'db'})
    if parts[0] in {'web-client', '.github'}:
        return True
    if parts[0] == 'docs':
        return relative.suffix.lower() in {'.md', '.html', '.css', '.js', '.json', '.svg'}
    return len(parts) == 1 and name in {'readme.md', '.gitignore'}

files = sorted((p for p in project.rglob('*') if p.is_file() and allowed(p.relative_to(project))),
               key=lambda p: p.relative_to(project).as_posix())
manifest = []
with ZipFile(output, 'w', ZIP_DEFLATED, compresslevel=6) as archive:
    for path in files:
        name = 'StudyFix/' + path.relative_to(project).as_posix()
        content = path.read_bytes()
        archive.writestr(name, content)
        manifest.append(hashlib.sha256(content).hexdigest() + '  ' + name)
    guide = out_dir / 'HUONG_DAN_NHAN_SOURCE_VI.md'
    archive.writestr(guide.name, guide.read_bytes())
    archive.writestr('SOURCE_MANIFEST_SHA256.txt', '\n'.join(manifest) + '\n')

with ZipFile(output) as archive:
    assert archive.testzip() is None, 'ZIP CRC validation failed'
    names = set(archive.namelist())
    for required in ['server/src/studyfix/StudyFixGateway.java',
                     'server/lib/sqlite-jdbc-3.53.0.0.jar',
                     'server/lib/mysql-connector-j-9.5.0.jar',
                     'server/build.xml', 'server/run-server-manager.ps1',
                     'server/.env.example', 'web-client/static/index.html']:
        assert 'StudyFix/' + required in names, required
    for line in manifest:
        digest, name = line.split('  ', 1)
        assert hashlib.sha256(archive.read(name)).hexdigest() == digest, name
    for name in names:
        if name.startswith('StudyFix/'):
            assert allowed(Path(name.removeprefix('StudyFix/'))), name

digest = hashlib.sha256(output.read_bytes()).hexdigest()
output.with_suffix('.zip.sha256').write_text(digest + '  ' + output.name + '\n', encoding='utf-8')
print(json.dumps({'zip': str(output), 'files': len(files), 'sizeBytes': output.stat().st_size,
                  'sha256': digest, 'validation': 'CRC, manifest and excluded-file checks passed'}))
