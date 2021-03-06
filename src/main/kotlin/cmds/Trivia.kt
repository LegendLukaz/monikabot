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

package cmds

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import core.BuilderHelper.buildEmbed
import core.BuilderHelper.buildMessage
import core.BuilderHelper.insertSeparator
import core.Client
import core.Core.getDiscordTag
import core.ILogger
import core.Parser
import org.apache.commons.text.StringEscapeUtils
import sx.blah.discord.handle.impl.events.guild.channel.message.MessageReceivedEvent
import java.net.URL

object Trivia : IBase, ILogger {
    override fun handler(event: MessageReceivedEvent): Parser.HandleState {
        val args = getArgumentList(event.message.content)

        val questions = args.firstOrNull()?.toIntOrNull() ?: 5
        val difficulty = args.find { it.matches(Regex("(easy|medium|hard|any)")) } ?: "easy"

        val channel = event.author.orCreatePMChannel
        val triviaData = getTriviaQuestions(questions, difficulty)

        if (triviaData.responseCode != 0) {
            buildMessage(channel) {
                if (triviaData.responseCode == 1) {
                    withContent("I could only ask a maximum of 50 questions at a time!")
                } else {
                    withContent("I don't have questions to ask you... Let's play later! =3")
                }
            }

            return Parser.HandleState.HANDLED
        }

        logger.info("Starting Trivia for ${event.author.getDiscordTag()}")
        buildMessage(channel) {
            withContent("Let's play Trivia! There will be $questions questions with $difficulty difficulty for you to answer.")
            appendContent("\nType \"exit\" to quit any time!")
        }

        users.add(event.author.longID)

        var correctAnswers = 0
        var totalAnswers = 0

        game@ for (trivia in triviaData.results) {
            val answers = trivia.incorrectAnswers.toMutableList().also { it.add(trivia.correctAnswer) }.shuffled().map { it.trim() }

            var answerDebugStr = ""
            answers.forEachIndexed { i, s ->
                answerDebugStr += "\n[$i] $s ${if (answers.indexOfFirst { it == trivia.correctAnswer.trim() } == i) "<" else ""}"
            }
            logger.debug("Shuffled Answers:$answerDebugStr")

            buildEmbed(channel) {
                withAuthorName("Difficulty: ${trivia.difficulty.capitalize()}")
                withTitle("Category: ${trivia.category}")
                withDesc(StringEscapeUtils.unescapeHtml4(trivia.question))

                answers.forEachIndexed { i, answer ->
                    appendField((i + 65).toChar().toString(), StringEscapeUtils.unescapeHtml4(answer), true)
                }
            }

            while (channel.messageHistory.latestMessage == null) {
                Thread.sleep(500)
            }

            var lastMessageId = channel.messageHistory.latestMessage.longID
            logger.debug("Waiting for user input for Question ${totalAnswers + 1} of $questions")
            checkResponse@ while (true) {
                if (channel.messageHistory.latestMessage.longID != lastMessageId) {
                    val message = channel.messageHistory.latestMessage

                    lastMessageId = message.longID

                    if (message.content.equals("exit", true)) {
                        break@game
                    }

                    if (answers.any { it.equals(message.content, true) } ||
                            (message.content.length == 1 && (message.content[0].toUpperCase().toInt() - 65) in 0..answers.lastIndex)) {
                        if (answers.any { it.equals(message.content, true) }) {
                            logger.debug("Input \"${message.content}\" matches Answer Index ${answers.indexOfFirst { it.equals(message.content, true) }}")
                        } else {
                            logger.debug("Input \"${message.content}\" converted to match Answer Index ${message.content[0].toUpperCase().toInt() - 65}")
                        }
                        break@checkResponse
                    }
                } else {
                    Thread.sleep(500)
                }
            }

            val ans = try {
                channel.messageHistory.latestMessage.content ?: throw Exception("Latest message is a NullPointer")
            } catch (e: Exception) {
                buildMessage(event.channel) {
                    withContent("Monika hit a hiccup and needs to take a break :(")
                }

                e.printStackTrace()
                break@game
            }

            when (trivia.type) {
                "boolean" -> {
                    when {
                        ans.toBoolean() == trivia.correctAnswer.toBoolean() ||
                                ans.length == 1 && (ans[0].toUpperCase().toInt() - 65) == answers.indexOfFirst { it == trivia.correctAnswer } -> {
                            buildMessage(channel) {
                                withContent("You are correct! =D")
                            }
                            ++correctAnswers
                        }
                        else -> {
                            buildMessage(channel) {
                                withContent("You're incorrect... :(\nThe correct answer is ${trivia.correctAnswer}.")
                            }
                        }
                    }
                }
                "multiple" -> {
                    when {
                        ans.equals(trivia.correctAnswer.trim(), true) ||
                                ans.length == 1 && (ans[0].toUpperCase().toInt() - 65) == answers.indexOfFirst { it == trivia.correctAnswer } -> {
                            buildMessage(channel) {
                                withContent("You are correct! =D")
                            }
                            ++correctAnswers
                        }
                        else -> {
                            buildMessage(channel) {
                                withContent("You're incorrect... :(\nThe correct answer is ${StringEscapeUtils.unescapeHtml4(trivia.correctAnswer)}.")
                            }
                        }
                    }
                }
            }

            ++totalAnswers
        }

        buildMessage(channel) {
            withContent("Thanks for playing trivia with me! You got $correctAnswers out of $totalAnswers correct!")
        }

        users.remove(event.author.longID)
        logger.info("Ending Trivia for ${event.author.getDiscordTag()}")

        return Parser.HandleState.HANDLED
    }

