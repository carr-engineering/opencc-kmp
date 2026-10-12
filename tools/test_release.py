"""Release bundle integrity, coordinate validation and real detached-signature regression checks."""
import hashlib
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
import zipfile
from unittest.mock import patch
from release import ALGORITHMS, validate_pom, version_for_tag, verify_bundle, verify_signature, sign_file, pack_selections, release_configs


class ReleaseTests(unittest.TestCase):
    def test_release_selects_every_config_and_deduplicated_common_pack(self):
        selections=pack_selections().split(';')
        self.assertEqual(set(release_configs()),set(selections[:-1]))
        self.assertEqual(23,len(selections[:-1]))
        self.assertEqual('s2t,t2s',selections[-1])

    def test_versions_and_snapshot_rejection(self):
        for tag in ['v0.1.0','v1.2.3-rc.1']: self.assertEqual(tag[1:],version_for_tag(tag))
        for tag in ['main','v01.2.3','v1.2','v1.2.3-SNAPSHOT','v1.2.3/../../bad','v1.2.3-.','v1.2.3-01']:
            with self.assertRaises(ValueError): version_for_tag(tag)

    def test_bundle_missing_tampered_and_unsafe_members(self):
        with tempfile.TemporaryDirectory(prefix='occ-',dir='/private/tmp' if Path('/private/tmp').exists() else None) as temporary:
            path = Path(temporary)/'bundle.zip'; data=b'artifact'
            entries={'owner/core/1.0.0/core-1.0.0.jar':data}
            name=next(iter(entries)); entries[name+'.asc']=b'signature'
            for algorithm in ALGORITHMS: entries[name+'.'+algorithm]=hashlib.new(algorithm,data).hexdigest().encode()
            def write():
                with zipfile.ZipFile(path,'w') as archive:
                    for name,data in entries.items(): archive.writestr(name,data)
            write();self.assertEqual(1,verify_bundle(path))
            entries[name]=b'changed';write()
            with self.assertRaises(ValueError): verify_bundle(path)
            entries[name]=data;entries['../escape.jar']=b'escape';write()
            with self.assertRaises(ValueError): verify_bundle(path)
            del entries['../escape.jar'];del entries[name+'.asc'];write()
            with self.assertRaises(ValueError): verify_bundle(path)

    def test_pom_requires_owner_metadata_sources_and_docs(self):
        with tempfile.TemporaryDirectory() as temporary:
            path=Path(temporary)/'core-1.0.0.pom'
            path.write_text("""<project xmlns="http://maven.apache.org/POM/4.0.0"><groupId>io.example</groupId><artifactId>core</artifactId>
                <version>1.0.0</version><name>core</name><description>description</description><url>https://github.com/example/repo</url>
                <licenses><license><name>Apache-2.0</name><url>https://www.apache.org/licenses/LICENSE-2.0</url></license></licenses>
                <developers><developer><id>example</id><name>Example Publisher</name></developer></developers>
                <scm><url>https://github.com/example/repo</url><connection>scm:git:https://github.com/example/repo.git</connection></scm></project>""")
            with self.assertRaises(ValueError): validate_pom(path,'io.example','1.0.0')
            path.with_name('core-1.0.0-sources.jar').write_bytes(b'sources')
            path.with_name('core-1.0.0-javadoc.jar').write_bytes(b'documentation')
            validate_pom(path,'io.example','1.0.0')
            with self.assertRaises(ValueError): validate_pom(path,'io.other','1.0.0')
            with self.assertRaises(ValueError): validate_pom(path,'io.example','2.0.0')

    @unittest.skipUnless(shutil.which('gpg'),'GPG unavailable')
    def test_signature_detects_mutation_of_final_bytes(self):
        with tempfile.TemporaryDirectory(prefix='occ-',dir='/private/tmp' if Path('/private/tmp').exists() else None) as temporary:
            home=Path(temporary)/'gpg';home.mkdir(mode=0o700)
            with patch.dict(os.environ,{'GNUPGHOME':str(home),'OPENCC_SIGNING_PASSWORD':''}):
                subprocess.run(['gpg','--batch','--pinentry-mode','loopback','--passphrase','','--quick-generate-key',
                                'OpenCC Test <test@example.invalid>','ed25519','sign','1d'],check=True,capture_output=True)
                payload=Path(temporary)/'artifact.jar';payload.write_bytes(b'final embedded notices and payload')
                sign_file(payload,'test@example.invalid');verify_signature(payload)
                payload.write_bytes(b'tampered')
                with self.assertRaises(subprocess.CalledProcessError): verify_signature(payload)
                subprocess.run(['gpgconf','--kill','gpg-agent'],check=True)


if __name__=='__main__': unittest.main()
