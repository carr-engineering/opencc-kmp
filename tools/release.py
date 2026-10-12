"""Prepare and verify signed Maven Central bundles; upload only with the explicit upload command."""
import argparse
import base64
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import tempfile
import subprocess
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
import xml.etree.ElementTree as ET
import zipfile
from upstream import ROOT, LOCK
from verify_publications import verify as verify_publications, SUFFIXES, check_artifact

CENTRAL = 'https://central.sonatype.com/api/v1/publisher'
ALGORITHMS = ('md5', 'sha1', 'sha256', 'sha512')
CLI_TARGETS = ('jvm', 'linuxX64', 'mingwX64', 'macosArm64', 'macosX64')


def version_for_tag(tag):
    if not re.fullmatch(r'v(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(?:-[0-9A-Za-z.-]+)?', tag):
        raise ValueError('Release tag must be vMAJOR.MINOR.PATCH, optionally with a prerelease suffix')
    version = tag[1:]
    if '-' in version:
        identifiers=version.split('-',1)[1].split('.')
        if any(not identifier or (identifier.isdigit() and len(identifier)>1 and identifier.startswith('0')) for identifier in identifiers):
            raise ValueError('Invalid semantic-version prerelease identifiers')
    if version.endswith('-SNAPSHOT'): raise ValueError('Snapshots cannot be released')
    return version


def check_tag(tag, expected_commit=None):
    version = version_for_tag(tag)
    commit = subprocess.check_output(['git', 'rev-parse', f'refs/tags/{tag}^{{commit}}'], cwd=ROOT, text=True).strip()
    head = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip()
    if commit != head or (expected_commit and expected_commit != commit):
        raise ValueError('The release tag, checked-out commit and expected CI commit must agree')
    print(json.dumps({'tag':tag, 'version':version, 'commit':commit}))
    return version


def release_configs():
    source=json.loads((ROOT/'docs/upstream-inventory.json').read_text())
    if source['upstream']!=LOCK: raise ValueError('Release config inventory does not match pinned upstream')
    configs=source['configs']
    if len(configs)!=23: raise ValueError('Expected the complete pinned 23-config release inventory')
    return configs


def pack_selections():
    return ';'.join(sorted(release_configs())+['s2t,t2s'])


def verify_required_packs(repository,group,version):
    base=repository.joinpath(*group.split('.'))
    for name in sorted(release_configs())+['s2t-t2s']:
        path=base/f'opencc-data-{name}'/version/f'opencc-data-{name}-{version}-resources.zip'
        if not path.is_file(): raise ValueError(f'Missing public selected pack {name}')
        validate_data_pack(path,f'opencc-data-{name}')


def validate_data_pack(path,module):
    configs=release_configs()
    selected=set(configs) if module=='opencc-data' else set(module.removeprefix('opencc-data-').split('-'))
    if not selected.issubset(configs): raise ValueError(f'{path}: unknown pack configurations')
    with zipfile.ZipFile(path) as archive:
        names=archive.namelist()
        if len(names)!=len(set(names)): raise ValueError(f'{path}: duplicate resources')
        manifest=json.loads(archive.read('opencc/manifest.json'))
        if manifest['upstream']!=LOCK or set(manifest['configs'])!=selected: raise ValueError(f'{path}: incomplete config selection/provenance')
        resources=set()
        for name in selected:
            expected=set(configs[name]['resources'])
            if configs[name]['extension']=='jieba': expected|={'jieba_dict/idf.utf8','jieba_dict/stop_words.utf8'}
            if set(manifest['configs'][name])!=expected: raise ValueError(f'{path}: incorrect dependency closure for {name}')
            if hashlib.sha256(archive.read('opencc/'+name+'.json')).hexdigest()!=configs[name]['sha256']:
                raise ValueError(f'{path}: changed upstream configuration {name}')
            resources|=expected
        expected_files=resources|{name+'.json' for name in selected}|{'LICENSE','NOTICE','AUTHORS',
            'licenses/marisa.txt','licenses/darts-clone.txt','licenses/cppjieba.txt'}
        if module=='opencc-data': expected_files|={'jieba_dict/jieba.dict.utf8','jieba_dict/user.dict.utf8'}
        if set(manifest['files'])!=expected_files: raise ValueError(f'{path}: missing or unrelated resources')
        if set(names)!={'opencc/'+name for name in expected_files|{'manifest.json'}}: raise ValueError(f'{path}: extra/missing archive entries')
        for name,spec in manifest['files'].items():
            data=archive.read('opencc/'+name)
            if len(data)!=spec['size'] or hashlib.sha256(data).hexdigest()!=spec['sha256']: raise ValueError(f'{path}: changed resource {name}')


