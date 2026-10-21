#!/usr/bin/env python3
"""Static preflight for environments without an Android SDK/Gradle installation."""
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
errors = []
checks = []

def check(condition, ok_message, error_message):
    if condition:
        checks.append(ok_message)
    else:
        errors.append(error_message)

manifest_path = ROOT / "app/src/main/AndroidManifest.xml"
try:
    manifest = ET.parse(manifest_path).getroot()
    ns = {"android": "http://schemas.android.com/apk/res/android"}
    app = manifest.find("application")
    check(app is not None, "AndroidManifest.xml is valid XML", "AndroidManifest.xml has no application element")
    if app is not None:
        check(app.get("{http://schemas.android.com/apk/res/android}usesCleartextTraffic") == "false",
              "Cleartext network traffic is disabled", "usesCleartextTraffic must be false")
        check(app.get("{http://schemas.android.com/apk/res/android}allowBackup") == "false",
              "Android backup is disabled", "allowBackup must be false for this initial secret-storage design")
except Exception as exc:
    errors.append(f"Could not parse AndroidManifest.xml: {exc}")

required_files = [
    "README.md", "CHANGELOG.md",
    "settings.gradle.kts", "build.gradle.kts", "app/build.gradle.kts",
    "app/src/main/java/io/github/astromg01/nucleo/MainActivity.kt",
    "app/src/main/java/io/github/astromg01/nucleo/NucleoApplication.kt",
    "app/src/main/java/io/github/astromg01/nucleo/data/local/AppDatabase.kt",
    "app/src/main/java/io/github/astromg01/nucleo/core/security/SecretStore.kt",
    "app/src/main/java/io/github/astromg01/nucleo/core/ai/ModelRouter.kt",
    "app/src/main/java/io/github/astromg01/nucleo/worker/PlanningWorker.kt",
    "app/src/main/java/io/github/astromg01/nucleo/domain/task/ProjectSnapshotReader.kt",
    "app/src/test/java/io/github/astromg01/nucleo/data/settings/ProviderConfigurationValidatorTest.kt",
    "app/src/main/java/io/github/astromg01/nucleo/core/lang/NucleoLexer.kt",
    "app/src/main/java/io/github/astromg01/nucleo/core/lang/NucleoAst.kt",
    "app/src/main/java/io/github/astromg01/nucleo/core/lang/NucleoParser.kt",
    "app/src/main/java/io/github/astromg01/nucleo/core/lang/NucleoInterpreter.kt",
    "app/src/main/java/io/github/astromg01/nucleo/core/lang/NucleoHostFunction.kt",
    "app/src/test/java/io/github/astromg01/nucleo/core/lang/NucleoInterpreterTest.kt",
    "app/src/main/java/io/github/astromg01/nucleo/ui/lang/LangPlaygroundScreen.kt",
    "docs/NUCLEOLANG_SPEC.md",
    "docs/NUCLEOLANG_PLATFORM_ARCHITECTURE.md",
    "docs/PROJECT_FACTORY_SPEC.md",
    "examples/hello.nuc",
    "examples/collections.nuc",
    ".github/workflows/android-ci.yml",
]
for relative in required_files:
    check((ROOT / relative).is_file(), f"Found {relative}", f"Missing required file: {relative}")

entities = (ROOT / "app/src/main/java/io/github/astromg01/nucleo/data/local/Entities.kt").read_text(encoding="utf-8")
worker = (ROOT / "app/src/main/java/io/github/astromg01/nucleo/worker/PlanningWorker.kt").read_text(encoding="utf-8")
tasks = (ROOT / "app/src/main/java/io/github/astromg01/nucleo/MainActivity.kt").read_text(encoding="utf-8")
secret_store = (ROOT / "app/src/main/java/io/github/astromg01/nucleo/core/security/SecretStore.kt").read_text(encoding="utf-8")
check("shareProjectContext" in entities and "task.shareProjectContext" in worker,
      "Project-context sharing is persisted and gated in the worker", "Project snapshot lacks a persisted consent gate")
check("consentToShareCode" in tasks and "Autorizo o envio de um snapshot limitado" in tasks,
      "Project-task UI explains optional consent before sharing excerpts", "Project-task UI consent explanation missing")
coordinator = (ROOT / "app/src/main/java/io/github/astromg01/nucleo/domain/task/TaskCoordinator.kt").read_text(encoding="utf-8")
check("shareProjectContext = projectId != null && consentToShareProjectContext" in coordinator,
      "Project linkage does not implicitly authorize sharing source files", "Project context consent is not persisted independently")
check("AndroidKeyStore" in secret_store and "AES/GCM/NoPadding" in secret_store,
      "API key storage uses Android Keystore and AES-GCM", "API key encryption marker missing")
check("TODO(" not in "\n".join(p.read_text(encoding="utf-8", errors="ignore") for p in ROOT.rglob("*.kt")),
      "No unresolved TODO() placeholders", "Found TODO() placeholders")

lang_interpreter = (ROOT / "app/src/main/java/io/github/astromg01/nucleo/core/lang/NucleoInterpreter.kt").read_text(encoding="utf-8")
host_api = (ROOT / "app/src/main/java/io/github/astromg01/nucleo/core/lang/NucleoHostFunction.kt").read_text(encoding="utf-8")
check(all(token in lang_interpreter for token in ["ListLiteral", "IndexGet", "IndexSet", '"push"', '"pop"', '"len"']),
      "NúcleoLang list literals, indexing and collection builtins are wired into the runtime", "NúcleoLang collection runtime wiring is incomplete")
check("NucleoHostFunction" in host_api and "arbitrary JVM objects is rejected" in host_api,
      "NúcleoLang exposes explicit host capabilities and documents value restrictions", "NúcleoLang host-capability contract is missing")

print("Núcleo AI — static preflight")
for item in checks:
    print(f"PASS  {item}")
for item in errors:
    print(f"FAIL  {item}", file=sys.stderr)
print(f"\n{len(checks)} checks passed; {len(errors)} errors.")
print("NOTE: This is not an Android compile, lint, instrumentation test, or device smoke test.")
sys.exit(1 if errors else 0)
