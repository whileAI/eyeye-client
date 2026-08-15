# EyEye Global Chat Worker

This Worker provides one shared WebSocket channel for EyEye Client users.

## Deploy

```powershell
cd cloudflare/global-chat
npm install
npx wrangler login
npm run deploy
```

Copy the printed Worker URL, change `https://` to `wss://`, append `/chat`, then paste it into the Global Chat module's **Endpoint** setting.

Example: `wss://eyeye-global-chat.example.workers.dev/chat`.

Messages that start with `!` are sent to the shared channel by default.
