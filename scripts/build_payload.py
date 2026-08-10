from pathlib import Path
import zipfile

root = Path(__file__).resolve().parents[1]
out = root / 'manager' / 'payload.zip'
include = [
    'agent', 'config', 'Dockerfile', 'docker-compose.yml', '.env.example',
    'docs/Haya_Saadeh_CV.pdf'
]
with zipfile.ZipFile(out, 'w', zipfile.ZIP_DEFLATED) as z:
    for rel in include:
        p = root / rel
        if p.is_dir():
            for f in sorted(p.rglob('*')):
                if f.is_file():
                    arc = f.relative_to(root)
                    if str(arc) == 'docs/Haya_Saadeh_CV.pdf':
                        arc = Path('documents/Haya_Saadeh_CV.pdf')
                    z.write(f, arc)
        elif p.is_file():
            arc = Path('documents/Haya_Saadeh_CV.pdf') if rel == 'docs/Haya_Saadeh_CV.pdf' else Path(rel)
            z.write(p, arc)
print(out)