def validate_pom(path, group, version):
    root = ET.fromstring(path.read_bytes())
    namespace = {'m':'http://maven.apache.org/POM/4.0.0'}
    def value(name):
        node = root.find('m:'+name.replace('/', '/m:'), namespace)
        return node.text.strip() if node is not None and node.text else ''
    for field in ['groupId','artifactId','version','name','description','url','licenses/license/name',
                  'licenses/license/url','developers/developer/id','developers/developer/name','scm/url','scm/connection']:
        if not value(field): raise ValueError(f'{path}: missing {field}')
    if value('groupId') != group or value('version') != version: raise ValueError(f'{path}: wrong coordinate')
    if not value('url').startswith('https://github.com/'): raise ValueError(f'{path}: missing public repository URL')
    if not path.with_name(f'{value("artifactId")}-{version}-sources.jar').exists(): raise ValueError(f'{path}: sources missing')
    if not path.with_name(f'{value("artifactId")}-{version}-javadoc.jar').exists(): raise ValueError(f'{path}: documentation missing')


def publication_files(repository, group, version):
    group_path = repository.joinpath(*group.split('.'))
    if not re.fullmatch(r'[A-Za-z][\w]*(?:\.[A-Za-z][\w]*)+', group): raise ValueError('Invalid Maven group')
    files = []
    for directory in sorted(group_path.glob(f'*/{version}')):
        poms = list(directory.glob('*.pom'))
        if len(poms) != 1: raise ValueError(f'{directory}: expected one POM')
        validate_pom(poms[0], group, version)
        for path in sorted(directory.iterdir()):
            if path.is_file() and path.suffix not in ['.asc','.md5','.sha1','.sha256','.sha512']:
                if path.suffix not in ['.pom','.module','.jar','.aar','.klib','.zip']:
                    raise ValueError(f'{path}: unexpected publication file')
                if path.suffix in ['.jar','.aar','.klib','.zip']:
                    coordinate=directory.parent.name
                    suffix=next((suffix for suffix in sorted(SUFFIXES.values(),key=len,reverse=True) if coordinate.endswith(suffix)),None)
                    module=coordinate[:-len(suffix)] if suffix else coordinate
                    check_artifact(path,module)
                    if path.suffix=='.zip' and module.startswith('opencc-data'): validate_data_pack(path,module)
                files.append(path)
    if not files: raise ValueError('No release publications found')
    return files


def inventory(repository, group, version, output):
    verify_publications(repository,list(SUFFIXES),group,version)
    verify_required_packs(repository,group,version)
    files=publication_files(repository,group,version)
    report={'schema_version':1,'group':group,'version':version,
            'commit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip(),
            'artifacts':{path.relative_to(repository).as_posix():hashlib.sha256(path.read_bytes()).hexdigest() for path in files}}
    output.parent.mkdir(parents=True,exist_ok=True);output.write_text(json.dumps(report,sort_keys=True,indent=2)+'\n')
    return report


