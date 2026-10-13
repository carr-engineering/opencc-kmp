"""Direct pinned CLI assertion ports for filesystem, stream boundaries and tofu-risk defaults."""
import argparse
import json
import os
from pathlib import Path
import subprocess
import time
import hashlib
import math
import shutil
import stat
import zipfile
import test_cli
from upstream import ROOT, SOURCE, LOCK

CASES = {
    'SkipsTofuRiskDictionariesByDefault': ('t2s', '㑮'.encode(), b'', []),
    'IncludeTofuRiskDictionariesFlagRestoresLegacy': ('t2s', '㑮'.encode(), b'', ['--include-tofu-risk-dictionaries']),
    'StdinPreservesLineEndingsAndUnknownCharacters': ('s2t', '鼠标=mouse\r\n123\n未登录'.encode(), b'', []),
    'WarnsWhenInputContainsVariationSelector': ('s2t', '汉禰\U000e0100'.encode(), b'', []),
    'WarnsWhenSupplementaryVariationSelectorCrossesChunkBoundary': ('s2t', b'a' * (1048576 - 1) + '\U000e0100'.encode(), b'', []),
    'PreservesLineEnding_LF': ('s2t', '第一行\n第二行\n第三行\n'.encode(), b'', []),
    'PreservesLineEnding_CRLF': ('s2t', '第一行\r\n第二行\r\n第三行\r\n'.encode(), b'', []),
    'PreservesLineEnding_CRLF_ASCII': ('s2t', b'hello\r\nworld\r\n', b'', []),
    'PreservesLineEnding_NoTrailingNewline_LF': ('s2t', '第一行\n第二行'.encode(), b'', []),
    'PreservesLineEnding_NoTrailingNewline_CRLF': ('s2t', '第一行\r\n第二行'.encode(), b'', []),
    'ChunkingBoundaryTruncationFix': ('s2t', b'a' * (1048576 - 3) + '头发尾巴'.encode(), b'', []),
    'ExactChunkSizeDoesNotTruncate': ('s2t', b'a' * 1048576, b'', []),
    'MissingInputDoesNotTruncateOutput': ('s2t', b'', b'existing output', ['missing']),
}


EXTRA_CASES = [
    'ConvertFromJson', 'ResourceZipConvertsWithoutResourcePaths', 'ResourceOcd2ZipConvertsWithoutResourcePaths',
    'AmbiguitiesEmitsDefineOnFirstUseRecords', 'AmbiguitiesCoversMultiStageChain', 'AmbiguitiesRejectsInvalidUtf8',
    'AmbiguitiesInPlaceInvalidUtf8KeepsOriginal', 'AmbiguitiesInPlaceRewritesFile', 'PipeShortReadContinuesUntilEof',
    'ConvertsFilesWithUnicodePaths', 'ConvertsInPlaceWithUnicodePath', 'RejectsInPlaceConversionWithoutFlag',
    'ConvertsInPlaceWithDifferentPathSpellings', 'InPlaceConversionPreservesFileMode', 'InPlaceConversionRejectsHardLinks',
    'ConvertsInPlaceThroughSymlink', 'WritesMeasuredResultJson', 'SegmentationOutputIsJson', 'InspectOutputIsJson',
    'SegmentationAndInspectAreMutuallyExclusive', 'SegmentationFailsWhenConfigHasNoSegmentation',
    'MeasuredResultIncludesOutputMode', 'SegmentationDoesNotRunConversionChain', 'SegmentationNoSpuriousEofRecord',
]
CASES.update(dict.fromkeys(EXTRA_CASES))


def extended_json(text):
    """Remove only comments and trailing commas outside strings, as accepted by pinned fixtures."""
    result=[]; index=0; quoted=False; escaped=False
    while index<len(text):
        char=text[index]
        if quoted:
            result.append(char)
            if escaped: escaped=False
            elif char=='\\': escaped=True
            elif char=='"': quoted=False
            index+=1; continue
        if char=='"': quoted=True;result.append(char);index+=1;continue
        if text.startswith('//',index):
            end=text.find('\n',index+2);index=len(text) if end<0 else end;continue
        if text.startswith('/*',index):
            end=text.find('*/',index+2)
            if end<0: raise ValueError('Unterminated upstream fixture comment')
            result.append(' ');index=end+2;continue
        result.append(char);index+=1
    text=''.join(result);result=[];quoted=False;escaped=False
    for index,char in enumerate(text):
        if quoted:
            if escaped: escaped=False
            elif char=='\\': escaped=True
            elif char=='"': quoted=False
        elif char=='"': quoted=True
        elif char==',' and text[index+1:].lstrip().startswith(('}',']')): continue
        result.append(char)
    return json.loads(''.join(result))


