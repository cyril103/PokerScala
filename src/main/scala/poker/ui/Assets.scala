package poker.ui

import javafx.scene.Scene

object Assets {
  private val stylesheetPath = "/ui/application.css"

  def apply(scene: Scene): Unit = {
    val root = scene.getRoot
    root.getStyleClass.add("poker-root")
    Option(getClass.getResource(stylesheetPath)).foreach { url =>
      val css = url.toExternalForm
      if (!scene.getStylesheets.contains(css)) scene.getStylesheets.add(css)
    }
  }
}
