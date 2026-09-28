#!/usr/bin/env python3
"""
Local development mail server: accepts SMTP on port 1025 and shows every message in a web inbox on port 8025.
A dependency-free stand-in for MailHog when Docker is not used. With --store, messages survive restarts.

Usage: python3 scripts/dev_mail_server.py [--smtp-port 1025] [--http-port 8025] [--store inbox.json]
"""
import argparse
import email
import html
import json
import socketserver
import threading
from datetime import datetime
from email.header import decode_header, make_header
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

MESSAGES = []
LOCK = threading.Lock()
STORE = None


def save():
    if STORE:
        with open(STORE, "w", encoding="utf-8") as f:
            json.dump(MESSAGES, f, ensure_ascii=False)


def decode(value):
    return str(make_header(decode_header(value))) if value else ""


def body_of(message):
    part = next((p for p in message.walk() if p.get_content_type() == "text/plain"), message)
    payload = part.get_payload(decode=True) or b""
    return payload.decode(part.get_content_charset() or "utf-8", errors="replace")


class SmtpHandler(socketserver.StreamRequestHandler):
    def reply(self, line):
        self.wfile.write((line + "\r\n").encode())
        self.wfile.flush()

    def handle(self):
        self.reply("220 fops-dev-mail ready")
        recipients = []
        while True:
            line = self.rfile.readline().decode(errors="replace")
            if not line:
                return
            command = line.strip().upper()
            if command.startswith(("EHLO", "HELO")):
                self.reply("250 fops-dev-mail")
            elif command.startswith("MAIL FROM"):
                recipients = []
                self.reply("250 OK")
            elif command.startswith("RCPT TO"):
                recipients.append(line.strip()[8:].strip("<> "))
                self.reply("250 OK")
            elif command == "DATA":
                self.reply("354 End data with <CR><LF>.<CR><LF>")
                lines = []
                while True:
                    data = self.rfile.readline().decode(errors="replace")
                    if data.rstrip("\r\n") == ".":
                        break
                    lines.append(data[1:] if data.startswith("..") else data)
                self.store("".join(lines), recipients)
                self.reply("250 OK queued")
            elif command == "QUIT":
                self.reply("221 bye")
                return
            else:
                self.reply("250 OK")

    @staticmethod
    def store(raw, recipients):
        message = email.message_from_string(raw)
        entry = {
            "id": max((m["id"] for m in MESSAGES), default=0) + 1,
            "receivedAt": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
            "from": decode(message.get("From")),
            "to": decode(message.get("To")) or ", ".join(recipients),
            "subject": decode(message.get("Subject")),
            "body": body_of(message),
        }
        with LOCK:
            MESSAGES.insert(0, entry)
            save()
        print(f"[mail] {entry['receivedAt']} to={entry['to']} subject={entry['subject']!r}", flush=True)


PAGE = """<!doctype html><html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta http-equiv="refresh" content="3"><title>Dev inbox · Fusion Operations</title>
<style>
body{{margin:0;font-family:Inter,"Segoe UI",sans-serif;background:#f3f6fb;color:#122033}}
header{{display:flex;justify-content:space-between;align-items:center;gap:12px;padding:14px 16px;background:#fff;border-bottom:1px solid #edf1f7}}
main{{max-width:900px;margin:0 auto;padding:20px 16px;display:flex;flex-direction:column;gap:14px}}
article{{background:#fff;border-radius:14px;padding:16px 18px;box-shadow:0 8px 18px rgba(15,23,42,.05)}}
h1{{font-size:1.1rem;margin:0}} h2{{font-size:1rem;margin:0 0 6px}}
.meta{{color:#67798e;font-size:.85rem}} pre{{white-space:pre-wrap;font:inherit;margin:12px 0 0}}
button{{border:0;border-radius:10px;padding:9px 14px;background:#eaf1ff;color:#1d4ed8;font-weight:700;cursor:pointer}}
.empty{{text-align:center;color:#67798e;padding:40px 0}}
</style></head><body>
<header><h1>Dev inbox <span class="meta">· {count} message(s) · refreshes every 3s</span></h1>
<form method="post" action="/clear"><button>Clear inbox</button></form></header>
<main>{items}</main></body></html>"""


class InboxHandler(BaseHTTPRequestHandler):
    def do_GET(self):
        with LOCK:
            messages = list(MESSAGES)
        if self.path.startswith("/api/messages"):
            self.send(200, "application/json", json.dumps(messages))
            return
        items = "".join(
            f"<article><h2>{html.escape(m['subject'])}</h2>"
            f"<div class='meta'>To {html.escape(m['to'])} · from {html.escape(m['from'])} · {m['receivedAt']}</div>"
            f"<pre>{html.escape(m['body'])}</pre></article>"
            for m in messages
        ) or "<p class='empty'>No messages yet. Complete an order to receive its email here.</p>"
        self.send(200, "text/html; charset=utf-8", PAGE.format(count=len(messages), items=items))

    def do_POST(self):
        if self.path == "/clear":
            with LOCK:
                MESSAGES.clear()
                save()
        self.send_response(303)
        self.send_header("Location", "/")
        self.end_headers()

    do_DELETE = do_POST

    def send(self, status, content_type, text):
        body = text.encode()
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, *args):
        pass


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--smtp-port", type=int, default=1025)
    parser.add_argument("--http-port", type=int, default=8025)
    parser.add_argument("--store", help="JSON file to persist the inbox across restarts")
    args = parser.parse_args()

    global STORE
    STORE = args.store
    if STORE:
        try:
            with open(STORE, encoding="utf-8") as f:
                MESSAGES.extend(json.load(f))
        except (FileNotFoundError, json.JSONDecodeError):
            pass

    socketserver.ThreadingTCPServer.allow_reuse_address = True
    smtp = socketserver.ThreadingTCPServer(("127.0.0.1", args.smtp_port), SmtpHandler)
    threading.Thread(target=smtp.serve_forever, daemon=True).start()
    print(f"[mail] SMTP on 127.0.0.1:{args.smtp_port}, inbox on http://localhost:{args.http_port}", flush=True)
    ThreadingHTTPServer(("127.0.0.1", args.http_port), InboxHandler).serve_forever()


if __name__ == "__main__":
    main()
