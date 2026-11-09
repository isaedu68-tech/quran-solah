#!/usr/bin/env bash
# Gửi tin/tệp vào Telegram của chủ repo (bot Radar NEWs).
#   tg.sh doc <tệp> <chú thích>     gửi tệp (APK…)
#   tg.sh msg <nội dung>            gửi tin nhắn
# Cần secret TELEGRAM_BOT_TOKEN (env TOKEN). Chat id: secret TELEGRAM_CHAT_ID → file .tgchat ở nhánh radar-pdf → tin nhắn gần nhất gửi bot.
set -uo pipefail
TOKEN=$(printf '%s' "${TOKEN:-}" | tr -d '[:space:]')
[ -z "$TOKEN" ] && { echo "Chưa có TELEGRAM_BOT_TOKEN – bỏ qua gửi Telegram"; exit 0; }
API="https://api.telegram.org/bot$TOKEN"
CHAT="${CHAT:-}"; TH=()
# Nhóm "Studio Nội dung" có chia ngăn: gửi vào ngăn $TOPIC (mặc định app)
T="$(dirname "$0")/../tg_topics.json"
if [ -s "$T" ]; then CHAT=$(jq -r .chat "$T"); TH=(-F "message_thread_id=$(jq -r ".${TOPIC:-app}" "$T")"); fi
if [ -z "$CHAT" ]; then
  git fetch -q --depth 1 origin radar-pdf 2>/dev/null && CHAT=$(git show FETCH_HEAD:.tgchat 2>/dev/null | tr -d '[:space:]')
fi
if [ -z "$CHAT" ]; then
  CHAT=$(curl -s "$API/getUpdates" | jq -r '[.result[] | (.message // .channel_post // .my_chat_member) | .chat.id] | last // empty')
fi
[ -z "$CHAT" ] && { echo "::warning::Không tìm thấy chat Telegram – gửi /start cho bot rồi build lại"; exit 0; }
case "$1" in
  doc) res=$(curl -s --retry 5 --retry-delay 10 --retry-all-errors -F chat_id="$CHAT" "${TH[@]}" -F "document=@$2" --form-string caption="${3:0:1000}" "$API/sendDocument");;
  msg) res=$(curl -s --retry 5 --retry-delay 10 --retry-all-errors -F chat_id="$CHAT" "${TH[@]}" --form-string text="${2:0:3500}" -F disable_web_page_preview=true "$API/sendMessage");;
esac
echo "$res" | jq -e '.ok' >/dev/null && echo "Đã gửi Telegram" || echo "::warning::Telegram từ chối: $(echo "$res" | jq -r '.description')"
exit 0
