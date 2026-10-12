"""Differential execution of Kotlin tool processes against the pinned native executables."""
import argparse
import json
import os
from pathlib import Path
import random
import subprocess
import sys
import zipfile
from upstream import ROOT

TOOLS = {'opencc':'io.github.opencckmp.cli.MainKt', 'opencc_dict':'io.github.opencckmp.cli.DictionaryCommand', 'opencc_phrase_extract':'io.github.opencckmp.cli.PhraseCommand'}
BACKEND = 'jvm'
NATIVE_TARGET = 'linuxX64' if sys.platform.startswith('linux') else 'mingwX64' if sys.platform=='win32' else 'macosArm64'


def run(tool, args, input_bytes, native):
    if native:
        command = [tool, *args]
        executable = str(ROOT/'.cache/oracle/src/tools'/tool)
    elif BACKEND == 'jvm':
        classpath = (ROOT/'opencc-cli/build/reports/jvm-classpath.txt').read_text()
        command = ['java','-cp',classpath,TOOLS[tool],*args]
        executable = None
    else:
        extension = '.exe' if NATIVE_TARGET=='mingwX64' else '.kexe'
        command = [str(ROOT/'opencc-cli/build/bin'/NATIVE_TARGET/(tool+'ReleaseExecutable')/(tool+extension)), *args]
        executable = None
    environment = os.environ.copy()
    environment['OPENCC_SEGMENTATION_PLUGIN_PATH'] = str(ROOT/'.cache/oracle/plugins/jieba')
    environment['OPENCC_DATA_DIR'] = str(ROOT/'.cache/conformance/jieba/opencc')
    return subprocess.run(command, executable=executable, input=input_bytes, capture_output=True, cwd=ROOT, env=environment)


