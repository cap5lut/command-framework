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

package net.kautler.command.api.restriction.jda

import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Message
import org.jboss.weld.junit4.WeldInitiator
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Subject

import javax.inject.Inject

import static net.dv8tion.jda.api.Permission.MANAGE_SERVER

class ServerManagerJdaTest extends Specification {
    @Rule
    WeldInitiator weld = WeldInitiator
            .from(ServerManagerJda)
            .inject(this)
            .build()

    @Inject
    @Subject
    ServerManagerJda serverManagerJda

    Message message = Stub {
        it.member >> null
    }

    Message guildMessage = Stub {
        it.member >> Stub(Member)
    }

    def 'server manager "#serverManager" should #be allowed'() {
        given:
            guildMessage.member.hasPermission(MANAGE_SERVER) >> serverManager

        expect:
            !serverManagerJda.allowCommand(message)
            serverManagerJda.allowCommand(guildMessage) == allowed

        where:
            serverManager || allowed | be
            true          || true    | 'be'
            false         || false   | 'not be'
    }
}
