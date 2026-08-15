import { DurableObject } from "cloudflare:workers";

export interface Env {
  GLOBAL_CHAT: DurableObjectNamespace<GlobalChatRoom>;
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    if (new URL(request.url).pathname !== "/chat") {
      return new Response("EyEye Global Chat", { status: 200 });
    }

    if (request.headers.get("Upgrade")?.toLowerCase() !== "websocket") {
      return new Response("Expected WebSocket upgrade.", { status: 426 });
    }

    return env.GLOBAL_CHAT.getByName("global").fetch(request);
  }
};

export class GlobalChatRoom extends DurableObject<Env> {
  async fetch(_request: Request): Promise<Response> {
    const [client, server] = Object.values(new WebSocketPair()) as [WebSocket, WebSocket];
    this.ctx.acceptWebSocket(server);

    return new Response(null, { status: 101, webSocket: client });
  }

  async webSocketMessage(_socket: WebSocket, message: string | ArrayBuffer): Promise<void> {
    if (typeof message !== "string") return;

    const payload = parseMessage(message);
    if (!payload) return;

    const broadcast = `${payload.author}\t${payload.message}`;
    for (const socket of this.ctx.getWebSockets()) socket.send(broadcast);
  }

  async webSocketClose(socket: WebSocket, code: number, reason: string): Promise<void> {
    socket.close(code, reason);
  }
}

function parseMessage(value: string): { author: string; message: string } | null {
  const separator = value.indexOf("\t");
  if (separator < 1) return null;

  const author = normalize(value.slice(0, separator), 32);
  const message = normalize(value.slice(separator + 1), 256);
  return author && message ? { author, message } : null;
}

function normalize(value: string, maximumLength: number): string {
  return value.replace(/[\t\r\n]/g, " ").trim().slice(0, maximumLength);
}
