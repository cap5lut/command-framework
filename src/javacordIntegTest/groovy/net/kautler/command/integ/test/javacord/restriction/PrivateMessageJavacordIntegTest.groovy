/*
 * Copyright 2019 Björn Kautler
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.kautler.command.integ.test.javacord.restriction

import net.kautler.command.api.annotation.RestrictedTo
import net.kautler.command.api.event.javacord.CommandNotAllowedEventJavacord
import net.kautler.command.api.restriction.javacord.PrivateMessageJavacord
import net.kautler.command.integ.test.ManualTests
import net.kautler.command.integ.test.javacord.PingIntegTest
import net.kautler.command.integ.test.spock.AddBean
import org.javacord.api.DiscordApi
import org.javacord.api.entity.channel.ServerTextChannel
import org.junit.experimental.categories.Category
import spock.lang.Specification
import spock.lang.Subject
import spock.util.concurrent.BlockingVariable

import javax.enterprise.event.ObservesAsync

import static java.util.UUID.randomUUID

@Subject(PrivateMessageJavacord)
class PrivateMessageJavacordIntegTest extends Specification {
    @AddBean(PingCommand)
    def 'ping command should not respond if in server channel'(ServerTextChannel serverTextChannelAsUser) {
        given:
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommand.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        when:
            serverTextChannelAsUser
                    .sendMessage('!ping')
                    .join()

        then:
            commandNotAllowedEventReceived.get()
    }

    @Category(ManualTests)
    @AddBean(PingCommand)
    def 'ping command should respond if in private channel'(DiscordApi botDiscordApi) {
        given:
            def owner = botDiscordApi.owner.join()
            def random = randomUUID()
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)

        and:
            def listenerManagers = [
                    owner.openPrivateChannel().join().addMessageCreateListener {
                        if (it.message.author.yourself && (it.message.content == "pong: $random")) {
                            responseReceived.set(true)
                        }
                    }
            ]

        when:
            def commandReceived = new BlockingVariable<Boolean>(System.properties.testManualCommandTimeout as double)
            listenerManagers << owner.addMessageCreateListener {
                if (it.privateMessage && (it.message.content == "!ping $random")) {
                    commandReceived.set(true)
                }
            }
            owner
                    .sendMessage("$owner.mentionTag please send `!ping $random` in this channel")
                    .join()
            commandReceived.get()

        then:
            responseReceived.get()

        cleanup:
            listenerManagers*.remove()
    }

    @RestrictedTo(PrivateMessageJavacord)
    static class PingCommand extends PingIntegTest.PingCommand {
        static commandNotAllowedEventReceived

        void handleCommandNotAllowedEvent(@ObservesAsync CommandNotAllowedEventJavacord commandNotAllowedEvent) {
            commandNotAllowedEventReceived?.set(commandNotAllowedEvent)
        }
    }
}
