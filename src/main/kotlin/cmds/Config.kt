/**
 * This file is part of MonikaBot.
 *
 * Copyright (C) 2018 Derppening <david.18.19.21@gmail.com>
 *
 * RTLib is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * RTLib is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with RTLib.  If not, see <http://www.gnu.org/licenses/>.
 */

package cmds

import core.*
import core.BuilderHelper.buildMessage
import insertSeparator
import sx.blah.discord.handle.impl.events.guild.channel.message.MessageReceivedEvent
import sx.blah.discord.util.DiscordException
import kotlin.concurrent.thread

/**
 * Singleton handling "config" commands.
 */
object Config : IBase, IChannelLogger {
    override fun handlerSu(event: MessageReceivedEvent): Parser.HandleState {
        val args = getArgumentList(event.message.content)

        if (args.isEmpty()) {
            Config.help(event, true)
            return Parser.HandleState.HANDLED
        }

        when (args[0]) {
            "experimental" -> {
                experimentalHandler(args, event)
            }
        }

        return Parser.HandleState.HANDLED
    }

    override fun help(event: MessageReceivedEvent, isSu: Boolean) {
        try {
            BuilderHelper.buildEmbed(event.channel) {
                withTitle("Help Text for `config`")
                withDesc("Core configurations for MonikaBot.")
                insertSeparator()
                appendField("Usage", "```config [configuration] [options...]```", false)
                appendField("Configuration: `experimental`", "Whether to enable experimental features", false)
            }
        } catch (e: DiscordException) {
            log(IChannelLogger.LogLevel.ERROR, "Cannot display help text") {
                author { event.author }
                channel { event.channel }
                info { e.errorMessage }
            }
            e.printStackTrace()
        }
    }

    /**
     * Handler for "config experimental" commands.
     *
     * @param args List of arguments.
     * @param event Event of the original message.
     */
    private fun experimentalHandler(args: List<String>, event: MessageReceivedEvent) {
        if (args.size == 1) {
            buildMessage(event.channel) {
                withContent("Experimental Features: ${if (enableExperimentalFeatures) "Enabled" else "Disabled"}.")
            }

            return
        } else if (args.size != 2 || args[1].matches(Regex("-{0,2}help"))) {
            BuilderHelper.buildEmbed(event.channel) {
                withTitle("Help Text for config-experimental`")
                withDesc("Whether to enable experimental features.")
                appendField("\u200B", "\u200B", false)
                appendField("Usage", "```config experimental [enable|disable]```", false)
                appendField("`[enable|disable]`", "Enables/Disables experimental features.", false)
            }

            return
        }

        enableExperimentalFeatures = args[1].toBoolean() || args[1] == "enable"
        buildMessage(event.channel) {
            withContent("Experimental Features are now ${if (enableExperimentalFeatures) "enabled" else "disabled"}.")
        }

        thread {
            PersistentMessage.modify("Config", "Experimental Features", enableExperimentalFeatures.toString(), true)
        }
    }

    /**
     * Whether to enable experimental features.
     */
    var enableExperimentalFeatures = Core.monikaVersionBranch == "development"
        private set
}