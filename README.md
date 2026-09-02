# Pixel Sprite Editor

90年代の対戦格闘ゲーム（ストリートファイターII風）のキャラクタースプライトを
描くためのシンプルなドット絵エディタです。C# + [Avalonia UI](https://avaloniaui.net/)
製で、Windows / Linux / macOS で動作します。

> 以前は C++/SDL2 で実装していましたが、ビルドした exe が Windows Smart App
> Control に不安定にブロックされる問題が解決できなかったため、C# + Avalonia
> で最初から作り直しました。経緯は [`legacy-cpp-sdl2/NOTE.md`](legacy-cpp-sdl2/NOTE.md)
> を参照してください。旧実装自体は `legacy-cpp-sdl2/` 以下にそのまま残っています。

---

## 特長

- **16×16px ブロック単位**でキャラクターを組み立てられるドット絵キャンバス
- 内部は実解像度のまま保持し、**1x〜32x のニアレストネイバー拡大表示**で編集
  (1クリック = 1ピクセル)
- **1px グリッド** と **16×16 ブロックガイド**を個別にON/OFF可能。
  どちらも保存PNGには一切焼き込まれません
- 鉛筆・消しゴム・塗りつぶし（4方向）・スポイトの4ツール
- **RGB565 (16bit, R5G6B5)** カラーモデル。0-255のRGB入力を16bit相当に
  自動丸め込みし、実際にゲーム機で見える色をそのまま表示
- 透明背景はチェッカーボード表示、保存PNGでは正しく alpha=0
- 元の解像度のまま RGBA PNG として保存・読み込み
- Undo / Redo (Ctrl+Z / Ctrl+Y)
- ズーム、パン（中ボタンドラッグ / Space+ドラッグ）
- 日本語 / English 切り替え
- OSネイティブのファイルダイアログで開く・保存する

## 動作環境

実行には **.NET 8 Desktop Runtime** が必要です（.NET 8 SDK でも可）。
配布物には .exe や DLL 本体は含めていますが、.NET 本体（ランタイム）は
含まれていません（後述の「フレームワーク依存配布」を参照）。