def command(args,native):
    if native:
        return ['opencc',*args],str(ROOT/'.cache/oracle/src/tools/opencc')
    if test_cli.BACKEND=='jvm':
        classpath=(ROOT/'opencc-cli/build/reports/jvm-classpath.txt').read_text()
        return ['java','-cp',classpath,test_cli.TOOLS['opencc'],*args],None
    extension='.exe' if test_cli.NATIVE_TARGET=='mingwX64' else '.kexe'
    return [str(ROOT/'opencc-cli/build/bin'/test_cli.NATIVE_TARGET/'openccReleaseExecutable'/('opencc'+extension)),*args],None


def delayed_pipe(args,native):
    argv,executable=command(args,native)
    environment=os.environ.copy()
    environment['OPENCC_SEGMENTATION_PLUGIN_PATH']=str(ROOT/'.cache/oracle/plugins/jieba')
    environment['OPENCC_DATA_DIR']=str(ROOT/'.cache/conformance/jieba/opencc')
    process=subprocess.Popen(argv,executable=executable,stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.PIPE,cwd=ROOT,env=environment)
    try:
        process.stdin.write('后台'.encode());process.stdin.flush()
        time.sleep(0.1)
        process.stdin.write('老板'.encode());process.stdin.flush();process.stdin.close();process.stdin=None
        stdout,stderr=process.communicate(timeout=30)
        return subprocess.CompletedProcess(argv,process.returncode,stdout,stderr)
    finally:
        if process.poll() is None: process.kill();process.communicate()


def measured(path):
    if not path.exists(): return None
    data=json.loads(path.read_text())
    for field in ['load_ms','convert_ms','write_ms','total_ms']:
        value=data[field]
        assert isinstance(value,(int,float)) and not isinstance(value,bool) and math.isfinite(value) and value>=0,(field,value)
        data.pop(field)
    return data


def ambiguity_records(payload,plain):
    definitions=[];references=[];reconstructed='';ended=False
    for line in payload.splitlines():
        if not line: continue
        assert not ended,'Record after ambiguity end'
        record=json.loads(line)
        if 'def' in record:
            assert isinstance(record['def'],str);definitions.append(record['def'])
        elif 'lit' in record:
            assert isinstance(record['lit'],str);reconstructed+=record['lit']
        elif 'amb' in record:
            ambiguity=record['amb'];index=ambiguity['s']
            assert isinstance(index,int) and not isinstance(index,bool) and 0<=index<len(definitions)
            assert isinstance(ambiguity['t'],str)
            references.append(index);reconstructed+=ambiguity['t']
        elif 'end' in record:
            ended=True;end=record['end']
            assert end['output_bytes']==len(reconstructed.encode())
            assert end['ambiguities']==len(references) and end['sources']==len(definitions)
        else: raise AssertionError(('Unknown ambiguity record',record))
    assert ended and reconstructed.encode()==plain
    return definitions,references


def prepare_resource_zip(directory,kind,data):
    config=json.loads((data/'s2twp.json').read_text())
    files=set()
    def convert(node):
        if isinstance(node,dict):
            if node.get('type')=='ocd2':
                if kind=='text': node['type']='text';node['file']=node['file'].removesuffix('.ocd2')+'.txt'
                files.add(node['file'])
            for value in node.values(): convert(value)
        elif isinstance(node,list):
            for value in node: convert(value)
    convert(config)
    archive=directory/f'{kind}.zip'
    with zipfile.ZipFile(archive,'w',compression=zipfile.ZIP_STORED) as output:
        output.writestr('zip-only-s2twp.json',json.dumps(config,ensure_ascii=False))
        for name in sorted(files):
            source=(ROOT/'.cache/conformance/text'/name) if kind=='text' else (data/name)
            if not source.is_file(): raise RuntimeError(f'Missing native-derived {kind} ZIP fixture resource {source}')
            output.write(source,name)
    return archive


