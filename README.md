# Pixel Sprite Editor

90年代の対戦格闘ゲーム（ストリートファイターII風）のキャラクタースプライトを
描くためのドット絵エディタです。このリポジトリでは同じアプリを3つの技術で
作り直してきました。**現在の実装は Java（Swing）版**です。

## 現在の実装: [`PixelSpriteEditor/`](PixelSpriteEditor/README.md)（Java / Swing）

JDK標準ライブラリのみで実装。外部ライブラリ・ビルドツール不要、`javac`/`java`
だけでビルド・実行できます。中身は**すべてコンパイル前のソースコード**で、
ビルド済みバイナリは含みません。使い方・ビルド手順・機能一覧は
[`PixelSpriteEditor/README.md`](PixelSpriteEditor/README.md) を参照してください。

## これまでの経緯（3つの実装）

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
