# My Video API

NewPipeExtractorを利用する試作用APIです。

## エンドポイント
- `GET /health` 起動確認
- `GET /api/video/{動画ID}` 動画情報と720p以下の映像・音声一体型ストリームを返す

## Render
1. このZIPを展開し、中のファイルとフォルダーをGitHubリポジトリの直下にアップロード。
2. RenderでNew Web Serviceを作り、自分のGitHubリポジトリを選択。
3. RuntimeはDockerを選択。
4. デプロイ後、`/health` にアクセスして起動確認。

この試作品は動画ファイルを保存・中継せず、再生URLをJSONで返すだけです。再生URLは期限切れやアクセス制限で再生できなくなる場合があります。公開運用前に利用規約・著作権・アクセス制限を確認してください。NewPipeExtractorはGPL-3.0です。
