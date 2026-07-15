#!/system/bin/sh
# discover.sh — Xiaomi 15T Pro (HyperOS) の輝度/HBM 関連 sysfs ノードを調べる
#
# 使い方（どちらか）:
#   A) Termux で:   su -c 'sh /sdcard/discover.sh'
#   B) PC から:     adb push discover.sh /data/local/tmp/ && adb shell 'su -c "sh /data/local/tmp/discover.sh"'
#
# 実機を壊す書き込みは一切しない。読み取りのみ。出力を貼ってくれれば
# 15T Pro 専用の最短コマンドを確定します。

echo "================ MaxBright discover ================"
echo "# device"
getprop ro.product.model
getprop ro.build.version.incremental
getprop ro.mi.os.version.name 2>/dev/null
echo

echo "# 1) backlight ノード（brightness / max_brightness）"
for d in /sys/class/backlight/*; do
    [ -d "$d" ] || continue
    echo "-- $d"
    for f in brightness max_brightness actual_brightness bl_power type; do
        [ -e "$d/$f" ] && printf "   %-16s = %s\n" "$f" "$(cat "$d/$f" 2>/dev/null)"
    done
done
echo

echo "# 2) LED backlight（mtk 系に多い）"
for d in /sys/class/leds/*backlight* /sys/class/leds/lcd-backlight; do
    [ -d "$d" ] || continue
    echo "-- $d"
    for f in brightness max_brightness; do
        [ -e "$d/$f" ] && printf "   %-16s = %s\n" "$f" "$(cat "$d/$f" 2>/dev/null)"
    done
done
echo

echo "# 3) Xiaomi mi_display / mi_disp（HBM はここの disp_param が多い）"
for base in /sys/class/mi_display /sys/class/mi_disp; do
    [ -d "$base" ] || continue
    for d in "$base"/*; do
        [ -d "$d" ] || continue
        echo "-- $d"
        ls "$d" 2>/dev/null | tr '\n' ' '; echo
        for f in disp_param hbm hbm_mode brightness_clone dc_alpha; do
            [ -e "$d/$f" ] && printf "   %-16s (存在)\n" "$f"
        done
    done
done
echo

echo "# 4) 名前に hbm を含むノードを全検索（読み取り可能なものだけ表示）"
find /sys -maxdepth 7 -iname '*hbm*' 2>/dev/null | while read p; do
    v=$(cat "$p" 2>/dev/null)
    printf "   %s = %s\n" "$p" "$v"
done
echo

echo "# 5) 名前に brightness を含む書き込み可能ノード"
find /sys -maxdepth 7 -name 'brightness' -perm -u+w 2>/dev/null | while read p; do
    mx="${p%brightness}max_brightness"
    printf "   %s (cur=%s max=%s)\n" "$p" "$(cat "$p" 2>/dev/null)" "$(cat "$mx" 2>/dev/null)"
done
echo "================ end ================"