def golden_conversions(directory,data):
    source=SOURCE/'test/testcases/testcases.json'
    cases=extended_json(source.read_text())['cases'];by_config={}
    for row in cases:
        if not isinstance(row.get('input'),str) or not isinstance(row.get('expected'),dict): continue
        for config,expected in row['expected'].items():
            if isinstance(expected,str): by_config.setdefault(config,[]).append((row['input'].split('\0')[0],expected.split('\0')[0]))
    count=0
    for config,entries in sorted(by_config.items()):
        source_file=directory/'golden-input.txt';output=directory/'golden-output.txt'
        source_file.write_bytes(''.join(text+'\n' for text,_ in entries).encode())
        expected=''.join(text+'\n' for _,text in entries).encode()
        results=[]
        for native in (True,False):
            output.unlink(missing_ok=True)
            result=test_cli.run('opencc',['-c',str(data/(config+'.json')),'-i',str(source_file),'-o',str(output),'--include-tofu-risk-dictionaries'],b'',native)
            emitted=output.read_bytes() if output.exists() else b''
            assert result.returncode==0,(config,result.stderr)
            assert emitted==expected,(config,'Pinned golden output differs')
            results.append((result.returncode,result.stdout,result.stderr,emitted))
        assert results[0]==results[1],(config,'Native/Kotlin golden process differences')
        count+=len(entries)
    return {'golden_assertions':count,'configs':sorted(by_config),'fixture_sha256':hashlib.sha256(source.read_bytes()).hexdigest()}


