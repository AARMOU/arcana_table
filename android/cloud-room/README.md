# Arcana Table internet room service

This is the first internet transport for Arcana Table. It uses one SQLite-backed Cloudflare Durable Object per room and secure WebSockets. The current LAN socket path stays available separately.

## Deploy the free prototype

1. Create a Cloudflare account and install Node.js on a computer with the project checkout.
2. From this directory, run `npm install` and `npx wrangler login`.
3. Run `npm run deploy`.
4. Wrangler prints a `https://…workers.dev` address. Enter that base address in Arcana Table's lobby under **Internet**. The app remembers it on that phone.
5. Test with two phones on different networks. One creates an internet room; the other selects Internet, enters the same service URL, room code, and password.

`GET /health` returns a small health response. Room sockets use `/room/{6-to-8-character-code}`. Use the HTTPS `workers.dev` base URL; the app converts it to WSS.

## Current scope

- Create and join password-protected rooms; allocate up to four seats and restore a disconnected seat by reconnecting with its seat number.
- Synchronize lobby readiness, deck status, game snapshots, chat, and stack state. Private libraries and unrevealed hands are filtered separately for each recipient.
- The room is destroyed as soon as the last connected player leaves or disconnects. A 24-hour alarm remains as a cleanup fallback for abandoned records.
- The Android client reconnect button can reconnect to an internet room.

This is still a prototype. Host moderation/removal and host migration are not implemented for cloud rooms yet. The server relays and stores state but is not a complete Magic rules engine or anti-cheat authority. Use private room passwords and avoid putting sensitive information in game chat.

## Free-tier behavior

Cloudflare's free plan has hard daily limits. If the room workload exceeds them, operations can fail until the quota resets. Keep room updates event-driven and avoid frequent full-state writes as usage grows. Check Cloudflare's current Durable Objects limits before inviting a large group.