- Windows 10/11 (x64) / Linux / macOS
- [.NET 8 Desktop Runtime](https://dotnet.microsoft.com/download/dotnet/8.0)
  (Windows の場合は "Desktop Runtime 8.0.x - Windows x64" を選択)

## 使用ライブラリ

| ライブラリ | 用途 |
|---|---|
| [Avalonia UI](https://avaloniaui.net/) 11.1 | クロスプラットフォームGUI（ウィンドウ・メニュー・OSネイティブファイルダイアログ） |
| [SixLabors.ImageSharp](https://sixlabors.com/products/imagesharp/) 3.1 | PNGの読み込み・書き出し（正確なピクセル単位の入出力のため） |

Avalonia自体が持つ`Bitmap`/`WriteableBitmap`は書き込み用途に最適化されており、
読み込んだPNGから生ピクセルを確実に取り出す標準APIがないため、PNGの
読み書きだけはImageSharpに任せています（画面表示用のプレビュー生成は
Avaloniaの`WriteableBitmap`を使用）。

### なぜC++/SDL2からC#/Avaloniaに変えたのか

旧C++版は単一の未署名exeをビルドしていましたが、Windows Smart App Control に
再現性なくブロックされ続け、コード署名や配布経路を変えても解決しませんでした。
C#への移行で「フレームワーク依存配布」（後述）を選んだのは、実際に起動する
プロセスをMicrosoft自身が署名した`dotnet.exe`にすることで、この種の
未知バイナリ判定を根本的に回避するためです。副次効果として、Avaloniaの
OSネイティブファイルダイアログにより、旧版で何度も手直しが必要だった
「開く/保存」のパス指定UIを自作する必要もなくなりました。

## ビルド方法（ソースから）

```bash
cd src
dotnet build
```

## 実行方法

### ソースから直接実行

```bash
cd src
dotnet run
```

### 配布されたZIPから実行（Windows）

1. ZIPを展開する
2. `.NET 8 Desktop Runtime` が入っていなければ先にインストールする
   （[ダウンロードページ](https://dotnet.microsoft.com/download/dotnet/8.0)）
3. `run.bat` をダブルクリックする

`run.bat` は展開先フォルダで `dotnet PixelSpriteEditor.dll` を実行するだけの
シンプルなバッチファイルです。ネイティブexeを自前ビルドしないことで、
署名なしバイナリとして警告・ブロックされる問題を避けています
（＝**フレームワーク依存配布**）。

### 自分で配布物を作る

```bash
cd src
dotnet publish -c Release -r win-x64 --self-contained false -p:UseAppHost=false -o ../dist
cp ../dist-template/run.bat ../dist/
```

`../dist/` に `run.bat` と一緒にZIPで固めれば配布できます。

## 使い方

| 操作 | 内容 |
|---|---|
| 左クリック（鉛筆/消しゴム） | ドラッグで連続描画（間の座標も自動補間） |
| 左クリック（塗りつぶし） | クリックした位置と同じ色の4方向連結領域を塗る |
| 左クリック（スポイト） | クリックした位置の色を現在色にする |
| マウスホイール | ズームイン・アウト（カーソル位置を中心に） |
| 中ボタンドラッグ / Space+ドラッグ | キャンバスをパン（平行移動） |

### 新規キャンバス

`File > New` から、**ピクセルサイズ指定**と**タイル数指定**
（16×16ブロック単位、格ゲーのスプライトらしく組み立てやすい）の
どちらでも新規キャンバスを作成できます。

### カラー

右パネルのR/G/B/Aスライダーは0-255の見た目上の値ですが、内部的には
RGB565（R5G6B5）に丸め込まれます。`RGB565: 0xXXXX` の表示で、実際に
16bitカラーとして保持される値を常に確認できます。Aのみ8bitのまま
（16bitカラーフォーマットにアルファは無いため）。

### PNGの保存について

保存されるPNGは、キャンバスの実解像度（表示ズーム倍率とは無関係）で、
グリッドやチェッカーボードなどの編集用UIは一切含まれません。透明ピクセルは
`alpha = 0` としてそのまま保存されます。

## キーボードショートカット

| 操作 | ショートカット |
|---|---|
| 新規キャンバス | Ctrl+N |
| 開く | Ctrl+O |
| 保存 | Ctrl+S |
| 名前を付けて保存 | Ctrl+Shift+S |
| 元に戻す | Ctrl+Z |
| やり直し | Ctrl+Y |
| ズームイン | Ctrl++ |
| ズームアウト | Ctrl+- |
| パン（一時的） | Space を押しながらドラッグ |

## ファイル構成

```
src/
  Program.cs              エントリポイント（通常起動 / --headless-verify / --selftest）
  SelfTest.cs              ロジック層の自己テスト（モデル/ツール/PNG入出力）
  App.axaml(.cs)           Avaloniaアプリケーションのブートストラップ
  Models/
    PixelColor.cs           RGBA8888のピクセル値 + RGB565量子化
    PixelCanvas.cs           実解像度のピクセルバッファ
    History.cs               ストローク単位のUndo/Redo
    EditorState.cs            アプリの中心状態（キャンバス・ツール・カメラ・色）
  Tools/
    IEditorContext.cs / ITool.cs   ツールが必要とする最小インターフェース
    PencilTool.cs / EraserTool.cs / FillTool.cs / EyedropperTool.cs
    LineUtil.cs               ブレゼンハムの線分補間（ドラッグの間引き防止）
  Services/
    Loc.cs                    日本語/英語の切り替え
    ImageIO.cs                 PNGの読み書き（ImageSharp）+ 表示用ビットマップ生成
  Views/
    MainWindow.axaml(.cs)     メニュー・ツールバー・カラーパネル・ステータスバー
    PixelCanvasView.cs         キャンバス描画（ニアレストネイバー拡大・グリッド）とポインタ操作
    NewCanvasDialog.axaml(.cs) 新規キャンバスダイアログ

dist-template/
  run.bat                   配布用の起動バッチ（フレームワーク依存配布）

legacy-cpp-sdl2/            旧C++/SDL2実装（参考用、メンテナンス対象外）
```

新しいツールを追加する場合は `ITool` を実装して `EditorState` の
`_tools` 辞書に登録するだけで済むように設計しています。アニメーション
フレーム、スプライトシート書き出し、左右反転、選択範囲、コピー＆ペースト、
レイヤー、パレット/プロジェクト保存、オニオンスキンなどは現時点では
未実装ですが、`EditorState`/`PixelCanvas` の単純な構造の上に無理なく
拡張できるはずです。

## 動作確認について

このサンドボックス環境にはディスプレイが無いため、実際の目視確認は
Avalonia.Headlessによるオフスクリーンレンダリングで行っています。

```bash
cd src
dotnet run -- --selftest              # モデル/ツール/PNG入出力のロジックテスト
dotnet run -- --headless-verify <dir> # 実際のポインタ操作・Ctrl+Zショートカット・
                                       # 言語切り替えをUI越しに再現し、各段階のPNGを書き出す
```
