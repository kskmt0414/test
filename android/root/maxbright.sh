#!/system/bin/sh
# maxbright.sh — root で「限界突破」: バックライトを物理最大にし、HBM を有効化する
#
# 使い方:
#   ON  : su -c 'sh /sdcard/maxbright.sh on'
#   OFF : su -c 'sh /sdcard/maxbright.sh off'
#
# 仕組み:
#   1) すべての backlight ノードで brightness = max_brightness を書く（＝物理最大）
#   2) HyperOS の disp_param 経由で HBM を ON/OFF（既知のマジック値を順に試行）
#   3) 名前に hbm を含むノードに 1/0 を書く（総当たり・安全側）
#
# ⚠️ 注意: HBM 常時 ON は 発熱・電池消費・OLED 焼き付き のリスクあり。
#          屋外など必要な時だけ ON にし、済んだら OFF に戻すこと。

MODE="${1:-on}"

log() { echo "[maxbright] $*"; }

set_backlight_max() {
    for d in /sys/class/backlight/* /sys/class/leds/lcd-backlight /sys/class/leds/*backlight*; do
        [ -d "$d" ] || continue
        b="$d/brightness"; m="$d/max_brightness"
        [ -w "$b" ] || continue
        if [ "$MODE" = "on" ] && [ -r "$m" ]; then
            mx=$(cat "$m" 2>/dev/null)
            [ -n "$mx" ] && echo "$mx" > "$b" 2>/dev/null && log "backlight max: $b <- $mx"
        fi
    done
}

# HyperOS/Xiaomi: /sys/class/mi_display/disp-DSI-0/disp_param に hex を書いて HBM 制御。
# 機種で値が違うため、代表的な HBM on/off 値を順に試す。
# （0x10000 台 = HBM 系。discover.sh の結果で確定値に絞れます）
set_hbm_disp_param() {
    for base in /sys/class/mi_display /sys/class/mi_disp; do
        for d in "$base"/disp-DSI-0 "$base"/disp-DSI-*; do
            p="$d/disp_param"
            [ -w "$p" ] || continue
            if [ "$MODE" = "on" ]; then
                for v in 0x50000 0xF0000 0x10000; do
                    echo "$v" > "$p" 2>/dev/null && log "disp_param HBM on try: $p <- $v"
                done
            else
                for v in 0xE0000 0x20000 0x00000; do
                    echo "$v" > "$p" 2>/dev/null && log "disp_param HBM off try: $p <- $v"
                done
            fi
        done
    done
}

set_hbm_nodes() {
    val=0; [ "$MODE" = "on" ] && val=1
    find /sys -maxdepth 7 -iname '*hbm*' -perm -u+w 2>/dev/null | while read p; do
        # ディレクトリではなくファイルのみ
        [ -f "$p" ] || continue
        echo "$val" > "$p" 2>/dev/null && log "hbm node: $p <- $val"
    done
}

log "mode = $MODE"
set_backlight_max
set_hbm_disp_param
set_hbm_nodes
log "done. 効果がなければ discover.sh の出力を共有してください。"
