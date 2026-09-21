import os
import asyncio
import contextlib
import html
import logging
from pathlib import Path
from sys import argv

from pyrogram import Client
from pyrogram.types import InputMediaDocument, LinkPreviewOptions

logging.basicConfig(
    level=logging.INFO,
    format="[%(asctime)s] %(levelname)s [%(name)s]: %(message)s",
    datefmt="%H:%M:%S",
)
logging.getLogger("pyrogram.syncer").setLevel(logging.WARNING)

api_id = os.environ.get("APP_ID") or 6
api_hash = os.environ.get("APP_HASH") or "eb06d4abfb49dc3eeb1aeb98ae0f581e"
artifacts_path = Path("artifacts")
test_version = argv[3] == "test" if len(argv) > 3 else None
metadata_chat_id = argv[4].strip() if len(argv) > 4 and argv[4].strip() else None

def find_apk(abi: str) -> Path | None:
    return next((apk for apk in artifacts_path.rglob("*.apk") if abi in apk.name), None)

def get_commit_info():
    commit_id_raw = os.environ.get("COMMIT_ID") or "unknown"
    commit_id = commit_id_raw[:7]
    commit_url = os.environ.get("COMMIT_URL") or "https://github.com/HSSkyBoy/NiagramX/commits"
    commit_message = os.environ.get("COMMIT_MESSAGE") or "unknown"
    return commit_id, commit_url, commit_message

def get_caption() -> str:
    commit_id, commit_url, commit_message = get_commit_info()
    tag = "#updateBeta" if test_version else "#updateRelease"
    pre = "Test version." if test_version else "Release version."
    caption = f"{tag}\n{pre}\n\n"
    caption += f"Commit Message:\n<blockquote expandable>{html.escape(commit_message)}</blockquote>\n\n"
    caption += f"See commit details [{commit_id}]({commit_url})"
    return caption

def get_document() -> list["InputMediaDocument"]:
    documents = []
    abis = ["universal", "arm64-v8a", "x86_64"]
    for abi in abis:
        if apk := find_apk(abi):
            documents.append(
                InputMediaDocument(
                    media = str(apk),
                )
            )
    if not documents:
        raise FileNotFoundError("No APK artifacts found")
    base_caption = get_caption()
    if base_caption and len(base_caption) > 1024:
        base_caption = base_caption[:1020] + "..."
    documents[-1].caption = base_caption
    return documents

def get_metadata():
    commit_id = "<code>" + (os.environ.get("COMMIT_ID") or "unknown")[:7] + "</code>"
    commit_message = "<code>" + html.escape(os.environ.get("COMMIT_MESSAGE") or "unknown") + "</code>"
    build_timestamp = "<code>" + (os.environ.get("BUILD_TIMESTAMP") or "-1") + "</code>"
    tag = "#updateBeta" if test_version else "#updateRelease"
    return f"{tag}\n{build_timestamp} {commit_id}\n{commit_message}"

def retry(func):
    async def wrapper(*args, **kwargs):
        for attempt in range(3):
            try:
                return await func(*args, **kwargs)
            except Exception as e:
                logging.error(f"Attempt {attempt + 1} failed with error: {e}", exc_info=True)
                if attempt == 2:
                    raise
                await asyncio.sleep(5)
    return wrapper

@retry
async def send_to_channel(client: "Client", cid: str):
    with contextlib.suppress(ValueError):
        cid = int(cid)
    documents = get_document()
    print("Uploading to Telegram:", flush=True)
    for document in documents:
        p = Path(document.media)
        size_mb = p.stat().st_size / (1024 * 1024) if p.exists() else 0
        print(f"- {document.media} ({size_mb:.2f} MB)", flush=True)
    await asyncio.wait_for(
        client.send_media_group(
            cid,
            media = documents,
        ),
        timeout = 600,
    )
    print("Successfully uploaded media group to channel!", flush=True)

@retry
async def send_metadata(client: "Client", cid: str):
    with contextlib.suppress(ValueError):
        cid = int(cid)
    await client.send_message(
        chat_id = cid,
        text = get_metadata(),
    )
    print("Successfully sent metadata!", flush=True)

def get_client(bot_token: str):
    return Client(
        "helper_bot",
        api_id=api_id,
        api_hash=api_hash,
        bot_token=bot_token,
        in_memory=True,
        ipv6=False,
        max_concurrent_transmissions=8,
    )

async def main():
    bot_token = argv[1]
    chat_id = argv[2]
    client = get_client(bot_token)
    await client.start()
    try:
        await send_to_channel(client, chat_id)
        if metadata_chat_id and str(metadata_chat_id).strip() != str(chat_id).strip():
            await send_metadata(client, metadata_chat_id)
    finally:
        await client.stop()

if __name__ == "__main__":
    asyncio.run(main())
