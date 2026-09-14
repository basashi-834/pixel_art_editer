# Pixel Sprite Editor

90年代の対戦格闘ゲーム（ストリートファイターII風）のキャラクタースプライトを
描くためのドット絵エディタです。このリポジトリでは同じアプリを複数の技術で
作り直してきました。**デスクトップ向けの現在の実装は Java（Swing）版**、
**スマートフォン（iPhone等）のブラウザでも動く実装として Web版**を並行して
提供しています。

## 現在の実装

- [`PixelSpriteEditor/`](PixelSpriteEditor/README.md)（Java / Swing、デスクトップ向け）
  JDK標準ライブラリのみで実装。外部ライブラリ・ビルドツール不要、`javac`/`java`
  だけでビルド・実行できます。中身は**すべてコンパイル前のソースコード**で、
  ビルド済みバイナリは含みません。Swing製デスクトップアプリのため、
  スマートフォンのOS上ではそのまま動作しません。
- [`WebPixelSpriteEditor/`](WebPixelSpriteEditor/README.md)（HTML/CSS/素のJavaScript、iPhone等スマホ対応）
  外部ライブラリ・ビルドツール不要。`index.html` を開くだけで動きます
  （PCならダブルクリック、iPhoneならホスティングしてSafariで開く）。
  Java版とほぼ同じ機能を持ち、ピンチズーム・2本指パンなどタッチ操作にも
  対応しています。詳しくは
  [`WebPixelSpriteEditor/README.md`](WebPixelSpriteEditor/README.md) を
  参照してください。

両実装とも今後メンテナンス対象です。用途に応じて使い分けてください
（PCでしっかり作業するならJava版、外出先や手元にiPhoneしかない場合はWeb版）。

## これまでの経緯（Java版に至るまでの3つの実装）

1. **C++ / SDL2**（[`legacy-cpp-sdl2/`](legacy-cpp-sdl2/)）
   最初の実装。動作はしていましたが、ビルドした未署名の `.exe` が Windows
   Smart App Control に再現性なくブロックされ続け、解決策が見つかりませんでした。
2. **C# / Avalonia UI**（[`legacy-csharp-avalonia/`](legacy-csharp-avalonia/)）
   フレームワーク依存配布（Microsoft署名済みの `dotnet.exe` 経由で起動）に
   することでSmart App Controlの問題を回避した2つ目の実装。これも動作は
   していましたが、より確実な安心感（＝ユーザー自身がソースからビルドする）を
   求めて次に進みました。
3. **Java / Swing**（[`PixelSpriteEditor/`](PixelSpriteEditor/)）— **現在**
   コンパイル前のソースコードを受け取り、自分のPC上で `javac`/`java` のみで
   ビルド・実行できる形にした3つ目の実装。実行するものを100%自分の手元で
   ビルドするため、配布バイナリの信頼性を気にする必要がありません。

各フォルダの `NOTE.md`（`legacy-*/`内）に、そのバージョンから次に進んだ理由を
記載しています。過去の実装は参考用に残していますが、メンテナンス対象外です。
