from pathlib import Path
import unittest
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

    def test_wrapper_distribution_is_checksum_pinned(self):
        self.assertTrue((ROOT / "gradle/wrapper/gradle-wrapper.properties").is_file(), "Verified wrapper is missing")
        properties = (ROOT / 'gradle/wrapper/gradle-wrapper.properties').read_text()
        self.assertRegex(properties, r'(?m)^distributionSha256Sum=[0-9a-f]{64}$')
        self.assertGreater((ROOT / 'gradle/wrapper/gradle-wrapper.jar').stat().st_size, 10000)


if __name__ == '__main__':
    unittest.main()
