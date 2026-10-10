package com.cupcake.game

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MathQuizEngineTest {

    @Test
    fun `generates valid question`() {
        val question = MathQuizEngine.generateQuestion(1)
        assertThat(question.expression).isNotEmpty()
        assertThat(question.correctAnswer).isNotNull()
        assertThat(question.botWrongAnswer).isNotEqualTo(question.correctAnswer)
    }

    @Test
    fun `generates questions with different difficulties`() {
        val easy = MathQuizEngine.generateQuestion(1)
        val medium = MathQuizEngine.generateQuestion(2)
        val hard = MathQuizEngine.generateQuestion(3)
        
        assertThat(easy.expression).isNotEmpty()
        assertThat(medium.expression).isNotEmpty()
        assertThat(hard.expression).isNotEmpty()
    }

    @Test
    fun `checks correct answer`() {
        val question = MathQuizEngine.MathQuestion("5+3", 8, 10)
        val board = Game.MathQuizBoard(currentQuestion = question)
        
        assertThat(MathQuizEngine.checkAnswer(board, 8)).isTrue()
        assertThat(MathQuizEngine.checkAnswer(board, 10)).isFalse()
        assertThat(MathQuizEngine.checkAnswer(board, 7)).isFalse()
    }

    @Test
    fun `checks correct answer for subtraction`() {
        val question = MathQuizEngine.MathQuestion("10-3", 7, 5)
        val board = Game.MathQuizBoard(currentQuestion = question)
        
        assertThat(MathQuizEngine.checkAnswer(board, 7)).isTrue()
        assertThat(MathQuizEngine.checkAnswer(board, 5)).isFalse()
    }

    @Test
    fun `checks correct answer for multiplication`() {
        val question = MathQuizEngine.MathQuestion("6×7", 42, 35)
        val board = Game.MathQuizBoard(currentQuestion = question)
        
        assertThat(MathQuizEngine.checkAnswer(board, 42)).isTrue()
        assertThat(MathQuizEngine.checkAnswer(board, 35)).isFalse()
    }

    @Test
    fun `checks correct answer for division`() {
        val question = MathQuizEngine.MathQuestion("56÷8", 7, 6)
        val board = Game.MathQuizBoard(currentQuestion = question)
        
        assertThat(MathQuizEngine.checkAnswer(board, 7)).isTrue()
        assertThat(MathQuizEngine.checkAnswer(board, 6)).isFalse()
    }

    @Test
    fun `bot response for correct human answer`() {
        val response = MathQuizEngine.getBotResponse(true)
        assertThat(response).isNotEmpty()
        // Should be a "wrong" response since bot gives wrong answers on purpose
        assertThat(response).matches("(?i).*wrong.*|.*nope.*|.*incorrect.*|.*try again.*")
    }

    @Test
    fun `bot response for incorrect human answer`() {
        val response = MathQuizEngine.getBotResponse(false)
        assertThat(response).isNotEmpty()
        // Should be a "correct" response since human got it wrong
        assertThat(response).matches("(?i).*correct.*|.*fine.*|.*ugh.*|.*lucky.*")
    }
}