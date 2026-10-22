#!/bin/bash -e
set +x

# Modify the Info.plist to ensure that CFBundleVersion is always incremented
# AppStoreConnect requires each upload, whether it is released or not, to be
# higher than the previous successful upload's CFBundleVersion. So this
# script sets the CFBundleVersion to the first component of
# CFBundleShortVersionString and the UTC timestamp of when this script was run
# is appended as the second component
info_plist="$BUILT_PRODUCTS_DIR/$INFOPLIST_PATH"
if [ ! -f "$info_plist" ]; then
    echo "Error: $info_plist does not exist or is not a regular file" >&2
    exit 1
fi

bundle_short_version=`/usr/libexec/PlistBuddy -c "Print :CFBundleShortVersionString" "$info_plist"`
if [ -z "$bundle_short_version" ]; then
    echo "Error: CFBundleShortVersionString key in $info_plist empty" >&2
    exit 1
fi

major_version=`echo "$bundle_short_version" | cut -d. -f1`
if [ -z "$major_version" -o "$major_version" = "0" ]; then
    echo "Error: CFBundleShortVersionString major version is empty or 0" >&2
    exit 1
fi

bundle_version="$major_version.`date -u '+%Y%m%d%H%M'`"
/usr/libexec/PlistBuddy -c "Set :CFBundleVersion $bundle_version" "$info_plist"

# Keep the template's curated UIAppFonts list (including configured app fonts),
# but omit entries whose files are not in this build's bundle. Core registers
# its full runtime font directories itself when it builds the document font
# list; adding every runtime font here would register those files twice and
# move extra font work into app launch.
bundle_root="$BUILT_PRODUCTS_DIR/$WRAPPER_NAME"
source_info_plist="$INFOPLIST_FILE"
if [[ "$source_info_plist" != /* ]]; then
    source_info_plist="$PROJECT_DIR/$source_info_plist"
fi
if [ ! -f "$source_info_plist" ]; then
    echo "Error: source Info.plist $source_info_plist does not exist" >&2
    exit 1
fi
font_entries=()
missing_font_count=0
font_index=0
while relative_path=$(/usr/libexec/PlistBuddy -c "Print :UIAppFonts:$font_index" "$source_info_plist" 2>/dev/null); do
    if [ -f "$bundle_root/$relative_path" ]; then
        font_entries+=("$relative_path")
    else
        missing_font_count=$((missing_font_count + 1))
    fi
    font_index=$((font_index + 1))
done

if /usr/libexec/PlistBuddy -c "Print :UIAppFonts" "$info_plist" >/dev/null 2>&1; then
    /usr/libexec/PlistBuddy -c "Delete :UIAppFonts" "$info_plist"
fi
if [ "${#font_entries[@]}" -gt 0 ]; then
    /usr/libexec/PlistBuddy -c "Add :UIAppFonts array" "$info_plist"
    for index in "${!font_entries[@]}"; do
        /usr/libexec/PlistBuddy -c "Add :UIAppFonts:$index string ${font_entries[$index]}" "$info_plist"
    done
fi

echo "Kept ${#font_entries[@]} bundled UIAppFonts entries, removed $missing_font_count missing paths, and updated CFBundleVersion"
