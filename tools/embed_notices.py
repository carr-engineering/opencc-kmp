"""Deterministically include module-specific provenance/notices in actual published artifacts."""
import argparse
import json
from pathlib import Path
import tempfile
import zipfile
from upstream import ROOT, LOCK


def payloads(module, version):
    names=['LICENSE','NOTICE','AUTHORS']
    if module=='opencc-core': names+=['licenses/marisa.txt']
    elif module=='opencc-dictionary-tools': names+=['licenses/marisa.txt','licenses/darts-clone.txt']
    elif module=='opencc-jieba': names+=['licenses/CPPJIEBA-LICENSE']
    elif module=='opencc-cli': names+=['licenses/tclap.txt']
    result={name:(ROOT/name).read_bytes() for name in names}
    result['provenance.json']=(json.dumps({'schema_version':1,'module':module,'port_version':version,'upstream':LOCK},sort_keys=True,indent=2)+'\n').encode()
    return result


def embed(path, module, version):
    path=Path(path)
    if path.suffix not in {'.jar','.aar','.klib','.zip'}: return
    wanted=payloads(module, version)
    # Resource classifier archives already contain their complete original data notices.
    if path.suffix=='.zip':
        with zipfile.ZipFile(path) as archive:
            if 'opencc/manifest.json' in archive.namelist():
                assert all('opencc/'+name in archive.namelist() for name in ['LICENSE','NOTICE','AUTHORS'])
                return
    with zipfile.ZipFile(path) as archive:
        prefix='META-INF/opencc/'
        if path.suffix=='.klib':
            manifest=next(name for name in archive.namelist() if name.endswith('/manifest') or name=='manifest')
            base=manifest.rpartition('/')[0]
            prefix=(base+'/' if base else '')+'resources/META-INF/opencc/'
        entries={prefix+name:data for name,data in wanted.items()}
        if all(name in archive.namelist() and archive.read(name)==data for name,data in entries.items()): return
        info=archive.infolist()
        with tempfile.NamedTemporaryFile(dir=path.parent,suffix='.notices',delete=False) as stream: temporary=Path(stream.name)
        try:
            with zipfile.ZipFile(temporary,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as target:
                for item in info:
                    if item.filename not in entries: target.writestr(item,archive.read(item.filename))
                for name,data in sorted(entries.items()):
                    item=zipfile.ZipInfo(name,date_time=(1980,1,1,0,0,0));item.compress_type=zipfile.ZIP_DEFLATED;item.external_attr=0o644<<16
                    target.writestr(item,data,compresslevel=9)
            temporary.replace(path)
        finally: temporary.unlink(missing_ok=True)


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--module',required=True);parser.add_argument('--version',required=True)
    parser.add_argument('--manifest',type=Path,required=True)
    args=parser.parse_args()
    for artifact in json.loads(args.manifest.read_text()): embed(artifact,args.module,args.version)
