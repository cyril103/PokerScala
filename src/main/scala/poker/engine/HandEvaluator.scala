package poker.engine

object HandEvaluator {
  def evaluate7(cards: Seq[Card]): HandRank = {
    require(cards.length >= 5 && cards.length <= 7, "evaluate7 expects 5 to 7 cards")
    combinations(cards.toVector, 5).map(evaluate5).max
  }

  def evaluate5(cards: Seq[Card]): HandRank = {
    require(cards.length == 5, "evaluate5 expects exactly 5 cards")
    val ranks = cards.map(_.rank.value)
    val suits = cards.map(_.suit)
    val ranksDescending = ranks.sorted(Ordering[Int].reverse)
    val counts = ranks.groupBy(identity).view.mapValues(_.size).toSeq.sortBy { case (value, count) => (-count, -value) }
    val isFlush = suits.distinct.size == 1
    val straightHighOpt = straightHighCard(ranks)

    (isFlush, straightHighOpt, counts) match {
      case (true, Some(high), _) =>
        HandRank(HandCategory.StraightFlush, Vector(high))
      case (_, _, (fourValue, 4) :: (kicker, _) :: Nil) =>
        HandRank(HandCategory.FourOfAKind, Vector(fourValue, kicker))
      case (_, _, (tripsValue, 3) :: (pairValue, 2) :: Nil) =>
        HandRank(HandCategory.FullHouse, Vector(tripsValue, pairValue))
      case (true, None, _) =>
        HandRank(HandCategory.Flush, ranksDescending.toVector)
      case (_, Some(high), _) =>
        // Use descending straight ranks; wheel is represented with 5 high
        val straightRanks = straightKickers(high)
        HandRank(HandCategory.Straight, straightRanks)
      case (_, _, (tripsValue, 3) :: rest) =>
        val kickers = rest.map(_._1).sorted(Ordering[Int].reverse)
        HandRank(HandCategory.ThreeOfAKind, Vector(tripsValue) ++ kickers.toVector)
      case (_, _, (highPair, 2) :: (lowPair, 2) :: rest) =>
        val kicker = rest.map(_._1).maxOption.getOrElse(0)
        HandRank(HandCategory.TwoPairs, Vector(math.max(highPair, lowPair), math.min(highPair, lowPair), kicker))
      case (_, _, (pairValue, 2) :: rest) =>
        val kickers = rest.map(_._1).sorted(Ordering[Int].reverse)
        HandRank(HandCategory.Pair, Vector(pairValue) ++ kickers.toVector)
      case _ =>
        HandRank(HandCategory.HighCard, ranksDescending.toVector)
    }
  }

  private def straightHighCard(ranks: Seq[Int]): Option[Int] = {
    val unique = ranks.distinct.sorted
    if (unique.size < 5) None
    else if (unique == List(2, 3, 4, 5, 14)) Some(5)
    else if (unique.sliding(5).exists(window => window.last - window.head == 4 && window.distinct.size == 5))
      Some(unique.sliding(5).find(window => window.last - window.head == 4 && window.distinct.size == 5).get.last)
    else None
  }

  private def straightKickers(high: Int): Vector[Int] = {
    if (high == 5) Vector(5, 4, 3, 2, 1)
    else Vector(high, high - 1, high - 2, high - 3, high - 4)
  }

  private def combinations[A](items: Vector[A], choose: Int): Vector[Vector[A]] = {
    if (choose == 0) Vector(Vector.empty[A])
    else if (items.isEmpty) Vector.empty
    else if (items.length == choose) Vector(items)
    else {
      val withHead = combinations(items.tail, choose - 1).map(items.head +: _)
      val withoutHead = combinations(items.tail, choose)
      withHead ++ withoutHead
    }
  }
}