def extended_case(name,directory,data):
    folder=directory/'cases'/name
    config='s2twp' if name in {'AmbiguitiesCoversMultiStageChain','SegmentationOutputIsJson','SegmentationDoesNotRunConversionChain','SegmentationNoSpuriousEofRecord'} else 's2t'
    if name=='ConvertFromJson':
        folder.mkdir(parents=True,exist_ok=True)
        return golden_conversions(folder,data)
    if os.name=='nt' and name in {'PipeShortReadContinuesUntilEof','InPlaceConversionPreservesFileMode','InPlaceConversionRejectsHardLinks'}:
        return {'result':'skipped','reason':'Pinned upstream POSIX-only assertion'}
    captures=[]
    for native in (True,False):
        if folder.exists(): shutil.rmtree(folder)
        folder.mkdir(parents=True)
        working=folder/'中文路径-cli-😀-𠮷' if name=='ConvertsFilesWithUnicodePaths' else folder
        working.mkdir(exist_ok=True)
        source=working/'输入 文件.txt';output=working/'輸出 文件.txt';metric=working/'測量 結果.json'
        text='开放中文转换';original=text.encode();flags=[];expected_status=0
        if name=='AmbiguitiesEmitsDefineOnFirstUseRecords': original='文丑与文丑相争，头发干燥'.encode();flags=['--ambiguities']
        elif name=='AmbiguitiesCoversMultiStageChain': original='下面是软件的信号'.encode();flags=['--ambiguities']
        elif name in {'AmbiguitiesRejectsInvalidUtf8','AmbiguitiesInPlaceInvalidUtf8KeepsOriginal'}:
            original=b'abc\xe4AAzzz';flags=['--ambiguities'];expected_status=1
        elif name=='AmbiguitiesInPlaceRewritesFile': original='文丑'.encode();flags=['--ambiguities']
        elif name.startswith('Segmentation'):
            flags=['--segmentation'];original=(text+'\n').encode()
            if name in {'SegmentationAndInspectAreMutuallyExclusive','SegmentationFailsWhenConfigHasNoSegmentation'}: expected_status=1
            if name=='SegmentationAndInspectAreMutuallyExclusive': flags+=['--inspect']
            if name=='SegmentationNoSpuriousEofRecord': original=(text+'\n汉字\n').encode()
        elif name in {'InspectOutputIsJson','MeasuredResultIncludesOutputMode'}: flags=['--inspect'];original=(text+'\n').encode()
        elif name=='WritesMeasuredResultJson': original=(text+'\n').encode()
        source.write_bytes(original)
        source.chmod(0o644)
        if name in {'AmbiguitiesInPlaceInvalidUtf8KeepsOriginal','AmbiguitiesInPlaceRewritesFile','ConvertsInPlaceWithUnicodePath','InPlaceConversionPreservesFileMode'}:
            output=source;flags+=['--in-place']
        elif name=='RejectsInPlaceConversionWithoutFlag': output=source;expected_status=1
        elif name=='ConvertsInPlaceWithDifferentPathSpellings': output=str(working)+'/./'+source.name;flags+=['--in-place']
        elif name=='InPlaceConversionRejectsHardLinks':
            try: os.link(source,output)
            except OSError as error: return {'result':'skipped','reason':f'Hard links unsupported: {error}'}
            flags+=['--in-place'];expected_status=1
        elif name=='ConvertsInPlaceThroughSymlink':
            try: output.symlink_to(source.name)
            except OSError as error: return {'result':'skipped','reason':f'Symlink unsupported: {error}'}
            flags+=['--in-place']
        args=['-c',str(data/(config+'.json')),'-i',str(source),'-o',str(output),*flags]
        if name in {'ConvertsFilesWithUnicodePaths','WritesMeasuredResultJson','MeasuredResultIncludesOutputMode'}: args+=['--measured_result',str(metric)]
        if name in {'ResourceZipConvertsWithoutResourcePaths','ResourceOcd2ZipConvertsWithoutResourcePaths'}:
            original='打印机和鼠标'.encode();source.write_bytes(original)
            archive=prepare_resource_zip(folder,'text' if name=='ResourceZipConvertsWithoutResourcePaths' else 'ocd2',data)
            args=['-c','zip-only-s2twp.json','--resource-zip',str(archive),'-i',str(source),'-o',str(output)]
        result=delayed_pipe(['-c',str(data/'s2t.json'),'-o',str(output)],native) if name=='PipeShortReadContinuesUntilEof' else test_cli.run('opencc',args,b'',native)
        emitted=Path(output).read_bytes() if Path(output).exists() else b''
        if expected_status: assert result.returncode!=0,(name,result.stderr)
        else: assert result.returncode==0,(name,result.stderr)
        if name in {'AmbiguitiesEmitsDefineOnFirstUseRecords','AmbiguitiesCoversMultiStageChain'}:
            plain_output=working/'plain.txt'
            plain=test_cli.run('opencc',['-c',str(data/(config+'.json')),'-i',str(source),'-o',str(plain_output)],b'',native)
            assert plain.returncode==0
            definitions,references=ambiguity_records(emitted,plain_output.read_bytes())
            if name=='AmbiguitiesEmitsDefineOnFirstUseRecords':
                assert definitions.count('文丑')==1 and references.count(definitions.index('文丑'))>=2
            else:
                assert {'下面','信号'}.issubset(definitions)
                assert {'下面','信号'}.issubset({definitions[index] for index in references})
        elif name=='AmbiguitiesRejectsInvalidUtf8':
            assert b'"end"' not in emitted
            plain=test_cli.run('opencc',['-c',str(data/'s2t.json'),'-i',str(source),'-o',str(working/'plain.txt')],b'',native)
            assert plain.returncode==0 and (working/'plain.txt').read_bytes()==original
        elif name=='AmbiguitiesInPlaceInvalidUtf8KeepsOriginal':
            assert source.read_bytes()==original and sorted(path.name for path in folder.iterdir())==[source.name]
        elif name=='AmbiguitiesInPlaceRewritesFile':
            assert '{"def":"文丑"}'.encode() in emitted and b'"end"' in emitted
        elif name in {'RejectsInPlaceConversionWithoutFlag','InPlaceConversionRejectsHardLinks'}:
            assert source.read_bytes()==original and emitted==original
        elif name=='InPlaceConversionPreservesFileMode':
            assert emitted=='開放中文轉換'.encode() and stat.S_IMODE(source.stat().st_mode)==0o644
        elif name=='PipeShortReadContinuesUntilEof': assert emitted=='後臺老闆'.encode()
        elif name.startswith('Resource'): assert emitted=='印表機和滑鼠'.encode()
        elif name in {'ConvertsFilesWithUnicodePaths','ConvertsInPlaceWithUnicodePath','ConvertsInPlaceWithDifferentPathSpellings','ConvertsInPlaceThroughSymlink'}:
            assert emitted=='開放中文轉換'.encode()
            if name!='ConvertsFilesWithUnicodePaths': assert source.read_bytes()=='開放中文轉換'.encode()
        if name in {'SegmentationOutputIsJson','InspectOutputIsJson','SegmentationDoesNotRunConversionChain'}:
            record=json.loads(emitted);assert isinstance(record,dict) and isinstance(record['input'],str) and isinstance(record['segments'],list)
            if name=='InspectOutputIsJson':
                assert isinstance(record['output'],str) and isinstance(record['stages'],list)
                for stage in record['stages']: assert isinstance(stage['index'],int) and stage['index']>=0 and isinstance(stage['segments'],list)
            else:
                assert record['segments'] and 'stages' not in record and 'output' not in record
                if name=='SegmentationDoesNotRunConversionChain': assert ''.join(record['segments'])==text
        if name=='SegmentationNoSpuriousEofRecord':
            records=emitted.rstrip(b'\n').split(b'\n');assert len(records)==2
            assert all(isinstance(json.loads(record),dict) for record in records)
        metric_data=measured(metric)
        if metric_data is not None:
            assert metric_data['config']==str(data/(config+'.json')) and metric_data['mode']=='file'
            assert isinstance(metric_data['input_bytes'],int) and metric_data['input_bytes']>=0
            assert isinstance(metric_data['output_bytes'],int) and metric_data['output_bytes']>=0
            if name=='ConvertsFilesWithUnicodePaths': assert metric_data['input']==str(source)
            if name=='MeasuredResultIncludesOutputMode': assert metric_data['output_mode']=='inspect'
        captures.append((result.returncode,result.stdout,result.stderr,emitted,source.read_bytes(),stat.S_IMODE(source.stat().st_mode),metric_data))
    assert captures[0]==captures[1],f'{name}: native/Kotlin process or filesystem results differ'
    return {}


