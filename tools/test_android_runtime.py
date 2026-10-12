"""Execute shrinking-enabled published consumer APKs on an owned emulator (or an explicit device)."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import socket
import uuid
import tempfile
import time
from upstream import ROOT
from measure_android import inspect_apk

PACKAGE = 'io.github.opencckmp.consumer'


def run(serial=None, emulator=False):
    sdk = Path(os.environ.get('ANDROID_HOME') or os.environ['ANDROID_SDK_ROOT'])
    adb = sdk/'platform-tools/adb'
    process = None
    with tempfile.TemporaryDirectory(prefix='opencc-avd-') as directory:
        environment = os.environ.copy(); environment['ANDROID_AVD_HOME'] = directory
        try:
            if emulator:
                avd_name='opencc-ci-'+uuid.uuid4().hex[:8]
                subprocess.run([str(sdk/'cmdline-tools/latest/bin/avdmanager'),'create','avd','--name',avd_name,
                                '--package','system-images;android-35;google_apis;x86_64','--device','pixel_4'],
                               input=b'no\n',env=environment,check=True)
                devices=subprocess.check_output([str(adb),'devices'],text=True)
                port=None
                for candidate in range(5554,5682,2):
                    if f'emulator-{candidate}' in devices: continue
                    sockets=[socket.socket(),socket.socket()]
                    try:
                        sockets[0].bind(('127.0.0.1',candidate)); sockets[1].bind(('127.0.0.1',candidate+1));port=candidate
                    except OSError: pass
                    finally:
                        for item in sockets: item.close()
                    if port is not None: break
                if port is None: raise RuntimeError('No unused emulator port pair available')
                serial=f'emulator-{port}'
                log = ROOT/'.cache/android-runtime/emulator.log';log.parent.mkdir(parents=True,exist_ok=True)
                with log.open('wb') as output:
                    process = subprocess.Popen([str(sdk/'emulator/emulator'),'-avd',avd_name,'-port',str(port),
                                                '-no-window','-no-audio','-no-boot-anim','-no-snapshot','-gpu','swiftshader'],
                                               stdout=output,stderr=subprocess.STDOUT,env=environment)
            if not serial: raise ValueError('Provide --serial, or explicitly request --emulator')
            command = [str(adb),'-s',serial]
            deadline = time.monotonic()+300
            while True:
                if process and process.poll() is not None: raise RuntimeError('Owned emulator exited before boot')
                boot = subprocess.run(command+['shell','getprop','sys.boot_completed'],capture_output=True,text=True)
                if boot.returncode==0 and boot.stdout.strip()=='1': break
                if time.monotonic()>=deadline: raise TimeoutError('Android device did not boot')
                time.sleep(3)
            if emulator:
                actual_name=subprocess.check_output(command+['emu','avd','name'],text=True).splitlines()[0]
                if actual_name!=avd_name: raise RuntimeError('Refusing to operate a device not owned by this runner')
            subprocess.run(command+['shell','input','keyevent','82'],check=True)
            results = []
            for profile in ['engine','one','multi','full']:
                subprocess.run([str(ROOT/'gradlew'),'-p',str(ROOT/'consumers'),f'-Popencc.profile={profile}',':android:assembleRelease'],check=True)
                apk = ROOT/'consumers/android/build/outputs/apk/release/android-release.apk'
                inspect_apk(apk,profile)
                subprocess.run(command+['install','-r',str(apk)],check=True)
                subprocess.run(command+['shell','am','force-stop',PACKAGE],check=True)
                subprocess.run(command+['logcat','-c'],check=True)
                subprocess.run(command+['shell','am','start','-W','-n',PACKAGE+'/.MainActivity'],check=True)
                deadline = time.monotonic()+60
                while True:
                    log = subprocess.check_output(command+['logcat','-d','-v','brief','OpenCCFixture:I','AndroidRuntime:E','*:S'],text=True)
                    if 'FATAL EXCEPTION' in log: raise RuntimeError(log)
                    if 'PASS 漢字轉換' in log: break
                    if time.monotonic()>=deadline: raise TimeoutError(f'{profile}: conversion assertion not observed')
                    time.sleep(1)
                results.append({'profile':profile,'apk_sha256':hashlib.sha256(apk.read_bytes()).hexdigest(),'log':log})
            info = {key:subprocess.check_output(command+['shell','getprop',key],text=True).strip()
                    for key in ['ro.product.model','ro.product.cpu.abi','ro.build.version.sdk','ro.build.fingerprint']}
            report = ROOT/'.cache/android-runtime/report.json';report.parent.mkdir(parents=True,exist_ok=True)
            report.write_text(json.dumps({'schema_version':1,'device':info,'emulator':emulator,'profiles':results,
                'scope':'R8 release APK startup, inline conversion, shipped asset loading and s2t/t2s output assertions; not physical-device performance'},indent=2)+'\n')
        finally:
            if process:
                process.terminate()
                try: process.wait(timeout=20)
                except subprocess.TimeoutExpired: process.kill();process.wait()


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    choice=parser.add_mutually_exclusive_group(required=True)
    choice.add_argument('--emulator',action='store_true');choice.add_argument('--serial')
    args=parser.parse_args();run(args.serial,args.emulator)
