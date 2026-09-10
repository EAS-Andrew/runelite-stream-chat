package com.streamchat;

/** Connection state of a single {@link ChatSource}, surfaced to the user via the !streamchat command. */
public enum SourceStatus
{
	/** Not configured, or turned off in config. */
	DISABLED,
	/** Connecting, or waiting out a reconnect backoff. */
	CONNECTING,
	/** Receiving messages. */
	CONNECTED,
	/** Stopped after an error that retrying will not fix (bad credentials, bad channel). */
	FAILED
}
