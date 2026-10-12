"""Verify unpacked CLI distributions without development paths or data/plugin environment settings."""
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import zipfile
from upstream import ROOT


def verify(kinds):
    target=ROOT/'.cache/cli-unpacked'
    target.mkdir(parents=True,exist_ok=True)
    cwd=target/'different-cwd';cwd.mkdir(exist_ok=True)
    report={}
    for kind in kinds:
        archive=ROOT/'opencc-cli/build/distributions'/f'opencc-kmp-0.1.0-SNAPSHOT-{kind}-cli.zip'
        folder=target/kind
        if folder.exists(): shutil.rmtree(folder)
        with zipfile.ZipFile(archive) as bundle:
            bundle.extractall(folder)
            assert {'LICENSE','NOTICE','AUTHORS'}.issubset(bundle.namelist())
            assert any(name.startswith('licenses/') for name in bundle.namelist())
        for tool in ['opencc','opencc_dict','opencc_phrase_extract']:(folder/'bin'/tool).chmod(0o755)
        environment=os.environ.copy()
        for key in ['OPENCC_DATA_DIR','OPENCC_PACKAGE_DATA_DIR','OPENCC_SEGMENTATION_PLUGIN_PATH']:environment.pop(key,None)
        checks=[]
        for config,text,expected in [('s2t','汉字转换','漢字轉換'),('s2twp_jieba','勇敢的士兵','勇敢的士兵')]:
            process=subprocess.run([str(folder/'bin/opencc'),'-c',config],input=text.encode(),capture_output=True,cwd=cwd,env=environment)
            assert process.returncode==0 and process.stdout.decode()==expected,(kind,config,process.returncode,process.stdout,process.stderr)
            checks.append({'config':config,'stdout':process.stdout.decode()})
        for tool in ['opencc_dict','opencc_phrase_extract']:
            process=subprocess.run([str(folder/'bin'/tool),'--version'],capture_output=True,cwd=cwd,env=environment)
            assert process.returncode==0 and b'1.4.2' in process.stdout
        report[kind]={'archive_bytes':archive.stat().st_size,'sha256':hashlib.sha256(archive.read_bytes()).hexdigest(),
                      'default_resource_checks':checks,'version_launchers':2}
    (target/'report.json').write_text(json.dumps(report,sort_keys=True,indent=2)+'\n')
    print(json.dumps(report,indent=2))
    return report


if __name__=='__main__':
    import argparse
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('kinds',nargs='*',default=['jvm','macosArm64'])
    verify(parser.parse_args().kinds)