def sign_file(path, key):
    command = ['gpg','--batch','--yes','--armor','--detach-sign','--local-user',key]
    password = os.environ.get('OPENCC_SIGNING_PASSWORD')
    if password is not None:
        command += ['--pinentry-mode','loopback','--passphrase-fd','0']
    subprocess.run(command + ['--output',str(path)+'.asc',str(path)],
                   input=(password+'\n').encode() if password is not None else None, check=True)


def verify_signature(path):
    subprocess.run(['gpg','--batch','--verify',str(path)+'.asc',str(path)],
                   stdout=subprocess.DEVNULL, stderr=subprocess.PIPE, check=True)


def prepare(repository, group, version, output, sign_key=None):
    verify_publications(repository, list(SUFFIXES), group, version)
    verify_required_packs(repository,group,version)
    files = publication_files(repository, group, version)
    members = []
    for path in files:
        if sign_key: sign_file(path, sign_key)
        if not Path(str(path)+'.asc').is_file(): raise ValueError(f'{path}: signature missing')
        verify_signature(path)
        members += [path, Path(str(path)+'.asc')]
        for algorithm in ALGORITHMS:
            checksum = Path(str(path)+'.'+algorithm)
            checksum.write_text(hashlib.new(algorithm,path.read_bytes()).hexdigest()+'\n')
            members.append(checksum)
    output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(output,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as archive:
        for path in sorted(members):
            info = zipfile.ZipInfo(path.relative_to(repository).as_posix(),date_time=(1980,1,1,0,0,0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o100644 << 16
            archive.writestr(info,path.read_bytes())
    if output.stat().st_size >= 1_000_000_000: raise ValueError('Central bundle exceeds the 1 GB upload limit')
    verify_bundle(output)
    report = {'schema_version':1,'group':group,'version':version,'upstream':LOCK,
              'commit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip(),
              'files':len(files),'bundle':output.name,'sha256':hashlib.sha256(output.read_bytes()).hexdigest()}
    output.with_suffix('.json').write_text(json.dumps(report,indent=2,sort_keys=True)+'\n')
    print(json.dumps(report))


def verify_bundle(path):
    with zipfile.ZipFile(path) as archive:
        names = archive.namelist()
        if len(names) != len(set(names)): raise ValueError('Duplicate bundle members')
        if any(name.startswith('/') or '..' in Path(name).parts for name in names): raise ValueError('Unsafe bundle path')
        payloads = [name for name in names if not name.endswith(tuple('.'+suffix for suffix in ('asc',)+ALGORITHMS))]
        if not payloads: raise ValueError('Empty bundle')
        for name in payloads:
            if name+'.asc' not in names: raise ValueError(f'{name}: signature missing')
            for algorithm in ALGORITHMS:
                expected = hashlib.new(algorithm,archive.read(name)).hexdigest()
                if archive.read(name+'.'+algorithm).decode().strip() != expected: raise ValueError(f'{name}: invalid {algorithm}')
    return len(payloads)


def request(endpoint, token, data=b'', content_type=None):
    headers = {'Authorization':'Bearer '+token}
    if content_type: headers['Content-Type'] = content_type
    try:
        with urllib.request.urlopen(urllib.request.Request(CENTRAL+endpoint,data=data,headers=headers,method='POST'), timeout=120) as response:
            return response.read()
    except urllib.error.HTTPError as error:
        raise RuntimeError(f'Central API request failed with HTTP {error.code}; inspect the portal deployment') from None


def upload(bundle, report, timeout):
    verify_bundle(bundle)
    if report.exists(): raise ValueError('Deployment report already exists; inspect/resume that deployment instead of uploading a duplicate')
    with tempfile.TemporaryDirectory(prefix='opencc-release-verify-') as temporary:
        with zipfile.ZipFile(bundle) as archive:
            archive.extractall(temporary)
            for name in archive.namelist():
                if name.endswith('.asc'): verify_signature(Path(temporary)/name[:-4])
    username = os.environ['CENTRAL_USERNAME']; password = os.environ['CENTRAL_PASSWORD']
    token = base64.b64encode((username+':'+password).encode()).decode()
    boundary = uuid.uuid4().hex
    body = (f'--{boundary}\r\nContent-Disposition: form-data; name="bundle"; filename="central-bundle.zip"\r\n'
            'Content-Type: application/octet-stream\r\n\r\n').encode()+bundle.read_bytes()+f'\r\n--{boundary}--\r\n'.encode()
    deployment = request('/upload?'+urllib.parse.urlencode({'publishingType':'AUTOMATIC','name':bundle.name}),token,body,'multipart/form-data; boundary='+boundary).decode().strip()
    if not re.fullmatch(r'[0-9a-f-]{36}', deployment): raise RuntimeError('Unexpected deployment identifier')
    report.parent.mkdir(parents=True,exist_ok=True)
    report.write_text(json.dumps({'deploymentId':deployment,'bundleSha256':hashlib.sha256(bundle.read_bytes()).hexdigest()})+'\n')
    deadline = time.monotonic()+timeout
    while time.monotonic() < deadline:
        state = json.loads(request('/status?'+urllib.parse.urlencode({'id':deployment}),token))
        report.write_text(json.dumps(state,indent=2,sort_keys=True)+'\n')
        status = state['deploymentState']
        print(json.dumps({'deploymentId':deployment,'deploymentState':status}),flush=True)
        if status == 'PUBLISHED': return
        if status == 'FAILED': raise RuntimeError('Central rejected the deployment; see saved validation report')
        time.sleep(10)
    raise TimeoutError('Central deployment still pending; inspect the saved deployment ID before retrying')


def collect(downloaded, version):
    sources = [path for path in downloaded.rglob('maven') if path.is_dir() and list(path.rglob('*.module'))]
    if len(sources) != 1: raise ValueError('Expected one staged Maven repository')
    destination = ROOT/'.cache/release/maven'
    if destination.exists(): raise ValueError('Refusing to overwrite an existing release stage')
    shutil.copytree(sources[0],destination)
    inventories=list(downloaded.rglob('staged-inventory.json'))
    if len(inventories)!=1: raise ValueError('Expected one reviewed staged inventory')
    inventory=json.loads(inventories[0].read_text())
    commit=subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip()
    if inventory['commit']!=commit or inventory['version']!=version: raise ValueError('Staged inventory commit/version mismatch')
    for name,digest in inventory['artifacts'].items():
        if name.startswith('/') or '..' in Path(name).parts: raise ValueError('Unsafe staged artifact path')
        if hashlib.sha256((destination/name).read_bytes()).hexdigest()!=digest: raise ValueError(f'{name}: staged hash mismatch')
    shutil.copyfile(inventories[0],ROOT/'.cache/release/staged-inventory.json')
    assets = ROOT/'.cache/release/assets'; assets.mkdir(parents=True,exist_ok=True)
    for target in CLI_TARGETS:
        name = f'opencc-kmp-{version}-{target}-cli.zip'
        candidates = list(downloaded.rglob(name))
        if len(candidates) != 1: raise ValueError(f'Expected exactly one {name}')
        shutil.copyfile(candidates[0],assets/name)


def prepare_assets(version, key):
    if not key: raise ValueError('OPENCC_SIGNING_KEY_ID must identify the release key')
    assets = ROOT/'.cache/release/assets'
    checksums = []
    for target in CLI_TARGETS:
        path = assets/f'opencc-kmp-{version}-{target}-cli.zip'
        with zipfile.ZipFile(path) as archive:
            names = archive.namelist()
            for notice in ['LICENSE','NOTICE','AUTHORS']:
                if archive.read(notice) != (ROOT/notice).read_bytes(): raise ValueError(f'{path}: incorrect attribution')
            for tool in ['opencc','opencc_dict','opencc_phrase_extract']:
                name = 'bin/'+tool+('.exe' if target=='mingwX64' else '')
                if name not in names: raise ValueError(f'{path}: missing {tool}')
            if target!='jvm' and not any(name.startswith('share/opencc/') for name in names): raise ValueError(f'{path}: offline data missing')
            if target=='jvm' and not any('opencc-data' in name for name in names if name.startswith('lib/')): raise ValueError(f'{path}: offline data missing')
        sign_file(path,key); verify_signature(path)
        checksums.append(hashlib.sha256(path.read_bytes()).hexdigest()+'  '+path.name)
    (assets/'SHA256SUMS').write_text('\n'.join(checksums)+'\n')
    sign_file(assets/'SHA256SUMS',key); verify_signature(assets/'SHA256SUMS')
    public = subprocess.check_output(['gpg','--batch','--armor','--export',key])
    if not public: raise ValueError('Release public key missing')
    (assets/'release-key.asc').write_bytes(public)
    (assets/'RELEASE_NOTES.md').write_text(f'OpenCC KMP {version}\n\nUpstream OpenCC {LOCK["tag"]}, commit `{LOCK["commit"]}`.\n\n'
        'Maven artifacts are published to Central; CLI ZIPs include the complete offline data bundle. '
        'Verify CLI downloads against SHA256SUMS and its detached signature with the release public key.\n')


def import_key():
    key = os.environ['OPENCC_SIGNING_KEY']
    subprocess.run(['gpg','--batch','--import'],input=key.encode(),stdout=subprocess.DEVNULL,stderr=subprocess.PIPE,check=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest='command',required=True)
    commands.add_parser('pack-selections')
    commands.add_parser('import-key')
    staged=commands.add_parser('inventory');staged.add_argument('--repository',type=Path,default=ROOT/'.cache/release/maven')
    staged.add_argument('--group',required=True);staged.add_argument('--version',required=True)
    staged.add_argument('--output',type=Path,default=ROOT/'.cache/release/staged-inventory.json')
    gather = commands.add_parser('collect'); gather.add_argument('--downloaded',type=Path,required=True); gather.add_argument('--version',required=True)
    assets = commands.add_parser('prepare-assets'); assets.add_argument('--version',required=True)
    tag = commands.add_parser('check-tag'); tag.add_argument('tag'); tag.add_argument('--commit')
    build = commands.add_parser('prepare')
    build.add_argument('--repository',type=Path,default=ROOT/'.cache/release/maven')
    build.add_argument('--group',required=True); build.add_argument('--version',required=True)
    build.add_argument('--output',type=Path,default=ROOT/'.cache/release/central-bundle.zip')
    build.add_argument('--sign-key',default=os.environ.get('OPENCC_SIGNING_KEY_ID'))
    verify = commands.add_parser('verify-bundle'); verify.add_argument('bundle',type=Path)
    deploy = commands.add_parser('upload'); deploy.add_argument('bundle',type=Path)
    deploy.add_argument('--report',type=Path,default=ROOT/'.cache/release/deployment.json')
    deploy.add_argument('--timeout',type=int,default=1800)
    args = parser.parse_args()
    if args.command == 'pack-selections': print(pack_selections())
    elif args.command == 'inventory': inventory(args.repository,args.group,args.version,args.output)
    elif args.command == 'import-key': import_key()
    elif args.command == 'collect': collect(args.downloaded,args.version)
    elif args.command == 'prepare-assets': prepare_assets(args.version,os.environ.get('OPENCC_SIGNING_KEY_ID'))
    elif args.command == 'check-tag': check_tag(args.tag,args.commit)
    elif args.command == 'prepare': prepare(args.repository,args.group,args.version,args.output,args.sign_key)
    elif args.command == 'verify-bundle': print(json.dumps({'verifiedFiles':verify_bundle(args.bundle)}))
    else: upload(args.bundle,args.report,args.timeout)
