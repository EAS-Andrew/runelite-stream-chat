package com.streamchat;

import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;

/**
 * Lifecycle, status tracking and reconnect backoff shared by every source.
 *
 * <p>Subclasses implement {@link #doConnect()} and {@link #doStop()} and call
 * {@link #onConnected()} / {@link #onDisconnected(String)} / {@link #onFatal(String)}. Everything
 * to do with <em>when</em> to retry lives here so the three platforms cannot drift apart.
 */
@Slf4j
public abstract class AbstractChatSource implements ChatSource
{
	private static final long BASE_BACKOFF_MS = 2_000L;
	private static final long MAX_BACKOFF_MS = 300_000L;

	protected final ScheduledExecutorService executor;
	protected final ChatSink sink;

	private final AtomicBoolean running = new AtomicBoolean();
	private volatile Future<?> pending;
	private volatile SourceStatus status = SourceStatus.DISABLED;
	private volatile String statusDetail = "";
	private volatile int consecutiveFailures;

	protected AbstractChatSource(ScheduledExecutorService executor, ChatSink sink)
	{
		this.executor = executor;
		this.sink = sink;
	}

	@Override
	public final void start()
	{
		if (!running.compareAndSet(false, true))
		{
			return;
		}

		consecutiveFailures = 0;
		setStatus(SourceStatus.CONNECTING, "connecting");
		schedule(0L);
	}

	@Override
	public final void stop()
	{
		if (!running.compareAndSet(true, false))
		{
			return;
		}

		final Future<?> p = pending;
		if (p != null)
		{
			p.cancel(false);
			pending = null;
		}

		try
		{
			doStop();
		}
		catch (Exception ex)
		{
			log.debug("[{}] error during stop", platform(), ex);
		}

		setStatus(SourceStatus.DISABLED, "stopped");
	}

	/** True between {@link #start()} and {@link #stop()}. Subclasses must not act once this is false. */
	protected final boolean isRunning()
	{
		return running.get();
	}

	@Override
	public final SourceStatus status()
	{
		return status;
	}

	@Override
	public final String statusDetail()
	{
		return statusDetail;
	}

	/**
	 * Open the connection, or start the poll loop. Called on the executor. Throwing, or calling
	 * {@link #onDisconnected(String)} later, both schedule a retry.
	 */
	protected abstract void doConnect() throws Exception;

	/** Release sockets/timers. Called at most once per {@link #start()}, and never concurrently with it. */
	protected abstract void doStop();

	/** Report a working connection. Resets the backoff. */
	protected final void onConnected()
	{
		consecutiveFailures = 0;
		setStatus(SourceStatus.CONNECTED, "connected");
	}

	/** Report a recoverable failure. Schedules a retry with backoff. */
	protected final void onDisconnected(String reason)
	{
		if (!isRunning())
		{
			return;
		}

		final long delay = nextBackoffMs();
		setStatus(SourceStatus.CONNECTING, reason + "; retrying in " + (delay / 1000) + "s");
		log.debug("[{}] disconnected: {} (retry in {}ms)", platform(), reason, delay);

		try
		{
			doStop();
		}
		catch (Exception ex)
		{
			log.debug("[{}] error cleaning up before retry", platform(), ex);
		}

		schedule(delay);
	}

	/**
	 * Report a failure that retrying will not fix -- a rejected API key, a channel that does not
	 * exist. Stops trying and leaves the reason visible to the user.
	 */
	protected final void onFatal(String reason)
	{
		log.warn("[{}] giving up: {}", platform(), reason);
		running.set(false);

		final Future<?> p = pending;
		if (p != null)
		{
			p.cancel(false);
			pending = null;
		}

		try
		{
			doStop();
		}
		catch (Exception ex)
		{
			log.debug("[{}] error during fatal stop", platform(), ex);
		}

		setStatus(SourceStatus.FAILED, reason);
	}

	private void schedule(long delayMs)
	{
		pending = executor.schedule(() ->
		{
			if (!isRunning())
			{
				return;
			}

			try
			{
				doConnect();
			}
			catch (Exception ex)
			{
				log.debug("[{}] connect failed", platform(), ex);
				onDisconnected(describe(ex));
			}
		}, delayMs, TimeUnit.MILLISECONDS);
	}

	/**
	 * Exponential backoff capped at five minutes, with +/-20% jitter so that a client watching
	 * several channels does not reconnect to all of them in lockstep after an outage.
	 */
	private long nextBackoffMs()
	{
		final int failures = Math.min(consecutiveFailures++, 8);
		final long base = Math.min(BASE_BACKOFF_MS << failures, MAX_BACKOFF_MS);
		final double jitter = 0.8d + (ThreadLocalRandom.current().nextDouble() * 0.4d);
		return (long) (base * jitter);
	}

	private void setStatus(SourceStatus newStatus, String detail)
	{
		this.status = newStatus;
		this.statusDetail = detail == null ? "" : detail;
		sink.onStatus(platform(), newStatus, this.statusDetail);
	}

	private static String describe(Exception ex)
	{
		final String msg = ex.getMessage();
		return msg == null || msg.isEmpty() ? ex.getClass().getSimpleName() : msg;
	}
}
