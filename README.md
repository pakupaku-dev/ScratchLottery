# ScratchLottery

Paper 26.2 / Java 25向けのスクラッチ宝くじプラグインです。

## 機能

- GUIからゲーム内通貨で購入。定型枚数とチャットによる任意枚数指定。
- 発行バッチ、1等〜5等の当たり本数、賞金・アイテム報酬を設定。
- 購入・当選・支払・払戻・等級別の統計を記録。

## 導入とコマンド

VaultとVault対応経済プラグインが必要です。ビルドしたJARをPaperの `plugins` に入れて再起動し、生成された `plugins/ScratchLottery/config.yml` を設定してください。

- `/scratch`：購入GUI（別名 `/scratchlottery`）。
- `/scratch stats`：自分の統計。
- `/scratch prizes`：賞品一覧。
- `/scratch reload`：設定再読み込み。

`scratchlottery.use` は全員、`scratchlottery.reload` はOPに既定で許可されます。

VaultAPIは[公式のMaven導入手順](https://github.com/MilkBowl/VaultAPI)に従い、JitPackからビルド時に取得します。

## ビルド

JDK 25とMaven 3.9以降を用意し、リポジトリ直下で `mvn clean verify` を実行します。
生成物: `target/ScratchLottery-1.0.0.jar`。Paper 26.2の `plugins` フォルダーへ配置して再起動してください。
