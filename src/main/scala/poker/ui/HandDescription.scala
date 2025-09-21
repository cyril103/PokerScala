package poker.ui

import poker.engine.{Card, HandCategory, HandRank, Suit}
import poker.model.ShowdownResult

object HandDescription {
  private val rankNames: Map[Int, String] = Map(
    14 -> "As",
    13 -> "Roi",
    12 -> "Dame",
    11 -> "Valet",
    10 -> "Dix",
    9 -> "Neuf",
    8 -> "Huit",
    7 -> "Sept",
    6 -> "Six",
    5 -> "Cinq",
    4 -> "Quatre",
    3 -> "Trois",
    2 -> "Deux",
    1 -> "As"
  )

  private val suitNames: Map[Suit, String] = Map(
    Suit.Clubs -> "trèfle",
    Suit.Diamonds -> "carreau",
    Suit.Hearts -> "coeur",
    Suit.Spades -> "pique"
  )

  def describe(result: ShowdownResult, board: Vector[Card]): String = {
    val hand = result.handRank
    val combined = result.cards ++ board

    hand.category match {
      case HandCategory.StraightFlush =>
        val suitText = flushSuit(combined, hand, requireStraight = true).map(s => s" de ${suitNames.getOrElse(s, "")}" ).getOrElse("")
        val high = rankName(hand.ranks.head)
        s"Quinte flush$suitText hauteur $high"
      case HandCategory.FourOfAKind =>
        val quad = rankName(hand.ranks.head)
        val kicker = hand.ranks.lift(1).map(rankName).map(k => s" avec $k en kicker").getOrElse("")
        s"Carré de $quad$kicker"
      case HandCategory.FullHouse =>
        val trips = rankName(hand.ranks.head)
        val pair = hand.ranks.lift(1).map(rankName).getOrElse("")
        s"Full $trips par $pair"
      case HandCategory.Flush =>
        val suitText = flushSuit(combined, hand, requireStraight = false).map(suitNames.getOrElse(_, "")).map(n => s" de $n").getOrElse("")
        val high = rankName(hand.ranks.head)
        val extras = formatList(hand.ranks.tail.map(rankName))
        val tail = if (extras.nonEmpty) s" puis $extras" else ""
        s"Couleur$suitText hauteur $high$tail"
      case HandCategory.Straight =>
        val high = rankName(hand.ranks.head)
        s"Suite hauteur $high"
      case HandCategory.ThreeOfAKind =>
        val trips = rankName(hand.ranks.head)
        val kickers = formatList(hand.ranks.tail.map(rankName))
        val tail = if (kickers.nonEmpty) s" avec $kickers en kicker" else ""
        s"Brelan de $trips$tail"
      case HandCategory.TwoPairs =>
        val highPair = rankName(hand.ranks.head)
        val lowPair = hand.ranks.lift(1).map(rankName).getOrElse("")
        val kicker = hand.ranks.lift(2).map(rankName).map(k => s", kicker $k").getOrElse("")
        s"Double paire $highPair et $lowPair$kicker"
      case HandCategory.Pair =>
        val pair = rankName(hand.ranks.head)
        val kickers = formatList(hand.ranks.tail.map(rankName))
        val tail = if (kickers.nonEmpty) s" accompagnée de $kickers" else ""
        s"Paire de $pair$tail"
      case HandCategory.HighCard =>
        val high = rankName(hand.ranks.head)
        val kickers = formatList(hand.ranks.tail.map(rankName))
        val tail = if (kickers.nonEmpty) s" suivie de $kickers" else ""
        s"Hauteur $high$tail"
    }
  }

  private def rankName(value: Int): String = rankNames.getOrElse(value, value.toString)

  private def formatList(items: Seq[String]): String = {
    val clean = items.filter(_.nonEmpty)
    clean match {
      case Nil => ""
      case head :: Nil => head
      case _ =>
        val init = clean.dropRight(1)
        val last = clean.last
        if (init.isEmpty) last else s"${init.mkString(", " )} et $last"
    }
  }

  private def flushSuit(cards: Seq[Card], hand: HandRank, requireStraight: Boolean): Option[Suit] = {
    val groups = cards.groupBy(_.suit).filter(_._2.size >= 5)
    if (groups.isEmpty) None
    else if (requireStraight) groups.collectFirst { case (suit, suitedCards) if containsStraight(suitedCards, hand.ranks) => suit }
    else {
      val target = normalizeRank(hand.ranks.head)
      groups.collectFirst { case (suit, suitedCards) if suitedCards.exists(c => normalizeRank(c.rank.value) == target) => suit }
        .orElse(groups.headOption.map(_._1))
    }
  }

  private def containsStraight(cards: Seq[Card], ranks: Vector[Int]): Boolean = {
    val values: Set[Int] = cards.flatMap { card =>
      val v = card.rank.value
      if (v == 14) Seq(14, 1) else Seq(v)
    }.toSet
    ranks.forall { r => values.contains(if (r == 1) 1 else r) || (r == 1 && values.contains(14)) }
  }

  private def normalizeRank(value: Int): Int = if (value == 1) 14 else value
}
