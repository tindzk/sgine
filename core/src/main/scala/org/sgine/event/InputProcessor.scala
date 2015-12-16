package org.sgine.event

import com.badlogic.gdx.input.GestureDetector
import com.badlogic.gdx.input.GestureDetector.GestureListener
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.{Gdx, InputProcessor => GDXInputProcessor}
import org.sgine._
import org.sgine.component.{ActorWidget, Component}
import org.sgine.input.Key
import pl.metastack.metarx.{Channel, Sub}

import scala.annotation.tailrec

class InputProcessor(screen: Screen) extends GDXInputProcessor with GestureListener{
  private val gestures = new GestureDetector(this)
  private val vector = new Vector2

  private var screenX: Int = 0
  private var screenY: Int = 0
  private var stageX: Double = 0.0
  private var stageY: Double = 0.0
  private var localX: Double = 0.0
  private var localY: Double = 0.0

  val component: Sub[Component] = Sub[Component](screen)
  val focused: Sub[Option[Component]] = Sub[Option[Component]](None)

  def fireKeyEvent(keyCode: Int,
                   gestureFunction: Int => Boolean,
                   stageFunction: Int => Boolean,
                   componentChannel: Channel[KeyEvent],
                   screenChannel: Channel[KeyEvent],
                   uiChannel: Channel[KeyEvent]): Boolean = {
    Key.byCode(keyCode) match {
      case Some(key) => {
        val evt = KeyEvent(key, component.get, focused.get)
        componentChannel := evt
        screenChannel := evt
        uiChannel := evt
      }
      case None => Gdx.app.log("Unsupported Key Code", s"Unsupported keyCode: $keyCode in InputProcessor.fireKeyEvent.")
    }
    gestureFunction(keyCode)
    stageFunction(keyCode)
    true
  }

  def fireMouseEvent(componentChannel: Channel[MouseEvent],
                     screenChannel: Channel[MouseEvent],
                     uiChannel: Channel[MouseEvent],
                     button: Int = -1,
                     screenX: Int = screenX,
                     screenY: Int = screenY,
                     stageX: Double = stageX,
                     stageY: Double = stageY,
                     localX: Double = localX,
                     localY: Double = localY,
                     component: Component = this.component.get,
                     focused: Option[Component] = this.focused.get): Boolean = {
    val evt = MouseEvent(button, screenX, screenY, stageX, stageY, localX, localY, component, focused)
    componentChannel := evt
    screenChannel := evt
    uiChannel := evt
    true
  }

  private def updateCoordinates(screenX: Int, screenY: Int): Unit = {
    this.screenX = screenX
    this.screenY = screenY
    vector.set(screenX, screenY)
    screen.stage.screenToStageCoordinates(vector)
    this.stageX = vector.x
    this.stageY = vector.y

    val touchable: Boolean = false
    val actor = screen.stage.hit(stageX.toFloat, stageY.toFloat, touchable)
    val widget = findTouchable(actor).getUserObject.asInstanceOf[ActorWidget[Actor]]
    if (component.get != widget) {
      component := widget
    }
    vector.set(screenX, screenY)
    widget.actor.screenToLocalCoordinates(vector)
    this.localX = vector.x
    this.localY = vector.y
  }

  @tailrec
  final def findTouchable(actor: Actor): Actor = {
    if (actor != null && actor.isTouchable) {
      actor
    } else if (actor == null || actor.getParent == null) {
      screen.stage.getRoot
    } else {
      findTouchable(actor.getParent)
    }
  }

  override def keyDown(keycode: Int): Boolean = fireKeyEvent(keycode, gestures.keyDown, screen.stage.keyDown, focused.get.getOrElse(screen).key.down, screen.key.down, ui.key.down)

  override def keyUp(keycode: Int): Boolean = fireKeyEvent(keycode, gestures.keyUp, screen.stage.keyUp, focused.get.getOrElse(screen).key.up, screen.key.up, ui.key.up)

