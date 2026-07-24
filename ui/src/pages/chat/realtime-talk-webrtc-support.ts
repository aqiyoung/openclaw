// Control UI chat module owns low-level WebRTC offer and media-message helpers.
import type { RealtimeTalkWebRtcSdpSessionResult } from "./realtime-talk-shared.ts";
import type { RealtimeTalkVideoFrame } from "./realtime-talk-video.ts";

const REALTIME_WEBRTC_OFFER_TIMEOUT_MS = 30_000;
const REALTIME_TALK_DEFAULT_MAX_MESSAGE_SIZE = 64 * 1024;

export type RealtimeServerEvent = {
  type?: string;
  item_id?: string;
  call_id?: string;
  name?: string;
  delta?: string;
  transcript?: string;
  text?: string;
  arguments?: string;
  error?: unknown;
  response?: {
    status?: string;
    status_details?: unknown;
  };
  item?: {
    id?: string;
    type?: string;
    text?: string;
  };
  turn?: {
    id?: string;
    role?: string;
    transcript?: string;
  };
};

type PendingOfferRequest = {
  controller: AbortController;
  timeout: ReturnType<typeof globalThis.setTimeout>;
};

export class RealtimeTalkWebRtcOfferExchange {
  private pendingRequest: PendingOfferRequest | null = null;

  async readAnswer(params: {
    session: RealtimeTalkWebRtcSdpSessionResult;
    offer: RTCSessionDescriptionInit;
    isCurrent: () => boolean;
  }): Promise<string | undefined> {
    const request = this.beginRequest();
    try {
      let response: Response;
      try {
        response = await fetch(
          params.session.offerUrl ?? "https://api.openai.com/v1/realtime/calls",
          {
            method: "POST",
            body: params.offer.sdp,
            headers: {
              ...params.session.offerHeaders,
              Authorization: `Bearer ${params.session.clientSecret}`,
              "Content-Type": "application/sdp",
            },
            signal: request.controller.signal,
          },
        );
      } catch (error) {
        if (!params.isCurrent()) {
          return undefined;
        }
        throw error;
      }
      if (!params.isCurrent()) {
        return undefined;
      }
      if (!response.ok) {
        throw new Error(`Realtime WebRTC setup failed (${response.status})`);
      }
      let answer: string;
      try {
        answer = await response.text();
      } catch (error) {
        if (!params.isCurrent()) {
          return undefined;
        }
        throw error;
      }
      return params.isCurrent() ? answer : undefined;
    } finally {
      this.finishRequest(request);
    }
  }

  abort(): void {
    const request = this.pendingRequest;
    if (!request) {
      return;
    }
    this.pendingRequest = null;
    globalThis.clearTimeout(request.timeout);
    request.controller.abort();
  }

  private beginRequest(): PendingOfferRequest {
    this.abort();
    const controller = new AbortController();
    const request = {
      controller,
      timeout: globalThis.setTimeout(() => {
        controller.abort(
          new Error(
            `Realtime WebRTC offer request timed out after ${REALTIME_WEBRTC_OFFER_TIMEOUT_MS}ms`,
          ),
        );
      }, REALTIME_WEBRTC_OFFER_TIMEOUT_MS),
    };
    this.pendingRequest = request;
    return request;
  }

  private finishRequest(request: PendingOfferRequest): void {
    globalThis.clearTimeout(request.timeout);
    // A stopped transport may already have started a replacement request.
    // Never let the old request's finally block detach the new lifecycle owner.
    if (this.pendingRequest === request) {
      this.pendingRequest = null;
    }
  }
}

export function realtimeTalkDataChannelMaxMessageSize(peer: RTCPeerConnection | null): number {
  const negotiated = peer?.sctp?.maxMessageSize;
  return typeof negotiated === "number" && Number.isFinite(negotiated) && negotiated > 0
    ? negotiated
    : REALTIME_TALK_DEFAULT_MAX_MESSAGE_SIZE;
}

export function realtimeTalkImageEvent(frame: RealtimeTalkVideoFrame): unknown {
  return {
    type: "conversation.item.create",
    item: {
      type: "message",
      role: "user",
      content: [{ type: "input_image", image_url: `data:${frame.mimeType};base64,${frame.data}` }],
    },
  };
}
