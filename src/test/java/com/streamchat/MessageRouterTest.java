package com.streamchat;

import com.streamchat.MessageRouter.Queued;
import java.util.ArrayDeque;
import java.util.Deque;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class MessageRouterTest
{
	private static final long NOW = 1_000_000L;
	private static final long MAX_AGE = 30_000L;

	private static Queued at(long receivedAtMs)
	{
		return new Queued(StreamChatMessage.builder()
			.platform(StreamPlatform.TWITCH)
			.channel("c")
			.author("a")
			.message("m")
			.id(Long.toString(receivedAtMs))
			.build(), receivedAtMs);
	}

	@Test
	public void keepsFreshMessages()
	{
		final Deque<Queued> q = new ArrayDeque<>();
		q.add(at(NOW - 1_000));
		q.add(at(NOW - 500));

		assertEquals(0, MessageRouter.purgeStale(q, NOW, MAX_AGE));
		assertEquals(2, q.size());
	}

	@Test
	public void dropsEverythingOlderThanTheCap()
	{
		// The login-screen case: a full queue built up while no game ticks were firing.
		final Deque<Queued> q = new ArrayDeque<>();
		for (int i = 0; i < 100; i++)
		{
			q.add(at(NOW - 120_000 + i));
		}

		assertEquals(100, MessageRouter.purgeStale(q, NOW, MAX_AGE));
		assertTrue(q.isEmpty());
	}

	@Test
	public void purgeIsNotRationed()
	{
		// The whole point: stale entries go in one pass, not a couple per tick, or clearing a
		// backlog would itself take half a minute of bursty output.
		final Deque<Queued> q = new ArrayDeque<>();
		for (int i = 0; i < 500; i++)
		{
			q.add(at(NOW - 60_000));
		}

		assertEquals(500, MessageRouter.purgeStale(q, NOW, MAX_AGE));
		assertEquals(0, q.size());
	}

	@Test
	public void stopsAtTheFirstFreshMessage()
	{
		final Deque<Queued> q = new ArrayDeque<>();
		q.add(at(NOW - 90_000));
		q.add(at(NOW - 60_000));
		q.add(at(NOW - 1_000));
		q.add(at(NOW));

		assertEquals(2, MessageRouter.purgeStale(q, NOW, MAX_AGE));
		assertEquals(2, q.size());
		assertEquals(NOW - 1_000, q.peekFirst().receivedAtMs);
	}

	@Test
	public void boundaryIsExclusive()
	{
		final Deque<Queued> q = new ArrayDeque<>();
		q.add(at(NOW - MAX_AGE));

		// Exactly at the cap is still young enough.
		assertEquals(0, MessageRouter.purgeStale(q, NOW, MAX_AGE));

		q.clear();
		q.add(at(NOW - MAX_AGE - 1));
		assertEquals(1, MessageRouter.purgeStale(q, NOW, MAX_AGE));
	}

	@Test
	public void handlesEmptyQueue()
	{
		final Deque<Queued> q = new ArrayDeque<>();
		assertEquals(0, MessageRouter.purgeStale(q, NOW, MAX_AGE));
	}
}