  override def keyTyped(character: Char): Boolean = {
    Key.byChar(character) match {
      case Some(key) => {
        val evt = KeyEvent(key, component.get, focused.get)
        focused.get.getOrElse(screen).key.typed := evt
        screen.key.typed := evt
        ui.key.typed := evt
      }
      case None => Gdx.app.log("Unsupported Key Code", s"Unsupported keyChar: $character in InputProcessor.keyTyped.")
    }
    gestures.keyTyped(character)
    screen.stage.keyTyped(character)
    true
  }

  override def mouseMoved(screenX: Int, screenY: Int): Boolean = {
    updateCoordinates(screenX, screenY)
    gestures.mouseMoved(screenX, screenY)
    screen.stage.mouseMoved(screenX, screenY)
    fireMouseEvent(component.get.touch.moved, screen.touch.moved, ui.touch.moved)
  }

  override def touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean = {
    updateCoordinates(screenX, screenY)
    gestures.touchDown(screenX, screenY, pointer, button)
    screen.stage.touchDown(screenX, screenY, pointer, button)
    fireMouseEvent(component.get.touch.down, screen.touch.down, ui.touch.down, button)
  }

  override def touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean = {
    updateCoordinates(screenX, screenY)
    gestures.touchDragged(screenX, screenY, pointer)
    screen.stage.touchDragged(screenX, screenY, pointer)
    fireMouseEvent(component.get.touch.dragged, screen.touch.dragged, ui.touch.dragged)
  }

  override def touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean = {
    updateCoordinates(screenX, screenY)
    gestures.touchUp(screenX, screenY, pointer, button)
    screen.stage.touchUp(screenX, screenY, pointer, button)
    fireMouseEvent(component.get.touch.up, screen.touch.up, ui.touch.up, button)
  }

  override def scrolled(amount: Int): Boolean = {
    gestures.scrolled(amount)
    val evt = ScrollEvent(-1, screenX, screenY, stageX, stageY, localX, localY, component.get, amount, focused.get)
    component.get.scrolled := evt
    screen.scrolled := evt
    ui.scrolled := evt
    true
  }

  override def touchDown(x: Float, y: Float, pointer: Int, button: Int): Boolean = {
    true
  }

  override def longPress(x: Float, y: Float): Boolean = {
    component.get.touch.longPressed := MouseEvent
    screen.touch.longPressed := MouseEvent
    ui.touch.longPressed := MouseEvent
    true
  }

  override def zoom(initialDistance: Float, distance: Float): Boolean = {
    val evt = ZoomEvent(-1, screenX, screenY, stageX, stageY, localX, localY, component.get, initialDistance, distance, focused.get)
    component.get.zoomed := evt
    screen.zoomed := evt
    ui.zoomed := evt
    true
  }

  override def pan(x: Float, y: Float, deltaX: Float, deltaY: Float): Boolean = {
    val evt = PanEvent(-1, screenX, screenY, stageX, stageY, localX, localY, component.get, deltaX, deltaY, focused.get)
    component.get.pan.start := evt
    screen.pan.start := evt
    ui.pan.start := evt
    true
  }

  override def panStop(x: Float, y: Float, pointer: Int, button: Int): Boolean = {
    component.get.pan.stop := MouseEvent
    screen.pan.stop := MouseEvent
    ui.pan.stop := MouseEvent
    true
  }

  override def tap(x: Float, y: Float, count: Int, button: Int): Boolean = {
    val evt = MouseEvent(button, screenX, screenY, stageX, stageY, localX, localY, component.get, focused.get)
    component.get.touch.tapped := evt
    screen.touch.tapped := evt
    ui.touch.tapped := evt
    true
  }

  override def fling(velocityX: Float, velocityY: Float, button: Int): Boolean = {
    val evt = FlingEvent(button, screenX, screenY, stageX, stageY, localX, localY, component.get, velocityX, velocityY, focused.get)
    component.get.flung := evt
    screen.flung := evt
    ui.flung := evt
    true
  }

  override def pinch(initialPointer1: Vector2, initialPointer2: Vector2, pointer1: Vector2, pointer2: Vector2): Boolean = {
    val evt = PinchEvent(-1, screenX, screenY, stageX, stageY, localX, localY, component.get, initialPointer1, initialPointer2, pointer1, pointer2, focused.get)
    component.get.pinched := evt
    screen.pinched := evt
    ui.pinched := evt
    true
  }
}