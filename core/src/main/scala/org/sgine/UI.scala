package org.sgine

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.TextureRegion
import org.sgine.event.InputProcessor
import pl.metastack.metarx.{ReadStateChannel, Sub}

trait UI extends RenderFlow with InputSupport {
  UI.instance = Some(this)

  private[sgine] var textureMap = Map.empty[String, Texture]
  private[sgine] var textureRegionMap = Map.empty[String, TextureRegion]

  def delta: Double = Gdx.graphics.getDeltaTime.toDouble
  private val _width = Sub(0.0)
  private val _height = Sub(0.0)
  def width: ReadStateChannel[Double] = _width
  def height: ReadStateChannel[Double] = _height
  lazy val aspectRatio: ReadStateChannel[Double] = {
    val s = Sub(0.0)
    s := width / height
    s
  }
  val continuousRendering: Sub[Boolean] = Sub(true)

  private[sgine] val listener = new GDXApplicationListener(this)
  private[sgine] val inputProcessor = new InputProcessor(this)

  create.once {
    continuousRendering.attach(cr => Gdx.graphics.setContinuousRendering(cr))
    Gdx.input.setInputProcessor(inputProcessor)
  }
  resize.on {
    _width := Gdx.graphics.getWidth.toDouble
    _height := Gdx.graphics.getHeight.toDouble
    invalidateDisplay()
  }

  def invalidateDisplay(): Unit = Gdx.graphics.requestRendering()

  def error(t: Throwable, message: Option[String] = None) = {
    // TODO: support logging
    System.err.println(message.getOrElse("An Error Occurred"))
    t.printStackTrace()
  }

  def catchErrors[R](f: => R) = try {
    f
  } catch {
    case t: Throwable => error(t)
  }
}

object UI {
  private var instance: Option[UI] = None

  def apply(): UI = instance.get

  def get(): Option[UI] = instance
}