package poker.engine

import poker.model.{Config, Player, PlayerStatus, Street}

final case class GameState(
  config: Config,
  handId: Long,
  players: Vector[Player],
  buttonIndex: Int,
  deck: Deck,
  board: Vector[Card],
  street: Street,
  potManager: PotManager,
  bettingRound: Option[BettingRound]
) {
  def playerById(id: Int): Option[Player] = players.find(_.id == id)

  def updatePlayer(updated: Player): GameState = {
    val idx = players.indexWhere(_.id == updated.id)
    if (idx < 0) this else copy(players = players.updated(idx, updated))
  }

  def replacePlayers(nextPlayers: Vector[Player]): GameState = copy(players = nextPlayers)

  def withDeck(nextDeck: Deck): GameState = copy(deck = nextDeck)

  def withBoard(nextBoard: Vector[Card]): GameState = copy(board = nextBoard)

  def withStreet(nextStreet: Street): GameState = copy(street = nextStreet)

  def withPot(nextPot: PotManager): GameState = copy(potManager = nextPot)

  def withBettingRound(round: Option[BettingRound]): GameState = copy(bettingRound = round)

  def activePlayers: Vector[Player] = players.filter(_.inHand)

  def alivePlayers: Vector[Player] = players.filter(_.stack > 0)
}

object GameState {
  def initial(config: Config, players: Vector[Player]): GameState = {
    GameState(
      config = config,
      handId = 1L,
      players = players,
      buttonIndex = 0,
      deck = Deck(config.rngSeed),
      board = Vector.empty,
      street = Street.PreFlop,
      potManager = PotManager(),
      bettingRound = None
    )
  }
}
