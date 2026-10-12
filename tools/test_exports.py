"""Run direct JS and Swift consumers of the built focused export artifacts, without Kotlin wrappers."""
import argparse
import gzip
import hashlib
import io
import json
from pathlib import Path
import platform
import subprocess
import tempfile
import zipfile
from upstream import ROOT


def verify(js=True, swift=True):
    binary=ROOT/'opencc-data/build/generated/resources/opencc/STCharacters.ocd2'
    if not binary.is_file(): raise ValueError('Publish/generate complete data before testing the binary resource interface')
    report={'schema_version':1,'host':platform.platform(),'dictionary_sha256':hashlib.sha256(binary.read_bytes()).hexdigest(),
            'limitations':['Export library artifact sizes are not incremental application or physical-device performance measurements']}
    if js:
        folder=ROOT/'interop/build/dist/js/productionLibrary'
        declaration=(folder/'opencc-interop.d.mts').read_text()
        assert 'data: Int8Array' in declaration and 'convertUtf8(bytes: Int8Array): Int8Array' in declaration
        assert 'export declare class OpenCCResources' in declaration
        output=subprocess.check_output(['node',str(ROOT/'interop/consumers/node.mjs'),str(folder/'opencc-interop.mjs'),str(binary)],text=True)
        modules={path.name:{'raw_bytes':path.stat().st_size,'gzip_bytes':len(gzip.compress(path.read_bytes(),mtime=0))}
                 for path in sorted(folder.glob('*.mjs'))}
        report['javascript']={'execution':output.strip(),'modules':modules,'raw_bytes':sum(v['raw_bytes'] for v in modules.values()),
                              'separate_module_gzip_bytes':sum(v['gzip_bytes'] for v in modules.values())}
    if swift:
        framework=ROOT/'interop/build/bin/macosArm64/releaseFramework/OpenCCInterop.framework'
        header=(framework/'Headers/OpenCCInterop.h').read_text()
        for name in ['Config','Dict','Converter','ConversionChain','SimpleConverter']:
            assert f'swift_name("{name}")' not in header,('Unexpected broad dependency export',name)
        with tempfile.TemporaryDirectory(prefix='opencc-exports-') as temporary:
            executable=Path(temporary)/'swift-consumer'
            subprocess.run(['xcrun','swiftc',str(ROOT/'interop/consumers/main.swift'),'-F',str(framework.parent),
                            '-module-cache-path',str(Path(temporary)/'module-cache'),'-framework','OpenCCInterop','-Xlinker','-dead_strip','-o',str(executable)],check=True)
            output=subprocess.check_output([str(executable),str(binary)],text=True)
            packed=io.BytesIO()
            with zipfile.ZipFile(packed,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as archive:
                for path in sorted(framework.rglob('*')):
                    if path.is_file(): archive.writestr(path.relative_to(framework).as_posix(),path.read_bytes())
            report['swift']={'execution':output.strip(),'consumer_executable_bytes':executable.stat().st_size,
                             'framework_raw_bytes':sum(p.stat().st_size for p in framework.rglob('*') if p.is_file()),
                             'framework_zip_bytes':len(packed.getvalue())}
    packages={}
    for archive_path in sorted((ROOT/'interop/build/distributions').glob('*.zip')):
        if archive_path.name.endswith('-apple.zip') and not swift: continue
        with zipfile.ZipFile(archive_path) as archive:
            names=archive.namelist()
            assert len(names)==len(set(names)),('Duplicate export package entries',archive_path)
            for name in ['LICENSE','NOTICE','AUTHORS','licenses/marisa.txt','licenses/rapidjson.txt']:
                assert name in names,(archive_path,'missing attribution',name)
            if archive_path.name.endswith('-js.zip'):
                manifest=json.loads(archive.read('package.json'))
                assert not manifest.get('devDependencies'),('Build-only TypeScript compiler leaked into runtime package',archive_path)
                assert not any(name.endswith('.map') for name in names),('Production source maps retained',archive_path)
                with tempfile.TemporaryDirectory(prefix='opencc-js-package-') as temporary:
                    archive.extractall(temporary)
                    output=subprocess.check_output(['node',str(ROOT/'interop/consumers/node.mjs'),
                        str(Path(temporary)/'opencc-interop.mjs'),str(binary)],text=True)
            else:
                assert 'OpenCCInterop.xcframework/Info.plist' in names
                with tempfile.TemporaryDirectory(prefix='opencc-apple-package-') as temporary:
                    archive.extractall(temporary)
                    framework=Path(temporary)/'OpenCCInterop.xcframework'
                    info=json.loads(subprocess.check_output(['plutil','-convert','json','-o','-',str(framework/'Info.plist')]))
                    libraries=info['AvailableLibraries']
                    assert len(libraries)==3 and {entry['SupportedPlatform'] for entry in libraries}=={'ios','macos'}
                    simulator=next(entry for entry in libraries if entry.get('SupportedPlatformVariant')=='simulator')
                    assert set(simulator['SupportedArchitectures'])=={'arm64','x86_64'}
                    native=next(entry for entry in libraries if entry['SupportedPlatform']=='macos')
                    assert set(native['SupportedArchitectures'])=={'arm64','x86_64'}
                    mac_framework=framework/native['LibraryIdentifier']/native['LibraryPath']
                    executable=Path(temporary)/'packaged-swift-consumer'
                    subprocess.run(['xcrun','swiftc',str(ROOT/'interop/consumers/main.swift'),'-F',str(mac_framework.parent),
                        '-module-cache-path',str(Path(temporary)/'module-cache'),'-framework','OpenCCInterop',
                        '-Xlinker','-dead_strip','-o',str(executable)],check=True)
                    output=subprocess.check_output([str(executable),str(binary)],text=True).strip()+'; XCFramework architecture closure validated'
            packages[archive_path.name]={'bytes':archive_path.stat().st_size,'sha256':hashlib.sha256(archive_path.read_bytes()).hexdigest(),
                                         'verification':output.strip()}
    report['packages']=packages
    destination=ROOT/'.cache/exports/report.json';destination.parent.mkdir(parents=True,exist_ok=True)
    destination.write_text(json.dumps(report,sort_keys=True,indent=2)+'\n')
    print(json.dumps(report,sort_keys=True,indent=2))
    return report


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--js-only',action='store_true')
    args=parser.parse_args();verify(swift=not args.js_only)