def verify(backend, target):
    test_cli.BACKEND = backend
    test_cli.NATIVE_TARGET = target
    directory = ROOT / '.cache/cli-upstream'
    directory.mkdir(parents=True, exist_ok=True)
    data = ROOT / '.cache/conformance/jieba/opencc'
    rows = []
    for name, case in CASES.items():
        if case is None: continue
        config, input_bytes, original_output, flags = case
        if os.name == "nt" and name.startswith("WarnsWhen"):
            rows.append({"case":name,"result":"skipped","reason":"Pinned upstream POSIX-only variation warning assertion"})
            continue
        source = directory / '输入 文件-😀.txt'
        output = directory / '輸出 文件.txt'
        captures = []
        for native in (True, False):
            source.write_bytes(input_bytes)
            output.write_bytes(original_output)
            args = ['-c', str(data / f'{config}.json'), '-o', str(output)]
            missing = flags == ['missing']
            if missing:
                args += ['-i', str(directory / '不存在.txt')]
            elif name == 'StdinPreservesLineEndingsAndUnknownCharacters':
                pass
            else:
                args += ['-i', str(source)]
            if not missing: args += flags
            result = test_cli.run('opencc', args, input_bytes, native)
            captures.append((result.returncode, result.stdout, result.stderr, output.read_bytes()))
        assert captures[0] == captures[1], f'{name}: native/Kotlin process results differ'
        code, _, stderr, emitted = captures[1]
        if name == 'MissingInputDoesNotTruncateOutput':
            assert code != 0 and emitted == original_output, name
        else:
            assert code == 0, (name, stderr)
        if name.startswith('WarnsWhen'):
            assert b'warning: input contains Unicode variation selectors' in stderr, name
        if name == 'SkipsTofuRiskDictionariesByDefault': assert emitted == '㑮'.encode()
        if name == 'IncludeTofuRiskDictionariesFlagRestoresLegacy': assert emitted == '𫝈'.encode()
        if name == 'ChunkingBoundaryTruncationFix': assert emitted.endswith('頭髮尾巴'.encode())
        if name == 'ExactChunkSizeDoesNotTruncate': assert len(emitted) == 1048576
        if name == 'StdinPreservesLineEndingsAndUnknownCharacters': assert emitted == '鼠標=mouse\r\n123\n未登錄'.encode()
        if name == 'WarnsWhenInputContainsVariationSelector': assert emitted == '漢禰\U000e0100'.encode()
        if name == 'WarnsWhenSupplementaryVariationSelectorCrossesChunkBoundary': assert emitted == input_bytes
        if name == 'PreservesLineEnding_LF': assert b'\n' in emitted and b'\r' not in emitted
        if name in {'PreservesLineEnding_CRLF','PreservesLineEnding_CRLF_ASCII'}: assert b'\r' in emitted and b'\n' in emitted
        if name == 'PreservesLineEnding_NoTrailingNewline_LF': assert b'\n' in emitted and b'\r' not in emitted and not emitted.endswith(b'\n')
        if name == 'PreservesLineEnding_NoTrailingNewline_CRLF': assert b'\r' in emitted and b'\n' in emitted and not emitted.endswith((b'\r',b'\n'))
        rows.append({'case': name, 'result': 'passed'})
    for name in EXTRA_CASES:
        details=extended_case(name,directory,data)
        rows.append({'case':name,'result':details.pop('result','passed'),**details})
    source_test=SOURCE/'test/CommandLineConvertTest.cpp'
    report = {'schema_version': 2, 'upstream': LOCK, 'source_sha256':hashlib.sha256(source_test.read_bytes()).hexdigest(), 'backend': backend, 'target': target, 'cases': rows,
              'scope': '37 named pinned CommandLineConvertTest assertions; explicit upstream OS/link skips, two ZIP variants and per-config golden outputs; broader tool suites remain separate'}
    (directory / f'report-{backend}.json').write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps({'backend': backend, 'cases': len(rows), 'passed':sum(row['result']=='passed' for row in rows),'skipped':sum(row['result']=='skipped' for row in rows)}))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--backend', choices=['jvm', 'native'], default='jvm')
    parser.add_argument('--native-target', default=test_cli.NATIVE_TARGET)
    args = parser.parse_args()
    verify(args.backend, args.native_target)
