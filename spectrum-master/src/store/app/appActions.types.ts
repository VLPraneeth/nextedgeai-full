export interface ConnectMessageStream {
  type: string;
  channelId: string;
  userName: string;
}
export interface ClearErrorMessage {
  type: string;
}

export type AppAction = ClearErrorMessage | ConnectMessageStream;
