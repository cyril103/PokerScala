ScalaHoldemFX — Texas Hold’em (Scala 3.3.6 + JavaFX)

Jeu Texas Hold’em No-Limit en Scala 3.3.6 avec JavaFX (UI).
1 joueur humain contre 1 à 8 IA (Monte-Carlo + profilage simple).
Moteur complet (blinds, streets, side pots, showdown), tests (ScalaTest).

🚀 Fonctionnalités

Règles complètes Hold’em (preflop → flop → turn → river → showdown)

Blinds rotatives, antes optionnelles, multi side pots pour all-ins

IA crédibles : EHS/Equity estimée par simulations Monte-Carlo, profil (risk, bluff, aggression) et policy fold/call/raise avec sizings cohérents

UI JavaFX : table, sièges, cartes, pot(s), historique, slider de mise, réglages (nb IA, vitesse, itérations Monte-Carlo, seed RNG)

Code testable & déterministe (seed RNG), tests unitaires clés

📦 Prérequis

JDK 21+ (Temurin/Oracle/Zulu)

sbt ≥ 1.9

JavaFX 21.x (récupéré via dépendances Maven)

OS : Windows / macOS (Intel/Apple Silicon) / Linux

🛠️ Installation & Lancement

Cloner ce dépôt

git clone <votre-repo> ScalaHoldemFX
cd ScalaHoldemFX


sbt run

sbt run


Tests

sbt test


💡 Si vous rencontrez un problème JavaFX de classifier, lisez la section Notes JavaFX (classifier OS).

⚙️ Configuration du build

build.sbt (extrait) :

ThisBuild / scalaVersion := "3.3.6"

lazy val root = (project in file("."))
  .settings(
    name := "ScalaHoldemFX",
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % "3.2.19" % Test,
      "org.openjfx" % "javafx-controls" % "21.0.3" classifier osClassifier.value,
      "org.openjfx" % "javafx-graphics" % "21.0.3" classifier osClassifier.value
    )
  )

def osClassifier = Def.setting {
  val os = System.getProperty("os.name").toLowerCase
  val arch = System.getProperty("os.arch").toLowerCase
  if (os.contains("win")) "win"
  else if (os.contains("mac") && arch.contains("aarch64")) "mac-aarch64"
  else if (os.contains("mac")) "mac"
  else "linux"
}

🗂️ Arborescence
/project
/build.sbt
/src
  /main/scala/poker
    /engine
      Card.scala
      Deck.scala
      HandRank.scala
      HandEvaluator.scala
      BettingRound.scala
      PotManager.scala
      GameState.scala
      Dealer.scala
      Rules.scala
    /ai
      MonteCarloSimulator.scala
      HandStrength.scala
      OpponentModel.scala
      AIPolicy.scala
      Bot.scala
    /ui
      MainApp.scala
      TableView.scala
      SeatsView.scala
      CardsView.scala
      ControlsView.scala
      SettingsDialog.scala
      Assets.scala
    /model
      Player.scala
      Action.scala
      Config.scala
      Events.scala
  /test/scala/poker
    HandEvaluatorSpec.scala
    PotManagerSpec.scala
    GameStateSpec.scala
    AIPolicySpec.scala
/README.md

🎮 Commandes & UI

Boutons : Fold / Check-Call / Bet-Raise

Slider : taille de mise/relance (min-raise, 1/2 pot, 2/3 pot, pot, overbet selon contexte IA)

Raccourcis :

F → Fold

C → Check/Call

R → Raise

+ / - → Ajuster le slider

Settings : nombre d’IA (1–8), stacks initiaux, blinds, vitesse animations, itérations Monte-Carlo, seed RNG

🧠 IA — Aperçu rapide

Équité / EHS via Monte-Carlo (ex. 2 000 itérations par décision, avec time-budget ~15–30 ms)

Profils : riskTolerance [0..1], bluffFreq [0..0.3], aggression [0..1], callStation [0..1]
- Préréglages : Balanced, Tight-Passive, Loose-Aggressive, Calling Station, Opportunist (assignés de façon déterministe selon le siège des bots)

Policy : position, street, SPR, pot odds, EHS

EHS < t_fold → Fold (sauf prix très favorable)

t_call ≤ EHS < t_raise → Call

EHS ≥ t_raise → Raise (sizing contextuel)

Bluffs : prob. bluffFreq (semi-bluffs privilégiés), ε-greedy faible pour diversité

🃏 Évaluateur de mains (7 cartes)

Classe le meilleur 5 sur 7 : quinte flush > carré > full > couleur > suite > brelan > double paire > paire > hauteur

API : evaluate7(cards: Seq[Card]): Int (documenter l’ordre croissant/décroissant)

Implémentation optimisée (bitmasks / table-driven) + tests de non-régression

🔧 Paramètres (ex. model/Config.scala)
case class Config(
  startingStack: Int = 200,        // Big Blinds ou jetons
  smallBlind: Int = 1,
  bigBlind: Int = 2,
  ante: Int = 0,
  numBots: Int = 4,                // 1..8
  mcIterations: Int = 2000,
  decisionTimeMs: Int = 25,        // budget IA par action
  rngSeed: Option[Long] = Some(42),
  uiAnimationMs: Int = 300
)


Conseil : Ajustez mcIterations + decisionTimeMs pour équilibrer qualité IA / fluidité.

Fixez rngSeed pour des runs reproductibles (utile en tests/débogage).

✅ Tests

HandEvaluatorSpec : hiérarchie, égalités, cas limites

PotManagerSpec : multi all-ins, side pots corrects

GameStateSpec : transitions de street, conditions de fin

AIPolicySpec : cohérence des décisions selon EHS/pot odds/profil

Exécuter :

sbt test

🐞 Dépannage & FAQ

JavaFX “module not found” ou crash au lancement

Vérifiez le classifier (Windows: win, macOS Intel: mac, Apple Silicon: mac-aarch64, Linux: linux).

Assurez-vous d’être sur JDK 21+.

Essayez sbt -J-Xmx2G run si compilation lente/manque mémoire.

UI se fige pendant les décisions IA

Réduire mcIterations ou decisionTimeMs.

Vérifier que les simulations se font hors thread UI (Platform.runLater uniquement pour MAJ UI).

Résultats IA trop “nit” ou trop “maniac”

Ajuster riskTolerance, aggression, bluffFreq, seuils t_fold/t_raise.

Augmenter MC pour boards complexes.

Mac (Apple Silicon) :

Si soucis, forcez -Dos.arch=aarch64 ou utilisez un JDK aarch64 et le classifier mac-aarch64.

🧭 Roadmap (idées)

Replayer de mains + export historique

Profils IA prédéfinis (nit/lag/maniac/calling-station) sélectionnables

Mode tournoi (blinds progressives)

Persistances des préférences (JSON)

SFX/animations additionnelles

🔒 Licence

MIT (ou à préciser).
© 2025 — Contributions bienvenues via PR/Issues.

🙌 Crédit & Contrib

Style Scala 3 idiomatique (case classes, immutabilité quand pertinent)

Merci de :

documenter les méthodes publiques,

ajouter des tests pour toute nouvelle logique,

respecter la structure du projet.

Bon jeu et bons commits ! 🃏♠️