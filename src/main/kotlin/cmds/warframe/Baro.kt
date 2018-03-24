package cmds.warframe

import cmds.IBase
import cmds.Warframe
import core.BuilderHelper.buildEmbed
import core.BuilderHelper.buildMessage
import core.BuilderHelper.insertSeparator
import core.IChannelLogger
import core.Parser
import sx.blah.discord.handle.impl.events.guild.channel.message.MessageReceivedEvent
import java.time.Duration
import java.time.Instant

object Baro : IBase, IChannelLogger {
    override fun handler(event: MessageReceivedEvent): Parser.HandleState {
        val args = getArgumentList(event.message.content).drop(1)
        if (args.isNotEmpty() && args.any { it.matches(Regex("-{0,2}help")) }) {
            help(event, false)

            return Parser.HandleState.HANDLED
        }

        val baro = try {
            Warframe.worldState.voidTraders.first()
        } catch (e: NoSuchElementException) {
            buildMessage(event.channel) {
                withContent("Unable to retrieve Baro Ki'Teer information! Please try again later.")
            }

            return Parser.HandleState.HANDLED
        }

        event.channel.toggleTypingStatus()
        buildEmbed(event.channel) {
            withTitle("Baro Ki'Teer Information")

            if (baro.manifest.isEmpty()) {
                val nextTimeDuration = Duration.between(Instant.now(), baro.activation.date.numberLong)
                appendField("Time to Next Appearance", formatTimeDuration(nextTimeDuration), true)
                appendField("Relay", WorldState.getSolNode(baro.node).value, true)
            } else {
                val expiryTimeDuration = Duration.between(Instant.now(), baro.expiry.date.numberLong)
                appendField("Time Left", formatTimeDuration(expiryTimeDuration), false)
                baro.manifest.forEach {
                    val item = WorldState.getLanguageFromAsset(it.itemType).let { fmt ->
                        if (fmt.isEmpty()) {
                            it.itemType
                        } else {
                            fmt
                        }
                    }
                    val ducats = it.primePrice
                    val credits = it.regularPrice
                    appendField(item, "$ducats Ducats - $credits Credits", true)
                }
            }

            withTimestamp(Instant.now())
        }

        return Parser.HandleState.HANDLED
    }

    override fun help(event: MessageReceivedEvent, isSu: Boolean) {
        buildEmbed(event.channel) {
            withTitle("Help Text for `warframe-baro`")
            withDesc("Displays information about Baro Ki'Teer.")
            insertSeparator()
            appendField("Usage", "```warframe baro```", false)

            onDiscordError { e ->
                log(IChannelLogger.LogLevel.ERROR, "Cannot display help text") {
                    author { event.author }
                    channel { event.channel }
                    info { e.errorMessage }
                }
            }
        }
    }

    /**
     * Formats a duration.
     */
    private fun formatTimeDuration(duration: Duration): String {
        return (if (duration.toDays() > 0) "${duration.toDays()}d " else "") +
                (if (duration.toHours() % 24 > 0) "${duration.toHours() % 24}h " else "") +
                (if (duration.toMinutes() % 60 > 0) "${duration.toMinutes() % 60}m " else "") +
                "${duration.seconds % 60}s"
    }
}