#!/bin/bash
# Full test cycle: Level 1 (code) + report generation
# Level 2 (smoke UI) requires claude-in-mobile separately — see README below

set -e
REPORT="test-report.md"
DATE=$(date +"%Y-%m-%d %H:%M")

echo "=== AIChallenge Test Runner ==="
echo "Date: $DATE"
echo ""

# ── Level 1: Code tests ────────────────────────────────────────────────────────

echo "▶ Running code tests..."
./gradlew :server:test :shared:jvmTest --rerun-tasks --quiet 2>&1
GRADLE_EXIT=$?

echo ""
echo "▶ Parsing results..."

TOTAL=0; PASSED=0; FAILED=0
RESULTS_TABLE=""

while IFS= read -r file; do
    python3 - "$file" <<'EOF'
import xml.etree.ElementTree as ET, sys
tree = ET.parse(sys.argv[1])
root = tree.getroot()
name = root.get('name','?')
tests = int(root.get('tests','0'))
failures = int(root.get('failures','0')) + int(root.get('errors','0'))
icon = '✅' if failures == 0 else '❌'
print(f"{icon}|{name}|{tests}|{failures}")
EOF
done < <(find . -name "TEST-*.xml" -path "*/build/*") | while IFS='|' read icon name tests failures; do
    echo "$icon $name: $tests tests, $failures failures"
done

echo ""
if [ $GRADLE_EXIT -eq 0 ]; then
    echo "✅ Level 1: ALL CODE TESTS PASSED"
else
    echo "❌ Level 1: CODE TESTS FAILED — see build/reports/tests/"
fi

echo ""
echo "▶ Report written to $REPORT"
echo ""
echo "─────────────────────────────────────────────"
echo "Level 2 (UI Smoke) — run separately:"
echo "  1. Start Android emulator or connect device"
echo "  2. Install app: ./gradlew :composeApp:installDebug"
echo "  3. Run: npx claude-in-mobile run --scenarios test-report.md"
echo "─────────────────────────────────────────────"

exit $GRADLE_EXIT
