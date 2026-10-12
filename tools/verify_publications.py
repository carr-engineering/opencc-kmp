"""Verify latest local publications: checksums, embedded notices/provenance and runtime graphs."""
import argparse
import hashlib
import io
import json
from pathlib import Path
import zipfile
from upstream import ROOT, LOCK

SUFFIXES={'jvm':'-jvm','android':'-android','js':'-js','wasmJs':'-wasm-js','macosArm64':'-macosarm64','macosX64':'-macosx64',
          'iosArm64':'-iosarm64','iosSimulatorArm64':'-iossimulatorarm64','iosX64':'-iosx64','linuxX64':'-linuxx64','mingwX64':'-mingwx64'}


def target_coordinates(module,targets):
    return {module+SUFFIXES[target] for target in targets
            if module!='opencc-cli' or target in {'jvm','macosArm64','macosX64','linuxX64','mingwX64'}}


def check_root_variants(module,data,targets):
    redirects={variant['available-at']['module'] for variant in data['variants']
               if variant.get('available-at',{}).get('group')=='io.github.opencckmp'}
    required=target_coordinates(module,targets)
    assert required.issubset(redirects),(module,'Root metadata omits target variants',sorted(required-redirects))


def check_artifact(path,module):
    with zipfile.ZipFile(path) as artifact:
        names=artifact.namelist()
        prefix='META-INF/opencc/'
        if path.suffix=='.klib':
            manifest=next(name for name in names if name.endswith('/manifest') or name=='manifest')
            base=manifest.rpartition('/')[0]
            prefix=(base+'/' if base else '')+'resources/META-INF/opencc/'
        if path.suffix=='.zip' and 'opencc/manifest.json' in names:
            assert all('opencc/'+name in names for name in ['LICENSE','NOTICE','AUTHORS'])
            return
        for name in ['LICENSE','NOTICE','AUTHORS']:
            assert prefix+name in names,(path,name)
            assert artifact.read(prefix+name)==(ROOT/name).read_bytes(),(path,name,'stale notice')
        provenance=json.loads(artifact.read(prefix+'provenance.json'))
        assert provenance['upstream']==LOCK,(path,'upstream')
        if module=='opencc-core': assert prefix+'licenses/marisa.txt' in names,path
        elif module=='opencc-dictionary-tools':
            assert all(prefix+name in names for name in ['licenses/marisa.txt','licenses/darts-clone.txt']),path
        elif module=='opencc-jieba': assert prefix+'licenses/CPPJIEBA-LICENSE' in names,path
        elif module=='opencc-cli': assert prefix+'licenses/tclap.txt' in names,path
        if path.suffix=='.aar' and 'classes.jar' in names:
            with zipfile.ZipFile(io.BytesIO(artifact.read('classes.jar'))) as classes:
                assert all(info.compress_type==zipfile.ZIP_STORED for info in classes.infolist()),(path,'nested class compression')


def verify(repository,targets):
    assert set(targets).issubset(SUFFIXES),('Unknown targets',targets)
    group=repository/'io/github/opencckmp'
    checked=[]
    # Match Wasm before JS: '-wasm-js' also ends in '-js'.
    suffixes=sorted(SUFFIXES.values(),key=len,reverse=True)
    for directory in sorted(group.iterdir()):
        suffix=next((suffix for suffix in suffixes if directory.name.endswith(suffix)),None)
        if suffix is not None and suffix not in [SUFFIXES[target] for target in targets]: continue
        metadata=list(directory.glob('*/*.module'))
        if not metadata: continue
        latest=max(metadata,key=lambda path:path.stat().st_mtime_ns)
        data=json.loads(latest.read_text())
        module=directory.name[:-len(suffix)] if suffix else directory.name
        if suffix is None: check_root_variants(module,data,targets)
        files={file['name']:file for variant in data['variants'] for file in variant.get('files',[])}
        for name,spec in files.items():
            path=latest.parent/spec['url']
            if not path.exists() and latest.parent.name.endswith('-SNAPSHOT'):
                resolved=latest.name.removeprefix(directory.name+'-').removesuffix('.module')
                path=latest.parent/spec['url'].replace(latest.parent.name,resolved)
            content=path.read_bytes()
            assert len(content)==spec['size'],(path,'size')
            for algorithm in ['sha512','sha256','sha1','md5']:
                if algorithm in spec: assert hashlib.new(algorithm,content).hexdigest()==spec[algorithm],(path,algorithm)
            if path.suffix in ['.jar','.aar','.klib','.zip']: check_artifact(path,module)
        all_dependencies=[dependency for variant in data['variants'] for dependency in variant.get('dependencies',[])]
        dependencies=[dependency['module'] for dependency in all_dependencies if dependency.get('group')=='io.github.opencckmp']
        if module=='opencc-core':
            assert all(dependency['group']=='org.jetbrains.kotlin' and dependency['module']=='kotlin-stdlib'
                       for dependency in all_dependencies),(directory,'core must remain stdlib-only',all_dependencies)
        if module in ['opencc-platform','opencc-cache','opencc-jieba','opencc-zip','opencc-detect']:
            assert all(name.startswith('opencc-core') for name in dependencies),(directory,dependencies)
        if module=='opencc-all':
            required={'opencc-core','opencc-platform','opencc-cache','opencc-jieba','opencc-zip','opencc-dictionary-tools','opencc-data','opencc-phrase-extract'}
            assert required.issubset(set(dependencies)),(directory,dependencies)
        checked.append({'coordinate':directory.name,'artifacts':sorted(files),'bytes':sum(value['size'] for value in files.values())})
    coordinates={row['coordinate'] for row in checked}
    modules={'opencc-core','opencc-platform','opencc-cache','opencc-jieba','opencc-zip','opencc-detect',
             'opencc-dictionary-tools','opencc-phrase-extract','opencc-data','opencc-all','opencc-cli'}
    expected=set(modules)
    for module in modules: expected.update(target_coordinates(module,targets))
    assert expected.issubset(coordinates),('Missing publications',sorted(expected-coordinates))
    report={'schema_version':1,'targets':targets,'publications':checked,'upstream':LOCK}
    destination=ROOT/'.cache/publications-verification.json';destination.write_text(json.dumps(report,sort_keys=True,indent=2)+'\n')
    print(json.dumps({'publications':len(checked),'artifacts':sum(len(row['artifacts']) for row in checked)}))
    return report


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--repository',type=Path,default=ROOT/'.cache/maven')
    parser.add_argument('--targets',default='jvm,android,js,wasmJs,macosArm64,iosSimulatorArm64')
    args=parser.parse_args();verify(args.repository,args.targets.split(','))