    override fun help(event: MessageReceivedEvent, isSu: Boolean) {
        buildEmbed(event.channel) {
            withTitle("Help Text for `trivia`")
            withDesc("Starts a trivia game with Monika.")
            insertSeparator()
            appendField("Usage", "```trivia [questions] [difficulty]```", false)
            appendField("`[questions]`", "Number of questions to ask.\nDefaults to 5", false)
            appendField("`[difficulty]`", "Difficulty of the questions. Can be easy, medium, hard, or any.\nDefaults to easy.", false)

            onDiscordError { e ->
                log(ILogger.LogLevel.ERROR, "Cannot display help text") {
                    author { event.author }
                    channel { event.channel }
                    info { e.errorMessage }
                }
            }
        }
    }

    /**
     * Checks whether the current user is playing trivia.
     */
    fun checkUserTriviaStatus(event: MessageReceivedEvent): Boolean {
        if (users.any { it == event.author.longID }) {
            if (!event.channel.isPrivate) {
                buildMessage(event.channel) {
                    withContent("It looks like you're still in a trivia game... Type \"exit\" in my private chat to quit it!")
                }
            }
            return true
        }

        return false
    }

    fun gracefulShutdown() {
        users.forEach {
            val channel = Client.getUserByID(it)!!.orCreatePMChannel
            buildMessage(channel) {
                withContent("Friendly Reminder: I will be going down for maintenance in one minute!")
            }
        }
    }

    var users = mutableListOf<Long>()
        private set

    private fun getTriviaQuestions(questions: Int, difficulty: String): TriviaData {
        return jacksonObjectMapper().apply {
            configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true)
        }.readValue(URL("https://opentdb.com/api.php?amount=$questions${if (difficulty != "any") "&difficulty=$difficulty" else ""}"))
    }

    class TriviaData {
        @JsonProperty("response_code")
        val responseCode = 0
        val results = listOf<Result>()

        class Result {
            val category = ""
            val type = ""
            val difficulty = ""
            val question = ""
            @JsonProperty("correct_answer")
            val correctAnswer = ""
            @JsonProperty("incorrect_answers")
            val incorrectAnswers = listOf<String>()
        }
    }
}