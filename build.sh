#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
command -v javac >/dev/null || { echo 'Cần cài JDK 17 trở lên (thiếu javac).' >&2; exit 1; }
# Clean generated classes so renamed/deleted sources never remain in the JAR.
rm -rf build/classes build/test-classes
mkdir -p build/classes build/test-classes
find src/main/java -name '*.java' > build/sources.txt
javac -encoding UTF-8 --release 17 -d build/classes @build/sources.txt
jar --create --file build/pharmacy.jar --main-class vn.pharmacy.Main -C build/classes .
if [[ "${1:-}" == "test" ]]; then
 find src/test/java -name '*.java' > build/tests.txt
 javac -encoding UTF-8 --release 17 -cp build/classes -d build/test-classes @build/tests.txt
 for suite in ServiceTest DaoTest RecordEditorTest TableSupportTest InvoicePrintTest; do
  java -ea -Djava.awt.headless=true -cp build/classes:build/test-classes "vn.pharmacy.$suite"
 done
fi
