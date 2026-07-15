# root で「限界突破」— Xiaomi 15T Pro 向け

root 権限があれば、OS のスライダー最大を**超える** HBM（High Brightness Mode）を
強制でき、バックライトを物理最大まで上げられます。ここに 2 本のスクリプトがあります。

> ⚠️ **リスク**: HBM 常時 ON は 発熱・電池急減・OLED 焼き付き（寿命低下）を招きます。
> 屋外など**必要な時だけ ON**にし、済んだら **OFF** に戻してください。自己責任で。

## 手順

### 1. 実機のノードを調べる（読み取りのみ・安全）

Termux の場合:
```sh
cp discover.sh /sdcard/ ; su -c 'sh /sdcard/discover.sh' | tee /sdcard/discover.txt
```
PC + adb の場合:
```sh
adb push discover.sh /data/local/tmp/
adb shell 'su -c "sh /data/local/tmp/discover.sh"'
```

**この出力を私に貼ってください。** 15T Pro 専用の「これ一発」コマンドを確定します。

### 2. 限界突破を適用する

```sh
su -c 'sh /sdcard/maxbright.sh on'    # 最大＋HBM ON
su -c 'sh /sdcard/maxbright.sh off'   # 元に戻す
```

`maxbright.sh` は既知パターンを総当たりで安全側に試します。効かない場合は
手順 1 の出力から正しいノード/値を特定して確定版に絞ります。

## 補足：仕組み

- **バックライト最大化**: `/sys/class/backlight/*/brightness` に `max_brightness` の値を書く。
  スライダーの上限（例: 2047）を超えて、パネルが持つ真の最大値まで上げられます。
- **HBM**: HyperOS は `/sys/class/mi_display/disp-DSI-0/disp_param` に hex マジック値を
  書いて HBM を切り替えます。値は機種依存のため discover.sh で確定します。
- これらは AMOLED パネルドライバへの直接指示で、root なしのアプリからは書き込めません。

## 恒久化したい場合（Magisk）

起動のたびに手動実行したくない場合は Magisk の `post-fs-data.d` / サービススクリプトに
`maxbright.sh on` を置く方法があります。ただし常時 HBM は焼き付きリスクが高いので
非推奨。トグル運用（必要時だけ ON）を推奨します。
