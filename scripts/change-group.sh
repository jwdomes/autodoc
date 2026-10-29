#!/usr/bin/env bash
# Changes autodoc's Maven group and package root, e.g. com.example -> org.myco.
# Usage: scripts/change-group.sh <new-group>
set -euo pipefail

if [[ $# -ne 1 || ! $1 =~ ^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)*$ ]]; then
  echo "Usage: $0 <new-group>   (for example: $0 org.myco)" >&2
  exit 1
fi

cd "$(dirname "$0")/.."
new=$1
old=$(sed -n 's/^group=//p' gradle.properties)
if [[ $new == "$old" ]]; then
  echo "Group is already $new" >&2
  exit 1
fi

for s in main testFixtures test; do
  mkdir -p "src/$s/java/${new//.//}"
  git mv "src/$s/java/${old//.//}/autodoc" "src/$s/java/${new//.//}/autodoc"
done
# perl rather than sed -i, which differs between GNU and macOS
grep -rlF "$old.autodoc" src | xargs perl -pi -e "s/\Q$old.autodoc\E/$new.autodoc/g"
perl -pi -e "s/^group=.*/group=$new/" gradle.properties

echo "Group and package changed from $old to $new. Now run: ./gradlew clean build publishToMavenLocal"
