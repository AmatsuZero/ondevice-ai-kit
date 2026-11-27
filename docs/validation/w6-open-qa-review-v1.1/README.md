# W6 v1.1 独立人工评阅

两位评阅者各自观看 12 段原片，为 24 道题的 48 份匿名回答评分。每人从空白表开始，独立交回一份 JSON。表单可在本机浏览器离线使用。

## 准备原片和空白表

从主检出根目录运行。已有的本机评估素材可直接使用。若 12 份 `clip-01.mp4` 至 `clip-12.mp4` 在其他目录，将 `W6_REVIEW_MEDIA` 设为该目录的绝对路径。命令会逐份核对 SHA-256，全部匹配后才输出评阅页路径。表单通过本地链接读取视频。Charades 素材及衍生预览仅供本地评估。

```sh
(
  set -eu
  packet="$PWD/docs/validation/w6-open-qa-review-v1.1/review"
  media="${W6_REVIEW_MEDIA:-$PWD/.worktrees/foundation/artifacts/verification/w6-everyday-open-qa-20261127/media}"
  media="$(cd "$media" && pwd -P)"
  (cd "$media" && shasum -a 256 -c "$packet/media.sha256")
  mkdir -p artifacts
  form="$(mktemp -d "$PWD/artifacts/w6-review-v1.1-XXXXXXXX")"
  cp "$packet/评阅.html" "$form/评阅.html"
  ln -s "$media" "$form/media"
  printf '%s\n' "$form/评阅.html"
)
```

## 填写和交回

评阅者在另一台已有评估原片的电脑上工作时，只需 `review/评阅.html` 和 `review/media.sha256`。把两者放在同一目录，将本机已获准使用的 12 份原片放在其下的 `media/`，文件名与清单一致。在 Mac 上从评阅页所在目录运行 `(cd media && shasum -a 256 -c ../media.sha256)`，确认 12 项全部显示 `OK`，再打开页面。只向评阅者交付空白表和校验清单，协调人保管模型对照键。

在浏览器里打开上一步输出的 HTML 路径。先看片和题目，再展开两份回答。每个要点计 0 或 1 分。给分时，从这份回答里逐字摘录依据。遇到关键反例时，填写理由。需要中断时点「保存草稿」，回来后可导入继续。

完成 24 题后点「导出评分」，将 `final-*.json` 交回协调人。请保留两份独立原始回卷。对卷步骤见[协调人说明](coordinator/README.md)。
