# LiteEncoderDesktop

ffmpeg を使った Windows 向けの簡易動画エンコーダ。

動画ファイルをドロップし、プレビューと推定サイズを見ながら品質やビットレートを調整して書き出す。

## 必要なもの

- Windows (x64)
- ffmpeg / ffprobe

## ビルド

JDK 21 が必要。

```
./gradlew run         # 起動
./gradlew packageMsi  # MSI を作成
```

## 技術スタック

- Kotlin / Compose Multiplatform (Desktop)