def verify():
    target=ROOT/'.cache/cli-contract'
    target.mkdir(parents=True,exist_ok=True)
    cfg=target/'config.json'
    dictionary=target/'dict.txt'
    dictionary.write_text('汉\t漢\n字\t字\n里\t里 裏\nabc\tABC\n')
    cfg.write_text(json.dumps({'name':'CLI','segmentation':{'type':'mmseg','dict':{'type':'text','file':'dict.txt'}},'conversion_chain':[{'dict':{'type':'text','file':'dict.txt'}}]}))
    plain=target/'plain.json'
    plain.write_text('{"name":"plain","conversion_chain":[]}')
    bundle=target/'stored.zip'
    with zipfile.ZipFile(bundle,'w',compression=zipfile.ZIP_STORED) as z:
        z.write(cfg,'config.json');z.write(dictionary,'dict.txt')
    fixture=target/'input.txt'; output=target/'output.txt'
    corpus='汉字里abc\r\n\n汉𠀀神\uFE0F\r\n'
    cases=[]
    for tool in TOOLS:
        for args in [['--help'],['-h'],['--version'],[],['--unknown'],['--'],['-xh'],['-help']]: cases.append((tool,args,b''))
    for args in [['--noflush','0'],['--noflush','1'],['--noflush','true'],['--noflush',''],['--noflush',' 1'],['--noflush','1 '],['--noflush','1 0'],['-c'],['-c','x','-c','y'],['--inspect','--segmentation'],['--inspect=1'],['--inspect yes'],['--resource-zip'],['--resource-zip='],['--resource-zip=x','--resource-zip=y']]:
        cases.append(('opencc',['--path',str(target),*args],b''))
    for mode in [[],['--inspect'],['--segmentation'],['--ambiguities']]:
        cases.append(('opencc',['-c',str(cfg),*mode],corpus.encode()))
        cases.append(('opencc',['-c',str(cfg),'-i',str(fixture),'-o',str(output),*mode],b''))
        cases.append(('opencc',['-c','config.json','--resource-zip',str(bundle),*mode],corpus.encode()))
    for content in [b'',b'\n',b'abc\n',b'abc\n\n',b'a\x00b',bytes.fromhex('e44141'),bytes.fromhex('80'),bytes.fromhex('e4b8')]:
        cases.append(('opencc',['-c',str(plain)],content))
        cases.append(('opencc',['-c',str(cfg),'--inspect'],content))
        cases.append(('opencc',['-c',str(cfg),'--ambiguities'],content))
        cases.append(('opencc',['-c',str(plain),'--ambiguities'],content))
    large=('汉字里abc𠀀\n'*90000).encode()
    for mode in [[],['--ambiguities']]: cases.append(('opencc',['-c',str(cfg),*mode],large))
    for source_format in ['text','text_space','ocd','ocd2','cppjieba_utf8','bad']:
        cases.append(('opencc_dict',['-i',str(dictionary),'-o',str(output),'-f',source_format,'-t','text'],b''))
    for args in [['-o',str(output),str(fixture)],['-o',str(output),str(target/'missing')],['-o',str(output)],['--no-such-flag'],['--','-o','x']]:
        cases.append(('opencc_phrase_extract',args,b''))
    # Exercise actual selected phrase output and every bundled configuration through full JSON.
    rare=target/'rare.txt'
    rare.write_text('背景'*100000 + ''.join(chr(0x4e50+i)+'甲乙'+chr(0x4ea0+i) for i in range(64)))
    cases.append(('opencc_phrase_extract',['-o',str(output),str(rare)],b''))
    data=ROOT/'.cache/conformance/jieba/opencc'
    for config in sorted(data.glob('*.json')):
        if config.name=='manifest.json': continue
        for mode in [[],['--inspect'],['--segmentation'],['--ambiguities']]:
            cases.append(('opencc',['-c',str(config),*mode],'勇敢的士兵汉字转换牛骥同一皁神𠀀\n'.encode()))
    for mode in [[],['--inspect'],['--segmentation'],['--ambiguities']]:
        for overwrite in [[],['--in-place']]:
            cases.append(('opencc',['-c',str(cfg),'-i',str(fixture),'-o',str(fixture),*mode,*overwrite],b''))
    symlink=target/'input-link.txt'
    hardlink=target/'hardlink.txt'
    symlink.unlink(missing_ok=True); hardlink.unlink(missing_ok=True)
    symlink.symlink_to(fixture.name)
    os.link(fixture,hardlink)
    for link in [symlink,hardlink]:
        cases.append(('opencc',['-c',str(cfg),'-i',str(fixture),'-o',str(link),'--in-place'],b''))
    source_space=target/'space.txt'
    source_space.write_text('汉 漢\n里 里 裏\n')
    for source,fmt in [(dictionary,'text'),(source_space,'text_space')]:
        for to in ['text','ocd','ocd2']:
            cases.append(('opencc_dict',['-i',str(source),'-o',str(output),'-f',fmt,'-t',to],b''))
    # Multi-input cppjieba metadata uses base frequencies plus ordered user overrides.
    base=target/'jieba-base.txt';users=target/'jieba-users.txt'
    base.write_text('汉 10 n\n中国 20 ns\n');users.write_text('汉 3 x\n臺灣\n')
    cases.append(('opencc_dict',['-i',str(base),'-i',str(users),'-o',str(output),'-f','cppjieba_utf8','-t','ocd2'],b''))
    measured=target/'measured.json' 
    cases.append(('opencc',['-c',str(cfg),'-i',str(fixture),'-o',str(output),'--measured_result',str(measured)],b''))
    for args in [[f'-c {cfg}'],[f'--config {cfg}'],['--config='+str(cfg)],['-c'+str(cfg)],[f'-i {fixture}',f'-c {cfg}']]:
        cases.append(('opencc',args,b'abc'))
    differences=[]
    rows=[]
    environment=os.environ.copy()
    for index,(tool,args,input_bytes) in enumerate(cases):
        captures=[]
        for native in (True,False):
            # Recreate hard links after a prior --in-place replacement, preserving test isolation.
            hardlink.unlink(missing_ok=True)
            if str(hardlink) in args: os.link(fixture,hardlink)
            fixture.write_text(corpus)
            fixture.chmod(0o640)
            measured.unlink(missing_ok=True)
            output.unlink(missing_ok=True)
            result=run(tool,args,input_bytes,native)
            emitted=output.read_bytes() if output.exists() else None
            metric=None
            if measured.exists():
                metric=json.loads(measured.read_text())
                for field in ['load_ms','convert_ms','write_ms','total_ms']:
                    assert metric[field]>=0, (BACKEND,metric)
                    metric.pop(field)
            captures.append((result.returncode,result.stdout,result.stderr,emitted,fixture.read_bytes(),fixture.stat().st_mode & 0o7777,metric))
        if captures[0]!=captures[1]:
            differences.append({'case':index,'tool':tool,'args':args,'input_hex':input_bytes.hex(),
                'native':[item.hex() if isinstance(item,bytes) else item for item in captures[0]],
                'kotlin':[item.hex() if isinstance(item,bytes) else item for item in captures[1]]})
        rows.append({'tool':tool,'args':args,'input_hex':input_bytes.hex(),'passed':captures[0]==captures[1]})
    (target/('differences-'+BACKEND+'.json')).write_text(json.dumps(differences,ensure_ascii=False,indent=2)+'\n')
    (target/('report-'+BACKEND+'.json')).write_text(json.dumps({'cases':len(rows),'passed':len(rows)-len(differences),'rows':rows},ensure_ascii=False,indent=2)+'\n')
    print(json.dumps({'backend':BACKEND,'cases':len(rows),'differences':len(differences)}))
    for item in differences[:8]:
        print(item['case'],item['tool'],item['args'])
        for name,fields in [('native',item['native']),('kotlin',item['kotlin'])]:
            print(name,fields[0],bytes.fromhex(fields[2]).decode(errors='replace')[:250])
    hardlink.unlink(missing_ok=True); symlink.unlink(missing_ok=True)
    return not differences


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--backend',choices=['jvm','native'],default='jvm')
    parser.add_argument('--native-target',choices=['macosArm64','macosX64','linuxX64','mingwX64'],default=NATIVE_TARGET)
    args=parser.parse_args();BACKEND=args.backend;NATIVE_TARGET=args.native_target
    raise SystemExit(0 if verify() else 1)
