name: Update Manifest
on:
push:
branches: [ "master" ]
jobs:
build-manifest:
runs-on: ubuntu-latest
steps:
- name: Checkout
uses: actions/checkout@v4

      - name: Generate MANIFEST.md
        run: |
          REPO="boogiback/BAM-"
          BRANCH="master"
          RAW_BASE="https://raw.githubusercontent.com/$REPO/$BRANCH"
          echo "# Code Manifest (ChatGPT)" > MANIFEST.md
          echo "" >> MANIFEST.md
          echo "**Branch:** $BRANCH  " >> MANIFEST.md
          echo "**Primary entry:** \`app/src/main/AndroidManifest.xml\`" >> MANIFEST.md
          echo "" >> MANIFEST.md
          echo "## Key files" >> MANIFEST.md
          # הוסף כאן תבנית מותאמת: רשימות מסוימות של קבצים שתרצה תמיד לכלול
          for f in \
            app/src/main/AndroidManifest.xml \
            app/src/main/res/values/strings.xml \
            app/src/main/res/layout/activity_main.xml \
            app/src/main/java \
          ; do
            if [ -e "$f" ]; then
              if [ -d "$f" ]; then
                # קח רק קבצי קוד/לייאאוט מעניינים
                find "$f" -type f \( -name "*.java" -o -name "*.kt" -o -name "*.xml" \) | sort | while read -r file; do
                  echo "- $file" >> MANIFEST.md
                  echo "  RAW: $RAW_BASE/$file" >> MANIFEST.md
                done
              else
                echo "- $f" >> MANIFEST.md
                echo "  RAW: $RAW_BASE/$f" >> MANIFEST.md
              fi
            fi
          done

      - name: Commit changes
        run: |
          if git diff --quiet MANIFEST.md; then
            echo "No changes to MANIFEST.md"
          else
            git config user.name "github-actions[bot]"
            git config user.email "41898282+github-actions[bot]@users.noreply.github.com"
            git add MANIFEST.md
            git commit -m "chore: update MANIFEST.md"
            git push
          fi
