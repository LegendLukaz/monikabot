/**
 * This file is part of MonikaBot.
 *
 * Copyright (C) 2018 Derppening <david.18.19.21@gmail.com>
 *
 * MonikaBot is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * MonikaBot is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with MonikaBot.  If not, see <http://www.gnu.org/licenses/>.
 */

package core

import core.BuilderHelper.buildEmbed
import core.Core.getChannelName
import core.Core.getDiscordTag
import core.Persistence.debugChannel
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import sx.blah.discord.handle.obj.IChannel
import sx.blah.discord.handle.obj.IMessage
import sx.blah.discord.handle.obj.IUser
import java.awt.Color

interface ILogger {
    enum class LogLevel {
        DEBUG,
        INFO,
        WARN,
        ERROR
    }

    class LogHelper(val type: LogLevel, val message: String, val clazz: Class<*>) {
        private var srcMessage: () -> IMessage? = { null }
        private var srcAuthor: () -> IUser? = { null }
        private var srcChannel: () -> IChannel? = { null }
        private var info: () -> String = { "" }
        private var stackTrace: () -> Array<StackTraceElement>? = { null }

        fun message(action: () -> IMessage) { srcMessage = action }
        fun author(action: () -> IUser) { srcAuthor = action }
        fun channel(action: () -> IChannel) { srcChannel = action }
        fun info(action: () -> String) { info = action }
        fun stackTrace(action: () -> Array<StackTraceElement>) { stackTrace = action }

        fun build() {
            buildEmbed(debugChannel) {
                when (type) {
                    LogLevel.DEBUG -> {
                        withColor(Color.BLACK)
                        withTitle("Debug")
                    }
                    LogLevel.INFO -> {
                        withColor(Color.GRAY)
                        withTitle("Info")
                    }
                    LogLevel.WARN -> {
                        withColor(Color.YELLOW)
                        withTitle("Warning")
                    }
                    LogLevel.ERROR -> {
                        withColor(Color.RED)
                        withTitle("Error")
                    }
                }

                withDesc(message)

                if (srcMessage() != null) appendField("Caused by", "`${srcMessage()?.content}`", false)
                if (srcAuthor() != null) appendField("From", srcAuthor()!!.getDiscordTag(), false)
                if (srcChannel() != null) appendField("In", srcChannel()!!.getChannelName(), false)
                if (info().isNotBlank()) appendField("Additional Info", info(), false)
                if (stackTrace() != null) appendField("Stack Trace", "```${stackTrace()?.joinToString("\n")}```", false)

                withFooterText("Package: ${clazz.name}")
            }
        }
    }

    /**
     * Logs a message to the debug channel.
     *
     * @param level Level of logging.
     * @param message Message to log.
     * @param action Actions to apply to the logger.
     */
    fun log(level: LogLevel, message: String, action: LogHelper.() -> Unit = {}) {
        LogHelper(level, message, this.javaClass).apply(action).build()
    }

    val logger: Logger
        get() = LoggerFactory.getLogger(javaClass.name)!!
}
