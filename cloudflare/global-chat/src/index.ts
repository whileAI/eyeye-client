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

    const server = normalize(new URL(request.url).searchParams.get("server") ?? "", 128);
    if (!server) return new Response("Missing server identifier.", { status: 400 });

    const name = normalize(new URL(request.url).searchParams.get("name") ?? "", 32);
    if (!name) return new Response("Missing player name.", { status: 400 });

    return env.GLOBAL_CHAT.getByName(server).fetch(request);
  }
};

export class GlobalChatRoom extends DurableObject<Env> {
  private readonly sessions = new Map<WebSocket, string>();

  constructor(ctx: DurableObjectState, env: Env) {
    super(ctx, env);

    this.ctx.getWebSockets().forEach(socket => {
      const name = socket.deserializeAttachment();
      if (typeof name === "string") this.sessions.set(socket, name);
    });
  }

  async fetch(request: Request): Promise<Response> {
    const name = normalize(new URL(request.url).searchParams.get("name") ?? "", 32);
    if (!name) return new Response("Missing player name.", { status: 400 });

    const [client, server] = Object.values(new WebSocketPair()) as [WebSocket, WebSocket];
    this.ctx.acceptWebSocket(server);
    server.serializeAttachment(name);

    this.sessions.forEach(existingName => server.send(`P\t${existingName}`));
    this.sessions.set(server, name);
    this.broadcast(`P\t${name}`);

    return new Response(null, { status: 101, webSocket: client });
  }

  async webSocketMessage(socket: WebSocket, message: string | ArrayBuffer): Promise<void> {
    if (typeof message !== "string") return;

    const name = this.sessions.get(socket);
    if (!name || !message.startsWith("M\t")) return;

    const content = normalize(message.slice(2), 256);
    if (!content) return;

    this.broadcast(`M\t${name}\t${content}`);
  }

  async webSocketClose(socket: WebSocket): Promise<void> {
    const name = this.sessions.get(socket);
    this.sessions.delete(socket);
    if (name) this.broadcast(`L\t${name}`);
  }

  private broadcast(message: string): void {
    this.sessions.forEach((_name, socket) => {
      try {
        socket.send(message);
      } catch {
        this.sessions.delete(socket);
      }
    });
  }
}

function normalize(value: string, maximumLength: number): string {
  return value.replace(/[\t\r\n]/g, " ").trim().slice(0, maximumLength);
}
