package poker

import org.scalatest.funsuite.AnyFunSuite
import poker.engine._

final class HandEvaluatorSpec extends AnyFunSuite {
  test("detects straight flush with ace high") {
    val cards = Vector(
      Card(Rank.Ten, Suit.Spades),
      Card(Rank.Jack, Suit.Spades),
      Card(Rank.Queen, Suit.Spades),
      Card(Rank.King, Suit.Spades),
      Card(Rank.Ace, Suit.Spades)
    )
    val rank = HandEvaluator.evaluate5(cards)
    assert(rank.category == HandCategory.StraightFlush)
    assert(rank.ranks.head == Rank.Ace.value)
  }

  test("evaluate7 chooses best five cards") {
    val cards = Vector(
      Card(Rank.Ace, Suit.Hearts),
      Card(Rank.Ace, Suit.Spades),
      Card(Rank.Ace, Suit.Diamonds),
      Card(Rank.King, Suit.Clubs),
      Card(Rank.King, Suit.Spades),
      Card(Rank.Two, Suit.Hearts),
      Card(Rank.Three, Suit.Clubs)
    )
    val rank = HandEvaluator.evaluate7(cards)
    assert(rank.category == HandCategory.FullHouse)
    assert(rank.ranks == Vector(Rank.Ace.value, Rank.King.value))
  }

  test("wheel straight counts as five high") {
    val cards = Vector(
      Card(Rank.Ace, Suit.Clubs),
      Card(Rank.Two, Suit.Diamonds),
      Card(Rank.Three, Suit.Hearts),
      Card(Rank.Four, Suit.Spades),
      Card(Rank.Five, Suit.Clubs)
    )
    val rank = HandEvaluator.evaluate5(cards)
    assert(rank.category == HandCategory.Straight)
    assert(rank.ranks.head == 5)
  }

  test("pair beats high card") {
    val pair = HandEvaluator.evaluate5(Vector(
      Card(Rank.Ten, Suit.Clubs),
      Card(Rank.Ten, Suit.Diamonds),
      Card(Rank.Five, Suit.Hearts),
      Card(Rank.Seven, Suit.Spades),
      Card(Rank.Two, Suit.Clubs)
    ))
    val high = HandEvaluator.evaluate5(Vector(
      Card(Rank.King, Suit.Clubs),
      Card(Rank.Queen, Suit.Diamonds),
      Card(Rank.Nine, Suit.Hearts),
      Card(Rank.Four, Suit.Spades),
      Card(Rank.Three, Suit.Clubs)
    ))
    assert(pair > high)
  }
}
