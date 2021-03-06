/*
 * Copyright 2011-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.lettuce.core.commands;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import io.lettuce.core.AbstractRedisClientTest;
import io.lettuce.core.RedisCommandExecutionException;
import io.lettuce.core.RedisException;
import io.lettuce.core.TransactionResult;
import io.lettuce.core.api.sync.RedisCommands;

/**
 * @author Will Glozer
 * @author Mark Paluch
 */
public class TransactionCommandTest extends AbstractRedisClientTest {

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Test
    public void discard() {
        assertThat(redis.multi()).isEqualTo("OK");
        redis.set(key, value);
        assertThat(redis.discard()).isEqualTo("OK");
        assertThat(redis.get(key)).isNull();
    }

    @Test
    public void exec() {
        assertThat(redis.multi()).isEqualTo("OK");
        redis.set(key, value);
        assertThat(redis.exec()).contains("OK");
        assertThat(redis.get(key)).isEqualTo(value);
    }

    @Test
    public void watch() {
        assertThat(redis.watch(key)).isEqualTo("OK");

        RedisCommands<String, String> redis2 = client.connect().sync();
        redis2.set(key, value + "X");
        redis2.getStatefulConnection().close();

        redis.multi();
        redis.append(key, "foo");

        TransactionResult transactionResult = redis.exec();

        assertThat(transactionResult.wasDiscarded()).isTrue();
        assertThat(transactionResult).isEmpty();

    }

    @Test
    public void unwatch() {
        assertThat(redis.unwatch()).isEqualTo("OK");
    }

    @Test
    public void commandsReturnNullInMulti() {

        assertThat(redis.multi()).isEqualTo("OK");
        assertThat(redis.set(key, value)).isNull();
        assertThat(redis.get(key)).isNull();

        TransactionResult exec = redis.exec();
        assertThat(exec.wasDiscarded()).isFalse();
        assertThat(exec).contains("OK", value);

        assertThat(redis.get(key)).isEqualTo(value);
    }

    @Test
    public void execmulti() {
        redis.multi();
        redis.set("one", "1");
        redis.set("two", "2");
        redis.mget("one", "two");
        redis.llen(key);
        assertThat(redis.exec()).contains("OK", "OK", list(kv("one", "1"), kv("two", "2")), 0L);
    }

    @Test
    public void emptyMulti() {
        redis.multi();
        TransactionResult exec = redis.exec();
        assertThat(exec.wasDiscarded()).isFalse();
        assertThat(exec).isEmpty();
    }

    @Test
    public void errorInMulti() {
        redis.multi();
        redis.set(key, value);
        redis.lpop(key);
        redis.get(key);
        TransactionResult values = redis.exec();
        assertThat(values.wasDiscarded()).isFalse();
        assertThat((String) values.get(0)).isEqualTo("OK");
        assertThat(values.get(1) instanceof RedisException).isTrue();
        assertThat((String) values.get(2)).isEqualTo(value);
    }

    @Test
    public void execWithoutMulti() {
        exception.expect(RedisCommandExecutionException.class);
        exception.expectMessage("ERR EXEC without MULTI");
        redis.exec();
    }

    @Test
    public void multiCalledTwiceShouldFail() {
        exception.expect(RedisCommandExecutionException.class);
        exception.expectMessage("ERR MULTI calls can not be nested");

        redis.multi();
        redis.multi();
    }
}
