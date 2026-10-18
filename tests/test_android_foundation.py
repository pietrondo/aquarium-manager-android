from pathlib import Path
import unittest
import os
import shutil
import subprocess
import tempfile
import textwrap
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]


class AndroidFoundationTests(unittest.TestCase):
    def test_module_boundaries(self):
        self.assertTrue((ROOT / "settings.gradle.kts").is_file(), "Android modules are not configured")
        settings = (ROOT / 'settings.gradle.kts').read_text()
        self.assertIn('include(":app", ":domain", ":data")', settings)
        domain = (ROOT / 'domain/build.gradle.kts').read_text()
        self.assertIn('libs.plugins.kotlin.jvm', domain)
        self.assertNotIn('android', domain.lower())
        self.assertNotIn('project(', domain)
        data = (ROOT / 'data/build.gradle.kts').read_text()
        self.assertIn('implementation(project(":domain"))', data)
        self.assertNotIn('project(":app")', data)
        for source in (ROOT / 'domain/src').rglob('*.kt'):
            self.assertNotRegex(source.read_text(), r'(?m)^import (android|androidx)\.')

    def test_source_dependency_edges(self):
        # Focused import guard, not a complete architecture proof.
        for source in (ROOT / 'domain/src/main').rglob('*.kt'):
            self.assertNotRegex(source.read_text(), r'(?m)^import (android|androidx|com\.pietrondo\.aquariummanager\.(data|presentation))\.')
        for source in (ROOT / 'data/src/main').rglob('*.kt'):
            self.assertNotRegex(source.read_text(), r'(?m)^import com\.pietrondo\.aquariummanager\.(presentation|MainActivity|AquariumApplication)')
        presentation = ROOT / 'app/src/main/kotlin/com/pietrondo/aquariummanager/presentation'
        self.assertTrue(presentation.is_dir(), 'Presentation boundary is missing')
        for source in presentation.rglob('*.kt'):
            self.assertNotRegex(source.read_text(), r'(?m)^import (androidx\.room|com\.pietrondo\.aquariummanager\.data)\.')
        for source in (ROOT / 'app/src/main').rglob('*.kt'):
            if source.name != 'AquariumApplication.kt':
                self.assertNotRegex(source.read_text(), r'(?m)^import (androidx\.room|com\.pietrondo\.aquariummanager\.data)\.')

    def test_real_list_is_wired_without_placeholder_or_seed(self):
        root = ROOT / 'app/src/main/kotlin/com/pietrondo/aquariummanager'
        composition = root / 'AquariumApplication.kt'
        self.assertTrue(composition.is_file(), 'Real Room composition root is missing')
        self.assertIn('Room.databaseBuilder', composition.read_text())
        self.assertNotIn('fallbackToDestructiveMigration', composition.read_text())
        manifest = (ROOT / 'app/src/main/AndroidManifest.xml').read_text()
        self.assertIn('android:name=".AquariumApplication"', manifest)
        screen = (root / 'presentation/AquariumListScreen.kt').read_text()
        self.assertIn('collectAsStateWithLifecycle', screen)
        self.assertNotIn('foundation_message', (root / 'MainActivity.kt').read_text())

    def test_app_declares_no_permissions(self):
        self.assertTrue((ROOT / "app/src/main/AndroidManifest.xml").is_file(), "Android app manifest is missing")
        manifest = ET.parse(ROOT / 'app/src/main/AndroidManifest.xml')
        self.assertEqual([], manifest.findall('uses-permission'))

    def test_gradle_action_uses_verified_commit_not_annotated_tag(self):
        workflow = (ROOT / '.github/workflows/android.yml').read_text(encoding='utf-8')
        self.assertIn(
            'gradle/actions/setup-gradle@ed408507eac070d1f99cc633dbcf757c94c7933a',
            workflow,
        )
        self.assertNotIn('0b6dd653ba04f4f93bf581ec31e66cbd7dcb644d', workflow)

    def test_sdk_install_uses_sdk_home_without_sdkmanager_on_path(self):
        bash = (Path(os.environ.get('ProgramFiles', 'C:/Program Files')) / 'Git/bin/bash.exe'
                if os.name == 'nt' else Path(shutil.which('bash') or '/bin/bash'))
        self.assertTrue(bash.is_file(), 'Bash is required to exercise the CI shell block')
        workflow = (ROOT / '.github/workflows/android.yml').read_text(encoding='utf-8')
        step = workflow.split('      - name: Install pinned SDK packages\n', 1)[1]
        run = step.split('      - uses:', 1)[0].split('        run:', 1)[1].strip()
        script = textwrap.dedent(run[1:]).strip() if run.startswith('|') else run
        with tempfile.TemporaryDirectory(prefix='android sdk test ') as temp:
            sdk = Path(temp) / 'sdk with spaces'
            manager = sdk / 'cmdline-tools/latest/bin/sdkmanager'
            manager.parent.mkdir(parents=True)
            manager.write_text('#!/bin/bash\nprintf "%s\\n" "$@" > "$SDK_ARGS_OUTPUT"\n', encoding='utf-8')
            manager.chmod(0o755)
            output = Path(temp) / 'arguments.txt'
            env = dict(os.environ, ANDROID_HOME=sdk.as_posix(),
                       SDK_ARGS_OUTPUT=output.as_posix(), PATH=temp)
            result = subprocess.run([str(bash), '--noprofile', '--norc', '-e', '-c', script],
                                    env=env, capture_output=True, text=True, timeout=15)
            self.assertEqual(0, result.returncode, result.stderr)
            self.assertEqual(['platforms;android-36', 'build-tools;35.0.0'],
                             output.read_text(encoding='utf-8').splitlines())

    def test_wrapper_distribution_is_checksum_pinned(self):
        self.assertTrue((ROOT / "gradle/wrapper/gradle-wrapper.properties").is_file(), "Verified wrapper is missing")
        properties = (ROOT / 'gradle/wrapper/gradle-wrapper.properties').read_text()
        self.assertRegex(properties, r'(?m)^distributionSha256Sum=[0-9a-f]{64}$')
        self.assertGreater((ROOT / 'gradle/wrapper/gradle-wrapper.jar').stat().st_size, 10000)


if __name__ == '__main__':
    unittest.main()
