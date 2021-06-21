package uk.co.notnull.proxydiscord.cloud;

import cloud.commandframework.arguments.parser.ArgumentParseResult;
import cloud.commandframework.arguments.parser.ArgumentParser;
import cloud.commandframework.arguments.standard.LongArgument;
import cloud.commandframework.context.CommandContext;
import cloud.commandframework.exceptions.parsing.NoInputProvidedException;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.util.*;

public final class LongParser<C> implements ArgumentParser<C, Long> {
	private final long min;
	private final long max;

	public LongParser(final long min, final long max) {
		this.min = min;
		this.max = max;
	}

	@Override
	public @NonNull ArgumentParseResult<Long> parse(
			final @NonNull CommandContext<C> commandContext,
			final @NonNull Queue<@NonNull String> inputQueue
	) {
		final String input = inputQueue.peek();
		if (input == null) {
			return ArgumentParseResult.failure(new NoInputProvidedException(
					LongParser.class,
					commandContext
			));
		}
		try {
			final long value = Long.parseLong(input);
			if (value < this.min || value > this.max) {
				return ArgumentParseResult.failure(new LongArgument.LongParseException(
						input,
						this.min,
						this.max,
						commandContext
				));
			}
			inputQueue.remove();
			return ArgumentParseResult.success(value);
		} catch (final Exception e) {
			return ArgumentParseResult.failure(new LongArgument.LongParseException(
					input,
					this.min,
					this.max,
					commandContext
			));
		}
	}

	@Override
	public boolean isContextFree() {
		return true;
	}
}